# Interior AR — 카메라 기반 공간 편집 / AR 가구 재배치

`hackathon_spatial_editing_design.md` 설계서를 따르는 실험. 초기에는 1인이
앱(사용자 1) → 서버(사용자 3) → 외부 AI 순으로 진행한다.

- **`app/`** — Android 앱 (사용자 1, 공간 / AR). 카메라로 실제 공간을 보면서
  브로셔에서 고른 저폴리 3D 가구를 배치·이동·회전·크기 조절하고, "빈 배경" 대표 이미지를
  캡처해 비교한다.
- **[`server/`](server/README.md)** — FastAPI 서버 (사용자 3, 서버 / 통합).
  작업 세션, 키프레임 업로드/저장, 사물 정보 저장(형식만), 외부 AI 연결 구조
  (mock + 실제 API 자리), 사물 제거 job, 가구 카탈로그 API. `pytest` 5개 통과.
- **`docs/`** — PHASE 0 설계·규칙.

`experiments/shinym87/ar2/` (Branch `agent/shinym87/ar2`) 의 검증 코드에서
카메라 실행 · 평면 인식 · 탭 생성 · 드래그 이동 로직을 재사용했고, 설계서 폴더/개념
구분에 맞춰 파일을 다시 정리했다. **핀치 크기 조절**은 이번 프로젝트에서 새로 추가했다.

## 설계 문서 (PHASE 0)

초기에는 역할을 나누지 않고 1인이 앱 → 서버 → 외부 AI 순으로 진행한다.
PHASE 0 산출물은 `docs/` 에 있다.

- [`docs/phase-0.md`](docs/phase-0.md) — 시연 시나리오, MVP 범위, 체크리스트, PHASE 1 진입 조건
- [`docs/architecture.md`](docs/architecture.md) — 시스템 구조, A/B/C 전략(A 우선)
- [`docs/data-model.md`](docs/data-model.md) — 좌표계 · ID · 사물 영역 · 키프레임 · 가구 데이터 형식
- [`docs/api.md`](docs/api.md) — 앱 ↔ 서버 ↔ 외부 AI API 규격 초안
- [`docs/backlog.md`](docs/backlog.md) — PHASE 1 이슈 목록, 아이디어 백로그
- [`docs/decisions.md`](docs/decisions.md) — 주요 결정 기록

## 구현된 기능

| 설계서 항목 (사용자 1) | 구현 위치 |
|---|---|
| 카메라 화면 표시 / AR 실행 환경 구성 | `ar/ArSpaceController.kt` |
| 벽 / 바닥 평면 탐지 (수평 + 수직) | `ar/ArSpaceController.kt`, `ar/PlaneKind.kt` |
| 화면 터치 위치 획득 (hitTest) | `ar/ArSpaceController.hitTest()` |
| 임시 가구 배치 (탭 → 이름/실물 크기 입력) | `furniture/FurnitureController.kt`, `ui/FurnitureInfoDialog.kt` |
| 첫 화면 샘플 브로셔 → `우리 집에 적용` | `furniture/CatalogController.kt` |
| 화보 가구 선택 → `AR로 배치` / `구매하기` → 주문 Mock | `CatalogActivity.kt` |
| TV·소파·테이블·의자·선반 GLB 3D 모델 (미지원 항목은 저폴리 대체) | `res/raw/*.glb`, `furniture/GlbFurnitureFactory.kt`, `furniture/ProceduralFurnitureFactory.kt` |
| 가구 이동 (드래그 후 평면에 재고정) | `furniture/FurnitureController.kt` (`beginDrag`/`drag`/`endDrag`) |
| 가구 크기 조절 (**핀치** + `＋`/`－` 버튼) | `furniture/FurnitureController.scaleSelectedBy()` |
| 가구 회전 (`회전 ⟳`) | `furniture/FurnitureController.rotateSelectedBy()` |
| 대표 이미지 캡처 / 변경 전·후 비교 | `keyframe/BackgroundKeyframe.kt` (PHASE 5 데모에서 UI 숨김) |
| 제거할 물체 영역 드래그 지정 (bbox) + 선택 취소 | `remove/BboxSelectionView.kt`, `RemovalController.clearSelection()` |
| 지울 사물 종류 선택 (TV/소파/테이블/의자/선반) → 요청 `objectType` 반영 | `objectTypeSpinner`, `RemovalController.selectedObjectType()` |
| 키프레임 캡처 + 서버 호출 (`/scenes` `/keyframes` `/remove-object`) | `remove/RemovalController.kt`, `remove/InteriorApiClient.kt` |
| job 폴링 → 결과 이미지를 벽 quad 로 적용 + "삭제 전/후" 전환 | `remove/RemovalController.kt` |

서버 주소는 **화면 상단 입력창**에서 지정한다(값은 저장돼 유지). 실기기에서는
`localhost` 가 아니라 서버 PC 의 LAN IP(예 `http://192.168.0.10:8000`)를 넣어야 하고,
서버는 `uvicorn app.main:app --host 0.0.0.0` 로 띄운다.

미구현: GLB 실제 기기 비율·재질 검증, 결과 정합 다듬기, 가림(occlusion).

## 프로젝트 구조

