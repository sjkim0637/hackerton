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
from .mock import MockRemoveObjectProvider
from .objects import GENERIC_TYPE, KNOWN_TYPES, is_generic, is_known, normalize_object_type

__all__ = [
    "ProviderError",
    "ProviderNotConfigured",
    "RemoveObjectProvider",
    "RemoveResult",
    "KNOWN_TYPES",
    "GENERIC_TYPE",
    "is_known",
    "is_generic",
    "normalize_object_type",
    "AnomalyReport",
    "check_result_anomaly",
    "match_to_source",
    "build_provider",
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
    return MockRemoveObjectProvider()


def provider_status(settings: Settings) -> dict:
    """/health 에서 노출. 키 값 자체는 돌려주지 않는다."""
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
