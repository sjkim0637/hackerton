# 데이터 형식 정의 (PHASE 0)

앱 ↔ 서버 ↔ 외부 AI 사이에서 쓰는 형식을 한곳에 모은다. MVP 기준이며, 확장 필드는
"확장"으로 표시한다.

## 1. 좌표계

- 기준: **ARCore world space** — 오른손 좌표계, **Y-up**, 단위 **미터(m)**.
- 원점: AR 세션 시작 시점의 기기 pose.
- 모든 배치/복원 결과는 ARCore `Anchor` 에 귀속시킨다. 좌표값만 저장하지 않는다.
- 회전은 쿼터니언 `[x, y, z, w]`.
- 서버로 넘길 때의 카메라 정보:
  - `cameraIntrinsics`: `{ fx, fy, cx, cy }` (픽셀)
  - `worldToCamera`: 4x4 행렬, **row-major**, 길이 16 배열
  - `imageSize`: `{ width, height }` (키프레임 픽셀 크기)
- 벽 평면: ARCore `Plane`(`VERTICAL`) 의 `center`(pose), `normal`(단위 벡터),
  `extent`: `{ x, z }` (미터).

## 2. 식별자(ID) 규칙

| 대상 | 형식 | 예시 |
|---|---|---|
| 작업 세션 | `scene_<uuid8>` | `scene_9f3a1c02` |
| 인식/편집 대상 사물 | `obj_<scene8>_<seq3>` | `obj_9f3a1c02_001` |
| 키프레임 | `kf_<scene8>_<seq3>` | `kf_9f3a1c02_002` |
| 가구 카탈로그 항목 | `cat_<category>_<slug>` | `cat_sofa_nordic-3seat` |
| 배치 인스턴스 | `obj_<scene8>_<seq3>` (사물과 동일 규칙) | `obj_9f3a1c02_005` |

- `seq` 는 세션 안에서 1부터 증가. 세션 로컬이며 전역 유일성은 보장하지 않는다.
- `category` 값(초기): `tv`, `sofa`, `table`, `chair`, `shelf`, `etc`.

## 3. 사물 영역(마스크) 표시 형식

- **MVP**: 축 정렬 사각형.
  ```json
  { "type": "bbox", "rect": [x, y, w, h] }
  ```
  `x, y, w, h` 는 키프레임 이미지 기준 **정규화 값(0~1)**.
- **확장**: 픽셀 마스크.
  ```json
  { "type": "mask", "png": "<base64 PNG>", "size": { "width": 1920, "height": 1080 } }
  ```
  키프레임과 같은 해상도, 흰색=대상 / 검정=배경 8bit 알파.
- 서버는 `bbox` 를 받으면 사각형을 채운 마스크로 변환해 AI 어댑터에 넘긴다.

## 4. 대표 이미지(키프레임) 형식

- 이미지: **JPEG**, 장변 ≤ 1920px, quality 85, sRGB.
- 실시간 카메라 영상은 전송하지 않는다. **편집 명령 실행 시점에만** 1장 캡처.
- 동반 메타(JSON):
  ```json
  {
    "keyframe_id": "kf_9f3a1c02_002",
    "scene_id": "scene_9f3a1c02",
    "capturedAt": "2026-09-02T10:30:00Z",
    "imageSize": { "width": 1920, "height": 1080 },
    "cameraIntrinsics": { "fx": 1400.0, "fy": 1400.0, "cx": 960.0, "cy": 540.0 },
    "worldToCamera": [ /* 16 floats, row-major */ ],
    "wallPlane": {
      "center": { "position": [x, y, z], "rotation": [x, y, z, w] },
      "normal": [x, y, z],
      "extent": { "x": 3.2, "z": 2.4 }
    },
    "targetObject": {
      "id": "obj_9f3a1c02_001",
      "object_type": "tv",
      "region": { "type": "bbox", "rect": [0.32, 0.28, 0.24, 0.18] }
    }
  }
  ```

## 5. 카메라 입력 형식

- 앱 내부: ARCore 가 관리하는 카메라 텍스처를 그대로 렌더 배경으로 사용. 저장/전송 없음.
- 서버 전송: 위 4번 키프레임(JPEG + 메타) 만.
- 다중 촬영(C 방식 실험)에서만 여러 키프레임을 누적한다. MVP 범위 밖.

## 6. 가구 데이터 형식

카탈로그 항목:
```json
{
  "id": "cat_sofa_nordic-3seat",
  "name": "노르딕 3인 소파",
  "category": "sofa",
  "size_m": { "w": 2.1, "h": 0.85, "d": 0.95 },
  "model": { "type": "glb", "url": "/assets/nordic-3seat.glb", "placeholder": "cube" },
  "thumbnail": "/assets/nordic-3seat.png",
  "anchor_hint": "floor"
}
```
- `anchor_hint`: `floor` | `wall`.
- `model.placeholder`: 모델 로드 전/실패 시 대체 표현. MVP 는 항상 `cube`.

배치 인스턴스(앱 상태 / 서버 저장 공용):
```json
{
  "instance_id": "obj_9f3a1c02_005",
  "scene_id": "scene_9f3a1c02",
  "catalog_id": "cat_sofa_nordic-3seat",
  "source": "catalog",
  "pose": { "position": [x, y, z], "rotation": [x, y, z, w] },
  "scale": 1.0,
  "plane": "floor",
  "state": "proposed"
}
```
- `source`: `catalog`(새 가구) | `existing`(현실에서 떼어낸 기존 가구).
- `state`: `proposed`(반투명 제안) | `placed`(확정) | `removed`(삭제됨).
- `existing` 인스턴스는 원래 위치를 `origin_pose` 로 함께 저장한다(재배치 되돌리기용).

## 7. 복원 결과 형식

```json
{
  "job_id": "job_9f3a1c02_002",
  "keyframe_id": "kf_9f3a1c02_002",
  "status": "done",
  "result_image_url": "/scenes/scene_9f3a1c02/results/job_9f3a1c02_002.jpg",
  "changed_region": { "type": "bbox", "rect": [0.30, 0.25, 0.30, 0.24] }
}
```
- `status`: `queued` | `running` | `done` | `failed`.
- 앱은 `result_image_url` 을 받아 `wallPlane` 기준 quad 텍스처로 벽에 고정한다.
