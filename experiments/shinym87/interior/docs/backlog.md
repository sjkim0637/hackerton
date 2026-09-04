# 이슈 목록 / 아이디어 백로그 (interior)

설계서 12장 `/issues` 와 18장 `idea-backlog` 를 이 실험 범위로 합쳐 둔다.
1인 진행이라 담당자는 모두 shinym87. 상태값은 루트 `AGENTS.md` 5장을 따른다.

## PHASE 1 — 전체 연결 (앱 → 서버 → AI → 앱 한 번 관통)

| # | 이슈 | 영역 | 상태 |
|---|---|---|---|
| P1-1 | `interior` 앱: 카메라/평면 인식/탭 배치/드래그/핀치 (이관 완료분 실기기 검증) | 앱 | IN_PROGRESS |
| P1-2 | 앱: 화면 드래그로 제거할 사물의 bbox 를 지정하는 UI (`BboxSelectionView`) | 앱 | DONE |
| P1-3 | 앱: "삭제 요청" 시 화면 캡처(키프레임) + 메타 JSON → `POST /scenes` `/keyframes` | 앱 | DONE |
| P1-4 | 서버: FastAPI 뼈대, `POST /scenes`, SQLite 초기화 | 서버 | DONE |
| P1-5 | 서버: `POST /scenes/{id}/keyframes` 업로드 + 파일 저장 | 서버 | DONE |
| P1-6 | 서버: 외부 AI 어댑터 인터페이스 + mock 구현 + external 자리 | 서버 | DONE |
| P1-7 | 서버: `POST /remove-object` → job, `GET /jobs/{id}` 폴링 | 서버 | DONE |
| P1-8 | 앱: job 폴링 → 결과 이미지를 벽 평면 quad(또는 전체화면 대체)로 적용 | 앱 | DONE |
| P1-9 | 앱: "삭제 전/후" 전환 버튼 | 앱 | DONE |
| P1-10 | `app/ai/external.py` 를 Google Gemini 이미지 편집 API 로 구현 + 실사진으로 실결과 검증 | 서버 | DONE |
| P1-11 | 실기기에서 end-to-end 1회 성공 + 데모 녹화 (서버 `--host 0.0.0.0`, baseUrl 을 PC IP 로) | 공통 | TODO |

P1-10 실결과 검증: `scripts/e2e_check_custom.py --image testdata/real_living_room.jpg
--bbox 0.34,0.39,0.30,0.28` + `gemini-2.5-flash-image` → TV·사운드바·케이블 제거,
벽 자연 복원, 결과 해상도 4032×3024 유지. 결과 이미지 크기 불일치 문제는 공통
`app/ai/imageops.ensure_jpeg_size()` 를 `_run_job` 에 넣어 해결(mock/external 모두 적용).

서버(P1-4~P1-7)는 `experiments/shinym87/interior/server/` 에 구현, `pytest` 5개 통과.
추가로 `GET /scenes/{id}/objects`(사물 정보 저장 형식만), `GET /catalog`(가구 데이터 API),
동일 요청 캐시, 작업당 호출 상한을 포함한다. 인계는 `docs/handoffs/user3.md`.

**전체 흐름 PC에서 검증 완료 (2026-09-02)**: `server/scripts/e2e_check.py` 로 합성 거실
이미지 1장을 써서 세션→키프레임 업로드→remove-object→job 폴링→결과 이미지
다운로드→서버 저장 파일 확인까지 실제 HTTP 로 13단계 PASS.

## PHASE 2 (진행 중)

- [DONE] 앱(사용자 1): 지울 사물 종류 선택(Spinner: TV/소파/테이블/의자/선반) → 서버 요청의
  `objectType` 이 선택값을 따라감 (더 이상 "tv" 하드코딩 아님).
- [DONE] 서버(사용자 2): 마스크 경계 비율 기반 페더링(`mask.py`), 사물 종류별 프롬프트
  (`external.py` `_SURFACE_HINTS`/`_OBJECT_ALIASES`). Pexels 소파/테이블 사진으로 실테스트 —
  TV 잘 됨(회귀 없음), 테이블 부분 성공(의자 잔존), 소파 실패(복잡한 배경). 상세는
  `docs/handoffs/user2.md`.
