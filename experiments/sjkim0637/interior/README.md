# Interior AR — 카메라 기반 공간 편집 및 AR 가구 재배치

실제 거실을 스마트폰 카메라로 보면서 기존 TV를 제거하고 벽을 복원한 뒤, TV와 새 소파를 다른 위치에 배치하는 해커톤 실험이다.

## 현재 상태

- PHASE 0: 완료
- 현재 단계: PHASE 1 구현 및 PC 검증 완료, 실기기 검증 대기
- 우선 아키텍처: 외부 AI 이미지 편집 API를 사용하는 방식 A
- Android/ARCore 앱, FastAPI 서버, Mock 및 Gemini Provider 구현

## PHASE 1 구현 결과

- 카메라·평면 인식, bbox 선택, 키프레임 캡처, 결과 표시 코드
- 반투명 가구 배치·이동·크기 조절
- Scene/Keyframe/사물 제거 Job/결과/가구 카탈로그 API
- Mock AI와 Google Gemini 이미지 편집 Provider
- 서버 단위 테스트 13개 통과
- 실제 HTTP Mock E2E 13/13 통과
- Android 빌드는 Gradle 배포 파일을 내려받을 수 없는 현재 검증 환경에서는 재실행하지 못함
- Android 실기기가 없어 앱 E2E와 시연 녹화는 대기

## 서버 주소 설정

Emulator 기본 주소는 `http://10.0.2.2:8000`이다. 실기기에서는 PC와 같은 Wi-Fi에 연결하고 다음처럼 PC LAN IP를 지정해 빌드한다.

```bash
./gradlew assembleDebug -PINTERIOR_API_BASE_URL=http://192.168.0.10:8000
```

## 확정한 시연 흐름

1. 거실을 카메라로 촬영한다.
2. 벽과 바닥을 인식한다.
3. TV 영역을 선택한다.
4. TV를 삭제하고 가려진 벽을 복원한다.
5. TV를 다른 벽으로 재배치한다.
6. 새 소파를 빈 공간에 배치한다.
7. 변경 전과 변경 후를 비교한다.

## 문서

- `docs/phase-0.md`: PHASE 0 결과와 완료 기준
- `docs/architecture.md`: 시스템 구조와 A/B/C 전략
- `docs/data-model.md`: 좌표계, ID, 키프레임, 사물 및 가구 형식
- `docs/api.md`: 앱과 서버 사이 API 초안
- `docs/backlog.md`: PHASE 1 작업 목록
- `docs/decisions.md`: 주요 결정과 이유

범위는 초기 MVP 기준이며 실기기 검증 결과와 해커톤 일정에 따라 확장하거나 변경할 수 있다.
