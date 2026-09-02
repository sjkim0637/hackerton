"""실제 외부 AI 없이 동작하는 mock. 대상 영역을 주변 색으로 덮어 "삭제"를 흉내낸다.

PHASE 1 목표는 "앱 → 서버 → AI → 앱" 흐름을 한 번 관통하는 것이므로, 복원 품질은
신경 쓰지 않는다. external 프로바이더가 준비되면 교체한다.
"""
from __future__ import annotations

import io

from PIL import Image, ImageFilter

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

        # 대상 영역 주변 색 평균으로 채우고, 원본을 흐리게 섞어 자연스러운 척.
        margin = 12
        border = img.crop(
            (
                max(0, x - margin),
                max(0, y - margin),
                min(width, x + w + margin),
                min(height, y + h + margin),
            )
        )
        avg_color = border.resize((1, 1)).getpixel((0, 0))
        fill = Image.new("RGB", (w, h), avg_color)
        blurred = img.crop((x, y, x + w, y + h)).filter(ImageFilter.GaussianBlur(20))
        patch = Image.blend(fill, blurred, 0.3)
        img.paste(patch, (x, y))

        out = io.BytesIO()
        img.save(out, format="JPEG", quality=88)
        return RemoveResult(
            image_bytes=out.getvalue(),
            changed_region={
                "type": "bbox",
                "rect": [x / width, y / height, w / width, h / height],
            },
        )
