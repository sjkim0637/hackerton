"""AI 프로바이더 선택. 설정값 하나로 mock <-> external 을 바꾼다."""
from __future__ import annotations

from ..config import Settings
from .base import (
    ProviderError,
    ProviderNotConfigured,
    RemoveObjectProvider,
    RemoveResult,
)
from .colormatch import AnomalyReport, check_result_anomaly, match_to_source
from .external import ExternalRemoveObjectProvider
from .lama import LamaInpaintProvider
from .mock import MockRemoveObjectProvider
from .objects import (
    GENERIC_TYPE,
    KNOWN_TYPES,
    is_generic,
    is_known,
    normalize_object_type,
)

__all__ = [
    "GENERIC_TYPE",
    "KNOWN_TYPES",
    "AnomalyReport",
    "ProviderError",
    "ProviderNotConfigured",
    "RemoveObjectProvider",
    "RemoveResult",
    "build_provider",
    "check_result_anomaly",
    "is_generic",
    "is_known",
    "match_to_source",
    "normalize_object_type",
    "provider_status",
]


def build_provider(settings: Settings) -> RemoveObjectProvider:
    if settings.ai_provider == "external":
        return ExternalRemoveObjectProvider(
            api_key=settings.ai_api_key,
            base_url=settings.ai_base_url,
            model=settings.ai_model,
            timeout=settings.ai_timeout_seconds,
        )
    if settings.ai_provider == "lama":
        if settings.lama_model_path is None:
            raise ProviderNotConfigured(
                "INTERIOR_LAMA_MODEL_PATH 가 설정되지 않았습니다 "
                "(docs/workstreams/interior-mobilesam.md 모델 준비 절차 참고)"
            )
        return LamaInpaintProvider(settings.lama_model_path)
    return MockRemoveObjectProvider()


def provider_status(settings: Settings) -> dict:
    """/health 에서 노출. 키 값 자체는 돌려주지 않는다."""
    if settings.ai_provider == "lama":
        ready = settings.lama_model_path is not None and settings.lama_model_path.is_file()
        return {
            "provider": "lama",
            "ready": ready,
            "detail": "로컬 모델 로드 가능" if ready else "INTERIOR_LAMA_MODEL_PATH 필요/파일 없음",
        }
    ready = settings.ai_provider == "mock" or bool(settings.ai_api_key)
    status: dict = {
        "provider": settings.ai_provider,
        "ready": ready,
        "detail": (
            "mock 은 항상 사용 가능"
            if settings.ai_provider == "mock"
            else ("키 설정됨" if ready else "INTERIOR_AI_API_KEY 필요")
        ),
    }
    if settings.ai_provider == "external":
        status["model"] = settings.ai_model
    return status
