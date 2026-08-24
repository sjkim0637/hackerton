# Platform Principles

## Retrieval과 Rendering

`Geo + Time`은 최종 콘텐츠 선택이 아니라 네트워크와 DB 후보군을 제한하는 1차 Retrieval이다. 사용자가 실제로 보는 콘텐츠는 후보군에 현재 6DoF Spatial Context를 적용해 정한다.

```text
Geo + Time
  -> Candidate Set with SpatialPlacement
  -> Zone/Session Coordinate Transform
  -> Distance + View Cone + optional Occlusion
  -> ARCore Anchor Rendering
```

## Organic과 Commercial

- Organic: 실제 발생 시간과 생성자를 가진 `Moment`
- Commercial: 미래 또는 현재 Geo × Time Window에 활성화되는 `Campaign`
- Commercial Inventory: `CampaignSchedule`

공통 `Content`와 `SpatialPlacement`를 사용하더라도 활성화, 측정, 권한 규칙은 합치지 않는다.

## 지속 좌표와 세션 좌표

ARCore 원시 Pose는 앱 세션마다 달라질 수 있다. DB에는 안정적인 Zone/Geospatial/Cloud Anchor 좌표만 저장하고, 기기에서 현재 세션 좌표로 변환한다.

## 시간 탐색 UX

사용자가 날짜 범위를 고르는 방식보다 같은 장소에서 기록을 발견하고 자연스럽게 감상하는 경험을 우선한다.

- AR 탐색 화면의 마커는 개별 날짜가 아니라 같은 POI의 `Moment Stack`을 나타낸다.
- 폰에서는 마커 터치, 5초 무음 미리보기, 재생 확인, 전체화면 감상 순서로 진행한다.
- 전체화면에서만 좌우 Swipe로 실제 Moment가 있는 시점 사이를 이동한다.
- 날짜는 마커에 계속 노출하지 않고 재생 시작과 Moment 전환 때만 잠깐 표시한다.
- 향후 Glass는 응시·음성·머리 Gesture를 사용하되 같은 상태 흐름을 유지한다.

핵심 원칙은 “조작법이 아니라 사용자의 의도를 기기 사이에서 통일한다”이다.

## Phone과 Glass 입력 분리

Phone MVP는 터치를 기본 입력으로 사용한다. Glass를 고려한다는 이유로 폰 사용자에게 응시나 고개 Gesture를 강제하지 않는다. 앱의 Mode 전환 버튼으로 Glass 데모를 선택한 경우에만 폰의 ARCore Pose를 Head Pose처럼 사용한다.

| 사용자 의도 | Phone | Glass 확장 방향 |
|---|---|---|
| 기록 선택 | 마커 터치 | 마커 응시 |
| 재생 승인 | 화면 버튼 | 끄덕임 또는 음성 |
| 이전·다음 기록 | 좌우 Swipe | 빠른 좌우 고개 Gesture |
| 콘텐츠 종료 | 아래 Swipe 또는 뒤로가기 | 시선 이탈 또는 음성 |

두 모드는 공통 상태 전이를 사용하고 입력 Adapter만 분리한다. Glass 모드에서도 안전한 공간 인식과 AR 복귀를 위해 6DoF 추적 자체는 계속 유지한다.

Glass 데모의 현재 입력 기준은 다음과 같다.

- 화면 중심 10도 이내 Marker를 5초간 유지하면 무음 미리보기를 시작한다.
- 미리보기 5초 뒤 Pitch 왕복은 재생 승인, Yaw 왕복은 취소로 해석한다.
- 전체 감상 중 빠른 Yaw 왕복은 같은 POI의 이전·다음 Moment 이동으로 해석한다.
- 기준 방향에서 38도 이상 벗어난 상태가 1.5초 지속되면 AR 탐색으로 돌아간다.
- 느린 방향 전환이나 기준 자세로 돌아오지 않은 움직임은 명령으로 확정하지 않는다.
