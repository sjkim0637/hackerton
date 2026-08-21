# 목표

이번 작업의 목표는 특정 서비스 하나를 먼저 만드는 것이 아니라, 향후 여러 Use Case를 올릴 수 있는 **Geo-Time AR Platform Core**를 구축하는 것이다.

핵심 데이터 축은 다음 3가지다.

- **AR**
- **위치정보(Geo)**
- **시간(Time)**

이 플랫폼 위에서 향후 다음과 같은 서비스가 가능해야 한다.

- 장소의 과거 Moment를 시간축으로 탐색하는 Time Rewind
- Geo-Time 기반 AR 광고 Campaign
- 특정 POI에서 도면/시공정보/점검이력을 보는 현장 업무
- 관광/게임/이벤트/커머스 등 위치·시간 기반 AR 콘텐츠

현재 단계에서는 특정 Use Case에 종속되지 않는 공통 플랫폼 구조를 우선 구축한다.

---

# 1. 반드시 지킬 설계 원칙

## 1-1. 플랫폼 중심 구조

아래 개념을 Core Domain으로 둔다.

- User
- GeoZone
- POI
- Content
- Moment
- TimeLayer
- Campaign
- CampaignSchedule
- SpatialImpression

특정 화면이나 특정 해커톤 데모에 맞춰 데이터 모델을 억지로 설계하지 않는다.

---

## 1-2. 콘텐츠 Retrieval

콘텐츠 검색의 기본 조건은:

`Geo + Time`

이다.

중요:

- Orientation
- Heading
- 6DoF
- Pose

를 콘텐츠 해금 또는 Retrieval 핵심 조건으로 사용하지 않는다.

이 데이터는 필요시 **Rendering Metadata**로만 사용한다.

---

## 1-3. AR Rendering

ARCore는:

- 6DoF Tracking
- Anchor
- Plane
- Camera Pose
- Spatial Rendering

용도로 사용한다.

즉:

`Geo + Time으로 콘텐츠 선택`
→ `ARCore로 현실 공간에 표현`

구조를 유지한다.

---

## 1-4. Organic / Commercial 분리

일반 사용자 콘텐츠와 광고 콘텐츠를 같은 개념으로 취급하지 않는다.

### Organic
- Moment
- 사용자 생성 콘텐츠
- 실제 발생한 장소와 시간

### Commercial
- Campaign
- 미래의 특정 Geo × Time Window에 편성되는 상업 콘텐츠

두 Domain은 DB 구조와 서비스 로직에서 분리한다.

---

# 2. Repository 구조

다음 구조를 기본으로 한다.

```text
/
├─ app/
│  └─ android/
│
├─ backend/
│
├─ db/
│  ├─ migrations/
│  ├─ seeds/
│  └─ schema/
│
├─ infra/
│  ├─ docker/
│  └─ scripts/
│
├─ docs/
│
├─ assets/
│  └─ demo/
│
├─ docker-compose.yml
├─ .env.example
├─ .gitignore
├─ README.md
└─ AGENTS.md
```

필요하면 구조는 개선 가능하지만, Android App / Backend / DB / Infra / Docs는 반드시 분리한다.

---

# 3. Docker 개발환경

서버 측 개발환경은 Docker Compose 기반으로 통일한다.

최소 구성:

## PostgreSQL + PostGIS
용도:
- User
- GeoZone
- POI
- Moment
- Campaign
- Schedule
- Spatial Query

PostGIS를 기본 활성화한다.

---

## Backend API
추천:
- Python FastAPI

역할:
- GeoZone 조회
- 주변 콘텐츠 조회
- Moment CRUD
- Campaign 조회
- Time Layer Query
- 업로드 메타데이터 관리

---

## Object Storage
초기에는 MinIO 사용.

용도:
- 영상
- 이미지
- 썸네일
- 3D Asset
- PDF/도면 등

Object Storage를 DB에 직접 저장하지 않는다.

DB에는 URL/Object Key만 저장한다.

---

## 선택 구성

필요시 추후 추가:

- Redis
- Nginx
- Admin Web
- Worker

초기에는 불필요한 서비스는 넣지 않는다.

---

# 4. Docker 실행 목표

새로운 개발자가 Repository를 Clone한 뒤 아래 명령만으로 서버 환경이 올라와야 한다.

```bash
docker compose up -d
```

실행 후 최소한 다음이 자동으로 준비돼야 한다.

- PostgreSQL
- PostGIS
- Backend API
- MinIO
- DB Migration
- Seed Data

가능하면 healthcheck도 구성한다.

---

# 5. 환경변수

실제 비밀번호/API Key는 Git에 올리지 않는다.

다음 방식으로 관리한다.

```text
.env
```

→ Git 제외

```text
.env.example
```

→ Git 포함

예:

