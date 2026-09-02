"""환경변수 기반 설정. 접두사 INTERIOR_, `.env` 파일 지원."""
from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

SERVER_ROOT = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="INTERIOR_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # 저장 위치
    data_dir: Path = SERVER_ROOT / "data"
    catalog_file: Path = SERVER_ROOT / "catalog" / "furniture.json"
    assets_dir: Path = SERVER_ROOT / "catalog" / "assets"

    # 외부 AI 연결 (mock | external)
    #  - external 은 Google Gemini 이미지 편집 API 를 호출한다.
    ai_provider: str = "mock"
    ai_api_key: str = ""  # INTERIOR_AI_API_KEY — Gemini API 키
    ai_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    ai_model: str = "gemini-3.1-flash-image"
    ai_timeout_seconds: float = 120.0

    # 작업당 외부 AI 호출 상한
    max_ai_calls_per_scene: int = 20

    @property
    def db_path(self) -> Path:
        return self.data_dir / "interior.db"

    @property
    def scenes_dir(self) -> Path:
        return self.data_dir / "scenes"


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.data_dir.mkdir(parents=True, exist_ok=True)
    settings.scenes_dir.mkdir(parents=True, exist_ok=True)
    return settings
