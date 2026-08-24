# 프로젝트 공통 작업 규칙

이 문서는 이 저장소에서 일하는 사람과 모든 AI Agent가 따라야 할 공통 규칙의 **단일 기준(Single Source of Truth)** 이다. Codex, Claude, Gemini 등 어떤 도구를 사용하더라도 작업을 시작하기 전에 이 문서를 먼저 읽는다.

## 1. 저장소의 현재 목적

현재는 실제 애플리케이션을 만드는 단계가 아니라 여러 팀이 독립적으로 기획하고 실험할 수 있는 협업 기반을 만드는 단계이다. 기술 스택과 제품 구조가 결정되기 전에는 소스 코드, 데이터베이스, 배포 설정을 임의로 만들지 않는다.

## 2. 작업 시작 전 확인 순서

1. `AGENTS.md`
2. `PROJECT_STATUS.md`
3. `TEAM_WORKBOARD.md`
4. `ROADMAP.md`
5. 관련 `docs/workstreams/` 문서
6. 관련 `docs/architecture/` 및 `docs/decisions/` 문서
7. 담당자의 `docs/workers/` 문서
8. 필요 시 `INTEGRATION.md`와 `docs/handoffs/`

다음 Git 상태도 반드시 확인한다.

```bash
git branch --show-current
git status
git log --oneline -5
git remote -v
```

미Commit 변경은 다른 사람의 작업일 수 있다. 내용을 확인하지 않고 삭제, 덮어쓰기, 되돌리기를 하지 않는다.

## 3. Branch 운영

기본 구조는 다음과 같다.

```text
main
 ├── integration
 └── agent/<github-id>/<task>
```

- `main`: 검증된 공통 기준과 프로젝트 전체 문서를 유지한다.
- `integration`: 여러 작업의 조합, 충돌 해결, 통합 테스트에 사용한다.
- `agent/<github-id>/<task>`: 개인 또는 팀의 독립 작업에 사용한다.
- Branch 정체성은 AI 모델명이 아니라 GitHub ID와 작업명으로 표현한다.
- `<task>`는 `ar-view`, `content-flow`처럼 목적이 드러나는 짧은 이름을 사용한다. `test`, `work`, `final`처럼 의미가 모호한 이름은 피한다.
- 실제 기능 개발과 실험은 `main`에서 직접 하지 않는다. 공통 하네스와 저장소 관리 문서는 필요할 때 `main`에서 수정할 수 있다.
- 모든 Branch를 합칠 필요는 없다. 장기 분기, 여러 구현의 비교, 일부 Commit만 선택하는 방식을 허용한다.

## 4. 새 작업 시작 절차

1. 작업 목적과 범위를 정한다.
2. `agent/<github-id>/<task>` Branch를 만든다.
3. `TEAM_WORKBOARD.md`에 작업을 한 줄로 등록한다.
4. 상세 설명이 필요하면 `docs/workstreams/<task>.md`를 만든다.
5. Owner, Branch, Status, 방향, 통합 후보 여부를 기록한다.
6. 작업을 시작한다.

Branch만 만들고 작업판에 알리지 않는 상태를 피한다. 작업판에는 요약만 쓰고 자세한 내용은 Workstream 문서에 쓴다.

## 5. 상태값

Workstream과 작업판은 다음 값만 사용한다.

| 상태 | 의미 |
|---|---|
| `IDEA` | 아이디어를 공유한 상태 |
| `PLANNING` | 범위와 방향을 정리하는 상태 |
| `IN_PROGRESS` | 실제 작업 중 |
| `BLOCKED` | 외부 결정이나 의존성 때문에 진행 불가 |
| `REVIEW` | 검토를 기다리는 상태 |
| `INTEGRATION` | 통합 후보로 검증 중 |
| `DONE` | 목표를 달성해 종료 |
| `PAUSED` | 다시 시작할 수 있도록 일시 중단 |
| `DROPPED` | 이유를 기록하고 채택하지 않기로 종료 |

## 6. 문서 역할

- `README.md`: 처음 방문한 사람을 위한 안내
- `PROJECT_STATUS.md`: 프로젝트 전체가 어느 단계인지 설명
- `TEAM_WORKBOARD.md`: 누가 무엇을 하는지 빠르게 확인하는 작업판
- `ROADMAP.md`: 프로젝트의 큰 단계
- `INTEGRATION.md`: Branch 결과를 선택하고 합치는 기준
- `docs/workstreams/`: 작업 단위의 목적, 방향, 상태
- `docs/workers/`: 사람 단위의 장기 상태
- `docs/architecture/`: 현재 채택된 시스템 구조
- `docs/decisions/`: 중요한 선택과 이유(ADR)
- `docs/handoffs/`: 다른 담당자에게 넘길 정보
- `docs/integration/`: 복잡한 통합의 선택과 결과

