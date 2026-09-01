# Geo-Time Construction
## Codex 전체 개발 지시서 v2

---

# 0. 프로젝트 핵심 정의

본 프로젝트는 BIM Viewer 또는 BIM Authoring Tool을 만드는 프로젝트가 아니다.

기존 BIM/CAD/도면/시공사진/준공도/유지보수 데이터를 실제 건물 공간과 연결하여,

**“건물의 설계·시공·변경·준공·유지보수 이력을 현실 공간 위에서 조회하는 Spatial History Platform”**

을 만드는 것이 목표다.

핵심 구조는 다음과 같다.

```text
GEO × TIME × LAYER
```

### GEO

실제 건물의 공간.

```text
Project
Building
Floor
Zone
X / Y / Z
Orientation
```

### TIME

건물의 변화 이력.

```text
Design
Revision 1
Revision 2
Construction
As-Built
Maintenance
Current
```

### LAYER

건물의 공종 및 정보 종류.

```text
Architecture
Structure
Mechanical
Plumbing
Electrical
Fire
Communication
HVAC
Construction Photo
Maintenance
```

---

# 1. 제품 포지션

## 하지 않는 것

본 프로젝트를 다음과 같이 정의하지 않는다.

```text
BIM 생성 프로그램
CAD 대체 프로그램
DWG Viewer
AR BIM Viewer
AI 자동 BIM 생성기
```

기존 BIM/AR 솔루션과 직접 경쟁하는 방향으로 개발하지 않는다.

---

## 우리가 만드는 것

기존 건설 데이터를 실제 공간에서 사용할 수 있도록 연결하는

**BIM / CAD Last Mile Spatial Interface**

이다.

전체 개념:

```text
BIM
DWG
준공도
설계도
시공사진
하자정보
점검정보
유지보수정보
    │
    │
    ▼
Geo-Time Construction
    │
    ▼
Construction Object Model
    │
    ▼
Geo × Time × Layer
    │
    ▼
실제 건물 공간
    │
    ▼
Web / Mobile / AR
```

---

# 2. 기존 BIM과의 차별점

기존 BIM/AR 시스템의 대표적인 사용 흐름은 다음과 같다.

```text
최신 BIM
+
현재 현장
=
설계 vs 현실 비교
```

Geo-Time Construction은 다음과 같이 접근한다.

```text
실제 공간
   │
   ├─ Architecture
   ├─ Plumbing
   ├─ Electrical
   ├─ Fire
   └─ Maintenance
          │
          ×
          │
       Design
          ↓
      Revision
          ↓
    Construction
          ↓
      As-Built
          ↓
    Maintenance
          ↓
       Current
```

즉 사용자는 같은 공간에서

**공종 Layer와 시간 Revision을 동시에 변경**

할 수 있어야 한다.

---

# 3. 핵심 차별화 메시지

본 프로젝트의 핵심 차별점은 AR 자체가 아니다.

AR BIM Viewer는 이미 존재한다.

차별화 포인트는:

> **건물의 현재 모델을 보여주는 것이 아니라 건물의 History를 탐색한다.**

개발 과정에서도 이 제품 철학을 유지한다.

예:

```text
현재 벽
    ↓

[Layer: Plumbing]

Design
    ↓
설계 당시 배관

Construction
    ↓
시공 당시 배관 + 시공사진

As-Built
    ↓
준공 도면상의 배관

Maintenance 2029
    ↓
밸브 교체

Current
    ↓
현재 상태
```

이를 내부적으로는

**Building Git History**

개념으로 설명한다.

---

# 4. 중요한 대상 시장

신축 BIM 현장만 대상으로 하지 않는다.

특히 기존 건축물도 중요한 대상이다.

예:

```text
BIM     없음
DWG     있음
PDF     있음
준공도  있음
사진    일부 있음
AS이력  있음
```

이러한 건축물의 기존 자산을

```text
Document
```

에서

```text
Spatial Data
```

로 변환한다.

따라서 가장 중요한 초기 입력은

**기존 DWG**

이다.

---

# 5. AS-IS

현재 현장/유지보수 담당자는 다음 과정으로 정보를 확인한다.

```text
현장 확인
   ↓
도면 검색
   ↓
건축도 확인
   ↓
설비도 확인
   ↓
전기도 확인
   ↓
준공도 확인
   ↓
시공 변경사항 확인
   ↓
실제 공간에 머릿속으로 Mapping
```

