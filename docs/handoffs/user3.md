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

## PHASE 3 — 결과 버전 관리 / 임시 저장 정리 / 폰 전달 최적화 (2026-09-03)

1. **복원 결과 버전 관리 + 목록 조회 API**
   - 결과 파일은 이미 `data/scenes/{scene}/results/{job_id}.jpg` 로 job 단위 분리 →
     같은 scene 에서 여러 번 삭제 요청해도 **덮어써지지 않는다**. 별도 저장 구조 변경 없음.
   - 신규 `GET /scenes/{id}/results` → 이 scene 의 모든 job 을 최신순으로 나열.
     항목: `job_id, keyframe_id, status, result_image_url, changed_region, error,
     created_at, updated_at, size_bytes, available`. (`store.list_jobs`,
     `schemas.ResultInfoOut`)
   - 정리로 파일이 지워진 버전은 `available:false` + `result_image_url:null` 로 표시되고
     job 기록(상태·영역·시각)은 남는다. 그 결과 이미지 직접 요청은 **410 Gone**
     (기존 "결과 없음"은 409 유지 — 아직 처리 중인 경우).
   - 중복 요청은 기존대로 같은 job 을 재사용하므로 새 버전이 안 생긴다.
   - 테스트: `test_results.py::test_list_results_keeps_every_job_as_version`,
     `::test_list_results_missing_scene_404`.
2. **결과 이미지 임시 저장 정리** (`app/cleanup.py`)
   - 개수 기준: `_run_job` 이 결과를 저장한 뒤 `prune_scene_results()` 호출 →
     scene 당 최근 `INTERIOR_RESULT_KEEP_PER_SCENE`(기본 12)개만 남기고 오래된 파일부터 삭제.
     mtime 우선, 동률이면 파일명(seq zero-pad) 순.
   - 기간 기준: 서버 기동 시 `main.create_app()` 에서 `sweep_old_results()` 한 번 →
     모든 scene 에서 `INTERIOR_RESULT_MAX_AGE_HOURS`(기본 72h)보다 오래된 결과 파일 삭제.
   - 둘 다 **파일만** 지우고 DB row 는 유지 (목록에서 이력 확인 가능). 값 `0` 이면 해당 정리 안 함.
   - 테스트: `::test_old_results_pruned_by_count`, `::test_sweep_old_results_by_age`.
3. **스마트폰 전달 최적화 — 용량 캡** (`app/ai/imageops.cap_jpeg_bytes`)
   - 후처리까지 끝난 결과 JPEG 이 `INTERIOR_RESULT_MAX_BYTES`(기본 5,000,000)를 넘으면
     품질을 `82→72→62` 로 낮춰 재인코딩. **해상도는 유지**(결과 quad 텍스처 저해상도 방지,
     e2e "원본과 같은 해상도" 불변식 유지). 최저 품질에서도 못 줄이면 그대로 저장 + 경고 로그.
   - 로그: `[job X] 결과 압축: 8,300,000 → 4,700,000 bytes (q72)`.
   - 테스트: `::test_cap_jpeg_bytes_shrinks_but_keeps_resolution`,
     `::test_cap_jpeg_bytes_noop_when_small_or_disabled`.

새 설정(`config.py`, `.env.example`): `INTERIOR_RESULT_MAX_BYTES`,
`INTERIOR_RESULT_KEEP_PER_SCENE`, `INTERIOR_RESULT_MAX_AGE_HOURS`.
검증: `pytest` **34개 통과**(신규 6), `scripts/e2e_check.py --ai-provider mock` 14/14 PASS.

### 알려진 한계 (PHASE 3 이후)
- 정리는 파일만 지우고 job row 는 무한정 쌓인다 (해커톤 범위). 필요 시 row 도 TTL 정리.
- 용량 캡은 품질만 낮춘다. 초고해상도 결과가 최저 품질에서도 한도를 넘으면 다운스케일이
  필요하지만, 그러면 해상도 불변식이 깨지므로 지금은 안 한다.
- 기간 정리는 기동 시 1회뿐. 장시간 떠 있는 서버는 주기 실행(스케줄러)이 필요.

## PHASE 4 — 재배치 상태 저장 + 간단한 실행 취소 (2026-09-03)

### 판단: 저장한다 (사용자 2 크롭 저장과 같은 논리 — 거의 공짜 + 모델 완결 + undo 전제)

