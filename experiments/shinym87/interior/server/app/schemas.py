"""요청/응답 스키마. 형식 근거는 docs/data-model.md, docs/api.md."""
from __future__ import annotations

from typing import Annotated, Literal, Union

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """키프레임 메타처럼 camelCase JSON(설계 문서 표기)과 snake_case 를 모두 받는다."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


# --------------------------------------------------------------------------- 공통

class ImageSize(CamelModel):
    width: int
    height: int


class CameraIntrinsics(CamelModel):
    fx: float
    fy: float
    cx: float
    cy: float


class Pose(CamelModel):
    position: Annotated[list[float], Field(min_length=3, max_length=3)]
    rotation: Annotated[list[float], Field(min_length=4, max_length=4)]


class PlaneExtent(CamelModel):
    x: float
    z: float


class WallPlane(CamelModel):
    center: Pose
    normal: Annotated[list[float], Field(min_length=3, max_length=3)]
    extent: PlaneExtent


class BBoxRegion(BaseModel):
    type: Literal["bbox"] = "bbox"
    # 키프레임 이미지 기준 정규화 [x, y, w, h]
    rect: Annotated[list[float], Field(min_length=4, max_length=4)]


class MaskRegion(CamelModel):
    type: Literal["mask"] = "mask"
    png: str  # base64 PNG
    size: ImageSize


Region = Annotated[Union[BBoxRegion, MaskRegion], Field(discriminator="type")]


class TargetObject(CamelModel):
    id: str | None = None
    object_type: str
    region: Region


# --------------------------------------------------------------------------- 세션

class SceneCreate(BaseModel):
    device: str = "android"


class SceneOut(BaseModel):
    scene_id: str
    created_at: str


# ----------------------------------------------------------------------- 키프레임

class KeyframeMeta(CamelModel):
    """multipart 업로드의 `meta` 파트. 형식은 docs/data-model.md 4절."""

    keyframe_id: str | None = None
    scene_id: str | None = None
    captured_at: str | None = None
    image_size: ImageSize
    camera_intrinsics: CameraIntrinsics
    world_to_camera: Annotated[list[float], Field(min_length=16, max_length=16)]
    wall_plane: WallPlane | None = None
    target_object: TargetObject | None = None


class KeyframeOut(BaseModel):
    keyframe_id: str


# ------------------------------------------------------------------------- 사물 정보

class ObjectInfoOut(BaseModel):
    id: str
    scene_id: str
    keyframe_id: str | None = None
    object_type: str
    region: dict
    created_at: str


# --------------------------------------------------------------- 사물 제거 / 복원 작업

class RemoveObjectRequest(BaseModel):
    keyframe_id: str
    target: Region
    object_type: str


class JobOut(BaseModel):
    job_id: str
    keyframe_id: str
    status: Literal["queued", "running", "done", "failed"]
    result_image_url: str | None = None
    changed_region: dict | None = None
    error: str | None = None


class ResultInfoOut(BaseModel):
    """`GET /scenes/{id}/results` 의 항목. 같은 scene 의 복원 결과를 job 단위 버전으로 나열한다."""

    job_id: str
    keyframe_id: str
    status: Literal["queued", "running", "done", "failed"]
    result_image_url: str | None = None
    # 제거된 사물을 원본 키프레임에서 그대로 오려낸 크롭(배경 포함). 이동 배치 재사용용.
    removed_object_image_url: str | None = None
    changed_region: dict | None = None
    error: str | None = None
    created_at: str
    updated_at: str
    size_bytes: int | None = None
    # 오래된 결과 정리로 파일이 삭제됐으면 false (job 기록은 남는다).
    available: bool = False


# --------------------------------------------------------------------- 가구 카탈로그

class FurnitureSize(BaseModel):
    w: float
    h: float
    d: float


class FurnitureModel(BaseModel):
    type: str = "glb"
    url: str | None = None
    placeholder: str = "cube"


class FurnitureData(BaseModel):
    id: str
    name: str
    category: str
    size_m: FurnitureSize
    model: FurnitureModel
    thumbnail: str | None = None
    anchor_hint: Literal["floor", "wall"] = "floor"
