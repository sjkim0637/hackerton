# Handoff

이 문서는 `agent/goguma-salad/geo-time-construction` Branch의 고정 인계 문서다. 매번 새 문서를 만들지 않고 이 파일을 계속 갱신한다. 다음 세션은 전체 프로젝트를 다시 읽지 않고 이 문서만으로 현재 상태를 파악하고 이어서 작업할 수 있어야 한다.

## From

goguma-salad (Codex 세션, 토큰 소진으로 중단)

## To

goguma-salad (다음 세션 — Claude가 같은 날 이어받아 Commit·문서화까지 완료)

## Workstream

[geo-time-construction](../workstreams/geo-time-construction.md)

## Completed

- Codex가 통신단자함·홈넷 기기 `INSERT`를 `communication_device` Construction Object로 변환하는 기능을 구현했다. Backend(`cad.py`, `models.py`)에 `DEVICE_LAYERS`, `DEVICE_TYPES` 매핑과 `_build_communication_devices`를 추가했고, Frontend(`App.tsx`, `CablePlan2D.tsx`, `CableScene3D.tsx`, `types.ts`, `styles.css`)에 기기 표시 Toggle·2D/3D 렌더링·선택 속성 패널을 추가했다. 이 구현은 코드 작성만 끝난 채 Commit, Workstream 갱신, Handoff 없이 토큰이 소진됐다.
- Claude가 이어받아 다음을 완료했다.
  - Backend Pytest 10개, Ruff, Frontend ESLint, TypeScript/Vite Production Build를 재확인했다(모두 통과).
  - 로컬 `data/derived/ET-1101 단위세대 홈네트워크설비 평면도.dxf`로 실제 84㎡A Device 변환을 재현해 개수와 Subtype 분포를 확인했다.
  - `docs/architecture.md`에 통신 설비 Object(Device) 섹션을 추가했다.
  - `docs/workstreams/geo-time-construction.md`의 Scope, Decisions, Verification, Known Issues, Next, Relevant Commits를 갱신했다.
  - `TEAM_WORKBOARD.md`의 Purpose와 Owner 표기를 갱신했다.
  - 위 코드 변경을 Commit `bfde30c`로 반영했다.
- 사용자가 Web Viewer 3D 화면에서 배선(천장, `표시 높이` Slider 값)과 기기(고정 1.4m/2.3m)의 높이가 서로 다른 파라미터를 써서 시각적으로 어긋나는 것을 발견했다. Claude가 기기 높이를 배선과 동일한 `표시 높이` Slider 기준 상대 Offset(`DEVICE_WALL_OFFSET_M` = 0.9m)으로 계산하도록 `_build_communication_devices`를 수정하고, 회귀 테스트를 추가해 Backend Pytest 11개로 확인했다.
- 사용자가 3D 화면에서 배선·기기 뭉치가 건축 배경과 동떨어져 붕 떠 보인다고 다시 지적했다. 실측 결과 원인은 좌표 버그가 아니라 `XR_단위`가 평형 하나가 아닌 42,000×29,700mm Sheet 전체(주변 통로·인접 세대 포함)를 담고 있어서였다(84A 건축 배경 X 0~42m·Y 0~29.7m vs 배선 X 7~23m·Y 1~21m). Claude가 `/api/cad/architecture-background`에 `focus_min/max_x/y` Query를 추가해 Sheet Crop 이후 배선·기기 Bounding Box + 4m 여백으로 한 번 더 Clip하도록 수정했다(Frontend는 이미 받은 좌표의 최소·최대만 계산해 전달, DXF 직접 해석 없음). 실측: 84A 배경이 Focus 상자(X 3.4~26.7m, Y 0.9~25.4m)로 좁혀지고 선분 2,687→2,595개. Backend Pytest 13개, Ruff, Frontend ESLint/Build 통과.

## Important Files

- `experiments/goguma-salad/Geo-Time Construction/backend/app/cad.py` — `DEVICE_LAYERS`, `DEVICE_TYPES`, `_build_communication_devices`
- `experiments/goguma-salad/Geo-Time Construction/backend/app/models.py` — `PointGeometry`, `DeviceProperties`, `CommunicationDevice`
- `experiments/goguma-salad/Geo-Time Construction/web/src/App.tsx`, `web/src/components/CablePlan2D.tsx`, `web/src/components/CableScene3D.tsx` — 기기 표시·선택 UI
- `experiments/goguma-salad/Geo-Time Construction/docs/architecture.md` — Device Object 설계 근거

