"""임의 이미지 + 임의 bbox 로 서버 전체 흐름을 한 번 돌리는 one-off 스크립트.

e2e_check.py 와 같은 흐름(세션 생성 → 키프레임 업로드 → remove-object → job 폴링 →
결과 이미지 다운로드/검증)을, 합성 이미지 대신 지정한 사진과 bbox 로 실행한다.

예 (server/ 에서, venv 활성화 상태):
    python scripts/e2e_check_custom.py \
        --image testdata/real_living_room.jpg \
        --bbox 0.34,0.39,0.30,0.28 \
        --ai-provider external --ai-api-key <GEMINI_KEY>

    # mock 으로 흐름만 확인:
    python scripts/e2e_check_custom.py --image testdata/real_living_room.jpg --bbox 0.34,0.39,0.30,0.28

--bbox 는 "x,y,width,height" 형식이며 각 값은 이미지 대비 0~1 비율이다.
성공 시 exit 0, 실패 시 exit 1. 결과 이미지는 scripts/_out/result_<job>.jpg 에 저장된다.
"""
from __future__ import annotations

import argparse
from pathlib import Path

# e2e_check.py 의 공용 로직을 그대로 재사용한다.
from e2e_check import (
    add_server_args,
    ensure_server,
    parse_bbox,
    print_summary,
    run_flow,
    stop_server,
)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="임의 이미지 + bbox 로 Interior AR 서버 전체 흐름 실행",
    )
    parser.add_argument("--image", required=True, help="이미지 경로 (JPEG)")
    parser.add_argument("--bbox", required=True, type=parse_bbox,
                        help="제거할 사물의 위치 'x,y,width,height' (각 0~1 비율)")
    parser.add_argument("--object-type", default="tv", help="사물 종류 (기본 tv)")
    add_server_args(parser)
    args = parser.parse_args()

    image_path = Path(args.image)
    if not image_path.exists():
        raise SystemExit(f"이미지를 찾을 수 없습니다: {image_path}")

    base_url, proc = ensure_server(args)
    try:
        ok = run_flow(
            base_url,
            image_path,
            Path(args.out_dir),
            list(args.bbox),
            object_type=args.object_type,
        )
    finally:
        stop_server(proc, args.keep_server)
    return print_summary(ok)


if __name__ == "__main__":
    raise SystemExit(main())
