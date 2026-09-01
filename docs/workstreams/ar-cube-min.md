# Workstream

## Topic

ARCore 최소 기능 검증 앱 (AR Cubes)

## Owner

shinym87 + Claude

## Git Branch

`agent/shinym87/ar2`

## Project Path

`experiments/shinym87/ar2/`

## Status

IN_PROGRESS

## Goal

Android + Kotlin + ARCore 조합에서 다음 기본 동작을 실기기로 검증한다.

1. 실시간 카메라 화면 표시
2. ARCore 기본 평면 인식과 격자(그리드) 오버레이 표시
3. 화면 탭으로 평면 위에 정육면체 배치

기본 3동작 검증 후, "제안된 가구/기기 배치" 시뮬레이션에 필요한 최소 편집 기능을
추가한다.

4. 큐브 선택(길게 누르기/탭) 후 드래그로 평면 위에서 이동, 떼면 고정
5. 선택한 큐브의 크기 조절 (하단 ＋/－ 패널, 최소/최대 배율 제한)
6. 큐브 생성 시 이름 + 실물 크기(cm) 입력 팝업, 입력값대로 큐브 크기 설정,
   큐브 위에 이름표(카메라를 향하는 빌보드) 상시 표시
7. 큐브를 반투명으로 렌더링 (아직 실재하지 않는 "제안" 느낌)
8. 수직 평면(벽) 인식 및 벽면 배치: 벽에서는 큐브를 세워 뒷면을 벽에 붙인다
9. 배경 촬영: 현재 카메라 화면(큐브 제외)을 저장 → 반투명 오버레이로 겹쳐
   같은 각도에서 "가구가 없어진 것처럼" 보이게 함 (불투명도 슬라이더 포함)

## Background

`geo-time-ar-v2` Workstream 이 위치·시간 기반의 복합 AR 흐름을 다루는 것과 달리, 이
Workstream 은 ARCore 자체가 개발 환경과 실기기에서 정상 동작하는지 확인하는 최소
스캐폴딩이다. 이후 다른 AR 작업의 출발점으로 재사용하거나, 라이브러리 선택
(SceneView vs 순수 OpenGL) 비교 근거로 사용할 수 있다.

## Current Direction

고수준 라이브러리 `io.github.sceneview:arsceneview:2.3.0` 을 사용한다. 이 라이브러리가
카메라 배경 렌더링, 평면 인식 격자, Filament 기반 씬 그래프, 카메라 권한 및 ARCore
설치 안내를 모두 처리하므로 검증 코드가 `MainActivity` 한 파일로 끝난다. 순수
ARCore + OpenGL(`hello_ar_kotlin`) 방식은 보일러플레이트가 많아 최소 검증 목적에는
채택하지 않았다. 이 선택은 이 Workstream 내부 가설이며 프로젝트 공통 표준이 아니다.

## Scope

- Android Studio 로 바로 열리는 Gradle 프로젝트 구성 (wrapper 포함)
- `ARSceneView` 기반 카메라 미리보기
- `planeRenderer` (RENDER_ALL) 로 평면 격자 표시
- `hitTestAR` + `AnchorNode` + `CubeNode` 로 탭 배치
- 큐브 편집: 선택 하이라이트(material `setColor`/`setRoughness`), 드래그 이동
  (`PoseNode.pose` 갱신 후 `onMoveEnd` 에서 `Session.createAnchor` 로 재고정),
  크기 조절(`Node.scale` 배율, 0.3~3.0), 삭제
- 큐브 정보 팝업(`AlertDialog` + `dialog_cube_info.xml`): 이름, 가로/세로/높이 cm
- 이름표: `Canvas` 로 텍스트 비트맵 생성 → `ImageNode`, 매 프레임 카메라
  `worldQuaternion` 복사로 빌보드 처리
- 반투명: `MaterialLoader.createColorInstance` 에 알파 < 1 (`transparent_colored` 머티리얼)
- 조명: `config.lightEstimationMode = ENVIRONMENTAL_HDR` (없으면 큐브가 새까맣게 보임)
- 수직면 배치: `HitResult.trackable` 이 `Plane.Type.VERTICAL` 이면 `cubeNode` 를
  X축 -90° 회전 + 깊이 절반만큼 벽 바깥으로 오프셋 (`applyPlacement()`)
- 배경 오버레이: `PixelCopy.request(window, ...)` 로 현재 창 픽셀을 캡처(캡처 직전
  큐브·오버레이를 잠깐 숨김), `filesDir/empty_background.png` 로 저장, 전체화면
  `ImageView` 에 알파로 겹침. 재실행 시 자동 로드
- 디버그 APK 빌드 및 `adb install` 로 실기기 설치 절차 문서화

## Out of Scope

