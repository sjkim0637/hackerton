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
| 복원 결과 버전 목록 | `GET /scenes/{id}/results` — scene 의 모든 job 을 최신순으로 (정리된 버전은 `available:false`). `removed_object_image_url` = 제거된 사물 크롭 |
| 제거된 사물 크롭 | `GET /scenes/{id}/results/{job}_object.jpg` — 원본 키프레임에서 bbox 만 잘라낸 것 (AI 없음, 이동 배치 재사용용) |
| 재배치 상태 저장 | `POST /scenes/{id}/placements` (이동/회전/크기), `GET /scenes/{id}/placements`, `POST /scenes/{id}/placements/undo` |
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

## 외부 AI: Google Gemini (`INTERIOR_AI_PROVIDER=external`)

기본값은 `mock`(Pillow 로 영역을 벽 색으로 덮음)이다. 실제 AI 로 바꾸려면 `.env` 에:

```
INTERIOR_AI_PROVIDER=external
INTERIOR_AI_API_KEY=<Google AI Studio 에서 발급한 Gemini API 키>
# 선택: 못 쓰는 모델이면 바꾼다 (기본 gemini-3.1-flash-image)
INTERIOR_AI_MODEL=gemini-3.1-flash-image
```

서버를 재시작하면 `build_provider()` 가 설정만 보고 `ExternalRemoveObjectProvider`
(`app/ai/external.py`)를 쓴다. 라우터·저장·작업 큐 코드는 그대로다.

동작: 키프레임 이미지 + bbox 로 만든 흑백 마스크 PNG + 사물 종류별 지시문을 Gemini
`:generateContent` 에 보내고, 응답 파트에서 이미지(base64)를 받아 JPEG 로 재인코딩한다.
`object_type` 은 서버에서 정규화한다 (`couch→sofa`, `desk→table`, `television→tv` …).

실패는 `job.status="failed"` + `error` 메시지로 나온다. **네트워크·타임아웃·429·5xx 는
`INTERIOR_AI_MAX_RETRIES`(기본 1) 회 자동 재시도**(`INTERIOR_AI_RETRY_BACKOFF_SECONDS` 대기),
인증(401/403)·400·404·안전차단·이미지없음 은 재시도 없이 바로 실패.

동일 `(keyframe_id, region, object_type)` 요청은 **완료됐거나 처리 중이면** 그 job 을
그대로 돌려준다(중복 AI 호출 방지). 서버 로그에 매 AI 호출마다 scene 누적 횟수와
`INTERIOR_AI_COST_PER_CALL_USD`(기본 0.039) 기준 예상 비용을 남긴다.

**결과 후처리** (`app/ai/colormatch.py`, `_run_job` 에서 자동):
- 색감 보정 — 원본의 **마스크 밖** 평균 밝기/색상에 맞춰 결과에 채널 게인을 곱한다
  (노출/화이트밸런스 수준). `INTERIOR_RESULT_COLOR_MATCH=false` 로 끌 수 있다.
- 이상 결과 감지 — 마스크 밖 영역이 원본과 크게 다르면(`INTERIOR_RESULT_ANOMALY_FAIL_MAD`
  기본 55) job 을 `failed` 로, 다소 다르면(`..._WARN_MAD` 22) 경고 로그. 결과 해상도가
  64px 미만이어도 fail.

**결과 관리** (`app/cleanup.py`, `app/ai/imageops.cap_jpeg_bytes`):
- 버전 관리 — 결과는 `data/scenes/{scene}/results/{job}.jpg` 로 job 마다 분리 저장되어
  덮어써지지 않는다. `GET /scenes/{id}/results` 로 전체 버전을 최신순 조회.
- 임시 저장 정리 — scene 당 최근 `INTERIOR_RESULT_KEEP_PER_SCENE`(기본 12)개만 남기고
  나머지는 결과 생성 시마다 파일 삭제. 서버 기동 시 `INTERIOR_RESULT_MAX_AGE_HOURS`
  (기본 72)보다 오래된 결과 파일도 삭제. **파일만** 지우고 job 기록은 남으며,
  지워진 버전의 이미지 요청은 410. 각 값 `0` = 해당 정리 안 함.
- 폰 전달 최적화 — 결과 JPEG 이 `INTERIOR_RESULT_MAX_BYTES`(기본 5MB)를 넘으면
  품질만 낮춰 재인코딩(해상도 유지).
