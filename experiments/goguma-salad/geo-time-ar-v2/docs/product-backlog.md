# Geo-Time AR 제품 TODO

이 문서는 Viewer Demo 이후 제품 형태로 발전시키기 위해 남은 일을 우선순위대로 정리한다. 완료 여부는 Checkbox로 관리하고, 중요한 방향 변경은 `docs/decisions.md`에 별도로 기록한다.

## 현재 완료된 범위

- [x] GPS 기반 주변 GeoZone 조회
- [x] 임의 기본 좌표 없이 Android가 제공하는 최근 GPS 기록 사용
- [x] 내장 GNSS의 L1/L5·ADR·Reset·Cycle Slip 실시간 진단 화면
- [x] 진단 화면을 연 동안만 GPS 측정을 활성화하고 종료 시 즉시 해제
- [x] ADR 연속성 확인을 위해 진단 중에만 GNSS Full Tracking 사용
- [x] POI별 Moment Stack Marker 표시
- [x] Phone Touch 기반 미리보기와 콘텐츠 집중 재생
- [x] Glass UX Simulation과 6DoF Preview → 3DoF형 정면 Screen 전환
- [x] 동작별 Coach Mark와 안내 표시 설정
- [x] Local FastAPI, PostGIS, MinIO 개발 환경
- [x] Android Build·Binding·설치 Task
- [x] Cyberpunk 시작 화면과 Phone·Glass Demo·Creator 진입 Flow
- [x] Loading·Empty·Offline·Permission·Server 오류 상태 화면
- [x] Viewer 화면의 개발·진단 Button을 설정으로 이동
- [x] 시스템 뒤로가기의 Viewer·Creator·콘텐츠 단계별 내부 복귀 처리
- [x] Phone·Glass 영상 자연 종료 후 AR Marker 화면 자동 복귀
- [x] 조준을 방해하는 Preview Frame 장식 제거
- [x] Glass 전체 재생 중 좌우 Roll 15도 기울임으로 AR 복귀
- [x] Phone·Glass 공통 대형 Compass Tape와 좌하단 소형 원형 Artificial Horizon HUD
- [x] 을지로 타워 107 Backend 좌표를 조회하는 기본 Demo, 연결 실패 시 Local Fallback과 Roll 영점 보정

## P0 — 화면 구조와 디자인

- [x] 앱 실행 후 바로 AR로 들어가지 않는 시작 화면 구현
- [x] 시작 화면에서 `Phone Viewer`, `Glass Demo`, `Creator` 진입 분기
- [x] 전체 화면 Flow와 Navigation 확정
- [x] 화면별 Cyberpunk UI 지시서와 이미지 생성 Prompt 작성
- [x] Native UI에 사용할 Cyberpunk 이미지 Asset 생성 Prompt 작성
- [x] 전달받은 디자인 Asset을 Android UI에 반영
- [x] Loading, Empty, Offline, Permission 거절, Server 오류 화면 디자인

필요한 주요 디자인 화면:

1. 시작 화면
2. Phone AR 탐색
3. Glass AR 탐색
4. Moment Preview
5. 재생 확인
6. 콘텐츠 집중 화면
7. Creator 촬영·선택
8. AR 공간 배치
9. Upload 진행·완료
10. 설정과 Server 연결 상태

## P0 — 설정

- [x] API Server 주소를 Build 고정값에서 앱 설정값으로 전환
- [x] Media Server 주소와 공개 Asset 주소 설정 및 Backend 공개 URL의 Origin 재작성
- [x] API·Media Server 연결 Test와 DNS·Timeout·HTTP·주소 오류 원인 표시
- [x] Demo·USB·운영 Server Profile 전환과 USB Reverse 의존성 표시
- [x] 마지막으로 사용한 Phone Viewer·Glass Demo Mode 기억
- [x] 동작별 Coach Mark 표시 On/Off
- [x] Viewer의 Mode 전환·Demo Preview·재조회·GNSS 진단 도구를 설정으로 이동
- [x] Preview 음소거와 자동 재생 설정
- [x] Camera·위치·마이크 권한 상태와 설정 바로가기
- [x] App·Backend Version 표시

