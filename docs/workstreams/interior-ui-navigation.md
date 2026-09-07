# Workstream

## Topic

Interior AR 화면 분리와 생성 Asset 적용

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/interior-ui-navigation`

## Project Path

`experiments/shinym87/interior/app/`

## Status

REVIEW

## Goal

AR 기능이 바로 실행되던 단일 화면을 메인 진입 화면, 사용 방법, 서버 설정, AR 작업 화면으로 분리한다.
생성한 Interior Asset을 실제 Android Resource로 적용하고 실기기에서 바로 검토할 수 있는 APK를 제공한다.

## Current Direction

- `HomeActivity`가 Launcher이며 AR 인테리어, 사용 방법, 설정으로 이동한다.
- `MainActivity`는 카메라와 AR 조작에 집중하고 홈·설정 이동만 제공한다.
- 서버 주소는 `SettingsActivity`에서 저장하며 모든 API Controller가 같은 `SharedPreferences` 값을 읽는다.
- USB 테스트 기본 주소는 `adb reverse`에 맞춘 `http://127.0.0.1:8000`이다.
- 메인·설명·설정 화면에는 `experiments/shinym87/interior/assets/`에서 만든 이미지를 사용한다.

## Verification

- `:app:assembleDebug` 성공
- `adb install -r` 성공 — SM-S908N (`R5CT21P60GF`)
- `adb reverse tcp:8000 tcp:8000` 성공
- PC의 `http://127.0.0.1:8000/health` 응답 정상 — Mock AI Provider 준비됨
- `HomeActivity` 실행 명령 성공, 앱 Process 유지, 치명적 오류 없음
- 휴대폰 잠금 상태로 화면 육안 검토는 사용자의 잠금 해제 후 진행

## Known Issues

- Debug APK는 원본 PNG를 포함해 약 54MB이다. 배포용에서는 WebP 변환과 해상도별 최적화가 필요하다.
- AR 화면의 기존 다수 조작 Button은 기능 보존을 우선해 유지했다. 실제 사용 후 하단 도구 구조를 추가로 다듬을 수 있다.

## Next

1. 휴대폰 잠금 해제 후 메인·설명·설정 화면 Crop과 글자 크기 확인
2. 메인 화면에서 AR 진입 후 카메라 권한과 평면 인식 확인
3. 승인 후 통합 후보로 전달

## Integration Candidate

YES

## Updated

2026-09-07
