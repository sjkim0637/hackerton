# 모바일 온디바이스 배치용 실시간 IR Depth Point Cloud 모듈 설계서

## 1. 목적

LiDAR 없이 스마트폰의 IR/Depth 데이터를 이용하여 **AR 객체 배치에 직접 사용할 수 있는 실시간 3D Point Cloud**를 생성한다.

본 구현의 최종 목표는 단순한 3D 시각화가 아니라 다음이다.

> 카메라가 바라보는 실제 공간의 Depth를 실시간 3D Point Cloud로 변환하고, 배치 가능한 Surface와 장애물을 판단하여 AR 객체를 안정적으로 배치할 수 있는 Pose를 계산하는 온디바이스 Placement Geometry Engine을 독립 모듈 형태로 제공한다.

모든 처리는 모바일 기기 내부에서 수행한다.

외부 서버, 클라우드 API, 서버 추론, 서버 렌더링은 사용하지 않는다.

---

# 2. 개발 형태

본 기능은 앱 내부 기능으로 직접 구현하지 않는다.

반드시 **독립 모듈 형태**로 개발한다.

목표는 다른 Android 프로젝트에서 다음 정도의 최소 작업만으로 사용할 수 있게 하는 것이다.

```text
1. module dependency 추가
2. import
3. initialize
4. Depth Frame 또는 AR Frame 전달
5. PlacementResult 수신
```

예시 개념:

```kotlin
import com.project.depthplacement.DepthPlacementEngine
import com.project.depthplacement.PlacementConfig
import com.project.depthplacement.PlacementResult
```

초기화:

```kotlin
val engine = DepthPlacementEngine(
    context = context,
    config = PlacementConfig.default()
)
```

사용:

```kotlin
engine.onDepthFrame(
    depthFrame = frame,
    cameraIntrinsics = intrinsics,
    cameraPose = pose
)
```

배치 평가:

```kotlin
val result = engine.evaluatePlacement(
    screenX = x,
    screenY = y,
    objectSize = PlacementObjectSize(
        widthMeters = 0.8f,
        depthMeters = 0.6f,
        heightMeters = 1.2f
    )
)
```

---

# 3. 모듈 독립성 원칙

모듈은 다음에 종속되면 안 된다.

- 특정 Activity
- 특정 Fragment
- 특정 Compose Screen
- 특정 View
- 특정 테스트 앱
- 특정 AR 객체 Renderer
- 특정 서버
- 특정 Backend
- 특정 UI Flow

모듈은 다음 역할만 담당한다.

```text
Depth
↓
Point Cloud
↓
Geometry
↓
Placement Analysis
↓
PlacementResult
```

UI와 렌더링은 모듈 외부에서 담당한다.

---

# 4. 전체 프로젝트 구조

권장 구조:

```text
root/
│
├─ depth-placement-core/
│   ├─ src/main/java/...
│   └─ build.gradle.kts
│
├─ depth-placement-arcore/
│   ├─ src/main/java/...
│   └─ build.gradle.kts
│
├─ depth-placement-debug/
│   ├─ src/main/java/...
│   └─ build.gradle.kts
│
├─ test-app/
│   ├─ src/main/java/...
│   └─ build.gradle.kts
│
└─ settings.gradle.kts
```

---

# 5. 모듈 분리

## 5.1 depth-placement-core

핵심 알고리즘 모듈.

포함:

```text
DepthProcessor
PointCloudGenerator
DepthFilter
LocalSurfaceEstimator
NormalEstimator
SurfaceClassifier
ObstacleChecker
ClearanceChecker
PlacementEvaluator
PlacementConfig
PlacementResult
PlacementObjectSize
```

ARCore 직접 의존은 최소화한다.

가능하면 순수 Kotlin/Android 계산 모듈로 유지한다.

---

## 5.2 depth-placement-arcore

ARCore Adapter 계층.

역할:

```text
ARCore Depth Image
ARCore Intrinsics
ARCore Camera Pose
        ↓
Core Module에서 사용하는 형태로 변환
```

예:

```kotlin
val input = ArCoreDepthAdapter.convert(frame)
engine.update(input)
```

장점:

향후 다른 Depth Provider가 들어와도 core를 유지할 수 있다.

---

## 5.3 depth-placement-debug

개발 및 테스트용 Debug Renderer.

포함:

- 3D Point Cloud Renderer
- Surface Normal 표시
- Candidate Plane 표시
- Object Footprint 표시
- Obstacle Point 표시
- FPS HUD
- Point Count HUD

