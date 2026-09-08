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
- AR 배치와 사진 속 사물 지우기는 서로 다른 화면으로 분리하지 않고 같은 카메라 작업 화면에 둔다.
- UI는 `MagazineFeedProvider`만 참조한다. 현재는 `MockMagazineFeedProvider`, 이후 Web 또는 Miso Provider로 교체한다.
- `AppSettings`가 서버 주소 정규화와 `SharedPreferences` 저장을 단독 담당한다.

## Asset

- `experiments/shinym87/interior/app/src/main/assets/interior_asset_BG.png`: 화보와 가구 hotspot 시연에 사용하는 원본 atlas
- `experiments/shinym87/interior/design/interior_asset1.png`: 차콜·베이지 visual system 참고 자료
- `AtlasCropView`가 원본을 재압축하지 않고 화보 영역을 `fitCenter`로 표시하며 객체 상대 좌표를 화면 좌표로 변환한다.

## Verification

- Android debug APK build 성공
- 화보 Activity와 AR Activity 간 객체 ID, 이름, 크기, 바닥/벽 Anchor 정보 전달 compile 확인
- Depth Core synthetic test 14개 통과
- Android Lint 통과(오류 0건)
- `SM-S908N` USB가 현재 `adb devices`에 나타나지 않아 최신 화면 실기기 검증은 보류

## Known Issues

- 현재 화보와 hotspot 좌표는 Mock 데이터이며 Web/Miso 연동 시 이미지별 객체 좌표를 응답으로 받아야 한다.
- 제공 atlas의 화보 수와 해상도가 제한적이므로 실제 콘텐츠 이미지는 별도로 공급해야 한다.
- AR 작업 화면의 기존 기능 버튼은 후속 visual polish 대상이다.

## Next

1. 기기 재연결 후 세로 넘김, 객체 hotspot 위치, 객체 탭 후 AR 진입을 검증한다.
2. Web/Miso Provider의 이미지 URL, 객체 좌표, 3D 자산 ID 계약을 확정한다.
3. 통합 AR 도구를 아이콘 기반 하단 도구 모음으로 정돈한다.

## Relevant Commits

- `bb038c4` — 제공 Asset 기반 초기 제품 홈과 설정 분리
- 다음 UI commit — 화보 기반 객체 직접 선택과 통합 AR 작업 화면

## Updated

2026-09-08
