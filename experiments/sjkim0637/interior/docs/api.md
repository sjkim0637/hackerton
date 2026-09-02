# 앱 ↔ 서버 API 초안

기본 응답 형식은 JSON이며 이미지 업로드는 `multipart/form-data`를 사용한다.

## Scene 생성

`POST /scenes`

```json
{
  "device_id": "demo-device",
  "name": "거실 TV 재배치"
}
```

응답:

```json
{
  "scene_id": "scn_20260902_001",
  "status": "created"
}
```

## Keyframe 업로드

`POST /scenes/{sceneId}/keyframes`

- `image`: JPEG 또는 PNG
- `metadata`: 카메라 Pose, 이미지 크기, 평면 및 Anchor 정보 JSON

## 사물 제거 요청

`POST /scenes/{sceneId}/remove-object`

```json
{
  "keyframe_id": "key_20260902_001",
  "object_type": "tv",
  "region": {
    "type": "bbox",
    "rect": [0.25, 0.30, 0.40, 0.35]
  },
  "prompt": "TV와 그림자를 제거하고 뒤쪽 벽을 자연스럽게 복원"
}
```

응답:

```json
{
  "job_id": "job_20260902_001",
  "status": "queued"
}
```

## 작업 조회

`GET /jobs/{jobId}`

```json
{
  "job_id": "job_20260902_001",
  "status": "succeeded",
  "result_url": "/jobs/job_20260902_001/result",
  "elapsed_ms": 8230
}
```

## 결과 이미지

`GET /jobs/{jobId}/result`

완료된 JPEG 이미지를 반환한다.

## 가구 카탈로그

`GET /catalog?category=sofa`

가구 ID, 이름, 실제 크기, 미리보기 및 3D 모델 경로를 반환한다.

## 오류 원칙

- 잘못된 bbox: `400`
- Scene 또는 Keyframe 없음: `404`
- 호출 제한 초과: `429`
- AI 시간 초과 또는 장애: 작업 상태 `failed`와 사용자 표시용 오류 코드
- 동일 요청은 idempotency key 또는 입력 hash로 중복 실행을 방지한다.
