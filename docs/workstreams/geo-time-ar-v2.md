# Workstream

## Topic

Geo-Time AR Platform Core Prototype

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/geo-time-ar-v2`

## Project Path

`experiments/goguma-salad/geo-time-ar-v2/`

## Status

IN_PROGRESS

## Goal

위치와 시간으로 주변 콘텐츠 후보를 조회하고, Android 기기의 6DoF Pose와 현재 시야를 이용해 실제로 표시할 콘텐츠를 선택·렌더링하는 핵심 흐름을 검증한다.

## Background

협업 Harness를 도입하기 전에 별도 로컬 저장소에서 Geo-Time AR 초기 Prototype을 개발했다. 기존 구현과 Commit 이력을 공식 작업 Branch로 안전하게 이전하고, 다른 탐색 방향과 비교하거나 필요한 기능만 선택적으로 통합할 수 있도록 Workstream으로 등록한다.

## Current Direction

FastAPI, PostgreSQL/PostGIS, MinIO를 이용해 `Geo + Time` 후보를 조회하고, Kotlin Android와 ARCore에서 거리·Camera View Cone을 포함한 6DoF 공간 조건으로 최종 표시 대상을 선택한다. 기존 구현의 구조와 기술 선택은 아직 프로젝트 전체 표준이 아니라 이 Workstream의 검증 대상이다.

구현과 전용 기술 문서는 [`experiments/goguma-salad/geo-time-ar-v2/`](../../experiments/goguma-salad/geo-time-ar-v2/)에 격리한다.

## Scope

- GeoZone 근접 검색과 시간 기반 콘텐츠 후보 조회
- Moment와 Campaign 도메인 분리
- PostGIS, Migration, Seed를 포함한 Backend Prototype
- Android ARCore Session과 Anchor Marker Prototype
- POI별 Moment Stack Marker와 Phone용 미리보기·콘텐츠 집중 재생 흐름
- 콘텐츠 집중 화면의 실제 Moment 간 좌우 Swipe 이동
- Phone·Glass 데모 Mode 전환과 ARCore Pose 기반 응시·Head Gesture 입력
- 거리와 Camera View Cone 기반 가시성 선택
- POI WGS84, GPS·True North와 ARCore Camera Pose 기반 자동 Session 좌표 정렬
- 재현 가능한 로컬 실행, 자동 테스트 및 관련 설계 문서
- 기존 로컬 Git Commit 이력의 공식 Branch 이전

## Out of Scope

- 운영 환경 배포와 Cloud Infrastructure
- 실제 인증, 결제, CDN, Admin 및 추천 AI
- iOS와 AR Glass 연동
- 제품 전체 Architecture 또는 공통 기술 Stack 확정

## Key Questions

- ARCore 지원 실기기에서 Camera, Tracking, Anchor가 의도대로 동작하는가?
- Zone-local 좌표와 ARCore Session 좌표를 실제 현장에서 어떻게 안정적으로 정합할 것인가?
- Geospatial API, Cloud Anchor, 현장 Calibration 중 어떤 방식이 요구사항에 적합한가?
- 현재 Prototype 중 다른 Workstream에서도 재사용할 공통 기능은 무엇인가?
- Phone과 Glass가 공유할 콘텐츠 상태 흐름과 기기별 입력 Adapter의 경계는 어디인가?

## Decisions

- 후보 조회와 최종 가시성 선택을 분리한다.
- Backend는 `Geo + Time`, Android는 6DoF 공간 맥락을 중심으로 검증한다.
- AR 탐색 화면에서는 날짜 Timeline을 노출하지 않고 POI별 Moment Stack Marker를 사용한다.
- Phone은 Marker 터치, 5초 무음 미리보기, 화면 승인, AR 배경을 유지하는 콘텐츠 집중 재생을 사용한다.
- 콘텐츠 집중 화면에서만 좌우 Swipe로 실제 Moment 사이를 이동하고 날짜를 잠깐 표시한다.
- Glass 데모는 ARCore Pose로 응시·머리 Gesture를 모사하며 6DoF 추적은 유지한다.
- 앱이 활성화된 동안 자동 화면 꺼짐을 막는다.
- Glass는 6DoF 공간 Preview 승인 후 시야 정면의 3DoF형 Screen으로 전환하고 영상 종료 시 AR로 복귀한다.
- 조작 안내는 Popup이 아니라 현재 상태에 맞춰 바뀌는 Coach Mark로 표시하며 앱 설정에서 숨길 수 있다.
- 실제 제품은 공통 상태 흐름 위에 Phone과 Glass Presentation·Tracking Adapter를 분리한다.
- 기존 기술 선택은 Workstream 내부 가설이며 프로젝트 전체 결정으로 간주하지 않는다.
- QR이나 수동 Landmark Calibration 대신 두 기준좌표로 산출한 POI 좌표와 GPS·True North 자동 Session 정렬을 사용한다.

## Dependencies

- ARCore 지원 Android 실기기와 Camera 권한
- Android SDK, ADB 및 Android Studio/JDK 환경
- Docker Desktop 기반 Backend·PostGIS·MinIO 실행 환경

## Notes for Other Teams

goguma-salad가 기존에 개발한 Prototype을 `experiments/goguma-salad/geo-time-ar-v2/`에서 이어서 검증한다. 같은 문제를 다른 방식으로 탐색하는 Workstream을 제한하지 않으며, 현재 기술 Stack이나 설계를 프로젝트 공통 기준으로 가정하지 않는다. 재사용이 필요한 경우 전체 Branch보다 검증된 Commit 또는 기능 단위를 우선 검토한다.

## Integration Candidate

TBD

## Verification

2026-08-24 Branch 이전과 실험 폴더 격리 후 다음 항목을 새 경로에서 다시 확인했다.

- Ruff: 통과
- Backend Pytest: 15개 통과
- Android Unit Test 11개와 `assembleDebug`: 통과
- Moment Stack 묶음·정렬·Swipe 경계 Test와 Debug APK Build: 통과
- 끄덕임·좌우 왕복·느린 회전 제외·각도 경계 Test: 통과
- Docker Compose 설정 검사: 통과
- Docker Smoke Test: 통과
- ARCore 실기기 `SM-S908N`: APK 설치·실행 및 Backend 연결 통과
- 실기기에서 Media3 데모 영상 URL HTTP 200 응답 확인
- Glass 데모 포함 최신 APK 실기기 설치 및 앱 기동 통과
- Cyberpunk 시작 화면과 상태 UI 적용 후 Android Unit Test 11개와 `assembleDebug`: 통과
- Viewer 개발·진단 도구의 설정 이동과 투명 PNG Asset 교체 후 Android Unit Test 11개와 `assembleDebug`: 통과
- Android 13+ System Back Dispatcher를 포함한 화면 단계별 복귀 처리 후 Android Unit Test 11개와 `assembleDebug`: 통과
- Phone·Glass 영상 종료 후 AR Marker 복귀와 Preview Frame 제거 후 Android Unit Test 11개와 `assembleDebug`: 통과
- Demo Zone을 을지로 타워 107로 이전한 뒤 Ruff, Backend Pytest 15개와 Docker Smoke Test: 통과
- Glass 전체 재생의 상하 Pitch 15도 AR 복귀 처리 후 Android Unit Test 12개와 `assembleDebug`: 통과, 이후 Roll 15도로 대체
- Phone·Glass 공통 대형 Compass Tape와 좌하단 Pitch·Roll 연동 Artificial Horizon HUD 적용 후 Android Unit Test 12개, `assembleDebug`, 실기기 배치 확인: 통과
- 기본 Local Demo 전환과 AR Tracking 시작 자세 기준 Roll 영점 보정 후 Android Unit Test 12개와 `assembleDebug`: 통과
- Demo의 을지로 타워 107 Backend 좌표 조회와 USB API·Media 이중 Reverse 자동화 후 Backend Health·Nearby·Timeline API 확인: 통과
- Glass 전체 재생 Roll 15도 AR 복귀와 좌하단 소형 원형 Artificial Horizon 적용 후 Android Unit Test 12개, `assembleDebug`, 실기기 Backend·화면 확인: 통과
- Demo·USB·운영 Server Profile, API·Media 주소 저장, 이중 연결 Test와 실패 원인 표시, Media Origin 재작성 적용 후 Android Unit Test 16개와 `assembleDebug`: 통과
- POI 절대좌표·선택 표고 API와 Migration 적용 후 Ruff와 Backend Pytest 16개: 통과
- WGS84 ENU, True North, ARCore Session Transform과 고정 6DoF 배치 적용 후 Android Unit Test 20개와 `assembleDebug`: 통과
- 로컬 `기준좌표 1`, `기준좌표 2` 등록과 사용 가능 기준점 거리순 조회 API 추가 후 Ruff와 Backend Pytest 17개: 통과
- Compass Tape·상단 안내 분리, Pitch·Roll HUD 축소와 Popup 최상위 계층 정리 후 Android Unit Test 20개, `assembleDebug`, 실기기 APK 설치: 통과
- Display Cutout 아래 Compass 배치와 홈 버튼 없는 3줄 Compact 안내 적용 후 Android Unit Test 20개, `assembleDebug`, 실기기 APK 설치: 통과
- 마지막 Viewer Mode, Preview 음소거·자동재생, 권한 상태, App·Backend Version 적용 후 Ruff, Backend Pytest 17개, Android Unit Test 20개와 `assembleDebug`: 통과

## Known Issues

- 실기기 잠금 해제 후 Phone과 Glass 데모 전체 흐름을 사람 눈으로 보는 최종 UX 평가는 남아 있다.
- Creator 진입 화면과 3단계 Flow는 준비됐지만 촬영·Gallery 선택·공간 배치·Upload 동작은 P1 구현 전이다.
- 현재 Seed는 영상이 아닌 SVG Placeholder라 Phone UX 데모는 외부 Media3 테스트 영상을 대신 사용한다.
- Tower 107 POI는 아직 실제 기준좌표 측량 결과가 아니라 기존 Seed 좌표를 사용한다.
- 원본 좌표 성과표는 저장소에 보관하지 않으며, 외부 좌표 자동 동기화와 Open API 인증키 연결은 후속 작업이다.
- 수직 위치는 POI와 Phone 양쪽에 타원체고가 있을 때만 자동 반영하며 국가 표고, 지면 Plane과 Camera 높이의 현장 보정은 남아 있다.
- 주변 GeoZone 조회는 Android가 제공하는 최근 GPS 기록을 사용하며 앱에 위치를 별도 저장하거나 임의의 기본 좌표로 대체하지 않는다.
- `SM-S908N` 야외 실측에서 유효 ADR이 0개로 확인되어 RTK는 P0·P1에서 `SKIP`한다.
- 기존 저장소의 기술 선택은 아직 다른 Workstream과 비교·검토되지 않았다.

## Next

상세 우선순위와 Checkbox는 [`제품 TODO`](../../experiments/goguma-salad/geo-time-ar-v2/docs/product-backlog.md)에서 관리한다.

1. `기준좌표 1`, `기준좌표 2`와 Tower 107 사이 측량 관측값으로 POI 좌표·표고를 확정한다.
2. Tower 107에서 자동 Session 정렬의 수평·수직 오차를 측정한다.
3. POI·Moment API, Media Storage와 Upload 흐름의 책임·배포 구조를 결정한다.
4. 외부 Backend와 Object Storage 최소 운영 환경을 구성한다.
5. 실제 Demo 영상을 교체하고 Preload와 Cache를 적용한다.
6. Creator 촬영·공간 배치·Upload MVP를 구현한다.

## Relevant Commits

- `e94334e` → `1bdf9bf` chore: initialize repository structure
- `4947562` → `1829181` feat: add postgis backend and docker environment
- `6e4439b` → `8f2eb45` feat: initialize android arcore application
- `24e12d8` → `4f5a297` docs: add architecture and verification guides
- `4d0d99e` → `83f565c` fix: route android debug api through adb reverse
- `508424c` → `7bfb9c9` feat: gate spatial content by geozone proximity
- `b430c3d` refactor: isolate Geo-Time AR experiment workspace
- `79e0b0e` feat(android): 실제 Moment를 넘기는 리와인드 제스처 구현
- `94aa987` fix(android): 리와인드 제스처 방향을 오른쪽 과거로 변경
- `c73c11b` feat(android): 기기별 콘텐츠 재생 UX 구현
- `2de87d5` feat(android): Glass 데모 제스처 모드 구현
- `76f0d6f` fix(android): Phone 전체화면 닫기 버튼 추가 — 이후 `d6a6a2b`로 대체
- `d6a6a2b` fix(android): AR 배경을 유지하는 Phone 재생 화면 적용
- `eb27be1` fix(android): Glass 시선 종료와 화면 유지 적용
- `3944f6c` feat(android): 모드별 조작 안내 설정 추가
- `3cc0c45` chore(android): 바인딩과 APK 설치 작업 추가
- `9ba3fb2` fix(android): 조작 안내를 동작별 Coach Mark로 변경
- `cb44c34` chore(android): Backend 바인딩 전용 작업 추가
- `d9f661d` fix(android): Glass 재생을 3DoF 정면 스크린으로 변경
- `a31e5fa` fix(android): 고정 좌표 대신 마지막 GPS 기록 사용
- `238e026` feat(android): 저장 없는 내장 GNSS 진단 화면 추가
- `8cf7961` fix(android): GNSS 진단 중 Full Tracking 활성화
- `e30a30c` docs: 사이버펑크 UI 전체 디자인 프롬프트 작성
- `156916d` docs: UI용 이미지 조각 생성 프롬프트 추가
- `e44f8e7` feat(android): Server Profile 설정과 연결 진단 추가
- `eba517e` feat(spatial): 익명 기준좌표 기반 자동 AR 정렬 구현
- `9e37e16` fix(android): Viewer HUD 겹침과 Popup 계층 정리
- `c917b86` fix(android): Pinhole 아래로 Viewer 안내 축소
- `7082d41` feat(p0): Viewer 설정과 Version 진단 마무리
- `4dce3ee` fix(backend): Supabase와 Vercel 운영 연결 안정화

## Updated

2026-08-26

## Backend Deployment Update — 2026-08-26

- Vercel Production `https://geo-time-ar-v2.vercel.app` 배포 완료
- Supabase PostgreSQL/PostGIS Migration `0002` 적용 및 Demo Seed 입력 완료
- Production `/health`, `/health/ready`, 주변 GeoZone, POI, Timeline, 가까운 기준좌표 조회 확인
- Backend Ruff 통과, Pytest 19개 통과
- Storage Bucket은 구성했으며 Creator Upload API와 접근 정책 연결은 P1 전 후속 작업
- Android `운영` Profile 기본 API를 Vercel Production으로, Media Endpoint를 Supabase Storage로 연결
- Android `0.1.1` Demo Preview Marker를 초기 Camera 정면 4m에 배치하고 시야 밖 탐색 안내 추가
