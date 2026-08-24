# Worker 상태 문서 안내

이 디렉터리는 사람을 기준으로 장기적인 참여 상태를 관리한다. 참여자의 GitHub ID가 확정된 뒤 `docs/workers/<github-id>.md` 파일을 만든다. 한 사람은 여러 Workstream과 Branch를 가질 수 있다.

## Template

```markdown
# Worker Status

## GitHub ID

<github-id>

## Active Workstreams

현재 진행 중인 Workstream

## Current Branches

현재 사용하는 Branch

## Status

IDLE | ACTIVE | BLOCKED

## Current Focus

현재 가장 집중하는 내용

## Dependencies

다른 팀 또는 작업과의 의존성

## Notes

협업에 필요한 참고사항

## Updated

YYYY-MM-DD
```

Worker 문서는 “이 사람이 무엇을 하는가”를, Workstream 문서는 “이 작업이 어떤 상태인가”를 설명한다.
