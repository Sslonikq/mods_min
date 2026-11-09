#!/usr/bin/env python3
"""Ресурсы блоков Ада (фаза 5.1): текстуры, blockstates, модели, лут, рецепты досок, теги, lang.
PIL в среде нет, поэтому, как и gen_hell_assets.py, пишем PNG через zlib. Скрипт идемпотентен: теги и lang дополняются, а не затираются.
Таблица блоков и поведение — в docs/hell_blocks.md."""
import json, random, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_hell_assets import png, noise, shade, ring, disc, clamp, bricks, put, A, D  # noqa: E402

T = A / "textures" / "block"
CLEAR = (0, 0, 0, 0)


def blank(): return [[CLEAR] * 16 for _ in range(16)]


def sp(px, pts, col):
    for (x, y) in pts:
        if 0 <= x < 16 and 0 <= y < 16: px[y][x] = col


def specks(px, seed, n, col, only_opaque=True):
    r = random.Random(seed)
    for _ in range(n):
        x, y = r.randint(0, 15), r.randint(0, 15)
        if not only_opaque or px[y][x][3]: px[y][x] = col


def cracks(px, seed, n, cols):
    """случайные ломаные трещины"""
    r = random.Random(seed)
    for _ in range(n):
        x, y = r.randint(0, 15), r.randint(0, 15)
        for _ in range(r.randint(3, 6)):
            px[y][x] = cols[r.randint(0, len(cols) - 1)]
            if r.random() < .5: x = (x + r.choice((-1, 1))) % 16
            else: y = (y + 1) % 16


def holes(px, seed, n):
    r = random.Random(seed)
    for _ in range(n): px[r.randint(0, 15)][r.randint(0, 15)] = CLEAR


def stalks(seed, cols, n, hmin, hmax, xs=None):
    """травинки/нити снизу вверх на прозрачном фоне"""
    r = random.Random(seed); px = blank()
    for i in range(n):
        x = xs[i] if xs else r.randint(0, 15)
        h = r.randint(hmin, hmax); c = cols[r.randint(0, len(cols) - 1)]
        for k in range(h):
            y = 15 - k
            px[y][(x + (1 if k > h - 3 and i % 2 else 0)) % 16] = c
    return px


def hang(seed, cols, n, hmin, hmax):
    """нити, свисающие сверху"""
    r = random.Random(seed); px = blank()
    for i in range(n):
        x = 1 + (i * 14) // max(1, n - 1) if n > 1 else 8
        h = r.randint(hmin, hmax); c = cols[r.randint(0, len(cols) - 1)]
        for y in range(h): px[y][x] = c
        px[h - 1][x] = cols[-1]
    return px


def flower(stem, petal, core, leaf):
    px = blank()
    for y in range(8, 16): px[y][7] = stem
    sp(px, [(6, 12), (5, 11), (8, 13), (9, 12)], leaf)
    disc(px, 7.5, 5, 3.2, petal); sp(px, [(7, 5), (8, 5), (7, 4), (8, 4)], core)
    return px


def planks(seed, base, edge, crack=None):
    px = noise(base, 6, seed)
    for y in range(16):
        for x in range(16):
            if y % 4 == 3: px[y][x] = shade(px[y][x], .7)
            elif y % 4 == 0: px[y][x] = shade(px[y][x], 1.1)
    r = random.Random(seed)
    for row in range(4):
        sp(px, [((row * 5 + 3) % 16, row * 4 + 1), ((row * 5 + 3) % 16, row * 4 + 2)], edge + (255,))
    if crack: cracks(px, seed + 1, 2, crack)
    return px


def log_side(seed, base, vein):
    r = random.Random(seed); px = noise(base, 6, seed)
    for x in range(16):
        k = .75 if x % 4 == 1 else (1.12 if x % 4 == 3 else 1)
        for y in range(16): px[y][x] = shade(px[y][x], k)
    for _ in range(5):
        x, y = r.randint(0, 15), r.randint(0, 12)
        for k in range(r.randint(3, 6)):
            px[(y + k) % 16][x] = vein[k % len(vein)]
            if r.random() < .3: x = (x + r.choice((-1, 1))) % 16
    return px


