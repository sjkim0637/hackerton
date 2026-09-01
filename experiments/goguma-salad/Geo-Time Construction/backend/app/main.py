from __future__ import annotations

from typing import Annotated

import ezdxf
from fastapi import FastAPI, File, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from app.cad import DEFAULT_CABLE_LAYERS, analyze_drawing, build_cable_objects, read_dxf_upload
from app.models import ConstructionObjectResponse, DrawingAnalysis

app = FastAPI(title="Geo-Time Construction Phase 1 API", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/cad/analyze", response_model=DrawingAnalysis)
def analyze(file: Annotated[UploadFile, File()]) -> DrawingAnalysis:
    doc = _read_upload(file)
    return analyze_drawing(doc, file.filename or "drawing.dxf")


@app.post("/api/cad/construction-objects", response_model=ConstructionObjectResponse)
def construction_objects(
    file: Annotated[UploadFile, File()],
    unit_type: Annotated[str, Query(pattern=r"^\d+[A-Z]$")] = "84A",
    layers: Annotated[list[str] | None, Query()] = None,
    elevation_m: Annotated[float, Query(ge=0, le=20)] = 2.3,
    diameter_m: Annotated[float, Query(gt=0, le=1)] = 0.03,
) -> ConstructionObjectResponse:
    doc = _read_upload(file)
    try:
        return build_cable_objects(
            doc,
            file.filename or "drawing.dxf",
            unit_type=unit_type,
            layers=layers or DEFAULT_CABLE_LAYERS,
            elevation_m=elevation_m,
            diameter_m=diameter_m,
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


def _read_upload(file: UploadFile):
    filename = file.filename or ""
    if not filename.lower().endswith(".dxf"):
        raise HTTPException(status_code=415, detail="Phase 1 accepts DXF files only")
    try:
        return read_dxf_upload(file.file)
    except (OSError, ezdxf.DXFError) as exc:  # type: ignore[name-defined]
        raise HTTPException(status_code=400, detail=f"Invalid DXF: {exc}") from exc
