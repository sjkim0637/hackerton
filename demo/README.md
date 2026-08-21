# Demo

`docker compose up -d` 후 루트의 `infra/scripts/smoke-test.ps1`을 실행한다. Android 앱은 현재 위치에 Zone이 없으면 서울 Demo 좌표를 사용하지 않으므로, 현장 외 테스트에서는 Emulator Location을 서울시청 좌표로 설정하거나 API를 직접 확인한다.
