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
- 실제 Moment 시점만 이동하는 `Reality Rewind` Swipe와 `Moment Snap`
- Snap 햅틱, 날짜 표시, AR 콘텐츠 Fade 전환, `NOW` 빠른 복귀
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
