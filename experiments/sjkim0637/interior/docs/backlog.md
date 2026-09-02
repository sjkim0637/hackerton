# PHASE 1 백로그

목표는 고품질 완성본이 아니라 앱 → 서버 → AI → 앱 흐름을 Android 실기기에서 한 번 성공시키는 것이다.

| 번호 | 작업 | 상태 |
|---|---|---|
| P1-1 | Android 프로젝트와 ARCore 실행 환경 생성 | DONE (코드) |
| P1-2 | 카메라 화면과 수평·수직 평면 인식 | DONE (코드, 실기기 확인 대기) |
| P1-3 | 화면 터치 및 TV bbox 선택 UI | DONE (코드) |
| P1-4 | 키프레임 캡처와 카메라·평면 메타데이터 생성 | DONE (코드) |
| P1-5 | FastAPI 기본 프로젝트와 Scene 생성 API | DONE |
| P1-6 | Keyframe 업로드와 파일 저장 | DONE |
| P1-7 | Mock AI와 사물 제거 Job API | DONE |
| P1-8 | 앱의 Job 폴링과 결과 이미지 표시 | DONE (코드, 실기기 확인 대기) |
| P1-9 | 삭제 전·후 비교 기능 | DONE (코드) |
| P1-10 | 반투명 TV·소파 배치, 이동, 크기 조절 | DONE (프록시 가구 코드) |
| P1-11 | PC에서 Mock E2E 테스트 | DONE (13/13) |
| P1-12 | 외부 AI Provider 연결과 오류 처리 | DONE (단위 테스트) |
| P1-13 | 외부 AI 실제 이미지 품질 확인 | TODO (API Key 필요) |
| P1-14 | Android 실기기 E2E 테스트와 시연 녹화 | TODO (Android 기기 필요) |

## 2026-09-02 검증 결과

- `pytest`: 13 passed
- `scripts/e2e_check.py --port 8021`: 13/13 passed
- Android 빌드 재검증: Gradle 8.11.1 배포 파일 다운로드가 차단되어 현재 환경에서는 미실행

## 진행 원칙

- 한 번에 하나의 항목만 `IN_PROGRESS`로 둔다.
- 실제 API Key와 `.env`는 Commit하지 않는다.
- 기능·API·데이터 형식이 바뀌면 관련 문서를 함께 갱신한다.
- 새 아이디어는 현재 구현을 중단하지 않고 이 문서 하단에 추가한다.

## 이후 후보

- 정밀 segmentation mask
- 가구 회전과 충돌 처리
- 벽지·벽 색상·바닥 재질 변경
- 여러 배치안 저장 및 비교
- 실제 판매 상품 연결
- B 경량 모델 및 C 자체 엔진 비교
