# ARCore 최소 검증 앱은 SceneView(arsceneview) 를 사용한다

## Status

Accepted

## Context

`ar-cube-min` Workstream 은 Android + Kotlin + ARCore 조합이 개발 환경과 실기기에서
동작하는지 확인하는 최소 앱이 필요하다. 요구 기능은 세 가지다.

1. 실시간 카메라 화면
2. ARCore 기본 평면 인식 + 격자 표시
3. 화면 탭으로 평면 위에 정육면체 배치

이 목적에 맞는 AR 렌더링 방식을 정해야 한다. 이 결정은 `ar-cube-min` 범위에
한정하며, 프로젝트 전체 AR 스택을 확정하지 않는다.

## Decision

`io.github.sceneview:arsceneview:2.3.0` (SceneView) 를 사용한다. 화면은
`ARSceneView` (View 기반, Compose 아님) 하나로 구성하고, 평면 격자는
`planeRenderer`, 탭 배치는 `hitTestAR` + `AnchorNode` + `CubeNode` 로 처리한다.

## Reason

- 카메라 배경 렌더링, 평면 인식 격자, 카메라 권한 요청, ARCore APK 설치 안내가
  라이브러리에 내장되어 있어 검증 코드가 `MainActivity` 한 파일로 끝난다.
- `CubeNode` 로 외부 3D 모델 파일 없이 정육면체를 만들 수 있어 에셋 의존이 없다.
- 개발 PC 에서 디버그 APK 빌드까지 실제로 확인했다 (AGP 8.9.1 / Gradle 8.11.1 /
  Kotlin 2.0.21, JDK 17).

## Alternatives

- **순수 ARCore + OpenGL ES (`hello_ar_kotlin` 방식)**: 공식 샘플이지만 렌더러
  보일러플레이트(BackgroundRenderer, Shader, Mesh, Framebuffer 등)가 많아 최소
  검증 목적에는 과하다. 저수준 제어가 필요할 때 재검토한다.
- **Sceneform**: Google 이 2020 년 아카이브했다. 유지되지 않아 제외.
- **arsceneview Compose API**: 동일 라이브러리의 Compose 버전. View 기반이 기존
  Android 예제와 통합 설명에 더 익숙해 View 기반을 선택했다.

## Impact

- `experiments/shinym87/ar2/` 앱은 SceneView 에 의존한다. 이 의존성은
  ARCore 1.48.0, Filament 1.56.0, kotlin-math, 일부 AndroidX/Compose 런타임
  라이브러리를 함께 가져온다 (arm64 네이티브 포함).
- `geo-time-ar-v2` 등 다른 AR Workstream 이 순수 ARCore 를 선택해도 무방하다.
  통합 시에는 전체 스택보다 검증된 코드 조각 단위로 비교한다.
- 프로젝트 공통 AR 프레임워크는 아직 미확정 상태로 둔다.
