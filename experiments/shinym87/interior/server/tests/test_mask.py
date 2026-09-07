"""mask.py: mask 타입 region 의 실제 바운딩 박스 계산과 정밀 마스크 페더링."""
import base64
import io

from PIL import Image, ImageDraw

from app.ai.mask import region_bbox, region_to_mask_png


def _mask_png_b64(width: int, height: int, box: tuple[int, int, int, int]) -> str:
    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rectangle(box, fill=255)
    buf = io.BytesIO()
    mask.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode("ascii")


def _jpeg(width: int, height: int) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (width, height), (100, 110, 120)).save(buf, format="JPEG")
    return buf.getvalue()


def test_region_bbox_reads_actual_mask_pixels_not_center_approx():
    width, height = 400, 300
    # PIL 의 rectangle() 은 좌표를 포함(inclusive)해서 그리므로 흰 영역은
    # 열 40..140, 행 30..120 (각각 101px) 이고, Image.getbbox() 의 right/bottom 은
    # 마지막 non-zero 픽셀 다음 인덱스(141, 121)를 돌려준다.
    png_b64 = _mask_png_b64(width, height, (40, 30, 140, 120))

    region = {"type": "mask", "png": png_b64, "size": {"width": width, "height": height}}
    x, y, w, h = region_bbox(region)

    assert x == 40 / width
    assert y == 30 / height
    assert w == 101 / width
    assert h == 91 / height


def test_region_bbox_falls_back_to_center_half_on_broken_png():
    region = {"type": "mask", "png": "not-a-real-png", "size": {"width": 10, "height": 10}}
    x, y, w, h = region_bbox(region)
    assert (x, y, w, h) == (0.25, 0.25, 0.5, 0.5)


def test_region_bbox_point_type_gives_small_box_around_point():
    region = {"type": "point", "point": [0.5, 0.5]}
    x, y, w, h = region_bbox(region)
    assert 0.3 < x + w / 2 < 0.7
    assert 0.3 < y + h / 2 < 0.7
    assert w == 0.2 and h == 0.2


def test_region_to_mask_png_from_mask_region_keeps_object_opaque_and_soft_edges():
    width, height = 320, 240
    box = (60, 50, 200, 160)
    png_b64 = _mask_png_b64(width, height, box)
    region = {"type": "mask", "png": png_b64, "size": {"width": width, "height": height}}

    out = region_to_mask_png(_jpeg(width, height), region)
    mask = Image.open(io.BytesIO(out)).convert("L")

    assert mask.size == (width, height)
    # 사물 중심은 여전히 완전 불투명(255에 가까움).
    cx, cy = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
    assert mask.getpixel((cx, cy)) > 240
    # 사물에서 멀리 떨어진 배경은 여전히 0에 가까움(과도하게 안 번짐).
    assert mask.getpixel((5, 5)) < 15


def test_region_to_mask_png_resizes_mask_that_does_not_match_image_size():
    # MobileSAM 마스크가 리사이즈된 이미지 기준이라 원본과 크기가 다를 수 있는 상황을 가정.
    mask_w, mask_h = 100, 80
    png_b64 = _mask_png_b64(mask_w, mask_h, (10, 10, 60, 60))
    region = {"type": "mask", "png": png_b64, "size": {"width": mask_w, "height": mask_h}}

    width, height = 320, 240
    out = region_to_mask_png(_jpeg(width, height), region)
    mask = Image.open(io.BytesIO(out)).convert("L")
    assert mask.size == (width, height)
