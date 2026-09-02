"""실제 외부 AI: Google Gemini 이미지 편집 API 로 사물 제거 + 배경 복원.

사용법:
  1. `.env` 에서
       INTERIOR_AI_PROVIDER=external
       INTERIOR_AI_API_KEY=<Gemini API 키>
     (모델/URL 은 기본값이 있고, 필요하면 INTERIOR_AI_MODEL / INTERIOR_AI_BASE_URL 로 덮어쓴다)
  2. 서버 재시작. 라우터·저장·작업 큐 코드는 그대로.

mock provider 는 그대로 남아 있고, INTERIOR_AI_PROVIDER=mock 이면 이 파일은 안 쓰인다.
"""
from __future__ import annotations

import base64
import io
import json

import httpx
from PIL import Image

from .base import ProviderError, ProviderNotConfigured, RemoveObjectProvider, RemoveResult
from .mask import data_url_b64, location_hint, region_bbox, region_to_mask_png

_DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
_DEFAULT_MODEL = "gemini-3.1-flash-image"


class ExternalRemoveObjectProvider(RemoveObjectProvider):
    name = "external"

    def __init__(
        self,
        *,
        api_key: str,
        base_url: str = "",
        model: str = "",
        timeout: float = 120.0,
    ) -> None:
        self.api_key = api_key
        self.base_url = (base_url or _DEFAULT_BASE_URL).rstrip("/")
        self.model = model or _DEFAULT_MODEL
        self.timeout = timeout

    # ------------------------------------------------------------------ 공개 API

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
                "INTERIOR_AI_API_KEY 가 비어 있습니다. .env 에 Gemini API 키를 넣거나 "
                "INTERIOR_AI_PROVIDER=mock 으로 두세요."
            )

        mask_png = region_to_mask_png(image_bytes, region)
        body = {
            "contents": [
                {
                    "role": "user",
                    "parts": [
                        {"text": self._build_prompt(object_type, region, prompt)},
                        {"inline_data": {"mime_type": "image/jpeg", "data": data_url_b64(image_bytes)}},
                        {"inline_data": {"mime_type": "image/png", "data": data_url_b64(mask_png)}},
                    ],
                }
            ],
            "generationConfig": {"responseModalities": ["TEXT", "IMAGE"]},
        }
        url = f"{self.base_url}/models/{self.model}:generateContent"

        try:
            resp = httpx.post(
                url,
                headers={
                    "x-goog-api-key": self.api_key,
                    "Content-Type": "application/json",
                },
                json=body,
                timeout=self.timeout,
            )
        except httpx.TimeoutException as exc:
            raise ProviderError(f"Gemini 요청 시간 초과 ({self.timeout}s)") from exc
        except httpx.HTTPError as exc:  # ConnectError, ReadError, ProxyError ...
            raise ProviderError(f"Gemini 네트워크 오류: {exc!s}") from exc

        self._raise_for_status(resp)

        raw = self._extract_image(resp.json())
        jpeg = self._to_jpeg(raw)

        x, y, w, h = region_bbox(region)
        return RemoveResult(
            image_bytes=jpeg,
            changed_region={"type": "bbox", "rect": [x, y, w, h]},
        )

    # ------------------------------------------------------------------ 내부

    @staticmethod
    def _build_prompt(object_type: str, region: dict, extra: str) -> str:
        where = location_hint(region)
        text = (
            f"You are an image inpainting tool for interior photos. "
            f"Remove the {object_type} in the {where} area of the first image. "
            f"The white region of the second image (a mask) marks exactly where to edit. "
            f"Reconstruct the wall / floor / background that was behind the {object_type} so it "
            f"looks like the {object_type} was never there: match the surrounding color, texture, "
            f"lighting, shadows and perspective, and fill any shadow the {object_type} cast. "
            f"Do not move, add, or change anything outside the masked region. "
            f"Keep the exact same resolution, framing and camera angle as the input. "
            f"Return only the edited image."
        )
        return f"{text} {extra}".strip() if extra else text

    @staticmethod
    def _raise_for_status(resp: httpx.Response) -> None:
        if resp.status_code == 200:
            return
        try:
            payload = resp.json()
            detail = payload.get("error", {}).get("message") or json.dumps(payload)[:400]
        except ValueError:
            detail = resp.text[:400]

        code = resp.status_code
        if code == 429:
            raise ProviderError(f"Gemini API 사용 한도 초과 (429): {detail}")
        if code in (401, 403):
            raise ProviderError(f"Gemini 인증 실패 ({code}) — API 키를 확인하세요: {detail}")
        if code == 400:
            raise ProviderError(f"Gemini 잘못된 요청 (400): {detail}")
        if code == 404:
            raise ProviderError(f"Gemini 모델 없음 (404) — INTERIOR_AI_MODEL 확인: {detail}")
        if 500 <= code < 600:
            raise ProviderError(f"Gemini 서버 오류 ({code}): {detail}")
        raise ProviderError(f"Gemini 요청 실패 ({code}): {detail}")

    @staticmethod
    def _extract_image(payload: dict) -> bytes:
        blocked = (payload.get("promptFeedback") or {}).get("blockReason")
        if blocked:
            raise ProviderError(f"Gemini 가 요청을 차단했습니다: {blocked}")

        candidates = payload.get("candidates") or []
        if not candidates:
            raise ProviderError(f"Gemini 응답에 후보가 없습니다: {json.dumps(payload)[:400]}")

        cand = candidates[0]
        parts = (cand.get("content") or {}).get("parts") or []
        texts: list[str] = []
        for part in parts:
            inline = part.get("inlineData") or part.get("inline_data")
            if inline and inline.get("data"):
                try:
                    return base64.b64decode(inline["data"])
                except (ValueError, TypeError) as exc:
                    raise ProviderError(f"Gemini 이미지 디코드 실패: {exc}") from exc
            if part.get("text"):
                texts.append(part["text"])

        finish = cand.get("finishReason", "?")
        hint = (" / ".join(texts))[:300]
        raise ProviderError(
            f"Gemini 응답에 이미지가 없습니다 (finishReason={finish}){f': {hint}' if hint else ''}"
        )

    @staticmethod
    def _to_jpeg(raw: bytes) -> bytes:
        try:
            with Image.open(io.BytesIO(raw)) as im:
                rgb = im.convert("RGB")
                out = io.BytesIO()
                rgb.save(out, format="JPEG", quality=90)
                return out.getvalue()
        except Exception as exc:  # noqa: BLE001
            raise ProviderError(f"Gemini 결과 이미지를 열 수 없습니다: {exc}") from exc
