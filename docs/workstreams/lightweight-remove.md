# Workstream

## Topic

온디바이스 경량 사물 제거

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/lightweight-remove`

## Project Path

`experiments/shinym87/interior/` (통합 앱 Host)

## Status

IN_PROGRESS

## Goal

사물 제거에서 외부 Gemini 호출을 사용하지 않고, 선택한 사물의 온디바이스 마스크와 OpenCV Telea 복원으로 빠른 데모 결과를 만든다.

## Background

현재 흐름은 전체 화면을 서버에 업로드하고 외부 이미지 AI 결과를 기다린 뒤 2D patch를 AR 평면에 붙인다. 네트워크 대기와 카메라 이동 뒤의 시차가 데모 흐름을 느리고 불안정하게 만든다.

## Current Direction

- ML Kit Subject Segmentation으로 사람이 지정한 bbox 안의 전경 사물 마스크를 만든다.
- 마스크가 없거나 품질이 낮으면 bbox를 유지한다.
- OpenCV `INPAINT_TELEA`로 캡처 이미지의 선택 영역을 즉시 복원한다.
- Gemini 요청·서버 job·fallback은 이 실험에 넣지 않는다.
- 결과는 현재 AR patch 적용 경로를 재사용하되, 원격 요청 없이 화면에서 바로 완료한다.

## Scope

- Android 의존성, 사물 마스크 생성, Telea 복원, 기존 결과 표시 경로 연결
- 선택한 사물의 전경 마스크와 복원 시간 진단 로그
- Debug APK 빌드 및 실기기 확인 준비

## Out of Scope

- Gemini 또는 다른 외부 생성형 모델 fallback
- 서버 API와 DB 변경
- 여러 프레임 기반의 3D 배경 재구성

## Integration Candidate

TBD

## Verification

- `:app:assembleDebug` 성공
- ML Kit Subject Segmentation 의존성은 Play services가 기기에서 내려받도록 Manifest에 등록
- OpenCV 4.10.0 AAR을 포함한 범용 Debug APK 크기: 약 199MB. OpenCV의 여러 ABI 라이브러리가 모두 들어가므로 실기기 검증 뒤 ABI별 APK 분리가 필요함.

## Known Issues

- 온디바이스 분할은 전경 사물에 강하지만 벽에 붙은 평면 사물이나 복잡하게 겹친 가구를 항상 정확히 분리하지 못할 수 있다.
- 단일 이미지 patch를 AR 평면에 붙이는 구조는 카메라 이동 시 시차 문제가 남는다.
- ML Kit 모델은 Google Play services로 기기에서 처음 한 번 내려받아야 한다. 내려받기 전에는 bbox Telea 대체를 사용한다.
- OpenCV 네이티브 라이브러리 추가로 Debug APK 용량이 증가한다.

## Next

1. ML Kit와 OpenCV 의존성, 마스크·Telea 복원을 연결하고 Debug APK 빌드를 완료했다.
2. 실기기에서 벽·바닥의 단순 배경 사물을 지워 처리 시간과 시각적 품질을 기록한다.
3. 복잡한 배경과 벽걸이 사물에서 마스크 대체(bbox)가 발생하는 비율을 기록한다.

## Relevant Commits

- 시작 기준: `3028fb7`

## Updated

2026-09-09
