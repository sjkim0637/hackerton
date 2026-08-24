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

**Status: Superseded by ADR-007**

일반 UI Slider는 날짜 범위를 선택하는 편집 도구처럼 보여 현실의 시간을 조작한다는 제품 경험을 약화한다. Android MVP는 Camera 화면의 좌우 Swipe를 사용하고, `NOW`와 실제 Moment가 존재하는 시점에만 Snap한다.

오른쪽 Swipe는 과거 시간층을 현재 화면으로 끌어당겨 더 오래된 Moment로 이동하고, 왼쪽 Swipe는 `NOW` 방향으로 이동한다. Snap 시 햅틱과 날짜를 잠깐 표시하고 AR 콘텐츠를 Fade로 전환한다. 이 시간 이동 의도는 향후 AR Glass의 Gesture와 Voice 입력에서도 재사용한다.

Reality Rewind의 방향 개념은 유지하지만, AR 탐색 화면 전체를 Timeline으로 사용하는 방식은 콘텐츠 발견과 영상 감상을 분리하기 위해 ADR-007로 대체한다.

## ADR-007 — 기기별 입력과 공통 콘텐츠 경험 분리

**Status: Accepted**

폰과 Glass가 같은 조작법을 억지로 사용하지 않는다. 두 기기는 `발견 → 미리보기 → 재생 확인 → 전체 감상 → AR 복귀`라는 사용자 의도와 상태 흐름만 공유하고, 입력 방식은 기기 능력에 맞게 교체한다.

폰에서는 장소별 Moment를 하나의 `시간 기록 N개` 마커로 묶는다. 마커를 터치하면 5초 무음 미리보기를 보여주고, 사용자가 화면에서 승인하면 전체화면으로 재생한다. 전체화면 안에서만 좌우 Swipe로 같은 장소의 이전·다음 기록을 이동하며, 날짜는 영상 재생과 기록 전환 때만 잠깐 표시한다. 아래로 Swipe하거나 뒤로가기를 누르면 같은 AR 탐색 화면으로 돌아온다.

향후 Glass에서는 6DoF 추적을 계속 유지하되 마커 응시, 음성, 끄덕임과 고개 흔들기 같은 입력을 같은 상태 전이에 연결한다. 콘텐츠 집중 화면이 3DoF처럼 안정적으로 보이더라도 실제 ARCore 추적을 3DoF로 낮추지는 않는다. 현재 Phone MVP에는 Head Gesture를 강제하지 않고 입력 교체 지점만 설계 원칙으로 남긴다.
