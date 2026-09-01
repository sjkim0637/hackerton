from __future__ import annotations

import re
from collections import Counter
from collections.abc import Iterable
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import BinaryIO

import ezdxf
from ezdxf import path as ezpath
from ezdxf.document import Drawing
from ezdxf.entities import DXFEntity

from app.models import (
    ArchitectureBackgroundResponse,
    ArchitectureSegment,
    CableProperties,
    CommunicationDevice,
    ConstructionObject,
    ConstructionObjectResponse,
    DeviceProperties,
    DrawingAnalysis,
    LayerSummary,
    Point3D,
    PointGeometry,
    PolylineGeometry,
    SourceReference,
    UnitRegion,
)

SUPPORTED_PATH_TYPES = {"LINE", "LWPOLYLINE", "POLYLINE", "ARC", "SPLINE"}
DEFAULT_CABLE_LAYERS = ("e-wire", "e-wire3s")
SHEET_WIDTH_MM = 42_000.0
SHEET_HEIGHT_MM = 29_700.0
ARCHITECTURE_UNIT_ORDER = ("84A", "84B", "84C", "84D", "120A", "144P", "155P")
DEVICE_LAYERS = {"통신단자함", "SYM", "E-SYM", "천정"}
DEVICE_WALL_OFFSET_M = 0.9
DEVICE_TYPES = {
    "100-57": ("communication_panel", "통신단자함"),
    "EFCL": ("entrance_camera", "세대 현관 카메라"),
    "A$C6A344DFD": ("magnetic_sensor", "마그네틱 센서"),
    "A$C4BEC3CC4": ("motion_detector", "동체 감지기"),
    "A$CCCBF2ACF": ("infrared_detector", "적외선 감지기"),
    "F19": ("batch_switch", "일괄소등 스위치"),
    "100-89": ("joint_box", "Joint Box"),
    "50J": ("joint_box", "Joint Box"),
    "A$C5B1B9F38": ("ceiling_device", "천장 홈넷 설비"),
}
UNIT_TITLE_PATTERN = re.compile(r"(?P<area>\d+)㎡(?P<variant>[A-Z])\s*단위세대", re.IGNORECASE)


def read_dxf_upload(stream: BinaryIO) -> Drawing:
    temporary_path: Path | None = None
    try:
        with NamedTemporaryFile(suffix=".dxf", delete=False) as temporary:
            temporary_path = Path(temporary.name)
            while chunk := stream.read(1024 * 1024):
                temporary.write(chunk)
        return ezdxf.readfile(temporary_path)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def analyze_drawing(doc: Drawing, filename: str) -> DrawingAnalysis:
    modelspace = doc.modelspace()
    entities = list(modelspace)
    entity_types = Counter(entity.dxftype() for entity in entities)
    layer_counts = Counter(entity.dxf.layer for entity in entities)
    unit_regions = detect_unit_regions(doc)
    unit_code = int(doc.header.get("$INSUNITS", 0))

    return DrawingAnalysis(
        filename=Path(filename).name,
        source_units=_unit_name(unit_code),
        source_unit_code=unit_code,
        layer_count=len(doc.layers),
        entity_count=len(entities),
        entity_types=dict(sorted(entity_types.items())),
        layers=[
            LayerSummary(name=name, entity_count=count)
            for name, count in layer_counts.most_common()
        ],
        unit_regions=unit_regions,
    )


def detect_unit_regions(doc: Drawing) -> list[UnitRegion]:
    candidates: dict[str, tuple[float, int, str]] = {}
    for entity in doc.modelspace().query("TEXT MTEXT"):
        text = _plain_text(entity)
        match = UNIT_TITLE_PATTERN.search(text)
        if not match:
            continue
        unit_type = f"{match.group('area')}{match.group('variant').upper()}"
        point = entity.dxf.insert
        current = candidates.get(unit_type)
        # The full drawing title is a more reliable anchor than the short title.
        score = (1 if "홈네트워크" in text else 0, len(text))
        if current is None or score > (current[1], len(current[2])):
            candidates[unit_type] = (float(point.x), score[0], text)

    ordered = sorted(candidates.items(), key=lambda item: item[1][0])
    sheet_origins = sorted(
        {
            (float(insert.dxf.insert.x), float(insert.dxf.insert.y))
            for insert in doc.modelspace().query("INSERT")
            if str(insert.dxf.name).upper() == "XR_SHEET"
        }
    )
    regions: list[UnitRegion] = []
    for index, (unit_type, (title_x, _, title)) in enumerate(ordered):
        # ET-1101 uses seven adjacent 42,000 mm title blocks. The left-most
        # XR_SHEET insertion is at X=1,235; derive it from title order so the
        # parser remains useful after a global drawing translation.
        if index < len(sheet_origins):
            origin_x, origin_y = sheet_origins[index]
        else:
            origin_x = title_x - (title_x % SHEET_WIDTH_MM)
            origin_y = -115_494.354
            if index and regions:
                origin_x = regions[0].origin_x_mm + index * SHEET_WIDTH_MM
        regions.append(
            UnitRegion(
                unit_type=unit_type,
                title=title,
                origin_x_mm=origin_x,
                origin_y_mm=origin_y,
                width_mm=SHEET_WIDTH_MM,
                height_mm=SHEET_HEIGHT_MM,
            )
        )
    return regions


