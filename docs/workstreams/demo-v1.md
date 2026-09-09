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
- 카탈로그의 `sofa`, `chair`, `table`, `tv`, `shelf`는 APK의 `res/raw` GLB를 우선 사용한다. 대상은 각각 `GlamVelvetSofa`, `SheenChair`, `tableCoffee`, `televisionModern`, `bookcaseOpen`이다.
- GLB는 원본 PBR 재질을 보존한다. 따라서 선택 강조에서 단일 색상 재질을 덮어쓰지 않는다.
- 화보의 가구 점을 누르면 `AR로 배치` 또는 `구매하기`를 고른다. 구매 흐름은 상품별 Mock 가격을 보여 주고 `주문하기` 후 접수 Toast를 표시한다.

## Verification

- Debug APK 생성: `app/build/outputs/apk/debug/app-debug.apk`
- APK 서명 검증 완료
- 패키지: `com.hackathon.interior`, `versionName 1.0`, `minSdk 24`, `targetSdk 35`
- AR 우선 UI 컴파일 및 APK 재생성 완료: `:app:compileDebugKotlin :app:processDebugResources`, `:app:assembleDebug`
- 상단 설정 단일 버튼, 도구 FAB 겹침 방지, `3D 조정` 패드 적용 후 `:app:assembleDebug` 성공
- GLB 5종 내장 및 모델 로더 연결 후 `:app:assembleDebug` 성공
- 화보 AR/구매 분기와 주문 Mock 적용 후 `:app:assembleDebug` 성공

## UI Direction

- 기본 화면에서는 하단 도구 시트를 숨기고 우하단 원형 도구 버튼만 표시한다.
- 도구 시트를 열면 원형 도구 버튼을 숨겨 같은 우하단 영역에서 겹치지 않게 한다.
- AR 화면 상단은 설정 버튼만 남기고, 가구 이름·배치 안내는 화면을 가리지 않도록 숨긴다.
- 가구를 선택하면 크기·회전을 하나의 중앙 다이얼 안 위·아래·좌·우 `3D 조정` 패드로 조작한다. 관련 없는 `다른 가구`·`사물 지우기`는 이때 숨기고, 삭제와 완료는 짧은 두 버튼으로 유지한다.
- `완료` 또는 뒤로가기로 도구 시트를 다시 접어 카메라와 AR 객체가 가려지지 않게 한다.

## Known Issues

- 현재 연결된 Android 기기가 없어 설치 및 실제 AR 동작은 검증하지 못했다.
- GLB는 정상적인 glTF 2.0 바이너리 형식을 확인했지만, 실제 기기에서 PBR 재질·그림자·첫 로딩 시간과 카탈로그 치수 대비 비율을 확인해야 한다.
- 외부 에셋의 재배포 라이선스는 원본 제공처 기준으로 별도 확인이 필요하다.
- Android Studio 기본 JBR은 Gradle 8.11.1과 호환되지 않아, Task에서 JDK 21을 명시한다.

## Next

1. ARCore 지원 기기에서 `Interior Demo v1: Install Debug APK`와 `Interior Demo v1: Run on Device`를 실행한다.
2. 삭제 후 이동 시나리오와 이동 객체 크기 보정을 기기에서 확인한다.

## Relevant Commits

- `f3f291e`: `agent/shinym87/interior_dev`의 데모 안정화 변경 통합

## Updated

2026-09-09
