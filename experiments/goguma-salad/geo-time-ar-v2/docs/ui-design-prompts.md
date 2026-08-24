# Geo-Time AR 사이버펑크 UI 디자인 지시서

이 문서는 Dreamina 같은 이미지 생성 도구로 Geo-Time AR의 **전체 화면 참고 시안**을 만들 때 사용하는 Prompt 모음이다. 실제 UI에 넣을 투명 이미지 조각은 [`UI용 이미지 Asset 생성 지시서`](ui-image-asset-prompts.md)를 사용한다.

## 1. 디자인 방향

한 줄 Concept:

> 미래 기기로 현실을 스캔하는 것이 아니라, 현재 공간에 남은 과거의 신호를 복원한다.

원하는 분위기는 화려한 게임식 Cyberpunk가 아니라 일상에서 사용할 수 있는 정제된 근미래 Cyberpunk다.

- 어두운 Navy·Black 기반
- 전기 Cyan은 Tracking과 System 상태에 사용
- 따뜻한 Amber는 과거 Moment와 시간 흔적에 사용
- Magenta는 선택·Creator Mode에서만 제한적으로 사용
- 얇은 선, 미세한 Grid, Scan 흔적과 잔상
- 현실 Camera 화면을 많이 가리지 않는 반투명 Glass Panel
- Slider로 시간을 고르는 UI는 사용하지 않음
- 화면 전체가 Timeline처럼 보이지 않게 함
- 큰 버튼과 명확한 상태 표시로 비개발자도 바로 이해할 수 있어야 함

### 권장 Color

| 용도 | Color |
|---|---|
| Background | `#050811`, `#090E1A` |
| Surface | `#101827` 88%, Camera 위에서는 68% |
| Primary Cyan | `#38D9FF` |
| Moment Amber | `#FFB547` |
| Creator Magenta | `#D76BFF` |
| Success Mint | `#50E3B3` |
| Error Coral | `#FF667A` |
| Main Text | `#F4F7FB` |
| Secondary Text | `#9AA7B8` |

### 글꼴 분위기

- 한글: Pretendard 또는 SUIT처럼 읽기 쉬운 Sans Serif
- 영문 제목: Space Grotesk 계열
- 좌표·날짜·진단값: JetBrains Mono 계열
- 한글 본문을 장식용 Pixel Font로 만들지 않음

## 2. 모든 화면 앞에 붙이는 공통 Prompt

아래 내용을 각 화면 Prompt 앞에 붙인다.

```text
세로형 Android 모바일 앱 UI/UX 디자인, 해상도 1440x3088, 정면에서 본 flat screen design, 스마트폰 기기 프레임과 손 없이 화면만 표시.

제품명은 Geo-Time AR. 현실 공간에 남은 과거의 영상 기록을 발견하고 재생하는 위치 기반 AR 서비스. 세련되고 절제된 near-future cyberpunk visual language. 깊은 navy black background, electric cyan tracking light, warm amber temporal memory light, creator mode에만 제한적인 violet magenta. 얇은 luminous line, subtle scanline, faint spatial grid, soft bloom, restrained chromatic ghosting, semi-transparent glass panels, high contrast Korean typography, generous spacing, large touch targets, production-ready Android interface.

게임 HUD처럼 복잡하지 않고 일반 사용자가 즉시 이해할 수 있어야 한다. 카메라 화면에서는 UI가 현실을 가리지 않아야 한다. 시간은 slider가 아니라 공간에 남은 echo, ring, afterimage로 표현한다. 날짜는 재생하거나 기록을 넘길 때만 짧게 나타난다. Material Design의 사용성을 유지하되 독창적인 Cyberpunk 브랜드로 표현한다.
```

## 3. 모든 화면 뒤에 붙이는 Negative Prompt

```text
과도한 neon, rainbow gradient, Blade Runner 복제, Matrix 초록 코드, 복잡한 cockpit HUD, RPG status UI, 총기 조준경, crypto dashboard, stock chart, timeline slider, video editor UI, calendar picker, 작은 글씨, 낮은 대비, 빽빽한 정보, 과도한 glitch, 더러운 dystopian texture, 해골, 로봇 캐릭터, anime character, floating smartphone mockup, perspective device, 손, 설명용 화살표, watermark, 임의의 영문 문장, 깨진 한글, 중복 버튼 금지.
```

## 4. 화면별 Prompt

### A01. App Icon과 Brand Mark

