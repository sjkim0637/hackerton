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

## ADR-006 — Time Slider 대신 Reality Rewind Gesture

**Status: Accepted**

일반 UI Slider는 날짜 범위를 선택하는 편집 도구처럼 보여 현실의 시간을 조작한다는 제품 경험을 약화한다. Android MVP는 Camera 화면의 좌우 Swipe를 사용하고, `NOW`와 실제 Moment가 존재하는 시점에만 Snap한다.

왼쪽 Swipe는 더 오래된 Moment, 오른쪽 Swipe는 `NOW` 방향으로 이동한다. Snap 시 햅틱과 날짜를 잠깐 표시하고 AR 콘텐츠를 Fade로 전환한다. 이 시간 이동 의도는 향후 AR Glass의 Gesture와 Voice 입력에서도 재사용한다.
