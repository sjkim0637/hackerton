"""external(Gemini) 프로바이더: 실제 키 없이 요청 조립 / 응답 파싱 / 에러 처리 검증.

httpx.post 를 가짜로 바꿔치기해서 네트워크 없이 상태 코드별 동작을 확인한다.
"""
import base64
import io

import httpx
import pytest
from PIL import Image

from app.ai.base import ProviderError, ProviderNotConfigured
from app.ai.external import ExternalRemoveObjectProvider
from app.ai.mask import region_to_mask_png

REGION = {"type": "bbox", "rect": [0.3, 0.3, 0.25, 0.2]}


def _jpeg(color=(120, 130, 140)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (320, 240), color).save(buf, format="JPEG")
    return buf.getvalue()


def _png_b64() -> str:
    buf = io.BytesIO()
    Image.new("RGB", (320, 240), (200, 200, 200)).save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode("ascii")


def _provider() -> ExternalRemoveObjectProvider:
    return ExternalRemoveObjectProvider(api_key="test-key", model="gemini-x", timeout=5)


def _call(provider):
    return provider.remove_object(
        image_bytes=_jpeg(), region=REGION, object_type="tv", prompt="",
    )


def test_missing_key_raises_not_configured():
    provider = ExternalRemoveObjectProvider(api_key="")
    with pytest.raises(ProviderNotConfigured):
        _call(provider)


def test_success_returns_jpeg(monkeypatch):
    captured = {}

    def fake_post(url, headers=None, json=None, timeout=None):
        captured["url"] = url
        captured["headers"] = headers
        captured["json"] = json
        payload = {
            "candidates": [
                {"content": {"parts": [{"inlineData": {"mimeType": "image/png", "data": _png_b64()}}]}}
            ]
        }
        return httpx.Response(200, json=payload, request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx, "post", fake_post)
    result = _call(_provider())

    assert result.image_bytes[:2] == b"\xff\xd8"  # JPEG magic (external 은 항상 JPEG 로 재인코딩)
    assert result.changed_region["type"] == "bbox"
    # 요청에 x-goog-api-key 헤더와 이미지+마스크 파트가 담긴다
    assert captured["headers"]["x-goog-api-key"] == "test-key"
    assert "gemini-x:generateContent" in captured["url"]
    parts = captured["json"]["contents"][0]["parts"]
    assert sum(1 for p in parts if "inline_data" in p) == 2


def test_rate_limit_maps_to_provider_error(monkeypatch):
    def fake_post(url, headers=None, json=None, timeout=None):
        return httpx.Response(
            429, json={"error": {"message": "quota exceeded"}},
            request=httpx.Request("POST", url),
        )

    monkeypatch.setattr(httpx, "post", fake_post)
    with pytest.raises(ProviderError) as exc:
        _call(_provider())
    assert "429" in str(exc.value)
    assert exc.value.retryable is True  # 한도 초과 = 잠깐 뒤 재시도 대상


def test_auth_error_maps_to_provider_error(monkeypatch):
    def fake_post(url, headers=None, json=None, timeout=None):
        return httpx.Response(403, json={"error": {"message": "invalid key"}},
                              request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx, "post", fake_post)
    with pytest.raises(ProviderError) as exc:
        _call(_provider())
    assert "403" in str(exc.value)
    assert exc.value.retryable is False  # 인증 오류는 재시도해도 소용 없음


def test_server_error_is_retryable(monkeypatch):
    def fake_post(url, headers=None, json=None, timeout=None):
        return httpx.Response(503, json={"error": {"message": "unavailable"}},
                              request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx, "post", fake_post)
    with pytest.raises(ProviderError) as exc:
        _call(_provider())
    assert exc.value.retryable is True


def test_network_error_wrapped(monkeypatch):
    def fake_post(url, headers=None, json=None, timeout=None):
        raise httpx.ConnectError("connection refused")

    monkeypatch.setattr(httpx, "post", fake_post)
    with pytest.raises(ProviderError) as exc:
        _call(_provider())
    assert "네트워크" in str(exc.value)
    assert exc.value.retryable is True


def test_timeout_wrapped(monkeypatch):
    def fake_post(url, headers=None, json=None, timeout=None):
        raise httpx.ReadTimeout("timed out")

    monkeypatch.setattr(httpx, "post", fake_post)
    with pytest.raises(ProviderError) as exc:
        _call(_provider())
    assert "시간 초과" in str(exc.value)
    assert exc.value.retryable is True


def test_response_without_image_raises(monkeypatch):
    def fake_post(url, headers=None, json=None, timeout=None):
        payload = {
            "candidates": [
                {"content": {"parts": [{"text": "I cannot edit this image."}]},
                 "finishReason": "STOP"}
            ]
        }
        return httpx.Response(200, json=payload, request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx, "post", fake_post)
    with pytest.raises(ProviderError) as exc:
        _call(_provider())
    assert "이미지가 없" in str(exc.value)


def test_prompt_is_object_type_aware():
    r = {"type": "bbox", "rect": [0.3, 0.4, 0.3, 0.3]}
    tv = ExternalRemoveObjectProvider._build_prompt("tv", r, "")
    sofa = ExternalRemoveObjectProvider._build_prompt("sofa", r, "")
    table = ExternalRemoveObjectProvider._build_prompt("table", r, "")
    couch = ExternalRemoveObjectProvider._build_prompt("couch", r, "")  # alias

    assert "wall" in tv and "bracket" in tv and "the tv" in tv
    assert "floor" in sofa and ("skirting" in sofa or "baseboard" in sofa) and "the sofa" in sofa
    assert "continuous" in table and "floor" in table
    # 별칭은 sofa 힌트를 쓰되 라벨은 입력한 단어 유지
    assert "the couch" in couch and "skirting" in couch


def test_mask_is_feathered_and_larger_than_rect(monkeypatch):
    buf = io.BytesIO()
    Image.new("RGB", (1200, 900), (128, 128, 128)).save(buf, format="JPEG")
    region = {"type": "bbox", "rect": [0.4, 0.4, 0.2, 0.2]}  # 240x180 px 중앙

    png = region_to_mask_png(buf.getvalue(), region)
    mask = Image.open(io.BytesIO(png)).convert("L")
    assert mask.size == (1200, 900)

    # 중앙은 완전 불투명
    assert mask.getpixel((600, 450)) == 255
    # rect 경계 바로 바깥(패딩+페더 영역)은 0도 255도 아닌 중간값 → 부드러운 전환
    edge_val = mask.getpixel((int(0.6 * 1200) + 20, 450))
    assert 0 < edge_val < 255
    # 원래 rect 밖 먼 곳은 0
    assert mask.getpixel((100, 100)) == 0


def test_blocked_prompt_raises(monkeypatch):
    def fake_post(url, headers=None, json=None, timeout=None):
        return httpx.Response(200, json={"promptFeedback": {"blockReason": "SAFETY"}},
                              request=httpx.Request("POST", url))

    monkeypatch.setattr(httpx, "post", fake_post)
    with pytest.raises(ProviderError) as exc:
        _call(_provider())
    assert "차단" in str(exc.value)
