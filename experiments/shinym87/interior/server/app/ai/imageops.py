"""프로바이더 결과 이미지를 일정한 형식/크기로 맞추는 공통 로직."""
from __future__ import annotations

import io

from PIL import Image, ImageDraw, ImageFilter


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


def crop_normalized_jpeg(
    image_bytes: bytes,
    rect: tuple[float, float, float, float],
    *,
    quality: int = 90,
) -> bytes:
    """`rect`(정규화 x, y, w, h)로 이미지를 잘라 RGB JPEG 로 돌려준다.

    "제거된 사물"의 겉모습을 원본 키프레임에서 그대로 오려낸다 (배경 포함, AI 없음).
    좌표는 이미지 경계로 클램프하고 최소 1px 를 보장한다.
    """
    x, y, w, h = rect
    with Image.open(io.BytesIO(image_bytes)) as im:
        rgb = im.convert("RGB")
        width, height = rgb.size
        left = max(0, min(int(round(x * width)), width - 1))
        top = max(0, min(int(round(y * height)), height - 1))
        right = max(left + 1, min(int(round((x + w) * width)), width))
        bottom = max(top + 1, min(int(round((y + h) * height)), height))
        crop = rgb.crop((left, top, right, bottom))
        out = io.BytesIO()
        crop.save(out, format="JPEG", quality=quality)
        return out.getvalue()


def cutout_rgba_png(
    image_bytes: bytes,
    rect: tuple[float, float, float, float],
    *,
    mask_png: bytes | None = None,
    feather_frac: float = 0.05,
) -> bytes:
    """`rect`(정규화 x, y, w, h)로 사물을 오려 **투명 배경 RGBA PNG** 로 돌려준다.

    삭제한 사물을 다시 배치할 때 네모 크롭 + 흰/배경 모서리가 딸려오지 않게 한다.
    - `mask_png`(원본 크기 흑백, 사물=255)이 오면 그 실루엣을 alpha 로 쓴다(MobileSAM).
    - 없으면 **바깥 테두리만** 얇게 페더링한다. 안쪽 대부분은 완전 불투명이라
      사물이 반투명하게 뜨지 않는다(테두리 폭·블러를 절대 px 로 상한 → 작은 크롭에서도
      가운데가 흐려지지 않음).
    """
    x, y, w, h = rect
    with Image.open(io.BytesIO(image_bytes)) as im:
        rgb = im.convert("RGB")
        width, height = rgb.size
        left = max(0, min(int(round(x * width)), width - 1))
        top = max(0, min(int(round(y * height)), height - 1))
        right = max(left + 1, min(int(round((x + w) * width)), width))
        bottom = max(top + 1, min(int(round((y + h) * height)), height))
        crop = rgb.crop((left, top, right, bottom)).convert("RGBA")
        cw, ch = crop.size

        if mask_png is not None:
            with Image.open(io.BytesIO(mask_png)) as m:
                alpha = m.convert("L")
            if alpha.size != (width, height):
                alpha = alpha.resize((width, height), Image.BILINEAR)
            alpha = alpha.crop((left, top, right, bottom))
        else:
            # 테두리 폭: 짧은 변의 feather_frac, 단 4~20px 로 상한(작은 크롭에서 폭발 방지).
            band = min(max(4, round(min(cw, ch) * max(0.0, feather_frac))), 20)
            # 불투명 사각형을 band 만큼만 안쪽으로 넣고, 블러는 그보다 작게(band*0.5) 준다.
            # → 가운데는 255 그대로, 바깥 ~1.5*band px 만 부드럽게 사라진다.
            inset = max(1, band // 2)
            alpha = Image.new("L", (cw, ch), 0)
            ImageDraw.Draw(alpha).rectangle(
                [inset, inset, cw - 1 - inset, ch - 1 - inset], fill=255
            )
            alpha = alpha.filter(ImageFilter.GaussianBlur(band * 0.5))

        crop.putalpha(alpha)
        out = io.BytesIO()
        crop.save(out, format="PNG")
        return out.getvalue()


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