def log_top(seed, base, ring_c, core):
    px = noise(base, 5, seed)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6.2: px[y][x] = shade(px[y][x], .6)
            elif int(d) % 2 == 1: px[y][x] = ring_c
    disc(px, 7.5, 7.5, 1.6, core)
    return px


def leaves(seed, cols, glow, hole_n):
    r = random.Random(seed)
    px = [[cols[r.randint(0, len(cols) - 1)] for _ in range(16)] for _ in range(16)]
    specks(px, seed + 1, 26, glow)
    holes(px, seed + 2, hole_n)
    return px


def bush(stage):
    r = random.Random(7); px = blank()
    leafc = [(40, 38, 36, 255), (58, 54, 50, 255), (30, 28, 28, 255)]
    for y in range(5, 16):
        for x in range(1, 15):
            if (x - 7.5) ** 2 / 36 + (y - 11) ** 2 / 25 < 1 and r.random() < .85: px[y][x] = leafc[r.randint(0, 2)]
    for (x, y) in [(2, 12), (4, 14), (6, 13), (9, 14), (11, 13), (13, 12)]: px[y][x] = (24, 22, 22, 255)
    berries = [(4, 10), (8, 8), (11, 11), (6, 13), (10, 7)][:stage * 2 - 1 if stage else 0]
    for (x, y) in berries:
        px[y][x] = (255, 120, 40, 255); px[y][x + 1] = (200, 50, 24, 255); px[y + 1][x] = (160, 36, 24, 255)
    return px


def stone(seed, base=(38, 36, 50), var=6): return noise(base, var, seed)


def ore(seed, spots, base=(38, 36, 50)):
    px = noise(base, 6, seed); r = random.Random(seed + 9)
    for _ in range(6):
        cx, cy = r.randint(2, 13), r.randint(2, 13)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)) if r.random() < .6 else ((0, 0), (1, 0)):
            px[(cy + dy) % 16][(cx + dx) % 16] = spots[r.randint(0, len(spots) - 1)]
    return px


def carved():
    px = bricks(11, (46, 42, 62), (24, 22, 34))
    purple, hi = (178, 96, 255, 255), (230, 190, 255, 255)
    disc(px, 7.5, 7.5, 4.8, (26, 22, 40, 255)); ring(px, 7.5, 7.5, 3.8, 4.8, purple)
    sp(px, [(7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)], purple)
    sp(px, [(7, 7), (8, 7), (7, 8), (8, 8)], hi)
    return px


def blazing():
    px = noise((30, 28, 40), 5, 21)
    cracks(px, 22, 7, [(255, 110, 30, 255), (255, 170, 60, 255), (210, 70, 20, 255)])
    return px


def crystal_block():
    r = random.Random(31); px = noise((150, 70, 230), 14, 31)
    for _ in range(9):
        x, y = r.randint(0, 13), r.randint(0, 13)
        for k in range(3): px[y + k][x + (k // 2)] = (225, 170, 255, 255)
    for i in range(16): px[i][0] = shade(px[i][0], .75); px[15][i] = shade(px[15][i], .75)
    return px


def cluster(stage):
    px = blank()
    base, mid, hi = (110, 40, 190, 255), (178, 96, 255, 255), (232, 190, 255, 255)
    spikes = [[(7, 7)], [(5, 6), (10, 5)], [(4, 5), (8, 3), (11, 6)], [(3, 4), (6, 2), (9, 4), (12, 6), (8, 7)]][stage]
    for (cx, hgt) in spikes:
        for y in range(hgt, 16):
            w = 1 if y - hgt < 3 else 2
            for x in range(cx - w + 1, cx + w):
                if 0 <= x < 16: px[y][x] = base if x == cx - w + 1 and w == 2 else mid
        px[hgt][cx] = hi
        if hgt + 1 < 16: px[hgt + 1][cx] = hi
    return px


def pumice():
    r = random.Random(41); px = noise((168, 150, 134), 10, 41)
    for _ in range(14):
        x, y = r.randint(0, 14), r.randint(0, 14); px[y][x] = (110, 94, 84, 255); px[y + 1][x] = (130, 114, 102, 255)
    return px


def slag():
    r = random.Random(43); px = noise((72, 56, 50), 9, 43)
    for _ in range(10):
        x, y = r.randint(1, 13), r.randint(1, 13)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)): px[y + dy][x + dx] = (34, 26, 24, 255)
    specks(px, 44, 5, (230, 90, 30, 255))
    return px


