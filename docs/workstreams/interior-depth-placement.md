# Workstream

## Topic

모바일 온디바이스 실시간 IR Depth Point Cloud 기반 AR 배치 모듈

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/interior-depth-placement`

## Project Path

`experiments/goguma-salad/interior-depth-placement/`

## Status

REVIEW

## Goal

ARCore Depth 입력을 실시간 Point Cloud로 변환하고, 표면 기울기와 객체 footprint 내 장애물을 검사해 AR 객체를 놓을 수 있는 Pose를 반환하는 독립 Gradle 모듈을 만든다. 모든 처리는 기기에서 수행하며 테스트 앱도 네트워크를 사용하지 않는다.

## Current Direction

- `depth-placement-core`: Android UI 및 ARCore에 의존하지 않는 계산과 Public API
- `depth-placement-arcore`: ARCore `Frame`, Depth image, intrinsics, pose 변환
- `depth-placement-debug`: 제품 앱에서 제외 가능한 OpenGL Point Cloud viewer
- `test-app`: 상태 Dashboard, Point Cloud Test, Settings를 제공하는 serverless 검증 앱
- 기존 `experiments/shinym87/interior/` 앱과 소스는 공유하지 않고 Gradle/SDK 호환 버전만 맞춘다.

## Scope

- Depth16 sampling, confidence/range filtering, temporal smoothing, point count 제한
- camera-to-world 좌표 변환, ROI plane fitting, normal/slope/surface 분류
- object footprint의 surface coverage와 obstacle clearance 검사
- ARCore depth adapter, 회전·확대·Freeze 가능한 3D viewer
- 로컬 설정 저장/초기화 및 rolling performance metrics
- synthetic unit test와 debug APK build

## Known Issues

- Depth FPS, 실제 해상도와 기기별 placement 품질은 ARCore Raw Depth 지원 실기기에서 추가 측정이 필요하다.
- RGB 색상 결합과 RANSAC은 Public Config에 예약되어 있으나 초기 구현은 depth 기반 색상과 PCA plane fitting을 사용한다.
- portrait 고정 테스트 앱만 빌드 검증했으며 화면 회전별 View-to-Depth 좌표 검증은 남아 있다.

## Verification

- `depth-placement-core` synthetic unit test 4개 통과: 평면 normal, 20° 경사, 바닥 배치, 장애물 검출
- `depth-placement-arcore` release AAR build 성공
- `depth-placement-debug` release AAR build 성공
- `test-app` debug APK build 성공
- `test-app:lintDebug` Android Lint 성공(오류 0건)
- 앱 Manifest에 `INTERNET` permission이 없음을 확인
- `SM-S908N` 설치 및 ARCore camera/IMU/VIO tracking 초기화 로그 확인. 잠금 화면 상태여서 시각 결과 측정은 대기 중.
- 정확도 판별을 위해 테스트 앱 기본 화면을 분리된 3D viewer에서 RGB camera 위 Depth pixel 직접 투영 방식으로 변경했다.

## Next

1. ARCore Raw Depth 지원 실기기에서 APK를 설치해 Depth FPS, 해상도, point 수와 배치 품질 측정
2. 기기별 sensitivity 기본값 튜닝
3. 기존 메인 AR 앱에 module dependency로 연결해 import 검증

## Relevant Commits

- `5d73c09` — 온디바이스 Point Cloud 배치 모듈, ARCore adapter, debug viewer와 테스트 앱 구현
- `dac675a` — VS Code에서 검증·빌드·설치·실행할 수 있는 task 일괄 등록
- `be6a46b` — dense Depth, 높이 컬러 Point Cloud, 하단 실화면과 쉬운 설정 UI 적용
- `67a5d85` — RGB 실화면 위에 Depth Point를 직접 투영하는 정확도 검증 UI 적용

## Updated

2026-09-07
