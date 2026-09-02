# Interior AR — Server / Integration

설계서의 **사용자 3 (서버 / 통합)** 작업 흐름. PHASE 1 범위로,
"앱 → 서버 → 외부 AI → 앱" 흐름을 한 번 관통하는 데 필요한 최소 서버다.

- 스택: Python 3.12 / FastAPI, 저장은 로컬 파일 + SQLite (해커톤 범위)
- 형식 근거: `../docs/data-model.md`, `../docs/api.md`

## 구현 범위 (PHASE 1, 사용자 3)

| 항목 | 엔드포인트 / 위치 |
|---|---|
| API 서버 기본 구조 | `app/main.py`, `GET /health`, `/docs` |
| 사용자 작업 세션 생성 | `POST /scenes`, `GET /scenes/{id}` |
| 이미지 업로드 + 대표 이미지 저장 | `POST /scenes/{id}/keyframes` (multipart), `data/scenes/{id}/keyframes/` |
| 사물 정보 저장 (형식만, 인식 없음) | 키프레임 메타의 `targetObject` 또는 `remove-object` 요청 → `objects` 테이블, `GET /scenes/{id}/objects` |
| 외부 AI 연결 구조 | `app/ai/` — `base.RemoveObjectProvider`, `mock`, `external`(자리만), `build_provider()` |
| 사물 제거 + 복원 작업 | `POST /scenes/{id}/remove-object` → job, `GET /scenes/{id}/jobs/{job_id}`, `GET /scenes/{id}/results/{job_id}.jpg` |
| 가구 데이터 API | `GET /catalog`, `GET /catalog/{id}` (`catalog/furniture.json`) |

부가: 동일 `(keyframe_id, target)` 재요청은 캐시된 job 반환, 작업당 외부 AI 호출 상한
(`INTERIOR_MAX_AI_CALLS_PER_SCENE`), 요청 로깅은 uvicorn 기본 액세스 로그 사용.

## 실행

```bash
cd experiments/shinym87/interior/server

python -m venv .venv
. .venv/bin/activate            # Windows PowerShell: .venv\Scripts\Activate.ps1
pip install -r requirements.txt

cp .env.example .env            # 그대로 두면 mock 프로바이더로 동작
uvicorn app.main:app --reload
```

- API 문서: http://127.0.0.1:8000/docs
- 업로드 이미지 / DB / 결과물은 `data/` 아래에 쌓인다 (git 무시).

## 외부 AI 키 넣는 법 (나중에)

지금은 `INTERIOR_AI_PROVIDER=mock` 이라 실제 API 없이 대상 영역을 주변 색으로 덮는다.
실제 외부 AI 를 붙일 때:

1. `.env` 에서
   ```
   INTERIOR_AI_PROVIDER=external
   INTERIOR_AI_API_KEY=<발급받은 키>
   INTERIOR_AI_BASE_URL=<엔드포인트>
   INTERIOR_AI_MODEL=<모델명>
   ```
2. `app/ai/external.py` 의 `remove_object()` 안 `TODO(P1-10)` 블록을 실제 HTTP 호출로 채운다.

라우터·저장·작업 큐 코드는 바꿀 필요가 없다. `build_provider()` 가 설정만 보고
프로바이더를 바꾼다.

## 요청 흐름 예시

```bash
BASE=http://127.0.0.1:8000

SCENE=$(curl -s -XPOST $BASE/scenes -H 'content-type: application/json' -d '{"device":"android"}' | jq -r .scene_id)

KF=$(curl -s -XPOST $BASE/scenes/$SCENE/keyframes \
  -F image=@keyframe.jpg \
  -F 'meta={"imageSize":{"width":1920,"height":1080},"cameraIntrinsics":{"fx":1400,"fy":1400,"cx":960,"cy":540},"worldToCamera":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1],"targetObject":{"objectType":"tv","region":{"type":"bbox","rect":[0.32,0.28,0.24,0.18]}}}' \
  | jq -r .keyframe_id)

JOB=$(curl -s -XPOST $BASE/scenes/$SCENE/remove-object -H 'content-type: application/json' \
  -d "{\"keyframe_id\":\"$KF\",\"object_type\":\"tv\",\"target\":{\"type\":\"bbox\",\"rect\":[0.32,0.28,0.24,0.18]}}" \
  | jq -r .job_id)

curl -s $BASE/scenes/$SCENE/jobs/$JOB | jq
```

## 테스트

```bash
pip install pytest
pytest
```

`tests/test_api.py` 가 세션 생성 → 키프레임 업로드 → 사물 정보 저장 → remove-object
(mock) → 결과 이미지 → 카탈로그 → external 미설정 오류까지 확인한다.

## 알려진 제약 / 다음

- `remove-object` 는 `BackgroundTasks` 로 동기 실행에 가깝게 처리한다. 실제 외부 AI
  연동 시 타임아웃·재시도·별도 워커가 필요하다 (PHASE 2, P2 재시도 정책).
- 사물 인식은 없다. 앱이 보낸 `bbox` 를 그대로 저장/사용한다.
- 인증·멀티유저 격리 없음. 해커톤 단일 사용자 가정.
- `external` 프로바이더 본문 미구현 (P1-10).