```
experiments/shinym87/interior/
├─ app/
│  ├─ build.gradle                       # namespace com.hackathon.interior
│  └─ src/main/
│     ├─ AndroidManifest.xml             # CAMERA 권한, AR Required 메타데이터
│     ├─ java/com/hackathon/interior/
│     │  ├─ MainActivity.kt              # 네 컨트롤러를 레이아웃/제스처에 연결
│     │  ├─ ar/
│     │  │  ├─ ArSpaceController.kt      # 카메라·AR 세션·평면 인식·hitTest
│     │  │  └─ PlaneKind.kt              # 평면 타입 집합 + 수직/수평 판별
│     │  ├─ furniture/
│     │  │  ├─ CatalogController.kt      # 첫 화면 샘플 가구 브로셔
│     │  │  ├─ FurnitureController.kt    # 생성·선택·이동·크기 조절·삭제
│     │  │  ├─ FurnitureItem.kt          # 노드/상태 묶음 + 상수
│     │  │  ├─ ProceduralFurnitureFactory.kt # 종류별 저폴리 3D 모델
│     │  │  └─ LabelRenderer.kt          # 이름표 비트맵
│     │  ├─ keyframe/
│     │  │  └─ BackgroundKeyframe.kt     # 빈 배경 캡처 + 반투명 오버레이
│     │  ├─ remove/
│     │  │  ├─ BboxSelectionView.kt      # 드래그로 제거 대상 사각형 지정 / clear()
│     │  │  ├─ InteriorApiClient.kt      # 서버 HTTP (baseUrl 주입)
│     │  │  └─ RemovalController.kt      # 서버주소(prefs)·선택취소·캡처·메타·플로우·결과·전/후
│     │  └─ ui/
│     │     └─ FurnitureInfoDialog.kt    # 이름/실물 크기 입력 팝업
│     └─ res/
│        ├─ raw/                         # APK에 내장한 가구 GLB
│        ├─ layout/activity_main.xml
│        ├─ layout/dialog_furniture_info.xml
│        ├─ values/strings.xml
│        ├─ values/themes.xml
│        └─ drawable/ic_launcher.xml
├─ build.gradle / settings.gradle / gradle.properties
└─ gradle/wrapper/                       # Gradle 8.11.1 wrapper 포함
```

## 기술 스택

| 항목 | 버전 |
|---|---|
| Android Gradle Plugin | 8.9.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 24 |
| AR 라이브러리 | `io.github.sceneview:arsceneview:2.3.0` (ARCore 1.48.0 + Filament 1.56.0 포함) |
| 그 외 | `kotlinx-coroutines-android:1.8.1`, `lifecycle-runtime-ktx:2.8.7` |
| 필요 JDK | 17 또는 21. Android Studio 번들 JBR 이 25 라 Gradle 8.11.1 CLI 빌드가 실패한다("Unsupported class file major version 69"). 이 PC 에서는 `JAVA_HOME=C:\Users\User\.jdks\jbr-21.0.11` 로 빌드했다. |

## 빌드 및 실기기 설치

```bash
cd experiments/shinym87/interior

# 디버그 APK 빌드 (Windows PowerShell: .\gradlew.bat assembleDebug)
./gradlew assembleDebug

# 폰을 USB 로 연결 (개발자 옵션 + USB 디버깅 ON)
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.hackathon.interior/.MainActivity
```

첫 실행 시 카메라 권한을 허용하고, 기기에 "Google Play 서비스 (AR)" 가 없으면
Play 스토어 설치 안내를 따른다.

## 사용 방법

1. 첫 화면 브로셔에서 샘플 가구를 고르고 **우리 집에 적용**을 누른다.
2. 바닥·책상·벽을 천천히 비춰 격자가 나타나면 안내된 평면을 탭해 3D 가구를 배치한다.
3. 가구를 **탭/길게 누르기** 로 선택(밝게 강조) → 하단 조작 패널 표시.
4. 선택 상태에서 **드래그** 하면 평면을 따라 이동, 떼면 그 자리에 고정된다.
5. **두 손가락 핀치** 또는 패널의 `＋`/`－` 로 크기를 조절하고 `회전 ⟳`로 방향을 바꾼다.
6. `삭제`로 가구를 제거하거나 `가구 추가`로 브로셔를 다시 연다.
7. 사물 제거 결과는 `삭제 전/후` 토글로 원본과 비교한다.

> "빈 배경" 캡처 오버레이(`배경 촬영`/`배경 표시`/투명도 슬라이더)는 효과가 미미하고
> 카메라를 따라가지 않아 PHASE 5 데모에서 UI 를 숨겨 뒀다(`BackgroundKeyframe.kt` 는
> 유지 — `activity_main.xml` 의 세 위젯을 `visibility="visible"` 로 되돌리면 다시 쓸 수 있다).

## 알려진 제약

- **AR Required 앱**이다. ARCore 미지원 기기에서는 설치/실행되지 않는다.
  [지원 기기 목록](https://developers.google.com/ar/devices).
- 저장소 경로에 한글(`신유민`)이 포함되어 `gradle.properties` 에
  `android.overridePathCheck=true` 와 UTF-8 인코딩 플래그를 넣었다. 그래도 빌드가
  경로 문제로 실패하면 ASCII 경로로 복사해서 빌드한다.
- 조명 추정만 적용하고 그림자·가림 처리는 없다. 샘플 가구는 외부 에셋이 아닌
  절차형 저폴리 모델이라 실제 상품의 재질·곡면과는 차이가 있다.
- 핀치와 드래그가 드물게 겹칠 수 있다. SceneView 제스처 detector 가 한 번에 하나의
  제스처만 처리하도록 되어 있어 실사용에는 문제되지 않지만, 필요하면
  `onMoveBegin` 에서 포인터 수를 확인하도록 보강한다.
