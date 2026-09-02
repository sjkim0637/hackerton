# Workstream

## Topic

카메라 기반 공간 편집 / AR 가구 재배치 시뮬레이터 — 공간 / AR 작업 흐름

## Owner

shinym87 + Claude

## Git Branch

`agent/shinym87/interior`

## Project Path

`experiments/shinym87/interior/`

## Status

IN_PROGRESS

## Goal

`hackathon_spatial_editing_design.md` 설계서의 **사용자 1 (공간 / AR)** 작업 흐름을
Android + Kotlin + ARCore 로 구현한다. 실기기에서 다음을 검증한다.

1. 카메라 화면 표시와 AR 세션 구성
2. 벽 / 바닥 평면 인식 (수평 + 수직)
3. 화면 탭으로 평면 위에 가구(현재는 반투명 큐브) 배치
4. 가구 선택 후 드래그 이동, 떼면 평면에 재고정
5. 핀치 및 `＋`/`－` 버튼으로 가구 크기 조절
6. "빈 배경" 대표 이미지 캡처와 변경 전 / 변경 후 비교 오버레이

## Background

`agent/shinym87/ar2` Branch(`experiments/shinym87/ar2/`, Workstream `ar-cube-min`) 에서
ARCore + SceneView 조합의 카메라·평면 인식·탭 배치·드래그 이동·수직면 배치·배경
오버레이를 이미 검증했다. 이 Workstream 은 그 검증 코드를 설계서의 폴더/개념 구분에
맞게 재정리하고, 설계서가 요구하는 **핀치 크기 조절**을 추가해 정식 공간/AR 흐름의
출발점으로 삼는다.

## Current Direction

- **초기에는 1인(shinym87) 진행**이다. 설계서의 3역할(사용자 1/2/3) 병렬 대신
  한 브랜치에서 앱 → 서버 → 외부 AI 순으로 수직 슬라이스를 만든다. PHASE 0 완료.
- 아키텍처는 A(외부 AI 이미지 편집 API) 우선. B/C 는 PHASE 6/7 별도 실험.
- AR 라이브러리는 `io.github.sceneview:arsceneview:2.3.0` 을 계속 사용한다.
- `MainActivity` 한 파일이던 검증 코드를 역할별로 나눈다.
  - `ar/ArSpaceController` : 카메라 실행, 세션 설정, 평면 인식, hitTest
  - `furniture/FurnitureController` : 생성 · 선택 · 이동 · 크기 조절 · 삭제
  - `keyframe/BackgroundKeyframe` : 대표 이미지 캡처와 오버레이
  - `ui/FurnitureInfoDialog` : 이름 / 실물 크기 입력 팝업
- 가구는 아직 3D 모델(glTF) 이 아니라 반투명 큐브로 부피만 표현한다.
- 기술 선택은 이 Workstream 내부 가설이며 프로젝트 공통 표준이 아니다.

## Scope

- Android Studio 로 바로 열리는 Gradle 프로젝트 (wrapper 포함)
- `ARSceneView` 기반 카메라 미리보기 + `planeRenderer` (RENDER_ALL) 격자
- `hitTestAR` + `AnchorNode` + `CubeNode` 탭 배치, 이름표 빌보드
- 드래그 이동: `AnchorNode.pose` 갱신 후 `onMoveEnd` 에서 `Session.createAnchor` 재고정
- 크기 조절: SceneView `onScale` (핀치) 와 `＋`/`－` 버튼, 배율 0.3~3.0
- 수직면(벽) 배치: `Plane.Type.VERTICAL` 히트 시 큐브 회전 + 벽 바깥 오프셋
- 대표 이미지: `PixelCopy` 로 현재 창 캡처(가구 숨김) → `filesDir` 저장 → 반투명 오버레이
- 디버그 APK 빌드 및 `adb install` 절차 문서화

## Out of Scope

- 가구 회전, 실제 3D 가구 모델, 바닥/벽 자동 맞춤, 충돌 처리
- 사물 인식 및 AI 기반 빈 공간 복원 (사용자 2 영역)
- API 서버, 작업 세션, 가구 카탈로그 (사용자 3 영역)
- 공간 정합(Spatial Anchoring) / 재투영(Reprojection) 고도화 (설계서 PHASE 3)

## API / 입력 / 출력

