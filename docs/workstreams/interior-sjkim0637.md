# Workstream

## Topic

카메라 기반 공간 편집 및 AR 가구 재배치

## Owner

sjkim0637 + Codex

## Git Branch

`agent/sjkim0637/interior`

## Project Path

`experiments/sjkim0637/interior/`

## Status

IN_PROGRESS

## Goal

실제 거실에서 TV를 선택해 제거하고 가려진 벽을 복원한 뒤 TV와 새 소파를 AR 공간에 재배치하는 MVP를 검증한다.

## Background

기존 AR 가구 서비스가 새로운 가구 추가에 집중하는 것과 달리, 실제로 존재하는 가구를 제거하고 빈 공간을 복원한 후 재배치하는 경험을 핵심 차별점으로 삼는다.

## Current Direction

PHASE 0을 완료했다. PHASE 1의 Android AR 앱, FastAPI 서버, Mock AI 및 Gemini Provider 코드를 구현했고 서버 단위 테스트와 PC Mock E2E를 통과했다. 초기 시연과 MVP 범위는 고정하되 검증 결과에 따라 확장하거나 변경할 수 있다.

## Scope

- Android 카메라와 ARCore
- 벽·바닥 인식
- TV bbox 선택과 키프레임
- TV 제거와 벽 복원
- TV 및 소파 배치·이동·크기 조절
- 변경 전·후 비교
- 중간 API 서버와 외부 AI Provider

## Out of Scope

자동 사물 인식, 다중 가구 편집, 고품질 3D 모델, 정교한 그림자, 충돌 처리, 벽지·바닥 변경, 자체 AI 학습, 로그인·결제·상품 연동은 초기 MVP에서 제외한다.

## Key Questions

- 외부 AI 결과가 원본 벽의 색상·질감·원근을 충분히 보존하는가?
- AI 결과를 벽 Anchor에 표시했을 때 카메라 이동 중 안정적인가?
- 실기기에서 캡처부터 결과 표시까지 시연 가능한 응답 시간을 확보할 수 있는가?

## Decisions

`experiments/sjkim0637/interior/docs/decisions.md`를 따른다.

## Dependencies

ARCore 지원 Android 실기기, Android Studio/JDK, Python 실행 환경, 외부 AI API Key가 필요하다. API Key는 실제 AI 검증 단계 전까지 필요하지 않다.

## Notes for Other Teams

이 Workstream의 기술 선택은 프로젝트 전체 표준이 아니다. 다른 interior 구현과 비교한 뒤 필요한 Commit 또는 기능만 통합할 수 있다.

## Integration Candidate

TBD

## Known Issues

사용자가 보유한 기기가 iOS뿐이어서 Android 실기기 검증과 시연 녹화는 대기 중이다. 실제 Gemini 품질은 API Key가 없어 검증 전이다. 현재 실행 환경에서는 Gradle 배포 파일 다운로드가 차단되어 Android 빌드를 재검증하지 못했다.

## Next

Android 기기 또는 Emulator가 준비되면 앱 빌드와 E2E를 수행한다. 그 전에는 Gemini API Key가 준비될 경우 실제 이미지 품질을 검증한다.

## Relevant Commits

- `b717d47` PHASE 0 문서
- `2a2f228` Android AR 앱과 FastAPI PHASE 1 기반 구현

## Updated

2026-09-02
