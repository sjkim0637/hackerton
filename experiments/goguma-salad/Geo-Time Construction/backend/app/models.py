from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class Point3D(BaseModel):
    x: float
    y: float
    z: float


class SourceReference(BaseModel):
    type: Literal["dxf"] = "dxf"
    drawing_name: str
    entity_handle: str
    cad_layer: str
    unit_type: str


class CableProperties(BaseModel):
    diameter_m: float = Field(gt=0)
    elevation_m: float = Field(ge=0)
    source_entity_type: str


class PolylineGeometry(BaseModel):
    type: Literal["polyline"] = "polyline"
    points: list[Point3D] = Field(min_length=2)


class ConstructionObject(BaseModel):
    id: str
    category: Literal["communication"] = "communication"
    type: Literal["cable_path"] = "cable_path"
    system: Literal["home_network"] = "home_network"
    source: SourceReference
    geometry: PolylineGeometry
    properties: CableProperties


class LayerSummary(BaseModel):
    name: str
    entity_count: int


class UnitRegion(BaseModel):
    unit_type: str
    title: str
    origin_x_mm: float
    origin_y_mm: float
    width_mm: float
    height_mm: float


class DrawingAnalysis(BaseModel):
    filename: str
    source_units: str
    source_unit_code: int
    layer_count: int
    entity_count: int
    entity_types: dict[str, int]
    layers: list[LayerSummary]
    unit_regions: list[UnitRegion]


class ConstructionObjectResponse(BaseModel):
    drawing: DrawingAnalysis
    unit_region: UnitRegion
    selected_layers: list[str]
    object_count: int
    objects: list[ConstructionObject]
