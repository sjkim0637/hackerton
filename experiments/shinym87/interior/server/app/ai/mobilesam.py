"""MobileSAM 점 프롬프트(point prompt) → 사물 마스크.

D5 결정(`experiments/shinym87/interior/docs/decisions.md`)에 따라, bbox 드래그 대신
"사물을 한 번 탭"만으로 정밀한 마스크를 얻기 위해 도입한다. 순수 OpenCV(GrabCut/
Saliency/floodFill)는 점 하나만으로는 입력이 부족해 인테리어 사진(배경 비중이 크고
색상이 벽/가구 간에 비슷함)에서 자주 실패하므로, 점 프롬프트 전용으로 설계된
MobileSAM(Segment Anything의 경량 증류 모델)을 쓴다.

모델 파일(encoder/decoder ONNX)은 저장소에 커밋하지 않는다 — 수동으로 받아
`INTERIOR_MOBILESAM_ENCODER_PATH` / `INTERIOR_MOBILESAM_DECODER_PATH` 로 지정한다.
경로가 없거나 파일이 없으면 `is_available()` 이 False 를 반환하고, 호출부
(`app/routers/scenes.py`)가 점 중심 정사각형 bbox 근사로 대체한다(품질은 떨어지지만
항상 동작은 한다).

인터페이스는 실제로 받은 모델 파일(`akbartus/MobileSAM-in-the-Browser`, SAMExporter로
변환된 ONNX)의 입출력을 그대로 검증해서 맞춘 것이다 — 공식 SAM 저장소의
`scripts/export_onnx_model.py` 산출물과는 입력 형태가 다르다:

- encoder 입력: `input_image`, `(H, W, 3)` float32 — **배치 차원 없음, HWC(NCHW 아님),
  0~255 원본 픽셀 값**(SAMExporter가 정규화를 그래프 안에 이미 포함). 정사각형 1024×1024로
  패딩하지 않고 긴 변만 1024로 리사이즈한 실제 크기를 그대로 넣는다(가로세로 비율 유지).
- decoder 입력: `image_embeddings, point_coords, point_labels, mask_input,
  has_mask_input, orig_im_size`. `point_coords`/`point_labels`는 실제 점 1개 + 더미 패딩
  점 `(0, 0)`/레이블 `-1`을 항상 같이 보내야 한다(레이블 1개만 보내도 에러는 안 나지만,
  `real_living_room.jpg`로 직접 비교해보니 패딩 점을 포함했을 때 사물 전체(예: TV 받침대·
  케이블까지)를 더 안정적으로 잡았다). `orig_im_size`는 **리사이즈 전 원본** `[height, width]`
  로 주면 decoder가 그래프 안의 Resize 로 마스크를 원본 해상도로 직접 돌려준다(우리가 따로
  후처리로 리사이즈할 필요 없음).
- decoder 출력: `masks (1, N, orig_h, orig_w)` 로짓, `iou_predictions (1, N)`.
"""
from __future__ import annotations

import io
import logging
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import TYPE_CHECKING

from PIL import Image

if TYPE_CHECKING:  # numpy/onnxruntime 는 MobileSAM 모델을 실제로 쓸 때만 필요 (지연 import).
    import numpy as np

_log = logging.getLogger("interior.ai.mobilesam")

_TARGET_LONG_SIDE = 1024


@dataclass
class SegmentResult:
    mask_png: bytes  # 원본 이미지와 같은 크기의 흑백 PNG (사물=255)
    score: float  # decoder 가 준 IoU 예측치 (참고용)


def _resize_longest_side(im: Image.Image, target: int) -> tuple[Image.Image, float]:
    w, h = im.size
    scale = target / max(w, h)
    new_w, new_h = round(w * scale), round(h * scale)
    return im.resize((new_w, new_h), Image.BILINEAR), scale


def _preprocess(image_bytes: bytes) -> tuple[np.ndarray, float, tuple[int, int]]:
    """encoder 입력 텐서(H,W,3 float32, 0~255), 리사이즈 배율, 원본 (w,h) 를 만든다."""
    import numpy as np

    with Image.open(io.BytesIO(image_bytes)) as im:
        im = im.convert("RGB")
        orig_size = im.size  # (w, h)
        resized, scale = _resize_longest_side(im, _TARGET_LONG_SIDE)
        arr = np.asarray(resized, dtype=np.float32)  # (h, w, 3), 0~255 그대로
    return arr, scale, orig_size


