#!/usr/bin/env python3
"""
Цитадель Затмения (фаза 5.3, постройка): шаблоны .nbt для данжа 1 Ада (Базальтовые пики) и контракт docs/citadel_layout.md.

    python tools/gen_citadel.py            # записать шаблоны resources/.../structures/citadel_*.nbt и docs/citadel_layout.md
    python tools/gen_citadel.py --check    # перегенерировать в памяти, сравнить с файлами, проверить ID, метки и проходимость
    python tools/gen_citadel.py --preview  # + превью (вид сверху, разрезы) в build/citadel_*.png

Только Java-механик тут нет: модуль строит блоки и ставит метки (ew_marks), как tools/gen_ash.py. Формат .nbt и метки - как в gen_dungeons.py
(DataVersion, size, palette, blocks, ew_origin, ew_marks); писатель .nbt вынесен на numpy, потому что гора - ~1 млн блоков.

Координаты (0,0,0) - ось горы на уровне подошвы: y=0 - первый воздух над ровной землёй у подножия (стоять), x - на восток, z - на юг.
Подошва сплошная (y -7..-1), гора - ступенчатый конус: склон r 96..114 поднимается с y=-1 до y=21; ярусы (верхний блок пола):
    двор кольца 1 y=21 (r 66..90), стена кольца 1 r 90..96 до y=34, стена кольца 2 r 58..66 до y=38 (в ней Тёмная галерея),
    терраса Солнечных часов y=29 (r 40..58), башня (кольцо 3) до y=65: Предтронный зал, пустота с лавой и Тронный двор (пол y=63, радиус 33).
Сборка идёт по ярусам; после шаблона код (mod-engineer) расчищает объём над и вокруг горы (см. контракт).
"""
import gzip, io, json, math, struct, sys, zlib
from pathlib import Path
import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import gen_dungeons as gd

ROOT = HERE.parent
OUT = ROOT / "resources" / "data" / "explosionwand" / "structures"
DOC = ROOT / "docs" / "citadel_layout.md"
BUILD = ROOT / "build"

PARTS = tuple(f"citadel_p{i}" for i in range(1, 7))
SPLITS = (-53, -23, 0, 23, 53)   # части по z (по ~250 тыс. блоков): z<-53, -53..-24, -23..-1, 0..22, 23..52, z>=53

O = 116                      # сдвиг индекса: x+O
N = 2 * O + 1                # 233
YMIN, YMAX = -8, 84
NY = YMAX - YMIN + 1
YO = -YMIN

# ---------------------------------------------------------------- уровни (верхний блок пола; стоять на +1)
FY = 21                      # двор кольца 1 и пол галереи
W1 = 34                      # верх стены кольца 1 (ходовая дорожка)
W2 = 38                      # верх стены кольца 2
SY = 29                      # терраса Солнечных часов, пол Предтронного зала и башен
CY = 63                      # пол Тронного двора
ARENA_R = 33

ID_AIR = 1
PAL = ["", "minecraft:air"]
PIDX = {"minecraft:air": 1}


def pid(b):
    if ":" not in b: b = "minecraft:" + b
    i = PIDX.get(b)
    if i is None:
        i = PIDX[b] = len(PAL); PAL.append(b)
    return i


A = np.zeros((N, N, NY), dtype=np.uint16)
MARKS = []                   # (t, a, (x,y,z), (sx,sy,sz))
INFO = {}                    # ключевые точки для контракта (заполняются по ходу)
rng = np.random.RandomState(20261109)

XX, ZZ = np.meshgrid(np.arange(-O, O + 1), np.arange(-O, O + 1), indexing="ij")
R = np.hypot(XX, ZZ)
TH = np.degrees(np.arctan2(ZZ, XX)) % 360.0       # 0 - восток, 90 - юг (+z), 180 - запад, 270 - север (-z)


def put(x, y, z, b): A[x + O, z + O, y + YO] = pid(b)


def get(x, y, z):
    if not (-O <= x <= O and -O <= z <= O and YMIN <= y <= YMAX): return ""
    return PAL[A[x + O, z + O, y + YO]]


def box(x1, y1, z1, x2, y2, z2, b):
    x1, x2 = sorted((x1, x2)); y1, y2 = sorted((y1, y2)); z1, z2 = sorted((z1, z2))
    A[x1 + O:x2 + O + 1, z1 + O:z2 + O + 1, y1 + YO:y2 + YO + 1] = pid(b)


def air(x1, y1, z1, x2, y2, z2): box(x1, y1, z1, x2, y2, z2, "air")


def mset(mask, y1, y2, b):
    """Слой(и) y1..y2 в клетках маски (N,N)."""
    v = A[:, :, y1 + YO:y2 + YO + 1]
    v[mask] = pid(b)


def mrand(mask, y1, y2, choices):
    """Случайный блок из choices (список (имя, вес)) в клетках маски на каждом слое."""
    names = [c[0] for c in choices]; w = np.array([c[1] for c in choices], float); w /= w.sum()
    ids = np.array([pid(n) for n in names], dtype=np.uint16)
    idx = np.argwhere(mask)
    for y in range(y1, y2 + 1):
        pick = ids[rng.choice(len(ids), size=len(idx), p=w)]
        A[idx[:, 0], idx[:, 1], y + YO] = pick


def ring(r1, r2): return (R >= r1) & (R < r2)


def ang(a1, a2):
    a1 %= 360; a2 %= 360
    return (TH >= a1) & (TH < a2) if a1 <= a2 else (TH >= a1) | (TH < a2)


def disc(cx, cz, r): return (XX - cx) ** 2 + (ZZ - cz) ** 2 <= r * r


def rect(x1, z1, x2, z2): return (XX >= x1) & (XX <= x2) & (ZZ >= z1) & (ZZ <= z2)


def pol(r, th):
    t = math.radians(th)
    return int(round(r * math.cos(t))), int(round(r * math.sin(t)))


def mark(t, a, x, y, z, sx=0, sy=0, sz=0): MARKS.append((t, a, (x, y, z), (sx, sy, sz)))


def face_of(dx, dz):
    if abs(dx) >= abs(dz): return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


STAIR = "polished_blackstone_brick_stairs"
BR, CBR, BL, PB = "polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "blackstone", "polished_blackstone"
CH, GB, BAS, SBAS, PBAS = "chiseled_polished_blackstone", "gilded_blackstone", "basalt", "smooth_basalt", "polished_basalt"
CD, MG, CO, OBS, GOLD, SL = "cobbled_deepslate", "magma_block", "crying_obsidian", "obsidian", "gold_block", "sea_lantern"
ACL, AMB = "amethyst_cluster[facing=up]", "amethyst_block"
BRICKS = [(BR, 6), (CBR, 1), (BL, 1), (PB, 1)]


def st(x, y, z, face, blk=STAIR): put(x, y, z, f"{blk}[facing={face},half=bottom]")


def lamp(x, y, z, lid):
    """Пьедестал + заглушка Кристальной лампы (код меняет её на свой блок). Метка lamp - на блоке-заглушке."""
    put(x, y, z, PB); put(x, y + 1, z, ACL); mark("lamp", lid, x, y + 1, z)


def brazier(x, y, z, off, bid):
    put(x, y - 1, z, PB); put(x, y, z, "soul_campfire[lit=false]"); mark("brazier", off, x, y, z)
    INFO["brazier" + bid] = (x, y, z)


# ================================================================== 1. гора, подошва, склон
H = np.full((N, N), -999, dtype=int)
INS = R <= 115.5
H[INS] = -1
sl = INS & (R >= 96) & (R < 114)
H[sl] = np.floor(-1 + 22 * (114 - R[sl]) / 18).clip(-1, FY).astype(int)
H[R < 96] = FY
H[ring(90, 96)] = W1
H[ring(66, 90)] = FY
H[ring(58, 66)] = W2
H[ring(40, 58)] = SY
H[R < 40] = 45
H[R < 38] = 56
H[R < 36.5] = 65

FILL = pid(BL)


def terrain():
    """Сплошной камень от y=-7 до поверхности (карверы подрезают естественную землю, подошва - залита), поверхность склона - пятнистая."""
    for xi, zi in zip(*np.nonzero(H > -999)):
        A[xi, zi, 1:H[xi, zi] + YO + 1] = FILL          # y=-7 -> индекс 1
    slope = INS & (R >= 96)
    # поверхность склона и подножия
    for xi, zi in zip(*np.nonzero(slope)):
        y = H[xi, zi]
        r = rng.random_sample()
        b = BAS if r < .40 else BL if r < .62 else SBAS if r < .78 else CD if r < .90 else MG if r < .95 else CO if r < .975 else GB
        A[xi, zi, y + YO] = pid(b)


# ================================================================== 2. серпантин
def serpentine():
    n, th0, th1 = 2600, -150.0, 90.0
    cells = {}                 # (x,z) -> (stand, stairs?, facing)
    prev_stand = 0
    for k in range(n + 1):
        t = k / n
        th = th0 + (th1 - th0) * t
        r = 112 - 15 * t
        stand = int(round(22 * min(1.0, max(0.0, (t - 0.02) / 0.96))))
        tr = math.radians(th)
        dx, dz = -math.sin(tr), math.cos(tr)
        trans = stand > prev_stand
        prev_stand = stand
        f = face_of(dx, dz)
        w = -2.5
        while w <= 2.5:
            x, z = int(round((r + w) * math.cos(tr))), int(round((r + w) * math.sin(tr)))
            if (x, z) not in cells: cells[(x, z)] = (stand, trans, f, t)
            w += 0.4
    for (x, z), (stand, trans, f, t) in cells.items():
        if R[x + O, z + O] > 115.5 or (R[x + O, z + O] < 96.6 and stand >= 20): continue
        yf = stand - 1
        H0 = H[x + O, z + O]
        if yf > H0: box(x, H0 + 1, z, x, yf, z, BL)
        box(x, yf + 1, z, x, yf + 6, z, "air")
        if trans: st(x, yf, z, f)
        else: put(x, yf, z, [BR, BR, BR, CBR, BL, PB][(x * 7 + z * 13) % 6])
    # бастионы-вышки (баллисты): 4 штуки по склону, на внутренней стороне дороги
    for i, t in enumerate((0.15, 0.38, 0.62, 0.85)):
        th = th0 + (th1 - th0) * t
        r = 112 - 15 * t - 6.0
        cx, cz = pol(r, th)
        stand = int(round(22 * min(1.0, max(0.0, (t - 0.02) / 0.96))))
        yf = stand - 1
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                box(cx + dx, min(yf - 4, H[cx + dx + O, cz + dz + O]), cz + dz, cx + dx, yf + 8, cz + dz, BR)
        for dx in (-2, 2):
            for dz in range(-2, 3): put(cx + dx, yf + 8, cz + dz, BR) if (dz % 2 == 0) else None
        for dz in (-2, 2):
            for dx in range(-1, 2): put(cx + dx, yf + 8, cz + dz, BR) if (dx % 2 == 0) else None
        box(cx, yf + 9, cz, cx, yf + 9, cz, "air")
        mark("mob", "ballista", cx, yf + 9, cz)
        INFO.setdefault("ballista", []).append((cx, yf + 9, cz))
    # старт: у начала дороги
    t0x, t0z = pol(112, th0)
    mark("start", "", t0x, 0, t0z)
    INFO["start"] = (t0x, 0, t0z)
    # лучники вдоль дороги
    for t in (0.28, 0.5, 0.72, 0.93):
        th = th0 + (th1 - th0) * t
        x, z = pol(112 - 15 * t + 0.0, th)
        stand = int(round(22 * min(1.0, max(0.0, (t - 0.02) / 0.96))))
        mark("mob", "archer", x, stand, z)
    return cells


