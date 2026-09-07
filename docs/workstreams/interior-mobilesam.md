# Workstream

## Topic

Interior 사물 선택 — MobileSAM 점 프롬프트 세그멘테이션

## Owner

goguma-salad + Claude

## Git Branch

`agent/goguma-salad/interior-mobilesam`

## Project Path

`experiments/shinym87/interior/` (기존 interior 구현 위에 얹는다, 별도 폴더 없음)

## Status

IN_PROGRESS

## Goal

`interior` Workstream(D3)의 "드래그로 사각형을 그려야 하는" 사물 선택 UX를 "탭(또는 누르고
있기) 한 번"으로 단순화한다. 서버에서 그 점을 MobileSAM으로 실제 사물 마스크로 변환해,
지금까지의 사각형 근사보다 정밀한 결과를 얻는다.

## Background

`interior` Workstream D3 결정으로 MVP는 bbox(드래그 사각형)를 썼다. 실사용 피드백은
"사각형을 정확히 그려야 하는 게 너무 번거롭다"였다. 순수 OpenCV(GrabCut/Saliency/floodFill)로
완전 자동 선택을 검토했으나, 인테리어 사진은 배경(벽·바닥) 비중이 크고 사물과 배경의 색상이
비슷한 경우가 많아 점 하나 또는 무입력만으로는 실패가 잦다는 결론에 도달했다(고전 CV는
"의미"를 모름). 대신 점 프롬프트 전용으로 설계된 MobileSAM(Segment Anything의 경량 증류
모델)을 채택했다. 자세한 대안 비교는 `experiments/shinym87/interior/docs/decisions.md` D5 참고.

## Current Direction

- 서버 우선 구현: 점(point) → 마스크 변환을 서버(`app/ai/mobilesam.py`)에 두고, 앱은 점
  좌표만 보내면 되게 한다 (모델 파일 관리·추론을 앱에 두지 않음).
- MobileSAM 모델이 없어도 서버가 항상 뜨고 항상 응답하도록, 점 중심 정사각형 bbox로
  자동 대체하는 경로를 기본으로 깐다(`app/ai/mobilesam.fallback_box_mask_png`).
- 기존 `Region` 스키마(`bbox`/`mask`)에 `point`를 얹어, 이후 파이프라인(캐시 키, 크롭,
  색감 보정, 페더링)이 최대한 그대로 재사용되게 한다 — 점은 `remove-object` 처리 진입점에서
  바로 `mask`로 바뀌고, 그 뒤로는 다른 코드가 `point`를 몰라도 된다.
- 모델 파일(encoder/decoder ONNX, 수십MB)은 저장소에 커밋하지 않는다(레포의 기존 원칙 —
  `interior.md` Known Issues의 "126MiB CAD 파일 제외"와 동일한 이유).

## Scope

- 서버: `PointRegion` 스키마, MobileSAM 점 프롬프트 추론(`app/ai/mobilesam.py`),
  모델 미가용 시 bbox 근사 대체, `mask.py`의 mask 타입 처리를 실제 마스크 픽셀 기반으로 개선
  (기존엔 "가운데 절반"으로 근사하던 것을 실제 바운딩 박스로 교체 — `mock.py`도 동일 로직 재사용).
- 앱: `BboxSelectionView`에 탭/누르고 있기 좌표를 알려주는 `onPointSelected` 콜백 추가
  (기존 드래그 경로는 그대로 유지, 순수 추가라 기존 동작 회귀 없음).

## Out of Scope (이번 Branch 에서는 안 함, Next 참고)

- `RemovalController`/`InteriorApiClient`/`MainActivity`를 실제로 `onPointSelected`에 연결해
  서버로 `PointRegion`을 보내는 앱 쪽 배선. AR 상태 기계(`RemovalController.kt`, 641줄)를
  깊이 이해하지 못한 채 blind edit 하는 위험을 피하려고 이번 Branch에서는 보류했다.
- 모델 파일을 저장소에 커밋하는 것(로컬에 받아 실제로 돌려보긴 했다 — 아래 참고).
- 앱 실기기 빌드 검증(이 환경엔 Android SDK/실기기가 없어 서버만 pytest로 검증했다).

