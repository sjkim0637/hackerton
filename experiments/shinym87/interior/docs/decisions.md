# 결정 기록 (interior)

이 실험 범위의 주요 결정. 형식은 루트 `docs/decisions/README.md` 의 ADR 템플릿을 줄인 것.
결정이 바뀌면 지우지 말고 Status 를 바꾸고 대체 결정을 연결한다.

---

## D1. 아키텍처는 A(외부 AI API) 우선

- Status: Accepted (2026-09-02)
- Context: 사물 삭제 후 빈 공간 복원 품질을 해커톤 기간에 확보해야 한다.
- Decision: 외부 AI 이미지 편집 API 로 전체 기능을 먼저 완성한다. B(경량 모델),
  C(자체 엔진)는 PHASE 6/7 에서 별도 브랜치로 비교 실험한다.
- Reason: 설계서 9장의 `A > B > C` 우선순위. A 는 학습/GPU 서버 없이 가장 빠르게
  동작하는 결과물을 만든다.
- Alternatives: B 먼저(모바일 최적화 부담), C 먼저(난이도 최고, 가려진 영역 한계).
- Impact: 서버에 외부 AI 어댑터 계층이 필요. 인터넷 의존/호출 비용/응답 지연을
  설계에 반영(캐시, 호출 상한, job 폴링).

---

## D2. 초기에는 1인(shinym87) 수직 슬라이스로 진행

- Status: Accepted (2026-09-02)
- Context: 설계서는 3인 역할 분담(사용자 1/2/3) + PHASE 0 부터 병렬을 전제한다.
  현재는 1명이 시작한다.
- Decision: 한 브랜치(`agent/shinym87/interior`)에서 앱 → 서버 → 외부 AI 순으로
  얇게 관통하는 슬라이스를 만든다. 역할 경계와 PHASE 구분은 문서상 유지한다.
- Reason: 초반에는 인터페이스 협상보다 한 번 동작하는 흐름을 빨리 만드는 게 낫다.
- Alternatives: 설계서대로 3역할 병렬(현재 인원으로는 과함).
- Impact: 인원 합류 시 미완료 PHASE 항목을 새 Workstream 으로 분리. 그때까지
  `TEAM_WORKBOARD.md` 에는 `interior` 한 줄만 유지.

---

## D3. MVP 사물 영역은 bbox(축 정렬 사각형)

- Status: Accepted (2026-09-02)
- Context: 정밀 세그멘테이션은 시간이 든다. TV 는 대체로 직사각형이다.
- Decision: MVP 는 정규화 `bbox` 로 대상 영역을 지정한다. 서버가 사각형 마스크로
  변환해 AI 에 넘긴다. 픽셀 마스크(`type: "mask"`)는 확장 필드로 스펙만 정의.
- Reason: 앱 UI(드래그로 사각형)와 서버 변환이 단순하다. PHASE 2 에서 정밀화.
- Impact: TV 처럼 사각형이 아닌 사물(소파 등)은 여백이 함께 지워질 수 있음 → PHASE 2 개선.

---

## D4. AR 라이브러리는 SceneView(arsceneview 2.3.0)

- Status: Accepted (2026-09-02) — `agent/shinym87/ar2` 검증 결과 계승
- Context: 순수 ARCore + OpenGL 은 보일러플레이트가 많다.
- Decision: `io.github.sceneview:arsceneview:2.3.0` 사용. 카메라 배경, 평면 격자,
  Filament 씬 그래프, 권한/설치 안내를 라이브러리가 처리.
- Alternatives: 순수 ARCore + OpenGL(`hello_ar_kotlin`).
- Impact: 씬 그래프/제스처 API 가 SceneView 에 종속. 프로젝트 공통 표준은 아님.

---

## D5. 사물 선택은 드래그 bbox 대신 MobileSAM 점 프롬프트

- Status: Accepted (2026-09-07, `agent/goguma-salad/interior-mobilesam` 브랜치)
- Context: D3(bbox 드래그)의 UX가 "정확히 사각형을 그려야 함"이라 사용자 피드백상
  너무 번거로웠다. 순수 OpenCV(GrabCut/Saliency/floodFill)로 완전 자동화를 검토했으나,
  인테리어 사진은 배경(벽·바닥) 비중이 크고 색상이 사물과 비슷한 경우가 많아 점 하나
  또는 무입력만으로는 실패가 잦다(고전 CV는 "의미"를 모름).
- Decision: 탭(또는 누르고 있기) 한 번으로 지정한 점을 MobileSAM(Segment Anything의
  경량 증류 모델, point-prompt 전용 설계)에 넘겨 사물 마스크를 얻는다. 모델 파일이
  없거나 로드에 실패하면 점 중심 정사각형 bbox로 자동 대체한다(품질은 낮지만 항상 동작).
- Reason: SAM 계열은 정확히 "점 하나 → 물체 마스크" 문제를 풀도록 학습된 모델이라,
  질감/색상이 배경과 비슷해도 GrabCut·floodFill보다 훨씬 안정적이다. MobileSAM은 CPU
  추론이 가능할 만큼 가벼워 서버에 GPU 없이도 쓸 수 있다.
- Alternatives:
  - OpenCV GrabCut(사용자 bbox seed) — 입력 자체가 여전히 필요, 정밀도는 개선되지만
    "사각형을 그려야 하는" 문제는 안 풀림.
  - OpenCV Saliency/floodFill(무입력 또는 점 하나) — 인테리어처럼 배경 비중이 큰 장면에서
    신뢰도가 낮음(D5 Context 참고).
  - 사전학습 객체탐지(YOLO 등, `cv2.dnn`) — 완전 무클릭이 가능하지만 클래스가 제한되고
    모델 관리 부담이 큼. 지금은 채택하지 않음.