## P0 — POI와 공간 위치 재현

### 현재 동작

```text
Android GPS
  → 주변 GeoZone 검색
  → GeoZone의 POI 절대좌표와 Timeline 조회
  → POI별 Moment 묶음
  → POI WGS84를 Phone 기준 ENU로 변환
  → True North와 ARCore Camera Yaw로 Session Transform 생성
  → POI 상대 SpatialPlacement를 ARCore Session 좌표에 배치
```

- GPS는 현재 사용자가 어느 `GeoZone` 주변에 있는지 찾는 데 사용한다.
- Android가 제공하는 최근 위치 기록만 사용한다. 위치 좌표를 앱 설정이나 Database에 별도로 저장하지 않으며, Android에서 기록을 받지 못하면 임의의 기본 좌표로 조회하지 않는다.
- `POI.location`의 WGS84 위·경도와 `SpatialPlacement.local_x/y/z`를 결합한다.
- Rotation Vector Sensor는 Magnetic Declination을 적용해 True North로 변환하고 최근 1초 표본을 평균한다.
- GPS·Heading·ARCore Camera Pose가 준비되면 Session Transform을 한 번 만들고 이후 ARCore 6DoF만 사용한다.
- POI 절대좌표는 검증된 두 기준좌표를 이용해 생성하며 현재 Tower 107은 Seed 좌표로 변환 계층을 검증한다.

### 남은 작업

- [x] GeoZone POI 절대 위·경도·선택 표고 조회 API 구현
- [x] Phone GPS와 POI WGS84를 ENU 동·북 좌표로 변환
- [x] Magnetic Declination·True North와 ARCore Camera Pose로 Session Transform 생성
- [x] 정렬 후 GPS·Compass 갱신 대신 ARCore 6DoF Tracking 유지
- [x] 사용 가능한 두 점을 `기준좌표 1`, `기준좌표 2`로 Backend Seed
- [x] 현재 위치에서 가까운 사용 가능 기준좌표 두 점 PostGIS 선택
- [x] GPS 오차가 크면 경고하고 Session Transform 생성 후 GPS를 계속 적용하지 않는 정책 결정
- [ ] Tower 107 POI 좌표와 표고를 실제 기준좌표 측량 성과로 교체
- [ ] 여러 기기에서 동일 POI Marker 위치 현장 Test

### RTK와 6DoF 결합 후보 — SKIP

현재 대상 기기의 내장 ADR 미제공으로 RTK 경로는 P0·P1 범위에서 제외한다. 외장 수신기나 ADR 제공 기기가 제품 전제로 확정될 때만 별도 Workstream으로 재개한다.

- 완료 기록: Phone 내장 GNSS의 L1/L5·ADR와 Cycle Slip 진단
- 완료 기록: `SM-S908N` 야외 진단 결과 내장 ADR 미제공 확인
- SKIP: 외장 RTK 연결, NTRIP, RTK Fix 관리와 ARCore 결합
- SKIP: Antenna·Camera Offset과 RTK 음영 전환 검증

POI 기반 Creator는 두 기준좌표로 산출한 POI 절대좌표와 자동 Session Transform을 기준으로 한다. QR 또는 수동 Visual Anchor는 기본 흐름에 포함하지 않는다. 수평 오차가 제품 허용 범위를 넘을 때만 별도 보강 계층을 검토한다.

## P1 진입 Gate — Backend와 Media Pipeline

Creator 구현 전에 POI·Moment Metadata와 영상 원본·변환본을 어디에서 받고 저장할지 먼저 결정한다. API, POI Database, Media Object Storage를 논리적으로 분리하되 실제 운영 Server를 물리적으로 나눌지는 이 Gate에서 검토한다.

