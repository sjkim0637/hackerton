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
    doc.layers.add("통신단자함")
    doc.layers.add("SYM")
    sheet = doc.blocks.new("XR_SHEET")
    sheet.add_line((0, 0), (42_000, 0))
    panel = doc.blocks.new("100-57")
    panel.add_lwpolyline([(0, 0), (300, 0), (300, 300), (0, 300)], close=True)
    camera = doc.blocks.new("EFCL")
    camera.add_circle((0, 0), 100)
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
    msp.add_blockref("100-57", (6_000, -101_000), dxfattribs={"layer": "통신단자함"})
    msp.add_blockref("EFCL", (10_000, -99_000), dxfattribs={"layer": "SYM"})
    msp.add_blockref("100-57", (48_000, -101_000), dxfattribs={"layer": "통신단자함"})
    return doc


@pytest.fixture
def sample_dxf_bytes(sample_doc) -> bytes:
    stream = BytesIO()
    sample_doc.write(stream, fmt="bin")
    return stream.getvalue()


@pytest.fixture
def architecture_doc():
    doc = ezdxf.new("R2018")
    doc.header["$INSUNITS"] = 4
    doc.layers.add("arch")
    msp = doc.modelspace()
    msp.add_line((1_000, 1_000), (2_000, 1_000), dxfattribs={"layer": "arch"})
    msp.add_line((2_000, 1_000), (1_000, 1_000), dxfattribs={"layer": "arch"})
    msp.add_line((2_000, 2_000), (2_010, 2_000), dxfattribs={"layer": "arch"})
    msp.add_line((-100, 3_000), (200, 3_000), dxfattribs={"layer": "arch"})
    msp.add_line((43_000, 1_000), (44_000, 1_000), dxfattribs={"layer": "arch"})
    msp.add_lwpolyline(
        [(3_000, 1_000), (3_000, 1_500), (3_500, 1_500)],
        dxfattribs={"layer": "arch"},
    )
    return doc


@pytest.fixture
def architecture_dxf_bytes(architecture_doc) -> bytes:
    stream = BytesIO()
    architecture_doc.write(stream, fmt="bin")
    return stream.getvalue()