```text
DATABASE_URL=
POSTGRES_USER=
POSTGRES_PASSWORD=

MINIO_ENDPOINT=
MINIO_ACCESS_KEY=
MINIO_SECRET_KEY=

APP_ENV=
```

실제 Secret 값을 Repository에 커밋하지 않는다.

---

# 6. DB 설계

초기 최소 Entity:

## User
기본 사용자 정보

---

## GeoZone
특정 공간 영역

예:
- 공원
- 광장
- 공사현장 구역
- 매장
- 관광지

필드 예:

- id
- name
- center_point
- radius
- geometry
- created_at

PostGIS Geography/Geometry 타입 활용 검토.

---

## POI

GeoZone 내부의 관심 지점.

예:
- 특정 설비
- 특정 벽
- 출입구
- 공사 위치
- 특정 AR 콘텐츠 지점

GeoZone과 별도 Entity로 둔다.

---

## Content

실제 미디어 Asset의 공통 부모 개념.

지원 가능 유형:

- video
- image
- audio
- 3d
- pdf
- document
- ar_object

---

## Moment

사용자가 실제 장소와 시간에 남긴 콘텐츠.

핵심:

- geo_zone_id
- poi_id optional
- content_id
- recorded_at
- created_by
- rendering_metadata optional

Moment의 핵심 Retrieval은 Geo + Time이다.

---

## Campaign

Commercial Layer.

- brand
- title
- content_id
- geo_zone_id

---

## CampaignSchedule

광고 편성표.

핵심:

- campaign_id
- geo_zone_id
- start_at
- end_at
- priority
- status

즉:

`Geo × Time Window`

를 광고 Inventory로 취급할 수 있도록 한다.

---

## SpatialImpression

향후 광고 측정용.

초기에는 최소 스키마만 만든다.

예:

- campaign_id
- user_id optional
- geo_zone_id
- displayed_at
- duration
- interaction_type
- metadata

---

# 7. Migration

DB 변경은 migration 기반으로 관리한다.

추천:

- Alembic

원칙:

- 실제 DB 파일 Git 업로드 금지
- 모든 Schema 변경은 Migration으로 재현 가능해야 함
- Seed Data는 별도 관리

---

# 8. Seed Data

해커톤 개발을 위해 최소 데모 데이터를 자동 생성한다.

예:

GeoZone 1개

Moment 5~10개

Campaign 1개

CampaignSchedule 1개

테스트 User 2~3개

실제 영상 Asset이 없으면 placeholder URL 사용 가능.

---

# 9. Backend API

초기 API는 최소한 다음을 제공한다.

## Health

```text
GET /health
```

---

## Geo

```text
GET /geozones/nearby
```

현재 위도/경도를 받아 주변 GeoZone 반환.

---

## Time Layer

```text
GET /geozones/{id}/timeline
```

특정 GeoZone의 Moment를 recorded_at 기준으로 반환.

필터:

- from
- to
- limit

---

## Moment

```text
GET /moments/{id}
POST /moments
```

---

## Campaign

```text
GET /geozones/{id}/campaigns/active
```

현재 시각 기준 활성 Campaign 반환.

---

# 10. Android App

Android 앱은 Docker에 넣지 않는다.

구성:

- Android Native
- Kotlin
- ARCore

실제 Android 기기에서 실행한다.

---

# 11. Android App 역할

초기 앱은 다음만 구현한다.

## GPS

현재 위치 확인

---

## GeoZone

Backend의 `/geozones/nearby` 호출

---

## Time Layer

현재 GeoZone의 timeline 조회

---

## Moment 선택

시간축 UI에서 Moment 선택

---

## AR Rendering

선택된 Content를 ARCore Anchor에 표시

중요:

ARCore Tracking 결과를 Moment Retrieval 조건으로 사용하지 않는다.

---

# 12. 6DoF

6DoF는 ARCore에 맡긴다.

용도:

- 현재 Camera Pose 추적
- Anchor 유지
- 사용자가 이동해도 AR 콘텐츠가 공간에 고정된 것처럼 보이게 함

직접 6DoF Engine을 구현하지 않는다.

---

# 13. MVP UI

초기 화면은 복잡하게 만들지 않는다.

최소:

## 1. Camera Screen

현재 카메라

---

## 2. GeoZone 상태

예:

```text
현재 장소: Demo Zone
Moment 8개
```

---

## 3. Time Slider

예:

```text
2024 ─ 2025 ─ 2026 ─ NOW
```

---

## 4. Moment Preview

선택된 과거 콘텐츠

---

## 5. Campaign Layer

현재 활성 Campaign 표시

---

# 14. Admin

초기에는 Admin Web을 반드시 만들 필요 없다.

필요하다면 매우 단순한 CRUD UI만 만든다.

관리 대상:

- GeoZone
- Moment
- Campaign
- CampaignSchedule

핵심 플랫폼보다 Admin UI에 시간을 많이 쓰지 않는다.

---

# 15. 테스트

다음 테스트를 반드시 구성한다.

