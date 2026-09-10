# Handoff

## From

shinym87 (+ Claude)

## To

shinym87 (Gemini API 키가 준비되면 실제 결과 확인) / 이후 합류하는 영상·AI 담당

## Workstream

[interior](../workstreams/interior.md) — 카메라 기반 공간 편집 / AR 가구 재배치.
PHASE 1 (P1-10) + PHASE 2 + PHASE 3 "사용자 2 (영상 / AI)".

## 라이브 화면 가림막 가장자리 "얇은 흰색 테두리 선" 제거 (2026-09-10)

Branch `integration-interior-demo`. 앞선 수정 후 실기기 재테스트: **정지 프리뷰
("삭제 결과 보기")는 완벽하게 깨끗**, 라이브 화면에서만 커버 quad 가장자리를 따라
얇은 밝은(흰) 선이 보였다.

### 원인 = SceneView 기본 텍스처 샘플러 (`TextureSampler2D`)

`io.github.sceneview.texture.TextureSampler2D` 는
`WrapMode.REPEAT` + `MinFilter.LINEAR_MIPMAP_LINEAR` 다. `ImageNode` 의 Plane 지오메트리
UV 는 0→1 이라:
- **REPEAT**: quad 가장자리(UV≈0/1)에서 bilinear 이 반대쪽 가장자리 텍셀을 wrap 해
  같이 섞는다.
- **밉맵**: `LINEAR_MIPMAP_LINEAR` 로 축소 렌더 시 밉을 쓰는데, 밉 생성이 alpha 가중이
  아니라 non-premultiplied 페이드 림(밝은 벽색 + alpha 0)을 안쪽으로 번지게 한다.

두 효과가 합쳐져 라이브(Filament)에서만 얇은 밝은 테두리가 났다. 정지 프리뷰는 일반
Android `ImageView`(clamp, 밉맵 없음)라 원래 깨끗했다 — 증상이 라이브에만 있던 이유.

### 수정

1. **`EdgeFade.crispSampler()` 신설** — `TextureSampler(LINEAR, LINEAR, CLAMP_TO_EDGE)`.
   커버 quad(`RemovalController.buildResultNode`)와 이동 마커(`MovedObjectController.setNode`)
   의 `ImageNode` 가 이 샘플러로 렌더한다. REPEAT wrap 과 밉맵 블리딩이 사라진다.
2. **`EdgeFade.feather`** — 페더 폭 `featherFrac` 0.08 → **0.10**, 추가로 최외곽
   `HARD_RIM_FRAC`(18% of feather, 최소 1px) 구간을 **완전 투명으로 강제**. CLAMP_TO_EDGE
   가 물어오는 최외곽 텍셀이 항상 alpha 0 이 되도록.
3. **크롭 inset** — `RemovalController.insetRegion()`: 커버 quad 텍스처 크롭을 각 변에서
   영역 크기의 `CROP_INSET_FRACTION`(3%) 만큼 안쪽으로 좁힌다. AI 인페인팅이 마스크
   경계에 남기는 seam / JPEG 링잉이 텍스처 가장자리에 밝게 섞이는 것을 배제. (사용자
   지적 1·2번.) 정지 프리뷰용 `full` 은 그대로 — inset 은 quad 텍스처에만 적용.
4. **`COVER_MARGIN` 1.08 → 1.04** — 페이드 밴드가 얹히는 여유를 줄여, 부드러워진
   가장자리가 벽 위로 밀려나 눈에 띄는 것을 완화. (사용자 지적 3번: quad 가 실물보다
   커서 그 여백이 흰색으로 렌더되는가 → CLAMP_TO_EDGE 로는 "흰 여백" 자체가 생기지
   않지만, 여유를 줄이면 페이드가 삭제 자리 안쪽에 들어와 더 안 보인다.)

튜닝 노브: `EdgeFade.HARD_RIM_FRAC`, `feather(featherFrac=)`,
`RemovalController.CROP_INSET_FRACTION`, `COVER_MARGIN`.

### 빌드

`JAVA_HOME=...\jbr-21.0.11` + `.\gradlew.bat :app:assembleDebug` → **BUILD SUCCESSFUL**
(24s). APK: `experiments/shinym87/interior/app/build/outputs/apk/debug/app-debug.apk`.
실기기에서 라이브 화면 커버 quad 가장자리 육안 확인 대기.

## 가림막 크기·어두운 경계선 + 이동 사물에 파란 테두리 박힘 수정 (2026-09-10)

Branch `integration-interior-demo`. 실기기 리포트 2건.

### 문제 1 — 빌보드 가림막이 선택 영역보다 크고 가장자리에 어두운 띠

**1-a. 크기 (`RemovalController.COVER_MARGIN` 1.12 → 1.08)**
빌보드 커버 quad 는 `patchWidthM/HeightM × COVER_MARGIN` 으로 그린다(측정값 자체는
안 건드림 — 이동 마커가 실측 그대로 써야 하므로). 가로·세로 둘 다 1.12 를 곱하면
면적이 1.25배라 "과하게 크다"로 읽혔다. 실물 가장자리가 안 삐져나올 최소 여유
8% 로 낮췄다. 벽걸이(수직 평면) 경로는 원래 여유 없이 `patchWidthM` 그대로라
영향 없음.

**1-b. 어두운 경계선 (`EdgeFade.kt` 전면 재작성)**
원인 = **premultiplied alpha 이중 곱**. 기존 `EdgeFade` 는 `Canvas` +
`PorterDuff.DST_IN` 그라데이션으로 alpha 를 깎았는데, Android 비트맵은 내부적으로
premultiplied 저장이라 페이드 밴드의 RGB 까지 `rgb × α` 로 어두워졌다. 게다가
SceneView `imageTextureMaterial`(Filament `blending: transparent`) 은 straight-alpha
텍스처를 받아 셰이더에서 다시 `rgb *= α` 를 한다 → 페이드 구간이 `rgb × α²` 로
이중으로 죽어 가장자리에 어두운 그림자선이 생겼다.
- 이제 픽셀을 직접 순회하며 **RGB 는 그대로 두고 alpha 만** 선형 램프로 낮춘다.
  출력 비트맵은 `isPremultiplied = false` + `setHasAlpha(true)` → straight-alpha
  텍스처를 straight-alpha 로 블렌딩하므로 색이 검게 죽지 않는다.
- 검은 패딩 우려에 대해: `applyResult` 의 `cropNormalized(full, region)` 는 이미
  x/y/w/h 를 비트맵 경계로 `coerceIn` 클램프한다 → 원본 이미지 밖을 읽지 않는다.
  `COVER_MARGIN` 은 crop 사각형이 아니라 **3D quad 크기**만 키우므로(같은 텍스처를
  늘일 뿐) 여백에 이미지 밖 영역이 섞이지 않는다. 어두운 띠는 패딩이 아니라 위의
  alpha 이중 곱이 원인이었다.
- `EdgeFade.feather` 시그니처(`src`, `featherFrac=0.08`)·호출부 동일. 이동 마커
  경로(`MovedObjectController` 의 RGBA 컷아웃/크롭/플레이스홀더)도 같은 함수를 타므로
  그쪽 가장자리 어두움도 함께 개선된다.

### 문제 2 — "여기로 옮기기" 사물 이미지에 선택 영역 파란 테두리가 찍힘

`capturedObjectBitmap` 은 `RemovalController.captureSceneJpeg` 의 `PixelCopy` 결과를
bbox 크롭한 것이다. `PixelCopy.request(sceneView, …)` 는 `SurfaceView` 서피스만 읽어
이론상 상위 오버레이(`bboxSelectionView`)는 안 찍혀야 하지만, 실기기에서 파란
사각형이 그대로 박혀 나왔다(기기/합성 경로 차이로 추정).
- 조치: ARCore 평면/특징점 오버레이를 `onBeforeCapture`/`onAfterCapture` 로 껐다 켜는
  것과 **같은 방식**으로, `captureSceneJpeg` 가 캡처 직전 `bboxSelectionView` 와
  `resultOverlay` 를 `INVISIBLE` 로 숨기고 `PixelCopy` 콜백에서 원래 visibility 로
  복구한다. 캡처 중 "현재 화면 캡처 중…" 동안 파란 사각형이 잠깐 사라졌다 돌아온다
  (평면 격자가 깜빡이는 것과 동일, 정상).
- 서버 RGBA 컷아웃(`{job}_object.png`) 경로는 원래부터 키프레임(오버레이 없는 JPEG)
  기반이라 무관 — 이 수정은 컷아웃이 없어 로컬 bbox 크롭으로 폴백하는 경우를 고친다.

### 빌드

`JAVA_HOME=C:\Users\User\.jdks\jbr-21.0.11` + `.\gradlew.bat :app:assembleDebug`
→ **BUILD SUCCESSFUL** (30s). APK:
`experiments/shinym87/interior/app/build/outputs/apk/debug/app-debug.apk` (약 59MB).
실기기 육안 확인(가림막 크기/경계선, 이동 사물 이미지 테두리) 대기.

## 이동 다이얼을 상하좌우 회전 십자로 + 대리석 벽 평면 인식 완화 (2026-09-10)

Branch `integration-interior-demo`.

### 이동 사물 조작 다이얼 재구성 (`activity_main.xml` + `MovedObjectController`)

십자 다이얼을 **회전 전용**으로 바꿈:
- 상 `btnMovedTiltUp` ▲ / 하 `btnMovedTiltDown` ▼ = **pitch(위아래 기울기)**
- 좌 `btnMovedRotateLeft` ↶ / 우 `btnMovedRotateRight` ↷ = yaw(좌우 회전, 기존)
- 가운데 = 라벨("회전")

크기 조절은 다이얼에서 빼서 **별도 한 줄** `[축소 −] [확대 ＋]` (`btnMovedShrink`/
`btnMovedGrow`, 배치복원/실행취소 줄 위). id 는 그대로라 배선 변경 최소.

`MovedObjectController`:
- `tiltDeg` 상태 추가 (yaw `rotDeg` 와 별개, x축). `tilt(delta)` — ±60° clamp,
  **서버 저장 안 함**(placements 스키마에 pitch 필드 없음 → 세션 로컬 표시 조정).
- `applyChildTransforms`: 바닥 `Rotation(tiltDeg, rotDeg, 0)`, 벽
  `Rotation(-90+tiltDeg, 0, rotDeg)`. `rotate()` 는 `rotate(deltaDeg)` 로 이미 일반화됨.
