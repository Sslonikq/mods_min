"""
Пепельное святилище (фаза 4.8, шаги 2а и 2б): шаблон ~173x142 блока для tools/gen_dungeons.py. Башня до y=64 (дымовые костры), шахта и уровни вниз.

Модуль не импортирует gen_dungeons: ему передают сам модуль (build_ash(gd)), так нет циклического импорта (как gen_city).
Координаты данжа: (0,0,0) — центр входа на поверхности, y=0 — первый воздух над землёй, x — на восток, z — на юг.

Что построено (2а):
  поверхность  двор x -26..26, z -52..4; башня 33x33 (x -16..16, z -50..-18), центр (0,-34); дверь `ash` в южной стене (z -19..-18)
  башня        ярусы стоя на y = 0, 14, 28, 42; кольцо шириной 7 вокруг открытого нефа 15x15; лестницы по рукавам с обрушенным пролётом
               (площадка с провалом в 1 блок), боковые комнаты-пристройки (залы 5, 6), колокольня (зал 4) с колоколом (метка bell),
               шпиль до y=64 с дымовыми кострами
  шахта        под нефом: крышка (secret `shaft`) -> винтовая лестница по стенам шахты 9x9 -> уровень 1 (y -22)
  уровень 1    «Зал Пепла»: хаб (зал 8) и четыре коридора, струи огня из стен (метка jets); север — ловушки и Кладовая (зал 9, сундук с
               Пепельным ключом), запад — Угольный склад (зал 10, рычаг тайника), юг — Кузня (зал 11), восток — дверь `ash_gate` и лестница вниз
  уровень 2    «Огненная переправа»: посадка (жаровня), зал 12 — озеро лавы с цепью платформ (осыпающиеся `crumble`, гейзеры `geyser`, острова),
               боковой Грот гейзеров (зал 13)
  тайники      14 (в бастионе пристройки башни, рычаг в Караулке), 15 (Зал Пепла), 16 (Огненная переправа); обычных подсказок нет

Шаг 2б (достроено):
  уровень 3    «Зал Четырёх Жаровен» (y -56): выход с западного уступа уровня 2 (проём в стене зала 12 и 16 ступеней на запад), зал 17 с четырьмя
               жаровнями (метка braziers), фреской на северной стене (порядок показывает только она), заслонкой спуска (secret `fires`),
               боковые залы 18 (рычаг `emberroom`) и 19, секретная каморка 21 за трещиной в зале 19, жаровня-якорь №3
  уровень 4    арена Хранителя (зал 20, y -64): 8 ступеней вниз от южной стены зала 17, печать seal, помост, Хранитель Углей и 4 Пеплохода,
               сундук с Угольным ключом (Ash.extra), дверь Гнезда `ember_gate` на востоке, тоннель в 64 блока и 10 ступеней вниз, жаровня-якорь №4
  уровень 5    Гнездо (зал 23, y -74): круглая пещера R=30 с центром (34, 4), помост в центре, метка boss phoenix (Пепельный Феникс, 4.10; яйцо
               рисует сам босс из block_display), печать seal на входе (x 3), сундук с добычей; жаровня-якорь №5 у входа
  колодец      башня 7x7 в пещере у (20, 0) и над ней до двора башни; центральный столб 3x3 и винтовая лестница по кольцу шириной 1 (75 ступеней),
               выход вровень со двором в клетках у западной стены (x 18, z -1..2); остальное закрыто вровень с двором. С поверхности внизу глухая
               дверь (secret `wellgate`), её открывает рычаг снаружи, из Гнезда: колодец — выход, а не вход в обход ключей
Номера залов: 1-4 башня, 5-6 пристройки, 7 (свободен), 8-11 уровень 1, 12-13 уровень 2, 14-16 тайники, 17-19 и 21 уровень 3, 20 арена, 23 Гнездо.
Габариты после 2б: x -96..76, z -106..35, y -76..64 (173 x 142; предел рамки — 192, см. Dungeons.ASH).
"""
import math
import random

# уровни: на каком y стоит игрок
S0, S1, S2, S3, S4, S5 = 0, -22, -40, -56, -64, -74

# 4.10 (Феникс): метка boss phoenix стоит в центре Гнезда (34, S5+1, 4, на помосте), печать seal зала 23 - в проёме входа (x 3); свободные номера залов: 22, 24..30
RESERVED = {
    "nest_boss": (34, S5 + 1, 4),
    "free_halls": (7, 22) + tuple(range(24, 31)),
}


