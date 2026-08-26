# 독립 실험 공간 안내

이 디렉터리는 기획·탐색 단계에서 서로 다른 아이디어와 구현 방향을 독립적으로 검증하는 공간이다.

## 이름 구분

| 구분 | 형식 | 예시 |
|---|---|---|
| Git Branch | `agent/<github-id>/<task>` | `agent/goguma-salad/geo-time-ar-v2` |
| 실제 폴더 | `experiments/<github-id>/<task>/` | `experiments/goguma-salad/geo-time-ar-v2/` |

`agent/`는 Git Branch 이름에만 사용한다. Repository에 `agent/` 폴더를 만들지 않는다. 실제 Project 파일은 항상 `experiments/` 아래에 둔다.

## 기본 구조

```text
experiments/
├── <github-id>/
│   └── <task>/
│       ├── README.md
│       ├── Source Code
│       ├── 실행 설정
│       └── 기술 문서
└── README.mdk
```

예:

```text
experiments/goguma-salad/geo-time-ar-v2/
```

## 운영 규칙

- Git Branch는 `agent/<github-id>/<task>`, 실제 Project Directory는 `experiments/<github-id>/<task>/` 형식을 사용한다.
- 실험 폴더 하나는 다른 폴더에 의존하지 않고 실행·검토할 수 있도록 구성한다.
- 실험의 목적과 현재 상태는 루트 `TEAM_WORKBOARD.md`와 `docs/workstreams/<task>.md`에 등록한다.
- 실험 내부 `README.md`에는 실행 방법, 현재 구현, 제약사항을 기록한다.
- 실험별 기술 규칙이 필요하면 해당 폴더에 `AGENTS.md`를 둘 수 있다. 루트 `AGENTS.md`의 공통 협업 규칙은 항상 적용된다.
- Build 결과, Local 환경, Secret은 Commit하지 않는다.
- 같은 이름의 설정이나 Source 경로를 Repository Root에 만들지 않는다.

## 통합과 승격

여러 실험은 `integration`에서 폴더별로 함께 비교할 수 있다. 모든 실험을 정식 프로젝트로 합칠 필요는 없다. 방향이 채택되면 전체 Branch를 그대로 옮기기보다 검증된 Commit과 기능을 선별하고, Architecture Decision을 남긴 뒤 정식 구조로 승격한다.

## Git Fetch와 작업 화면

폴더 분리는 충돌과 가독성 문제를 줄이지만 Git 다운로드 용량을 줄이지는 않는다. `git fetch`는 다른 Branch의 Git 객체를 내려받을 수 있지만, 작업 화면에는 현재 Checkout한 Branch의 파일만 표시된다. 저장소가 크게 성장하면 `--single-branch`, Partial Clone 또는 별도 Repository 사용을 검토한다.
