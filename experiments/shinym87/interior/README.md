# Interior AR — 카메라 기반 공간 편집 / AR 가구 재배치

`hackathon_spatial_editing_design.md` 설계서를 따르는 실험. 초기에는 1인이
앱(사용자 1) → 서버(사용자 3) → 외부 AI 순으로 진행한다.

- **`app/`** — Android 앱 (사용자 1, 공간 / AR). 카메라로 실제 공간을 보면서
  가구(현재는 반투명 큐브)를 배치·이동·크기 조절하고, "빈 배경" 대표 이미지를
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
| 가구 이동 (드래그 후 평면에 재고정) | `furniture/FurnitureController.kt` (`beginDrag`/`drag`/`endDrag`) |
| 가구 크기 조절 (**핀치** + `＋`/`－` 버튼) | `furniture/FurnitureController.scaleSelectedBy()` |
| 대표 이미지 캡처 / 변경 전·후 비교 | `keyframe/BackgroundKeyframe.kt` |
| TV 영역 드래그 지정 (bbox) | `remove/BboxSelectionView.kt` |
| 키프레임 캡처 + 서버 호출 (`/scenes` `/keyframes` `/remove-object`) | `remove/RemovalController.kt`, `remove/InteriorApiClient.kt` |
| job 폴링 → 결과 이미지를 벽 quad 로 적용 + "삭제 전/후" 전환 | `remove/RemovalController.kt` |

서버 주소는 `remove/InteriorApiClient.DEFAULT_BASE_URL = "http://localhost:8000"` 하드코딩
(실기기에서는 PC LAN IP 로 바꿔야 함).

미구현: 가구 회전, 실제 3D 모델(glTF), 실제 외부 AI 연동(P1-10), 결과 정합 다듬기(PHASE 3).

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
│     │  │  ├─ FurnitureController.kt    # 생성·선택·이동·크기 조절·삭제
│     │  │  ├─ FurnitureItem.kt          # 노드/상태 묶음 + 상수
│     │  │  └─ LabelRenderer.kt          # 이름표 비트맵
│     │  ├─ keyframe/
│     │  │  └─ BackgroundKeyframe.kt     # 빈 배경 캡처 + 반투명 오버레이
│     │  ├─ remove/
│     │  │  ├─ BboxSelectionView.kt      # 드래그로 제거 대상 사각형 지정
│     │  │  ├─ InteriorApiClient.kt      # 서버 HTTP (baseUrl 하드코딩)
│     │  │  └─ RemovalController.kt      # 캡처·메타·플로우·결과 quad·전/후 토글
│     │  └─ ui/
│     │     └─ FurnitureInfoDialog.kt    # 이름/실물 크기 입력 팝업
│     └─ res/
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

1. 바닥·책상·벽을 천천히 비춰 격자가 나타나게 한다.
2. 평면을 **탭** → 이름과 실물 크기(cm)를 입력하면 반투명 가구가 생긴다.
3. 가구를 **탭/길게 누르기** 로 선택(밝게 강조) → 하단 조작 패널 표시.
4. 선택 상태에서 **드래그** 하면 평면을 따라 이동, 떼면 그 자리에 고정된다.
5. **두 손가락 핀치** 또는 패널의 `＋`/`－` 로 크기를 조절한다. `삭제` 로 제거.
6. `배경 촬영` 으로 가구가 없는 현재 화면을 저장하고, `배경 표시` + 불투명도
   슬라이더로 "가구가 사라진 것처럼" 겹쳐 본다.

## 알려진 제약

- **AR Required 앱**이다. ARCore 미지원 기기에서는 설치/실행되지 않는다.
  [지원 기기 목록](https://developers.google.com/ar/devices).
- 저장소 경로에 한글(`신유민`)이 포함되어 `gradle.properties` 에
  `android.overridePathCheck=true` 와 UTF-8 인코딩 플래그를 넣었다. 그래도 빌드가
  경로 문제로 실패하면 ASCII 경로로 복사해서 빌드한다.
- 조명 추정만 적용하고 그림자는 없다. 가구는 단색 반투명 재질이다.
- 핀치와 드래그가 드물게 겹칠 수 있다. SceneView 제스처 detector 가 한 번에 하나의
  제스처만 처리하도록 되어 있어 실사용에는 문제되지 않지만, 필요하면
  `onMoveBegin` 에서 포인터 수를 확인하도록 보강한다.