def geyser_top():
    px = noise((34, 32, 38), 5, 51)
    disc(px, 7.5, 7.5, 5.5, (20, 18, 22, 255)); disc(px, 7.5, 7.5, 4, (150, 50, 20, 255)); disc(px, 7.5, 7.5, 2.6, (255, 150, 40, 255)); disc(px, 7.5, 7.5, 1.2, (255, 235, 140, 255))
    return px


def geyser_side():
    px = noise((38, 36, 42), 6, 52)
    for x in range(16):
        px[0][x] = (200, 80, 24, 255); px[1][x] = (110, 46, 26, 255)
    cracks(px, 53, 3, [(255, 120, 30, 255), (200, 70, 20, 255)])
    return px


def coral():
    px = blank(); red, hot = (200, 40, 30, 255), (255, 120, 50, 255)
    for y in range(8, 16): px[y][7] = red; px[y][8] = red
    for i in range(6): px[7 - i][6 - i] = red; px[7 - i][9 + i] = red
    sp(px, [(0, 1), (15, 1), (7, 7), (8, 7), (4, 11), (11, 11)], hot)
    sp(px, [(4, 12), (3, 11), (11, 12), (12, 11), (7, 3), (8, 3), (7, 4), (8, 4), (7, 5), (8, 5), (7, 6), (8, 6)], red)
    return px


def ember_moss():
    px = noise((128, 56, 28), 12, 61); specks(px, 62, 16, (230, 100, 30, 255)); specks(px, 63, 8, (80, 30, 20, 255))
    return px


def ghost_moss():
    px = noise((44, 96, 106), 12, 71); specks(px, 72, 14, (120, 220, 230, 255)); specks(px, 73, 8, (24, 56, 66, 255))
    return px


def archmage():
    px = bricks(81, (94, 100, 118), (50, 54, 70))
    disc(px, 7.5, 7.5, 5, (62, 68, 88, 255))
    cy, hi = (90, 230, 250, 255), (200, 255, 255, 255)
    sp(px, [(7, y) for y in range(4, 12)] + [(8, y) for y in range(4, 12)] + [(x, 7) for x in range(4, 12)] + [(x, 8) for x in range(4, 12)], cy)
    sp(px, [(7, 7), (8, 7), (7, 8), (8, 8)], hi)
    return px


def bones():
    px = noise((98, 94, 90), 8, 91)
    bone, sh = (226, 216, 186, 255), (150, 140, 116, 255)
    for x in range(1, 15): px[3][x] = bone; px[4][x] = sh
    for k in range(4):
        x0 = 2 + k * 3
        for y in range(6, 14): px[y][x0 + (1 if y > 9 else 0)] = bone
        px[13][x0 + 1] = sh
    return px


def mush_cap():
    px = noise((110, 40, 28), 10, 112)
    sp(px, [(2, 2), (3, 2), (2, 3), (9, 4), (10, 4), (5, 9), (6, 9), (5, 10), (12, 11), (13, 11), (1, 13), (8, 13), (9, 13)], (255, 160, 50, 255))
    return px


def mush_stem():
    px = noise((196, 176, 146), 8, 113)
    for y in range(16):
        for x in (2, 7, 12): px[y][x] = shade(px[y][x], .82)
    return px


def berries_item():
    px = blank()
    for (cx, cy) in ((5, 10), (10, 9), (8, 5)):
        disc(px, cx, cy, 2.6, (150, 34, 22, 255)); disc(px, cx, cy, 1.8, (232, 84, 30, 255)); px[cy - 1][cx - 1] = (255, 190, 90, 255)
    sp(px, [(8, 2), (9, 2), (9, 3)], (60, 54, 50, 255))
    return px


