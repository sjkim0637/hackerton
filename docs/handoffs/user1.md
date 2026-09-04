# Handoff

## From

shinym87 (+ Claude)

## To

shinym87 (실기기 연결·검증 단계에서 이어서 진행)

## Workstream

[interior](../workstreams/interior.md) — 카메라 기반 공간 편집 / AR 가구 재배치.
PHASE 1 + PHASE 2 + PHASE 3 + PHASE 4 + PHASE 5 "사용자 1 (공간 / AR)".

## PHASE 5 — "여기로 옮기기" 를 즉시 드래그로 개선 (2026-09-04)

버튼 → 탭 → 배치 → 드래그(간접적)를, **삭제 완료 즉시 마커를 끌어 옮기는** 직접
조작으로 바꿨다. `MovedObjectController`.

1. **"여기로 옮기기" 버튼 제거 · 삭제 완료 시 자동으로 이동 가능 상태**
   - `arm()` 이 곧바로 `placeMarkerNow()` 로 삭제된 자리에 마커를 띄운다:
     `originalPose`(= 삭제 시점 벽/바닥 앵커 pose)가 있으면 그 자리, 없으면
     `source_region` 중심을 화면에서 `hitTestPreferring` 한 자리.
   - 삭제 직후 평면이 아직 없으면 `awaitingPlane=true` → `MainActivity.space.onFrame` 에
     추가한 `moved.onFrame()` 이 평면 인식되는 즉시 마커를 띄운다.
   - `placing` 모드 / `onTap()` 배치 경로 삭제. 탭은 그대로 큐브/카탈로그 몫
     (`MainActivity` 의 `onSingleTapConfirmed` 에서 `moved.onTap` 제거).
2. **손 떼는 순간 최종 배치** — 기존 `onDragBegin/onDrag/onDragEnd` 재사용.
   `onDragEnd` 가 손 뗀 pose 로 새 앵커를 만들어 재고정(`updateAnchorPose=true`) +
   500ms 디바운스 `POST /placements`. (큐브 `finalizeDrag` 와 동일 로직.)
   `canManipulate()` 에서 `!placing` 조건 제거 → 마커가 뜬 직후 바로 드래그 가능.
3. **마커를 터치하기 쉽게 조금 크게** — `applyChildTransforms()` 에서
   `imageNode.scale = Scale(scaleF * MARKER_SCALE)` (`MARKER_SCALE = 1.35f`).
   서버에 저장하는 `scale` 은 `scaleF`(마커 확대는 표시용, 저장 안 함). 라벨은
   "<종류> · 끌어 옮기기".
4. **"원위치" / "실행 취소" 유지** — `btnMovedHome`(원위치: 삭제된 자리로 되돌림),
   `btnMovedUndo`(실행 취소). "치우기"·"서버 배치 복원" 도 그대로.

빌드: `:app:assembleDebug` 성공. 실기기 확인 남음: 마커가 삭제 자리에 뜨는지,
드래그 추적감, 마커 크기(터치 편의) — 필요하면 `MARKER_SCALE` 조정.

## PHASE 5 — 서버 카탈로그에서 새 가구 배치 (2026-09-03)

지금까지 빈 평면을 탭하면 이름/크기를 직접 입력해 큐브를 만들었다. 이번엔 **서버
카탈로그(`GET /catalog`, 5종)**에서 골라 배치하는 진입점을 추가했다.

1. **카탈로그 목록** (`furniture/CatalogController.kt`, `activity_main.xml`)
   - 상단에 **"가구 추가"** 버튼(`btnAddFurniture`). 누르면 하단 `catalogPanel`(스크롤 목록)
     이 열리고 `GET /catalog` 결과를 항목마다 한 줄 버튼으로:
     `이름 / 카테고리(TV·소파…) / 000×000×000cm / 벽|바닥`. "닫기" 로 접는다.
   - `InteriorApiClient.getCatalog()` → `CatalogItem(id, name, category, w/h/d(m),
     thumbnailUrl, anchorHint)` 파싱.
