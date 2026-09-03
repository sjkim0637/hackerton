"""PHASE 4: 삭제한 사물의 재배치(이동/회전/크기) 저장 + 간단한 실행 취소.

앱(사용자 1)은 이동 상태를 로컬(메모리)로만 들고 있어 앱을 끄면 사라진다.
여기 저장해 두면 다른 기기/세션·웹 뷰어가 "무엇을 어디로 옮겼는지"를 다시 불러올 수 있다.
`pose` 는 세션 로컬 월드 좌표라 그대로 재사용은 안 되지만, `source_region`(원래 제거 영역)
기준으로 재정합하면 되고 scale/rotation/plane 은 세션과 무관하게 재사용된다.
"""
from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException

from ..deps import get_store
from ..ids import new_placement_id
from ..schemas import PlacementCreate, PlacementOut

router = APIRouter(tags=["placements"])
_log = logging.getLogger("interior.placements")


def _require_scene(scene_id: str) -> dict:
    scene = get_store().get_scene(scene_id)
    if scene is None:
        raise HTTPException(status_code=404, detail=f"작업 세션 없음: {scene_id}")
    return scene


@router.post(
    "/scenes/{scene_id}/placements", response_model=PlacementOut, status_code=201
)
def create_placement(scene_id: str, body: PlacementCreate) -> dict:
    """재배치 상태 하나를 append 로 저장한다. 같은 사물을 다시 옮기면 새 행이 쌓인다
    (실행 취소가 이 순서를 되짚는다)."""
    _require_scene(scene_id)
    store = get_store()

    if body.job_id:
        job = store.get_job(body.job_id)
        if job is None or job["scene_id"] != scene_id:
            raise HTTPException(status_code=404, detail=f"작업 없음: {body.job_id}")

    seq = store.next_seq(scene_id, "plc_seq")
    placement_id = new_placement_id(scene_id, seq)
    row = store.create_placement(
        placement_id,
        scene_id,
        job_id=body.job_id,
        object_type=body.object_type,
        source_region=body.source_region.model_dump() if body.source_region else None,
        pose={"position": body.pose.position, "rotation": body.pose.rotation},
        scale=body.scale,
        rotation_deg=body.rotation_deg,
        plane=body.plane,
    )
    _log.info(
        "[scene %s] 배치 저장 %s type=%s scale=%.2f rot=%.0f plane=%s",
        scene_id, placement_id, body.object_type, body.scale, body.rotation_deg, body.plane,
    )
    return row


@router.get("/scenes/{scene_id}/placements", response_model=list[PlacementOut])
def list_placements(scene_id: str, include_undone: bool = False) -> list[dict]:
    """이 scene 의 재배치를 최신순으로. 기본은 `active` 만, `?include_undone=true` 면 취소분 포함."""
    _require_scene(scene_id)
    return get_store().list_placements(scene_id, include_undone=include_undone)


@router.post("/scenes/{scene_id}/placements/undo", response_model=PlacementOut)
def undo_placement(scene_id: str) -> dict:
    """가장 최근 배치 변경 하나를 취소한다(`status=undone`). 완전한 undo 스택은 아니지만
    연달아 호출하면 배치 이력을 한 단계씩 되짚는다."""
    _require_scene(scene_id)
    store = get_store()
    latest = store.latest_active_placement(scene_id)
    if latest is None:
        raise HTTPException(status_code=404, detail="취소할 배치가 없습니다")
    store.set_placement_status(latest["placement_id"], "undone")
    _log.info("[scene %s] 배치 취소 %s", scene_id, latest["placement_id"])
    return store.get_placement(latest["placement_id"])  # type: ignore[return-value]
