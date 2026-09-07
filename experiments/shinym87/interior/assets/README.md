# Interior AR Design Assets

현재 Android 구현을 바탕으로 메인 분기 화면과 브랜드 표현에 사용할 원본 이미지 Asset을 보관한다.
이 폴더의 이미지는 UI 버튼이나 문구를 포함하지 않는다. 화면 제목, 설명, 접근성 Label은 Android
Resource에서 별도로 렌더링한다.

## Asset 목록

| 파일 | 원본 크기 | 권장 적용 위치 | 표시 방식 |
|---|---:|---|---|
| `app-icon-master.png` | 1254×1254 | 앱 아이콘 디자인 원본 | 정사각형 유지, Android Icon Resource는 후속 변환 |
| `home-hero.png` | 853×1844 | 메인 분기 화면 전체 배경 | `centerCrop`, 상단 여백에 제목·설명 배치 |
| `ar-interior-card.png` | 1254×1254 | `AR 인테리어` 진입 Card | 중앙 기준 정사각형 또는 4:3 Crop |
| `guide-flow-hero.png` | 1731×909 | 설명 페이지의 Scan→Select→Place 흐름 | `fitCenter`, 좌우 단계가 잘리지 않게 표시 |
| `settings-header.png` | 1693×929 | 설정 페이지 상단 Header | 중앙 `centerCrop`, 높이는 화면의 약 20% |

생성 Prompt 원문은 [`PROMPTS.md`](PROMPTS.md)에 기록한다.

## 기본 Palette

| 역할 | 색상 |
|---|---|
| Primary Blue | `#1660FF` |
| Deep Navy | `#0B1736` |
| Spatial Cyan | `#38D6D0` |
| Warm Neutral | `#F2EDE4` |
| White | `#FFFFFF` |

## 공통 원칙

- 이미지 내부에 문구, 버튼, Logo Wordmark를 넣지 않는다.
- 중요한 피사체는 중앙 Safe Area에 두고 가장자리는 Crop 여백으로 사용한다.
- 앱 아이콘을 제외한 화면 Asset 위에는 Android UI가 별도로 올라간다.
- 생성 원본은 PNG로 보관하고 실제 Android Resource 변환은 화면 구현 단계에서 수행한다.
- 앱 아이콘 Master는 배경을 포함한 RGB 이미지다. Adaptive Icon Foreground는 이 심볼을
  Vector로 다시 그려 투명 배경으로 만드는 방식을 권장한다.
