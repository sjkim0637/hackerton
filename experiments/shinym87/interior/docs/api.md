# API 규격 초안 (PHASE 0)

FastAPI 서버. JSON 응답. 해커톤 범위라 인증 없음. 형식 상세는 `data-model.md` 참고.

## 원칙

- 외부 AI 는 **사용자가 편집 명령을 실행할 때만** 호출한다. 매 프레임/화면 호출 금지.
- 동일 `(keyframe_id, target)` 재요청은 서버 캐시에서 반환한다.
- 작업당 최대 AI 호출 횟수, 하루 총량 상한을 서버 설정값으로 둔다.
- 오래 걸릴 수 있으므로 `remove-object` 는 job 생성 → 폴링 구조를 기본으로 한다.

## 엔드포인트

### 세션

```
POST /scenes
  req:  { "device": "android" }
  res:  { "scene_id": "scene_9f3a1c02" }
```

### 키프레임 업로드

```
POST /scenes/{scene_id}/keyframes        (multipart/form-data)
  parts: image (JPEG), meta (JSON, data-model.md 4번)
  res:   { "keyframe_id": "kf_9f3a1c02_002" }
```

### 사물 제거 + 빈 공간 복원

```
POST /scenes/{scene_id}/remove-object
  req:  {
          "keyframe_id": "kf_9f3a1c02_002",
          "target": { "type": "bbox", "rect": [0.32, 0.28, 0.24, 0.18] },
          "object_type": "tv"
        }
  res:  { "job_id": "job_9f3a1c02_002", "status": "queued" }

GET /scenes/{scene_id}/jobs/{job_id}
  res:  {
          "job_id": "job_9f3a1c02_002",
          "status": "done",
          "result_image_url": "/scenes/.../job_9f3a1c02_002.jpg",
          "changed_region": { "type": "bbox", "rect": [0.30, 0.25, 0.30, 0.24] }
        }
```

`status`: `queued` | `running` | `done` | `failed`. `failed` 시 `error` 문자열 포함.

### 결과 이미지

```
GET /scenes/{scene_id}/results/{job_id}.jpg   -> image/jpeg
```

### 가구 카탈로그

```
GET /catalog?category=sofa      -> [ FurnitureData, ... ]
GET /catalog/{catalog_id}       -> FurnitureData
GET /assets/{file}              -> glb / png (정적 파일)
```

### (선택) 공간 상태 저장 — PHASE 4 이후

```
PUT  /scenes/{scene_id}/state   req: { "instances": [ PlacementInstance, ... ] }
GET  /scenes/{scene_id}/state   res: { "instances": [ ... ] }
```

## 로깅

서버는 요청마다 다음을 기록한다: 시각, `scene_id`, 엔드포인트, AI 호출 여부,
AI 응답 시간(ms), 캐시 hit 여부, 결과 상태. 비용 추정은 AI 호출 횟수 × 단가로 별도 집계.
