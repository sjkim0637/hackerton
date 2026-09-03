# Handoff

## From

shinym87 (+ Claude)

## To

shinym87 (Gemini API 키가 준비되면 실제 결과 확인) / 이후 합류하는 영상·AI 담당

## Workstream

[interior](../workstreams/interior.md) — 카메라 기반 공간 편집 / AR 가구 재배치.
PHASE 1 (P1-10) + PHASE 2 + PHASE 3 "사용자 2 (영상 / AI)".

## PHASE 5 — 가구 카탈로그 썸네일 서빙 (2026-09-03)

사용자 1 이 카탈로그 배치 UI 를 만들었지만 서버가 `/assets/*` 를 안 줘서 5종 모두 큐브
폴백이었다. 썸네일 이미지를 만들고 정적 서빙을 붙였다.

1. **썸네일 이미지** (`scripts/make_furniture_thumbnails.py`)
   - 무료 스톡 사진 대신 **Pillow 라인아트 카드**로 생성 (라이선스/네트워크 무관, 결정적).
     흰 라운드 카드 + 카테고리별 색 외곽선 가구 + 한글 이름(Malgun Gothic).
   - 출력: `catalog/assets/furniture/{tv,sofa,table,chair,shelf}.png` (640×640 RGBA,
     각 ~5KB, git 포함). 다시 만들려면 스크립트 재실행.
2. **정적 서빙** (`app/main.py`, `app/config.py`)
   - `app.mount("/assets", StaticFiles(directory=settings.assets_dir))` — `assets_dir` 은
     기존 설정값 `catalog/assets/`(`INTERIOR_ASSETS_DIR` 로 override 가능). `get_settings()`
     가 이 디렉터리도 mkdir 한다.
   - `catalog/furniture.json` 의 `thumbnail` 을 `/assets/furniture/<종류>.png` 로 갱신
     (기존 `/assets/tv-wall-55.png` 등 존재 안 하던 경로 → 실제 파일).
   - `model.url`(glb)은 손대지 않음 — 파일 없고 앱도 안 씀.
3. **검증**
   - 실서버: `GET /catalog` 의 5개 `thumbnail` 을 각각 열어 `200 image/png`,
     유효 PNG 640×640, 없는 파일은 404 확인.
   - 테스트: `tests/test_api.py::test_catalog_thumbnails_are_served` — 모든 항목의
     thumbnail 이 `/assets/furniture/` 로 시작하고 `client.get` → 200 + PNG 시그니처.
     `pytest` **43개 통과**, mock e2e 회귀 없음.
   - 앱 쪽은 코드 변경 불필요 — `CatalogController` 가 이미 `thumbnail` URL 을 받아
     `downloadBytes` 하므로, 서버만 붙이면 카탈로그 목록 선택 시 이미지 quad 로 뜬다.

향후: 실제 제품 사진으로 교체하고 싶으면 같은 파일명으로 `catalog/assets/furniture/` 에
덮어쓰면 된다. (썸네일 배경 투명화는 지금 라인아트라 불필요.)

## PHASE 4 — 제거된 사물 크롭 서버 저장 + 결과 API 필드 (2026-09-03)

### 1. 판단: 서버에도 저장한다 (조건부로 의미 있음 → 거의 공짜라 채택)

- 앱(사용자 1)은 이미 삭제 요청 시점에 **클라이언트에서** 키프레임을 bbox 로 잘라
  (`RemovalController.capturedObjectBitmap`) 이동 배치에 쓴다 → **현재 단일 기기 데모
  흐름만 보면 서버 저장은 불필요**하다.
