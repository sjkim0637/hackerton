# Interior 삭제 결과 전체화면 Fallback 오류 보고

## 보고 정보

- Reporter: goguma-salad + Codex
- Observed Branch: `agent/goguma-salad/interior-ui-navigation`
- Related Asset Branch: `agent/goguma-salad/interior-assets`
- Target Workstream: `agent/goguma-salad/interior-mobilesam`
- Severity: High — 삭제 완료 뒤 AR이 멈춘 것으로 오인되어 핵심 흐름을 계속 사용할 수 없다.
- Date: 2026-09-07

## 증상

사물 삭제가 끝난 뒤 실시간 카메라 화면으로 돌아오지 않고 삭제 결과 사진 한 장에서 화면이 멈춘 것처럼
보인다. 실제 AR Session이 중지된 것은 아니지만 정적 이미지가 화면 전체를 가려 사용자는 가구 배치나
공간 이동을 계속할 수 없다고 느낀다.

## 재현 조건

1. AR 화면에서 `영역 선택 모드`로 사물을 선택한다.
2. 사물 종류를 고르고 `삭제 요청`을 실행한다.
3. 서버 Job이 완료될 때까지 기다린다.
4. 선택 중심에서 유효한 ARCore Plane Anchor를 얻지 못한 경우 전체화면 정적 결과가 계속 표시된다.

## 코드 기준 원인

- `RemovalController.resolveWall()`은 선택 영역 중심 `hitTest` 결과가 있을 때만 `wallAnchor`를 만든다.
- `RemovalController.applyResult()`은 `wallAnchor`가 있으면 선택 영역 Crop을 `ImageNode`로 공간에 붙인다.
- Anchor가 없으면 서버가 반환한 전체 Bitmap을 `resultOverlay`에 넣고 `VISIBLE`로 만든다.
- 이 Overlay는 사용자가 `삭제 전/후`를 누르거나 선택을 지울 때까지 유지된다.

따라서 MobileSAM이 정밀한 Mask를 정상 생성해도 Plane Anchor 실패 시 동일한 전체화면 fallback이 발생한다.
세그멘테이션 결과와 화면 복귀 정책이 결합된 앱 측 UX 오류다.

## 평면 인식 관련 판단

삭제 API 자체는 2D Point·Mask·BBox와 캡처 이미지가 있으면 동작하므로 초기 Plane 인식이 필수는 아니다.
현재 앱은 가구 배치, 실측 크기 추정, 삭제 Patch 공간 고정, 이동 사물 재배치를 한 AR 화면에 묶었기 때문에
AR 화면 생성 시 `HORIZONTAL_AND_VERTICAL` Plane 탐색을 시작한다.

Plane 탐색은 비동기 준비 시간을 줄이기 위해 백그라운드에서 유지할 수 있다. 다만 초기부터 격자를 보여주거나
Plane을 얻지 못했다는 이유로 정적 전체화면을 유지하는 동작은 분리해야 한다.

## 요청 변경

1. 삭제 완료 후 라이브 카메라 화면을 기본 상태로 즉시 복원한다.
2. Anchor가 있으면 삭제된 영역 Patch만 AR 공간에 고정한다.
3. Anchor가 없으면 전체화면 Overlay 대신 닫을 수 있는 작은 전후 비교 Preview를 표시한다.
4. Preview를 닫거나 일정 시간이 지나면 라이브 카메라로 자동 복귀한다.
5. 가구 배치 Mode에 들어갈 때만 Plane 격자와 스캔 안내를 명시적으로 표시한다.
6. MobileSAM Mask 생성 성공 여부와 AR 결과 표시 방식을 독립 상태로 관리한다.

## 완료 조건

- Anchor 유무와 관계없이 Job 완료 후 실시간 카메라가 계속 보인다.
- 전체 화면을 가리는 정적 Bitmap이 무기한 남지 않는다.
- 사용자는 삭제 결과를 전후 비교 Preview로 다시 열 수 있다.
- Plane이 아직 없으면 가구 이동·배치 기능만 대기 상태가 되고 삭제 결과 확인은 가능하다.
- BBox 방식과 MobileSAM Point/Mask 방식 모두 같은 복귀 정책을 사용한다.

## 제외 범위

- 매 Camera Frame을 서버로 보내는 실시간 Video Inpainting
- Plane 또는 Depth 없이 삭제 Patch를 실제 공간에 영구 정합하는 기능
- MobileSAM Model 추론 정확도 개선