- Impact:
  - 서버: `Region` 스키마에 `PointRegion`(`type: "point"`) 추가, `remove-object` 처리 전
    `_resolve_point_region()`이 점을 `MaskRegion`으로 변환(`app/routers/scenes.py`).
    변환 로직은 `app/ai/mobilesam.py`(MobileSAM 추론)와 `app/ai/mask.py`(대체/페더링)에 있다.
    `onnxruntime`은 지연 import라 모델 미설정 환경에서도 서버는 정상 기동한다.
  - `mask.py::region_bbox()`가 `mask` 타입일 때 더 이상 "가운데 절반"으로 근사하지 않고
    실제 마스크 픽셀의 바운딩 박스를 계산한다 — `mock.py`의 자체 bbox 로직도 이걸 재사용하도록
    통합했다(전에는 mock만 별도로 가운데 절반 근사를 썼다).
  - 모델 파일(encoder/decoder ONNX, 수십MB)은 저장소에 커밋하지 않는다. 수동 다운로드 방법은
    `docs/workstreams/interior-mobilesam.md` 참고.
  - **앱(Kotlin) 쪽은 아직 부분 구현이다.** `BboxSelectionView`에 탭 좌표를 넘기는
    `onPointSelected` 콜백을 추가했지만(기존 드래그 경로는 그대로 유지), `RemovalController`가
    이걸 받아 `PointRegion`을 서버로 보내도록 연결하는 작업은 남아 있다
    (`docs/workstreams/interior-mobilesam.md`의 Next 참고).

---

## D6. 초기 AR 진입을 ARCore Depth API + Instant Placement로 가속

- Status: Accepted (2026-09-07, `agent/goguma-salad/interior-mobilesam` 브랜치)
- Context: 기존 AR 진입은 `Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL`(순수 시각 SLAM)
  만 썼다. 이 방식은 사용자가 폰을 좌우로 움직여 특징점을 충분히 모아야 Plane 이 잡히고,
  그 전까지는 `ArSpaceController.hitTest*()` 가 항상 null 이라 아무것도 배치할 수 없었다
  ("초기에 AR이 평면 찾는다고 계속 돈다"는 사용자 피드백의 원인). "IR 기반 Depth/ToF 로
  바꿔달라"는 요청이 있었다.
- Decision:
  1. `session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)` 로 지원 여부를 확인한 뒤
     지원하면 `config.depthMode = Config.DepthMode.AUTOMATIC` 를 켠다.
  2. 하드웨어와 무관하게 `config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP`
     을 항상 켠다.
  3. `ArSpaceController.hitTest()`/`hitTestPreferring()` 의 fallback 단계에서
     `depthPoint`/`instantPlacementPoint` 를 함께 받아들이도록 `hitTestAR` 호출을 확장한다.
- Reason: **"IR 기반 Depth/ToF"는 요청한 그대로는 일부 기기에서만 성립한다.** ARCore의
  Depth API(`DepthMode.AUTOMATIC`)는 기기에 실제 ToF/IR 깊이 센서가 있으면 그 하드웨어 값을
  자동으로 쓰지만, 요즘 주요 플래그십 다수(예: 이 프로젝트가 검증에 쓰는 Galaxy S25 FE 계열)는
  전용 ToF 센서가 없어 ARCore의 Motion Stereo(Depth-from-Motion, 카메라 이동으로 깊이 추정)로
  자동 대체된다 — 이 경로는 여전히 약간의 카메라 이동이 필요하다. 반면 Instant Placement는
  하드웨어와 무관하게 모든 ARCore 지원 기기에서 즉시 대략적인 배치를 허용하고, 이후 실제
  Plane/Depth 가 잡히면 자동으로 위치를 다듬는다 — "폰을 막 돌려야 하는" 체감을 실제로
  없애는 것은 이 부분이다. 그래서 "ToF만" 넣는 대신 두 기능을 함께 켜서, ToF가 있는 기기는
  최상의 정밀도를, 없는 기기도 즉시 반응성을 얻게 했다.
- Alternatives:
  - Depth API만 켜고 Instant Placement는 안 씀 — ToF 없는 기기(팀 보유 기기 대부분)에서는
    체감 개선이 거의 없어 기각.
  - 자체 IR/ToF 카메라 API(`android.hardware.camera2` Depth16, RGBD 등)를 직접 다룸 —
    기기별 파편화가 심하고 ARCore가 이미 추상화해주는 것을 재구현하는 셈이라 기각.
- Impact:
  - `ArSpaceController.kt` 에 `depthSupported` 프로퍼티 추가(hitTest 에서 depthPoint 사용
    여부 판단), 안내 문구가 더 이상 "평면부터 찾아야 함"을 암시하지 않도록 수정.
  - 실기기 검증 못 함(이 환경엔 Android SDK/실기기 없음) — 특히 Instant Placement 로 배치한
    뒤 실제 Plane 이 잡히며 위치가 "다듬어지는" 전환이 자연스러운지, ToF 미보유 기기에서
    체감 개선이 실제로 있는지는 실기기 확인이 필요하다.
  - 기존 `docs/handoffs/interior-removal-fallback.md`(삭제 결과 전체화면 fallback 문제)와
    별개 이슈다 — 그 문서의 "가구 배치 Mode에 들어갈 때만 Plane 격자 표시" 요청은 이번
    변경에 포함하지 않았다(RemovalController 쪽 모드 전환 로직이 필요해 범위를 분리함).
