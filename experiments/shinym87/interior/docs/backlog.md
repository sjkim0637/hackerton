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