```text
Geo-Time AR의 app icon과 brand mark design sheet. 공간 좌표를 뜻하는 얇은 location ring과 시간의 잔상을 뜻하는 어긋난 두 개의 echo arc를 하나의 단순한 symbol로 결합. 중앙에는 작은 amber memory point, 외곽에는 electric cyan tracking ring. 어두운 navy background. 작은 Android icon 크기에서도 알아볼 수 있는 매우 단순한 geometric symbol. 문자 G, T를 억지로 넣지 않는다. icon, monochrome symbol, horizontal lockup 세 가지를 한 화면에 정돈해 제시.
```

### A02. Splash

```text
Geo-Time AR splash screen. 거의 검은 navy 공간 중앙에 작은 brand symbol. cyan tracking ring이 amber memory point를 천천히 scan하는 듯한 정적인 한 장면. 아래에는 작은 제품명 “Geo-Time AR”, 보조 문구 “현실에 남은 시간을 발견하세요”. 로딩 spinner 대신 한 줄의 얇은 scanning arc. 매우 절제되고 영화적인 첫인상.
```

### A03. 초기 Mode 선택 화면 — 최우선

```text
Geo-Time AR 앱의 시작 화면. 상단에는 작은 logo와 “GEO-TIME AR”, 그 아래 큰 문구 “어떤 방식으로 시간을 볼까요?”. 배경은 어두운 spatial grid와 아주 흐릿한 도시 공간의 depth map.

중앙에 세 개의 명확한 mode card:
1. 가장 큰 primary card “Phone Viewer” — 휴대폰 카메라로 주변의 시간 기록 탐색, cyan eye/camera symbol, 버튼 문구 “휴대폰으로 시작”
2. secondary card “Glass” — 연결된 AR Glass에서 실행, thin visor symbol, 연결되지 않은 상태에는 “기기 연결 필요”
3. accent card “Creator” — 새 Moment 촬영과 공간 배치, amber-magenta record symbol, 버튼 문구 “기록 만들기”

하단에는 작은 server connection indicator “Demo Server · 연결됨”과 설정 gear. Phone Viewer가 처음 사용자에게 가장 자연스러운 선택으로 보여야 한다. 카드가 거대한 네온 박스처럼 보이지 않고 glass surface와 edge light로 구분된다.
```

### A04. 최초 권한 안내

```text
Geo-Time AR permission onboarding screen. 제목 “현실의 시간 기록을 찾으려면”, 세 개의 큰 permission row: Camera “공간을 인식합니다”, Location “주변 기록을 찾습니다”, Microphone “Creator 촬영 때만 사용합니다”. Camera와 Location은 필수 표시, Microphone은 선택 표시. 각 항목은 단순한 line icon과 한 문장 설명. 위치를 앱에 저장하지 않는다는 privacy note “위치 좌표는 앱이나 서버에 저장하지 않습니다”. 하단 primary button “필수 권한 허용”, secondary text button “나중에”. 신뢰감 있고 차분한 cyberpunk security console 분위기.
```

### B01. Phone AR 탐색 — 기본 상태

```text
실제 공원 또는 도시 거리 camera view 위에 표시되는 Geo-Time AR Phone Viewer. 화면 대부분은 현실 camera가 보인다. 상단에는 매우 얇은 transparent status capsule: 현재 장소 이름, GPS 정확도, 6DoF tracking status를 작은 cyan indicator로 표시.

공간 안에는 POI별 Moment Stack marker가 2~3개. Marker는 작은 amber core와 cyan spatial ring, “시간 기록 4개”처럼 개수만 표시하고 날짜는 표시하지 않는다. 가까운 Marker는 선명하고 먼 Marker는 작고 흐리다. 선택되지 않은 Marker는 현실을 거의 가리지 않는다.

하단에는 짧은 coach mark “시간 기록을 터치해 미리보세요”. navigation bar나 timeline slider 없음. 설정과 Creator quick action은 가장자리에 작은 icon으로만 배치.
```

### B02. Phone AR 탐색 — Empty·거리 밖

```text
Phone AR camera view의 empty state. Marker가 없는 현실 화면을 유지하면서 중앙 아래에 얇은 glass message panel. 상태는 “이 장소에는 아직 시간 기록이 없습니다”. 보조 문구 “첫 번째 Moment를 남겨보세요”. 작은 Creator action “기록 만들기”. 오류처럼 보이지 않고 발견되지 않은 공간이라는 신비로운 느낌. 주변을 scan하는 매우 희미한 cyan circular pulse.
```

