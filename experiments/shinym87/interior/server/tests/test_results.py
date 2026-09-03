"""PHASE 3 사용자 3 — 결과 버전 관리 / 임시 저장 정리 / 폰 전달 최적화."""
import io
import json
import time
from pathlib import Path

from PIL import Image

from app.ai.imageops import cap_jpeg_bytes, crop_normalized_jpeg
from app.cleanup import sweep_old_results
from app.config import get_settings
from app.deps import get_store

W, H = 320, 240


def _jpeg(color=(120, 140, 160)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (W, H), color).save(buf, format="JPEG")
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


def _remove(client, scene_id: str, kf: str, rect: list[float]) -> str:
    r = client.post(
        f"/scenes/{scene_id}/remove-object",
        json={"keyframe_id": kf, "object_type": "tv",
              "target": {"type": "bbox", "rect": rect}},
    )
    assert r.status_code == 202, r.text
    job_id = r.json()["job_id"]
    assert client.get(f"/scenes/{scene_id}/jobs/{job_id}").json()["status"] == "done"
    return job_id


# --------------------------------------------------------------- 1) 버전 목록 API

def test_list_results_keeps_every_job_as_version(client):
    scene_id, kf = _scene_with_kf(client)
    j1 = _remove(client, scene_id, kf, [0.10, 0.10, 0.20, 0.20])
    j2 = _remove(client, scene_id, kf, [0.50, 0.50, 0.20, 0.20])

    rows = client.get(f"/scenes/{scene_id}/results").json()
    assert [r["job_id"] for r in rows] == [j2, j1]  # 최신순
    for r in rows:
        assert r["status"] == "done"
        assert r["available"] is True
        assert r["size_bytes"] > 0
        assert r["result_image_url"].endswith(f"/results/{r['job_id']}.jpg")
        assert client.get(r["result_image_url"]).status_code == 200
        # 제거된 사물 크롭도 저장되어 URL 로 받을 수 있다.
        assert r["removed_object_image_url"].endswith(f"/results/{r['job_id']}_object.jpg")
        crop = client.get(r["removed_object_image_url"])
        assert crop.status_code == 200
        assert crop.headers["content-type"] == "image/jpeg"
        assert Image.open(io.BytesIO(crop.content)).size[0] > 0

    # 같은 요청 재호출은 새 버전을 만들지 않는다 (중복 방지).
    again = _remove(client, scene_id, kf, [0.50, 0.50, 0.20, 0.20])
    assert again == j2
    assert len(client.get(f"/scenes/{scene_id}/results").json()) == 2


def test_list_results_missing_scene_404(client):
    assert client.get("/scenes/scene_nope/results").status_code == 404


# ------------------------------------------------- 2) 임시 저장 정리 (개수/기간)

def test_old_results_pruned_by_count(client, monkeypatch):
    monkeypatch.setattr(get_settings(), "result_keep_per_scene", 2)
    scene_id, kf = _scene_with_kf(client)
    j1 = _remove(client, scene_id, kf, [0.10, 0.10, 0.15, 0.15])
    j2 = _remove(client, scene_id, kf, [0.30, 0.30, 0.15, 0.15])
    j3 = _remove(client, scene_id, kf, [0.60, 0.60, 0.15, 0.15])

    results_dir = get_settings().scenes_dir / scene_id / "results"
    # 메인 결과는 최근 2개만. 동반 크롭({job}_object.jpg)도 함께 정리된다.
    main = sorted(p.stem for p in results_dir.glob("*.jpg") if not p.stem.endswith("_object"))
    objs = sorted(p.stem for p in results_dir.glob("*_object.jpg"))
    assert main == sorted([j2, j3])
    assert objs == sorted([f"{j2}_object", f"{j3}_object"])

    # 가장 오래된 버전: 기록은 남고 파일만 없다.
    rows = {r["job_id"]: r for r in client.get(f"/scenes/{scene_id}/results").json()}
    assert rows[j1]["available"] is False
    assert rows[j1]["result_image_url"] is None
    assert rows[j1]["removed_object_image_url"] is None
    assert rows[j1]["size_bytes"] is None
    assert rows[j2]["available"] is True

    # 정리된 결과 이미지 요청은 410.
    assert client.get(f"/scenes/{scene_id}/results/{j1}.jpg").status_code == 410
    assert client.get(f"/scenes/{scene_id}/results/{j1}_object.jpg").status_code == 410
    assert client.get(f"/scenes/{scene_id}/results/{j2}.jpg").status_code == 200
    assert client.get(f"/scenes/{scene_id}/results/{j2}_object.jpg").status_code == 200


def test_sweep_old_results_by_age(client, tmp_path):
    scenes_dir = tmp_path / "scenes"
    (scenes_dir / "scene_x" / "results").mkdir(parents=True)
    old = scenes_dir / "scene_x" / "results" / "job_old.jpg"
    new = scenes_dir / "scene_x" / "results" / "job_new.jpg"
    old.write_bytes(b"old")
    new.write_bytes(b"new")
    stale = time.time() - 5 * 3600
    import os
    os.utime(old, (stale, stale))

    removed = sweep_old_results(scenes_dir, max_age_hours=1.0)
    assert removed == 1
    assert not old.exists()
    assert new.exists()

    # 0 이면 아무것도 안 한다.
    assert sweep_old_results(scenes_dir, max_age_hours=0) == 0
    assert new.exists()


# ------------------------------------------------- 3) 폰 전달 최적화 (용량 압축)

def _busy_jpeg(w=900, h=700) -> bytes:
    """압축이 잘 안 되는(노이즈) 이미지 — 용량 캡 테스트용."""
    import random
    rnd = random.Random(0)
    img = Image.new("RGB", (w, h))
    img.putdata([(rnd.randrange(256), rnd.randrange(256), rnd.randrange(256))
                 for _ in range(w * h)])
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=95)
    return buf.getvalue()


def test_cap_jpeg_bytes_shrinks_but_keeps_resolution():
    big = _busy_jpeg()
    limit = max(1, len(big) // 2)
    out, info = cap_jpeg_bytes(big, limit)
    assert info["capped"] is True
    assert len(out) < len(big)
    # 해상도는 그대로.
    with Image.open(io.BytesIO(big)) as a, Image.open(io.BytesIO(out)) as b:
        assert a.size == b.size


def test_cap_jpeg_bytes_noop_when_small_or_disabled():
    small = _jpeg()
    out, info = cap_jpeg_bytes(small, 5_000_000)
    assert out is small and info["capped"] is False
    out2, info2 = cap_jpeg_bytes(small, 0)
    assert out2 is small and info2["capped"] is False


# ------------------------------------------------- 제거된 사물 크롭 (서버 저장)

def test_crop_normalized_jpeg_size_and_clamp():
    src = _busy_jpeg(400, 300)
    # 정상 영역: 200x150 근처.
    out = crop_normalized_jpeg(src, (0.25, 0.25, 0.5, 0.5))
    w, h = Image.open(io.BytesIO(out)).size
    assert 195 <= w <= 205 and 145 <= h <= 155
    # 경계를 넘는 값도 이미지 안으로 클램프되고 최소 1px 보장.
    out2 = crop_normalized_jpeg(src, (0.9, 0.9, 0.5, 0.5))
    w2, h2 = Image.open(io.BytesIO(out2)).size
    assert w2 >= 1 and h2 >= 1 and w2 <= 400 and h2 <= 300
