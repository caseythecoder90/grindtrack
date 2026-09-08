#!/usr/bin/env python3
"""Renders the PWA icon set from the same geometry as public/favicon.svg.

A PWA manifest needs raster icons, and iOS needs one too, but the source mark is
an SVG and the repo carries no image tooling. Rather than commit opaque binaries
nobody can regenerate, this draws them: rounded rectangles with 4x supersampling,
encoded as PNG through zlib and struct alone. No dependencies.

    python3 tools/make_icons.py

Keep the geometry below in step with public/favicon.svg — that file is still what
browsers use for the tab, and the two should not drift.
"""

import struct
import zlib

NAVY = (0x0D, 0x1B, 0x2A)
MAGENTA = (0xFF, 0x2E, 0x93)
# The mark on a 32-unit grid: x, y, fill. Mirrors favicon.svg cell for cell.
CELLS = [
    (4, 4, (0x16, 0x30, 0x4A)), (13, 4, (0x1D, 0x5C, 0x38)), (22, 4, (0x2A, 0x8A, 0x4D)),
    (4, 13, (0x2A, 0x8A, 0x4D)), (13, 13, (0x3F, 0xBF, 0x5F)), (22, 13, (0x16, 0x30, 0x4A)),
    (4, 22, (0x3F, 0xBF, 0x5F)), (13, 22, (0x8F, 0xE8, 0xA8)), (22, 22, (0x3F, 0xBF, 0x5F)),
]
TODAY = (22, 22)  # gets the magenta ring, as in the SVG
CELL, RADIUS, GRID = 7.0, 1.5, 32.0
SS = 4  # supersampling factor; 4x is enough to hide the stair-stepping at 192px


def in_rounded_rect(px, py, x, y, w, h, r):
    if not (x <= px <= x + w and y <= py <= y + h):
        return False
    cx = min(max(px, x + r), x + w - r)
    cy = min(max(py, y + r), y + h - r)
    return (px - cx) ** 2 + (py - cy) ** 2 <= r * r


def render(size, *, scale=1.0):
    """One icon as a list of RGB rows. `scale` shrinks the mark for the maskable safe area.

    Deliberately square: every platform applies its own mask (iOS a squircle, Android
    whatever the launcher uses), so rounding here would show as a navy fringe inside it.
    """
    hi = size * SS
    acc = [[[0, 0, 0] for _ in range(size)] for _ in range(size)]
    inset = (1.0 - scale) * GRID / 2.0
    for sy in range(hi):
        u = (sy + 0.5) / hi * GRID
        row = acc[sy // SS]
        for sx in range(hi):
            v = (sx + 0.5) / hi * GRID
            colour = NAVY
            gv = (v - inset) / scale
            gu = (u - inset) / scale
            for cx, cy, fill in CELLS:
                ring = (cx, cy) == TODAY
                if ring and in_rounded_rect(gv, gu, cx - 0.8, cy - 0.8, CELL + 1.6, CELL + 1.6,
                                            RADIUS + 0.8):
                    colour = MAGENTA
                if in_rounded_rect(gv, gu, cx, cy, CELL, CELL, RADIUS):
                    colour = fill
            px = row[sx // SS]
            px[0] += colour[0]
            px[1] += colour[1]
            px[2] += colour[2]
    n = SS * SS
    return [bytes(b for px in row for b in (px[0] // n, px[1] // n, px[2] // n)) for row in acc]


def write_png(path, rows):
    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    size = len(rows)
    raw = b"".join(b"\x00" + r for r in rows)  # filter byte 0 (None) per scanline
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)
    print(f"wrote {path} ({size}x{size}, {len(png)} bytes)")


if __name__ == "__main__":
    write_png("public/icon-192.png", render(192))
    write_png("public/icon-512.png", render(512))
    # Maskable: Android crops to a circle inscribed in the middle 80%, so the mark
    # shrinks and the navy runs to the edges. Square corners for the same reason.
    write_png("public/icon-maskable-512.png", render(512, scale=0.62))
    # iOS does not round for you on older versions and never reads the manifest.
    write_png("public/apple-touch-icon.png", render(180))
