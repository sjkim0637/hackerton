# Workstream

## Topic

Geo-Time AR Platform Core Prototype

## Owner

goguma-salad + Codex

## Branch

`agent/goguma-salad/geo-time-ar-v2`

## Status

IN_PROGRESS

## Goal

위치와 시간으로 주변 콘텐츠 후보를 조회하고, Android 기기의 6DoF Pose와 현재 시야를 이용해 실제로 표시할 콘텐츠를 선택·렌더링하는 핵심 흐름을 검증한다.

## Background

협업 Harness를 도입하기 전에 별도 로컬 저장소에서 Geo-Time AR 초기 Prototype을 개발했다. 기존 구현과 Commit 이력을 공식 작업 Branch로 안전하게 이전하고, 다른 탐색 방향과 비교하거나 필요한 기능만 선택적으로 통합할 수 있도록 Workstream으로 등록한다.

## Current Direction

FastAPI, PostgreSQL/PostGIS, MinIO를 이용해 `Geo + Time` 후보를 조회하고, Kotlin Android와 ARCore에서 거리·Camera View Cone을 포함한 6DoF 공간 조건으로 최종 표시 대상을 선택한다. 기존 구현의 구조와 기술 선택은 아직 프로젝트 전체 표준이 아니라 이 Workstream의 검증 대상이다.

## Scope

- GeoZone 근접 검색과 시간 기반 콘텐츠 후보 조회
- Moment와 Campaign 도메인 분리
- PostGIS, Migration, Seed를 포함한 Backend Prototype
- Android ARCore Session, Anchor Marker, 시간 Slider Prototype
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

## Decisions

- 후보 조회와 최종 가시성 선택을 분리한다.
- Backend는 `Geo + Time`, Android는 6DoF 공간 맥락을 중심으로 검증한다.
- 기존 기술 선택은 Workstream 내부 가설이며 프로젝트 전체 결정으로 간주하지 않는다.

## Dependencies

- ARCore 지원 Android 실기기와 Camera 권한
- Android SDK, ADB 및 Android Studio/JDK 환경
- Docker Desktop 기반 Backend·PostGIS·MinIO 실행 환경

## Notes for Other Teams

goguma-salad가 기존에 개발한 Prototype을 이 Branch에서 이어서 검증한다. 같은 문제를 다른 방식으로 탐색하는 Workstream을 제한하지 않으며, 현재 기술 Stack이나 설계를 프로젝트 공통 기준으로 가정하지 않는다. 재사용이 필요한 경우 전체 Branch보다 검증된 Commit 또는 기능 단위를 우선 검토한다.

## Integration Candidate

TBD

## Known Issues

- ARCore 지원 실기기에서 Camera와 Anchor의 최종 실행 검증이 남아 있다.
- 현재 Zone-local 좌표와 ARCore Session 좌표의 정합은 시작 위치·방향이 맞는 것으로 가정한다.
- 기존 저장소 Commit을 공식 작업 Branch로 이전하는 중이다.

## Next

1. 기존 Commit 이력을 `agent/goguma-salad/geo-time-ar-v2`로 이전한다.
2. 하네스 공통 문서와 기존 프로젝트 문서의 역할 충돌을 정리한다.
3. 자동 테스트와 Build 상태를 다시 확인한다.
4. ARCore 지원 실기기에서 Camera, Tracking, Anchor를 검증한다.

## Relevant Commits

기존 저장소 이력을 Branch로 이전한 뒤 갱신한다.

## Updated

2026-08-24
