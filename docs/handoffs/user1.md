# Handoff

## From

shinym87 (+ Claude)

## To

shinym87 (실기기 연결·검증 단계에서 이어서 진행)

## Workstream

[interior](../workstreams/interior.md) — 카메라 기반 공간 편집 / AR 가구 재배치.
PHASE 1 "사용자 1 (공간 / AR)" 남은 작업.

## Completed

PHASE 1 사용자 1 항목(P1-2, P1-3, P1-8, P1-9)을 Android 앱에 구현했다.
서버(사용자 3)와 "캡처 → 전송 → 응답 → 화면 적용" 흐름을 연결했다.

- **P1-2 TV 탭 선택 + bbox 지정**: `remove/BboxSelectionView.kt` — `ARSceneView`
  위에 얹는 커스텀 오버레이 뷰. "TV 선택 모드" 버튼(`btnTvSelectMode`)을 켜면
  화면을 드래그해 사각형을 그리고, 그 영역이 "제거할 사물의 bounding box" 가 된다.
  모드가 꺼져 있으면 터치를 소비하지 않아 기존 큐브 생성/드래그에 영향이 없다.
- **P1-3 키프레임 생성 → 서버 호출**: `remove/RemovalController.kt` +
  `remove/InteriorApiClient.kt`.
  - "삭제 요청" 버튼(`btnRequestRemove`) → `PixelCopy` 로 현재 화면 캡처(JPEG q90).
  - 캡처 시점의 카메라 pose/intrinsics(`ArSpaceController.latestFrame`)와 벽 평면
    정보, bbox 를 `docs/data-model.md` 4절 형식의 메타 JSON 으로 만든다.
  - `POST /scenes` → `POST /scenes/{id}/keyframes` (multipart: `image` + `meta`).
  - 서버 주소는 `InteriorApiClient.DEFAULT_BASE_URL = "http://localhost:8000"` 하드코딩.
- **P1-8 결과 폴링 + 화면 적용**:
  - `POST /scenes/{id}/remove-object` → `GET .../jobs/{id}` 를 1초 간격 폴링(최대 60초).
  - `done` 이면 `GET .../results/{job}.jpg` 로 결과 이미지를 받아
    `changed_region`(없으면 bbox)으로 잘라 벽 평면 앵커에 `ImageNode` quad 로 붙인다.
  - 벽 앵커를 못 잡으면 전체화면 `ImageView`(`resultOverlay`)로 대체 표시.
- **P1-9 삭제 전/후 전환**: `btnToggleRemoval` 로 결과 quad(또는 오버레이) 표시/숨김.

의존성 추가: `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`,
`org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1`.
매니페스트: `INTERNET` 권한 + `android:usesCleartextTraffic="true"`(로컬 http).

## Important Files

```
experiments/shinym87/interior/app/src/main/
├─ AndroidManifest.xml                         INTERNET + usesCleartextTraffic
├─ res/layout/activity_main.xml                bboxSelectionView / resultOverlay /
│                                              btnTvSelectMode / btnRequestRemove /
│                                              btnToggleRemoval / removalStatusText 추가
└─ java/com/hackathon/interior/
   ├─ MainActivity.kt                          RemovalController 배선
   ├─ ar/ArSpaceController.kt                  latestFrame 노출
   └─ remove/
      ├─ BboxSelectionView.kt                  드래그로 사각형 지정
      ├─ InteriorApiClient.kt                  HttpURLConnection + org.json, baseUrl 하드코딩
      └─ RemovalController.kt                  캡처·메타·플로우·결과 quad·전/후 토글
```

## Decisions

- 네트워킹은 의존성 최소화를 위해 OkHttp 없이 `HttpURLConnection` + `org.json`.
- 서버 주소는 우선 `http://localhost:8000` 하드코딩(요청 사항). 실기기에서는 PC IP 로
  바꿔야 함 — `InteriorApiClient.DEFAULT_BASE_URL` 한 곳.
- 결과 이미지는 "바뀐 영역"만 잘라 벽에 붙인다(전체 키프레임 X). 정밀 정합은 PHASE 3.
- 3D quad 의 방향(수직 평면에서 X축 -90°)·크기(사각형 네 변 hitTest 거리)는 대략치.
  실기기에서 다듬는다.

## Constraints

- **실기기 네트워크 검증은 아직 안 함**(요청대로 코드/빌드까지만). `localhost` 는
  기기 자신을 가리키므로 실기기에서는 서버 PC 의 LAN IP 로 바꿔야 한다.
- 서버는 mock(Pillow) 이므로 결과는 "TV 자리를 벽 색으로 덮은" 수준. 정합/품질 기대 X.
- 캡처는 화면 픽셀(`PixelCopy`) 기준이라 카메라 원본 해상도/intrinsics 와 정확히
  일치하지 않는다. 메타의 intrinsics 는 스케일 근사값.
- "TV 선택 모드" 중에는 SceneView 제스처(큐브 생성/드래그)가 막힌다(의도).

## Known Issues

- 빌드 검증: **`:app:assembleDebug` 성공** (`app/build/outputs/apk/debug/app-debug.apk`,
  약 46MB). 단, Android Studio 번들 JBR 이 25 라 Gradle 8.11.1 이 실행되지 않는다
  ("Unsupported class file major version 69"). 이 PC 에서는
  `JAVA_HOME=C:\Users\User\.jdks\jbr-21.0.11` 로 빌드했다. → android-build-env-gotchas 메모
- SceneView `onScale`(핀치)와 마찬가지로 `ImageNode` quad 의 최종 방향은 실기기
  확인 필요.
- job 폴링 중 Activity 가 죽으면 `lifecycleScope` 로 취소된다(진행 중 작업은 유실).

## Next

1. 서버를 `uvicorn app.main:app --host 0.0.0.0` 로 띄우고, 앱의
   `InteriorApiClient.DEFAULT_BASE_URL` 을 PC LAN IP 로 바꿔 실기기에서
   캡처→전송→응답→화면적용 한 번 관통 확인 (P1-11 데모).
2. 서버 `app/ai/external.py` 의 `TODO(P1-10)` 를 실제 외부 AI 호출로 교체.
3. 결과 quad 의 방향/스케일/정합 다듬기 (PHASE 3 로 이어짐).

## Relevant Commits

- `feat(interior): PHASE 1 앱 — TV 영역 지정·키프레임 전송·결과 폴링/화면적용`
  (이 커밋; 브랜치 `agent/shinym87/interior`)