- `arm()` 에서 `tiltDeg=0` 리셋. `enableButtons` 에 tilt 버튼 포함.

### 무늬 없는 밝은 대리석 벽에서 평면(수직) 인식 안 됨

**원인은 confidence 임계값이 아님.** ARCore `Config` 에는 평면 신뢰도/민감도
임계값을 노출하는 API 자체가 없다. `planeFindingMode` 도 이미
`HORIZONTAL_AND_VERTICAL` 로 최대 범위다(더 완화할 값 없음). 광택·저대비 대리석은
특징점이 거의 안 잡히고 반사가 움직여 ARCore 가 평면을 못 세우는, 근본적으로 어려운
표면이다. 폰을 좌우로 움직여 **시차(parallax)** 를 줘야 하고, 정면·정지로 대면 안 된다.

완화책 (`ArSpaceController`):
- `config.instantPlacementMode = InstantPlacementMode.LOCAL_Y_UP` 추가 — Plane 없이도
  화면 탭 위치에 즉시 임시 배치, 이후 Plane/Depth 잡히면 자동 보정.
- `hitTest`/`hitTestPreferring` 이 `depthPoint = usesDepthPlacement`,
  `instantPlacementPoint = true` 도 후보로 받도록 함. `hitTestPreferring` 은 원하는
  평면 종류 → 아무 평면 → Depth/Instant 포인트 순 fallback.
- Depth API 는 `ArCoreDepthAdapter.prepareConfig` 로 이미 AUTOMATIC. (참고:
  `agent/goguma-salad/interior-mobilesam` D6 커밋 `e4a2f52`.)
- 추적 실패 안내 문구에 "광택·무늬 없는 벽은 인식이 어려워요" 추가.

`:app:assembleDebug` 성공.

## merge 후 리포트 2건 — 빌보드 45° / 이동 패널 버튼 (2026-09-10)

Branch `integration-interior-demo` (temp merge 이후).

### 문제 1 — 가림막(빌보드)이 45° 꺾임, 폰 기울일 때 심함

원인: 빌보드 회전을 **부모 AnchorNode** `worldQuaternion` 에 걸고, 바로 앞에서
`node.pose = Pose(pos, anchorQuat)` 로 앵커 회전을 넣고 있었다. 앵커 pose 갱신과
카메라 회전이 섞여 pitch 가 어긋났다.

수정 (`RemovalController.onFrame`): 빌보드면 부모는 **위치만**(회전 항등
`IDENTITY_QUAT`), 자식 `resultImageNode` 에 `worldQuaternion = cameraNode.worldQuaternion`
을 직접 건다 — `FurnitureController.billboard()` 의 라벨 처리와 동일 방식. pitch/roll
포함 카메라 전체 회전을 그대로 따라간다.

### 문제 2 — "이동 패널에 상하좌우 버튼이 있었는데 회전만 남음"

**merge 가 덮어쓴 것 아님.** 증거: `git diff 3028fb7 HEAD -- activity_main.xml`
= 0 bytes (merge `8ef01a0` 은 activity_main.xml 을 전혀 안 건드림). `movedObjectPanel`
블록은 `f3f291e` 이후 바이트 동일 — 원위치/치우기, −/＋/회전↷, 배치복원/실행취소
7버튼 그대로. temp 브랜치는 `remove/*.kt`·`server/*`·`docs/` 만 수정(파일 겹침 0).

"상하좌우 버튼" = demo-v1 이 만든 **`selectionPanel`(선택한 가구)** 의 조이스틱
다이얼(＋위/−아래/↶왼/↷오른). 이동한 사물 패널엔 원래 없었고 평범한 버튼 행이었다.