# ================================================================== 3. кольцо 1: стена, ворота, бастионы, двор
def brickify(mask, y1, y2): mrand(mask, y1, y2, BRICKS)


def ring1():
    wall = ring(90, 96)
    brickify(wall, FY + 1, W1)
    # зубцы: внешний ряд (r 95..96) и внутренний (r 90..91) через 2 блока вдоль дуги
    arc = np.floor(TH * R * math.pi / 180.0 / 2.0).astype(int) % 2 == 0
    mset(ring(95, 96) & arc, W1 + 1, W1 + 2, BR)
    mset(ring(90, 91) & arc, W1 + 1, W1 + 1, BR)
    # дорожка: плиты
    mrand(wall, W1, W1, [(PB, 3), (BL, 1), (BR, 2)])
    # --- ворота (юг, x -2..2): проход через стену, створки-дверь снаружи (код открывает Осколком)
    box(-9, FY + 1, 88, 9, 40, 97, BR)                                     # привратная башня
    for x in (-9, 9): box(x, W1 + 1, 88, x, 41, 97, CBR)
    air(-2, FY + 1, 88, 2, FY + 5, 97)                                       # проход
    for x in (-2, 2): box(x, FY + 6, 88, x, FY + 6, 97, CH)
    box(-2, FY + 6, 88, 2, FY + 6, 97, CH)
    box(-2, FY + 1, 94, 2, FY + 5, 96, CH)                                   # створки
    box(0, FY + 1, 94, 0, FY + 5, 96, GB)
    mark("door", "gate1", -2, FY + 1, 94, 5, 5, 3)
    for x in (-4, 4):                                                       # столбы и тусклые светильники у ворот снаружи
        pass
    INFO["gate1"] = (-2, FY + 1, 94, 5, 5, 3)
    # привратная площадка: вход с лестницы дороги (r 97)
    # --- 8 бастионов
    bast = []
    for k in range(8):
        a = 22.5 + 45 * k
        cx, cz = pol(93, a)
        m = disc(cx, cz, 6.2)
        for xi, zi in zip(*np.nonzero(m & INS)):
            h0 = H[xi, zi]
            if h0 < 45: A[xi, zi, h0 + YO + 1:45 + YO + 1] = pid(BR)
        brickify(m, FY + 1, 45) if False else None
        # зубцы по краю
        edge = m & ~disc(cx, cz, 5.0) & ((XX + ZZ) % 2 == 0)
        mset(edge, 46, 46, BR)
        # комната: r<3.7, y 35..40
        room = disc(cx, cz, 3.7)
        mset(room, W1 + 1, W1 + 6, "air")
        # коридор дорожки насквозь
        lane = m & (R >= 91) & (R < 95)
        mset(lane, W1 + 1, W1 + 4, "air")
        # бойницы наружу (3 на башню)
        for off in (-40, 0, 40):
            for d in np.arange(3.9, 6.6, 0.5):
                x = int(round(cx + d * math.cos(math.radians(a + off)))); z = int(round(cz + d * math.sin(math.radians(a + off))))
                box(x, W1 + 3, z, x, W1 + 4, z, "air")
        put(cx, 46, cz, CO); put(cx, 47, cz, CO)                              # лиловый огонь на макушке
        bast.append((a, cx, cz))
        mark("mob", "archer", cx + int(round(2 * math.cos(math.radians(a)))), W1 + 1, cz + int(round(2 * math.sin(math.radians(a)))))
        mark("mob", "eye", cx, W1 + 1, cz)
        # сундук в комнате (тир 1; у 3-й башни тайник оружейной - отдельно)
        chest_pos = (cx + int(round(-2 * math.cos(math.radians(a)))), W1 + 1, cz + int(round(-2 * math.sin(math.radians(a)))))
        mark("chest", f"Бастион кольца 1|1", *chest_pos)
        INFO.setdefault("bastions", []).append((a, cx, cz, chest_pos))
        mark("hall", str(9 + k), cx - 7, W1 + 1, cz - 7, 15, 12, 15)
    # --- рампы на стену (восток и запад): ступени вдоль z к дорожке
    for sgn in (1, -1):
        x1, x2 = (87, 89) if sgn > 0 else (-89, -87)
        for k in range(1, 14):
            z = -14 + k
            box(x1, FY + 1, z, x2, FY + k - 1, z, BR) if k > 1 else None
            for x in range(x1, x2 + 1): st(x, FY + k, z, "south")
        for z in (-1, 0):
            box(x1, FY + 1, z, x2, W1, z, BR)
        # убрать внутренние зубцы у рампы, чтобы выход на дорожку был свободен
        xi1, xi2 = (90, 91) if sgn > 0 else (-91, -90)
        box(xi1, W1 + 1, -4, xi2, W1 + 2, 3, "air")
        INFO.setdefault("ramps", []).append((x1, FY + 1, -14, x2, W1 + 1, 0))
    # --- двор: пол
    yard = ring(66, 90)
    mrand(yard, FY, FY, [(PB, 3), (BL, 2), (BAS, 2), (CBR, 1), (SBAS, 1)])
    # дорожка от ворот к воротам кольца 2 и круг (дымчатая плитка)
    path = (np.abs(XX) <= 2) & (ZZ >= 66) & (ZZ < 90)
    mset(path, FY, FY, PBAS)
    # --- казармы (4), диагонали
    barr = []
    for i, (bx, bz) in enumerate(((55, 55), (-55, 55), (-55, -55), (55, -55))):
        box(bx - 6, FY + 1, bz - 6, bx + 6, FY + 7, bz + 6, BR)
        air(bx - 5, FY + 1, bz - 5, bx + 5, FY + 4, bz + 5)
        box(bx - 5, FY, bz - 5, bx + 5, FY, bz + 5, PB)
        # дверь на сторону к оси (x)
        dxs = -1 if bx > 0 else 1
        dxw = bx + dxs * 6
        air(dxw, FY + 1, bz - 1, dxw, FY + 3, bz + 1)
        # крыша-скат: ступени по краю
        for x in range(bx - 6, bx + 7): st(x, FY + 7, bz - 6, "south"); st(x, FY + 7, bz + 6, "north")
        # внутренность: бочки, плиты-кровати
        for (ox, oz) in ((-4, -4), (-4, -3), (4, 4), (3, 4), (4, -4)): put(bx + ox, FY + 1, bz + oz, "barrel[facing=up]")
        for ox in (-3, -1, 1, 3): box(bx + ox, FY + 1, bz + 4, bx + ox, FY + 1, bz + 4, "polished_blackstone_slab[type=bottom]")
        lamp(bx + (3 if dxs > 0 else -3), FY + 1, bz - 2, f"barracks{i + 1}")
        mark("mob", "eater", bx - 1, FY + 1, bz - 1)
        mark("mob", "moth", bx + 2, FY + 1, bz + 2)
        mark("hall", str(5 + i), bx - 6, FY + 1, bz - 6, 13, 8, 13)
        barr.append((bx, bz))
    # сундуки: юго-восток и юго-запад (тир 1)
    mark("chest", "Казарма|1", 55 + 3, FY + 1, 55 + 3)
    mark("chest", "Казарма|1", -55 - 3, FY + 1, 55 + 3)
    INFO["barracks"] = barr
    # --- жаровня-якорь кольца 1: во дворе у ворот
    brazier(8, FY + 1, 84, "0,0,-2", "1")
    # --- мобы двора
    for (x, z, k) in ((-20, 80, "eater"), (22, 78, "moth"), (-12, 72, "eye"), (30, 70, "eater"), (-72, 20, "eater"), (-72, -20, "eye"), (72, 22, "serpent"),
                      (72, -25, "eater"), (-30, -76, "moth"), (35, -78, "eye"), (-50, -72, "eater")):
        mark("mob", k, x, FY + 1, z)
    # --- маяки (3 башни со стремянкой; наверху чаша)
    beacons = ((77, 0, "west"), (-77, 0, "east"), (-27, -73, "south"))
    for i, (bx, bz, doorside) in enumerate(beacons):
        box(bx - 2, FY + 1, bz - 2, bx + 2, FY + 17, bz + 2, BR)
        air(bx - 1, FY + 1, bz - 1, bx + 1, FY + 15, bz + 1)
        # дверь к оси (3 высоты 2)
        dx, dz = {"west": (-2, 0), "east": (2, 0), "south": (0, 2), "north": (0, -2)}[doorside]
        air(bx + dx, FY + 1, bz + dz, bx + dx, FY + 2, bz + dz)
        # стремянка у противоположной стены, лицом внутрь
        lx, lz, lf = {"west": (bx + 1, bz, "west"), "east": (bx - 1, bz, "east"), "south": (bx, bz - 1, "south"), "north": (bx, bz + 1, "north")}[doorside]
        for y in range(FY + 1, FY + 18):
            if y >= FY + 16: air(lx, y, lz, lx, y, lz)
            put(lx, y, lz, f"ladder[facing={lf}]")
        air(lx, FY + 16, lz, lx, FY + 17, lz)
        for y in range(FY + 16, FY + 18): put(lx, y, lz, f"ladder[facing={lf}]")
        # лестница упирается в стену противоположной стороны: стена на месте (внешний слой башни)
        # настил
        top = FY + 17
        box(bx - 2, top, bz - 2, bx + 2, top, bz + 2, BR)
        air(lx, top, lz, lx, top, lz); put(lx, top, lz, f"ladder[facing={lf}]")
        # зубцы
        for (ox, oz) in ((-2, -2), (-2, 0), (-2, 2), (0, -2), (0, 2), (2, -2), (2, 0), (2, 2)):
            put(bx + ox, top + 1, bz + oz, BR)
        put(bx, top + 1, bz, "soul_campfire[lit=false]")
        mark("beacon", str(i + 1), bx, top + 1, bz)
        put(bx, top, bz, PB)
        # светящийся шов на фасаде
        INFO.setdefault("beacons", []).append((i + 1, bx, top + 1, bz, bx + dx, FY + 1, bz + dz))
    return bast


