"""작업 세션 · 키프레임 업로드 · 사물 정보 · 사물 제거(복원) 작업."""
from __future__ import annotations

import hashlib
import json
import logging
import time
from pathlib import Path

from fastapi import APIRouter, BackgroundTasks, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from pydantic import ValidationError

from ..ai import ProviderError, is_known, normalize_object_type
from ..ai.colormatch import check_result_anomaly, match_to_source
from ..ai.imageops import ensure_jpeg_size, image_size
from ..config import get_settings
from ..deps import get_provider, get_store
from ..ids import new_job_id, new_keyframe_id, new_object_id, new_scene_id
from ..schemas import (
    JobOut,
    KeyframeMeta,
    KeyframeOut,
    ObjectInfoOut,
    RemoveObjectRequest,
    SceneCreate,
    SceneOut,
)

router = APIRouter(tags=["scenes"])
_log = logging.getLogger("interior.scenes")


def _require_scene(scene_id: str) -> dict:
    scene = get_store().get_scene(scene_id)
    if scene is None:
        raise HTTPException(status_code=404, detail=f"작업 세션 없음: {scene_id}")
    return scene


def _cache_key(keyframe_id: str, region: dict, object_type: str) -> str:
    payload = json.dumps(
        {"keyframe_id": keyframe_id, "region": region, "object_type": object_type},
        sort_keys=True,
        ensure_ascii=False,
    )
    return hashlib.sha1(payload.encode("utf-8")).hexdigest()


def _job_out(job: dict) -> JobOut:
    return JobOut(
        job_id=job["job_id"],
        keyframe_id=job["keyframe_id"],
        status=job["status"],
        result_image_url=job.get("result_url"),
        changed_region=job.get("changed_region"),
        error=job.get("error"),
    )


# --------------------------------------------------------------------- 세션 생성

@router.post("/scenes", response_model=SceneOut, status_code=201)
def create_scene(body: SceneCreate) -> dict:
    store = get_store()
    scene_id = new_scene_id()
    return store.create_scene(scene_id, body.device)


@router.get("/scenes/{scene_id}", response_model=SceneOut)
def get_scene(scene_id: str) -> dict:
    scene = _require_scene(scene_id)
    return {"scene_id": scene["scene_id"], "created_at": scene["created_at"]}


# ---------------------------------------------------------- 이미지(키프레임) 업로드

@router.post("/scenes/{scene_id}/keyframes", response_model=KeyframeOut, status_code=201)
async def upload_keyframe(
    scene_id: str,
    image: UploadFile = File(..., description="대표 이미지 (JPEG)"),
    meta: str = Form(..., description="docs/data-model.md 4절의 메타 JSON 문자열"),
) -> dict:
    _require_scene(scene_id)
    store = get_store()

    try:
        meta_obj = KeyframeMeta.model_validate_json(meta)
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail=f"meta 형식 오류: {exc}") from exc

    seq = store.next_seq(scene_id, "kf_seq")
    keyframe_id = meta_obj.keyframe_id or new_keyframe_id(scene_id, seq)
    meta_obj.keyframe_id = keyframe_id
    meta_obj.scene_id = scene_id

    kf_dir = get_settings().scenes_dir / scene_id / "keyframes"
    kf_dir.mkdir(parents=True, exist_ok=True)
    image_path = kf_dir / f"{keyframe_id}.jpg"
    image_path.write_bytes(await image.read())

    meta_path = kf_dir / f"{keyframe_id}.json"
    meta_path.write_text(
        meta_obj.model_dump_json(by_alias=True, indent=2, exclude_none=True),
        encoding="utf-8",
    )
    store.add_keyframe(keyframe_id, scene_id, str(image_path), str(meta_path))

    # 사물 정보가 함께 오면 저장 (실제 인식은 아직 없음). 종류는 정규화해서 보관.
    if meta_obj.target_object is not None:
        obj_seq = store.next_seq(scene_id, "obj_seq")
        object_id = meta_obj.target_object.id or new_object_id(scene_id, obj_seq)
        store.add_object(
            object_id,
            scene_id,
            keyframe_id,
            normalize_object_type(meta_obj.target_object.object_type),
            meta_obj.target_object.region.model_dump(),
        )

    return {"keyframe_id": keyframe_id}


@router.get("/scenes/{scene_id}/keyframes/{keyframe_id}")
def get_keyframe_meta(scene_id: str, keyframe_id: str) -> dict:
    _require_scene(scene_id)
    kf = get_store().get_keyframe(keyframe_id)
    if kf is None or kf["scene_id"] != scene_id:
        raise HTTPException(status_code=404, detail=f"키프레임 없음: {keyframe_id}")
    return json.loads(Path(kf["meta_path"]).read_text(encoding="utf-8"))


