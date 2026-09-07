"""실제 MobileSAM ONNX 파일로 encoder/decoder 계약을 검증한다.

모델 파일(수십MB)은 저장소에 커밋하지 않으므로, `server/models/`에 없으면 이 테스트
전체를 건너뛴다. 로컬에서 `docs/workstreams/interior-mobilesam.md`의 절차대로 받아두면
실행된다 — CI/다른 개발자 환경에 모델이 없어도 나머지 테스트에는 영향이 없다.

여기서 확인하는 계약(모두 실제 파일로 직접 검증한 내용, 문서만 보고 가정하지 않음):
- encoder 입력은 `input_image` 이름의 (H, W, 3) float32, 0~255 원본 픽셀(정규화 없음).
- decoder 는 실제 점 1개만 보내도 동작하지만, 더미 패딩 점(0,0)/레이블 -1 을 같이
  보내면(`MobileSamSegmenter.segment_point`가 하는 방식) 사물의 받침대/케이블처럼 딸린
  부분까지 포함해 더 완전한 마스크를 준다.
- `orig_im_size` 에 리사이즈 전 원본 크기를 주면 마스크가 이미 원본 해상도로 나온다.
"""
import io
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from app.ai.mobilesam import MobileSamSegmenter

_SERVER_DIR = Path(__file__).resolve().parent.parent
_ENCODER = _SERVER_DIR / "models" / "mobilesam.encoder.onnx"
_DECODER = _SERVER_DIR / "models" / "mobilesam.decoder.onnx"
_PHOTO = _SERVER_DIR / "testdata" / "real_living_room.jpg"

pytestmark = pytest.mark.skipif(
    not (_ENCODER.is_file() and _DECODER.is_file()),
    reason="MobileSAM 모델 파일 없음 (docs/workstreams/interior-mobilesam.md 참고)",
)


def test_segment_point_returns_full_resolution_mask_around_click():
    segmenter = MobileSamSegmenter(_ENCODER, _DECODER)
    image_bytes = _PHOTO.read_bytes()
    with Image.open(_PHOTO) as im:
        orig_w, orig_h = im.size

    # 실측 TV 근처로 클릭했다고 가정(정확한 실제 TV 위치는 사진마다 다르므로
    # "그럴듯한 크기의 사물 하나를 잡았는가"만 확인한다).
    result = segmenter.segment_point(image_bytes, x_norm=0.49, y_norm=0.53)

    mask = Image.open(io.BytesIO(result.mask_png)).convert("L")
    assert mask.size == (orig_w, orig_h)  # decoder 가 원본 해상도로 직접 돌려준다

    total_px = orig_w * orig_h
    nonzero_px = int(np.count_nonzero(np.asarray(mask)))
    # 사물 하나 크기: 전체 화면도 아니고(과분할 실패) 너무 작지도 않음(빈 결과 실패).
    assert 0.005 * total_px < nonzero_px < 0.5 * total_px
    assert result.score > 0.5  # decoder 의 IoU 예측치가 자신 있는 편


def test_segment_point_is_available_reports_true_with_real_files():
    from app.ai.mobilesam import is_available

    assert is_available(_ENCODER, _DECODER) is True
