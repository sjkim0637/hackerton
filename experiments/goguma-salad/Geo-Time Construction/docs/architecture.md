# Phase 1 Architecture

## 목적

Phase 1은 DWG Viewer를 만드는 단계가 아니라 CAD 선형 데이터를 제품 공통 모델인 Construction Object로 변환할 수 있는지 검증한다.

```text
DWG
 ↓  ODA File Converter 또는 AutoCAD
DXF
 ↓  ezdxf Adapter
DrawingAnalysis + UnitRegion
 ↓  평형 Crop, mm→m 정규화
ConstructionObject[communication/cable_path]
 ↓
React 2D SVG / Three.js 3D Cylinder
```

Frontend는 DXF Entity를 직접 해석하지 않는다. Backend가 반환한 Construction Object만 사용한다.

## 평형 분리

`ET-1101`의 일곱 평형은 Model Space에 가로로 배치되어 있다. `XR_SHEET`의 삽입점과 42,000 × 29,700mm 도곽을 평형 경계로 사용한다. 제목 TEXT의 `84㎡A 단위세대` 같은 값을 평형 Identifier로 사용한다.

각 Entity 경로의 중심점이 평형 경계 안에 있으면 해당 평형에 포함한다. 원본 좌표는 Source Drawing에 남기고 Viewer용 좌표는 다음과 같이 변환한다.

```text
local_x_m = (source_x_mm - sheet_origin_x_mm) / 1000
local_y_m = (source_y_mm - sheet_origin_y_mm) / 1000
```

## Construction Object

통신 배선은 다음 의미를 가진다.

```text
category = communication
type     = cable_path
system   = home_network
```

Geometry는 3차원 `polyline`이며 X/Y는 평면 좌표, Z는 표시 높이다. 표시 높이와 지름은 현재 실제 시공 속성이 아니라 3D 변환 검증용 파라미터다.

Source에는 DXF 파일명, Entity handle, CAD Layer와 평형을 보존한다. 향후 원본 도면 추적과 Revision 비교에 사용한다.

## 지원 Entity

- `LINE`
- `LWPOLYLINE`
- `POLYLINE`
- `ARC`
- `SPLINE`

`INSERT`, `TEXT`, `MTEXT`는 배선 경로 3D화에서 제외한다. Block과 기호는 후속 Object Type으로 분리한다.
