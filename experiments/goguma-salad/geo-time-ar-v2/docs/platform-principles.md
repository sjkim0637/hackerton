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

사용자가 날짜 범위를 고르는 방식보다 같은 현실 공간의 시간을 직접 되감는 경험을 우선한다.

- Android App의 Timeline은 `NOW`와 실제 Moment 시점으로 구성한다.
- 빈 날짜는 건너뛰고 Moment가 존재하는 시점에만 Snap한다.
- Camera 화면과 공간 Anchor는 유지하고 시간에 해당하는 콘텐츠만 전환한다.
- 스마트폰에서는 좌우 Rewind Gesture, 향후 AR Glass에서는 Gesture·Voice를 같은 시간 이동 명령에 연결한다.

핵심 원칙은 “Feed를 넘기는 것이 아니라 같은 현실 공간의 시간을 되감는다”이다.