# ================================================================== 4. кольцо 2: стена, галерея, ворота
def ring2():
    wall = ring(58, 66)
    brickify(wall, FY + 1, W2)
    arc = np.floor(TH * R * math.pi / 180.0 / 2.0).astype(int) % 2 == 0
    mset(ring(65, 66) & arc, W2 + 1, W2 + 2, BR)
    mset(ring(58, 59) & arc, W2 + 1, W2 + 1, BR)
    mrand(wall, W2, W2, [(PB, 3), (BL, 1), (BR, 2)])
    # ворота кольца 2 (север, x -2..2): проход r 64..66 до галереи; створки - дверь
    air(-2, FY + 1, -66, 2, FY + 5, -64)
    box(-2, FY + 1, -66, 2, FY + 5, -65, CH); box(0, FY + 1, -66, 0, FY + 5, -65, GB)
    mark("door", "gate2", -2, FY + 1, -66, 5, 5, 2)
    INFO["gate2"] = (-2, FY + 1, -66, 5, 5, 2)
    # ---- Тёмная галерея: кольцо r 61..64, y 22..25; к югу (90 deg) пол поднимается до y=30
    def stand_of(th):
        d = abs((th - 90 + 180) % 360 - 180)
        if d <= 2: return 30
        if d >= 11: return FY + 1
        return FY + 1 + int(round(8 * (11 - d) / 9))
    cells = {}
    for th in np.arange(0, 360, 0.2):
        s = stand_of(th)
        d = abs((th - 90 + 180) % 360 - 180)
        for r in np.arange(61.0, 64.0, 0.4):
            x, z = pol(r, th)
            cells.setdefault((x, z), (s, th))
    prev = {}
    for (x, z), (s, th) in cells.items():
        box(x, s, z, x, s + 3, z, "air")
        put(x, s - 1, z, BR if (x + z) % 3 else PBAS)
    # ступени на подъёме: клетка, если соседняя дальше от 90 deg ниже
    for (x, z), (s, th) in cells.items():
        if s > FY + 1 and s < 30 or (s == 30):
            tr = math.radians(th)
            below = None
            # клетка-ступень: если на 1.2 блока дальше от юга пол ниже
            sgn = -1 if ((th - 90 + 180) % 360 - 180) > 0 else 1
            nth = th + sgn * 1.2 * 180 / (math.pi * 62.5)
            if stand_of(nth) < s:
                dx, dz = -math.sin(tr) * (-sgn), math.cos(tr) * (-sgn)
                st(x, s - 1, z, face_of(dx, dz))
    # выход к террасе на юге: радиально внутрь, x -1..1, y 30..33
    air(-1, 30, 56, 1, 33, 60)
    box(-1, 29, 56, 1, 29, 60, PBAS)
    INFO["gallery_exit"] = (0, 30, 58)
    # ниши с лампами через ~15 блоков; кольцо r=62.5 -> длина дуги; по углам
    lamps = []
    for th in np.arange(22.0, 360.0, 22.0):
        if abs((th - 90 + 180) % 360 - 180) < 14 or abs((th - 270 + 180) % 360 - 180) < 5: continue
        x, z = pol(63.2, th)
        s = stand_of(th)
        lamp(x, s, z, f"gallery{len(lamps) + 1}")
        lamps.append((x, s + 1, z))
    INFO["gallery_lamps"] = lamps
    # мобы галереи
    k = 0
    for th in np.arange(35.0, 360.0, 30.0):
        if abs((th - 90 + 180) % 360 - 180) < 15 or abs((th - 270 + 180) % 360 - 180) < 8: continue
        x, z = pol(62.0, th)
        mark("mob", ("eater", "moth", "serpent", "eater", "eye")[k % 5], x, stand_of(th), z); k += 1
    # свод галереи: ничего, потолок - монолит стены


# ================================================================== 5. терраса, Солнечные часы, Лунные башни
def terrace():
    t = ring(40, 58)
    mrand(t, SY, SY, [(PB, 3), (BL, 3), (BAS, 1), (SBAS, 2), (CBR, 1)])
    # --- солнечные часы: круг r<=10 вокруг (0, 50): золотое кольцо, деления
    D = (0, 50)
    dist = np.hypot(XX - D[0], ZZ - D[1])
    mset((dist >= 9) & (dist < 10), SY, SY, GOLD)
    mset(dist < 9, SY, SY, PB)
    mset((dist < 9) & ((XX + ZZ) % 2 == 0), SY, SY, BL)
    mset(dist < 1.5, SY, SY, CO)
    for a in range(0, 360, 30):
        x, z = pol(11, a); x += D[0]; z += D[1]
        put(x, SY, z, CH)
    put(D[0], SY + 1, D[1], OBS)                                              # гномон (низкий: луч идёт на y=SY+2)
    # --- забор плазы (углы 48 и 132): стены с проходами-воротами
    for name, a in (("sun_e", 48), ("sun_w", 132)):
        d = ((XX * math.sin(math.radians(a)) - ZZ * math.cos(math.radians(a))))
        c = (np.abs(d) < 1.2) & ring(41, 58) & (np.cos(np.radians(TH - a)) > 0)
        mset(c, SY + 1, SY + 4, BR)
        mset(c, SY + 5, SY + 5, CH)
        # ворота: r 47..52
        cc = c & ring(47, 52)
        xs, zs = np.nonzero(cc)
        x1, x2, z1, z2 = xs.min() - O, xs.max() - O, zs.min() - O, zs.max() - O
        mset(cc, SY + 1, SY + 4, "air")
        box(x1, SY + 1, z1, x2, SY + 4, z2, CH)
        mark("door", name, x1, SY + 1, z1, x2 - x1 + 1, 4, z2 - z1 + 1)
        INFO[name] = (x1, SY + 1, z1, x2 - x1 + 1, 4, z2 - z1 + 1)
    # --- окно-источник (запад плазы) и 4 зеркала-кристалла, 4 печати
    box(-25, SY + 1, 48, -25, SY + 6, 52, CH)
    for z in (48, 52): box(-24, SY + 1, z, -23, SY + 6, z, CH)
    box(-24, SY + 6, 48, -23, SY + 6, 52, CH)
    put(-24, SY + 1, 50, PB); put(-24, SY + 2, 50, AMB)
    mark("sun_src", "sun", -24, SY + 2, 50)
    INFO["sun_src"] = (-24, SY + 2, 50)
    mirrors = [(-12, "N"), (-4, "S"), (4, "N"), (12, "S")]
    plates = []
    for i, (mx, side) in enumerate(mirrors):
        put(mx, SY + 1, 50, PB); put(mx, SY + 2, 50, AMB)
        mark("sun_mirror", str(i + 1), mx, SY + 2, 50)
        pz = 44 if side == "N" else 56
        mset(disc(mx, pz, 1.5), SY, SY, PB)
        put(mx, SY, pz, GOLD)
        mark("sun_plate", f"{i + 1},{side}", mx, SY, pz)
        plates.append((i + 1, mx, SY, pz, side))
        INFO.setdefault("sun_mirrors", []).append((i + 1, mx, SY + 2, 50))
    INFO["sun_plates"] = plates
    for (wx, wz) in ((-16, 55), (16, 45), (0, 55), (0, 45)): mark("wave", "sundial", wx, SY + 1, wz)
    for (x, z, k) in ((-16, 53, "eater"), (14, 54, "moth"), (-10, 45, "eye")): mark("mob", k, x, SY + 1, z)
    mark("hall", "26", -24, SY + 1, 41, 49, 9, 17)
    # жаровня-якорь кольца 2 - у выхода из галереи
    brazier(5, SY + 1, 54, "0,0,-1", "2")
    # --- лестница к обсерватории (секрет №3): вдоль -z на x 19..21, от террасы (z -47) к дорожке стены кольца 2 (верх 38)
    for k in range(0, 9):
        z = -47 - k
        top = SY + 1 + k
        for x in (19, 20, 21):
            if top > W2: box(x, SY + 1, z, x, W2, z, BR); continue
            if top - 1 >= SY + 1: box(x, SY + 1, z, x, top - 1, z, BR)
            st(x, top, z, "north")
            air(x, top + 1, z, x, top + 4, z)
    for x in (19, 20, 21): box(x, SY + 1, -56, x, W2, -56, BR)
    mset(rect(17, -63, 23, -54) & ring(58, 66), W2 + 1, W2 + 2, "air")
    INFO["observatory_stair"] = (20, -47, 20, -55)


def tower(idx, cx, cz, kind, door_side):
    """Лунная башня 11x11: оболочка y 30..50, внутри 9x9 до крыши y 49..50."""
    box(cx - 5, SY + 1, cz - 5, cx + 5, 50, cz + 5, BR)
    brickify(rect(cx - 5, cz - 5, cx + 5, cz + 5), SY + 1, 50)
    air(cx - 4, SY + 1, cz - 4, cx + 4, 48, cz + 4)
    ceil_lamp = None
    # дверь: 3 шир, 3 выс
    dx, dz = {"E": (5, 0), "W": (-5, 0), "S": (0, 5), "N": (0, -5)}[door_side]
    if dx: air(cx + dx, SY + 1, cz - 1, cx + dx, SY + 3, cz + 1)
    else: air(cx - 1, SY + 1, cz + dz, cx + 1, SY + 3, cz + dz)
    # пол
    box(cx - 4, SY, cz - 4, cx + 4, SY, cz + 4, PB if kind != "parkour" else "slime_block")
    # окна-бойницы (узкие) на трёх остальных сторонах
    for y in (36, 42):
        for s, (wx, wz) in {"E": (5, 0), "W": (-5, 0), "S": (0, 5), "N": (0, -5)}.items():
            if s == door_side: continue
            if wx: air(cx + wx, y, cz, cx + wx, y + 1, cz)
            else: air(cx, y, cz + wz, cx, y + 1, cz + wz)
    # крыша: 49..50 - монолит; зубцы по краю; лунный свет по углам
    for (ox, oz) in ((-5, -5), (5, -5), (-5, 5), (5, 5)):
        put(cx + ox, 51, cz + oz, SL); put(cx + ox, 52, cz + oz, SL)
    for k in range(-4, 5):
        if k % 2 == 0:
            for (ox, oz) in ((k, -5), (k, 5), (-5, k), (5, k)): put(cx + ox, 51, cz + oz, BR)
    stone = (cx, 51, cz)
    put(cx, 51, cz, PB); put(cx, 52, cz, CO)
    mark("moonstone", str(idx), cx, 53, cz)
    INFO.setdefault("moonstones", []).append((idx, cx, 53, cz))
    entrance = (cx + dx, SY + 1, cz + dz)
    if kind == "parkour":
        # платформы 2x2 по кругу радиуса 3.0, 18 штук; каждая на 1 выше предыдущей
        n = 20
        pts = []
        for i in range(n):
            a = math.radians(180 + 60 * i)
            px = int(round(cx + 2.8 * math.cos(a) - 0.5)); pz = int(round(cz + 2.8 * math.sin(a) - 0.5))
            y = SY + 1 + i        # верхний блок: первая платформа на 1 выше пола, последняя - слой крыши (стоять 50)
            box(px, y, pz, px + 1, y, pz + 1, PB if i % 2 else CBR)
            pts.append((px, y, pz))
        # крыша: проём над последней платформой
        for (qx, qy, qz) in pts[-5:]:
            air(qx - 1, 49, qz - 1, qx + 2, 50, qz + 2)
            box(qx, qy, qz, qx + 1, qy, qz + 1, PB)
        lx, ly, lz = pts[-1]
        # подушка: слизь на полу (уже), первая платформа низко
        INFO["parkour_platforms"] = pts
        mark("mob", "moth", cx, SY + 12, cz)
        mark("mob", "eye", cx + 3, SY + 6, cz - 3)
        mark("hall", str(17 + idx - 1), cx - 5, SY + 1, cz - 5, 11, 22, 11)
        mark("seal", f"{17 + idx - 1},{CH.split('[')[0]}", entrance[0] + 0 if dx else cx - 1, SY + 1, cz + dz if dz else cz - 1,
             *(((1, 3, 3)) if dx else ((3, 3, 1))))
        return
    # кольцо-лестница по внутренней стенке: две петли по периметру 8x8 (внутренний обод 9x9 -> клетки с max(|dx|,|dz|)=4)
    path = []
    for z in range(-4, 4): path.append((-4, z))      # запад, вверх на север -> по часовой
    for x in range(-4, 4): path.append((x, 4))       # юг (с запада на восток)... направление по часовой на карте
    for z in range(4, -4, -1): path.append((4, z))
    for x in range(4, -4, -1): path.append((x, -4))
    # начальная клетка - у двери: поворачиваем список так, чтобы начать у ближайшей к двери
    di = min(range(len(path)), key=lambda i: abs(path[i][0] - dx * 0.8) + abs(path[i][1] - dz * 0.8))
    path = path[di:] + path[:di]
    total = 2 * len(path)
    stand = SY + 1
    cells = []
    for i in range(total):
        px, pz = path[i % len(path)]
        s = SY + 1 + int(i * 21 / total)         # 0..20 -> стоять до 50 (пол последних клеток - слой крыши)
        cells.append((px, pz, s))
    prev = None
    for i, (px, pz, s) in enumerate(cells):
        x, z = cx + px, cz + pz
        nx, nz = cells[(i + 1) % len(cells)][0], cells[(i + 1) % len(cells)][1]
        f = face_of(nx - px, nz - pz)
        # чтобы кольцо не пересекало дверь первого круга: пол - только выше уровня 31
        if prev is not None and s > prev:
            st(x, s - 1, z, f)
        else:
            put(x, s - 1, z, BR)
        air(x, s, z, x, s + 2, z)
        prev = s
    # крыша: проём над последними клетками
    lastx, lastz, lasts = cells[-1]
    # (верхние клетки пути упираются в крышу заподлицо)
    INFO.setdefault("tower_ring_top", []).append((idx, cx + cells[-1][0], cells[-1][2], cz + cells[-1][1]))
    if kind == "dark":
        mark("hall", str(17 + idx - 1), cx - 5, SY + 1, cz - 5, 11, 22, 11)
        for (ox, oz, k) in ((-2, -2, "eater"), (2, -2, "serpent"), (-2, 2, "eater"), (2, 2, "moth"), (0, 0, "eater")):
            mark("mob", k, cx + ox, SY + 1, cz + oz)
    else:
        lamp(cx, SY + 1, cz, f"tower{idx}")
        mark("hall", str(17 + idx - 1), cx - 5, SY + 1, cz - 5, 11, 22, 11)
        for (ox, oz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)): mark("wave", f"tower{idx}", cx + ox, SY + 1, cz + oz)
        for (ox, oz, k) in ((-2, -2, "moth"), (2, 2, "jelly")): mark("mob", k, cx + ox, SY + 1, cz + oz)
    mark("seal", f"{17 + idx - 1},{CH}", *((cx + dx, SY + 1, cz - 1) if dx else (cx - 1, SY + 1, cz + dz)), *((1, 3, 3) if dx else (3, 3, 1)))