- [진단완료] 소파 실패 원인 = "배경 복잡"이 아니라 "bbox 안 다른 가구 겹침".
  타이트 bbox로 겹친 물체를 빼면 복잡 배경에서도 깨끗이 됨. 상세는 `docs/handoffs/user2.md`.
- [DONE] 서버(사용자 3): objectType 정규화(별칭 흡수)·중복 요청 방지(처리 중 포함)·
  일시적 오류 자동 재시도(1회)·타임아웃 대응·AI 호출 횟수/비용 로그. `docs/handoffs/user3.md`.
- [DONE] 앱(사용자 1): 드래그 박스가 화면 40% 이상이면 겹침 경고 Toast, 선택 모드 안내 문구.
- [TODO] 정밀 세그멘테이션 마스크(bbox → 실제 사물 윤곽) 또는 포인트/브러시 "이 사물만" 선택,
  remove-object 를 BackgroundTasks 밖 워커/큐로.

## PHASE 3 (진행 중)

- [DONE] 사용자 1: 결과 quad EMA 스무딩·추적 놓침 시 위치 유지·가장자리 알파 페이드
  (`RemovalController.onFrame`, `remove/EdgeFade.kt`). 실기기 확인 남음.
- [DONE] 사용자 2: 색감 보정(마스크 밖 평균에 맞춘 채널 게인) + 이상 결과 감지
  (마스크 밖 MAD/채널편차 임계값, 해상도 검사) → `app/ai/colormatch.py`,
  `docs/handoffs/user2.md`.
- [DONE] 사용자 3: 결과 버전 관리(`GET /scenes/{id}/results` 목록, job 단위로 안 덮어씀,
  정리된 버전은 `available:false`) · 임시 저장 정리(`app/cleanup.py` — scene 당 개수
  `INTERIOR_RESULT_KEEP_PER_SCENE`, 기동 시 기간 `INTERIOR_RESULT_MAX_AGE_HOURS`) ·
  폰 전달 최적화(`cap_jpeg_bytes` — `INTERIOR_RESULT_MAX_BYTES` 초과 시 품질만 낮춤,
  해상도 유지). `pytest` 34개. `docs/handoffs/user3.md`.
- [DONE] 잘못된 결과 재발 방지: 앱 스피너에 "기타/소품"(other) 옵션 + "사물 종류 선택…"
  초기값(미선택 시 삭제 요청 비활성화), 서버 `other`/소품 별칭 → 범용 `_DEFAULT_HINT`
  ("없던 평평한 벽 만들지 말 것"), 키프레임 캡처 직전 평면 격자/특징점 시각화 off.
  `docs/handoffs/user1.md`, `docs/handoffs/user2.md`.
- [TODO] 재투영 보정, 가림(occlusion) 처리, 회전 slerp. 정리된 job row TTL, 기간 정리 주기 실행.
- [TODO] bbox → 정밀 세그멘테이션 (겹친 물체 자동 배제). 컵 사례처럼 소품엔 특히 필요.

## PHASE 4 (진행 중)

- [DONE] 사용자 3: 재배치 상태 저장 — `POST/GET /scenes/{id}/placements`(이동/회전/크기,
  append 로그) + `POST /scenes/{id}/placements/undo`(간단한 실행 취소, 이력 되짚기).
  `placements` 테이블, `store.py` 메서드, `routers/placements.py`. pose 는 세션 로컬이라
  `source_region`+scale/rotation/plane 도 함께 저장(재정합 근거). 판단·한계는
  `docs/handoffs/user3.md`. `pytest` 42개.
- [DONE] 사용자 2: 제거된 사물 크롭을 서버에도 저장(`{job}_object.jpg`, AI 없음) +
  `GET /scenes/{id}/results` 에 `removed_object_image_url` + `_object.jpg` 서빙 라우트.
  판단·근거는 `docs/handoffs/user2.md` (앱은 로컬 크롭으로 충분하나 크로스 기기/세션·웹
  뷰어 재사용 + 결과 API 완결성 + 나중 투명 컷아웃 자리 확보 목적, 거의 공짜라 채택).
  `INTERIOR_SAVE_REMOVED_OBJECT_CROP` 로 끔. `pytest` 36개.
