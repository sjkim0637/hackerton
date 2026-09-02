# Handoff

## From

shinym87 (+ Claude)

## To

shinym87 (Gemini API 키가 준비되면 실제 결과 확인) / 이후 합류하는 영상·AI 담당

## Workstream

[interior](../workstreams/interior.md) — 카메라 기반 공간 편집 / AR 가구 재배치.
PHASE 1 "사용자 2 (영상 / AI)" — mock 을 실제 외부 AI 로 교체 (이슈 P1-10).

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
- E2E 스크립트에 `--ai-provider` / `--ai-api-key` 플래그 추가 (자동 기동하는 서버의
  환경변수를 덮어씀).

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
├─ tests/test_external.py       에러/응답 처리 단위 테스트
└─ scripts/e2e_check.py         --ai-provider / --ai-api-key 플래그 추가
```

## Decisions

- 프로바이더는 Google Gemini `:generateContent` (REST). 마스크는 별도 파라미터가
  없어 "두 번째 이미지 = 마스크" 방식으로 전달 + 프롬프트로 지시.
- 결과는 항상 JPEG 로 재인코딩해 저장 형식(mock/`.jpg`)과 통일.
- 모델명은 `INTERIOR_AI_MODEL` 로 교체 가능. 기본 `gemini-3.1-flash-image`,
  계정에서 못 쓰면 `gemini-2.5-flash-image` 등으로.

## Constraints

- **실제 Gemini 결과 이미지는 아직 검증 못 함** — 이 저장소/환경에 API 키가 없다.
  키를 넣고 아래 "Next 1" 을 돌려야 "그럴듯한지" 확인된다. 코드 경로(요청 조립,
  상태 코드별 에러, 응답 파싱)는 단위 테스트로 검증됨.
- `responseModalities` / `inline_data` 표기는 REST 문서 기준으로 맞췄으나, 모델
  버전에 따라 `400` 이 나면 `generationConfig` 를 조정해야 할 수 있다(에러 메시지에
  Gemini 원문이 포함됨).
- 이미지 생성은 느릴 수 있어 타임아웃 120초. 그래도 `remove-object` 는
  `BackgroundTasks` 동기 실행이라 오래 걸리면 폴링이 그만큼 길어진다(PHASE 2 에서
  워커 분리).

## Known Issues

- mock e2e 는 여전히 13/13 통과 (회귀 없음). external + 키 없음 e2e 는
  `job=failed, error=ProviderNotConfigured …` 로 정상적으로 실패한다(의도된 동작).
- Gemini 가 마스크를 항상 정확히 지키지는 않는다 — 넓게 편집되거나 원근이 틀어질 수
  있다. 정합 개선은 PHASE 3.

## Next

1. Google AI Studio(https://aistudio.google.com/app/apikey)에서 Gemini API 키 발급 후:
   ```bash
   cd experiments/shinym87/interior/server
   python scripts/e2e_check.py --port 8020 --ai-provider external --ai-api-key <KEY>
   ```
   → `scripts/_out/result_<job>.jpg` 확인. 모델 오류(404)면
   `--ai-provider` 대신 `.env` 에 `INTERIOR_AI_MODEL=gemini-2.5-flash-image` 로 재시도.
2. 프롬프트 튜닝 (그림자 제거, 경계 자연스럽게) — 설계서 PHASE 2 사용자 2 항목.
3. 앱(P1-11): 서버 `--host 0.0.0.0`, 앱 `InteriorApiClient.DEFAULT_BASE_URL` 을 PC IP 로.

## Relevant Commits

- `feat(interior): PHASE 1 영상/AI — external provider 를 Google Gemini 이미지 편집 API 로 구현`
  (이 커밋; 브랜치 `agent/shinym87/interior`)
