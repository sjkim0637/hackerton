# Handoff

## From

shinym87 (+ Claude)

## To

shinym87 (Gemini API 키가 준비되면 실제 결과 확인) / 이후 합류하는 영상·AI 담당

## Workstream

[interior](../workstreams/interior.md) — 카메라 기반 공간 편집 / AR 가구 재배치.
PHASE 1 (P1-10) + PHASE 2 "사용자 2 (영상 / AI)".

## PHASE 2 — 마스크 페더링 / 종류별 프롬프트 / 다양한 사물 테스트 (2026-09-03)

### 1. 마스크 경계 페더링 (`app/ai/mask.py`)
- 기존: `feather=6` **픽셀 고정** → 4K 사진에서 사실상 각진 사각형.
- 개선: `feather = 대상 사각형 짧은 변 × 0.08` (최소 8px, 이미지 12% 상한). 비율 기반이라
  큰 사물은 넓게, 작은 사물은 좁게, 해상도와 무관하게 부드럽다.
- 블러가 안쪽을 깎아도 원래 bbox 가 완전 불투명하도록 그린 사각형을 `feather×2` 만큼
  키운 뒤 블러(사물 잔털/그림자까지 덮음). 마스크 PNG 크기가 ~2KB → ~35~55KB 로 커진 것으로
  페더링 확인. `tests/test_external.py::test_mask_is_feathered_and_larger_than_rect`.

### 2. 사물 종류별 프롬프트 (`app/ai/external.py`)
- `_SURFACE_HINTS`: tv→벽/브래킷/케이블/그림자, sofa→바닥+걸레받이+접촉그림자+쿠션,
  table→연속된 바닥/타일줄눈/러그+상판 위 물건, chair→바닥, shelf→벽. 그 외는 기본 힌트.
- `_OBJECT_ALIASES`: couch→sofa, desk/coffee table→table, television/monitor→tv 등.
- `_build_prompt` 가 종류에 맞는 문장을 조립. 라벨은 입력 단어 유지(별칭도).
- `scenes.py._run_job` 은 이제 프롬프트를 만들지 않고 `prompt=""` 로 넘김(문구는 프로바이더 소유).
- `tests/test_external.py::test_prompt_is_object_type_aware`.

### 3. 다양한 사물 테스트 (Gemini `gemini-2.5-flash-image`, `scripts/e2e_check_custom.py`)
`testdata/` 에 Pexels 무료 사진 2장 추가: `pexels_sofa.jpg`(1600×2400),
`pexels_table.jpg`(1600×2324). 결과는 `scripts/_out/`.

| 사물 | 이미지 / bbox | 결과 | 메모 |
|---|---|---|---|
| TV | real_living_room.jpg / `0.34,0.39,0.30,0.28` | **잘 됨** | TV·사운드바·전선 완전 제거, 벽·걸레받이 자연 복원. 이전 버전에 있던 하단 이음매가 사라짐 → 새 페더 마스크+프롬프트로 개선, 회귀 없음. |
| 소파 | pexels_sofa.jpg / `0.24,0.50,0.66,0.30` | **안 됨** | Gemini 가 거의 원본 그대로 반환(소파·쿠션·앞 벤치 그대로). 창문 앞 + 노출 벽돌 + bbox 안에 벤치 겹침 → 편집을 회피한 것으로 보임. e2e 밝기 체크도 109→109 로 FAIL. |
| 테이블 | pexels_table.jpg / `0.36,0.52,0.42,0.30` | **부분 성공** | 테이블 자체는 깨끗이 제거되고 바닥 복원 양호. 그러나 의자 6개가 붕 뜬 배치로 남고(요청은 테이블만), 상판에 있던 꽃병이 공중에 뜬 아티팩트. |

관찰
- **평평한 단일 표면 앞의 고립된 사물**(벽걸이 TV)에서 가장 잘 된다.
- bbox 안에 다른 가구가 겹치거나(벤치↔소파, 의자↔테이블) 배경이 복잡하면(창/벽돌/패턴)
  실패하거나 어색해진다. 이건 마스크/프롬프트로는 한계 → 정밀 세그멘테이션(PHASE 3)과
  "딸린 물건 같이 제거"(테이블+의자) 가 필요.
- `e2e_check_custom.py` 의 밝기 델타 체크는 완전한 판정은 아니지만(어두운 사물↔어두운 바닥은
  통과할 수 있음) 소파 미제거를 정확히 잡아냈다. 스모크 신호로 유지.

## Completed

`server/app/ai/external.py` 의 `TODO(P1-10)` 를 **Google Gemini 이미지 편집 API**
호출로 구현했다. mock provider 는 그대로 두고, `INTERIOR_AI_PROVIDER=external` 로
바꾸면 실제 AI 를 쓴다 (`build_provider()` 가 설정만 보고 교체 — 라우터/저장/작업 큐
코드는 안 건드림).