# id, ru, en, kind, light, hardness, loot
# kind: cube | log | leaves | cross | carpet | layer | bush | cluster | geyser
B = [
    ("ash_soil", "Пепельная почва", "Ash Soil", "cube", 0, 0.5, "self"),
    ("ash_layer", "Слой пепла", "Ash Layer", "layer", 0, 0.1, "layers"),
    ("charred_log", "Обугленное бревно", "Charred Log", "log", 0, 2.0, "self"),
    ("charred_planks", "Обугленные доски", "Charred Planks", "cube", 0, 2.0, "self"),
    ("dry_grass", "Сухая трава", "Dry Grass", "cross", 0, 0.0, "shears"),
    ("ember_bloom", "Огнецвет", "Ember Bloom", "cross", 9, 0.0, "self"),
    ("coal_berry_bush", "Куст углеягод", "Coal Berry Bush", "bush", 0, 0.0, "berries"),
    ("petrified_bones", "Окаменелые кости", "Petrified Bones", "cube", 0, 1.5, "bones"),
    ("smoldering_log", "Тлеющее бревно", "Smoldering Log", "log", 5, 2.0, "self"),
    ("smoldering_leaves", "Тлеющая листва", "Smoldering Leaves", "leaves", 9, 0.2, "leaves"),
    ("smoldering_planks", "Тлеющие доски", "Smoldering Planks", "cube", 0, 2.0, "self"),
    ("fire_vine", "Огненная лиана", "Fire Vine", "cross", 7, 0.0, "self"),
    ("coal_mushroom_cap", "Шляпка угольного гриба", "Coal Mushroom Cap", "cube", 10, 0.2, "cap"),
    ("coal_mushroom_stem", "Ножка гриба", "Mushroom Stem", "cube", 0, 0.2, "self"),
    ("ember_moss", "Мох углей", "Ember Moss", "carpet", 4, 0.1, "self"),
    ("spark_fern", "Искровик", "Spark Fern", "cross", 5, 0.0, "self"),
    ("eclipse_stone", "Затменный камень", "Eclipse Stone", "cube", 0, 1.5, "cobble"),
    ("eclipse_cobblestone", "Затменный булыжник", "Eclipse Cobblestone", "cube", 0, 2.0, "self"),
    ("eclipse_bricks", "Затменные кирпичи", "Eclipse Bricks", "cube", 0, 2.0, "self"),
    ("carved_eclipse_bricks", "Резные затменные кирпичи", "Carved Eclipse Bricks", "cube", 7, 2.0, "self"),
    ("blazing_stone", "Раскалённый камень", "Blazing Stone", "cube", 12, 1.5, "self"),
    ("dark_obsidian", "Тёмный обсидиан", "Dark Obsidian", "cube", 0, 50.0, "self"),
    ("eclipse_crystal_block", "Кристалл затмения", "Eclipse Crystal", "cube", 12, 1.5, "self"),
    ("eclipse_cluster", "Друза затмения", "Eclipse Cluster", "cluster", 4, 1.5, "self"),
    ("slag", "Шлак", "Slag", "cube", 0, 1.0, "self"),
    ("pumice", "Пемза", "Pumice", "cube", 0, 0.6, "self"),
    ("geyser", "Гейзер", "Geyser", "geyser", 0, 2.0, "self"),
    ("fire_coral", "Огненный коралл", "Fire Coral", "cross", 8, 0.0, "self"),
    ("blood_iron_ore", "Руда кровавого железа", "Blood Iron Ore", "cube", 0, 3.0, "iron"),
    ("crown_gem_ore", "Самоцвет короны", "Crown Gem Ore", "cube", 9, 3.0, "self"),
    ("ghost_moss", "Призрачный мох", "Ghost Moss", "carpet", 0, 0.1, "self"),
    ("ghost_flower", "Призрачный цветок", "Ghost Flower", "cross", 7, 0.0, "self"),
    ("ghost_vine", "Призрачная лоза", "Ghost Vine", "cross", 0, 0.0, "self"),
    ("archmage_stone", "Камень Архимагов", "Archmage Stone", "cube", 0, 2.0, "self"),
]
KIND = {b[0]: b[3] for b in B}
ID = "explosionwand"


def model(name, obj): put(A / "models" / "block" / f"{name}.json", obj)


def one_loot(name, entries, conds=None):
    pool = {"rolls": 1, "entries": entries}
    if conds: pool["conditions"] = conds
    return pool