def build_cable_objects(
    doc: Drawing,
    filename: str,
    unit_type: str,
    layers: Iterable[str] = DEFAULT_CABLE_LAYERS,
    elevation_m: float = 2.3,
    diameter_m: float = 0.03,
) -> ConstructionObjectResponse:
    analysis = analyze_drawing(doc, filename)
    region = next(
        (candidate for candidate in analysis.unit_regions if candidate.unit_type == unit_type),
        None,
    )
    if region is None:
        available = ", ".join(item.unit_type for item in analysis.unit_regions) or "none"
        raise ValueError(f"Unknown unit type {unit_type!r}; available: {available}")

    selected_layers = list(dict.fromkeys(layers))
    objects: list[ConstructionObject] = []
    for entity in doc.modelspace():
        if entity.dxf.layer not in selected_layers or entity.dxftype() not in SUPPORTED_PATH_TYPES:
            continue
        points = _entity_points(entity)
        if len(points) < 2 or not _belongs_to_region(points, region):
            continue
        normalized = [
            Point3D(
                x=round((x - region.origin_x_mm) / 1000.0, 6),
                y=round((y - region.origin_y_mm) / 1000.0, 6),
                z=elevation_m,
            )
            for x, y in points
        ]
        handle = entity.dxf.get("handle", f"generated-{len(objects)}")
        objects.append(
            ConstructionObject(
                id=f"communication-{unit_type.lower()}-{handle.lower()}",
                source=SourceReference(
                    drawing_name=Path(filename).name,
                    entity_handle=handle,
                    cad_layer=entity.dxf.layer,
                    unit_type=unit_type,
                ),
                geometry=PolylineGeometry(points=normalized),
                properties=CableProperties(
                    diameter_m=diameter_m,
                    elevation_m=elevation_m,
                    source_entity_type=entity.dxftype(),
                ),
            )
        )

    devices = _build_communication_devices(doc, filename, region, elevation_m)
    return ConstructionObjectResponse(
        drawing=analysis,
        unit_region=region,
        selected_layers=selected_layers,
        object_count=len(objects),
        objects=objects,
        device_count=len(devices),
        devices=devices,
    )


def _build_communication_devices(
    doc: Drawing,
    filename: str,
    region: UnitRegion,
    cable_elevation_m: float,
) -> list[CommunicationDevice]:
    devices: list[CommunicationDevice] = []
    for entity_index, entity in enumerate(doc.modelspace().query("INSERT")):
        if entity.dxf.layer not in DEVICE_LAYERS:
            continue
        insert = entity.dxf.insert
        if not (
            region.origin_x_mm <= insert.x <= region.origin_x_mm + region.width_mm
            and region.origin_y_mm <= insert.y <= region.origin_y_mm + region.height_mm
        ):
            continue
        block_name = entity.dxf.name
        subtype, display_name = DEVICE_TYPES.get(
            block_name.upper(),
            ("home_network_device", "홈넷 설비"),
        )
        elevation_m = round(
            cable_elevation_m
            if entity.dxf.layer == "천정"
            else max(cable_elevation_m - DEVICE_WALL_OFFSET_M, 0.0),
            6,
        )
        handle = entity.dxf.get("handle", f"generated-device-{entity_index}")
        devices.append(
            CommunicationDevice(
                id=f"communication-device-{region.unit_type.lower()}-{handle.lower()}",
                source=SourceReference(
                    drawing_name=Path(filename).name,
                    entity_handle=handle,
                    cad_layer=entity.dxf.layer,
                    unit_type=region.unit_type,
                ),
                geometry=PointGeometry(
                    position=Point3D(
                        x=round((insert.x - region.origin_x_mm) / 1000.0, 6),
                        y=round((insert.y - region.origin_y_mm) / 1000.0, 6),
                        z=elevation_m,
                    )
                ),
                properties=DeviceProperties(
                    subtype=subtype,
                    display_name=display_name,
                    block_name=block_name,
                    elevation_m=elevation_m,
                    size_m=0.3,
                    rotation_deg=float(entity.dxf.get("rotation", 0.0)),
                ),
            )
        )
    return devices