class MobileSamSegmenter:
    """encoder/decoder ONNX 세션을 지연 로드하고 점 프롬프트 추론을 수행한다."""

    def __init__(self, encoder_path: Path, decoder_path: Path) -> None:
        self._encoder_path = encoder_path
        self._decoder_path = decoder_path
        self._encoder = None
        self._decoder = None

    def _ensure_loaded(self) -> None:
        if self._encoder is not None and self._decoder is not None:
            return
        import onnxruntime as ort  # 지연 import: 미설정 환경에서 의존성 없이도 서버가 뜨게 함

        self._encoder = ort.InferenceSession(
            str(self._encoder_path), providers=["CPUExecutionProvider"]
        )
        self._decoder = ort.InferenceSession(
            str(self._decoder_path), providers=["CPUExecutionProvider"]
        )
        _log.info(
            "MobileSAM 로드 완료: encoder=%s decoder=%s",
            self._encoder_path.name, self._decoder_path.name,
        )

    def segment_point(self, image_bytes: bytes, x_norm: float, y_norm: float) -> SegmentResult:
        """정규화 [0,1] 좌표 점 하나로 사물 마스크를 얻는다."""
        import numpy as np

        self._ensure_loaded()
        assert self._encoder is not None and self._decoder is not None

        image_hwc, scale, orig_size = _preprocess(image_bytes)
        (embedding,) = self._encoder.run(None, {"input_image": image_hwc})

        orig_w, orig_h = orig_size
        # 리사이즈된 이미지 픽셀 좌표계로 변환 (encoder 에 넣은 이미지 기준).
        px, py = x_norm * orig_w * scale, y_norm * orig_h * scale
        # 실제 점 + 더미 패딩 점(0,0)/레이블 -1. 패딩 없이 점 1개만 보내도 에러는
        # 안 나지만, 사물의 받침대/케이블처럼 딸린 부분까지 포함한 마스크를 얻으려면
        # 이 패딩 점 조합이 더 안정적이었다(모듈 docstring 참고).
        point_coords = np.array([[[px, py], [0.0, 0.0]]], dtype=np.float32)
        point_labels = np.array([[1, -1]], dtype=np.float32)
        mask_input = np.zeros((1, 1, 256, 256), dtype=np.float32)
        has_mask_input = np.zeros(1, dtype=np.float32)
        # 리사이즈 전 원본 크기를 줘야 decoder 가 원본 해상도 마스크를 바로 돌려준다.
        orig_im_size = np.array([orig_h, orig_w], dtype=np.float32)

        outputs = self._decoder.run(
            None,
            {
                "image_embeddings": embedding,
                "point_coords": point_coords,
                "point_labels": point_labels,
                "mask_input": mask_input,
                "has_mask_input": has_mask_input,
                "orig_im_size": orig_im_size,
            },
        )
        masks, iou_predictions = outputs[0], outputs[1]

        best = int(np.argmax(iou_predictions[0]))
        logits = masks[0, best]  # (orig_h, orig_w) — decoder 가 원본 크기로 이미 업샘플
        binary = (logits > 0).astype(np.uint8) * 255
        mask_img = Image.fromarray(binary, mode="L")

        out = io.BytesIO()
        mask_img.save(out, format="PNG")
        return SegmentResult(mask_png=out.getvalue(), score=float(iou_predictions[0][best]))


@lru_cache
def _cached_segmenter(encoder_path: str, decoder_path: str) -> MobileSamSegmenter | None:
    enc, dec = Path(encoder_path), Path(decoder_path)
    if not enc.is_file() or not dec.is_file():
        return None
    return MobileSamSegmenter(enc, dec)


def get_segmenter(encoder_path: Path | None, decoder_path: Path | None) -> MobileSamSegmenter | None:
    """설정된 경로에 모델 파일이 실제로 있을 때만 세그멘터를 반환한다."""
    if encoder_path is None or decoder_path is None:
        return None
    return _cached_segmenter(str(encoder_path), str(decoder_path))


def is_available(encoder_path: Path | None, decoder_path: Path | None) -> bool:
    return get_segmenter(encoder_path, decoder_path) is not None


def point_to_mask_png(
    image_bytes: bytes,
    x_norm: float,
    y_norm: float,
    *,
    encoder_path: Path | None,
    decoder_path: Path | None,
) -> bytes | None:
    """MobileSAM 이 준비돼 있으면 마스크 PNG 를, 아니면 None 을 반환한다(호출부가 대체)."""
    segmenter = get_segmenter(encoder_path, decoder_path)
    if segmenter is None:
        return None
    try:
        return segmenter.segment_point(image_bytes, x_norm, y_norm).mask_png
    except Exception as exc:  # noqa: BLE001 - 추론 실패는 대체 경로로 넘긴다
        _log.warning("MobileSAM 추론 실패, bbox 근사로 대체: %s", exc)
        return None


def fallback_box_mask_png(
    image_bytes: bytes, x_norm: float, y_norm: float, box_frac: float
) -> bytes:
    """MobileSAM 이 없을 때 점 중심 정사각형 마스크로 대체(품질 낮음, 항상 동작)."""
    with Image.open(io.BytesIO(image_bytes)) as im:
        width, height = im.size

    side = max(8, round(min(width, height) * box_frac))
    cx, cy = x_norm * width, y_norm * height
    left = int(max(0, cx - side / 2))
    top = int(max(0, cy - side / 2))
    right = int(min(width, cx + side / 2))
    bottom = int(min(height, cy + side / 2))

    mask = Image.new("L", (width, height), 0)
    from PIL import ImageDraw

    ImageDraw.Draw(mask).rectangle([left, top, right, bottom], fill=255)
    out = io.BytesIO()
    mask.save(out, format="PNG")
    return out.getvalue()
