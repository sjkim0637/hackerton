# API

OpenAPI UI는 `http://localhost:8000/docs`에서 확인한다.

## Health

- `GET /health`: 프로세스 Liveness
- `GET /health/ready`: DB 연결 Readiness

## GeoZone

```http
GET /geozones/nearby?latitude=37.5648801960179&longitude=126.991228638001&radius_m=1000&limit=20
```

위·경도 범위와 최대 반경 50km를 검증하고 거리순으로 반환한다.

## Timeline

```http
GET /geozones/{id}/timeline?from=2024-01-01T00:00:00Z&to=2026-12-31T23:59:59Z&limit=50
```

Moment를 `recorded_at DESC, id` 순으로 반환한다.

## Moment

- `GET /moments/{id}`
- `POST /moments`

POST는 기존 User, GeoZone, Content, SpatialPlacement ID를 요구한다.

## Campaign

```http
GET /geozones/{id}/campaigns/active?at=2026-08-21T05:00:00Z
```

`start_at <= at < end_at`, active 상태를 만족하는 Campaign을 priority 내림차순으로 반환한다.

## 콘텐츠 후보

```http
GET /geozones/{id}/content-candidates?at=2026-08-21T05:00:00Z&moment_window_minutes=5256000
```

Moment와 현재 활성 Campaign을 공통 후보 형태로 반환한다. 최종 표시 여부는 Placement와 현재 6DoF Pose를 사용해 Android에서 결정한다.

## 6DoF 선택 검증 API

```http
POST /spatial/select-visible
Content-Type: application/json

{
  "camera_position": {"x": 0, "y": 0, "z": 0},
  "camera_forward": {"x": 0, "y": 0, "z": -1},
  "candidates": [{
    "id": "00000000-0000-4000-8000-000000000001",
    "position": {"x": 0, "y": 1, "z": -5},
    "max_distance_m": 20,
    "view_cone_degrees": 70
  }]
}
```

Android와 동일한 거리·각도 규칙을 서버에서 독립적으로 확인하기 위한 진단 API다. 운영 렌더링 루프에서 매 프레임 호출하지 않는다.
