# Workstream

## Topic

Interior Demo v1 APK 및 VS Code Task

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/demo-v1`

## Project Path

`experiments/shinym87/interior/`

## Status

INTEGRATION

## Goal

`integration-interior-demo`의 최신 변경을 기준으로 데모용 Debug APK를 만들고, VS Code에서 빌드·설치·실행할 수 있는 Task를 제공한다.

## Current Direction

- 기준 Commit은 `f3f291e`이다. 이 Commit에는 삭제 후 이동, 이동 객체 크기, 라이브 배경 전환, 데모 UI, 진단 로그 관련 변경이 포함된다.
- 기본 빌드 Task는 `:app:assembleDebug`이며 JDK 21(`C:\\Program Files\\Java\\jdk-21.0.12`)을 사용한다.
- 설치와 실행은 각각 `:app:installDebug`, `adb shell am start -n com.hackathon.interior/.CatalogActivity`를 사용한다.

## Verification

- Debug APK 생성: `app/build/outputs/apk/debug/app-debug.apk`
- APK 서명 검증 완료
- 패키지: `com.hackathon.interior`, `versionName 1.0`, `minSdk 24`, `targetSdk 35`

## Known Issues

- 현재 연결된 Android 기기가 없어 설치 및 실제 AR 동작은 검증하지 못했다.
- Android Studio 기본 JBR은 Gradle 8.11.1과 호환되지 않아, Task에서 JDK 21을 명시한다.

## Next

1. ARCore 지원 기기에서 `Interior Demo v1: Install Debug APK`와 `Interior Demo v1: Run on Device`를 실행한다.
2. 삭제 후 이동 시나리오와 이동 객체 크기 보정을 기기에서 확인한다.

## Relevant Commits

- `f3f291e`: `agent/shinym87/interior_dev`의 데모 안정화 변경 통합

## Updated

2026-09-09
