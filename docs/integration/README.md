# Integration 기록 안내

여러 Commit을 선별하거나 일부 변경을 제외하고, 충돌을 해결하는 등 설명이 필요한 통합 결과를 이 디렉터리에 기록한다. 일반적인 통합 절차와 확인표는 루트의 `INTEGRATION.md`를 따른다.

## Template

```markdown
# Integration

## Source

agent/<github-id>/<task>

## Workstream

docs/workstreams/<task>.md

## Selected Commits

- abc123 feat(scope): 선택한 변경

## Deferred

- def456 아직 반영하지 않은 변경

## Reason

선택 또는 제외 이유

## Conflicts

통합 중 발생한 충돌과 해결 방법

## Result

최종 통합 결과와 검증 내용
```
