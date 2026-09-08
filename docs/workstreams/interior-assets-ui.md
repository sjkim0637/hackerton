# Workstream

## Topic

제공 Asset 기반 Interior 메인 화면과 기능 분기

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/interior-assets-ui`

## Project Path

`experiments/shinym87/interior/`

## Status

REVIEW

## Goal

기존 AR 실험 화면을 바로 노출하지 않고 제품 메인 화면에서 AR 가구 배치, 사물 지우기, 카탈로그로 진입하게 한다. 서버 주소 같은 기술 설정은 작업 화면에서 제거하고 별도 설정 화면과 단일 저장소에서 관리한다.

## Asset

- `experiments/shinym87/interior/app/src/main/assets/interior_asset_BG.png`: 인테리어 Hero와 카테고리 사진 atlas
- `experiments/shinym87/interior/design/interior_asset1.png`: 차콜·베이지 색상, 둥근 카드, 버튼과 navigation 디자인 기준
- 원본 bitmap을 다시 저장하거나 압축하지 않고 `AtlasCropView`가 필요한 영역만 crop 렌더링한다.

## Current Direction

- `MY HOME INTERIOR` 제품 홈과 따뜻한 베이지·차콜 visual system 적용
- `AR로 배치하기`, `사진에서 사물 지우기`, `가구 카탈로그` 기능 분기
- 기능 화면에서 홈/설정으로 돌아오는 navigation 제공
- `AppSettings`가 서버 주소 정규화와 `SharedPreferences` 저장을 단독 담당
- 기존 AR/Depth/제거/카탈로그 Controller는 화면 분기 뒤에도 재사용

## Verification

- Asset 포함 Android debug APK build 성공
- Depth Core synthetic test 14개 통과
- Android Lint 통과(오류 0건)
- APK에 런타임 atlas `assets/interior_asset_BG.png` 포함 확인
- 최종 APK 52,244,162 bytes
- 실기기 USB가 연결 해제되어 화면 비율과 navigation 확인은 보류

## Known Issues

- 제공 이미지는 여러 요소가 포함된 atlas이므로 기기 화면비에 따라 crop 중심을 조정할 수 있다.
- AR Session은 같은 Activity 아래에서 준비되므로 홈 표시 중에도 카메라 초기화가 진행된다.

## Next

1. `SM-S908N`에 설치해 홈 Hero와 버튼 잘림 확인
2. 세 기능 분기와 서버 주소 저장/복원 확인
3. 실기기 결과에 따라 atlas crop과 세로 간격 조정

## Relevant Commits

- `bb038c4` — 제공 Asset 기반 제품 홈, 기능 분기, 설정 저장소와 서버 주소 화면 분리

## Updated

2026-09-08
