import io
import json

from PIL import Image

from app.ai import build_provider
from app.ai.base import ProviderNotConfigured
from app.config import Settings

W, H = 640, 480


def _jpeg() -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (W, H), (120, 140, 160)).save(buf, format="JPEG")
    return buf.getvalue()


def _meta() -> dict:
    return {
        "imageSize": {"width": W, "height": H},
        "cameraIntrinsics": {"fx": 1400.0, "fy": 1400.0, "cx": W / 2, "cy": H / 2},
        "worldToCamera": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1],
        "wallPlane": {
            "center": {"position": [0.0, 0.0, -2.0], "rotation": [0.0, 0.0, 0.0, 1.0]},
            "normal": [0.0, 0.0, 1.0],
            "extent": {"x": 3.0, "z": 2.4},
        },
        "targetObject": {
            "objectType": "tv",
            "region": {"type": "bbox", "rect": [0.3, 0.3, 0.25, 0.2]},
        },
    }


def test_health_reports_mock_provider(client):
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["ai"]["provider"] == "mock"
    assert body["ai"]["ready"] is True


def test_full_remove_object_flow(client):
    scene_id = client.post("/scenes", json={"device": "android"}).json()["scene_id"]
    assert scene_id.startswith("scene_")

    upload = client.post(
        f"/scenes/{scene_id}/keyframes",
        files={"image": ("kf.jpg", _jpeg(), "image/jpeg")},
        data={"meta": json.dumps(_meta())},
    )
    assert upload.status_code == 201, upload.text
    keyframe_id = upload.json()["keyframe_id"]

    # 키프레임 메타에 담긴 사물 정보가 저장된다 (형식만).
    objects = client.get(f"/scenes/{scene_id}/objects").json()
    assert len(objects) == 1
    assert objects[0]["object_type"] == "tv"

    started = client.post(
        f"/scenes/{scene_id}/remove-object",
        json={
            "keyframe_id": keyframe_id,
            "object_type": "tv",
            "target": {"type": "bbox", "rect": [0.3, 0.3, 0.25, 0.2]},
        },
    )
    assert started.status_code == 202, started.text
    job_id = started.json()["job_id"]

    # TestClient 는 BackgroundTasks 를 응답 직후 동기 실행하므로 이미 done 이어야 한다.
    job = client.get(f"/scenes/{scene_id}/jobs/{job_id}").json()
    assert job["status"] == "done", job
    assert job["result_image_url"].endswith(f"/results/{job_id}.jpg")
    assert job["changed_region"]["type"] == "bbox"

    img = client.get(job["result_image_url"])
    assert img.status_code == 200
    assert img.headers["content-type"] == "image/jpeg"

    # 같은 (keyframe, target) 재요청은 캐시된 job 을 그대로 돌려준다.
    again = client.post(
        f"/scenes/{scene_id}/remove-object",
        json={
            "keyframe_id": keyframe_id,
            "object_type": "tv",
            "target": {"type": "bbox", "rect": [0.3, 0.3, 0.25, 0.2]},
        },
    )
    assert again.json()["job_id"] == job_id


def test_catalog_endpoints(client):
    all_items = client.get("/catalog").json()
    assert any(i["id"] == "cat_sofa_nordic-3seat" for i in all_items)

    sofas = client.get("/catalog", params={"category": "sofa"}).json()
    assert sofas and all(i["category"] == "sofa" for i in sofas)

    one = client.get("/catalog/cat_sofa_nordic-3seat").json()
    assert one["anchor_hint"] == "floor"

    assert client.get("/catalog/nope").status_code == 404


def test_missing_scene_is_404(client):
    assert client.get("/scenes/scene_deadbeef").status_code == 404


def test_external_provider_without_key_raises():
    provider = build_provider(
        Settings(ai_provider="external", ai_api_key="", ai_base_url="", ai_model="")
    )
    assert provider.name == "external"
    try:
        provider.remove_object(
            image_bytes=_jpeg(),
            region={"type": "bbox", "rect": [0.1, 0.1, 0.2, 0.2]},
            object_type="tv",
            prompt="x",
        )
    except ProviderNotConfigured:
        pass
    else:  # pragma: no cover
        raise AssertionError("빈 키로 external 호출 시 ProviderNotConfigured 가 나야 한다")
