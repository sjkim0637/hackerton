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
4. Timeline API에서 실제 Moment가 존재하는 시점을 최신순으로 조회한다.
5. 사용자가 화면을 왼쪽으로 밀면 더 오래된 Moment, 오른쪽으로 밀면 `NOW` 방향으로 이동한다.
6. Moment 시점을 통과할 때 햅틱과 날짜를 표시하고 해당 시점에 Snap한다.
7. 선택한 Moment ID의 콘텐츠만 조회 결과에서 선택하고 Fade로 전환한다.
8. 매 AR Frame에서 Camera Pose의 위치와 전방 벡터를 읽는다.
9. 거리와 View Cone을 통과한 후보에 Anchor를 만든다.
10. 카메라 배경 위에 Anchor Marker를 공간 투영해 표시한다.

## Reality Rewind

현재 UX는 날짜 범위를 고르는 Slider를 사용하지 않는다. Camera 화면 자체를 시간 탐색 면으로 사용한다.

```text
왼쪽 Swipe  → 더 오래된 Moment
오른쪽 Swipe → 현재에 가까운 Moment 또는 NOW
Moment 통과  → 햅틱 + 날짜 표시 + Snap
콘텐츠 전환  → 기존 Marker Fade-out 후 선택 Marker Fade-in
```

빈 날짜는 탐색 대상에서 제외한다. Timeline은 `NOW`와 실제 Moment 시점만 포함한다. `NOW`에서는 현재 활성 Campaign만 표시하고, 과거 시점에서는 선택한 Moment ID의 콘텐츠만 표시한다.

> Feed를 넘기는 것이 아니라 같은 현실 공간의 시간을 되감는다.

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
