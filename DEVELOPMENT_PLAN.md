# Geo-Time AR Platform 초기 구축 개발 계획

이 문서는 `Codex 지시문 — Geo-Time AR Platform 초기 구축.md`를 실제 개발 작업 단위로 정리한 체크리스트다.

## 현재 구현 상태 (2026-08-21)

- [x] Git 저장소와 기본 프로젝트 구조 생성
- [x] Docker Desktop, Android Studio/JDK, Android SDK 35/36, ADB 설치
- [x] PostgreSQL/PostGIS, MinIO, FastAPI Compose 환경 실행
- [x] Alembic Migration과 Seed Data 생성
- [x] GeoZone, Timeline, Moment, Campaign, Content Candidate API 구현
- [x] Backend 6DoF 가시성 선택 로직과 자동 테스트 구현
- [x] Android Kotlin/ARCore Session, Camera Background, Anchor Marker 구현
- [x] Backend 15개 테스트와 Android 2개 테스트 통과
- [x] Docker 완전 초기화 후 Smoke Test 통과
- [x] Android Debug APK 빌드 성공
- [ ] ARCore 지원 실기기에서 카메라·Anchor 최종 실행 확인

## 1. 개발 목표

- [ ] 특정 서비스 하나에 종속되지 않는 Geo-Time AR Platform Core 구축
- [ ] `Geo + Time`으로 주변 콘텐츠 후보군을 조회하고, 6DoF Pose와 현재 시야로 실제 노출 콘텐츠를 결정
- [ ] ARCore의 Position, Orientation, Frustum, Anchor 관계를 콘텐츠 선택·가시성 판단과 렌더링에 사용
- [ ] 일반 사용자 콘텐츠(`Moment`)와 상업 콘텐츠(`Campaign`)를 도메인과 서비스 계층에서 분리
- [ ] 향후 Time Rewind, 건설 POI, 게임, 관광, 광고 기능을 독립적으로 확장할 수 있는 구조 확보

## 2. 기본 개발 원칙

- API 우선 방식으로 Backend와 Android 사이의 계약을 먼저 정의한다.
- 데이터베이스 변경은 모두 Alembic migration으로 관리한다.
- DB에는 미디어 파일을 저장하지 않고 MinIO의 Object Key와 URL만 저장한다.
- 개발 환경은 Docker Compose 한 번으로 재현할 수 있게 구성한다.
- 작은 기능 단위로 구현하고 각 단계마다 테스트와 문서를 같이 갱신한다.
- 실제 비밀번호와 API Key는 `.env`에만 보관하고 Git에 포함하지 않는다.
- 과도한 추상화보다 MVP에 필요한 명확한 도메인 경계를 우선한다.

## 3. 예정 기술 스택

### Backend

- Python 3.12
- FastAPI
- SQLAlchemy 2.x
- GeoAlchemy2
- Pydantic v2
- Alembic
- Pytest

### Database / Storage

- PostgreSQL + PostGIS
- MinIO

### Android

- Kotlin
- Android Native
- Jetpack Compose 기반 최소 UI
- ARCore
- Retrofit/OkHttp 또는 동등한 HTTP 클라이언트
- Fused Location Provider

### Development environment

- Docker Compose
- Backend lint/format: Ruff
- Android build: Gradle Wrapper

## 4. 목표 저장소 구조

```text
/
├─ app/
│  ├─ android/
│  └─ backend/
├─ db/
│  ├─ migrations/
│  ├─ seeds/
│  └─ schema/
├─ infra/
│  ├─ docker/
│  └─ scripts/
├─ docs/
├─ assets/
├─ demo/
├─ docker-compose.yml
├─ .env.example
├─ .gitignore
├─ README.md
└─ AGENTS.md
```

## 5. 단계별 To-do

### Phase 0 — 저장소 및 개발 규칙

- [ ] Git 저장소 초기화
- [ ] 기본 디렉터리 구조 생성
- [ ] `.gitignore` 작성
- [ ] `.env.example` 작성
- [ ] 루트 `README.md` 작성
- [ ] `AGENTS.md`에 프로젝트 개발 규칙 기록
- [ ] Backend/Android 코드 스타일과 테스트 명령 정의

완료 기준:

- 새 개발자가 저장소 구조와 실행 방식을 README에서 이해할 수 있다.
- 실제 Secret이 Git 추적 대상에 포함되지 않는다.

### Phase 1 — Docker 개발 환경

- [ ] PostgreSQL/PostGIS 서비스 구성
- [ ] MinIO 서비스 구성
- [ ] MinIO 초기 Bucket 생성 방식 구성
- [ ] FastAPI Backend 이미지 구성
- [ ] Migration 실행 서비스 또는 시작 절차 구성
- [ ] Seed 실행 절차 구성
- [ ] 서비스별 healthcheck 추가
- [ ] 영속 볼륨과 내부 네트워크 구성
- [ ] 컨테이너 시작 순서와 재시작 정책 정의

