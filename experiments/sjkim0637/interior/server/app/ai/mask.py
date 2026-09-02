"""bbox/mask 영역을 Gemini 요청에 쓸 마스크 PNG 와 프롬프트 힌트로 바꾼다."""
from __future__ import annotations

import base64
import io

from PIL import Image, ImageDraw


def region_bbox(region: dict) -> tuple[float, float, float, float]:
    """region 을 정규화 [x, y, w, h] 로 정리한다. mask 타입이면 가운데 절반으로 근사."""
    if region.get("type") == "bbox":
        x, y, w, h = region["rect"]
    else:
        x, y, w, h = 0.25, 0.25, 0.5, 0.5
    x = min(max(x, 0.0), 1.0)
    y = min(max(y, 0.0), 1.0)
    w = min(max(w, 0.0), 1.0 - x)
    h = min(max(h, 0.0), 1.0 - y)
    return x, y, w, h


def region_to_mask_png(image_bytes: bytes, region: dict, feather: int = 6) -> bytes:
    """원본과 같은 크기의 흑백 마스크 PNG. 지울 영역이 흰색(255)."""
    with Image.open(io.BytesIO(image_bytes)) as im:
        width, height = im.size

    x, y, w, h = region_bbox(region)
    left = int(x * width)
    top = int(y * height)
    right = int((x + w) * width)
    bottom = int((y + h) * height)

    mask = Image.new("L", (width, height), 0)
    draw = ImageDraw.Draw(mask)
    draw.rectangle([left, top, right, bottom], fill=255)
    if feather > 0:
        from PIL import ImageFilter

        mask = mask.filter(ImageFilter.GaussianBlur(feather))

    out = io.BytesIO()
    mask.save(out, format="PNG")
    return out.getvalue()


def data_url_b64(raw: bytes) -> str:
    return base64.b64encode(raw).decode("ascii")


def location_hint(region: dict) -> str:
    """bbox 중심 위치를 사람이 읽는 표현으로 (프롬프트 보조용)."""
    x, y, w, h = region_bbox(region)
    cx, cy = x + w / 2, y + h / 2
    col = "left" if cx < 0.34 else "right" if cx > 0.66 else "center"
    row = "upper" if cy < 0.34 else "lower" if cy > 0.66 else "middle"
    return f"{row}-{col}"
