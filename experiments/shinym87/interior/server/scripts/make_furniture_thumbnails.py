"""가구 카탈로그 썸네일(간단한 일러스트) 생성.

무료 스톡 사진을 받는 대신, 라이선스/네트워크 걱정 없이 Pillow 로 카테고리별 라인아트
카드를 그린다. `catalog/furniture.json` 의 thumbnail 경로(`/assets/furniture/<종류>.png`)에
맞춰 `catalog/assets/furniture/{tv,sofa,table,chair,shelf}.png` 로 저장한다.

    python scripts/make_furniture_thumbnails.py
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT_DIR = Path(__file__).resolve().parent.parent / "catalog" / "assets" / "furniture"
SIZE = 640
CARD = (36, 36, SIZE - 36, SIZE - 36)
INK_BG = (250, 250, 252, 255)
BORDER = (210, 214, 222, 255)
TEXT = (60, 66, 78, 255)

PALETTE = {
    "tv": (46, 107, 230),
    "sofa": (198, 104, 59),
    "table": (184, 134, 63),
    "chair": (62, 155, 95),
    "shelf": (122, 79, 191),
}
NAMES = {
    "tv": "TV", "sofa": "소파", "table": "테이블", "chair": "의자", "shelf": "선반",
}


def _font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for name in ("malgun.ttf", "malgunbd.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def _canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(CARD, radius=40, fill=INK_BG, outline=BORDER, width=4)
    return img, d


def _label(d: ImageDraw.ImageDraw, name: str) -> None:
    font = _font(46)
    tw = d.textlength(name, font=font)
    d.text(((SIZE - tw) / 2, SIZE - 116), name, font=font, fill=TEXT)


def draw_tv(d: ImageDraw.ImageDraw, c: tuple[int, int, int]) -> None:
    d.rounded_rectangle((150, 150, 490, 360), radius=18, outline=c, width=14)
    d.rounded_rectangle((178, 178, 462, 332), radius=10, fill=(*c, 45))
    d.line((320, 360, 320, 400), fill=c, width=14)
    d.line((250, 402, 390, 402), fill=c, width=14)


def draw_sofa(d: ImageDraw.ImageDraw, c: tuple[int, int, int]) -> None:
    d.rounded_rectangle((150, 250, 490, 330), radius=26, outline=c, width=14)   # backrest
    d.rounded_rectangle((150, 300, 490, 400), radius=24, fill=(*c, 40), outline=c, width=14)  # seat
    d.rounded_rectangle((140, 285, 190, 405), radius=22, outline=c, width=14)   # left arm
    d.rounded_rectangle((450, 285, 500, 405), radius=22, outline=c, width=14)   # right arm
    for x in (168, 462):
        d.line((x, 405, x, 445), fill=c, width=14)


def draw_table(d: ImageDraw.ImageDraw, c: tuple[int, int, int]) -> None:
    d.rounded_rectangle((150, 250, 490, 300), radius=16, fill=(*c, 45), outline=c, width=14)
    for x in (185, 455):
        d.line((x, 300, x, 430), fill=c, width=14)
    d.line((215, 300, 215, 430), fill=c, width=14)
    d.line((425, 300, 425, 430), fill=c, width=14)


def draw_chair(d: ImageDraw.ImageDraw, c: tuple[int, int, int]) -> None:
    # 측면 프로필
    d.line((250, 170, 250, 330), fill=c, width=16)         # backrest post
    d.line((250, 178, 300, 178), fill=c, width=14)          # top rail
    d.rounded_rectangle((250, 320, 430, 350), radius=8, fill=(*c, 45), outline=c, width=14)  # seat
    d.line((260, 350, 260, 450), fill=c, width=16)          # front-ish leg
    d.line((418, 350, 418, 450), fill=c, width=16)          # back leg


def draw_shelf(d: ImageDraw.ImageDraw, c: tuple[int, int, int]) -> None:
    for x in (200, 440):
        d.line((x, 150, x, 430), fill=c, width=16)
    for y in (170, 290, 410):
        d.rounded_rectangle((190, y, 450, y + 20), radius=6, fill=(*c, 40), outline=c, width=12)
    # 윗 칸에 책 몇 권
    for i, x in enumerate((215, 235, 255, 280)):
        d.rectangle((x, 210 - i % 2 * 8, x + 14, 288), outline=c, width=6)


DRAWERS = {
    "tv": draw_tv, "sofa": draw_sofa, "table": draw_table,
    "chair": draw_chair, "shelf": draw_shelf,
}


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for key, drawer in DRAWERS.items():
        img, d = _canvas()
        drawer(d, PALETTE[key])
        _label(d, NAMES[key])
        path = OUT_DIR / f"{key}.png"
        img.save(path, format="PNG")
        print(f"  {path.relative_to(OUT_DIR.parents[2])}  ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
