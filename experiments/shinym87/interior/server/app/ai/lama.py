"""LaMa(ONNX) 로컬 인페인팅 프로바이더.

mock 은 "주변 색으로 사각형 덮기" 수준이라 실사용 품질이 아니다. Google Gemini(external)는
품질이 좋지만 API 키와 호출 비용이 필요하다. 이 프로바이더는 그 중간 — 로컬에서 무료로,
API 키 없이 실제 인페인팅 품질을 낸다.

모델: `Carve/LaMa-ONNX`(lama_fp32.onnx, ~198MB, 512×512 고정 입력). CPU 추론 기준
`real_living_room.jpg`(4032×3024)에서 약 5초 — TV+받침대 제거, 벽 자연 복원을 실측 확인했다
(Gemini 검증 결과와 비슷하거나 더 깨끗함, 콘센트 자국 정도만 옅게 남음).

모델 파일은 저장소에 커밋하지 않는다(MobileSAM과 동일 원칙) — `INTERIOR_LAMA_MODEL_PATH` 로
지정한다. mock 과 달리 이 프로바이더는 모델이 없으면 "낮은 품질로 조용히 대체"하지 않고
`ProviderNotConfigured` 를 던진다 — 품질 저하를 숨기지 않기 위해서다.
"""
from __future__ import annotations

import io
import logging
from pathlib import Path

import numpy as np
from PIL import Image

from .base import (
    ProviderError,
    ProviderNotConfigured,
    RemoveObjectProvider,
    RemoveResult,
)
from .mask import region_bbox, region_to_mask_png

_log = logging.getLogger("interior.ai.lama")

_SIZE = 512


def _letterbox_to_square(im: Image.Image, size: int) -> tuple[np.ndarray, float, int, int]:
    """종횡비를 유지하며 `size`x`size` 안에 맞추고 나머지는 0으로 채운다.

    반환: (size,size,3) uint8 배열, 스케일, 실제로 채워진 너비/높이(패딩 제외).
    """
    w, h = im.size
    scale = size / max(w, h)
    new_w, new_h = round(w * scale), round(h * scale)
    resized = im.resize((new_w, new_h), Image.BILINEAR)
    canvas = np.zeros((size, size, 3), dtype=np.uint8)
    canvas[:new_h, :new_w, :] = np.asarray(resized)
    return canvas, scale, new_w, new_h


def _mask_to_square(mask_png: bytes, size: int, scale: float, new_w: int, new_h: int) -> np.ndarray:
    with Image.open(io.BytesIO(mask_png)) as m:
        mask = m.convert("L")
        mw, mh = mask.size
        resized = mask.resize((round(mw * scale), round(mh * scale)), Image.NEAREST)

    canvas = np.zeros((size, size), dtype=np.float32)
    rw, rh = resized.size
    rw, rh = min(rw, new_w), min(rh, new_h)  # 안전하게 캔버스 안쪽으로만
    canvas[:rh, :rw] = np.asarray(resized, dtype=np.float32)[:rh, :rw] / 255.0
    return canvas


class LamaInpaintProvider(RemoveObjectProvider):
    name = "lama"

    def __init__(self, model_path: Path) -> None:
        if not model_path.is_file():
            raise ProviderNotConfigured(f"LaMa 모델 파일이 없습니다: {model_path}")
        self._model_path = model_path
        self._session = None

    def _ensure_loaded(self):
        if self._session is None:
            import onnxruntime as ort  # 지연 import: 미사용 환경에서 의존성 없이도 서버가 뜨게 함

            self._session = ort.InferenceSession(
                str(self._model_path), providers=["CPUExecutionProvider"]
            )
            _log.info("LaMa 모델 로드 완료: %s", self._model_path.name)
        return self._session

    def remove_object(
        self,
        *,
        image_bytes: bytes,
        region: dict,
        object_type: str,
        prompt: str,
    ) -> RemoveResult:
        session = self._ensure_loaded()

        with Image.open(io.BytesIO(image_bytes)) as im:
            im = im.convert("RGB")
            orig_w, orig_h = im.size
            square_img, scale, new_w, new_h = _letterbox_to_square(im, _SIZE)

        mask_png = region_to_mask_png(image_bytes, region)
        square_mask = _mask_to_square(mask_png, _SIZE, scale, new_w, new_h)

        image_tensor = (square_img.astype(np.float32) / 255.0).transpose(2, 0, 1)[None, :, :, :]
        mask_tensor = square_mask[None, None, :, :]

        try:
            (out,) = session.run(None, {"image": image_tensor, "mask": mask_tensor})
        except Exception as exc:
            raise ProviderError(f"LaMa 추론 실패: {exc}", retryable=False) from exc

        # 모델 출력은 0~255 범위로 나온다(0~1 아님, 실측 확인함).
        result_square = np.clip(out[0].transpose(1, 2, 0), 0, 255).astype(np.uint8)
        # 패딩 없이 실제로 채워졌던 영역만 잘라내고 원본 해상도로 복원.
        result_crop = result_square[:new_h, :new_w, :]
        result_img = Image.fromarray(result_crop).resize((orig_w, orig_h), Image.BILINEAR)

        out_buf = io.BytesIO()
        result_img.save(out_buf, format="JPEG", quality=92)

        x, y, w, h = region_bbox(region)
        return RemoveResult(
            image_bytes=out_buf.getvalue(),
            changed_region={"type": "bbox", "rect": [x, y, w, h]},
        )
