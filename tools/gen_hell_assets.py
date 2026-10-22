#!/usr/bin/env python3
"""Ассеты 4.13 без PIL (чистый zlib/struct): текстуры Печатного камня, Углей Феникса, занятого гнезда, проёма портала, Ключа Ада,
а также blockstates/модели блоков, лут-таблицы и тег кирки. Текстуры отполированы asset-engineer (0133)."""
import json, random, struct, zlib
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "resources"
A = RES / "assets" / "explosionwand"
D = RES / "data"


def png(path, px, alpha=False):
    w = len(px[0]); raw = b""
    for row in px:
        raw += b"\x00" + b"".join(bytes(c if alpha else c[:3]) for c in row)
    def chunk(t, d): c = struct.pack(">I", len(d)) + t + d; return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, len(px), 8, 6 if alpha else 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)


def noise(base, var, seed):
    r = random.Random(seed)
    return [[tuple(max(0, min(255, b + r.randint(-var, var))) for b in base) + (255,) for _ in range(16)] for _ in range(16)]


def clamp(v): return max(0, min(255, int(v)))


def shade(c, k): return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), c[3] if len(c) > 3 else 255)


def ring(px, cx, cy, r0, r1, col):
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if r0 <= d <= r1: px[y][x] = col


def disc(px, cx, cy, r, col):
    ring(px, cx, cy, 0, r, col)


def bricks(seed, base, mortar):
    """каменные кирпичи 8x8 в шахматном смещении, с подсветкой верхней/левой грани"""
    r = random.Random(seed); px = noise(base, 7, seed)
    for y in range(16):
        for x in range(16):
            row = y // 8; off = 4 if row else 0
            bx = (x + off) % 8
            if y % 8 == 7 or bx == 7: px[y][x] = mortar + (255,)
            elif y % 8 == 0 or bx == 0: px[y][x] = shade(px[y][x], 1.18)
            elif y % 8 == 6 or bx == 6: px[y][x] = shade(px[y][x], 0.85)
    return px


def seal_stone():
    # старый камень с круглой печатью-руной посередине
    px = bricks(1, (96, 88, 112), (44, 38, 58))
    ember, hot = (236, 110, 48, 255), (255, 190, 90, 255)
    disc(px, 7.5, 7.5, 5.6, (40, 34, 54, 255))
    ring(px, 7.5, 7.5, 4.6, 5.6, (170, 78, 42, 255))
    ring(px, 7.5, 7.5, 4.0, 4.6, ember)
    # внутри - ромб-руна (вертикаль и горизонталь креста с яркой серединой)
    for (x, y) in [(7, 4), (8, 4), (7, 11), (8, 11), (4, 7), (4, 8), (11, 7), (11, 8)]: px[y][x] = (150, 70, 40, 255)
    for (x, y) in [(7, 5), (8, 5), (6, 6), (9, 6), (5, 7), (10, 7), (5, 8), (10, 8), (6, 9), (9, 9), (7, 10), (8, 10)]: px[y][x] = ember
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]: px[y][x] = hot
    return px


def embers(lit_key=False):
    # остывшая зола с раскалёнными трещинами
    r = random.Random(2); px = noise((46, 28, 26), 9, 2)
    cracks = [(0, 4), (1, 4), (2, 5), (3, 5), (4, 6), (5, 6), (6, 7), (6, 8), (7, 9), (8, 9), (9, 10), (10, 10), (11, 11), (12, 11), (13, 12), (14, 12), (15, 13),
              (3, 0), (3, 1), (4, 2), (4, 3), (5, 3), (12, 0), (11, 1), (11, 2), (10, 3), (9, 4), (8, 4), (5, 12), (4, 13), (4, 14), (3, 15), (9, 11), (9, 12), (10, 13), (10, 14)]
    for (x, y) in cracks:
        px[y][x] = (232, 84, 24, 255) if (x + y) % 3 else (255, 160, 48, 255)
        for dx, dy in ((1, 0), (0, 1)):
            if 0 <= x + dx < 16 and 0 <= y + dy < 16 and (x + dx, y + dy) not in cracks and r.random() < .5: px[y + dy][x + dx] = (120, 42, 22, 255)
    for _ in range(18):
        px[r.randint(0, 15)][r.randint(0, 15)] = (80, 70, 68, 255)
    if lit_key:
        # в центре печать-замок: золотое кольцо и замочная скважина
        disc(px, 7.5, 7.5, 5.2, (36, 20, 20, 255))
        ring(px, 7.5, 7.5, 4.2, 5.2, (255, 214, 90, 255))
        ring(px, 7.5, 7.5, 3.5, 4.2, (200, 90, 30, 255))
        disc(px, 7.5, 6.5, 1.6, (255, 230, 120, 255))
        for y in (8, 9, 10): px[y][7] = px[y][8] = (255, 230, 120, 255)
    return px


FRAMES = 8


def portal():
    """полоса из 8 кадров 16x16: завихрения красно-оранжевого, полупрозрачные, цикл бесшовный"""
    import math
    out = []
    for f in range(FRAMES):
        ph = 2 * math.pi * f / FRAMES
        for y in range(16):
            row = []
            for x in range(16):
                a = 2 * math.pi * x / 16; b = 2 * math.pi * y / 16
                v = math.sin(a * 2 + ph) + math.sin(b * 2 - ph) + math.sin(a + b + ph) + math.sin(2 * (a - b) - 2 * ph) * .5
                t = (v + 3.5) / 7
                row.append((clamp(150 + 105 * t), clamp(20 + 140 * t * t), clamp(10 + 20 * t), clamp(150 + 80 * t)))
            out.append(row)
    return out