### B03. POI Moment Stack Marker 구성표

```text
Geo-Time AR spatial marker component design sheet on dark neutral background. 같은 디자인 언어로 다섯 상태를 나란히 제시: 기본 Moment 1개, Moment 여러 개 stack, 사용자가 응시하거나 터치한 focused state, 재생 중 state, commercial campaign state. Organic Moment는 amber memory core, tracking ring은 cyan. 여러 기록은 겹친 echo ring과 작은 숫자 badge로 표현. Campaign은 형태를 유지하되 “PROMOTED”를 작게 표시하고 violet edge를 사용. 각 marker는 작은 크기에서도 공간 위치와 선택 상태가 분명해야 한다.
```

### C01. 5초 무음 Preview

```text
Phone AR camera 위에서 Marker를 선택한 직후의 5-second muted preview overlay. 선택한 Marker 위치 근처에 작은 영상 window가 현실 공간과 연결된 것처럼 떠 있다. 영상 가장자리에는 amber temporal glow와 아주 미세한 과거 잔상. 상단 작은 label “3년 전 · 이 장소의 기록”, 우측에 muted icon. 아래에는 얇은 5초 진행 ring이 있지만 일반 video seek bar는 없음. 배경 camera와 다른 Marker는 그대로 보인다. 사용자가 아직 전체 콘텐츠로 들어가지 않은 가벼운 preview 상태.
```

### C02. 재생 확인

```text
5초 preview가 끝난 Phone 화면. Camera 배경과 마지막 preview frame은 유지하되 살짝 어둡게 처리. 중앙 아래 compact glass confirmation sheet: “이 기록을 재생할까요?”, 작은 날짜와 영상 길이. primary button “전체 영상 보기”, secondary button “AR로 돌아가기”. 버튼은 손가락으로 누르기 충분히 크다. Cyberpunk 효과는 panel edge와 amber focus에만 사용.
```

### C03. Phone 콘텐츠 집중 재생

```text
Phone mode full content focus screen. 완전한 검은 배경이 아니라 어둡게 dim 처리된 AR camera가 주변 맥락으로 남아 있다. 중앙에는 원본 비율을 보존한 세로 또는 가로 video가 가능한 한 크게 표시된다. 영상 시작 시 위쪽에 “2023.10.21 · 서울숲의 오후”가 잠깐 나타나는 amber date label. 좌우 가장자리에는 화살표 버튼 대신 아주 미세한 echo hint로 이전/다음 Moment 방향만 암시. 하단에는 잠시 나타나는 안내 “좌우로 넘기기 · 아래로 내려 AR 복귀”. 일반 video editor timeline과 seek slider는 보이지 않는다.
```

### C04. 콘텐츠 Loading·실패

```text
두 가지 state를 한 장의 component sheet로 표현. 첫 번째는 video first frame을 기다리는 loading state: 이전 frame을 흐리게 유지하고 중앙에 얇은 cyan-amber orbital loader, 문구 “시간 기록을 불러오는 중”. 두 번째는 playback failed state: 깨진 영상 icon 대신 끊어진 temporal ring, 문구 “기록을 불러오지 못했습니다”, primary “다시 시도”, secondary “AR로 돌아가기”. 불안감을 주는 빨간 전체 화면 금지.
```

### D01. Creator 시작

```text
Geo-Time AR Creator home screen. 제목 “새로운 시간을 남기세요”. 중앙에는 두 개의 큰 action: primary “지금 촬영” with amber-magenta record lens, secondary “갤러리에서 선택”. 아래에는 작은 draft section “임시 저장 1개”. Viewer의 cyan 중심 디자인에서 Creator의 amber와 violet accent가 조금 더 강해지지만 같은 제품으로 보인다. 하단에 3단계 progress map: 영상 선택 → 공간 배치 → 업로드.
```

### D02. Creator 촬영 화면

```text
Camera recording UI for Geo-Time AR Creator. 현실 camera view 중심, 하단 중앙에 큰 record control, 녹화 시간, 최대 길이 표시. 상단에는 닫기, flash, camera 전환. 기존 카메라 앱처럼 익숙하지만 record button 주변에 amber temporal ring이 쌓이는 독특한 표현. 위치는 저장 전에 사용자 확인이 필요하다는 작은 privacy badge. 과도한 방송 장비 HUD 금지.
```

### D03. 영상 확인과 정보 입력

