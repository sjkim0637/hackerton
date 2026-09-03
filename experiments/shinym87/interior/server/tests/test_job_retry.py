"""AI 호출 실패 시 자동 재시도 (_run_job) 검증."""
import io
import json

import pytest
from PIL import Image

from app.ai.base import ProviderError, RemoveObjectProvider, RemoveResult
from app.config import get_settings
from app.deps import get_store

W, H = 320, 240


def _jpeg() -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (W, H), (100, 120, 140)).save(buf, format="JPEG")
    return buf.getvalue()


def _meta() -> dict:
    return {
        "imageSize": {"width": W, "height": H},
        "cameraIntrinsics": {"fx": 500.0, "fy": 500.0, "cx": W / 2, "cy": H / 2},
        "worldToCamera": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1],
    }


class _FlakyProvider(RemoveObjectProvider):
    """첫 호출은 일시적 오류, 두 번째부터 성공."""

    name = "flaky"

    def __init__(self) -> None:
        self.calls = 0

    def remove_object(self, *, image_bytes, region, object_type, prompt) -> RemoveResult:
        self.calls += 1
        if self.calls == 1:
            raise ProviderError("일시적 네트워크 오류", retryable=True)
        return RemoveResult(image_bytes=_jpeg(), changed_region={"type": "bbox", "rect": region["rect"]})


class _AlwaysAuthFail(RemoveObjectProvider):
    name = "authfail"

    def __init__(self) -> None:
        self.calls = 0

    def remove_object(self, *, image_bytes, region, object_type, prompt) -> RemoveResult:
        self.calls += 1
        raise ProviderError("인증 실패 (403)", retryable=False)


def _scene_and_keyframe(client) -> tuple[str, str]:
    scene_id = client.post("/scenes", json={"device": "t"}).json()["scene_id"]
    kf = client.post(
        f"/scenes/{scene_id}/keyframes",
        files={"image": ("kf.jpg", _jpeg(), "image/jpeg")},
        data={"meta": json.dumps(_meta())},
    ).json()["keyframe_id"]
    return scene_id, kf


@pytest.fixture(autouse=True)
def _fast_backoff(monkeypatch):
    monkeypatch.setattr(get_settings(), "ai_retry_backoff_seconds", 0.0)
    monkeypatch.setattr(get_settings(), "ai_max_retries", 1)


def test_retryable_error_is_retried_once_then_succeeds(client, monkeypatch):
    provider = _FlakyProvider()
    monkeypatch.setattr("app.routers.scenes.get_provider", lambda: provider)

    scene_id, kf = _scene_and_keyframe(client)
    job_id = client.post(
        f"/scenes/{scene_id}/remove-object",
        json={"keyframe_id": kf, "object_type": "tv",
              "target": {"type": "bbox", "rect": [0.3, 0.3, 0.3, 0.3]}},
    ).json()["job_id"]

    job = client.get(f"/scenes/{scene_id}/jobs/{job_id}").json()
    assert job["status"] == "done", job
    assert provider.calls == 2  # 1회 실패 + 1회 재시도 성공
    # AI 호출 누적은 시도 횟수만큼 (재시도도 비용 발생)
    assert get_store().get_scene(scene_id)["ai_calls"] == 2


def test_non_retryable_error_fails_without_retry(client, monkeypatch):
    provider = _AlwaysAuthFail()
    monkeypatch.setattr("app.routers.scenes.get_provider", lambda: provider)

    scene_id, kf = _scene_and_keyframe(client)
    job_id = client.post(
        f"/scenes/{scene_id}/remove-object",
        json={"keyframe_id": kf, "object_type": "tv",
              "target": {"type": "bbox", "rect": [0.3, 0.3, 0.3, 0.3]}},
    ).json()["job_id"]

    job = client.get(f"/scenes/{scene_id}/jobs/{job_id}").json()
    assert job["status"] == "failed"
    assert "인증 실패" in job["error"]
    assert provider.calls == 1  # 재시도 안 함