def build_architecture_background(
    doc: Drawing,
    filename: str,
    unit_type: str,
    min_segment_length_mm: float = 100.0,
) -> ArchitectureBackgroundResponse:
    try:
        unit_index = ARCHITECTURE_UNIT_ORDER.index(unit_type)
    except ValueError as exc:
        available = ", ".join(ARCHITECTURE_UNIT_ORDER)
        raise ValueError(f"Unknown unit type {unit_type!r}; available: {available}") from exc

    min_x = unit_index * SHEET_WIDTH_MM
    min_y = 0.0
    max_x = min_x + SHEET_WIDTH_MM
    max_y = min_y + SHEET_HEIGHT_MM
    segments: list[ArchitectureSegment] = []
    seen: set[tuple[tuple[float, float], tuple[float, float]]] = set()
    source_entity_count = 0

    for entity_index, entity in enumerate(doc.modelspace()):
        if entity.dxftype() not in {"LINE", "LWPOLYLINE", "POLYLINE"}:
            continue
        points = _entity_points(entity)
        if len(points) < 2:
            continue
        handle = entity.dxf.get("handle", f"generated-{entity_index}")
        intersects_region = False
        for segment_index, (start, end) in enumerate(zip(points, points[1:], strict=False)):
            clipped = _clip_segment(start, end, min_x, min_y, max_x, max_y)
            if clipped is None:
                continue
            intersects_region = True
            if _distance(*clipped) < min_segment_length_mm:
                continue
            clipped_start, clipped_end = clipped
            normalized_start = (
                round((clipped_start[0] - min_x) / 1000.0, 6),
                round((clipped_start[1] - min_y) / 1000.0, 6),
            )
            normalized_end = (
                round((clipped_end[0] - min_x) / 1000.0, 6),
                round((clipped_end[1] - min_y) / 1000.0, 6),
            )
            key = tuple(sorted((normalized_start, normalized_end)))
            if key in seen:
                continue
            seen.add(key)
            segments.append(
                ArchitectureSegment(
                    id=f"architecture-{unit_type.lower()}-{handle.lower()}-{segment_index}",
                    cad_layer=entity.dxf.layer,
                    entity_handle=handle,
                    source_entity_type=entity.dxftype(),
                    start=Point3D(x=normalized_start[0], y=normalized_start[1], z=0),
                    end=Point3D(x=normalized_end[0], y=normalized_end[1], z=0),
                )
            )
        if intersects_region:
            source_entity_count += 1

    unit_code = int(doc.header.get("$INSUNITS", 0))
    return ArchitectureBackgroundResponse(
        filename=Path(filename).name,
        unit_type=unit_type,
        source_units=_unit_name(unit_code),
        source_path_entity_count=source_entity_count,
        rendered_segment_count=len(segments),
        min_segment_length_mm=min_segment_length_mm,
        segments=segments,
    )


def _entity_points(entity: DXFEntity) -> list[tuple[float, float]]:
    if entity.dxftype() == "LINE":
        return [
            (float(entity.dxf.start.x), float(entity.dxf.start.y)),
            (float(entity.dxf.end.x), float(entity.dxf.end.y)),
        ]
    try:
        path = ezpath.make_path(entity)
        return [(float(vertex.x), float(vertex.y)) for vertex in path.flattening(10.0)]
    except (AttributeError, TypeError, ValueError):
        return []


def _belongs_to_region(points: list[tuple[float, float]], region: UnitRegion) -> bool:
    center_x = sum(point[0] for point in points) / len(points)
    center_y = sum(point[1] for point in points) / len(points)
    return (
        region.origin_x_mm <= center_x < region.origin_x_mm + region.width_mm
        and region.origin_y_mm <= center_y < region.origin_y_mm + region.height_mm
    )


def _distance(
    start: tuple[float, float],
    end: tuple[float, float],
) -> float:
    return ((end[0] - start[0]) ** 2 + (end[1] - start[1]) ** 2) ** 0.5


def _clip_segment(
    start: tuple[float, float],
    end: tuple[float, float],
    min_x: float,
    min_y: float,
    max_x: float,
    max_y: float,
) -> tuple[tuple[float, float], tuple[float, float]] | None:
    x1, y1 = start
    x2, y2 = end
    dx = x2 - x1
    dy = y2 - y1
    p = (-dx, dx, -dy, dy)
    q = (x1 - min_x, max_x - x1, y1 - min_y, max_y - y1)
    lower = 0.0
    upper = 1.0

    for edge, distance in zip(p, q, strict=True):
        if edge == 0:
            if distance < 0:
                return None
            continue
        ratio = distance / edge
        if edge < 0:
            lower = max(lower, ratio)
        else:
            upper = min(upper, ratio)
        if lower > upper:
            return None

    return ((x1 + lower * dx, y1 + lower * dy), (x1 + upper * dx, y1 + upper * dy))


def _plain_text(entity: DXFEntity) -> str:
    if entity.dxftype() == "MTEXT":
        return entity.plain_text()
    return str(entity.dxf.get("text", ""))


def _unit_type_from_text(text: str) -> str | None:
    match = UNIT_TITLE_PATTERN.search(text)
    if not match:
        return None
    return f"{match.group('area')}{match.group('variant').upper()}"


def _unit_name(code: int) -> str:
    return {0: "unitless", 4: "millimeter", 6: "meter"}.get(code, f"code-{code}")
