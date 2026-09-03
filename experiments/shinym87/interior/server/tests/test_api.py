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


def _new_scene_with_keyframe(client) -> tuple[str, str]:
    scene_id = client.post("/scenes", json={"device": "t"}).json()["scene_id"]
    meta = _meta()
    meta.pop("targetObject", None)  # 사물 정보는 remove-object 로만
    kf = client.post(
        f"/scenes/{scene_id}/keyframes",
        files={"image": ("kf.jpg", _jpeg(), "image/jpeg")},
        data={"meta": json.dumps(meta)},
    ).json()["keyframe_id"]
    return scene_id, kf


def test_remove_object_uses_selected_object_type(client):
    """앱이 보낸 objectType(tv 외)이 그대로 저장/처리된다."""
    scene_id, kf = _new_scene_with_keyframe(client)
    r = client.post(
        f"/scenes/{scene_id}/remove-object",
        json={"keyframe_id": kf, "object_type": "sofa",
              "target": {"type": "bbox", "rect": [0.2, 0.3, 0.4, 0.4]}},
    )
    assert r.status_code == 202
    assert client.get(f"/scenes/{scene_id}/jobs/{r.json()['job_id']}").json()["status"] == "done"
    objs = client.get(f"/scenes/{scene_id}/objects").json()
    assert objs[-1]["object_type"] == "sofa"


def test_object_type_aliases_are_normalized(client):
    """couch → sofa 로 정규화되어 저장된다."""
    scene_id, kf = _new_scene_with_keyframe(client)
    client.post(
        f"/scenes/{scene_id}/remove-object",
        json={"keyframe_id": kf, "object_type": "couch",
              "target": {"type": "bbox", "rect": [0.2, 0.3, 0.4, 0.4]}},
    )
    objs = client.get(f"/scenes/{scene_id}/objects").json()
    assert objs[-1]["object_type"] == "sofa"


def test_generic_object_type_is_accepted(client):
    """목록에 없는 소품: 'other' 는 그대로, 'cup' 은 other 로 정규화되어 job 이 done."""
    scene_id, kf = _new_scene_with_keyframe(client)
    r = client.post(
        f"/scenes/{scene_id}/remove-object",
        json={"keyframe_id": kf, "object_type": "other",
              "target": {"type": "bbox", "rect": [0.4, 0.4, 0.15, 0.15]}},
    )
    assert r.status_code == 202
    assert client.get(f"/scenes/{scene_id}/jobs/{r.json()['job_id']}").json()["status"] == "done"

    client.post(
        f"/scenes/{scene_id}/remove-object",
        json={"keyframe_id": kf, "object_type": "cup",
              "target": {"type": "bbox", "rect": [0.1, 0.1, 0.15, 0.15]}},
    )
    types = [o["object_type"] for o in client.get(f"/scenes/{scene_id}/objects").json()]
    assert types[-2:] == ["other", "other"]


def test_duplicate_request_reuses_job_and_no_extra_ai_call(client):
    """동일 요청 재호출 → 같은 job, AI 호출 수 증가 없음."""
    scene_id, kf = _new_scene_with_keyframe(client)
    payload = {"keyframe_id": kf, "object_type": "table",
               "target": {"type": "bbox", "rect": [0.25, 0.25, 0.3, 0.3]}}
    j1 = client.post(f"/scenes/{scene_id}/remove-object", json=payload).json()["job_id"]
    j2 = client.post(f"/scenes/{scene_id}/remove-object", json=payload).json()["job_id"]
    j3 = client.post(f"/scenes/{scene_id}/remove-object", json=payload).json()["job_id"]
    assert j1 == j2 == j3
    # objects 도 재요청마다 늘지 않는다 (첫 요청만 add_object)
    assert len(client.get(f"/scenes/{scene_id}/objects").json()) == 1


def test_catalog_endpoints(client):
    all_items = client.get("/catalog").json()
    assert any(i["id"] == "cat_sofa_nordic-3seat" for i in all_items)

    sofas = client.get("/catalog", params={"category": "sofa"}).json()
    assert sofas and all(i["category"] == "sofa" for i in sofas)

    one = client.get("/catalog/cat_sofa_nordic-3seat").json()
    assert one["anchor_hint"] == "floor"

    assert client.get("/catalog/nope").status_code == 404


def test_catalog_thumbnails_are_served(client):
    """모든 카탈로그 항목의 thumbnail 이 /assets 정적 서빙으로 실제 이미지를 준다."""
    items = client.get("/catalog").json()
    assert len(items) == 5
    for it in items:
        url = it["thumbnail"]
        assert url.startswith("/assets/furniture/")
        r = client.get(url)
        assert r.status_code == 200, f"{url} -> {r.status_code}"
        assert r.headers["content-type"] == "image/png"
        assert r.content[:8] == b"\x89PNG\r\n\x1a\n"  # 유효한 PNG 시그니처

    assert client.get("/assets/furniture/does-not-exist.png").status_code == 404


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
