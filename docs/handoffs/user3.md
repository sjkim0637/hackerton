# Handoff

## From

shinym87 (+ Claude)

## To

shinym87 (같은 사람이 앱 쪽 PHASE 1 을 이어서 진행) / 이후 합류하는 서버·통합 담당

## Workstream

[interior](../workstreams/interior.md) — 카메라 기반 공간 편집 / AR 가구 재배치.
PHASE 1 + PHASE 2 "사용자 3 (서버 / 통합)".

## PHASE 2 — objectType 반영 / 중복 방지 / 재시도 / 타임아웃 / 비용 로그 (2026-09-03)

1. **objectType 파라미터 정상 처리** (`app/ai/objects.py` 신설)
   - `POST /scenes/{id}/remove-object` 가 `object_type` 을 `normalize_object_type()` 로
     정규화(공백/대소문자 + `couch→sofa`, `desk/coffee table→table`, `television→tv` …).
     알 수 없는 값은 거부하지 않고 경고 로그만 남기고 그대로 처리.
   - 정규화된 종류로 `objects` 테이블 저장 + 캐시 키 + `_run_job` → provider →
     `external._build_prompt` 의 종류별 지시문에 반영. 키프레임 메타의 `targetObject`
     도 저장 시 정규화. **"tv" 하드코딩 없음** (앱이 tv/sofa/table/chair/shelf 전송).
   - 테스트: `test_api.py::test_remove_object_uses_selected_object_type`,
     `::test_object_type_aliases_are_normalized`.
2. **동일 요청 중복 호출 방지 강화** (`store.find_job_by_cache_key`)
   - 기존엔 `status='done'` 만 캐시. 이제 `done` + **처리 중(`queued`/`running`)** 도 포함.
     같은 `(keyframe_id, region, object_type)` 요청이 아직 돌고 있으면 새 job 을 만들지
     않고 그 job 을 그대로 반환 → 진짜 중복 호출 방지. `add_object` 도 재요청 시 안 늘어남.
   - 테스트: `test_api.py::test_duplicate_request_reuses_job_and_no_extra_ai_call`.
3. **AI 호출 실패 시 자동 재시도** (`_run_job`, `ProviderError.retryable`)
   - `ProviderError(msg, retryable=True)` — 네트워크/타임아웃/429/5xx 는 재시도 대상.
     인증(401/403)·400·404·안전차단·이미지없음 은 재시도 안 함.
   - `_run_job` 이 `INTERIOR_AI_MAX_RETRIES`(기본 1) 회까지 `INTERIOR_AI_RETRY_BACKOFF_SECONDS`
     (기본 2초) 대기 후 재시도. 그래도 실패하면 `job.status="failed"` + error.
   - 테스트: `test_job_retry.py` (일시적 오류 1회 → 재시도 성공 / 인증 오류 → 재시도 없이 실패),
     `test_external.py` 의 `retryable` 플래그 검증.
4. **처리 시간 초과 대응**
   - `httpx.post(timeout=INTERIOR_AI_TIMEOUT_SECONDS)` (기본 120s) 로 호출 자체를 제한.
     타임아웃은 `retryable=True` 이므로 재시도 1회 후 실패 처리. 최악 대기 ≈
     2×timeout + backoff 로 유계. 앱은 별도로 120초 폴링 제한.
5. **AI 호출 횟수 / 대략 비용 로그** (`scenes.ai_calls` 컬럼, `store.bump_ai_calls`)
   - 실제 provider 호출마다(재시도 포함) `scenes.ai_calls` 증가. `_run_job` 로그:
     `[job X] AI 호출 (provider=…, 시도 n/m) · scene 누적 K회 · 예상 비용: 이번 ~$0.0390,
     scene 누적 ~$…`. mock 은 비용 0.
   - 비용 단가는 `INTERIOR_AI_COST_PER_CALL_USD` (기본 0.039, gemini-2.5-flash-image 기준).
   - `max_ai_calls_per_scene` 한도 체크도 `count_jobs` 대신 `scenes.ai_calls` 기준으로 변경.
   - 앱 UI 에도 종류 스피너에서 고른 값이 `objectType` 으로 전달됨(사용자 1).
   - 서버 기동 시 AI 설정 요약 1줄 로그 (`main.py`).

