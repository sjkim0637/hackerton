# AR Cubes — ARCore 최소 검증 앱

Android + Kotlin + ARCore(SceneView) 로 만든 **기능 검증용 최소 버전**이다.

## 구현된 기능

1. **실시간 카메라 화면** — `ARSceneView` 가 카메라 미리보기를 전체 화면으로 렌더링한다.
2. **평면 인식 + 격자 표시** — ARCore 기본 평면 인식을 사용하고, 인식된 바닥/책상 위에 격자(그리드) 오버레이를 그린다.
3. **탭하면 큐브 생성** — 화면을 탭하면 그 지점의 평면 위에 한 변 8cm 짜리 정육면체가 하나 생성된다.

## 기술 스택

| 항목 | 버전 |
|---|---|
| Android Gradle Plugin | 8.9.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 24 |
| AR 라이브러리 | `io.github.sceneview:arsceneview:2.3.0` (ARCore 1.48.0 + Filament 1.56.0 포함) |
| 필요 JDK | 17 (Android Studio 의 Gradle JDK 설정) |

## 프로젝트 구조

```
ar2/
├─ app/
│  ├─ build.gradle
│  └─ src/main/
│     ├─ AndroidManifest.xml        # CAMERA 권한, AR Required 메타데이터
│     ├─ java/com/example/arcubes/MainActivity.kt
│     └─ res/
│        ├─ layout/activity_main.xml   # ARSceneView + 안내 문구 TextView
│        ├─ values/strings.xml
│        ├─ values/themes.xml
│        └─ drawable/ic_launcher.xml
├─ build.gradle / settings.gradle / gradle.properties
└─ gradle/wrapper/                  # Gradle 8.11.1 wrapper 포함
```

## 빌드 및 실기기 설치 방법

`docs/workstreams/ar-cube-min.md` 의 "빌드 및 설치 절차" 항목을 참고한다.
요약하면 다음과 같다.

```bash
# 1. 프로젝트 폴더에서
cd experiments/shinym87/ar2

# 2. 디버그 APK 빌드 (Windows PowerShell 기준: .\gradlew.bat)
./gradlew assembleDebug

# 3. 폰을 USB 로 연결하고 (개발자 옵션 + USB 디버깅 ON)
adb devices

# 4. 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 5. 실행
adb shell am start -n com.example.arcubes/.MainActivity
```

첫 실행 시 카메라 권한 허용, 그리고 기기에 "Google Play 서비스 (AR)" 가 없으면
Play 스토어 설치 안내가 뜬다. 설치 후 앱을 다시 실행한다.

## 알려진 제약

- **AR Required 앱**이다. ARCore 미지원 기기에서는 설치/실행되지 않는다.
  [지원 기기 목록](https://developers.google.com/ar/devices) 확인.
- 저장소 경로에 한글(`신유민`)이 포함되어 `gradle.properties` 에
  `android.overridePathCheck=true` 를 넣었다. 빌드가 경로 문제로 실패하면
  ASCII 경로로 프로젝트를 복사해서 빌드한다.
- 큐브에는 조명 추정/그림자를 적용하지 않았다. 단색 재질만 사용한다.
