# 현재 개발 버전 설치와 운영 Server 테스트

이 문서는 Geo-Time AR의 현재 Android 개발 버전을 로컬 Backend, Docker, PostgreSQL, MinIO 또는 USB Reverse 없이 설치하고 시험하는 절차다.

## 현재 배포 기준

| 항목 | 값 |
|---|---|
| Android App Version | `0.1.2` (`versionCode 3`) |
| Backend Version | `0.1.0` |
| Build 기준 | 이 문서를 포함한 Branch 최신 HEAD |
| Production API | `https://geo-time-ar-v2.vercel.app` |
| 운영 Database | Supabase PostgreSQL/PostGIS |
| 확인일 | 2026-08-26 |

Vercel URL과 Supabase Project URL은 Client가 접속하는 공개 Endpoint이므로 APK와 설정 화면에 표시돼도 된다. DB 비밀번호, Supabase Service Role Key와 OAuth Client Secret은 APK에 포함하지 않는다.

## 준비물

- Android 8.0(API 26) 이상이며 ARCore를 지원하는 Android 기기
- Internet 연결
- 기기에 설치된 최신 `Google Play Services for AR`
- 전달받은 현재 개발용 `app-debug.apk`

APK를 만드는 담당자가 확인할 파일 위치는 다음과 같다.

```text
app/android/app/build/outputs/apk/debug/app-debug.apk
```

현재 APK는 Debug Build이며 Git에 포함하지 않는다. Tester는 APK 파일만 전달받으면 되고 Source Code, Android Studio와 Backend 개발 환경은 필요하지 않다.

## 기기에서 바로 설치

1. `app-debug.apk`를 기기에 전달한다.
2. 기기에서 APK를 열고 해당 파일 앱 또는 Browser의 `알 수 없는 앱 설치` 권한을 허용한다.
3. 설치를 완료하고 `Geo-Time AR`을 실행한다.
4. Camera와 위치 권한을 허용한다. 위치는 가능하면 `정확한 위치`를 사용한다.

같은 Debug 서명으로 설치한 이전 버전은 보통 덮어쓰기 된다. 서명이 다른 APK 때문에 설치가 거부되면 기존 앱을 삭제한 뒤 다시 설치할 수 있지만, 이 경우 저장된 Server Profile과 앱 설정도 삭제된다.

## ADB로 설치하는 경우

ADB가 이미 있는 Tester는 다음 명령으로 덮어쓰기 설치할 수 있다. 운영 Profile 테스트에는 `adb reverse`가 필요하지 않다.

```powershell
adb devices
adb install -r .\app-debug.apk
```

## 운영 Server 연결

1. 앱 시작 화면에서 `설정 · 연결 상태`를 연다.
2. `Server 설정`에서 `운영` Profile을 선택한다.
3. 기본 API 주소가 `https://geo-time-ar-v2.vercel.app`인지 확인한다.
4. `연결 테스트`를 누른다.
5. API 항목에 `연결됨`, `production`, `Backend 0.1.0`이 표시되는지 확인한다.
6. `저장하고 적용`을 누른다.

이전 개발 버전에서 `api.example.com` 또는 `media.example.com`을 저장한 기기는 현재 운영 기본 Endpoint로 자동 전환된다. 사용자가 직접 입력한 다른 운영 주소는 유지된다.

`Demo Preview`는 기본으로 활성화되어 실제 위치와 관계없이 을지로 타워 107의 운영 Seed를 조회한다. 실제 GPS 기준으로 시험하려면 설정에서 `Demo Preview`를 끈다.

`0.1.1`부터 운영 API에는 타워 107 주변의 실제 좌표를 가진 테스트 POI Marker가 여러 방향으로 등록된다. Marker가 시야 밖이면 상태 줄에 좌우 회전 안내가 표시된다.

`0.1.2`부터 Glass Demo에서 Preview 또는 전체 재생을 종료하고 AR로 복귀해도 같은 Marker를 다시 5초간 응시할 수 있다.

## 현재 확인할 수 있는 항목

- Vercel Production API 연결과 Backend Version
- Supabase PostGIS 기반 주변 GeoZone, POI와 Timeline 조회
- 기준좌표 두 점 조회
- Phone·Glass Viewer Mode
- Camera, ARCore Tracking, Compass Tape와 Pitch·Roll HUD
- POI Marker, Moment Stack, Preview와 콘텐츠 집중 화면

## 아직 제한되는 항목

- Supabase Storage Bucket은 비공개다. 실제 Media 재생을 위한 서명 URL 발급 API는 아직 구현 전이다.
- 운영 Seed의 Media는 Placeholder이므로 현재 Viewer는 개발용 대체 영상을 사용할 수 있다.
- Creator 촬영, Upload, 변환과 발행 Flow는 P1 작업 범위다.
- 현장 POI의 최종 수직 위치는 별도 Calibration이 필요하다.

## 문제 해결

- `DNS 실패`: Internet 연결과 운영 API 주소를 확인한다.
- `Timeout`: Mobile Network와 Wi-Fi를 바꿔 다시 시험한다.
- Camera가 열리지 않음: 앱의 Camera 권한과 `Google Play Services for AR` 설치 상태를 확인한다.
- 위치를 가져오지 못함: 정확한 위치 권한과 기기 위치 서비스를 켠다. 단순 UI 시험은 `Demo Preview`를 켠 상태로 진행할 수 있다.
- 설치 충돌: 기존 앱과 새 APK의 서명이 다를 수 있다. 기존 앱 삭제 전 저장된 설정이 사라지는 점을 확인한다.
