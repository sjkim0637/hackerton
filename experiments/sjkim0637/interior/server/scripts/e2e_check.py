"""서버 전체 흐름 E2E 검증.

세션 생성 → 키프레임 업로드(대표 이미지 저장) → 사물 정보 저장 확인 →
remove-object 요청 → job 폴링 → 결과 이미지 다운로드/검증 → 서버 저장 파일 확인 →
동일 요청 캐시 확인.

사용법 (server/ 에서, venv 활성화 상태):
    python scripts/e2e_check.py                     # 서버가 없으면 uvicorn 자동 기동
    python scripts/e2e_check.py --no-start          # 이미 떠 있는 서버로만 검증
    python scripts/e2e_check.py --base-url http://127.0.0.1:8000
    python scripts/e2e_check.py --keep-server       # 검증 후 자동 기동한 서버 유지

성공 시 exit 0, 하나라도 실패하면 exit 1.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import subprocess
import sys
import time
from pathlib import Path

import httpx
from PIL import Image, ImageStat

SERVER_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_test_image import TV_BBOX, build_living_room  # noqa: E402

STEPS: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str = "") -> None:
    STEPS.append((name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))


def keyframe_meta(width: int, height: int) -> dict:
    return {
        "capturedAt": "2026-09-02T12:00:00Z",
        "imageSize": {"width": width, "height": height},
        "cameraIntrinsics": {"fx": 1400.0, "fy": 1400.0, "cx": width / 2, "cy": height / 2},
        "worldToCamera": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, -2.4, 0, 0, 0, 1],
        "wallPlane": {
            "center": {"position": [0.0, 0.0, -2.4], "rotation": [0.0, 0.0, 0.0, 1.0]},
            "normal": [0.0, 0.0, 1.0],
            "extent": {"x": 3.4, "z": 2.5},
        },
        "targetObject": {"objectType": "tv", "region": {"type": "bbox", "rect": TV_BBOX}},
    }


def wait_for_health(base_url: str, timeout: float) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            if httpx.get(f"{base_url}/health", timeout=2.0).status_code == 200:
                return True
        except httpx.HTTPError:
            pass
        time.sleep(0.5)
    return False


def run_flow(base_url: str, image_path: Path, out_dir: Path) -> bool:
    client = httpx.Client(base_url=base_url, timeout=30.0)
    try:
        health = client.get("/health").json()
        record("GET /health", health.get("status") == "ok",
               f"ai provider={health.get('ai', {}).get('provider')}")

        with Image.open(image_path) as im:
            width, height = im.size
        image_bytes = image_path.read_bytes()

        r = client.post("/scenes", json={"device": "e2e-script"})
        scene_id = r.json().get("scene_id", "")
        record("POST /scenes (작업 세션 생성)",
               r.status_code == 201 and scene_id.startswith("scene_"), scene_id)

        r = client.post(
            f"/scenes/{scene_id}/keyframes",
            files={"image": ("living_room.jpg", image_bytes, "image/jpeg")},
            data={"meta": json.dumps(keyframe_meta(width, height))},
        )
        keyframe_id = r.json().get("keyframe_id", "")
        record("POST /keyframes (이미지 업로드 + 대표 이미지 저장)",
               r.status_code == 201 and keyframe_id.startswith("kf_"), keyframe_id)

        r = client.get(f"/scenes/{scene_id}/objects")
        objs = r.json()
        record("GET /objects (사물 정보 저장 확인)",
               r.status_code == 200 and len(objs) == 1 and objs[0]["object_type"] == "tv",
               f"{len(objs)}건")

        r = client.post(
            f"/scenes/{scene_id}/remove-object",
            json={"keyframe_id": keyframe_id, "object_type": "tv",
                  "target": {"type": "bbox", "rect": TV_BBOX}},
        )
        job_id = r.json().get("job_id", "")
        record("POST /remove-object", r.status_code == 202 and job_id.startswith("job_"), job_id)

        status, job = "", {}
        for _ in range(60):
            job = client.get(f"/scenes/{scene_id}/jobs/{job_id}").json()
            status = job.get("status", "")
            if status in ("done", "failed"):
                break
            time.sleep(0.5)
        record("GET /jobs/{id} 폴링 → done", status == "done",
               f"status={status}" + (f", error={job.get('error')}" if status == "failed" else ""))
        if status != "done":
            return False

        result_url = job.get("result_image_url") or ""
        record("job.result_image_url 존재", bool(result_url), result_url)
        record("job.changed_region 존재 (bbox)",
               isinstance(job.get("changed_region"), dict)
               and job["changed_region"].get("type") == "bbox",
               json.dumps(job.get("changed_region")))

        r = client.get(result_url)
        out_dir.mkdir(parents=True, exist_ok=True)
        saved = out_dir / f"result_{job_id}.jpg"
        saved.write_bytes(r.content)
        record("GET 결과 이미지 다운로드",
               r.status_code == 200
               and r.headers.get("content-type") == "image/jpeg"
               and len(r.content) > 1000,
               f"{len(r.content)} bytes → {saved}")

        try:
            result_img = Image.open(io.BytesIO(r.content))
            dims = result_img.size
            result_img.load()
            record("결과 이미지가 유효한 JPEG + 원본과 같은 해상도",
                   dims == (width, height), f"{dims}")
        except Exception as exc:  # noqa: BLE001
            result_img = None
            record("결과 이미지가 유효한 JPEG + 원본과 같은 해상도", False, repr(exc))

        if result_img is not None:
            bx = (int(TV_BBOX[0] * width), int(TV_BBOX[1] * height),
                  int((TV_BBOX[0] + TV_BBOX[2]) * width),
                  int((TV_BBOX[1] + TV_BBOX[3]) * height))
            with Image.open(image_path) as src:
                before = ImageStat.Stat(src.crop(bx).convert("L")).mean[0]
            after = ImageStat.Stat(result_img.crop(bx).convert("L")).mean[0]
            record("TV 영역이 실제로 바뀜 (어두운 TV → 밝은 벽)",
                   after > before + 40, f"밝기 {before:.0f} → {after:.0f}")

        server_file = (
            SERVER_ROOT / "data" / "scenes" / scene_id / "results" / f"{job_id}.jpg"
        )
        if server_file.exists():
            record("서버 저장 파일 확인 (data/scenes/.../results/*.jpg)",
                   server_file.stat().st_size == len(r.content),
                   f"{server_file} ({server_file.stat().st_size} bytes)")
        else:
            record("서버 저장 파일 확인", True,
                   f"SKIP — 로컬에 파일 없음 (원격 서버?): {server_file}")

        r = client.post(
            f"/scenes/{scene_id}/remove-object",
            json={"keyframe_id": keyframe_id, "object_type": "tv",
                  "target": {"type": "bbox", "rect": TV_BBOX}},
        )
        record("동일 요청 재호출 → 같은 job (캐시)",
               r.json().get("job_id") == job_id, r.json().get("job_id", ""))

        return all(ok for _, ok, _ in STEPS)
    finally:
        client.close()


def main() -> int:
    parser = argparse.ArgumentParser(description="Interior AR 서버 E2E 검증")
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--image", default=str(SERVER_ROOT / "testdata" / "living_room.jpg"))
    parser.add_argument("--out-dir", default=str(Path(__file__).resolve().parent / "_out"))
    parser.add_argument("--no-start", action="store_true", help="서버 자동 기동 안 함")
    parser.add_argument("--keep-server", action="store_true",
                        help="검증 후 자동 기동한 서버를 끄지 않음")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--ai-provider", choices=["mock", "external"],
                        help="자동 기동하는 서버의 INTERIOR_AI_PROVIDER 를 덮어쓴다")
    parser.add_argument("--ai-api-key",
                        help="자동 기동하는 서버의 INTERIOR_AI_API_KEY (Gemini 키). "
                             "생략하면 .env / 환경변수를 그대로 쓴다")
    args = parser.parse_args()

    image_path = Path(args.image)
    if not image_path.exists():
        build_living_room(image_path)
        print(f"테스트 이미지 생성: {image_path}")

    proc: subprocess.Popen | None = None
    base_url = args.base_url
    if not wait_for_health(base_url, timeout=1.5):
        if args.no_start:
            print(f"서버에 연결할 수 없습니다: {base_url} (--no-start)")
            return 2
        base_url = f"http://127.0.0.1:{args.port}"
        env = os.environ.copy()
        if args.ai_provider:
            env["INTERIOR_AI_PROVIDER"] = args.ai_provider
        if args.ai_api_key:
            env["INTERIOR_AI_API_KEY"] = args.ai_api_key
        print(
            f"서버가 없어 uvicorn 을 기동합니다 (port {args.port}, "
            f"provider={env.get('INTERIOR_AI_PROVIDER', 'mock')}) …"
        )
        proc = subprocess.Popen(
            [sys.executable, "-m", "uvicorn", "app.main:app",
             "--port", str(args.port), "--log-level", "warning"],
            cwd=str(SERVER_ROOT),
            env=env,
        )
        if not wait_for_health(base_url, timeout=30.0):
            print("uvicorn 기동 실패")
            proc.terminate()
            return 2

    try:
        ok = run_flow(base_url, image_path, Path(args.out_dir))
    finally:
        if proc is not None and not args.keep_server:
            proc.terminate()
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()

    passed = sum(1 for _, o, _ in STEPS if o)
    print("\n" + "=" * 60)
    print(f"결과: {passed}/{len(STEPS)} 단계 통과 — "
          f"{'전체 흐름 검증 성공' if ok else '검증 실패'}")
    print("=" * 60)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
