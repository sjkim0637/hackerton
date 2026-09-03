"""가구 데이터 API. 카탈로그는 catalog/furniture.json 에서 읽는다."""
from __future__ import annotations

import json
from functools import lru_cache

from fastapi import APIRouter, HTTPException

from ..config import get_settings
from ..schemas import FurnitureData

router = APIRouter(tags=["catalog"])


@lru_cache
def _load_catalog() -> list[dict]:
    path = get_settings().catalog_file
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def catalog_has(catalog_id: str) -> bool:
    """카탈로그에 이 id 의 항목이 있는지. (placements 라우터에서 참조 무결성 확인용)"""
    return any(item.get("id") == catalog_id for item in _load_catalog())


@router.get("/catalog", response_model=list[FurnitureData])
def list_catalog(category: str | None = None) -> list[dict]:
    items = _load_catalog()
    if category:
        items = [item for item in items if item.get("category") == category]
    return items


@router.get("/catalog/{catalog_id}", response_model=FurnitureData)
def get_catalog_item(catalog_id: str) -> dict:
    for item in _load_catalog():
        if item.get("id") == catalog_id:
            return item
    raise HTTPException(status_code=404, detail=f"카탈로그 항목 없음: {catalog_id}")
