# 프로젝트 협업 하네스

이 저장소는 여러 사람이 각자의 AI Agent를 활용해 독립적으로 기획·실험하면서도 진행 상황과 결과를 안전하게 공유하기 위한 Git + Markdown 기반 협업 공간이다.

현재는 **Phase 0 - Project Harness** 단계이다. 실제 Application, 기술 Stack, Architecture는 아직 정하지 않았으며 소스 코드도 없다.

## 처음 참여할 때

다음 문서를 순서대로 읽는다.

1. [`AGENTS.md`](AGENTS.md) - 모두가 지켜야 할 공통 규칙
2. [`PROJECT_STATUS.md`](PROJECT_STATUS.md) - 프로젝트 전체의 현재 단계
3. [`TEAM_WORKBOARD.md`](TEAM_WORKBOARD.md) - 현재 진행 중인 작업과 담당자
4. [`ROADMAP.md`](ROADMAP.md) - 앞으로의 큰 단계

## Branch 구조

```text
main
 ├── integration
 └── agent/<github-id>/<task>
```

`main`은 안정된 공통 기준, `integration`은 여러 결과의 조합과 검증, `agent/<github-id>/<task>`는 독립적인 기획·실험·개발 공간이다. AI 모델명이 아니라 GitHub ID를 Branch 정체성으로 사용한다.

## 주요 문서 위치

| 위치 | 용도 |
|---|---|
| `TEAM_WORKBOARD.md` | 누가 무엇을 왜 진행 중인지 빠르게 확인 |
| `docs/workstreams/` | 개별 작업의 상세 목적·방향·상태 |
| `docs/workers/` | 참여자별 장기 상태 |
| `docs/architecture/` | 채택된 시스템 구조 |
| `docs/decisions/` | 중요한 결정과 이유 |
| `docs/handoffs/` | 작업 인계 정보 |
| `INTEGRATION.md` | 결과를 선택하고 통합하는 기준 |
| `docs/integration/` | 복잡한 통합 기록 |
| `CONTRIBUTING.md` | Contributor 실무 안내 |

작업 로그를 이 README에 쌓지 않는다. 현재 작업은 작업판과 Workstream에, 중요한 결정은 Decision 문서에 기록한다.
