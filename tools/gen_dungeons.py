#!/usr/bin/env python3
"""
Генератор шаблонов данжей (.nbt) для мода explosionwand.

    python tools/gen_dungeons.py          # записать .nbt и текстуру осколка в resources/
    python tools/gen_dungeons.py --check  # перегенерировать в памяти, сравнить с файлами и проверить ID блоков/мобов

Данж строится в одной общей сетке «координаты данжа»:
    начало (0,0,0) = центр входа, y=0 — первый воздух над землёй, x — на восток, z — на юг.
Потом сетка режется на части (по плоскостям z=SPLITS) — по одной .nbt на часть. Так проверяется «структура из нескольких частей».
Формат .nbt — как у шаблонов структур Minecraft (DataVersion, size, palette, blocks, entities), поэтому файл можно загрузить
в игре командой /place template explosionwand:<имя>. Свои теги:
    ew_origin  — [x,y,z] смещение угла части от начала данжа
    ew_marks   — метки: t (тип), a (аргумент), p [x,y,z] (в координатах части), s [sx,sy,sz]
Типы меток: start, door (p+s: блоки двери), brazier (p: жаровня; a: "dx,dy,dz" смещение до точки возрождения),
            hall (p+s: зал, a: номер), spawn (p: точка моба, a: тип сущности или моб Разлома explosionwand:<вид> — см. RIFT_MOBS; лучник на колонне из аметиста), chest (p: сундук награды, a: "Название|тир": тир 1..3 — набор временной добычи),
            boss (p: центр площадки, где просыпается босс при пробуждении зала, a: вид — BOSS_KINDS; зал должен быть с печатью seal).
Механики (фаза 4.2, движок DungeonMech):
            steps (p+s: ступени над провалом, появляются при приближении; a: блок),
            beams (p: плитка-ловушка; a: "dx,dy,dz" смещение до кристалла, из которого бьёт луч),
            mirror (p: блок; a: "id,роль,сторона": роль src|mir|rcv, сторона N|E|S|W; поворот зеркала ПКМ, приёмник открывает secret с тем же id),
            platform (p+s: платформа в начальном положении; a: "+x|-x|+z|-z,длина,блок"; ходит туда-сюда),
            lever (p: рычаг; a: id), secret (p+s: стена, исчезает по id от lever или решённой головоломки mirror),
            seal (p+s: проём двери; a: "номер зала,блок"; закрывается, пока зал разбужен и не зачищен).
Пепельное святилище (фаза 4.8, tools/gen_ash.py) добавляет метки:
            bell (p: блок колокола; a: id секрета — ПКМ по колоколу открывает его),
            jets (p: сопло в стене — наблюдатель лицом в проход; a: "±x|±z,длина,период,сдвиг" — струя огня в проход по таймеру),
            crumble (p+s: платформа; a: блок — осыпается через 1,5 с после шага игрока и восстанавливается),
            geyser (p: блок-жерло; a: "±x|±z|up,период" — гейзер подбрасывает игрока над ним).
Достройка Святилища (4.8, шаг 2б):
            braziers (головоломка «Четыре жаровни», a: "id,роль,n"): роль b (p: костёр campfire[lit=false], n = 0..3 — жаровня), f (p+s: панель фрески, n = 0..3 —
                     место в порядке; роспись ставит движок по порядку, который считается из позиции данжа), w (p: точка, где при ошибке появляется волна мобов);
                     верный порядок открывает secret с тем же id, ошибка гасит жаровни.
Святилище строит модуль tools/gen_ash.py (метки spawn: ванильные husk/skeleton/blaze/magma_cube).
Город Спящих (фаза 4.5) строит модуль tools/gen_city.py (метки spawn: explosionwand:<вид> — мобы Города, см. RIFT_MOBS; заглушек stub:<вид> больше нет).
Файлы детерминированы (gzip без времени), поэтому --check сравнивает байты.
"""
import gzip, io, json, math, random, struct, sys, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resources" / "data" / "explosionwand" / "structures"
TEX = ROOT / "resources" / "assets" / "explosionwand" / "textures" / "item"
DATA_VERSION = 3465  # Minecraft 1.20.1
SPLITS = (24, 100)   # части Разлома режутся по z: z<24 — rift_entrance, 24..99 — rift_mid, z>=100 — rift_deep
PARTS = ("rift_entrance", "rift_mid", "rift_deep")
CITY_SPLITS = (-16, 16)   # части Города Спящих (фаза 4.5): z<-16, -16..15, z>=16
CITY_PARTS = ("sleep_north", "sleep_mid", "sleep_south")
ASH_SPLITS = (-52, -16)   # части Пепельного святилища (фаза 4.8): z<-52, -52..-17, z>=-16
ASH_PARTS = ("ash_north", "ash_mid", "ash_south")


# ---------------------------------------------------------------- NBT (минимальный писатель)
def _s(v):
    b = v.encode("utf-8")
    return struct.pack(">H", len(b)) + b


class Tag:
    def __init__(self, tid, payload): self.tid, self.payload = tid, payload


def t_int(v): return Tag(3, struct.pack(">i", v))
def t_str(v): return Tag(8, _s(v))
def t_list(items, tid=None):
    if not items: return Tag(9, struct.pack(">bi", 0, 0))
    tid = tid or items[0].tid
    return Tag(9, struct.pack(">bi", tid, len(items)) + b"".join(i.payload for i in items))
def t_ints(vals): return t_list([t_int(v) for v in vals], 3)
def t_comp(d):
    out = b""
    for k, v in d.items(): out += struct.pack(">b", v.tid) + _s(k) + v.payload
    return Tag(10, out + b"\x00")


def to_gzip(root):
    raw = struct.pack(">b", 10) + _s("") + root.payload
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0, compresslevel=9) as g: g.write(raw)
    return buf.getvalue()


# ---------------------------------------------------------------- сетка блоков
AIR = "minecraft:air"


class Grid:
    def __init__(self):
        self.cells = {}
        self.marks = []  # (t, a, (x,y,z), (sx,sy,sz))

    def set(self, x, y, z, b): self.cells[(x, y, z)] = b

    def box(self, x1, y1, z1, x2, y2, z2, b):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.cells[(x, y, z)] = b(x, y, z) if callable(b) else b

    def air(self, x1, y1, z1, x2, y2, z2): self.box(x1, y1, z1, x2, y2, z2, AIR)

    def mark(self, t, x, y, z, a="", size=(0, 0, 0)): self.marks.append((t, a, (x, y, z), size))


def bm(n): return "minecraft:" + n


S1, S2, S3 = -12, -32, -52      # уровень, на котором стоит игрок: «≈10 / 30 / 50» вниз от земли
LIGHTS = ("shroomlight", "sea_lantern", "lantern", "crying_obsidian", "glowstone", "soul_campfire", "soul_lantern", "campfire", "lava")


