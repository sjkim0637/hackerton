# 팀 작업판

현재 누가 어떤 주제를 왜 진행하는지 빠르게 확인하는 목록이다. 자세한 설명은 연결된 Workstream 문서에 기록한다.

## 현재 작업

| Owner | Git Branch | Project Path | Topic | Purpose | Status | Workstream | Updated |
|---|---|---|---|---|---|---|---|
| goguma-salad + Codex | `agent/goguma-salad/geo-time-ar-v2` | `experiments/goguma-salad/geo-time-ar-v2/` | Geo-Time AR Platform Core | 위치·시간 후보 조회와 6DoF 기반 AR 표시 흐름을 검증한다. | `IN_PROGRESS` | [geo-time-ar-v2](docs/workstreams/geo-time-ar-v2.md) | 2026-08-24 |
| shinym87 + Claude | `agent/shinym87/interior` | `experiments/shinym87/interior/` | 카메라 기반 공간 편집 / AR 가구 재배치 | 설계서의 공간·AR 흐름(카메라·평면 인식·탭 배치·드래그 이동·핀치 크기 조절·대표 이미지)을 구현한다. | `IN_PROGRESS` | [interior](docs/workstreams/interior.md) | 2026-09-02 |
| goguma-salad + Codex | `agent/goguma-salad/interior-assets` | `experiments/shinym87/interior/assets/` | Interior AR 화면·브랜드 Asset | 메인 분기 화면, AR 인테리어, 설정·설명 화면에 사용할 시각 자산과 앱 아이콘을 제작한다. | `IN_PROGRESS` | [interior-assets](docs/workstreams/interior-assets.md) | 2026-09-07 |
| goguma-salad + Codex | `agent/goguma-salad/interior-ui-navigation` | `experiments/shinym87/interior/app/` | Interior AR 화면 분리·Asset 적용 | 메인 화면에서 AR·설명·설정으로 이동하고 서버 주소를 설정 화면에서 관리한다. | `REVIEW` | [interior-ui-navigation](docs/workstreams/interior-ui-navigation.md) | 2026-09-07 |
| goguma-salad + Claude | `agent/goguma-salad/interior-mobilesam` | `experiments/shinym87/interior/` | Interior 사물 선택 — MobileSAM 점 프롬프트 | 드래그 bbox 대신 탭 한 번으로 사물을 지정하고 MobileSAM으로 정밀 마스크를 얻는다(서버 구현 완료, 앱 배선은 다음 단계). | `IN_PROGRESS` | [interior-mobilesam](docs/workstreams/interior-mobilesam.md) | 2026-09-07 |

## 사용 방법

- Owner는 `<github-id> + <Agent>` 형식을 권장한다. 예: `goguma-salad + Codex`
- Git Branch는 `agent/<github-id>/<task>` 형식을 사용한다.
- 구현이 있는 작업의 Project Path는 `experiments/<github-id>/<task>/` 형식을 사용한다. 구현이 없으면 `해당 없음`으로 적는다.
- Status는 `IDEA`, `PLANNING`, `IN_PROGRESS`, `BLOCKED`, `REVIEW`, `INTEGRATION`, `DONE`, `PAUSED`, `DROPPED` 중에서 선택한다.
- Purpose는 한 문장으로 짧게 쓰고 상세 내용은 `docs/workstreams/<task>.md`에 기록한다.
- Updated는 `YYYY-MM-DD` 형식으로 작성한다.
- 작업을 시작하거나 상태가 바뀌거나 종료할 때 즉시 갱신한다.
