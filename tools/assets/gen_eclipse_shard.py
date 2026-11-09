#!/usr/bin/env python3
"""Текстура Осколка Печати: Затмение (16x16 RGBA): тёмный фиолетовый осколок с золотым кольцом короны. Чистый Python, без PIL.
    python tools/assets/gen_eclipse_shard.py            # записать resources/.../textures/item/eclipse_seal_shard.png
    python tools/assets/gen_eclipse_shard.py --preview  # + build/eclipse_seal_shard_x16.png (увеличение для просмотра)
"""
import struct, sys, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "resources/assets/explosionwand/textures/item/eclipse_seal_shard.png"
PAL = {".": (0, 0, 0, 0), "o": (24, 8, 44, 255), "d": (70, 34, 130, 255), "m": (150, 96, 224, 255), "l": (204, 160, 255, 255),
       "g": (255, 214, 90, 255), "h": (232, 168, 40, 255), "k": (8, 2, 16, 255), "w": (255, 244, 200, 255)}
ROWS = ["................",
        "........o.......",
        ".......odo......",
        ".......dml.m....",
        "....o.hggh.o....",
        ".....hgkkkgh....",
        "....hgkkkkgh.m..",
        "....gkkkkkkg.l..",
        "....gkkkkkkwg...",
        "....hgkkkkgh....",
        ".....hgkkgh.....",
        "......hggh......",
        ".....odmdo......",
        "......odo.......",
        ".......o........",
        "................"]


def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)


def png(rows, scale=1):
    w = len(rows[0]) * scale
    raw = b""
    for r in rows:
        line = b"".join(bytes(PAL[c]) * scale for c in r)
        raw += (b"\x00" + line) * scale
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, len(rows) * scale, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    assert len(ROWS) == 16 and all(len(r) == 16 for r in ROWS)
    OUT.write_bytes(png(ROWS)); print("записана", OUT.relative_to(ROOT))
    if "--preview" in sys.argv:
        p = ROOT / "build" / "eclipse_seal_shard_x16.png"; p.parent.mkdir(exist_ok=True); p.write_bytes(png(ROWS, 16)); print("превью", p.relative_to(ROOT))