2. **배치**
   - 항목을 고르면 `CatalogController` 가 썸네일(`thumbnail` URL)을 받아보고
     (`downloadBytes`, 실패/없으면 null), `onPick` → `FurnitureController.beginCatalogPlacement(
     name, w, h, d, wantWall = anchorHint=="wall", thumb)`.
   - 이후 평면 탭 → `handleTap` 이 `pendingCatalog` 분기 → `placeCatalog()` 가
     **`hitTestPreferring(x, y, wantWall)`** (PHASE 4 에서 만든 것 재사용)으로 TV·선반은 벽,
     소파·테이블·의자는 바닥에 우선 배치. 안내: "'<이름>' — 벽/바닥을 탭해 배치하세요".
   - 표시: 썸네일이 있으면 `ImageNode` quad, 없으면 **기존 그대로** 이름표 붙은 반투명 큐브
     (`FurnitureItem.imageNode` 가 null 이면 큐브, 있으면 큐브 숨기고 이미지).
     ✅ 사용자 2 가 `/assets/furniture/<종류>.png` 정적 서빙을 붙여, 이제 목록에서 고르면
     이미지 quad 로 뜬다 (앱 코드 변경 없음).
3. **조작 재사용 (중복 구현 없음)**
   - 카탈로그 가구도 **같은 `FurnitureItem` + 같은 제스처 로직**을 쓴다:
     드래그(`beginDrag/drag/endDrag`, `drag` 는 이제 종류에 맞는 평면을 우선 hitTest),
     핀치/＋－(`scaleSelectedBy`), 회전(신규 `rotateSelectedBy(15°)` — `FurnitureItem.rotationDeg`
     를 큐브·이미지에 함께 적용, `selectionPanel` 에 "회전 ⟳" 버튼 추가).
   - 배치 직후 `select(item)` 으로 바로 선택 상태 → 조작 패널이 뜬다.
4. **기존 기능과 구분**
   - "가구 추가"(상단, 카탈로그) ↔ 빈 평면 탭 시 이름/크기 입력 다이얼로그(기존 큐브 생성) ↔
     "여기로 옮기기"(PHASE 4, 삭제한 사물 재배치, `movedObjectPanel`) — **세 진입점이 서로
     다른 버튼/패널**. 카탈로그 배치 대기 중엔 상단 안내 문구가 바뀐다.

빌드: `:app:assembleDebug` 성공 (`app-debug.apk` ≈ 47MB). 실기기 확인 남음: 카탈로그
로드/배치, 회전 버튼 방향, 벽/바닥 자동 선택.

### ✅ 카탈로그 배치를 placements API 에 연동 완료 (2026-09-03)

`FurnitureController` 가 카탈로그 가구를 `source="catalog"` 로 서버에 저장·복원한다.

1. **sceneId 확보** — `CatalogController` 가 패널을 열 때(`onOpen` 콜백) →
   `FurnitureController.ensureCatalogScene()`. `prefs "catalog_scene_id"` 있으면 재사용,
   없으면 `createScene()` 한 번 하고 저장. (삭제/이동 흐름 scene 과 독립.)
2. **저장** (`saveCatalogPlacement` / `scheduleCatalogSave`)
   - `beginCatalogPlacement(... catalogItemId, objectType)` 로 카탈로그 id·카테고리를 받아
     `FurnitureItem.catalogItemId`/`objectType` 에 보관.
   - `placeCatalog()` 성공 + `finalizeDrag`(드래그 종료) + `scaleSelectedBy`(핀치/＋－) +
     `rotateSelectedBy` 마다 `scheduleCatalogSave(item)` → **500ms 디바운스** 후
     `POST /scenes/{id}/placements` 1회. body: `source:"catalog"`, `catalog_item_id`,
     `object_type`(카테고리), `pose{position[3],rotation[4]}`(앵커 월드 pose),
     `scale`, `rotation_deg`, `plane`. (다른 가구를 만지면 이전 예약분을 즉시 flush.)
   - `catalogItemId == null` 인 아이템(이름/크기 다이얼로그 큐브)은 저장 안 함.
3. **복원** (`restoreCatalogFromServer`, 세션당 1회)
   - "가구 추가" 패널 최초 열 때(기존 scene 이면) + "서버 배치 복원" 버튼
     (`MovedObjectController.onAlsoRestore` → 이것도 호출)에서 트리거.
   - `GET /scenes/{id}/placements` → `source=="catalog"` 만, **`catalog_item_id` 별 최신 1건**
     (append 로그라 조작마다 행이 쌓임 → 최신순 목록의 첫 등장). 각각 `GET /catalog/{id}` 로
     크기/카테고리, 썸네일 다운로드, `plane`(없으면 `anchor_hint`)에 맞춰 화면을 조금씩
     나눈 지점에서 `hitTestPreferring` → `createFurniture(autoSelect=false)`, 저장된
     `scale`/`rotation_deg` 적용.
   - `MovedObjectController` 의 removed_object 복원과 **독립적으로 함께 동작** — 서로 다른
     scene·노드. `latestActivePlacement()` 는 `source=="removed_object"` 만 고르도록 필터.
