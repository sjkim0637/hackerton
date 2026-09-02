"""실제 외부 AI 없이 동작하는 mock. 대상 영역을 주변 벽 색으로 덮어 "삭제"를 흉내낸다.

PHASE 1 목표는 "앱 → 서버 → AI → 앱" 흐름을 한 번 관통하는 것이므로, 복원 품질은
신경 쓰지 않는다. external 프로바이더가 준비되면 교체한다.
"""
from __future__ import annotations

import io

from PIL import Image, ImageDraw, ImageFilter

from .base import RemoveObjectProvider, RemoveResult


def _bbox_px(region: dict, width: int, height: int) -> tuple[int, int, int, int]:
    if region.get("type") == "bbox":
        rx, ry, rw, rh = region["rect"]
    else:  # mask 는 아직 mock 에서 세밀 처리 안 함 → 가운데 절반
        rx, ry, rw, rh = 0.25, 0.25, 0.5, 0.5
    x = max(0, min(int(rx * width), width - 1))
    y = max(0, min(int(ry * height), height - 1))
    w = max(1, min(int(rw * width), width - x))
    h = max(1, min(int(rh * height), height - y))
    return x, y, w, h


def _surrounding_color(img: Image.Image, box: tuple[int, int, int, int], margin: int) -> tuple[int, int, int]:
    """대상 사각형에서 margin~2*margin 만큼 떨어진 바깥 링만 표본해 평균색을 구한다.

    대상 바로 옆(사물 테두리/그림자)은 건너뛰고 조금 떨어진 배경(벽)만 본다.
    """
    x, y, w, h = box
    iw, ih = img.size
    inner = margin
    outer_m = margin * 2
    ix0, iy0 = max(0, x - inner), max(0, y - inner)
    ix1, iy1 = min(iw, x + w + inner), min(ih, y + h + inner)
    ox0, oy0 = max(0, x - outer_m), max(0, y - outer_m)
    ox1, oy1 = min(iw, x + w + outer_m), min(ih, y + h + outer_m)

    strips = [
        img.crop((ox0, oy0, ox1, iy0)),   # top ring
        img.crop((ox0, iy1, ox1, oy1)),   # bottom ring
        img.crop((ox0, oy0, ix0, oy1)),   # left ring
        img.crop((ix1, oy0, ox1, oy1)),   # right ring
    ]
    samples = [s.resize((1, 1)).getpixel((0, 0)) for s in strips if s.width and s.height]
    if not samples:
        return img.resize((1, 1)).getpixel((0, 0))
    return tuple(sum(channel) // len(samples) for channel in zip(*samples))


class MockRemoveObjectProvider(RemoveObjectProvider):
    name = "mock"

    def remove_object(
        self,
        *,
        image_bytes: bytes,
        region: dict,
        object_type: str,
        prompt: str,
    ) -> RemoveResult:
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        width, height = img.size
        x, y, w, h = _bbox_px(region, width, height)

        margin = max(16, int(0.04 * width))
        fill_color = _surrounding_color(img, (x, y, w, h), margin)

        # 사물 테두리/그림자까지 덮도록 대상 사각형을 살짝 넓혀서 칠한다.
        pad = max(4, margin // 3)
        px, py = max(0, x - pad), max(0, y - pad)
        pw = min(width, x + w + pad) - px
        ph = min(height, y + h + pad) - py

        patch = Image.new("RGB", (pw, ph), fill_color)

        # 가장자리만 부드럽게 이어 붙인다 (딱딱한 사각형 티 줄이기).
        feather = max(3, pad)
        mask = Image.new("L", (pw, ph), 255)
        ImageDraw.Draw(mask).rectangle([0, 0, pw - 1, ph - 1], outline=0, width=feather)
        mask = mask.filter(ImageFilter.GaussianBlur(feather))

        img.paste(patch, (px, py), mask)

        out = io.BytesIO()
        img.save(out, format="JPEG", quality=88)
        return RemoveResult(
            image_bytes=out.getvalue(),
            changed_region={
                "type": "bbox",
                "rect": [x / width, y / height, w / width, h / height],
            },
        )
