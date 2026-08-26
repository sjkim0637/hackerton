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
8. 사용자가 화면에서 승인하면 Media3 Player를 콘텐츠 집중 화면으로 전환한다.
9. 콘텐츠 집중 화면에서 좌우 Swipe로 같은 Stack의 이전·다음 Moment를 재생한다.
10. 아래 Swipe나 뒤로가기로 Player를 닫고 기존 AR 탐색 화면으로 돌아온다.

## Phone 콘텐츠 UX

AR 탐색 화면에는 날짜 Timeline이나 Slider를 계속 노출하지 않는다. Marker는 특정 날짜가 아니라 해당 장소에 시간 기록이 있다는 사실만 알려준다.

```text
Marker 터치       → 5초 무음 미리보기
화면에서 승인     → 콘텐츠 집중 재생
오른쪽 Swipe      → 더 오래된 Moment
왼쪽 Swipe        → 더 최근 Moment
아래 Swipe/뒤로가기 → AR 탐색으로 복귀
```

날짜와 제목은 영상 재생을 시작하거나 다른 Moment로 이동할 때만 잠깐 표시한다. Phone의 콘텐츠 집중 화면은 완전한 검은 배경을 사용하지 않고 어두워진 AR Camera 위에 영상 원본 비율을 유지한다. 현재 Seed Asset은 SVG이므로 Phone UX 데모에서는 Media3 공식 테스트 영상을 대체 재생한다. 실제 `video/*` Content URL이 제공되면 해당 영상을 우선 사용한다.

## Glass 데모 모드

하단의 `Phone → Glass 데모` 버튼으로 입력 모드를 바꿀 수 있다. 실제 Glass 대신 폰을 머리처럼 움직여 다음 흐름을 확인한다.

```text
마커 중앙 응시 5초
  → 무음 미리보기 5초
  → 상하 끄덕임: 전체 재생
  → 좌우 흔들기: 취소
  → 재생 중 빠른 오른쪽 왕복: 과거 Moment
  → 재생 중 빠른 왼쪽 왕복: 최근 Moment
  → 재생 중 기준 자세에서 좌우 Roll 15도 기울임: AR 탐색 복귀
  → 영상 종료: AR 탐색 자동 복귀
```

제스처는 움직인 뒤 기준 자세로 돌아와야 확정된다. 느리게 주변을 둘러보는 동작은 명령에서 제외한다. 재생 승인 시 공간에 고정된 6DoF Preview가 시야 정면을 따라오는 3DoF형 Screen으로 바뀌지만 내부 ARCore 6DoF Tracking은 계속 유지한다. 고개를 멀리 돌리는 행동은 종료로 사용하지 않는다. 음성 명령은 이번 데모 범위에 포함하지 않는다.

## 화면 항상 켜기

AR 앱이 화면에 활성화된 동안에는 Android의 자동 화면 꺼짐을 막는다. 사용자가 앱을 백그라운드로 보내거나 직접 화면을 끄는 동작까지 무시하지는 않는다.

## Viewer HUD 계층

Compass Tape는 `DisplayCutout.safeInsetTop`을 반영해 Pinhole 아래에 배치하고 장소·Tracking·정렬 진단 안내는 그 아래에 둔다. 안내 Panel은 별도 홈 버튼 없이 장소와 Tracking을 한 줄에 표시하는 3줄 Compact 구조다. 좌하단 Pitch·Roll Artificial Horizon은 시야를 가리지 않도록 축소한다. HUD는 AR Camera보다 위, 상태 안내와 재생 확인 Popup보다 아래에 그려서 사용자의 선택이 필요한 Popup을 항상 최상단에 표시한다.

## 앱 내 안내 설정

설명서 Popup을 먼저 보여주지 않고 현재 상태에 맞는 Coach Mark를 화면에 표시한다. 마커 선택, 응시 유지 시간, 미리보기, 재생 승인, 기록 이동과 AR 복귀 순서에 맞춰 안내 문구가 바뀐다. 하단 `설정`에서 `동작별 조작 안내 표시`를 끄면 Coach Mark만 숨기며, 조회 상태·진행 상황·오류와 실제 선택이 필요한 재생 확인창은 계속 표시한다.

