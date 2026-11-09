#!/usr/bin/env python3
"""Фаза 5.2 (без PIL): руды Ада и пещеры.
Предметы raw_blood_iron / blood_iron_ingot / crown_gem / eclipse_shard, инструменты из кровавого железа (текстуры 16x16, модели),
рецепты (плавка, инструменты, блок Кристалла затмения), настоящие карверы Ада + тег замещаемых блоков + прописывание карверов в 5 биомах, lang ru/en.
Запуск повторяем. Превью текстур (x8) пишется в build/ore_preview.png."""
import json, sys, zlib, struct
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_hell_assets import png, put, A, D  # noqa: E402

ID = "explosionwand"
ROOT = Path(__file__).resolve().parent.parent
T = A / "textures" / "item"
CL = (0, 0, 0, 0)


def blank(): return [[CL] * 16 for _ in range(16)]


def put_px(px, pts, col):
    for (x, y) in pts:
        if 0 <= x < 16 and 0 <= y < 16: px[y][x] = col


def line(px, a, b, col):
    (x0, y0), (x1, y1) = a, b
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        t = i / n if n else 0
        put_px(px, [(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t))], col)


def outline(px, col):
    """тёмный контур вокруг непрозрачных пикселей"""
    pts = []
    for y in range(16):
        for x in range(16):
            if px[y][x][3]: continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and px[ny][nx][3] and px[ny][nx] != col:
                    pts.append((x, y)); break
    put_px(px, pts, col)


# кровавое железо: от тёмного к светлому
BI = [(60, 10, 16, 255), (104, 18, 24, 255), (150, 32, 36, 255), (198, 66, 58, 255), (236, 132, 112, 255)]
STICK = [(52, 34, 22, 255), (88, 58, 36, 255), (122, 84, 52, 255)]
EDGE = (26, 8, 10, 255)


def raw_blood_iron():
    px = blank()
    blob = [(6, 4), (7, 4), (8, 4), (9, 4), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6),
            (4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7), (4, 8), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8),
            (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (11, 9), (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10), (6, 11), (7, 11), (8, 11), (9, 11)]
    put_px(px, blob, BI[1])
    put_px(px, [(7, 5), (8, 5), (6, 6), (7, 6), (5, 7), (6, 7), (9, 6), (10, 7)], BI[2])
    put_px(px, [(7, 5), (6, 6), (9, 6)], BI[3])
    put_px(px, [(5, 9), (6, 10), (7, 11), (8, 10), (10, 9), (11, 8), (9, 11), (8, 9)], BI[0])
    put_px(px, [(8, 7), (9, 8), (7, 9)], (170, 150, 150, 255))  # крапины железа
    put_px(px, [(10, 6), (6, 8)], BI[4])
    outline(px, EDGE)
    return px