문제:

- 여러 문서를 개별적으로 찾아야 한다.
- 도면과 현실 공간을 사람이 직접 매칭해야 한다.
- 설계와 실제 시공 변경 이력을 찾기 어렵다.
- 벽 내부 설비를 직관적으로 확인하기 어렵다.
- 준공 후 유지보수 정보가 기존 설계정보와 단절된다.
- BIM이 없는 기존 건물은 활용 가능한 Spatial Data가 부족하다.

---

# 6. TO-BE

현장에서 사용자는 스마트폰/태블릿으로 공간을 확인한다.

예:

```text
[현재 벽]
```

사용자 선택:

```text
LAYER

건축
설비
전기
소방
통신
```

그리고:

```text
TIME

설계
변경
시공
준공
유지보수
현재
```

예:

```text
Layer = Plumbing
Time = As-Built
```

결과:

```text
현재 벽 위에
준공 기준 배관 표시
```

Time을 변경하면:

```text
Design
→ 최초 설계 위치

Construction
→ 실제 시공 시점

As-Built
→ 최종 준공 위치

Maintenance
→ 이후 교체/보수 기록
```

을 확인한다.

---

# 7. 핵심 데모 시나리오

최종 데모에서는 단순히

```text
DWG → AR
```

만 보여주지 않는다.

반드시 Geo-Time 차별점을 보여준다.

## 데모 흐름

### STEP 1

DWG 업로드.

```text
Building A
Floor 3
```

### STEP 2

CAD Layer 자동 추출.

```text
A-WALL
M-CW
M-HW
E-POWER
F-SPR
```

### STEP 3

Layer Mapping.

```text
A-WALL → Architecture / Wall

M-CW → Plumbing / Cold Water

E-POWER → Electrical

F-SPR → Fire / Sprinkler
```

### STEP 4

2D 도면 표시.

### STEP 5

선택 Layer 3D 변환.

```text
Wall
Pipe
```

### STEP 6

AR 공간 정합.

```text
AprilTag
+
ARCore SLAM
```

### STEP 7

실제 벽에 배관 표시.

### STEP 8

Geo-Time 기능.

```text
Layer = Plumbing

Design
→
Construction
→
As-Built
→
Maintenance
```

### STEP 9

가능할 경우 시공사진을 해당 Revision에 표시.

이 STEP 8이 기존 BIM/AR와 가장 중요한 차별화 데모다.

---

# 8. 기술 스택

## Backend

```text
Python
FastAPI
Pydantic
SQLAlchemy 또는 Supabase Client
```

---

## CAD

```text
DWG
 ↓
DXF
 ↓
ezdxf
```

초기에는 DWG 직접 파싱보다

DXF 기반 구조화 검증을 우선한다.

---

## Frontend

```text
React
TypeScript
Vite
Three.js
React Three Fiber optional
```

---

## Database

```text
Supabase
PostgreSQL
PostGIS optional
```

---

## Storage

```text
Supabase Storage
```

저장 대상:

```text
DWG
DXF
GLB
Drawing Image
Construction Photo
Maintenance Photo
```

---

## Mobile AR

```text
Android
Kotlin
ARCore
glTF / GLB
```

---

## Indoor Positioning

초기:

```text
AprilTag
+
ARCore SLAM
```

향후:

```text
Visual Positioning
LiDAR
Depth
BIM Geometry Matching
Multi Anchor Calibration
```

---

# 9. 핵심 Architecture

```text
                INPUT

      DWG / DXF / BIM / Photo
                 │
                 ▼
        Source Adapter Layer
                 │
                 ▼
       Construction Object Model
                 │
         ┌───────┼───────┐
         ▼       ▼       ▼
       GEO      TIME    LAYER
         │       │       │
         └───────┼───────┘
                 ▼
            Geometry Engine
                 │
          ┌──────┴──────┐
          ▼             ▼
     Web Viewer       AR App
```

---

# 10. 매우 중요한 Architecture 원칙

Frontend 또는 AR 앱이 DWG 구조에 직접 의존하면 안 된다.

잘못된 구조:

```text
DWG
 ↓
AR Renderer
```

올바른 구조:

```text
DWG
 ↓
CAD Parser
 ↓
Construction Object Model
 ↓
Geometry
 ↓
Web / AR
```