# ================================================================== 6. башня-ядро: Предтронный зал, пустота, Тронный двор
def keep():
    brickify(R < 40, SY + 1, 45)
    brickify(R < 38, 46, 56)
    brickify(R < 36.5, 57, 65)
    # --- Предтронный зал: r<21, y 30..43
    mset(R < 21, SY + 1, 43, "air")
    mset(R < 21, SY, SY, PB)
    mset((R < 21) & ((XX + ZZ) % 2 == 0), SY, SY, BL)
    mset(R < 5, SY + 1, SY + 1, PBAS)
    mset(R < 4, SY, SY, GB)
    # потолок и пол пустоты
    mset(R < 22, 44, 47, OBS)
    mset(R < 22, 48, 49, "lava")
    mset(R < 22, 50, 61, "air")
    brickify(ring(22, 23.5), 48, 61)
    mset(R < 22, 44, 44, OBS)
    # колонны и лампы
    for k in range(12):
        x, z = pol(16, 30 * k + 15)
        box(x, SY + 1, z, x, 43, z, BR)
    for k in range(8):
        x, z = pol(11, 45 * k + 22.5)
        lamp(x, SY + 1, z, f"hall{k + 1}")
    mark("mob", "colossus", 0, SY + 2, 0)
    INFO["colossus"] = (0, SY + 2, 0)
    for (wx, wz) in ((-9, -9), (9, -9), (-9, 9), (9, 9), (0, 14), (0, -14)): mark("wave", "hall", wx, SY + 1, wz)
    mark("hall", "20", -21, SY + 1, -21, 43, 14, 43)
    # проход с юга: x -2..2, z 21..39; Лунные врата - створки у фасада
    air(-2, SY + 1, 21, 2, SY + 5, 39)
    box(-2, SY + 1, 37, 2, SY + 5, 39, CH); box(0, SY + 1, 37, 0, SY + 5, 39, GB)
    mark("door", "prethrone", -2, SY + 1, 37, 5, 5, 3)
    INFO["prethrone"] = (-2, SY + 1, 37, 5, 5, 3)
    # лунные сокеты
    for i, (sx, sz) in enumerate(((-8, 42), (8, 42), (0, 46))):
        put(sx, SY + 1, sz, PB); put(sx, SY + 2, sz, CH)
        mark("moon_socket", str(i + 1), sx, SY + 3, sz)
        INFO.setdefault("sockets", []).append((i + 1, sx, SY + 3, sz))
    brazier(-4, SY + 1, 43, "0,0,-1", "3")
    for (x, z) in ((-5, 42), (5, 42)): put(x, SY + 1, z, PB); put(x, SY + 2, z, SL)
    # --- дверь Печати Трона: север зала -> винтовая лестница
    air(-2, SY + 1, -25, 2, SY + 5, -21)
    box(-2, SY + 1, -24, 2, SY + 5, -22, CH); box(0, SY + 1, -24, 0, SY + 5, -22, GB)
    mark("door", "throne_seal", -2, SY + 1, -24, 5, 5, 3)
    INFO["throne_seal"] = (-2, SY + 1, -24, 5, 5, 3)
    # --- винтовая лестница: r 25..29, 540 градусов, 28 ступеней по спирали и 6 крутых ступеней у выхода (стоя 58 -> 64), старт на севере, выход на юге
    th0, tmax = 270.0, 542.0 + 270.0 - 270.0

    def sp_stand(t):
        if t <= 530.0: return SY + 1 + min(28, int(t / (530.0 / 29)))
        return min(CY + 1, SY + 1 + 28 + int(math.ceil((t - 530.0) / 2.0)))
    spiral = {}
    t = 0.0
    while t <= 542.0:
        tr = math.radians(th0 + t)
        for r in np.arange(25.0, 29.0, 0.4):
            x, z = int(round(r * math.cos(tr))), int(round(r * math.sin(tr)))
            spiral.setdefault((x, z), []).append((sp_stand(t), t))
        t += 0.2
    INFO["spiral_cells"] = len(spiral)
    stand_hatch = []
    sp_stairs = []

    def carve_spiral(min_s):
        for (x, z), lst in spiral.items():
            for (s, t) in lst:
                if s < min_s: continue
                air(x, s, z, x, s + 3, z)
        for (x, z), lst in spiral.items():
            for (s, t) in lst:
                if s < min_s: continue
                tr = math.radians(th0 + t)
                dx, dz = -math.sin(tr), math.cos(tr)
                s_prev = sp_stand(max(0.0, t - 1.5 * 180 / (math.pi * 27)))
                if s_prev < s: st(x, s - 1, z, face_of(dx, dz))
                elif s - 1 < CY: put(x, s - 1, z, BR)
    carve_spiral(0)
    for (x, z), lst in spiral.items():
        for (s, t) in lst:
            if s in (62, 63): stand_hatch.append((x, z))
    # --- Тронный двор: воздух, пол, парапет
    mset(R < ARENA_R, CY + 1, CY + 2, "air")
    mset(R < ARENA_R, CY + 3, 74, "air")
    mset(R < ARENA_R, CY - 1, CY - 1, BL)
    # кольца пола: чёрно-золотые
    fl = np.empty((N, N), dtype=object)
    for xi, zi in zip(*np.nonzero(R < ARENA_R + 1)):
        r = R[xi, zi]
        if r < 3: b = GB
        elif r < 9 and int(r) in (8,): b = GOLD
        elif int(r) in (9,): b = GOLD
        elif int(r) in (18, 27, 32): b = GOLD
        elif r < 9: b = PB if int(r) % 2 else BL
        elif r < 18: b = BR if (xi + zi) % 2 else BL
        elif r < 27: b = PB if (xi + zi) % 2 else BL
        else: b = BR if (xi + zi) % 2 else BL
        A[xi, zi, CY + YO] = pid(b) if r < ARENA_R else A[xi, zi, CY + YO]
    # люк: пол над последними ступенями убран, ступени на уровне пола возвращены
    carve_spiral(57)
    # парапет: R 33..36.5 уже монолит до 65; верхний зубчатый
    arc = np.floor(TH * R * math.pi / 180.0 / 2.0).astype(int) % 2 == 0
    mset(ring(ARENA_R, 36.5) & arc, 66, 66, BR)
    mrand(ring(ARENA_R, 34.5), 64, 65, [(BR, 6), (CH, 1), (CBR, 1)])
    # --- трон у северного края
    box(-6, CY, -32, 6, CY + 1, -27, PB)
    box(-4, CY + 2, -32, 4, CY + 2, -29, GB)
    box(-2, CY + 3, -32, 2, CY + 3, -30, BR)
    st(0, CY + 3, -29, "south", "polished_blackstone_stairs")
    for x in (-3, 3): box(x, CY + 3, -32, x, CY + 7, -32, CH)
    box(-3, CY + 8, -32, 3, CY + 8, -32, GB)
    box(-2, CY + 4, -32, 2, CY + 7, -32, BR)
    for x in (-3, 3): put(x, CY + 9, -32, GOLD)
    # центр: метка босса, печать закрывается как у остальных боссов
    mark("boss", "eclipse_king", 0, CY + 1, 0)
    INFO["arena_center"] = (0, CY + 1, 0)
    # печать: прямоугольник внутри люка (клетки лестницы со стоя CY), полная ширина прохода; закрывает его при пробуждении босса
    hs = {c for c in stand_hatch if all(get(c[0], y, c[1]) == 'minecraft:air' for y in range(CY, CY + 4))}
    zs_all = sorted({z for (_, z) in hs})
    best = None
    for zlo in zs_all:
        for zhi in zs_all:
            if zhi < zlo: continue
            xs_ok = [x for x in sorted({x for (x, _) in hs}) if all((x, z) in hs for z in range(zlo, zhi + 1))]
            # самый длинный непрерывный участок по x
            run, cur = [], []
            for x in xs_ok:
                if cur and x == cur[-1] + 1: cur.append(x)
                else:
                    if len(cur) > len(run): run = cur
                    cur = [x]
            if len(cur) > len(run): run = cur
            if run and (zhi - zlo + 1) >= 4:
                key = (len(run) * (zhi - zlo + 1))
                if best is None or key > best[0]: best = (key, run[0], run[-1], zlo, zhi)
    assert best, "нет прямоугольника люка"
    _, x1, x2, z1, z2 = best
    mark("seal", "21," + BR, x1, CY, z1, x2 - x1 + 1, 4, z2 - z1 + 1)
    INFO["court_seal"] = (x1, CY, z1, x2 - x1 + 1, 4, z2 - z1 + 1)
    mark("hall", "21", -34, CY, -34, 69, 12, 69)
    INFO["hall21_ok"] = True
    # --- сокровищница за троном (секрет 2): контрфорс, комната, рычаг-маскировка на подлокотнике трона
    box(-8, 46, -40, 8, CY + 5, -33, BR)
    air(-4, CY + 1, -39, 4, CY + 4, -36)
    box(-4, CY, -39, 4, CY, -36, PB)
    box(-1, CY + 1, -35, 1, CY + 2, -34, BL)                                   # скрытая дверь (secret)
    put(5, CY + 3, -29, "lever[face=floor,facing=north,powered=false]")        # на подлокотнике
    mark("lever", "treasury", 5, CY + 3, -29)
    mark("secret", "treasury", -1, CY + 1, -35, 3, 2, 2)
    mark("chest", "Сокровищница|3", 0, CY + 1, -38)
    mark("hall", "24", -4, CY + 1, -39, 9, 4, 4)
    INFO["treasury"] = {"lever": (5, CY + 3, -29), "secret": (-1, CY + 1, -35, 3, 2, 2), "chest": (0, CY + 1, -38)}
    # свет в сокровищнице не нужен; лампа-заглушка для «подарка»
    lamp(-3, CY + 1, -38, "treasury")
    # --- уголок: мобы двора не ставим (босс сам)
    INFO["throne"] = (0, CY + 3, -29)