마지막으로 연 Phone Viewer·Glass Demo Mode를 저장한다. 설정에서 5초 Preview의 음소거와 자동 재생을 각각 바꿀 수 있으며, 자동 재생을 끄면 Player의 재생 Button을 눌렀을 때 5초 Timer가 시작된다. Camera·위치·마이크 권한 상태와 App 설정 바로가기, App Version도 같은 설정 화면에서 확인한다. Backend Version은 Server 연결 Test의 `/health` 응답에 표시한다.

## Server 연결 설정

시작 화면의 `설정 · 연결 상태`에서 다음 Profile을 선택한다.

- `Demo`: Server 없이 앱의 Local Moment Stack을 사용한다.
- `USB`: 기본 API `http://127.0.0.1:8000`, Media `http://127.0.0.1:9000`에 연결한다. 개발 PC에서 Docker 서비스와 API·Media 두 Port의 `adb reverse`가 모두 필요하다.
- `운영`: 인터넷에서 접근 가능한 API·Media 주소를 직접 지정한다. 운영 주소는 HTTPS 사용을 권장한다.

API와 Media 주소는 Profile별로 저장된다. `연결 테스트`는 API `/health`와 Media 주소를 각각 확인하고 DNS 실패, Timeout, 잘못된 주소, HTTP 응답 오류를 구분해 표시한다. Backend의 `public_url`이 `localhost`를 가리켜도 Object Path와 Query는 유지하고 선택한 Media 주소의 Origin으로 바꿔 재생한다.

## 실제 Glass와 현재 데모의 차이

현재 Glass 데모는 폰 Camera와 ARCore Pose를 사용한다. 폰의 AR 영상을 실제 Glass로 Streaming하는 구현은 아니다. 실제 Glass 제품에서는 Hardware 유형에 따라 Glass 또는 전용 Computing 장치에서 별도 Runtime을 실행하거나, Phone이 유선 Display Host가 되고 Vendor SDK를 통해 Glass Pose를 받는다.

Phone과 Glass는 Backend와 콘텐츠 상태 흐름을 공유하지만 화면과 입력은 분리한다. 실제 Target이 정해지면 ARCore Pose 입력을 OpenXR 또는 Vendor SDK 입력으로 교체한다.

## 기기 연결

```powershell
adb devices -l
adb reverse tcp:8000 tcp:8000
adb reverse tcp:9000 tcp:9000
adb install -r .\app\android\app\build\outputs\apk\debug\app-debug.apk
```

앱의 초기 Profile은 기존 개발 흐름을 유지하는 `USB`다. USB 기기나 Emulator에서 두 Port의 `adb reverse`를 먼저 실행한다. 무선 연결에서는 설정의 API와 Media 주소를 PC의 LAN IP로 함께 변경한다.

## GPS·Compass 자동 공간 정렬

Backend의 POI WGS84 좌표를 현재 Phone 기준 ENU 미터 좌표로 변환한다. Rotation Vector Sensor Heading은 GPS 위치와 시각에 해당하는 Magnetic Declination을 더해 True North로 바꾸고 최근 1초 표본을 Circular Mean으로 평균한다. GPS Snapshot, True Heading, ARCore Camera Position·Yaw가 준비되면 `GeoArAlignment`를 한 번 생성한다.

```text
POI WGS84 - Phone WGS84
→ ENU East/North/선택 Up
→ POI local_x/y/z 결합
→ True North와 ARCore Yaw 차이만큼 회전
→ ARCore Session 좌표
```

상단 진단에는 GPS Accuracy, True Heading과 추정 정확도, Yaw Offset, 현재 위치에서 가까운 사용 가능 기준좌표 두 점을 표시한다. GPS Accuracy 20m 초과 또는 Heading 정확도 15도 초과는 경고하지만 Demo 진행을 막지 않는다. 정렬 후에는 GPS·Compass 값을 계속 적용하지 않아 Marker Jump를 방지하고 ARCore 6DoF Tracking만 유지한다.

POI와 Phone에 타원체고가 모두 있을 때만 상대 Up을 적용한다. 국가 표고와 Android GNSS 타원체고를 직접 빼지 않는다. 현재 남은 검증은 실제 기준좌표 측량 결과로 Tower 107 POI 좌표·표고를 교체하고 현장에서 수평·수직 오차를 측정하는 작업이다.
