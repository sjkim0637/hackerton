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
    CableProperties,
    ConstructionObject,
    ConstructionObjectResponse,
    DrawingAnalysis,
    LayerSummary,
    Point3D,
    PolylineGeometry,
    SourceReference,
    UnitRegion,
)

SUPPORTED_PATH_TYPES = {"LINE", "LWPOLYLINE", "POLYLINE", "ARC", "SPLINE"}
DEFAULT_CABLE_LAYERS = ("e-wire", "e-wire3s")
SHEET_WIDTH_MM = 42_000.0
SHEET_HEIGHT_MM = 29_700.0
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

    return ConstructionObjectResponse(
        drawing=analysis,
        unit_region=region,
        selected_layers=selected_layers,
        object_count=len(objects),
        objects=objects,
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
