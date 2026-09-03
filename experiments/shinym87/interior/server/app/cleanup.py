"""결과 이미지 임시 저장 정리. 해커톤 범위라 개수/기간 기준의 단순 삭제만 한다.

- job 기록(DB row)은 남기고 **파일만** 지운다. 목록 조회는 `available:false` 로 표시된다.
- scene 당 개수 제한: 새 결과가 생길 때마다 `_run_job` 끝에서 호출한다.
- 기간 제한: 서버 기동 시 `sweep_old_results()` 로 한 번 훑는다.
"""
from __future__ import annotations

import logging
import time
from pathlib import Path

_log = logging.getLogger("interior.cleanup")


def _result_files(results_dir: Path) -> list[Path]:
    if not results_dir.is_dir():
        return []
    # mtime 우선, 동률이면 파일명(job_..._001 처럼 seq 가 zero-pad 되어 생성순과 일치).
    return sorted(
        (p for p in results_dir.glob("*.jpg") if p.is_file()),
        key=lambda p: (p.stat().st_mtime, p.name),
    )


def prune_scene_results(scenes_dir: Path, scene_id: str, keep_max: int) -> int:
    """scene 하나의 결과 파일이 `keep_max` 개를 넘으면 오래된 것부터 지운다.

    반환값은 삭제한 파일 수. `keep_max <= 0` 이면 무제한(정리 안 함).
    """
    if keep_max <= 0:
        return 0
    files = _result_files(scenes_dir / scene_id / "results")
    if len(files) <= keep_max:
        return 0
    removed = 0
    for old in files[:-keep_max]:
        try:
            old.unlink()
            removed += 1
            _log.info("[scene %s] 오래된 결과 정리(개수 초과): %s", scene_id, old.name)
        except OSError as exc:  # noqa: PERF203
            _log.warning("[scene %s] 결과 삭제 실패 %s: %s", scene_id, old.name, exc)
    return removed


def sweep_old_results(scenes_dir: Path, max_age_hours: float) -> int:
    """모든 scene 에서 `max_age_hours` 보다 오래된 결과 파일을 지운다. 반환값은 삭제 수.

    `max_age_hours <= 0` 이면 아무것도 하지 않는다.
    """
    if max_age_hours <= 0 or not Path(scenes_dir).is_dir():
        return 0
    cutoff = time.time() - max_age_hours * 3600.0
    removed = 0
    for f in Path(scenes_dir).glob("*/results/*.jpg"):
        try:
            if f.is_file() and f.stat().st_mtime < cutoff:
                f.unlink()
                removed += 1
        except OSError as exc:  # noqa: PERF203
            _log.warning("결과 삭제 실패 %s: %s", f, exc)
    if removed:
        _log.info("오래된 결과 파일 %d개 정리 (기준 %.0fh)", removed, max_age_hours)
    return removed
