"""프로바이더 결과 이미지를 일정한 형식/크기로 맞추는 공통 로직."""
from __future__ import annotations

import io

from PIL import Image


def ensure_jpeg_size(image_bytes: bytes, size: tuple[int, int], quality: int = 90) -> bytes:
    """`image_bytes` 를 RGB JPEG 로, 정확히 `size`(width, height) 해상도로 맞춰 돌려준다.

    외부 AI(예: Gemini)가 원본과 다른 해상도의 이미지를 돌려줘도 여기서 원본 크기로
    강제 리사이즈한다. mock 처럼 이미 같은 크기면 재인코딩만 한다.
    """
    with Image.open(io.BytesIO(image_bytes)) as im:
        rgb = im.convert("RGB")
        if rgb.size != tuple(size):
            rgb = rgb.resize(size, Image.LANCZOS)
        out = io.BytesIO()
        rgb.save(out, format="JPEG", quality=quality)
        return out.getvalue()


def image_size(image_bytes: bytes) -> tuple[int, int]:
    with Image.open(io.BytesIO(image_bytes)) as im:
        return im.size
