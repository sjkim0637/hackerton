"""bbox/mask 영역을 Gemini 요청에 쓸 마스크 PNG 와 프롬프트 힌트로 바꾼다."""
from __future__ import annotations

import base64
import io

from PIL import Image, ImageDraw, ImageFilter


def _mask_png_bbox(png_b64: str) -> tuple[float, float, float, float] | None:
    """`MaskRegion.png`(base64) 를 디코드해 흰 픽셀의 실제 바운딩 박스를 정규화로 구한다.

    MobileSAM 처럼 정밀 마스크가 있는 경우(D5) 사각형 근사보다 정확한 크롭/색감 보정
    범위를 준다. 디코드 실패나 빈 마스크면 None(호출부가 기존 근사로 대체).
    """
    try:
        raw = base64.b64decode(png_b64)
        with Image.open(io.BytesIO(raw)) as im:
            mask = im.convert("L")
            bbox = mask.getbbox()
            if bbox is None:
                return None
            width, height = mask.size
            left, top, right, bottom = bbox
            return left / width, top / height, (right - left) / width, (bottom - top) / height
    except Exception:  # noqa: BLE001 - 손상된 입력은 근사로 대체
        return None


def region_bbox(region: dict) -> tuple[float, float, float, float]:
    """region 을 정규화 [x, y, w, h] 로 정리한다.

    - `bbox`: 그대로 사용.
    - `mask`: 실제 마스크 PNG 의 흰 픽셀 바운딩 박스(디코드 실패 시 가운데 절반 근사).
    - `point`: 여기까지 오면 안 됨 — `remove-object` 처리 전에 MobileSAM 이 `mask` 로
      바꿔야 한다(`app/routers/scenes.py`). 방어적으로 점 주변 작은 사각형만 근사한다.
    """
    kind = region.get("type")
    if kind == "bbox":
        x, y, w, h = region["rect"]
    elif kind == "mask":
        decoded = _mask_png_bbox(region["png"])
        x, y, w, h = decoded if decoded is not None else (0.25, 0.25, 0.5, 0.5)
    elif kind == "point":
        px, py = region["point"]
        x, y, w, h = px - 0.1, py - 0.1, 0.2, 0.2
    else:
        x, y, w, h = 0.25, 0.25, 0.5, 0.5
    x = min(max(x, 0.0), 1.0)
    y = min(max(y, 0.0), 1.0)
    w = min(max(w, 0.0), 1.0 - x)
    h = min(max(h, 0.0), 1.0 - y)
    return x, y, w, h


def _feather_precise_mask(
    png_b64: str, width: int, height: int, feather_frac: float
) -> bytes:
    """MobileSAM 처럼 이미 사물 윤곽을 아는 마스크는 사각형보다 가볍게 페더링한다.

    bbox 경로(`region_to_mask_png`)는 "잔털/그림자까지 덮으려" 사각형을 feather 의 2배
    만큼 부풀리지만, 여기서는 이미 정밀한 실루엣이 있으므로 팽창 없이 가장자리만
    블러해 anti-alias 정도로만 부드럽게 만든다(사물을 과도하게 깎아먹지 않기 위함).
    """
    raw = base64.b64decode(png_b64)
    with Image.open(io.BytesIO(raw)) as im:
        mask = im.convert("L")
        if mask.size != (width, height):
            mask = mask.resize((width, height), Image.NEAREST)

    bbox = mask.getbbox()
    if bbox is None:
        # 빈 마스크(마스킹 실패) — 안전하게 가운데 절반을 대체 영역으로.
        mask = Image.new("L", (width, height), 0)
        ImageDraw.Draw(mask).rectangle(
            [width * 0.25, height * 0.25, width * 0.75, height * 0.75], fill=255
        )
        bbox = mask.getbbox()

    box_w = max(1, bbox[2] - bbox[0])
    box_h = max(1, bbox[3] - bbox[1])
    feather = round(min(box_w, box_h) * max(0.0, feather_frac) * 0.5)
    feather = max(3, min(feather, round(min(width, height) * 0.06)))

    mask = mask.filter(ImageFilter.GaussianBlur(feather))
    out = io.BytesIO()
    mask.save(out, format="PNG")
    return out.getvalue()


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

    if region.get("type") == "mask":
        return _feather_precise_mask(region["png"], width, height, feather_frac)

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
