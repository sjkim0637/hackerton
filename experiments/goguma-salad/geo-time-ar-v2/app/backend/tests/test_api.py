from datetime import UTC, datetime

from conftest import (
    CAMPAIGN_ID,
    CONTENT_ID,
    MOMENT_ID,
    NOW,
    PLACEMENT_ID,
    USER_ID,
    ZONE_ID,
)


def test_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["version"] == "0.1.0"


def test_nearby_geozones(client):
    response = client.get(
        "/geozones/nearby",
        params={"latitude": 37.5665, "longitude": 126.978, "radius_m": 1000},
    )
    assert response.status_code == 200
    assert response.json()[0]["name"] == "Test Zone"
    assert response.json()[0]["distance_m"] == 12.5


def test_nearby_rejects_invalid_latitude(client):
    response = client.get("/geozones/nearby", params={"latitude": 91, "longitude": 126.978})
    assert response.status_code == 422


def test_geozone_pois_include_absolute_position_and_elevation(client):
    response = client.get(f"/geozones/{ZONE_ID}/pois")

    assert response.status_code == 200
    poi = response.json()[0]
    assert poi["latitude"] == 37.5648801960179
    assert poi["longitude"] == 126.991228638001
    assert poi["ellipsoid_height_m"] == 52.4
    assert poi["orthometric_height_m"] == 29.1


def test_nearest_control_points_returns_available_points_by_distance(client):
    response = client.get(
        "/control-points/nearest",
        params={"latitude": 37.5648801960179, "longitude": 126.991228638001},
    )

    assert response.status_code == 200
    assert [point["id"] for point in response.json()] == ["기준좌표 1", "기준좌표 2"]
    assert response.json()[0]["ellipsoid_height_m"] == 83.4359


def test_timeline_passes_filters(client, fake_repository):
    from_at = "2025-01-01T00:00:00Z"
    to_at = "2026-01-01T00:00:00Z"
    response = client.get(
        f"/geozones/{ZONE_ID}/timeline",
        params={"from": from_at, "to": to_at, "limit": 10},
    )
    assert response.status_code == 200
    assert response.json()[0]["id"] == str(MOMENT_ID)
    _, actual_from, actual_to, actual_limit = fake_repository.timeline_args
    assert actual_from == datetime(2025, 1, 1, tzinfo=UTC)
    assert actual_to == datetime(2026, 1, 1, tzinfo=UTC)
    assert actual_limit == 10


def test_timeline_rejects_reversed_range(client):
    response = client.get(
        f"/geozones/{ZONE_ID}/timeline",
        params={"from": "2026-01-02T00:00:00Z", "to": "2026-01-01T00:00:00Z"},
    )
    assert response.status_code == 422
    assert response.json()["detail"] == "from must be before or equal to to"


def test_get_missing_moment(client):
    response = client.get("/moments/20000000-0000-4000-8000-000000000001")
    assert response.status_code == 404


def test_create_moment(client):
    response = client.post(
        "/moments",
        json={
            "geo_zone_id": str(ZONE_ID),
            "content_id": str(CONTENT_ID),
            "placement_id": str(PLACEMENT_ID),
            "recorded_at": NOW.isoformat(),
            "created_by": str(USER_ID),
            "rendering_metadata": {"billboard": True},
        },
    )
    assert response.status_code == 201
    assert response.json()["id"] == str(MOMENT_ID)


def test_active_campaign(client):
    response = client.get(f"/geozones/{ZONE_ID}/campaigns/active", params={"at": NOW.isoformat()})
    assert response.status_code == 200
    assert response.json()[0]["id"] == str(CAMPAIGN_ID)
    assert response.json()[0]["priority"] == 10


def test_content_candidates_include_spatial_placement(client):
    response = client.get(f"/geozones/{ZONE_ID}/content-candidates")
    assert response.status_code == 200
    candidate = response.json()[0]
    assert candidate["source_type"] == "moment"
    assert candidate["placement"]["local_z"] == -4


def test_visibility_endpoint_selects_only_content_in_view(client):
    visible_id = "30000000-0000-4000-8000-000000000001"
    behind_id = "30000000-0000-4000-8000-000000000002"
    response = client.post(
        "/spatial/select-visible",
        json={
            "camera_position": {"x": 0, "y": 0, "z": 0},
            "camera_forward": {"x": 0, "y": 0, "z": -1},
            "candidates": [
                {
                    "id": visible_id,
                    "position": {"x": 0, "y": 0, "z": -5},
                    "max_distance_m": 10,
                    "view_cone_degrees": 70,
                },
                {
                    "id": behind_id,
                    "position": {"x": 0, "y": 0, "z": 5},
                    "max_distance_m": 10,
                    "view_cone_degrees": 70,
                },
            ],
        },
    )
    assert response.status_code == 200
    assert response.json() == {
        "visible": [{"id": visible_id, "distance_m": 5.0, "angle_degrees": 0.0}]
    }


def test_visibility_endpoint_rejects_zero_forward_vector(client):
    response = client.post(
        "/spatial/select-visible",
        json={
            "camera_position": {"x": 0, "y": 0, "z": 0},
            "camera_forward": {"x": 0, "y": 0, "z": 0},
            "candidates": [],
        },
    )
    assert response.status_code == 422
    assert response.json()["detail"] == "camera_forward must be non-zero"