4. **실행 취소** — 기존 `POST /scenes/{id}/placements/undo` 그대로(출처 무관 최신 되짚기).
   카탈로그 가구는 로컬 "삭제" 버튼으로 화면에서 제거.

`InteriorApiClient`: `createPlacement(... source, catalogItemId)` 파라미터 추가(기본
`removed_object` → 기존 호출 하위호환), `listPlacements`, `getCatalogItem`, `Placement` DTO 에
`source`/`catalogItemId` 추가. 실서버 스모크 통과(카탈로그 3건 저장 → 목록 3 → 클라 dedup
2개(최신 편집 반영) → `GET /catalog/{id}` 크기 → undo). `:app:assembleDebug` OK.

**한계**: 같은 카탈로그 모델을 2개 놓으면 복원 시 1개로 합쳐진다(placements 에 개별 객체
식별자 없음, `catalog_item_id` 로만 dedup). 복원 위치는 `source_region` 이 없어 화면 기준 근사.

## PHASE 4 — 재배치를 서버(placements API)와 연동 (2026-09-03)

이동/회전/크기 상태를 **서버에 저장**하고, 앱을 다시 켜면(또는 "서버 배치 복원" 버튼으로)
`source_region` 기준으로 **현재 세션에 맞게 다시 hitTest 해서 복원**한다. "실행 취소" 버튼은
서버 `placements/undo` 를 호출하고 화면에서도 그 배치를 없앤다. (사용자 3 이 만든 API 사용.)

1. **배치 저장** (`MovedObjectController` + `InteriorApiClient.createPlacement`)
   - `RemovalController.onRemovalApplied` 콜백이 이제 `(sceneId, jobId, type, bitmap,
     originalPose, sourceRect, wM, hM)` 를 넘긴다 → `moved.arm(...)` 이 scene/job/원래 bbox 를
     보관하고 `SharedPreferences("interior")` 에 `moved_last_scene`/`moved_last_job` 저장.
   - 배치 확정("여기로 옮기기" 탭) · 드래그 종료(`onDragEnd`) · ＋－/핀치(`bump`) · 회전(`rotate`)
     마다 `scheduleSave()` → **500ms 디바운스** 후 `POST /scenes/{id}/placements` 1회
     (핀치 연속 이벤트를 합쳐 append 로그가 안 불어나게). body: `object_type`, `pose{position
     [3], rotation[4]}`(앵커 현재 월드 pose), `scale`, `rotation_deg`, `plane`("wall"/"floor"),
     `job_id`, `source_region`.
2. **배치 복원** ("서버 배치 복원" 버튼 / `restoreFromServer`)
   - 앱 재실행 시 `init` 이 `moved_last_scene` 를 읽어 패널을 열고 복원/취소 버튼만 활성화.
   - 버튼 → `GET /scenes/{id}/placements` 의 **맨 앞(최신) active** 를 받아
     `objectType/scale/rotation_deg/sourceRect` 적용, 사물 이미지는 서버의
     `{job}_object.jpg`(사용자 2) 를 받아 씀(없으면 플레이스홀더).
   - **pose 는 세션 로컬이라 안 쓴다** — `source_region` 중심을 현재 화면 좌표로 환산해
     `space.hitTestPreferring(cx, cy, plane=="wall")` → 새 앵커에 재배치. 평면 미인식이면
     "그 방향 비춘 뒤 다시 눌러주세요".
3. **실행 취소** ("실행 취소" 버튼 / `undoOnServer`)
   - `POST /scenes/{id}/placements/undo` → 200 이면 화면의 이동 노드 제거 + 예약된 저장 취소,
     404("취소할 배치 없음")면 안내만. "원위치"(로컬로 지운 자리 되돌리기)와는 별개 버튼.

`activity_main.xml` `movedObjectPanel` 에 버튼 2개 추가: `btnMovedRestore`("서버 배치 복원"),
`btnMovedUndo`("실행 취소"). `RemovalController.serverBaseUrl()`(부수효과 없는 주소 읽기) 신설.

