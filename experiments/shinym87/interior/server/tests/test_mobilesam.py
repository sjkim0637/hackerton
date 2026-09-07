"""mobilesam.py: 모델 파일이 없을 때의 대체 경로(fallback)와 가용성 판정."""
import io
from pathlib import Path

from PIL import Image

from app.ai.mobilesam import (
    fallback_box_mask_png,
    get_segmenter,
    is_available,
    point_to_mask_png,
)


def _jpeg(width: int, height: int) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (width, height), (90, 100, 110)).save(buf, format="JPEG")
    return buf.getvalue()


def test_is_available_false_when_paths_missing():
    assert is_available(None, None) is False
    assert is_available(Path("no/such/encoder.onnx"), Path("no/such/decoder.onnx")) is False


def test_get_segmenter_none_when_files_do_not_exist():
    assert get_segmenter(Path("no/such/encoder.onnx"), Path("no/such/decoder.onnx")) is None


def test_point_to_mask_png_returns_none_without_mobilesam_configured():
    result = point_to_mask_png(
        _jpeg(200, 150), 0.5, 0.5, encoder_path=None, decoder_path=None,
    )
    assert result is None


def test_fallback_box_mask_png_centers_a_square_on_the_point():
    width, height = 400, 200
    out = fallback_box_mask_png(_jpeg(width, height), x_norm=0.75, y_norm=0.5, box_frac=0.2)
    mask = Image.open(io.BytesIO(out)).convert("L")

    assert mask.size == (width, height)
    # 점 위치는 흰색(사물로 표시)
    assert mask.getpixel((int(0.75 * width), int(0.5 * height))) == 255
    # 반대쪽 모서리는 검정(배경)
    assert mask.getpixel((2, 2)) == 0


def test_fallback_box_mask_png_clamps_to_image_bounds_near_edges():
    width, height = 100, 100
    # 점이 모서리 바로 근처라 정사각형이 이미지 밖으로 나가려는 경우.
    out = fallback_box_mask_png(_jpeg(width, height), x_norm=0.0, y_norm=0.0, box_frac=0.5)
    mask = Image.open(io.BytesIO(out)).convert("L")
    assert mask.size == (width, height)
    assert mask.getpixel((0, 0)) == 255
