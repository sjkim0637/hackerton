# Architecture Decisions

## ADR-001 — 후보 검색과 가시성 선택 분리

Backend는 `Geo + Time` 후보를 반환하고 Android는 6DoF로 최종 선택한다. 매 Frame마다 서버에 Pose를 전송하지 않아 지연, 네트워크 비용, 개인정보 노출을 줄인다.

## ADR-002 — SpatialPlacement 도입

`rendering_metadata` JSON만으로 핵심 공간 조건을 숨기지 않고 위치, Quaternion, 거리, View Cone을 명시적 Entity로 관리한다.

## ADR-003 — TimeLayer는 Query 모델

Moment와 Campaign 원본을 중복 저장하지 않는다. 필요성이 확인될 때 Materialized View 또는 별도 Projection을 검토한다.

## ADR-004 — PostgreSQL Geography 사용

위·경도 근접 거리를 미터 단위로 직접 처리하기 위해 GeoZone 중심과 POI에 Geography를 사용한다. Polygon은 Geometry로 선택 지원한다.

## ADR-005 — Android의 Zone-local MVP

초기 빌드는 콘텐츠 선택과 Anchor 렌더링을 검증하기 위해 Zone/Session 변환을 Identity로 둔다. 현장 영속 배치 단계에서 Calibration 전략을 별도 추가한다.