- 조명 추정, 그림자, 큐브 외 3D 모델(glb) 로딩
- 앵커 지속성(Cloud Anchor), 좌표 정합, 위치/시간 연동
- 릴리스 서명, Play 스토어 배포, CI
- iOS

## Decisions

- AR 라이브러리는 SceneView(arsceneview) 2.3.0 을 사용한다. 근거는 `docs/decisions/20260901-arcore-minimal-uses-sceneview.md`.
- "AR Required" 앱으로 구성한다. ARCore 미지원 기기는 대상에서 제외한다.
- `applicationId` 는 `com.example.arcubes` 로 둔다. 정식 패키지명은 통합 시 결정한다.

## Dependencies

- ARCore 지원 Android 실기기, USB 디버깅, 카메라 권한
- 기기의 "Google Play 서비스 (AR)" (미설치 시 첫 실행에서 설치 안내)
- JDK 17 (Android Studio 의 Gradle JDK 설정). 시스템 기본 JDK 가 8 또는 25 이면 별도 지정 필요.
- Android SDK Platform 35, Build-Tools 35 (Android Studio 가 자동 설치)

## 빌드 및 설치 절차

### A. Android Studio 로 빌드 (권장)

1. Android Studio 에서 `experiments/shinym87/ar2/` 폴더를 **Open** 한다.
2. 첫 Gradle Sync 시 SDK Platform 35 / Build-Tools 설치를 요청하면 수락한다.
3. Gradle JDK 를 17 로 맞춘다.
   `File > Settings > Build, Execution, Deployment > Build Tools > Gradle`
   → `Gradle JDK` 를 17 로 선택하거나 `Download JDK...` 로 Temurin 17 을 받는다.
   (이 PC 의 Android Studio 번들 JDK 는 25 라 그대로 쓰면 Gradle 이 실패한다.)
4. 폰을 USB 로 연결한다. 폰에서 `개발자 옵션 > USB 디버깅` 을 켜고, 연결 시
   "USB 디버깅 허용" 팝업을 허용한다.
5. 상단 기기 선택 드롭다운에서 폰을 고르고 ▶ **Run 'app'** 을 누른다.
   빌드 → 설치 → 실행이 한 번에 된다.

### B. 명령줄로 빌드 후 adb 설치

PowerShell 기준 (`./gradlew` 대신 `.\gradlew.bat`):

```powershell
cd experiments\shinym87\ar2

# JDK 17 이 PATH 에 없으면 이 세션에서만 지정
$env:JAVA_HOME = "C:\Path\To\jdk-17"

# 1) 디버그 APK 빌드
.\gradlew.bat assembleDebug
#  결과물: app\build\outputs\apk\debug\app-debug.apk

# 2) 폰 연결 확인 (처음이면 폰에서 USB 디버깅 허용 팝업 수락)
adb devices
#  List of devices attached
#  XXXXXXXX   device

# 3) 설치 (-r: 이미 있으면 덮어쓰기)
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 4) 실행
adb shell am start -n com.example.arcubes/.MainActivity
```

### C. 첫 실행 동작

1. 카메라 권한 팝업 → **허용**.
2. "Google Play 서비스 (AR)" 미설치 기기면 Play 스토어 설치 화면으로 이동 →
   설치 후 앱 재실행.
3. 바닥이나 책상을 천천히 비추면 몇 초 뒤 평면 위에 점 격자가 나타난다.
4. 격자 위를 탭하면 파란 정육면체가 생성된다. 계속 탭하면 계속 추가된다.

### D. 문제 해결

| 증상 | 원인 / 조치 |
|---|---|
| `Unsupported class file major version 69` | Gradle 를 JDK 25(Android Studio 번들)로 실행 중. Gradle JDK 를 17 로 변경. |
| `Your project path contains non-ASCII characters` | 경로의 한글(`신유민`). `gradle.properties` 에 `android.overridePathCheck=true` 로 검사만 끈 상태. |
| `compileDebugKotlin` 에서 `source file or directory not found ...\uC2E0uC720uBBFC\...` | 한글 경로 + Windows 기본 인코딩(CP949) 문제. `gradle.properties` 에 이미 `kotlin.compiler.execution.strategy=in-process`, `-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8`, `kotlin.daemon.jvmargs` 를 넣어 해결함. 그래도 안 되면 `subst X: "D:\신유민\GitLab\hackerton"` 로 ASCII 드라이브를 만든 뒤 `X:\experiments\shinym87\ar2` 를 열어 빌드. |
| `adb: no devices/emulators found` | USB 디버깅 미설정, 케이블 불량, 또는 폰의 허용 팝업 미수락. |
| 카메라는 나오는데 평면이 절대 안 잡히고 `tracking` 이 TRACKING↔PAUSED 로 계속 깜빡임 | **가장 흔한 원인.** `AndroidManifest.xml` 에 `android.permission.HIGH_SAMPLING_RATE_SENSORS` 가 없으면 Android 12+ 에서 ARCore 가 가속도계/자이로를 등록하지 못한다(logcat: `Failed to register sensor to queue 0 with error -1`). VIO 트래킹이 수렴을 못 해 평면이 하나도 안 생긴다. 이 권한을 추가하면 해결. Galaxy S25 FE(Android 16) 실기기에서 확인. |
| 설치는 되는데 격자가 안 보임 | SceneView `planeRenderer` 기본 모드가 `RENDER_CENTER` 라 화면 정중앙이 겨눈 수평면 한 장에만 격자를 그린다. `MainActivity` 에서 `planeRendererMode = RENDER_ALL` 로 바꿔 모든 평면에 격자를 그리게 했다. |
| 그래도 평면이 안 잡힘 | 조명이 밝고 무늬가 있는 바닥에서 기기를 좌우로 천천히 움직인다. `adb logcat -s ARCubes:D` 로 `tracking=` / `failureReason=` / `planes=` 값을 확인한다. |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | ARCore/Filament 는 arm64 네이티브 라이브러리를 포함한다. arm64 실기기에서 테스트한다(x86 에뮬레이터 비권장). |

