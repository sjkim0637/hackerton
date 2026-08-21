import math
import uuid

import pytest

from app.spatial import Point3, select_visible


def candidate(position, min_distance=0.0, max_distance=30.0, cone=70.0):
    return (uuid.uuid4(), position, min_distance, max_distance, cone)


def test_select_visible_filters_distance_and_view_cone():
    in_front = candidate(Point3(0, 0, -5))
    too_far = candidate(Point3(0, 0, -40))
    behind = candidate(Point3(0, 0, 5))
    result = select_visible(
        Point3(0, 0, 0),
        Point3(0, 0, -1),
        [in_front, too_far, behind],
    )
    assert [item.id for item in result] == [in_front[0]]
    assert result[0].distance_m == 5.0
    assert result[0].angle_degrees == 0.0


def test_select_visible_uses_half_of_full_cone_angle():
    inside = candidate(Point3(math.tan(math.radians(34)), 0, -1), cone=70)
    outside = candidate(Point3(math.tan(math.radians(36)), 0, -1), cone=70)
    result = select_visible(Point3(0, 0, 0), Point3(0, 0, -1), [outside, inside])
    assert [item.id for item in result] == [inside[0]]


def test_select_visible_sorts_by_angle_then_distance():
    centered_far = candidate(Point3(0, 0, -10))
    off_center_near = candidate(Point3(1, 0, -2))
    centered_near = candidate(Point3(0, 0, -2))
    result = select_visible(
        Point3(0, 0, 0),
        Point3(0, 0, -1),
        [off_center_near, centered_far, centered_near],
    )
    assert [item.id for item in result] == [centered_near[0], centered_far[0], off_center_near[0]]


def test_select_visible_rejects_zero_forward_vector():
    with pytest.raises(ValueError, match="camera_forward must be non-zero"):
        select_visible(Point3(0, 0, 0), Point3(0, 0, 0), [])