## MobileSAM 모델 준비 (수동, 저장소에 커밋 안 함)

이 Branch를 만들면서 실제로 아래 파일을 받아 `server/models/`(gitignore됨)에 두고
`real_living_room.jpg`로 직접 돌려서 검증했다 — 문서만 보고 가정한 게 아니다.

1. encoder/decoder ONNX 다운로드:
   - encoder(약 28MB): `https://huggingface.co/spaces/Akbartus/projects/resolve/main/mobilesam.encoder.onnx`
   - decoder(약 16.5MB, 일반 버전 — 양자화된 8.8MB 버전도 있음):
     `https://raw.githubusercontent.com/akbartus/MobileSAM-in-the-Browser/main/models/mobilesam.decoder.onnx`
   - 출처: `akbartus/MobileSAM-in-the-Browser` 저장소(`SAMExporter`로 변환, `ChaoningZhang/MobileSAM`
     원 모델). 직접 export하려면 `ChaoningZhang/MobileSAM`의
     `scripts/export_onnx_model.py --checkpoint mobile_sam.pt --model-type vit_t`.
2. `.env`(gitignore됨) 또는 환경변수로 경로 지정:
   ```
   INTERIOR_MOBILESAM_ENCODER_PATH=/path/to/mobilesam.encoder.onnx
   INTERIOR_MOBILESAM_DECODER_PATH=/path/to/mobilesam.decoder.onnx
   ```
3. `pip install -r requirements.txt -r requirements-mobilesam.txt` (onnxruntime 추가 설치).
4. 둘 중 하나라도 없거나 로드 실패하면 자동으로 bbox 근사로 대체되므로, 모델 없이도
   서버는 정상 동작한다(품질만 낮다) — 팀원 각자 환경에서 안전하게 개발 가능.

**주의 — 이 모델은 "공식" SAM ONNX export와 입력 형태가 다르다.** 처음엔 공식 export
스크립트 문서만 보고 `(1,3,1024,1024)` NCHW + SAM 평균/표준편차 정규화 + 정사각형 패딩으로
짰는데, 실제로 받은 파일을 `onnxruntime`으로 열어 `get_inputs()`를 찍어보니 전혀 달랐다:
encoder 입력은 `input_image` 이름의 `(H, W, 3)` — 배치 차원도 없고 HWC이고 정규화 없이
0~255 원본 픽셀 그대로(정규화가 그래프 안에 있음). 실제 파일로 검증하지 않았다면 이
버그를 그대로 커밋할 뻔했다 — `app/ai/mobilesam.py` 상단 docstring에 실측한 정확한
계약을 적어뒀다.

## API 변경

`RemoveObjectRequest.target`(및 `TargetObject.region`)이 받는 `Region`에 세 번째 타입 추가:

```json
{ "type": "point", "point": [0.5, 0.42] }
```

서버가 `remove-object` 처리 전에 이걸 `{"type": "mask", "png": "<base64>", "size": {...}}`로
바꿔서 저장/캐시/AI 호출에 넘긴다. 앱은 `mask` 타입을 직접 만들 필요가 없다 — 점만 보내면 된다.

## Verification

- **실제 MobileSAM 모델로 검증 완료** (위 "모델 준비" 절차대로 받은 진짜 encoder/decoder
  ONNX, 목업 아님):
  - `tests/test_mobilesam_real_model.py`: 모델 파일이 있으면 실행되고 없으면 자동
    skip — `real_living_room.jpg`에 점을 찍어 원본 해상도(4032×3024) 마스크가 나오는지,
    크기가 합리적인지(전체도 빈 것도 아님), IoU 점수가 높은지 확인.
  - 수동 확인(테스트로 안 남김): 실제 `.env` 설정으로 서버를 띄우고
    `POST /remove-object`에 `{"type":"point","point":[0.49,0.53]}`를 보내 job이 `done`까지
    가고, `GET /objects`에 저장된 region이 실제 마스크(전체 화면의 약 5.3%, 640,402px)인
    것까지 확인했다. 시각적으로도(마스크 PNG를 직접 열어봄) TV+받침대+케이블 모양의
    깔끔한 실루엣이 나왔다 — 사각형 근사보다 명백히 낫다.
  - 이 과정에서 처음 짠 전처리 가정(공식 SAM export 기준)이 실제 파일과 달라 버그였다는
    걸 발견해 고쳤다(위 "주의" 참고) — 실제 파일로 검증하지 않았다면 몰랐을 문제.