새 문서를 만들기 전에 위 역할 중 어디에 해당하는지 확인한다. `final2.md`, `temp.md`, `new_notes.md` 같은 중복·임시 문서는 만들지 않는다.

## 7. 문서 작성 원칙

- 공식 문서는 비개발자도 이해할 수 있는 한국어로 작성한다.
- 코드 Identifier, API, Library, Framework, Git Command, 파일·디렉터리·Branch 이름, Commit Message는 원문 표기를 유지한다.
- 공식 문서는 변경 과정보다 현재 상태를 먼저 설명한다. 중요한 선택 과정은 `docs/decisions/`에 남긴다.
- 기능, API, DB Schema, Architecture, Library, 실행 방법, 환경 변수, 프로젝트 구조, Workstream 방향 또는 통합 방식이 바뀌면 관련 문서도 함께 갱신한다.
- 이미 기록된 Decision은 삭제하지 않고 상태를 `Superseded` 또는 `Rejected`로 바꿔 이력을 보존한다.

## 8. Commit 규칙

나중에 필요한 변경만 선택할 수 있도록 하나의 논리적인 목적을 한 Commit에 담는다.

권장 형식:

```text
<type>(선택적 범위): 명확한 변경 내용
```

예:

```text
docs: register AR view workstream
feat(api): add moment retrieval endpoint
fix(auth): handle expired token
refactor(core): isolate timeline service
```

`update`, `수정`, `final2`처럼 목적을 알 수 없는 메시지는 사용하지 않는다. 관련 없는 변경을 한 Commit에 섞지 않는다.

## 9. 통합 원칙

- 전체 Branch Merge, 특정 Commit Cherry-pick, 일부 기능 수동 통합 중 상황에 맞는 방법을 선택한다.
- 같은 기능의 여러 구현을 허용하고 비교 후 하나만 채택할 수 있다.
- 선택하지 않은 Branch도 실험 기록으로 유지할 수 있다.
- 통합 전 목적, Owner, Source Branch, Workstream, Commit, 의존성, API/DB/Architecture 영향, 충돌 가능성, 테스트, 문서, Known Issues를 확인한다.
- 검증된 결과만 `main`에 반영한다. 자세한 절차는 `INTEGRATION.md`를 따른다.

## 10. 안전과 보안

- `.env`, API Key, Access Token, Password, Private Key, Credential, 개인정보, 운영 DB 및 서버 접속정보를 Commit하지 않는다.
- `git reset --hard`, `git clean -fd`, `git push --force`, `git rebase --onto`는 다른 작업을 잃게 할 수 있으므로 대상과 영향을 확인하지 않고 실행하지 않는다.
- 기존 사용자 또는 다른 Agent의 변경을 임의로 삭제하거나 되돌리지 않는다.
- Stack이 정해지기 전에는 `package.json`, `requirements.txt`, `pyproject.toml`, `Dockerfile`, CI/CD, Cloud Infrastructure, 실제 Source Code 등을 만들지 않는다.

## 11. 여러 작업 동시 운영

여러 Agent나 작업을 동시에 진행할 때는 Git Worktree 사용을 권장한다. 각 Worktree는 하나의 `agent/<github-id>/<task>` Branch에 연결한다. Worktree 생성 여부와 위치는 팀과 합의하며, 하네스 자체가 특정 IDE나 Agent에 의존하지 않도록 한다.

## 12. 작업 종료 점검

1. Workstream과 `TEAM_WORKBOARD.md`의 Status를 실제 상태로 맞춘다.
2. `Next`, `Known Issues`, `Relevant Commits`를 갱신한다.
3. 필요하면 Handoff를 작성한다.
4. `git status`와 `git diff`로 범위를 확인한다.
5. 테스트 가능한 내용을 검증한다.
6. 논리적인 단위로 Commit한다.

작업 종료 상태는 `DONE`, `PAUSED`, `DROPPED`, `INTEGRATION` 중 결과에 맞게 선택한다. 탐색 결과가 `DROPPED`여도 이유와 배운 점을 남기면 유효한 프로젝트 자산이다.
