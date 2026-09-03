"""로컬 파일 + SQLite 저장. 해커톤 범위라 마이그레이션 없이 CREATE IF NOT EXISTS 로 둔다."""
from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator

_SEQ_COLUMNS = {"kf_seq", "obj_seq", "job_seq"}

_SCHEMA = """
CREATE TABLE IF NOT EXISTS scenes (
    scene_id   TEXT PRIMARY KEY,
    device     TEXT,
    created_at TEXT NOT NULL,
    kf_seq     INTEGER NOT NULL DEFAULT 0,
    obj_seq    INTEGER NOT NULL DEFAULT 0,
    job_seq    INTEGER NOT NULL DEFAULT 0,
    ai_calls   INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS keyframes (
    keyframe_id TEXT PRIMARY KEY,
    scene_id    TEXT NOT NULL,
    image_path  TEXT NOT NULL,
    meta_path   TEXT NOT NULL,
    created_at  TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS objects (
    id          TEXT PRIMARY KEY,
    scene_id    TEXT NOT NULL,
    keyframe_id TEXT,
    object_type TEXT NOT NULL,
    region_json TEXT NOT NULL,
    created_at  TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS jobs (
    job_id               TEXT PRIMARY KEY,
    scene_id             TEXT NOT NULL,
    keyframe_id          TEXT NOT NULL,
    cache_key            TEXT,
    status               TEXT NOT NULL,
    result_path          TEXT,
    result_url           TEXT,
    removed_object_path  TEXT,
    removed_object_url   TEXT,
    changed_region_json  TEXT,
    error                TEXT,
    created_at           TEXT NOT NULL,
    updated_at           TEXT NOT NULL
);
"""


def utcnow() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


# 이미 만들어진 DB 에 뒤늦게 추가된 컬럼 (CREATE IF NOT EXISTS 로는 안 붙는다).
_EXTRA_COLUMNS = {
    "scenes": {"ai_calls": "INTEGER NOT NULL DEFAULT 0"},
    "jobs": {
        "removed_object_path": "TEXT",
        "removed_object_url": "TEXT",
    },
}


