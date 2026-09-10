"""LaMa 로컬 인페인팅 프로바이더.

모델 파일(~198MB)은 저장소에 커밋하지 않으므로, `server/models/lama_fp32.onnx` 가 없으면
실제 추론 테스트는 건너뛴다. 모델 없이도 검증 가능한 것(생성 실패 처리)은 항상 돈다.

실측 확인한 모델 계약(문서만 보고 가정하지 않음, `real_living_room.jpg`로 직접 검증):
- 입력 `image`: (1,3,512,512) float32, 0~1 정규화. `mask`: (1,1,512,512) float32, 1=채울 영역.
- 출력은 0~1 이 아니라 0~255 범위로 나온다 — 그대로 clip 해서 써야 한다.
- CPU 기준 4032×3024 이미지에서 약 5초. TV+받침대 제거, 벽 자연 복원을 육안 확인함
  (Gemini 검증 결과와 비슷하거나 더 깨끗함 — 콘센트 흔적 정도만 옅게 남음).
"""
import io
from pathlib import Path

import pytest
from PIL import Image

from app.ai import build_provider
from app.ai.base import ProviderNotConfigured
from app.ai.lama import LamaInpaintProvider
from app.config import Settings

_SERVER_DIR = Path(__file__).resolve().parent.parent
_MODEL = _SERVER_DIR / "models" / "lama_fp32.onnx"
_PHOTO = _SERVER_DIR / "testdata" / "real_living_room.jpg"

REGION = {"type": "bbox", "rect": [0.3, 0.35, 0.35, 0.35]}


def test_missing_model_file_raises_provider_not_configured():
    with pytest.raises(ProviderNotConfigured):
        LamaInpaintProvider(Path("no/such/lama.onnx"))


def test_build_provider_lama_without_path_raises_clear_error():
    """모델 경로 미설정 시 조용히 mock 으로 대체하지 않고 명확히 실패해야 한다."""
    settings = Settings(ai_provider="lama", lama_model_path=None)
    with pytest.raises(ProviderNotConfigured):
        build_provider(settings)


def test_build_provider_lama_with_missing_file_raises_clear_error():
    settings = Settings(ai_provider="lama", lama_model_path=Path("no/such/lama.onnx"))
    with pytest.raises(ProviderNotConfigured):
        build_provider(settings)


@pytest.mark.skipif(not _MODEL.is_file(), reason="LaMa 모델 파일 없음 (docs/workstreams 참고)")
def test_remove_object_inpaints_region_at_original_resolution():
    provider = LamaInpaintProvider(_MODEL)
    image_bytes = _PHOTO.read_bytes()
    with Image.open(_PHOTO) as im:
        orig_w, orig_h = im.size

    result = provider.remove_object(
        image_bytes=image_bytes, region=REGION, object_type="tv", prompt="",
    )

    out = Image.open(io.BytesIO(result.image_bytes))
    assert out.size == (orig_w, orig_h)  # 원본 해상도로 복원됐는지
    assert result.changed_region == {"type": "bbox", "rect": REGION["rect"]}

    # 마스크 영역 안쪽 픽셀이 실제로 바뀌었는지(값을 베껴 붙인 게 아닌지) 대략 확인.
    orig = Image.open(_PHOTO).convert("RGB")
    cx = int((REGION["rect"][0] + REGION["rect"][2] / 2) * orig_w)
    cy = int((REGION["rect"][1] + REGION["rect"][3] / 2) * orig_h)
    assert out.convert("RGB").getpixel((cx, cy)) != orig.getpixel((cx, cy))
