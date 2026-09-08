# 팀 작업판

현재 누가 어떤 주제를 왜 진행하는지 빠르게 확인하는 목록이다. 자세한 설명은 연결된 Workstream 문서에 기록한다.

## 현재 작업

| Owner | Git Branch | Project Path | Topic | Purpose | Status | Workstream | Updated |
|---|---|---|---|---|---|---|---|
| goguma-salad + Codex | `agent/goguma-salad/geo-time-ar-v2` | `experiments/goguma-salad/geo-time-ar-v2/` | Geo-Time AR Platform Core | 위치·시간 후보 조회와 6DoF 기반 AR 표시 흐름을 검증한다. | `IN_PROGRESS` | [geo-time-ar-v2](docs/workstreams/geo-time-ar-v2.md) | 2026-08-24 |
| shinym87 + Claude + Codex | `integration-interior-demo` | `experiments/shinym87/interior/` | 카메라 기반 공간 편집 / AR 가구 재배치 | 카메라·AI 제거·카탈로그 배치 흐름에 Depth 표면 검증을 통합한다. | `INTEGRATION` | [interior](docs/workstreams/interior.md) | 2026-09-08 |
| goguma-salad + Codex | `agent/goguma-salad/interior-depth-placement` | `experiments/goguma-salad/interior-depth-placement/` | IR Depth Point Cloud 배치 모듈 | ARCore Depth를 온디바이스 Point Cloud와 안정적인 객체 배치 Pose로 변환하는 독립 모듈과 테스트 앱을 구현한다. | `INTEGRATION` | [interior-depth-placement](docs/workstreams/interior-depth-placement.md) | 2026-09-08 |
| goguma-salad + Codex | `agent/goguma-salad/interior-depth-runtime` | `experiments/shinym87/interior/` | Interior Depth 직접 배치 | 평면 격자 의존을 제거하고 Depth world Pose로 가구 Anchor를 직접 배치한다. | `DONE` | [interior-depth-runtime](docs/workstreams/interior-depth-runtime.md) | 2026-09-08 |
| goguma-salad + Codex | `agent/goguma-salad/interior-assets-ui` | `experiments/shinym87/interior/` | Interior Magazine 객체 선택 UI | 공간 화보 속 객체 직접 선택과 AR 배치·사물 지우기 통합 작업 화면을 구현한다. | `REVIEW` | [interior-assets-ui](docs/workstreams/interior-assets-ui.md) | 2026-09-08 |

## 사용 방법

- Owner는 `<github-id> + <Agent>` 형식을 권장한다. 예: `goguma-salad + Codex`
- Git Branch는 `agent/<github-id>/<task>` 형식을 사용한다.
- 구현이 있는 작업의 Project Path는 `experiments/<github-id>/<task>/` 형식을 사용한다. 구현이 없으면 `해당 없음`으로 적는다.
- Status는 `IDEA`, `PLANNING`, `IN_PROGRESS`, `BLOCKED`, `REVIEW`, `INTEGRATION`, `DONE`, `PAUSED`, `DROPPED` 중에서 선택한다.
- Purpose는 한 문장으로 짧게 쓰고 상세 내용은 `docs/workstreams/<task>.md`에 기록한다.
- Updated는 `YYYY-MM-DD` 형식으로 작성한다.
- 작업을 시작하거나 상태가 바뀌거나 종료할 때 즉시 갱신한다.
