# Workstream

## Topic

Geo-Time Construction

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/geo-time-construction`

## Project Path

`experiments/goguma-salad/Geo-Time Construction/`

현재 원본 자료가 위 경로에 있어 그대로 보존한다. 구현을 시작하기 전 저장소 규칙에 맞는 `experiments/goguma-salad/geo-time-construction/` 경로로 정리해야 한다.

## Status

IN_PROGRESS

## Goal

실제 단위세대 통신 DWG를 DXF로 변환하고, 84㎡A의 홈네트워크 배선을 Construction Object로 구조화하여 2D·3D Viewer에 표시한다.

## Current Direction

Phase 1에서는 AR, AI, 전체 BIM 변환보다 실제 CAD 구조화 검증을 우선한다. Frontend와 AR이 DWG에 직접 의존하지 않도록 DWG/DXF Adapter와 Construction Object Model 사이의 경계를 유지한다.

Pipe 대신 `e-wire`, `e-wire3s`의 선형 Entity를 `communication/cable_path`로 변환한다. X/Y 평면 좌표는 평형 원점 기준 m 단위로 정규화하고, Z 높이와 배선 지름은 Viewer용 파라미터로 입력한다. 84㎡A를 첫 PoC 평형으로 사용한다.

분석 대상은 `ET-1101 단위세대 홈네트워크설비 평면도.dwg`이다. 전체 CAD 50개를 Git에 포함하지 않고, 대상 도면과 재현에 필요한 `XR_단위.dwg`, `XR_SHEET.dwg`만 최소 Dataset 후보로 관리한다.

## Initial CAD Review

- 원본은 AutoCAD 2010 계열 DWG Header인 `AC1024`이며 삽입 단위는 mm이다.
- `ET-1101`에는 77개 Layer와 Model Space Entity 931개가 있다.
- 주요 Entity는 `LWPOLYLINE` 270개, `TEXT` 261개, `INSERT` 204개, `CIRCLE` 143개, `LINE` 50개이다.
- 주요 Layer는 `지시선`, `TEX`, `e-wire`, `SYM`, `TEXT`, `title`, `e-wire3s`, `천정`, `E-SYM`, `EZ-HN-TEXT`, `통신단자함`이다.
- 유효한 Paper Space 분리는 없고, 7개 평형이 Model Space에 42,000mm 간격으로 가로 배치되어 있다.
- 평형은 84㎡A, 84㎡B, 84㎡C, 84㎡D, 120㎡A, 144㎡P, 155㎡P이며 제목 TEXT와 좌표 구간으로 분리할 수 있다.
- `ET-1101`은 `XR_단위.dwg`와 `XR_SHEET.dwg`를 외부참조한다. 대상 파일 하나만으로는 건축 골격과 시트가 완전하게 재현되지 않는다.
- `XR_단위`에는 82,745개 Model Space Entity가 있고 이 중 82,258개가 `arch` Layer에 있어, 벽체 의미를 CAD Layer 이름만으로 자동 판별하기 어렵다.
- 홈네트워크 도면은 통신 배선과 기호 검증에는 적합하지만, 작업지시서의 Pipe 3D화 성공 기준을 직접 검증할 배관 Layer는 제공하지 않는다.

상세 수치는 [Phase 1 CAD 사전 검토](../../experiments/goguma-salad/Geo-Time%20Construction/docs/phase-1-cad-review.md)에 기록한다.

## Scope

- DWG를 DXF로 변환하는 재현 가능한 절차 확정
- Layer와 Entity 종류 및 개수 추출
- CAD 단위와 원본 좌표 보존 확인
- 제목 TEXT와 42,000mm 좌표 구간을 이용한 평형별 분리
- 통신 배선과 기호를 Structured CAD JSON 후보로 변환
- Phase 1 최소 Dataset과 제외 대상 확정
- FastAPI 기반 DXF 분석·Construction Object API
- React 2D SVG와 Three.js 3D 통신 배선 Viewer

## Out of Scope

- 전체 50개 CAD 파일의 저장소 반영과 일괄 변환
- 모든 DWG 형식과 모든 공종 지원
- AR 정합, AI 도면 해석과 Time History 구현
- 홈네트워크 도면만으로 Wall 또는 Pipe 의미를 완전 자동 추론
- 실제 시공 높이와 배선 지름의 자동 추론

## Key Questions

- Xref를 유지한 채 분석할지, 분석용 DXF에서 결합할지?
- `arch` 단일 Layer의 선을 벽체로 판별할 최소 규칙은 무엇인가?
- 통신 기호 `INSERT`를 어떤 Construction Object Type으로 분리할 것인가?

## Decisions

- 첫 PoC 평형은 84㎡A로 한다.
- Phase 1의 Pipe 3D화 항목은 통신 `cable_path` 3D화로 대체한다.
- `e-wire`, `e-wire3s`의 선형 Entity만 경로로 변환하고 주석 `INSERT`는 제외한다.
- Backend가 Construction Object를 생성하며 Frontend는 DXF를 직접 해석하지 않는다.
- 높이 2.3m와 지름 0.03m는 실제 시공값이 아닌 초기 Viewer 기본값으로 표시한다.

## Integration Candidate

TBD

## Verification

- Autodesk DWG TrueView 2023으로 원본과 두 Xref가 정상 해석되는 것을 확인했다.
- ODA File Converter 27.1로 세 파일을 로컬 임시 DXF로 변환했다.
- `ezdxf`로 Layer, Entity, Layout, Xref, 단위와 평형 제목 좌표를 확인했다.
- 변환 DXF는 임시 분석 산출물로만 사용하고 저장소에 포함하지 않는다.
- PowerShell 변환 Script로 Phase 1 DWG 세 파일만 `data/derived/`에 DXF로 변환했다.
- 실제 ET-1101 84㎡A에서 통신 배선 객체 28개와 경로 점 189개를 생성했다.
- `e-wire` 24개와 `e-wire3s` 선형 4개가 변환되고 주석 `INSERT` 2개는 제외됐다.
- Backend Ruff 통과, Pytest 7개 통과.
- Frontend ESLint와 TypeScript/Vite Production Build 통과.
- FastAPI 실행 상태에서 실제 ET-1101 DXF Upload API가 객체 28개를 반환했다.

## Known Issues

- 실제 실험 폴더 이름에 공백과 대문자가 있어 저장소 표준 경로와 다르다.
- 평형은 Layout이나 Block으로 독립 분리되어 있지 않아 좌표 구간 추출 규칙이 필요하다.
- `ET-1101` 단독 Commit은 Xref 누락으로 완전한 도면 재현이 불가능하다.
- 원본 50개 CAD는 약 126MiB이며 최소 Dataset 외 파일은 Git에서 제외해야 한다.
- 실제 통신 배선의 높이, 지름과 시공 경로는 현재 DWG만으로 확정할 수 없다.
- 3D Viewer Production Bundle이 약 1.08MB라 후속 단계에서 Three.js Code Splitting을 검토해야 한다.
- 연결 가능한 Browser 인스턴스가 없어 이번 작업에서 UI 육안 검증은 수행하지 못했다.

## Next

1. 로컬 Browser에서 실제 ET-1101 2D·3D 화면을 육안 검증한다.
2. `XR_단위` 건축 골격을 경량 Background Geometry로 결합한다.
3. 통신단자함과 홈넷 기호 `INSERT`를 별도 Object Type으로 변환한다.
4. 84㎡B 등 다른 평형에도 동일 Crop/Translate 규칙을 회귀 검증한다.
5. 필요한 경우 GLB Export 경계를 검토한다.

## Relevant Commits

- `3bd0ef8` docs(cad): Geo-Time Construction Phase 1 검토 등록
- `3b2a4ce` feat(cad): 통신 배선 2D·3D 변환 PoC 구현

## Updated

2026-09-01
