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

## Important Files

- `experiments/goguma-salad/Geo-Time Construction/backend/app/cad.py` — `DEVICE_LAYERS`, `DEVICE_TYPES`, `_build_communication_devices`
- `experiments/goguma-salad/Geo-Time Construction/backend/app/models.py` — `PointGeometry`, `DeviceProperties`, `CommunicationDevice`
- `experiments/goguma-salad/Geo-Time Construction/web/src/App.tsx`, `web/src/components/CablePlan2D.tsx`, `web/src/components/CableScene3D.tsx` — 기기 표시·선택 UI
- `experiments/goguma-salad/Geo-Time Construction/docs/architecture.md` — Device Object 설계 근거

## Decisions

- 통신단자함·홈넷 기기는 `cable_path`(선형)와 별도로 `category=communication`, `type=communication_device`, `system=home_network`의 point Object로 분리한다.
- Device 추출 대상 Layer는 `통신단자함`, `SYM`, `E-SYM`, `천정`으로 제한한다.
- Block 이름으로 Subtype을 매핑하고, 매핑되지 않은 Block은 `home_network_device` 기본값으로 분류해 누락을 방지한다.
- 표시 높이는 Layer가 `천정`이면 2.3m, 그 외에는 1.4m 초기값을 사용한다(실제 시공 높이 아님).

## Constraints

- 프로젝트 원본 지시서(`experiments/goguma-salad/Geo-Time Construction/docs/raw/Geo-Time Construction.md`)의 Phase 1 범위를 유지한다. AR·AI·Time Revision은 아직 구현 대상이 아니다.
- `experiments/goguma-salad/geo-time-ar-v2/`의 미추적 파일(`.venv`, `.gradle`, `tmp` 등)은 `.gitignore` 규칙에 이미 걸러지고 있어 별도 조치가 필요 없다. `git status`에 폴더째로 뜨는 이유는 그 안에 `.vercel/README.txt`, `.vercel/project.json` 두 파일만 규칙에 안 걸리기 때문이며, 다른 Workstream 소유 파일이라 이 작업에서는 손대지 않았다.

## Known Issues

- Device Block 매핑(`DEVICE_TYPES`)은 84㎡A 기준으로만 확인했다. 84㎡B 등 다른 평형에서 새로운 Block 이름이 나오면 `home_network_device` 기본값으로만 표시되고 별도 Subtype으로 분류되지 않는다.
- 연결 가능한 Browser 인스턴스가 없어 Device 표시 Toggle과 선택 UI의 실제 육안 검증은 아직 못했다(Backend/Frontend 자동 검증만 완료).
- 3D Viewer Production Bundle이 약 1.09MB로 기존에 알려진 Three.js Code Splitting 필요성이 그대로 남아 있다.

## Next

1. 로컬 Browser에서 통신단자함·홈넷 기기 표시와 선택 동작을 육안 검증한다.
2. 84㎡B 등 다른 평형에도 동일 Crop/Translate 규칙과 Device 매핑을 회귀 검증하고, 새 Block이 나오면 `DEVICE_TYPES`에 추가한다.
3. 건축 배경 응답의 압축 또는 Streaming 필요성을 측정한다.
4. 필요한 경우 GLB Export 경계를 검토한다.

## Relevant Commits

- `3bd0ef8` docs(cad): Geo-Time Construction Phase 1 검토 등록
- `3b2a4ce` feat(cad): 통신 배선 2D·3D 변환 PoC 구현
- `eadd7fe` feat(cad): 건축 배경과 배선 객체 선택 기능 추가
- `bfde30c` feat(cad): 통신단자함·홈넷 설비 INSERT를 Construction Object로 변환
