# Geo-Time AR UI용 이미지 Asset 생성 지시서

이 문서는 전체 화면 시안이 아니라 Android Native UI 안에 넣을 **이미지 조각**을 만들기 위한 Prompt 모음이다. Button, Text, Card, Toggle, 입력창은 Android에서 직접 구현하고 아래의 Brand·Illustration·AR Marker만 이미지 Asset으로 사용한다.

## 정식 Asset 파일 기준

- 앱에서 참조하거나 후속 UI 구현에 사용할 정식 결과물은 `assets/`의 날짜·차수 표기가 없는 PNG 파일이다.
- 투명 배경 대상은 Alpha Channel을 포함하며, `brand-app-icon-master.png`와 `start-background.png`만 불투명하다.
- `-01`, `-02`, `-03` 등이 붙은 JPEG·PNG는 생성 원본 후보로 보존하며 앱에서 직접 참조하지 않는다.

## 제작 원칙

- 각 Prompt는 한 번에 Asset 하나만 생성한다.
- 별도 언급이 없으면 정사각형 `2048x2048`로 생성한다.
- 배경은 완전 투명한 PNG로 요청한다.
- 그림 주변에 최소 15%의 안전 여백을 둔다.
- Text, 숫자, Button, UI Panel, 스마트폰 Frame을 넣지 않는다.
- 모든 Asset은 같은 Seed와 Style Reference를 사용한다.
- 얇은 선이 작은 화면에서 사라지지 않도록 핵심 외곽선은 충분히 굵게 만든다.
- 완성된 PNG를 전달하면 필요한 크기와 Android Density로 재가공한다.

## 공통 Style Prompt

각 Asset Prompt 앞에 다음 문장을 붙인다.

```text
Geo-Time AR 모바일 앱에 실제로 사용할 단일 UI image asset. 정제된 near-future cyberpunk style, deep navy black, electric cyan spatial tracking light, warm amber temporal memory light, creator 기능에만 제한적인 violet magenta, clean geometric shape, subtle holographic depth, restrained glow, crisp edge, production-ready game-quality icon but not a game HUD, isolated object centered, generous empty margin, transparent background PNG, no text, no number, no button, no panel, no device mockup, no watermark.
```

## 공통 Negative Prompt

각 Prompt 뒤에 다음 문장을 붙인다.

```text
full screen UI, dashboard, smartphone frame, hand, scenery, city background, text, letters, numbers, logo typography, button, card, menu, watermark, rainbow neon, excessive glow, blurry edge, dirty dystopian texture, anime, character, robot, weapon, skull, crypto symbol, stock chart, Matrix code 금지.
```

## P0 — 먼저 필요한 필수 Asset

### AS01. App Icon 원본

파일명: `brand-app-icon-master.png`

배경 투명 대신 App Icon 자체의 짙은 Navy Rounded Square 배경을 포함한다.

```text
Geo-Time AR app icon master. 짙은 navy rounded square 안에 공간 좌표를 의미하는 하나의 cyan tracking ring과 시간 잔상을 의미하는 두 개의 어긋난 amber echo arc를 결합한다. 중앙에 작은 warm amber memory point. 매우 단순하고 강한 silhouette, 48px 크기에서도 인식 가능, 문자 G와 T를 사용하지 않음, icon 하나만 화면 중앙에 배치.
```

### AS02. Brand Symbol 투명본

파일명: `brand-symbol.png`

```text
App icon과 정확히 같은 Geo-Time AR symbol만 분리. Rounded square 배경 없이 cyan tracking ring, amber echo arcs, central memory point로 구성된 단일 brand symbol. Splash와 시작 화면 상단에 사용할 수 있도록 수평·수직 균형이 정확하고 투명 배경.
```

### AS03. 시작 화면 Background

파일명: `start-background.png`

규격: `1440x3088`, 이 Asset만 불투명 세로 배경이다.

```text
Geo-Time AR 시작 화면용 세로 background art only. deep navy black 공간에 희미한 3D spatial grid, 멀리 사라지는 cyan point cloud, 중앙 아래에서 위로 번지는 아주 약한 amber time echo, 공간을 스캔한 depth map 같은 추상적 구조. 카드나 글자가 올라갈 중앙과 하단은 어둡고 비어 있어야 한다. 사람, 건물, 글자, UI component 없이 고급스럽고 절제된 cyberpunk ambient background.
```

### AS04. Phone Viewer Mode Illustration

파일명: `mode-phone-viewer.png`

```text
Phone Viewer mode card에 들어갈 단일 illustration. 정면을 향한 단순한 smartphone camera outline이 현실 공간의 작은 amber memory point를 cyan spatial ring으로 scan하는 모습. 실제 phone mockup이 아니라 상징적인 geometric hologram. 오른쪽 위 방향으로 약간의 depth, compact composition, transparent background.
```

