"""PHASE 4 사용자 3 — 재배치 상태 저장(POST/GET /placements) + 간단한 실행 취소."""
import json

from PIL import Image
import io

W, H = 320, 240


def _jpeg() -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (W, H), (120, 140, 160)).save(buf, format="JPEG")
    return buf.getvalue()


def _meta() -> dict:
    return {
        "imageSize": {"width": W, "height": H},
        "cameraIntrinsics": {"fx": 900.0, "fy": 900.0, "cx": W / 2, "cy": H / 2},
        "worldToCamera": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1],
    }


def _scene_with_kf(client) -> tuple[str, str]:
    scene_id = client.post("/scenes", json={"device": "t"}).json()["scene_id"]
    kf = client.post(
        f"/scenes/{scene_id}/keyframes",
        files={"image": ("kf.jpg", _jpeg(), "image/jpeg")},
        data={"meta": json.dumps(_meta())},
    ).json()["keyframe_id"]
    return scene_id, kf


def _placement_body(**over) -> dict:
    body = {
        "object_type": "tv",
        "pose": {"position": [0.1, 0.2, -0.5], "rotation": [0.0, 0.0, 0.0, 1.0]},
        "scale": 1.2,
        "rotation_deg": 15.0,
        "plane": "wall",
        "source_region": {"type": "bbox", "rect": [0.3, 0.3, 0.2, 0.2]},
    }
    body.update(over)
    return body


def _catalog_body(**over) -> dict:
    body = {
        "object_type": "tv",
        "pose": {"position": [0.0, 1.0, -1.2], "rotation": [0.0, 0.0, 0.0, 1.0]},
        "source": "catalog",
        "catalog_item_id": "cat_tv_wall-55",
        "scale": 1.0,
        "rotation_deg": 0.0,
        "plane": "wall",
    }
    body.update(over)
    return body


def test_create_and_list_placement(client):
    scene_id, _ = _scene_with_kf(client)
    r = client.post(f"/scenes/{scene_id}/placements", json=_placement_body())
    assert r.status_code == 201, r.text
    p = r.json()
    assert p["placement_id"].startswith("plc_")
    assert p["scene_id"] == scene_id
    assert p["object_type"] == "tv"
    assert p["source"] == "removed_object"          # 기본 출처
    assert p["catalog_item_id"] is None
    assert p["pose"]["position"] == [0.1, 0.2, -0.5]
    assert p["scale"] == 1.2 and p["rotation_deg"] == 15.0 and p["plane"] == "wall"
    assert p["source_region"]["rect"] == [0.3, 0.3, 0.2, 0.2]
    assert p["status"] == "active"

    rows = client.get(f"/scenes/{scene_id}/placements").json()
    assert [x["placement_id"] for x in rows] == [p["placement_id"]]


# ------------------------------------------------ PHASE 5: 카탈로그 출처

def test_catalog_placement_stores_source_and_item(client):
    scene_id, _ = _scene_with_kf(client)
    r = client.post(f"/scenes/{scene_id}/placements", json=_catalog_body(rotation_deg=30.0))
    assert r.status_code == 201, r.text
    p = r.json()
    assert p["source"] == "catalog"
    assert p["catalog_item_id"] == "cat_tv_wall-55"
    assert p["job_id"] is None and p["source_region"] is None
    assert p["rotation_deg"] == 30.0

    rows = client.get(f"/scenes/{scene_id}/placements").json()
    assert rows[0]["source"] == "catalog"
    assert rows[0]["catalog_item_id"] == "cat_tv_wall-55"


def test_catalog_placement_requires_item_id(client):
    scene_id, _ = _scene_with_kf(client)
    body = _catalog_body()
    body.pop("catalog_item_id")
    assert client.post(f"/scenes/{scene_id}/placements", json=body).status_code == 422


