# Workstream

## Topic

Interior Demo 촬영 안정화

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/demo-recording`

## Project Path

`experiments/shinym87/interior/`

## Status

IN_PROGRESS

## Goal

2026-09-10 데모 영상에서 카탈로그 선택 → AR 배치 → 조정 → 구매/사물 지우기 흐름이 중단 없이 명확히 보이도록 한다.

## Direction

- 실기기 Depth 판정은 기록용으로만 남기고, 카탈로그 가구 배치와 드래그에는 이미 인식된 ARCore 평면을 우선 허용한다.
- 서버 연결이 없는 상황도 화면 흐름이 끊기지 않도록, 배치와 제품 정보는 로컬 mock으로 완결한다.
- 촬영 중 불필요한 고급 도구와 실패 메시지를 줄이고 현재 단계와 다음 행동이 한눈에 보이게 정리한다.

## Current Result

- `origin/integration-interior-demo`를 fetch했으며 최신 커밋은 `3028fb7`이다. 원격에 추가 변경은 없다.
- Depth 지원 기기에서도 평면 가이드를 표시하고, Depth 품질 판정이 거절되어도 이미 인식된 ARCore 평면이 있으면 배치와 드래그를 계속 허용하도록 변경했다.
- 가구를 놓으면 선택 조작 시트와 함께 배치 완료 안내를 표시한다.

## Scope

- 배치/이동 실패를 줄이는 데모 우선 fallback
- 배치 완료, 조정, 구매 mock의 상태 문구와 UI 정리
- Debug APK 빌드 및 시연 체크리스트 확인

## Out of Scope

- Depth 품질 gate의 정밀도 개선
- 사물 제거 AI 품질 개선과 서버 연동 검증
- 영구 저장과 재실행 복원

## Integration Candidate

TBD

## Updated

2026-09-10
