# Workstream

## Topic

인테리어 화보 객체 선택과 Depth 면적 검사·AR 도구 가독성 개선

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/interior-assets-ui`

## Project Path

- Host: `experiments/shinym87/interior/`
- Depth Core: `experiments/goguma-salad/interior-depth-placement/depth-placement-core/`

사용자 요청에 따라 기존 Branch에서 Host와 공용 Depth 모듈을 함께 수정한다.

## Status

INTEGRATION

## Goal

화보에서 선택한 가구를 AR로 배치할 때 필요한 지지 면적을 검사하고, 카메라 위의 안내와 도구를 쉽게 읽고 조작할 수 있게 정리한다.

## Current Direction

- 화보는 공급받은 `assets/magazine/interior_magazine1.png`, `interior_magazine2.png`의 8개 지면을 사용한다.
- 사진과 객체 좌표는 유지한다. 가구 선택 점은 48dp 터치 영역, 대비가 있는 테두리와 15sp 이름 태그를 사용한다.
- 태그를 숨길 때도 크기를 측정할 수 있게 하고, 실제 크기를 기준으로 화면 안에 배치한다.
- AR 화면은 상단 안내 카드와 하단 스크롤 도구 패널로 나눈다. 하단은 사용 가능한 높이의 48% 이내로 제한한다.
- 선택 가구의 이름과 실제 크기·배율·회전을 두 줄로 표시한다.
- 사물 지우기는 동일한 AR 화면에서 펼쳐 사용한다. 사물 선택 → 영역 지정 → 지우기 순서로 표시한다.
- 가구 편집 패널과 지운 사물 편집 패널은 세로로 쌓여 겹치지 않는다.
- 버튼의 최소 높이는 48dp이고, 활성·비활성 상태를 다른 색으로 표시한다.

## Depth 면적 검사

- 가구의 바닥 또는 벽 접촉 영역을 4×4 구역으로 나눈다.
- 기본값은 16개 구역 중 12개 이상에서 표면 점이 관측되어야 한다.
- 모든 행과 열에서 최소 2개 구역이 관측되어야 하므로 중앙에만 점이 많거나 한쪽이 완전히 끊긴 표면은 거절한다.
- 필요한 영역이 카메라 시야 밖에 있으면 관측되지 않은 구역도 분모에 포함한다.
- 기울어진 바닥을 좁은 영상 ROI로 잘못 제한하지 않도록 현재 Depth frame 전체에서 점을 찾고 실제 크기의 영역으로 걸러낸다.
- 최소 점 개수, 평탄도, 표면 종류와 장애물 검사도 유지한다.
- Host의 평면 대체 배치는 Depth frame 또는 점이 부족한 경우에만 허용한다. 면적 부족·장애물·부적합 표면은 대체 배치로 우회하지 않는다.
- `PlacementConfig.minimumFootprintCoverage` 기본값은 0.75이다. 노이즈·거리 관련 기존 Host 설정은 유지한다.

## Verification

- Depth Core 테스트 19개 통과: 기존 14개와 중앙 작은 표면, 시야 밖 영역, 지지면 한쪽 누락, 산발적 Depth 누락, 작은 벽 표면 등 5개 추가 사례.
- 기존 표면 경계 테스트는 실제로 들어가는 작은 객체의 터치 Pose를 검증하도록 수정했다.
- 최종 Android debug APK build 성공. Lint 오류 0건, 경고 131건. Android 7(minSdk 24)과 호환되는 버튼 여백 속성을 사용한다.
- 이번 작업에서 연결된 Android 실기기가 없어 화면·터치·Depth 실측은 미검증이다.

## Known Issues

- 면적 검사는 관측 점의 구역 분포를 보는 근사 검사이며 실제 바닥의 연속성이나 가구의 물리적 안정성을 보증하지 않는다.
- Depth가 준비되지 않은 평면 대체 배치에는 이 면적 검사가 적용되지 않는다.
- 배치 이후 확대·회전 버튼 조작 시 즉시 면적을 재검사하지 않는다. 최초 배치와 드래그 완료 시 검사한다.
- frame 전체 검색의 실기기 처리시간, sparse Raw Depth에서의 오거절률은 측정이 필요하다.
- 화보 객체 ID와 서버 카탈로그 ID 불일치, 서버 저장·복원 문제는 이번 범위에 포함하지 않았다.
- `bed`, `lamp`, `decor`의 상세 모델은 없으며 기존 procedural 모델을 유지한다.
- 배율이 자동으로 최소값까지 줄었다는 기존 보고는 실기기 재현이 필요하다.

## Integration Candidate

사용자 검토 대기. 이번 변경은 현재 Branch에만 보관한다.
`integration-interior-demo` 병합과 원격 Push는 사용자의 확인 이후 별도로 진행한다.

## Next

1. APK에서 화보 점·태그를 눌러 선택과 AR 진입을 확인한다.
2. AR 하단 도구 펼치기·접기, 가구 편집, 큰 글꼴에서의 스크롤을 확인한다.
3. 넓은 바닥·벽은 배치되고 좁은 받침면·표면 가장자리에서는 거절되는지 확인한다.
4. 사용자 확인 후 `integration-interior-demo`에 병합한다.

## Relevant Commits

- `7c5c1dd`: 이번 작업 시작 기준 Handoff.
- 이번 Branch의 `fix(depth)` 및 `feat(ui)` Commit: 면적 검사와 UI 개선을 목적별로 분리한다.

## Handoff

[interior-assets-ui-magazine-ar](../handoffs/interior-assets-ui-magazine-ar.md)

## Updated

2026-09-08