조치: 요청대로 `movedObjectPanel` 에도 **같은 조이스틱 다이얼**을 넣었다.
- `activity_main.xml`: `movedObjectPanel` 의 `−/＋/회전↷` 행 → `selectionPanel` 과
  동일한 `FrameLayout` 다이얼. ids `btnMovedGrow`(＋)/`btnMovedShrink`(−)/
  **`btnMovedRotateLeft`(↶)**/**`btnMovedRotateRight`(↷)**. `btnMovedRotate` 제거.
- `MovedObjectController`: `rotate()` → `rotate(deltaDeg)`, 좌 −15°/우 +15° 배선,
  `enableButtons` 갱신.

`:app:assembleDebug` 성공. 빌보드/컷아웃/잔상 수정은 그대로 유지.

## 삭제한 사물을 "투명 배경 컷아웃"으로 재배치 (2026-09-09)

Branch `integration-interior-demo-temp`. 문제: 삭제한 사물을 다시 배치하면 선택
사각형이 그대로 잘려 흰/배경 모서리까지 딸려와 매끄럽지 않았다. `agent/goguma-salad/
interior-mobilesam` 브랜치의 MobileSAM 세그멘테이션을 참조해 서버가 **RGBA 컷아웃**을
만들도록 하고, 앱이 그걸 이동 마커 이미지로 쓴다.

**서버:**
- `app/ai/mobilesam.py` 를 mobilesam 브랜치에서 그대로 이식(단, `numpy`/`onnxruntime`
  을 **지연 import** 로 바꿔 모델 없이도 서버가 뜨게 함). 모델 파일이 있으면
  실루엣 마스크, 없으면 `None` 반환 → 호출부가 대체.
- `app/ai/imageops.py::cutout_rgba_png(source, rect, mask_png=None)` — bbox 크롭에
  alpha 를 씌워 투명 배경 PNG. mask 있으면 실루엣, 없으면 크롭 **안쪽으로** 페더링
  (인페인팅용 `region_to_mask_png` 와 달리 밖으로 안 키움).
- `scenes.py::_run_job`: `{job}_object.jpg`(기존, 네모 크롭) 옆에 `{job}_object.png`
  (컷아웃) 도 저장. mask 는 MobileSAM(bbox 중심점) → 없으면 페더링.
- 라우트 `GET /scenes/{id}/results/{job}_object.png` (`.jpg` 라우트들보다 먼저 등록).
- `store.jobs` 에 `removed_object_cutout_path/url` 컬럼(+`_EXTRA_COLUMNS` ALTER).
- `JobOut`/`ResultInfoOut` 에 `removed_object_cutout_image_url`. `cleanup.py` 가 동반
  `_object.png` 도 함께 정리.
- `config.py`: `mobilesam_encoder_path`/`mobilesam_decoder_path`/`mobilesam_fallback_box_frac`.
- `requirements-onnx.txt`: `numpy` + `onnxruntime` (모델 켤 때만). `pytest` 47 통과
  (`test_api.py` 에 컷아웃 RGBA 검증 추가).

**앱:**
- `InteriorApiClient.JobStatus.cutoutImageUrl` 추가 (`removed_object_cutout_image_url` 파싱).
- `RemovalController.runFlow`: job done 후 `cutoutImageUrl` 이 있으면 다운로드·디코드해
  이동 마커 비트맵으로 사용(실패 시 로컬 bbox 크롭으로 대체).
- `MovedObjectController.restoreFromServer`: `{job}_object.png` → `{job}_object.jpg` 순으로 시도.
- `:app:assembleDebug` 성공.

**실물 세그멘테이션을 켜려면**: `pip install -r requirements-onnx.txt`, MobileSAM
encoder/decoder ONNX 를 받아 `INTERIOR_MOBILESAM_ENCODER_PATH`/`..._DECODER_PATH` 설정.
안 켜도 페더링 컷아웃으로 흰 모서리는 사라진다(실루엣은 아님).

## 가림막(빌보드) 회귀 수정 — 크기를 화면 선택 종횡비로 (2026-09-09)

Branch `integration-interior-demo-temp`. 증상 (빌보드 도입 후):
1. 평면 미인식 시 삭제 완료 후 화면 전체가 거대 반투명 판으로 뒤덮임.
2. 컵 삭제 시 엉뚱한 위치에 세로로 긴 막대형 가림막, 컵은 안 가려짐.

**원인 = merge/Depth 아님. 이 세션의 빌보드 커밋(`c44bc4a` + `e2f0033`) 자체.**
- Depth 코드(`DepthPlacementController`, `depth-placement-*` 모듈, 패키지
  `com.project.depthplacement`)는 `RemovalController`/`patchWidthM`/`resolveWall`/
  `wallAnchor` 를 **한 군데도 참조 안 함** — `FurnitureController.validatePlacement`
  전용 검증기. 좌표계·스케일 공유 없음.
- `RemovalController.kt` 는 `f3f291e` merge 이후 **이 세션 4커밋(`683a9ea`
  `35340fb` `c44bc4a` `e2f0033`)만** 건드림. merge 가 바꾼 게 아님.
- 증상1 = `e2f0033` 의 "평면 미인식 → 카메라 앞 0.8m fallback 앵커": 크기가
  선택값이 아니라 기본 1.2×0.7 인데 얼굴 앞 0.8m 에 세워 화면을 다 덮음.
- 증상2 = (a) 자식 ImageNode 에 `worldQuaternion` 을 걸었더니 부모 pose 갱신에
  밀려 빌보드 회전이 안 먹고 평면 앵커 로컬프레임의 세로 quad 로 남음(막대),
  (b) `resolveWall` 이 상/하단 평면 hitTest 로 높이를 재 지평선 근처에서 폭발/
  종횡비 붕괴.

**수정 (commit `<이번>`):**
- `resolveWall`: 폭 = 좌·우 변 hitTest 실거리(한쪽만이면 중심~그쪽×2), **높이 =
  폭 × 화면 선택 사각형 종횡비**(`rect.height()/rect.width()`). 상/하단 평면
  hitTest 와 4m 폭발 캡(`683a9ea`) 제거 — 커버 quad 가 항상 "내가 그린 박스" 모양.
- `applyResult`: 카메라 앞 fallback 앵커 삭제 → 평면 없으면 전체화면 프리뷰만.
- `onFrame`: 빌보드 회전을 **부모 AnchorNode** 에 건다(`node.worldQuaternion`).
- `COVER_MARGIN` 1.35 → 1.12 (살짝만).
- 빌드 `:app:assembleDebug` 성공.

기대 로그: `resolveWall: patchW=... patchH=... (edges L R · screenAspect=... · center=)`,
`buildResultNode ... billboard=true cover=WxH` (W:H = 선택 박스 비율).

## 조사 — "화면에 두 UI가 겹쳐 보인다" 는 레이아웃 버그 아님 (2026-09-09)

Branch `integration-interior-demo-temp`. 리포트: 상단 "화보 / 내 공간에 배치" 와
하단 "지운 사물 편집 / 사물 종류: 의자" 가 동시에 떠서 Activity/Fragment 두 개가
겹쳐 렌더링되는 것으로 의심.

**결론: 버그 아님. `MainActivity` 하나 · `activity_main.xml`(FrameLayout) 하나인
통합 워크스페이스이고, 이 두 패널 동시 표시는 설계된 동작이다. 의자 삭제도 정상 처리됨.**

- **상단 "화보 / 내 공간에 배치" = `arTopPanel`** — 고정 헤더 바(`‹ 화보` 뒤로가기 +
  "내 공간에 배치" 제목 TextView + 설정 톱니 + `instructionText`). user1(goguma-salad)
  이 `9bbea8c`/`697b1ed` 에서 만든 통합 AR 화면의 상단 바. **카탈로그/화보 화면이 아님** —
  `catalogPanel`·`homeScreen` 은 `setupUnifiedWorkspace()` + XML 기본값으로 둘 다 `GONE`,
  `CatalogController` 는 `MainActivity` 에 인스턴스화조차 안 됨(화보 브라우징은 별도
  `CatalogActivity` 런처 담당).
- **하단 "지운 사물 편집" = `movedObjectPanel`** (+ `removalTools`>`removalTypeRow` "사물 종류").
  `MovedObjectController.arm()` 이 삭제 성공 직후 띄운다 → **삭제 성공 신호**이지 잔상 아님.
- `WorkspaceScrollView.onMeasure` 가 높이를 화면 48% 로 제한 → 카메라 시야 확보. `arTopPanel`
  (top-gravity) + `WorkspaceScrollView`(bottom-gravity) 가 위·아래 띠로 공존 = 설계.
- **merge 로 빠진 로직 없음**: `agent/shinym87/interior_dev` 는 `MainActivity` 의 패널
  visibility 를 한 줄도 안 건드렸고(`git diff 3b98b9a..interior_dev` visibility/Panel 라인 0건),
  merge(`f3f291e`) 결과에 `setupUnifiedWorkspace()` 온전. 두 패널 사이 상호배제 로직은
  애초에 없었다(통합 설계 전제).
- **의자 삭제 = 성공** (logcat 13:48:01): `runFlow done → onRemovalApplied(type=chair
  hasBmp=true hasPose=true)` → `applyResult: 빌보드 커버 quad` → `buildResultNode
  billboard=true cover=1.58x0.94m` → `arm: type=chair markerPlaced=true`. `runFlow` 는
  서버 job `done` + 결과 이미지 디코드까지 끝나야 `onRemovalApplied` 를 부르므로 서버
  remove-object 정상 완료. UI 겹침이 삭제를 막지 않았다.

**후속(선택, 미적용)**: UX 산만함은 사실 — `movedObjectPanel` 표시 중엔 `removalTools`
자동 접기 같은 상호배제를 넣을 수 있으나 user1 통합 워크스페이스 설계라 합의 후 반영.

## 진단 로그 추가 — 이동 후 원래 자리에 남는 "반투명 잔상" (2026-09-08)

Branch `agent/shinym87/interior_dev`. 증상: 모니터 삭제→이동 후 화면에 3개가 동시에
보인다 — (1) 실제 모니터, (2) 원래 자리 근처 반투명 잔상, (3) 새 자리의 이동된 모니터.
(2)의 정체를 A/B 로 나눠 로그를 넣었다. **아직 수정은 안 함** — 실기기 재현으로 원인
확정 후 대응.

### (2)는 `RemovalController.resultNode`(커버 quad)일 가능성이 높다

직전 커밋에서 커버 quad 는 "결과가 있는 한 항상 렌더링" 하도록 바꿨다. 즉 원래 자리에
반투명(EdgeFade 알파 램프) 패치가 계속 떠 있는 건 **의도된 동작**이다. 문제는 그게
실제 모니터와 **어긋나** 둘 다 보인다는 것. 두 원인 후보:

**A) 드래그 노드 정리 누락?** — 코드상으로는 아님.
- `MovedObjectController` 의 이동 마커는 `node` **하나뿐**이다. `onDrag` 는 같은 `node`
  의 `pose` 만 바꾸고, `onDragEnd` 는 **노드는 그대로 두고 `anchor` 만 교체**한다
  (새 노드/마커 생성 없음). 원래 자리에 남을 임시 노드가 없다.
- 새 노드는 `setNode()` 에서만 생기고, `setNode` 는 맨 앞에서 `clearMovedNode()`
  (remove + `anchor.detach()` + `destroy()`) 를 부른다. `arm`/`disarm`/`undo`/"치우기"
  도 `clearMovedNode`.
- 로그로 확인: `setNode: … node#<id>`, `clearMovedNode: node#<id> 제거`,
  `onDragEnd: node#<id> 같은 노드 재고정(새 노드/마커 생성 안 함)`,
  `[moved A] node#<id> anchorPos=… dragging=…`(60프레임마다). **node# 가 계속 하나로
  유지되고 drag 후 anchorPos 가 "새 위치" 면 A 아님.**

**B) 평면 이미지 재투영 한계 (시야각 어긋남)** — 유력.
- 커버 quad 는 한 시점에서 찍은 **평면 텍스처 1장**을 한 앵커에 붙인 것이다. 생성 때와
  다른 각도/위치에서 보면 실제 3D 장면의 시차(parallax)를 못 살려 실물과 어긋난다.
- 로그: `[cover B] 커버 생성시점 대비 카메라 Δ이동=…m Δ회전=…° · 현재 카메라→커버앵커=…m
  · anchorΔ … · track=…`(60프레임마다). **Δ이동/Δ회전이 크고 그때 잔상이 심해지면 B.**
- `buildResultNode: node#… anchorPose=… camAtBuild=…` — 커버 quad 앵커·생성 시점 카메라.

**B의 하위 원인 — 앵커가 애초에 엉뚱한 곳:** 선택 시점에 `wallAnchor` 를 못 잡았으면
`applyResult`/`onFrame` 이 `hitTestSourceRegion` 으로 **선택 시점의 화면 좌표(정규화
bbox 중심)** 를 지금 다시 hitTest 한다. 그 사이 카메라가 움직였으면 같은 픽셀이 다른
월드 지점을 가리켜 커버 quad 가 실제 모니터에서 벗어난 곳에 박힌다.
- 로그: `hitTestSourceRegion: 화면(x,y)px → hitPose=… · 현재 camPose=…`. **선택할 때와
  결과 왔을 때 camPose 가 크게 다르면 이 경로가 범인.**

### 추가한 로그 (tag `InteriorAR`, 모두 `TEMP-DIAG` 주석)

| 위치 | 로그 | 무엇을 보나 |
|---|---|---|
| `RemovalController.buildResultNode` | `buildResultNode: node#… anchorPose=… camAtBuild=…` | 커버 quad 생성 위치·시점 카메라 |
| `RemovalController.hitTestSourceRegion` | `hitTestSourceRegion: 화면(x,y)px → hitPose=… camPose=…` | 스테일 화면좌표 재투영 여부 |
| `RemovalController.onFrame` (60f) | `[cover B] … Δ이동 Δ회전 … 카메라→커버앵커 … anchorΔ … track=…` | 시야각 어긋남(B) 정량 |
| `RemovalController.clearResult` | `clearResult: 커버 quad node#… 제거` | 커버 quad 소멸 시점 |
| `MovedObjectController.setNode` | `setNode: 이동 마커 node#… anchorPose=… camAtCreate=…` | 마커 생성(개수/위치) |
| `MovedObjectController.clearMovedNode` | `clearMovedNode: node#… 제거` | 마커 소멸 |
| `MovedObjectController.onDragEnd` | `onDragEnd: node#… 같은 노드 재고정(새 노드 안 만듦) …` | A(드래그 잔여 노드) 배제 |
| `MovedObjectController.onFrame` (60f) | `[moved A] node#… anchorPos=… track=… dragging=… · 생성 후 카메라 Δ…` | 마커가 하나로 유지되는지 |

빌드 `:app:assembleDebug` 성공.

### 재현 시 볼 것

1. `setNode` 로그가 이동 1회당 몇 번 찍히나 (1번이어야 A 아님). `[moved A]` 의 node# 가
   계속 같은지, `clearMovedNode` 없이 `[moved A]` 가 두 줄씩 안 나오는지.
2. 삭제 영역 선택할 때 vs 결과 왔을 때 `camPose` 차이 (`hitTestSourceRegion` 로그) —
   크면 커버 앵커가 잘못 박힌 것(B 하위).
3. 잔상이 심한 순간 `[cover B]` 의 Δ이동/Δ회전 값 — 클수록 재투영 한계(B).
4. `anchorΔ` 가 계속 튀면 앵커 표류(ARCore 재추적).

## 진단 + 수정 — "결과 닫기 (라이브로)" 시 커버 quad 도 같이 꺼져 사물이 2개로 보임 (2026-09-08)

Branch `agent/shinym87/interior_dev`. 증상: "삭제 결과 보기" → "결과 닫기 (라이브로)"
로 전환하면 삭제 자리를 가려주던 결과 오버레이가 사라지고 실제 모니터가 다시 보인다.
거기에 이동시킨 모니터 이미지까지 새 자리에 있어 모니터가 2개.

### 1. "결과 닫기 (라이브로)" 핸들러

`RemovalController.toggleBeforeAfter()` (init 에서 `btnToggleRemoval` 클릭에 연결).
**정지 화면 on/off 와 앵커 고정 결과 quad 의 표시/숨김을 하나의 `showingAfter`
불리언으로 같이 건드리고 있었다:**

```kotlin
fun toggleBeforeAfter() {
    showingAfter = !showingAfter
    resultNode?.isVisible = showingAfter          // ← 월드 앵커 커버 quad 까지 껐다
    if (resultOverlay.drawable != null) resultOverlay.visibility = if (showingAfter) VISIBLE else GONE
    ...
}
```

`onFrame()` 도 `if (!node.isVisible && showingAfter) node.isVisible = true` 라, 한 번
끄면 다시 안 켜졌다.

### 2. 원인 확인 → 맞음. 게다가 사용자 케이스엔 커버 quad 가 아예 없었다.

- 두 관심사(전체화면 프리뷰 / 월드 커버 quad)가 `showingAfter` 하나에 묶여 있었다. ✅
- 추가로: "삭제 결과 보기" / "결과 닫기 (라이브로)" **버튼 텍스트는 `resultNode == null`
  일 때만** 나오는 분기였다. 즉 사용자 세션에선 선택 시점에 `wallAnchor` 를 못 잡아
  **커버 quad(`resultNode`) 가 처음부터 만들어지지 않았고**, 2D 전체화면 이미지만
  있었다. 그걸 닫으면 실제 모니터가 그대로 → 이동 마커와 합쳐 2개.

### 3. 수정 — "정지화면 표시"와 "커버 quad 표시"를 분리

`RemovalController`:

- **커버 quad (`resultNode`) 는 결과가 있는 한 항상 렌더링.**
  `onFrame()` 이 추적 상태만 보고(`STOPPED` 면 숨김, 아니면 `isVisible = true`)
  관리한다. `toggleBeforeAfter` 는 이제 이 노드를 **건드리지 않는다**.
- **선택 시점에 평면이 없어도 커버 quad 를 만든다.** `applyResult` 에서 `wallAnchor`
  가 null 이면 그 자리에서 사물 영역(`hitTestSourceRegion`, 정규화 bbox 중심)을
  hitTest 해 앵커를 잡는다(결과가 도착한 이 무렵엔 대개 평면이 잡혀 있음). 그래도
  없으면 `awaitingCoverAnchor=true` → `onFrame` 이 매 프레임 재시도해 잡히는 즉시
  `buildResultNode`. (전체화면으로 덮지 않고 라이브 유지 — 이전 fallback 수정과 일관.)
- **`toggleBeforeAfter()` 는 전체화면 프리뷰(`resultOverlay`)만** on/off.
  버튼 텍스트도 항상 "삭제 결과 보기" ↔ "결과 닫기 (라이브로)" 하나로 통일.
  (앵커 유무로 갈라지던 "삭제 전(원본)/삭제 후(보임)" 텍스트·동작 제거 — 커버는 늘
  떠 있어야 하므로 "패치를 숨겨 원본과 비교" 기능은 의도적으로 없앴다.)
- `applyResult`: 전체화면 프리뷰용 `full` 비트맵은 앵커 유무와 무관하게 항상
  `resultOverlay` 에 세팅(기본 `GONE`).
- `captureSceneJpeg`: 캡처 후 복구를 `resultNode?.isVisible = showingAfter` →
  `= true` 로.
- 새 헬퍼: `hitTestSourceRegion(region)`, `buildResultNode(anchor, isVertical, patch)`.
  새 상태: `awaitingCoverAnchor`, `pendingCoverPatch`, `pendingCoverRegion`
  (`clearResult` 에서 함께 정리).

### 4. 이동 기능과의 상호작용 → 정상 (동시 표시)

`RemovalController.resultNode`(원래 자리 커버)와 `MovedObjectController.node`(새 자리
이동 마커)는 **서로 독립된 노드**다. 한쪽이 생겨도 다른 쪽을 지우는 코드 경로가 없다.
- 삭제 → 커버 quad 가 원래 자리에 고정(실제 모니터 가림).
- `moved.arm()` → 이동 마커가 (originalPose 있으면 그 자리, 없으면 사물 영역) 에 생성.
  처음엔 커버와 같은 자리에 겹쳐 뜨고, 드래그해서 새 자리로 옮기면 커버는 원래 자리에
  그대로 남는다 → **지운 자리는 가려지고, 새 자리엔 사물이 보이는** 그림.
- 둘을 함께 지우는 건 `clearSelection()`(명시적 "선택 취소") → `clearResult()` +
  `onRemovalCleared()` → `moved.disarm()` 뿐.

빌드 `:app:assembleDebug` 성공.

## 진단 + 수정 — 이동된 사물이 원본보다 ~1.5배 크게 표시 (2026-09-08)

Branch `agent/shinym87/interior_dev`. 텀블러(=objectType `other`)를 삭제 후 이동하니
마커 이미지가 원본보다 약 1.5배 크게 보였다.

### 1. 이동 사물 quad 크기가 결정되는 경로

```
RemovalController.resolveWall(rect)
  bbox 네 변(rect.left/right/centerY, centerX/top/bottom)에서 space.hitTest → hitPose
  patchWidthM  = distance(left, right)   // 3D 거리(m), coerceIn(0.2, 4)  ← 기존
  patchHeightM = distance(top,  bottom)  // 3D 거리(m), coerceIn(0.2, 4)  ← 기존
        │  (RemovalController.runFlow → onRemovalApplied 의 마지막 두 인자)
        ▼
MovedObjectController.arm(… widthM=patchWidthM, heightM=patchHeightM)
  baseW = widthM.coerceIn(0.15, 3)      ← 기존
  baseH = heightM.coerceIn(0.15, 3)     ← 기존
        ▼
setNode() → ImageNode(size = Size(baseW, baseH))   // 월드 미터 단위 quad
applyChildTransforms(): imageNode.scale = Scale(scaleF * MARKER_SCALE)
  scaleF = 1 (초기), MARKER_SCALE = 1.35   ← 여기!
```

- **정규화 bbox × 해상도로 픽셀 크기를 구하나?** — 아니다. bbox 네 변의 화면 좌표에서
  직접 `hitTest` 하고, 맞은 **3D 점들 사이 유클리드 거리(m)**를 크기로 쓴다.
- **픽셀 → 미터 변환 공식?** — 별도 변환 없음. hitTest 가 이미 월드 좌표(m)를 준다.
  즉 "hitTest 거리 기반" 이 맞고, 원근 계산은 hitTest 내부(ARCore)에서 처리된다.

### 2. 카메라-사물 거리 차이는 반영되는가 → **이미 올바르게 반영됨**

실제 크기를 **삭제 당시 hitTest 로 잰 미터값**으로 저장하고(`patchWidthM/HeightM`),
새 위치의 quad 도 `Size(baseW, baseH)` = **월드 미터** 로 만든다. 월드 미터 quad 는
보는 거리가 달라지면 화면상 크기가 원근으로 자동 조정된다 — 실제 사물과 동일.
따라서 "삭제 거리 ↔ 이동 거리" 차이는 **재계산할 필요가 없고, 이미 맞다.**
(사용자가 제안한 "원래 거리 기준 cm 저장 후 유지" 는 현재 코드가 이미 하는 일.)

남는 오차는 **측정 자체의 원근 과대추정**이다: bbox 좌/우 변을 지나는 광선이
사물 앞면이 아니라 그 뒤 지지면(책상)에 맞아, 두 교点 간격이 사물 실제 폭보다
약간 넓게 나온다. 깊이 없이는 정밀 보정이 어려워 임시 노브로 처리(아래 4).

### 3. 512px 다운스케일 → **크기 버그와 무관**

`MovedObjectController.downscale()` 는 가장 긴 변을 512(`MAX_TEX`)로 맞추되 **가로/세로에
같은 계수 `f`** 를 곱한다 → 종횡비 보존. 게다가 quad 월드 크기(`Size(baseW, baseH)`)와
**독립**이다(텍스처 해상도만 바뀜). `EdgeFade.feather` 도 치수 불변(가장자리 alpha 램프
뿐, 오히려 불투명 영역이 ~16% 작아 보이게 함).
다만 `baseW/baseH` 를 이미지 종횡비와 무관하게 **각각 hitTest 로** 재던 탓에 quad 비율이
이미지와 어긋나 늘어나 보일 수 있었다 → 이번에 세로를 이미지 종횡비로 유도하도록 수정.

### 원인 정리

| 요인 | 영향 | 조치 |
|---|---|---|
| **`MARKER_SCALE = 1.35`** (commit a5d089e, "터치하기 쉽게") | quad 를 항상 1.35× 확대. 마커는 `isTouchable=false` 고 드래그는 화면 좌표 기반이라 **터치 이득 0** — 순수 부작용 | `1.0` 으로 되돌림 (주 원인) |
| `patchWidthM/HeightM` `coerceIn(0.2, 4)` + `baseW/baseH` `coerceIn(0.15, 3)` | 텀블러(~7–9cm)가 15–20cm 로 바닥 클램프 → 최대 2–3× 과대 | 하한 `0.05m` 로 낮춤 |
| `baseW`·`baseH` 를 각각 독립 hitTest | quad 종횡비 ≠ 이미지 종횡비 → 늘어남 | 폭만 실측, 세로는 크롭 이미지 종횡비로 유도 |
| bbox 가장자리 hitTest 의 원근 과대추정 | 폭이 실제보다 약간 큼(잔차) | `MOVED_SCALE_CORRECTION` 노브 |

### 적용 (수정)

- **`MovedObjectController`**
  - `MARKER_SCALE = 1.35f → 1f`. `applyChildTransforms` 의 `disp = scaleF * MARKER_SCALE
    * MOVED_SCALE_CORRECTION`.
  - `MOVED_SCALE_CORRECTION = 1f` 추가 (companion 상수). **크기 계산이 전부 클라이언트라
    서버 env 가 아니라 앱 상수다.** 실기기에서 크게 나오면 `0.67` 등으로 내리고
    `:app:assembleDebug`(증분 ~40s) 재설치.
  - `arm()`: `baseW = (widthM * MOVED_SCALE_CORRECTION).coerceIn(0.05, 3)`,
    `baseH = baseW * cropBmp.height / cropBmp.width` (이미지 종횡비 유지, 없으면
    `heightM` 폴백). 하한 0.05m. 계산값 `Log.d(TAG, "arm size: …")` 로 남김.
- **`RemovalController.resolveWall`**: `patchWidthM/HeightM` `coerceIn(0.2,4) → coerceIn(0.05,4)`.
  `Log.d(TAG, "resolveWall: patchW=… patchH=… edges=…")` 추가.
- 빌드 `:app:assembleDebug` 성공.

### 알려진 잔여 이슈 (이번 범위 밖)

- **서버 배치 복원 시 실제 크기 유실**: `savePlacementNow` 는 `scaleF` 만 저장하고
  `baseW/baseH` 는 저장/복원하지 않는다 → `restoreFromServer` 후 `baseW=baseH=0.6`(필드
  기본값)으로 뜬다. 같은 세션 내 삭제→이동에는 영향 없음. 서버 스키마에 `base_w/base_h`
  (또는 `source_region` + `plane_distance`)를 추가하면 근본 해결.

## 정리 — "배경 촬영 / 배경 표시" 기능 데모 UI 에서 숨김 (2026-09-08)

Branch `agent/shinym87/interior_dev`. 피드백: 이 기능 효과가 잘 안 느껴진다.

### 확인한 것

1. **켜졌을 때 실제 효과** — `BackgroundKeyframe.capture()` 가 현재 카메라
   프레임(가구 AR 노드만 숨김, 실제 물리 가구는 그대로)을 `PixelCopy` 로 찍어
   `empty_background.png` 저장 → `배경 표시` 를 누르면 그 **정지 이미지**를
   `backgroundOverlay`(match_parent ImageView) 에 `alpha≈0.5` 로 겹친다.
   - 문제 (a): 같은 방의 정지 사진 ↔ 같은 방의 라이브 영상을 반투명 블렌딩 →
     차이가 거의 없어 "아무 일도 안 일어난 것"처럼 보인다.
   - 문제 (b): 오버레이가 **카메라를 안 따라간다**(2D 고정). 폰을 조금만 움직여도
     프레임이 어긋나 유령처럼 겹친다 — 시연에서 오히려 버그처럼 보인다.
   - 문제 (c): "변경 전/후 비교" 목적은 이미 `RemovalController` 의 **`삭제 전/후`**
     토글(`btnToggleRemoval`)이 담당한다 — 그쪽은 실제 AI 결과와 원본을 비교하므로
     훨씬 설득력 있다. 배경 오버레이는 그와 중복.

2. **`opacitySeekBar` 가 화면에 보이나?** — 버그로 숨은 게 아니다.
   `BackgroundKeyframe.show(visible)` 가 `opacityBar.visibility = VISIBLE` 로
   토글하므로 `배경 표시` 를 누르면 나타난다. 다만 위치가 나쁘다 — 상단
   컨트롤 스택(안내문 → 배경/가구 버튼 → **슬라이더** → 서버주소 입력 → 스피너 →
   삭제 버튼들 → 상태문)에 끼어 있어 라벨도 없고 눈에 안 띈다. (이번에 숨김 처리로 무의미해짐.)

3. **PHASE 9 / 2분 시연에 필요한가? — 아니다.**
   현재 제출 문서 `experiments/shinym87/interior/NOTION_SUBMISSION.md` 의 2분 시연
   시나리오(0:00–2:00)에 배경 촬영/표시 단계가 **없다**. "전후 비교와 가치"(1:52–2:00)는
   삭제 결과 토글 + 이동/카탈로그 흐름으로 전달된다. PHASE 0 `phase-0.md` 시연
   시나리오 6번("`배경 표시` 토글로 변경 전/후 비교")의 잔재이며, 그 역할은
   `삭제 전/후` 로 대체됐다.

### 적용

- `activity_main.xml`: `btnCaptureBg` · `btnToggleBg` · `opacitySeekBar` 를
  `visibility="gone"`. `backgroundOverlay` 는 원래 gone. 되돌리는 법을 주석에 명시.
- `BackgroundKeyframe.kt` 와 `MainActivity` 배선은 **그대로 유지** — 세 위젯을
  `visible` 로만 바꾸면 부활. `MainActivity` 에 이유 주석.
- `README.md` 사용 방법 7번 / 기능표 갱신.
- 빌드: `:app:assembleDebug` 성공.

### 남은 판단 (원하면)

되살릴 가치가 있으려면 오버레이를 **카메라 추적**에 얹거나(정지 프레임이 아니라
캡처 시점 pose 기준 빌보드/평면 투영), 애초에 이 기능을 접고 완전 제거(옵션 3)해도
된다. 지금은 코드만 남기고 UI 만 숨긴 상태.

## 진단 + 수정 — "삭제 완료 후 이동이 안 먹힘 / 화면이 멈춘 듯" (2026-09-08)

Branch `agent/shinym87/interior_dev`. 증상: 사물 삭제가 끝난 화면에서 탭·드래그가
무반응, 화면이 정지한 것처럼 보이고 바닥 평면 점(dot)도 새로 안 뜬다.

goguma-salad 가 다른 브랜치에서 같은 건을 이미 보고했다
(`docs/handoffs/interior-removal-fallback.md`, Severity High). 원인 분석이 일치한다.

### 요청한 4가지 확인

1. **ARCore 세션 / onFrame 이 계속 도는가? → 돈다.**
   `ArSpaceController.onSessionUpdated` 는 SceneView GL 렌더 스레드가 돌리며 어떤
   View 오버레이와도 무관하다. 삭제/이동 흐름 어디에서도 세션·lifecycle 을 멈추지
   않는다. 화면이 "멈춘 것처럼" 보이는 건 **정지 이미지가 카메라를 덮고 있어서**다.
   → 확인용으로 `onFrame heartbeat #N tracking=… planes=…` 로그를 약 2초마다 찍게 했다
   (tag `InteriorAR`). 삭제 후에도 이 줄이 계속 나오면 세션은 살아있다.

2. **오버레이/패치가 터치를 가로채는가? → `resultOverlay` 가 화면을 덮는 게 핵심.**
   `activity_main.xml` 의 `@id/resultOverlay` 는 `match_parent` × `match_parent`
   `ImageView`. `RemovalController.applyResult()` 는 **벽/바닥 앵커(`wallAnchor`)를
   못 잡았을 때** 서버가 준 전체 결과 Bitmap 을 이 오버레이에 넣고 `VISIBLE` 로
   만들고, 그대로 무기한 남는다. `clickable=false` 라 터치 이벤트 자체는 아래
   `sceneView` 로 통과하지만, **라이브 카메라·평면 격자/점·삭제 후 뜨는 이동 마커가
   전부 이 정지 이미지에 가려진다.**
   - `movedObjectPanel` 은 `wrap_content` 하단 패널이라 버튼 영역만 차지 — 무관.
   - `backgroundOverlay`(gone), `bboxSelectionView`(선택 후 gone) — 무관.

3. **"여기로 옮기기 버튼 → 탭" → "삭제 즉시 드래그" 변경이 제대로 적용됐는가? → 됐다. 충돌 없음.**
   커밋 `a5d089e` 확인: `btnMovedPlace` 레이아웃에서 제거, `MovedObjectController`
   의 `placing`/`onTap()` 경로 삭제, `MainActivity.onSingleTapConfirmed` 에서
   `moved.onTap` 제거, `space.onFrame` 에 `moved.onFrame()` 추가, `canManipulate()`
   에서 `!placing` 제거. 드래그 경로(`onDragBegin/onDrag/onDragEnd`)는 살아 있다.
   → 진짜 문제는 로직 충돌이 아니라, **앵커가 없는 경로에서 `arm()` 이 마커를 못
   띄운다**는 것: `originalPose == null`(= `wallAnchor?.pose`) + `source_region`
   hitTest 도 평면이 없어 실패 → `node == null` → `canManipulate()` 가 false →
   마커에 대한 탭·드래그가 전부 no-op. 여기에 2번의 전체화면 오버레이가 겹쳐
   "완전히 멈춘 화면"으로 보인다.

4. **logcat 진단 로그 (임시, tag `InteriorAR`, 코드에 `TEMP-DIAG` 주석)**
   - `MainActivity` 제스처: `[gesture] tap …`, `[gesture] moveBegin … movedTook=`,
     `[gesture] move #N … movedTook=`(15회마다), `[gesture] moveEnd movedTook=`.
     → 터치가 앱에 도달하는지, 이동 컨트롤러가 먹는지.
   - `MovedObjectController`: `arm: … markerPlaced= awaitingPlane=`,
     `placeMarkerNow: …`(어느 경로로 마커를 놓/못 놓았는지), `onDragBegin … canManipulate=`,
     `onDrag: hitTest 없음 …`, `onFrame: 이동 마커 배치 성공`.
   - `RemovalController`: `runFlow done → onRemovalApplied(… hasPose=)`,
     `applyResult: 벽 앵커 quad 경로` / `applyResult: 벽 앵커 없음 → …`.
   - `ArSpaceController`: 위 heartbeat.

### 적용한 수정

- **`RemovalController.applyResult()`** — 앵커가 없어도 전체화면으로 덮지 않는다.
  결과 Bitmap 은 `resultOverlay` 에 넣어두되 `GONE` 으로 두고, 라이브 카메라를
  유지한다. `btnToggleRemoval` 이 "삭제 결과 보기" ↔ "결과 닫기 (라이브로)" 로
  동작해 필요할 때만 프리뷰를 연다. 앵커가 있으면(벽 quad) 기존 동작 그대로.
- **`MovedObjectController.placeMarkerNow()`** — 마지막 fallback 추가: 평면을 전혀
  못 잡으면 **카메라 앞 ~1.2m** 에 마커를 띄운다. 평면 고정은 아니지만 사용자가
  바로 붙잡아 끌 수 있고, `onDrag` 의 hitTest 가 평면 위에서 다시 재고정한다.
  이로써 삭제 직후 거의 항상 `node != null` → `canManipulate()` true → 드래그가 먹는다.
- 위 진단 로그. **데모 안정화 후 `TEMP-DIAG` 표시 줄은 제거할 것.**

### 빌드

`JAVA_HOME=C:\Users\User\.jdks\jbr-21.0.11` + `.\gradlew.bat :app:assembleDebug`
→ **BUILD SUCCESSFUL**. APK: `experiments/shinym87/interior/app/build/outputs/apk/debug/app-debug.apk` (약 47MB).

### 실기기에서 좁힐 것

- 삭제 완료 직후 `onFrame heartbeat` 가 계속 찍히는지 (세션 생존 확인).
- `applyResult:` 로그가 "벽 앵커 quad 경로" 인지 "벽 앵커 없음" 인지 → 어느 경로 버그인지 확정.
- `arm: … markerPlaced=true` 인지, `placeMarkerNow:` 가 어느 단계에서 성공/실패하는지.
- 드래그 시 `[gesture] moveBegin … movedTook=true` + `onDragBegin … canManipulate=true` 가 뜨는지.
- 마커가 눈에 보이는지(전체화면 오버레이 제거 후) — 안 보이면 마커 배치 좌표/스케일 문제로 좁힌다.

## PHASE 5 — 가구 카탈로그 썸네일 서빙 (2026-09-03)

사용자 1 이 카탈로그 배치 UI 를 만들었지만 서버가 `/assets/*` 를 안 줘서 5종 모두 큐브
폴백이었다. 썸네일 이미지를 만들고 정적 서빙을 붙였다.

1. **썸네일 이미지** (`scripts/make_furniture_thumbnails.py`)
   - 무료 스톡 사진 대신 **Pillow 라인아트 카드**로 생성 (라이선스/네트워크 무관, 결정적).
     흰 라운드 카드 + 카테고리별 색 외곽선 가구 + 한글 이름(Malgun Gothic).
   - 출력: `catalog/assets/furniture/{tv,sofa,table,chair,shelf}.png` (640×640 RGBA,
     각 ~5KB, git 포함). 다시 만들려면 스크립트 재실행.
2. **정적 서빙** (`app/main.py`, `app/config.py`)
   - `app.mount("/assets", StaticFiles(directory=settings.assets_dir))` — `assets_dir` 은
     기존 설정값 `catalog/assets/`(`INTERIOR_ASSETS_DIR` 로 override 가능). `get_settings()`
     가 이 디렉터리도 mkdir 한다.
   - `catalog/furniture.json` 의 `thumbnail` 을 `/assets/furniture/<종류>.png` 로 갱신
     (기존 `/assets/tv-wall-55.png` 등 존재 안 하던 경로 → 실제 파일).
   - `model.url`(glb)은 손대지 않음 — 파일 없고 앱도 안 씀.
3. **검증**
   - 실서버: `GET /catalog` 의 5개 `thumbnail` 을 각각 열어 `200 image/png`,
     유효 PNG 640×640, 없는 파일은 404 확인.
   - 테스트: `tests/test_api.py::test_catalog_thumbnails_are_served` — 모든 항목의
     thumbnail 이 `/assets/furniture/` 로 시작하고 `client.get` → 200 + PNG 시그니처.
     `pytest` **43개 통과**, mock e2e 회귀 없음.
   - 앱 쪽은 코드 변경 불필요 — `CatalogController` 가 이미 `thumbnail` URL 을 받아
     `downloadBytes` 하므로, 서버만 붙이면 카탈로그 목록 선택 시 이미지 quad 로 뜬다.

향후: 실제 제품 사진으로 교체하고 싶으면 같은 파일명으로 `catalog/assets/furniture/` 에
덮어쓰면 된다. (썸네일 배경 투명화는 지금 라인아트라 불필요.)

## PHASE 4 — 제거된 사물 크롭 서버 저장 + 결과 API 필드 (2026-09-03)

### 1. 판단: 서버에도 저장한다 (조건부로 의미 있음 → 거의 공짜라 채택)

- 앱(사용자 1)은 이미 삭제 요청 시점에 **클라이언트에서** 키프레임을 bbox 로 잘라
  (`RemovalController.capturedObjectBitmap`) 이동 배치에 쓴다 → **현재 단일 기기 데모
  흐름만 보면 서버 저장은 불필요**하다.
- 그래도 저장하기로 한 이유:
  - **AI 호출이 전혀 없다** (Pillow crop 한 번). 디스크만 조금 더 쓴다.
  - 결과 API 가 self-contained 해진다 — `keyframe` / `result` / `removed_object` 세 장이
    모두 URL 로 나와, 다른 기기·다음 세션·웹 뷰어가 "삭제 전/후/이동"을 서버 URL 만으로
    재구성할 수 있다 (사용자가 지적한 "크로스 기기/세션 재사용" 용도).
  - 나중에 배경 투명 컷아웃(아래 3번)을 넣으면 **같은 경로/URL 자리에** PNG 로 갈아끼우면
    되므로, 지금 자리를 잡아두는 게 이득.
- 끌 수 있다: `INTERIOR_SAVE_REMOVED_OBJECT_CROP=false`.

### 2. 구현

- `app/ai/imageops.py::crop_normalized_jpeg(image_bytes, rect)` — 정규화 `[x,y,w,h]` 로
  잘라 RGB JPEG. 경계 클램프 + 최소 1px.
- `_run_job`: 결과 저장 직후(`status=done` 이후, 개수 정리 전) `region_bbox(region)` 으로
  원본 키프레임을 잘라 `data/scenes/{scene}/results/{job_id}_object.jpg` 저장,
  job 에 `removed_object_path`/`removed_object_url` 기록. 실패해도 job 은 done 유지(부가 산출물).
- `store.jobs` 에 `removed_object_path`/`removed_object_url` 컬럼(+ 기존 DB용 ALTER 가드).
- `GET /scenes/{id}/results` 응답에 **`removed_object_image_url`** 추가 (파일 있을 때만).
- `GET /scenes/{id}/results/{job}_object.jpg` 신설 — `{job}.jpg` 라우트보다 **먼저** 등록해
  `_object.jpg` 가 여기로 매칭되게 함. 없으면 404, 정리됐으면 410.
- `cleanup.py`: 개수 정리는 메인 결과(`{job}.jpg`)만 세고(`_object` 접미사 제외), 메인을
  지울 때 동반 크롭도 함께 unlink. 기간 정리는 오래된 `.jpg` 를 종류 구분 없이 지운다.
- 테스트: `test_results.py` — 목록에 `removed_object_image_url` + 다운로드 확인,
  개수 정리 시 `_object.jpg` 동반 삭제/410, `crop_normalized_jpeg` 단위. `pytest` 36개,
  mock e2e 14/14 (`{job}_object.jpg` 생성·DB URL 채워짐 확인).

### 3. 배경 투명 컷아웃 — 지금은 검토만 (idea-backlog 기록)

지금 크롭은 사각형이라 배경이 딸려온다. 사물 윤곽만 분리(알파 투명)하는 방법 후보:

| 방법 | 비용/무게 | 품질 | 메모 |
|---|---|---|---|
| Gemini 후속 1콜 ("removed 사물만 투명 배경 PNG 로") | AI 1콜(~$0.039)·지연 +수초 | 선명한 사물엔 양호, 가는/털 경계 약함 | 이미 키프레임+마스크를 보내니 프롬프트만 추가하면 됨 (가장 저마찰) |
| `rembg`(u2net) 오프라인 | 모델 ~170MB·CPU 1~2s | 범용적으로 무난 | 무거운 의존성 추가 |
| OpenCV GrabCut (bbox seed) | 가벼움(opencv 이미 후보) | 잡동사니 책상에선 중간 이하 | 추가 AI 비용 0 |
| ML Kit Subject Segmentation | 온디바이스 | 사람 위주 튜닝 | 가구/소품엔 부적합 |

**판단: 지금은 구현하지 않는다.** 이동 배치는 개념 증명이고 `EdgeFade` 페더링된
사각형이 "사물 사진 카드"로 충분히 읽힌다. 투명 컷아웃은 (a) 반복 AI 비용/지연 또는
(b) 170MB 의존성을 부르는데, 데모 가치 대비 과함. 크롭 경로/URL 을 이번에 고정해 뒀으니
나중에 `crop_normalized_jpeg` → 컷아웃 함수로 바꾸고 `.jpg`→`.png` 만 하면 됨.
`experiments/shinym87/interior/docs/backlog.md` 아이디어란에 방법 비교와 함께 기록.

## PHASE 3 — objectType 범용값(other) + 잘못된 힌트 방지 프롬프트 (2026-09-03)

실기기 사례: 책상 위 컵을 지우려 했으나 앱 스피너가 TV 기본값이라 서버가 TV 전용
지시문("This TV is mounted on the wall. Rebuild the flat wall…")을 받아 책상 전체를
몰딩 있는 회색 벽으로 대체(할루시네이션). 서버 로그/DB 재구성으로 원인 확정.

1. **`app/ai/objects.py` — 범용 키 `other` 추가**
   - `GENERIC_TYPE = "other"`. `ALIASES` 에 `기타/소품/etc` + `cup/mug/tumbler/glass/
     bottle/vase/plant/lamp/fan/book/box/clock/...` → `other` 매핑.
   - `is_generic()` 신설. `is_known()` 은 `KNOWN_TYPES` + `other` 를 인정(경고 로그 억제).
2. **`app/ai/external.py` — 종류에 맞는 힌트만 사용**
   - `_build_prompt`: `norm == "other"` 이거나 `_SURFACE_HINTS` 에 없는 종류면
     **`_DEFAULT_HINT`** 사용 + 라벨을 `"object"` 로, 부속 문구도 소품용
     ("lid, handle, cable, small parts or contact shadow")으로 바꾼다.
     가구일 때만 "legs, base, stand" 문구.
   - `_DEFAULT_HINT` 강화: "주변에 실제로 있던 면(책상 상판/바닥/벽/선반)을 그대로 이어서
     복원. **없던 평평한 벽·빈 패널·새 표면을 만들지 말 것(Do NOT invent a plain flat
     wall…)**. 실제로 있는 것만 연장." → 회색 벽 할루시네이션 억제.
   - 회귀 확인: tv/sofa/table/couch 프롬프트 기존 문구 유지
     (`tests/test_external.py::test_prompt_is_object_type_aware` 에 other/cup 케이스 추가).
   - `tests/test_api.py::test_generic_object_type_is_accepted` (other 그대로, cup→other).
   - 앱(사용자 1): 스피너에 "기타/소품" 옵션 + "사물 종류 선택…" 초기값,
     미선택 시 '삭제 요청' 비활성화. 상세 `docs/handoffs/user1.md`.
   - `pytest` 35개 통과. mock e2e 회귀 없음.

## PHASE 3 — 색감 보정 + 이상 결과 감지 (2026-09-03)

`app/ai/colormatch.py` 신설. 기준은 **마스크(선택 영역) 밖**이다 — AI 는 그 밖을
건드리지 않아야 하므로, 그 영역의 원본↔결과 차이로 전역 이동을 잡는다.
`_run_job` 이 `ensure_jpeg_size` 직후 → 이상 감지 → 색감 보정 순으로 후처리한다.

1. **색감 보정** (`match_to_source`)
   - 마스크 밖 영역의 원본/결과 채널별 평균으로 게인 `g_c = mean_src_c / mean_res_c`
     계산, `[0.7, 1.4]` 로 클램프, `Image.point` LUT 로 결과에 곱한다(노출/화이트밸런스
     수준의 가벼운 후처리, JPEG q92 재인코딩).
   - 게인이 모두 ±3% 이내면 건너뛴다(재인코딩 안 함). 선택 영역이 화면의 85% 초과라
     바깥 표본이 부족하면 건너뛴다.
   - 로그: `[job X] 색감 보정 gains(r,g,b)=[1.08, 1.05, 1.11]`.
   - `INTERIOR_RESULT_COLOR_MATCH=false` 로 끌 수 있다.
2. **이상 결과 감지** (`check_result_anomaly`) — 명백한 케이스만
   - 리사이즈 전 결과가 64px 미만 → **fail**. 종횡비가 원본과 25% 초과 차이 → warn.
   - 마스크 밖 영역을 384px 로 다운스케일해 원본과 비교:
     - grayscale MAD ≥ `INTERIOR_RESULT_ANOMALY_FAIL_MAD`(기본 55) 또는 채널편차 ≥ 45
       → **job 을 `failed` 처리** (에러: "이상 결과 감지: 마스크 밖 영역이 원본과 크게
       다름 … AI 가 장면 전체를 바꿨거나 다른 이미지를 만든 것으로 보임").
     - MAD ≥ `..._WARN_MAD`(기본 22) 또는 채널편차 ≥ 18 → **경고 로그만**, 결과는 유지.
   - 재시도는 안 한다(잘못 생성된 이미지는 다시 해도 비슷). fail 은 곧바로 실패로 마감.
   - 초기 실기기 버그(검은 키프레임 → AI 가 상상한 방)가 이 fail 조건에 걸린다.
   - 테스트: `tests/test_colormatch.py` 8개 (전체 `pytest` 28개 통과). mock 은 마스크 밖이
     원본과 동일 → 항상 "ok", 색감 보정도 no-op → mock e2e 회귀 없음.

## PHASE 2 — 마스크 페더링 / 종류별 프롬프트 / 다양한 사물 테스트 (2026-09-03)

### 1. 마스크 경계 페더링 (`app/ai/mask.py`)
- 기존: `feather=6` **픽셀 고정** → 4K 사진에서 사실상 각진 사각형.
- 개선: `feather = 대상 사각형 짧은 변 × 0.08` (최소 8px, 이미지 12% 상한). 비율 기반이라
  큰 사물은 넓게, 작은 사물은 좁게, 해상도와 무관하게 부드럽다.
- 블러가 안쪽을 깎아도 원래 bbox 가 완전 불투명하도록 그린 사각형을 `feather×2` 만큼
  키운 뒤 블러(사물 잔털/그림자까지 덮음). 마스크 PNG 크기가 ~2KB → ~35~55KB 로 커진 것으로
  페더링 확인. `tests/test_external.py::test_mask_is_feathered_and_larger_than_rect`.

### 2. 사물 종류별 프롬프트 (`app/ai/external.py`)
- `_SURFACE_HINTS`: tv→벽/브래킷/케이블/그림자, sofa→바닥+걸레받이+접촉그림자+쿠션,
  table→연속된 바닥/타일줄눈/러그+상판 위 물건, chair→바닥, shelf→벽. 그 외는 기본 힌트.
- `_OBJECT_ALIASES`: couch→sofa, desk/coffee table→table, television/monitor→tv 등.
- `_build_prompt` 가 종류에 맞는 문장을 조립. 라벨은 입력 단어 유지(별칭도).
- `scenes.py._run_job` 은 이제 프롬프트를 만들지 않고 `prompt=""` 로 넘김(문구는 프로바이더 소유).
- `tests/test_external.py::test_prompt_is_object_type_aware`.

### 3. 다양한 사물 테스트 (Gemini `gemini-2.5-flash-image`, `scripts/e2e_check_custom.py`)
`testdata/` 에 Pexels 무료 사진 2장 추가: `pexels_sofa.jpg`(1600×2400),
`pexels_table.jpg`(1600×2324). 결과는 `scripts/_out/`.

| 사물 | 이미지 / bbox | 결과 | 메모 |
|---|---|---|---|
| TV | real_living_room.jpg / `0.34,0.39,0.30,0.28` | **잘 됨** | TV·사운드바·전선 완전 제거, 벽·걸레받이 자연 복원. 이전 버전에 있던 하단 이음매가 사라짐 → 새 페더 마스크+프롬프트로 개선, 회귀 없음. |
| 소파 | pexels_sofa.jpg / `0.24,0.50,0.66,0.30` | **안 됨** | Gemini 가 거의 원본 그대로 반환(소파·쿠션·앞 벤치 그대로). 창문 앞 + 노출 벽돌 + bbox 안에 벤치 겹침 → 편집을 회피한 것으로 보임. e2e 밝기 체크도 109→109 로 FAIL. |
| 테이블 | pexels_table.jpg / `0.36,0.52,0.42,0.30` | **부분 성공** | 테이블 자체는 깨끗이 제거되고 바닥 복원 양호. 그러나 의자 6개가 붕 뜬 배치로 남고(요청은 테이블만), 상판에 있던 꽃병이 공중에 뜬 아티팩트. |

관찰
- **평평한 단일 표면 앞의 고립된 사물**(벽걸이 TV)에서 가장 잘 된다.
- bbox 안에 다른 가구가 겹치거나(벤치↔소파, 의자↔테이블) 배경이 복잡하면(창/벽돌/패턴)
  실패하거나 어색해진다. 이건 마스크/프롬프트로는 한계 → 정밀 세그멘테이션(PHASE 3)과
  "딸린 물건 같이 제거"(테이블+의자) 가 필요.
- `e2e_check_custom.py` 의 밝기 델타 체크는 완전한 판정은 아니지만(어두운 사물↔어두운 바닥은
  통과할 수 있음) 소파 미제거를 정확히 잡아냈다. 스모크 신호로 유지.

## PHASE 2 — 진단 실험: 실패 원인 분리 (배경 복잡도 vs 가구 겹침) (2026-09-03)

소파 삭제 실패가 "배경이 복잡해서"인지 "bbox 안에 다른 가구가 겹쳐서"인지 분리했다.

### 준비한 이미지 (`testdata/`, Pexels 무료)
| 코드 | 파일 | 배경 | bbox 안 겹침 |
|---|---|---|---|
| A | `sofa_A_simple_isolated.jpg` | 단순 (흰 벽) | 없음 |
| B | `sofa_B_complex_isolated.jpg` | 복잡 (창+담쟁이+거친 회벽) | 없음 |
| C | `sofa_C_simple_overlap.jpg` | 비교적 단순 (개방형) | 커피테이블+러그 |
| D | `pexels_sofa.jpg` (기존) | 복잡 (창+벽돌) | 앞 벤치+쿠션 |

### 실행 (모두 `gemini-2.5-flash-image`, `e2e_check_custom.py`)
```
# baseline (기본 프롬프트, 보통 bbox)
python scripts/e2e_check_custom.py --image testdata/sofa_A_simple_isolated.jpg  --bbox 0.48,0.52,0.52,0.28  --object-type sofa
python scripts/e2e_check_custom.py --image testdata/sofa_B_complex_isolated.jpg --bbox 0.00,0.575,1.0,0.425 --object-type sofa
python scripts/e2e_check_custom.py --image testdata/sofa_C_simple_overlap.jpg   --bbox 0.00,0.50,0.66,0.44  --object-type sofa
python scripts/e2e_check_custom.py --image testdata/pexels_sofa.jpg             --bbox 0.24,0.50,0.66,0.30  --object-type sofa
# 타이트 bbox (겹친 물체를 박스 밖으로)
python scripts/e2e_check_custom.py --image testdata/sofa_C_simple_overlap.jpg --bbox 0.00,0.53,0.43,0.40 --object-type sofa
python scripts/e2e_check_custom.py --image testdata/pexels_sofa.jpg           --bbox 0.52,0.47,0.36,0.30 --object-type sofa
# "박스 안 다른 물체도 제거, 밖은 건드리지 마" 명시 프롬프트
python scripts/e2e_check_custom.py --image testdata/pexels_sofa.jpg --bbox 0.24,0.50,0.66,0.30 --object-type sofa \
  --ai-extra-prompt "If other small objects sit inside the white masked area (a bench, a coffee table, cushions, a rug, or items resting on the furniture), remove those as well and rebuild the surface beneath them. Never remove, move, shrink or alter any object whose main body lies outside the white masked area."
```

### 결과
| 실험 | 결과 | 상세 |
|---|---|---|
| **A** 단순bg·고립 (보통 bbox) | ✅ 완벽 | 소파 완전 제거, 벽/바닥/걸레받이·뒤 사이드테이블·러그 자연 복원 |
| **B** 복잡bg·고립 (보통 bbox) | ✅ 성공 | 소파+쿠션 완전 제거. 창틀 아래 벽·마루 원근까지 재구성. (밝기 체크는 false-FAIL) |
| **C** 단순bg·겹침 (보통 bbox) | ⚠️ 부분 | 소파는 제거되나 **커피테이블+러그 잔존** (덩그러니 남음) |
| **D** 복잡bg·겹침 (보통 bbox) | ⚠️ 부분·불안정 | 소파 몸체만 지워지고 쿠션/스로우/벤치 잔존, 지저분. 동일 조건 다른 런에서는 아예 passthrough(편집 0) |
| **B** tight bbox | ✅ 성공 | B-normal보다 더 깔끔, 옆 스툴 보존 |
| **C** tight bbox (커피테이블 제외) | ✅ 성공 | 소파만 깨끗이 제거, 박스 밖 커피테이블·러그 그대로 |
| **D** tight bbox (벤치 제외) | ✅ 성공 | 소파·쿠션 전부 제거, **복잡한 벽돌벽·창도 복원**, 벤치 그대로 |
| **C** + 명시지시 (보통 bbox) | ❌ 악화 | 아무것도 안 지움 (passthrough). C-normal보다 나쁨 |
| **D** + 명시지시 (보통 bbox) | ✅ 성공 | 소파+쿠션+스로우+**벤치까지 전부** 제거, 박스 밖(화분·커튼) 보존 |

### 결론 — 진짜 원인은?
- **배경 복잡도가 아니라 "bbox 안 가구 겹침"이 실패 원인이다.**
  B(복잡bg·고립)가 깨끗이 성공했으므로 복잡한 배경 자체는 문제가 아니다.
  A↔C(배경 비슷, 겹침만 다름), B↔D(배경 비슷, 겹침만 다름) 모두 겹침 있는 쪽만 실패.
- **타이트 bbox로 겹친 물체를 박스 밖으로 빼면** C·D 모두(복잡 배경 포함) 깨끗이 성공.
  → 가장 안정적인 해법.
- **명시 프롬프트**("박스 안 다른 물체도 제거")는 비결정적: D는 확 좋아졌고 C는 오히려
  passthrough. 신뢰 불가. 프롬프트에 상시 넣지 않는다(`INTERIOR_AI_EXTRA_INSTRUCTION` 로
  실험만 가능하게 유지).
- `e2e_check_custom.py` 밝기 델타 체크는 B에서 false-FAIL(밝기 유사한 사물↔배경).
  스모크 신호로만 쓰고 최종 판정은 이미지 육안 확인.

### 지금 UI(사각형 드래그)로 실전에서 쓸만한가?
- **쓸만한 조건**: 지우려는 사물 하나에만 딱 맞게 사각형을 그리면 배경이 복잡해도 잘 된다.
  벽걸이 TV, 고립된 소파, 벽 앞 단독 가구 → 데모/초기 실전 가능.
- **한계**: 사각형 안에 다른 가구(커피테이블·벤치·의자)가 물리적으로 들어올 수밖에 없는
  배치. 이땐 "이것만" 선택이 불가능해 부분 제거/잔존/불안정.
- **권장 대응**: (1) 사용자 가이드 "사물 하나 = 사각형 하나, 다른 가구 안 겹치게".
  (2) 드래그 후 박스가 화면의 큰 비율을 덮거나 다른 가구를 포함할 것 같으면 경고 문구.
  (3) PHASE 3: 정밀 세그멘테이션(사물 윤곽) 또는 포인트/브러시로 "이 사물만" 선택.

## Completed

`server/app/ai/external.py` 의 `TODO(P1-10)` 를 **Google Gemini 이미지 편집 API**
호출로 구현했다. mock provider 는 그대로 두고, `INTERIOR_AI_PROVIDER=external` 로
바꾸면 실제 AI 를 쓴다 (`build_provider()` 가 설정만 보고 교체 — 라우터/저장/작업 큐
코드는 안 건드림).

- **요청 조립** (`ExternalRemoveObjectProvider.remove_object`)
  - `app/ai/mask.py` 의 `region_to_mask_png()` 로 bbox → 원본과 같은 크기의 흑백
    마스크 PNG (지울 영역이 흰색, 가장자리 약간 feather).
  - 지시문: "첫 번째 이미지에서 <object_type> 을 지우고, 두 번째 이미지(마스크)의
    흰색 영역만 편집, 뒤 배경을 색/질감/조명/그림자/원근에 맞춰 복원, 마스크 밖은
    그대로, 해상도·프레이밍 유지, 편집된 이미지만 반환". bbox 중심 위치를
    `location_hint()` 로 "upper-center" 식 힌트도 넣는다.
  - `POST {base_url}/models/{model}:generateContent`, 헤더 `x-goog-api-key: <키>`,
    body `contents[0].parts = [text, inline_data(jpeg), inline_data(mask png)]`,
    `generationConfig.responseModalities=["TEXT","IMAGE"]`.
- **응답 파싱**: `candidates[0].content.parts` 에서 `inlineData`(또는 `inline_data`)
  의 base64 를 디코드 → Pillow 로 열어 JPEG(q90)로 재인코딩해 반환.
  `changed_region` = bbox.
- **에러 처리** (모두 `ProviderError`/`ProviderNotConfigured` 로, 라우터가 job 을
  `failed` + `error` 로 마감):
  - 키 없음 → `ProviderNotConfigured`
  - 429 → "API 사용 한도 초과", 401·403 → "인증 실패", 400 → "잘못된 요청",
    404 → "모델 없음", 5xx → "서버 오류"
  - `httpx.TimeoutException` → "요청 시간 초과", 그 외 `httpx.HTTPError` → "네트워크 오류"
  - `promptFeedback.blockReason` → "요청 차단", 이미지 파트 없음 →
    "응답에 이미지가 없음 (finishReason=…)"
- **설정** (`app/config.py`, 접두사 `INTERIOR_`): `ai_model` 기본
  `gemini-3.1-flash-image`, `ai_base_url` 기본 `https://generativelanguage.googleapis.com/v1beta`,
  `ai_timeout_seconds` 기본 120.
- **`.env.example`** 갱신: `INTERIOR_AI_PROVIDER` / `INTERIOR_AI_API_KEY`(형식만) /
  `INTERIOR_AI_MODEL` / `INTERIOR_AI_BASE_URL` / `INTERIOR_AI_TIMEOUT_SECONDS` 설명.
- **`.gitignore`**: `.env` 는 `server/.gitignore` 와 루트 `.gitignore`(`.env`,
  `.env.*`, `!.env.example`) 양쪽에 이미 있음 — 확인 완료, 추가 불필요.
- **테스트**: `tests/test_external.py` 8개 — `httpx.post` 를 가짜로 바꿔 키 없이
  요청 조립 / 200 성공(→ JPEG) / 429 / 403 / 네트워크 / 타임아웃 / 이미지 없음 /
  안전 차단을 검증. 전체 `pytest` 13개 통과.
- **결과 해상도 강제**: `app/ai/imageops.ensure_jpeg_size(bytes, size)` 신설.
  `_run_job` 이 프로바이더 결과를 **항상 원본 키프레임 해상도의 JPEG 로** 맞춰 저장한다
  (mock/external 공통). external 은 그 전에도 한 번 맞춘다. Gemini 가 다른 해상도로
  돌려줘도 이제 결과가 원본과 일치.
- **임의 사진용 스크립트**: `scripts/e2e_check_custom.py` — `--image` 와
  `--bbox "x,y,w,h"`(0~1) 를 받아 `e2e_check.py` 와 동일한 흐름을 돈다.
  `e2e_check.py` 의 공용 로직(`run_flow`, `ensure_server`, `parse_bbox` …)을
  그대로 import 해서 씀. `e2e_check.py` 에도 `--bbox` / `--ai-provider` /
  `--ai-api-key` 추가.
- **실결과 검증 완료 (2026-09-03)**: `real_living_room.jpg`(4032×3024,
  `server/testdata/`) + `--bbox 0.34,0.39,0.30,0.28` + `gemini-2.5-flash-image`
  → **TV·사운드바·전선이 깨끗이 제거되고 벽/걸레받이가 자연스럽게 복원됨**,
  결과 해상도 4032×3024 유지, e2e 14/14 PASS. mock 회귀도 14/14.

## Important Files

```
experiments/shinym87/interior/server/
├─ .env.example                 필요한 환경변수 형식 (실제 키 없음)
├─ app/config.py                ai_model / ai_base_url / ai_timeout_seconds 기본값
├─ app/ai/
│  ├─ external.py               Gemini 호출 본체 (이 커밋의 핵심)
│  ├─ mask.py                   bbox → 마스크 PNG, 위치 힌트, base64
│  ├─ mock.py                   그대로 유지 (기본 provider)
│  └─ __init__.py               build_provider() — 설정으로 mock/external 교체
├─ app/ai/imageops.py          결과를 원본 해상도 JPEG 로 맞추는 공통 로직
├─ app/routers/scenes.py       _run_job 이 ensure_jpeg_size() 로 결과 크기 보정
├─ tests/test_external.py       에러/응답 처리 단위 테스트
├─ testdata/real_living_room.jpg  실사진 테스트 픽스처 (4032×3024)
└─ scripts/
   ├─ e2e_check.py             공용 로직 + --bbox / --ai-provider / --ai-api-key
   └─ e2e_check_custom.py      --image / --bbox 로 임의 사진 검증
```

## Decisions

- 프로바이더는 Google Gemini `:generateContent` (REST). 마스크는 별도 파라미터가
  없어 "두 번째 이미지 = 마스크" 방식으로 전달 + 프롬프트로 지시.
- 결과는 항상 JPEG 로 재인코딩해 저장 형식(mock/`.jpg`)과 통일.
- 모델명은 `INTERIOR_AI_MODEL` 로 교체 가능. 기본 `gemini-3.1-flash-image`,
  계정에서 못 쓰면 `gemini-2.5-flash-image` 등으로.

## Constraints

- 실결과는 `INTERIOR_AI_MODEL=gemini-2.5-flash-image` 로 검증했다
  (`gemini-3.1-flash-image` 는 해당 계정에서 사용 불가 → `.env` 에서 교체).
- 키(`INTERIOR_AI_API_KEY`)는 `server/.env` 에 있고 `.gitignore` 로 커밋에서 제외된다.
  `.env.example` 에는 형식만 있다.
- `responseModalities` / `inline_data` 표기는 REST 문서 기준으로 맞췄으나, 모델
  버전에 따라 `400` 이 나면 `generationConfig` 를 조정해야 할 수 있다(에러 메시지에
  Gemini 원문이 포함됨).
- 이미지 생성은 느릴 수 있어 타임아웃 120초. 그래도 `remove-object` 는
  `BackgroundTasks` 동기 실행이라 오래 걸리면 폴링이 그만큼 길어진다(PHASE 2 에서
  워커 분리).

## 진단 로그 (2026-09-03)

`interior.*` 로거를 uvicorn 콘솔에 붙였다(`app/main.py._setup_logging`). 요청/응답 추적:
- `app/routers/scenes.py` `_run_job`: provider, 키프레임 파일명/바이트/해상도, region, 완료/실패
- `app/ai/external.py`: `[Gemini 요청]` — 모델, part별 크기(이미지 bytes·해상도·base64
  길이, 마스크 base64 길이), 프롬프트 앞부분. 이미지가 2KB 미만이면 경고("검은 화면
  가능성"). `[Gemini 응답]` — status, finishReason, parts 요약(이미지 base64→bytes,
  또는 `text=...` 원문), promptFeedback.
- 실기기 첫 실패 원인은 서버가 아니라 **앱의 키프레임 캡처**였다(검은 화면 전송).
  앱 쪽 수정은 user1 handoff 참고.

## Known Issues

- mock e2e 는 여전히 13/13 통과 (회귀 없음). external + 키 없음 e2e 는
  `job=failed, error=ProviderNotConfigured …` 로 정상적으로 실패한다(의도된 동작).
- Gemini 가 마스크를 항상 정확히 지키지는 않는다 — 넓게 편집되거나 원근이 틀어질 수
  있다. 정합 개선은 PHASE 3.

## Next

1. 프롬프트/모델 튜닝 — 경계 이음매(하단 미세 seam), 그림자 처리, 다른 사물 종류
   (소파/테이블). 설계서 PHASE 2 사용자 2 항목.
2. `remove-object` 를 별도 워커/큐로 (지금은 `BackgroundTasks` 동기) — Gemini 호출이
   길면 폴링이 그만큼 길어짐. 재시도 정책도.
3. 앱(P1-11): 서버 `--host 0.0.0.0`, 앱 `InteriorApiClient.DEFAULT_BASE_URL` 을 PC IP 로,
   앱에서 실제 bbox 지정 → Gemini 결과를 벽에 붙이는 흐름 실기기 확인.

## Relevant Commits

- `feat(interior): PHASE 1 영상/AI — external provider 를 Google Gemini 이미지 편집 API 로 구현`
  (이 커밋; 브랜치 `agent/shinym87/interior`)
