from app.cad import analyze_drawing, build_cable_objects


def test_analyze_detects_all_unit_regions(sample_doc):
    analysis = analyze_drawing(sample_doc, "sample.dxf")

    assert analysis.source_units == "millimeter"
    assert [region.unit_type for region in analysis.unit_regions] == [
        "84A",
        "84B",
        "84C",
        "84D",
        "120A",
        "144P",
        "155P",
    ]
    assert analysis.unit_regions[0].origin_x_mm == 1_235


def test_build_cable_objects_crops_and_normalizes_84a(sample_doc):
    response = build_cable_objects(sample_doc, "sample.dxf", "84A")

    assert response.object_count == 2
    assert {item.source.cad_layer for item in response.objects} == {"e-wire", "e-wire3s"}
    first = next(item for item in response.objects if item.source.cad_layer == "e-wire")
    assert first.category == "communication"
    assert first.type == "cable_path"
    assert first.geometry.points[0].x == 3.765
    assert first.geometry.points[0].y == 15.494354
    assert first.geometry.points[0].z == 2.3


def test_build_cable_objects_rejects_unknown_unit(sample_doc):
    try:
        build_cable_objects(sample_doc, "sample.dxf", "999Z")
    except ValueError as exc:
        assert "Unknown unit type" in str(exc)
    else:
        raise AssertionError("Expected ValueError")
