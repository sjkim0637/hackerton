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


def test_rejects_dwg_upload():
    response = client.post(
        "/api/cad/analyze",
        files={"file": ("sample.dwg", b"AC1024", "application/acad")},
    )

    assert response.status_code == 415