@router.get("/scenes/{scene_id}/keyframes/{keyframe_id}/image")
def get_keyframe_image(scene_id: str, keyframe_id: str) -> FileResponse:
    _require_scene(scene_id)
    kf = get_store().get_keyframe(keyframe_id)
    if kf is None or kf["scene_id"] != scene_id:
        raise HTTPException(status_code=404, detail=f"키프레임 없음: {keyframe_id}")
    return FileResponse(kf["image_path"], media_type="image/jpeg")


# ------------------------------------------------------------------- 사물 정보 조회

@router.get("/scenes/{scene_id}/objects", response_model=list[ObjectInfoOut])
def list_objects(scene_id: str) -> list[dict]:
    _require_scene(scene_id)
    return get_store().list_objects(scene_id)


# --------------------------------------------------- 사물 제거 + 빈 공간 복원 작업

def _run_job(
    job_id: str,
    scene_id: str,
    image_path: str,
    region: dict,
    object_type: str,
) -> None:
    store = get_store()
    settings = get_settings()
    provider = get_provider()
    store.update_job(job_id, status="running")

    source_bytes = Path(image_path).read_bytes()
    original_size = image_size(source_bytes)
    _log.info(
        "[job %s] provider=%s keyframe=%s (%d bytes, %dx%d) object=%s region=%s",
        job_id, provider.name, Path(image_path).name, len(source_bytes),
        original_size[0], original_size[1], object_type, region,
    )

    per_call_cost = 0.0 if provider.name == "mock" else settings.ai_cost_per_call_usd
    attempts = max(1, settings.ai_max_retries + 1)
    last_exc: Exception | None = None

    for attempt in range(1, attempts + 1):
        # --- AI 호출 횟수 / 대략 비용 기록 ---
        scene_calls = store.bump_ai_calls(scene_id)
        _log.info(
            "[job %s] AI 호출 (provider=%s, 시도 %d/%d) · scene 누적 %d회 · "
            "예상 비용: 이번 ~$%.4f, scene 누적 ~$%.4f",
            job_id, provider.name, attempt, attempts, scene_calls,
            per_call_cost, scene_calls * per_call_cost,
        )
        try:
            # 프롬프트 문구는 프로바이더가 사물 종류에 맞춰 만든다.
            # INTERIOR_AI_EXTRA_INSTRUCTION 이 있으면 프롬프트 끝에 덧붙인다 (튜닝/실험용).
            result = provider.remove_object(
                image_bytes=source_bytes,
                region=region,
                object_type=object_type,
                prompt=settings.ai_extra_instruction,
            )
            raw_result_size = image_size(result.image_bytes)  # 리사이즈 전
            # 공통 보정: 결과는 항상 원본과 같은 해상도의 JPEG.
            final_bytes = ensure_jpeg_size(result.image_bytes, original_size)

            # --- 이상 결과 감지 (명백한 케이스만) ---
            report = check_result_anomaly(
                final_bytes, source_bytes, region,
                raw_result_size=raw_result_size,
                warn_mad=settings.result_anomaly_warn_mad,
                fail_mad=settings.result_anomaly_fail_mad,
            )
            if report.severity == "fail":
                _log.warning("[job %s] 이상 결과 → 실패 처리: %s %s",
                             job_id, report.reason, report.metrics)
                store.update_job(job_id, status="failed",
                                 error=f"이상 결과 감지: {report.reason}")
                return
            if report.severity == "warn":
                _log.warning("[job %s] 이상 결과 의심: %s %s",
                             job_id, report.reason, report.metrics)

            # --- 색감 보정: 원본의 (마스크 밖) 밝기/색상에 맞춘다 ---
            if settings.result_color_match:
                cm = match_to_source(final_bytes, source_bytes, region)
                if cm.applied:
                    _log.info("[job %s] 색감 보정 gains(r,g,b)=%s",
                              job_id, [round(g, 3) for g in cm.gains])
                    final_bytes = cm.image_bytes

            results_dir = settings.scenes_dir / scene_id / "results"
            results_dir.mkdir(parents=True, exist_ok=True)
            out_path = results_dir / f"{job_id}.jpg"
            out_path.write_bytes(final_bytes)

            store.update_job(
                job_id,
                status="done",
                result_path=str(out_path),
                result_url=f"/scenes/{scene_id}/results/{job_id}.jpg",
                changed_region=result.changed_region,
            )
            _log.info(
                "[job %s] done → %s (%d bytes, 시도 %d회)",
                job_id, out_path.name, len(final_bytes), attempt,
            )
            return
        except ProviderError as exc:
            last_exc = exc
            if getattr(exc, "retryable", False) and attempt < attempts:
                _log.warning(
                    "[job %s] 일시적 오류 (재시도 %d/%d), %.1fs 후 재시도: %s",
                    job_id, attempt, attempts - 1, settings.ai_retry_backoff_seconds, exc,
                )
                time.sleep(settings.ai_retry_backoff_seconds)
                continue
            break
        except Exception as exc:  # noqa: BLE001 - 그 외 오류는 재시도 안 함
            last_exc = exc
            break

    _log.warning("[job %s] failed: %s: %s", job_id, type(last_exc).__name__, last_exc)
    store.update_job(job_id, status="failed", error=f"{type(last_exc).__name__}: {last_exc}")


