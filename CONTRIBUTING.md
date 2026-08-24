# 기여 안내

이 문서는 사람 Contributor가 작업을 시작하고 공유하는 데 필요한 실무 절차를 요약한다. 전체 공통 규칙은 `AGENTS.md`가 기준이다.

## 저장소 구조와 작업 시작

안정된 기준은 `main`, 결과 조합과 검증은 `integration`, 독립 작업은 `agent/<github-id>/<task>`에서 진행한다. 새 작업은 다음 순서로 시작한다.

1. 최신 프로젝트 현황과 기존 작업을 확인한다.
2. 목적이 드러나는 `agent/<github-id>/<task>` Branch를 만든다.
3. `TEAM_WORKBOARD.md`에 작업을 등록한다.
4. 상세 설명이 필요하면 `docs/workstreams/<task>.md`를 작성한다.

## Workstream과 작업판

작업판에는 Owner, Branch, Topic, Purpose, Status, Workstream, Updated만 간결하게 적는다. 목표, 배경, 범위, 방향, 질문, 의존성, 통합 후보 여부는 Workstream 문서에 적는다. 상태가 달라지면 두 문서를 함께 갱신한다.

## Commit Convention

Commit은 나중에 필요한 변경만 선택할 수 있도록 논리적인 단위로 나눈다. 메시지는 `feat(scope): ...`, `fix(scope): ...`, `docs: ...`, `refactor(scope): ...`, `chore: ...`처럼 목적이 드러나게 작성한다.

## Pull Request

`.github/pull_request_template.md`의 항목을 작성한다. 목적, Owner, Source Branch, 관련 Workstream, 테스트 결과, 영향, Known Issues가 검토자가 이해할 수 있을 만큼 명확해야 한다. 작업 범위 밖의 변경은 분리한다.

## 문서 갱신

기능, API, DB, Architecture, Library, 실행 방법, 환경 변수, Workstream 방향 또는 통합 방식에 변화가 있으면 관련 공식 문서를 함께 갱신한다. 현재 상태는 기존 문서에 반영하고 중요한 결정 과정은 `docs/decisions/`에 남긴다.

## 테스트

현재는 기술 Stack이 정해지지 않아 공통 테스트 명령이 없다. 각 Workstream은 검증 방법과 결과를 문서화하고, Pull Request에는 실제로 수행한 확인만 기록한다.

## 통합

통합 방식과 확인 항목은 `INTEGRATION.md`를 따른다. 전체 Merge 외에도 Cherry-pick과 수동 통합을 사용할 수 있으며, 검증된 변경만 `main`에 반영한다.

## 보안

Secret, API Key, Access Token, Password, Private Key, Credential, 개인정보, 운영 데이터와 접속정보를 파일·Commit·Pull Request에 포함하지 않는다. 발견하면 공개하기 전에 담당자와 안전하게 제거 및 교체한다.
