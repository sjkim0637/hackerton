"""AI 결과 이미지의 색감 보정 + 명백한 이상 결과 감지.

기준은 **마스크 밖 영역**이다. AI 는 마스크(선택 영역) 밖을 건드리지 않아야 하므로,
그 영역의 원본↔결과 차이로 (a) 전역 노출/색온도 이동을 보정하고 (b) 장면 전체가
바뀐 것 같은 이상 결과를 잡는다. 완벽한 감지가 아니라 "명백한" 케이스만 본다.
"""
from __future__ import annotations

import io
from dataclasses import dataclass, field

from PIL import Image, ImageChops, ImageStat


def _bbox_rect_px(region: dict, w: int, h: int, margin_frac: float = 0.03) -> tuple[int, int, int, int]:
    """선택 영역 사각형(픽셀). AI 의 페더 편집이 새어나올 수 있어 조금 넓게 잡는다."""
    if region.get("type") == "bbox":
        rx, ry, rw, rh = region["rect"]
    else:
        rx, ry, rw, rh = 0.25, 0.25, 0.5, 0.5
    left = max(0, int((rx - margin_frac) * w))
    top = max(0, int((ry - margin_frac) * h))
    right = min(w, int((rx + rw + margin_frac) * w))
    bottom = min(h, int((ry + rh + margin_frac) * h))
    if right <= left:
        right = min(w, left + 1)
    if bottom <= top:
        bottom = min(h, top + 1)
    return left, top, right, bottom


def _outside_sum_and_count(img: Image.Image, rect: tuple[int, int, int, int]) -> tuple[list[float], int]:
    """이미지 전체에서 rect 를 뺀 영역의 채널별 합과 픽셀 수."""
    total = ImageStat.Stat(img).sum
    inside = ImageStat.Stat(img.crop(rect)).sum
    w, h = img.size
    n_out = max(1, w * h - (rect[2] - rect[0]) * (rect[3] - rect[1]))
    return [t - i for t, i in zip(total, inside)], n_out


def _outside_means(img: Image.Image, rect: tuple[int, int, int, int]) -> list[float]:
    sums, n = _outside_sum_and_count(img, rect)
    return [s / n for s in sums]


# --------------------------------------------------------------------- 색감 보정

@dataclass
class ColorMatchResult:
    image_bytes: bytes
    applied: bool
    gains: list[float]  # [r, g, b]


def match_to_source(
    result_bytes: bytes,
    source_bytes: bytes,
    region: dict,
    *,
    min_gain: float = 0.7,
    max_gain: float = 1.4,
    deadband: float = 0.03,
    min_outside_frac: float = 0.15,
) -> ColorMatchResult:
    """결과 이미지를 원본의 (마스크 밖) 평균 밝기/색상에 맞춰 채널별 게인으로 보정한다.

    노출/화이트밸런스 보정 수준의 가벼운 후처리. 게인이 거의 1 이면(=차이 미미) 건너뛴다.
    선택 영역이 화면 대부분이라 바깥 표본이 부족하면 보정하지 않는다.
    """
    res = Image.open(io.BytesIO(result_bytes)).convert("RGB")
    src = Image.open(io.BytesIO(source_bytes)).convert("RGB")
    if src.size != res.size:
        src = src.resize(res.size)
    w, h = res.size
    rect = _bbox_rect_px(region, w, h)

    src_sums, n_out = _outside_sum_and_count(src, rect)
    res_sums, _ = _outside_sum_and_count(res, rect)
    if n_out < min_outside_frac * w * h:
        return ColorMatchResult(result_bytes, False, [1.0, 1.0, 1.0])

    gains: list[float] = []
    for s, r in zip(src_sums, res_sums):
        g = (s / r) if r > 1.0 else 1.0
        gains.append(min(max(g, min_gain), max_gain))

    if all(abs(g - 1.0) <= deadband for g in gains):
        return ColorMatchResult(result_bytes, False, gains)

    lut = bytes(
        min(255, round(i * gains[c]))
        for c in range(3)
        for i in range(256)
    )
    corrected = res.point(lut)
    out = io.BytesIO()
    corrected.save(out, format="JPEG", quality=92)
    return ColorMatchResult(out.getvalue(), True, gains)


# --------------------------------------------------------------- 이상 결과 감지

@dataclass
class AnomalyReport:
    severity: str  # "ok" | "warn" | "fail"
    reason: str = ""
    metrics: dict = field(default_factory=dict)


def check_result_anomaly(
    result_bytes: bytes,
    source_bytes: bytes,
    region: dict,
    *,
    raw_result_size: tuple[int, int] | None = None,
    warn_mad: float = 22.0,
    fail_mad: float = 55.0,
    warn_channel_shift: float = 18.0,
    fail_channel_shift: float = 45.0,
) -> AnomalyReport:
    """명백한 이상만 본다: 해상도 비정상 / 마스크 밖이 원본과 크게 다름(장면 전체가 바뀜)."""
    res = Image.open(io.BytesIO(result_bytes)).convert("RGB")
    src = Image.open(io.BytesIO(source_bytes)).convert("RGB")
    if src.size != res.size:
        src = src.resize(res.size)
    w, h = res.size

    # (a) 리사이즈 전 원본 결과 해상도 검사
    if raw_result_size:
        rw, rh = raw_result_size
        if rw < 64 or rh < 64:
            return AnomalyReport("fail", f"결과 해상도가 비정상적으로 작음 ({rw}x{rh})",
                                 {"raw_size": [rw, rh]})
        src_ar = w / h
        raw_ar = rw / max(1, rh)
        if abs(raw_ar - src_ar) / src_ar > 0.25:
            return AnomalyReport(
                "warn",
                f"결과 종횡비가 원본과 다름 (raw {rw}x{rh}, {raw_ar:.2f} vs 원본 {src_ar:.2f})",
                {"raw_size": [rw, rh]},
            )

    # (b) 마스크 밖 영역 비교 (다운스케일해서 빠르게)
    small = res.copy()
    small.thumbnail((384, 384))
    ss = src.resize(small.size)
    rect = _bbox_rect_px(region, *small.size)

    diff = ImageChops.difference(ss.convert("L"), small.convert("L"))
    d_total = ImageStat.Stat(diff).sum[0]
    d_inside = ImageStat.Stat(diff.crop(rect)).sum[0]
    n_out = max(1, small.size[0] * small.size[1] - (rect[2] - rect[0]) * (rect[3] - rect[1]))
    outside_mad = (d_total - d_inside) / n_out

    src_means = _outside_means(ss, rect)
    res_means = _outside_means(small, rect)
    channel_shift = max(abs(s - r) for s, r in zip(src_means, res_means))

    metrics = {"outside_mad": round(outside_mad, 1), "channel_shift": round(channel_shift, 1)}

    if outside_mad >= fail_mad or channel_shift >= fail_channel_shift:
        return AnomalyReport(
            "fail",
            f"마스크 밖 영역이 원본과 크게 다름 (MAD {outside_mad:.0f}, 채널편차 {channel_shift:.0f}) "
            "— AI 가 장면 전체를 바꿨거나 다른 이미지를 만든 것으로 보임",
            metrics,
        )
    if outside_mad >= warn_mad or channel_shift >= warn_channel_shift:
        return AnomalyReport(
            "warn",
            f"마스크 밖 영역이 원본과 다소 다름 (MAD {outside_mad:.0f}, 채널편차 {channel_shift:.0f})",
            metrics,
        )
    return AnomalyReport("ok", "", metrics)
