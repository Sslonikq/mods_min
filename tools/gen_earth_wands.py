#!/usr/bin/env python3
"""Текстуры и модели палочек Земли 4.11 часть B (16x16 PNG без PIL, через gen_materials.C). Запуск: python tools/gen_earth_wands.py
Взрывная палочка (explosion_wand) остаётся со старой текстурой."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_materials import C, TEX, MODEL  # noqa: E402


def base(c, wood=0x6b4a2b, wood2=0x8a6238):
    c.line(2, 14, 10, 6, wood); c.line(3, 14, 11, 6, wood2)
    c.line(5, 11, 6, 10, 0xf2c94c); c.line(6, 11, 7, 10, 0xf2c94c)   # золотая оправа


def light(c):
    c.disc(12, 3, 2.4, (0xe0a020, 0xffe060, 0xffffe0))
    for (x, y) in ((12, 0), (12, 6), (9, 3), (15, 3)): c.set(x, y, 0xfff4a0)


def frost(c):
    for (x, y, k) in ((12, 0, 0xe8fcff), (12, 1, 0xbfefff), (11, 1, 0x80d0f0), (13, 1, 0xbfefff), (11, 2, 0x80d0f0), (12, 2, 0xe8fcff), (13, 2, 0x60b0e0),
                      (10, 3, 0x80d0f0), (11, 3, 0xbfefff), (12, 3, 0xffffff), (13, 3, 0xbfefff), (14, 3, 0x60b0e0), (11, 4, 0x60b0e0), (12, 4, 0xbfefff), (13, 4, 0x4890c8),
                      (12, 5, 0x80d0f0)):
        c.set(x, y, k)


def growth(c):
    c.line(10, 5, 12, 3, 0x3a8a2a)
    for (x, y, k) in ((12, 2, 0x58c040), (13, 2, 0x58c040), (13, 1, 0x80e060), (14, 1, 0x58c040), (11, 2, 0x3a8a2a), (10, 2, 0x58c040), (10, 1, 0x80e060), (9, 2, 0x3a8a2a),
                      (12, 3, 0x80e060), (13, 3, 0x3a8a2a), (11, 4, 0x58c040)):
        c.set(x, y, k)


def gather(c):
    for (x, y, k) in ((9, 1, 0xd03030), (9, 2, 0xd03030), (9, 3, 0xb02020), (10, 4, 0xb02020), (11, 5, 0x888890), (12, 5, 0xc0c0c8), (13, 4, 0xc0c0c8), (14, 3, 0xe0e0e8),
                      (14, 2, 0xe0e0e8), (14, 1, 0xc0c0c8), (13, 1, 0xff6060), (10, 1, 0xff6060), (12, 4, 0x70e0ff)):
        c.set(x, y, k)


def ret(c):
    c.disc(12, 3, 2.6, (0x7a3fc8, 0xc060e0, 0xffd0ff))
    for (x, y) in ((11, 2), (12, 2), (13, 3), (12, 4), (11, 3)): c.set(x, y, 0xffffff)


def signal(c):
    c.disc(12, 4, 2.2, (0xa01818, 0xe83030, 0xffa090))
    for (x, y, k) in ((12, 0, 0xffe060), (12, 1, 0xffa030), (11, 0, 0xff6030), (13, 0, 0xff6030), (14, 1, 0xffe060), (10, 1, 0xffe060)):
        c.set(x, y, k)


def digger(c):
    for (x, y, k) in ((9, 4, 0x40c8d8), (10, 3, 0x40c8d8), (11, 2, 0x70f0f0), (12, 1, 0x70f0f0), (13, 1, 0x70f0f0), (14, 2, 0x40c8d8), (14, 3, 0x2890a0),
                      (10, 4, 0x2890a0), (11, 3, 0x40c8d8), (12, 2, 0x70f0f0), (13, 2, 0x40c8d8), (12, 3, 0x2890a0)):
        c.set(x, y, k)
    c.line(10, 5, 12, 3, 0x6b4a2b)


def spikes(c):
    for (x, y, k) in ((12, 0, 0xf0d8ff), (12, 1, 0xd8a8f8), (11, 2, 0xb070e0), (12, 2, 0xe0c0ff), (13, 2, 0x9050c8), (10, 3, 0xb070e0), (11, 3, 0xd8a8f8),
                      (12, 3, 0xf0d8ff), (13, 3, 0xd8a8f8), (14, 3, 0x7a3fb0), (9, 4, 0x9050c8), (11, 4, 0xb070e0), (13, 4, 0x9050c8), (15, 4, 0x7a3fb0)):
        c.set(x, y, k)


def stone_skin(c):
    for y in range(0, 6):
        for x in range(9, 15):
            c.set(x, y, 0x8a8a90 if (x + y) % 2 == 0 else 0x6c6c72)
    for (x, y) in ((10, 1), (12, 2), (13, 4), (11, 4)): c.set(x, y, 0xb0b0b6)
    for (x, y) in ((9, 5), (14, 0), (10, 3)): c.set(x, y, 0x4a4a50)


def wind(c):
    for (x, y, k) in ((9, 1, 0xe8f8ff), (10, 1, 0xe8f8ff), (11, 1, 0xbfe4f0), (12, 1, 0xbfe4f0), (13, 1, 0xe8f8ff), (14, 2, 0xe8f8ff), (14, 3, 0xbfe4f0), (13, 4, 0xbfe4f0),
                      (11, 3, 0xbfe4f0), (12, 3, 0xe8f8ff), (13, 3, 0xe8f8ff), (10, 4, 0x90c8e0), (9, 5, 0x90c8e0), (12, 0, 0x90c8e0)):
        c.set(x, y, k)


def vine(c):
    c.line(10, 5, 14, 1, 0x2f7a22); c.line(9, 4, 13, 0, 0x3a8a2a)
    for (x, y, k) in ((12, 0, 0x58c040), (14, 3, 0x58c040), (11, 3, 0x58c040), (15, 2, 0x80e060), (10, 2, 0x58c040), (13, 4, 0x80e060), (14, 0, 0xff9bd0)):
        c.set(x, y, k)


def pull(c):
    c.disc(12, 3, 2.4, (0x2a1850, 0x5a2aa0, 0xb080ff))
    for (x, y, k) in ((12, 3, 0x10081c), (11, 2, 0xe0d0ff), (14, 1, 0xe0d0ff), (10, 5, 0xb080ff), (15, 4, 0xb080ff)):
        c.set(x, y, k)


def decoy(c):
    for (x, y, k) in ((12, 0, 0xc0f0ff), (11, 1, 0x90d8f0), (12, 1, 0xc0f0ff), (13, 1, 0x90d8f0), (12, 2, 0x70c0e8), (11, 2, 0x90d8f0), (13, 2, 0x90d8f0),
                      (12, 3, 0xc0f0ff), (11, 4, 0x70c0e8), (13, 4, 0x70c0e8), (10, 3, 0x70c0e8), (14, 3, 0x70c0e8), (15, 1, 0xffffff), (9, 1, 0xffffff)):
        c.set(x, y, k)


def spark(c):
    for (x, y, k) in ((12, 1, 0xfff0a0), (11, 2, 0xffc040), (12, 2, 0xffffff), (13, 2, 0xffc040), (12, 3, 0xffc040), (12, 0, 0xff8020), (10, 2, 0xff8020),
                      (14, 2, 0xff8020), (12, 4, 0xff8020), (15, 4, 0xffe060), (10, 5, 0xffe060), (14, 0, 0xffe060)):
        c.set(x, y, k)


def heat(c):
    c.disc(12, 3, 2.5, (0xc03010, 0xff8020, 0xffe070))
    for (x, y, k) in ((12, 3, 0xfffff0), (12, 0, 0xff6020), (13, 0, 0xffa030), (9, 3, 0xff6020), (15, 2, 0xffa030)):
        c.set(x, y, k)


WANDS = {"light_wand": light, "frost_wand": frost, "growth_wand": growth, "gather_wand": gather,
         "return_wand": ret, "signal_wand": signal, "digger_wand": digger,
         "crystal_spikes_wand": spikes, "stone_skin_wand": stone_skin, "wind_wand": wind, "vine_wand": vine, "pull_wand": pull,
         "decoy_wand": decoy, "spark_wand": spark, "heat_wand": heat}

if __name__ == "__main__":
    for n, f in WANDS.items():
        c = C(); base(c); f(c); c.outline(0x1c1428)
        (TEX / f"{n}.png").write_bytes(c.png())
        (MODEL / f"{n}.json").write_text('{\n  "parent": "minecraft:item/handheld",\n  "textures": {\n    "layer0": "explosionwand:item/%s"\n  }\n}\n' % n, encoding="utf-8")
    print("ok", len(WANDS))
