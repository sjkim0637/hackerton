from __future__ import annotations

from io import BytesIO

import ezdxf
import pytest

UNIT_TYPES = ("84A", "84B", "84C", "84D", "120A", "144P", "155P")


@pytest.fixture
def sample_doc():
    doc = ezdxf.new("R2018")
    doc.header["$INSUNITS"] = 4
    doc.layers.add("e-wire")
    doc.layers.add("e-wire3s")
    sheet = doc.blocks.new("XR_SHEET")
    sheet.add_line((0, 0), (42_000, 0))
    msp = doc.modelspace()
    for index, unit_type in enumerate(UNIT_TYPES):
        origin_x = 1_235 + index * 42_000
        msp.add_blockref("XR_SHEET", (origin_x, -115_494.354))
        area, variant = unit_type[:-1], unit_type[-1]
        msp.add_text(
            f"{area}㎡{variant} 단위세대 홈네트워크설비 평면도(확장형)",
            dxfattribs={"insert": (origin_x + 19_000, -112_346), "layer": "0"},
        )

    msp.add_line(
        (5_000, -100_000),
        (9_000, -100_000),
        dxfattribs={"layer": "e-wire"},
    )
    msp.add_lwpolyline(
        [(9_000, -100_000), (9_000, -96_000), (12_000, -96_000)],
        dxfattribs={"layer": "e-wire3s"},
    )
    msp.add_line(
        (47_000, -100_000),
        (51_000, -100_000),
        dxfattribs={"layer": "e-wire"},
    )
    return doc


@pytest.fixture
def sample_dxf_bytes(sample_doc) -> bytes:
    stream = BytesIO()
    sample_doc.write(stream, fmt="bin")
    return stream.getvalue()