향후 데이터가 바뀌더라도:

```text
IFC ─┐
RVT ─┤
DWG ─┤
DXF ─┤
PDF ─┤
Photo┘
     ↓
Construction Object Model
```

형태를 유지한다.

---

# 11. Construction Object Model

프로젝트 핵심 데이터 모델이다.

예:

```json
{
  "id": "PIPE-001",

  "project_id": "project-a",
  "building_id": "101",
  "floor_id": "03F",

  "category": "plumbing",
  "type": "pipe",
  "system": "cold_water",

  "source": {
    "type": "dwg",
    "drawing_id": "drawing-001",
    "layer": "M-CW"
  },

  "geometry": {
    "type": "polyline",
    "points": [
      [1.2, 0.8, 2.2],
      [1.2, 3.4, 2.2],
      [5.1, 3.4, 2.2]
    ]
  },

  "properties": {
    "diameter": 0.05
  },

  "revision": {
    "type": "as_built",
    "revision_id": "REV-04"
  }
}
```

---

# 12. Time Model

기존 Revision 시스템을 단순 문자열로 구현하지 않는다.

Revision 자체를 독립 객체로 만든다.

예:

```text
DrawingRevision
```

Schema 예:

```json
{
  "id": "REV-04",
  "name": "As-Built",
  "type": "as_built",
  "valid_from": "2026-08-01",
  "valid_to": null,
  "previous_revision": "REV-03"
}
```

지원 Type:

```text
design
revision
construction
as_built
maintenance
current
```

향후 실제 날짜 기반 Timeline으로 확장할 수 있도록 설계한다.

---

# 13. Layer Model

Layer에는 CAD Layer와 Logical Layer를 분리한다.

## CAD Layer

실제 도면:

```text
M-CW
A-WALL
F-SPR
```

## Logical Layer

우리 플랫폼:

```text
Architecture
Plumbing
Electrical
Fire
Communication
```

예:

```text
M-CW

↓

category = Plumbing
system   = Cold Water
```

---

# 14. 시공사진도 Layer/Time 데이터다

시공사진을 단순 첨부파일로 처리하지 않는다.

예:

```json
{
  "type": "construction_photo",

  "project_id": "project-a",

  "floor": "03F",

  "position": {
    "x": 10.2,
    "y": 4.8,
    "z": 1.6
  },

  "orientation": {
    "yaw": 124
  },

  "revision_id": "REV-03",

  "captured_at": "2026-05-22"
}
```

향후 AR 공간에서 해당 위치에 사진을 Overlay 할 수 있어야 한다.

---

# 15. 유지보수 데이터

향후 유지보수 이력도 동일 Spatial History에 넣는다.

예:

```json
{
  "type": "maintenance",

  "object_id": "PIPE-001",

  "date": "2029-05-12",

  "action": "valve_replacement",

  "description": "밸브 교체",

  "photo": "..."
}
```

이로써 건물의 정보가:

```text
Design
→ Construction
→ As-Built
→ Maintenance
→ Current
```

까지 이어진다.

---

# 16. 개발 Roadmap

---

# PHASE 1
## 실제 DWG → Web 3D PoC

목적:

> 실제 현장 DWG를 구조화할 수 있는지 검증한다.

AR보다 먼저 수행한다.

---

## Phase 1 완료 조건

```text
DXF Upload
 ↓
Layer 추출
 ↓
Entity 추출
 ↓
2D 표시
 ↓
Layer 선택
 ↓
Construction Object 변환
 ↓
Wall / Pipe Geometry 생성
 ↓
Three.js 표시
```

---

# PHASE 1 분산 개발

## User A
### CAD / Backend

담당:

```text
DXF Parsing
API
Layer
Entity
Source Data
```

TODO:

- FastAPI 기본 구성
- DXF Upload API
- ezdxf 연결
- Layer 목록 반환
- LINE 처리
- LWPOLYLINE 처리
- POLYLINE 처리
- ARC 처리
- CIRCLE 처리
- BLOCK / INSERT 기본 처리
- TEXT / MTEXT Metadata
- Drawing Metadata
- Original CAD Coordinate 보존
- 단위 확인
- JSON API 생성

완료 결과:

```text
DXF
 ↓
Structured CAD JSON
```

---

## User B
### Web / Viewer

담당:

```text
React
2D Viewer
3D Viewer
Layer UI
Time UI
```