SILK = {"condition": "minecraft:match_tool", "predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}}
SHEARS = {"condition": "minecraft:match_tool", "predicate": {"items": ["minecraft:shears"]}}
BOOM = {"condition": "minecraft:survives_explosion"}


def item(name, **extra): return {"type": "minecraft:item", "name": name, **extra}


def count(a, b): return {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": a, "max": b}}


def loot(bid, kind):
    me = f"{ID}:{bid}"
    selfd = [item(me)]
    if kind == "self": p = [one_loot(bid, selfd, [BOOM])]
    elif kind == "layers":
        fn = [{"function": "minecraft:set_count", "count": n, "conditions": [{"condition": "minecraft:block_state_property", "block": me, "properties": {"layers": str(n)}}]} for n in range(2, 9)]
        p = [one_loot(bid, [item(me, functions=fn)], [BOOM])]
    elif kind == "shears": p = [one_loot(bid, selfd, [SHEARS])]
    elif kind == "cobble": p = [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [item(me, conditions=[SILK]), item(f"{ID}:eclipse_cobblestone")]}], "conditions": [BOOM]}]
    elif kind == "bones": p = [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [item(me, conditions=[SILK]), item("minecraft:bone_meal", functions=[count(2, 4)])]}], "conditions": [BOOM]}]
    elif kind == "cap": p = [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [item(me, conditions=[SILK]), item("minecraft:charcoal", functions=[count(1, 2)])]}], "conditions": [BOOM]}]
    elif kind == "iron": p = [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [item(me, conditions=[SILK]), item("minecraft:raw_iron", functions=[count(1, 2), {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"}])]}], "conditions": [BOOM]}]
    elif kind == "leaves": p = [{"rolls": 1, "entries": [item(me)], "conditions": [{"condition": "minecraft:any_of", "terms": [SHEARS, SILK]}]},
                                {"rolls": 1, "entries": [item("minecraft:stick", functions=[count(1, 2)])], "conditions": [{"condition": "minecraft:inverted", "term": {"condition": "minecraft:any_of", "terms": [SHEARS, SILK]}}, {"condition": "minecraft:random_chance", "chance": 0.1}]}]
    elif kind == "berries":
        def stage(n, a, b):
            return {"condition": "minecraft:block_state_property", "block": me, "properties": {"age": str(n)}}, count(a, b)
        c3, f3 = stage(3, 2, 3); c2, f2 = stage(2, 1, 2)
        p = [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [item(f"{ID}:coal_berries", conditions=[c3], functions=[f3]), item(f"{ID}:coal_berries", conditions=[c2], functions=[f2])]}], "conditions": [BOOM]}]
    return {"type": "minecraft:block", "pools": p}


def main():
    # --- текстуры ---
    tex = {
        "ash_soil": (lambda: [[c for c in row] for row in noise((72, 68, 66), 8, 101)]),
        "ash_layer": lambda: noise((184, 182, 176), 8, 102),
        "charred_log": lambda: log_side(103, (26, 22, 22), [(230, 90, 30, 255), (170, 60, 24, 255), (255, 160, 60, 255)]),
        "charred_log_top": lambda: log_top(104, (36, 30, 30), (22, 18, 18, 255), (170, 60, 24, 255)),
        "charred_planks": lambda: planks(105, (58, 44, 38), (30, 22, 20), [(230, 90, 30, 255), (170, 60, 24, 255)]),
        "dry_grass": lambda: stalks(106, [(150, 138, 100, 255), (118, 108, 78, 255), (178, 166, 120, 255)], 9, 5, 12),
        "ember_bloom": lambda: flower((54, 100, 40, 255), (255, 100, 40, 255), (255, 220, 100, 255), (40, 80, 36, 255)),
        "smoldering_log": lambda: log_side(107, (46, 28, 24), [(255, 130, 40, 255), (210, 80, 24, 255)]),
        "smoldering_log_top": lambda: log_top(108, (74, 40, 30), (48, 28, 24, 255), (255, 150, 50, 255)),
        "smoldering_leaves": lambda: leaves(109, [(214, 70, 28, 255), (240, 110, 30, 255), (170, 40, 24, 255), (250, 150, 40, 255)], (255, 214, 90, 255), 10),
        "smoldering_planks": lambda: planks(110, (124, 58, 42), (80, 34, 26), [(255, 150, 50, 255), (210, 80, 24, 255)]),
        "fire_vine": lambda: hang(111, [(160, 52, 24, 255), (200, 70, 28, 255), (255, 150, 50, 255)], 5, 8, 16),
        "coal_mushroom_cap": mush_cap,
        "coal_mushroom_stem": mush_stem,
        "ember_moss": ember_moss,
        "spark_fern": lambda: stalks(114, [(150, 52, 28, 255), (196, 76, 30, 255), (255, 140, 50, 255)], 6, 6, 14),
        "eclipse_stone": lambda: stone(115),
        "eclipse_cobblestone": lambda: bricks(116, (44, 42, 58), (20, 18, 30)),
        "eclipse_bricks": lambda: bricks(117, (50, 46, 66), (24, 22, 34)),
        "carved_eclipse_bricks": carved,
        "blazing_stone": blazing,
        "dark_obsidian": lambda: noise((22, 14, 38), 6, 118),
        "eclipse_crystal_block": crystal_block,
        "slag": slag,
        "pumice": pumice,
        "geyser_top": geyser_top,
        "geyser_side": geyser_side,
        "fire_coral": coral,
        "blood_iron_ore": lambda: ore(119, [(150, 28, 36, 255), (96, 20, 30, 255), (200, 60, 60, 255)]),
        "crown_gem_ore": lambda: ore(120, [(255, 214, 80, 255), (255, 240, 150, 255), (200, 140, 30, 255)]),
        "ghost_moss": ghost_moss,
        "ghost_flower": lambda: flower((70, 110, 120, 255), (100, 220, 255, 255), (230, 255, 255, 255), (60, 100, 110, 255)),
        "ghost_vine": lambda: hang(121, [(40, 100, 112, 255), (70, 150, 160, 255), (150, 235, 245, 255)], 4, 9, 16),
        "archmage_stone": archmage,
        "petrified_bones": bones,
    }
    for s in range(4): tex[f"eclipse_cluster_{s}"] = (lambda s=s: cluster(s))
    for s in range(4): tex[f"coal_berry_bush_{s}"] = (lambda s=s: bush(s))
    for name, f in tex.items(): png(T / f"{name}.png", f(), alpha=True)
    png(A / "textures" / "item" / "coal_berries.png", berries_item(), alpha=True)
    put(A / "models" / "item" / "coal_berries.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{ID}:item/coal_berries"}})

    # --- blockstates, модели ---
    for bid, ru, en, kind, light, hard, lt in B:
        bs = A / "blockstates" / f"{bid}.json"; tx = f"{ID}:block/{bid}"
        item_model = {"parent": f"{ID}:block/{bid}"}
        if kind == "cube":
            put(bs, {"variants": {"": {"model": tx}}}); model(bid, {"parent": "minecraft:block/cube_all", "textures": {"all": tx}})
        elif kind == "leaves":
            put(bs, {"variants": {"": {"model": tx}}}); model(bid, {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped", "textures": {"all": tx}})
        elif kind == "log":
            put(bs, {"variants": {"axis=y": {"model": tx}, "axis=z": {"model": tx, "x": 90}, "axis=x": {"model": tx, "x": 90, "y": 90}}})
            model(bid, {"parent": "minecraft:block/cube_column", "textures": {"end": tx + "_top", "side": tx}})
        elif kind == "geyser":
            put(bs, {"variants": {"": {"model": tx}}})
            model(bid, {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": tx + "_top", "bottom": tx + "_side", "side": tx + "_side"}})
            # текстура блока: geyser_top / geyser_side
        elif kind == "cross":
            put(bs, {"variants": {"": {"model": tx}}}); model(bid, {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": tx}})
            item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": tx}}
        elif kind == "carpet":
            put(bs, {"variants": {"": {"model": tx}}}); model(bid, {"parent": "minecraft:block/carpet", "textures": {"wool": tx}})
        elif kind == "layer":
            v = {}
            for n in range(1, 9):
                h = 2 * n; nm = f"{bid}_height{h}"
                v[f"layers={n}"] = {"model": f"{ID}:block/{nm}"}
                f = lambda uv, cull=None: ({"uv": uv, "texture": "#t"} | ({"cullface": cull} if cull else {}))
                model(nm, {"textures": {"particle": "#t", "t": tx}, "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": {
                    "down": f([0, 0, 16, 16], "down"), "up": f([0, 0, 16, 16]), "north": f([0, 16 - h, 16, 16], "north"), "south": f([0, 16 - h, 16, 16], "south"),
                    "west": f([0, 16 - h, 16, 16], "west"), "east": f([0, 16 - h, 16, 16], "east")}}]})
            put(bs, {"variants": v}); item_model = {"parent": f"{ID}:block/{bid}_height2"}
        elif kind == "bush":
            v = {}
            for s in range(4):
                v[f"age={s}"] = {"model": f"{tx}_stage{s}"}
                model(f"{bid}_stage{s}", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": f"{tx}_{s}"}})
            put(bs, {"variants": v}); item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{tx}_3"}}
        elif kind == "cluster":
            rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90}, "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}
            v = {}
            for s in range(4):
                model(f"{bid}_stage{s}", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": f"{tx}_{s}"}})
                for face, r in rot.items(): v[f"age={s},facing={face}"] = {"model": f"{tx}_stage{s}", **r}
            put(bs, {"variants": v}); item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{tx}_3"}}
        put(A / "models" / "item" / f"{bid}.json", item_model)
        put(D / ID / "loot_tables" / "blocks" / f"{bid}.json", loot(bid, lt))

    # --- рецепты досок ---
    for wood, ru in (("charred", "charred"), ("smoldering", "smoldering")):
        put(D / ID / "recipes" / f"{wood}_planks.json", {"type": "minecraft:crafting_shapeless", "category": "building", "ingredients": [{"item": f"{ID}:{wood}_log"}], "result": {"item": f"{ID}:{wood}_planks", "count": 4}})

    # --- теги ---
    def tag(path, values):
        f = D / path; cur = json.loads(f.read_text(encoding="utf-8"))["values"] if f.exists() else []
        put(f, {"replace": False, "values": cur + [v for v in values if v not in cur]})
    n = lambda *ids: [f"{ID}:{i}" for i in ids]
    tb = "minecraft/tags/blocks"
    tag(f"{tb}/mineable/pickaxe.json", n("petrified_bones", "eclipse_stone", "eclipse_cobblestone", "eclipse_bricks", "carved_eclipse_bricks", "blazing_stone", "dark_obsidian",
                                      "eclipse_crystal_block", "eclipse_cluster", "slag", "pumice", "geyser", "blood_iron_ore", "crown_gem_ore", "archmage_stone"))
    tag(f"{tb}/mineable/axe.json", n("charred_log", "charred_planks", "smoldering_log", "smoldering_planks", "coal_mushroom_cap", "coal_mushroom_stem", "coal_berry_bush"))
    tag(f"{tb}/mushroom_grow_block.json", n("ash_soil"))
    tag(f"{tb}/mineable/shovel.json", n("ash_soil", "ash_layer"))
    tag(f"{tb}/mineable/hoe.json", n("smoldering_leaves", "ember_moss", "ghost_moss"))
    tag(f"{tb}/needs_stone_tool.json", n("blood_iron_ore", "eclipse_stone", "blazing_stone"))
    tag(f"{tb}/needs_iron_tool.json", n("crown_gem_ore"))
    tag(f"{tb}/logs.json", n("charred_log", "smoldering_log"))
    tag(f"{tb}/planks.json", n("charred_planks", "smoldering_planks"))
    tag(f"{tb}/leaves.json", n("smoldering_leaves"))
    tag(f"{tb}/replaceable_plants.json" if False else f"{tb}/flowers.json", n("ember_bloom", "ghost_flower"))
    ti = "minecraft/tags/items"
    tag(f"{ti}/logs.json", n("charred_log", "smoldering_log"))
    tag(f"{ti}/planks.json", n("charred_planks", "smoldering_planks"))
    tag(f"{ti}/leaves.json", n("smoldering_leaves"))
    tag(f"{ti}/flowers.json", n("ember_bloom", "ghost_flower"))

    # --- lang ---
    for fn, idx in (("ru_ru", 1), ("en_us", 2)):
        f = A / "lang" / f"{fn}.json"; d = json.loads(f.read_text(encoding="utf-8"))
        for b in B: d[f"block.{ID}.{b[0]}"] = b[idx]
        d[f"item.{ID}.coal_berries"] = "Углеягоды" if fn == "ru_ru" else "Coal Berries"
        f.write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("ok", len(B), "блоков")


if __name__ == "__main__":
    main()
