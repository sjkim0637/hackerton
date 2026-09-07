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

인터페이스는 공식 SAM ONNX export(`segment-anything`/`MobileSAM` 저장소의
`scripts/export_onnx_model.py` 산출물)와 동일한 decoder 입출력을 가정한다:

- encoder 입력: `(1, 3, 1024, 1024)` float32, SAM 픽셀 정규화
- decoder 입력: `image_embeddings, point_coords, point_labels, mask_input,
  has_mask_input, orig_im_size`
- decoder 출력: `masks (1, 1, H, W)` 로짓, `iou_predictions`
"""
from __future__ import annotations

import io
import logging
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image

_log = logging.getLogger("interior.ai.mobilesam")

_TARGET_LONG_SIDE = 1024
_SAM_MEAN = np.array([123.675, 116.28, 103.53], dtype=np.float32)
_SAM_STD = np.array([58.395, 57.12, 57.375], dtype=np.float32)


@dataclass
class SegmentResult:
    mask_png: bytes  # 원본 이미지와 같은 크기의 흑백 PNG (사물=255)
    score: float  # decoder 가 준 IoU 예측치 (참고용)


def _resize_longest_side(im: Image.Image, target: int) -> tuple[Image.Image, float]:
    w, h = im.size
    scale = target / max(w, h)
    new_w, new_h = round(w * scale), round(h * scale)
    return im.resize((new_w, new_h), Image.BILINEAR), scale


def _preprocess(image_bytes: bytes) -> tuple[np.ndarray, float, tuple[int, int], tuple[int, int]]:
    """SAM encoder 입력 텐서, 스케일, 리사이즈 후 크기, 원본 크기를 만든다."""
    with Image.open(io.BytesIO(image_bytes)) as im:
        im = im.convert("RGB")
        orig_size = im.size  # (w, h)
        resized, scale = _resize_longest_side(im, _TARGET_LONG_SIDE)
        arr = np.asarray(resized, dtype=np.float32)

    arr = (arr - _SAM_MEAN) / _SAM_STD
    padded = np.zeros((_TARGET_LONG_SIDE, _TARGET_LONG_SIDE, 3), dtype=np.float32)
    padded[: arr.shape[0], : arr.shape[1], :] = arr
    tensor = padded.transpose(2, 0, 1)[None, :, :, :]  # (1, 3, 1024, 1024)
    return tensor, scale, (resized.size[0], resized.size[1]), orig_size


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
        self._ensure_loaded()
        assert self._encoder is not None and self._decoder is not None

        tensor, scale, _resized_size, orig_size = _preprocess(image_bytes)
        (embedding,) = self._encoder.run(None, {self._encoder.get_inputs()[0].name: tensor})

        orig_w, orig_h = orig_size
        point = np.array([[x_norm * orig_w * scale, y_norm * orig_h * scale]], dtype=np.float32)
        point = point[None, :, :]  # (1, 1, 2)
        label = np.array([[1]], dtype=np.float32)  # 1 = 전경(포함) 점
        mask_input = np.zeros((1, 1, 256, 256), dtype=np.float32)
        has_mask_input = np.zeros(1, dtype=np.float32)
        orig_im_size = np.array([orig_h, orig_w], dtype=np.float32)

        decoder_inputs = {
            "image_embeddings": embedding,
            "point_coords": point,
            "point_labels": label,
            "mask_input": mask_input,
            "has_mask_input": has_mask_input,
            "orig_im_size": orig_im_size,
        }
        names = {i.name for i in self._decoder.get_inputs()}
        decoder_inputs = {k: v for k, v in decoder_inputs.items() if k in names}
        outputs = self._decoder.run(None, decoder_inputs)
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
