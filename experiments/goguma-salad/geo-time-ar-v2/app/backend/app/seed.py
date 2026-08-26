import uuid
from datetime import UTC, datetime, timedelta

from geoalchemy2.elements import WKTElement

from app.config import get_settings
from app.database import SessionLocal
from app.models import (
    POI,
    Campaign,
    CampaignSchedule,
    Content,
    GeoZone,
    Moment,
    SpatialPlacement,
    SurveyControlPoint,
    User,
)

DEMO_ZONE_ID = uuid.UUID("00000000-0000-4000-8000-000000000101")
DEMO_POI_ID = uuid.UUID("00000000-0000-4000-8000-000000000301")
DEMO_POINT_WKT = "POINT(126.991228638001 37.5648801960179)"
DEMO_USER_IDS = [
    uuid.UUID("00000000-0000-4000-8000-000000000201"),
    uuid.UUID("00000000-0000-4000-8000-000000000202"),
    uuid.UUID("00000000-0000-4000-8000-000000000203"),
]
DEMO_CONTROL_POINTS = [
    {
        "id": "기준좌표 1",
        "latitude": 37.55735084722222,
        "longitude": 126.99438868888889,
        "ellipsoid_height_m": 83.4359,
        "orthometric_height_m": 60.0883,
        "geoid_height_m": 23.364,
        "status": "available",
    },
    {
        "id": "기준좌표 2",
        "latitude": 37.566232525,
        "longitude": 126.97020440555556,
        "ellipsoid_height_m": 68.0229,
        "orthometric_height_m": 44.7049,
        "geoid_height_m": 23.3031,
        "status": "available",
    },
]


def demo_location() -> WKTElement:
    return WKTElement(DEMO_POINT_WKT, srid=4326)


def seed_control_points(db) -> None:
    for item in DEMO_CONTROL_POINTS:
        point_id = item["id"]
        db.merge(
            SurveyControlPoint(
                id=point_id,
                point_type="integrated",
                location=WKTElement(
                    f"POINT({item['longitude']} {item['latitude']})",
                    srid=4326,
                ),
                ellipsoid_height_m=item["ellipsoid_height_m"],
                orthometric_height_m=item["orthometric_height_m"],
                geoid_height_m=item["geoid_height_m"],
                status=item["status"],
                source_document=None,
            )
        )


def seed() -> None:
    settings = get_settings()
    with SessionLocal() as db:
        seed_control_points(db)
        existing_zone = db.get(GeoZone, DEMO_ZONE_ID)
        if existing_zone is not None:
            existing_zone.name = "을지로 타워 107"
            existing_zone.description = "Geo-Time AR development zone at Tower 107, Euljiro"
            existing_zone.center_point = demo_location()
            existing_poi = db.get(POI, DEMO_POI_ID)
            if existing_poi is not None:
                existing_poi.name = "타워 107"
                existing_poi.poi_type = "office"
                existing_poi.location = demo_location()
            db.commit()
            print("Existing demo zone updated")
            return

        users = [
            User(id=user_id, display_name=f"Demo User {index}", email=f"demo{index}@example.com")
            for index, user_id in enumerate(DEMO_USER_IDS, start=1)
        ]
        zone = GeoZone(
            id=DEMO_ZONE_ID,
            name="을지로 타워 107",
            description="Geo-Time AR development zone at Tower 107, Euljiro",
            center_point=demo_location(),
            radius_m=500.0,
        )
        poi = POI(
            id=DEMO_POI_ID,
            geo_zone_id=zone.id,
            name="타워 107",
            poi_type="office",
            location=demo_location(),
            metadata_={"coordinate_frame": "zone_local_ar_x_east_y_up_minus_z_north"},
        )
        db.add_all([*users, zone, poi])
        db.flush()

        now = datetime.now(UTC)
        moments: list[Moment] = []
        for index in range(8):
            content = Content(
                id=uuid.UUID(f"00000000-0000-4000-8000-{401 + index:012d}"),
                content_type="image",
                title=f"Demo Moment {index + 1}",
                object_key="demo/placeholder.svg",
                public_url=(
                    f"{settings.minio_public_endpoint}/{settings.minio_bucket}/"
                    "demo/placeholder.svg"
                ),
                mime_type="image/svg+xml",
                metadata_={"placeholder": True},
            )
            placement = SpatialPlacement(
                id=uuid.UUID(f"00000000-0000-4000-8000-{501 + index:012d}"),
                geo_zone_id=zone.id,
                poi_id=poi.id,
                local_x=float((index % 3) * 2 - 2),
                local_y=1.4,
                local_z=float(-3 - index),
                max_visible_distance_m=30.0,
                view_cone_degrees=80.0,
            )
            moment = Moment(
                id=uuid.UUID(f"00000000-0000-4000-8000-{601 + index:012d}"),
                geo_zone_id=zone.id,
                poi_id=poi.id,
                content_id=content.id,
                placement_id=placement.id,
                recorded_at=now - timedelta(days=365 * (7 - index)),
                created_by=users[index % len(users)].id,
                rendering_metadata={"billboard": True},
            )
            db.add_all([content, placement])
            moments.append(moment)
        db.add_all(moments)

        campaign_content = Content(
            id=uuid.UUID("00000000-0000-4000-8000-000000000701"),
            content_type="image",
            title="Demo Campaign Object",
            object_key="demo/placeholder.svg",
            public_url=(
                f"{settings.minio_public_endpoint}/{settings.minio_bucket}/"
                "demo/placeholder.svg"
            ),
            mime_type="image/svg+xml",
            metadata_={"placeholder": True},
        )
        campaign_placement = SpatialPlacement(
            id=uuid.UUID("00000000-0000-4000-8000-000000000702"),
            geo_zone_id=zone.id,
            poi_id=poi.id,
            local_x=0.0,
            local_y=1.0,
            local_z=-5.0,
            max_visible_distance_m=20.0,
            view_cone_degrees=60.0,
        )
        campaign = Campaign(
            id=uuid.UUID("00000000-0000-4000-8000-000000000703"),
            brand="Geo-Time Demo",
            title="Active Demo Campaign",
            content_id=campaign_content.id,
            geo_zone_id=zone.id,
            placement_id=campaign_placement.id,
        )
        schedule = CampaignSchedule(
            id=uuid.UUID("00000000-0000-4000-8000-000000000704"),
            campaign_id=campaign.id,
            geo_zone_id=zone.id,
            start_at=now - timedelta(days=30),
            end_at=now + timedelta(days=365),
            priority=100,
            status="active",
        )
        db.add_all([campaign_content, campaign_placement, campaign, schedule])
        db.commit()
        print("Seed data created")


if __name__ == "__main__":
    seed()
