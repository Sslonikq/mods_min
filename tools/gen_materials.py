#!/usr/bin/env python3
"""Текстуры материалов 4.11 (16x16 PNG без PIL: zlib+struct). Запуск: python tools/gen_materials.py"""
import math, struct, zlib
from pathlib import Path

TEX = Path(__file__).resolve().parent.parent / "resources/assets/explosionwand/textures/item"
MODEL = Path(__file__).resolve().parent.parent / "resources/assets/explosionwand/models/item"


def rgb(h): return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


class C:
    def __init__(s): s.p = {}
    def set(s, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16: s.p[(x, y)] = rgb(c) if isinstance(c, int) else c
    def line(s, x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1): s.set(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), c)
    def disc(s, cx, cy, r, cols):
        """шар: cols = (тёмный, средний, светлый); свет сверху-слева"""
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - cx, y - cy)
                if d <= r:
                    l = (x - cx) * -0.5 + (y - cy) * -0.6
                    s.set(x, y, cols[2] if l > r * 0.45 else cols[0] if l < -r * 0.35 else cols[1])
    def outline(s, c):
        add = {}
        for (x, y) in list(s.p):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in s.p and 0 <= x + dx < 16 and 0 <= y + dy < 16: add[(x + dx, y + dy)] = rgb(c)
        s.p.update(add)
    def png(s):
        raw = b""
        for y in range(16):
            raw += b"\x00" + b"".join(bytes(s.p.get((x, y), (0, 0, 0, 0))) for x in range(16))
        def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
        return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def magic_rod():
    c = C(); c.line(3, 13, 11, 5, 0x6b4a2b); c.line(4, 13, 12, 5, 0x8a6238)
    c.line(5, 11, 6, 10, 0xf2c94c); c.line(6, 11, 7, 10, 0xf2c94c)
    c.disc(12, 3, 2.3, (0x7a3fc8, 0xb06cf0, 0xe4c8ff)); c.outline(0x2a1740); return c


def core(cols, spark=()):
    c = C(); c.disc(7.5, 7.5, 5.5, cols)
    for (x, y, k) in spark: c.set(x, y, k)
    c.outline(0x1c1a24); return c


def simple_core(): return core((0x5a5a6e, 0x8a8aa0, 0xd8d8ee), [(5, 7, 0xd02020), (9, 5, 0x2a52d0), (8, 10, 0xd02020), (6, 5, 0x2a52d0)])
def crystal_core(): return core((0x6a2a9a, 0xb060e0, 0xf0d0ff), [(7, 7, 0xffffff), (8, 8, 0xe8b0ff)])
def wind_core():
    c = core((0x6aa0b0, 0xa8e4ee, 0xf4ffff))
    for (x, y) in ((4, 8), (5, 9), (6, 9), (7, 9), (8, 8), (9, 7), (9, 6), (8, 5), (7, 5)): c.set(x, y, 0xffffff)
    return c
def fire_core(): return core((0xa02010, 0xf06418, 0xffe060), [(7, 7, 0xffffd0), (8, 7, 0xffffd0), (7, 8, 0xfff090)])


def rift_dust():
    c = C()
    for (x, y, k) in ((6, 9, 0x7a3fc8), (7, 9, 0xb06cf0), (8, 9, 0x7a3fc8), (5, 10, 0x7a3fc8), (6, 10, 0xb06cf0), (7, 10, 0xe4c8ff), (8, 10, 0xb06cf0), (9, 10, 0x7a3fc8),
                      (4, 11, 0x5a2a98), (5, 11, 0x7a3fc8), (6, 11, 0xb06cf0), (7, 11, 0xb06cf0), (8, 11, 0xb06cf0), (9, 11, 0x7a3fc8), (10, 11, 0x5a2a98),
                      (7, 7, 0xe4c8ff), (5, 6, 0xb06cf0), (10, 7, 0xb06cf0), (8, 5, 0x7a3fc8), (6, 4, 0xe4c8ff)):
        c.set(x, y, k)
    c.outline(0x2a1740); return c


def echo_crystal():
    c = C()
    for y, (a, b) in enumerate([(7, 8), (6, 9), (6, 9), (5, 10), (5, 10), (5, 10), (6, 9), (6, 9), (7, 8), (7, 8)], start=2):
        for x in range(a, b + 1): c.set(x, y, 0x5aa8d8 if x > (a + b) / 2 else 0x9ad8f4)
    for y in range(3, 10): c.set(7, y, 0xe8fbff)
    c.line(5, 12, 10, 12, 0x3a78b0); c.line(6, 13, 9, 13, 0x3a78b0); c.outline(0x16304a); return c