```text
Creator video review and metadata screen. 상단 큰 video preview, 간단한 trim 시작·끝 handle만 허용. 아래 form fields: 제목, 짧은 설명, 기록 시각, 공개 범위. “현재 시각 사용” quick option. 다음 button “공간에 배치하기”. Form은 어두운 glass surface, 선택 field는 amber edge. 키보드는 그리지 않는다.
```

### D04. POI 선택

```text
Creator가 현재 장소의 POI를 선택하는 screen. 상단에 실제 camera 또는 compact map-like spatial list, 아래에 가까운 장소 card 목록: 장소명, 거리, 기존 Moment 개수. 가장 가까운 POI를 recommended badge로 강조. action “새 장소 제안”은 secondary. 정확하지 않은 GPS 상태에는 amber accuracy warning과 “기준점 스캔 필요”. 일반 지도 앱 전체 화면처럼 만들지 말고 공간 기록 선택 화면으로 보이게 한다.
```

### D05. AR 공간 배치와 Calibration

```text
Creator AR placement screen. Camera view 중앙에 사용자가 배치하려는 Moment marker hologram. 바닥 또는 벽 plane을 cyan grid로 아주 얇게 표시. 안내 “기록을 남길 위치에 마커를 놓으세요”. Phone에서는 drag로 위치, pinch로 크기, vertical handle로 높이 조정. 하단 primary “이 위치에 고정”. 상단에는 GPS accuracy와 calibration status. QR 또는 visual anchor가 필요할 때 “기준점 스캔” 안내가 자연스럽게 나타난다. 좌표 숫자를 일반 사용자에게 노출하지 않는다.
```

### D06. Upload 진행·완료

```text
Creator upload state component sheet. 진행 상태는 영상 thumbnail 주변의 incomplete temporal ring과 percentage, 단계 문구 “영상 업로드 중”, “공간 정보 확인 중”, “Moment 발행 중”. 완료 상태는 ring이 하나의 안정된 amber echo marker로 닫히고 문구 “이 장소에 새로운 시간이 남았습니다”. action “AR에서 확인”, secondary “완료”. 실패 상태에는 “임시 저장됨 · 연결되면 다시 시도합니다”.
```

### E01. 설정 Home

```text
Geo-Time AR settings screen. 일반 사용자가 이해할 수 있는 grouped list: 실행 모드, 재생 및 미리보기, 조작 안내, 권한과 개인정보, 저장공간, Server 연결, 개발자 진단, 앱 정보. 각 그룹은 dark glass card가 아니라 얇은 divider와 충분한 여백으로 구분. 현재 상태를 오른쪽에 짧게 표시: “Phone”, “켜짐”, “연결됨”. 상단 title “설정”, 뒤로가기. GNSS 진단은 “개발자 진단” 안에 있어 메인 AR 화면에서는 최종적으로 제거될 예정.
```

### E02. Server 연결 설정

```text
Geo-Time AR server settings screen. 일반 사용자는 “Demo”, “개발”, “운영” profile card 중 선택. 고급 설정을 펼치면 API Server 주소와 Media Server 주소 입력란. 상태 panel에는 cyan pulse와 “연결됨 · API 0.1.0”, 실패 시 coral edge와 “서버에 연결할 수 없습니다”. action “연결 테스트”, “저장”. USB adb reverse 같은 개발 용어는 advanced developer note에서만 작게 표시.
```

### E03. Permission·Privacy 설정

```text
Permission and privacy settings screen. Camera, precise location, microphone 세 항목의 현재 권한 상태와 system settings shortcut. 상단 privacy summary “위치 좌표와 GNSS 진단값은 저장하거나 서버로 전송하지 않습니다”. Creator upload 시에만 사용자가 선택한 장소와 배치 정보가 저장된다는 구분을 명확히 표시. 보안 dashboard처럼 과장하지 않고 신뢰감 있는 cyan lock motif.
```

### E04. GNSS 개발자 진단

```text
Developer-only GNSS Carrier Phase diagnostic screen redesigned in Geo-Time AR cyberpunk style. 상단에는 큰 판정 badge “내장 ADR 미제공”, 그 아래 signal summary cards: 전체 신호, L1/E1, L5/E5a, 유효 ADR. 하단에는 Epoch, Reset, Cycle Slip의 monospaced live values. 값이 저장되지 않는다는 privacy note. 기술자가 야외에서 한눈에 판단할 수 있게 숫자는 크고 명확하게 표시하되 일반 사용자 설정과 분리된 화면.
```

