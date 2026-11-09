#!/usr/bin/env python3
"""Текстура Печати Трона (16x16 RGBA): золотая печать с чёрным солнцем затмения в центре и фиолетовым ореолом. Чистый Python, без PIL.
    python tools/assets/gen_throne_seal.py            # записать resources/.../textures/item/throne_seal.png
    python tools/assets/gen_throne_seal.py --preview  # + build/throne_seal_x16.png
"""
import struct, sys, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "resources/assets/explosionwand/textures/item/throne_seal.png"
PAL = {".": (0, 0, 0, 0), "g": (255, 214, 90, 255), "h": (232, 168, 40, 255), "d": (150, 98, 14, 255), "k": (8, 2, 16, 255),
       "p": (150, 96, 224, 255), "l": (204, 160, 255, 255), "o": (60, 30, 100, 255)}
ROWS = ["................",
        "......gggg......",
        "....ghhggghg....",
        "...ghdddddhhg...",
        "..ghdooooopdhg..",
        "..ghdokkkkodhg..",
        ".ghdokkllkkodhd.",
        ".ghpokklpkkopdd.",
        ".ghpokkpkkkopdd.",
        ".ghdokkkkkkodhd.",
        "..ghdokkkkodhd..",
        "..ghdooooopdhd..",
        "...hdddddddhd...",
        "....hhddddhd....",
        "......dddd......",
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
        p = ROOT / "build" / "throne_seal_x16.png"; p.parent.mkdir(exist_ok=True); p.write_bytes(png(ROWS, 16)); print("превью", p.relative_to(ROOT))
