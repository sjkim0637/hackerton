from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    assert client.get("/health").json() == {"status": "ok"}


def test_analyze_upload(sample_dxf_bytes):
    response = client.post(
        "/api/cad/analyze",
        files={"file": ("sample.dxf", sample_dxf_bytes, "application/dxf")},
    )

    assert response.status_code == 200, response.text
    assert response.json()["source_units"] == "millimeter"


def test_construction_object_upload(sample_dxf_bytes):
    response = client.post(
        "/api/cad/construction-objects?unit_type=84A&layers=e-wire&layers=e-wire3s",
        files={"file": ("sample.dxf", sample_dxf_bytes, "application/dxf")},
    )

    assert response.status_code == 200, response.text
    assert response.json()["object_count"] == 2
    assert response.json()["device_count"] == 2


def test_rejects_dwg_upload():
    response = client.post(
        "/api/cad/analyze",
        files={"file": ("sample.dwg", b"AC1024", "application/acad")},
    )

    assert response.status_code == 415


def test_architecture_background_upload(architecture_dxf_bytes):
    response = client.post(
        "/api/cad/architecture-background?unit_type=84A&min_segment_length_mm=100",
        files={"file": ("XR-unit.dxf", architecture_dxf_bytes, "application/dxf")},
    )

    assert response.status_code == 200, response.text
    assert response.json()["rendered_segment_count"] == 4


def test_architecture_background_upload_with_focus_bounds(architecture_dxf_bytes):
    response = client.post(
        "/api/cad/architecture-background"
        "?unit_type=84A&focus_min_x=1.0&focus_min_y=0.5&focus_max_x=2.5&focus_max_y=1.5",
        files={"file": ("XR-unit.dxf", architecture_dxf_bytes, "application/dxf")},
    )

    assert response.status_code == 200, response.text
    assert response.json()["rendered_segment_count"] == 1