실제 제품 앱에서는 포함하지 않아도 된다.

---

# 6. 모듈 Public API

외부 프로젝트에서는 내부 구현 클래스를 직접 접근하지 않는다.

Public API를 명확하게 제한한다.

권장:

```kotlin
DepthPlacementEngine
PlacementConfig
PlacementResult
PlacementObjectSize
PointCloudSnapshot
PlacementListener
```

---

# 7. Engine API

예시:

```kotlin
interface DepthPlacementEngine {

    fun start()

    fun stop()

    fun updateDepthFrame(
        frame: DepthFrameInput
    )

    fun evaluatePlacement(
        screenX: Float,
        screenY: Float,
        objectSize: PlacementObjectSize
    ): PlacementResult

    fun getLatestPointCloud(): PointCloudSnapshot?

    fun updateConfig(
        config: PlacementConfig
    )

    fun release()
}
```

---

# 8. 비동기 API

모바일에서 Geometry 계산이 길어질 수 있으므로 비동기 방식도 지원한다.

예:

```kotlin
suspend fun evaluatePlacementAsync(
    screenX: Float,
    screenY: Float,
    objectSize: PlacementObjectSize
): PlacementResult
```

또는 Callback:

```kotlin
engine.evaluatePlacement(
    x,
    y,
    objectSize
) { result ->
    ...
}
```

Render Thread를 블로킹하면 안 된다.

---

# 9. PointCloudSnapshot

Debug Renderer 또는 외부 앱에서 Point Cloud를 시각화할 수 있도록 Snapshot API를 제공한다.

예:

```kotlin
data class PointCloudSnapshot(
    val points: FloatArray,
    val pointCount: Int,
    val timestampNanos: Long,
    val coordinateSystem: CoordinateSystem
)
```

가능하면 내부 Buffer를 그대로 노출하지 않는다.

필요하면 read-only view 또는 copy 정책을 명확히 한다.

---

# 10. Configuration 구조

모든 주요 동작값은 코드에 하드코딩하지 않는다.

`PlacementConfig`로 관리한다.

예:

```kotlin
data class PlacementConfig(
    val globalStride: Int,
    val roiStride: Int,
    val minDepthMeters: Float,
    val maxDepthMeters: Float,
    val depthConfidenceThreshold: Float,
    val temporalSmoothingAlpha: Float,
    val maxPointCount: Int,
    val roiSizePixels: Int,
    val minValidPointCount: Int,
    val maxSurfaceSlopeDegrees: Float,
    val obstacleHeightThresholdMeters: Float,
    val planeDistanceThresholdMeters: Float,
    val enableTemporalSmoothing: Boolean,
    val enableRansac: Boolean
)
```

---

# 11. 기본값

권장 초기 기본값:

```text
globalStride = 4
roiStride = 1

minDepth = 0.2m
maxDepth = 5.0m

confidenceThreshold = 0.5

maxPointCount = 20,000

ROI = 15 × 15 pixel

minValidPointCount = 20

maxSurfaceSlope = 15°

obstacleHeightThreshold = 0.05m

temporalSmoothing = ON
```

실제 값은 기기 테스트 후 조정한다.

---

# 12. 테스트 앱

Core 모듈과 별도로 **독립 테스트 앱**을 제작한다.

테스트 앱의 목적은 다음이다.

- Depth 입력 상태 확인
- Point Cloud 시각 확인
- 성능 측정
- 센서 특성 파악
- 설정값 튜닝
- 배치 알고리즘 검증
- 향후 메인 프로젝트 Import 전 사전 검증

앱 이름 예:

```text
Depth Placement Lab
```

또는:

```text
PC Placement Tester
```

---

# 13. 테스트 앱 메뉴 구조

테스트 앱은 최소 다음 3개 메뉴를 가진다.

```text
Main
Point Cloud Test
Settings
```

하단 Navigation 또는 Drawer 구조 중 현재 프로젝트 스타일에 맞게 구현한다.

---

# 14. Main 화면

Main 화면은 전체 상태 확인용 Dashboard 역할을 한다.

표시 항목:

```text
Depth Sensor
지원 / 미지원

Depth Stream
Running / Stopped

Depth Resolution
320 × 240

Depth FPS
xx

Render FPS
xx

Point Count
xxxx

Min Depth
x.xx m

Max Depth
x.xx m

Average Processing Time
xx ms
```

버튼:

```text
[Start Depth]
[Stop Depth]

[Open Point Cloud Test]

[Reset Config]
```