- 그래도 저장하기로 한 이유:
  - **AI 호출이 전혀 없다** (Pillow crop 한 번). 디스크만 조금 더 쓴다.
  - 결과 API 가 self-contained 해진다 — `keyframe` / `result` / `removed_object` 세 장이
    모두 URL 로 나와, 다른 기기·다음 세션·웹 뷰어가 "삭제 전/후/이동"을 서버 URL 만으로
    재구성할 수 있다 (사용자가 지적한 "크로스 기기/세션 재사용" 용도).
  - 나중에 배경 투명 컷아웃(아래 3번)을 넣으면 **같은 경로/URL 자리에** PNG 로 갈아끼우면
    되므로, 지금 자리를 잡아두는 게 이득.
- 끌 수 있다: `INTERIOR_SAVE_REMOVED_OBJECT_CROP=false`.

### 2. 구현

- `app/ai/imageops.py::crop_normalized_jpeg(image_bytes, rect)` — 정규화 `[x,y,w,h]` 로
  잘라 RGB JPEG. 경계 클램프 + 최소 1px.
- `_run_job`: 결과 저장 직후(`status=done` 이후, 개수 정리 전) `region_bbox(region)` 으로
  원본 키프레임을 잘라 `data/scenes/{scene}/results/{job_id}_object.jpg` 저장,
  job 에 `removed_object_path`/`removed_object_url` 기록. 실패해도 job 은 done 유지(부가 산출물).
- `store.jobs` 에 `removed_object_path`/`removed_object_url` 컬럼(+ 기존 DB용 ALTER 가드).
- `GET /scenes/{id}/results` 응답에 **`removed_object_image_url`** 추가 (파일 있을 때만).
- `GET /scenes/{id}/results/{job}_object.jpg` 신설 — `{job}.jpg` 라우트보다 **먼저** 등록해
  `_object.jpg` 가 여기로 매칭되게 함. 없으면 404, 정리됐으면 410.
- `cleanup.py`: 개수 정리는 메인 결과(`{job}.jpg`)만 세고(`_object` 접미사 제외), 메인을
  지울 때 동반 크롭도 함께 unlink. 기간 정리는 오래된 `.jpg` 를 종류 구분 없이 지운다.
- 테스트: `test_results.py` — 목록에 `removed_object_image_url` + 다운로드 확인,
  개수 정리 시 `_object.jpg` 동반 삭제/410, `crop_normalized_jpeg` 단위. `pytest` 36개,
  mock e2e 14/14 (`{job}_object.jpg` 생성·DB URL 채워짐 확인).

### 3. 배경 투명 컷아웃 — 지금은 검토만 (idea-backlog 기록)

지금 크롭은 사각형이라 배경이 딸려온다. 사물 윤곽만 분리(알파 투명)하는 방법 후보:

| 방법 | 비용/무게 | 품질 | 메모 |
|---|---|---|---|
| Gemini 후속 1콜 ("removed 사물만 투명 배경 PNG 로") | AI 1콜(~$0.039)·지연 +수초 | 선명한 사물엔 양호, 가는/털 경계 약함 | 이미 키프레임+마스크를 보내니 프롬프트만 추가하면 됨 (가장 저마찰) |
| `rembg`(u2net) 오프라인 | 모델 ~170MB·CPU 1~2s | 범용적으로 무난 | 무거운 의존성 추가 |
| OpenCV GrabCut (bbox seed) | 가벼움(opencv 이미 후보) | 잡동사니 책상에선 중간 이하 | 추가 AI 비용 0 |
| ML Kit Subject Segmentation | 온디바이스 | 사람 위주 튜닝 | 가구/소품엔 부적합 |

**판단: 지금은 구현하지 않는다.** 이동 배치는 개념 증명이고 `EdgeFade` 페더링된
사각형이 "사물 사진 카드"로 충분히 읽힌다. 투명 컷아웃은 (a) 반복 AI 비용/지연 또는
(b) 170MB 의존성을 부르는데, 데모 가치 대비 과함. 크롭 경로/URL 을 이번에 고정해 뒀으니
나중에 `crop_normalized_jpeg` → 컷아웃 함수로 바꾸고 `.jpg`→`.png` 만 하면 됨.
`experiments/shinym87/interior/docs/backlog.md` 아이디어란에 방법 비교와 함께 기록.

