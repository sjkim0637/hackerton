import uuid
from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from app.main import app, get_repository
from app.schemas import (
    CampaignRead,
    ContentCandidate,
    ContentRead,
    GeoZoneNearbyRead,
    MomentCreate,
    MomentRead,
    PlacementRead,
)

ZONE_ID = uuid.UUID("10000000-0000-4000-8000-000000000001")
MOMENT_ID = uuid.UUID("10000000-0000-4000-8000-000000000002")
CONTENT_ID = uuid.UUID("10000000-0000-4000-8000-000000000003")
PLACEMENT_ID = uuid.UUID("10000000-0000-4000-8000-000000000004")
USER_ID = uuid.UUID("10000000-0000-4000-8000-000000000005")
CAMPAIGN_ID = uuid.UUID("10000000-0000-4000-8000-000000000006")
NOW = datetime(2026, 8, 21, 5, 0, tzinfo=UTC)


def content() -> ContentRead:
    return ContentRead(
        id=CONTENT_ID,
        content_type="image",
        title="Test content",
        object_key="test/content.png",
        public_url="http://localhost:9000/assets/test/content.png",
        mime_type="image/png",
    )


def placement() -> PlacementRead:
    return PlacementRead(
        id=PLACEMENT_ID,
        geo_zone_id=ZONE_ID,
        poi_id=None,
        anchor_type="zone_local",
        local_x=0,
        local_y=1.5,
        local_z=-4,
        qx=0,
        qy=0,
        qz=0,
        qw=1,
        scale=1,
        min_visible_distance_m=0,
        max_visible_distance_m=30,
        view_cone_degrees=70,
    )


def moment(moment_id: uuid.UUID = MOMENT_ID) -> MomentRead:
    return MomentRead(
        id=moment_id,
        geo_zone_id=ZONE_ID,
        poi_id=None,
        content_id=CONTENT_ID,
        placement_id=PLACEMENT_ID,
        recorded_at=NOW,
        created_by=USER_ID,
        rendering_metadata={"billboard": True},
        created_at=NOW,
        content=content(),
        placement=placement(),
    )


class FakeRepository:
    def __init__(self) -> None:
        self.db = self
        self.timeline_args = None

    def rollback(self) -> None:
        return None

    def nearby_geozones(self, latitude, longitude, radius_m, limit):
        return [
            GeoZoneNearbyRead(
                id=ZONE_ID,
                name="Test Zone",
                description="Test",
                radius_m=500,
                distance_m=12.5,
            )
        ]

    def timeline(self, geo_zone_id, from_at, to_at, limit):
        self.timeline_args = (geo_zone_id, from_at, to_at, limit)
        return [moment()]

    def get_moment(self, moment_id):
        return moment(moment_id) if moment_id == MOMENT_ID else None

    def create_moment(self, payload: MomentCreate):
        return MomentRead(
            id=MOMENT_ID,
            created_at=NOW,
            content=content(),
            placement=placement(),
            **payload.model_dump(),
        )

    def active_campaigns(self, geo_zone_id, at, limit):
        return [
            CampaignRead(
                id=CAMPAIGN_ID,
                brand="Test Brand",
                title="Test Campaign",
                content=content(),
                placement=placement(),
                priority=10,
                start_at=at - timedelta(hours=1),
                end_at=at + timedelta(hours=1),
            )
        ]

    def content_candidates(self, geo_zone_id, at, moment_window_minutes, limit):
        return [
            ContentCandidate(
                source_type="moment",
                source_id=MOMENT_ID,
                content=content(),
                placement=placement(),
                occurred_at=NOW,
            )
        ]


@pytest.fixture
def fake_repository() -> FakeRepository:
    return FakeRepository()


@pytest.fixture
def client(fake_repository: FakeRepository):
    app.dependency_overrides[get_repository] = lambda: fake_repository
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()
