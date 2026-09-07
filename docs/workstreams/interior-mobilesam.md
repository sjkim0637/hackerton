# Workstream

## Topic

Interior 사물 선택/삭제/재배치 재설계 — MobileSAM 점 프롬프트(D5) + LaMa 로컬
인페인팅(D7) + Depth API/Instant Placement(D6) + 실제 3D 가구 모델(D8)

이 문서는 애초 D5(세그멘테이션)만 다뤘지만, 이후 같은 브랜치에서 D6~D8까지 이어져
사실상 "AR 화면 UX 전면 단순화" 작업 전체를 기록한다. 결정별 자세한 내용은
`experiments/shinym87/interior/docs/decisions.md`의 D5~D8을 참고.

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
- 앱: D6 — 초기 AR 진입 가속. `ArSpaceController`에 ARCore Depth API(`DepthMode.AUTOMATIC`,
  지원 기기만) + Instant Placement(`InstantPlacementMode.LOCAL_Y_UP`, 전 기기)를 추가해
  Plane 을 아직 못 찾아도 즉시 hitTest/배치가 되게 한다. "IR 기반 Depth/ToF" 요청에 대한
  실제 구현 범위와 한계(ToF 없는 기기에서는 소프트웨어 Depth-from-Motion으로 대체)는
  `docs/decisions.md` D6 참고.

## Out of Scope (이번 Branch 에서는 안 함, Next 참고)

- `RemovalController`/`InteriorApiClient`/`MainActivity`를 실제로 `onPointSelected`에 연결해
  서버로 `PointRegion`을 보내는 앱 쪽 배선. AR 상태 기계(`RemovalController.kt`, 641줄)를
  깊이 이해하지 못한 채 blind edit 하는 위험을 피하려고 이번 Branch에서는 보류했다.
- 모델 파일(MobileSAM, LaMa 둘 다)을 저장소에 커밋하는 것(로컬에 받아 실제로
  돌려보긴 했다 — 아래 참고).
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
3. `pip install -r requirements.txt -r requirements-onnx.txt` (onnxruntime 추가 설치;
   D7에서 MobileSAM 전용이던 `requirements-mobilesam.txt`를 `requirements-onnx.txt`로
   리네임했다 — LaMa도 같은 의존성을 쓴다).
4. 둘 중 하나라도 없거나 로드 실패하면 자동으로 bbox 근사로 대체되므로, 모델 없이도
   서버는 정상 동작한다(품질만 낮다) — 팀원 각자 환경에서 안전하게 개발 가능.

**주의 — 이 모델은 "공식" SAM ONNX export와 입력 형태가 다르다.** 처음엔 공식 export
스크립트 문서만 보고 `(1,3,1024,1024)` NCHW + SAM 평균/표준편차 정규화 + 정사각형 패딩으로
짰는데, 실제로 받은 파일을 `onnxruntime`으로 열어 `get_inputs()`를 찍어보니 전혀 달랐다:
encoder 입력은 `input_image` 이름의 `(H, W, 3)` — 배치 차원도 없고 HWC이고 정규화 없이
0~255 원본 픽셀 그대로(정규화가 그래프 안에 있음). 실제 파일로 검증하지 않았다면 이
버그를 그대로 커밋할 뻔했다 — `app/ai/mobilesam.py` 상단 docstring에 실측한 정확한
계약을 적어뒀다.

## LaMa 인페인팅 모델 준비 (D7, 수동, 저장소에 커밋 안 함)

MobileSAM은 "어디를 지울지"만 정하고, 실제로 그 자리를 자연스럽게 채우는 건 별도
인페인팅 모델의 역할이다. mock(주변 색 평균)은 품질이 낮아 D7에서 로컬 LaMa로 교체했다.

1. 다운로드(약 198MB): `https://huggingface.co/Carve/LaMa-ONNX/resolve/main/lama_fp32.onnx`
   (`opencv/inpainting_lama`, `sapienkit/LaMa-ONNX` 등 다른 재배포본도 있다).