def rift_marrow():
    c = C(); c.disc(7.5, 7.5, 6, (0x6a1a7a, 0xc040c0, 0xff90f0))
    c.line(7, 2, 7, 13, 0xff90f0); c.line(2, 7, 13, 7, 0xff90f0); c.disc(7.5, 7.5, 2, (0xe8c0ff, 0xffffff, 0xffffff))
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)): c.set(x, y, 0xffd0ff)
    c.outline(0x28082e); return c


def sleep_thread():
    c = C()
    for x in range(1, 15): c.set(x, 8 + round(2.2 * math.sin(x * 0.7)), 0x3ac8c0 if x % 3 else 0xa8f4ee)
    for x in range(2, 14): c.set(x, 6 + round(2.2 * math.sin(x * 0.7 + 1.5)), 0x2a9890)
    c.outline(0x0c3a40); return c


def teal_stitch():
    c = C()
    for i, x in enumerate(range(2, 14, 2)):
        c.line(x, 5, x + 2, 11, 0x20b8b0); c.line(x + 2, 5, x, 11, 0x78f0e8)
    c.line(1, 8, 14, 8, 0x187a78); c.set(14, 8, 0xe0ffff); c.outline(0x0a3438); return c


def sleeper_tear():
    c = C()
    for y in range(2, 14):
        w = 0.4 + (y - 2) * 0.5 if y < 7 else max(0.0, 2.9 - (y - 7) * 0.2) if False else None
        r = (y - 2) * 0.55 if y < 8 else max(0, math.sqrt(max(0, 9 - (y - 10) ** 2)))
        for x in range(16):
            if abs(x - 7.5) <= r: c.set(x, y, 0xb8e8ff if x < 8 else 0x80c0f0)
    c.set(6, 9, 0xffffff); c.set(6, 10, 0xffffff); c.set(7, 4, 0xe8f8ff); c.outline(0x1c3a60); return c


def ash_coal():
    c = C(); c.disc(7.5, 8, 5.5, (0x1c1c1e, 0x3a3a3e, 0x5a5a60))
    for (x, y, k) in ((6, 6, 0xf08020), (9, 8, 0xe05010), (7, 10, 0xf8c040), (10, 6, 0xe05010), (5, 9, 0xf08020)): c.set(x, y, k)
    c.outline(0x0a0a0a); return c


def smoldering_feather():
    c = C(); c.line(3, 13, 12, 3, 0x7a3a1a)
    for i in range(0, 9):
        x, y = 3 + i, 13 - i
        c.set(x + 1, y, 0xe05818 if i % 2 else 0xf8a030); c.set(x, y - 1, 0xe05818 if i % 2 else 0xf8a030); c.set(x + 2, y, 0x3a3a3e if i < 5 else 0xf8c040)
    c.set(12, 2, 0xffe080); c.outline(0x2a1006); return c


def phoenix_spark():
    c = C()
    for i in range(1, 7):
        for d in ((i, 0), (-i, 0), (0, i), (0, -i)):
            if i < 7 - 0: c.set(int(7.5 + d[0] - (0.5 if d[0] > 0 or d[1] > 0 else -0.5) * 0) , int(7.5 + d[1]), 0xf8a030 if i > 3 else 0xffe060)
    c.disc(7.5, 7.5, 2.5, (0xffd040, 0xfff0a0, 0xffffff))
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)): c.set(x, y, 0xf06418)
    c.outline(0x4a1c04); return c


ITEMS = {"magic_rod": magic_rod, "simple_core": simple_core, "crystal_core": crystal_core, "wind_core": wind_core, "fire_core": fire_core,
         "rift_dust": rift_dust, "echo_crystal": echo_crystal, "rift_marrow": rift_marrow,
         "sleep_thread": sleep_thread, "teal_stitch": teal_stitch, "sleeper_tear": sleeper_tear,
         "ash_coal": ash_coal, "smoldering_feather": smoldering_feather, "phoenix_spark": phoenix_spark}

if __name__ == "__main__":
    for n, f in ITEMS.items():
        (TEX / f"{n}.png").write_bytes(f().png())
        (MODEL / f"{n}.json").write_text('{\n  "parent": "minecraft:item/generated",\n  "textures": {\n    "layer0": "explosionwand:item/%s"\n  }\n}\n' % n, encoding="utf-8")
    print("ok", len(ITEMS))