검증: `POST/GET/undo` 라운드트립을 실서버로 스모크(앱이 만드는 JSON body 그대로) — 저장 2건 →
목록 2 → undo → 1 → undo → 0 → undo 404 → `include_undone` 2, 전부 정상. `:app:assembleDebug` OK.
실기기 확인 남음: 재실행 후 복원 시 hitTest 재정합 정확도.

---

## PHASE 4 — 삭제한 사물을 다른 위치로 "이동" (개념 증명) (2026-09-03)

> ⚠️ 아래 "여기로 옮기기" 버튼/`placing`/`onTap` 흐름은 **PHASE 5 (2026-09-04) 에서
> "삭제 즉시 마커 드래그" 로 대체됨** — 위쪽 최신 섹션 참고. 이하는 초기 구현 기록.

지금까지 "삭제"만 하던 걸, 삭제한 사물을 **새 위치로 옮겨** "지운 자리 + 새 자리"
두 곳에서 흔적을 보게 했다. 완벽한 3D 재배치가 아니라 개념 증명 수준.

**신규 `remove/MovedObjectController.kt`** — 큐브 배치 방식(탭 배치·드래그 이동·
핀치/＋－ 크기·회전)을 그대로 쓰되 표시는 큐브 대신 **캡처한 사물 이미지 quad**.

1. **원래 위치 저장** (`RemovalController`)
   - "삭제 요청" 캡처 콜백에서 키프레임을 디코드해 `bbox` 로 크롭한
     `capturedObjectBitmap`(= 삭제 전 사물 모습)과 `originalObjectPose`(= `wallAnchor.pose`,
     선택 영역 중심의 벽/바닥 앵커 pose)를 기억.
   - 삭제 완료(`runFlow` 끝)에서 `onRemovalApplied(type, bitmap, pose, wM, hM)` 콜백 →
     `MainActivity` 가 `moved.arm(...)` 호출. "선택 취소" 시 `onRemovalCleared()` → `moved.disarm()`.
2. **다른 위치로 이동**
   - 삭제가 끝나면 하단에 이동 패널(`movedObjectPanel`) 등장: `여기로 옮기기` /
     `원위치` / `치우기` + `－` `＋` `회전 ⟳`.
   - `여기로 옮기기` → `placing=true`, 새 지점 탭 → `MovedObjectController.onTap()` 이
     제스처를 가로채(`true` 반환 시 큐브로 안 넘어감) 그 자리에 `AnchorNode`+`ImageNode`
     (캡처 이미지, `EdgeFade.feather` 로 경계 블렌딩, 512px 상한 다운스케일) 생성.
   - `원위치` → 저장해 둔 `originalObjectPose` 에 다시 배치(지운 자리로 되돌리기).
   - 드래그 이동: `onDragBegin/onDrag/onDragEnd` — 큐브 `finalizeDrag` 와 동일하게
     드래그 중 `updateAnchorPose=false` + pose 직접, 끝나면 새 앵커로 재고정.
   - 크기: 핀치(`onScale`) + `－`/`＋`(1.15×, 0.3~3.0 클램프). 회전: `회전 ⟳` 15° 스텝.
   - 큐브가 선택돼 있으면(`furniture.hasSelection()`) 제스처는 큐브 몫 — 이동된 사물은
     건드리지 않는다. `MainActivity` 제스처가 `moved.*` → 실패 시 `furniture.*` 순.
3. **벽/바닥 자동 맞춤** (`ArSpaceController.hitTestPreferring(x, y, wantVertical)`)
   - TV·선반 → 수직(벽) 우선, 그 외(소파/테이블/의자/기타) → 수평(바닥) 우선으로 hitTest.
     원하는 평면이 없으면 아무 평면이나 붙이고 Toast 로 "원랜 벽/바닥이 어울린다" 안내.
   - 벽에 붙으면 quad 를 `Rotation(-90°, 0, rot)` 로 세우고(결과 quad 와 동일), 바닥이면
     `Rotation(0, rot, 0)` 로 세워 세운 사진처럼 표시. 라벨 "여기로 옮김 · <종류>".

### 아직 실기기 확인 필요 / 한계
- 빌드만 확인(`app-debug.apk` ≈ 47MB). 실기기에서 (a) 새 위치에 사물 이미지가 뜨는지,
  (b) 벽/바닥 자동 선택이 맞는지, (c) 드래그/핀치/회전이 자연스러운지 확인해야 한다.