### AS05. Glass Mode Illustration

파일명: `mode-glass.png`

```text
Glass mode card에 들어갈 단일 illustration. 가볍고 현실적인 AR glasses visor silhouette, 양쪽 lens 앞에 매우 얇은 cyan 6DoF spatial points, 시야 중앙이 아닌 한쪽 공간에 작은 amber Moment echo. 거대한 VR headset가 아니라 일상형 optical AR glass, 정면 3/4 view, transparent background.
```

### AS06. Creator Mode Illustration

파일명: `mode-creator.png`

```text
Creator mode card에 들어갈 단일 illustration. camera aperture와 record point, 공간에 고정되는 holographic anchor를 하나의 symbol illustration으로 결합. warm amber 중심, violet magenta edge, small cyan spatial coordinates. 촬영한 순간이 공간에 봉인되는 느낌, transparent background.
```

### AS07. 기본 Moment Marker

파일명: `marker-moment-default.png`

```text
AR camera 위 실제 POI 위치에 표시할 기본 Moment marker sprite. 중앙의 작은 amber memory core, 그 주위를 감싸는 cyan spatial location ring, 아래쪽으로 매우 짧은 anchor stem. 현실을 가리지 않는 open shape, 원형이지만 일반 지도 pin처럼 보이지 않음, front-facing, transparent background, glow 범위가 잘리지 않음.
```

### AS08. Moment Stack Marker

파일명: `marker-moment-stack.png`

```text
같은 장소에 여러 시간 기록이 있음을 나타내는 AR marker sprite. AS07의 amber core와 cyan ring을 유지하면서 뒤쪽에 3개의 어긋난 temporal echo ring이 겹쳐 보인다. 숫자 badge와 글자 없이도 stack이라는 의미가 명확해야 한다. front-facing, transparent background.
```

### AS09. Focus·응시 Marker Ring

파일명: `marker-focus-ring.png`

```text
기본 Moment marker 위에 겹쳐 사용할 focused state overlay only. 네 개의 분리된 cyan arc가 중심을 향해 정렬되고 바깥에는 5초 응시 진행을 암시하는 얇은 amber orbital arc. 중앙은 완전히 비어 있어 아래 marker가 보여야 한다. 원형 transparent overlay, no marker core.
```

### AS10. Preview Hologram Frame

파일명: `preview-hologram-frame.png`

규격: 가로형 `2048x1152`, 투명 배경.

```text
5초 영상 Preview 가장자리에 overlay할 holographic frame only. 중앙 영상 영역은 완전히 투명한 넓은 rectangle. 네 모서리에 짧은 cyan spatial corner line, 좌측 아래에 작은 amber temporal echo accent, 아주 미세한 chromatic afterimage. 완전한 두꺼운 테두리가 아니라 현실과 영상이 자연스럽게 섞이는 open corner frame.
```

### AS11. Reality Rewind Gesture Icon

파일명: `gesture-rewind-horizontal.png`

```text
콘텐츠 집중 재생에서 같은 장소의 과거 기록을 좌우로 넘긴다는 의미의 gesture icon. 중앙의 amber memory point에서 왼쪽과 오른쪽으로 서로 다른 간격의 echo trail이 이어지는 단순한 horizontal symbol. 일반적인 양방향 화살표나 video skip icon처럼 보이지 않고 시간을 긁는 잔상처럼 보임. transparent background.
```

### AS12. 아래 Swipe 복귀 Icon

파일명: `gesture-swipe-down-exit.png`

```text
Phone 콘텐츠에서 아래로 Swipe해 AR 현실로 돌아간다는 단일 gesture icon. 작은 손가락 그림 없이 위쪽의 solid amber frame이 아래로 이동하며 cyan camera space로 풀리는 세 단계의 fading trail. 화살표는 보조적으로만 매우 작게 사용. transparent background.
```

## P1 — Creator 구현 전에 필요한 Asset

### AS13. Creator Record Ring

파일명: `creator-record-ring.png`

```text
Creator camera의 녹화 버튼 위에 겹칠 circular record ring overlay. 중앙은 완전히 투명하고 바깥 ring은 amber에서 violet으로 변하는 temporal accumulation segments. 녹화 시간이 쌓이는 느낌, glow는 절제, perfect circle, transparent background.
```

### AS14. AR Placement Anchor

파일명: `creator-placement-anchor.png`

```text
Creator가 Camera 공간에 Moment를 배치할 때 사용할 holographic anchor. 바닥 또는 벽 표면에 닿는 cyan base ring, 위에 떠 있는 amber memory core, 세로 높이를 나타내는 짧은 violet axis. 3D 공간에 고정된 느낌의 isometric object, 좌표 숫자와 text 없음, transparent background.
```

