"""외부 AI "사물 제거 + 빈 공간 복원" 프로바이더 인터페이스."""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass


class ProviderError(RuntimeError):
    """프로바이더 처리 실패.

    `retryable=True` 이면 일시적 오류(네트워크/타임아웃/429/5xx)로 보고 자동 재시도 대상이다.
    """

    def __init__(self, message: str, *, retryable: bool = False) -> None:
        super().__init__(message)
        self.retryable = retryable


class ProviderNotConfigured(ProviderError):
    """API 키 등 설정이 비어 있어 호출할 수 없음. (재시도해도 소용 없음)"""

    def __init__(self, message: str) -> None:
        super().__init__(message, retryable=False)


@dataclass
class RemoveResult:
    image_bytes: bytes
    # 실제로 바뀐 영역. 키프레임 기준 정규화 {"type": "bbox", "rect": [x, y, w, h]}
    changed_region: dict


class RemoveObjectProvider(ABC):
    """구현체: mock(로컬 흉내), external(실제 외부 AI, 아직 자리만)."""

    name: str = "base"

    @abstractmethod
    def remove_object(
        self,
        *,
        image_bytes: bytes,
        region: dict,
        object_type: str,
        prompt: str,
    ) -> RemoveResult:
        """`image_bytes` 에서 `region` 의 사물을 지우고 배경을 복원한 이미지를 돌려준다."""
        raise NotImplementedError