def key():
    # ключ по диагонали: круглая печать-головка сверху-справа, бородка снизу-слева
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    outl, gold, hi, lo, red = (74, 28, 14, 255), (255, 200, 64, 255), (255, 238, 150, 255), (190, 110, 30, 255), (226, 56, 28, 255)
    cx, cy = 11, 4
    ring(px, cx, cy, 0, 4.2, outl)
    ring(px, cx, cy, 0, 3.3, gold)
    ring(px, cx, cy, 0, 1.6, red)
    px[3][10] = hi; px[2][10] = hi; px[3][9] = hi
    px[6][13] = lo; px[5][14] = lo; px[6][12] = lo
    for i in range(0, 8):
        x, y = 9 - i, 6 + i
        if y > 14: break
        for (dx, dy, c) in ((1, 0, outl), (-1, 0, outl), (0, -1, outl), (0, 1, outl)):
            if px[y + dy][x + dx][3] == 0: px[y + dy][x + dx] = c
    for i in range(0, 8):
        x, y = 9 - i, 6 + i
        if y > 14: break
        px[y][x] = gold
        if x + 1 < 16: px[y][x + 1] = hi if i % 2 == 0 else gold
    for (x, y) in [(3, 11), (4, 12), (2, 11), (4, 11), (5, 13), (6, 14), (3, 13), (5, 12)]: px[y][x] = outl if (x, y) in ((2, 11), (3, 13), (5, 12)) else gold
    return px


def put(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    png(A / "textures" / "block" / "seal_stone.png", seal_stone())
    png(A / "textures" / "block" / "phoenix_embers.png", embers())
    png(A / "textures" / "block" / "hell_socket.png", embers(True))
    png(A / "textures" / "block" / "hell_portal.png", portal(), alpha=True)
    put(A / "textures" / "block" / "hell_portal.png.mcmeta", {"animation": {"frametime": 3, "interpolate": True}})
    png(A / "textures" / "item" / "hell_key.png", key(), alpha=True)
    for b in ("seal_stone", "phoenix_embers", "hell_socket", "hell_portal"):
        put(A / "blockstates" / f"{b}.json", {"variants": {"": {"model": f"explosionwand:block/{b}"}}})
        m = {"parent": "minecraft:block/cube_all", "textures": {"all": f"explosionwand:block/{b}"}}
        if b == "hell_portal": m["render_type"] = "minecraft:translucent"
        put(A / "models" / "block" / f"{b}.json", m)
    for b in ("seal_stone", "phoenix_embers"):
        put(A / "models" / "item" / f"{b}.json", {"parent": f"explosionwand:block/{b}"})
        put(D / "explosionwand" / "loot_tables" / "blocks" / f"{b}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"explosionwand:{b}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # занятое гнездо при разрушении возвращает Угли Феникса
    put(D / "explosionwand" / "loot_tables" / "blocks" / "hell_socket.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "explosionwand:phoenix_embers"}]}]})
    put(A / "models" / "item" / "hell_key.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "explosionwand:item/hell_key"}})
    put(D / "minecraft" / "tags" / "blocks" / "mineable" / "pickaxe.json", {"replace": False, "values": ["explosionwand:seal_stone", "explosionwand:phoenix_embers"]})
    R = D / "explosionwand" / "recipes"
    put(R / "hell_key.json", {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": "explosionwand:rift_core"}, {"item": "explosionwand:dreaming_eye"}, {"item": "explosionwand:phoenix_feather"}], "result": {"item": "explosionwand:hell_key", "count": 1}})
    put(R / "seal_stone.json", {"type": "minecraft:crafting_shapeless", "category": "building", "ingredients": [{"item": "minecraft:stone_bricks"}, {"item": "minecraft:stone_bricks"}, {"item": "explosionwand:rift_dust"}, {"item": "explosionwand:ash_coal"}, {"item": "explosionwand:sleep_thread"}], "result": {"item": "explosionwand:seal_stone", "count": 3}})
    put(R / "phoenix_embers.json", {"type": "minecraft:crafting_shapeless", "category": "building", "ingredients": [{"item": "explosionwand:ash_coal"}, {"item": "explosionwand:ash_coal"}, {"item": "explosionwand:ash_coal"}, {"item": "explosionwand:phoenix_spark"}, {"item": "minecraft:magma_block"}], "result": {"item": "explosionwand:phoenix_embers", "count": 1}})
    # измерение hell: тип как у eclipse, но жаркое (ultrawarm), свой биом без мобов
    t = json.loads((D / "explosionwand" / "dimension_type" / "eclipse.json").read_text(encoding="utf-8"))
    t.update({"ambient_light": 0.1, "ultrawarm": True})
    put(D / "explosionwand" / "dimension_type" / "hell.json", t)
    dim = json.loads((D / "explosionwand" / "dimension" / "eclipse.json").read_text(encoding="utf-8"))
    dim["type"] = "explosionwand:hell"; dim["generator"]["settings"]["biome"] = "explosionwand:hell"
    put(D / "explosionwand" / "dimension" / "hell.json", dim)
    bio = json.loads((D / "explosionwand" / "worldgen" / "biome" / "eclipse.json").read_text(encoding="utf-8"))
    e = bio["effects"]; e["fog_color"] = 3803653; e["sky_color"] = 3803653
    e["mood_sound"]["sound"] = "minecraft:ambient.nether_wastes.mood"
    e["music"] = {"max_delay": 0, "min_delay": 0, "replace_current_music": True, "sound": "minecraft:music.nether.nether_wastes"}
    e["particle"]["options"]["type"] = "minecraft:white_ash"
    put(D / "explosionwand" / "worldgen" / "biome" / "hell.json", bio)
    print("ok")
