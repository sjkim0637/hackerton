# 팀 작업판

현재 누가 어떤 주제를 왜 진행하는지 빠르게 확인하는 목록이다. 자세한 설명은 연결된 Workstream 문서에 기록한다.

## 현재 작업

| Owner | Git Branch | Project Path | Topic | Purpose | Status | Workstream | Updated |
|---|---|---|---|---|---|---|---|
| goguma-salad + Codex | `agent/goguma-salad/geo-time-ar-v2` | `experiments/goguma-salad/geo-time-ar-v2/` | Geo-Time AR Platform Core | 위치·시간 후보 조회와 6DoF 기반 AR 표시 흐름을 검증한다. | `IN_PROGRESS` | [geo-time-ar-v2](docs/workstreams/geo-time-ar-v2.md) | 2026-08-24 |
| sjkim0637 + Codex | `agent/sjkim0637/interior` | `experiments/sjkim0637/interior/` | 카메라 기반 공간 편집 / AR 가구 재배치 | 실제 거실에서 TV 제거·벽 복원·가구 재배치가 가능한 MVP를 검증한다. | `IN_PROGRESS` | [interior-sjkim0637](docs/workstreams/interior-sjkim0637.md) | 2026-09-02 |

## 사용 방법

- Owner는 `<github-id> + <Agent>` 형식을 권장한다. 예: `goguma-salad + Codex`
- Git Branch는 `agent/<github-id>/<task>` 형식을 사용한다.
- 구현이 있는 작업의 Project Path는 `experiments/<github-id>/<task>/` 형식을 사용한다. 구현이 없으면 `해당 없음`으로 적는다.
- Status는 `IDEA`, `PLANNING`, `IN_PROGRESS`, `BLOCKED`, `REVIEW`, `INTEGRATION`, `DONE`, `PAUSED`, `DROPPED` 중에서 선택한다.
- Purpose는 한 문장으로 짧게 쓰고 상세 내용은 `docs/workstreams/<task>.md`에 기록한다.
- Updated는 `YYYY-MM-DD` 형식으로 작성한다.
- 작업을 시작하거나 상태가 바뀌거나 종료할 때 즉시 갱신한다.
