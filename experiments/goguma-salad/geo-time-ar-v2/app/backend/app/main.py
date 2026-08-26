import uuid
from datetime import UTC, datetime

from fastapi import Depends, FastAPI, HTTPException, Query, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.config import get_settings
from app.database import get_db
from app.repository import PostgresRepository
from app.schemas import (
    CampaignRead,
    ContentCandidate,
    GeoZoneNearbyRead,
    HealthRead,
    MomentCreate,
    MomentRead,
    PoiRead,
    SurveyControlPointRead,
    VisibilityRequest,
    VisibilityResponse,
    VisibleCandidate,
)
from app.spatial import Point3, select_visible

settings = get_settings()
APP_VERSION = "0.1.0"
app = FastAPI(
    title="Geo-Time AR Platform API",
    version=APP_VERSION,
    description="Geo + Time candidate retrieval with 6DoF spatial visibility selection.",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def get_repository(db: Session = Depends(get_db)) -> PostgresRepository:
    return PostgresRepository(db)


@app.get("/health", response_model=HealthRead, tags=["health"])
def health() -> HealthRead:
    return HealthRead(status="ok", environment=settings.app_env, version=APP_VERSION)


@app.get("/health/ready", response_model=HealthRead, tags=["health"])
def readiness(db: Session = Depends(get_db)) -> HealthRead:
    db.execute(text("SELECT 1"))
    return HealthRead(status="ready", environment=settings.app_env, version=APP_VERSION)


@app.get("/geozones/nearby", response_model=list[GeoZoneNearbyRead], tags=["geo"])
def nearby_geozones(
    latitude: float = Query(ge=-90, le=90),
    longitude: float = Query(ge=-180, le=180),
    radius_m: float = Query(default=1000, gt=0, le=50_000),
    limit: int = Query(default=20, ge=1, le=100),
    repository: PostgresRepository = Depends(get_repository),
) -> list[GeoZoneNearbyRead]:
    return repository.nearby_geozones(latitude, longitude, radius_m, limit)


@app.get("/geozones/{geo_zone_id}/pois", response_model=list[PoiRead], tags=["geo"])
def geozone_pois(
    geo_zone_id: uuid.UUID,
    limit: int = Query(default=100, ge=1, le=500),
    repository: PostgresRepository = Depends(get_repository),
) -> list[PoiRead]:
    return repository.pois(geo_zone_id, limit)


@app.get(
    "/control-points/nearest",
    response_model=list[SurveyControlPointRead],
    tags=["geo"],
)
def nearest_control_points(
    latitude: float = Query(ge=-90, le=90),
    longitude: float = Query(ge=-180, le=180),
    radius_m: float = Query(default=50_000, gt=0, le=100_000),
    limit: int = Query(default=2, ge=1, le=20),
    repository: PostgresRepository = Depends(get_repository),
) -> list[SurveyControlPointRead]:
    return repository.nearest_control_points(latitude, longitude, radius_m, limit)


@app.get("/geozones/{geo_zone_id}/timeline", response_model=list[MomentRead], tags=["time"])
def timeline(
    geo_zone_id: uuid.UUID,
    from_at: datetime | None = Query(default=None, alias="from"),
    to_at: datetime | None = Query(default=None, alias="to"),
    limit: int = Query(default=50, ge=1, le=200),
    repository: PostgresRepository = Depends(get_repository),
) -> list[MomentRead]:
    if from_at and to_at and from_at > to_at:
        raise HTTPException(status_code=422, detail="from must be before or equal to to")
    return repository.timeline(geo_zone_id, from_at, to_at, limit)


@app.get("/moments/{moment_id}", response_model=MomentRead, tags=["moments"])
def get_moment(
    moment_id: uuid.UUID,
    repository: PostgresRepository = Depends(get_repository),
) -> MomentRead:
    moment = repository.get_moment(moment_id)
    if moment is None:
        raise HTTPException(status_code=404, detail="Moment not found")
    return moment


@app.post(
    "/moments",
    response_model=MomentRead,
    status_code=status.HTTP_201_CREATED,
    tags=["moments"],
)
def create_moment(
    payload: MomentCreate,
    repository: PostgresRepository = Depends(get_repository),
) -> MomentRead:
    try:
        return repository.create_moment(payload)
    except IntegrityError as exc:
        repository.db.rollback()
        raise HTTPException(status_code=409, detail="Referenced resource does not exist") from exc


@app.get(
    "/geozones/{geo_zone_id}/campaigns/active",
    response_model=list[CampaignRead],
    tags=["campaigns"],
)
def active_campaigns(
    geo_zone_id: uuid.UUID,
    at: datetime | None = None,
    limit: int = Query(default=20, ge=1, le=100),
    repository: PostgresRepository = Depends(get_repository),
) -> list[CampaignRead]:
    return repository.active_campaigns(geo_zone_id, at or datetime.now(UTC), limit)


@app.get(
    "/geozones/{geo_zone_id}/content-candidates",
    response_model=list[ContentCandidate],
    tags=["spatial-content"],
)
def content_candidates(
    geo_zone_id: uuid.UUID,
    at: datetime | None = None,
    moment_window_minutes: int = Query(default=525_600, ge=1, le=5_256_000),
    limit: int = Query(default=100, ge=1, le=500),
    repository: PostgresRepository = Depends(get_repository),
) -> list[ContentCandidate]:
    return repository.content_candidates(
        geo_zone_id,
        at or datetime.now(UTC),
        moment_window_minutes,
        limit,
    )


@app.post(
    "/spatial/select-visible",
    response_model=VisibilityResponse,
    tags=["spatial-content"],
)
def visible_content(payload: VisibilityRequest) -> VisibilityResponse:
    try:
        visible = select_visible(
            Point3(**payload.camera_position.model_dump()),
            Point3(**payload.camera_forward.model_dump()),
            [
                (
                    candidate.id,
                    Point3(**candidate.position.model_dump()),
                    candidate.min_distance_m,
                    candidate.max_distance_m,
                    candidate.view_cone_degrees,
                )
                for candidate in payload.candidates
            ],
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return VisibilityResponse(
        visible=[
            VisibleCandidate(
                id=item.id,
                distance_m=item.distance_m,
                angle_degrees=item.angle_degrees,
            )
            for item in visible
        ]
    )