- `experiments/shinym87/interior/server`에서 `pytest` 60개 통과(기존 47개 + 13개: 위
  실모델 테스트 2개 포함 `tests/test_mask.py`, `tests/test_mobilesam.py`,
  `tests/test_mobilesam_real_model.py`, `test_api.py`의 point region 흐름).
- `ruff check`: 이번에 건드린 파일 기준 통과(사전에 있던 `scenes.py`의 `File(...)` 기본값
  경고 1개는 이번 변경과 무관한 기존 항목이라 그대로 둠).
- 앱(`BboxSelectionView.kt`) 변경은 컴파일/실기기 확인 못 함 — Android SDK 없는 환경에서
  작업함. 코드 리뷰상 기존 드래그 경로는 로직을 안 건드렸다(추가만 함).

## Known Issues

- 앱이 아직 점을 서버로 안 보낸다 — 이 Branch는 서버 능력만 갖췄다.
- `PointRegion` 하나만으로 여러 개 겹친 사물 중 무엇을 고를지는 MobileSAM의 판단에 맡긴다
  (SAM은 점 위치에서 가장 그럴듯한 사물 하나를 고르는데, 겹친 물체가 많으면 여전히 실패할
  수 있다 — 기존 PHASE 2 백로그의 "겹침" 이슈와 동일선상).
- 추론 속도(지연시간)를 재지 않았다 — CPU 전용 onnxruntime, encoder 28MB/decoder 16.5MB
  모델이라 실기기 요구 응답시간 안에 들어오는지는 실측이 필요하다.
- 테스트용으로 받은 모델은 저장소에 없다 — 다른 개발자/CI는 위 "모델 준비" 절차를 직접
  거쳐야 `test_mobilesam_real_model.py`가 skip 되지 않고 돈다.

## Next

1. **삭제 결과 fallback 수정(우선)**: [`docs/handoffs/interior-removal-fallback.md`](../handoffs/interior-removal-fallback.md)의
   완료 조건에 따라 Anchor가 없어도 전체화면 정적 Bitmap을 유지하지 않고 라이브 카메라로 복귀한다.
   MobileSAM Mask 성공 여부와 AR 결과 표시 상태를 분리한다.
2. **앱 배선**: `RemovalController`에서 "TV 선택 모드" 진입 시
   `binding.bboxSelectionView.onPointSelected = ::onPointSelected` 로 연결하고, 새 핸들러가
   기존 `onRectSelected(rect: RectF)`와 같은 자리에서 `target: {"type": "point", "point": [x,y]}`
   를 만들어 `RemoveObjectRequest`에 실어 보내도록 `InteriorApiClient`를 확장한다.
   단, 기존 `resolveWall(rect)`(벽 hitTest로 실측 크기 표시)는 사각형의 네 변에 의존하므로,
   점 하나로는 그대로 못 쓴다 — 점 주변에 작은 hitTest 사각형을 합성하거나, 정밀 마스크가
   서버에서 오기 전까지는 실측 표시를 생략하는 방향을 검토해야 한다.
3. 겹친 사물 처리(여러 후보 마스크 중 선택 UI) 여부 결정 — SAM decoder는 여러 후보를
   `iou_predictions`로 함께 주므로, 상위 1개 대신 상위 N개를 앱에 보여줄 수도 있다.
4. 추론 속도 실측, 필요하면 양자화 decoder(`mobilesam.decoder.quant.onnx`, 8.8MB)로 교체.
5. 실기기에서 앱 빌드 확인 (`:app:assembleDebug`, 이 환경엔 Android SDK 없음).

## Integration Candidate

`interior`(D5) 확정 결정에 따른 후속 구현. `agent/shinym87/interior`가 main으로 통합될 때
이 Branch도 같이 검토한다(`TEAM_WORKBOARD.md`의 통합 요청 참고).

## Relevant Commits

- 이 Workstream 등록 및 MobileSAM 점 프롬프트 서버 구현 커밋 (본 Branch)

## Updated

2026-09-07
