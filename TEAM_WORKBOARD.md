# 팀 작업판

현재 누가 어떤 주제를 왜 진행하는지 빠르게 확인하는 목록이다. 자세한 설명은 연결된 Workstream 문서에 기록한다.

## 현재 작업

| Owner | Branch | Topic | Purpose | Status | Workstream | Updated |
|---|---|---|---|---|---|---|
| goguma-salad + Codex | `agent/goguma-salad/geo-time-ar-v2` | Geo-Time AR Platform Core | 위치·시간 후보 조회와 6DoF 기반 AR 표시 흐름을 검증한다. | `IN_PROGRESS` | [geo-time-ar-v2](docs/workstreams/geo-time-ar-v2.md) | 2026-08-24 |

## 사용 방법

- Owner는 `<github-id> + <Agent>` 형식을 권장한다. 예: `goguma-salad + Codex`
- Branch는 `agent/<github-id>/<task>` 형식을 사용한다.
- Status는 `IDEA`, `PLANNING`, `IN_PROGRESS`, `BLOCKED`, `REVIEW`, `INTEGRATION`, `DONE`, `PAUSED`, `DROPPED` 중에서 선택한다.
- Purpose는 한 문장으로 짧게 쓰고 상세 내용은 `docs/workstreams/<task>.md`에 기록한다.
- Updated는 `YYYY-MM-DD` 형식으로 작성한다.
- 작업을 시작하거나 상태가 바뀌거나 종료할 때 즉시 갱신한다.