## Decisions

- 통신단자함·홈넷 기기는 `cable_path`(선형)와 별도로 `category=communication`, `type=communication_device`, `system=home_network`의 point Object로 분리한다.
- Device 추출 대상 Layer는 `통신단자함`, `SYM`, `E-SYM`, `천정`으로 제한한다.
- Block 이름으로 Subtype을 매핑하고, 매핑되지 않은 Block은 `home_network_device` 기본값으로 분류해 누락을 방지한다.
- 기기 표시 높이는 고정값이 아니라 배선 `표시 높이` Slider(`elevation_m`)를 기준으로 계산한다. Layer가 `천정`이면 배선과 같은 높이, 그 외에는 배선 높이 - 0.9m를 사용해 Slider를 조정해도 상대 높이가 유지된다(실제 시공 높이 아님).
- 건축 배경은 평형 Sheet 전체 대신, 배선·기기 Bounding Box + 여백 4m(`ARCHITECTURE_FOCUS_MARGIN_M`)로 다시 Clip해서 보여준다. 세대 전체 맥락(주변 통로 등)은 일부 잘리지만 배선과 스케일이 맞는 화면을 우선한다.

## Constraints

- 프로젝트 원본 지시서(`experiments/goguma-salad/Geo-Time Construction/docs/raw/Geo-Time Construction.md`)의 Phase 1 범위를 유지한다. AR·AI·Time Revision은 아직 구현 대상이 아니다.
- `experiments/goguma-salad/geo-time-ar-v2/`의 미추적 파일(`.venv`, `.gradle`, `tmp` 등)은 `.gitignore` 규칙에 이미 걸러지고 있어 별도 조치가 필요 없다. `git status`에 폴더째로 뜨는 이유는 그 안에 `.vercel/README.txt`, `.vercel/project.json` 두 파일만 규칙에 안 걸리기 때문이며, 다른 Workstream 소유 파일이라 이 작업에서는 손대지 않았다.

## Known Issues

- Device Block 매핑(`DEVICE_TYPES`)은 84㎡A 기준으로만 확인했다. 84㎡B 등 다른 평형에서 새로운 Block 이름이 나오면 `home_network_device` 기본값으로만 표시되고 별도 Subtype으로 분류되지 않는다.
- 연결 가능한 Browser 인스턴스가 없어 Device 표시 Toggle, 높이 연동, 건축 배경 Focus Crop의 실제 육안 검증은 아직 못했다(Backend/Frontend 자동 검증만 완료).
- 3D Viewer Production Bundle이 약 1.09MB로 기존에 알려진 Three.js Code Splitting 필요성이 그대로 남아 있다.
- Focus 여백 4m는 실제 세대 규모를 분석해 정한 값이 아닌 초기 가정값이다.

## Next

1. 로컬 Browser에서 통신단자함·홈넷 기기 표시·선택, 높이 연동, 좁혀진 건축 배경을 육안 검증한다.
2. 84㎡B 등 다른 평형에도 동일 Crop/Translate 규칙, Device 매핑, Focus Crop을 회귀 검증하고, 새 Block이 나오면 `DEVICE_TYPES`에 추가한다.
3. `DEVICE_WALL_OFFSET_M`(0.9m)과 `ARCHITECTURE_FOCUS_MARGIN_M`(4m) 고정값이 실제 도면 규모와 맞는지 검토한다.
4. 건축 배경 응답의 압축 또는 Streaming 필요성을 측정한다.
5. 필요한 경우 GLB Export 경계를 검토한다.

## Relevant Commits

- `3bd0ef8` docs(cad): Geo-Time Construction Phase 1 검토 등록
- `3b2a4ce` feat(cad): 통신 배선 2D·3D 변환 PoC 구현
- `eadd7fe` feat(cad): 건축 배경과 배선 객체 선택 기능 추가
- `bfde30c` feat(cad): 통신단자함·홈넷 설비 INSERT를 Construction Object로 변환
- `2018cbc` fix(cad): 홈넷 기기 표시 높이를 배선 Slider에 연동
- `8b50cec` fix(cad): 건축 배경을 배선 범위 기준으로 다시 Crop
