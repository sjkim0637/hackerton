from app.cad import analyze_drawing, build_architecture_background, build_cable_objects


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
    assert response.device_count == 2
    assert {device.properties.subtype for device in response.devices} == {
        "communication_panel",
        "entrance_camera",
    }
    panel = next(
        device for device in response.devices if device.properties.subtype == "communication_panel"
    )
    assert panel.geometry.position.x == 4.765
    assert panel.geometry.position.y == 14.494354
    assert panel.geometry.position.z == 1.4


def test_build_cable_objects_device_elevation_follows_cable_elevation(sample_doc):
    response = build_cable_objects(sample_doc, "sample.dxf", "84A", elevation_m=1.9)

    assert {item.geometry.points[0].z for item in response.objects} == {1.9}
    panel = next(
        device for device in response.devices if device.properties.subtype == "communication_panel"
    )
    assert panel.geometry.position.z == 1.0
    assert panel.properties.elevation_m == 1.0


def test_build_cable_objects_rejects_unknown_unit(sample_doc):
    try:
        build_cable_objects(sample_doc, "sample.dxf", "999Z")
    except ValueError as exc:
        assert "Unknown unit type" in str(exc)
    else:
        raise AssertionError("Expected ValueError")


def test_architecture_background_crops_filters_and_deduplicates(architecture_doc):
    response = build_architecture_background(architecture_doc, "XR-unit.dxf", "84A")

    assert response.source_path_entity_count == 5
    assert response.rendered_segment_count == 4
    assert response.source_units == "millimeter"
    crossing = next(segment for segment in response.segments if segment.start.y == 3)
    assert crossing.start.x == 0
    assert crossing.end.x == 0.2


def test_architecture_background_applies_focus_bounds(architecture_doc):
    response = build_architecture_background(
        architecture_doc,
        "XR-unit.dxf",
        "84A",
        focus_bounds_m=(1.0, 0.5, 2.5, 1.5),
    )

    assert response.rendered_segment_count == 1
    only = response.segments[0]
    assert only.start.x == 1 and only.start.y == 1
    assert only.end.x == 2 and only.end.y == 1


def test_architecture_background_rejects_unknown_unit(architecture_doc):
    try:
        build_architecture_background(architecture_doc, "XR-unit.dxf", "999Z")
    except ValueError as exc:
        assert "Unknown unit type" in str(exc)
    else:
        raise AssertionError("Expected ValueError")
