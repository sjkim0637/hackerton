# 결정 기록 (interior)

이 실험 범위의 주요 결정. 형식은 루트 `docs/decisions/README.md` 의 ADR 템플릿을 줄인 것.
결정이 바뀌면 지우지 말고 Status 를 바꾸고 대체 결정을 연결한다.

---

## D1. 아키텍처는 A(외부 AI API) 우선

- Status: Accepted (2026-09-02)
- Context: 사물 삭제 후 빈 공간 복원 품질을 해커톤 기간에 확보해야 한다.
- Decision: 외부 AI 이미지 편집 API 로 전체 기능을 먼저 완성한다. B(경량 모델),
  C(자체 엔진)는 PHASE 6/7 에서 별도 브랜치로 비교 실험한다.
- Reason: 설계서 9장의 `A > B > C` 우선순위. A 는 학습/GPU 서버 없이 가장 빠르게
  동작하는 결과물을 만든다.
- Alternatives: B 먼저(모바일 최적화 부담), C 먼저(난이도 최고, 가려진 영역 한계).
- Impact: 서버에 외부 AI 어댑터 계층이 필요. 인터넷 의존/호출 비용/응답 지연을
  설계에 반영(캐시, 호출 상한, job 폴링).

---

## D2. 초기에는 1인(shinym87) 수직 슬라이스로 진행

- Status: Accepted (2026-09-02)
- Context: 설계서는 3인 역할 분담(사용자 1/2/3) + PHASE 0 부터 병렬을 전제한다.
  현재는 1명이 시작한다.
- Decision: 한 브랜치(`agent/shinym87/interior`)에서 앱 → 서버 → 외부 AI 순으로
  얇게 관통하는 슬라이스를 만든다. 역할 경계와 PHASE 구분은 문서상 유지한다.
- Reason: 초반에는 인터페이스 협상보다 한 번 동작하는 흐름을 빨리 만드는 게 낫다.
- Alternatives: 설계서대로 3역할 병렬(현재 인원으로는 과함).
- Impact: 인원 합류 시 미완료 PHASE 항목을 새 Workstream 으로 분리. 그때까지
  `TEAM_WORKBOARD.md` 에는 `interior` 한 줄만 유지.

---

## D3. MVP 사물 영역은 bbox(축 정렬 사각형)

- Status: Accepted (2026-09-02)
- Context: 정밀 세그멘테이션은 시간이 든다. TV 는 대체로 직사각형이다.
- Decision: MVP 는 정규화 `bbox` 로 대상 영역을 지정한다. 서버가 사각형 마스크로
  변환해 AI 에 넘긴다. 픽셀 마스크(`type: "mask"`)는 확장 필드로 스펙만 정의.
- Reason: 앱 UI(드래그로 사각형)와 서버 변환이 단순하다. PHASE 2 에서 정밀화.
- Impact: TV 처럼 사각형이 아닌 사물(소파 등)은 여백이 함께 지워질 수 있음 → PHASE 2 개선.

---

## D4. AR 라이브러리는 SceneView(arsceneview 2.3.0)

- Status: Accepted (2026-09-02) — `agent/shinym87/ar2` 검증 결과 계승
- Context: 순수 ARCore + OpenGL 은 보일러플레이트가 많다.
- Decision: `io.github.sceneview:arsceneview:2.3.0` 사용. 카메라 배경, 평면 격자,
  Filament 씬 그래프, 권한/설치 안내를 라이브러리가 처리.
- Alternatives: 순수 ARCore + OpenGL(`hello_ar_kotlin`).
- Impact: 씬 그래프/제스처 API 가 SceneView 에 종속. 프로젝트 공통 표준은 아님.
