"""bbox/mask 영역을 Gemini 요청에 쓸 마스크 PNG 와 프롬프트 힌트로 바꾼다."""
from __future__ import annotations

import base64
import io

from PIL import Image, ImageDraw, ImageFilter


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


def region_to_mask_png(image_bytes: bytes, region: dict, feather_frac: float = 0.08) -> bytes:
    """원본과 같은 크기의 흑백 마스크 PNG. 지울 영역이 흰색(255).

    가장자리를 부드럽게 페더링한다:
    - feather 반경 = 대상 사각형의 짧은 변 * `feather_frac` (최소 8px, 이미지 12% 상한).
      → 큰 사물은 넓게, 작은 사물은 좁게. 픽셀 고정값이 아니라 비율이라 4K 사진에서도
        각지지 않는다.
    - 블러가 안쪽을 깎아먹어도 원래 bbox 가 완전 불투명하도록, 그린 사각형을 feather 의
      2배만큼 키운 뒤 블러한다(사물의 잔털/그림자까지 덮는 효과). 사물이 살짝 남는 것보다
      배경을 조금 더 칠하는 편이 결과가 낫다.
    """
    with Image.open(io.BytesIO(image_bytes)) as im:
        width, height = im.size

    x, y, w, h = region_bbox(region)
    left = int(x * width)
    top = int(y * height)
    right = int((x + w) * width)
    bottom = int((y + h) * height)

    box_w = max(1, right - left)
    box_h = max(1, bottom - top)
    feather = round(min(box_w, box_h) * max(0.0, feather_frac))
    feather = max(8, min(feather, round(min(width, height) * 0.12)))

    pad = feather * 2
    gl = max(0, left - pad)
    gt = max(0, top - pad)
    gr = min(width, right + pad)
    gb = min(height, bottom + pad)

    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rectangle([gl, gt, gr, gb], fill=255)
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