- 이미지 quad 는 평면 사진이라 각도가 바뀌면 원근이 안 맞는다(개념 증명 한계).
- 이동된 사물엔 `onFrame` EMA 스무딩을 안 걸었다(ARCore 앵커 추적에 맡김). 결과 quad 처럼
  흔들리면 `RemovalController.onFrame` 방식을 이식하면 된다.
- 큐브 시스템(`FurnitureController`)은 `hasSelection()` 추가 외 변경 없음.

## PHASE 3 — 공간 정합: 결과 quad 안정화 + 경계 블렌딩 + 지터 스무딩 (2026-09-03)

기획서상 이 프로젝트의 기술적 핵심 단계. "완벽함보다 폰을 자연스럽게 움직여도 어색하지
않은 수준"이 목표. **결과 이미지(quad)** 를 벽 앵커에 더 견고하게 붙인다.

1. **카메라 이동 안정성 + 3. 지터 스무딩** (`RemovalController.onFrame()`)
   - `applyResult()` 에서 결과 `AnchorNode` 를 만들 때 `updateAnchorPose = false` 로 두고,
     `MainActivity` 의 `space.onFrame` 에서 `removal.onFrame()` 을 매 프레임 호출한다.
   - `onFrame()`: 앵커 pose 를 읽어 **위치에 이동 평균(EMA, `SMOOTH_ALPHA = 0.2`)** 을
     적용한 뒤 `resultNode.pose` 에 넣는다 → ARCore 재추적 지터가 완화된다.
   - 앵커 `trackingState`:
     - `TRACKING` → 스무딩 적용
     - `PAUSED`(잠깐 놓침) → **아무것도 안 함(마지막 위치 유지)** → "미끄러짐" 방지
     - `STOPPED`(영구 소실) → quad 숨김
   - 앵커는 이미 `HitResult.createAnchorOrNull()` 로 만들어 평면 trackable 에 붙어 있다
     (world anchor 보다 재추적에 강함).
2. **경계 알파 블렌딩** (`remove/EdgeFade.kt`)
   - 결과 이미지를 quad 로 붙이기 전에 `EdgeFade.feather()` 로 **네 가장자리 alpha 를
     0 으로 그라데이션**(짧은 변의 8%, `PorterDuff.DST_IN` + `LinearGradient` 4장, 모서리는
     두 램프가 곱해짐). 사각형 경계가 카메라 화면과 자연스럽게 섞인다.
   - `ImageNode` 는 RGBA 비트맵의 alpha 를 그대로 렌더한다(반투명 머티리얼).

### 아직 실기기 확인 필요
- 코드/빌드 완료. "폰을 움직여도 quad 가 벽에 붙어 보이는지 / 지터가 눈에 덜 띄는지 /
  경계가 덜 각져 보이는지" 는 실기기에서 눈으로 확인해야 한다.
- 튜닝 포인트: `SMOOTH_ALPHA`(작을수록 더 부드럽지만 반응 느림),
  `EdgeFade.feather(featherFrac=…)`(클수록 더 흐리게 섞임).
- 회전은 스무딩하지 않음(회전 지터가 상대적으로 작음). 필요하면 quaternion slerp 추가.

## PHASE 3 — 잘못된 결과 재발 방지: 사물 종류 필수 선택 + 캡처 오버레이 제거 (2026-09-03)

실기기에서 "책상 위 컵을 지우려 했는데 스피너가 TV 기본값이라 서버가 TV 전용 지시문
('벽 복원')을 받아 책상을 회색 벽으로 대체" 한 사례 진단(서버측 로그/DB 재구성) 반영.

