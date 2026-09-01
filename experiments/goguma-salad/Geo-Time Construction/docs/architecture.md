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
 + ArchitectureBackground[filtered line segments]
 ↓  공통 평형 Local 좌표
React 2D SVG / Three.js 3D Cylinder
```

Frontend는 DXF Entity를 직접 해석하지 않는다. Backend가 반환한 Construction Object만 사용한다.

## 건축 배경

`XR_단위`는 대부분의 Entity가 `arch` 단일 Layer에 있어 벽체를 의미적으로 확정하기 어렵다. Phase 1에서는 벽체 3D 객체를 추론하지 않고 `LINE`, `LWPOLYLINE`, `POLYLINE`을 평형 도곽으로 자른 뒤 길이 100mm 이상 선분만 중복 제거하여 경량 배경으로 제공한다.

건축 배경과 통신 배선은 동일한 평형 Local m 좌표를 사용한다. 배경은 바닥 높이의 회색 선으로만 표시하고, 사용자가 Toggle로 숨길 수 있다. 이 방식은 통신 경로의 공간 맥락을 제공하지만 벽, 문, 가구 등 건축 의미 분류를 보장하지 않는다.

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

Viewer에서 통신 경로를 선택하면 동일 Source 속성을 2D와 3D에서 공통으로 확인한다.

## 지원 Entity

- `LINE`
- `LWPOLYLINE`
- `POLYLINE`
- `ARC`
- `SPLINE`

`INSERT`, `TEXT`, `MTEXT`는 배선 경로 3D화에서 제외한다.

## 통신 설비 Object (Device)

통신단자함과 홈넷 기기는 선형이 아닌 점형 설비이므로 `cable_path`와 별도 Object Type으로 분리한다.

```text
category = communication
type     = communication_device
system   = home_network
```

Geometry는 `point`이며 `INSERT`의 삽입점을 평형 Local m 좌표로 변환한다. Layer가 `통신단자함`, `SYM`, `E-SYM`, `천정`인 `INSERT`만 대상으로 하고, Block 이름을 Subtype으로 매핑한다.

```text
100-57        → communication_panel  (통신단자함)
EFCL          → entrance_camera      (세대 현관 카메라)
A$C6A344DFD   → magnetic_sensor      (마그네틱 센서)
A$C4BEC3CC4   → motion_detector      (동체 감지기)
A$CCCBF2ACF   → infrared_detector    (적외선 감지기)
F19           → batch_switch         (일괄소등 스위치)
100-89 / 50J  → joint_box            (Joint Box)
A$C5B1B9F38   → ceiling_device       (천장 홈넷 설비)
그 외         → home_network_device  (매핑되지 않은 Block의 기본값)
```

기기 표시 높이는 고정값이 아니라 배선과 같은 `표시 높이` 파라미터를 기준으로 계산한다. Layer가 `천정`이면 배선과 같은 높이, 그 외에는 배선 높이에서 `DEVICE_WALL_OFFSET_M`(0.9m)을 뺀 높이를 사용한다. 사용자가 배선 표시 높이를 조정하면 벽부착 기기와 천장 기기가 상대 높이를 유지한 채 함께 움직인다. 실제 시공 높이가 아니라 Viewer 표시용 가정값이다.
