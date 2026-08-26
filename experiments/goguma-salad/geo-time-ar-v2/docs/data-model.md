# Data Model

| Entity | 역할 | 주요 관계 |
|---|---|---|
| User | 콘텐츠 생성 사용자 | Moment 생성자 |
| GeoZone | 공간 검색 단위 | POI, Moment, Campaign 포함 |
| POI | Zone 내부 관심 지점 | 선택적 Placement 기준점 |
| SurveyControlPoint | 공개 국가기준점 성과 Cache | POI 측량 기준과 근접 기준점 조회 |
| Content | Object Storage Asset 메타데이터 | Moment/Campaign이 참조 |
| SpatialPlacement | 안정 좌표계상의 위치·회전·가시성 규칙 | Moment/Campaign이 참조 |
| Moment | Organic 콘텐츠와 기록 시간 | GeoZone + Content + Placement |
| Campaign | Commercial 콘텐츠 | Schedule로 활성화 |
| CampaignSchedule | Geo × Time Window와 priority | Campaign 활성화 규칙 |
| SpatialImpression | Campaign 노출 측정 골격 | Campaign/User/GeoZone 참조 |

## 공간 타입

- `GeoZone.center_point`: `geography(Point, 4326)`
- `GeoZone.geometry`: 선택적 `geometry(MultiPolygon, 4326)`
- `POI.location`: `geography(Point, 4326)`
- `POI.ellipsoid_height_m`: WGS84 타원체고, Android GNSS 고도와 동일 기준일 때 수직 상대 위치 계산에 사용
- `POI.orthometric_height_m`: 국가 수준망·지오이드 기준 표고, 지면 높이 보정용
- 근접 검색: `ST_DWithin`
- 거리 정렬: `ST_Distance`
- `SurveyControlPoint.location`: `geography(Point, 4326)`, 현재 위치 기준 거리순 선택

## SpatialPlacement

- `local_x`, `local_y`, `local_z`: Zone-local AR 좌표 (`+X 동쪽, +Y 위쪽, -Z 북쪽`)
- `qx`, `qy`, `qz`, `qw`: 콘텐츠 방향 Quaternion
- `scale`: 렌더링 크기 배율
- `min_visible_distance_m`, `max_visible_distance_m`: 거리 조건
- `view_cone_degrees`: 카메라 전방을 중심으로 한 전체 허용 각도

`POI.location`은 국가기준점 성과를 이용해 산출한 절대좌표를 저장할 수 있다. 기준점 원본과 측량 이력은 향후 별도 Import/Provenance 계층으로 확장하며 ARCore Session Pose는 DB에 저장하지 않는다.

`SurveyControlPoint`는 내부 이름, 종류, WGS84 위치, 타원체고, 표고, 지오이드고와 사용 상태를 가진다. 현재 Seed는 원본 점명 대신 `기준좌표 1`, `기준좌표 2`를 사용하고 출처 문서를 저장하지 않는다.

`TimeLayer`는 중복 저장 테이블로 만들지 않고 Moment와 Campaign을 시간 조건으로 합성하는 Query/응답 개념으로 시작한다.