TODO:

- React + TypeScript
- Drawing Upload
- Layer Panel
- 2D Viewer
- Zoom / Pan
- Grid
- Layer ON/OFF
- Three.js
- OrbitControls
- Object Selection
- Object Property Panel

TIME UI는 Phase 1에서는 Mock 가능.

```text
Design
As-Built
```

두 개만 우선 표현한다.

---

## User C
### Geometry / Domain Model

담당:

```text
Construction Object
Geometry Engine
Coordinate
Revision Model
```

TODO:

- ConstructionObject Schema
- Logical Layer Schema
- Revision Schema
- Layer Mapping
- Polyline → Wall
- Polyline → Pipe
- Wall height parameter
- Pipe elevation parameter
- Pipe diameter parameter
- Geometry Unit normalization
- GLB Export 검토
- 테스트 Dataset

---

# PHASE 2
## Geo × Layer Platform

목적:

단순 CAD Viewer에서 벗어난다.

구현:

```text
Project
Building
Floor
Drawing
CAD Layer
Logical Layer
Construction Object
```

Layer:

```text
Architecture
Plumbing
Electrical
Fire
Communication
```

---

# PHASE 3
## Time / Revision Platform

이 Phase가 제품 차별화 핵심이다.

구현:

```text
Design
Revision
Construction
As-Built
Maintenance
```

같은 Object의 Revision 차이를 표현한다.

예:

```text
PIPE-001

Design
Path A

↓

Construction
Path B

↓

As-Built
Path B
```

Viewer에서:

```text
[Design]
[Construction]
[As-Built]
```

전환 가능하게 한다.

---

# PHASE 4
## AR Prototype

```text
Construction Object
 ↓
Geometry
 ↓
GLB
 ↓
Android
 ↓
ARCore
```

초기 지원:

```text
Wall
Pipe
```

---

# PHASE 5
## Indoor Alignment

GPS를 사용하지 않는다.

초기 방식:

```text
AprilTag
+
Building Coordinate
+
ARCore SLAM
```

Flow:

```text
Building 기준 좌표
 ↓
실제 공간 Marker
 ↓
Camera Detect
 ↓
Pose
 ↓
Transform Matrix
 ↓
AR Coordinate
```

---

# PHASE 6
## Geo-Time Construction AR

AR에서 실제 Time과 Layer UI를 구현한다.

예:

```text
LAYER

Architecture
Plumbing
Electrical
Fire


TIME

Design
Construction
As-Built
Maintenance
Current
```

---

# PHASE 7
## Construction Time X-Ray

최종 핵심 Experience.

사용자가 벽을 바라본다.

```text
현재

████████████
    WALL
████████████
```

Layer:

```text
Plumbing
```

선택:

```text
████████████

 ───── PIPE

████████████
```

Time:

```text
Design
→
Construction
→
As-Built
→
Maintenance
```

변경 가능.

---

# PHASE 8
## AI Assistance

AI는 자동 BIM 생성기가 아니다.

AI 사용 범위:

### CAD Layer Mapping

```text
M-P-WSUP
```

↓

```text
Plumbing / Water Supply
```

추천.

### Drawing Classification

```text
건축
설비
전기
소방
```

분류.

### TEXT / MTEXT 해석

배관 규격, 층고, 장비명 등 추출.

### Revision Analysis

예:

```text
Revision 02 → Revision 03

배관 위치 3건 변경
벽체 1건 변경
설비 2건 추가
```

---

# 17. Repository 구조

```text
geo-time-construction/

├── apps/
│   ├── web/
│   └── android/
│
├── backend/
│   ├── api/
│   ├── cad/
│   ├── adapters/
│   └── services/
│
├── packages/
│   ├── construction-schema/
│   ├── geometry-core/
│   ├── layer-mapping/
│   ├── revision-core/
│   └── coordinate-core/
│
├── samples/
│   ├── dwg/
│   ├── dxf/
│   ├── json/
│   └── photos/
│
├── docs/
│   ├── architecture.md
│   ├── construction-object.md
│   ├── coordinate-system.md
│   ├── layer-model.md
│   ├── revision-model.md
│   └── roadmap.md
│
└── agent/
    ├── user-a/
    ├── user-b/
    └── user-c/
```

---

# 18. Branch 전략

```text
main

├── agent/user-a
├── agent/user-b
└── agent/user-c
```