- **요청 조립** (`ExternalRemoveObjectProvider.remove_object`)
  - `app/ai/mask.py` 의 `region_to_mask_png()` 로 bbox → 원본과 같은 크기의 흑백
    마스크 PNG (지울 영역이 흰색, 가장자리 약간 feather).
  - 지시문: "첫 번째 이미지에서 <object_type> 을 지우고, 두 번째 이미지(마스크)의
    흰색 영역만 편집, 뒤 배경을 색/질감/조명/그림자/원근에 맞춰 복원, 마스크 밖은
    그대로, 해상도·프레이밍 유지, 편집된 이미지만 반환". bbox 중심 위치를
    `location_hint()` 로 "upper-center" 식 힌트도 넣는다.
  - `POST {base_url}/models/{model}:generateContent`, 헤더 `x-goog-api-key: <키>`,
    body `contents[0].parts = [text, inline_data(jpeg), inline_data(mask png)]`,
    `generationConfig.responseModalities=["TEXT","IMAGE"]`.
- **응답 파싱**: `candidates[0].content.parts` 에서 `inlineData`(또는 `inline_data`)
  의 base64 를 디코드 → Pillow 로 열어 JPEG(q90)로 재인코딩해 반환.
  `changed_region` = bbox.
- **에러 처리** (모두 `ProviderError`/`ProviderNotConfigured` 로, 라우터가 job 을
  `failed` + `error` 로 마감):
  - 키 없음 → `ProviderNotConfigured`
  - 429 → "API 사용 한도 초과", 401·403 → "인증 실패", 400 → "잘못된 요청",
    404 → "모델 없음", 5xx → "서버 오류"
  - `httpx.TimeoutException` → "요청 시간 초과", 그 외 `httpx.HTTPError` → "네트워크 오류"
  - `promptFeedback.blockReason` → "요청 차단", 이미지 파트 없음 →
    "응답에 이미지가 없음 (finishReason=…)"
- **설정** (`app/config.py`, 접두사 `INTERIOR_`): `ai_model` 기본
  `gemini-3.1-flash-image`, `ai_base_url` 기본 `https://generativelanguage.googleapis.com/v1beta`,
  `ai_timeout_seconds` 기본 120.
- **`.env.example`** 갱신: `INTERIOR_AI_PROVIDER` / `INTERIOR_AI_API_KEY`(형식만) /
  `INTERIOR_AI_MODEL` / `INTERIOR_AI_BASE_URL` / `INTERIOR_AI_TIMEOUT_SECONDS` 설명.
- **`.gitignore`**: `.env` 는 `server/.gitignore` 와 루트 `.gitignore`(`.env`,
  `.env.*`, `!.env.example`) 양쪽에 이미 있음 — 확인 완료, 추가 불필요.
- **테스트**: `tests/test_external.py` 8개 — `httpx.post` 를 가짜로 바꿔 키 없이
  요청 조립 / 200 성공(→ JPEG) / 429 / 403 / 네트워크 / 타임아웃 / 이미지 없음 /
  안전 차단을 검증. 전체 `pytest` 13개 통과.
- **결과 해상도 강제**: `app/ai/imageops.ensure_jpeg_size(bytes, size)` 신설.
  `_run_job` 이 프로바이더 결과를 **항상 원본 키프레임 해상도의 JPEG 로** 맞춰 저장한다
  (mock/external 공통). external 은 그 전에도 한 번 맞춘다. Gemini 가 다른 해상도로
  돌려줘도 이제 결과가 원본과 일치.
- **임의 사진용 스크립트**: `scripts/e2e_check_custom.py` — `--image` 와
  `--bbox "x,y,w,h"`(0~1) 를 받아 `e2e_check.py` 와 동일한 흐름을 돈다.
  `e2e_check.py` 의 공용 로직(`run_flow`, `ensure_server`, `parse_bbox` …)을
  그대로 import 해서 씀. `e2e_check.py` 에도 `--bbox` / `--ai-provider` /
  `--ai-api-key` 추가.
- **실결과 검증 완료 (2026-09-03)**: `real_living_room.jpg`(4032×3024,
  `server/testdata/`) + `--bbox 0.34,0.39,0.30,0.28` + `gemini-2.5-flash-image`
  → **TV·사운드바·전선이 깨끗이 제거되고 벽/걸레받이가 자연스럽게 복원됨**,
  결과 해상도 4032×3024 유지, e2e 14/14 PASS. mock 회귀도 14/14.

## Important Files

