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


def cap_jpeg_bytes(
    image_bytes: bytes,
    max_bytes: int,
    *,
    qualities: tuple[int, ...] = (82, 72, 62),
) -> tuple[bytes, dict]:
    """JPEG 용량이 `max_bytes` 를 넘으면 품질을 낮춰 재인코딩한다.

    폰 다운로드 속도용. **해상도는 그대로 두고 품질만** 낮춘다(결과 quad 텍스처가
    저해상도가 되지 않도록). 목표 이하로 못 줄이면 마지막 시도 결과를 그대로 쓴다.

    반환: (바이트, 로그용 info dict). `info["capped"]` 가 False 면 원본을 그대로 돌려준 것.
    """
    n = len(image_bytes)
    if max_bytes <= 0 or n <= max_bytes:
        return image_bytes, {"capped": False, "bytes": n}

    with Image.open(io.BytesIO(image_bytes)) as im:
        rgb = im.convert("RGB")
    best = image_bytes
    for q in qualities:
        buf = io.BytesIO()
        rgb.save(buf, format="JPEG", quality=q, optimize=True)
        best = buf.getvalue()
        if len(best) <= max_bytes:
            return best, {
                "capped": True,
                "quality": q,
                "bytes": len(best),
                "original_bytes": n,
            }
    return best, {
        "capped": True,
        "quality": qualities[-1],
        "bytes": len(best),
        "original_bytes": n,
        "note": "max_bytes 미달(최저 품질에서도 초과)",
    }
