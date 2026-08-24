import math
from dataclasses import dataclass


@dataclass(frozen=True)
class Point3:
    x: float
    y: float
    z: float


@dataclass(frozen=True)
class VisibilityResult:
    id: object
    distance_m: float
    angle_degrees: float


def _length(vector: Point3) -> float:
    return math.sqrt(vector.x**2 + vector.y**2 + vector.z**2)


def select_visible(
    camera_position: Point3,
    camera_forward: Point3,
    candidates: list[tuple[object, Point3, float, float, float]],
) -> list[VisibilityResult]:
    """Select candidates inside each distance range and centered view cone.

    Coordinates must already share the same stable frame, normally a Zone-local AR
    frame (+X east, +Y up, -Z north). ARCore session coordinates must be transformed first.
    """
    forward_length = _length(camera_forward)
    if forward_length <= 1e-8:
        raise ValueError("camera_forward must be non-zero")

    forward = Point3(
        camera_forward.x / forward_length,
        camera_forward.y / forward_length,
        camera_forward.z / forward_length,
    )
    visible: list[VisibilityResult] = []

    for candidate_id, position, min_distance, max_distance, cone_degrees in candidates:
        offset = Point3(
            position.x - camera_position.x,
            position.y - camera_position.y,
            position.z - camera_position.z,
        )
        distance = _length(offset)
        if distance < min_distance or distance > max_distance:
            continue
        if distance <= 1e-8:
            angle = 0.0
        else:
            dot = (forward.x * offset.x + forward.y * offset.y + forward.z * offset.z) / distance
            angle = math.degrees(math.acos(max(-1.0, min(1.0, dot))))
        if angle <= cone_degrees / 2.0:
            visible.append(
                VisibilityResult(
                    id=candidate_id,
                    distance_m=round(distance, 4),
                    angle_degrees=round(angle, 4),
                )
            )

    return sorted(visible, key=lambda item: (item.angle_degrees, item.distance_m, str(item.id)))