## PHASE 3 — objectType 범용값(other) + 잘못된 힌트 방지 프롬프트 (2026-09-03)

실기기 사례: 책상 위 컵을 지우려 했으나 앱 스피너가 TV 기본값이라 서버가 TV 전용
지시문("This TV is mounted on the wall. Rebuild the flat wall…")을 받아 책상 전체를
몰딩 있는 회색 벽으로 대체(할루시네이션). 서버 로그/DB 재구성으로 원인 확정.

1. **`app/ai/objects.py` — 범용 키 `other` 추가**
   - `GENERIC_TYPE = "other"`. `ALIASES` 에 `기타/소품/etc` + `cup/mug/tumbler/glass/
     bottle/vase/plant/lamp/fan/book/box/clock/...` → `other` 매핑.
   - `is_generic()` 신설. `is_known()` 은 `KNOWN_TYPES` + `other` 를 인정(경고 로그 억제).
2. **`app/ai/external.py` — 종류에 맞는 힌트만 사용**
   - `_build_prompt`: `norm == "other"` 이거나 `_SURFACE_HINTS` 에 없는 종류면
     **`_DEFAULT_HINT`** 사용 + 라벨을 `"object"` 로, 부속 문구도 소품용
     ("lid, handle, cable, small parts or contact shadow")으로 바꾼다.
     가구일 때만 "legs, base, stand" 문구.
   - `_DEFAULT_HINT` 강화: "주변에 실제로 있던 면(책상 상판/바닥/벽/선반)을 그대로 이어서
     복원. **없던 평평한 벽·빈 패널·새 표면을 만들지 말 것(Do NOT invent a plain flat
     wall…)**. 실제로 있는 것만 연장." → 회색 벽 할루시네이션 억제.
   - 회귀 확인: tv/sofa/table/couch 프롬프트 기존 문구 유지
     (`tests/test_external.py::test_prompt_is_object_type_aware` 에 other/cup 케이스 추가).
   - `tests/test_api.py::test_generic_object_type_is_accepted` (other 그대로, cup→other).
   - 앱(사용자 1): 스피너에 "기타/소품" 옵션 + "사물 종류 선택…" 초기값,
     미선택 시 '삭제 요청' 비활성화. 상세 `docs/handoffs/user1.md`.
   - `pytest` 35개 통과. mock e2e 회귀 없음.

## PHASE 3 — 색감 보정 + 이상 결과 감지 (2026-09-03)

`app/ai/colormatch.py` 신설. 기준은 **마스크(선택 영역) 밖**이다 — AI 는 그 밖을
건드리지 않아야 하므로, 그 영역의 원본↔결과 차이로 전역 이동을 잡는다.
`_run_job` 이 `ensure_jpeg_size` 직후 → 이상 감지 → 색감 보정 순으로 후처리한다.

1. **색감 보정** (`match_to_source`)
   - 마스크 밖 영역의 원본/결과 채널별 평균으로 게인 `g_c = mean_src_c / mean_res_c`
     계산, `[0.7, 1.4]` 로 클램프, `Image.point` LUT 로 결과에 곱한다(노출/화이트밸런스
     수준의 가벼운 후처리, JPEG q92 재인코딩).
   - 게인이 모두 ±3% 이내면 건너뛴다(재인코딩 안 함). 선택 영역이 화면의 85% 초과라
     바깥 표본이 부족하면 건너뛴다.
   - 로그: `[job X] 색감 보정 gains(r,g,b)=[1.08, 1.05, 1.11]`.
   - `INTERIOR_RESULT_COLOR_MATCH=false` 로 끌 수 있다.