1. **objectType 확장 — '기타/소품' + 필수 선택**
   - `OBJECT_TYPES` 에 `"other" to "기타/소품"` 추가 (목록에 없는 작은 물건: 컵·소품 등).
   - 스피너 0번은 안내 항목 `SPINNER_PROMPT = "사물 종류 선택…"` (실제 종류 아님).
     `binding.objectTypeSpinner.setSelection(0)` 로 시작.
   - `selectedObjectType()` → **`selectedObjectTypeOrNull(): String?`** — 0번이면 null.
   - `refreshRequestButton()`: `!busy && bboxNorm != null && selectedObjectTypeOrNull() != null`
     일 때만 '삭제 요청' 활성화. 스피너 `onItemSelectedListener` + `onRectSelected` +
     `clearSelection` + `setControlsEnabled` 에서 호출. **기본값 방치로 인한 오요청 불가.**
   - `requestRemoval()` 진입 시 종류 미선택이면 상태문구
     "지울 사물 종류를 먼저 선택하세요 (목록에 없으면 '기타/소품')" 후 return.
   - 서버(`app/ai/objects.py`)가 `other` 및 `cup/mug/plant/lamp/...` 별칭을 `other` 로
     정규화하고, `external.py` `_build_prompt` 가 `other`(및 힌트 없는 종류)면 TV 전용
     `_SURFACE_HINTS` 대신 **범용 `_DEFAULT_HINT`**("주변에 실제로 있던 면을 이어서 복원,
     없던 평평한 벽을 만들지 말 것")로 처리. 상세는 `docs/handoffs/user2.md`.
2. **키프레임 캡처 시 평면 격자/특징점 제거**
   - `ArSpaceController.setPlaneVisualizationEnabled(Boolean)` 신설
     (`sceneView.planeRenderer.isEnabled` 토글, 인식 자체는 계속 동작).
   - `MainActivity` 의 `beforeCapture`/`afterCapture` 람다(=`RemovalController` 와
     `BackgroundKeyframe` 이 공유)에 `space.setPlaneVisualizationEnabled(false/true)` 추가.
     캡처 직전 잠깐 끄고( `postDelayed` 100~120ms 뒤 `PixelCopy` ), 콜백에서 다시 켠다.
     기존 가구 노드 숨김과 같은 지점·같은 타이밍. → AI 로 가는 이미지에 흰 점/격자 안 찍힘.

빌드: `:app:assembleDebug` 성공 (`app-debug.apk` ≈ 47MB, `JAVA_HOME=jbr-21.0.11`).
실기기 확인 남음: (a) 스피너에서 '기타/소품' 고르고 컵 삭제 시 회색 벽 안 나오는지,
(b) 캡처 이미지에 평면 점/격자 사라졌는지.

## PHASE 2 — 선택 영역 실측 표시 (2026-09-03)

그린 사각형의 실제 가로/세로를 재서 사각형 근처에 표시. **"가구 크기"가 아니라
"선택 영역(내가 그린 박스)" 크기**임을 문구로 분명히 한다.

- `RemovalController.measureSelectionLabel(rect)`:
  - 좌상단-우상단(가로), 좌상단-좌하단(세로) 지점에서 `space.hitTest()` (ARCore).
  - 세 지점이 모두 평면에 닿으면 두 3D 좌표 사이 거리를 cm 로:
    `"선택 영역: 약 120cm × 80cm\n(내가 그린 박스 기준 · 가구 실측 아님)"`
  - 하나라도 실패(평면 미인식) → `"선택 영역: 정확한 측정 어려움\n(평면이 인식되지 않았어요)"`
- `BboxSelectionView.measurementText` (여러 줄) — 확정된 사각형 아래(공간 없으면 위)에
  옅은 청록 글씨로 그린다. `clear()` 시 함께 지워짐.
- `onRectSelected()` 에서 `measurementText` 설정. 큰 영역 경고 Toast 는 그대로.
- "영역 선택 모드" 첫 안내 문구 변경:
  > 지우고 싶은 사물에 딱 맞게 사각형을 그리면,
  > 결과 품질과 크기 측정 정확도가 모두 좋아집니다.

## PHASE 2 — 선택 영역 겹침 경고 (2026-09-03, 진단 실험 반영)

사용자 2 진단 실험에서 "사각형 안에 다른 가구가 겹치면 삭제 결과가 불안정"이 확인됨
(`docs/handoffs/user2.md`). 이를 UI 에 반영. **삭제를 막지는 않고 안내/경고만.**

- `RemovalController.toggleSelectionMode()` 켤 때 안내 문구 변경:
  > 지우고 싶은 사물 하나만 딱 맞게 사각형으로 그려주세요.
  > 다른 가구가 겹치지 않게 그릴수록 결과가 좋습니다.
- `RemovalController.onRectSelected()`: 그린 사각형 면적이 화면의
  `LARGE_SELECTION_FRACTION`(0.40) 이상이면
  `Toast` "선택 영역이 넓습니다. 다른 가구가 포함되지 않았는지 확인해주세요" 를
  `LENGTH_LONG` 으로 잠깐 표시. 상태 문구("영역 지정됨 …")와 '삭제 요청' 버튼은 그대로.
- 이제 종류별 드래그 안내에 사물 라벨을 넣지 않으므로 `selectedObjectLabel()` 제거
  (`selectedObjectType()` 만 사용).

## PHASE 2 — 사물 종류 선택 (2026-09-03)

"TV 선택 모드" 하나로 고정이던 걸, **지울 사물 종류를 고를 수 있게** 바꿨다.

- `activity_main.xml`: 서버 주소 입력창 아래에 `objectTypeSpinner`(Spinner) 추가.
  라벨: TV / 소파 / 테이블 / 의자 / 선반. 접힌 상태는 흰 글씨
  (`res/layout/spinner_item_light.xml`), 펼친 목록은 기본 흰 배경.
- `RemovalController`:
  - `OBJECT_TYPES = [("tv","TV"),("sofa","소파"),("table","테이블"),("chair","의자"),("shelf","선반")]`
    — 서버 키는 `server/catalog/furniture.json` 의 category 와 맞췄다.
  - `selectedObjectType()` 가 스피너 선택값의 **서버 키**를 돌려준다.
  - "삭제 요청" 시점에 그 값을 잡아 `buildMetaJson(... objectType)` 의 `targetObject.objectType`
    와 `requestRemoveObject(... objectType)` 에 그대로 넣는다. **더 이상 "tv" 하드코딩 없음.**
  - 버튼 라벨 "TV 선택 모드" → "영역 선택 모드". 드래그 안내 문구에 선택한 종류 표시.
  - 처리 중에는 스피너도 비활성화(`setControlsEnabled`).
- 서버는 `object_type` 을 자유 문자열로 받으므로(스키마 `str`) 변경 불필요.
  Gemini 프롬프트가 `Remove the {object_type} ...` 로 그대로 반영된다.
- 나머지 흐름(선택 영역 표시 · 삭제 버튼 · 결과 수신 · 벽면 적용 · 삭제 전/후)은 그대로.

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
   ├─ MainActivity.kt                          Removal/Moved/Catalog 배선 + 공유 before/afterCapture + 제스처 라우팅
   ├─ ar/ArSpaceController.kt                  latestFrame, setPlaneVisualizationEnabled(), hitTestPreferring()
   ├─ furniture/
   │  ├─ FurnitureController.kt                큐브/카탈로그 가구 배치·드래그·핀치·회전·삭제 + 카탈로그 placements 저장/복원 (ensureCatalogScene/scheduleCatalogSave/restoreCatalogFromServer)
   │  ├─ FurnitureItem.kt                      노드 묶음 (imageNode?/rotationDeg/catalogItemId?/objectType 추가)
   │  └─ CatalogController.kt                  PHASE 5: "가구 추가" → GET /catalog 목록 패널 → onOpen/onPick
   ├─ keyframe/BackgroundKeyframe.kt           "배경 촬영" 캡처 (동일 오버레이 제거 적용)
   └─ remove/
      ├─ BboxSelectionView.kt                  드래그로 사각형 지정, clear() 로 지움
      ├─ InteriorApiClient.kt                  HttpURLConnection + org.json (createPlacement(source/catalogItemId)/listPlacements/getCatalog(Item)/undoPlacement)
      ├─ MovedObjectController.kt              PHASE 4/5: 삭제 즉시 뜨는 마커를 끌어 이동(핀치/회전, 벽·바닥 자동) + placements 저장·복원·undo
      └─ RemovalController.kt                  서버주소(serverBaseUrl)·사물종류(필수)·선택취소·캡처·메타·플로우·결과 quad·전/후·원래위치+scene/job 전달
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

## 캡처 버그 수정 (2026-09-03)

실기기에서 키프레임이 "카메라 없이 검은 배경 + UI 위젯"으로 저장되던 문제:
`ARSceneView` 는 `SurfaceView` 라 카메라·3D 가 별도 서피스에 그려지는데
`PixelCopy.request(window, ...)` 는 그 서피스를 포함하지 않아 카메라가 검게 나왔다.
→ `PixelCopy.request(sceneView /* SurfaceView */, bitmap, listener, handler)` 로 교체.
`RemovalController.captureSceneJpeg` 와 `BackgroundKeyframe.capture`("배경 촬영",
동일 버그) 둘 다 수정. UI 오버레이는 서피스 밖이라 자동 제외된다.

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