완료 기준:

```bash
docker compose down -v
docker compose up -d
```

- 위 명령으로 PostgreSQL/PostGIS, MinIO, Backend가 준비된다.
- Backend가 DB 준비 전에 잘못 시작되지 않는다.
- PostGIS extension 활성화 여부를 확인할 수 있다.

### Phase 2 — Backend 기반 구조

- [ ] FastAPI 애플리케이션 생성
- [ ] 환경 설정 모듈 작성
- [ ] SQLAlchemy 세션과 DB 연결 구성
- [ ] 공통 API 응답 및 오류 처리 정의
- [ ] `/health` 구현
- [ ] OpenAPI 문서 확인
- [ ] Ruff와 Pytest 설정
- [ ] Backend 단위 테스트 기본 구조 생성

완료 기준:

- `GET /health`가 정상 응답한다.
- 잘못된 입력과 존재하지 않는 자원에 일관된 오류 응답을 제공한다.

### Phase 3 — DB 모델과 Migration

- [ ] `User` 모델
- [ ] `GeoZone` 모델
- [ ] `POI` 모델
- [ ] `Content` 모델
- [ ] `Moment` 모델
- [ ] `TimeLayer` 모델 또는 Query 계층에서의 역할 확정
- [ ] `Campaign` 모델
- [ ] `CampaignSchedule` 모델
- [ ] `SpatialImpression` 모델
- [ ] Enum과 상태 값 정의
- [ ] Foreign Key와 삭제 정책 정의
- [ ] 공간/시간 조회용 Index 설계
- [ ] 콘텐츠의 안정적인 공간 배치를 위한 좌표 기준 정의
- [ ] 콘텐츠별 위치, 방향, 크기, 노출 거리와 가시 범위 메타데이터 정의
- [ ] 최초 Alembic migration 생성
- [ ] 빈 DB에 upgrade/downgrade 검증

초기 공간 타입 방침:

- `GeoZone.center_point`: `geography(Point, 4326)`
- `GeoZone.geometry`: 필요 시 `geometry(Polygon 또는 MultiPolygon, 4326)`
- 반경 검색은 `ST_DWithin` 사용
- 단순 반경 Zone과 Polygon Zone을 모두 확장 가능하게 유지

완료 기준:

- 모든 Schema 변경이 migration으로 재현된다.
- 주요 Foreign Key와 공간 Index가 적용된다.

### Phase 4 — Seed Data

- [ ] 테스트 사용자 2~3명 생성
- [ ] Demo GeoZone 1개 이상 생성
- [ ] POI 샘플 생성
- [ ] Content 메타데이터 샘플 생성
- [ ] Moment 5~10개 생성
- [ ] Campaign 1개 생성
- [ ] 현재 활성 상태인 CampaignSchedule 1개 생성
- [ ] Placeholder Asset URL/Object Key 구성
- [ ] Seed 중복 실행 시 처리 정책 구현

완료 기준:

- 초기화된 DB에서 API를 바로 시험할 수 있다.
- Seed를 다시 실행해도 의도하지 않은 중복 데이터가 쌓이지 않는다.

### Phase 5 — Core API

- [ ] `GET /health`
- [ ] `GET /geozones/nearby`
- [ ] `GET /geozones/{id}/timeline`
- [ ] `GET /moments/{id}`
- [ ] `POST /moments`
- [ ] `GET /geozones/{id}/campaigns/active`
- [ ] 요청/응답 Schema 작성
- [ ] Pagination 또는 `limit` 상한 적용
- [ ] UTC 저장 및 ISO 8601 응답 규칙 적용
- [ ] API 문서에 예제 추가

`nearby` 기본 입력:

- `latitude`
- `longitude`
- `radius_m` 또는 서버 기본 반경
- `limit`

`timeline` 기본 입력:

- `from`
- `to`
- `limit`

Active Campaign 조건:

- 요청 시각이 `start_at <= now < end_at`을 만족
- Schedule 상태가 active
- 같은 Zone 내에서는 priority 순으로 정렬

완료 기준:

- GeoZone 근접 검색이 PostGIS 쿼리로 동작한다.
- Moment가 `recorded_at` 기준으로 안정적으로 정렬된다.
- 현재 시간 창에 해당하는 Campaign만 반환된다.

### Phase 6 — Backend 및 DB 테스트