### F01. 공통 상태 화면 Sheet

```text
Geo-Time AR common state design sheet containing six compact full-screen or panel states with consistent cyberpunk visual language: 위치 찾는 중, 주변 Moment 없음, Camera 권한 거절, 위치 권한 거절, Offline, Server 연결 실패. 각 상태는 하나의 단순한 temporal/spatial line icon, 쉬운 한국어 제목, 한 문장 설명, primary action 하나만 사용. Camera 배경을 유지할 수 있는 상태와 완전한 앱 surface가 필요한 상태를 구분해서 제시.
```

### G01. Glass AR 탐색 HUD

```text
AR Glass binocular field-of-view UI, transparent background simulation, no smartphone frame. 현실 공원 또는 거리 위에 매우 최소화된 Geo-Time AR HUD. 중앙에 고정 crosshair를 두지 않고 사용자가 바라보는 Moment marker만 얇은 cyan focus ring으로 반응. Marker는 amber memory point와 “기록 4개”만 표시. 5초 응시가 진행되면 marker 둘레의 작은 arc가 차오른다. 시야 가장자리에는 tracking과 connection status만 작게 표시. 고개를 움직여 현실을 탐색하는 행동을 방해하지 않는다.
```

### G02. Glass Preview와 재생 확인

```text
AR Glass에서 6DoF 공간에 고정된 small video preview. 현실 위 POI 위치에 5초 무음 영상이 떠 있고 주변은 그대로 보인다. Preview 종료 후 영상 근처에 “이 기록을 재생할까요?”와 gesture hint: 위아래 끄덕임 “예”, 좌우 흔들기 “아니오”. 버튼이나 손가락 touch UI 없음. 설명은 짧고 큰 글자로, cyan head motion line과 amber selection feedback.
```

### G03. Glass 3DoF 콘텐츠 Screen

```text
AR Glass에서 재생 승인 후 사용자의 시야 정면에 안정적으로 따라오는 3DoF cinema screen. 현실 배경은 어둡게 하지 않고 영상 주변만 subtle vignette. 화면은 시야를 전부 덮지 않으며 고개가 움직여도 정면의 편안한 거리와 크기를 유지하는 느낌. 날짜는 시작 순간에만 작은 amber label로 나타난다. 빠른 좌우 고개 왕복으로 이전/다음 기록 이동을 암시하는 최소한의 edge echo. 물리 button, touch slider, 검은 전체 화면 없음.
```

## 5. 전체 화면 시안이 별도로 필요할 때의 1차 묶음

전체를 한 번에 만들면 화면마다 Style이 달라질 가능성이 크다. 다음 6개를 먼저 같은 Seed·Style Reference로 생성한다.

1. `A01` App Icon과 Brand Mark
2. `A03` 초기 Mode 선택
3. `B01` Phone AR 탐색
4. `C01` 5초 Preview
5. `C03` Phone 콘텐츠 집중 재생
6. `E01` 설정 Home

이 6개에서 Color, Font, Panel 투명도, Marker 모양을 확정한 뒤 Creator와 Glass 화면을 같은 Reference Image로 생성한다.

## 6. 전달 규격

- 화면별 PNG 한 장
- 세로 화면: `1440x3088` 또는 같은 비율의 고해상도
- Glass 화면: `1920x1080`, 투명 HUD를 확인할 수 있는 현실 배경 포함
- 스마트폰 Frame, 손, 책상 배경이 없는 정면 Screen
- 같은 Seed와 Style Reference 사용
- 파일명 예: `A03-start-mode-v1.png`
- 한글이 깨져도 재생성에 시간을 쓰지 않아도 된다. 실제 글자는 Android에서 다시 구현한다.
- Button, Card, Text를 잘라서 개별 PNG로 만들지 않는다. 화면 전체 시안을 전달하면 Native UI로 재구현한다.

## 7. 최종 확인 기준

- 화면만 보고 Phone, Glass, Creator의 차이를 알 수 있는가?
- AR Camera가 UI에 가려지지 않는가?
- Moment Marker가 날짜 선택기가 아니라 장소의 시간 흔적으로 보이는가?
- Slider 없이도 Preview와 콘텐츠 재생 흐름이 이해되는가?
- Cyberpunk 분위기가 있어도 일반인이 버튼과 상태를 바로 이해할 수 있는가?
- Organic Moment와 광고 Campaign을 오해하지 않게 구분했는가?
- 작은 글자와 낮은 대비를 사용하지 않았는가?