def blood_iron_ingot():
    px = blank()
    # слиток-параллелепипед, как ванильный, но красный
    body = [(x, y) for y in range(6, 11) for x in range(3 + (10 - y) // 2 * 0, 13)]
    put_px(px, [(x, y) for y in range(7, 12) for x in range(3, 12)], BI[2])       # лицевая грань
    put_px(px, [(x, y) for y in range(5, 7) for x in range(5, 14)], BI[3])        # верх
    put_px(px, [(x, y) for y in range(7, 11) for x in range(12, 14)], BI[1])      # правая грань
    put_px(px, [(x, 11) for x in range(3, 12)], BI[1])
    put_px(px, [(x, 7) for x in range(3, 12)], BI[3])
    put_px(px, [(5, 5), (6, 5), (7, 5), (4, 7), (5, 7)], BI[4])
    put_px(px, [(12, 6), (13, 6)], BI[2])
    put_px(px, [(6, 9), (7, 9), (8, 10)], BI[1])
    put_px(px, [(5, 8), (6, 8)], BI[3])
    outline(px, EDGE)
    return px


def crown_gem():
    px = blank()
    G = [(110, 36, 8, 255), (190, 80, 12, 255), (250, 150, 30, 255), (255, 214, 90, 255), (255, 244, 190, 255)]
    # ограненный самоцвет-«корона»: пять зубцов сверху и грани снизу
    rows = {2: (3, 3), 3: (3, 4), 4: (2, 13), 5: (2, 13), 6: (3, 12), 7: (4, 11), 8: (5, 10), 9: (6, 9), 10: (7, 8)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1): px[y][x] = G[2]
    put_px(px, [(7, 2), (8, 2), (7, 3), (8, 3), (12, 2), (12, 3), (13, 3)], G[2])
    put_px(px, [(x, 4) for x in range(2, 14)], G[3])
    put_px(px, [(x, y) for y in range(6, 9) for x in range(6, 10)], G[3])
    put_px(px, [(7, 7), (8, 7), (3, 4), (3, 5)], G[4])
    put_px(px, [(x, 5) for x in range(9, 14)] + [(10, 6), (11, 6), (9, 7), (10, 7), (8, 8), (9, 8), (8, 9)], G[1])
    put_px(px, [(11, 6), (10, 7), (9, 8), (8, 9), (7, 10)], G[0])
    outline(px, (50, 16, 6, 255))
    return px


def eclipse_shard():
    px = blank()
    D0, D1, D2 = (14, 8, 28, 255), (46, 22, 78, 255), (98, 52, 150, 255)
    GOLD, HOT = (232, 168, 40, 255), (255, 232, 140, 255)
    # осколок-кристалл по диагонали, остриём вверх-вправо
    pts = []
    for i in range(10):
        pts += [(3 + i, 12 - i), (4 + i, 12 - i), (4 + i, 13 - i)]
    put_px(px, pts, D1)
    put_px(px, [(4 + i, 12 - i) for i in range(9)], D2)
    put_px(px, [(3 + i, 12 - i) for i in range(3, 8)], D0)
    put_px(px, [(12, 3), (13, 2), (12, 2), (13, 3)], D1)
    put_px(px, [(13, 2), (12, 3)], D2)
    # золотая кайма «короны затмения» по краю и искра на острие
    put_px(px, [(3, 12), (4, 13), (3, 13), (5, 13)], GOLD)
    put_px(px, [(7, 10), (9, 8)], GOLD)
    put_px(px, [(14, 1), (13, 1), (14, 2)], HOT)
    put_px(px, [(6, 11), (8, 9), (10, 7)], HOT)
    outline(px, (10, 4, 20, 255))
    return px


def handle(px, a=(2, 13), b=(10, 5)):
    line(px, a, b, STICK[1])
    put_px(px, [(a[0] + i, a[1] - i + 1) for i in range(0, b[0] - a[0])], STICK[0])
    put_px(px, [(a[0] + 1, a[1] - 1), (a[0] + 3, a[1] - 3), (a[0] + 5, a[1] - 5)], STICK[2])


def finish(px, head):
    put_px(px, head, BI[2])
    return px


def pickaxe():
    px = blank(); handle(px, (2, 13), (11, 4))
    head = [(5, 3), (6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (11, 3), (12, 3), (13, 4), (13, 5), (13, 6), (12, 7), (13, 7),
            (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (11, 4), (12, 4), (12, 5), (12, 6)]
    put_px(px, head, BI[2])
    put_px(px, [(6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (11, 3), (12, 3), (13, 4)], BI[3])
    put_px(px, [(7, 2), (9, 2)], BI[4])
    put_px(px, [(5, 3), (13, 6), (13, 7), (12, 7), (12, 6), (12, 5), (11, 4)], BI[1])
    outline(px, EDGE)
    return px


def axe():
    px = blank(); handle(px, (2, 13), (10, 5))
    head = [(8, 2), (9, 2), (10, 2), (7, 3), (8, 3), (9, 3), (10, 3), (11, 3), (12, 3), (7, 4), (8, 4), (9, 4), (10, 4), (11, 4), (12, 4), (13, 4),
            (8, 5), (9, 5), (10, 5), (11, 5), (12, 5), (13, 5), (9, 6), (10, 6), (11, 6), (12, 6), (13, 6), (11, 7), (12, 7), (13, 7)]
    put_px(px, head, BI[2])
    put_px(px, [(8, 2), (9, 2), (10, 2), (7, 3), (7, 4)], BI[3])
    put_px(px, [(13, 4), (13, 5), (13, 6), (13, 7), (12, 7), (11, 7), (12, 6)], BI[1])
    put_px(px, [(8, 3), (8, 4)], BI[4])
    put_px(px, [(10, 5)], STICK[1])
    outline(px, EDGE)
    return px


def shovel():
    px = blank(); handle(px, (2, 13), (10, 5))
    head = [(12, 2), (13, 2), (11, 3), (12, 3), (13, 3), (14, 3), (10, 4), (11, 4), (12, 4), (13, 4), (14, 4), (10, 5), (11, 5), (12, 5), (13, 5), (14, 5),
            (11, 6), (12, 6), (13, 6), (12, 7), (13, 7)]
    put_px(px, [(x, y) for (x, y) in head if y <= 7], BI[2])
    put_px(px, [(12, 2), (13, 2), (11, 3), (12, 3)], BI[3])
    put_px(px, [(12, 3)], BI[4])
    put_px(px, [(14, 4), (14, 5), (13, 6), (13, 7), (12, 7), (12, 6), (13, 5)], BI[1])
    outline(px, EDGE)
    return px


def hoe():
    px = blank(); handle(px, (2, 13), (11, 4))
    head = [(8, 2), (9, 2), (10, 2), (11, 2), (12, 2), (13, 3), (8, 3), (9, 3), (10, 3), (11, 3), (12, 3), (13, 4), (9, 4), (10, 4), (13, 5), (9, 5)]
    put_px(px, head, BI[2])
    put_px(px, [(8, 2), (9, 2), (10, 2), (11, 2), (12, 2)], BI[3])
    put_px(px, [(13, 3), (13, 4), (13, 5), (9, 5), (9, 4), (10, 4)], BI[1])
    put_px(px, [(10, 2)], BI[4])
    put_px(px, [(11, 4)], STICK[1])
    outline(px, EDGE)
    return px


def sword():
    px = blank()
    # рукоять и гарда
    put_px(px, [(2, 13), (3, 12), (4, 11)], STICK[1]); put_px(px, [(2, 14), (3, 13), (1, 13), (2, 12)], STICK[0])
    line(px, (4, 8), (7, 11), BI[0]); line(px, (5, 8), (8, 11), BI[1]); put_px(px, [(4, 9), (5, 10), (6, 11)], BI[2])
    # клинок толщиной 2 по диагонали
    for i in range(8):
        x, y = 6 + i, 9 - i
        px[y][x] = BI[3]; px[y + 1][x] = BI[2]
        if i < 7: px[y][x + 1] = BI[1]
    put_px(px, [(13, 2), (14, 1), (13, 1), (14, 2)], BI[4])
    put_px(px, [(8, 7), (10, 5), (12, 3)], BI[4])
    outline(px, EDGE)
    return px


ITEMS = {"raw_blood_iron": raw_blood_iron, "blood_iron_ingot": blood_iron_ingot, "crown_gem": crown_gem, "eclipse_shard": eclipse_shard}
TOOLS = {"blood_iron_pickaxe": pickaxe, "blood_iron_axe": axe, "blood_iron_shovel": shovel, "blood_iron_hoe": hoe, "blood_iron_sword": sword}


def preview(names, fns):
    """таблица x8 на тёмном фоне -> build/ore_preview.png"""
    S = 8; cols = len(names); W, H = cols * 16 * S + (cols + 1) * 4, 16 * S + 8
    rows = []
    for y in range(H):
        row = []
        for x in range(W):
            tx, ty = (x - 4) // (16 * S + 4) if False else None, None
            cell = (x - 4) // (16 * S + 4); lx = (x - 4) - cell * (16 * S + 4)
            col = (60, 56, 64)
            if 0 <= cell < cols and 0 <= lx < 16 * S and 4 <= y < 4 + 16 * S:
                c = fns[cell][(y - 4) // S][lx // S]
                if c[3]: col = c[:3]
            row.append(col)
        rows.append(row)
    out = ROOT / "build" / "ore_preview.png"
    png(out, rows)
    return out


# ---------------- JSON ----------------
def shaped(result, pattern, key, count=1, category="equipment"):
    return {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": result, "count": count}}


def recipes():
    R = D / ID / "recipes"
    ing, stick = f"{ID}:blood_iron_ingot", "minecraft:stick"
    put(R / "blood_iron_ingot_smelting.json", {"type": "minecraft:smelting", "category": "misc", "ingredient": {"item": f"{ID}:raw_blood_iron"}, "result": ing, "experience": 0.9, "cookingtime": 200})
    put(R / "blood_iron_ingot_blasting.json", {"type": "minecraft:blasting", "category": "misc", "ingredient": {"item": f"{ID}:raw_blood_iron"}, "result": ing, "experience": 0.9, "cookingtime": 100})
    k = {"#": ing, "S": stick}
    put(R / "blood_iron_pickaxe.json", shaped(f"{ID}:blood_iron_pickaxe", ["###", " S ", " S "], k))
    put(R / "blood_iron_axe.json", shaped(f"{ID}:blood_iron_axe", ["##", "#S", " S"], k))
    put(R / "blood_iron_shovel.json", shaped(f"{ID}:blood_iron_shovel", ["#", "S", "S"], k))
    put(R / "blood_iron_hoe.json", shaped(f"{ID}:blood_iron_hoe", ["##", " S", " S"], k))
    put(R / "blood_iron_sword.json", shaped(f"{ID}:blood_iron_sword", ["#", "#", "S"], k))
    put(R / "eclipse_crystal_block.json", shaped(f"{ID}:eclipse_crystal_block", ["##", "##"], {"#": f"{ID}:eclipse_shard"}, category="building"))


CARVER_BLOCKS = ["eclipse_stone", "eclipse_cobblestone", "ash_soil", "slag", "pumice", "blazing_stone", "petrified_bones"]
CARVER_VANILLA = ["soul_soil", "soul_sand", "magma_block", "basalt", "smooth_basalt", "blackstone", "netherrack", "gravel"]
BIOMES = ["hell", "hell_ash_wastes", "hell_smoldering_forest", "hell_basalt_peaks", "hell_lava_sea", "hell_soul_valley"]


def carvers():
    # формат 1.20.1 как у minecraft:cave / minecraft:canyon; карверы выше лавового слоя (дно моря лавы y<28, лава до y=40: ниже 54 карвер вскрывал дно и лава сливалась в пещеры); lava_level absolute 14: пещеры не заливаются лавой выше дна (океан лавы на y=40 — не карвер); float uniform и trapezoid с "value"
    put(D / ID / "tags" / "blocks" / "hell_carver_replaceables.json", {"replace": False, "values": [f"{ID}:{b}" for b in CARVER_BLOCKS] + [f"minecraft:{b}" for b in CARVER_VANILLA]})
    cave = {"type": "minecraft:cave", "config": {
        "probability": 0.2,
        "y": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 54}, "max_inclusive": {"absolute": 160}},
        "yScale": {"type": "minecraft:uniform", "value": {"min_inclusive": 0.1, "max_exclusive": 0.9}},
        "lava_level": {"absolute": 14},
        "replaceable": f"#{ID}:hell_carver_replaceables",
        "horizontal_radius_multiplier": {"type": "minecraft:uniform", "value": {"min_inclusive": 0.7, "max_exclusive": 1.4}},
        "vertical_radius_multiplier": {"type": "minecraft:uniform", "value": {"min_inclusive": 0.8, "max_exclusive": 1.3}},
        "floor_level": {"type": "minecraft:uniform", "value": {"min_inclusive": -1.0, "max_exclusive": -0.4}}}}
    canyon = {"type": "minecraft:canyon", "config": {
        "probability": 0.02,
        "y": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 64}, "max_inclusive": {"absolute": 110}},
        "yScale": 3.0,
        "lava_level": {"absolute": 14},
        "replaceable": f"#{ID}:hell_carver_replaceables",
        "vertical_rotation": {"type": "minecraft:uniform", "value": {"min_inclusive": -0.125, "max_exclusive": 0.125}},
        "shape": {
            "distance_factor": {"type": "minecraft:uniform", "value": {"min_inclusive": 0.75, "max_exclusive": 1.0}},
            "thickness": {"type": "minecraft:trapezoid", "value": {"min": 0.0, "max": 6.0, "plateau": 2.0}},
            "width_smoothness": 3,
            "horizontal_radius_factor": {"type": "minecraft:uniform", "value": {"min_inclusive": 0.75, "max_exclusive": 1.0}},
            "vertical_radius_default_factor": 1.0,
            "vertical_radius_center_factor": 0.0}}}
    put(D / ID / "worldgen" / "configured_carver" / "hell_cave.json", cave)
    put(D / ID / "worldgen" / "configured_carver" / "hell_canyon.json", canyon)
    for b in BIOMES:
        f = D / ID / "worldgen" / "biome" / f"{b}.json"
        j = json.loads(f.read_text(encoding="utf-8"))
        j["carvers"] = {"air": [f"{ID}:hell_cave", f"{ID}:hell_canyon"]}
        f.write_text(json.dumps(j, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# ---------------- lang ----------------
RU = {
    "item.explosionwand.raw_blood_iron": "Сырое кровавое железо",
    "item.explosionwand.raw_blood_iron.desc": "Руда Ада. Плавится в слиток в печи или плавильне",
    "explosionwand.jei.raw_blood_iron": "Выпадает из руды кровавого железа в Аду (1-2 штуки, удача увеличивает добычу; шёлковое касание даёт сам блок). Переплавляется в Слиток кровавого железа в печи или плавильне.",
    "item.explosionwand.blood_iron_ingot": "Слиток кровавого железа",
    "item.explosionwand.blood_iron_ingot.desc": "Материал Ада: стержни, инструменты, печати",
    "explosionwand.jei.blood_iron_ingot": "Выплавляется из Сырого кровавого железа в печи или плавильне. Нужен для Адского стержня, Осколка Печати: Затмение и инструментов из кровавого железа.",
    "item.explosionwand.crown_gem": "Самоцвет короны",
    "item.explosionwand.crown_gem.desc": "Редкий светящийся самоцвет Ада",
    "explosionwand.jei.crown_gem": "Выпадает из руды самоцвета короны в Аду. Руда редкая, нужна железная кирка; удача увеличивает добычу, шёлковое касание даёт сам блок. Нужен для Адского стержня, Осколка Печати: Затмение и сильных палочек Ада.",
    "item.explosionwand.eclipse_shard": "Осколок затмения",
    "item.explosionwand.eclipse_shard.desc": "Осколок кристалла затмения",
    "explosionwand.jei.eclipse_shard": "Выпадает из взрослой друзы затмения в Аду (2-4 штуки, удача увеличивает добычу). Нужен для Ядра Ада, Осколка Печати: Затмение и блока Кристалла затмения (4 осколка).",
}
EN = {
    "item.explosionwand.raw_blood_iron": "Raw Blood Iron",
    "item.explosionwand.raw_blood_iron.desc": "Hell ore. Smelts into an ingot in a furnace or blast furnace",
    "explosionwand.jei.raw_blood_iron": "Dropped by Blood Iron Ore in Hell (1-2 pieces, Fortune increases the yield; Silk Touch gives the block). Smelt it into a Blood Iron Ingot in a furnace or blast furnace.",
    "item.explosionwand.blood_iron_ingot": "Blood Iron Ingot",
    "item.explosionwand.blood_iron_ingot.desc": "Hell material: rods, tools, seals",
    "explosionwand.jei.blood_iron_ingot": "Smelted from Raw Blood Iron in a furnace or blast furnace. Needed for the Hell Rod, the Seal Shard: Eclipse and Blood Iron tools.",
    "item.explosionwand.crown_gem": "Crown Gem",
    "item.explosionwand.crown_gem.desc": "A rare glowing gem of Hell",
    "explosionwand.jei.crown_gem": "Dropped by Crown Gem Ore in Hell. The ore is rare and needs an iron pickaxe; Fortune increases the yield, Silk Touch gives the block. Needed for the Hell Rod, the Seal Shard: Eclipse and the strong Hell wands.",
    "item.explosionwand.eclipse_shard": "Eclipse Shard",
    "item.explosionwand.eclipse_shard.desc": "A shard of eclipse crystal",
    "explosionwand.jei.eclipse_shard": "Dropped by a mature Eclipse Cluster in Hell (2-4 pieces, Fortune increases the yield). Needed for the Hell Core, the Seal Shard: Eclipse and the Eclipse Crystal block (4 shards).",
}
TOOL_NAMES = {"pickaxe": ("Кирка", "Pickaxe"), "axe": ("Топор", "Axe"), "shovel": ("Лопата", "Shovel"), "hoe": ("Мотыга", "Hoe"), "sword": ("Меч", "Sword")}
for t, (ru, en) in TOOL_NAMES.items():
    k = f"blood_iron_{t}"
    RU[f"item.explosionwand.{k}"] = f"{ru} из кровавого железа"
    RU[f"item.explosionwand.{k}.desc"] = "Прочнее алмазной, быстро ломает блоки Ада" if t != "sword" else "Прочнее алмазного, наносит больше урона"
    RU[f"explosionwand.jei.{k}"] = "Крафтится из слитков кровавого железа и палок. Чинится на наковальне слитками кровавого железа."
    EN[f"item.explosionwand.{k}"] = f"Blood Iron {en}"
    EN[f"item.explosionwand.{k}.desc"] = "Tougher than diamond, breaks Hell blocks fast" if t != "sword" else "Tougher than diamond, hits harder"
    EN[f"explosionwand.jei.{k}"] = "Crafted from Blood Iron Ingots and sticks. Repaired on an anvil with Blood Iron Ingots."
RU["explosionwand.jei.eclipse_crystal_block"] = "Крафтится из 4 Осколков затмения (2x2). Светится."
EN["explosionwand.jei.eclipse_crystal_block"] = "Crafted from 4 Eclipse Shards (2x2). Glows."


def main():
    fns = []; names = []
    for n, f in {**ITEMS, **TOOLS}.items():
        px = f(); png(T / f"{n}.png", px, alpha=True); fns.append(px); names.append(n)
        parent = "minecraft:item/handheld" if n in TOOLS else "minecraft:item/generated"
        put(A / "models" / "item" / f"{n}.json", {"parent": parent, "textures": {"layer0": f"{ID}:item/{n}"}})
    preview(names, fns)
    recipes(); carvers()
    for fn, d in (("ru_ru.json", RU), ("en_us.json", EN)):
        p = A / "lang" / fn; j = json.loads(p.read_text(encoding="utf-8")); j.update(d)
        p.write_text(json.dumps(j, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("ok", names)


if __name__ == "__main__":
    main()