- [ ] Health API 테스트
- [ ] GeoZone nearby 거리 경계 테스트
- [ ] Timeline 기간 필터 테스트
- [ ] Timeline 정렬 테스트
- [ ] Moment 생성/조회 테스트
- [ ] Campaign 활성 시간 경계 테스트
- [ ] Campaign priority 정렬 테스트
- [ ] Migration 적용 테스트
- [ ] Seed 검증 테스트
- [ ] PostGIS extension 및 공간 쿼리 테스트
- [ ] Docker 재생성 테스트

완료 기준:

- 핵심 API 자동 테스트가 통과한다.
- `docker compose down -v` 이후에도 전체 환경을 재구축할 수 있다.

### Phase 7 — Android 프로젝트 Skeleton

- [ ] Gradle Wrapper를 포함한 Android 프로젝트 생성
- [ ] Kotlin 및 Compose 기본 설정
- [ ] Camera/Location/Internet 권한 구성
- [ ] API Base URL 환경별 설정
- [ ] Backend API Client 작성
- [ ] Location Provider 연결
- [ ] 현재 GeoZone 조회 흐름 구현
- [ ] Timeline 조회 흐름 구현
- [ ] Moment 선택 상태 구현
- [ ] Campaign 조회 상태 구현
- [ ] 로딩/오류/권한 거부 상태 처리

완료 기준:

- Android 프로젝트가 별도로 빌드된다.
- 위치를 얻어 Backend의 nearby API를 호출할 수 있다.
- 선택한 GeoZone의 Moment와 Campaign 데이터를 화면 상태로 유지한다.

### Phase 8 — ARCore Skeleton 및 MVP UI

- [ ] ARCore 의존성 및 Manifest 설정
- [ ] 지원 기기/미지원 기기 처리
- [ ] ARCore Session 생명주기 연결
- [ ] Plane 탐지
- [ ] 선택한 Moment용 Anchor 생성
- [ ] Placeholder 이미지 또는 3D Object 렌더링
- [ ] Camera Screen 작성
- [ ] 현재 GeoZone과 Moment 개수 표시
- [ ] Time Slider 작성
- [ ] Moment Preview 작성
- [ ] 활성 Campaign Layer 표시
- [ ] Tracking 손실과 Session 오류 처리

콘텐츠 선택 파이프라인:

1. Backend가 `Geo + Time`으로 현재 장소와 시간에 유효한 후보 콘텐츠를 반환한다.
2. 각 후보에는 현실 공간상의 배치 위치, 방향, 크기, 노출 반경 등 Spatial Metadata가 포함된다.
3. Android가 ARCore의 현재 6DoF Pose를 안정적인 Zone/Anchor 좌표계로 변환한다.
4. 현재 Camera Frustum, 거리, 방향, 필요 시 Occlusion을 평가해 화면에 들어오는 콘텐츠를 선택한다.
5. 선택된 콘텐츠를 Anchor에 연결해 렌더링하고 Pose 변화에 따라 가시성을 계속 갱신한다.

중요 원칙:

- `Geo + Time`만으로 최종 표시 콘텐츠를 확정하지 않는다.
- 최종 노출 집합은 `Geo + Time + 6DoF Spatial Context`로 결정한다.
- ARCore의 원시 Pose는 세션 로컬 좌표이므로 그대로 DB 검색 키로 저장하지 않는다.
- 저장된 콘텐츠 좌표와 현재 Pose를 같은 기준 좌표계로 변환한 뒤 거리와 시야를 계산한다.
- 방향·접근 조건은 제품 요구에 따라 가시성, 선택 또는 Unlock 규칙으로 명시적으로 분리한다.

완료 기준:

- 사용자가 시간대 또는 Moment를 선택할 수 있다.
- 선택된 콘텐츠를 AR Anchor에 붙인 Placeholder로 표시할 수 있다.
- 최소 한 대의 ARCore 지원 실기기에서 Session을 실행한다.

### Phase 9 — 문서화

- [ ] `docs/architecture.md`
- [ ] `docs/data-model.md`
- [ ] `docs/api.md`
- [ ] `docs/docker-dev.md`
- [ ] `docs/android-arcore.md`
- [ ] `docs/platform-principles.md`
- [ ] `docs/decisions.md`
- [ ] `docs/patent-ftr.md`
- [ ] Mermaid 또는 텍스트 기반 전체 흐름도 작성
- [ ] 알려진 제약과 후속 작업 기록

문서에 반드시 명시할 원칙:

```text
Candidate Retrieval: Geo + Time
Visible Content Selection: Candidate Set + 6DoF Spatial Context
Rendering: ARCore + Anchor
Organic: Moment
Commercial: Campaign
Commercial Inventory: Geo × Time Window
```

완료 기준:

- 새 개발자가 데이터 흐름과 도메인 경계를 문서만 보고 설명할 수 있다.
- API 실행 예제와 Android 연결 방법이 기록돼 있다.

### Phase 10 — 최종 검증 및 인계

