"""색감 보정(match_to_source) + 이상 결과 감지(check_result_anomaly)."""
import io

from PIL import Image, ImageEnhance, ImageStat

from app.ai.colormatch import check_result_anomaly, match_to_source

W, H = 400, 300
REGION = {"type": "bbox", "rect": [0.35, 0.35, 0.3, 0.3]}  # 가운데


def _bytes(img: Image.Image) -> bytes:
    b = io.BytesIO()
    img.save(b, format="JPEG", quality=95)
    return b.getvalue()


def _base() -> Image.Image:
    im = Image.new("RGB", (W, H), (130, 130, 130))
    for x in range(W // 2, W):
        for y in range(H):
            im.putpixel((x, y), (110, 140, 170))
    return im


def _with_center_edit(src: Image.Image, color=(200, 200, 200)) -> Image.Image:
    res = src.copy()
    for x in range(140, 260):
        for y in range(100, 200):
            res.putpixel((x, y), color)
    return res


# --------------------------------------------------------------- match_to_source

def test_match_noop_when_identical_outside():
    src = _base()
    res = _with_center_edit(src)
    out = match_to_source(_bytes(res), _bytes(src), REGION)
    assert out.applied is False
    assert all(abs(g - 1.0) < 0.05 for g in out.gains)


def test_match_corrects_global_dim():
    src = _base()
    dim = ImageEnhance.Brightness(src).enhance(0.75)  # 결과가 전체적으로 어둡다
    out = match_to_source(_bytes(dim), _bytes(src), REGION)
    assert out.applied is True
    assert all(g > 1.05 for g in out.gains)

    corrected = Image.open(io.BytesIO(out.image_bytes)).convert("L")
    src_l = src.convert("L")
    box = (0, 0, 120, 120)  # 마스크 밖
    assert abs(
        ImageStat.Stat(corrected.crop(box)).mean[0]
        - ImageStat.Stat(src_l.crop(box)).mean[0]
    ) < 12


def test_match_skips_when_selection_covers_most():
    src = _base()
    res = ImageEnhance.Brightness(src).enhance(0.7)
    big = {"type": "bbox", "rect": [0.02, 0.02, 0.96, 0.96]}
    out = match_to_source(_bytes(res), _bytes(src), big)
    assert out.applied is False


# ----------------------------------------------------------- check_result_anomaly

def test_anomaly_ok_for_local_edit():
    src = _base()
    res = _with_center_edit(src, (170, 170, 170))
    r = check_result_anomaly(_bytes(res), _bytes(src), REGION, raw_result_size=(W, H))
    assert r.severity == "ok", r


def test_anomaly_fail_when_whole_scene_changed():
    src = _base()
    res = Image.new("RGB", (W, H), (40, 90, 30))  # 완전히 다른 이미지
    r = check_result_anomaly(_bytes(res), _bytes(src), REGION, raw_result_size=(W, H))
    assert r.severity == "fail", r


def test_anomaly_fail_when_raw_result_tiny():
    src = _base()
    r = check_result_anomaly(_bytes(src), _bytes(src), REGION, raw_result_size=(32, 24))
    assert r.severity == "fail"


def test_anomaly_warn_on_moderate_color_shift():
    src = _base()
    res = ImageEnhance.Brightness(src).enhance(0.7)  # 전체가 꽤 어두움 (경고~실패 수준)
    r = check_result_anomaly(_bytes(res), _bytes(src), REGION, raw_result_size=(W, H))
    assert r.severity in ("warn", "fail"), r
    # 미미한 차이(예: 5%)는 ok 여야 한다 (색감 보정이 흡수)
    mild = ImageEnhance.Brightness(src).enhance(0.95)
    assert check_result_anomaly(_bytes(mild), _bytes(src), REGION,
                                raw_result_size=(W, H)).severity == "ok"