### 알려진 한계 (PHASE 3)
- `remove-object` 는 아직 `BackgroundTasks` 로 실행 → 오래 걸리는 호출이 폴링을 길게 만든다.
  워커/큐 분리 필요.
- 중복 방지·한도 체크는 서버가 단일 프로세스라는 가정. 동시에 들어온 서로 다른 요청 2건이
  한도 체크를 같이 통과할 수 있는 미세 레이스 존재 (해커톤 범위에선 무시).

## Completed

PHASE 1 사용자 3 항목을 `experiments/shinym87/interior/server/` 에 구현했다.

- **API 서버 기본 구조**: FastAPI (`app/main.py`), CORS 허용, `GET /health`,
  자동 문서 `/docs`.
- **작업 세션 생성**: `POST /scenes` → `scene_<8hex>`, `GET /scenes/{id}`.
  세션별 순번(`kf_seq`/`obj_seq`/`job_seq`)을 SQLite 에서 원자적으로 증가.
- **이미지 업로드 + 대표 이미지 저장**: `POST /scenes/{id}/keyframes` (multipart:
  `image` JPEG + `meta` JSON 문자열). 이미지와 정규화한 메타를
  `data/scenes/{scene}/keyframes/{kf}.jpg|.json` 로 저장. 메타 형식은
  `docs/data-model.md` 4절. camelCase(문서 표기)와 snake_case 를 모두 받는다.
- **사물 정보 저장 (형식만)**: 키프레임 메타의 `targetObject` 또는 `remove-object`
  요청의 `target` 을 `objects` 테이블에 저장. `GET /scenes/{id}/objects` 로 조회.
  실제 사물 인식은 없음 — 앱이 보낸 `bbox` 를 그대로 보관.
- **외부 AI 연결 구조**: `app/ai/`
  - `base.RemoveObjectProvider` (ABC) + `RemoveResult`
  - `mock.MockRemoveObjectProvider`: Pillow 로 대상 bbox 를 주변 색+블러로 덮어
    "삭제"를 흉내. 실제 API 불필요.
  - `external.ExternalRemoveObjectProvider`: **자리만**. `remove_object()` 안에
    `TODO(P1-10)` 로 실제 HTTP 호출 위치를 주석으로 남김. 키 없으면
    `ProviderNotConfigured`.
  - `build_provider(settings)`: `INTERIOR_AI_PROVIDER` 값(`mock`/`external`)만 보고
    구현체 교체.
- **사물 제거 + 복원 작업**: `POST /scenes/{id}/remove-object` → job 생성 후
  `BackgroundTasks` 로 프로바이더 호출, 결과를
  `data/scenes/{scene}/results/{job}.jpg` 저장. `GET /scenes/{id}/jobs/{job_id}`
  폴링(`queued`/`running`/`done`/`failed`), `GET /scenes/{id}/results/{job_id}.jpg`.
  동일 `(keyframe_id, target)` 재요청은 캐시된 job 반환. 작업당 호출 상한
  `INTERIOR_MAX_AI_CALLS_PER_SCENE`(기본 20) 초과 시 429.
- **가구 데이터 API**: `GET /catalog?category=`, `GET /catalog/{id}`.
  데이터는 `catalog/furniture.json` (tv/sofa/table/chair/shelf 5종, `data-model.md`
  6절 형식).
- **테스트**: `tests/test_api.py` — 전체 흐름 + 카탈로그 + external 미설정 오류.
- **전체 흐름 PC에서 검증 완료 (2026-09-02)**: `scripts/e2e_check.py` 로 합성 거실
  이미지(`testdata/living_room.jpg`, 1280×853) 하나를 써서 실제 HTTP 로
  세션 생성 → 키프레임 업로드 → 사물 정보 저장 확인 → remove-object → job 폴링
  (`done`) → 결과 이미지 다운로드 → 서버 저장 파일(`data/scenes/<scene>/results/
  <job>.jpg`) 존재·크기 일치 확인 → 캐시 재호출까지 **13단계 전부 PASS (exit 0)**.
  결과 이미지는 유효한 JPEG, 원본과 같은 해상도, TV 영역 평균 밝기 14 → 183
  (어두운 TV 가 벽 색으로 덮임). mock 프로바이더 기준이며 external 은 P1-10.