# ================================================================== 7. тайник-оружейная и обсерватория, темница
def armory():
    # у стены кольца 1 на севере: трещина во внутренней кладке
    air(-4, FY + 1, -94, 4, FY + 4, -92)
    box(-4, FY, -94, 4, FY, -92, PB)
    box(-1, FY + 1, -91, 0, FY + 2, -90, "cracked_stone_bricks")
    # воздух за трещиной: клетка z=-91..-90 это сама трещина; комната z=-94..-92
    mark("secret", "armory", -1, FY + 1, -91, 2, 2, 2)
    mark("chest", "Оружейная|2", 0, FY + 1, -93)
    mark("hall", "23", -4, FY + 1, -94, 9, 4, 3)
    put(3, FY + 1, -93, "barrel[facing=up]"); put(-3, FY + 1, -93, "barrel[facing=up]")
    for i in range(3): put(-3 + 3 * i, FY + 1, -92, "polished_blackstone_slab[type=bottom]") if False else None
    INFO["armory"] = {"secret": (-1, FY + 1, -91, 2, 2, 2), "chest": (0, FY + 1, -93)}
    # подсказка: потрескавшаяся кладка видна изнутри двора, плюс лампа у стены
    lamp(2, FY + 1, -88, "armory_hint")


def observatory():
    # на стене кольца 2, угол 330: дорожка превращена в провал со столбиками-зубцами; площадка-обсерватория
    # провал: r 58..66, углы 304..326, y 35..38 (остаётся y=34 - в 4 блока ниже)
    m = ring(58, 66) & ang(304, 326)
    mset(m, 35, W2, "air")
    # дорожка от лестницы до провала: чистая плита (зубцы убраны у верха лестницы)
    # столбики через клетку: r 60..61 каждые 2 блока по дуге
    k = 0
    for th in np.arange(304, 326, 2.2):
        x, z = pol(62, th)
        box(x, 35, z, x, W2, z, BR) if k % 1 == 0 else None
        k += 1
    # первый столбик имеет стремянку со стороны провала
    # обсерватория: комната 7x7 на дорожке, центр - угол 335, r=62
    cx, cz = pol(62, 335)
    box(cx - 4, W2 + 1, cz - 4, cx + 4, W2 + 6, cz + 4, BR)
    air(cx - 3, W2 + 1, cz - 3, cx + 3, W2 + 4, cz + 3)
    air(cx - 4, W2 + 1, cz, cx - 4, W2 + 2, cz)            # проём в сторону столбиков (запад-юго-запад по грубой оценке)
    put(cx, W2 + 1, cz, "lectern[facing=north]")
    mark("chest", "Обсерватория|2", cx + 2, W2 + 1, cz + 2)
    mark("hall", "25", cx - 4, W2 + 1, cz - 4, 9, 5, 9)
    INFO["observatory"] = (cx, W2 + 1, cz)
    INFO["observatory_gap"] = ((24, W2 + 1, -57), (pol(62, 327)[0], W2 + 1, pol(62, 327)[1]))


