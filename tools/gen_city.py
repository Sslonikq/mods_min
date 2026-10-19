"""
Город Спящих (фаза 4.5): шаблон наземного города ~97x97 блоков (стена 91x91 + ров) для tools/gen_dungeons.py.

Модуль не импортирует gen_dungeons: ему передают сам модуль (build_city(gd)), так нет циклического импорта.
Координаты данжа: (0,0,0) — центр города, y=0 — первый воздух над землёй (мостовая на y=-1), x — на восток, z — на юг.
Ворота — в южной стене (z=43..45), игрок идёт на север.

Сетка внутри стены (x и z одинаково): запад -42..-30 (кварталы), улица -29..-25, центр -24..24, улица 25..29, восток 30..42.
  - 4 угловых квартала: по дому 9x9; 4 боковых квартала: по 4 дома 9x11; юг (за воротами): проспект x -5..5 и по 2 дома по сторонам.
  - центр: храм (север, с шпилем Монолита), библиотека (северо-восток), обсерватория (северо-запад), площадь с фонтанами и картой на полу.
Залы (метки hall): 1..6 улицы и площадь (на площади, зал 6, один Колыбельщик), 7..18 дома с мобами, 19 библиотека (ловушка с печатью), 20 логово храма (заглушка до 4.7),
21 склеп, 22 обсерватория, 23..25 тайники.
"""
import math
import random