- 앱(사용자 1)은 이동/회전/크기를 **로컬 메모리로만** 들고 있어 앱을 끄면 사라진다.
  단일 기기 데모만 보면 서버 저장은 지금 당장 필요 없다.
- 그래도 저장하는 이유:
  - AI·파일 I/O 없음 (SQLite 행 하나). 사실상 공짜.
  - scene 모델이 완결된다 — `keyframe → object → job(result/crop)` 다음의 **최종 산출물
    (무엇을 어디로 옮겼나)** 이 지금까지 어디에도 없었다.
  - **실행 취소(2번)가 성립하려면** 서버에 배치 이력이 있어야 한다.
  - 설계서 PHASE 4 = "원위치 저장 → 이동/회전/크기, undo/redo" 이고 사용자 3 담당.
- **한계(문서화)**: `pose` 는 ARCore **세션 로컬 월드 좌표**라 다른 세션/기기에서 그대로는
  못 쓴다. 그래서 pose 와 함께 `object_type` · `source_region`(원래 제거 bbox) ·
  `scale` · `rotation_deg` · `plane` 을 같이 저장한다 — 재정합은 `source_region` 기준으로
  하고, 크기/회전/평면종류는 세션 무관하게 재사용 가능. (클라우드 앵커는 범위 밖.)

### 구현

- 테이블 `placements` (`store.py`): `placement_id`(`plc_<scene8>_NNN`), `scene_id`, `job_id?`,
  `object_type`, `source_region_json?`, `pose_json`, `scale`, `rotation_deg`, `plane?`,
  `status`('active'|'undone'), `created_at`, `updated_at`. `scenes.plc_seq` 순번 컬럼 추가
  (기존 DB 는 `_EXTRA_COLUMNS` ALTER 가드).
- `POST /scenes/{id}/placements` — body `PlacementCreate`(`object_type`, `pose{position[3],
  rotation[4]}`, 선택 `job_id`/`source_region`/`scale`(기본 1, >0 ≤20)/`rotation_deg`/`plane`).
  **append 로그**: 같은 사물을 또 옮기면 새 행이 쌓인다. `job_id` 주면 그 scene 소속인지 검증.
- `GET /scenes/{id}/placements` — 최신순. 기본 `active` 만, `?include_undone=true` 면 취소분 포함.
- `POST /scenes/{id}/placements/undo` — 가장 최근에 만들어졌거나 갱신된 `active` 배치 하나를
  `undone` 으로. 완전한 스택은 아니지만 **연달아 호출하면 이력을 한 단계씩 되짚는다**.
  active 가 없으면 404. (redo 는 안 넣음 — 행은 다 남아 있으니 나중에 추가 가능.)
- `store` 메서드: `create_placement` / `list_placements(include_undone)` /
  `latest_active_placement` / `set_placement_status` / `_placement_row`(pose·region JSON 파싱).
- 새 라우터 `app/routers/placements.py`, `main.py` 에 include. 새 설정 없음(비용 0).
- 테스트: `tests/test_placements.py` 6개 — 생성/조회, append 최신순, undo 되짚기 +
  `include_undone`, active 없을 때 404, job 연결·타 scene job 거부, 없는 scene 404.
  `pytest` **42개 통과**, mock e2e 14/14(기존 DB 에 `plc_seq`/`placements` 자동 추가 확인).

### 알려진 한계 (PHASE 4 이후)
- pose 재사용은 세션 로컬 한계 (위 참조). 클라이언트가 아직 이 API 를 호출/복원하지 않는다
  (사용자 1 앱은 로컬 상태만) — 서버는 저장/undo 만 제공, 실제 복원 연동은 다음 작업.
- undo 만 있고 redo 없음. per-object 가 아니라 scene 전체의 "마지막 변경" 기준.

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
   ├─ store.py                SQLite + 파일 저장 (scenes/keyframes/objects/jobs/placements)
   ├─ ids.py                  scene_/kf_/obj_/job_/plc_ 생성
   ├─ deps.py                 Store / provider 싱글턴
   ├─ cleanup.py              결과 이미지 정리 (개수: prune_scene_results / 기간: sweep_old_results)
   ├─ ai/                     base·mock·external·objects·colormatch·imageops·build_provider
   └─ routers/
      ├─ scenes.py            세션·키프레임·objects·remove-object·jobs·results(목록/이미지/크롭)
      ├─ placements.py        재배치(이동/회전/크기) 저장·조회·실행취소
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
