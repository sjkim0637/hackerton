"""사물 종류(objectType) 정규화. 앱 스피너는 tv/sofa/table/chair/shelf 를 보낸다."""
from __future__ import annotations

# 앱에서 보내는 표준 키 (server/catalog/furniture.json 의 category 와 일치)
KNOWN_TYPES = ("tv", "sofa", "table", "chair", "shelf")

# 다양한 표기를 표준 키로 모은다.
ALIASES = {
    "television": "tv", "monitor": "tv", "screen": "tv",
    "couch": "sofa", "settee": "sofa", "loveseat": "sofa",
    "desk": "table", "dining table": "table", "coffee table": "table", "side table": "table",
    "armchair": "chair", "stool": "chair", "seat": "chair",
    "bookshelf": "shelf", "bookcase": "shelf", "shelves": "shelf", "cabinet": "shelf",
}


def normalize_object_type(object_type: str) -> str:
    """앞뒤 공백 제거 + 소문자 + 별칭 매핑. 알 수 없는 값은 그대로 둔다(거부하지 않음)."""
    key = (object_type or "").strip().lower()
    return ALIASES.get(key, key)


def is_known(object_type: str) -> bool:
    return normalize_object_type(object_type) in KNOWN_TYPES
