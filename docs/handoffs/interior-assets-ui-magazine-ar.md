# Handoff

## 현재 후속 개선 — 데모 Branch 병합 완료

- `7c5c1dd` 이후 Depth 면적 검사와 화보·AR UI 가독성을 개선했다. 아래 기존 Completed·Verification은 이전 전달 시점의 기록이다.
- 사용자 승인으로 `28c7053`, `697b1ed`를 `integration-interior-demo`에 fast-forward 병합했다. 기존 하단의 Branch 위치와 완료 기록은 당시 시점의 이력이다.
- Depth: 4×4 지지 구역 중 75% 이상, 각 행·열 최소 절반의 지지가 필요하다. 면적 부족·장애물 판정은 평면 대체 배치로 우회하지 않는다.
- UI: 상단 안내와 하단 스크롤 도구, 접을 수 있는 사물 지우기, 48dp 가구 선택 영역, 큰 이름 태그를 적용했다.
- Depth 테스트 19개 통과. 실제 기기 연결이 없어 이번 UI와 면적 검사의 실기기 검증은 사용자 확인이 필요하다.
- 확인 순서: 화보 태그 → AR 배치 → 좁은 면적 거절 → 도구 펼치기·접기 → 큰 글꼴과 편집 패널 스크롤.
- 서버 저장·복원 문제와 상세 3D 모델은 이번 수정에 포함하지 않았다.
- 현재 상태의 기준은 [Workstream](../workstreams/interior-assets-ui.md)을 참고한다.

## From

goguma-salad (Claude)

## To

Integration / 이 앱의 화보·AR 화면을 이어받는 담당자

## Workstream

[interior-assets-ui](../workstreams/interior-assets-ui.md) — 인테리어 화보 기반 객체 선택과 통합 AR 작업 화면

Project Path: `experiments/shinym87/interior/`
Git Branch: `agent/goguma-salad/interior-assets-ui`
통합 상태: `integration-interior-demo`에 fast-forward로 반영 완료. 두 Branch가 같은 지점(`3cd515c`)이다.

## Completed

### 화면 흐름

앱을 열면 세로 화보가 한 장씩 나온다. 사진 속 가구 위에 작은 점이 은은하게 깜빡인다. 점을 누르면 가구 이름 태그가 뜨고, 태그를 누르면 그 가구가 선택된 채 AR 화면으로 넘어간다. AR 화면에서는 가구 배치와 사진 속 사물 지우기를 함께 쓴다.

1. 화보 화면(`CatalogActivity`): 위아래로 쓸어 화보를 넘긴다. 사진 위에 앱이 글자를 얹지 않는다. 제목과 설명은 사진 안에 이미 들어 있다.
2. 점 marker를 누른다 → `가구 이름 · AR로 보기 ›` 태그가 뜬다. 사진 여백을 누르면 태그가 닫힌다.
3. 태그를 누른다 → `MainActivity`로 이동하며 객체 ID, 이름, 종류, 실제 크기(m), 바닥/벽 정보가 함께 전달된다.
4. AR 화면에서 바닥이나 벽을 누르면 그 자리에 가구가 놓인다. 같은 화면에 `지울 사물`, `영역 선택 모드`, `삭제 요청`이 있다.

### 이번에 한 일

- 화보 위에 가구 이름 버튼을 상시 노출하던 방식을 없앴다. 기본 상태에는 점 marker만 보인다.
- 공급받은 화보 atlas 두 장에서 지면 8개를 잘라 화보로 연결했다.
- 사진을 화면 전체에 보여 주고, 앱이 얹던 제호·제목·크레딧·안내 문구를 모두 걷어냈다.
- 화보를 한 번 탭했을 때 페이지가 넘어가던 버그를 고쳤다.
- 실기기에서 Depth 배치가 자주 막히던 원인을 찾아 판정 기준을 실제 방 조건에 맞게 넓혔다.
- Depth 판정이 막아도 ARCore가 인식한 평면이 있으면 그 위에 배치하도록 했다.
- 서버 주소 설정을 `AppSettings` 한 곳에서 관리하도록 분리했다.

## Important Files

| 파일 | 역할 |
|---|---|
| `app/src/main/java/com/hackathon/interior/CatalogActivity.kt` | 화보 화면. 페이지 넘김, 점 marker, 태그, AR 전달 |
| `app/src/main/java/com/hackathon/interior/magazine/MagazineFeedProvider.kt` | 화보 데이터 계약과 임시 구현(`MockMagazineFeedProvider`) |
| `app/src/main/java/com/hackathon/interior/ui/AtlasCropView.kt` | 큰 이미지에서 지정 영역만 잘라 그리고, 상대 좌표를 화면 좌표로 변환 |
| `app/src/main/java/com/hackathon/interior/ar/DepthPlacementController.kt` | Depth 모듈 연결과 앱 전용 판정 설정(`ROOM_CONFIG`) |
| `app/src/main/java/com/hackathon/interior/furniture/FurnitureController.kt` | 배치 판정 호출, 평면 대체 배치, 가구 생성과 서버 저장 |
| `app/src/main/java/com/hackathon/interior/settings/AppSettings.kt` | 서버 주소 정규화와 저장 |
| `app/src/main/res/layout/activity_catalog.xml` | 화보 화면 레이아웃. 사진 전체 + 점 layer + 설정 아이콘 |
| `app/src/main/assets/magazine/interior_magazine1.png`, `interior_magazine2.png` | 화보 atlas 원본 |