def test_catalog_placement_unknown_item_404(client):
    scene_id, _ = _scene_with_kf(client)
    r = client.post(f"/scenes/{scene_id}/placements", json=_catalog_body(catalog_item_id="cat_nope"))
    assert r.status_code == 404


def test_mixed_sources_listed_with_source_field(client):
    scene_id, _ = _scene_with_kf(client)
    client.post(f"/scenes/{scene_id}/placements", json=_placement_body())       # removed_object
    client.post(f"/scenes/{scene_id}/placements", json=_catalog_body())         # catalog
    rows = client.get(f"/scenes/{scene_id}/placements").json()
    assert [x["source"] for x in rows] == ["catalog", "removed_object"]   # 최신순
    by_src = {x["source"]: x for x in rows}
    assert by_src["removed_object"]["source_region"] is not None
    assert by_src["removed_object"]["catalog_item_id"] is None
    assert by_src["catalog"]["catalog_item_id"] == "cat_tv_wall-55"
    assert by_src["catalog"]["source_region"] is None


def test_placements_are_appended_newest_first(client):
    scene_id, _ = _scene_with_kf(client)
    ids = []
    for i in range(3):
        body = _placement_body(rotation_deg=float(i * 30))
        ids.append(client.post(f"/scenes/{scene_id}/placements", json=body).json()["placement_id"])

    rows = client.get(f"/scenes/{scene_id}/placements").json()
    assert [x["placement_id"] for x in rows] == list(reversed(ids))
    assert all(x["status"] == "active" for x in rows)


def test_undo_steps_back_through_history(client):
    scene_id, _ = _scene_with_kf(client)
    ids = [
        client.post(f"/scenes/{scene_id}/placements", json=_placement_body(rotation_deg=float(i)))
        .json()["placement_id"]
        for i in range(3)
    ]

    u = client.post(f"/scenes/{scene_id}/placements/undo")
    assert u.status_code == 200
    assert u.json()["placement_id"] == ids[-1]
    assert u.json()["status"] == "undone"

    active = client.get(f"/scenes/{scene_id}/placements").json()
    assert [x["placement_id"] for x in active] == [ids[1], ids[0]]

    # 취소분 포함 조회
    allrows = client.get(f"/scenes/{scene_id}/placements", params={"include_undone": "true"}).json()
    assert len(allrows) == 3
    assert {x["placement_id"]: x["status"] for x in allrows}[ids[-1]] == "undone"

    # 한 번 더 되짚기
    assert client.post(f"/scenes/{scene_id}/placements/undo").json()["placement_id"] == ids[1]
    assert [x["placement_id"] for x in client.get(f"/scenes/{scene_id}/placements").json()] == [ids[0]]


def test_undo_with_nothing_active_is_404(client):
    scene_id, _ = _scene_with_kf(client)
    client.post(f"/scenes/{scene_id}/placements", json=_placement_body())
    client.post(f"/scenes/{scene_id}/placements/undo")
    assert client.post(f"/scenes/{scene_id}/placements/undo").status_code == 404


def test_placement_links_to_job_and_rejects_foreign_job(client):
    scene_id, kf = _scene_with_kf(client)
    started = client.post(
        f"/scenes/{scene_id}/remove-object",
        json={"keyframe_id": kf, "object_type": "tv",
              "target": {"type": "bbox", "rect": [0.3, 0.3, 0.2, 0.2]}},
    )
    job_id = started.json()["job_id"]
    ok = client.post(f"/scenes/{scene_id}/placements", json=_placement_body(job_id=job_id))
    assert ok.status_code == 201 and ok.json()["job_id"] == job_id

    other_scene, _ = _scene_with_kf(client)
    bad = client.post(f"/scenes/{other_scene}/placements", json=_placement_body(job_id=job_id))
    assert bad.status_code == 404


def test_placements_missing_scene_404(client):
    assert client.get("/scenes/scene_nope/placements").status_code == 404
    assert client.post("/scenes/scene_nope/placements", json=_placement_body()).status_code == 404