2. **이상 결과 감지** (`check_result_anomaly`) — 명백한 케이스만
   - 리사이즈 전 결과가 64px 미만 → **fail**. 종횡비가 원본과 25% 초과 차이 → warn.
   - 마스크 밖 영역을 384px 로 다운스케일해 원본과 비교:
     - grayscale MAD ≥ `INTERIOR_RESULT_ANOMALY_FAIL_MAD`(기본 55) 또는 채널편차 ≥ 45
       → **job 을 `failed` 처리** (에러: "이상 결과 감지: 마스크 밖 영역이 원본과 크게
       다름 … AI 가 장면 전체를 바꿨거나 다른 이미지를 만든 것으로 보임").
     - MAD ≥ `..._WARN_MAD`(기본 22) 또는 채널편차 ≥ 18 → **경고 로그만**, 결과는 유지.
   - 재시도는 안 한다(잘못 생성된 이미지는 다시 해도 비슷). fail 은 곧바로 실패로 마감.
   - 초기 실기기 버그(검은 키프레임 → AI 가 상상한 방)가 이 fail 조건에 걸린다.
   - 테스트: `tests/test_colormatch.py` 8개 (전체 `pytest` 28개 통과). mock 은 마스크 밖이
     원본과 동일 → 항상 "ok", 색감 보정도 no-op → mock e2e 회귀 없음.

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

## PHASE 2 — 진단 실험: 실패 원인 분리 (배경 복잡도 vs 가구 겹침) (2026-09-03)

소파 삭제 실패가 "배경이 복잡해서"인지 "bbox 안에 다른 가구가 겹쳐서"인지 분리했다.

### 준비한 이미지 (`testdata/`, Pexels 무료)
| 코드 | 파일 | 배경 | bbox 안 겹침 |
|---|---|---|---|
| A | `sofa_A_simple_isolated.jpg` | 단순 (흰 벽) | 없음 |
| B | `sofa_B_complex_isolated.jpg` | 복잡 (창+담쟁이+거친 회벽) | 없음 |
| C | `sofa_C_simple_overlap.jpg` | 비교적 단순 (개방형) | 커피테이블+러그 |
| D | `pexels_sofa.jpg` (기존) | 복잡 (창+벽돌) | 앞 벤치+쿠션 |

### 실행 (모두 `gemini-2.5-flash-image`, `e2e_check_custom.py`)
```
# baseline (기본 프롬프트, 보통 bbox)
python scripts/e2e_check_custom.py --image testdata/sofa_A_simple_isolated.jpg  --bbox 0.48,0.52,0.52,0.28  --object-type sofa
python scripts/e2e_check_custom.py --image testdata/sofa_B_complex_isolated.jpg --bbox 0.00,0.575,1.0,0.425 --object-type sofa
python scripts/e2e_check_custom.py --image testdata/sofa_C_simple_overlap.jpg   --bbox 0.00,0.50,0.66,0.44  --object-type sofa
python scripts/e2e_check_custom.py --image testdata/pexels_sofa.jpg             --bbox 0.24,0.50,0.66,0.30  --object-type sofa
# 타이트 bbox (겹친 물체를 박스 밖으로)
python scripts/e2e_check_custom.py --image testdata/sofa_C_simple_overlap.jpg --bbox 0.00,0.53,0.43,0.40 --object-type sofa
python scripts/e2e_check_custom.py --image testdata/pexels_sofa.jpg           --bbox 0.52,0.47,0.36,0.30 --object-type sofa
# "박스 안 다른 물체도 제거, 밖은 건드리지 마" 명시 프롬프트
python scripts/e2e_check_custom.py --image testdata/pexels_sofa.jpg --bbox 0.24,0.50,0.66,0.30 --object-type sofa \
  --ai-extra-prompt "If other small objects sit inside the white masked area (a bench, a coffee table, cushions, a rug, or items resting on the furniture), remove those as well and rebuild the surface beneath them. Never remove, move, shrink or alter any object whose main body lies outside the white masked area."
```

### 결과
| 실험 | 결과 | 상세 |
|---|---|---|
| **A** 단순bg·고립 (보통 bbox) | ✅ 완벽 | 소파 완전 제거, 벽/바닥/걸레받이·뒤 사이드테이블·러그 자연 복원 |
| **B** 복잡bg·고립 (보통 bbox) | ✅ 성공 | 소파+쿠션 완전 제거. 창틀 아래 벽·마루 원근까지 재구성. (밝기 체크는 false-FAIL) |
| **C** 단순bg·겹침 (보통 bbox) | ⚠️ 부분 | 소파는 제거되나 **커피테이블+러그 잔존** (덩그러니 남음) |
| **D** 복잡bg·겹침 (보통 bbox) | ⚠️ 부분·불안정 | 소파 몸체만 지워지고 쿠션/스로우/벤치 잔존, 지저분. 동일 조건 다른 런에서는 아예 passthrough(편집 0) |
| **B** tight bbox | ✅ 성공 | B-normal보다 더 깔끔, 옆 스툴 보존 |
| **C** tight bbox (커피테이블 제외) | ✅ 성공 | 소파만 깨끗이 제거, 박스 밖 커피테이블·러그 그대로 |
| **D** tight bbox (벤치 제외) | ✅ 성공 | 소파·쿠션 전부 제거, **복잡한 벽돌벽·창도 복원**, 벤치 그대로 |
| **C** + 명시지시 (보통 bbox) | ❌ 악화 | 아무것도 안 지움 (passthrough). C-normal보다 나쁨 |
| **D** + 명시지시 (보통 bbox) | ✅ 성공 | 소파+쿠션+스로우+**벤치까지 전부** 제거, 박스 밖(화분·커튼) 보존 |

### 결론 — 진짜 원인은?
- **배경 복잡도가 아니라 "bbox 안 가구 겹침"이 실패 원인이다.**
  B(복잡bg·고립)가 깨끗이 성공했으므로 복잡한 배경 자체는 문제가 아니다.
  A↔C(배경 비슷, 겹침만 다름), B↔D(배경 비슷, 겹침만 다름) 모두 겹침 있는 쪽만 실패.
- **타이트 bbox로 겹친 물체를 박스 밖으로 빼면** C·D 모두(복잡 배경 포함) 깨끗이 성공.
  → 가장 안정적인 해법.
- **명시 프롬프트**("박스 안 다른 물체도 제거")는 비결정적: D는 확 좋아졌고 C는 오히려
  passthrough. 신뢰 불가. 프롬프트에 상시 넣지 않는다(`INTERIOR_AI_EXTRA_INSTRUCTION` 로
  실험만 가능하게 유지).
- `e2e_check_custom.py` 밝기 델타 체크는 B에서 false-FAIL(밝기 유사한 사물↔배경).
  스모크 신호로만 쓰고 최종 판정은 이미지 육안 확인.

### 지금 UI(사각형 드래그)로 실전에서 쓸만한가?
- **쓸만한 조건**: 지우려는 사물 하나에만 딱 맞게 사각형을 그리면 배경이 복잡해도 잘 된다.
  벽걸이 TV, 고립된 소파, 벽 앞 단독 가구 → 데모/초기 실전 가능.
- **한계**: 사각형 안에 다른 가구(커피테이블·벤치·의자)가 물리적으로 들어올 수밖에 없는
  배치. 이땐 "이것만" 선택이 불가능해 부분 제거/잔존/불안정.
- **권장 대응**: (1) 사용자 가이드 "사물 하나 = 사각형 하나, 다른 가구 안 겹치게".
  (2) 드래그 후 박스가 화면의 큰 비율을 덮거나 다른 가구를 포함할 것 같으면 경고 문구.
  (3) PHASE 3: 정밀 세그멘테이션(사물 윤곽) 또는 포인트/브러시로 "이 사물만" 선택.

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
