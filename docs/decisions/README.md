# Decision 기록 안내

제품과 기술에 큰 영향을 주는 선택은 이 디렉터리에 ADR(Architecture Decision Record) 형태로 남긴다. 파일명은 `YYYYMMDD-short-title.md` 형식을 사용한다.

예: `20260824-select-ar-framework.md`

## Template

```markdown
# 제목

## Status

Proposed | Accepted | Superseded | Rejected

## Context

왜 결정이 필요한가

## Decision

무엇을 선택했는가

## Reason

왜 선택했는가

## Alternatives

검토한 대안

## Impact

프로젝트에 미치는 영향
```

결정이 바뀌어도 기존 문서를 삭제하지 않는다. 기존 문서의 Status와 대체 문서 연결을 갱신해 선택의 배경을 보존한다.
