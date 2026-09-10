# Workstream

## Topic

RGB-D 기반 실물 가구 삭제·이동·재배치

## Owner

goguma-salad + Codex

## Git Branch

`agent/goguma-salad/rgbd-space-edit`

## Project Path

`experiments/shinym87/interior/`

## Status

IN_PROGRESS

## Goal

선택 시점의 RGB, MobileSAM Mask, ARCore Depth, Camera Intrinsics, Camera 6DoF Pose를 하나의 Snapshot으로 수집한다. 이후 배경은 world-space geometry에, 선택 가구는 RGB-D 2.5D `PlaceableObject`에 연결한다.

## Direction

- 기존 2D crop/PNG quad 이동 경로를 확장하지 않는다.
- 삭제 RGB는 ROI LaMa 복원 후 background plane 또는 depth mesh에 적용한다.
- 선택 객체의 RGB와 Depth를 함께 사용해 local mesh, 실제 크기, pivot, ground contact를 계산한다.
- Catalog GLB와 Captured RGB-D Object는 공통 `PlaceableObject` 입력으로 기존 Placement Engine을 사용한다.
- 구현은 RGB/Mask/Depth/Pose Snapshot 검증부터 단계별로 진행한다.

## Scope

- Phase 1: ARCore RGB·Depth·Intrinsics·Pose Snapshot 및 Debug Overlay
- Phase 2: ROI LaMa, mask dilation, feather 합성
- Phase 3: background plane/geometry 복원과 world-space texture 적용
- Phase 4~5: RGB-D object mesh와 Placement Engine 연결

## Out of Scope

- Image-to-3D, NeRF, Gaussian Splatting, 360도 객체 생성
- 별도 서버 기반 3D 변환

## Integration Candidate

TBD

## Updated

2026-09-10
