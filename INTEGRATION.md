# 통합 운영 안내

통합은 모든 Branch를 한꺼번에 합치는 일이 아니라, 여러 결과 중 프로젝트에 필요한 변경을 골라 안전하게 검증하는 과정이다.

## 기본 흐름

```text
agent/<github-id>/<task>
          ↓
      integration
          ↓
       검토·테스트
          ↓
         main
```

## 선택 가능한 방식

- Branch 전체를 Merge한다.
- 필요한 Commit만 Cherry-pick한다.
- 특정 기능이나 아이디어만 수동으로 반영한다.
- 여러 구현을 비교해 하나만 채택한다.
- 아직 선택하지 않은 Branch는 실험 기록으로 유지한다.

모든 Branch를 반드시 `integration`이나 `main`에 합치지 않는다. `main`에는 목적과 영향이 명확하고 검증이 끝난 결과만 반영한다.

## 통합 전 확인표

- [ ] 기능 또는 변경의 목적이 명확하다.
- [ ] Owner GitHub ID와 Source Branch를 확인했다.
- [ ] 관련 Workstream과 Integration Candidate 값을 확인했다.
- [ ] 반영할 Commit과 변경 파일을 확인했다.
- [ ] Dependency, API, DB, Architecture 영향을 확인했다.
- [ ] 다른 Branch와의 충돌 가능성을 확인했다.
- [ ] 수행한 테스트와 결과를 확인했다.
- [ ] 필요한 문서가 갱신되었다.
- [ ] Known Issues와 제외할 변경을 확인했다.

## 통합 결과 기록

단순한 통합은 Pull Request와 Workstream에 기록한다. 여러 Commit을 선별하거나 충돌 해결, 일부 제외처럼 설명이 필요한 통합은 `docs/integration/`에 별도 기록을 남긴다.
