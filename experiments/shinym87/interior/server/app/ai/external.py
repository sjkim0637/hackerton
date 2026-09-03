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
import json
import logging

import httpx

from .base import ProviderError, ProviderNotConfigured, RemoveObjectProvider, RemoveResult
from .imageops import ensure_jpeg_size, image_size
from .mask import data_url_b64, location_hint, region_bbox, region_to_mask_png

_DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
_DEFAULT_MODEL = "gemini-3.1-flash-image"

_log = logging.getLogger("interior.ai.external")

# 다양한 표기를 표준 키로 모은다 (앱 스피너는 tv/sofa/table/chair/shelf 를 보낸다).
_OBJECT_ALIASES = {
    "television": "tv", "monitor": "tv", "screen": "tv",
    "couch": "sofa", "settee": "sofa", "loveseat": "sofa",
    "desk": "table", "dining table": "table", "coffee table": "table", "side table": "table",
    "armchair": "chair", "stool": "chair", "seat": "chair",
    "bookshelf": "shelf", "bookcase": "shelf", "shelves": "shelf", "cabinet": "shelf",
}

# 사물 종류별로 "무엇을 복원해야 하는가" 가 다르다.
_SURFACE_HINTS = {
    "tv": (
        "This TV is mounted on the wall. Rebuild the flat wall that was behind it: continue "
        "the wall's colour, paint texture and any moulding, panel seams or wallpaper pattern "
        "straight through the area. Also remove the wall bracket, any visible cables and the "
        "faint rectangular shadow the TV cast on the wall."
    ),
    "sofa": (
        "This sofa stands on the floor, usually against or near a wall. Rebuild the floor where "
        "it sat with the same flooring material, plank/tile direction and perspective, and rebuild "
        "the lower part of the wall and the skirting board / baseboard behind it. Remove the soft "
        "contact shadow on the floor and any cushions or throws that belong to the sofa."
    ),
    "table": (
        "This table stands on the floor. Rebuild one continuous, unbroken floor where it and its "
        "legs were: match the plank or tile lines, grout, rug edges and the perspective. Remove "
        "the shadow pooled under the table and anything resting on top of it."
    ),
    "chair": (
        "This chair stands on the floor. Rebuild the continuous floor beneath it and its legs, "
        "matching material, pattern direction and perspective, and remove the small contact "
        "shadow it cast."
    ),
    "shelf": (
        "This shelf is fixed to the wall. Rebuild the flat wall behind it (colour, texture and "
        "any pattern) and remove its brackets, the items sitting on it and the shadow line it "
        "cast on the wall."
    ),
}
_DEFAULT_HINT = (
    "Rebuild whatever surface was behind it — wall, floor, or both — matching the surrounding "
    "colour, texture, pattern and perspective, and remove any shadow it cast."
)


def _normalize_object_type(object_type: str) -> str:
    key = (object_type or "").strip().lower()
    return _OBJECT_ALIASES.get(key, key)


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
        image_b64 = data_url_b64(image_bytes)
        mask_b64 = data_url_b64(mask_png)
        instruction = self._build_prompt(object_type, region, prompt)
        body = {
            "contents": [
                {
                    "role": "user",
                    "parts": [
                        {"text": instruction},
                        {"inline_data": {"mime_type": "image/jpeg", "data": image_b64}},
                        {"inline_data": {"mime_type": "image/png", "data": mask_b64}},
                    ],
                }
            ],
            "generationConfig": {"responseModalities": ["TEXT", "IMAGE"]},
        }
        url = f"{self.base_url}/models/{self.model}:generateContent"

        # --- 요청 진단 로그 (이미지가 실제로 실려 나가는지 확인용) ---
        src_w, src_h = image_size(image_bytes)
        _log.info(
            "[Gemini 요청] model=%s url=%s\n"
            "  parts: text(%d자) + image(jpeg %d bytes, %dx%d, base64 %d자) + mask(png %d bytes, base64 %d자)\n"
            "  region=%s prompt.head=%r",
            self.model, url,
            len(instruction),
            len(image_bytes), src_w, src_h, len(image_b64),
            len(mask_png), len(mask_b64),
            region, instruction[:120],
        )
        if len(image_bytes) < 2000 or len(image_b64) < 2000:
            _log.warning(
                "[Gemini 요청] 원본 이미지가 비정상적으로 작습니다 (%d bytes). "
                "키프레임 캡처가 검은 화면일 수 있음.", len(image_bytes),
            )

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

        payload = resp.json()
        self._log_response(resp.status_code, payload)
        raw = self._extract_image(payload)
        # Gemini 는 원본과 다른 해상도로 돌려줄 수 있어 원본 크기로 강제 리사이즈한다.
        try:
            jpeg = ensure_jpeg_size(raw, image_size(image_bytes))
        except Exception as exc:  # noqa: BLE001
            raise ProviderError(f"Gemini 결과 이미지를 열 수 없습니다: {exc}") from exc

        x, y, w, h = region_bbox(region)
        return RemoveResult(
            image_bytes=jpeg,
            changed_region={"type": "bbox", "rect": [x, y, w, h]},
        )

    # ------------------------------------------------------------------ 내부

    @staticmethod
    def _build_prompt(object_type: str, region: dict, extra: str) -> str:
        norm = _normalize_object_type(object_type)
        surface = _SURFACE_HINTS.get(norm, _DEFAULT_HINT)
        label = (object_type or "").strip() or norm or "object"
        where = location_hint(region)
        text = (
            f"You are a photo inpainting tool for interior photos. "
            f"In the first image, completely remove the {label} located in the {where} area, "
            f"including its legs, base, stand and any attached parts. "
            f"The white region of the second image is a mask marking exactly where you may edit. "
            f"{surface} "
            f"Match the lighting, white balance and grain of the rest of the photo so the edit is "
            f"invisible. Do not move, add, resize or restyle anything outside the masked region and "
            f"do not introduce any new furniture. Keep the exact same resolution, framing and "
            f"camera angle as the input. Return only the edited image, with no text."
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
    def _log_response(status: int, payload: dict) -> None:
        """응답 파트 요약 로그: 이미지가 왔는지 / 텍스트로 뭐라 답했는지."""
        cand = (payload.get("candidates") or [{}])[0]
        finish = cand.get("finishReason")
        parts = (cand.get("content") or {}).get("parts") or []
        summary: list[str] = []
        for part in parts:
            inline = part.get("inlineData") or part.get("inline_data")
            if inline and inline.get("data"):
                mime = inline.get("mimeType") or inline.get("mime_type")
                data = inline.get("data", "")
                try:
                    decoded = len(base64.b64decode(data))
                except Exception:  # noqa: BLE001
                    decoded = -1
                summary.append(f"image({mime}, base64 {len(data)}자 → {decoded} bytes)")
            elif part.get("text") is not None:
                summary.append(f"text={part['text']!r}")
        pf = payload.get("promptFeedback")
        _log.info(
            "[Gemini 응답] status=%s finishReason=%s parts=%s%s",
            status, finish, summary or "(없음)",
            f" promptFeedback={pf}" if pf else "",
        )

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