- [ ] Android가 영상을 Backend 경유로 보낼지 서명 URL로 Object Storage에 직접 Upload할지 결정
- [ ] POI·Moment API와 Media 공개 URL의 책임 경계 결정
- [ ] 단일 배포와 API·Worker·Object Storage 분리 배포 비교
- [ ] Upload 등록 → 영상 전송 → 검증·변환 → Moment 발행 상태 흐름 확정
- [ ] HTTPS API Domain과 운영 환경 배포
- [ ] PostgreSQL/PostGIS 운영 DB
- [ ] S3 호환 Object Storage와 CDN
- [ ] 사용자 로그인, Token과 Creator 소유권
- [ ] Thumbnail 생성, Metadata 추출과 영상 변환 Worker
- [ ] Backup, Log, Monitoring과 장애 알림

## P1 — Creator Mode

- [ ] Camera 촬영과 Gallery 영상 선택
- [ ] 영상 길이 제한과 간단한 Trim
- [ ] 제목·설명·기록 시각 입력
- [ ] 현재 위치의 기존 POI 선택
- [ ] 필요한 경우 새 POI 생성 요청
- [ ] Camera AR 화면에서 Marker 위치·높이·크기 배치
- [ ] 공개·비공개 또는 공유 범위 설정
- [ ] Upload 진행률, 취소, 실패 재시도
- [ ] Network 단절 시 임시저장과 이어 올리기
- [ ] Upload 화질과 Wi-Fi 전용 Upload 설정
- [ ] Creator가 배치한 Anchor를 다음 Session에서 복원
- [ ] Upload 완료 직후 현재 공간에서 Moment 확인
- [ ] 내가 만든 Moment 목록·수정·삭제

## P1 — 영상 반응 속도

- [ ] 미리보기 전에 최신 Moment 영상 Preload
- [ ] Preview → 집중 재생 전환 시 Player와 Buffer 재사용
- [ ] 이전·다음 Moment 사전 준비
- [ ] Local Media Cache와 만료 정책
- [ ] Media Cache 용량 확인과 삭제
- [ ] 첫 Frame, Loading과 재생 실패 UI
- [ ] 외부 Test MP4를 자체 Demo 영상으로 교체

## P2 — 실제 Glass Runtime

- [ ] Target Glass Hardware 확정
- [ ] 기기 특성에 따른 Glass 화면 자동 진입
- [ ] Standalone/Compute-pack 방식과 Phone Tethered 방식 결정
- [ ] OpenXR 또는 Vendor SDK 검증
- [ ] Glass 전용 App Module 또는 Build Variant 분리
- [ ] Glass Camera·IMU·6DoF Tracking 연결
- [ ] Voice와 Vendor 입력 기반 재생·종료 명령
- [ ] 실제 Head Gesture 오인식과 피로도 Test
- [ ] 발열, 배터리와 장시간 실행 안정성 Test
- [ ] Phone 설정·계정·Media와 Glass 동기화

## P2 — 외부 좌표 Provider

- [ ] 외부 좌표 Open API 인증키 연결과 주기적 전체 성과 동기화

## P2 — 운영과 안전

- [ ] 촬영 대상과 위치 정보에 대한 개인정보 안내
- [ ] 부적절한 콘텐츠 신고·차단·숨김
- [ ] 공개 장소 Upload 정책과 관리자 검토
- [ ] 사용자별 저장 용량과 영상 길이 제한
- [ ] 동일 POI에 Moment가 많을 때 정렬·대표 Marker 정책
- [ ] Organic Moment와 Commercial Campaign 표시 구분
- [ ] 접근성, 글자 크기, 햅틱과 음성 설정

## 권장 진행 순서

1. 시작 화면과 전체 화면 Flow 확정
2. UI 지시서 작성과 디자인 Asset 제작
3. Server 설정과 연결 Test 구현
4. POI·Session Calibration 최소 방식 검증
5. Backend·Media Pipeline 책임과 배포 구조 결정
6. 외부 Backend와 Object Storage 최소 운영 환경 구성
7. 실제 영상 Preload와 Cache 적용
8. Creator 촬영·배치·Upload MVP
9. 실제 Glass Hardware 확정과 별도 Runtime 개발
