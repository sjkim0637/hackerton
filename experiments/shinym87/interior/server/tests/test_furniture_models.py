"""D7: `scripts/generate_furniture_models.py`가 만든 절차적 3D 모델(.glb) 검증.

모델 파일은 저장소에 커밋한다(MobileSAM/LaMa와 달리 수십 KB 수준으로 작아서 gitignore
대상이 아니다). 이 테스트는 각 파일이 실제로 존재하고, 유효한 glTF이며, `furniture.json`의
`size_m`과 바운딩 박스가 (오차 범위 안에서) 일치하는지 — 즉 카탈로그 설명과 실제 3D 모델
크기가 어긋나지 않는지 확인한다. `trimesh`가 없는 환경에서는 skip한다(모델 생성/검증
전용 개발 의존성이라 서버 실행에는 필요 없다).
"""
import json
from pathlib import Path

import pytest

trimesh = pytest.importorskip("trimesh", reason="trimesh는 모델 생성/검증 전용 개발 의존성")

SERVER_DIR = Path(__file__).resolve().parent.parent
CATALOG_DIR = SERVER_DIR / "catalog"
MODELS_DIR = CATALOG_DIR / "assets" / "models"


def _catalog_entries() -> list[dict]:
    return json.loads((CATALOG_DIR / "furniture.json").read_text(encoding="utf-8"))


@pytest.mark.parametrize("entry", _catalog_entries(), ids=lambda e: e["id"])
def test_model_file_exists_and_matches_declared_size(entry: dict):
    model_path = MODELS_DIR / f"{entry['id']}.glb"
    assert model_path.is_file(), f"모델 파일 없음: {model_path}"

    scene = trimesh.load(model_path)
    bounds = scene.bounds  # (2, 3): min/max in (x, y, z)
    actual = bounds[1] - bounds[0]
    expect = (entry["size_m"]["w"], entry["size_m"]["h"], entry["size_m"]["d"])

    # 절차적 생성이라 도형 조합(구/원뿔대 등)에 따라 약간의 오차가 남을 수 있어 6cm까지 허용.
    for axis, (a, e) in enumerate(zip(actual, expect)):
        assert abs(a - e) < 0.06, f"{entry['id']} 축{axis}: 실제={a:.3f} 기대={e:.3f}"

    # anchor_hint 규칙: floor 가구는 바닥(y=0)에서 위로, wall 가구는 뒤판(y=0 근방)에서
    # 위로 솟아야 자연스럽다 — 둘 다 y 최솟값이 원점 근처(0)여야 한다.
    assert abs(bounds[0][1]) < 0.02, f"{entry['id']}: y 최솟값이 0 근처가 아님 ({bounds[0][1]:.3f})"


def test_furniture_json_has_ten_entries_with_glb_models():
    entries = _catalog_entries()
    assert len(entries) == 10
    for e in entries:
        assert e["model"]["type"] == "glb"
        assert e["model"]["url"] == f"/assets/models/{e['id']}.glb"
        assert e["anchor_hint"] in ("wall", "floor")