2. `.env` 또는 환경변수: `INTERIOR_LAMA_MODEL_PATH=/path/to/lama_fp32.onnx`
3. `INTERIOR_AI_PROVIDER` 기본값이 이미 `lama` 로 바뀌었다(D7) — 경로만 지정하면 된다.
   경로가 없거나 파일이 없으면(MobileSAM과 달리) **조용히 mock으로 대체되지 않고
   `remove-object` 요청이 503으로 실패한다** — 품질 저하를 숨기지 않기 위한 의도적 설계.
4. Gemini API 키가 생기면 `INTERIOR_AI_PROVIDER=external` + `INTERIOR_AI_API_KEY`로 언제든
   전환 가능(코드 변경 불필요).

실측 스펙(모델 카드에 없어서 실제 파일을 열어 확인함): 입력 `image` (1,3,512,512) float32
0~1 정규화, `mask` (1,1,512,512) float32(1=채울 영역), 출력은 0~1이 아니라 **0~255** 범위로
나온다(이것도 실측 확인 — 카드/README에 안 적혀 있었다). CPU 기준 4032×3024 이미지에서
약 5초.

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
- **2026-09-07 실기기 검증 완료 (D6, Depth API + Instant Placement).**
  `agent/goguma-salad/interior-ui-navigation`(asset·UI 분리)과 이 브랜치(MobileSAM 서버 +
  D6)를 임시로 병합(`agent/goguma-salad/interior-apk-test`, 이 브랜치 자체엔 반영 안 함)해서
  `:app:assembleDebug` 빌드(JDK 21) → 실기기 설치 → 실행까지 확인. 크래시 없음,
  `BboxSelectionView.kt`의 기존 드래그 경로도 정상 동작(회귀 없음).
  - logcat: `Depth API 지원: true` — 이 실기기는 ARCore Depth API를 지원한다.
  - **Plane 0개(`planes=0/0`) 상태에서 화면을 탭하니 가구가 즉시 배치됨** — Instant
    Placement가 의도대로 동작. "AR이 평면 찾는다고 계속 돈다"는 원래 불만이 실기기에서
    실제로 해소된 것을 확인.
  - MobileSAM 점 프롬프트로 사물을 선택하는 것 자체는 이번엔 검증 안 함(Out of Scope의
    앱 배선 미완료 때문 — 여전히 기존 bbox 드래그로만 삭제 요청 가능).

## Known Issues

- ~~앱이 아직 점을 서버로 안 보낸다~~ → **완료 (2026-09-07, D8)**. `RemovalController.
  onScreenTapped`가 탭 좌표를 바로 `PointRegion`으로 보낸다. `:app:assembleDebug` 성공,
  다만 실기기에서 탭→마스크→인페인팅까지 전체 흐름을 직접 확인하진 못했다(서버 개별
  기능은 각각 검증됨: MobileSAM 실모델 테스트, LaMa 실모델 테스트, D6 실기기 Depth 테스트).
- **D8(실제 3D 가구 모델)도 컴파일만 확인, 실기기 미검증.** `ModelLoader.
  createModelInstance()`가 실제로 캐시 파일 경로에서 로드에 성공하는지, glb 좌표계(원점
  위치)가 앵커에 자연스럽게 맞는지는 API 문서/바이트코드 추론으로만 확인했다.
- 선택한 가구가 3D 모델로 바뀌면 선택 강조(파란 하이라이트)가 안 보인다 — 강조는
  `item.material`(큐브 재질)에만 적용되기 때문. 하단 조작 패널이 뜨는 것으로만 "선택됨"을
  알 수 있다.
