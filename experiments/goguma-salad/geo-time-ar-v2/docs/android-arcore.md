# Android ARCore

## 빌드

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\scripts\android-build.ps1
```

구성은 Android API 36, AGP 9.3, Gradle 9.5, ARCore SDK 1.54.0, Media3 1.10.1, Kotlin built-in 지원을 사용한다.

## 실행 흐름

1. Camera/Location 권한을 요청한다.
2. ARCore 지원과 Google Play Services for AR 설치 상태를 확인한다.
3. GPS로 nearby GeoZone을 조회한다.
4. Timeline API의 Moment를 POI 기준 `Moment Stack`으로 묶는다.
5. 매 AR Frame에서 Camera Pose의 위치와 전방 벡터를 읽는다.
6. 거리와 View Cone을 통과한 Stack에 `시간 기록 N개` Anchor Marker를 표시한다.
7. 사용자가 화면 중앙의 Marker를 터치하면 최신 Moment의 5초 무음 미리보기를 재생한다.
8. 사용자가 화면에서 승인하면 Media3 Player를 전체화면으로 전환한다.
9. 전체화면에서 좌우 Swipe로 같은 Stack의 이전·다음 Moment를 재생한다.
10. 아래 Swipe나 뒤로가기로 Player를 닫고 기존 AR 탐색 화면으로 돌아온다.

## Phone 콘텐츠 UX

AR 탐색 화면에는 날짜 Timeline이나 Slider를 계속 노출하지 않는다. Marker는 특정 날짜가 아니라 해당 장소에 시간 기록이 있다는 사실만 알려준다.

```text
Marker 터치       → 5초 무음 미리보기
화면에서 승인     → 전체화면 재생
오른쪽 Swipe      → 더 오래된 Moment
왼쪽 Swipe        → 더 최근 Moment
아래 Swipe/뒤로가기 → AR 탐색으로 복귀
```

날짜와 제목은 영상 재생을 시작하거나 다른 Moment로 이동할 때만 잠깐 표시한다. 현재 Seed Asset은 SVG이므로 Phone UX 데모에서는 Media3 공식 테스트 영상을 대체 재생한다. 실제 `video/*` Content URL이 제공되면 해당 영상을 우선 사용한다.

## Glass 확장 경계

Glass에서는 마커 응시, 끄덕임·고개 흔들기, 음성을 같은 콘텐츠 상태 흐름에 연결할 수 있다. 다만 Phone MVP에서는 Touch를 기본 입력으로 사용하며 Head Gesture를 강제하지 않는다. Glass 콘텐츠 집중 화면도 내부 6DoF 추적은 유지한다.

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
