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
