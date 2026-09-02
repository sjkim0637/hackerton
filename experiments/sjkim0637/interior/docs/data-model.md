# 데이터 모델 초안

## ID 규칙

| 대상 | 형식 | 예시 |
|---|---|---|
| Scene | `scn_<날짜>_<식별자>` | `scn_20260902_001` |
| Keyframe | `key_<날짜>_<식별자>` | `key_20260902_001` |
| Object | `obj_<날짜>_<식별자>` | `obj_20260902_001` |
| Job | `job_<날짜>_<식별자>` | `job_20260902_001` |
| Furniture | `fur_<종류>_<식별자>` | `fur_sofa_001` |

실제 구현에서는 서버가 충돌하지 않는 UUID를 생성하고 위 접두사로 유형을 구분한다.

## 좌표계

- 이미지 bbox: 왼쪽 위가 원점이며 `[x, y, width, height]` 순서로 저장한다.
- bbox 값: 이미지 크기와 무관한 `0.0~1.0` 정규화 좌표다.
- AR 공간: ARCore world coordinate를 사용하며 길이 단위는 미터다.
- 위치: `[x, y, z]`
- 회전: quaternion `[x, y, z, w]`
- 크기: 실제 미터 단위 `[width, height, depth]`

## 사물 선택 영역

```json
{
  "type": "bbox",
  "rect": [0.25, 0.30, 0.40, 0.35]
}
```

PHASE 1은 bbox만 사용하고 PHASE 2에서 polygon 또는 mask를 추가한다.

## 키프레임

```json
{
  "keyframe_id": "key_20260902_001",
  "scene_id": "scn_20260902_001",
  "image_format": "image/jpeg",
  "width": 1920,
  "height": 1080,
  "captured_at": "2026-09-02T18:00:00+09:00",
  "camera_pose": {
    "position": [0.0, 1.5, 0.0],
    "rotation": [0.0, 0.0, 0.0, 1.0]
  },
  "plane": {
    "type": "vertical",
    "anchor_id": "anchor_wall_001"
  }
}
```

## 가구 정보

```json
{
  "id": "fur_sofa_001",
  "name": "3인용 소파",
  "category": "sofa",
  "size_m": {
    "width": 2.1,
    "height": 0.85,
    "depth": 0.9
  },
  "model_url": "/assets/sofa_001.glb",
  "preview_url": "/assets/sofa_001.jpg"
}
```

## 작업 상태

`queued → processing → succeeded | failed`

작업에는 처리 시간, AI Provider, 오류 내용, 결과 이미지 위치를 함께 기록한다.
