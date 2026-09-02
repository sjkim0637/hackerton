# PHASE 1 백로그

목표는 고품질 완성본이 아니라 앱 → 서버 → AI → 앱 흐름을 Android 실기기에서 한 번 성공시키는 것이다.

| 번호 | 작업 | 상태 |
|---|---|---|
| P1-1 | Android 프로젝트와 ARCore 실행 환경 생성 | TODO |
| P1-2 | 카메라 화면과 수평·수직 평면 인식 | TODO |
| P1-3 | 화면 터치 및 TV bbox 선택 UI | TODO |
| P1-4 | 키프레임 캡처와 카메라·평면 메타데이터 생성 | TODO |
| P1-5 | FastAPI 기본 프로젝트와 Scene 생성 API | TODO |
| P1-6 | Keyframe 업로드와 파일 저장 | TODO |
| P1-7 | Mock AI와 사물 제거 Job API | TODO |
| P1-8 | 앱의 Job 폴링과 결과 이미지 표시 | TODO |
| P1-9 | 삭제 전·후 비교 기능 | TODO |
| P1-10 | 반투명 TV·소파 배치, 이동, 크기 조절 | TODO |
| P1-11 | PC에서 Mock E2E 테스트 | TODO |
| P1-12 | 외부 AI Provider 연결과 오류 처리 | TODO |
| P1-13 | 외부 AI 실제 이미지 품질 확인 | TODO |
| P1-14 | Android 실기기 E2E 테스트와 시연 녹화 | TODO |

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