---

# 15. Main 상태 카드

예:

```text
Depth Status
────────────
Sensor        : Available
Depth Stream  : Running
Resolution    : 320 × 240
Depth FPS     : 24

Point Cloud
────────────
Points        : 4,812
Processing    : 5.4 ms

Placement
────────────
Last Result   : VALID
Confidence    : 0.87
```

---

# 16. Point Cloud Test 화면

핵심 테스트 화면.

반드시 **실시간 3D Viewer**를 제공한다.

단순 Depth Heatmap으로 대체하면 안 된다.

화면 구성:

```text
┌─────────────────────────┐
│                         │
│    3D Point Cloud        │
│                         │
│                         │
└─────────────────────────┘

Depth FPS : 24
Render FPS: 58
Points    : 4821

[Reset View]
[Freeze]
[Placement Test]
```

---

# 17. Point Cloud Viewer 조작

필수:

```text
Drag
→ Orbit Rotation

Pinch
→ Zoom

Reset
→ 초기 위치

Freeze
→ 현재 Point Cloud 고정
```

Freeze는 디버깅에 중요하다.

실시간으로 계속 흔들리는 데이터에서 특정 Frame을 정지시켜 3D 구조를 자세히 볼 수 있어야 한다.

---

# 18. Point Cloud 표시 모드

설정 가능:

```text
Raw Point Cloud
Filtered Point Cloud
Placement ROI
Surface Points
Obstacle Points
```

Debug Overlay:

```text
Surface Normal
Plane
Placement Center
Object Footprint
Bounding Box
```

---

# 19. Placement 테스트 모드

Point Cloud Test 화면에서 화면 특정 위치를 터치하면 해당 지점 기준으로 배치 평가를 수행한다.

표시:

```text
Placement Result
───────────────
Valid      : YES
Confidence : 0.84
Surface    : FLOOR

Depth      : 1.42 m
Slope      : 3.1°
Points     : 82
```

실패:

```text
Valid  : NO

Reason:
OBSTACLE_DETECTED
```

---

# 20. 테스트 객체 크기

Point Cloud Test 화면에서 배치할 가상 객체 크기를 선택할 수 있게 한다.

Preset:

```text
Small Object
0.2 × 0.2 × 0.2m

Chair
0.6 × 0.6 × 1.0m

Trash Can
0.4 × 0.4 × 0.7m

Custom
```

Custom:

```text
Width
Depth
Height
```

직접 입력 가능.

---

# 21. Settings 화면

Settings는 알고리즘 튜닝을 위한 화면이다.

값 변경 시 가능하면 즉시 Engine에 반영한다.

---

# 22. Depth Settings

```text
Minimum Depth
Maximum Depth

Depth Confidence Threshold

Enable Invalid Depth Filter

Enable Depth Jump Filter

Enable Temporal Smoothing

Temporal Smoothing Strength
```

---

# 23. Point Cloud Settings

```text
Global Stride
ROI Stride

Max Point Count

Point Size

Point Update FPS Limit

Enable RGB Color

Enable Raw Depth View
```

---

# 24. Placement Settings

```text
ROI Size

Minimum Valid Point Count

Maximum Surface Slope

Plane Distance Threshold

Obstacle Height Threshold

Minimum Surface Confidence

Enable Plane Fitting

Enable RANSAC
```

---

# 25. Rendering Settings

```text
Point Size

Camera Near Plane

Camera Far Plane

Show Normal

Show Surface Plane

Show Placement Footprint

Show Obstacle Points

Show FPS

Show Point Count
```

---

# 26. Sensitivity 설정

사용자가 이해하기 쉬운 상위 Sensitivity Preset을 제공한다.

```text
Low
Normal
High
Custom
```

예:

## Low

안정성을 우선한다.

```text
높은 confidence 요구
많은 valid point 요구
노이즈에 보수적
```

## Normal

기본값.

## High

낮은 품질의 Depth도 적극적으로 사용한다.

```text
작은 영역에서도 Placement 허용
낮은 confidence point 일부 허용
```

Custom 선택 시 세부 값을 직접 조절한다.

---

# 27. Point 수 설정

Settings에서 다음 설정을 제공한다.

```text
Target Point Count
Max Point Count
Sampling Stride
```

Preset:

```text
Low
≈ 2,000 ~ 5,000

Medium
≈ 5,000 ~ 10,000

High
≈ 10,000 ~ 20,000

Ultra
Device dependent
```

Ultra는 Debug 전용으로 한다.

