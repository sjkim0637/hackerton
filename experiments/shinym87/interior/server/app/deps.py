"""프로세스 단위 싱글턴 (Store, AI 프로바이더)."""
from __future__ import annotations

from functools import lru_cache

from .ai import RemoveObjectProvider, build_provider
from .config import get_settings
from .store import Store


@lru_cache
def get_store() -> Store:
    return Store(get_settings().db_path)


@lru_cache
def get_provider() -> RemoveObjectProvider:
    return build_provider(get_settings())
