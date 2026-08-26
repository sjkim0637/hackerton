# GEO TIME AR — 국가기준점 기반 POI와 자동 AR 정렬

## 현재 방향

QR, Visual Marker 또는 사용자가 화면 중앙에 랜드마크를 맞추는 수동 Calibration은 사용하지 않는다.

POI 절대좌표는 국토지리정보원의 국가기준점 성과를 기준으로 생성한다. Viewer는 Phone GPS와 Magnetic Compass로 현재 ARCore Session을 현실 좌표에 자동 정렬하고, 이후 이동과 회전은 ARCore 6DoF Tracking만 사용한다.

```text
국가기준점 #1·#2
→ 측량된 POI 절대좌표
→ Backend POI 위·경도·선택 표고

Phone GPS + True North Heading + ARCore Camera Pose
→ Session 시작 시 Geo→AR Transform 1회 생성
→ POI ENU 상대좌표를 ARCore +X East, +Y Up, -Z North로 변환
→ 이후 ARCore Local 6DoF Tracking 유지
```

## 국가기준점 데이터

국가기준점 성과는 비공개 자료가 아니다. 국토지리정보원 국토정보플랫폼과 공공데이터포털에서 공개한다.

- 통합기준점: 위·경도, 평면좌표, 타원체고, 표고, 지오이드고를 사용할 수 있다.
- 삼각점: 수평 위치 기준으로 사용할 수 있다.
- 수준점: 정밀한 표고 기준으로 사용할 수 있다.

국토정보플랫폼 기준점 검색 Open API 연동은 후속 작업으로 둔다. 연동할 때에도 Android가 정부 API를 직접 호출하지 않고 Backend가 주기적으로 성과를 동기화한 뒤 PostGIS에서 현재 위치와 가까운 기준점을 선택한다.

현재 Prototype은 확인된 사용 가능 좌표 두 점만 `기준좌표 1`, `기준좌표 2`라는 내부 이름으로 저장한다. 실제 국가기준점 명칭과 원본 성과표 파일은 저장소에 포함하지 않는다.

Tower 107 Seed 좌표에서 가까운 사용 가능 기준점은 다음과 같다.

```text
기준좌표 1  약 0.88km
기준좌표 2  약 1.86km
```

Backend는 두 좌표를 Seed하고 거리순으로 반환한다. 기준좌표만으로 기존 Tower 107 POI 좌표를 다시 산출할 수는 없으므로, 두 좌표와 POI 사이의 실제 측량 관측값을 확보하기 전까지 POI는 기존 Seed 좌표를 유지한다.

## 현재 구현

Backend는 다음 POI 절대좌표를 제공한다.

```json
{
  "id": "poi-id",
  "latitude": 37.5648801960179,
  "longitude": 126.991228638001,
  "ellipsoid_height_m": null,
  "orthometric_height_m": null
}
```

Android 자동 정렬 순서는 다음과 같다.

1. Android가 제공하는 최근 GPS Snapshot을 Session 위치 기준으로 사용한다.
2. GPS 좌표와 시각으로 `GeomagneticField`의 Magnetic Declination을 구한다.
3. Rotation Vector Sensor의 최근 1초 Heading을 Circular Mean으로 평균한다.
4. Magnetic Heading에 Declination을 적용해 True North Heading을 구한다.
5. ARCore Camera Position·Yaw와 True Heading으로 고정 `GeoArAlignment`를 만든다.
6. WGS84 POI 좌표를 Phone 기준 ENU 미터 좌표로 변환한다.
7. POI ENU와 `SpatialPlacement.local_x/y/z`를 ARCore Session 좌표로 변환한다.
8. 정렬 후에는 GPS·Compass 갱신으로 Marker를 움직이지 않고 ARCore 6DoF만 사용한다.

좌표 방향은 다음으로 통일한다.

```text
+X = East
+Y = Up
-Z = North
```

## Y 처리

수평 좌표와 Y를 분리한다.

- `ellipsoid_height_m`: Android GNSS `Location.altitude`와 동일한 높이 기준일 때만 자동 상대 높이 계산에 사용한다.
- `orthometric_height_m`: 국가 표고와 수준점 기반 지면 높이로 보존한다.
- `SpatialPlacement.local_y`: POI 지면 또는 배치 기준면에서 콘텐츠를 띄울 상대 높이다.

두 높이 기준을 임의로 섞지 않는다. POI와 Phone 양쪽에 타원체고가 있을 때만 차이를 AR Y에 반영하고, 값이 없으면 `local_y`만 적용한다. 현장에서는 GNSS 고도 Noise, Camera 높이, ARCore Plane과 실제 지면 차이를 추가 검증해야 한다.

## 정확도와 Reset

- GPS Accuracy가 20m를 넘으면 경고색으로 표시하지만 자동 정렬을 차단하지 않는다.
- Heading Sensor 정확도가 15도를 넘거나 불명확하면 경고색으로 표시한다.
- Spot 변경, Demo 전환, 사용자의 현재 장소 재조회 때 Transform을 다시 만든다.
- 짧은 Tracking Lost나 GPS 갱신은 이미 고정된 Transform을 변경하지 않는다.
- 위치 좌표와 Sensor 표본은 앱 설정이나 DB에 저장하지 않는다.

## 검증 상태

- [x] POI 절대 위·경도 API
- [x] POI 타원체고·표고 Schema와 Migration
- [x] 사용 가능한 두 점을 익명 내부 이름으로 Backend Seed
- [x] 현재 위치 기준 사용 가능한 가까운 기준점 두 점 PostGIS 조회 API
- [x] WGS84 ECEF→ENU 변환
- [x] Magnetic Declination 기반 True North 변환
- [x] 최근 1초 Heading Circular Mean
- [x] GPS·Heading·ARCore Camera Pose 기반 Session Transform
- [x] `+X East, +Y Up, -Z North` 변환 Test
- [x] 정렬 후 GPS·Compass를 Marker에 계속 적용하지 않는 구조
- [x] GPS Accuracy·Heading·Yaw Offset 진단 표시
- [ ] 국토정보플랫폼 Open API 인증키 연결과 주기적 성과 동기화
- [ ] Tower 107의 실제 국가기준점 기반 POI 좌표·표고 입력
- [ ] Tower 107 현장 수평·수직 오차 측정
- [ ] 여러 기기와 다른 AR Session 비교

## 남은 한계

국가기준점은 POI 좌표 정확도를 높이지만 Phone GPS와 Magnetic Compass의 측정 오차를 제거하지는 않는다. 현재 방식은 별도 현장 Marker 없이 자동 실행되는 대신 최종 수평 오차가 Phone GPS와 Heading 품질에 좌우된다. 현장 검증 결과가 제품 허용 범위를 벗어날 때만 RTK, Visual Landmark 또는 다른 절대 위치 보강 계층을 검토한다.