def dungeon():
    # лестница из казармы (-55,-55) вниз: 14 ступеней на запад вдоль z -60..-58, начало x=-51 (пол казармы y=21)
    for i in range(1, 15):
        x = -50 - i
        s = 22 - i
        for z in (-60, -59, -58):
            box(x, s, z, x, s + 4, z, "air")
            st(x, s - 1, z, "west")
    # коридор: x -65..-63, z -58..-21, стоять y=8
    air(-65, 8, -58, -63, 11, -21)
    box(-65, 7, -58, -63, 7, -21, BR)
    for x in range(-65, -62): air(x, 8, -60, x, 11, -58)
    # камеры: запад x -71..-67, восток x -61..-57
    cells = []
    for j in range(5):
        z0 = -55 + 8 * j
        for side, (x1, x2, wall_x) in (("w", (-71, -67, -66)), ("e", (-61, -57, -62))):
            air(x1, 8, z0, x2, 11, z0 + 4)
            box(x1, 7, z0, x2, 7, z0 + 4, PB)
            # передняя стенка: решётка с проёмом
            for z in range(z0, z0 + 5):
                if z == z0 + 2:
                    air(wall_x, 8, z, wall_x, 10, z); box(wall_x, 11, z, wall_x, 11, z, BR)
                else:
                    put(wall_x, 8, z, "iron_bars[north=true,south=true]"); put(wall_x, 9, z, "iron_bars[north=true,south=true]")
                    put(wall_x, 10, z, "iron_bars[north=true,south=true]"); put(wall_x, 11, z, BR)
            mark("mob", "prisoner", (x1 + x2) // 2, 8, z0 + 2)
            cells.append((side, j, (x1 + x2) // 2, z0 + 2))
    lamp(-64, 8, -45, "dungeon1"); lamp(-64, 8, -33, "dungeon2"); lamp(-64, 8, -24, "dungeon3")
    # записка Архимага: у тупика коридора
    put(-64, 8, -22, "lectern[facing=north]")
    mark("note", "archmage", -64, 8, -22)
    mark("chest", "Темница|1", -69, 8, -37)
    mark("hall", "22", -72, 8, -61, 17, 5, 44)
    INFO["dungeon"] = {"stair_top": (-51, FY + 1, -59), "bottom": (-64, 8, -59), "note": (-64, 8, -22), "chest": (-69, 8, -37)}


def build():
    terrain()
    ring1()
    ring2()
    terrace()
    tower(1, *pol(49, 150), "dark", "E")
    tower(2, *pol(49, 30), "parkour", "W")
    tower(3, *pol(49, 270), "lamp", "S")
    INFO["towers"] = [(1, *pol(49, 150), "dark", "E"), (2, *pol(49, 30), "parkour", "W"), (3, *pol(49, 270), "lamp", "S")]
    keep()
    armory()
    observatory()
    dungeon()
    # дорога - в конце, чтобы подрезать склон
    serpentine()


# ---------------------------------------------------------------- выход
build_done = False


def nbt_part(name, sub, marks, z0):
    """Часть шаблона: sub - срез массива (по z), ориентация (x, z, y)."""
    xs, zs, ys = np.nonzero(sub)
    mn = (int(xs.min()), int(zs.min()), int(ys.min()))
    mx = (int(xs.max()), int(zs.max()), int(ys.max()))
    vals = sub[xs, zs, ys]
    # порядок как у gen_dungeons: сортировка по (x, y, z)
    order = np.lexsort((zs, ys, xs))
    xs, zs, ys, vals = xs[order], zs[order], ys[order], vals[order]
    used = np.unique(vals)
    remap = {int(u): i for i, u in enumerate(used)}
    lut = np.zeros(len(PAL), dtype=np.int64)
    for u, i in remap.items(): lut[u] = i
    st_ = lut[vals].astype(">i4")
    n = len(xs)
    rec = np.zeros((n, 36), dtype=np.uint8)
    head = b"\x09" + struct.pack(">H", 3) + b"pos" + b"\x03" + struct.pack(">i", 3)
    rec[:, :len(head)] = np.frombuffer(head, dtype=np.uint8)
    base = len(head)
    posarr = np.stack([xs - mn[0], ys - mn[2], zs - mn[1]], axis=1).astype(">i4")
    rec[:, base:base + 12] = posarr.view(np.uint8).reshape(n, 12)
    mid = b"\x03" + struct.pack(">H", 5) + b"state"
    rec[:, base + 12:base + 12 + len(mid)] = np.frombuffer(mid, dtype=np.uint8)
    o2 = base + 12 + len(mid)
    rec[:, o2:o2 + 4] = st_.view(np.uint8).reshape(n, 4)
    rec[:, o2 + 4] = 0
    assert o2 + 5 == 36
    blocks = gd.Tag(9, struct.pack(">bi", 10, n) + rec.tobytes())
    pal_tags = []
    for u in used:
        b = PAL[int(u)]
        d = {}
        if "[" in b:
            nm, props = b[:-1].split("[")
            d["Name"] = gd.t_str(nm)
            d["Properties"] = gd.t_comp({k: gd.t_str(v) for k, v in (kv.split("=") for kv in props.split(","))})
        else: d["Name"] = gd.t_str(b)
        pal_tags.append(gd.t_comp(d))
    org = (mn[0] - O, mn[2] - YO, mn[1] + z0)           # x, y, z угла части в координатах данжа (z0 - z первого среза)
    size = (mx[0] - mn[0] + 1, mx[2] - mn[2] + 1, mx[1] - mn[1] + 1)
    mt = [gd.t_comp({"t": gd.t_str(t), "a": gd.t_str(a), "p": gd.t_ints([p[0] - org[0], p[1] - org[1], p[2] - org[2]]), "s": gd.t_ints(list(s))})
          for (t, a, p, s) in marks]
    root = gd.t_comp({"DataVersion": gd.t_int(gd.DATA_VERSION), "size": gd.t_ints(list(size)), "palette": gd.t_list(pal_tags, 10),
                      "blocks": blocks, "entities": gd.t_list([]), "ew_origin": gd.t_ints(list(org)),
                      "ew_marks": gd.t_list(mt, 10) if mt else gd.t_list([])})
    return gd.to_gzip(root), n, org, size


def part_index(z):
    return sum(1 for s in SPLITS if z >= s)


def generate():
    build()
    files, stats = {}, {}
    bounds = [(-O, -O + 0)]
    edges = (-O,) + SPLITS + (O + 1,)
    zbands = [(edges[i], edges[i + 1] - 1) for i in range(len(PARTS))]
    for name, (z1, z2) in zip(PARTS, zbands):
        sub = A[:, z1 + O:z2 + O + 1, :]
        # np.nonzero по (x, z, y): приводим к (x, z, y)
        sub_t = sub                      # shape (N, dz, NY): оси x, z, y
        marks = [m for m in MARKS if z1 <= m[2][2] <= z2]
        data, n, org, size = nbt_part(name, np.ascontiguousarray(sub_t), marks, z1)
        # смещение по z части: nbt_part считает от нуля среза
        files[name + ".nbt"] = (data, n, org, size, len(marks))
    return files


def fix_origin(files):
    """nbt_part считал origin от начала среза: поправка уже внесена в generate(); тут только для читаемости."""
    return files


# ---------------------------------------------------------------- проверки
def reg_blocks():
    import lookup
    lookup.ensure()
    return {k: set(v) for k, v in json.load(open(lookup.MAPS / "reg.json", encoding="utf-8")).items() if isinstance(v, list)}


def solid_at(x, y, z):
    b = get(x, y, z)
    if not b: return False
    n = b[10:].split("[")[0]
    return n not in ("air", "ladder", "lava", "amethyst_cluster", "soul_campfire", "lever", "water") and not n.endswith("_pressure_plate")


def validate():
    bad = []
    reg = reg_blocks()
    for b in PAL[1:]:
        n = b.split("[")[0]
        if not n.startswith("minecraft:") or n[10:] not in reg["block"]: bad.append("блока нет в реестре: " + b)
    halls = {}
    for (t, a, p, s) in MARKS:
        if t == "hall":
            i = int(a)
            if i in halls: bad.append(f"повтор номера зала {i}")
            if not 0 < i < 31: bad.append(f"номер зала {i} вне 1..30")
            halls[i] = (p, s)
    def inh(p, h): return all(h[0][i] <= p[i] < h[0][i] + h[1][i] for i in range(3))
    for (t, a, p, s) in MARKS:
        if t in ("chest", "boss"):
            hs = [i for i, h in halls.items() if inh(p, h)]
            if len(hs) != 1: bad.append(f"{t} {p} '{a}': залов {hs}, нужен один")
            if get(*p) not in ("minecraft:air", ""): bad.append(f"{t} {p}: клетка занята ({get(*p)})")
            if not solid_at(p[0], p[1] - 1, p[2]): bad.append(f"{t} {p}: нет пола")
        if t == "mob":
            if not solid_at(p[0], p[1] - 1, p[2]) and a not in ("ballista", "eater", "eye", "moth", "jelly"): bad.append(f"mob {a} {p}: нет пола ({get(p[0], p[1] - 1, p[2])})")
            if get(*p) not in ("minecraft:air", "") : bad.append(f"mob {a} {p}: клетка занята ({get(*p)})")
    for (t, a, p, s) in MARKS:
        if t in ("door", "secret"):
            cells = [(p[0] + x, p[1] + y, p[2] + z) for x in range(s[0]) for y in range(s[1]) for z in range(s[2])]
            if not all(solid_at(*c) for c in cells): bad.append(f"{t} {a} {p}: часть клеток пустая")
        if t == "seal":
            cells = [(p[0] + x, p[1] + y, p[2] + z) for x in range(s[0]) for y in range(s[1]) for z in range(s[2])]
            if any(solid_at(*c) for c in cells): bad.append(f"seal {a} {p}: проём не пустой")
        if t == "lamp" and not get(*p).startswith("minecraft:amethyst_cluster"): bad.append(f"lamp {a} {p}: нет заглушки")
    ids = [a for (t, a, p, s) in MARKS if t == "lamp"]
    if len(ids) != len(set(ids)): bad.append("повтор id ламп")
    bad += check_walk()
    return bad


def check_walk():
    """Проходимость: шагом (подъём до 1, спуск до 3) от старта, двери и печати открыты; к ключевым точкам. Парящие платформы Башни 2 - прыжками."""
    import collections
    bad = []
    openc = set()
    for (t, a, p, s) in MARKS:
        if t in ("door",):
            for x in range(s[0]):
                for y in range(s[1]):
                    for z in range(s[2]): openc.add((p[0] + x, p[1] + y, p[2] + z))
    sealc = set()
    use_seals = [False]
    def free(x, y, z):
        if use_seals[0] and (x, y, z) in sealc: return False
        return (x, y, z) in openc or not solid_at(x, y, z)
    def stand(x, y, z): return free(x, y, z) and free(x, y + 1, z) and (solid_at(x, y - 1, z) or (x, y - 1, z) in openc and False)
    def ladder(x, y, z): return get(x, y, z).startswith("minecraft:ladder")
    start = INFO["start"]
    def bfs(src, jumps=False, blocked_doors=None):
        seen = {src}; dq = collections.deque([src])
        while dq:
            x, y, z = dq.popleft()
            if ladder(x, y, z):
                for dy in (-1, 1):
                    q = (x, y + dy, z)
                    if q not in seen and (ladder(*q) or stand(*q)): seen.add(q); dq.append(q)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, nz = x + dx, z + dz
                if ladder(nx, y, nz) and free(nx, y, nz):
                    q = (nx, y, nz)
                    if q not in seen: seen.add(q); dq.append(q)
                for ny in (y + 1, y, y - 1, y - 2, y - 3):
                    if ny == y + 1 and not free(x, y + 2, z): continue
                    if stand(nx, ny, nz) or (ladder(nx, ny, nz) and free(nx, ny + 1, nz)):
                        q = (nx, ny, nz)
                        if q not in seen: seen.add(q); dq.append(q)
                        break
                if jumps:
                    for k in (2,):
                        jx, jz = x + dx * k, z + dz * k
                        for ny in (y + 1, y, y - 1):
                            if stand(jx, ny, jz) and free(x, y + 2, z):
                                q = (jx, ny, jz)
                                if q not in seen: seen.add(q); dq.append(q)
                                break
        return seen
    sx, sy, sz = start
    sealc = set()
    for (t, a, p, s) in MARKS:
        if t == "seal":
            for x in range(s[0]):
                for y in range(s[1]):
                    for z in range(s[2]): sealc.add((p[0] + x, p[1] + y, p[2] + z))
    reach = bfs((sx, sy, sz))
    globals()['_SEALS'] = sealc
    global REACH; REACH = reach
    def reached(p, label, near=2):
        x, y, z = p
        if any((x + dx, y + dy, z + dz) in reach for dx in range(-near, near + 1) for dz in range(-near, near + 1) for dy in range(-1, 2)): return
        bad.append(f"недостижимо: {label} {p}")
    reached((0, 22, 98), "ворота 1 (снаружи)")
    reached((0, 22, 86), "двор у ворот 1")
    for (i, bx, top, bz, ex, ey, ez) in INFO["beacons"]: reached((ex, ey, ez), f"вход в маяк {i}")
    reached((0, 22, -62), "галерея у ворот 2")
    reached((0, 22, -63), "галерея у ворот 2")
    reached(INFO["gallery_exit"], "выход из галереи")
    reached((0, SY + 1, 50), "плаза часов")
    reached((-30, SY + 1, 50), "западная часть плазы")
    reached(INFO["dungeon"]["bottom"], "темница")
    reached(INFO["dungeon"]["note"], "записка темницы")
    reached(INFO["dungeon"]["chest"], "сундук темницы")
    for (a, cx, cz, cp) in INFO["bastions"]: reached(cp, f"сундук бастиона {a}")
    for (i, px, py, pz, side) in INFO["sun_plates"]: reached((px, py + 1, pz), f"печать {i}")
    reached(INFO["observatory_gap"][0], "начало провала обсерватории")
    # после ворот плазы (открыты): башни
    for (idx, cx, cz, kind, ds) in INFO["towers"]:
        reached((cx + {"E": 6, "W": -6, "S": 0, "N": 0}[ds], SY + 1, cz + {"S": 6, "N": -6, "E": 0, "W": 0}[ds]), f"подход к башне {idx}")
    for (idx, cx, py, cz) in INFO["moonstones"]:
        if idx != 2: reached((cx, 51, cz), f"крыша башни {idx}", near=3)
    reached((0, SY + 1, 41), "вход в Предтронный зал")
    reached((0, SY + 1, 30), "коридор в Предтронный зал")
    reached((0, SY + 2, 0), "центр Предтронного зала")
    reached((0, SY + 1, -26), "начало спирали")
    reached((0, CY + 1, 0), "центр Тронного двора")
    reached(INFO["treasury"]["chest"], "сундук сокровищницы (в обход секрета не проверяем)") if False else None
    # печати закрыты: в Тронный двор не пройти (обхода нет)
    use_seals[0] = True
    sealc.update(globals()["_SEALS"])
    shut = bfs((sx, sy, sz))
    use_seals[0] = False
    if any((0 + dx, 64, 0 + dz) in shut for dx in (-1, 0, 1) for dz in (-1, 0, 1)): bad.append("при закрытых печатях в Тронный двор можно пройти в обход люка")
    # закрытая дверь отсекает следующий ярус (обхода шагом нет); остальные двери открыты, печати не мешают
    door_cells = {}
    for (t, a, p, s) in MARKS:
        if t == "door":
            door_cells.setdefault(a, set()).update((p[0] + x, p[1] + y, p[2] + z) for x in range(s[0]) for y in range(s[1]) for z in range(s[2]))
    def shut_with(ids, cut, label):
        gone = set()
        for i in ids:
            if i not in door_cells: bad.append(f"нет двери {i}"); continue
            gone |= door_cells[i]
        openc.difference_update(gone)
        r = bfs((sx, sy, sz))
        openc.update(gone)
        for q in cut:
            if any((q[0] + dx, q[1] + dy, q[2] + dz) in r for dx in (0,) for dz in (0,) for dy in (-1, 0, 1)):
                bad.append(f"при закрытой двери {label} достижимо {q}")
    shut_with(["gate1"], [(0, 22, 86), (0, 22, -63), (0, SY + 1, 50), (0, CY + 1, 0)], "gate1")
    shut_with(["gate2"], [(0, 22, -63), INFO["gallery_exit"], (0, SY + 1, 50), (0, CY + 1, 0)], "gate2")
    shut_with(["sun_e", "sun_w"], [(cx + {"E": 6, "W": -6, "S": 0, "N": 0}[ds], SY + 1, cz + {"S": 6, "N": -6, "E": 0, "W": 0}[ds]) for (idx, cx, cz, kind, ds) in INFO["towers"]], "sun_e+sun_w")
    shut_with(["prethrone"], [(0, SY + 2, 0), (0, SY + 1, -26), (0, CY + 1, 0)], "prethrone")
    shut_with(["throne_seal"], [(0, SY + 1, -26), (0, CY + 1, 0)], "throne_seal")
    # лава не вытекает: бока и дно - твёрдое или лава
    for xi, zi, yi in zip(*np.nonzero(A == pid("lava"))):
        x, z, y = int(xi) - O, int(zi) - O, int(yi) - YO
        for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
            q = get(x + d[0], y + d[1], z + d[2])
            if q in ("", "minecraft:air"): bad.append(f"лава ({x},{y},{z}) открыта к {d}"); break
    # парящие платформы башни 2: прыжки (шаг по горизонтали до 2)
    t2 = next(t for t in INFO["towers"] if t[0] == 2)
    reach2 = bfs((t2[1] - 5 + 1, SY + 1, t2[2]), jumps=True)
    px, py, pz = INFO["parkour_platforms"][-1]
    if not any((px + dx, py + 1, pz + dz) in reach2 for dx in (0, 1) for dz in (0, 1)): bad.append("парящие платформы башни 2: последняя недостижима прыжками")
    return bad


# ---------------------------------------------------------------- превью
COL = {"air": None, "lava": (255, 110, 20), "magma_block": (200, 70, 20), "crying_obsidian": (140, 60, 220), "obsidian": (30, 10, 50), "gold_block": (240, 200, 60),
       "sea_lantern": (230, 245, 250), "amethyst_block": (170, 110, 230), "amethyst_cluster": (180, 120, 240), "gilded_blackstone": (110, 90, 50),
       "basalt": (60, 60, 66), "smooth_basalt": (50, 50, 56), "polished_basalt": (70, 70, 76), "blackstone": (40, 36, 42), "polished_blackstone": (52, 48, 58),
       "polished_blackstone_bricks": (60, 56, 66), "cracked_polished_blackstone_bricks": (54, 50, 60), "chiseled_polished_blackstone": (70, 64, 80),
       "cobbled_deepslate": (60, 60, 64), "slime_block": (110, 200, 90), "soul_campfire": (80, 200, 220), "ladder": (130, 100, 60), "iron_bars": (150, 150, 150),
       "polished_blackstone_brick_stairs": (72, 68, 80), "barrel": (110, 80, 50), "lectern": (150, 110, 60)}


def png_bytes(w, h, rgb):
    raw = b"".join(b"\x00" + rgb[y].tobytes() for y in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b"")


def palette_colors():
    cols = np.zeros((len(PAL), 3), dtype=np.uint8); vis = np.zeros(len(PAL), bool)
    for i, b in enumerate(PAL):
        if i == 0: continue
        n = b[10:].split("[")[0]
        c = COL.get(n, (90, 90, 100))
        if n == "air" or c is None: continue
        cols[i] = c; vis[i] = True
    return cols, vis


def preview():
    BUILD.mkdir(exist_ok=True)
    cols, vis = palette_colors()
    vmask = vis[A]                                    # (N, N, NY)
    # вид сверху: самый верхний видимый блок, затенение по высоте
    top = NY - 1 - np.argmax(vmask[:, :, ::-1], axis=2)
    has = vmask.any(axis=2)
    ids = np.take_along_axis(A, top[:, :, None], axis=2)[:, :, 0]
    img = cols[ids].astype(float)
    shade = 0.55 + 0.45 * ((top - 0) / 80.0).clip(0, 1)
    img = (img * shade[:, :, None]).clip(0, 255).astype(np.uint8)
    img[~has] = (10, 6, 16)
    # подпись ключевых точек: цветные точки
    big = np.kron(img, np.ones((3, 3, 1), dtype=np.uint8))          # (3N, 3N, 3), оси x, z
    big = big.transpose(1, 0, 2)                                    # строки - z
    h, w, _ = big.shape
    (BUILD / "citadel_top.png").write_bytes(png_bytes(w, h, big))
    # разрез z=0 и x=0
    def section(fix, name, axis):
        sl = A[:, fix + O, :] if axis == "z" else A[fix + O, :, :]
        v = vis[sl]
        c = cols[sl].astype(np.uint8)
        c[~v] = (10, 6, 16)
        # неустановленные/воздух над - фон; стоять: полосы
        img = np.kron(c.transpose(1, 0, 2)[::-1], np.ones((3, 3, 1), dtype=np.uint8))
        (BUILD / name).write_bytes(png_bytes(img.shape[1], img.shape[0], img))
    section(0, "citadel_section_z0.png", "z")
    section(0, "citadel_section_x0.png", "x")
    section(-62, "citadel_section_gallery_z-62.png", "z")


# ---------------------------------------------------------------- контракт (docs/citadel_layout.md)
def doc_text(files):
    I = INFO
    L = []
    w = L.append
    byt = {}
    for (t, a, p, s) in MARKS: byt.setdefault(t, []).append((a, p, s))
    def c(p): return f"({p[0]}, {p[1]}, {p[2]})"
    def b(p, s): return f"({p[0]}, {p[1]}, {p[2]}) размер {s[0]}×{s[1]}×{s[2]}"
    w("# Цитадель Затмения: контракт построек для mod-engineer (фаза 5.3)")
    w("")
    w("Файл генерирует `tools/gen_citadel.py` (`python tools/gen_citadel.py` пишет шаблоны и этот документ; `--check` сверяет и проверяет проходимость; `--preview` рисует `build/citadel_*.png`). **Не править руками**: при изменении координат всё перегенерируется. Источник замысла: `docs/world.md`, «Данж 1 Ада», картинка `docs/concepts/citadel.png`.")
    w("")
    w("Здесь только постройка и метки. Механики (свет и тьма, лампы, маяки, часы, испытания башен, дверь по предметам, спавн, арена Короля) делает код по меткам из раздела «Метки».")
    w("")
    w("## Система координат и размеры")
    w("- Координаты данжа **(0,0,0) = ось горы на уровне подошвы**: `y=0` - первый воздух над ровной землёй у подножия, `x` - на восток, `z` - на юг. Все координаты ниже именно в этой системе (как у Разлома, Города и Святилища: код сдвигает их на точку данжа).")
    w(f"- Габарит шаблонов: x -116..116, z -116..116, y -7..82 (233×233×~90). Вокруг горы нужен свободный круг радиуса ~118 блоков.")
    w("- Подошва (y -7..-1) залита сплошным `blackstone` на круге r<=115 - карверы и рельеф Базальтовых пиков её не подрежут. Гора - сплошной массив (внутри только комнаты, ходы, шахта, пустота).")
    w("- **Что должен сделать код перед вставкой:** расчистить воздухом цилиндр r<=116, y 0..~120 (шпили Базальтовых пиков и деревья), потому что воздух вне комнат в шаблон не записан (шаблон хранит только твёрдые блоки и воздух комнат/ходов). Над Тронным двором нужно ≥55 блоков свободного неба (y>=64) - расчистка до y~125 это даёт.")
    w("- Блоки: только ваниль (`minecraft:*`: чёрный камень и его кирпичи, базальт, слёзный обсидиан, золото, слизь, лава, морской фонарь, аметист и т.д.). Блоков мода в шаблонах нет; при желании код может подменить при вставке `blackstone`→`explosionwand:eclipse_stone`, `polished_blackstone_bricks`→`explosionwand:eclipse_bricks` (для этого `Dungeons.state` должен принимать пространство имён мода).")
    tot = sum(f[1] for f in files.values())
    w(f"- **Объём: {tot:,} блоков** (в 6 частях, см. ниже).".replace(",", " ") + " Парсер `Nbt` в `DungeonTemplate.parse` строит Map на каждый блок - для такой структуры нужен потоковый разбор в массивы или хотя бы пофайловая загрузка с освобождением памяти (часть = ~250 тыс. блоков, файл ~0,65 МБ). Установка в мир - порциями по тикам, как у Разлома.")
    w("")
    w("## Части шаблона")
    w("")
    w("Режутся по z (как у других данжей); `ew_origin` - угол части в координатах данжа.")
    w("")
    w("| Имя | z-диапазон | ew_origin (x, y, z) | size (x, y, z) | блоков | меток |")
    w("|---|---|---|---|---|---|")
    zr = [f"{a}..{b}" for a, b in zip((-O,) + SPLITS, tuple(x - 1 for x in SPLITS) + (O,))]
    for (name, f), r in zip(files.items(), zr):
        data, n, org, size, nm = f
        w(f"| `{name[:-4]}` | {r} | {org} | {size} | {n} | {nm} |".replace(",", ",").replace("(", "(").replace(")", ")"))
    w("")
    w("Регистрация: `new Def(\"citadel\", cell, margin, layout, \"citadel_p1\", ... , \"citadel_p6\")`, подземной частью считаются только подошва (y>=-7) - данж наземный: площадка должна быть расчищена и ровной по подошве (`ground(flat,maxVar)` с большим допуском, т.к. подошва маскирует перепады до ~7 блоков; в Базальтовых пиках - искать самую плоскую клетку, остальное срезает расчистка).")
    w("")
    w("## Карта высот (верхний блок пола, стоять на +1)")
    w("")
    w("| Ярус | Радиус r | Пол y | Что |")
    w("|---|---|---|---|")
    w("| Подножие | r>=114 | -1 (стоять 0) | старт серпантина, `start` |")
    w("| Склон | 96..114 | -1 -> 21 | серпантин: 5 блоков шириной, ~440 блоков длиной, 4 баллисты |")
    w("| Кольцо 1 (стена) | 90..96 | 34 | ходовая дорожка с зубцами, 8 бастионов до y=45, ворота на юге |")
    w("| Внешний двор | 66..90 | 21 | казармы, маяки, жаровня-якорь 1 |")
    w("| Кольцо 2 (стена) | 58..66 | 38 | внутри Тёмная галерея r 61..64, y 22..25 |")
    w("| Терраса часов | 40..58 | 29 | Солнечные часы (юг), Лунные башни, жаровни 2 и 3 |")
    w("| Башня-ядро (кольцо 3) | <40 | 45 / 56 / 65 | Предтронный зал, пустота с лавой, спираль, Тронный двор |")
    w("| Тронный двор | <33 | 63 (стоять 64) | арена R=33, парапет r 33..36.5 до y=65 |")
    w("")
    w("## Главный путь и ключевые точки")
    w("")
    w("Все координаты - (x, y, z) блока метки; у дверей и печатей - угол и размер объёма. Этот объём **в шаблоне**: у `door`/`secret` он заполнен твёрдым блоком (его убирает код), у `seal` - воздух (его закрывает код).")
    w("")
    w("### 1. Серпантин и ворота кольца 1 (дверь `gate1`, открывает Осколок Печати: Затмение)")
    w(f"- Старт (`start`): {c(I['start'])} - подножие, запад-северо-запад горы; серпантин идёт по часовой стрелке (вид сверху, север вверху): от запад-северо-запада через север и восток к южным воротам, ~440 блоков пути.")
    for bpos in I["ballista"]: w(f"- метка `mob ballista`: {c(bpos)}")
    w(f"- Ворота кольца 1: дверь `gate1` - {b(I['gate1'][:3], I['gate1'][3:])} (толщина 3 по z, внешняя сторона z=94..96); проход шириной 5 через привратную башню (x -9..9, z 88..97).")
    w(f"- Жаровня-якорь 1 (`brazier`, a=смещение до точки возрождения): {c(I['brazier1'])}")
    w("")
    w("### 2. Внешний двор: маяки (дверь `gate2` открывается, когда зажжены 3 маяка)")
    for (i, bx, top, bz, ex, ey, ez) in I["beacons"]: w(f"- Маяк {i}: метка `beacon` a=`{i}` на чаше {c((bx, top, bz))} (костёр `soul_campfire[lit=false]` - код зажигает), вход в башню {c((ex, ey, ez))}, наверх по стремянке внутри (ладдер).")
    w(f"- Ворота кольца 2: дверь `gate2` - {b(I['gate2'][:3], I['gate2'][3:])}; за ней Тёмная галерея.")
    w("- Казармы (побочные комнаты, залы 5-8): " + ", ".join(c((x, 22, z)) for x, z in I["barracks"]) + " (центры); в каждой метка `lamp`, 2 метки `mob`, сундуки (тир 1) в двух.")
    w("")
    w("### 3. Тёмная галерея (кольцо r 61..64, внутри стены кольца 2)")
    w("- Круговой коридор шириной 3, высотой 4, `y` 22..25, входит от ворот `gate2` (север); обход вправо или влево до выхода на юге (пол плавно поднимается до y=30).")
    w(f"- Выход: {c(I['gallery_exit'])} (проход x -1..1, z 56..60, y 30..33) - на террасу к Солнечным часам.")
    w(f"- Кристальные лампы галереи: {len(I['gallery_lamps'])} меток `lamp` (a=`galleryN`), через ~15 блоков: " + "; ".join(c(p) for p in I["gallery_lamps"]))
    w("- Мобов галереи ставят метки `mob` (eater/moth/serpent/eye) через ~30°.")
    w("")
    w("### 4. Бастионы кольца 1 (побочные, залы 9-16)")
    w("- 8 круглых башен радиуса 6 на стене кольца 1 (углы 22.5°+45°k, r=93), комната внутри на уровне дорожки y 35..40 с сундуком (тир 1) и метками `mob archer/eye`; на дорожку поднимаются по рампам (восток и запад двора, ступени вдоль z, x 87..89, z -13..-1).")
    for (a, cx, cz, cp) in I["bastions"]: w(f"  - бастион {a:.1f}°: центр ({cx}, {cz}), сундук {c(cp)}")
    w("")
    w("### 5. Солнечные часы (терраса, юг, кольцо 2)")
    w("Круг с центром (0, 29, 50): золотое кольцо r 9..10 в плитке, гномон-обсидиан (0,30,50).")
    w(f"- Окно-источник (`sun_src`): {c(I['sun_src'])}; луч идёт на восток вдоль z=50 на высоте y=31.")
    for (i, mx, my, mz) in I["sun_mirrors"]: w(f"- Зеркало-кристалл {i} (`sun_mirror` a=`{i}`): {c((mx, my, mz))} - блок `amethyst_block` на пьедестале; код рисует поворачиваемый кристалл и смотрит куда направлено (N/E/S/W).")
    for (i, px, py, pz, side) in I["sun_plates"]: w(f"- Печать {i} (`sun_plate` a=`{i},{side}`): {c((px, py, pz))}, верный поворот зеркала {i} - в сторону `{side}` (N = к -z, S = к +z), луч идёт от зеркала по колонке x={px} к печати.")
    w("- Идея: каждое зеркало пропускает луч дальше на восток и, повёрнутое в нужную сторону, пускает ветвь в свою печать; все 4 печати горят - открываются дверь `sun_w` и дверь `sun_e`. Ошибка (по решению кода) - тьма и волна мобов (4 точки `wave` a=`sundial`).")
    w(f"- Дверь `sun_e`: {b(I['sun_e'][:3], I['sun_e'][3:])}; дверь `sun_w`: {b(I['sun_w'][:3], I['sun_w'][3:])} (проходы в заборах плазы, 5 блоков, по углам 48° и 132°).")
    w(f"- Жаровня-якорь 2: {c(I['brazier2'])}.")
    w("")
    w("### 6. Лунные башни (залы 17, 18, 19; квадратные 11×11, высота до y=50, крыша - y 51)")
    for (idx, cx, cz, kind, ds) in I["towers"]:
        desc = {"dark": "бой в полной тьме (внутри кольцевая лестница по периметру, 2 витка до крыши)", "parkour": "паркур по парящим платформам 2×2 (20 штук по спирали, пол - слизь, падение безвредно)", "lamp": "защита лампы от волн (лампа в центре, 4 точки `wave`; кольцевая лестница до крыши)"}[kind]
        w(f"- Башня {idx} ({desc}): центр ({cx}, {cz}), вход с стороны `{ds}`: печать `seal` на двери башни - {b(*[m for m in [(p, s) for (t, a, p, s) in MARKS if t == 'seal' and a.startswith(str(16 + idx) + ',')][0]])}.")
    for (idx, cx, py, cz) in I["moonstones"]: w(f"- Лунный камень {idx} (`moonstone` a=`{idx}`): {c((cx, py, cz))} - над постаментом на крыше башни {idx} (код кладёт предмет/блок после испытания).")
    w("")
    w("### 7. Предтронный зал (зал 20) и Лунные врата (ворота кольца 3 - это дверь `prethrone`; ворота колец 1 и 2 - `gate1` и `gate2`)")
    for (i, x, y, z) in I["sockets"]: w(f"- Сокет Лунного камня {i} (`moon_socket` a=`{i}`): {c((x, y, z))} (постамент перед фасадом башни-ядра).")
    w(f"- Дверь `prethrone` (Лунные врата, 3 камня): {b(I['prethrone'][:3], I['prethrone'][3:])}; за ней коридор z 21..36 в зал r<21.")
    w(f"- Жаровня-якорь 3 (место возрождения перед залом): {c(I['brazier3'])}.")
    w(f"- Метка `mob colossus` (Затмённый колосс, мини-босс): {c(I['colossus'])}, 8 меток `lamp` (a=`hall1..8`, гасит колосс), 6 меток `wave`.")
    w(f"- Дверь `throne_seal` (Печать Трона): {b(I['throne_seal'][:3], I['throne_seal'][3:])}; за ней винтовая лестница r 25..29, 540°, 34 ступени, выход в Тронный двор на юге.")
    w("")
    w("### 8. Тронный двор (зал 21) - арена Короля Затмения")
    w(f"- Центр арены (метка `boss` a=`eclipse_king`): {c(I['arena_center'])}; пол y=63 (стоять 64), радиус 33, парапет r 33..36.5 (2 блока), без крыши.")
    w(f"- Печать `seal` двора (a=`21,{BR}`): {b(I['court_seal'][:3], I['court_seal'][3:])} - люк, откуда игрок выходит из лестницы; закрывается при пробуждении босса (воздух в шаблоне).")
    w("- Пустота: r<22, y 50..61 воздух, y 48..49 лава (дно `obsidian` y 44..47). Пол двора - 2 слоя (y 62..63), на r 22..33 держится на монолите; код осыпает пол с краёв (кольцо r 22..33 остаётся опорным, внутри r<22 - настоящая яма).")
    w(f"- Трон: ступени и кресло у северного края, сиденье около {c(I['throne'])}.")
    w("")
    w("## Секреты")
    a = I["armory"]
    w(f"1. **Оружейная** (зал 23): трещина `cracked_stone_bricks` в стене двора кольца 1, север. `secret` a=`armory` - {b(a['secret'][:3], a['secret'][3:])}; сундук (тир 2) {c(a['chest'])}; открывает любой взрыв/кирка (решает код, как «трещины» Святилища); подсказка - метка `lamp` a=`armory_hint`.")
    t = I["treasury"]
    w(f"2. **Сокровищница за троном** (зал 24): рычаг-маскировка `lever` a=`treasury` на подлокотнике трона {c(t['lever'])}; `secret` a=`treasury` - {b(t['secret'][:3], t['secret'][3:])}; сундук (тир 3) {c(t['chest'])}.")
    ob = I["observatory"]
    w(f"3. **Обсерватория** (зал 25) на стене кольца 2: лестница от террасы {c(I['observatory_stair'][:3] if False else (I['observatory_stair'][0], 30, I['observatory_stair'][1]))} к {c((I['observatory_stair'][2], 38, I['observatory_stair'][3]))} на дорожку стены; дальше провал с зубцами-столбиками (прыжки), дальше комната, сундук (тир 2) около {c(ob)}.")
    w("")
    w("## Темница (побочная, зал 22)")
    d = I["dungeon"]
    w(f"- Вход: лестница из северо-западной казармы {c(d['stair_top'])}, 14 ступеней на запад вниз, до {c(d['bottom'])}; коридор на юг до {c(d['note'])} (записка Архимага, метка `note` a=`archmage`, лекторий).")
    w(f"- 10 камер (по 5 с каждой стороны), в каждой метка `mob prisoner`; сундук (тир 1) {c(d['chest'])}; 3 метки `lamp` (a=`dungeon1..3`).")
    w("")
    w("## Метки (что ставит шаблон, что делает код)")
    w("")
    w("Формат тот же, что у Разлома/Святилища: `t`, `a`, `p` (в координатах части), `s`. Прежние типы работают как раньше (`start`, `door`, `brazier`, `hall`, `chest`, `boss`, `seal`, `lever`, `secret`). Новые типы Цитадели (код сам решает, как их обрабатывать; неизвестные типы `Def.index` просто складывает в `byType`):")
    w("")
    w("| Метка | a | Что стоит в шаблоне | Что делает код |")
    w("|---|---|---|---|")
    w("| `lamp` | id (`galleryN`, `barracksN`, `hallN`, `towerN`, `dungeonN`, ...) | на блоке-заглушке `amethyst_cluster[facing=up]` на пьедестале `polished_blackstone` (метка на заглушке) | заменяет заглушку Кристальной лампой (ПКМ - свет r~8) |")
    w("| `beacon` | 1..3 | `soul_campfire[lit=false]` на дне чаши | зажигает по ПКМ; 3 из 3 - открывает `gate2` |")
    w("| `moon_socket` | 1..3 | постамент (`chiseled_polished_blackstone`), метка над ним (воздух) | принимает Лунный камень; 3 из 3 - открывает `prethrone` |")
    w("| `moonstone` | 1..3 | пьедестал и `crying_obsidian` на крыше башни, метка над ним (воздух) | выдаёт камень после испытания |")
    w("| `sun_src`, `sun_mirror`, `sun_plate` | `sun` / номер / `номер,сторона` | `amethyst_block` (источник и зеркала), плиты-печати из `gold_block` в полу | головоломка Солнечных часов (см. выше) |")
    w("| `wave` | зона (`sundial`, `towerN`, `hall`) | воздух, с полом | точки появления волн мобов |")
    w("| `mob` | вид: `archer`, `ballista`, `eater` (Пожиратель света), `eye` (Око), `moth` (Мотылёк-гаситель), `jelly` (Лунная медуза), `serpent` (Теневой змей), `colossus`, `prisoner` | воздух с полом | **не `spawn`**: мобов Цитадели ещё нет (5.4), метки пока не обрабатываются; ванильного спавна нет |")
    w("| `note` | `archmage` | `lectern` | записка, текст - код |")
    w("| `door` | `gate1`, `gate2`, `sun_e`, `sun_w`, `prethrone`, `throne_seal` | сплошной блок (`chiseled_polished_blackstone` + золотая жила) | убирает по условию (осколок, маяки, часы, 3 камня, Печать Трона) |")
    w("| `seal` | `зал,блок` | воздух | закрывает при пробуждении зала (двери башен 1-3 и люк Тронного двора) |")
    w("| `secret`+`lever` | `armory`, `treasury` | сплошные блоки; рычаг на подлокотнике | убирает стену по рычагу/взрыву |")
    w("| `brazier` | `dx,dy,dz` | `soul_campfire[lit=false]` на `polished_blackstone` | якорь возрождения: 3 штуки (1 - двор у ворот, 2 - терраса у выхода из галереи, 3 - перед Лунными вратами) |")
    w("| `hall` | номер 5..26 | - | единицы пробуждения: 5-8 казармы, 9-16 бастионы, 17-19 башни, 20 Предтронный, 21 Тронный двор (босс+печать), 22 темница, 23 оружейная, 24 сокровищница, 25 обсерватория, 26 плаза часов |")
    w("| `chest` | `Название|тир` | воздух с полом | `Loot.chest` |")
    w("| `boss` | `eclipse_king` | воздух с полом в центре арены | **kind `eclipse_king` надо добавить в `BOSS_KINDS`** (gen_dungeons.py) если метка пройдёт его проверку; `Boss2` переводится на этот центр |")
    w("")
    mobs = {}
    for (a, p, s_) in byt.get("mob", []): mobs[a] = mobs.get(a, 0) + 1
    w("Метки `mob` по видам (координаты - в метках шаблонов): " + ", ".join(f"`{k}` {v}" for k, v in sorted(mobs.items())) + f"; всего {sum(mobs.values())}. Метки `wave`: {len(byt.get('wave', []))}, `lamp`: {len(byt.get('lamp', []))}, `chest`: {len(byt.get('chest', []))}.")
    w("")
    w("Номера залов 1-4 и 27-30 свободны (во дворе кольца 1 отдельных залов нет - только метки `mob`).")
    w("")
    w("## Проверки (tools/gen_citadel.py --check)")
    w("- все ID блоков есть в реестре 1.20.1; метки `chest`/`boss` по одной в зале, на воздухе, с полом;")
    w("- проходимость шагом (подъём 1, спуск до 3, двери открыты, лестницы-ладдеры) от `start` до: ворот, маяков, галереи, плазы, тёмницы, бастионов, печатей, подхода к башням, крыш башен 1 и 3, Лунных врат, зала, спирали, центра арены; парящие платформы башни 2 - прыжком через 1 блок;")
    w("- двери и тайники в шаблоне заполнены, печати - пустые.")
    w("")
    w("## Не сделано (следующий шаг)")
    w("- Орнамент и мелкий декор (знамёна, цепи, треснувшие участки стен) - по вкусу пользователя после игры; светящихся блоков в шаблоне мало намеренно: внутри должно быть темно.")
    w("- Столб света над башней-ядром - эффект кода (частицы/`block_display`).")
    return "\n".join(L) + "\n"


def main():
    check = "--check" in sys.argv
    files = generate()
    bad = validate()
    out = {}
    for name, f in files.items(): out[name] = f
    doc = doc_text(files)
    if "--preview" in sys.argv: preview()
    if check:
        for name, f in files.items():
            p = OUT / name
            if not p.exists() or gzip.decompress(p.read_bytes()) != gzip.decompress(f[0]): bad.append(f"{name}: устарел, запустите python tools/gen_citadel.py")
        if not DOC.exists() or DOC.read_text(encoding="utf-8") != doc: bad.append("docs/citadel_layout.md: устарел, запустите python tools/gen_citadel.py")
        print(f"[gen_citadel] частей {len(files)}, блоков {sum(f[1] for f in files.values())}, проблем {len(bad)}")
        if bad: sys.exit("\n".join(bad))
        return
    if bad: print("\n".join(bad)); sys.exit(1)
    OUT.mkdir(parents=True, exist_ok=True)
    for name, f in files.items():
        (OUT / name).write_bytes(f[0]); print("записан", name, len(f[0]), "байт,", f[1], "блоков")
    DOC.write_text(doc, encoding="utf-8"); print("записан", DOC.relative_to(ROOT))


if __name__ == "__main__":
    main()