## Decisions

- **화보는 상품 목록이 아니라 공간 사진이다.** 가구를 하나씩 넘겨 보는 구조가 아니라, 한 장의 인테리어 사진 안에서 여러 가구를 각각 누르는 구조다.
- **별도의 `배치하기` 버튼을 두지 않는다.** 사진 속 가구 자체가 AR 진입점이다.
- **AR 배치와 사물 지우기를 한 화면에 둔다.** 두 기능을 화면으로 나누지 않는다.
- **UI는 `MagazineFeedProvider`만 본다.** 지금은 앱에 들어 있는 임시 데이터를 쓰고, 나중에 Web이나 Miso 구현으로 갈아끼우면 화면 코드는 그대로 둔다.
- **Depth 판정 완화는 앱 안에서만 한다.** 공용 모듈 `depth-placement-core`의 기본값은 바꾸지 않았다. 다른 실험이 그 기본값에 기대고 있기 때문이다.

## Constraints

- **Gradle은 JDK 21로 실행한다.** Android Studio 기본 JBR이 JDK 25라 그대로 쓰면 `depth-placement-core` 설정 단계에서 build가 깨진다.

  ```bash
  JAVA_HOME="/c/Program Files/Java/jdk-21.0.12" ./gradlew.bat :app:assembleDebug
  ```

- 실기기가 보안 잠금 상태이면 `adb`로 화면을 찍을 수 없다. 검은 화면만 나온다.
- 이 앱은 ARCore를 쓴다. iOS는 지원하지 않는다. 아래 `Known Issues`의 iOS 항목을 참고한다.

### 새 화보를 추가하는 방법

`MockMagazineFeedProvider`에 `MagazinePage`를 하나 더 넣으면 된다. 화면 코드는 건드리지 않는다.

```kotlin
MagazinePage(
    id = "kids-room",
    asset = "magazine/interior_magazine2.png",  // assets 안의 경로
    issue = "KIDS ROOM",
    title = "아이의 상상이 자라는 공간",
    description = "",
    crop = AtlasCrop(0.6682f, 0.2961f, 0.9977f, 0.5743f),  // 원본 기준 0..1 비율
    objects = listOf(
        MagazineObject("bed-house-01", "하우스 프레임 침대", "bed", 1.00f, 1.45f, 1.95f, "floor", 0.58f, 0.60f),
    ),
)
```

- `crop`은 원본 이미지 기준 `0.0..1.0` 비율이다. 픽셀 좌표를 원본 가로·세로로 나눠 구한다.
- 객체의 `x`, `y`는 crop 안에서의 비율이다. 왼쪽 위가 `0, 0`이다.
- 좌표는 PC에서 원본을 잘라 점을 찍어 보면 빠르게 맞출 수 있다. 이번 작업도 그렇게 8개 지면의 좌표를 잡았다.
- 화보 전용 사진 파일을 따로 받으면 `crop`을 `0, 0, 1, 1`로 두고 그 파일을 `asset`에 지정하면 된다.

## Known Issues

- **배치 후 배율이 최소값으로 떨어지는 현상.** 소파를 놓으면 실제 크기(220×78×95 cm, 배율 1.00)로 생기는데, 몇 초 뒤 배율이 0.30으로 줄어든 경우가 있었다. 0.30은 코드상 최소값이다. 배율을 바꾸는 경로는 핀치와 `＋－` 버튼뿐이라, 당시 휴대폰이 엎어져 있어 화면에 눌림이 들어갔을 가능성이 크다. 휴대폰을 손에 들고 배치한 뒤 그대로 두고 다시 확인해야 한다.
- **화보 사진이 저장소에 두 벌 들어 있다.** `app/src/main/assets/magazine/`과 `server/catalog/assets/magazine/`에 같은 파일이 있다. 약 4.8MB가 중복이다. 서버가 이 파일을 쓰지 않으면 한쪽을 지우는 것이 좋다.
- **화보 2~8페이지는 실기기에서 넘겨 보지 않았다.** 사진 내용과 점 위치는 PC에서 렌더링해 확인했다.
- **iOS는 지원하지 않는다.** ARCore 기반이라 아이폰에서 동작하지 않는다. 웹으로 옮겨도 해결되지 않는다. iOS Safari가 WebXR의 `immersive-ar`를 지원하지 않기 때문이다. 아이폰까지 가려면 세 가지 선택지가 있다.
  1. 화보를 웹으로 만들고, 아이폰은 AR Quick Look으로 가구만 놓아 보게 한다. 사물 지우기는 안 된다.
  2. Unity AR Foundation으로 옮긴다. 두 플랫폼 모두 기능이 같지만 AR 계층을 다시 쓴다.
  3. 네이티브 ARKit 앱을 따로 만든다.
