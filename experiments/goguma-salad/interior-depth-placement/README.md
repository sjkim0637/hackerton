# Depth Placement Lab

ARCore Depth를 기기 안에서 Point Cloud로 변환하고 AR 객체의 배치 가능 Pose를 계산하는 독립 Android 모듈 실험이다. 네트워크 권한, 서버, AI Runtime, OpenCV를 사용하지 않는다.

전체 계산 흐름과 정확도 판별 기준은 [심도 기반 실시간 3D 공간 및 가구 배치 계산 정리](docs/depth-based-realtime-3d-space-and-furniture-placement.md)를 참고한다.

## Module

| Module | 역할 | 제품 앱 포함 여부 |
|---|---|---|
| `depth-placement-core` | Depth filtering, XYZ 생성, PCA plane/normal, slope·footprint·obstacle 판정 | 필수 |
| `depth-placement-arcore` | ARCore `Frame`을 core 입력으로 복사·변환 | ARCore Host에서 필수 |
| `depth-placement-debug` | Orbit, pinch zoom, freeze, normal·footprint overlay가 있는 OpenGL ES 3D viewer | 선택 |
| `test-app` | Main, Point Cloud Test, Settings를 제공하는 serverless 검증 앱 | 제품 앱에는 불필요 |

`depth-placement-core`는 Android 및 ARCore를 참조하지 않는 JVM 17 library다. Snapshot의 point 배열은 내부 buffer를 노출하지 않으며 `copyPoints()`로 `[x, y, z, confidence]` interleaved copy를 반환한다. 좌표는 ARCore world meter 좌표다.

## Build

Android SDK 35와 JDK 17 이상이 필요하다. 현재 PC에서는 JDK 21로 검증했다.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
.\gradlew.bat :depth-placement-core:test :test-app:assembleDebug
```

APK:

```text
test-app/build/outputs/apk/debug/test-app-debug.apk
```

## VS Code Tasks

Repository root를 VS Code로 연 뒤 `Terminal > Run Task...`에서 다음 task를 실행할 수 있다.

- `Depth: Verify All`: unit test → Android Lint → release AAR → debug APK 순차 검증. 기본 build task이므로 `Ctrl+Shift+B`로도 실행된다.
- `Depth: Core Unit Test`: core synthetic test만 실행
- `Depth: Android Lint`: 테스트 앱과 의존 모듈 정적 검사
- `Depth: Build Release AARs`: ARCore/debug module release AAR 생성
- `Depth: Build Debug APK`: 테스트 앱 debug APK 생성
- `Depth: Install Debug APK`: 연결된 Android 기기에 APK build 및 설치
- `Depth: Run Test App`: 설치 후 바로 `Point Cloud Test` 화면 실행
- `Depth: Clean`: 실험 프로젝트 build 산출물 정리

Task는 현재 검증된 `C:\Program Files\Java\jdk-21.0.12`를 `JAVA_HOME`으로 사용한다. 설치·실행 task에는 `ANDROID_HOME`과 USB debugging이 설정된 Android 기기가 필요하다.

## Main App Import

Host의 `settings.gradle`:

```groovy
include ':depth-placement-core', ':depth-placement-arcore'
```

Host module의 dependency:

```groovy
implementation project(':depth-placement-core')
implementation project(':depth-placement-arcore')
```

최소 사용 흐름:

```kotlin
val engine = DepthPlacementEngineFactory.create(PlacementConfig.default())
engine.start()

// ARCore Session은 시작 전에 ArCoreDepthAdapter.configure(session)로 Depth를 활성화한다.
ArCoreDepthAdapter.convert(frame)?.let { engine.updateDepthFrame(it.input) }

val snapshot = engine.getLatestPointCloud()
val result = engine.evaluatePlacement(
    screenX = depthPixelX,
    screenY = depthPixelY,
    objectSize = PlacementObjectSize(0.6f, 0.6f, 1.0f),
)
if (result.isValid) {
    val pose = result.pose // position(m), quaternion(x/y/z/w), surfaceNormal
}
```

`evaluatePlacement`의 좌표는 Android View pixel이 아니라 최신 Depth image pixel이다. 카메라 화면 터치 좌표는 `ArCoreDepthAdapter.viewToDepth(...)`로 변환한다. UI thread에서 계산하지 않도록 Host가 frame 변환과 `updateDepthFrame`을 worker thread에서 호출하거나 callback 기반 `evaluatePlacementAsync`를 사용한다.

## Test App

- Main: sensor/stream, resolution, FPS, point count, intrinsics, timestamp 차이, rolling 5초 처리시간
- Point Cloud Test: 실제 카메라 전체 화면 위에 같은 frame의 Depth sample을 가까움(빨강)→멀리(파랑) 색점으로 직접 투영
- 색상은 화면 내 유효 Depth의 5~95 percentile을 inverse-depth 상대 척도로 펼쳐 근거리 물체의 작은 깊이 차이를 강조
- 화면 투영점은 분석용 3D point보다 최대 4배 촘촘하게 생성하며, 분석용 point 수와 투영점 수를 HUD에 별도로 표시
- RGB 윤곽과 Depth 점의 정합을 즉시 비교하며 `Freeze`, `Points ON/OFF`, 객체 preset과 placement 결과를 확인
- Settings: 첫 화면은 `안정 / 균형 / 디테일` preset만 제공하며 전문 threshold는 접힌 `세부 설정`에서 조절
- 설정은 `SharedPreferences`에 로컬 저장되며 `Reset to Default`로 복원된다.

정상 동작이면 실제 카메라 속 벽·바닥·가구의 모서리 위에 Depth 색점이 겹친다. RGB 모서리와 점의 위치가 어긋나면 좌표 정합 문제이고, 점은 맞지만 듬성듬성하면 sampling 문제이므로 Settings에서 `디테일`을 선택한다. `Points`가 계속 0이면 Depth 미지원 또는 AR tracking 준비 중이다.

앱 Manifest에는 `INTERNET` 권한이 없다. 핵심 동작은 Wi-Fi와 Mobile Data 없이 실행된다.

## Algorithm

1. Depth16 millimetre와 raw confidence를 range/confidence/depth-jump filter에 통과시킨다.
2. stride와 최대 point 수를 적용하고 pinhole intrinsics로 camera XYZ를 계산한다.
3. ARCore camera pose로 world coordinate에 변환하고 optional temporal smoothing을 적용한다.
4. 터치 주변 ROI를 PCA로 plane fitting하여 최소 eigenvector를 surface normal로 선택한다.
5. slope와 plane error를 검사하고 객체 footprint 안의 surface coverage를 계산한다.
6. plane 위 `obstacleHeightThresholdMeters`보다 높은 point를 장애물로 판정한다.
7. 성공 시 surface center, up-to-normal quaternion, confidence를 `PlacementResult`로 반환한다.

## Known Issues

- ARCore Raw Depth 지원 기기의 실측 FPS, 해상도, timestamp 차이와 배치 품질은 아직 측정하지 않았다.
- `enableRansac`은 향후 확장용 API이며 현재 estimator는 PCA를 사용한다.
- RGB camera color 결합과 raw/filtered point를 동시에 보관하는 debug mode는 구현하지 않았다. Viewer 색은 depth 기반이다.
- 테스트 앱은 portrait 고정이며 기기 회전별 View-to-Depth 좌표 검증이 남아 있다.