## Integration Candidate

TBD — 최소 스캐폴딩이므로 통합보다는 참조/재사용 대상.

## Known Issues

- **기본 3동작 실기기 검증 완료 (2026-09-01, Galaxy S25 FE / SM-S947N / Android 16).**
  카메라 표시, 평면 인식(`planes=2/2`), 화면 탭 큐브 생성(13개) 모두 동작 확인.
  단, `HIGH_SAMPLING_RATE_SENSORS` 권한을 추가하기 전에는 평면이 전혀 안 잡혔다
  (ARCore 가 IMU 센서 등록 실패). 위 "문제 해결" 표 첫 줄 참고.
- **편집 기능(4~7) 실기기 동작 확인 (2026-09-01).** 이름/크기 팝업, 큐브 생성,
  드래그 이동(여러 개 독립), ＋/－ 크기 조절, 이름표 표시 모두 정상. 로그·스크린샷 확인.
  처음엔 큐브가 새까맣게 보여 `ENVIRONMENTAL_HDR` 조명 추정 추가 + 색/알파 상향,
  이름표가 과도하게 커서 폭 18cm→10cm 로 축소.
- **수직면 배치(8) + 배경 오버레이(9) 는 빌드 성공까지만 확인, 실기기 재검증 대기.**
  테스트 직전 폰 USB 연결이 끊겨 재설치 못 함.
- 벽면 큐브 회전 방향(X -90°)은 추정값. 실기기에서 위아래가 뒤집히거나 벽에
  파묻히면 `applyPlacement()` 의 부호를 조정한다.
- 이름표 빌보드는 카메라 `worldQuaternion` 을 그대로 복사한다. 이미지 면이
  뒤집혀 안 보이면 `onSessionUpdated` 의 라벨 회전에 Y축 180° 보정을 넣는다.
- `PixelCopy` 로 캡처한 배경이 검게 나오면(일부 기기의 SurfaceView 보안 플래그)
  ARCore `frame.acquireCameraImage()` (YUV) 방식으로 교체 필요.
- 개발 PC 의 Android Studio 번들 JDK 가 25 라, Gradle JDK 를 17(또는 21)로 수동
  지정해야 한다. 25 로는 Gradle 8.11.1 이 실행되지 않는다.
- 한글 경로 때문에 `gradle.properties` 에 인코딩/컴파일 전략 옵션을 추가했다.
  다른 PC 에서 문제가 재현되면 위 "문제 해결" 표의 `subst` 방법을 쓴다.
- logcat 에 `Callback list for SENSOR_TYPE_GYROSCOPE_UNCALIBRATED ... not found`,
  간헐적 `VIO_OUTPUT_NOT_TRACKING` 이 남는다. 트래킹·평면·큐브가 정상 동작하므로
  무해한 Samsung/ARCore 내부 로그로 판단.
- `com.example.arcubes` applicationId 는 임시값이다.

## Next

1. (완료) 실기기 3기능 검증.
2. (완료) 화면 상단에 추적 상태/평면 개수 안내 문구 노출.
3. `geo-time-ar-v2` 와 겹치는 ARCore 초기화 코드가 있으면 공통화 후보로 정리한다.
   특히 `HIGH_SAMPLING_RATE_SENSORS` 권한 누락은 그쪽에서도 확인이 필요하다.
4. 진단 로그(`onSessionUpdated` 내 Log.d)는 검증용이므로 통합 전 정리 여부 결정.

## Relevant Commits

- (작성 예정) `feat(ar2): ARCore 최소 검증 앱 스캐폴딩 추가`

## Updated

2026-09-01
