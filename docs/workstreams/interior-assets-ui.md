# Workstream

## Topic

인테리어 화보 기반 객체 선택과 통합 AR 작업 화면

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/interior-assets-ui`

## Project Path

`experiments/shinym87/interior/`

## Status

REVIEW

## Goal

세로로 한 장씩 넘기는 인테리어 잡지에서 사진 속 가구를 직접 선택하고, 선택한 객체가 준비된 통합 AR 화면에서 Depth 배치와 사진 속 사물 지우기를 함께 수행한다. 서버 주소 같은 기술 설정은 별도 설정 화면과 단일 저장소에서 관리한다.

## Current Direction

- 첫 화면은 단일 가구 상품 목록이 아니라 인테리어 공간 전체를 보여주는 화보이다.
- 화보를 위아래로 넘겨 다른 공간을 탐색한다.
- 별도의 `배치하기` CTA 없이 사진 속 가구 hotspot을 누르면 해당 객체가 선택된 채 AR 화면으로 이동한다.
- 화보 위에 가구 이름이 적힌 버튼을 상시 노출하지 않는다. 기본 상태에서는 숨쉬듯 퍼지는 작은 점 marker만 보인다.
- 점을 누르면 그 자리에 `가구 이름 · AR로 보기 ›` 태그가 떠오르고, 태그를 누르면 AR 작업 화면으로 이동한다. 화보 여백을 누르면 태그가 닫힌다.
- 화면은 사진을 화면 전체에 까는 방식이 아니라 인쇄된 잡지 한 페이지처럼 구성한다. 제호, 발행 정보, 큰 제목, 사진 판, 사진 설명, `이 화보의 가구` 크레딧, 쪽 번호 순서로 읽힌다.
- 크레딧 목록은 누르는 곳이 아니다. 화보에 어떤 가구가 있고 실제 크기가 얼마인지 알려 주는 읽는 정보이며, 누르는 곳은 사진 속 점 하나뿐이다.
- AR 배치와 사진 속 사물 지우기는 서로 다른 화면으로 분리하지 않고 같은 카메라 작업 화면에 둔다.
- UI는 `MagazineFeedProvider`만 참조한다. 현재는 `MockMagazineFeedProvider`, 이후 Web 또는 Miso Provider로 교체한다.
- `AppSettings`가 서버 주소 정규화와 `SharedPreferences` 저장을 단독 담당한다.

## Asset

- `experiments/shinym87/interior/app/src/main/assets/interior_asset_BG.png`: 화보와 가구 hotspot 시연에 사용하는 원본 atlas
- `experiments/shinym87/interior/design/interior_asset1.png`: 차콜·베이지 visual system 참고 자료
- `AtlasCropView`가 원본을 재압축하지 않고 화보 영역을 표시하며 객체 상대 좌표를 화면 좌표로 변환한다.
- 사진 판의 높이를 사진 비율에 맞춰 정한다. 그래서 사진이 잘리거나 검은 여백이 생기지 않는다.
- atlas에서 고른 세 영역은 로고, 홍보 문구, 화살표 버튼이 들어가지 않는 순수한 사진 부분이다. 상품 배너처럼 보이던 원인을 여기서 제거했다.
- `assets/magazine/<page.id>.jpg|png|webp` 파일이 있으면 atlas crop 대신 그 세로 사진을 그대로 사용한다. 실제 화보 사진이 준비되면 코드 변경 없이 교체된다.

## Verification

- Android debug APK build 성공
- Android Lint 통과(오류 0건)
- Depth Core synthetic test 14개 통과
- `SM-S908N` 실기기에서 첫 화보를 확인했다. 제호, 제목, 사진 판, 크레딧이 잡지 지면처럼 배치되고 이름 버튼 없이 점 marker만 보인다.
- 실기기에서 점을 눌렀을 때 `라운지 체어 · AR로 보기 ›` 태그가 뜨는 것을 확인했다.
- 실기기에서 태그를 눌러 AR 작업 화면으로 이동하고, 선택한 가구 이름과 크기가 전달되는 것을 확인했다.
- 같은 AR 화면에 배치 도구와 `지울 사물`, `영역 선택 모드`, `삭제 요청`이 함께 있는 것을 확인했다.
- 2번, 3번 화보는 기기 잠금 때문에 화면을 찍지 못했다. 대신 atlas crop과 가구 좌표를 PC에서 이미지로 렌더링해 사진 내용과 점 위치가 맞는 것을 확인했다.
- Gradle 실행 시 JDK는 21을 사용한다. Android Studio 기본 JBR(JDK 25)로는 `depth-placement-core` 설정 단계에서 build가 실패한다.

## Depth 배치 판정 조사

실기기에서 "표면을 천천히 비춰 Depth를 더 모아 주세요"가 자주 뜨고 배치가 되지 않는 문제를 조사했다.
합성 Depth로 상황을 재현해 원인을 두 가지로 좁혔다. 화보 화면의 좌표 수정과는 관련이 없다.

| 상황 | 기존 기준 | 완화 후 |
|---|---|---|
| 서서 5m 넘는 바닥을 탭 | INSUFFICIENT_POINTS | 통과 |
| Depth 노이즈 30mm | OBSTACLE_DETECTED | 통과 |
| Depth 노이즈 50mm | OBSTACLE_DETECTED | OBSTACLE_DETECTED |

- 깊이 상한이 5m였다. 카메라 높이 1.4m에서 휴대폰을 수평에 가깝게 들면 화면 중앙 바닥이 5m를 넘어가고, 판정에 쓸 점이 하나도 남지 않는다. 이때 나오는 안내 문구가 실제 원인인 거리를 알려 주지 못했다.
- 평면 허용 오차 2.5cm와 장애물 기준 5cm가 실제 Depth 노이즈보다 좁았다. 빈 바닥의 노이즈를 물체로 세고, 허용치가 표면 점의 1%뿐이라 쉽게 넘어섰다.
- Depth가 거부하면 ARCore가 이미 인식한 평면이 있어도 아무것도 놓이지 않았다.

`DepthPlacementController`가 앱 전용 설정으로 상한 8m, 평면 오차 4.5cm, 장애물 10cm, 최소 신뢰도 0.40을 쓰도록 하고, Depth가 거부해도 평면이 잡히면 그 위에 배치하도록 고쳤다. 공용 `depth-placement-core` 모듈의 기본값은 바꾸지 않았다.

## Known Issues

- 현재 화보와 hotspot 좌표는 Mock 데이터이며 Web/Miso 연동 시 이미지별 객체 좌표를 응답으로 받아야 한다.
- 가장 큰 남은 문제는 사진 자체이다. 지금 쓰는 `interior_asset_BG.png`는 잡지 사진이 아니라 로고, 아이콘, 작은 홍보 배너가 모여 있는 UI Kit 이미지이다. 이 안의 작은 조각을 확대해 화보처럼 쓰고 있어 실제 잡지 느낌이 나지 않는다.
- 화보용 세로 사진(권장 비율 9:16 또는 3:4, 긴 변 1600px 이상)이 화보 수만큼 필요하다. 사진 안에 로고나 버튼 같은 UI 요소가 들어가면 안 된다.
- Depth 배치 완화 설정은 실기기 확인을 하지 못했다. 기기 잠금이 풀린 상태에서 바닥 탭 배치를 확인해야 한다.
- 실기기가 보안 잠금 상태이면 `adb`로 화면을 찍을 수 없다. 화면 확인이 필요하면 기기 잠금을 먼저 풀어야 한다.
- 이번 작업 환경에서는 이미지 생성 도구를 쓸 수 없어 사진을 새로 만들지 못했다. Codex의 `imagegen`은 Codex 전용 도구이고, CLI 대체 경로에 필요한 `OPENAI_API_KEY`도 이 환경에 없다.
- AR 작업 화면의 기존 기능 버튼은 후속 visual polish 대상이다.

## Next

1. 기기 잠금을 푼 상태에서 AR 바닥 탭 배치를 확인한다. 완화 설정으로 배치가 되는지, 평면 대체 배치가 동작하는지 본다.
2. 화보용 세로 사진을 확보한다. `imagegen`을 쓸 수 있는 환경에서 생성하거나 sjkim0637에게 실제 사진을 받는다.
3. 사진을 `assets/magazine/<page.id>.jpg`로 넣고 각 가구의 상대 좌표를 사진에 맞게 다시 잡는다.
4. Web/Miso Provider의 이미지 URL, 객체 좌표, 3D 자산 ID 계약을 확정한다.
5. 통합 AR 도구를 아이콘 기반 하단 도구 모음으로 정돈한다.

## Relevant Commits

- `bb038c4` — 제공 Asset 기반 초기 제품 홈과 설정 분리
- 다음 UI commit — 화보 기반 객체 직접 선택과 통합 AR 작업 화면

## Updated

2026-09-08
