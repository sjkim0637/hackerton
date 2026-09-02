"""식별자 생성. 규칙은 docs/data-model.md 2절 참고."""
from __future__ import annotations

import secrets


def _scene_suffix(scene_id: str) -> str:
    return scene_id.removeprefix("scene_")


def new_scene_id() -> str:
    return f"scene_{secrets.token_hex(4)}"  # 8 hex chars


def new_keyframe_id(scene_id: str, seq: int) -> str:
    return f"kf_{_scene_suffix(scene_id)}_{seq:03d}"


def new_object_id(scene_id: str, seq: int) -> str:
    return f"obj_{_scene_suffix(scene_id)}_{seq:03d}"


def new_job_id(scene_id: str, seq: int) -> str:
    return f"job_{_scene_suffix(scene_id)}_{seq:03d}"
