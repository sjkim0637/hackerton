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
    # 프롬프트 끝에 덧붙일 추가 지시문 (프롬프트 튜닝/실험용, 코드 수정 없이).
    ai_extra_instruction: str = ""

    # 일시적 오류(네트워크/타임아웃/429/5xx) 시 자동 재시도 횟수와 대기(초)
    ai_max_retries: int = 1
    ai_retry_backoff_seconds: float = 2.0

    # 호출 1회당 대략적 비용(USD). 로그 집계용. gemini-2.5-flash-image ≈ $0.039/장.
    ai_cost_per_call_usd: float = 0.039

    # 결과 후처리: 원본의 (마스크 밖) 평균 밝기/색상에 맞춰 채널 게인 보정
    result_color_match: bool = True
    # 이상 결과 감지: 마스크 밖 영역의 원본↔결과 차이(MAD, 0~255) 임계값
    result_anomaly_warn_mad: float = 22.0
    result_anomaly_fail_mad: float = 55.0

    # 결과 관리
    result_max_bytes: int = 5_000_000        # 이보다 크면 품질을 낮춰 압축해서 저장 (0=안 함)
    result_keep_per_scene: int = 12          # scene 당 유지할 최근 결과 개수 (0=무제한)
    result_max_age_hours: float = 72.0       # 기동 시 이보다 오래된 결과 파일 정리 (0=안 함)
    # 제거된 사물의 크롭 이미지(원본 키프레임에서 bbox 만 잘라낸 것)도 저장한다.
    # AI 호출 없음. 앱은 로컬에서 만들지만 다른 기기/세션·웹 뷰어 재사용용으로 서버에도 남긴다.
    save_removed_object_crop: bool = True

    # 작업(scene) 당 외부 AI 호출 상한
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