- [ ] 저장소 Clone 기준 설정 누락 확인
- [ ] `.env.example`만으로 환경 변수 목록 확인
- [ ] Docker clean start 검증
- [ ] PostGIS 활성화 검증
- [ ] Migration 및 Seed 검증
- [ ] `/health` 검증
- [ ] nearby API 검증
- [ ] timeline API 검증
- [ ] active campaign API 검증
- [ ] Backend 자동 테스트 실행
- [ ] Android Debug Build 실행
- [ ] ARCore 지원 기기 Session 검증
- [ ] README 최종 실행 절차 검토
- [ ] 미구현 항목과 다음 권장 작업 보고

## 6. 개발 진행 방식

각 Phase는 아래 순서로 진행한다.

1. 해당 Phase의 인터페이스와 완료 기준 확정
2. 최소 구현
3. 자동 테스트 또는 실행 검증
4. 관련 문서 갱신
5. 변경 파일과 검증 결과 확인
6. 기능 단위 커밋

예상 커밋 구분:

```text
chore: initialize repository structure
chore: initialize docker development environment
feat: add postgis schema and migrations
feat: add seed data
feat: add geozone and timeline api
feat: add campaign active query
test: cover core geo-time queries
feat: initialize android arcore application
docs: add platform architecture
```

커밋 전에는 다음을 확인한다.

- [ ] 관련 테스트 통과
- [ ] Secret 포함 여부 확인
- [ ] 불필요한 생성 파일 제외
- [ ] README 또는 관련 문서 갱신
- [ ] 기존 사용자 파일을 의도치 않게 변경하지 않았는지 확인

## 7. 설계 시 먼저 확정할 항목

- [ ] `TimeLayer`를 DB Entity로 유지할지, Moment/Campaign을 묶는 Query/View 개념으로 둘지 결정
- [ ] GeoZone이 반경, Polygon 또는 두 방식을 동시에 지원할지 확정
- [ ] 익명 Moment 생성을 허용할지 결정
- [ ] 초기 인증을 제외할지, 최소 개발용 인증을 넣을지 결정
- [ ] Content 업로드 API를 MVP에 포함할지 결정
- [ ] Campaign 중복 노출 시 priority 외의 정렬 규칙 결정
- [ ] Android 최소 지원 SDK와 ARCore 지원 정책 결정
- [ ] Android에서 표시할 첫 AR Asset 형식 결정

초기 구현 기본값:

- TimeLayer는 별도 테이블보다 Query/응답 모델로 시작한다.
- GeoZone은 중심점+반경을 필수로 하고 Polygon은 선택값으로 둔다.
- 인증은 Core 데이터 모델만 준비하고 MVP API 강제 인증은 보류한다.
- 콘텐츠 파일 업로드보다 Placeholder Object Key와 조회 흐름을 먼저 구현한다.
- Android 첫 AR Asset은 단순 Plane 위 이미지 또는 기본 3D Placeholder로 구현한다.

## 8. 현재 환경 제약

현재 확인된 개발 머신 상태:

- [x] Git 실행 파일 존재
- [x] Java Runtime 존재
- [x] Docker Desktop 4.87.0 설치 및 Engine 실행 확인
- [x] Android SDK Platform/Build Tools 35와 36 설치
- [x] ADB 37.0.1 설치
- [x] Android Studio 내장 JDK와 `javac` 확인

현재 다음 항목까지 검증했다.

- [x] Docker Compose 실제 실행
- [x] PostGIS/MinIO 통합 실행
- [x] Android Gradle Build
- [ ] ARCore 실기기 Session

## 9. 이번 초기 구축에서 제외할 범위

- [ ] 친구 관계 및 SNS 기능
- [ ] VPS/운영 배포
- [ ] 대규모 Vision Database
- [ ] 실제 광고 결제
- [ ] 실제 CDN
- [ ] Recommendation AI
- [ ] Creator Revenue Share
- [ ] 실시간 영상 AI 생성
- [ ] 복잡한 Admin 시스템
- [ ] iOS App
- [ ] 실제 AR Glass 연동

위 항목은 이번 단계에서 구현하지 않으며, Core 구조가 확장 가능하도록 경계만 고려한다.

## 10. 전체 완료 체크리스트

- [ ] Repository Clone 가능
- [ ] `.env.example` 제공
- [ ] `docker compose up -d` 성공
- [ ] PostGIS 활성화 확인
- [ ] Migration 성공
- [ ] Seed Data 생성
- [ ] Backend health 응답
- [ ] nearby GeoZone 조회
- [ ] GeoZone timeline 조회
- [ ] 현재 활성 Campaign 조회
- [ ] Android Project 빌드
- [ ] ARCore Session 실행
- [ ] 요구 문서 작성 완료