- [DONE] 사용자 1: 삭제한 사물을 다른 위치로 "이동" (개념 증명). `remove/MovedObjectController.kt`
  — 원래 위치(`originalObjectPose`)·삭제 전 사물 이미지(`capturedObjectBitmap`) 저장,
  "여기로 옮기기" → 새 지점 탭 배치, 드래그 이동 / 핀치·＋－ 크기 / 회전 버튼,
  사물 종류별 벽(TV·선반)/바닥 자동 맞춤(`ArSpaceController.hitTestPreferring`),
  "원위치" 되돌리기. 큐브 시스템은 `hasSelection()` 추가 외 무변경. 빌드만 확인, 실기기 남음.
  `docs/handoffs/user1.md`.
- [DONE] 사용자 1: 재배치를 서버 `placements` API 와 연동 — 배치/드래그/회전/크기 변경마다
  500ms 디바운스 `POST /placements`, "서버 배치 복원"(최신 active 를 `source_region` 기준
  현재 세션에서 재 hitTest), "실행 취소"(`POST /placements/undo` + 화면 노드 제거).
  `MovedObjectController` + `InteriorApiClient`(createPlacement/latestActivePlacement/
  undoPlacement). 실서버 라운드트립 스모크 OK. `docs/handoffs/user1.md`.
- [TODO] 이동된 사물 원근 보정(평면 사진 한계), `onFrame` EMA 스무딩 이식, redo, 복원 자동화
  (평면 인식되면 버튼 없이).

## PHASE 5 (진행 중)

- [DONE] 사용자 1: 서버 카탈로그에서 새 가구 배치. "가구 추가" → `GET /catalog` 목록 패널
  (`furniture/CatalogController.kt`) → 골라서 종류별 벽/바닥 평면에 탭 배치
  (`FurnitureController.beginCatalogPlacement` + `hitTestPreferring` 재사용). 썸네일 있으면
  이미지 quad, 없으면 기존 큐브+이름표(`FurnitureItem.imageNode?`). 드래그/핀치/회전은 큐브
  로직 그대로 재사용 — 회전만 신규(`rotateSelectedBy`, `FurnitureItem.rotationDeg`,
  "회전 ⟳" 버튼). "삭제 후 재배치"와 별개 진입점. 빌드만 확인. `docs/handoffs/user1.md`.
- [DONE] 사용자 2: 카탈로그 썸네일 서빙. `scripts/make_furniture_thumbnails.py`(Pillow
  라인아트) → `catalog/assets/furniture/{종류}.png`, `app.mount("/assets", StaticFiles(...))`,
  `furniture.json` thumbnail 을 `/assets/furniture/<종류>.png` 로 갱신. 실서버·pytest 검증.
  이제 카탈로그 목록에서 고르면 이미지 quad 로 뜬다(앱 코드 변경 없음). `docs/handoffs/user2.md`.
- [DONE] 사용자 3: placements 를 카탈로그 가구까지 확장. `source`(removed_object|catalog)
  + `catalog_item_id` 컬럼(기존 행은 removed_object 로 ALTER 가드), `POST /placements` 분기
  (catalog 면 catalog_item_id 필수·존재 검증), `GET /placements` 응답에 `source` 포함.
  `pytest` 47개. 앱 연동은 사용자 1 TODO(`docs/handoffs/user1.md`). `docs/handoffs/user3.md`.
- [DONE] 사용자 1: "여기로 옮기기" 를 즉시 드래그로 개선 — 버튼/`placing`/`onTap` 제거,
  `arm()` 이 삭제 자리에 마커를 바로 띄우고(`placeMarkerNow`, 평면 없으면 `onFrame` 재시도)
  손가락으로 끌어 옮김. 손 떼면 `onDragEnd` 로 최종 배치(+ 500ms 디바운스 저장). 마커는
  `MARKER_SCALE=1.35` 로 조금 크게. "원위치"/"실행 취소" 유지. `docs/handoffs/user1.md`.