- 제거된 사물 크롭 — `INTERIOR_SAVE_REMOVED_OBJECT_CROP`(기본 true)면 원본 키프레임에서
  `region` 만큼 잘라 `{job}_object.jpg` 로 저장(AI 호출 없음). 앱은 로컬에서 만들지만,
  다른 기기/세션·웹 뷰어가 "이동된 사물"을 재구성할 수 있게 서버에도 남긴다. 개수 정리 시
  메인 결과와 함께 지워진다.

키를 넣은 뒤 실제 결과 확인 (실사진 권장):

```bash
cd experiments/shinym87/interior/server
python scripts/e2e_check_custom.py --image testdata/real_living_room.jpg \
  --bbox 0.34,0.39,0.30,0.28 --ai-provider external --ai-api-key <KEY>
# → scripts/_out/result_<job>.jpg 에 실제 편집 결과가 저장된다
```

검증됨 (2026-09-03): `real_living_room.jpg`(4032×3024) + 위 bbox + `gemini-2.5-flash-image`
→ TV·사운드바·케이블이 깨끗이 지워지고 벽이 자연스럽게 복원됨, 결과 해상도 4032×3024 유지.

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

- `tests/test_api.py` — 세션 생성 → 키프레임 업로드 → 사물 정보 저장 →
  remove-object(mock) → 결과 이미지 → 카탈로그.
- `tests/test_external.py` — Gemini 프로바이더의 요청 조립·응답 파싱·에러 처리
  (키 없음 / 200 성공 / 429 / 403 / 네트워크 / 타임아웃 / 이미지 없음 / 안전 차단)를
  `httpx.post` 를 가짜로 바꿔 실제 키·네트워크 없이 검증한다.

## 전체 흐름 E2E 검증 (실제 HTTP)

`scripts/e2e_check.py` 가 합성 거실 이미지 하나로 **세션 생성 → 키프레임 업로드
→ 사물 정보 확인 → remove-object → job 폴링 → 결과 이미지 다운로드/검증 →
서버 저장 파일 확인 → 캐시 확인**까지 실제 HTTP 로 한 번에 실행한다.
서버가 안 떠 있으면 uvicorn 을 자동으로 띄웠다가 끝나면 내린다.

```bash
# server/ 에서 (venv 활성화 상태) — 합성 거실 이미지 + TV 위치
python scripts/e2e_check.py                    # exit 0 이면 전체 흐름 OK
python scripts/e2e_check.py --no-start         # 이미 떠 있는 서버로만
python scripts/e2e_check.py --ai-provider mock # .env 와 무관하게 mock 강제
.\scripts\run_e2e.ps1                          # Windows 래퍼
```

**임의 사진 + 임의 bbox** 로 돌리려면 `scripts/e2e_check_custom.py`:

```bash
python scripts/e2e_check_custom.py \
  --image testdata/real_living_room.jpg \
  --bbox 0.34,0.39,0.30,0.28 \
  --ai-provider external --ai-api-key <GEMINI_KEY>   # 또는 .env 에 설정
```

- `--bbox` 는 `"x,y,width,height"` (각 0~1 비율). `--object-type` 으로 종류 지정(기본 tv).
- 두 스크립트 모두 서버가 없으면 uvicorn 을 자동으로 띄웠다가 끝나면 내린다.

testdata 예시:
```bash
python scripts/e2e_check_custom.py --image testdata/pexels_sofa.jpg  --bbox 0.24,0.50,0.66,0.30 --object-type sofa
python scripts/e2e_check_custom.py --image testdata/pexels_table.jpg --bbox 0.36,0.52,0.42,0.30 --object-type table
```
평평한 벽 앞의 고립된 사물(벽걸이 TV)이 가장 잘 되고, bbox 안에 다른 가구가 겹치거나
배경이 복잡하면 결과가 어색하거나 편집이 안 될 수 있다 (PHASE 3 정밀 마스크 필요).
- 결과 이미지: `scripts/_out/result_<job>.jpg` (git 무시), 서버 저장본:
  `data/scenes/<scene>/results/<job>.jpg`. **둘 다 항상 원본과 같은 해상도**
  (`app/ai/imageops.ensure_jpeg_size()` 가 `_run_job` 에서 강제 리사이즈).

## 알려진 제약 / 다음

- `remove-object` 는 `BackgroundTasks` 로 동기 실행에 가깝게 처리한다. 실제 외부 AI
  연동 시 타임아웃·재시도·별도 워커가 필요하다 (PHASE 2, P2 재시도 정책).
- 사물 인식은 없다. 앱이 보낸 `bbox` 를 그대로 저장/사용한다.
- 인증·멀티유저 격리 없음. 해커톤 단일 사용자 가정.
- `external` 프로바이더 본문 미구현 (P1-10).
