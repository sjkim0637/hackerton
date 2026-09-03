"""사물 종류(objectType) 정규화. 앱 스피너는 tv/sofa/table/chair/shelf/other 를 보낸다."""
from __future__ import annotations

# 앱에서 보내는 표준 가구 키 (server/catalog/furniture.json 의 category 와 일치)
KNOWN_TYPES = ("tv", "sofa", "table", "chair", "shelf")

# 목록에 없는 작은 물건(컵·소품 등) 을 지울 때 쓰는 범용 키.
# 이 값은 특정 사물 힌트 대신 범용 배경 복원 지시문(_DEFAULT_HINT)으로 처리된다.
GENERIC_TYPE = "other"

# 다양한 표기를 표준 키로 모은다.
ALIASES = {
    "television": "tv", "monitor": "tv", "screen": "tv",
    "couch": "sofa", "settee": "sofa", "loveseat": "sofa",
    "desk": "table", "dining table": "table", "coffee table": "table", "side table": "table",
    "armchair": "chair", "stool": "chair", "seat": "chair",
    "bookshelf": "shelf", "bookcase": "shelf", "shelves": "shelf", "cabinet": "shelf",
    # 범용/소품 → other (특정 힌트 없이 "주변 배경으로 자연스럽게 복원")
    "기타": GENERIC_TYPE, "소품": GENERIC_TYPE, "기타/소품": GENERIC_TYPE,
    "etc": GENERIC_TYPE, "misc": GENERIC_TYPE, "object": GENERIC_TYPE,
    "item": GENERIC_TYPE, "thing": GENERIC_TYPE, "small object": GENERIC_TYPE,
    "cup": GENERIC_TYPE, "mug": GENERIC_TYPE, "tumbler": GENERIC_TYPE, "glass": GENERIC_TYPE,
    "bottle": GENERIC_TYPE, "can": GENERIC_TYPE, "bowl": GENERIC_TYPE, "plate": GENERIC_TYPE,
    "vase": GENERIC_TYPE, "plant": GENERIC_TYPE, "pot": GENERIC_TYPE, "flowerpot": GENERIC_TYPE,
    "book": GENERIC_TYPE, "box": GENERIC_TYPE, "bag": GENERIC_TYPE, "basket": GENERIC_TYPE,
    "lamp": GENERIC_TYPE, "fan": GENERIC_TYPE, "clock": GENERIC_TYPE, "speaker": GENERIC_TYPE,
    "toy": GENERIC_TYPE, "frame": GENERIC_TYPE, "picture frame": GENERIC_TYPE,
}


def normalize_object_type(object_type: str) -> str:
    """앞뒤 공백 제거 + 소문자 + 별칭 매핑. 알 수 없는 값은 그대로 둔다(거부하지 않음)."""
    key = (object_type or "").strip().lower()
    return ALIASES.get(key, key)


def is_generic(object_type: str) -> bool:
    """목록에 없는 소품(범용 배경 복원 대상)인지."""
    return normalize_object_type(object_type) == GENERIC_TYPE


def is_known(object_type: str) -> bool:
    """서버가 특정 처리를 갖고 있는 값인지 (가구 5종 + 범용 other). 경고 로그 판단용."""
    norm = normalize_object_type(object_type)
    return norm in KNOWN_TYPES or norm == GENERIC_TYPE