@router.post("/scenes/{scene_id}/remove-object", response_model=JobOut, status_code=202)
def remove_object(
    scene_id: str, body: RemoveObjectRequest, background: BackgroundTasks
) -> JobOut:
    scene = _require_scene(scene_id)
    store = get_store()
    settings = get_settings()

    kf = store.get_keyframe(body.keyframe_id)
    if kf is None or kf["scene_id"] != scene_id:
        raise HTTPException(status_code=404, detail=f"키프레임 없음: {body.keyframe_id}")

    # 사물 종류 정규화: 앱은 tv/sofa/table/chair/shelf 를 보낸다. couch→sofa 등 별칭도 흡수.
    object_type = normalize_object_type(body.object_type)
    if not object_type:
        raise HTTPException(status_code=422, detail="object_type 이 비어 있습니다")
    if not is_known(object_type):
        _log.warning(
            "[scene %s] 알 수 없는 object_type=%r (그대로 처리)", scene_id, body.object_type
        )

    region = body.target.model_dump()
    cache_key = _cache_key(body.keyframe_id, region, object_type)

    # 중복 호출 방지: 완료됐거나 아직 처리 중인 동일 요청이 있으면 그 job 을 그대로 돌려준다.
    existing = store.find_job_by_cache_key(scene_id, cache_key)
    if existing is not None:
        _log.info(
            "[job %s] 중복 요청 → 기존 job 재사용 (status=%s)",
            existing["job_id"], existing["status"],
        )
        return _job_out(existing)

    # 호출 한도: 이 scene 의 실제 AI 호출 누적 기준
    if scene.get("ai_calls", 0) >= settings.max_ai_calls_per_scene:
        raise HTTPException(
            status_code=429,
            detail=f"이 작업의 외부 AI 호출 한도({settings.max_ai_calls_per_scene}회)를 초과했습니다",
        )

    # 편집 대상 사물 정보도 저장 (형식만, 정규화된 종류로)
    obj_seq = store.next_seq(scene_id, "obj_seq")
    store.add_object(
        new_object_id(scene_id, obj_seq),
        scene_id,
        body.keyframe_id,
        object_type,
        region,
    )

    job_seq = store.next_seq(scene_id, "job_seq")
    job_id = new_job_id(scene_id, job_seq)
    store.create_job(job_id, scene_id, body.keyframe_id, cache_key)
    background.add_task(
        _run_job, job_id, scene_id, kf["image_path"], region, object_type
    )
    return JobOut(job_id=job_id, keyframe_id=body.keyframe_id, status="queued")


@router.get("/scenes/{scene_id}/jobs/{job_id}", response_model=JobOut)
def get_job(scene_id: str, job_id: str) -> JobOut:
    _require_scene(scene_id)
    job = get_store().get_job(job_id)
    if job is None or job["scene_id"] != scene_id:
        raise HTTPException(status_code=404, detail=f"작업 없음: {job_id}")
    return _job_out(job)


@router.get("/scenes/{scene_id}/results/{job_id}.jpg")
def get_job_result_image(scene_id: str, job_id: str) -> FileResponse:
    _require_scene(scene_id)
    job = get_store().get_job(job_id)
    if job is None or job["scene_id"] != scene_id:
        raise HTTPException(status_code=404, detail=f"작업 없음: {job_id}")
    if job["status"] != "done" or not job.get("result_path"):
        raise HTTPException(status_code=409, detail=f"결과가 아직 없습니다 (status={job['status']})")
    return FileResponse(job["result_path"], media_type="image/jpeg")
