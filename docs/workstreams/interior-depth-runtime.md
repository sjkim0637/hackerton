# Workstream

## Topic

Interior 앱의 Depth world Pose 직접 배치

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/interior-depth-runtime`

## Project Path

`experiments/shinym87/interior/`

## Status

INTEGRATION

## Goal

Depth 지원 기기에서 ARCore 평면 격자와 plane hit를 먼저 기다리지 않고, 사용자가 터치한 Depth 표면의 world Pose에 가구 Anchor를 직접 생성한다. Depth 미지원 또는 frame 준비 전에는 기존 ARCore plane placement를 fallback으로 유지한다.

## Current Direction

- `DepthPlacementResult.pose`를 ARCore `Pose`로 변환해 `Session.createAnchor()`에 직접 전달한다.
- 바닥·벽 카탈로그는 지정된 표면으로 판정하고, 일반 가구는 바닥 판정 후 벽 판정을 시도한다.
- Depth 지원 세션에서는 평면 격자 renderer를 숨긴다.
- 가구 이동을 끝낼 때도 Depth Pose로 Anchor를 갱신한다.
- 계산과 surface 판정은 독립 Depth 모듈, Anchor와 model UX는 Interior Host가 담당한다.

## Known Issues

- 드래그 중 실시간 preview는 AR plane hit가 있을 때만 움직이며, Depth 전용 위치는 손을 뗄 때 확정된다.
- Depth frame이 아직 없는 초기 순간에는 보이지 않는 AR plane fallback을 사용할 수 있다.
- 기기별 Depth 품질과 배치 오거절률은 실기기 관찰이 필요하다.

## Verification

- Depth Core synthetic test 14개 통과
- Depth 직접 Pose를 포함한 Interior debug APK build 통과
- Interior Android Lint 통과(오류 0건)
- `SM-S908N` 재설치 중 USB 연결이 해제되어 새 APK의 화면 확인은 보류

## Next

1. `SM-S908N`에 APK 설치 후 평면 격자가 사라지는지 확인
2. Chair 바닥 배치와 Picture Frame 벽 배치 확인
3. 배치/이동 오거절 메시지 및 anchor 안정성 확인
4. `integration-interior-demo` 병합 후 실기기 검증 결과를 반영

## Relevant Commits

- `b87a6c7` — ARCore 평면 격자 대신 Depth world Pose로 직접 Anchor를 생성하는 Runtime 통합

## Updated

2026-09-08