def build_ash(gd):
    Grid, Rift, bm, AIR = gd.Grid, gd.Rift, gd.bm, gd.AIR

    class AshBuilder(Rift):
        PHASES = ("base", "shell", "carve", "floor", "pit", "deco")

        def __init__(self):
            super().__init__()
            self.rng = random.Random(20261121)

    A = AshBuilder()
    g, rng = A.g, A.rng

    # ------------------------------------------------------------------ материалы
    SB, CSB = bm("stone_bricks"), bm("cracked_stone_bricks")
    BLK, PB, PBB, CPBB = bm("blackstone"), bm("polished_blackstone"), bm("polished_blackstone_bricks"), bm("cracked_polished_blackstone_bricks")
    CHB, COAL = bm("chiseled_polished_blackstone"), bm("coal_block")
    BAS, SBAS, PBAS = bm("basalt"), bm("smooth_basalt"), bm("polished_basalt")
    NB, CNB, RNB = bm("nether_bricks"), bm("cracked_nether_bricks"), bm("red_nether_bricks")
    MAG, OBS, CO, LAVA = bm("magma_block"), bm("obsidian"), bm("crying_obsidian"), bm("lava")
    SHL, HAY = bm("shroomlight"), bm("hay_block")
    WALLB = bm("polished_blackstone_brick_wall")
    STAIR = "polished_blackstone_brick_stairs"
    LAMP, LAMPU = bm("lantern[hanging=true]"), bm("lantern[hanging=false]")
    CAMP = bm("campfire[facing=north,lit=true,signal_fire=true]")
    # мобы Святилища (4.9): метки explosionwand:<вид>, см. AshMobs/AshMobsB и RIFT_MOBS в gen_dungeons.py
    W, SP, HD, RG, WM, PR, GD, KP = ("explosionwand:" + n for n in ("ash_walker", "ash_spark", "ash_hound", "ash_ringer", "ash_worm", "ash_priest", "ash_guard", "ash_keeper"))

    def tw(*_): return rng.choice([SB, SB, CSB, BLK, CSB, PBB, BLK, COAL])          # башня: закопчённый камень
    def scorch(*_): return rng.choice([BLK, BLK, SB, CSB, BAS, PBB])
    def w1(*_): return rng.choice([PBB, PBB, PBB, CPBB, BLK, PBB])                  # уровень 1
    def f1(x, y, z): return PBAS if (x + z) % 2 == 0 else SBAS
    def w2(*_): return rng.choice([NB, NB, NB, CNB, RNB, BLK])                      # уровень 2
    def f2(x, y, z): return BLK if (x + z) % 2 == 0 else PB

    def hall(n, x1, z1, x2, z2, S, H, low=0):
        g.mark("hall", x1, S - 1 - low, z1, str(n), (x2 - x1 + 1, H + low + 2, z2 - z1 + 1))

    def hall_raw(n, x, y, z, sx, sy, sz): g.mark("hall", x, y, z, str(n), (sx, sy, sz))
    def mob(x, y, z, t): g.mark("spawn", x, y, z, t)
    def chest(x, y, z, name, tier): g.mark("chest", x, y, z, f"{name}|{tier}")

    def near_mark(x, z, r=1):
        return any(t in ("spawn", "chest", "brazier", "start", "lever", "bell", "geyser", "braziers", "door") and abs(p[0] - x) <= r and abs(p[2] - z) <= r for (t, a, p, s) in g.marks)

    JET_LINES = set()      # клетки (x, z) на линиях струй — туда колонны и мобов не ставим

    def pillar(x, y1, y2, z, blk):
        if not near_mark(x, z) and (x, z) not in JET_LINES: g.box(x, y1, z, x, y2, z, blk)

    DIRS = {"+x": (1, 0, "east"), "-x": (-1, 0, "west"), "+z": (0, 1, "south"), "-z": (0, -1, "north")}

    def jet(x, y, z, d, ln, period=100, off=0):
        """Сопло в стене (наблюдатель лицом в проход: без окна инвентаря): каждые period тиков выдох огня на ln клеток. y — на уровне груди (пол + 1)."""
        dx, dz, face = DIRS[d]
        A.add("deco", lambda: g.set(x, y, z, bm(f"observer[facing={face}]")))
        g.mark("jets", x, y, z, f"{d},{ln},{period},{off}")
        for i in range(1, ln + 1): JET_LINES.add((x + dx * i, z + dz * i))

    # ================================================================== поверхность и башня
    X1, X2, Z1, Z2 = -16, 16, -50, -18          # внешние габариты башни
    IX1, IX2, IZ1, IZ2 = -14, 14, -48, -20      # внутренние
    CZ, ROOF = -34, 54
    VX1, VX2, VZ1, VZ2 = -7, 7, -41, -27        # неф (открытый провал колец) 15x15
    STAND = (0, 14, 28, 42)                     # на каком y стоит игрок: земля, ярус I, ярус II, колокольня

    def yard(x, y, z):
        if abs(x) <= 2 and z >= -17: return PBB
        return rng.choice([PB, BLK, SB, CSB, PBB, BLK, BAS])

    def base():
        g.air(-26, 0, -52, 26, 9, 4)                                # расчистка: холм, деревья
        g.box(-26, -6, -52, 26, -2, 4, scorch)                      # фундамент площадки
        g.box(-26, -1, -52, 26, -1, 4, yard)
    A.add("base", base)

    def tower_shell():
        g.box(X1, -1, Z1, X2, ROOF, Z2, tw)
        for k in range(9):                                          # шпиль: 9 слоёв, каждый на 2 уже по краю
            h, y = 16 - 2 * k, ROOF + 1 + k
            if h > 0: g.box(-h, y, CZ - h, h, y, CZ + h, lambda *_: rng.choice([SB, CSB, BLK, SB]))
            else: g.set(0, y, CZ, HAY)
    A.add("shell", tower_shell)

    def tower_carve():
        g.air(IX1, 0, IZ1, IX2, ROOF - 1, IZ2)
        for y0 in (4, 18, 32):                                      # бойницы 1x3 (дальше от пристроек и двери)
            for x in (-9, -3, 3, 9):
                g.air(x, y0, Z1, x, y0 + 2, Z1 + 1); g.air(x, y0, Z2 - 1, x, y0 + 2, Z2)
            for z in (-46, -22):
                g.air(X1, y0, z, X1 + 1, y0 + 2, z); g.air(X2 - 1, y0, z, X2, y0 + 2, z)
        for z in (Z1, Z2 - 1):                                      # арки колокольни N/S, парапет в 2 блока остаётся
            g.air(-3, 44, z, 3, 49, z + 1); g.air(-2, 50, z, 2, 50, z + 1)
        for x in (X1, X2 - 1):                                      # арки W/E
            g.air(x, 44, CZ - 3, x + 1, 49, CZ + 3); g.air(x, 50, CZ - 2, x + 1, 50, CZ + 2)
    A.add("carve", tower_carve)

    def tower_bars():                                           # решётки в проёмах: с элитрами внутрь в обход двери Осколка не влететь
        # блоки ставятся с флагом 18 без пересчёта форм, поэтому соединения заданы явно: иначе столбики с щелью 0,875 блока
        BX = bm("iron_bars[east=true,west=true]")                   # полотно вдоль x (стены N/S)
        BZ = bm("iron_bars[north=true,south=true]")                 # полотно вдоль z (стены W/E)
        for y0 in (4, 18, 32):
            for x in (-9, -3, 3, 9):
                g.box(x, y0, Z1, x, y0 + 2, Z1 + 1, BX); g.box(x, y0, Z2 - 1, x, y0 + 2, Z2, BX)
            for z in (-46, -22):
                g.box(X1, y0, z, X1 + 1, y0 + 2, z, BZ); g.box(X2 - 1, y0, z, X2, y0 + 2, z, BZ)
        for z in (Z1, Z2 - 1):
            g.box(-3, 44, z, 3, 49, z + 1, BX); g.box(-2, 50, z, 2, 50, z + 1, BX)
        for x in (X1, X2 - 1):
            g.box(x, 44, CZ - 3, x + 1, 49, CZ + 3, BZ); g.box(x, 50, CZ - 2, x + 1, 50, CZ + 2, BZ)
    A.add("deco", tower_bars)

    # --- шахта под нефом: стена, воздух (крышка y=-1 — secret `shaft`)
    A.add("shell", lambda: g.box(-5, -14, CZ - 5, 5, -2, CZ + 5, w1))
    A.add("carve", lambda: g.air(-4, -14, CZ - 4, 4, -2, CZ + 4))

    # --- полы
    def fl0(x, z):
        if -4 <= x <= 4 and -38 <= z <= -30:
            r = max(abs(x), abs(z - CZ))
            return CO if r <= 1 else (OBS if r in (2, 4) else PB)
        return PBAS if (x + z) % 2 == 0 else SBAS

    CUT = {1: (-13, -11, -38, -21), 2: (11, 13, -41, -24), 3: (-13, -11, -38, -21)}     # проёмы над лестницами в плитах ярусов 1..3

    def floors():
        for x in range(IX1, IX2 + 1):
            for z in range(IZ1, IZ2 + 1): g.set(x, -1, z, fl0(x, z))
        for i in (1, 2, 3):
            y = STAND[i] - 1
            cx1, cx2, cz1, cz2 = CUT[i]
            for x in range(IX1, IX2 + 1):
                for z in range(IZ1, IZ2 + 1):
                    if VX1 <= x <= VX2 and VZ1 <= z <= VZ2: continue
                    if cx1 <= x <= cx2 and cz1 <= z <= cz2: continue
                    g.set(x, y, z, rng.choice([PB, PB, BLK, PBB]))
        g.box(-2, 41, -41, 2, 41, -37, PBB)                         # мост колокольни и площадка под колоколом
        g.box(-2, 41, -36, 2, 41, -32, PBB); g.box(-1, 41, -35, 1, 41, -33, CO)
    A.add("floor", floors)

    # --- лестницы по рукавам: два марша со ступенями и площадка с провалом в 1 блок («обрушенный пролёт»)
    def flight(lx, a0, d, y0, n1=7, land=5, n2=6):
        """Марш вдоль z шириной 3 (центр x=lx): n1 ступеней, площадка из land клеток (средняя пропала), n2 ступеней. Блоки на y0..; стоять на y0+1.."""
        face = "north" if d < 0 else "south"
        k = 0
        for i in range(n1):
            z, y = a0 + d * k, y0 + i; k += 1
            g.box(lx - 1, y, z, lx + 1, y, z, bm(f"{STAIR}[facing={face}]"))
        for i in range(land):
            z = a0 + d * k; k += 1
            if i == land // 2: continue                              # провал
            g.box(lx - 1, y0 + n1 - 1, z, lx + 1, y0 + n1 - 1, z, PBB)
        for i in range(n2):
            z, y = a0 + d * k, y0 + n1 + i; k += 1
            g.box(lx - 1, y, z, lx + 1, y, z, bm(f"{STAIR}[facing={face}]"))
        return a0 + d * (k - 1)

    A.add("floor", lambda: (flight(-12, -21, -1, 0), flight(12, -41, +1, 14), flight(-12, -21, -1, 28)))
    A.rooms.append(("башня: земля", IX1 - 1, IZ1 - 1, IX2 + 1, IZ2 + 1, 0, 13))
    A.rooms.append(("башня: ярус I", IX1 - 1, IZ1 - 1, IX2 + 1, IZ2 + 1, 14, 13))
    A.rooms.append(("башня: ярус II", IX1 - 1, IZ1 - 1, IX2 + 1, IZ2 + 1, 28, 13))
    A.rooms.append(("башня: колокольня", IX1 - 1, IZ1 - 1, IX2 + 1, IZ2 + 1, 42, 12))

    # --- перила: края нефа и проёмов над лестницами (+ пропущенные участки и обломки)
    def rails():
        for i in (1, 2, 3):
            y = STAND[i]
            edge = set()
            for z in range(VZ1, VZ2 + 1): edge |= {(VX1 - 1, z), (VX2 + 1, z)}
            for x in range(VX1 - 1, VX2 + 2): edge |= {(x, VZ1 - 1), (x, VZ2 + 1)}
            if i == 1: edge -= {(VX2 + 1, z) for z in range(-35, -30)}                # обрушенный участок перил
            if i == 2: edge -= {(x, VZ2 + 1) for x in range(2, 7)}
            if i == 3: edge -= {(x, VZ1 - 1) for x in range(-2, 3)}                   # мост к колоколу
            for (x, z) in edge: g.set(x, y, z, WALLB)
            if i == 1: g.set(VX2 + 1, y, -33, bm("cobbled_deepslate")); g.set(VX2 + 2, y, -32, bm("cobbled_deepslate"))
            if i == 2: g.set(4, y, VZ2 + 1, bm("cobbled_deepslate")); g.set(3, y, VZ2 + 2, bm("cobbled_deepslate"))
            cx1, cx2, cz1, cz2 = CUT[i]
            for z in range(cz1, cz2 + 1):
                g.set(cx1 - 1, y, z, WALLB); g.set(cx2 + 1, y, z, WALLB)
            for x in range(cx1, cx2 + 1): g.set(x, y, cz2 + 1 if i != 2 else cz1 - 1, WALLB)   # торец у начала марша внизу
        for z in range(-41, -36):
            g.set(-2, 42, z, WALLB); g.set(2, 42, z, WALLB)                       # перила моста колокольни
        for z in range(-36, -31): g.set(-2, 42, z, WALLB); g.set(2, 42, z, WALLB)
        for x in range(-2, 3): g.set(x, 42, -32, WALLB)
    A.add("deco", rails)

    # --- колокол на балке под потолком колокольни
    def belfry_deco():
        g.box(-14, 45, CZ, 14, 45, CZ, bm("polished_blackstone_bricks"))                                         # балка
        g.set(0, 44, CZ, bm("bell[attachment=ceiling,facing=north,powered=false]"))
        for x in (-5, 5): g.set(x, 44, CZ, LAMP)
        for (x, z) in ((-10, -45), (10, -45), (-10, -23), (10, -23)): g.set(x, 53, z, LAMP)
    A.add("deco", belfry_deco)
    g.mark("bell", 0, 44, CZ, "shaft")
    g.mark("secret", -4, -1, -38, "shaft", (9, 1, 9))

    # --- фонари яруса: подвешены под плитой следующего этажа
    def tower_lamps():
        for (x, z) in ((-10, -45), (10, -45), (-10, -23), (10, -23), (-9, -30), (9, -30)): g.set(x, 12, z, LAMP)     # земля: под плитой яруса I
        for (x, z) in ((-10, -45), (10, -45), (-10, -23), (10, -23)): g.set(x, 26, z, LAMP); g.set(x, 40, z, LAMP)
        for (x, z) in ((-8, -22), (8, -22)): g.box(x, 0, z, x, 2, z, PB); g.set(x, 3, z, LAMPU)                  # светильники на стойках у двери
        for (x, z) in ((-8, -46), (8, -46), (-8, -34), (8, -34)): g.box(x, 0, z, x, 2, z, PB); g.set(x, 3, z, LAMPU)
    A.add("deco", tower_lamps)

    # --- вход: дверь, колонны, двор, дымовые костры
    def entrance():
        g.box(-2, 0, -19, 2, 4, -18, CHB)                           # дверь (метка door)
        g.box(0, 0, -19, 0, 3, -18, bm("gilded_blackstone"))
        for sx in (-4, 4):
            g.box(sx, 0, -17, sx, 6, -17, PBB); g.set(sx, 7, -17, SHL)
            g.box(sx, 0, -16, sx, 1, -16, PB); g.set(sx, 2, -16, LAMPU)
        for (px, pz, h) in ((-12, -10, 3), (13, -9, 2), (-18, -4, 4), (19, -13, 3), (-21, -20, 2), (22, -22, 4)):     # обломки колонн
            g.box(px, 0, pz, px, h, pz, rng.choice([SB, CSB, BLK]))
        for (px, pz) in ((-7, -6), (9, -3), (-15, -14), (16, -6), (-6, -13)):                                    # завалы
            g.set(px, 0, pz, bm("cobbled_deepslate")); g.set(px + 1, 0, pz, bm("blackstone"))
        for (px, pz) in ((-14, -8), (14, -8), (-22, -28), (22, -28)):                                            # дымовые костры
            g.box(px, 0, pz, px, 2, pz, PBB); g.set(px, 3, pz, HAY); g.set(px, 4, pz, CAMP)
        g.set(-15, 55, CZ - 15, HAY); g.set(15, 55, CZ - 15, HAY); g.set(-15, 55, CZ + 15, HAY); g.set(15, 55, CZ + 15, HAY)      # костры на углах крыши
        for (px, pz) in ((-15, CZ - 15), (15, CZ - 15), (-15, CZ + 15), (15, CZ + 15)): g.set(px, 56, pz, CAMP)
        g.set(0, 64, CZ, CAMP)                                      # на острие шпиля
    A.add("deco", entrance)
    g.mark("door", -2, 0, -19, "ash", (5, 5, 2))
    g.mark("start", 0, 0, -8)
    g.mark("brazier", 7, 0, -12, "-1,0,0")
    A.add("deco", lambda: g.set(7, 0, -12, bm("soul_campfire[lit=false]")))

    # --- залы башни и мобы
    hall_raw(1, IX1, -1, IZ1, 29, 13, 29)
    hall_raw(2, IX1, 13, IZ1, 29, 13, 29)
    hall_raw(3, IX1, 27, IZ1, 29, 13, 29)
    hall_raw(4, IX1, 41, IZ1, 29, 14, 29)
    for (x, z, t) in ((-8, -45, W), (8, -45, W), (8, -24, SP), (-9, -24, W)): mob(x, 0, z, t)
    for (x, z, t) in ((-9, -45, W), (9, -45, RG), (9, -25, W), (-9, -30, SP)): mob(x, 14, z, t)
    for (x, z, t) in ((-9, -45, W), (9, -45, RG), (-9, -24, W), (9, -22, SP)): mob(x, 28, z, t)
    for (x, z, t) in ((-9, -45, SP), (10, -45, W), (-9, -24, RG), (10, -23, SP)): mob(x, 42, z, t)
    chest(-9, 42, -30, "Колокольня Пепла", 2)

    # --- пристройки башни (боковые комнаты на ярусах): бастионы с сундуком, мобами и рычагом
    BASTION = lambda *_: rng.choice([SB, CSB, BLK, SB])
    A.add("shell", lambda: g.box(17, -6, -45, 28, 12, -34, BASTION))
    A.room("Караулка", 18, -44, 27, -35, 14, 6, tw, lambda x, y, z: PBB if (x + z) % 2 == 0 else PB)
    A.add("carve", lambda: g.air(15, 14, -41, 17, 17, -39))                                                  # арка из башни
    A.add("shell", lambda: g.box(-28, -6, -45, -17, 26, -34, BASTION))
    A.room("Оружейная", -27, -44, -18, -35, 28, 6, tw, lambda x, y, z: PBB if (x + z) % 2 == 0 else PB)
    A.add("carve", lambda: g.air(-17, 28, -41, -15, 31, -39))

    def annex_deco():
        g.set(27, 15, -40, bm("lever[face=wall,facing=west,powered=false]"))
        for (x, z) in ((19, -43), (19, -42), (26, -37)): g.set(x, 14, z, bm("barrel[facing=up]"))
        g.set(25, 14, -43, bm("crafting_table")); g.set(20, 14, -36, bm("grindstone[face=floor,facing=north]"))
        for (x, z) in ((-26, -43), (-26, -42), (-19, -37)): g.set(x, 28, z, bm("barrel[facing=up]"))
        g.set(-20, 28, -43, bm("anvil[facing=east]")); g.set(-26, 28, -36, bm("blast_furnace[facing=east,lit=false]"))
        g.box(-26, 28, -38, -26, 29, -38, bm("bookshelf"))
    A.add("deco", annex_deco)
    g.mark("lever", 27, 15, -40, "tower")
    hall(5, 18, -44, 27, -35, 14, 6)
    hall(6, -27, -44, -18, -35, 28, 6)
    mob(20, 14, -42, W); mob(25, 14, -37, PR); chest(26, 14, -43, "Караулка башни", 1)
    mob(-24, 28, -42, W); mob(-20, 28, -37, SP); chest(-26, 28, -40, "Оружейная башни", 1)

    # --- тайник 14: каморка в бастионе, за кладкой в восточной стене башни (рычаг в Караулке)
    A.room("тайник 14", 19, -44, 26, -36, 0, 5, tw, lambda x, y, z: PBB if (x + z) % 2 == 0 else PB)
    A.add("carve", lambda: g.air(17, 0, -40, 18, 2, -38))
    A.add("floor", lambda: g.box(17, -1, -40, 18, -1, -38, PB))

    def stash14():
        g.box(15, 0, -40, 16, 2, -38, CSB)
        for z in (-43, -41, -39): g.box(19, 0, z, 19, 2, z, bm("bookshelf"))
        g.set(24, 0, -44, bm("enchanting_table")); g.set(21, 0, -44, bm("lectern[facing=south]"))
    A.add("deco", stash14)
    g.mark("secret", 15, 0, -40, "tower", (2, 3, 3))
    hall(14, 19, -44, 26, -36, 0, 5)
    chest(25, 0, -39, "Каморка звонаря", 3)

    # ================================================================== шахта: винтовая лестница по стенам (крышка secret `shaft`)
    def ring_cells():
        cells = [(-4, k) for k in range(0, -5, -1)]                                 # запад, на север
        cells += [(k, -4) for k in range(-3, 5)]                                    # север, на восток
        cells += [(4, k) for k in range(-3, 5)]                                     # восток, на юг
        cells += [(k, 4) for k in range(3, -5, -1)]                                 # юг, на запад
        cells += [(-4, k) for k in range(3, 0, -1)]                                 # запад, на север
        return cells

    RING = ring_cells()
    assert len(RING) == 32 and len(set(RING)) == 32

    def inner(dx, dz): return (dx - (1 if dx == 4 else -1 if dx == -4 else 0), dz - (1 if dz == 4 else -1 if dz == -4 else 0))

    def spiral():
        for j in range(21):
            dx, dz = RING[j]
            stand = -1 - j
            if j == 0: face = "west"
            else:
                px, pz = RING[j - 1]
                face = {(-1, 0): "west", (1, 0): "east", (0, -1): "north", (0, 1): "south"}[(px - dx, pz - dz)]
            g.set(dx, stand - 1, CZ + dz, bm(f"{STAIR}[facing={face}]"))
            ix, iz = inner(dx, dz)
            g.set(ix, stand - 1, CZ + iz, PB); g.set(ix, stand, CZ + iz, WALLB)
            if j in (4, 12): g.set(ix, stand + 1, CZ + iz, LAMPU)                       # фонарь на стене-перилах
    A.add("floor", spiral)
    A.rooms.append(("шахта", -5, CZ - 5, 5, CZ + 5, -14, 12))

    # ================================================================== уровень 1: Зал Пепла (стоять на -22)
    def level1():
        S = S1
        FL = S - 1
        A.room("зал 8", -14, -48, 14, -20, S, 8, w1, f1, lights=False)
        for (x, z) in ((-10, -44), (10, -44), (-10, -24), (10, -24), (-10, -34), (10, -34)): A.add("deco", lambda x=x, z=z: g.set(x, S + 8, z, SHL))
        for (x, z) in ((-10, -46), (10, -46), (-10, -22), (10, -22), (-12, -40), (12, -40), (-12, -28), (12, -28)):
            A.add("deco", lambda x=x, z=z: pillar(x, S, S + 6, z, PB))
        A.add("deco", lambda: g.set(-12, S, -27, bm("soul_campfire[lit=false]")))
        g.mark("brazier", -12, S, -27, "1,0,0")
        for (x, y, z, d) in ((15, S + 1, -44, "-x"), (15, S + 1, -24, "-x"), (-15, S + 1, -44, "+x"), (-15, S + 1, -24, "+x")):
            jet(x, y, z, d, 6, 120, (x + z) % 60 + (30 if x > 0 else 0))
        hall(8, -14, -48, 14, -20, S, 8)
        for (x, z, t) in ((-8, -41, W), (8, -41, W), (-8, -27, HD), (8, -27, HD), (12, -34, HD), (-12, -34, SP)): mob(x, S, z, t)

        # восточный коридор «Дыхание»: струи с обеих сторон, дальше дверь `ash_gate`
        A.corr("зал Пепла: восток", 15, -36, 48, -32, S, w1, f1, H=7)
        for i, (x, side) in enumerate(((22, 0), (28, 1), (34, 0), (40, 1), (46, 0))):
            jet(x, S + 1, -37 if side == 0 else -31, "+z" if side == 0 else "-z", 5, 100, (i * 37) % 100)
        A.add("shell", lambda: g.box(49, FL, -37, 50, S + 5, -31, PB))
        A.add("floor", lambda: g.box(49, FL, -36, 50, FL, -32, f1))
        A.add("deco", lambda: g.box(49, S, -36, 50, S + 4, -32, bm("chiseled_polished_blackstone")))
        g.mark("door", 49, S, -36, "ash_gate", (2, 5, 5))

        # северный коридор ловушек и Кладовая (зал 9): ключ уровня
        A.corr("зал Пепла: север", -2, -86, 2, -49, S, w1, f1, H=7)
        for i, (z, side) in enumerate(((-55, 0), (-61, 1), (-67, 0), (-73, 1), (-79, 0))):
            jet(-3 if side == 0 else 3, S + 1, z, "+x" if side == 0 else "-x", 5, 100, (i * 41 + 10) % 100)
        A.room("зал 9", -12, -105, 12, -87, S, 8, w1, f1)
        jet(-13, S + 1, -92, "+x", 25, 100, 0)
        jet(13, S + 1, -98, "-x", 25, 100, 50)

        def vault():
            for (x, z) in ((-9, -90), (9, -90), (-9, -95), (9, -95), (-9, -101), (9, -101)): pillar(x, S, S + 6, z, PB)
            for x in (-3, 3): g.box(x, S, -104, x, S + 2, -104, bm("coal_block"))
        A.add("deco", vault)
        hall(9, -12, -105, 12, -87, S, 8)
        for (x, z, t) in ((-6, -90, SP), (6, -90, W), (-6, -101, W), (6, -101, PR), (0, -95, W)): mob(x, S, z, t)
        chest(0, S, -104, "Кладовая Пепла", 2)

        # западный коридор и Угольный склад (зал 10): рычаг тайника 15
        A.corr("зал Пепла: запад", -47, -36, -15, -32, S, w1, f1, H=7)
        jet(-24, S + 1, -37, "+z", 5, 160, 20)
        A.room("зал 10", -68, -46, -48, -22, S, 8, w1, f1)

        def coal():
            for (x, z) in ((-66, -44), (-64, -44), (-66, -24), (-52, -44), (-52, -24)): g.box(x, S, z, x + 1, S + 1, z, COAL)
            for (x, z) in ((-60, -34), (-58, -30), (-62, -38)): pillar(x, S, S + 5, z, BLK)
            g.set(-68, S + 1, -34, bm("lever[face=wall,facing=east,powered=false]"))
            for (x, z) in ((-66, -30), (-66, -38)): g.set(x, S, z, bm("barrel[facing=up]"))
        A.add("deco", coal)
        g.mark("lever", -68, S + 1, -34, "ashroom")
        hall(10, -68, -46, -48, -22, S, 8)
        for (x, z, t) in ((-62, -42, W), (-56, -26, W), (-64, -28, HD), (-54, -40, HD), (-58, -34, HD)): mob(x, S, z, t)
        chest(-66, S, -42, "Угольный склад", 1)

        # тайник 15: за трещиной в северной стене западного коридора
        A.room("тайник 15", -36, -48, -24, -42, S, 5, w1, f1)
        A.add("carve", lambda: g.air(-31, S, -41, -29, S + 2, -38))
        A.add("floor", lambda: g.box(-31, FL, -41, -29, FL, -38, f1))
        A.add("deco", lambda: g.box(-31, S, -37, -29, S + 2, -37, CPBB))
        g.mark("secret", -31, S, -37, "ashroom", (3, 3, 1))

        def stash15():
            g.box(-35, S, -47, -35, S + 2, -47, bm("bookshelf")); g.box(-33, S, -47, -33, S + 2, -47, bm("bookshelf"))
            g.set(-26, S, -47, bm("cauldron")); g.set(-25, S, -46, COAL)
        A.add("deco", stash15)
        hall(15, -36, -48, -24, -42, S, 5)
        chest(-30, S, -47, "Пепельный тайник", 3)

        # южный коридор и Кузня (зал 11)
        A.corr("зал Пепла: юг", -2, -19, 2, -4, S, w1, f1, H=7)
        A.room("зал 11", -12, -3, 12, 24, S, 8, w1, f1)

        def forge():
            for (x, z) in ((-10, 1), (10, 1), (-10, 20), (10, 20)): g.set(x, S, z, bm("anvil[facing=east]"))
            for x in (-8, -5, 5, 8): g.set(x, S, 22, bm("blast_furnace[facing=north,lit=false]"))
            for x in (-10, 10): g.set(x, S, 10, bm("lava_cauldron"))
            for (x, z) in ((-6, 12), (6, 12), (0, 15)): pillar(x, S, S + 5, z, PB)
            for (x, z) in ((-10, 8), (10, 8), (0, 22)): g.set(x, S, z, bm("barrel[facing=up]"))
        A.add("deco", forge)
        hall(11, -12, -3, 12, 24, S, 8)
        for (x, z, t) in ((-6, 4, W), (6, 4, W), (-8, 14, SP), (8, 14, SP), (-4, 19, PR), (4, 19, GD)): mob(x, S, z, t)
        chest(0, S, 23, "Кузня Пепла", 2)

        # лестница вниз за дверью `ash_gate`: 18 ступеней на восток (стоять -23 .. -40)
        for k in range(18):
            x, stand = 51 + k, S - 1 - k
            A.add("shell", lambda x=x, stand=stand: g.box(x, stand - 2, -36, x, stand + 4, -32, w1))
            A.add("carve", lambda x=x, stand=stand: g.air(x, stand, -35, x, stand + 3, -33))
            A.add("floor", lambda x=x, stand=stand: g.box(x, stand - 1, -35, x, stand - 1, -33, bm(f"{STAIR}[facing=west]")))
            if k % 5 == 2: A.add("deco", lambda x=x, stand=stand: g.set(x, stand + 3, -34, LAMP))
        A.rooms.append(("зал Пепла: лестница вниз", 50, -36, 68, -32, S2, 18))

    # ================================================================== уровень 2: Огненная переправа (стоять на -40)
    HX1, HX2, HZ1, HZ2 = -36, 36, -58, -8
    MOVES = [("W", 2, "C"), ("W", 1, "S"), ("N", 1, "C"), ("N", 1, "G"), ("N", 2, "C"), ("W", 2, "C"), ("W", 1, "S"), ("N", 1, "C"),
             ("N", 1, "S"), ("N", 2, "G"), ("N", 1, "C"), ("N", 2, "I"), ("W", 2, "C"), ("W", 1, "C"), ("W", 2, "G"), ("W", 1, "C"),
             ("S", 1, "S"), ("S", 2, "C"), ("W", 1, "C"), ("W", 1, "S"), ("W", 2, "C"), ("W", 1, "G")]
    HALF = {"S": 1, "C": 1, "G": 1, "I": 3}
    GDIR = {"W": "-x", "E": "+x", "N": "-z", "S": "+z"}
    STEP = {"W": (-1, 0), "E": (1, 0), "N": (0, -1), "S": (0, 1)}

    def path():
        """Цепь платформ: (cx, cz, вид, полуширина, направление следующего шага). Зазор между краями gap (1 или 2 клетки)."""
        cx, cz = 27, -12
        plats = [[cx, cz, "S", 1, None]]
        for i, (dr, gap, kind) in enumerate(MOVES):
            ph, h = plats[-1][3], HALF[kind]
            dx, dz = STEP[dr]
            dist = ph + gap + 1 + h
            cx += dx * dist; cz += dz * dist
            plats[-1][4] = dr
            plats.append([cx, cz, kind, h, None])
        plats[-1][4] = "W"
        return plats

    PLATS = path()

    def level2():
        S = S2
        FL = S - 1
        # посадка и коридоры
        A.room("посадка уровня 2", 69, -37, 75, -31, S, 8, w2, f2)
        A.add("deco", lambda: g.set(73, S, -34, bm("soul_campfire[lit=false]")))
        g.mark("brazier", 73, S, -34, "-1,0,0")
        A.corr("уровень 2: юг", 71, -30, 73, -14, S, w2, f2, H=6)
        A.corr("уровень 2: запад", 37, -14, 73, -10, S, w2, f2, H=6)

        # озеро лавы
        def hall_shell(): g.box(HX1 - 1, -53, HZ1 - 1, HX2 + 1, -29, HZ2 + 1, w2)
        def hall_carve(): g.air(HX1, -47, HZ1, HX2, -30, HZ2)
        def hall_pit():
            g.box(HX1, -52, HZ1, HX2, -52, HZ2, OBS)
            g.box(HX1, -51, HZ1, HX2, -48, HZ2, LAVA)
        A.add("shell", hall_shell); A.add("carve", hall_carve); A.add("pit", hall_pit)
        A.rooms.append(("зал 12", HX1 - 1, HZ1 - 1, HX2 + 1, HZ2 + 1, -47, 18))

        def hall_deco():
            for x in range(HX1 + 6, HX2, 12):
                for z in range(HZ1 + 6, HZ2, 12): g.set(x, -29, z, SHL)
            g.box(30, -52, -16, 36, -41, -8, f2)                                                # восточный выступ (вход)
            g.box(HX1, -52, -44, -31, -41, -36, f2)                                              # западный выступ (выход, 2б)
            for (cx, cz, kind, h, nxt) in PLATS:
                if kind == "I":
                    g.box(cx - 3, -52, cz - 3, cx + 3, -41, cz + 3, scorch)
                    g.box(cx - 3, -41, cz - 3, cx + 3, -41, cz + 3, f2)
                elif kind == "C":
                    g.box(cx - 1, -41, cz - 1, cx + 1, -41, cz + 1, CNB)
                else:
                    g.box(cx - 1, -41, cz - 1, cx + 1, -41, cz + 1, PBB)
                    g.box(cx, -52, cz, cx, -42, cz, BAS)
                    if kind == "G": g.set(cx, -41, cz, MAG)
        A.add("deco", hall_deco)
        for (cx, cz, kind, h, nxt) in PLATS:
            if kind == "C": g.mark("crumble", cx - 1, -41, cz - 1, "cracked_nether_bricks", (3, 1, 3))
            if kind == "G": g.mark("geyser", cx, -41, cz, f"{GDIR[nxt]},150")
        hall(12, HX1, HZ1, HX2, HZ2, S, 10, low=12)
        mob(33, S, -12, SP); mob(33, S, -10, SP)
        for i in (7, 9, 17):
            assert PLATS[i][2] == "S"
            mob(PLATS[i][0], S, PLATS[i][1], SP)
        isl = [p for p in PLATS if p[2] == "I"][0]
        for (dx, dz) in ((-2, -1), (2, 1), (0, 2)): mob(isl[0] + dx, S, isl[1] + dz, WM)
        chest(-35, S, -42, "Огненная переправа", 2)

        # Грот гейзеров (зал 13)
        A.room("зал 13", 46, -30, 62, -18, S, 8, w2, f2)
        A.corr("к гроту 13", 54, -17, 56, -15, S, w2, f2, H=5)
        for (x, z) in ((50, -26), (58, -26), (54, -22)):
            A.add("deco", lambda x=x, z=z: g.set(x, FL, z, MAG))
            g.mark("geyser", x, FL, z, "up,160")

        def grotto():
            for (x, z) in ((47, -29), (48, -29), (61, -29), (60, -29)): g.set(x, FL, z, LAVA)
            g.set(54, S + 1, -30, bm("lever[face=wall,facing=south,powered=false]"))
            for (x, z) in ((47, -19), (61, -19)): pillar(x, S, S + 5, z, BLK)
        A.add("deco", grotto)
        g.mark("lever", 54, S + 1, -30, "ashlava")
        hall(13, 46, -30, 62, -18, S, 8)
        for (x, z, t) in ((50, -20, SP), (58, -20, SP), (47, -23, W), (60, -24, W), (52, -28, HD), (56, -28, HD)): mob(x, S, z, t)
        chest(61, S, -21, "Грот гейзеров", 1)

        # тайник 16: под коридором, за кладкой в южной стене
        A.room("тайник 16", 50, -5, 62, 3, S, 5, w2, f2)
        A.corr("к тайнику 16", 55, -8, 57, -6, S, w2, f2, H=3)
        A.add("deco", lambda: g.box(55, S, -9, 57, S + 2, -9, CNB))
        g.mark("secret", 55, S, -9, "ashlava", (3, 3, 1))

        def stash16():
            for (x, z) in ((51, -4), (61, -4), (51, 2), (61, 2)): g.box(x, S, z, x, S + 3, z, BLK)
            g.set(56, FL, 1, LAVA); g.set(55, FL, 1, LAVA); g.set(57, FL, 1, LAVA)
        A.add("deco", stash16)
        hall(16, 50, -5, 62, 3, S, 5)
        chest(56, S, -4, "Под лавой", 3)

    # ================================================================== 2б: общее
    r2 = random.Random(20261122)       # отдельный генератор: блоки башни и уровней 1-2 от 2б не меняются
    def W2R(*_): return r2.choice([NB, NB, NB, CNB, RNB, BLK])                                  # стена уровня 2 для достроек 2б
    def W3(*_): return r2.choice([BLK, BLK, PBB, CPBB, BAS, RNB])                               # уровень 3
    def F3(x, y, z): return PBB if (x + z) % 2 == 0 else BLK
    def W4(*_): return r2.choice([BLK, BLK, PBB, COAL, CPBB, BAS])                              # уровень 4 и тоннель
    def F4(x, y, z): return BLK if (x + z) % 2 == 0 else PB
    def W5(*_): return r2.choice([BAS, BAS, BLK, SBAS, bm("tuff")])                             # Гнездо
    def F5(x, y, z): return r2.choice([BLK, BAS, SBAS, BLK, PB])
    FIRE_COLORS = ("red_concrete", "blue_concrete", "yellow_concrete", "lime_concrete")        # цвет жаровни i (как в DungeonMech.FIRE_COLORS)

    FACE_UP = {("x", 1): "west", ("x", -1): "east", ("z", 1): "north", ("z", -1): "south"}      # лестница вниз по ходу d: «вверх» — против хода

    def stairs_run(name, axis, c, a_start, d, stand0, n, wall):
        """Лестница шириной 3 вниз на n ступеней вдоль оси (центр поперёк — c): k-я ступень в клетке a_start + d*(k-1), стоять на stand0 - k. Стены 5 в ширину, потолок +4."""
        face = FACE_UP[(axis, d)]
        def at(a, l): return (a, l) if axis == "x" else (l, a)
        for k in range(1, n + 1):
            a, stand = a_start + d * (k - 1), stand0 - k
            def shell(a=a, stand=stand):
                (x1, z1), (x2, z2) = at(a, c - 2), at(a, c + 2)
                g.box(x1, stand - 2, z1, x2, stand + 4, z2, wall)
            def carve(a=a, stand=stand):
                (x1, z1), (x2, z2) = at(a, c - 1), at(a, c + 1)
                g.air(x1, stand, z1, x2, stand + 3, z2)
            def floor(a=a, stand=stand):
                (x1, z1), (x2, z2) = at(a, c - 1), at(a, c + 1)
                g.box(x1, stand - 1, z1, x2, stand - 1, z2, bm(f"{STAIR}[facing={face}]"))
            A.add("shell", shell); A.add("carve", carve); A.add("floor", floor)
            if k % 5 == 2:
                (lx, lz) = at(a, c)
                A.add("deco", lambda lx=lx, lz=lz, stand=stand: g.set(lx, stand + 3, lz, LAMP))
        a_end = a_start + d * (n - 1)
        (x1, z1), (x2, z2) = at(min(a_start, a_end), c - 2), at(max(a_start, a_end), c + 2)
        A.rooms.append((name, x1, z1, x2, z2, stand0 - n, n + 5))

    # ================================================================== уровень 3: Зал Четырёх Жаровен (стоять на -56)
    FIRES = ((-81, -48), (-65, -48), (-81, -32), (-65, -32))    # NW, NE, SW, SE: жаровни 0..3
    FRESCO_X0, FRESCO_Z = -80, -57                              # четыре панели 3x5 на северной стене зала 17

    def level3():
        S = S3
        FL = S - 1
        # --- выход с западного уступа уровня 2: проём в стене зала 12 (x=-37) и 16 ступеней на запад
        A.add("shell", lambda: g.box(-37, S2 - 2, -42, -37, S2 + 4, -38, W2R))
        A.add("carve", lambda: g.air(-37, S2, -41, -37, S2 + 3, -39))

        def arch():
            for z in (-42, -38): g.box(-36, S2, z, -36, S2 + 4, z, CHB)
            g.box(-36, S2 + 4, -41, -36, S2 + 4, -39, CHB)
        A.add("deco", arch)
        stairs_run("лестница к залу 17", "x", -40, -38, -1, S2, 16, W2R)
        A.corr("к залу 17", -56, -41, -54, -39, S, W3, F3, H=5)

        # --- зал 17: головоломка
        HX1, HZ1, HX2, HZ2 = -89, -56, -57, -24
        A.room("зал 17", HX1, HZ1, HX2, HZ2, S, 10, W3, F3)
        hall(17, HX1, HZ1, HX2, HZ2, S, 10)

        def fires():
            for i, (bx, bz) in enumerate(FIRES):                                    # площадка 5x5, цветной квадрат 3x3 и постамент с костром
                g.box(bx - 2, FL, bz - 2, bx + 2, FL, bz + 2, PB)
                g.box(bx - 1, FL, bz - 1, bx + 1, FL, bz + 1, bm(FIRE_COLORS[i]))
                g.box(bx, S, bz, bx, S + 1, bz, CHB)
                g.set(bx, S + 2, bz, bm("campfire[lit=false]"))
            g.box(FRESCO_X0 - 1, S + 1, FRESCO_Z, FRESCO_X0 + 15, S + 7, FRESCO_Z, CHB)        # рама фрески
            for k in range(4): g.box(FRESCO_X0 + 4 * k, S + 2, FRESCO_Z, FRESCO_X0 + 4 * k + 2, S + 6, FRESCO_Z, PB)   # заготовки: роспись ставит движок
            for (x, z) in ((-86, -53), (-60, -53), (-86, -27), (-60, -27), (-73, -27)): pillar(x, S, S + 8, z, PB)
            g.box(-77, S, -23, -73, S + 4, -23, CHB)                                # рама спуска (южная стена)
            g.box(-76, S, -23, -74, S + 3, -23, OBS)                                # заслонка спуска: открывается, когда горят все четыре
        A.add("deco", fires)
        for i, (bx, bz) in enumerate(FIRES): g.mark("braziers", bx, S + 2, bz, f"fires,b,{i}")
        for k in range(4): g.mark("braziers", FRESCO_X0 + 4 * k, S + 2, FRESCO_Z, f"fires,f,{k}", (3, 5, 1))
        for n, (x, z) in enumerate(((-79, -44), (-67, -44), (-79, -36), (-67, -36), (-73, -52), (-73, -29))): g.mark("braziers", x, S, z, f"fires,w,{n}")
        g.mark("secret", -76, S, -23, "fires", (3, 4, 1))
        A.add("deco", lambda: g.set(-58, S, -46, bm("soul_campfire[lit=false]")))
        g.mark("brazier", -58, S, -46, "-1,0,0")                                    # жаровня-якорь №3

        # --- север: зал 18 (рычаг тайника 21)
        A.room("зал 18", -94, -78, -80, -64, S, 7, W3, F3)
        A.corr("к залу 18", -88, -63, -86, -57, S, W3, F3, H=5)

        def room18():
            g.set(-79, S + 1, -71, bm("lever[face=wall,facing=west,powered=false]"))
            for (x, z) in ((-93, -77), (-93, -65), (-81, -77)): g.set(x, S, z, bm("barrel[facing=up]"))
            g.set(-92, S, -70, bm("anvil[facing=north]"))
            for (x, z) in ((-90, -74), (-84, -68)): pillar(x, S, S + 5, z, BLK)
        A.add("deco", room18)
        g.mark("lever", -79, S + 1, -71, "emberroom")
        hall(18, -94, -78, -80, -64, S, 7)
        for (x, z, t) in ((-92, -76, W), (-83, -76, PR), (-91, -66, SP)): mob(x, S, z, t)
        chest(-93, S, -67, "Склад жаровен", 1)

        # --- юг: зал 19 и секретная каморка 21 (за трещиной в восточной стене, рычаг в зале 18)
        A.room("зал 19", -66, -17, -52, -3, S, 7, W3, F3)
        A.corr("к залу 19", -60, -23, -58, -18, S, W3, F3, H=5)

        def room19():
            for (x, z) in ((-65, -16), (-53, -16), (-65, -4)): g.set(x, S, z, bm("barrel[facing=up]"))
            for x in (-64, -62): g.box(x, S, -4, x, S + 2, -4, bm("bookshelf"))
            for (x, z) in ((-59, -13), (-57, -7)): pillar(x, S, S + 5, z, BLK)
        A.add("deco", room19)
        hall(19, -66, -17, -52, -3, S, 7)
        for (x, z, t) in ((-64, -14, W), (-54, -14, PR), (-55, -5, W)): mob(x, S, z, t)
        chest(-65, S, -5, "Угольная кладовая", 1)
        A.room("зал 21", -48, -13, -42, -7, S, 5, W3, F3)
        A.add("carve", lambda: g.air(-51, S, -11, -49, S + 2, -9))
        A.add("floor", lambda: g.box(-51, FL, -11, -49, FL, -9, F3))
        A.add("deco", lambda: g.box(-51, S, -11, -49, S + 2, -9, CPBB))
        g.mark("secret", -51, S, -11, "emberroom", (3, 3, 3))

        def room21():
            for z in (-12, -8): g.box(-47, S, z, -47, S + 2, z, bm("bookshelf"))
            g.set(-43, S, -12, bm("cauldron")); g.set(-43, S, -8, COAL); g.set(-45, S, -7, bm("lectern[facing=north]"))
        A.add("deco", room21)
        hall(21, -48, -13, -42, -7, S, 5)
        chest(-45, S, -12, "Угольный тайник", 3)

    # ================================================================== уровень 4: арена Хранителя (стоять на -64)
    AX1, AZ1, AX2, AZ2, ACX, ACZ = -95, -14, -73, 8, -84, -3

    def level4():
        S = S4
        stairs_run("спуск к арене", "z", -75, -22, 1, S3, 8, W4)
        A.room("арена Хранителя", AX1, AZ1, AX2, AZ2, S, 11, W4, F4)

        def arena():
            for x in range(ACX - 5, ACX + 6):                                       # помост в центре
                for z in range(ACZ - 5, ACZ + 6):
                    r = math.hypot(x - ACX, z - ACZ)
                    if r <= 5.2: g.set(x, S, z, CO if 3.4 <= r <= 4.2 else (COAL if (x + z) % 2 == 0 else PB))
            for (dx, dz) in ((9, 0), (-9, 0), (0, -9), (6, 6), (-6, 6), (6, -6), (-6, -6)):
                pillar(ACX + dx, S, S + 8, ACZ + dz, PB)
            for (dx, dz) in ((6, 6), (-6, 6), (6, -6), (-6, -6)):                  # огни на угловых колоннах
                if g.cells.get((ACX + dx, S + 8, ACZ + dz)) == PB: g.set(ACX + dx, S + 9, ACZ + dz, bm("soul_campfire[lit=true]"))
            g.box(-72, S, 3, -71, S + 3, 5, CHB)                                    # дверь Гнезда (метка door ember_gate)
            g.box(-72, S + 1, 4, -71, S + 2, 4, bm("gilded_blackstone"))
            g.box(-77, S, -15, -73, S + 4, -15, CHB)                                # рама входной печати: проём 3x4 остаётся воздухом
            g.box(-76, S, -15, -74, S + 3, -15, AIR)
        A.add("deco", arena)
        g.mark("seal", -76, S, -15, "20,obsidian", (3, 4, 1))
        g.mark("door", -72, S, 3, "ember_gate", (2, 4, 3))
        hall(20, AX1, AZ1, AX2, AZ2, S, 11)
        mob(ACX, S + 1, ACZ, KP)                                                    # Хранитель Углей (мини-босс, AshMobsB.Keeper)
        for (x, z) in ((-91, -10), (-77, -10), (-91, 4), (-77, 4)): mob(x, S, z, W)
        chest(ACX, S, 7, "Хранилище Углей", 2)

        # --- тоннель к Гнезду: после двери, дальше 10 ступеней вниз
        A.corr("тоннель к Гнезду", -71, 3, -8, 5, S, W4, F4, H=5)
        A.add("deco", lambda: g.set(-67, S, 4, bm("soul_campfire[lit=false]")))
        g.mark("brazier", -67, S, 4, "1,0,0")                                       # жаровня-якорь №4
        stairs_run("лестница в Гнездо", "x", 4, -7, 1, S, 10, W4)

    # ================================================================== уровень 5: Гнездо и колодец-выход (стоять на -74)
    NX, NZ, NR = 34, 4, 30        # круглая пещера R=30
    WX, WZ = 20, 0                # колодец: 7x7 снаружи, 5x5 внутри; выходит во двор башни (двор x -26..26, z -52..4)
    RING5 = [(2, 0), (2, -1), (2, -2), (1, -2), (0, -2), (-1, -2), (-2, -2), (-2, -1), (-2, 0), (-2, 1), (-2, 2), (-1, 2), (0, 2), (1, 2), (2, 2), (2, 1)]
    WELL_STEPS = -S5              # ступень j стоит на S5 + j; последняя (j = 74) — на y=0, вровень с двором

    def cave_h(r): return 4 + int(11 * math.sqrt(max(0.0, 1.0 - (r / NR) ** 2)))

    def level5():
        S = S5
        FL = S - 1
        A.corr("вход в Гнездо", 3, 3, 5, 5, S, W5, F5, H=5)
        A.add("deco", lambda: g.set(6, S, 6, bm("soul_campfire[lit=false]")))
        g.mark("brazier", 6, S, 6, "1,0,0")                                         # жаровня-якорь №5

        COLS = []
        for x in range(NX - NR - 2, NX + NR + 3):
            for z in range(NZ - NR - 2, NZ + NR + 3):
                r = math.hypot(x - NX, z - NZ)
                if r <= NR: COLS.append((x, z, r, cave_h(r)))

        def shell():
            for (x, z, r, h) in COLS: g.box(x, S - 2, z, x, S + h + 3, z, W5)       # дно, стенка и кровля в 4 слоя
            for x in range(NX - NR - 3, NX + NR + 4):
                for z in range(NZ - NR - 3, NZ + NR + 4):
                    r = math.hypot(x - NX, z - NZ)
                    if NR < r <= NR + 1.8: g.box(x, S - 2, z, x, S + 8, z, W5)
        A.add("shell", shell)

        def carve():
            for (x, z, r, h) in COLS: g.air(x, S, z, x, S + h - 1, z)
        A.add("carve", carve)
        A.add("floor", lambda: [g.set(x, FL, z, F5(x, FL, z)) for (x, z, r, h) in COLS])
        hall(23, NX - NR, NZ - NR, NX + NR, NZ + NR, S, 15)
        A.rooms.append(("Гнездо", NX - NR - 1, NZ - NR - 1, NX + NR + 1, NZ + NR + 1, S, 15))
        # 4.10: босс Пепельный Феникс. Метка в центре на помосте (S+1), печать в проёме входа (x 3, z 3..5, высота коридора 5),
        # сундук с добычей ставит победа (Dungeons.lairWon). Яйцо из пепла рисует Boss5 (block_display), в шаблоне его нет
        g.mark("boss", NX, S + 1, NZ, "phoenix")
        g.mark("seal", 3, S, 3, "23,obsidian", (1, 5, 3))
        chest(NX + 10, S, NZ + 3, "Гнездо Феникса", 2)

        LAVA_POOLS = ((NX + 8, NZ + 22), (NX + 22, NZ + 10), (NX - 4, NZ - 22))

        def nest_deco():
            for x in range(NX - 8, NX + 9):                                         # помост у яйца
                for z in range(NZ - 8, NZ + 9):
                    r = math.hypot(x - NX, z - NZ)
                    if r <= 7.5: g.set(x, S, z, PB if r <= 4.8 else (COAL if (x + z) % 3 == 0 else BLK))
            GRAY, LGRAY = bm("gray_concrete"), bm("light_gray_concrete")
            for dy in range(0, 11):                                                 # бывшее яйцо из пепла: блоков не ставим (его рисует Boss5), r2 крутим как раньше,
                                                                                    # чтобы сталагмиты и стены колодца остались на тех же местах
                t = (dy - 5) / 5.5
                rad = 3.6 * math.sqrt(max(0.0, 1 - t * t)) * (1.12 - 0.16 * t)
                for x in range(NX - 4, NX + 5):
                    for z in range(NZ - 4, NZ + 5):
                        d2 = math.hypot(x - NX, z - NZ)
                        if d2 > rad: continue
                        if d2 < 1.3 and 3 <= dy <= 7: b = SHL
                        elif rad - d2 < 0.9 and r2.random() < 0.22: b = MAG
                        else: b = r2.choice([GRAY, LGRAY, LGRAY, GRAY])
            for (lx, lz) in LAVA_POOLS:                                             # лавовые лужки в обсидиановых бортах
                for x in range(lx - 4, lx + 5):
                    for z in range(lz - 4, lz + 5):
                        d2 = (x - lx) ** 2 + (z - lz) ** 2
                        if d2 <= 4.5: g.set(x, FL, z, LAVA)
                        elif d2 <= 10.5: g.set(x, FL, z, OBS)
            placed, tries = 0, 0
            while placed < 22 and tries < 400:                                      # сталагмиты (вход и подход к колодцу свободны)
                tries += 1
                ang, rr = r2.random() * 2 * math.pi, 9 + r2.random() * 19
                x, z = int(NX + rr * math.cos(ang)), int(NZ + rr * math.sin(ang))
                if x <= 28 and -9 <= z <= 13: continue
                if math.hypot(x - WX, z - WZ) < 9 or math.hypot(x - NX, z - NZ) < 9.5 or near_mark(x, z, 3): continue
                if any((x - lx) ** 2 + (z - lz) ** 2 <= 30 for (lx, lz) in LAVA_POOLS): continue
                hh = min(cave_h(math.hypot(x - NX, z - NZ)) - 2, 3 + r2.randrange(6))
                g.box(x, S, z, x, S + hh, z, BAS)
                placed += 1
            for ring, n in ((8.5, 6), (15, 10), (22, 14)):                          # светильники в кровле
                for i in range(n):
                    ang = 2 * math.pi * (i + (0.5 if ring == 15 else 0.0)) / n
                    x, z = int(round(NX + ring * math.cos(ang))), int(round(NZ + ring * math.sin(ang)))
                    if math.hypot(x - WX, z - WZ) < 6: continue
                    g.set(x, S + cave_h(math.hypot(x - NX, z - NZ)), z, SHL)
        A.add("deco", nest_deco)

    def well():
        """Колодец-выход: башня 7x7 в пещере и над ней, центральный столб 3x3 и винтовая лестница по кольцу шириной 1 (75 ступеней), выход вровень с двором.
        С поверхности вход в Гнездо закрыт: внизу лестница упирается в глухую дверь, открывает её рычаг снаружи, из Гнезда (секрет `wellgate`)."""
        S = S5
        perim = [(dx, dz) for dx in range(-3, 4) for dz in range(-3, 4) if max(abs(dx), abs(dz)) == 3]
        def WALL_W(*_): return r2.choice([PBB, PBB, BLK, CPBB])
        A.add("shell", lambda: g.box(WX - 3, S - 2, WZ - 3, WX + 3, -2, WZ + 3, WALL_W))
        A.add("carve", lambda: g.air(WX - 2, S, WZ - 2, WX + 2, 0, WZ + 2))

        def walls():                                                                # стены внутри пещеры (кровлю пещеры carve пещеры уже снёс)
            for (dx, dz) in perim: g.box(WX + dx, S - 1, WZ + dz, WX + dx, -2, WZ + dz, WALL_W)
        A.add("floor", walls)
        open_cells = {RING5[7], RING5[8], RING5[9], RING5[10]}                      # последние четыре ступени: выход открыт (над 71-й нужен запас в 3 блока)

        def spiral():
            g.box(WX - 1, S, WZ - 1, WX + 1, -2, WZ + 1, PB)                        # центральный столб: перил нет, упасть с лестницы некуда
            for dx in range(-2, 3):                                                 # устье закрыто вровень со двором, кроме последних ступеней
                for dz in range(-2, 3):
                    if (dx, dz) not in open_cells: g.set(WX + dx, -1, WZ + dz, PB)
            for j in range(1, WELL_STEPS + 1):
                dx, dz = RING5[j % 16]
                px, pz = RING5[(j - 1) % 16]
                face = {(1, 0): "east", (-1, 0): "west", (0, -1): "north", (0, 1): "south"}[(dx - px, dz - pz)]       # вверх идём по ходу кольца
                stand = S + j
                g.set(WX + dx, stand - 1, WZ + dz, bm(f"{STAIR}[facing={face}]"))
                if j % 9 == 4 and stand + 1 <= -3:                                  # светильник во внутренней стенке кольца
                    ix, iz = dx - (1 if dx == 2 else -1 if dx == -2 else 0), dz - (1 if dz == 2 else -1 if dz == -2 else 0)
                    g.set(WX + ix, stand + 1, WZ + iz, SHL)
            for (px, pz) in ((WX - 5, WZ - 2), (WX - 5, WZ + 2)):                   # столбы-светильники у выхода на поверхность
                g.box(px, 0, pz, px, 2, pz, PBB); g.set(px, 3, pz, LAMPU)
        A.add("floor", spiral)

        def gate():                                                                 # глухая дверь на востоке (к яйцу), рычаг рядом снаружи
            for z in (WZ - 2, WZ + 2): g.box(WX + 3, S, z, WX + 3, S + 3, z, CHB)
            g.box(WX + 3, S + 3, WZ - 1, WX + 3, S + 3, WZ + 1, CHB)
            g.box(WX + 3, S, WZ - 1, WX + 3, S + 2, WZ + 1, OBS)
            g.set(WX + 4, S + 1, WZ + 2, bm("lever[face=wall,facing=east,powered=false]"))
        A.add("deco", gate)
        g.mark("lever", WX + 4, S + 1, WZ + 2, "wellgate")
        g.mark("secret", WX + 3, S, WZ - 1, "wellgate", (1, 3, 3))
        A.rooms.append(("колодец", WX - 3, WZ - 3, WX + 3, WZ + 3, S, WELL_STEPS + 2))

    level1()
    level2()
    level3(); level4(); level5(); well()
    A.run()
    g.rift = A
    g.ash_path = PLATS
    return g