## Important Files

```
experiments/shinym87/interior/server/
├─ README.md                  실행법, 키 넣는 법, 요청 예시
├─ requirements.txt           fastapi / uvicorn / python-multipart / pydantic-settings / pillow / httpx
├─ .env.example               INTERIOR_* 설정 (그대로면 mock)
├─ catalog/furniture.json     가구 카탈로그 시드
├─ testdata/living_room.jpg   E2E 검증용 합성 거실 이미지
├─ scripts/
│  ├─ e2e_check.py            전체 흐름 E2E 검증 (실제 HTTP, uvicorn 자동 기동)
│  ├─ make_test_image.py      거실 이미지 생성기
│  └─ run_e2e.ps1             Windows 실행기
└─ app/
   ├─ main.py                 FastAPI 앱, /health
   ├─ config.py               Settings (env 접두사 INTERIOR_)
   ├─ schemas.py              요청/응답 + 키프레임 메타 스키마
   ├─ store.py                SQLite + 파일 저장 (scenes/keyframes/objects/jobs)
   ├─ ids.py                  scene_/kf_/obj_/job_ 생성
   ├─ deps.py                 Store / provider 싱글턴
   ├─ ai/                     base·mock·external·build_provider
   └─ routers/
      ├─ scenes.py            세션·키프레임·objects·remove-object·jobs·results
      └─ catalog.py           가구 데이터 API
```

## Decisions

- `docs/decisions.md` D1(외부 AI A 우선)·D2(1인 진행)·D3(사물 영역 bbox) 를 따른다.
- 저장은 SQLite + 로컬 파일. ORM/마이그레이션 없이 `CREATE TABLE IF NOT EXISTS`.
- 작업은 job + 폴링 구조. 지금은 `BackgroundTasks` 로 사실상 동기. 실제 외부 AI
  붙이면 타임아웃/재시도/워커 분리 필요 (PHASE 2).
- 서버 스키마는 `object_type` 등 snake_case 로 정규화하고, 키프레임 메타는
  camelCase 입력도 허용(`populate_by_name`).

## Constraints

- 인증·멀티유저 격리 없음. 해커톤 단일 사용자 가정.
- 사물 인식 없음. 앱이 보낸 `bbox` 신뢰.
- `external` 프로바이더 본문 미구현. `INTERIOR_AI_PROVIDER=external` + 빈 키로
  `remove-object` 하면 job 이 `failed` (error 메시지 포함) 로 끝난다.
- `data/` 는 git 무시. 서버 재시작해도 유지되지만 백업 대상 아님.
- Windows + 한글 저장소 경로: `python` 이 Store 스텁이라 `py -3` 사용.
  venv 는 `py -3 -m venv .venv` 로 생성.

## Known Issues

- 로컬 검증: `py -3 -m venv .venv` → `pip install -r requirements.txt pytest` →
  `pytest` 5개 통과 (Python 3.12.3, Windows). uvicorn 실기동은 아직 안 함.
- `fastapi.testclient` 가 httpx 관련 StarletteDeprecationWarning 을 띄우지만 동작에는
  영향 없음.
- 대용량 이미지 리사이즈/용량 제한 없음 (PHASE 8, P8 이미지 최적화).
- job 상태 전이에 락 없음. 단일 프로세스 가정.

## Next

1. 앱(P1-2, P1-3): TV 를 탭해 bbox 지정하는 UI + 키프레임(JPEG)/메타(JSON) 생성 후
   `POST /keyframes` → `POST /remove-object` 호출.
2. 앱(P1-8): job 폴링 → `result_image_url` 을 벽 평면 quad 텍스처로 고정, 전/후 비교.
3. 서버(P1-10): `app/ai/external.py` 의 `TODO` 를 실제 외부 AI 이미지 편집 API
   호출로 채우고, `.env` 에 키 주입. 라우터/저장 코드는 그대로.
4. end-to-end 1회 성공 데모 녹화 (P1-11).

## Relevant Commits

- `feat(interior): PHASE 1 서버/통합 — FastAPI 세션·키프레임·사물정보·AI 연결 구조·가구 카탈로그`
  (이 커밋; 브랜치 `agent/shinym87/interior`)
