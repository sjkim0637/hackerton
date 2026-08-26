# 팀 작업판

현재 누가 어떤 주제를 왜 진행하는지 빠르게 확인하는 목록이다. 자세한 설명은 연결된 Workstream 문서에 기록한다.

## 현재 작업

| Owner | Git Branch | Project Path | Topic | Purpose | Status | Workstream | Updated |
|---|---|---|---|---|---|---|---|
| goguma-salad + Codex | `agent/goguma-salad/geo-time-ar-v2` | `experiments/goguma-salad/geo-time-ar-v2/` | Geo-Time AR Platform Core | 위치·시간 후보 조회와 6DoF 기반 AR 표시 흐름을 검증한다. | `IN_PROGRESS` | [geo-time-ar-v2](docs/workstreams/geo-time-ar-v2.md) | 2026-08-24 |
| shinym87 + Claude | `agent/shinym87/ar-memory` | `experiments/shinym87/ar-memory/` | 단지 추억 공유 소셜 AR | 지도(단지 배치도) 위 핀·타임라인 기반으로 장소별 추억 기록/공유 컨셉을 웹 프로토타입으로 검증한다. | `IN_PROGRESS` | [ar-memory](docs/workstreams/ar-memory.md) | 2026-08-26 |

## 사용 방법

- Owner는 `<github-id> + <Agent>` 형식을 권장한다. 예: `goguma-salad + Codex`
- Git Branch는 `agent/<github-id>/<task>` 형식을 사용한다.
- 구현이 있는 작업의 Project Path는 `experiments/<github-id>/<task>/` 형식을 사용한다. 구현이 없으면 `해당 없음`으로 적는다.
- Status는 `IDEA`, `PLANNING`, `IN_PROGRESS`, `BLOCKED`, `REVIEW`, `INTEGRATION`, `DONE`, `PAUSED`, `DROPPED` 중에서 선택한다.
- Purpose는 한 문장으로 짧게 쓰고 상세 내용은 `docs/workstreams/<task>.md`에 기록한다.
- Updated는 `YYYY-MM-DD` 형식으로 작성한다.
- 작업을 시작하거나 상태가 바뀌거나 종료할 때 즉시 갱신한다.