User A:

```text
Backend
CAD Parser
```

User B:

```text
Web Viewer
AR
UX
```

User C:

```text
Geometry
Schema
Coordinate
Time/Revision
```

---

# 19. 공통 Schema 먼저 고정

다음 Schema는 모든 Agent가 공유한다.

```text
Project
Building
Floor

Drawing
DrawingRevision

CadLayer
LogicalLayer

ConstructionObject

SpatialAnchor

SpatialMedia

MaintenanceEvent
```

한 Agent가 임의로 공통 Schema를 변경하지 않는다.

변경이 필요한 경우:

```text
docs/schema-change-proposal.md
```

에 기록한 후 변경한다.

---

# 20. Phase 1 최우선 TODO

현재 가장 먼저 수행할 작업.

```text
1. 실제 DWG 확보

2. DXF Export

3. DXF Layer 목록 출력

4. Entity 종류 통계 출력

5. CAD Unit 확인

6. Origin 확인

7. LINE/POLYLINE 좌표 추출

8. A-WALL / Pipe Layer 탐색

9. 해당 Layer 2D Viewer 표시

10. Wall / Pipe 하나 3D화
```

---

# 21. 반드시 확인할 실제 DWG 정보

분석 도구를 먼저 만든다.

출력 예:

```text
File
sample.dxf

Unit
mm

Layers
243

Entities
LINE          23,452
LWPOLYLINE     8,220
INSERT         3,431
TEXT           5,002
ARC            1,203
```

Layer Ranking:

```text
Layer                  Entity Count

A-WALL                 4,320
M-CW                     923
M-HW                     843
E-POWER                1,034
F-SPR                     781
```

이 결과를 보고 Phase 1 상세 개발범위를 확정한다.

---

# 22. 개발 중 하지 말아야 할 것

다음 작업을 초기에 하지 않는다.

- 모든 DWG Format 지원
- PDF 자동 BIM 생성
- 모든 공종 Geometry 생성
- AI 기반 완전자동 도면 해석
- mm급 AR 정밀도 주장
- 자체 BIM Editor 제작
- Revit 대체 기능
- 복잡한 사용자 권한 시스템
- 대규모 Cloud Architecture

---

# 23. 성공 기준

해커톤 MVP 성공은 BIM 전체 구현이 아니다.

다음 시나리오가 성공하면 된다.

```text
실제 DXF 업로드

↓

Layer 자동 추출

↓

M-CW 선택

↓

2D 배관 표시

↓

3D 배관 생성

↓

AR Mode

↓

실제 벽에 배관 Overlay

↓

TIME

Design
→
As-Built

↓

배관 위치 또는 모델 변경
```

---

# 24. 최종 제품 메시지

개발 과정에서 계속 다음 메시지를 기준으로 판단한다.

> **Geo-Time Construction은 BIM을 대체하지 않는다.**

> **BIM, DWG, 시공사진, 준공도, 유지보수 정보를 실제 공간과 연결한다.**

> **현재 모델이 아니라 건물의 History를 보여준다.**

> **건설 데이터를 Document에서 Spatial Data로 바꾼다.**

최종 제품 정의:

# Geo-Time Construction

### Building History in Space

```text
GEO
×
TIME
×
LAYER
```

건물의 설계부터 시공, 준공, 유지보수까지의 정보를 하나의 공간 이력으로 연결한다.

---

# 25. Codex 작업 지침

Codex는 현재 Phase보다 앞선 기능을 임의로 개발하지 않는다.

현재 기본 Phase는:

# PHASE 1

이다.

따라서 최초 작업 우선순위는 반드시:

```text
실제 DWG/DXF 분석
→
CAD 구조화
→
Construction Object
→
Web 3D Viewer
```

순서로 진행한다.

AR, AI, Time History는 Architecture를 고려하여 인터페이스만 준비하되,

Phase 1 완료 이전에는 핵심 구현 대상으로 삼지 않는다.

각 Agent는 작업 완료 시 다음을 반드시 기록한다.

```text
agent/{user}/handoff.md
```

내용:

```text
현재 Branch
완료 기능
변경 파일
공통 Schema 변경 여부
테스트 결과
남은 이슈
다음 작업
다른 Agent가 알아야 할 내용
```

모든 코드는 향후

```text
Geo × Time × Layer
```

구조로 확장 가능하도록 작성한다.