- **가구 3D 모델 파일이 없다.** 카탈로그의 모든 항목이 `procedural_low_poly`이고, `ProceduralFurnitureFactory`가 상자를 조립해 모양을 만든다. 저장소에 `glb`도 `usdz`도 없다. iOS나 웹으로 확장하려면 모델을 GLB로 먼저 만들고 iOS용 USDZ를 변환해 뽑아야 한다. 안드로이드는 USDZ를 읽지 못한다.
- **화보와 hotspot 좌표가 아직 앱 안에 있다.** Web 또는 Miso 연동 시 이미지 URL과 객체 좌표를 응답으로 받아야 한다. 계약은 [sjkim0637-interior-magazine-ux.md](sjkim0637-interior-magazine-ux.md)에 적어 두었다.

## Verification

실기기 `SM-S908N`에서 확인한 것.

- 화보 첫 화면이 사진 전체로 나오고, 가구 위 점 세 개가 제자리에 붙는다.
- 점을 한 번 누르면 태그가 뜨고 페이지는 그대로 있다.
- 태그를 누르면 AR 화면이 해당 가구 선택 상태로 열린다.
- 바닥을 누르면 가구가 한 번에 배치된다. 크기는 220×78×95 cm, 배율 1.00으로 카탈로그 값 그대로다.
- 같은 화면에 배치 도구와 사물 지우기 도구가 함께 있다.

빌드 쪽.

- Android debug APK build 성공
- Android Lint 오류 0건
- Depth Core 테스트 통과

### Depth 배치 판정 조사 결과

실기기에서 "Depth를 더 모아 주세요"가 자주 뜨고 배치가 안 되던 문제를 합성 Depth로 재현해 원인을 찾았다.

| 상황 | 기존 기준 | 완화 후 |
|---|---|---|
| 서서 5m 넘는 바닥을 탭 | 실패(점 부족) | 통과 |
| Depth 노이즈 30mm | 실패(장애물로 오인) | 통과 |
| Depth 노이즈 50mm | 실패(장애물로 오인) | 실패 |

- 깊이 상한이 5m였다. 카메라 높이 1.4m에서 휴대폰을 수평에 가깝게 들면 화면 중앙 바닥이 5m를 넘어 판정에 쓸 점이 하나도 남지 않는다.
- 평면 허용 오차 2.5cm와 장애물 기준 5cm가 실제 Depth 노이즈보다 좁았다. 빈 바닥의 노이즈를 물체로 셌다.

`DepthPlacementController.ROOM_CONFIG`에서 상한 8m, 평면 오차 4.5cm, 장애물 10cm, 최소 신뢰도 0.40으로 조정했다.

## Next

1. 휴대폰을 손에 들고 배치한 뒤 배율이 1.00으로 유지되는지 확인한다. 혼자 줄어들면 배율 변경 경로를 다시 본다.
2. 화보 2~8페이지를 실기기에서 넘겨 보며 점 위치를 확인한다.
3. 중복된 화보 사진 한쪽을 정리한다.
4. Web 또는 Miso Provider의 이미지 URL, 객체 좌표, 3D 자산 ID 계약을 확정하고 `MockMagazineFeedProvider`를 교체한다.
5. 아이폰 지원 여부를 결정한다. 필요하면 가구 GLB 제작부터 시작한다.

## Relevant Commits

`agent/goguma-salad/interior-assets-ui` 및 `integration-interior-demo` 기준.

| Commit | 내용 |
|---|---|
| `bb038c4` | Asset 홈과 기능별 설정 화면 추가 |
| `9bbea8c` | 화보 속 객체 선택과 통합 AR 화면 구현 |
| `145518a` | 화보 위 가구 버튼을 숨기고 점 marker 탭 방식으로 변경 |
| `f1b4161` | 화보 화면을 인쇄 잡지 지면 구조로 재구성 |
| `07c8a8f` | 공급받은 화보 atlas 8면을 화보 화면에 연결 |
| `a80f3d6` | 한 번 탭했을 때 페이지가 넘어가던 문제 수정 |
| `75cfdbc` | Depth 배치 기준 완화와 평면 대체 배치 추가 |
| `3cd515c` | Depth 배치 실패 원인 조사 결과 기록 |

## Updated

2026-09-08