---

# 28. 설정 저장

테스트 앱의 Settings는 로컬에 저장한다.

예:

```text
DataStore
```

사용.

서버 저장 금지.

앱 재실행 시 마지막 설정값 복원.

---

# 29. 설정 Reset

다음 기능 필수:

```text
Reset to Default
```

설정 조정 중 잘못된 값으로 인해 Point Cloud가 보이지 않을 수 있으므로 기본값 복귀 기능이 필요하다.

---

# 30. Config Import / Export

개발 편의를 위해 선택적으로 설정값 Export/Import를 지원한다.

예:

```json
{
  "globalStride": 4,
  "roiStride": 1,
  "maxPointCount": 10000,
  "confidenceThreshold": 0.5,
  "maxSurfaceSlope": 15.0
}
```

파일은 로컬에서만 처리한다.

이 기능은 필수는 아니지만 기기별 튜닝값 공유에 유용하다.

---

# 31. 실시간 Config 반영

가능한 설정은 앱 재시작 없이 즉시 반영한다.

예:

```text
Point Size
Stride
Confidence
Depth Range
ROI Size
Slope Threshold
```

설정 변경:

```text
Settings
↓
PlacementConfig 변경
↓
engine.updateConfig()
↓
다음 Depth Frame부터 적용
```

---

# 32. 진단 정보

테스트 앱에서는 아래 정보까지 확인 가능하게 한다.

```text
Device Model
Android Version

ARCore Availability
Depth Support

Depth Resolution
Depth Format

Camera Intrinsics

fx
fy
cx
cy

Depth Frame Timestamp
Camera Frame Timestamp
```

Depth/Camera timestamp 차이도 가능하면 표시한다.

---

# 33. Performance Metrics

필수 측정값:

```text
Depth FPS
Render FPS
Point Cloud Update FPS

Depth Acquisition Time
Point Generation Time
Filtering Time
Placement Evaluation Time

Point Count
Valid Point Ratio
Invalid Point Ratio
```

---

# 34. Point Cloud Performance

프레임별 로그를 무한정 남기지 않는다.

최근 N초 기준 rolling average를 사용한다.

예:

```text
Average 5 sec

Depth FPS          24.2
PC Generation      4.7 ms
Placement          8.1 ms
Render FPS         57.9
```

---

# 35. 테스트 앱에서 서버 사용 금지

테스트 앱도 반드시 Serverless다.

다음 조건에서 완전히 동작해야 한다.

```text
Wi-Fi OFF
Mobile Data OFF
```

필수 기능:

```text
Depth
Point Cloud
Settings
Placement Test
Performance Metrics
```

모두 정상 동작.

---

# 36. 모듈 Packaging

초기 Repository에서는 Gradle Module 형태로 제공한다.

예:

```text
implementation(project(":depth-placement-core"))
implementation(project(":depth-placement-arcore"))
```

향후 필요 시 AAR 배포 가능하도록 한다.

예:

```text
depth-placement-core.aar
depth-placement-arcore.aar
```

즉 설계 시부터 특정 앱에 종속된 resource나 Activity 사용을 피한다.

---

# 37. Main App Import 목표

실제 메인 AR 앱에서 사용할 때 이상적인 형태:

```kotlin
implementation(project(":depth-placement-core"))
implementation(project(":depth-placement-arcore"))
```

그리고:

```kotlin
val placementEngine =
    DepthPlacementEngineFactory.create(
        context,
        config
    )
```

이후 기존 AR Frame Update에서:

```kotlin
placementEngine.update(frame)
```

터치 시:

```kotlin
val result =
    placementEngine.evaluatePlacement(
        x,
        y,
        objectSize
    )
```

성공하면:

```kotlin
if (result.isValid) {
    placeArObject(result.pose)
}
```

이 수준으로 단순해야 한다.

---

# 38. Host App이 알아야 하는 것

메인 앱은 아래 내부 구현을 몰라도 된다.

```text
Depth Sampling
XYZ 변환
Filtering
PCA
RANSAC
Surface Normal
Obstacle 검사
Point Cloud Buffer
```

메인 앱이 알아야 하는 것은:

```text
Frame 전달
Config 전달
Object Size 전달
PlacementResult 수신
```

뿐이다.

---

# 39. Dependency 최소화

Core Module에는 가능한 한 무거운 라이브러리를 추가하지 않는다.

원칙:

```text
필요 없는 AI Runtime 추가 금지
필요 없는 CV Library 추가 금지
필요 없는 Network Library 추가 금지
```

