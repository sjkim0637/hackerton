# sjkim0637 전달: Interior Magazine UX 및 콘텐츠 연동 요청

## 받는 사람

`sjkim0637`

## 확정된 UX

카탈로그를 가구 하나씩 보여주는 상품 Feed로 만들지 않는다. 인테리어 잡지처럼 완성된 공간 사진을 세로로 한 장씩 넘겨 본다. 사용자는 사진 속 소파, 테이블, 조명 등 객체를 직접 누른다. 별도의 `이 가구 배치하기` 버튼은 두지 않는다.

객체를 누르면 다음 정보가 선택된 채 AR 화면으로 이동한다.

- 객체 ID와 표시 이름
- 종류(`sofa`, `table`, `chair`, `lamp`, `decor` 등)
- 실제 폭·높이·깊이(m)
- 바닥 또는 벽 Anchor 정보
- 3D Asset 식별자 또는 내려받을 URL

AR 화면에서는 가구 배치와 사진 속 기존 사물 지우기를 동시에 사용할 수 있어야 한다. 두 기능을 별도 화면으로 나누지 않는다.

## 콘텐츠 공급 요청

화보와 hotspot을 화면 코드에 매번 하드코딩하지 않도록 Web 또는 Miso에서 아래 구조를 공급해 달라. 현재 Android 구현은 같은 계약의 `MockMagazineFeedProvider`를 사용한다.

```json
{
  "pages": [
    {
      "id": "warm-reading-room",
      "imageUrl": "https://.../room.jpg",
      "issue": "SEPTEMBER · LIVING",
      "title": "빛이 머무는 독서 공간",
      "description": "...",
      "objects": [
        {
          "id": "lounge-chair-01",
          "name": "라운지 체어",
          "category": "chair",
          "x": 0.31,
          "y": 0.72,
          "widthM": 0.82,
          "heightM": 0.88,
          "depthM": 0.78,
          "anchorHint": "floor",
          "assetUrl": "https://.../chair.glb"
        }
      ]
    }
  ]
}
```

`x`, `y`는 원본 이미지 기준 `0.0..1.0` 상대 좌표로 맞춘다. 여러 화면비에서도 같은 객체 위치를 찾기 위해 픽셀 좌표 대신 상대 좌표가 필요하다. 가능하면 단일 점 외에 `bbox` 또는 segmentation mask도 함께 제공하면 객체 전체를 자연스럽게 터치 영역으로 사용할 수 있다.

## 완료 기준

- 위아래 Swipe로 공간 화보가 한 장씩 전환된다.
- 한 화보에 여러 객체를 선택할 수 있다.
- 사진 속 객체를 직접 누르면 해당 가구가 선택된 AR 화면이 열린다.
- 별도 `배치하기` CTA가 없다.
- Android UI에 콘텐츠 목록을 직접 추가하지 않고 Provider 응답만 바꿔 새 화보를 노출한다.
- AR 배치와 사물 지우기가 같은 작업 화면에 존재한다.
