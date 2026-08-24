# Workstream 문서 안내

Workstream은 하나의 독립적인 작업 또는 탐색 방향이다. 구현뿐 아니라 기획 탐색, UX 검토, Architecture 비교, 기술 검증, Business Model, Prototype, API 설계도 Workstream이 될 수 있다.

파일은 `docs/workstreams/<task>.md` 형식으로 만들고 `TEAM_WORKBOARD.md`에서 연결한다.

## Template

```markdown
# Workstream

## Topic

작업 주제

## Owner

<github-id> + Agent

## Branch

agent/<github-id>/<task>

## Status

IDEA | PLANNING | IN_PROGRESS | BLOCKED | REVIEW | INTEGRATION | DONE | PAUSED | DROPPED

## Goal

이번 작업에서 확인하거나 구현하려는 내용

## Background

왜 이 작업을 시작했는가

## Current Direction

현재 검토하거나 구현 중인 방향

## Scope

이번 Workstream에서 다루는 범위

## Out of Scope

이번 Workstream에서 다루지 않는 범위

## Key Questions

아직 확인해야 할 핵심 질문

## Decisions

현재까지 내려진 중요한 결정

## Dependencies

다른 Workstream이나 시스템과의 의존성

## Notes for Other Teams

다른 팀이 알아야 할 내용

## Integration Candidate

YES | NO | TBD

## Known Issues

현재 알려진 문제

## Next

다음 진행 예정 내용

## Relevant Commits

관련 Commit

## Updated

YYYY-MM-DD
```

작업판은 전체 목록을 빠르게 보는 Index이고 Workstream은 상세 설명이다. 작업 종료 또는 중단 시 Status, Next, Known Issues, Relevant Commits를 실제 상태로 갱신한다.
