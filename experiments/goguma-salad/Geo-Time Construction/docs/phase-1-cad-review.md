# Phase 1 CAD 사전 검토

## 결론

`ET-1101 단위세대 홈네트워크설비 평면도.dwg`는 Phase 1의 CAD 구조화와 평형별 분리 검증에 사용할 수 있다. 다만 평형이 Layout이나 독립 Block으로 나뉘어 있지 않으므로 제목 TEXT와 42,000mm 간격의 좌표 구간을 이용해 분리해야 한다.

대상 파일은 독립 도면이 아니다. 건축 골격은 `XR_단위.dwg`, 도곽은 `XR_SHEET.dwg`를 외부참조하므로 세 파일을 최소 재현 Dataset으로 본다.

## 분석 대상

| 파일 | 원본 크기 | 역할 |
|---|---:|---|
| `ET-1101 단위세대 홈네트워크설비 평면도.dwg` | 373,480 bytes | 홈네트워크 배선, 기호, 주석 |
| `XR_단위.dwg` | 7,741,022 bytes | 평형별 건축 골격 |
| `XR_SHEET.dwg` | 129,696 bytes | 42,000 × 29,700mm 도곽 |

세 파일의 합계는 8,244,198 bytes, 약 7.9MiB다. 원본 CAD 폴더 전체 50개 약 126MiB는 Git 대상에서 제외한다.

## ET-1101 구조

- DWG Header: `AC1024`
- 삽입 단위: mm (`$INSUNITS = 4`)
- Model Space 범위: X 8,480.0–294,078.9mm, Y -116,994.7–-80,918.2mm
- Layer: 77개
- Model Space Entity: 931개
- Paper Space: `Layout1`에 `VIEWPORT` 1개만 있어 평형 분리 기준으로 사용할 수 없음

Entity 통계:

| Entity | 개수 |
|---|---:|
| `LWPOLYLINE` | 270 |
| `TEXT` | 261 |
| `INSERT` | 204 |
| `CIRCLE` | 143 |
| `LINE` | 50 |
| `SPLINE` | 3 |

주요 Layer:

| Layer | Entity 개수 |
|---|---:|
| `지시선` | 286 |
| `TEX` | 175 |
| `e-wire` | 138 |
| `SYM` | 133 |
| `TEXT` | 44 |
| `title` | 35 |
| `e-wire3s` | 30 |
| `천정` | 23 |
| `E-SYM` | 15 |
| `EZ-HN-TEXT` | 15 |
| `통신단자함` | 8 |

## 평형 분리 가능성

도곽 폭은 42,000mm이며 다음 7개 평형이 X축 방향으로 연속 배치되어 있다.

| 순서 | 평형 | 기준 구간 |
|---:|---|---|
| 1 | 84㎡A | 첫 번째 42,000mm 구간 |
| 2 | 84㎡B | 두 번째 42,000mm 구간 |
| 3 | 84㎡C | 세 번째 42,000mm 구간 |
| 4 | 84㎡D | 네 번째 42,000mm 구간 |
| 5 | 120㎡A | 다섯 번째 42,000mm 구간 |
| 6 | 144㎡P | 여섯 번째 42,000mm 구간 |
| 7 | 155㎡P | 일곱 번째 42,000mm 구간 |

각 구간에는 `84㎡A 단위세대 홈네트워크설비 평면도(확장형)`과 같은 제목 TEXT가 있다. 따라서 제목을 Anchor로 삼아 가장 가까운 도곽 구간을 식별한 뒤 Entity를 Crop하고, 구간 원점으로 Translate하는 방식이 적합하다.

## Xref 영향

`ET-1101`은 다음 외부참조를 사용한다.

- `XR_단위.dwg`: 한 번 삽입되며 7개 평형의 건축 골격을 제공한다.
- `XR_SHEET.dwg`: 42,000mm 간격으로 일곱 번 삽입되어 각 평형의 도곽을 제공한다.

`XR_단위`에는 Model Space Entity 82,745개가 있다. 이 중 82,258개가 `arch` Layer에 모여 있어 Layer 이름만으로 Wall을 판별할 수 없다. 선 연결성, 폐합 영역, 두 평행선 간격 또는 별도 건축 도면의 의미 정보가 필요하다.

## Phase 1 범위 제안

첫 PoC는 84㎡A 한 구간만 대상으로 다음 순서로 진행한다.

1. 세 DWG를 로컬에서 DXF로 변환한다.
2. 제목 TEXT로 84㎡A 구간을 식별한다.
3. 42,000 × 29,700mm 범위의 Entity를 추출하고 로컬 원점으로 이동한다.
4. `e-wire`, `e-wire3s`, `SYM`, `E-SYM`, `통신단자함`을 우선 표시한다.
5. Xref 건축 골격은 배경 Layer로 표시한다.
6. Structured CAD JSON에서 원본 좌표와 평형 로컬 좌표를 함께 보존한다.

현재 도면은 통신 배선과 기호를 검증하기에는 적합하지만 Pipe Dataset은 아니다. Phase 1에서는 Pipe 3D화 대신 `e-wire`, `e-wire3s`를 통신 `cable_path`로 구조화하고 3D화하기로 결정했다. 배관 도면 추가는 현재 PoC 완료 조건이 아니다.

## 분석 환경

- Autodesk DWG TrueView 2023: 원본과 Xref 해석 확인
- ODA File Converter 27.1: 로컬 DWG→DXF 변환
- `ezdxf`: Layer, Entity, Layout, Xref와 좌표 통계 확인

변환한 DXF와 분석용 임시 파일은 저장소에 포함하지 않는다.
