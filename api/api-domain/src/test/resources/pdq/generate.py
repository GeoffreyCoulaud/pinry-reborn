"""Writes random RGB images beside this file, and hashes.csv with their hashes from Meta's C++ reference.

Run once, in a virtual environment holding `pdqhash` (PyPI, which wraps `facebook/ThreatExchange`'s `pdq/cpp`):
    python3 -m venv venv && venv/bin/pip install pdqhash==0.2.8 numpy && venv/bin/python generate.py
"""

import pathlib

import numpy
import pdqhash

HERE = pathlib.Path(__file__).parent

generator = numpy.random.default_rng(seed=20261005)


def noise(width, height):
    return generator.integers(0, 256, size=(height, width, 3), dtype=numpy.uint8)


# Blocks of ten pixels in a narrow range of greys, so that the quality is under its cap of 100.
def blocks(width, height):
    small = generator.integers(96, 160, size=(height // 10, width // 10, 3), dtype=numpy.uint8)
    return numpy.ascontiguousarray(small.repeat(10, axis=0).repeat(10, axis=1))


# 64 by 64 skips the blur; 64 by 61 blurs over one pixel; 260 by 130 over three along a row, two along a column.
IMAGES = {"noise-64x64": noise(64, 64), "noise-64x61": noise(64, 61), "noise-260x130": noise(260, 130),
          "blocks-120x90": blocks(120, 90)}

lines = []
for name, image in IMAGES.items():
    height, width, _ = image.shape
    (HERE / f"{name}.ppm").write_bytes(f"P6\n{width} {height}\n255\n".encode() + image.tobytes())
    bits, quality = pdqhash.compute(image)
    # The vector holds the most significant bit first, as Meta's hexadecimal does.
    hexadecimal = f"{int(''.join(str(bit) for bit in bits), 2):064x}"
    lines.append(f"{name}.ppm,{hexadecimal},{quality}\n")
(HERE / "hashes.csv").write_text("".join(lines))
