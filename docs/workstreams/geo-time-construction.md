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

PLANNING

## Goal

실제 단위세대 통신 DWG를 DXF로 변환하고 Layer, Entity, 단위, 원점과 평형별 분리 가능성을 확인하여 Phase 1 구현 범위를 확정한다.

## Current Direction

Phase 1에서는 AR, AI, 전체 BIM 변환보다 실제 CAD 구조화 검증을 우선한다. Frontend와 AR이 DWG에 직접 의존하지 않도록 DWG/DXF Adapter와 Construction Object Model 사이의 경계를 유지한다.

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

## Out of Scope

- 전체 50개 CAD 파일의 저장소 반영과 일괄 변환
- 모든 DWG 형식과 모든 공종 지원
- AR 정합, AI 도면 해석과 Time History 구현
- 홈네트워크 도면만으로 Wall 또는 Pipe 의미를 완전 자동 추론

## Key Questions

- 7개 평형 중 어떤 하나를 첫 PoC 기준 Dataset으로 선택할 것인가?
- Xref를 유지한 채 분석할지, 분석용 DXF에서 결합할지?
- `arch` 단일 Layer의 선을 벽체로 판별할 최소 규칙은 무엇인가?
- 홈네트워크 배선 3D화로 성공 기준을 조정할지, 배관 도면을 별도 Dataset으로 추가할지?

## Integration Candidate

TBD

## Verification

- Autodesk DWG TrueView 2023으로 원본과 두 Xref가 정상 해석되는 것을 확인했다.
- ODA File Converter 27.1로 세 파일을 로컬 임시 DXF로 변환했다.
- `ezdxf`로 Layer, Entity, Layout, Xref, 단위와 평형 제목 좌표를 확인했다.
- 변환 DXF는 임시 분석 산출물로만 사용하고 저장소에 포함하지 않는다.

## Known Issues

- 실제 실험 폴더 이름에 공백과 대문자가 있어 저장소 표준 경로와 다르다.
- 평형은 Layout이나 Block으로 독립 분리되어 있지 않아 좌표 구간 추출 규칙이 필요하다.
- `ET-1101` 단독 Commit은 Xref 누락으로 완전한 도면 재현이 불가능하다.
- 원본 50개 CAD는 약 126MiB이며 최소 Dataset 외 파일은 Git에서 제외해야 한다.

## Next

1. 84㎡A를 우선 PoC 평형으로 선택할지 확정한다.
2. 최소 Dataset 3개를 이용해 평형별 Crop/Translate 규칙을 검증한다.
3. DXF Layer와 Entity 통계 출력 형식을 고정한다.
4. 홈네트워크 통신 배선 중심 데모와 Pipe 중심 데모 중 Phase 1 성공 기준을 확정한다.

## Relevant Commits

아직 없음.

## Updated

2026-09-01
