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
  - **서버 주소는 화면 상단 입력창(`serverUrlInput`)에서 지정**한다. 실기기에서는
    `localhost` 가 폰 자신을 가리키므로 서버 PC 의 LAN IP(예 `http://192.168.0.10:8000`)를
    넣어야 한다. 입력값은 `SharedPreferences("interior")` 에 저장돼 다음 실행에도 유지.
    스킴 생략 시 `http://` 자동 보정, 끝 슬래시 제거.
- **선택 취소**: `btnClearSelection` — 지정한 영역/그린 사각형/결과 quad 를 모두 지운다.
  "TV 선택 모드" 를 다시 켜면 이전 선택을 자동으로 지우고 새로 그린다.
- **P1-8 결과 폴링 + 화면 적용**:
  - `POST /scenes/{id}/remove-object` → `GET .../jobs/{id}` 를 1초 간격 폴링(최대 120초).
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
├─ res/layout/activity_main.xml                serverUrlInput / bboxSelectionView /
│                                              resultOverlay / btnTvSelectMode /
│                                              btnClearSelection / btnRequestRemove /
│                                              btnToggleRemoval / removalStatusText
└─ java/com/hackathon/interior/
   ├─ MainActivity.kt                          RemovalController 배선
   ├─ ar/ArSpaceController.kt                  latestFrame 노출
   └─ remove/
      ├─ BboxSelectionView.kt                  드래그로 사각형 지정, clear() 로 지움
      ├─ InteriorApiClient.kt                  HttpURLConnection + org.json, baseUrl 주입
      └─ RemovalController.kt                  서버주소(prefs)·선택취소·캡처·메타·플로우·결과 quad·전/후
```

## Decisions

- 네트워킹은 의존성 최소화를 위해 OkHttp 없이 `HttpURLConnection` + `org.json`.
- 서버 주소는 화면 상단 입력창에서 지정하고 `SharedPreferences` 에 저장한다.
  기본값 `http://192.168.0.2:8000` (거의 항상 사용자가 바꿔야 함).
- 결과 이미지는 "바뀐 영역"만 잘라 벽에 붙인다(전체 키프레임 X). 정밀 정합은 PHASE 3.
- 3D quad 의 방향(수직 평면에서 X축 -90°)·크기(사각형 네 변 hitTest 거리)는 대략치.
  실기기에서 다듬는다.

## Constraints

- 실기기에서 서버에 연결하려면: ① 서버를 `uvicorn app.main:app --host 0.0.0.0` 로
  띄우고 ② 폰과 PC 가 같은 Wi-Fi ③ 앱 상단 입력창에 `http://<PC-LAN-IP>:8000`
  입력 ④ Windows 방화벽에서 8000 포트(또는 python) 인바운드 허용.
  `localhost` / `127.0.0.1` 로 두면 폰 자신을 가리켜 "Failed to connect" 가 난다.
- 서버가 mock 이면 결과는 "영역을 벽 색으로 덮은" 수준, external(Gemini) 이면 실제 복원.
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

1. 실기기 P1-11: 서버 `--host 0.0.0.0`, 앱 입력창에 PC LAN IP → 캡처→전송→응답→화면적용
   한 번 관통 + 데모 녹화. (서버는 external=Gemini 로 이미 검증됨 — P1-10 완료)
2. 결과 quad 의 방향/스케일/정합 다듬기 (PHASE 3 로 이어짐).
3. 벽 앵커를 못 잡을 때가 잦으면 전체화면 fallback UX 개선.

## Relevant Commits

- `feat(interior): PHASE 1 앱 — TV 영역 지정·키프레임 전송·결과 폴링/화면적용`
  (이 커밋; 브랜치 `agent/shinym87/interior`)
