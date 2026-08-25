# Architecture Decisions

## ADR-001 — 후보 검색과 가시성 선택 분리

Backend는 `Geo + Time` 후보를 반환하고 Android는 6DoF로 최종 선택한다. 매 Frame마다 서버에 Pose를 전송하지 않아 지연, 네트워크 비용, 개인정보 노출을 줄인다.

## ADR-002 — SpatialPlacement 도입

`rendering_metadata` JSON만으로 핵심 공간 조건을 숨기지 않고 위치, Quaternion, 거리, View Cone을 명시적 Entity로 관리한다.

## ADR-003 — TimeLayer는 Query 모델

Moment와 Campaign 원본을 중복 저장하지 않는다. 필요성이 확인될 때 Materialized View 또는 별도 Projection을 검토한다.

## ADR-004 — PostgreSQL Geography 사용

위·경도 근접 거리를 미터 단위로 직접 처리하기 위해 GeoZone 중심과 POI에 Geography를 사용한다. Polygon은 Geometry로 선택 지원한다.

## ADR-005 — Android의 Zone-local MVP

초기 빌드는 콘텐츠 선택과 Anchor 렌더링을 검증하기 위해 Zone/Session 변환을 Identity로 둔다. 현장 영속 배치 단계에서 Calibration 전략을 별도 추가한다.

## ADR-006 — Time Slider 대신 Reality Rewind Gesture

**Status: Superseded by ADR-007**

일반 UI Slider는 날짜 범위를 선택하는 편집 도구처럼 보여 현실의 시간을 조작한다는 제품 경험을 약화한다. Android MVP는 Camera 화면의 좌우 Swipe를 사용하고, `NOW`와 실제 Moment가 존재하는 시점에만 Snap한다.

오른쪽 Swipe는 과거 시간층을 현재 화면으로 끌어당겨 더 오래된 Moment로 이동하고, 왼쪽 Swipe는 `NOW` 방향으로 이동한다. Snap 시 햅틱과 날짜를 잠깐 표시하고 AR 콘텐츠를 Fade로 전환한다. 이 시간 이동 의도는 향후 AR Glass의 Gesture와 Voice 입력에서도 재사용한다.

Reality Rewind의 방향 개념은 유지하지만, AR 탐색 화면 전체를 Timeline으로 사용하는 방식은 콘텐츠 발견과 영상 감상을 분리하기 위해 ADR-007로 대체한다.

## ADR-007 — 기기별 입력과 공통 콘텐츠 경험 분리

**Status: Accepted**

폰과 Glass가 같은 조작법을 억지로 사용하지 않는다. 두 기기는 `발견 → 미리보기 → 재생 확인 → 전체 감상 → AR 복귀`라는 사용자 의도와 상태 흐름만 공유하고, 입력 방식은 기기 능력에 맞게 교체한다.

폰에서는 장소별 Moment를 하나의 `시간 기록 N개` 마커로 묶는다. 마커를 터치하면 5초 무음 미리보기를 보여주고, 사용자가 화면에서 승인하면 화면 전체를 콘텐츠 집중 영역으로 사용한다. 이때 검은 화면으로 AR 맥락을 끊지 않고 어두워진 Camera 배경 위에 영상 비율을 보존해 크게 표시한다. 콘텐츠 집중 화면에서 좌우 Swipe로 같은 장소의 이전·다음 기록을 이동하며, 날짜는 영상 재생과 기록 전환 때만 잠깐 표시한다. 아래 Swipe나 시스템 뒤로가기를 사용하면 같은 AR 탐색 화면으로 돌아온다.

Glass 데모 모드는 폰의 ARCore Camera Pose를 사용해 Glass Head Pose를 모사한다. 6DoF 추적을 계속 유지하면서 마커 중앙 응시, 끄덕임, 좌우 고개 동작을 같은 상태 전이에 연결한다. 콘텐츠 집중 화면이 3DoF처럼 안정적으로 보이더라도 실제 ARCore 추적을 3DoF로 낮추지는 않는다. Phone 모드에는 Head Gesture를 강제하지 않고 화면의 전환 버튼으로 두 입력 방식을 명확히 분리한다.

Glass 데모의 확인·기록 이동 동작은 일정 각도 이상 움직인 뒤 짧은 시간 안에 기준 자세로 돌아왔을 때 명령으로 확정한다. 확인 화면에서는 Pitch 왕복을 `예`, Yaw 왕복을 `아니오`로 해석하고 전체 감상 중 빠른 Yaw 왕복은 이전·다음 Moment 이동으로 사용한다. 전체 감상 종료는 별도로 재생 시작 자세에서 상하 15도 Pitch 기울임에 도달하는 즉시 AR 복귀로 처리한다.

고개를 좌우로 멀리 돌리는 행동은 사용자가 현실을 둘러보는 정상적인 6DoF 행동이므로 콘텐츠 종료 명령으로 사용하지 않는다. 공간에 고정된 6DoF 미리보기에서 재생을 승인하면 시야 정면을 따라오는 3DoF형 Screen으로 즉시 전환한다. AR Tracking 자체는 6DoF로 계속 동작한다. 현재 Demo는 상하 15도 Pitch 기울임, 영상 종료, 시스템 뒤로가기로 AR에 복귀한다. 실제 Glass에서는 Target Hardware가 정해진 뒤 Voice 또는 Vendor Input과 함께 임곗값을 다시 검증한다.