PCA나 plane fitting이 간단한 수학으로 가능하면 직접 구현한다.

OpenCV가 반드시 필요한 경우에만 검토한다.

---

# 40. 테스트 가능한 구조

Algorithm class는 Android UI 없이 Unit Test가 가능해야 한다.

예:

```text
PointCloudGeneratorTest
SurfaceEstimatorTest
ObstacleCheckerTest
PlacementEvaluatorTest
```

Synthetic Point Cloud를 넣어 결과를 검증한다.

---

# 41. Synthetic Test

예:

## 평평한 바닥

```text
Y = 0
```

Point들을 생성.

Expected:

```text
FLOOR
Slope ≈ 0°
```

---

## 장애물

바닥 위:

```text
height = 0.5m
```

Point group 추가.

Expected:

```text
OBSTACLE_DETECTED
```

---

## 경사면

20° Plane 생성.

Expected:

```text
SURFACE_TOO_STEEP
```

---

# 42. 개발 단계

## Phase 1

Repository 구조 분리

```text
core
arcore
debug
test-app
```

---

## Phase 2

Depth Frame Adapter

```text
ARCore
↓
DepthFrameInput
```

---

## Phase 3

실시간 Point Cloud Engine

```text
Depth
↓
XYZ
↓
Buffer
```

---

## Phase 4

Test App Point Cloud Viewer

---

## Phase 5

Settings 화면

---

## Phase 6

Surface / Placement Engine

---

## Phase 7

Obstacle / Footprint 검사

---

## Phase 8

Main AR 프로젝트 Import 검증

실제 테스트 앱이 아닌 별도의 최소 Sample Host에서 다음 코드만으로 실행되는지 확인한다.

```text
module dependency
↓
initialize
↓
frame 전달
↓
placement result
```

---

# 43. Acceptance Criteria

완료라고 판단하려면 아래를 모두 만족해야 한다.

### Module

- 독립 Gradle module
- 특정 Activity 종속 없음
- 특정 UI 종속 없음
- 테스트 앱과 분리
- Main App에서 import 가능

### Point Cloud

- 실시간 갱신
- 회전 가능한 3D Viewer
- 장애물 형상이 실제 depth 차이로 표현됨

### Placement

- surface 판단
- normal 계산
- object footprint 검사
- obstacle 검사
- PlacementResult 반환

### Settings

- sensitivity 변경 가능
- point 수 조정 가능
- stride 조정 가능
- depth 범위 조정 가능
- confidence 조정 가능
- placement threshold 조정 가능

### Serverless

- 네트워크 OFF 상태에서 핵심 기능 정상 동작

---

# 44. 최종 산출물

Codex는 구현 완료 후 다음을 보고한다.

```text
[Module]

depth-placement-core
상태:

depth-placement-arcore
상태:

depth-placement-debug
상태:

test-app
상태:
```

그리고:

```text
[Device Test]

Depth Support:
Depth Resolution:

Depth FPS:
Point Cloud FPS:
Render FPS:

Average Points:
PC Generation Time:

Placement Evaluation Time:
```

그리고:

```text
[Test App]

Main:
완료 / 미완료

Point Cloud Test:
완료 / 미완료

Settings:
완료 / 미완료
```

그리고:

```text
[Integration]

Main Project Import:
성공 / 실패

Required Code:
...

Known Issues:
...

Next:
...
```

---

# 45. 최종 정의

이 프로젝트는 단순한 테스트용 Point Cloud 앱이 아니다.

최종 산출물은 두 가지다.

## 1. Placement Geometry Module

다른 Android / AR 프로젝트에서 바로 import하여 사용할 수 있는 독립 모듈.

```text
Depth Input
↓
Point Cloud
↓
Placement Evaluation
↓
PlacementResult
```

## 2. Depth Placement Test App

모듈 자체를 검증하고 기기별 Depth 특성과 알고리즘 설정값을 조절하기 위한 독립 테스트 앱.

구성:

```text
Main

Point Cloud Test

Settings
```

Settings에서는 최소한 다음 항목을 조절할 수 있어야 한다.

```text
Sensitivity
Point Count
Sampling Stride
Depth Range
Confidence
ROI
Surface Slope
Obstacle Threshold
Point Size
Smoothing
Processing FPS
```

최종적으로 메인 AR 프로젝트에서는 테스트 앱 코드를 복사하지 않고 **모듈 dependency + import만으로 동일 Placement Engine을 사용할 수 있어야 한다.**