## Backend

- health
- GeoZone nearby
- timeline sort
- campaign active window

---

## DB

- migration
- seed
- PostGIS query

---

## Docker

새 환경에서:

```bash
docker compose down -v
docker compose up -d
```

후 정상 동작 검증.

---

# 16. 문서

docs에 최소 다음 문서를 만든다.

```text
docs/
├─ architecture.md
├─ data-model.md
├─ api.md
├─ docker-dev.md
├─ android-arcore.md
├─ platform-principles.md
└─ decisions.md
```

---

# 17. Architecture 문서

다음 전체 흐름을 설명한다.

```text
Android App
   │
   ├─ GPS
   │
   ▼
Backend API
   │
   ├─ Geo Query
   ├─ Time Query
   └─ Campaign Query
   │
   ▼
PostgreSQL + PostGIS
   │
   └─ Content Metadata
          │
          ▼
        MinIO

Moment 선택
   │
   ▼
Android ARCore
   │
   ├─ 6DoF
   ├─ Anchor
   └─ Rendering
```

---

# 18. 플랫폼 원칙 문서

반드시 다음 내용을 기록한다.

### Retrieval

```text
Geo + Time
```

### Rendering

```text
ARCore + 6DoF + Anchor
```

### Organic

```text
Moment
```

### Commercial

```text
Campaign
```

### Commercial Inventory

```text
Geo × Time Window
```

---

# 19. 특허 회피 설계 원칙

현재 preliminary FTO 검토 결과를 반영한다.

다음 구현은 Core 기능으로 만들지 않는다.

- 위치 + 바라보는 방향이 일치하면 콘텐츠 Unlock
- Orientation 기반 콘텐츠 Retrieval
- AR icon에 접근해야 콘텐츠 Unlock
- 특정 방향을 Commercial Inventory로 판매

Orientation / Pose는 Rendering Metadata로만 사용한다.

관련 세부 검토는 docs/patent-ftr.md 또는 별도 문서에 기록한다.

---

# 20. 이번 작업 범위

이번 단계에서는 실제 서비스 완성까지 하지 않는다.

목표:

## 1
Repository 구조 생성

## 2
Docker Compose 개발환경 완성

## 3
PostgreSQL + PostGIS 준비

## 4
MinIO 준비

## 5
FastAPI Skeleton

## 6
Migration 및 Seed 구성

## 7
GeoZone / Moment / Campaign 기본 API

## 8
Android ARCore Skeleton

## 9
문서 작성

---

# 21. 하지 않을 것

현재 단계에서 구현하지 않는다.

- 전 세계 지도
- VPS
- 건물 Vision Database
- 실제 광고 결제
- 실제 CDN
- Recommendation AI
- Creator Revenue Share
- SNS 팔로워 시스템
- 실시간 영상 AI 생성
- 복잡한 관리자 시스템
- iOS App
- 실제 AR Glass 연동

---

# 22. 완료 조건

다음이 모두 되어야 이번 초기 구축 작업을 완료로 본다.

1. Repository Clone 가능
2. `.env.example` 제공
3. `docker compose up -d` 성공
4. PostGIS 활성 확인
5. Migration 성공
6. Seed Data 생성
7. Backend health 응답
8. nearby GeoZone 조회
9. GeoZone Time Layer 조회
10. 현재 활성 Campaign 조회
11. Android Project 빌드 가능
12. ARCore Session 실행 가능
13. 문서 작성 완료

---

# 23. 작업 방식

작업 전 현재 Repository를 먼저 분석한다.

기존 파일을 무시하거나 삭제하지 않는다.

작업 과정에서:

- 필요한 파일 생성
- 작은 단위 Commit
- Commit Message 명확히 작성

을 수행한다.

가능하다면 기능 단위로 Commit한다.

예:

```text
chore: initialize docker development environment
feat: add postgis schema and migrations
feat: add geozone and timeline api
feat: initialize android arcore application
docs: add platform architecture
```

---

# 24. 중요

현재 목표는 **해커톤 앱 하나를 빨리 만드는 것**이 아니라:

> **AR + 위치 + 시간을 공통 데이터 구조로 다루는 플랫폼 기반을 만들고, 그 위에서 여러 팀원이 각자 Use Case를 실험할 수 있게 하는 것**

이다.

따라서 특정 Use Case에 과도하게 결합된 구현은 피한다.

플랫폼 Core를 먼저 만든 뒤, 이후 각 Branch에서:

- Time Rewind
- Construction POI
- Game
- Tourism
- Advertising

등을 독립적으로 실험할 수 있도록 설계한다.

작업 완료 후 반드시 아래 내용을 보고한다.

1. 생성/변경한 구조
2. Docker 서비스 목록
3. 주요 Entity
4. 주요 API
5. Android ARCore 상태
6. 실행 방법
7. 아직 구현하지 않은 부분
8. 다음 권장 작업