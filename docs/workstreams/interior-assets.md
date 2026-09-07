# Workstream

## Topic

Interior AR 화면·브랜드 Asset 제작

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/interior-assets`

## Project Path

`experiments/shinym87/interior/assets/`

## Status

IN_PROGRESS

## Goal

현재 구현된 Android AR 기능을 바탕으로 앱의 진입 구조와 브랜드 인상을 정리한다. 메인 화면에서
AR 인테리어, 설명, 설정으로 이동하는 분기 구조에 사용할 이미지 Asset과 앱 아이콘 원본을 만든다.

## Background

현재 앱은 카메라 기반 AR 화면 하나에 서버 주소, 사물 삭제, 가구 추가와 배치 조작이 모두 노출돼
있다. 기능 구현과 별개로 첫 진입 화면과 설명 화면에 일관된 시각 언어가 필요하다.

## Current Direction

- 기존 대표색 `#1660FF`를 유지하되 짙은 남색, 청록, 따뜻한 중성색을 보조색으로 사용한다.
- 앱 UI 텍스트와 버튼은 Android Layout에서 렌더링하고 이미지에는 글자를 넣지 않는다.
- 공간 프레임, 가구 실루엣, AR 배치점을 공통 모티프로 사용한다.
- 생성 원본과 화면별 사용 가이드를 `experiments/shinym87/interior/assets/`에 보관한다.

## Scope

- 앱 아이콘 Master PNG
- 메인 분기 화면 Hero Asset
- AR 인테리어 진입 Card Asset
- 설명 페이지 Hero Asset
- 설정 화면 Header Asset
- Asset 사용 위치, Crop, 색상 가이드

## Out of Scope

- Android 화면 분기와 Navigation 구현
- 기존 AR 조작 화면 Layout 개편
- 실제 가구 상품 사진과 3D glTF 모델 제작

## Key Questions

- 생성한 시각 자산이 작은 모바일 화면에서도 가구·공간 모티프를 명확히 전달하는가?
- 향후 Web 버전에서도 같은 브랜드 자산을 재사용할 수 있는가?

## Decisions

- 이미지 내부에는 한글이나 설명 문구를 넣지 않는다.
- 앱 아이콘은 작은 크기에서도 식별되는 단순한 공간 프레임과 배치점 조합으로 만든다.
- 화면용 자산은 UI가 올라갈 여백을 포함하고, 버튼·입력창 자체는 이미지로 굽지 않는다.

## Dependencies

- `agent/goguma-salad/interior`
- `experiments/shinym87/interior/app/`

## Notes for Other Teams

디자인 Asset 제작 중이다. 현재 Android 기능 구현 파일은 변경하지 않으며, 화면 구현 시
`assets/README.md`의 역할과 Crop 가이드를 먼저 확인한다.

## Integration Candidate

YES

## Known Issues

- 실제 화면 적용 전 다양한 Android 해상도에서 Crop 결과 확인이 필요하다.
- 내장 ImageGen의 투명 배경 결과가 실제 Alpha 대신 체크무늬 RGB로 생성되어 Adaptive Icon
  Foreground는 포함하지 않았다. 배경 포함 Master를 확정한 뒤 Vector로 재작성한다.

## Next

1. 생성 후보 5종 디자인 검토
2. 승인된 앱 아이콘의 Adaptive Icon Resource 변환
3. Android Resource 변환과 화면 분기는 후속 Workstream에서 진행

## Relevant Commits

- 작업 완료 후 기록

## Updated

2026-09-07