- [DONE] 사용자 1: 카탈로그 배치를 placements API 에 연동. `FurnitureController.ensureCatalogScene`
  ("가구 추가" 최초 createScene), 배치/드래그/회전/크기 후 500ms 디바운스 `POST /placements`
  (`source="catalog"`, `catalog_item_id`), `restoreCatalogFromServer`(catalog_item_id 별 최신
  1건 → `GET /catalog/{id}` 크기 → 화면 기준 hitTest 재배치). `InteriorApiClient` 확장
  (createPlacement source/catalogItemId 하위호환, listPlacements, getCatalogItem, Placement DTO).
  removed_object 복원과 독립 공존. 실서버 스모크 OK. `docs/handoffs/user1.md`.
- [TODO] 바닥/벽 자동 스냅(가까이 가면 붙기), 벽지/색상 변경. 썸네일을 실제 제품 사진으로 교체.

## PHASE 2 이후 (개요만)

- PHASE 3 (진행 중, 사용자 1): 결과 quad 를 벽 앵커에 프레임마다 EMA 스무딩해서 고정
  (`RemovalController.onFrame`, `updateAnchorPose=false`), 추적 놓치면 마지막 위치 유지,
  가장자리 알파 페이드아웃(`remove/EdgeFade.kt`). 실기기 확인 남음. 남은 것: 재투영 보정,
  가림(occlusion) 처리, 회전 slerp
- PHASE 4: 기존 가구 떼어내기(원위치 저장) → 이동/회전/크기, undo/redo
- PHASE 5: 새 가구 카탈로그 배치, 바닥/벽 자동 맞춤, 벽지/색상 변경
- PHASE 6/7: B(경량 모델) / C(자체 엔진) 실험 — 별도 `experiment/*` 브랜치
- PHASE 8: AI 호출 최소화, 이미지 용량/영역 최적화, 사용량 상한
- PHASE 9: 거실 시연 완성, 발표 순서, 실패 대비 녹화

## 아이디어 백로그 (구현 중단하지 말고 여기 적기)

설계서 18장에서 가져온 목록. 핵심 기능을 방해하지 않는 선에서 추가한다.

- 가구를 지우면 기존 그림자도 함께 제거 / 새 가구에 간단한 그림자 생성
- 가구를 벽 가까이 가져가면 자동 맞춤 (스냅)
- 실제 방 크기에 맞춰 가구 크기 자동 조정 / 실제 가구 크기 자동 측정
- 가구가 벽을 뚫으면 경고, 문 열림 범위·콘센트 가림 위치 경고
- "TV 없애줘" 같은 음성 명령
- 변경 전 / 변경 후 비교 슬라이더(막대)
- 한 번에 실행 취소, 방 전체 초기화
- 배치안 여러 개 저장 후 A/B 비교, 가족에게 공유, 배치 투표
- 빈 공간 감지 후 가구 추천
- 결과를 사진/영상으로 내보내기
- 실제 판매 상품 연결

## 새 아이디어 추가란

<!-- 날짜 · 한 줄 아이디어 · (선택) 왜 유용한지 -->
- 2026-09-02 · 이 백로그 문서 신설
- 2026-09-03 · **이동된 사물의 배경 투명 컷아웃** — 지금 `{job}_object.jpg` 는 bbox 사각형
  크롭이라 배경이 딸려온다. 사물 윤곽만 분리(알파 투명)하면 새 위치에 붙였을 때 훨씬 자연스럽다.
  방법 비교: (a) Gemini 후속 1콜 "removed 사물만 투명 배경 PNG" — 가장 저마찰(키프레임+마스크
  이미 전송), AI 1콜(~$0.039)+지연, 가는 경계 약함 · (b) `rembg`(u2net) 오프라인 — 모델 170MB,
  CPU 1~2s, 범용 무난 · (c) OpenCV GrabCut(bbox seed) — 가볍고 AI 비용 0, 잡동사니 배경엔 중간
  이하 · (d) ML Kit Subject Segmentation — 온디바이스지만 사람 위주. **현시점 판단: 미구현**
  (이동은 개념 증명, 페더링된 사각형이면 충분 / 비용·의존성 과함). 크롭 경로·URL 을 이미
  고정해 뒀으니 나중에 `crop_normalized_jpeg` → 컷아웃 함수, `.jpg`→`.png` 만 바꾸면 됨.
  상세는 `../../../../docs/handoffs/user2.md` PHASE 4 절.
