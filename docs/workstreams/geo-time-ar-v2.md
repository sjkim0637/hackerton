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
- POI별 Moment Stack Marker와 Phone용 미리보기·전체화면 재생 흐름
- 전체화면의 실제 Moment 간 좌우 Swipe 이동
- 거리와 Camera View Cone 기반 가시성 선택
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
- Phone은 Marker 터치, 5초 무음 미리보기, 화면 승인, 전체화면 재생을 사용한다.
- 전체화면에서만 좌우 Swipe로 실제 Moment 사이를 이동하고 날짜를 잠깐 표시한다.
- Glass는 향후 응시·음성·머리 Gesture를 입력 Adapter로 연결하되 6DoF 추적은 유지한다.
- 기존 기술 선택은 Workstream 내부 가설이며 프로젝트 전체 결정으로 간주하지 않는다.

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
- Android Unit Test 7개와 `assembleDebug`: 통과
- Moment Stack 묶음·정렬·Swipe 경계 Test와 Debug APK Build: 통과
- Docker Compose 설정 검사: 통과
- Docker Smoke Test: 통과
- ARCore 실기기 `SM-S908N`: APK 설치·실행 및 Backend 연결 통과
- 실기기에서 Media3 데모 영상 URL HTTP 200 응답 확인

## Known Issues

- 실기기에서 Marker 터치부터 5초 미리보기, 승인, 전체화면 Swipe까지 사람 눈으로 보는 최종 UX 평가는 남아 있다.
- 현재 Seed는 영상이 아닌 SVG Placeholder라 Phone UX 데모는 외부 Media3 테스트 영상을 대신 사용한다.
- 현재 Zone-local 좌표와 ARCore Session 좌표의 정합은 시작 위치·방향이 맞는 것으로 가정한다.
- 기존 저장소의 기술 선택은 아직 다른 Workstream과 비교·검토되지 않았다.

## Next

1. 실기기에서 Marker 터치부터 전체화면 이동까지 실제 사용감을 평가한다.
2. Seed에 자체 촬영한 실제 Moment 영상을 연결한다.
3. Zone-local 좌표와 실제 현장 좌표의 정합 방식을 비교한다.
4. Glass 입력 Adapter의 인터페이스를 구현할 시점을 정한다.

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

## Updated

2026-08-24