- 입력: 기기 카메라, 화면 터치(탭 / 롱프레스 / 드래그 / 핀치)
- 출력: 화면 내 AR 렌더링, `filesDir/empty_background.png` (대표 이미지)
- 외부 연동 인터페이스는 아직 없다. 이후 사용자 2/3 와 연결할 때
  대표 이미지 + 카메라 Pose + 평면 정보 + 사물 영역을 함께 넘기는 형태를 예상한다.

## 선행 조건

- ARCore 지원 Android 실기기와 카메라 권한
- Android SDK, ADB, Gradle JDK 17 환경

## Known Issues

- SceneView `onScale` 콜백 시그니처는 v2.3.0 소스로 확인했으나, 실기기 빌드로
  핀치 동작을 아직 검증하지 않았다.
- 핀치와 드래그가 겹칠 가능성 (현재는 `draggingSelected` 가드로만 방어).
- 한글 저장소 경로에서 Kotlin 컴파일러 경로 문제 → `gradle.properties` 우회 설정 의존.

## Decisions

- `ar2` 검증 코드를 Branch 통째로 옮기지 않고, 역할별로 재정리한 파일만 가져온다.
- 크기 조절은 핀치를 기본으로 하되 `＋`/`－` 버튼도 함께 유지한다.
- PHASE 0 결정은 `experiments/shinym87/interior/docs/decisions.md` 에 기록:
  D1 아키텍처 A 우선, D2 1인 수직 슬라이스, D3 MVP 사물 영역 bbox, D4 SceneView.

## PHASE 0 산출물

`experiments/shinym87/interior/docs/` 에 정리했다.

- `phase-0.md` — 시연 시나리오·MVP 범위·체크리스트 상태·PHASE 1 진입 조건
- `architecture.md` — 시스템 구조, A/B/C 전략
- `data-model.md` — 좌표계·ID·사물 영역·키프레임·카메라 입력·가구 데이터 형식
- `api.md` — 앱 ↔ 서버 ↔ 외부 AI API 규격 초안
- `backlog.md` — PHASE 1 이슈 목록 + 아이디어 백로그
- `decisions.md` — 결정 기록

## 진행 상황

- PHASE 0: 완료 (`experiments/shinym87/interior/docs/`)
- PHASE 1 서버/통합 (P1-4~P1-7): 완료. `experiments/shinym87/interior/server/`
  FastAPI — 세션 생성, 키프레임 업로드/저장, 사물 정보 저장(형식만),
  외부 AI 연결 구조(mock + external 자리), 사물 제거 job, 가구 카탈로그 API.
  `pytest` 5개 통과. **전체 흐름 PC에서 검증 완료 (2026-09-02)** —
  `scripts/e2e_check.py` 로 합성 거실 이미지 1장을 써서 세션→키프레임 업로드→
  remove-object→job 폴링→결과 이미지 다운로드→서버 저장 파일 확인까지 실제
  HTTP 로 13단계 전부 PASS, 결과 이미지 정상 저장 확인. 인계: `docs/handoffs/user3.md`.
- PHASE 1 앱 (P1-2, P1-3, P1-8, P1-9): 완료(코드 + 디버그 APK 빌드 성공).
  TV 영역 드래그 지정 → 키프레임 캡처 → `POST /scenes` `/keyframes` `/remove-object`
  → job 폴링 → 결과 이미지를 벽 quad(또는 전체화면 대체)로 적용 → "삭제 전/후" 토글.
  서버 주소는 `http://localhost:8000` 하드코딩. `assembleDebug` 는 JBR 25 로는 실패하고
  `JAVA_HOME=C:\Users\User\.jdks\jbr-21.0.11` 로 성공. 인계: `docs/handoffs/user1.md`.
- 남은 것: 실기기 네트워크 검증(P1-11), 실제 외부 AI 연동(P1-10), 결과 정합 다듬기.

## Next

1. 서버를 `--host 0.0.0.0` 로 띄우고 앱의 `InteriorApiClient.DEFAULT_BASE_URL` 을
   PC LAN IP 로 바꿔 실기기에서 캡처→전송→응답→화면적용 한 번 관통 (P1-11).
2. 서버 `app/ai/external.py` 의 `TODO(P1-10)` 를 실제 외부 AI 호출로 교체 (P1-10).
3. 결과 quad 방향/스케일/정합 다듬기 (PHASE 3 로 이어짐).

## Relevant Commits

- 이 Workstream 등록 및 `experiments/shinym87/interior/` 초기 구현 커밋 (본 Branch)

## Updated

2026-09-02