### AS15. Upload Complete Echo

파일명: `creator-upload-complete.png`

```text
Moment upload와 공간 등록 완료를 나타내는 celebratory symbol. 흩어진 cyan spatial fragments와 amber time arcs가 중앙 memory point 주변에서 하나의 안정된 marker로 닫히는 순간. 폭죽이나 checkmark 중심 디자인이 아니라 시간이 장소에 남았다는 느낌, transparent background.
```

## P1 — 상태 안내 Illustration

### AS16. Moment Empty

파일명: `state-empty-moment.png`

```text
현재 장소에 아직 Moment가 없다는 empty-state illustration. 희미한 cyan spatial ring 하나와 그 중앙에 비어 있는 amber dotted echo, 발견되지 않은 시간의 자리 같은 시적인 느낌. 슬프거나 오류처럼 보이지 않음, transparent background.
```

### AS17. Permission·Privacy

파일명: `state-permission-privacy.png`

```text
Camera와 위치 권한, 개인정보 보호를 함께 상징하는 illustration. cyan camera aperture와 location ring을 얇은 protective arc가 감싸고, 데이터가 바깥으로 나가지 않는다는 의미의 inward-facing geometry. 자물쇠를 크게 그리지 않고 신뢰감 있는 근미래 보안 symbol, transparent background.
```

### AS18. Offline·Server Failure

파일명: `state-offline-server.png`

```text
Server 또는 Network 연결 실패 상태 illustration. 두 개의 cyan node 사이 연결이 끊어지고 중간의 amber time echo가 안전하게 보존된 모습. Wi-Fi 금지 아이콘이나 거대한 빨간 X 대신 재연결 가능한 차분한 상태, coral은 끊어진 한 지점에만 제한, transparent background.
```

## P2 — Glass UX용 Asset

### AS19. Glass Nod Yes

파일명: `glass-gesture-nod-yes.png`

```text
AR Glass에서 위아래로 끄덕이면 “예”라는 의미의 head gesture symbol. 사람 얼굴 전체 없이 단순한 visor side silhouette와 위아래 cyan motion echo, 결정 순간에 작은 amber confirmation point. 한눈에 상하 움직임으로 인식, transparent background.
```

### AS20. Glass Yaw No

파일명: `glass-gesture-yaw-no.png`

```text
AR Glass에서 좌우로 고개를 흔들면 “아니오”라는 의미의 head gesture symbol. 단순한 visor top silhouette와 좌우 cyan motion echo, 중앙에서 멀어지는 muted amber point. 한눈에 좌우 움직임으로 인식, transparent background.
```

### AS21. Glass 3DoF Screen Edge

파일명: `glass-content-screen-edge.png`

규격: 가로형 `2048x1152`, 투명 배경.

```text
AR Glass의 시야 정면 3DoF 영상 screen에 사용할 open edge overlay. 중앙과 대부분의 가장자리는 투명하고 네 모서리에만 짧은 cyan stabilization bracket. 양옆에는 Moment 이동을 암시하는 매우 희미한 amber temporal echo. 조준경이나 helmet HUD처럼 보이지 않고 편안한 cinema frame, transparent background.
```

## 이미지로 만들지 않을 것

다음 요소는 해상도·접근성·상태 변화 때문에 Android Native UI 또는 Vector로 구현한다.

- 모든 한글과 영문 Text
- Button, Card, Dialog, Bottom Sheet
- Toggle, Checkbox, Text Field
- 설정 Gear, Back, Close, Camera 같은 일반 Icon
- Loading Spinner와 진행률
- Glass Panel 배경과 Blur
- 날짜 Label과 Moment 개수 Badge
- 단순 Grid, Divider, Shadow

## 우선 전달 묶음

초기 화면과 현재 Viewer를 먼저 디자인하려면 다음 12개만 우선 제작한다.

1. `AS01` App Icon 원본
2. `AS02` Brand Symbol
3. `AS03` 시작 화면 Background
4. `AS04` Phone Viewer Illustration
5. `AS05` Glass Illustration
6. `AS06` Creator Illustration
7. `AS07` 기본 Moment Marker
8. `AS08` Moment Stack Marker
9. `AS09` Focus Ring
10. `AS10` Preview Frame
11. `AS11` Reality Rewind Icon
12. `AS12` 아래 Swipe 복귀 Icon

파일은 개별 PNG로 전달한다. 여러 Asset을 한 장의 Sheet로 만들면 자르는 과정에서 Glow와 투명 여백이 달라지므로 가능하면 한 파일에 하나씩 생성한다.
