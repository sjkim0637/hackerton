"""실제 외부 AI 이미지 편집 API 자리. 지금은 껍데기만 있다.

나중에 할 일:
  1. `.env` 에서 INTERIOR_AI_PROVIDER=external 로 바꾸고
     INTERIOR_AI_API_KEY / INTERIOR_AI_BASE_URL / INTERIOR_AI_MODEL 을 채운다.
  2. 아래 `remove_object()` 의 TODO 블록을 실제 HTTP 호출로 채운다.
그 밖의 서버 코드(라우터, 저장, 작업 큐)는 그대로 두면 된다.
"""
from __future__ import annotations

from .base import ProviderNotConfigured, RemoveObjectProvider, RemoveResult


class ExternalRemoveObjectProvider(RemoveObjectProvider):
    name = "external"

    def __init__(self, *, api_key: str, base_url: str, model: str) -> None:
        self.api_key = api_key
        self.base_url = base_url.rstrip("/")
        self.model = model

    def remove_object(
        self,
        *,
        image_bytes: bytes,
        region: dict,
        object_type: str,
        prompt: str,
    ) -> RemoveResult:
        if not self.api_key:
            raise ProviderNotConfigured(
                "INTERIOR_AI_API_KEY 가 비어 있습니다. .env 에 키를 넣거나 "
                "INTERIOR_AI_PROVIDER=mock 으로 두세요."
            )

        # ------------------------------------------------------------------
        # TODO(P1-10): 실제 외부 AI 이미지 편집 API 호출로 교체.
        #
        #   import httpx
        #   from .mask import region_to_mask_png       # region -> 같은 해상도 PNG 마스크
        #
        #   files = {
        #       "image": ("keyframe.jpg", image_bytes, "image/jpeg"),
        #       "mask": ("mask.png", region_to_mask_png(image_bytes, region), "image/png"),
        #   }
        #   data = {"prompt": prompt, "model": self.model}
        #   headers = {"Authorization": f"Bearer {self.api_key}"}
        #   resp = httpx.post(
        #       f"{self.base_url}/images/edits", files=files, data=data,
        #       headers=headers, timeout=60.0,
        #   )
        #   resp.raise_for_status()
        #   result_bytes = resp.content            # 또는 base64 필드 디코드
        #   return RemoveResult(
        #       image_bytes=result_bytes,
        #       changed_region=_bbox_of(region),
        #   )
        # ------------------------------------------------------------------
        raise NotImplementedError(
            "외부 AI 프로바이더 연동은 아직 구현 전입니다 (PHASE 1, 이슈 P1-10). "
            "지금은 INTERIOR_AI_PROVIDER=mock 을 사용하세요."
        )
