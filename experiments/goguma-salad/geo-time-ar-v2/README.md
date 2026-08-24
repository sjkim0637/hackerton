# Geo-Time AR Platform Core 개발 안내

Geo-Time AR은 위치와 시간으로 콘텐츠 후보군을 조회하고, Android 기기의 6DoF Pose와 현재 시야로 실제 표시할 콘텐츠를 결정하는 초기 Platform Prototype이다.

```text
Candidate Retrieval: Geo + Time
Visible Selection: Candidate Set + 6DoF Spatial Context
Rendering: ARCore + Anchor
```

이 문서는 `goguma-salad`의 Workstream 전용 실행 안내이다. 저장소 전체 협업 안내는 [루트 README](../../../README.md), 공통 작업 규칙은 [루트 AGENTS](../../../AGENTS.md)를 따른다.

아래 명령은 Repository Root에서 이 실험 폴더로 이동한 뒤 실행한다.

```powershell
cd experiments\goguma-salad\geo-time-ar-v2
```

## 현재 구현

- FastAPI Core API와 OpenAPI 문서
- PostgreSQL 16 + PostGIS 3.4
- Alembic Migration과 재실행 가능한 Seed
- MinIO Bucket 및 Placeholder Asset
- GeoZone 근접 검색, Moment Timeline, Active Campaign, 통합 후보 조회
- 거리와 Camera View Cone 기반 6DoF 가시성 선택
- Kotlin Android 앱, ARCore Session, Camera 배경, Anchor Marker
- 같은 장소의 Moment를 묶어 보여주는 `시간 기록 N개` AR Marker
- Marker 터치 후 5초 무음 미리보기와 재생 확인
- 어두워진 AR 배경 위 콘텐츠 집중 감상과 좌우 Swipe 기반 이전·다음 Moment 이동
- 재생 시점의 짧은 날짜 표시와 아래 Swipe·뒤로가기 기반 AR 복귀
- Phone과 Glass 데모 Mode 전환
- Glass 데모의 5초 응시, 끄덕임·좌우 고개 확인, Head Gesture 기반 Moment 이동
- 앱이 활성화된 동안 Android 자동 화면 꺼짐 방지
- 현재 동작에 맞춰 바뀌는 Coach Mark와 설정의 안내 On/Off
- Backend Pytest와 Android JUnit Test
- Docker 및 Android Build Smoke Test

## 빠른 실행

`.env.example`을 `.env`로 복사한 뒤 필요하면 개발용 비밀번호를 변경한다. `.env` 없이도 개발용 기본값으로 실행할 수 있다.

```powershell
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File .\infra\scripts\smoke-test.ps1
```

확인 주소:

- API: `http://localhost:8000`
- OpenAPI: `http://localhost:8000/docs`
- MinIO API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001`
- Demo Asset: `http://localhost:9000/geo-time-assets/demo/placeholder.svg`

서비스 종료:

```powershell
docker compose down
```

DB와 Object Storage 데이터까지 초기화하면 개발 데이터가 삭제된다.

```powershell
docker compose down -v
```

## Backend 로컬 테스트

```powershell
cd app\backend
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[dev]"
.\.venv\Scripts\ruff.exe check app tests migrations
.\.venv\Scripts\pytest.exe
```

## Android Build

Android Studio에서 `app/android`를 열거나 다음 Script를 실행한다.

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\scripts\android-build.ps1
```

Debug APK 위치:

```text
app/android/app/build/outputs/apk/debug/app-debug.apk
```

개발용 APK는 `http://127.0.0.1:8000`을 사용한다. USB 실기기와 Emulator에서는 다음 Reverse 명령을 먼저 실행한다.

```powershell
adb reverse tcp:8000 tcp:8000
```

또는 `app/android/app/build.gradle.kts`의 `API_BASE_URL`을 개발 PC의 LAN IP로 변경한다.

## Antigravity / VS Code 작업 실행

Repository Root를 연 상태에서 `터미널 → 작업 실행`을 선택하고 다음 작업을 실행한다.

- `Geo-Time AR: 빌드 · 바인딩 · APK 설치 · 실행`: Unit Test와 APK Build 후 `adb reverse`, 덮어쓰기 설치와 앱 실행까지 수행한다.
- `Geo-Time AR: 바인딩 · 기존 APK 빠른 재설치 · 실행`: Build를 생략하고 이미 생성된 Debug APK를 바로 설치한다.
- `Geo-Time AR: Backend 바인딩만`: 연결 기기에 `adb reverse tcp:8000 tcp:8000`만 적용하고 Build·설치·실행은 건드리지 않는다.

기기가 한 대면 자동으로 선택한다. 여러 대가 연결된 경우에는 잘못된 기기 설치를 막기 위해 중단하며, PowerShell에서 `$env:ANDROID_SERIAL='<기기번호>'`를 지정한 뒤 다시 실행한다.

## Workstream 문서

- [현재 Workstream 상태](../../../docs/workstreams/geo-time-ar-v2.md)
- [개발 계획](docs/development-plan.md)
- [원본 구축 지시문](docs/original-instructions.md)
- [프로젝트 전용 개발 규칙](AGENTS.md)
- [Architecture](docs/architecture.md)
- [Data Model](docs/data-model.md)
- [API](docs/api.md)
- [Docker 개발](docs/docker-dev.md)
- [Android/ARCore](docs/android-arcore.md)
- [Platform 원칙](docs/platform-principles.md)
- [기존 설계 결정](docs/decisions.md)

## 현재 제약

- Zone-local 좌표와 ARCore Session 좌표의 정밀 정합은 MVP에서 시작 위치·방향이 맞는 것으로 가정한다.
- 현실의 같은 위치를 영구적으로 재현하려면 ARCore Geospatial API, Cloud Anchor 또는 현장 Calibration 절차가 필요하다.
- 인증, 운영 배포, Admin, 결제, CDN, 추천 AI와 iOS는 포함하지 않는다.
- 실제 ARCore Session 검증에는 ARCore 지원 Android 기기와 Camera 권한이 필요하다.
