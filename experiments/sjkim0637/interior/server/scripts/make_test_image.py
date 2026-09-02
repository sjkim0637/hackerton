"""테스트용 거실 이미지 생성: 벽 + 바닥 + 벽에 걸린 TV(어두운 사각형).

실제 사진 대신 쓰는 합성 이미지다. mock 프로바이더가 TV 영역을 주변 색으로 덮으면
"TV 가 사라진 벽"처럼 보이도록 벽에 약한 노이즈를 넣는다.

단독 실행:
    python scripts/make_test_image.py [출력경로]
"""
from __future__ import annotations

import random
from pathlib import Path

from PIL import Image, ImageDraw

# TV 영역 — 전체 이미지 대비 정규화 [x, y, w, h]. E2E 스크립트가 그대로 재사용한다.
TV_BBOX: list[float] = [0.37, 0.22, 0.26, 0.28]
DEFAULT_SIZE = (1280, 853)


def build_living_room(path: str | Path, size: tuple[int, int] = DEFAULT_SIZE, seed: int = 7) -> Path:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    rng = random.Random(seed)
    w, h = size

    img = Image.new("RGB", size, (208, 202, 193))
    draw = ImageDraw.Draw(img)

    floor_top = int(h * 0.62)
    draw.rectangle([0, floor_top, w, h], fill=(150, 118, 86))
    for i in range(-4, 9):
        x = int(w * (0.5 + i * 0.14))
        draw.line([(w // 2, floor_top), (x, h)], fill=(135, 104, 74), width=2)
    draw.rectangle([0, floor_top - 6, w, floor_top], fill=(190, 185, 176))  # 걸레받이

    # 벽 질감 노이즈
    for _ in range(9000):
        x, y = rng.randint(0, w - 1), rng.randint(0, floor_top - 1)
        d = rng.randint(-8, 8)
        base = img.getpixel((x, y))
        img.putpixel((x, y), tuple(max(0, min(255, c + d)) for c in base))

    # TV
    x0, y0 = int(TV_BBOX[0] * w), int(TV_BBOX[1] * h)
    x1 = int((TV_BBOX[0] + TV_BBOX[2]) * w)
    y1 = int((TV_BBOX[1] + TV_BBOX[3]) * h)
    draw.rectangle([x0 - 6, y0 - 6, x1 + 6, y1 + 6], fill=(20, 20, 24))  # 베젤
    draw.rectangle([x0, y0, x1, y1], fill=(12, 14, 18))                   # 화면
    cx = (x0 + x1) // 2
    draw.line([(cx, y1), (cx, y1 + 40)], fill=(30, 30, 34), width=8)     # 스탠드

    img.save(path, format="JPEG", quality=90)
    return path


if __name__ == "__main__":
    import sys

    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "testdata" / "living_room.jpg"
    written = build_living_room(out)
    print(f"wrote {written} ({written.stat().st_size} bytes)")