class Store:
    def __init__(self, db_path: Path) -> None:
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        with self._conn() as conn:
            conn.executescript(_SCHEMA)
            for table, cols in _EXTRA_COLUMNS.items():
                have = {r["name"] for r in conn.execute(f"PRAGMA table_info({table})")}
                for name, decl in cols.items():
                    if name not in have:
                        conn.execute(f"ALTER TABLE {table} ADD COLUMN {name} {decl}")

    @contextmanager
    def _conn(self) -> Iterator[sqlite3.Connection]:
        conn = sqlite3.connect(self.db_path, check_same_thread=False)
        conn.row_factory = sqlite3.Row
        try:
            yield conn
            conn.commit()
        finally:
            conn.close()

    # ------------------------------------------------------------------ scenes

    def create_scene(self, scene_id: str, device: str) -> dict[str, Any]:
        now = utcnow()
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO scenes (scene_id, device, created_at) VALUES (?, ?, ?)",
                (scene_id, device, now),
            )
        return {"scene_id": scene_id, "created_at": now}

    def get_scene(self, scene_id: str) -> dict[str, Any] | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT * FROM scenes WHERE scene_id = ?", (scene_id,)
            ).fetchone()
        return dict(row) if row else None

    def next_seq(self, scene_id: str, column: str) -> int:
        if column not in _SEQ_COLUMNS:
            raise ValueError(f"unknown seq column: {column}")
        with self._conn() as conn:
            cur = conn.execute(
                f"UPDATE scenes SET {column} = {column} + 1 WHERE scene_id = ?",
                (scene_id,),
            )
            if cur.rowcount == 0:
                raise KeyError(scene_id)
            row = conn.execute(
                f"SELECT {column} AS value FROM scenes WHERE scene_id = ?", (scene_id,)
            ).fetchone()
        return int(row["value"])

    def bump_ai_calls(self, scene_id: str) -> int:
        """실제 외부 AI 를 호출할 때마다 1 증가. 반환값은 이 scene 의 누적 호출 수."""
        with self._conn() as conn:
            conn.execute(
                "UPDATE scenes SET ai_calls = ai_calls + 1 WHERE scene_id = ?", (scene_id,)
            )
            row = conn.execute(
                "SELECT ai_calls FROM scenes WHERE scene_id = ?", (scene_id,)
            ).fetchone()
        return int(row["ai_calls"]) if row else 0

    # --------------------------------------------------------------- keyframes

    def add_keyframe(
        self, keyframe_id: str, scene_id: str, image_path: str, meta_path: str
    ) -> None:
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO keyframes (keyframe_id, scene_id, image_path, meta_path, created_at) "
                "VALUES (?, ?, ?, ?, ?)",
                (keyframe_id, scene_id, image_path, meta_path, utcnow()),
            )

    def get_keyframe(self, keyframe_id: str) -> dict[str, Any] | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT * FROM keyframes WHERE keyframe_id = ?", (keyframe_id,)
            ).fetchone()
        return dict(row) if row else None

    # ----------------------------------------------------------------- objects

    def add_object(
        self,
        object_id: str,
        scene_id: str,
        keyframe_id: str | None,
        object_type: str,
        region: dict,
    ) -> dict[str, Any]:
        now = utcnow()
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO objects (id, scene_id, keyframe_id, object_type, region_json, created_at) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                (object_id, scene_id, keyframe_id, object_type, json.dumps(region), now),
            )
        return {
            "id": object_id,
            "scene_id": scene_id,
            "keyframe_id": keyframe_id,
            "object_type": object_type,
            "region": region,
            "created_at": now,
        }

    def list_objects(self, scene_id: str) -> list[dict[str, Any]]:
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT * FROM objects WHERE scene_id = ? ORDER BY created_at", (scene_id,)
            ).fetchall()
        return [
            {
                "id": r["id"],
                "scene_id": r["scene_id"],
                "keyframe_id": r["keyframe_id"],
                "object_type": r["object_type"],
                "region": json.loads(r["region_json"]),
                "created_at": r["created_at"],
            }
            for r in rows
        ]

    # -------------------------------------------------------------------- jobs

    def create_job(
        self, job_id: str, scene_id: str, keyframe_id: str, cache_key: str
    ) -> dict[str, Any]:
        now = utcnow()
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO jobs (job_id, scene_id, keyframe_id, cache_key, status, created_at, updated_at) "
                "VALUES (?, ?, ?, ?, 'queued', ?, ?)",
                (job_id, scene_id, keyframe_id, cache_key, now, now),
            )
        return self.get_job(job_id)  # type: ignore[return-value]

    def update_job(self, job_id: str, **fields: Any) -> None:
        if "changed_region" in fields:
            fields["changed_region_json"] = json.dumps(fields.pop("changed_region"))
        allowed = {
            "status",
            "result_path",
            "result_url",
            "removed_object_path",
            "removed_object_url",
            "changed_region_json",
            "error",
        }
        sets = {k: v for k, v in fields.items() if k in allowed}
        if not sets:
            return
        sets["updated_at"] = utcnow()
        assignments = ", ".join(f"{k} = ?" for k in sets)
        with self._conn() as conn:
            conn.execute(
                f"UPDATE jobs SET {assignments} WHERE job_id = ?",
                (*sets.values(), job_id),
            )

    def get_job(self, job_id: str) -> dict[str, Any] | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT * FROM jobs WHERE job_id = ?", (job_id,)
            ).fetchone()
        return self._job_row(row)

    def list_jobs(self, scene_id: str) -> list[dict[str, Any]]:
        """이 scene 의 모든 job 을 최신순으로. (결과 버전 목록 조회용)"""
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT * FROM jobs WHERE scene_id = ? ORDER BY created_at DESC, job_id DESC",
                (scene_id,),
            ).fetchall()
        return [j for r in rows if (j := self._job_row(r)) is not None]

    def count_jobs(self, scene_id: str) -> int:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT COUNT(*) AS n FROM jobs WHERE scene_id = ?", (scene_id,)
            ).fetchone()
        return int(row["n"])

    _DEDUP_STATUSES = ("done", "queued", "running")

    def find_job_by_cache_key(
        self, scene_id: str, cache_key: str, statuses: tuple[str, ...] = _DEDUP_STATUSES
    ) -> dict[str, Any] | None:
        """동일 요청(cache_key)의 job 을 찾는다.

        기본값은 완료(done) + 처리 중(queued/running) 을 모두 포함해 **중복 호출을 막는다**.
        같은 요청이 아직 처리 중이면 새 job 을 만들지 않고 그 job 을 그대로 돌려준다.
        """
        placeholders = ", ".join("?" for _ in statuses)
        with self._conn() as conn:
            row = conn.execute(
                f"SELECT * FROM jobs WHERE scene_id = ? AND cache_key = ? "
                f"AND status IN ({placeholders}) ORDER BY updated_at DESC LIMIT 1",
                (scene_id, cache_key, *statuses),
            ).fetchone()
        return self._job_row(row)

    def find_done_job_by_cache_key(
        self, scene_id: str, cache_key: str
    ) -> dict[str, Any] | None:
        return self.find_job_by_cache_key(scene_id, cache_key, statuses=("done",))

    @staticmethod
    def _job_row(row: sqlite3.Row | None) -> dict[str, Any] | None:
        if row is None:
            return None
        data = dict(row)
        raw = data.pop("changed_region_json", None)
        data["changed_region"] = json.loads(raw) if raw else None
        return data