- `PointRegion` 하나만으로 여러 개 겹친 사물 중 무엇을 고를지는 MobileSAM의 판단에 맡긴다
  (SAM은 점 위치에서 가장 그럴듯한 사물 하나를 고르는데, 겹친 물체가 많으면 여전히 실패할
  수 있다 — 기존 PHASE 2 백로그의 "겹침" 이슈와 동일선상).
- 추론 속도(지연시간)를 재지 않았다 — CPU 전용 onnxruntime, encoder 28MB/decoder 16.5MB
  모델이라 실기기 요구 응답시간 안에 들어오는지는 실측이 필요하다.
- 테스트용으로 받은 모델은 저장소에 없다 — 다른 개발자/CI는 위 "모델 준비" 절차를 직접
  거쳐야 `test_mobilesam_real_model.py`가 skip 되지 않고 돈다.
- ~~D6은 컴파일/실기기 확인 못 함~~ → **2026-09-07 실기기 검증 완료** (아래 Verification
  참고). 실제 테스트 기기는 `Depth API 지원: true` 로 나왔다 — ToF 하드웨어인지
  Depth-from-Motion인지는 로그만으론 구분 안 되지만, 어느 쪽이든 지원 자체는 확인됐다.
  **Plane 0개 상태에서 탭 → 가구가 즉시 배치되는 것도 실기기에서 직접 확인**(Instant
  Placement가 의도대로 동작). "폰을 막 돌려야 하는" 원래 불만이 실제로 해소됐다.

## Next

1. **실기기 통합 검증(최우선, 미완료)**: 탭 → 화면 캡처 → MobileSAM 마스크 → LaMa
   인페인팅 → 벽/바닥에 결과 적용 → 이동 패널까지 실기기에서 한 번에 관통 확인.
   서버 쪽 개별 기능(MobileSAM, LaMa, Depth API)은 각각 실기기/실모델로 검증했지만
   D8까지 다 합친 통합 흐름은 아직 안 돌려봤다. `.env`에 `INTERIOR_LAMA_MODEL_PATH`,
   `INTERIOR_MOBILESAM_ENCODER_PATH`/`DECODER_PATH` 세팅 후 서버를 띄우고 실기기에서
   확인.
2. **3D 가구 모델 실기기 확인**: "가구 추가"에서 각 항목을 배치했을 때 큐브 대신 실제
   글꼴로 모델이 뜨는지, 벽/바닥 원점이 자연스러운지(특히 TV/선반처럼 `wall` 가구가
   벽에서 붕 뜨거나 파묻히지 않는지).
3. **삭제 결과 fallback 수정**: [`docs/handoffs/interior-removal-fallback.md`](../handoffs/interior-removal-fallback.md)의
   완료 조건에 따라 Anchor가 없어도 전체화면 정적 Bitmap을 유지하지 않고 라이브 카메라로 복귀한다.
   MobileSAM Mask 성공 여부와 AR 결과 표시 상태를 분리한다.
4. 선택 강조를 3D 모델에도 적용(예: 모델 주위에 아웃라인/바닥 링 표시 — Filament 재질을
   직접 못 바꾸므로 별도 시각 요소가 필요).
5. 겹친 사물 처리(여러 후보 마스크 중 선택 UI) 여부 결정 — SAM decoder는 여러 후보를
   `iou_predictions`로 함께 주므로, 상위 1개 대신 상위 N개를 앱에 보여줄 수도 있다.
6. 추론 속도 실측(MobileSAM+LaMa 합산 지연시간), 필요하면 양자화 decoder
   (`mobilesam.decoder.quant.onnx`, 8.8MB)로 교체.

## Integration Candidate

`interior`(D5) 확정 결정에 따른 후속 구현. `agent/shinym87/interior`가 main으로 통합될 때
이 Branch도 같이 검토한다(`TEAM_WORKBOARD.md`의 통합 요청 참고).

## Relevant Commits

- 이 Workstream 등록 및 MobileSAM 점 프롬프트 서버 구현 커밋 (본 Branch)

## Updated

2026-09-07