## ADR-008 — Phone과 실제 Glass 실행 화면 분리

**Status: Accepted for Prototype**

현재 Android 앱의 Glass 모드는 실제 Glass Runtime이 아니라 폰 ARCore Pose로 Head Pose 입력을 모사하는 UX 데모다. ARCore 화면을 네트워크로 Glass에 Streaming하는 구조로 간주하지 않는다.

제품 단계에서는 공통 Backend·Domain·콘텐츠 상태 흐름을 공유하되 표시 화면과 입력 Adapter를 분리한다.

- Phone Runtime: Phone 앱에서 Camera AR, Touch 미리보기와 콘텐츠 집중 화면을 실행한다.
- Standalone/Compute-pack Glass Runtime: Glass 또는 전용 Computing 장치에 Glass 앱을 설치하고 Vendor SDK나 OpenXR의 Camera·IMU·6DoF Tracking과 Display를 직접 사용한다.
- Tethered Display Runtime: Phone이 Rendering Host라면 유선 Display 출력과 Vendor Sensor SDK를 사용한다. 이 경우에도 일반적인 네트워크 영상 Streaming과는 다르다.

[Rokid AR Studio 공식 안내](https://arstudio.rokid.com/)와 [공식 개발자 FAQ](https://forum.rokid.com/post/detail/524)는 AOSP 기반 공간 OS, OpenXR 생태계와 UXR SDK, Camera·IMU 기반 VIO-SLAM 6DoF를 안내한다. 실제 Target 기기가 확정되면 Phone ARCore Adapter를 해당 Hardware SDK Adapter로 교체하고 별도 App Module 또는 Build Variant를 결정한다.

## ADR-009 — RTK는 절대 위치 보정 계층으로 결합

**Status: Proposed**

RTK GNSS를 붙일 수 있지만 ARCore의 6DoF Tracking을 대체하지 않는다. 외장 RTK 수신기와 NTRIP 보정정보로 야외의 세계 좌표 기준을 정밀하게 잡고, Camera·IMU 기반 ARCore는 Frame 사이의 부드러운 이동과 회전, 공간 특징 추적을 담당한다.

결합 계층은 RTK 위치·정확도·Fix 상태·측정 시각을 받아 Zone-local 기준점을 갱신하고 ARCore Session 좌표로 변환한다. GNSS Antenna와 Camera 사이의 물리적 간격, 기기 방향과 높이는 별도 Calibration 대상으로 둔다. RTK Fix가 풀리거나 실내로 이동하면 기존 ARCore Tracking을 유지하고 VPS, 일반 GNSS 또는 현장 Visual Marker를 보조 기준으로 사용한다.

Android는 여러 기기에서 Raw GNSS 측정을 제공하지만 Carrier Phase와 다중 주파수 같은 세부 항목 지원은 Chipset별로 다르다. 1차 검증은 Phone 내장 GNSS의 L1/L5, ADR 유효 상태, Reset과 Cycle Slip을 실시간으로 측정한다. 측정 품질이 충분하면 내장 Carrier Phase와 NTRIP 보정정보를 결합하고, 품질이 부족할 때 외장 RTK 수신기를 대안으로 검토한다. 진단값과 위치 좌표는 현재 Session 화면에만 사용하고 앱이나 서버에 별도로 저장하지 않는다.

2026-08-24 야외 실측에서 `SM-S908N`은 42 Epoch 동안 29개 신호(L1/E1 26개, L5/E5a 3개)를 수신했지만 유효 ADR은 0개였다. Reset과 Cycle Slip도 0개인 것은 안정적이라는 뜻이 아니라 ADR 자체가 제공되지 않았기 때문이다. 따라서 이 기기의 현재 Firmware와 Android API 조합에서는 내장 Carrier Phase RTK 경로를 채택하지 않는다. 외장 RTK 수신기를 연결하거나 ADR을 실제로 제공하는 다른 Target 기기를 선정해야 한다.

- [Android Raw GNSS Measurements 공식 안내](https://developer.android.com/develop/sensors-and-location/sensors/gnss)
- [ARCore Geospatial API 공식 안내](https://developers.google.com/ar/develop/geospatial)
- [ARCore Geospatial Mode 정확도 설명](https://developers.google.com/ar/reference/java/com/google/ar/core/Config.GeospatialMode)

## ADR-010 — Glass 전체 감상 종료를 Roll 15도로 보정

**Status: Accepted**

ADR-007에서 정한 전체 감상 종료의 상하 Pitch 15도 입력은 사용 의도 해석이 잘못된 것으로 확인되어 이 항목만 본 결정으로 대체한다. 전체 감상 중 재생 시작 자세에서 좌우 Roll이 어느 방향이든 15도에 도달하면 즉시 AR 탐색으로 복귀한다. 빠른 Yaw 왕복을 이용한 이전·다음 Moment 이동과 확인 화면의 Pitch·Yaw 왕복 입력은 그대로 유지한다.

HUD는 넓은 Pitch Ladder 대신 좌하단의 작은 원형 Artificial Horizon을 사용한다. 원 내부 지평선은 Pitch에 따라 이동하고 Roll에 따라 회전하며, Glass 전체 감상에서는 원 둘레에 좌우 Roll 15도 종료 지점을 표시한다.
