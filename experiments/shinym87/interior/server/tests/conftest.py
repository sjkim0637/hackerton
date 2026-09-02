import os
import tempfile

# 앱 임포트 전에 격리된 데이터 디렉터리와 mock 프로바이더를 강제한다.
os.environ.setdefault("INTERIOR_DATA_DIR", tempfile.mkdtemp(prefix="interior-test-"))
os.environ.setdefault("INTERIOR_AI_PROVIDER", "mock")

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)
