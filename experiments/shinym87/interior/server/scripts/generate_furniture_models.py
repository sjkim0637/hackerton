"""절차적으로 간단한 가구 3D 모델(.glb) 10개를 만들어 카탈로그에 등록한다.

실제 제품 스캔이나 아티스트 모델링 없이, 단순 도형(박스/원기둥/원뿔대) 조합 + 단색으로
"모양만" 표현한다(질감/조명 렌더링 없음 — `trimesh`가 만드는 PBR 재질 기본값 그대로).
결과 파일은 `catalog/assets/models/*.glb`에 저장하고, `catalog/furniture.json`을
이 스크립트가 정의한 10개 항목으로 덮어쓴다.

좌표계 규칙(원점 위치가 배치 앵커와 맞아야 자연스럽다):
- `floor` 가구: 원점 = 바닥 중심(y=0에서 위로 솟음).
- `wall` 가구: 원점 = 뒤판 중심(벽에 닿는 면, 그 면에서 앞으로 튀어나옴).

실행: `.venv/Scripts/python.exe scripts/generate_furniture_models.py`
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import trimesh

SERVER_ROOT = Path(__file__).resolve().parent.parent
MODELS_DIR = SERVER_ROOT / "catalog" / "assets" / "models"
CATALOG_FILE = SERVER_ROOT / "catalog" / "furniture.json"


def _box(extents, center, color):
    m = trimesh.creation.box(extents=extents)
    m.apply_translation(center)
    m.visual.face_colors = color
    return m


def _cylinder(radius, height, center, color, sections=24):
    """`trimesh.creation.cylinder`는 Z축을 높이축으로 만들지만 이 스크립트는 Y-up이므로,
    그대로 쓰면 옆으로 누운 원기둥이 된다(실제로 겪은 버그) — X축 -90도 회전으로 맞춘다."""
    m = trimesh.creation.cylinder(radius=radius, height=height, sections=sections)
    m.apply_transform(trimesh.transformations.rotation_matrix(-np.pi / 2, [1, 0, 0]))
    m.apply_translation(center)
    m.visual.face_colors = color
    return m


def _cone_frustum(radius_bottom, radius_top, height, center, color, sections=24):
    """원뿔대(꽃병/화분/조명 갓에 씀). trimesh 에 직접 API가 없어 Y 를 높이축으로 직접 만든다
    (Z-높이로 만든 뒤 회전시키면 축 혼동으로 위치가 어긋나기 쉬워, 처음부터 Y-up으로 만든다)."""
    theta = np.linspace(0, 2 * np.pi, sections, endpoint=False)
    bottom = np.stack([radius_bottom * np.cos(theta), np.zeros(sections), radius_bottom * np.sin(theta)], axis=1)
    top = np.stack([radius_top * np.cos(theta), np.full(sections, height), radius_top * np.sin(theta)], axis=1)
    vertices = np.vstack([bottom, top, [[0, 0, 0]], [[0, height, 0]]])
    bottom_center_idx = 2 * sections
    top_center_idx = 2 * sections + 1
    faces = []
    for i in range(sections):
        j = (i + 1) % sections
        faces.append([i, j, sections + i])
        faces.append([j, sections + j, sections + i])
        faces.append([bottom_center_idx, j, i])
        faces.append([top_center_idx, sections + i, sections + j])
    m = trimesh.Trimesh(vertices=vertices, faces=faces, process=True)
    m.apply_translation(center)
    m.visual.face_colors = color
    return m


def _sphere(radius, center, color, subdivisions=2):
    m = trimesh.creation.icosphere(radius=radius, subdivisions=subdivisions)
    m.apply_translation(center)
    m.visual.face_colors = color
    return m


def _export(name: str, parts: list[trimesh.Trimesh]) -> None:
    scene = trimesh.Scene(parts)
    path = MODELS_DIR / f"{name}.glb"
    scene.export(path)
    print(f"  {name}.glb ({path.stat().st_size} bytes, {len(parts)} parts)")


# ------------------------------------------------------------------ 모델 정의
# 색상은 (R,G,B,A) 0~255. 크기(m)는 furniture.json 의 size_m 과 맞춘다.

def make_tv(w: float, h: float, d: float) -> list[trimesh.Trimesh]:
    """벽걸이 TV. 원점 = 뒤판(벽 접촉면) 중심. 화면은 진한 회색, 얇은 베젤."""
    screen = _box([w, h, d * 0.6], [0, h / 2, d * 0.8], [15, 15, 18, 255])
    stand = _box([w * 0.25, d * 0.4, d * 0.4], [0, 0.02, d * 0.2], [40, 40, 42, 255])
    return [screen, stand]


def make_sofa(w: float, h: float, d: float) -> list[trimesh.Trimesh]:
    """3인 소파. 원점 = 바닥 중심."""
    seat_h = h * 0.45
    base = _box([w, seat_h, d], [0, seat_h / 2, 0], [150, 130, 110, 255])
    back = _box([w, h - seat_h, d * 0.22], [0, seat_h + (h - seat_h) / 2, -d / 2 + d * 0.11], [140, 120, 100, 255])
    arm_h = h * 0.75
    arm_w = w * 0.08
    left_arm = _box([arm_w, arm_h, d], [-w / 2 + arm_w / 2, arm_h / 2, 0], [130, 110, 92, 255])
    right_arm = _box([arm_w, arm_h, d], [w / 2 - arm_w / 2, arm_h / 2, 0], [130, 110, 92, 255])
    leg_h = h * 0.08
    legs = [
        _box([0.05, leg_h, 0.05], [sx * (w / 2 - 0.08), leg_h / 2, sz * (d / 2 - 0.08)], [60, 45, 35, 255])
        for sx in (-1, 1) for sz in (-1, 1)
    ]
    return [base, back, left_arm, right_arm, *legs]


def make_table(w: float, h: float, d: float) -> list[trimesh.Trimesh]:
    """로우 테이블. 원점 = 바닥 중심."""
    top_t = 0.05
    top = _box([w, top_t, d], [0, h - top_t / 2, 0], [160, 120, 80, 255])
    leg_h = h - top_t
    legs = [
        _box([0.06, leg_h, 0.06], [sx * (w / 2 - 0.08), leg_h / 2, sz * (d / 2 - 0.08)], [110, 80, 55, 255])
        for sx in (-1, 1) for sz in (-1, 1)
    ]
    return [top, *legs]


def make_chair(w: float, h: float, d: float) -> list[trimesh.Trimesh]:
    """식탁 의자. 원점 = 바닥 중심."""
    seat_h = h * 0.5
    seat = _box([w, 0.05, d], [0, seat_h, 0], [90, 70, 55, 255])
    back = _box([w, h - seat_h, 0.05], [0, seat_h + (h - seat_h) / 2, -d / 2 + 0.025], [90, 70, 55, 255])
    legs = [
        _box([0.04, seat_h, 0.04], [sx * (w / 2 - 0.04), seat_h / 2, sz * (d / 2 - 0.04)], [60, 45, 35, 255])
        for sx in (-1, 1) for sz in (-1, 1)
    ]
    return [seat, back, *legs]


def make_shelf(w: float, h: float, d: float) -> list[trimesh.Trimesh]:
    """3단 벽 선반. 원점 = 뒤판(벽 접촉면) 중심."""
    back = _box([w, h, 0.02], [0, h / 2, 0.01], [120, 95, 70, 255])
    boards = [
        _box([w, 0.02, d], [0, y, d / 2], [140, 110, 82, 255])
        for y in (h * 0.15, h * 0.5, h * 0.85)
    ]
    return [back, *boards]


def make_vase(w: float, h: float, d: float) -> list[trimesh.Trimesh]:
    """꽃병. 원점 = 바닥 중심. 세라믹 화이트."""
    r = max(w, d) / 2
    body = _cone_frustum(r * 0.55, r, h * 0.65, [0, 0, 0], [235, 230, 222, 255])
    neck = _cone_frustum(r * 0.35, r * 0.55, h * 0.35, [0, h * 0.65, 0], [235, 230, 222, 255])
    return [body, neck]


def make_floor_lamp(w: float, h: float, d: float) -> list[trimesh.Trimesh]:
    """스탠드 조명. 원점 = 바닥 중심."""
    base_r = max(w, d) / 2
    base = _cylinder(base_r, 0.03, [0, 0.015, 0], [40, 40, 40, 255])
    pole = _cylinder(0.015, h * 0.8, [0, h * 0.4 + 0.03, 0], [55, 55, 58, 255])
    shade = _cone_frustum(base_r * 0.9, base_r * 0.5, h * 0.2, [0, h * 0.8 + 0.03, 0], [250, 235, 200, 255])
    return [base, pole, shade]


def make_plant(w: float, h: float, d: float) -> list[trimesh.Trimesh]:
    """화분(식물). 원점 = 바닥 중심. 잎사귀 구가 화분 위에 얹혀 전체 높이가 정확히 h가 되게 잰다."""
    pot_r = max(w, d) / 2
    pot_h = h * 0.35
    foliage_r = (h - pot_h) / 2
    pot = _cone_frustum(pot_r * 0.75, pot_r, pot_h, [0, 0, 0], [150, 90, 60, 255])
    foliage = _sphere(foliage_r, [0, pot_h + foliage_r, 0], [55, 110, 60, 255])
    return [pot, foliage]


def make_bookshelf(w: float, h: float, d: float) -> list[trimesh.Trimesh]:
    """책장. 원점 = 바닥 중심(바닥 가구로 취급)."""
    t = 0.03
    left = _box([t, h, d], [-w / 2 + t / 2, h / 2, 0], [100, 80, 60, 255])
    right = _box([t, h, d], [w / 2 - t / 2, h / 2, 0], [100, 80, 60, 255])
    shelves = [
        _box([w, t, d], [0, y, 0], [120, 95, 70, 255])
        for y in np.linspace(t, h - t, 4)
    ]
    return [left, right, *shelves]


def make_bed(w: float, h: float, d: float) -> list[trimesh.Trimesh]:
    """침대(프레임+매트리스+베개). 원점 = 바닥 중심."""
    frame_h = h * 0.3
    frame = _box([w, frame_h, d], [0, frame_h / 2, 0], [90, 70, 55, 255])
    mattress_h = h * 0.35
    mattress = _box([w * 0.96, mattress_h, d * 0.96], [0, frame_h + mattress_h / 2, 0], [235, 235, 230, 255])
    headboard = _box([w, h, 0.06], [0, h / 2, -d / 2 + 0.03], [80, 60, 48, 255])
    pillow = _box([w * 0.35, h * 0.12, d * 0.22], [0, frame_h + mattress_h + h * 0.06, -d * 0.32],
                  [250, 250, 248, 255])
    return [frame, mattress, headboard, pillow]


CATALOG: list[dict] = [
    {"id": "cat_tv_wall-55", "name": "55인치 벽걸이 TV", "category": "tv",
     "size": (1.24, 0.72, 0.06), "anchor_hint": "wall", "builder": make_tv},
    {"id": "cat_sofa_nordic-3seat", "name": "노르딕 3인 소파", "category": "sofa",
     "size": (2.1, 0.85, 0.95), "anchor_hint": "floor", "builder": make_sofa},
    {"id": "cat_table_low-oak", "name": "오크 로우 테이블", "category": "table",
     "size": (1.1, 0.4, 0.6), "anchor_hint": "floor", "builder": make_table},
    {"id": "cat_chair_dining-basic", "name": "기본 식탁 의자", "category": "chair",
     "size": (0.46, 0.9, 0.5), "anchor_hint": "floor", "builder": make_chair},
    {"id": "cat_shelf_wall-3tier", "name": "3단 벽 선반", "category": "shelf",
     "size": (0.8, 0.9, 0.25), "anchor_hint": "wall", "builder": make_shelf},
    {"id": "cat_vase_ceramic-round", "name": "세라믹 원형 꽃병", "category": "vase",
     "size": (0.18, 0.3, 0.18), "anchor_hint": "floor", "builder": make_vase},
    {"id": "cat_lamp_floor-standard", "name": "스탠드 조명", "category": "lamp",
     "size": (0.35, 1.5, 0.35), "anchor_hint": "floor", "builder": make_floor_lamp},
    {"id": "cat_plant_potted-medium", "name": "중형 화분", "category": "plant",
     "size": (0.4, 0.7, 0.4), "anchor_hint": "floor", "builder": make_plant},
    {"id": "cat_bookshelf_open-4tier", "name": "4단 오픈 책장", "category": "shelf",
     "size": (0.9, 1.8, 0.3), "anchor_hint": "floor", "builder": make_bookshelf},
    {"id": "cat_bed_queen-frame", "name": "퀸 사이즈 침대", "category": "bed",
     "size": (1.6, 0.6, 2.05), "anchor_hint": "floor", "builder": make_bed},
]


def main() -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    entries = []
    print("가구 모델 생성 중...")
    for spec in CATALOG:
        w, h, d = spec["size"]
        parts = spec["builder"](w, h, d)
        _export(spec["id"], parts)
        entries.append({
            "id": spec["id"],
            "name": spec["name"],
            "category": spec["category"],
            "size_m": {"w": w, "h": h, "d": d},
            "model": {
                "type": "glb",
                "url": f"/assets/models/{spec['id']}.glb",
                "placeholder": "cube",
            },
            "thumbnail": f"/assets/furniture/{spec['category']}.png",
            "anchor_hint": spec["anchor_hint"],
        })

    CATALOG_FILE.write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\n{CATALOG_FILE} 에 {len(entries)}개 항목 기록 완료.")


if __name__ == "__main__":
    main()
