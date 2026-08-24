# Android ARCore

## 빌드

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\scripts\android-build.ps1
```

구성은 Android API 36, AGP 9.3, Gradle 9.5, ARCore SDK 1.54.0, Kotlin built-in 지원을 사용한다.

## 실행 흐름

1. Camera/Location 권한을 요청한다.
2. ARCore 지원과 Google Play Services for AR 설치 상태를 확인한다.
3. GPS로 nearby GeoZone을 조회한다.
4. Slider의 선택 시간으로 콘텐츠 후보를 조회한다.
5. 매 AR Frame에서 Camera Pose의 위치와 전방 벡터를 읽는다.
6. 거리와 View Cone을 통과한 후보에 Anchor를 만든다.
7. 카메라 배경 위에 Anchor Marker를 공간 투영해 표시한다.

## 기기 연결

```powershell
adb devices -l
adb reverse tcp:8000 tcp:8000
adb install -r .\app\android\app\build\outputs\apk\debug\app-debug.apk
```

개발용 APK는 `127.0.0.1:8000`을 사용하므로 USB 기기나 Emulator에서 `adb reverse`를 먼저 실행한다. 무선 설치에서는 PC의 LAN IP로 `API_BASE_URL`을 변경해야 한다.

## 알려진 공간 정합 제약

현재 `SpatialPlacement`와 ARCore Session 좌표의 변환은 Identity Demo다. 실제 장소에서 지속 가능한 배치를 위해 다음 중 하나가 필요하다.

- ARCore Geospatial Anchor
- Cloud Anchor
- QR/Visual Marker 기반 현장 Calibration
- 측량된 POI 기준점과 초기 Heading Calibration

이 변환이 추가돼도 Backend 후보 검색과 6DoF 가시성 선택의 경계는 유지된다.