def build_city(gd):
    Grid, Rift, bm, AIR = gd.Grid, gd.Rift, gd.bm, gd.AIR

    class CityBuilder(Rift):
        PHASES = ("base", "shell", "carve", "floor", "pit", "deco")

        def __init__(self):
            super().__init__()
            self.rng = random.Random(20261105)

    C = CityBuilder()
    g, rng = C.g, C.rng
    shapes_rng = random.Random(4242)

    # ------------------------------------------------------------------ материалы
    SB, CSB, MSB = bm("stone_bricks"), bm("cracked_stone_bricks"), bm("mossy_stone_bricks")
    DB, CDB, DT, CDT = bm("deepslate_bricks"), bm("cracked_deepslate_bricks"), bm("deepslate_tiles"), bm("cracked_deepslate_tiles")
    PD, CD = bm("polished_deepslate"), bm("cobbled_deepslate")
    DP, PRB = bm("dark_prismarine"), bm("prismarine_bricks")
    SL, PBB = bm("sea_lantern"), bm("polished_blackstone_bricks")
    WP, DOP = bm("warped_planks"), bm("dark_oak_planks")
    GLASS, MOSS = bm("light_blue_stained_glass"), bm("moss_block")
    BC, CC = bm("black_concrete"), bm("cyan_concrete")
    STAIR = bm("dark_prismarine_stairs")

    def wdb(*_): return rng.choice([DB, DB, DB, CDB, DB, SB])
    def fchk(x, y, z): return DT if (x + z) % 2 == 0 else PD
    def fwood(x, y, z): return DOP if (x + z) % 2 == 0 else WP

    INNER, WALLO, MOAT, TOP, FOUND = 42, 45, 48, 20, -10
    STREETS = ((-29, -25), (25, 29))

    def in_r(v, r): return r[0] <= v <= r[1]

    def is_street(x, z):
        if any(in_r(x, r) for r in STREETS) or any(in_r(z, r) for r in STREETS): return True
        if abs(x) <= 5 and z >= 30: return True            # проспект и площадь у ворот
        if abs(x) <= 24 and -7 <= z <= 24: return True     # центральная площадь
        return False

    def ground(x, z):
        if not (abs(x) <= INNER and abs(z) <= INNER): return rng.choice([SB, CD, SB])      # под стеной
        if abs(x) <= 5 and z >= 30 and abs(x) <= 1: return DP                               # полоса проспекта
        if abs(x) <= 24 and -7 <= z <= 24: return DT if (x + z) % 2 == 0 else PD         # площадь
        if is_street(x, z): return rng.choice([DT, DT, DT, PD, CDT, CD, DT, PRB if rng.randrange(12) == 0 else DT])
        return rng.choice([CD, CD, MOSS, CD, bm("coarse_dirt")])                             # дворы

    # ------------------------------------------------------------------ основание: воздух, фундамент, мостовая, перемычка через ров
    def base():
        g.air(-MOAT, 0, -MOAT, MOAT, TOP, MOAT)
        g.box(-WALLO, FOUND, -WALLO, WALLO, -2, WALLO, lambda x, y, z: rng.choice([SB, SB, CD, MSB]))
        g.box(-WALLO, -1, -WALLO, WALLO, -1, WALLO, lambda x, y, z: ground(x, z))
        g.box(-3, -6, WALLO + 1, 3, -2, MOAT, SB)
        g.box(-3, -1, WALLO + 1, 3, -1, MOAT, fchk)
    C.add("base", base)

    # ------------------------------------------------------------------ стена, башни, ворота
    def walls():
        w = lambda x, y, z: rng.choice([DB, DB, DB, CDB, SB, DB])
        g.box(-WALLO, -1, -WALLO, WALLO, 9, -43, w)
        g.box(-WALLO, -1, 43, WALLO, 9, WALLO, w)
        g.box(-WALLO, -1, -42, -43, 9, 42, w)
        g.box(43, -1, -42, WALLO, 9, 42, w)
        for t in range(-WALLO, WALLO + 1):                       # зубцы по внешней кромке
            if t % 2 == 0:
                g.set(t, 10, -WALLO, DB); g.set(t, 10, WALLO, DB); g.set(-WALLO, 10, t, DB); g.set(WALLO, 10, t, DB)
        for sx in (-1, 1):
            for sz in (-1, 1):                                   # башни 6x6 по углам
                x1, x2 = (-WALLO, -40) if sx < 0 else (40, WALLO)
                z1, z2 = (-WALLO, -40) if sz < 0 else (40, WALLO)
                g.box(x1, -1, z1, x2, 15, z2, lambda x, y, z: rng.choice([PRB, PRB, DP, DB]))
                g.box(x1, 16, z1, x2, 16, z2, DP)
                for t in range(x1, x2 + 1):
                    if (t - x1) % 2 == 0: g.set(t, 17, z1, DP); g.set(t, 17, z2, DP)
                for t in range(z1, z2 + 1):
                    if (t - z1) % 2 == 0: g.set(x1, 17, t, DP); g.set(x2, 17, t, DP)
                cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
                g.box(cx, 17, cz, cx + 1, 18, cz + 1, SL)
                for y in range(2, 14, 4):                         # светящиеся швы на башнях
                    g.set(x1 if sx < 0 else x2, y, cz, SL)
        g.box(-4, -1, 43, -3, 9, WALLO, PBB)                     # столбы ворот
        g.box(3, -1, 43, 4, 9, WALLO, PBB)
    C.add("shell", walls)
    C.add("carve", lambda: g.air(-2, 0, 43, 2, 5, WALLO))

    def gate():
        g.box(-2, 0, 43, 2, 5, WALLO, bm("chiseled_deepslate"))
        for z in (43, WALLO):
            g.box(0, 0, z, 0, 4, z, SL)                          # светящийся «шов»
        g.mark("door", -2, 0, 43, "sleep", (5, 6, 3))
        for sx in (-4, 4):                                        # фонари у ворот снаружи
            g.box(sx, 0, 47, sx, 2, 47, PD); g.set(sx, 3, 47, bm("soul_lantern[hanging=false]"))
    C.add("deco", gate)
    g.mark("start", 0, 0, 47)

    # ------------------------------------------------------------------ крыши
    def roof(x1, z1, x2, z2, y0, blk=DP):
        ax1, az1, ax2, az2, y = x1 - 1, z1 - 1, x2 + 1, z2 + 1, y0
        while ax2 - ax1 >= 2 and az2 - az1 >= 2:
            for x in range(ax1, ax2 + 1):
                corner = x in (ax1, ax2)
                g.set(x, y, az1, blk if corner else STAIR + "[facing=south]")
                g.set(x, y, az2, blk if corner else STAIR + "[facing=north]")
            for z in range(az1 + 1, az2):
                g.set(ax1, y, z, STAIR + "[facing=east]")
                g.set(ax2, y, z, STAIR + "[facing=west]")
            g.box(ax1 + 1, y, az1 + 1, ax2 - 1, y, az2 - 1, blk)
            ax1 += 1; az1 += 1; ax2 -= 1; az2 -= 1; y += 1
        if ax2 >= ax1 and az2 >= az1: g.box(ax1, y, az1, ax2, y, az2, blk)

    # ------------------------------------------------------------------ дома
    LAYOUT = []     # (вид, x1, z1, x2, z2) — для карты на полу
    PADS = []       # прямоугольники эмблем у дверей (x1, z1, x2, z2)
    DOOR_DIR = {"N": "north", "S": "south", "W": "west", "E": "east"}
    BACK_DIR = {"N": "south", "S": "north", "W": "east", "E": "west"}
    SHAPES = {
        "moon": ["..##.", ".#...", ".#...", ".#...", "..##."],
        "star": ["..#..", "..#..", "#####", ".###.", ".#.#."],
        "sun": ["#.#.#", ".###.", "##.##", ".###.", "#.#.#"],
        "tree": ["..#..", ".###.", "#####", "..#..", "..#.."],
        "key": [".###.", ".#.#.", ".###.", "..#..", "..##."],
        "eye": [".....", ".###.", "##.##", ".###.", "....."],
        "cross": ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"],
        "gem": ["..#..", ".###.", "#####", ".###.", "..#.."],
    }
    ARROW = ["..#..", "..#..", "#.#.#", ".###.", "..#.."]

    HOUSES = {}

    def add_house(name, cx, cz, W, D, side):
        if side in "NS": x1, x2, z1, z2 = cx - W // 2, cx + W // 2, cz - D // 2, cz + D // 2
        else: x1, x2, z1, z2 = cx - D // 2, cx + D // 2, cz - W // 2, cz + W // 2
        if side == "N": loc = lambda a, b: (cx + a, z1 + b)
        elif side == "S": loc = lambda a, b: (cx - a, z2 - b)
        elif side == "W": loc = lambda a, b: (x1 + b, cz + a)
        else: loc = lambda a, b: (x2 - b, cz - a)
        if side == "E": pad = (x2 + 2, cz - 2, x2 + 6, cz + 2)
        elif side == "W": pad = (x1 - 6, cz - 2, x1 - 2, cz + 2)
        elif side == "S": pad = (cx - 2, z2 + 2, cx + 2, z2 + 6)
        else: pad = (cx - 2, z1 - 6, cx + 2, z1 - 2)
        HOUSES[name] = dict(name=name, cx=cx, cz=cz, W=W, D=D, side=side, x1=x1, x2=x2, z1=z1, z2=z2, loc=loc, pad=pad)
        PADS.append(pad)
        LAYOUT.append(("house", x1, z1, x2, z2))

    for i, cx in enumerate((-18, -6, 6, 18), 1): add_house(f"N{i}", cx, -36, 9, 11, "S")
    for i, cx in enumerate((-20, -10, 10, 20), 1): add_house(f"S{i}", cx, 36, 9, 11, "N")
    for i, cz in enumerate((-18, -6, 6, 18), 1): add_house(f"W{i}", -36, cz, 9, 11, "E")
    for i, cz in enumerate((-18, -6, 6, 18), 1): add_house(f"E{i}", 36, cz, 9, 11, "W")
    add_house("NW", -35, -35, 9, 9, "E"); add_house("NE", 35, -35, 9, 9, "W")
    add_house("SW", -35, 35, 9, 9, "E"); add_house("SE", 35, 35, 9, 9, "W")

    # 4.6: все мобы Города настоящие (CityMobs, CityMobsB): метки explosionwand:<вид>, заглушек нет
    SL_, ST_, DR_, MT_ = "explosionwand:lunatic", "explosionwand:stitch_guard", "explosionwand:drowsy", "explosionwand:night_moth"
    DM_, BK_, SS_, CR_ = "explosionwand:dreamer", "explosionwand:book_guard", "explosionwand:statue_guard", "explosionwand:cradler"
    # name: (вид обстановки, мобы, сундук или None)
    MOB_HOUSES = [
        ("NW", "bed", [SL_, SL_, ST_], "Дом Ткача"), ("NE", "study", [SL_, ST_, DR_], "Дом Переписчика"),
        ("SW", "kitchen", [SL_, SL_, ST_], "Дом Пекаря"), ("SE", "bed", [SL_, ST_, SL_], "Дом Лекаря"),
        ("N1", "ruin", [SL_, SL_, ST_], None), ("N3", "kitchen", [SL_, MT_, SL_], "Дом Фонарщика"),
        ("S1", "bed", [SL_, SL_, DR_], None), ("S4", "study", [SL_, ST_, SL_], "Дом Звонаря"),
        ("W1", "kitchen", [SL_, SL_, DR_], None), ("W4", "bed", [SL_, DR_, ST_], "Дом Кузнеца"),
        ("E1", "ruin", [SL_, ST_, SL_], None), ("E4", "study", [SL_, SL_, DM_], None)]
    EMPTY_HOUSES = {"N2": "bed", "N4": "ruin", "S2": "kitchen", "W2": "ruin", "E2": "study"}
    LEVERS = {"N4": "bed", "W2": "bell"}                             # дом -> id тайника, который открывает рычаг
    CRYPT_HOUSE = "W3"
    SECRET_HOUSES = {"S3": ("bed", "Потайная спальня", 24), "E3": ("bell", "Тайник Звонаря", 23)}   # дом -> (id рычага, сундук, зал)
    for n in (CRYPT_HOUSE, *SECRET_HOUSES): assert n in HOUSES
    assert len(MOB_HOUSES) + len(EMPTY_HOUSES) + 1 + len(SECRET_HOUSES) == 20

    # эмблемы у дверей: луна только у дома со склепом
    others = [k for k in SHAPES if k != "moon"]
    EMBLEM = {}
    names = sorted(HOUSES)
    shapes_rng.shuffle(names)
    for i, n in enumerate(names): EMBLEM[n] = others[i % len(others)]
    EMBLEM[CRYPT_HOUSE] = "moon"

    def hall(n, x, y, z, sx, sy, sz): g.mark("hall", x, y, z, str(n), (sx, sy, sz))
    def chest(x, y, z, name, tier): g.mark("chest", x, y, z, f"{name}|{tier}")

    def build_house(h, kind, wall_fn):
        x1, x2, z1, z2, W, D, side, loc = h["x1"], h["x2"], h["z1"], h["z2"], h["W"], h["D"], h["side"], h["loc"]
        back, door = BACK_DIR[side], DOOR_DIR[side]
        C.add("shell", lambda: g.box(x1, -1, z1, x2, 5, z2, wall_fn))
        C.add("shell", lambda: roof(x1, z1, x2, z2, 6))

        def frame():                                                # косяки двери: тёмные столбы по бокам проёма
            for a in (-2, 2):
                x, z = loc(a, 0); g.box(x, 0, z, x, 3, z, DP)
        C.add("shell", frame)

        def carve():
            g.air(x1 + 1, 0, z1 + 1, x2 - 1, 4, z2 - 1)
            for a in (-1, 0, 1):
                x, z = loc(a, 0); g.air(x, 0, z, x, 2, z)
        C.add("carve", carve)

        def floor():
            for x in range(x1 + 1, x2):
                for z in range(z1 + 1, z2):
                    if g.cells.get((x, -1, z)) != AIR: g.set(x, -1, z, fwood(x, -1, z))
        C.add("floor", floor)

        def deco():
            for (a, b) in ((0, 3), (0, D - 4)):                   # фонари под потолком
                x, z = loc(a, b); g.set(x, 4, z, bm("lantern[hanging=true]"))
            for a in (-4, 4):                                      # окна на боковых стенах
                for b in (3, D - 4):
                    x, z = loc(a, b); g.box(x, 2, z, x, 3, z, GLASS)
            for a in (-2, 2):                                      # окна в задней стене
                x, z = loc(a, D - 1); g.box(x, 2, z, x, 3, z, GLASS)
            for a in (-2, 2):                                      # столбики с фонарями у двери (во дворе)
                x, z = loc(a, -1); g.set(x, 0, z, PD); g.set(x, 1, z, bm("soul_lantern[hanging=false]"))
            if kind == "bed":
                for a in (-2, 0):
                    xf, zf = loc(a, D - 3); xh, zh = loc(a, D - 2)
                    g.set(xf, 0, zf, bm(f"cyan_bed[facing={back},occupied=false,part=foot]"))
                    g.set(xh, 0, zh, bm(f"cyan_bed[facing={back},occupied=false,part=head]"))
                x, z = loc(-3, 1); g.set(x, 0, z, bm("barrel[facing=up]"))
            elif kind == "study":
                for a in range(-3, 2):
                    x, z = loc(a, D - 2); g.box(x, 0, z, x, 2, z, bm("bookshelf"))
                x, z = loc(-2, D - 4); g.set(x, 0, z, bm(f"lectern[facing={door}]"))
            elif kind == "kitchen":
                x, z = loc(-3, D - 2); g.set(x, 0, z, bm(f"furnace[facing={door},lit=false]"))
                x, z = loc(-2, D - 2); g.set(x, 0, z, bm("crafting_table"))
                x, z = loc(-1, D - 2); g.set(x, 0, z, bm("cauldron"))
                for a in (1, 2): x, z = loc(a, D - 2); g.set(x, 0, z, bm("barrel[facing=up]"))
            elif kind == "ruin":
                for (a, b) in ((-3, D - 3), (2, D - 2), (-2, 1)):
                    x, z = loc(a, b); g.set(x, 0, z, CD)
                for a in (-3, 3):
                    x, z = loc(a, D - 2); g.set(x, 4, z, bm("cobweb"))
                x, z = loc(3, 1); g.set(x, 4, z, bm("cobweb"))
            elif kind == "plain":
                for a in (-3, 3):
                    x, z = loc(a, 1); g.set(x, 0, z, bm("barrel[facing=up]"))
        C.add("deco", deco)
        C.rooms.append((f"дом {h['name']}", x1, z1, x2, z2, 0, 5))

    # --- роли домов
    hall_id = 6
    for (name, kind, mobs, chest_name) in MOB_HOUSES:
        h = HOUSES[name]
        hall_id += 1
        build_house(h, kind, wdb)
        loc, D = h["loc"], h["D"]
        xs = [loc(a, b)[0] for a in (-3, 3) for b in (1, D - 2)]; zs = [loc(a, b)[1] for a in (-3, 3) for b in (1, D - 2)]
        hall(hall_id, min(xs), -1, min(zs), max(xs) - min(xs) + 1, 7, max(zs) - min(zs) + 1)
        for m, (a, b) in zip(mobs, ((-2, 3), (2, 3), (0, 5))):
            x, z = loc(a, b); g.mark("spawn", x, 0, z, m)
        if chest_name:
            x, z = loc(3, D - 2); chest(x, 0, z, chest_name, 1)
    assert hall_id == 18

    for name, kind in EMPTY_HOUSES.items():
        h = HOUSES[name]
        build_house(h, kind, wdb)
        if name in LEVERS:
            loc, D, side = h["loc"], h["D"], h["side"]
            x, z = loc(-3, D - 2)
            C.add("deco", lambda x=x, z=z, side=side: g.set(x, 1, z, bm(f"lever[face=wall,facing={DOOR_DIR[side]},powered=false]")))
            g.mark("lever", x, 1, z, LEVERS[name])
    for name, (lid, cname, hid) in SECRET_HOUSES.items():
        h = HOUSES[name]
        build_house(h, "plain", wdb)
        loc, D, side = h["loc"], h["D"], h["side"]
        part = [loc(a, D - 4) for a in range(-3, 4)]
        sec = [loc(a, D - 4) for a in (-1, 0, 1)]
        sx, sz = min(p[0] for p in sec), min(p[1] for p in sec)

        def chamber(loc=loc, D=D, part=part, sec=sec):
            for (x, z) in part: g.box(x, 0, z, x, 4, z, wdb())
            for (x, z) in sec: g.box(x, 0, z, x, 2, z, CDB)
            x, z = loc(0, D - 3); g.set(x, 4, z, bm("lantern[hanging=true]"))
            x, z = loc(-2, D - 2); g.set(x, 0, z, bm("bookshelf")); g.set(x, 1, z, bm("bookshelf"))
        C.add("deco", chamber)
        g.mark("secret", sx, 0, sz, lid, (3, 3, 1) if side in "NS" else (1, 3, 3))
        cells = [loc(a, b) for a in (-3, 3) for b in (D - 3, D - 2)]
        mx, mz = min(c[0] for c in cells), min(c[1] for c in cells)
        hall(hid, mx, -1, mz, max(c[0] for c in cells) - mx + 1, 7, max(c[1] for c in cells) - mz + 1)
        x, z = loc(0, D - 2); chest(x, 0, z, cname, 3)
        C.rooms.append((f"тайник {name}", mx - 1, mz - 1, mx + 8, mz + 8, 0, 5))
    # дом со склепом: лестница вниз у двери в сторону задней стены
    ch = HOUSES[CRYPT_HOUSE]
    build_house(ch, "ruin", wdb)

    # --- склеп под домом W3 (дом: x -41..-31, z 2..10; дверь на восток): лестница вдоль x на запад, потом ход на юг в зал
    CS, CH = -8, 5                                                 # стоять на y=-8, высота воздуха 5 (потолок -3 — внутри фундамента)
    ZC = 8                                                         # лестница 3 шириной: z 7..9
    N_ST = 8

    def stairs_x():
        for k in range(N_ST):
            x, ys = -33 - k, -1 - k
            C.add("carve", lambda x=x, ys=ys: g.air(x, ys + 1, ZC - 1, x, ys + 4, ZC + 1))
            C.add("floor", lambda x=x, ys=ys: g.box(x, ys, ZC - 1, x, ys, ZC + 1, bm("deepslate_brick_stairs[facing=east]")))
            if k in (5, 7): C.add("deco", lambda x=x, ys=ys: g.set(x, ys + 4, ZC, bm("lantern[hanging=true]")))
        C.rooms.append(("склеп: лестница", -41, ZC - 1, -33, ZC + 1, -8, 12))
    stairs_x()
    C.corr("склеп: ход", -43, 7, -41, 13, CS, wdb, fchk, H=CH)
    g.mark("brazier", -43, CS, 8, "1,0,0")
    C.add("deco", lambda: g.set(-43, CS, 8, bm("soul_campfire[lit=false]")))
    C.room("склеп", -43, 14, -33, 30, CS, CH, wdb, fchk, light="sea_lantern")
    C.corr("склеп: тайный ход", -32, 17, -27, 19, CS, wdb, fchk, H=CH)
    C.room("ложе Архимага", -26, 14, -18, 22, CS, CH, wdb, fchk, light="sea_lantern")

    def crypt():
        for z in (18, 22, 26):                                      # саркофаги вдоль стен
            for x in (-42, -35):
                g.box(x, CS, z, x + 1, CS, z + 1, PD); g.set(x, CS + 1, z, bm("cobweb"))
        for (x, z) in ((-42, 16), (-34, 16), (-42, 29), (-34, 29)): g.box(x, CS, z, x, CS + 3, z, DP)
        g.box(-32, CS, 17, -32, CS + 2, 19, CDB)                  # трещина в стене: тайный ход
        for (x, z) in ((-24, 16), (-20, 16), (-24, 20), (-20, 20)): g.set(x, CS, z, bm("bookshelf")); g.set(x, CS + 1, z, bm("bookshelf"))
        g.set(-22, CS, 21, bm("lectern[facing=north]"))
    C.add("deco", crypt)
    hall(21, -43, CS - 1, 14, 11, CH + 2, 17)
    for (x, z) in ((-36, 17), (-40, 21), (-37, 24), (-40, 27), (-36, 28)): g.mark("spawn", x, CS, z, SS_)
    chest(-38, CS, 29, "Склеп Спящих", 2)
    g.mark("secret", -32, CS, 17, "crypt", (1, 3, 3))
    hall(25, -26, CS - 1, 14, 9, CH + 2, 9)
    chest(-22, CS, 18, "Ложе Архимага", 3)

    # ------------------------------------------------------------------ обсерватория (северо-запад центра): рычаг тайника склепа
    def building(name, x1, z1, x2, z2, doorx, H=7, wall=None):
        wall = wall or wdb
        C.add("shell", lambda: g.box(x1, -1, z1, x2, H, z2, wall))
        C.add("shell", lambda: roof(x1, z1, x2, z2, H + 1))
        C.add("carve", lambda: (g.air(x1 + 1, 0, z1 + 1, x2 - 1, H - 1, z2 - 1), g.air(doorx - 1, 0, z2, doorx + 1, 2, z2)))
        C.add("floor", lambda: g.box(x1 + 1, -1, z1 + 1, x2 - 1, -1, z2 - 1, fwood))
        C.rooms.append((name, x1, z1, x2, z2, 0, H))
        LAYOUT.append((name, x1, z1, x2, z2))

        def deco():
            for x in (doorx - 3, doorx + 3):
                g.set(x, 0, z2 + 1, PD); g.set(x, 1, z2 + 1, bm("soul_lantern[hanging=false]"))
            for x in (x1 + 3, x2 - 3):
                for z in (z1 + 4, z2 - 3): g.set(x, H - 1, z, bm("lantern[hanging=true]"))
        C.add("deco", deco)
    OX1, OX2 = -24, -14
    building("обсерватория", OX1, -24, OX2, -8, -19)
    OCX = -19

    def obs_deco():
        for x in range(OX1 + 1, OX2):
            if x != OCX: g.box(x, 0, -23, x, 3, -23, bm("bookshelf"))
        for x in (OX1 + 1, OX2 - 1):
            for z in range(-20, -11, 2): g.box(x, 0, z, x, 2, z, bm("bookshelf"))
        g.set(OCX, 0, -23, bm("enchanting_table"))
        g.set(OCX, 1, -23, bm("lever[face=wall,facing=south,powered=false]"))
        g.set(OCX - 4, 0, -16, bm("enchanting_table")); g.set(OCX + 4, 0, -16, bm("lectern[facing=south]"))
    C.add("deco", obs_deco)
    g.mark("lever", OCX, 1, -23, "crypt")
    hall(22, OX1 + 1, -1, -23, 9, 8, 15)
    for m, (x, z) in zip((DM_, DR_, ST_, ST_), ((-21, -20), (-17, -20), (-21, -13), (-17, -13))): g.mark("spawn", x, 0, z, m)
    chest(-19, 0, -21, "Обсерватория", 2)

    # ------------------------------------------------------------------ библиотека (северо-восток центра): ловушка с печатью
    LX1, LX2 = 14, 24
    building("библиотека", LX1, -24, LX2, -8, 19)

    def lib_deco():
        g.box(LX1 + 1, 0, -11, LX2 - 1, 6, -11, wdb)                    # перегородка: вход в зал только через печать
        g.air(18, 0, -11, 20, 2, -11)
        for x in range(LX1 + 1, LX2): g.box(x, 0, -23, x, 3, -23, bm("bookshelf"))
        for x in (LX1 + 1, LX2 - 1):
            for z in range(-21, -12, 2): g.box(x, 0, z, x, 3, z, bm("bookshelf"))
        g.air(19, 0, -23, 19, 3, -23)                                    # ниша для сундука
        g.set(19, 3, -23, bm("bookshelf"))
        g.set(17, 0, -22, bm("lectern[facing=south]")); g.set(21, 0, -22, bm("lectern[facing=south]"))
        g.set(19, 6, -14, bm("lantern[hanging=true]")); g.set(19, 6, -20, bm("lantern[hanging=true]"))
        g.set(19, 6, -9, bm("lantern[hanging=true]"))
        g.set(17, 0, -9, PD); g.set(17, 1, -9, bm("soul_lantern[hanging=false]"))
    C.add("deco", lib_deco)
    g.mark("seal", 18, 0, -11, "19,polished_blackstone_bricks", (3, 3, 1))
    hall(19, LX1 + 1, -1, -23, 9, 8, 12)
    for (x, z) in ((17, -15), (21, -15), (17, -19), (21, -19)): g.mark("spawn", x, 0, z, BK_)
    chest(19, 0, -22, "Библиотека Спящих", 2)

    # ------------------------------------------------------------------ храм (север центра) и шпиль Монолита
    def temple():
        C.add("shell", lambda: g.box(-12, -1, -24, 12, 13, -6, lambda x, y, z: rng.choice([DB, DB, DP, DB, CDB])))
        C.add("carve", lambda: (g.air(-10, 0, -22, 10, 11, -8), g.air(-2, 0, -7, 2, 5, -6)))
        C.add("floor", lambda: g.box(-10, -1, -22, 10, -1, -8, lambda x, y, z: bm("obsidian") if (x + z) % 2 else PD))
        C.rooms.append(("храм: притвор", -11, -11, 11, -6, 0, 6))
        C.rooms.append(("храм: логово", -11, -23, 11, -11, 0, 12))

        def deco():
            g.box(-10, 0, -11, 10, 11, -11, DB)                          # перегородка притвор / логово
            g.box(-10, 6, -10, 10, 11, -8, DB)                           # потолок притвора
            g.air(-1, 0, -11, 1, 2, -11)
            for x in (-6, 6): g.set(x, 5, -9, bm("lantern[hanging=true]"))
            g.box(-2, 0, -7, 2, 5, -6, bm("chiseled_deepslate"))          # дверь храма (метка door)
            g.box(0, 0, -6, 0, 4, -6, SL); g.box(0, 0, -7, 0, 4, -7, SL)
            for x in (-8, -4, 4, 8):                                      # колонны крыльца
                g.box(x, 0, -5, x, 8, -5, DP); g.set(x, 9, -5, SL)
            for x in (-6, 6): g.box(x, 0, -6, x, 11, -6, SL)              # светящиеся швы фасада
            g.set(-9, 0, -9, bm("soul_campfire[lit=false]"))
            for (x, z) in ((-7, -14), (7, -14), (-7, -18), (7, -18), (-7, -21), (7, -21)):
                g.box(x, 0, z, x, 10, z, DP); g.set(x, 6, z, SL)
            for x in range(-2, 3):                                        # помост под Монолит
                for z in range(-19, -14): g.set(x, -1, z, bm("crying_obsidian") if x in (-2, 2) or z in (-19, -15) else bm("obsidian"))
            for (x, z) in ((-8, -13), (8, -13), (0, -12), (-8, -21), (8, -21), (0, -21)): g.set(x, 11, z, SL)
            # шпиль: плита, три сужающихся сегмента, светящийся венец
            g.box(-4, 14, -19, 4, 16, -11, DP)
            g.box(-3, 17, -18, 3, 26, -12, PRB)
            g.box(-2, 27, -17, 2, 40, -13, PRB)
            g.box(-1, 41, -16, 1, 54, -14, PRB)
            g.box(0, 55, -15, 0, 60, -15, SL)
            g.box(0, 61, -15, 0, 63, -15, bm("cyan_stained_glass"))
            for y in range(18, 26, 3):
                for (dx, dz) in ((0, -3), (0, 3), (-3, 0), (3, 0)): g.set(dx, y, -15 + dz, SL)
            for y in range(28, 40, 4):
                for (dx, dz) in ((0, -2), (0, 2), (-2, 0), (2, 0)): g.set(dx, y, -15 + dz, SL)
        C.add("deco", deco)
        g.mark("door", -2, 0, -7, "sleep_temple", (5, 6, 2))
        g.mark("brazier", -9, 0, -9, "1,0,0")
        g.mark("seal", -1, 0, -11, "20,polished_blackstone_bricks", (3, 3, 1))
        hall(20, -10, -1, -22, 21, 14, 11)
        for m, (x, z) in zip((ST_, ST_, ST_, DR_, DM_, DR_), ((-8, -16), (8, -16), (-4, -20), (4, -20), (-5, -13), (5, -13))): g.mark("spawn", x, 0, z, m)
        chest(0, 0, -21, "Логово Монолита", 2)
        LAYOUT.append(("temple", -12, -24, 12, -6))
    temple()

    # ------------------------------------------------------------------ улицы: залы-патрули, площадь, фонтаны, фонари, эмблемы, карта, фрески
    hall(1, -5, -1, 30, 11, 11, 13)
    for (x, z) in ((-2, 34), (3, 37), (0, 40)): g.mark("spawn", x, 0, z, SL_)
    hall(2, -29, -1, 25, 59, 11, 5)
    for m, (x, z) in zip((SL_, DR_, MT_, DR_), ((-20, 27), (-8, 27), (8, 27), (22, 27))): g.mark("spawn", x, 0, z, m)
    hall(3, -29, -1, -29, 59, 11, 5)
    for m, (x, z) in zip((SL_, DR_, MT_, DR_), ((-20, -27), (-8, -27), (8, -27), (22, -27))): g.mark("spawn", x, 0, z, m)
    hall(4, -29, -1, -24, 5, 11, 49)
    for m, (x, z) in zip((SL_, SL_, DR_), ((-27, -16), (-27, 0), (-27, 16))): g.mark("spawn", x, 0, z, m)
    hall(5, 25, -1, -24, 5, 11, 49)
    for m, (x, z) in zip((SL_, SL_, DR_), ((27, -16), (27, 0), (27, 16))): g.mark("spawn", x, 0, z, m)
    hall(6, -24, -1, -5, 49, 11, 30)
    for m, (x, z) in zip((SL_, DR_, MT_, DR_), ((-12, 6), (12, 6), (0, 22), (-20, 18))): g.mark("spawn", x, 0, z, m)
    g.mark("spawn", 0, 0, 12, CR_)                                   # Колыбельщик (элита, один на город): центр площади, на карте на полу (пол вровень)
    LAYOUT.append(("plaza", -24, -7, 24, 24))

    g.mark("brazier", 4, 0, 38, "-1,0,0")
    g.mark("brazier", 6, 0, -4, "-1,0,0")

    def square():
        g.set(4, 0, 38, bm("soul_campfire[lit=false]")); g.set(6, 0, -4, bm("soul_campfire[lit=false]"))
        for (fx, fz) in ((-17, 12), (17, 12)):                           # фонтаны: бассейн 5x5, вода внутри, столб с фонарём
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if max(abs(dx), abs(dz)) == 2: g.set(fx + dx, 0, fz + dz, PRB)
                    else: g.set(fx + dx, -1, fz + dz, bm("water"))
            g.box(fx, -1, fz, fx, 2, fz, DP); g.set(fx, 3, fz, SL)
        # карта города на полу площади: пиксель = 5x5 блоков, верх = север
        mx0, mz0 = -8, 4
        g.box(mx0 - 1, -1, mz0 - 1, mx0 + 17, -1, mz0 + 17, PBB)
        for i in range(17):
            for j in range(17):
                x, z = -40 + 5 * i, -40 + 5 * j
                kind = "street" if is_street(x, z) else "yard"
                for (k, x1, z1, x2, z2) in LAYOUT:
                    if x1 <= x <= x2 and z1 <= z <= z2: kind = k; break
                col = {"house": CC, "street": bm("light_gray_concrete"), "yard": bm("gray_concrete"), "plaza": bm("light_gray_concrete"),
                       "temple": bm("blue_concrete"), "библиотека": bm("purple_concrete"), "обсерватория": bm("purple_concrete")}.get(kind, CC)
                if (i, j) == ((ch["cx"] + 42) // 5, (ch["cz"] + 42) // 5): col = SL           # дом со склепом светится
                if (i, j) == (8, 16): col = bm("white_concrete")                                  # ворота
                g.set(mx0 + i, -1, mz0 + j, col)
    C.add("deco", square)

    def pads():
        for name, h in HOUSES.items():
            x1, z1, x2, z2 = h["pad"]
            bits = SHAPES[EMBLEM[name]]
            for r in range(5):
                for c in range(5):
                    on = bits[r][c] == "#"
                    g.set(x1 + c, -1, z1 + r, SL if (on and name == CRYPT_HOUSE) else CC if on else BC)
    C.add("deco", pads)

    def posts():
        def free(x, z): return not any(p[0] <= x <= p[2] and p[1] <= z <= p[3] for p in PADS) and not any(abs(m[2][0] - x) <= 1 and abs(m[2][2] - z) <= 1 for m in g.marks)
        spots = []
        for t in range(-40, 41, 8):
            for sx in (-29, -25, 25, 29): spots.append((sx, t))
            for sz in (-29, -25, 25, 29): spots.append((t, sz))
        for (x, z) in spots:
            if abs(x) > 24 and abs(z) > 24 and (abs(x) in (25, 29)) and (abs(z) in (25, 29)): continue
            if free(x, z):
                g.box(x, 0, z, x, 2, z, PD); g.set(x, 3, z, bm("soul_lantern[hanging=false]"))
    C.add("deco", posts)

    def fresco(wx, view, zc, bits):
        """Панель 7x6 в стене x=wx; view: -1 — смотрят на запад (слева юг), +1 — на восток (слева север); пиксели светятся."""
        g.box(wx, 0, zc - 3, wx, 5, zc + 3, BC)
        for r in range(5):
            for c in range(5):
                if bits[r][c] != "#": continue
                z = zc + 2 - c if view < 0 else zc - 2 + c
                g.set(wx, 5 - r, z, SL)
    # на стенах домов S2 (x2=-6) и S3 (x1=6) лицом к проспекту
    C.add("deco", lambda: (fresco(-6, -1, 36, SHAPES["moon"]), fresco(6, 1, 36, ARROW)))

    C.run()
    g.rift = C
    return g
