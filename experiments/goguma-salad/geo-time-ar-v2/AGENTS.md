# Geo-Time AR Workstream 개발 규칙

이 문서는 Git Branch `agent/goguma-salad/geo-time-ar-v2`에서 관리하는 Repository Directory `experiments/goguma-salad/geo-time-ar-v2/`에 추가로 적용되는 기술 규칙이다. 저장소 전체 협업 규칙은 [루트 AGENTS](../../../AGENTS.md)를 함께 따른다.

- 후보 조회와 최종 가시성 선택을 분리한다.
- Backend 후보 조회는 `Geo + Time`, Android 최종 가시성 선택은 6DoF 공간 맥락을 사용한다.
- ARCore Session의 원시 좌표를 영구 Database 좌표로 저장하지 않는다.
- 현재 배치 좌표는 문서화된 GeoZone-local AR 좌표계인 `+X east, +Y up, -Z north`를 사용한다.
- Media는 Object Storage에 저장하고 DB에는 Metadata와 Object Key만 저장한다.
- 모든 DB Schema 변경에는 Alembic Migration을 함께 작성한다.
- 응답 형태가 비슷해도 Moment와 Campaign의 Domain 동작을 분리한다.
- 시간은 UTC로 저장하고 Timezone이 포함된 ISO 8601 형식으로 반환한다.
- Backend 변경을 마치기 전에 가능한 경우 Ruff, Pytest, Docker Smoke Test를 실행한다.
- Android 변경을 마치기 전에 `testDebugUnitTest`와 `assembleDebug`를 실행한다.
- API, Data Model, 좌표계 또는 Platform 규칙을 바꾸면 관련 문서를 함께 갱신한다.
