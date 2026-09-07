# Interior Depth 배치 통합 기록

## 결과

`agent/goguma-salad/interior-depth-placement`의 독립 Depth 모듈을 `integration-interior-demo`의 Interior AR 앱에 Gradle project dependency로 연결했다. 기존 카메라·AI 제거·카탈로그·가구 편집 기능은 유지하면서 가구를 처음 놓거나 이동을 끝낼 때 Depth 기반 품질 검사를 추가한다.

## 통합 범위

- Source Branch: `agent/goguma-salad/interior-depth-placement`
- Target Branch: `integration-interior-demo`
- Module: `depth-placement-core`, `depth-placement-arcore`
- Host: `experiments/shinym87/interior/app`
- AR Session: 지원 기기에서는 `AUTOMATIC`, 차선으로 `RAW_DEPTH_ONLY`, 미지원이면 `DISABLED`
- Placement Gate: 표면 종류, 경사, 객체 footprint coverage, 장애물
- UX: 벽/바닥 간 잘못된 fallback 제거, 이동 완료 후 재검증과 실패 시 원위치 복구, 좌우 15° 회전과 현재 각도 표시

## 모듈 경계

Depth 모듈은 Depth frame을 world point와 배치 가능 여부로 바꾸는 계산만 담당한다. SceneView `Anchor`, procedural model 생성, 선택·이동·확대축소·회전·서버 저장은 Host 앱이 담당한다. 따라서 회전 UI나 renderer가 Core에 역으로 의존하지 않는다.

## Fallback

Depth 미지원 기기나 세션 시작 직후 아직 Depth frame이 없는 상태에서는 기존 ARCore plane hit 결과로 배치를 허용한다. Depth 결과가 확보된 뒤에는 부적합한 표면을 거절하고 사용자에게 한국어 원인을 표시한다.

## 검증

- `depth-placement-core` synthetic unit test 14개 통과
- 독립 Depth 프로젝트: Core test, 두 Android library release AAR, test-app debug APK, Android Lint 통과
- Interior Host 프로젝트: Depth module dependency를 포함한 debug APK build와 Android Lint 통과(오류 0건, 기존 품질 경고 85건)

## 남은 실기기 검증

- 안정/균형/디테일 민감도별 실제 바닥·벽 배치 성공률과 오거절률
- 반사면, 무늬 없는 벽, 작은 근거리 물체 경계의 Depth 품질
- 장시간 사용 시 발열·frame 처리시간·배터리
- 화면 회전별 View-to-Depth 좌표 정합
- Depth fallback 상태를 사용자가 구분할 필요가 있는지 UX 확인

## 채택 판단

모듈 경계와 자동 테스트, Android build는 통합 후보 기준을 충족한다. `main` 반영 전에는 Depth 지원 실기기에서 최초 배치, 벽/바닥 거절, 드래그 복구를 한 차례 관통 검증해야 한다.