```
experiments/shinym87/interior/server/
├─ .env.example                 필요한 환경변수 형식 (실제 키 없음)
├─ app/config.py                ai_model / ai_base_url / ai_timeout_seconds 기본값
├─ app/ai/
│  ├─ external.py               Gemini 호출 본체 (이 커밋의 핵심)
│  ├─ mask.py                   bbox → 마스크 PNG, 위치 힌트, base64
│  ├─ mock.py                   그대로 유지 (기본 provider)
│  └─ __init__.py               build_provider() — 설정으로 mock/external 교체
├─ app/ai/imageops.py          결과를 원본 해상도 JPEG 로 맞추는 공통 로직
├─ app/routers/scenes.py       _run_job 이 ensure_jpeg_size() 로 결과 크기 보정
├─ tests/test_external.py       에러/응답 처리 단위 테스트
├─ testdata/real_living_room.jpg  실사진 테스트 픽스처 (4032×3024)
└─ scripts/
   ├─ e2e_check.py             공용 로직 + --bbox / --ai-provider / --ai-api-key
   └─ e2e_check_custom.py      --image / --bbox 로 임의 사진 검증
```

## Decisions

- 프로바이더는 Google Gemini `:generateContent` (REST). 마스크는 별도 파라미터가
  없어 "두 번째 이미지 = 마스크" 방식으로 전달 + 프롬프트로 지시.
- 결과는 항상 JPEG 로 재인코딩해 저장 형식(mock/`.jpg`)과 통일.
- 모델명은 `INTERIOR_AI_MODEL` 로 교체 가능. 기본 `gemini-3.1-flash-image`,
  계정에서 못 쓰면 `gemini-2.5-flash-image` 등으로.

## Constraints

- 실결과는 `INTERIOR_AI_MODEL=gemini-2.5-flash-image` 로 검증했다
  (`gemini-3.1-flash-image` 는 해당 계정에서 사용 불가 → `.env` 에서 교체).
- 키(`INTERIOR_AI_API_KEY`)는 `server/.env` 에 있고 `.gitignore` 로 커밋에서 제외된다.
  `.env.example` 에는 형식만 있다.
- `responseModalities` / `inline_data` 표기는 REST 문서 기준으로 맞췄으나, 모델
  버전에 따라 `400` 이 나면 `generationConfig` 를 조정해야 할 수 있다(에러 메시지에
  Gemini 원문이 포함됨).
- 이미지 생성은 느릴 수 있어 타임아웃 120초. 그래도 `remove-object` 는
  `BackgroundTasks` 동기 실행이라 오래 걸리면 폴링이 그만큼 길어진다(PHASE 2 에서
  워커 분리).

## 진단 로그 (2026-09-03)

`interior.*` 로거를 uvicorn 콘솔에 붙였다(`app/main.py._setup_logging`). 요청/응답 추적:
- `app/routers/scenes.py` `_run_job`: provider, 키프레임 파일명/바이트/해상도, region, 완료/실패
- `app/ai/external.py`: `[Gemini 요청]` — 모델, part별 크기(이미지 bytes·해상도·base64
  길이, 마스크 base64 길이), 프롬프트 앞부분. 이미지가 2KB 미만이면 경고("검은 화면
  가능성"). `[Gemini 응답]` — status, finishReason, parts 요약(이미지 base64→bytes,
  또는 `text=...` 원문), promptFeedback.
- 실기기 첫 실패 원인은 서버가 아니라 **앱의 키프레임 캡처**였다(검은 화면 전송).
  앱 쪽 수정은 user1 handoff 참고.

## Known Issues

- mock e2e 는 여전히 13/13 통과 (회귀 없음). external + 키 없음 e2e 는
  `job=failed, error=ProviderNotConfigured …` 로 정상적으로 실패한다(의도된 동작).
- Gemini 가 마스크를 항상 정확히 지키지는 않는다 — 넓게 편집되거나 원근이 틀어질 수
  있다. 정합 개선은 PHASE 3.

## Next

1. 프롬프트/모델 튜닝 — 경계 이음매(하단 미세 seam), 그림자 처리, 다른 사물 종류
   (소파/테이블). 설계서 PHASE 2 사용자 2 항목.
2. `remove-object` 를 별도 워커/큐로 (지금은 `BackgroundTasks` 동기) — Gemini 호출이
   길면 폴링이 그만큼 길어짐. 재시도 정책도.
3. 앱(P1-11): 서버 `--host 0.0.0.0`, 앱 `InteriorApiClient.DEFAULT_BASE_URL` 을 PC IP 로,
   앱에서 실제 bbox 지정 → Gemini 결과를 벽에 붙이는 흐름 실기기 확인.

## Relevant Commits

- `feat(interior): PHASE 1 영상/AI — external provider 를 Google Gemini 이미지 편집 API 로 구현`
  (이 커밋; 브랜치 `agent/shinym87/interior`)