class Rift:
    """Строитель в фазах: оболочки -> воздух -> полы -> провалы -> украшения. Так проходы пробивают стены, а соседние комнаты не мешают друг другу."""
    PHASES = ("shell", "carve", "floor", "pit", "deco")

    def __init__(self):
        self.g = Grid()
        self.rng = random.Random(20261020)
        self.ph = {k: [] for k in self.PHASES}
        self.rooms = []   # (имя, x1, z1, x2, z2, S, H) — для проверки света

    def add(self, ph, fn): self.ph[ph].append(fn)

    def run(self):
        for k in self.PHASES:
            for fn in self.ph[k]: fn()

    def room(self, name, x1, z1, x2, z2, S, H, wall, floor, light="shroomlight", lights=True):
        """Помещение: внутри x1..x2, z1..z2, воздух S..S+H-1, пол S-1, потолок S+H (в потолок вставляются светильники)."""
        g, FL = self.g, S - 1
        self.add("shell", lambda: g.box(x1 - 1, FL - 1, z1 - 1, x2 + 1, S + H, z2 + 1, wall))
        self.add("carve", lambda: g.air(x1, S, z1, x2, S + H - 1, z2))
        self.add("floor", lambda: g.box(x1, FL, z1, x2, FL, z2, floor))
        self.rooms.append((name, x1 - 1, z1 - 1, x2 + 1, z2 + 1, S, H))
        if lights:
            def put():
                def pos(a, b):
                    n = max(1, (b - a + 1) // 9)
                    return [a + int((i + 0.5) * (b - a + 1) / n) for i in range(n)]
                for x in pos(x1, x2):
                    for z in pos(z1, z2): g.set(x, S + H, z, bm(light))
            self.add("deco", put)

    def corr(self, name, x1, z1, x2, z2, S, wall, floor, H=4):
        """Коридор (прямоугольник, заходит в стены комнат); вдоль него через 8 блоков висят фонари."""
        g, FL = self.g, S - 1
        self.add("shell", lambda: g.box(x1 - 1, FL - 1, z1 - 1, x2 + 1, S + H, z2 + 1, wall))
        self.add("carve", lambda: g.air(x1, S, z1, x2, S + H - 1, z2))
        self.add("floor", lambda: g.box(x1, FL, z1, x2, FL, z2, floor))
        self.rooms.append((name, x1 - 1, z1 - 1, x2 + 1, z2 + 1, S, H))

        def put():
            lamp = bm("lantern[hanging=true]")
            if x2 - x1 >= z2 - z1:
                xs = list(range(x1 + 3, x2 - 1, 8)) or [(x1 + x2) // 2]
                for x in xs: g.set(x, S + H - 1, (z1 + z2) // 2, lamp)
            else:
                zs = list(range(z1 + 3, z2 - 1, 8)) or [(z1 + z2) // 2]
                for z in zs: g.set((x1 + x2) // 2, S + H - 1, z, lamp)
        self.add("deco", put)

    def stairs(self, name, cx, z0, y0, n, dz, wall):
        """Лестница шириной 3 вдоль z: k-я ступень на z0+dz*k, стоять на ней y0-k; после n ступеней — площадка на y0-n."""
        g = self.g
        face = "north" if dz > 0 else "south"
        for k in range(n):
            z, ys = z0 + dz * k, y0 - 1 - k
            self.add("shell", lambda z=z, ys=ys: g.box(cx - 2, ys - 1, z, cx + 2, ys + 5, z, wall))
            self.add("carve", lambda z=z, ys=ys: g.air(cx - 1, ys + 1, z, cx + 1, ys + 4, z))
            self.add("floor", lambda z=z, ys=ys: g.box(cx - 1, ys, z, cx + 1, ys, z, bm(f"deepslate_brick_stairs[facing={face}]")))
            if k % 4 == 1: self.add("deco", lambda z=z, ys=ys: g.set(cx, ys + 4, z, bm("lantern[hanging=true]")))
        self.rooms.append((name, cx - 2, min(z0, z0 + dz * (n - 1)), cx + 2, max(z0, z0 + dz * (n - 1)), y0 - n, n + 5))


def build_rift():
    """Разлом: ~190x200 блоков, 3 уровня вниз (стоять на y=-12 / -32 / -52). План: docs/concepts/rift_plan.png, залы — docs/world.md."""
    R = Rift()
    g, rng = R.g, R.rng
    SB, CSB, MSB, CHSB = bm("stone_bricks"), bm("cracked_stone_bricks"), bm("mossy_stone_bricks"), bm("chiseled_stone_bricks")
    DB, CDB, DT, PD = bm("deepslate_bricks"), bm("cracked_deepslate_bricks"), bm("deepslate_tiles"), bm("polished_deepslate")
    PBB, CPBB, PB, BS = bm("polished_blackstone_bricks"), bm("cracked_polished_blackstone_bricks"), bm("polished_blackstone"), bm("blackstone")
    CO, AM, OBS, CAL = bm("crying_obsidian"), bm("amethyst_block"), bm("obsidian"), bm("calcite")
    CLU, SL, WATER, GLASS = bm("amethyst_cluster[facing=up]"), bm("sea_lantern"), bm("water"), bm("light_blue_stained_glass")

    def w1(*_): return rng.choice([SB, SB, SB, CSB, MSB])
    def w2(*_): return rng.choice([DB, DB, DB, DB, CDB, DT])
    def w3(*_): return rng.choice([PBB, PBB, PBB, CPBB, BS])
    def f1(x, y, z): return SB if (x + z) % 2 == 0 else bm("polished_andesite")
    def f2(x, y, z): return DT if (x + z) % 2 == 0 else PD
    def f3(x, y, z): return PB if (x + z) % 2 == 0 else BS
    def pit_floor(*_): return CO if rng.randrange(8) == 0 else OBS

    def hall(n, x1, z1, x2, z2, S, H, low=0):
        """Метка зала: низ — на `low` блоков ниже пола (провал), верх — потолок."""
        g.mark("hall", x1, S - 1 - low, z1, str(n), (x2 - x1 + 1, H + low + 2, z2 - z1 + 1))

    SH, AR, SD, PR, EA, GO = ("explosionwand:" + n for n in ("shardling", "crystal_archer", "rift_shade", "prism", "echo_eater", "amethyst_golem"))

    def mobs(S, lst):
        """Метки мобов зала. Лучник стоит на колонне (блок аметиста в слое deco), метка — на её верхе."""
        for (x, z, t) in lst:
            if t == AR:
                R.add("deco", lambda x=x, z=z, S=S: g.set(x, S, z, AM))
                g.mark("spawn", x, S + 1, z, t)
            else: g.mark("spawn", x, S, z, t)

    def chest(x, y, z, name, tier): g.mark("chest", x, y, z, f"{name}|{tier}")

    def near_mark(x, z, r=1):
        """Рядом с меткой моба/сундука/жаровни/рычага: сюда колонны и препятствия не ставим."""
        return any(t in ("spawn", "chest", "brazier", "start", "lever", "mirror") and abs(p[0] - x) <= r and abs(p[2] - z) <= r for (t, a, p, s) in g.marks)

    def cluster(x, y, z):
        if not near_mark(x, z, 0): g.set(x, y, z, CLU)

    def pillar(x, y1, y2, z, blk):
        if not near_mark(x, z): g.box(x, y1, z, x, y2, z, blk)

    # =================================================================== вход на поверхности
    R.add("shell", lambda: g.box(-14, -7, -23, 14, -2, -5, SB))                       # фундамент площадки
    R.add("carve", lambda: g.air(-14, 0, -23, 14, 9, -5))                              # расчистка: деревья, холм
    R.add("floor", lambda: g.box(-13, -1, -22, 13, -1, -5, w1))                        # плитка площадки
    R.room("вход", -7, -3, 7, 8, 0, 6, w1, f1, lights=False)
    R.stairs("лестница к залу 1", 0, 9, 0, 12, 1, w1)

    def crack():
        # «трещина»: длинная расщелина вдоль x с проходом посередине; по краям торчат кристаллы
        for x in list(range(-30, -5)) + list(range(6, 31)):
            z1, z2 = -14, -13
            if rng.randrange(4) == 0: z1 = -15
            if rng.randrange(5) == 0: z2 = -12
            g.air(x, -5, z1, x, -1, z2)
            g.box(x, -6, z1, x, -6, z2, rng.choice([AM, AM, CAL, OBS]))
            if rng.randrange(3) == 0: cluster(x, -5, rng.choice([z1, z2]))
    R.add("pit", crack)

    def entrance():
        for sx in (-8, 8):
            for sz in (-4, 9): g.box(sx, 0, sz, sx, 6, sz, CHSB)                      # угловые колонны
        g.box(-2, 0, -4, -2, 3, -4, DB); g.box(2, 0, -4, 2, 3, -4, DB); g.box(-2, 3, -4, 2, 3, -4, DB)   # арка
        g.box(-1, 0, -4, 1, 2, -4, AM)                                                 # дверь из кристалла
        g.mark("door", -1, 0, -4, "rift", (3, 3, 1))
        for sx in (-3, 3):
            g.set(sx, 0, -5, CO); g.set(sx, 1, -5, CO); g.set(sx, 2, -5, bm("lantern[hanging=false]"))   # стойки у двери
        for (px, pz) in ((-12, -6), (12, -6), (-12, -20), (12, -20), (-9, -10), (9, -10)):               # обломки колонн
            g.box(px, 0, pz, px, rng.choice((1, 2, 3)), pz, w1)
        for (lx, lz) in ((-5, -1), (5, -1), (-5, 6), (5, 6), (0, 6)):
            g.set(lx, 5, lz, bm("lantern[hanging=true]"))
        g.set(5, 0, -1, bm("soul_campfire[lit=false]"))                                # якорь №0
        for (px, pz) in ((-6, -9), (7, -9), (-4, -17), (4, -19), (10, -18), (-10, -18), (-2, -7), (2, -11), (0, -21)):
            cluster(px, 0, pz)
    g.mark("brazier", 5, 0, -1, "-1,0,0")
    g.mark("start", 0, 0, -8)
    R.add("deco", entrance)

    # =================================================================== уровень 1: Верхние пещеры (стоять на -12)
    def level1():
        S = S1
        FL = S - 1
        R.corr("в зал 1", -1, 21, 1, 25, S, w1, f1)
        # зал 1: Зал Осколков, провал на всю ширину z 40..49, ступени-кристаллы
        R.room("зал 1", -28, 26, 28, 62, S, 7, w1, f1)
        PIT = FL - 6
        R.add("shell", lambda: g.box(-29, PIT - 1, 39, 29, FL - 1, 50, w1))

        def pit1():
            g.air(-28, PIT + 1, 40, 28, FL, 49)
            g.box(-28, PIT, 40, 28, PIT, 49, pit_floor)
            for i in range(5):                                                         # пандус для упавших: вдоль западной стены на север
                z, top = 40 + i, FL - 1 - i
                g.box(-28, PIT + 1, z, -27, top - 1, z, DB)
                g.box(-28, top, z, -27, top, z, bm("deepslate_brick_stairs[facing=north]"))
            for (x, z) in ((-20, 43), (-14, 46), (-6, 41), (6, 47), (14, 42), (20, 45), (-10, 48), (10, 44)): cluster(x, PIT + 1, z)
        R.add("pit", pit1)
        for (px, pz) in ((-22, 30), (22, 30), (-22, 36), (22, 36), (-22, 54), (22, 54), (-22, 60), (22, 60)):
            R.add("deco", lambda px=px, pz=pz: (g.box(px, S, pz, px, S + 5, pz, DB), g.set(px, S + 3, pz, CO)))
        for (x, z) in ((-1, 41), (0, 44), (-1, 47)): g.mark("steps", x, FL, z, "amethyst_block", (2, 1, 2))
        hall(1, -28, 26, 28, 62, S, 7, low=6)
        mobs(S, [(-12, 31, SH), (12, 31, SH), (-14, 56, AR), (14, 56, AR)])

        # пещера Осколочников (зал 8), в ней рычаг тайника
        R.corr("в пещеру 8", -45, 31, -29, 33, S, w1, f1)
        R.room("зал 8", -68, 28, -46, 50, S, 8, w1, f1)                              # 23x23 (было 43x31)
        g.mark("lever", -68, S, 44, "arch")
        g.mark("secret", -58, S, 51, "arch", (3, 3, 1))

        def cave():
            for _ in range(8):
                x, z = rng.randrange(-66, -48), rng.randrange(30, 49)
                if (x >= -52 and 30 <= z <= 35) or (-61 <= x <= -53 and z >= 44): continue
                pillar(x, S, S + rng.randrange(2, 6), z, rng.choice([CAL, DB, CAL]))
            for (x, z) in ((-64, 34), (-60, 40), (-56, 34), (-50, 46)): cluster(x, S, z)
            g.box(-69, S - 1, 43, -69, S + 1, 45, MSB)                                  # обычная кладка у рычага (не подсказка)
            g.set(-68, S, 44, bm("lever[face=wall,facing=east,powered=false]"))
            g.box(-58, S, 51, -56, S + 2, 51, CSB)                                      # трещина в кладке
        R.add("deco", cave)
        hall(8, -68, 28, -46, 50, S, 8)
        mobs(S, [(-64, 32, SH), (-58, 38, SH), (-52, 38, SH), (-62, 46, SH), (-66, 40, SH), (-50, 44, SH)])
        chest(-66, S, 30, "Пещера Осколочников", 1)

        # затопленный грот (зал 9): уступ у входа, дальше вода, на дне сундук
        R.corr("в грот 9", 29, 31, 51, 33, S, w1, f1)
        R.room("зал 9", 52, 32, 77, 57, S, 9, w1, f1)                                # 26x26 (было 39x39)
        R.add("shell", lambda: g.box(57, S - 6, 31, 78, FL - 1, 58, w1))

        def grotto():
            for x in range(58, 78):
                top = {58: FL - 1, 59: FL - 2, 60: FL - 3}.get(x, FL - 4)             # блок дна; ступени уходят под воду
                g.box(x, top, 32, x, top, 57, DB)
                g.box(x, top + 1, 32, x, FL, 57, WATER)
            for (x, z) in ((64, 36), (70, 44), (75, 36), (66, 54), (74, 54)): g.set(x, FL - 4, z, SL)
        R.add("pit", grotto)
        hall(9, 52, 32, 77, 57, S, 9, low=4)
        mobs(S - 4, [(66, 38, "drowned"), (72, 46, "drowned"), (68, 52, "drowned"), (75, 40, "drowned")])
        chest(72, S - 4, 54, "Затопленный грот", 1)

        # зал 2: Кристальный сад — арена с колоннами и волнами
        R.corr("в зал 2", -1, 63, 1, 87, S, w1, f1)
        R.room("зал 2", -34, 88, 34, 136, S, 9, w1, f1)
        for gx in (-24, -12, 12, 24):
            for gz in (96, 108, 120, 130):
                R.add("deco", lambda gx=gx, gz=gz: (g.box(gx, S, gz, gx, S + 7, gz, AM), g.set(gx, S + 8, gz, CO), g.set(gx, S, gz, DB)))
        R.add("deco", lambda: [cluster(x, S, z) for (x, z) in ((-30, 92), (30, 92), (-30, 134), (30, 134), (-18, 102), (18, 102), (-6, 116), (6, 116),
                                                               (-18, 126), (18, 126), (-30, 112), (30, 112), (-4, 92), (4, 92))])
        hall(2, -34, 88, 34, 136, S, 9)
        mobs(S, [(-18, 100, SH), (18, 100, SH), (-18, 114, SH), (18, 114, SH), (-30, 106, AR), (30, 106, AR),
                 (-30, 124, SH), (30, 124, SH), (-18, 130, AR), (18, 130, AR), (0, 110, SH), (0, 124, SH)])
        chest(0, S, 133, "Кристальный сад", 1)

        # гнездо Теней (зал 10)
        R.corr("в гнездо 10", 35, 118, 51, 120, S, w1, f1)
        R.room("зал 10", 52, 106, 77, 131, S, 8, w1, f1)                             # 26x26 (было 39x39)

        def nest():
            for _ in range(14):                                                        # паутина у потолка и по углам
                g.set(rng.randrange(53, 77), S + rng.choice((6, 7)), rng.randrange(107, 131), bm("cobweb"))
            for (x, z) in ((54, 108), (75, 108), (54, 129), (75, 129), (75, 118)):
                g.set(x, S, z, bm("cobweb")); g.set(x, S + 1, z, bm("cobweb"))
            for _ in range(6):
                pillar(rng.randrange(56, 75), S, S + rng.randrange(1, 4), rng.randrange(109, 129), BS)
        R.add("deco", nest)
        hall(10, 52, 106, 77, 131, S, 8)
        mobs(S, [(58, 111, SD), (70, 111, SD), (62, 124, SH), (72, 124, SH), (66, 129, SD), (58, 118, SH)])
        chest(74, S, 128, "Гнездо Теней", 1)

        # тайник Архимага (зал 14, секретный): за трещиной в кладке пещеры
        R.corr("тайник 14: ход", -58, 52, -56, 61, S, w1, f1)
        R.room("зал 14", -62, 62, -52, 72, S, 6, w1, f1)                             # каморка 11x11 (было 37x29)

        def stash():
            for z in (64, 66, 68, 70): g.box(-61, S, z, -61, S + 2, z, bm("bookshelf"))
            g.set(-60, S, 72, bm("lectern")); g.set(-56, S, 72, bm("enchanting_table"))
            g.box(-53, S, 64, -53, S + 2, 64, bm("bookshelf")); g.box(-53, S, 66, -53, S + 2, 66, bm("bookshelf"))
            for (x, z) in ((-59, 64), (-54, 70)): cluster(x, S, z)
        R.add("deco", stash)
        hall(14, -62, 62, -52, 72, S, 6)
        chest(-53, S, 70, "Тайник Архимага", 3)

        # лестница на 2-й уровень
        R.corr("из зала 2", -1, 137, 1, 140, S, w1, f1)
        R.stairs("лестница на уровень 2", 0, 141, S, 20, 1, w2)

    # =================================================================== уровень 2: Зеркальные залы (стоять на -32)
    def level2():
        S = S2
        FL = S - 1
        R.corr("посадка уровня 2", -1, 161, 1, 163, S, w2, f2)
        R.add("shell", lambda: g.box(1, FL - 1, 160, 4, S + 4, 163, w2))               # ниша жаровни №1
        R.add("carve", lambda: g.air(2, S, 161, 3, S + 3, 162))
        R.add("floor", lambda: g.box(2, FL, 161, 3, FL, 162, f2))
        R.add("deco", lambda: (g.set(3, S, 161, bm("soul_campfire[lit=false]")), g.set(3, S + 3, 161, bm("lantern[hanging=true]"))))
        g.mark("brazier", 3, S, 161, "-1,0,0")

        # зал 3: Зеркальный коридор с плитками-лучами
        R.room("зал 3", -22, 164, 22, 172, S, 8, w2, f2)
        plates = []
        for i, x in enumerate((-7, -10, -13, -16, -19)):
            win = {0: 164, 1: 170, 2: 164, 3: 170, 4: 167}[i]
            plates += [(x, z) for z in range(164, 173) if not win <= z <= win + 2]
        for i, x in enumerate((9, 13, 17)):
            win = {0: 170, 1: 164, 2: 167}[i]
            plates += [(x, z) for z in range(164, 173) if not win <= z <= win + 2]

        def hall3():
            for (x, z) in plates:
                g.set(x, FL, z, bm("shroomlight"))
                g.set(x, S + 2, 173 if z <= 168 else 163, AM)
            for x in range(-21, 22, 6):
                for wz in (163, 173): g.set(x, S + 5, wz, SL)
        R.add("deco", hall3)
        for (x, z) in plates: g.mark("beams", x, FL, z, f"0,3,{(173 if z <= 168 else 163) - z}")
        hall(3, -22, 164, 22, 172, S, 8)
        mobs(S, [(2, 166, PR), (-3, 170, SD), (4, 172, PR), (-1, 164, SD)])

        # зал 4: Зал Отражений — зеркала открывают дверь на мост
        R.corr("в зал 4: запад", -40, 166, -23, 168, S, w2, f2)
        R.corr("в зал 4: вверх", -41, 147, -39, 165, S, w2, f2)
        R.room("зал 4", -64, 102, -16, 146, S, 9, w2, f2)

        def hall4():
            def stand(x, z, blk, a):
                g.set(x, S, z, PD)
                g.set(x, S + 1, z, blk)
                g.mark("mirror", x, S + 1, z, a)
            stand(-60, 106, CO, "refl,src,E")
            stand(-40, 106, bm("purple_glazed_terracotta[facing=north]"), "refl,mir,N")
            stand(-40, 126, bm("purple_glazed_terracotta[facing=west]"), "refl,mir,W")
            stand(-24, 126, bm("purple_glazed_terracotta[facing=east]"), "refl,mir,E")
            stand(-24, 110, SL, "refl,rcv,E")
            for (x, z) in ((-56, 116), (-32, 116), (-32, 136), (-56, 136), (-20, 140), (-20, 104), (-48, 142)):
                g.box(x, S, z, x, S + 7, z, DB); g.set(x, S + 4, z, CO)
            for (x, z) in ((-62, 120), (-62, 130), (-18, 118), (-18, 132), (-50, 122), (-28, 140)): cluster(x, S, z)
            g.box(-15, S, 124, -15, S + 2, 126, CDB)                                    # дверь на мост: открывается решённой головоломкой
            g.mark("secret", -15, S, 124, "refl", (1, 3, 3))
        R.add("deco", hall4)
        hall(4, -64, 102, -16, 146, S, 9)
        mobs(S, [(-50, 112, PR), (-30, 112, SD), (-56, 128, SD), (-36, 132, PR), (-22, 120, SD), (-46, 140, SH)])

        # логово Тени (зал 11, элита)
        R.corr("в логово Тени 11", -60, 77, -58, 101, S, w2, f2)
        R.room("зал 11", -72, 56, -46, 76, S, 9, w2, f2)                             # 27x21 (было 55x37)

        def shadow():
            for _ in range(6):
                x, z = rng.randrange(-70, -48), rng.randrange(58, 75)
                if -62 <= x <= -56 and z >= 70: continue
                pillar(x, S, S + rng.randrange(2, 7), z, BS)
            for _ in range(10):
                g.set(rng.randrange(-71, -47), S + rng.choice((7, 8)), rng.randrange(57, 76), bm("cobweb"))
        R.add("deco", shadow)
        hall(11, -72, 56, -46, 76, S, 9)
        mobs(S, [(-68, 60, SD), (-60, 62, AR), (-50, 60, SD), (-52, 72, AR), (-68, 70, SD), (-48, 66, SH), (-62, 68, GO)])
        chest(-70, S, 58, "Логово Тени", 2)

        # склад храма (зал 12)
        R.corr("на склад 12: восток", 23, 166, 50, 168, S, w2, f2)
        R.corr("на склад 12: вверх", 49, 151, 51, 165, S, w2, f2)
        R.room("зал 12", 36, 129, 65, 150, S, 8, w2, f2)                             # 30x22 (было 57x37)

        def store():
            for x in range(40, 63, 5):
                for z in (133, 141):
                    if rng.randrange(3) == 0: continue
                    for (bx, bz) in ((x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)):
                        pillar(bx, S, S + 1, bz, bm("barrel"))
            for z in range(131, 143, 5): g.box(37, S, z, 37, S + 2, z, bm("bookshelf"))
            for (x, z) in ((39, 131), (63, 131), (39, 148), (63, 148)): cluster(x, S, z)
        R.add("deco", store)
        hall(12, 36, 129, 65, 150, S, 8)
        mobs(S, [(40, 137, SH), (52, 131, AR), (60, 137, SH), (58, 146, AR), (46, 145, PR)])
        chest(62, S, 146, "Склад храма", 2)

        # зал 4 -> мост: проход за дверью
        R.corr("к мосту: от зала 4", -15, 124, -7, 126, S, w2, f2)
        R.corr("к мосту: север", -9, 74, -7, 126, S, w2, f2)
        R.corr("к мосту: в зал 5", -9, 74, 1, 76, S, w2, f2)

        # зал 5: Мост над бездной, две движущиеся платформы
        R.room("зал 5", 2, 60, 88, 90, S, 10, w2, f2)
        PIT = FL - 8
        R.add("shell", lambda: g.box(14, PIT - 1, 59, 66, FL - 1, 91, w2))

        def pit5():
            for (x1, x2) in ((15, 34), (46, 65)):
                g.air(x1, PIT + 1, 60, x2, FL, 90)
                g.box(x1, PIT, 60, x2, PIT, 90, pit_floor)
            for x0 in (15, 46):                                                        # пандусы для упавших: вдоль южной стены, ведут на запад
                for i in range(7):
                    x, top = x0 + i, FL - 1 - i
                    g.box(x, PIT + 1, 89, x, top - 1, 90, DB)
                    g.box(x, top, 89, x, top, 90, bm("deepslate_brick_stairs[facing=west]"))
            for (x, z) in ((24, 66), (28, 80), (52, 70), (60, 84), (30, 64), (56, 64)): cluster(x, PIT + 1, z)
        R.add("pit", pit5)
        g.mark("platform", 15, FL, 74, "+x,18,crying_obsidian", (2, 1, 2))
        g.mark("platform", 46, FL, 74, "+x,18,crying_obsidian", (2, 1, 2))
        g.mark("lever", 86, S + 1, 60, "fall")
        g.mark("secret", 82, S, 59, "fall", (3, 3, 1))

        def hall5():
            for (x, z) in ((4, 62), (4, 88), (12, 62), (12, 88), (38, 62), (42, 88), (68, 88), (86, 88)):
                pillar(x, S, S + 8, z, DB); g.set(x, S + 5, z, CO)
            # «водопад»: бак с водой за стеклянным окном в северной стене (вода заперта, не течёт)
            g.box(66, FL, 56, 70, S + 9, 59, w2)
            g.box(67, S, 57, 69, S + 7, 58, WATER)
            g.box(67, S + 1, 59, 69, S + 6, 59, GLASS)
            g.box(81, S, 59, 85, S + 2, 59, w2)
            g.box(82, S, 59, 84, S + 2, 59, CDB)                                        # проход за водопадом
            g.set(86, S + 1, 60, bm("lever[face=wall,facing=south,powered=false]"))
            g.set(86, S, 60, bm("soul_lantern[hanging=false]"))
        R.add("deco", hall5)
        hall(5, 2, 60, 88, 90, S, 10, low=8)
        mobs(S, [(8, 68, SD), (8, 84, PR), (40, 68, PR), (40, 84, SD), (74, 66, PR), (80, 82, SD), (72, 84, SH)])

        # лестница на 3-й уровень: из зала 5 на север
        R.corr("к лестнице вниз", 74, 57, 76, 59, S, w2, f2)
        R.stairs("лестница на уровень 3", 75, 56, S, 20, -1, w3)

        # за водопадом (зал 15, секретный)
        R.corr("за водопадом: ход", 82, 38, 84, 58, S, w2, f2)
        R.room("зал 15", 78, 27, 88, 37, S, 6, w2, f2)                               # каморка 11x11 (было 39x34)

        def fall():
            for (x, z) in ((79, 29), (87, 29)):
                g.box(x, S, z, x, S + 4, z, DB); g.set(x, S + 3, z, CO)
            for (x, z) in ((80, 35), (86, 35), (83, 30)): cluster(x, S, z)
            for x in (80, 83, 86): g.set(x, S + 5, 27, SL)
        R.add("deco", fall)
        hall(15, 78, 27, 88, 37, S, 6)
        chest(83, S, 29, "За водопадом", 3)

    # =================================================================== уровень 3: Сердце (стоять на -52)
    def level3():
        S = S3
        FL = S - 1
        R.room("посадка уровня 3", 70, 32, 80, 36, S, 6, w3, f3)
        R.add("deco", lambda: g.set(79, S, 34, bm("soul_campfire[lit=false]")))
        g.mark("brazier", 79, S, 34, "-1,0,0")

        # зал 6: Преддверие — волны, дверь за спиной закрывается
        R.add("carve", lambda: g.air(74, S, 31, 76, S + 2, 31))
        R.add("floor", lambda: g.box(74, FL, 31, 76, FL, 31, f3))
        g.mark("seal", 74, S, 31, "6,polished_blackstone_bricks", (3, 3, 1))
        R.room("зал 6", 40, 4, 88, 30, S, 9, w3, f3)
        g.mark("lever", 88, S, 8, "u1")

        def hall6():
            for (x, z) in ((46, 8), (46, 26), (58, 8), (58, 26), (70, 8), (82, 26), (70, 26)):
                pillar(x, S, S + 7, z, BS); g.set(x, S + 4, z, CO)
            for (x, z) in ((44, 14), (84, 14), (64, 6), (64, 28)): cluster(x, S, z)
            g.set(88, S, 8, bm("lever[face=wall,facing=west,powered=false]"))
        R.add("deco", hall6)
        hall(6, 40, 4, 88, 30, S, 9)
        mobs(S, [(48, 12, EA), (48, 22, SH), (62, 12, SH), (62, 22, EA), (76, 12, AR), (76, 22, SH),
                 (84, 18, SH), (54, 18, AR), (68, 18, EA), (80, 8, SH)])
        chest(64, S, 5, "Преддверие", 1)

        # под мостом (зал 16, секретный)
        R.corr("под мостом: восток", 81, 33, 92, 35, S, w3, f3)
        R.corr("под мостом: юг", 91, 36, 93, 61, S, w3, f3)
        R.add("floor", lambda: g.box(82, S, 33, 82, S + 2, 35, PBB))
        g.mark("secret", 82, S, 33, "u1", (1, 3, 3))
        R.room("зал 16", 87, 62, 97, 72, S, 6, w3, f3)                               # каморка 11x11 (было 39x39)

        def under():
            for (x, z) in ((88, 64), (96, 64), (88, 71), (96, 71)):
                g.box(x, S, z, x, S + 4, z, BS); g.set(x, S + 3, z, CO)
            for (x, z) in ((89, 67), (95, 67), (92, 70)): cluster(x, S, z)
            for x in (88, 91, 94): g.box(x, S, 72, x, S + 1, 72, bm("budding_amethyst"))
        R.add("deco", under)
        hall(16, 87, 62, 97, 72, S, 6)
        chest(92, S, 68, "Под мостом", 3)

        # главный коридор: Преддверие -> Логово, ответвление в Камеру кристаллов
        R.corr("из зала 6 на запад", -39, 16, 39, 18, S, w3, f3)
        R.corr("к Логову", -31, 19, -29, 80, S, w3, f3)
        R.add("shell", lambda: g.box(-29, FL - 1, 59, -26, S + 4, 63, w3))             # ниша жаровни №3
        R.add("carve", lambda: g.air(-28, S, 60, -27, S + 3, 62))
        R.add("floor", lambda: g.box(-28, FL, 60, -27, FL, 62, f3))
        R.add("deco", lambda: (g.set(-27, S, 61, bm("soul_campfire[lit=false]")), g.set(-27, S + 3, 61, bm("lantern[hanging=true]"))))
        g.mark("brazier", -27, S, 61, "-1,0,0")
        R.room("зал 13", -65, 8, -40, 29, S, 8, w3, f3)                              # 26x22 (было 49x31)

        def chamber():
            for _ in range(10):
                x, z = rng.randrange(-63, -42), rng.randrange(10, 28)
                if 14 <= z <= 20 and x >= -50: continue
                pillar(x, S, S + rng.randrange(1, 5), z, AM)
            for (x, z) in ((-63, 10), (-48, 27), (-62, 27), (-47, 10)): cluster(x, S, z)
        R.add("deco", chamber)
        hall(13, -65, 8, -40, 29, S, 8)
        mobs(S, [(-61, 12, AR), (-55, 26, SH), (-50, 11, PR), (-58, 21, AR), (-62, 19, SH), (-46, 26, PR)])
        chest(-63, S, 27, "Камера кристаллов", 2)

        # Логово Сердца (зал 7): круглый зал над бездной (кольцо, бездна глубиной 5, круглая площадка, мост с севера)
        CX, CZ, RR, HH = -30, 108, 28, 14
        PIT = FL - 5

        def d(x, z): return math.hypot(x - CX, z - CZ)

        def in_pit(dd): return 9.5 <= dd < 20.5

        def lair_shell():
            for x in range(CX - RR - 2, CX + RR + 3):
                for z in range(CZ - RR - 2, CZ + RR + 3):
                    dd = d(x, z)
                    if dd <= RR + 0.5:
                        g.set(x, (PIT if in_pit(dd) else FL) - 1, z, w3())
                        g.set(x, S + HH, z, w3())
                    elif dd <= RR + 1.5:
                        g.box(x, FL - 1, z, x, S + HH, z, w3)

        def lair_carve():
            for x in range(CX - RR, CX + RR + 1):
                for z in range(CZ - RR, CZ + RR + 1):
                    dd = d(x, z)
                    if dd <= RR + 0.5: g.air(x, (PIT + 1) if in_pit(dd) else S, z, x, S + HH - 1, z)

        def lair_floor():
            for x in range(CX - RR, CX + RR + 1):
                for z in range(CZ - RR, CZ + RR + 1):
                    dd = d(x, z)
                    if dd > RR + 0.5: continue
                    g.set(x, PIT if in_pit(dd) else FL, z, pit_floor() if in_pit(dd) else f3(x, FL, z))

        def lair_deco():
            g.box(CX - 1, FL, 88, CX + 1, FL, 99, PBB)                                  # мост через бездну
            for sgn in (1, -1):                                                        # пандусы для упавших: по бокам, к кольцу
                for i in range(5):
                    x, top = CX + sgn * (20 - i), FL - 1 - i
                    g.box(x, PIT + 1, CZ - 1, x, top - 1, CZ + 1, BS)
                    g.box(x, top, CZ - 1, x, top, CZ + 1, bm(f"polished_blackstone_brick_stairs[facing={'east' if sgn == 1 else 'west'}]"))
            for k in range(12):                                                         # колонны кольца
                ang = math.radians(k * 30 + 15)
                px, pz = int(round(CX + 24 * math.cos(ang))), int(round(CZ + 24 * math.sin(ang)))
                if not near_mark(px, pz): g.box(px, S, pz, px, S + 9, pz, AM); g.set(px, S + 10, pz, CO)
            for k in range(8):                                                          # свет в потолок
                ang = math.radians(k * 45)
                g.set(int(round(CX + 24 * math.cos(ang))), S + HH, int(round(CZ + 24 * math.sin(ang))), bm("shroomlight"))
                g.set(int(round(CX + 14 * math.cos(ang + 0.4))), S + HH, int(round(CZ + 14 * math.sin(ang + 0.4))), bm("shroomlight"))
            g.set(CX, S + HH, CZ, bm("shroomlight"))
            for (x, z) in ((CX + 5, CZ), (CX - 5, CZ), (CX, CZ + 5), (CX, CZ - 5)): cluster(x, S, z)
            for _ in range(14):
                ang, rad = rng.random() * 6.283, 11 + rng.random() * 9
                x, z = int(round(CX + rad * math.cos(ang))), int(round(CZ + rad * math.sin(ang)))
                if in_pit(d(x, z)) and abs(x - CX) > 2 and abs(z - CZ) > 2: cluster(x, PIT + 1, z)
        R.add("shell", lair_shell); R.add("carve", lair_carve); R.add("floor", lair_floor); R.add("deco", lair_deco)
        R.rooms.append(("зал 7", CX - RR - 1, CZ - RR - 1, CX + RR + 1, CZ + RR + 1, S, HH))
        R.add("floor", lambda: g.box(CX - 1, FL, 72, CX + 1, FL, 72, f3))               # проём двери логова (закрывает движок)
        g.mark("seal", CX - 1, S, 72, "7,polished_blackstone_bricks", (3, 3, 1))
        hall(7, CX - RR, CZ - RR, CX + RR, CZ + RR, S, HH, low=5)
        # 4.4: обычных мобов в логове нет, зал будит босс (метка boss: центр круглой площадки, a = вид босса); сундук там же, после победы
        g.mark("boss", CX, S, CZ, "rift_heart")
        chest(CX, S, CZ, "Логово Сердца", 2)

    level1()
    level2()
    level3()
    R.run()
    g.rift = R
    return g


def build_mech_test():
    """Тестовый стенд механик (в мире не ставится): ступени, лучи, зеркала, платформа, рычаг, дверь-печать. Образец для настоящего Разлома."""
    g = Grid()
    SB, CSB = bm("stone_bricks"), bm("cracked_stone_bricks")
    AM, CO, SL = bm("amethyst_block"), bm("crying_obsidian"), bm("sea_lantern")
    g.box(-8, -6, -2, 44, -1, 54, SB)                      # основание
    g.air(-8, 0, -2, 44, 4, 54)                            # расчистка над землёй
    # оболочки комнат (стены y 0..3), потом выемки
    rooms = [(-6, 0, 6, 13), (8, 0, 12, 12), (14, 0, 26, 12), (27, 17, 42, 24), (-6, 24, 6, 32), (2, 32, 8, 37), (-2, 32, 2, 40), (-7, 40, 7, 52)]
    for (x1, z1, x2, z2) in rooms: g.box(x1, 0, z1, x2, 3, z2, SB)
    for (x1, z1, x2, z2) in rooms: g.air(x1 + 1, 0, z1 + 1, x2 - 1, 3, z2 - 1)
    # A: зал с провалом, ступени появляются по очереди
    g.air(-5, -5, 2, 5, -1, 11)                            # провал на всю ширину зала: в обход не пройти
    for (x, z) in ((-1, 3), (0, 6), (-1, 9)): g.mark("steps", x, -1, z, "amethyst_block", (2, 1, 2))
    g.mark("start", 0, 0, 1)
    # B: коридор плиток-ловушек (плитка, смещение до кристалла в стене)
    for (px, pz, dx, cx) in ((10, 3, -2, 8), (9, 7, 3, 12), (11, 9, -3, 8)):
        g.set(px, -1, pz, bm("shroomlight")); g.set(cx, 1, pz, AM)
        g.mark("beams", px, -1, pz, f"{dx},2,0")
    # C: головоломка зеркал: src на восток, два зеркала, приёмник; решение: M1 на юг, M2 на восток
    g.set(15, 1, 2, CO); g.mark("mirror", 15, 1, 2, "m1,src,E")
    g.set(21, 1, 2, bm("purple_glazed_terracotta[facing=north]")); g.mark("mirror", 21, 1, 2, "m1,mir,N")
    g.set(21, 1, 8, bm("purple_glazed_terracotta[facing=west]")); g.mark("mirror", 21, 1, 8, "m1,mir,W")
    g.set(25, 1, 8, SL); g.mark("mirror", 25, 1, 8, "m1,rcv,E")
    g.box(19, 0, 12, 21, 2, 12, AM); g.mark("secret", 19, 0, 12, "m1", (3, 3, 1))
    # D: движущаяся платформа через провал
    g.air(30, -5, 18, 39, -1, 23)                          # провал на всю ширину зала
    g.mark("platform", 30, -1, 20, "+x,8,crying_obsidian", (2, 1, 2))
    # E: рычаг и фальшивая стена в тайник
    g.air(-1, 0, 32, 1, 2, 32)
    g.set(-3, 1, 25, bm("lever[face=wall,facing=south,powered=false]")); g.mark("lever", -3, 1, 25, "s1")
    g.box(4, 0, 32, 5, 2, 32, CSB); g.mark("secret", 4, 0, 32, "s1", (2, 3, 1))
    # F: дверь-печать и логово
    g.air(-1, 0, 40, 1, 2, 40)
    g.mark("seal", -1, 0, 40, "7,deepslate_bricks", (3, 3, 1))
    g.mark("hall", -6, 0, 41, "7", (13, 4, 11))
    for (x, z, t) in ((-3, 47, "zombie"), (3, 47, "zombie"), (0, 50, "skeleton"), (-4, 44, "zombie")): g.mark("spawn", x, 0, z, t)
    g.mark("chest", 0, 0, 51, "Тест механик")
    return g


# ---------------------------------------------------------------- разрезание на части и запись
def split(g, splits=SPLITS, names=PARTS):
    parts = {n: {} for n in names}
    for pos, b in g.cells.items(): parts[part_of(pos, splits, names)][pos] = b
    return parts


def part_of(pos, splits=SPLITS, names=PARTS): return names[sum(1 for s in splits if pos[2] >= s)]


def encode(name, cells, marks):
    xs = [p[0] for p in cells]; ys = [p[1] for p in cells]; zs = [p[2] for p in cells]
    mn = (min(xs), min(ys), min(zs)); mx = (max(xs), max(ys), max(zs))
    size = (mx[0] - mn[0] + 1, mx[1] - mn[1] + 1, mx[2] - mn[2] + 1)
    palette, index = [], {}
    for pos in sorted(cells):
        b = cells[pos]
        if b not in index: index[b] = len(palette); palette.append(b)
    pal_tags = []
    for b in palette:
        d = {}
        if "[" in b:
            name_, props = b[:-1].split("[")
            d["Name"] = t_str(name_)
            d["Properties"] = t_comp({k: t_str(v) for k, v in (kv.split("=") for kv in props.split(","))})
        else: d["Name"] = t_str(b)
        pal_tags.append(t_comp(d))
    blocks = [t_comp({"pos": t_ints([p[0] - mn[0], p[1] - mn[1], p[2] - mn[2]]), "state": t_int(index[cells[p]])}) for p in sorted(cells)]
    mark_tags = [t_comp({"t": t_str(t), "a": t_str(a), "p": t_ints([p[0] - mn[0], p[1] - mn[1], p[2] - mn[2]]), "s": t_ints(list(s))})
                 for (t, a, p, s) in marks]
    root = t_comp({"DataVersion": t_int(DATA_VERSION), "size": t_ints(list(size)), "palette": t_list(pal_tags, 10),
                   "blocks": t_list(blocks, 10), "entities": t_list([]), "ew_origin": t_ints(list(mn)),
                   "ew_marks": t_list(mark_tags, 10) if mark_tags else t_list([])})
    return to_gzip(root)


def generate():
    g = build_rift()
    parts = split(g)
    files = {}
    for name, cells in parts.items():
        marks = [m for m in g.marks if part_of(m[2]) == name]
        files[name + ".nbt"] = encode(name, cells, marks)
    mg = build_mech_test()
    files["mech_test.nbt"] = encode("mech_test", mg.cells, mg.marks)
    import gen_city
    cg = gen_city.build_city(sys.modules[__name__])
    for name, cells in split(cg, CITY_SPLITS, CITY_PARTS).items():
        marks = [m for m in cg.marks if part_of(m[2], CITY_SPLITS, CITY_PARTS) == name]
        files[name + ".nbt"] = encode(name, cells, marks)
    import gen_ash
    ag = gen_ash.build_ash(sys.modules[__name__])
    for name, cells in split(ag, ASH_SPLITS, ASH_PARTS).items():
        marks = [m for m in ag.marks if part_of(m[2], ASH_SPLITS, ASH_PARTS) == name]
        files[name + ".nbt"] = encode(name, cells, marks)
    return [g, mg, cg, ag], files


# ---------------------------------------------------------------- текстура осколка (16x16 PNG без PIL)
def shard_png():
    pal = {".": (0, 0, 0, 0), "o": (46, 18, 84, 255), "d": (96, 52, 168, 255), "m": (150, 96, 224, 255), "l": (204, 160, 255, 255),
           "w": (244, 232, 255, 255), "g": (255, 214, 90, 255)}
    rows = ["................",
            ".......oo.......",
            "......oddo......",
            "......odmlo.....",
            ".....odmmwlo....",
            ".....odmlwlo....",
            "....odmmlwllo...",
            "....odmmlwllo...",
            "....odmmmllo....",
            "....odddmmlo....",
            ".....oddmmo.....",
            ".....ogoddo.....",
            "......ggoo......",
            "......ggo.......",
            ".......o........",
            "................"]
    raw = b"".join(b"\x00" + b"".join(bytes(pal[c]) for c in r) for r in rows)

    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def temple_seal_png():
    """Печать Храма (4.5): круглая бирюзовая печать с лунным серпом внутри."""
    pal = {".": (0, 0, 0, 0), "o": (14, 40, 48, 255), "c": (38, 140, 150, 255), "l": (150, 230, 225, 255), "m": (24, 70, 92, 255), "w": (240, 255, 252, 255)}
    rows = ["................",
            "......oooo......",
            "....ooccccoo....",
            "...occllllcco...",
            "..occlllmmlcco..",
            "..oclllmwllcco..",
            ".occllmlllllcco.",
            ".occllmlllllcco.",
            ".occllmlllllcco.",
            ".occllmlllllcco.",
            "..ocllmlllllco..",
            "..occlllmmlcco..",
            "...occllllcco...",
            "....ooccccoo....",
            "......oooo......",
            "................"]
    assert all(len(r) == 16 for r in rows) and len(rows) == 16
    raw = b"".join(b"\x00" + b"".join(bytes(pal[c]) for c in r) for r in rows)

    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def ash_key_png():
    """Пепельный ключ (4.8): тёмный ключ с тлеющим кольцом, ключ уровня «Зал Пепла»."""
    pal = {".": (0, 0, 0, 0), "o": (28, 22, 24, 255), "d": (62, 54, 58, 255), "m": (104, 94, 98, 255), "l": (160, 148, 150, 255),
           "e": (255, 120, 32, 255), "f": (255, 196, 80, 255)}
    rows = ["................",
            "....ooooo.......",
            "...oeffeeo......",
            "..oefooofeo.....",
            "..oefo.ofeo.....",
            "..oefooofeo.....",
            "...oeffeeo......",
            "....oooood......",
            ".......odmo.....",
            "........odmo....",
            ".........odmo...",
            ".........omdmo..",
            "........omlmo...",
            "........oodmo...",
            ".........omlo...",
            "..........oo...."]
    assert all(len(r) == 16 for r in rows) and len(rows) == 16
    raw = b"".join(b"\x00" + b"".join(bytes(pal[c]) for c in r) for r in rows)

    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def ember_key_png():
    """Угольный ключ (4.8, 2б): ключ уровня «арена Хранителя», раскалённое кольцо и зубцы-угли."""
    pal = {".": (0, 0, 0, 0), "o": (24, 16, 16, 255), "d": (70, 40, 36, 255), "m": (128, 74, 58, 255), "l": (206, 150, 110, 255),
           "e": (214, 48, 28, 255), "f": (255, 214, 96, 255)}
    rows = ["................",
            "....ooooo.......",
            "...oeffeeo......",
            "..oefooofeo.....",
            "..oefo.ofeo.....",
            "..oefooofeo.....",
            "...oeffeeo......",
            "....oooood......",
            ".......odmo.....",
            "........odmo....",
            "........odmoo...",
            "........omdmeo..",
            ".........odmo...",
            "........oodmeo..",
            ".........omlo...",
            "..........oo...."]
    assert all(len(r) == 16 for r in rows) and len(rows) == 16
    raw = b"".join(b"\x00" + b"".join(bytes(pal[c]) for c in r) for r in rows)

    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# ---------------------------------------------------------------- проверки
# мобы Разлома (RiftMobs, фаза 4.3): id метки spawn -> сколько клеток воздуха нужно над меткой
RIFT_MOBS = {"explosionwand:shardling": 2, "explosionwand:crystal_archer": 5, "explosionwand:rift_shade": 3,
             "explosionwand:prism": 6, "explosionwand:echo_eater": 3, "explosionwand:amethyst_golem": 4,
             # Город Спящих (4.6)
             "explosionwand:lunatic": 2, "explosionwand:drowsy": 3, "explosionwand:stitch_guard": 2, "explosionwand:night_moth": 3,
             "explosionwand:dreamer": 3, "explosionwand:book_guard": 3, "explosionwand:statue_guard": 3, "explosionwand:cradler": 5}
BOSS_KINDS = ("rift_heart", "monolith")   # виды босса для метки boss (4.4)
MECH_TYPES = ("steps", "beams", "mirror", "platform", "lever", "secret", "seal", "bell", "jets", "crumble", "geyser", "braziers")


def validate(g):
    """ID блоков и сущностей по реестру 1.20.1; метки внутри клеток; зал содержит свои метки."""
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import lookup
    lookup.ensure()
    reg = {k: set(v) for k, v in json.load(open(lookup.MAPS / "reg.json", encoding="utf-8")).items() if isinstance(v, list)}
    bad = []
    for b in set(g.cells.values()):
        n = b.split("[")[0]
        if not n.startswith("minecraft:") or n[10:] not in reg["block"]: bad.append("блока нет в реестре: " + b)
    for (t, a, p, s) in g.marks:
        if t == "spawn" and a not in RIFT_MOBS and a not in reg["entity_type"]: bad.append("моба нет в реестре: " + a)
        if t not in ("start", "door", "brazier", "hall", "spawn", "chest", "boss") + MECH_TYPES: bad.append("неизвестная метка: " + t)
        if t == "boss" and a not in BOSS_KINDS: bad.append(f"boss {p}: неизвестный босс '{a}'")
    halls = [(int(a), p, s) for (t, a, p, s) in g.marks if t == "hall"]
    for (t, a, p, s) in g.marks:
        if t in ("spawn", "chest", "boss") and not any(all(h[1][i] <= p[i] < h[1][i] + h[2][i] for i in range(3)) for h in halls):
            bad.append(f"метка {t} {p} вне залов")
    if len({h[0] for h in halls}) != len(halls): bad.append("повтор номеров залов")
    sealed = {int(a.split(",")[0]) for (t, a, p, s) in g.marks if t == "seal"}
    for h in halls:
        inside = lambda p, h=h: all(h[1][i] <= p[i] < h[1][i] + h[2][i] for i in range(3))
        bosses = [m for m in g.marks if m[0] == "boss" and inside(m[2])]
        if len(bosses) > 1: bad.append(f"в зале {h[0]} боссов: {len(bosses)} (нужен один)")
        if bosses and h[0] not in sealed: bad.append(f"зал {h[0]} с боссом без печати seal")
        if bosses and any(m[0] == "spawn" and inside(m[2]) for m in g.marks): bad.append(f"зал {h[0]}: у босса не должно быть обычных мобов")
    chest_halls = {}
    for (t, a, p, s) in g.marks:
        if t == "chest":
            nm, _, tier = a.partition("|")
            if not nm or (tier and tier not in ("1", "2", "3")): bad.append(f"chest {p}: a='{a}', нужно 'Название|1..3'")
            if "'" in nm or '"' in nm: bad.append(f"chest {p}: кавычки в названии")
            for h in halls:
                if all(h[1][i] <= p[i] < h[1][i] + h[2][i] for i in range(3)): chest_halls[h[0]] = chest_halls.get(h[0], 0) + 1
    for n, c in chest_halls.items():
        if c > 1: bad.append(f"в зале {n} сундуков: {c} (нужен один)")
    bad += validate_cells(g)
    bad += validate_mobs(g, halls)
    for h in halls:
        if not (0 < h[0] < 31): bad.append(f"номер зала {h[0]} вне 1..30 (bit-маска int в DungeonData)")
    bad += validate_mech(g, reg, halls)
    return bad


def validate_mobs(g, halls):
    """Мобы Разлома: известные id, над меткой хватает воздуха (лучник стоит на колонне из блока, голем высок), нет двух меток в одной клетке."""
    bad, seen = [], set()
    for (t, a, p, s) in g.marks:
        if t != "spawn": continue
        if p in seen: bad.append(f"spawn {p}: две метки в одной клетке")
        seen.add(p)
        if a.startswith("explosionwand:"):
            if a not in RIFT_MOBS: bad.append(f"spawn {p}: неизвестный моб Разлома {a}"); continue
            for dy in range(RIFT_MOBS[a]):
                if g.cells.get((p[0], p[1] + dy, p[2]), AIR) != AIR: bad.append(f"spawn {p} {a}: над меткой занято (+{dy})")
            if a.endswith("crystal_archer") and g.cells.get((p[0], p[1] - 1, p[2])) != bm("amethyst_block"): bad.append(f"spawn {p}: под лучником нет колонны")
        elif a.startswith("stub:"): bad.append(f"spawn {p}: заглушки мобов убраны, нужен explosionwand:<вид> ({a})")
        else:   # ванильный моб: над меткой свободно (блейз выше), вода тоже считается свободной (утопленники)
            for dy in range(3 if a == "blaze" else 2):
                if g.cells.get((p[0], p[1] + dy, p[2]), AIR) not in (AIR, bm("water")): bad.append(f"spawn {p} {a}: над меткой занято (+{dy})")
    return bad


def validate_cells(g):
    """Разумность построенного: у мобов и сундуков есть пол и свободная клетка, фонари и кристаллы не висят в воздухе, в каждом помещении есть свет."""
    bad = []
    free = (AIR, bm("water"))

    def cell(p): return g.cells.get(p)
    def solid(p): return g.cells.get(p, AIR) not in (AIR, bm("water"))
    for (t, a, p, s) in g.marks:
        if t in ("spawn", "chest", "start", "brazier", "boss") and t != "brazier":
            if cell(p) not in free and not (t == "start" and cell(p) is None): bad.append(f"{t} {p}: клетка занята ({cell(p)})")
            if not solid((p[0], p[1] - 1, p[2])): bad.append(f"{t} {p}: под меткой нет пола")
    for pos, b in g.cells.items():
        if b == bm("lantern[hanging=true]") and not solid((pos[0], pos[1] + 1, pos[2])): bad.append(f"фонарь {pos} висит в воздухе")
        elif b == bm("amethyst_cluster[facing=up]") and not solid((pos[0], pos[1] - 1, pos[2])): bad.append(f"кристалл {pos} стоит в воздухе")
        elif (b.startswith(bm("soul_campfire")) or b.startswith(bm("campfire"))) and not solid((pos[0], pos[1] - 1, pos[2])): bad.append(f"костёр {pos} без пола")
        elif b == bm("lava"):   # лава не должна вытекать: бока и низ — лава или твёрдое
            for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
                n = (pos[0] + d[0], pos[1] + d[1], pos[2] + d[2])
                if g.cells.get(n, AIR) in (AIR, bm("water")): bad.append(f"лава {pos} открыта к {n}"); break
    rift = getattr(g, "rift", None)
    if rift:
        for (name, x1, z1, x2, z2, S, H) in rift.rooms:
            if not any(g.cells.get((x, y, z), "").split("[")[0][10:] in LIGHTS
                       for x in range(x1, x2 + 1) for z in range(z1, z2 + 1) for y in range(S, S + H + 1)):
                bad.append(f"в помещении «{name}» нет света")
    return bad


def validate_mech(g, reg, halls):
    """Форматы и согласованность меток механик (см. шапку файла)."""
    bad = []

    def solid(p): return g.cells.get(p, AIR) != AIR
    def blk(n): return n in reg["block"]
    def box(p, s): return [(p[0] + x, p[1] + y, p[2] + z) for x in range(s[0]) for y in range(s[1]) for z in range(s[2])]
    levers, srcs, rcvs, bells, fires = set(), set(), set(), set(), {}
    for (t, a, p, s) in g.marks:
        if t not in MECH_TYPES: continue
        if t in ("steps", "platform", "secret", "seal", "crumble") and not all(v >= 1 for v in s): bad.append(f"{t} {p}: размер {s}")
        if t == "braziers":
            v = a.split(",")
            if len(v) != 3 or v[1] not in ("b", "f", "w") or not v[2].isdigit(): bad.append(f"braziers {p}: a='{a}', нужно id,b|f|w,n"); continue
            fires.setdefault(v[0], {"b": [], "f": [], "w": []})[v[1]].append((int(v[2]), p, s))
            below_solid = solid((p[0], p[1] - 1, p[2]))
            if v[1] == "b":
                if not g.cells.get(p, AIR).startswith("minecraft:campfire[") or "lit=false" not in g.cells.get(p, AIR): bad.append(f"braziers {p}: на метке b должен стоять незажжённый campfire")
                if not below_solid: bad.append(f"braziers {p}: костёр без постамента")
            elif v[1] == "w":
                if solid(p) or solid((p[0], p[1] + 1, p[2])) or not below_solid: bad.append(f"braziers {p}: точка волны должна быть свободной клеткой с полом")
            else:
                if not all(v2 >= 1 for v2 in s) or sorted(s)[:1] != [1] and min(s[0], s[2]) != 1: bad.append(f"braziers {p}: панель фрески {s}: нужен слой толщиной 1")
                if not all(solid(c) for c in box(p, s)): bad.append(f"braziers {p}: панель фрески должна быть сплошной")
        if t == "steps":
            if not blk(a): bad.append(f"steps {p}: блока нет в реестре: {a}")
            if any(solid(c) for c in box(p, s)): bad.append(f"steps {p}: в шаблоне на месте ступеней должен быть воздух")
        elif t == "beams":
            v = a.split(",")
            if len(v) != 3 or not all(x.lstrip("-").isdigit() for x in v): bad.append(f"beams {p}: a='{a}', нужно dx,dy,dz")
            elif not solid(p) or not solid((p[0] + int(v[0]), p[1] + int(v[1]), p[2] + int(v[2]))): bad.append(f"beams {p}: плитка или кристалл — воздух")
        elif t == "mirror":
            v = a.split(",")
            if len(v) != 3 or v[1] not in ("src", "mir", "rcv") or v[2] not in ("N", "E", "S", "W"): bad.append(f"mirror {p}: a='{a}', нужно id,src|mir|rcv,N|E|S|W")
            else:
                if not solid(p): bad.append(f"mirror {p}: на метке воздух")
                if v[1] == "src": srcs.add(v[0])
                if v[1] == "rcv": rcvs.add(v[0])
        elif t == "platform":
            v = a.split(",")
            if len(v) != 3 or v[0] not in ("+x", "-x", "+z", "-z") or not v[1].isdigit() or int(v[1]) < 1 or not blk(v[2]): bad.append(f"platform {p}: a='{a}', нужно ±x|±z,длина,блок")
            else:
                dx, dz = (1 if v[0] == "+x" else -1 if v[0] == "-x" else 0), (1 if v[0] == "+z" else -1 if v[0] == "-z" else 0)
                for o in range(int(v[1]) + 1):   # дорожка и все положения платформы свободны
                    if any(solid(c) for c in box((p[0] + dx * o, p[1], p[2] + dz * o), s)): bad.append(f"platform {p}: путь занят на шаге {o}")
        elif t == "lever":
            if not g.cells.get(p, AIR).startswith("minecraft:lever"): bad.append(f"lever {p}: на метке не рычаг")
            levers.add(a)
        elif t == "bell":
            if not g.cells.get(p, AIR).startswith("minecraft:bell"): bad.append(f"bell {p}: на метке не колокол")
            bells.add(a)
        elif t == "jets":
            v = a.split(",")
            if len(v) != 4 or v[0] not in ("+x", "-x", "+z", "-z") or not all(x.isdigit() for x in v[1:]) or int(v[1]) < 1 or int(v[2]) < 60: bad.append(f"jets {p}: a='{a}', нужно ±x|±z,длина,период>=60,сдвиг")
            else:
                dx, dz = (1 if v[0] == "+x" else -1 if v[0] == "-x" else 0), (1 if v[0] == "+z" else -1 if v[0] == "-z" else 0)
                if not g.cells.get(p, AIR).startswith("minecraft:observer"): bad.append(f"jets {p}: сопло — не наблюдатель")
                if g.cells.get((p[0] + dx, p[1], p[2] + dz), AIR) != AIR: bad.append(f"jets {p}: перед соплом не воздух")
                if sum(1 for i in range(1, int(v[1]) + 1) if g.cells.get((p[0] + dx * i, p[1], p[2] + dz * i), AIR) == AIR) < int(v[1]) // 2: bad.append(f"jets {p}: линия струи почти вся в стене")
        elif t == "crumble":
            if not blk(a): bad.append(f"crumble {p}: блока нет в реестре: {a}")
            elif not all(g.cells.get(c) == "minecraft:" + a for c in box(p, s)): bad.append(f"crumble {p}: в шаблоне платформа должна быть из {a}")
            if any(solid((c[0], c[1] + dy, c[2])) for c in box(p, s) for dy in (1, 2, 3)): bad.append(f"crumble {p}: над платформой занято")
        elif t == "geyser":
            v = a.split(",")
            if len(v) != 2 or v[0] not in ("+x", "-x", "+z", "-z", "up") or not v[1].isdigit() or int(v[1]) < 60: bad.append(f"geyser {p}: a='{a}', нужно ±x|±z|up,период>=60")
            if not solid(p) or any(solid((p[0], p[1] + dy, p[2])) for dy in (1, 2, 3)): bad.append(f"geyser {p}: жерло не твёрдое или над ним занято")
        elif t == "secret":
            if not all(solid(c) for c in box(p, s)): bad.append(f"secret {p}: в шаблоне стена должна быть сплошной")
        elif t == "seal":
            v = a.split(",")
            if len(v) != 2 or not v[0].isdigit() or not blk(v[1]): bad.append(f"seal {p}: a='{a}', нужно зал,блок")
            elif int(v[0]) not in {h[0] for h in halls}: bad.append(f"seal {p}: нет зала {v[0]}")
            if any(solid(c) for c in box(p, s)): bad.append(f"seal {p}: проём в шаблоне открыт (воздух), закрывает движок")
    for fid, f in fires.items():
        if sorted(n for n, _, _ in f["b"]) != [0, 1, 2, 3] or sorted(n for n, _, _ in f["f"]) != [0, 1, 2, 3] or len(f["w"]) < 3: bad.append(f"braziers '{fid}': нужны жаровни 0..3, панели 0..3 и не менее 3 точек волны")
    for (t, a, p, s) in g.marks:
        if t == "secret" and a not in levers and a not in bells and a not in fires and not (a in srcs and a in rcvs): bad.append(f"secret {p}: нет рычага или головоломки с id '{a}'")
    for i in srcs ^ rcvs: bad.append(f"mirror '{i}': нужны и src, и rcv")
    return bad


def main():
    check = "--check" in sys.argv
    gs, files = generate()
    bad = []
    for g in gs: bad += validate(g)
    tex = shard_png()
    if check:
        for name, data in files.items():
            f = OUT / name
            if not f.exists() or gzip.decompress(f.read_bytes()) != gzip.decompress(data): bad.append(f"{name}: устарел, запустите python tools/gen_dungeons.py")
        f = TEX / "seal_shard_rift.png"
        if not f.exists() or f.read_bytes() != tex: bad.append("seal_shard_rift.png: устарел, запустите python tools/gen_dungeons.py")
        f = TEX / "temple_seal.png"
        if not f.exists() or f.read_bytes() != temple_seal_png(): bad.append("temple_seal.png: устарел, запустите python tools/gen_dungeons.py")
        f = TEX / "ash_key.png"
        if not f.exists() or f.read_bytes() != ash_key_png(): bad.append("ash_key.png: устарел, запустите python tools/gen_dungeons.py")
        f = TEX / "ember_key.png"
        if not f.exists() or f.read_bytes() != ember_key_png(): bad.append("ember_key.png: устарел, запустите python tools/gen_dungeons.py")
        print(f"[gen_dungeons] файлов {len(files)}, проблем {len(bad)}")
        if bad: sys.exit("\n".join(bad))
        return
    if bad: sys.exit("\n".join(bad))
    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in files.items():
        (OUT / name).write_bytes(data); print("записан", name, len(data), "байт")
    TEX.mkdir(parents=True, exist_ok=True)
    (TEX / "seal_shard_rift.png").write_bytes(tex); print("записана текстура seal_shard_rift.png")
    (TEX / "temple_seal.png").write_bytes(temple_seal_png()); print("записана текстура temple_seal.png")
    (TEX / "ash_key.png").write_bytes(ash_key_png()); print("записана текстура ash_key.png")
    (TEX / "ember_key.png").write_bytes(ember_key_png()); print("записана текстура ember_key.png")


if __name__ == "__main__":
    main()
