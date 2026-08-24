import uuid
from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class ApiModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class ContentRead(ApiModel):
    id: uuid.UUID
    content_type: str
    title: str
    object_key: str
    public_url: str | None
    mime_type: str | None


class PlacementRead(ApiModel):
    id: uuid.UUID
    geo_zone_id: uuid.UUID
    poi_id: uuid.UUID | None
    anchor_type: str
    local_x: float
    local_y: float
    local_z: float
    qx: float
    qy: float
    qz: float
    qw: float
    scale: float
    min_visible_distance_m: float
    max_visible_distance_m: float
    view_cone_degrees: float


class GeoZoneNearbyRead(ApiModel):
    id: uuid.UUID
    name: str
    description: str | None
    radius_m: float
    distance_m: float


class MomentCreate(ApiModel):
    geo_zone_id: uuid.UUID
    poi_id: uuid.UUID | None = None
    content_id: uuid.UUID
    placement_id: uuid.UUID
    recorded_at: datetime
    created_by: uuid.UUID
    rendering_metadata: dict[str, Any] = Field(default_factory=dict)


class MomentRead(MomentCreate):
    id: uuid.UUID
    created_at: datetime
    content: ContentRead
    placement: PlacementRead


class CampaignRead(ApiModel):
    id: uuid.UUID
    brand: str
    title: str
    content: ContentRead
    placement: PlacementRead
    priority: int
    start_at: datetime
    end_at: datetime


class ContentCandidate(ApiModel):
    source_type: Literal["moment", "campaign"]
    source_id: uuid.UUID
    content: ContentRead
    placement: PlacementRead
    occurred_at: datetime | None = None
    priority: int = 0


class Vector3(ApiModel):
    x: float
    y: float
    z: float


class VisibilityCandidate(ApiModel):
    id: uuid.UUID
    position: Vector3
    min_distance_m: float = Field(default=0.0, ge=0.0)
    max_distance_m: float = Field(default=30.0, gt=0.0)
    view_cone_degrees: float = Field(default=70.0, gt=0.0, le=180.0)

    @model_validator(mode="after")
    def validate_distances(self) -> "VisibilityCandidate":
        if self.min_distance_m > self.max_distance_m:
            raise ValueError("min_distance_m must be <= max_distance_m")
        return self


class VisibilityRequest(ApiModel):
    camera_position: Vector3
    camera_forward: Vector3
    candidates: list[VisibilityCandidate] = Field(max_length=500)


class VisibleCandidate(ApiModel):
    id: uuid.UUID
    distance_m: float
    angle_degrees: float


class VisibilityResponse(ApiModel):
    visible: list[VisibleCandidate]


class HealthRead(ApiModel):
    status: str
    environment: str
