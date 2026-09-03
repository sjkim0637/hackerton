"""FastAPI 앱 진입점.

실행 (server/ 디렉터리에서):
    python -m venv .venv && . .venv/bin/activate      # Windows: .venv\\Scripts\\activate
    pip install -r requirements.txt
    cp .env.example .env
    uvicorn app.main:app --reload

문서: http://127.0.0.1:8000/docs
"""
from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .ai import provider_status
from .cleanup import sweep_old_results
from .config import get_settings
from .routers import catalog, placements, scenes


def _setup_logging() -> None:
    """앱 로거(`interior.*`)가 uvicorn 콘솔에 보이도록 핸들러를 붙인다."""
    logger = logging.getLogger("interior")
    logger.setLevel(logging.INFO)
    if not logger.handlers:
        handler = logging.StreamHandler()
        handler.setFormatter(logging.Formatter("%(levelname)s [%(name)s] %(message)s"))
        logger.addHandler(handler)
    logger.propagate = False


def create_app() -> FastAPI:
    _setup_logging()
    settings = get_settings()
    logging.getLogger("interior").info(
        "AI 설정: provider=%s, model=%s, timeout=%.0fs, 재시도=%d회, "
        "호출당 비용≈$%.4f, scene 당 최대 %d회",
        settings.ai_provider, settings.ai_model, settings.ai_timeout_seconds,
        settings.ai_max_retries, settings.ai_cost_per_call_usd,
        settings.max_ai_calls_per_scene,
    )

    # 오래된 결과 이미지 정리 (기간 기준). 개수 기준은 결과 생성 시마다 처리한다.
    sweep_old_results(settings.scenes_dir, settings.result_max_age_hours)

    app = FastAPI(
        title="Interior AR — Server / Integration",
        version="0.1.0",
        summary="PHASE 1 사용자 3(서버/통합): 세션 · 키프레임 업로드 · 사물 정보 · 외부 AI 연결 구조 · 가구 카탈로그",
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(scenes.router)
    app.include_router(catalog.router)
    app.include_router(placements.router)

    @app.get("/health", tags=["meta"])
    def health() -> dict:
        return {"status": "ok", "ai": provider_status(settings)}

    return app


app = create_app()
