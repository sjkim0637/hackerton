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

## P0 — 화면 구조와 디자인

- [ ] 앱 실행 후 바로 AR로 들어가지 않는 시작 화면 구현
- [ ] 시작 화면에서 `Phone Viewer`, `Glass Demo`, `Creator` 진입 분기
- [ ] 실제 Glass Runtime에서는 기기 특성에 따라 Glass 화면 자동 진입
- [ ] 전체 화면 Flow와 Navigation 확정
- [x] 화면별 Cyberpunk UI 지시서와 이미지 생성 Prompt 작성
- [x] Native UI에 사용할 Cyberpunk 이미지 Asset 생성 Prompt 작성
- [ ] 전달받은 디자인 Asset을 Android UI에 반영
- [ ] Loading, Empty, Offline, Permission 거절, Server 오류 화면 디자인

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

- [ ] API Server 주소를 Build 고정값에서 앱 설정값으로 전환
- [ ] Media Server 주소와 공개 Asset 주소 설정
- [ ] Server 연결 Test와 결과 표시
- [ ] Demo·개발·운영 Server Profile 전환
- [ ] 기본 시작 Mode 또는 마지막 Mode 기억
- [x] 동작별 Coach Mark 표시 On/Off
- [ ] Preview 음소거와 자동 재생 설정
- [ ] Upload 화질과 Wi-Fi 전용 Upload 설정
- [ ] Media Cache 용량 확인과 삭제
- [ ] Camera·위치·마이크 권한 상태와 설정 바로가기
- [ ] App·Backend Version 표시

## P0 — POI와 공간 위치 재현

### 현재 동작

```text
Android GPS
  → 주변 GeoZone 검색
  → GeoZone의 Timeline과 POI ID 조회
  → POI별 Moment 묶음
  → SpatialPlacement.local_x/y/z를 ARCore Session 좌표에 그대로 배치
```

- GPS는 현재 사용자가 어느 `GeoZone` 주변에 있는지 찾는 데 사용한다.
- Android가 제공하는 최근 위치 기록만 사용한다. 위치 좌표를 앱 설정이나 Database에 별도로 저장하지 않으며, Android에서 기록을 받지 못하면 임의의 기본 좌표로 조회하지 않는다.
- `POI.location`에도 GPS 위·경도가 저장되지만 Android Marker 배치 계산에는 아직 사용하지 않는다.
- Marker 위치는 Seed의 `local_x/y/z`를 사용한다.
- Zone-local 좌표와 ARCore Session 좌표 변환은 현재 Identity로 가정한다.
- 따라서 앱을 다시 실행하거나 다른 기기로 접속했을 때 같은 물리 위치를 정확히 재현한다고 보장할 수 없다.

### 남은 작업

- [ ] 현재 GPS 기준 주변 POI 조회 API 구현
- [ ] GeoZone 중심과 POI GPS를 Zone-local 동·북 좌표로 변환
- [ ] 기기 Heading과 ARCore Session 원점 Calibration
- [ ] QR·Visual Marker, Geospatial Anchor, Cloud Anchor 비교
- [ ] Creator가 배치한 Anchor를 다음 Session에서 복원
- [ ] GPS 오차가 큰 실내·도심 환경의 보정 방식 결정
- [ ] 여러 기기에서 동일 POI Marker 위치 현장 Test

### RTK와 6DoF 결합 후보

- [ ] 외장 RTK GNSS 수신기와 Android 연결 방식(Bluetooth, USB, Vendor SDK) 검증
- [ ] NTRIP 보정정보 공급망과 현장 통신 안정성 검증
- [x] Phone 내장 GNSS의 L1/L5·ADR와 Cycle Slip 진단
- [x] `SM-S908N` 야외 진단 결과 내장 ADR 미제공 확인
- [ ] ADR 제공이 확인된 다른 Phone 또는 Glass 후보 기기 비교
- [ ] RTK Fix 상태, 수평·수직 정확도와 측정 시각을 현재 Session에서 관리
- [ ] RTK 절대 위치와 ARCore의 연속 6DoF Pose를 결합하는 좌표 변환 계층 설계
- [ ] GNSS Antenna와 Camera 사이 Offset, 기기 방향, 높이 Calibration
- [ ] RTK 음영·Fix 해제 시 ARCore·VPS·일반 GNSS로 단계적 전환

RTK는 야외 절대 위치를 정밀하게 잡는 수단이며 Camera 방향, 공간 특징 추적, 실내 위치를 단독으로 해결하지 않는다. 제품 구조는 RTK로 세계 좌표의 기준점을 잡고 ARCore가 근거리의 부드러운 6DoF 움직임을 담당하도록 분리한다.

POI 기반 Creator를 시작하기 전에 최소한 하나의 Calibration 방식을 선택해야 한다. 초기 Demo는 QR 또는 명확한 POI 기준점을 이용한 Calibration이 가장 재현하기 쉽다.

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
- [ ] Upload 완료 직후 현재 공간에서 Moment 확인
- [ ] 내가 만든 Moment 목록·수정·삭제

## P1 — 외부 Backend와 Media Pipeline

다른 사용자와 Moment를 공유하려면 인터넷에서 접근 가능한 Backend와 Object Storage가 필요하다. 현재 Local Docker 환경은 구조 검증용으로 재사용할 수 있다.

- [ ] HTTPS API Domain과 운영 환경 배포
- [ ] PostgreSQL/PostGIS 운영 DB
- [ ] S3 호환 Object Storage와 CDN
- [ ] 사용자 로그인, Token과 Creator 소유권
- [ ] 서명 URL 기반 영상 직접 Upload
- [ ] Content 등록과 Moment 발행을 분리한 안전한 절차
- [ ] Thumbnail 생성과 영상 Metadata 추출
- [ ] 영상 압축·다중 화질 변환 Worker
- [ ] Upload·변환 상태 조회 API
- [ ] 운영 Backup, Log, Monitoring과 장애 알림
- [ ] 향후 DASH 또는 HLS 적용

## P1 — 영상 반응 속도

- [ ] 미리보기 전에 최신 Moment 영상 Preload
- [ ] Preview → 집중 재생 전환 시 Player와 Buffer 재사용
- [ ] 이전·다음 Moment 사전 준비
- [ ] Local Media Cache와 만료 정책
- [ ] 첫 Frame, Loading과 재생 실패 UI
- [ ] 외부 Test MP4를 자체 Demo 영상으로 교체

## P2 — 실제 Glass Runtime

- [ ] Target Glass Hardware 확정
- [ ] Standalone/Compute-pack 방식과 Phone Tethered 방식 결정
- [ ] OpenXR 또는 Vendor SDK 검증
- [ ] Glass 전용 App Module 또는 Build Variant 분리
- [ ] Glass Camera·IMU·6DoF Tracking 연결
- [ ] Voice와 Vendor 입력 기반 재생·종료 명령
- [ ] 실제 Head Gesture 오인식과 피로도 Test
- [ ] 발열, 배터리와 장시간 실행 안정성 Test
- [ ] Phone 설정·계정·Media와 Glass 동기화

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
5. 실제 영상 Preload와 Cache 적용
6. Creator 촬영·배치·Upload MVP
7. 외부 Backend와 Object Storage 배포
8. 실제 Glass Hardware 확정과 별도 Runtime 개발
