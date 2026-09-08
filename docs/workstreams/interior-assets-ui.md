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
- AR 배치와 사진 속 사물 지우기는 서로 다른 화면으로 분리하지 않고 같은 카메라 작업 화면에 둔다.
- UI는 `MagazineFeedProvider`만 참조한다. 현재는 `MockMagazineFeedProvider`, 이후 Web 또는 Miso Provider로 교체한다.
- `AppSettings`가 서버 주소 정규화와 `SharedPreferences` 저장을 단독 담당한다.

## Asset

- `experiments/shinym87/interior/app/src/main/assets/interior_asset_BG.png`: 화보와 가구 hotspot 시연에 사용하는 원본 atlas
- `experiments/shinym87/interior/design/interior_asset1.png`: 차콜·베이지 visual system 참고 자료
- `AtlasCropView`가 원본을 재압축하지 않고 화보 영역을 표시하며 객체 상대 좌표를 화면 좌표로 변환한다.
- 화보는 위아래 빈 여백 없이 화면을 꽉 채우는 잘라 채우기(cover) 방식으로 그린다.
- `assets/magazine/<page.id>.jpg|png|webp` 파일이 있으면 atlas crop 대신 그 세로 사진을 그대로 사용한다. 실제 화보 사진이 준비되면 코드 변경 없이 교체된다.

## Verification

- Android debug APK build 성공
- Android Lint 통과(오류 0건)
- Depth Core synthetic test 14개 통과
- `SM-S908N` 실기기에서 화보 첫 화면 확인: 이름 버튼 없이 점 marker만 보인다.
- 실기기에서 점을 눌렀을 때 `라운지 체어 · AR로 보기 ›` 태그가 뜨는 것을 확인했다.
- 실기기에서 태그를 눌러 AR 작업 화면으로 이동하고, 선택한 가구 이름과 크기가 전달되는 것을 확인했다.
- 같은 AR 화면에 배치 도구와 `지울 사물`, `영역 선택 모드`, `삭제 요청`이 함께 있는 것을 확인했다.
- Gradle 실행 시 JDK는 21을 사용한다. Android Studio 기본 JBR(JDK 25)로는 `depth-placement-core` 설정 단계에서 build가 실패한다.

## Known Issues

- 현재 화보와 hotspot 좌표는 Mock 데이터이며 Web/Miso 연동 시 이미지별 객체 좌표를 응답으로 받아야 한다.
- 가장 큰 남은 문제는 사진 자체이다. 지금 쓰는 `interior_asset_BG.png`는 잡지 사진이 아니라 로고, 아이콘, 작은 홍보 배너가 모여 있는 UI Kit 이미지이다. 이 안의 작은 조각을 확대해 화보처럼 쓰고 있어 실제 잡지 느낌이 나지 않는다.
- 화보용 세로 사진(권장 비율 9:16 또는 3:4, 긴 변 1600px 이상)이 화보 수만큼 필요하다. 사진 안에 로고나 버튼 같은 UI 요소가 들어가면 안 된다.
- 이번 작업 환경에서는 이미지 생성 도구를 쓸 수 없어 사진을 새로 만들지 못했다. Codex의 `imagegen`은 Codex 전용 도구이고, CLI 대체 경로에 필요한 `OPENAI_API_KEY`도 이 환경에 없다.
- AR 작업 화면의 기존 기능 버튼은 후속 visual polish 대상이다.

## Next

1. 화보용 세로 사진을 확보한다. `imagegen`을 쓸 수 있는 환경에서 생성하거나 sjkim0637에게 실제 사진을 받는다.
2. 사진을 `assets/magazine/<page.id>.jpg`로 넣고 각 가구의 상대 좌표를 사진에 맞게 다시 잡는다.
3. Web/Miso Provider의 이미지 URL, 객체 좌표, 3D 자산 ID 계약을 확정한다.
4. 통합 AR 도구를 아이콘 기반 하단 도구 모음으로 정돈한다.

## Relevant Commits

- `bb038c4` — 제공 Asset 기반 초기 제품 홈과 설정 분리
- 다음 UI commit — 화보 기반 객체 직접 선택과 통합 AR 작업 화면

## Updated

2026-09-08
