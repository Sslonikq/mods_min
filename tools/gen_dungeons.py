#!/usr/bin/env python3
"""
Генератор шаблонов данжей (.nbt) для мода explosionwand.

    python tools/gen_dungeons.py          # записать .nbt и текстуру осколка в resources/
    python tools/gen_dungeons.py --check  # перегенерировать в памяти, сравнить с файлами и проверить ID блоков/мобов

Данж строится в одной общей сетке «координаты данжа»:
    начало (0,0,0) = центр входа, y=0 — первый воздух над землёй, x — на восток, z — на юг.
Потом сетка режется на части (по плоскости z=SPLIT_Z) — по одной .nbt на часть. Так проверяется «структура из нескольких частей».
Формат .nbt — как у шаблонов структур Minecraft (DataVersion, size, palette, blocks, entities), поэтому файл можно загрузить
в игре командой /place template explosionwand:<имя>. Свои теги:
    ew_origin  — [x,y,z] смещение угла части от начала данжа
    ew_marks   — метки: t (тип), a (аргумент), p [x,y,z] (в координатах части), s [sx,sy,sz]
Типы меток: start, door (p+s: блоки двери), brazier (p: жаровня; a: "dx,dy,dz" смещение до точки возрождения),
            hall (p+s: зал, a: номер), spawn (p: точка моба, a: тип сущности), chest (p: сундук награды, a: название).
Файлы детерминированы (gzip без времени), поэтому --check сравнивает байты.
"""
import gzip, io, json, random, struct, sys, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resources" / "data" / "explosionwand" / "structures"
TEX = ROOT / "resources" / "assets" / "explosionwand" / "textures" / "item"
DATA_VERSION = 3465  # Minecraft 1.20.1
SPLIT_Z = 18         # клетки с z < SPLIT_Z — в часть «entrance», остальные — в «halls»


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


def build_rift_test():
    """Тестовый разлом: вход на поверхности, лестница вниз, зал с провалом, зал с жаровней, логово."""
    g = Grid()
    rng = random.Random(20261019)
    SB, CSB, MSB = bm("stone_bricks"), bm("cracked_stone_bricks"), bm("mossy_stone_bricks")
    DB, CDB, DT, PD = bm("deepslate_bricks"), bm("cracked_deepslate_bricks"), bm("deepslate_tiles"), bm("polished_deepslate")
    CO, AM, OBS = bm("crying_obsidian"), bm("amethyst_block"), bm("obsidian")

    def stone(*_): return rng.choice([SB, SB, SB, CSB, MSB])
    def deep(*_): return rng.choice([DB, DB, DB, DB, CDB, DT])
    def checker(x, y, z): return DT if (x + z) % 2 == 0 else PD

    # ---------- вход на поверхности: площадка и комната с дверью из кристалла
    g.air(-6, 0, -9, 6, 8, 4)                              # расчистка (деревья, холм)
    g.box(-6, -6, -9, 6, -2, 4, SB)                        # фундамент
    g.box(-6, -1, -9, 6, -1, 4, stone)                     # пол
    g.box(-4, 0, -4, 4, 4, 4, stone)                       # стены комнаты
    g.air(-3, 0, -3, 3, 4, 3)
    g.box(-4, 5, -4, 4, 5, 4, SB)                          # крыша
    for sx in (-4, 4):
        for sz in (-4, 4): g.box(sx, 0, sz, sx, 5, sz, bm("chiseled_stone_bricks"))
    g.box(-2, 0, -4, -2, 3, -4, DB); g.box(2, 0, -4, 2, 3, -4, DB); g.box(-2, 3, -4, 2, 3, -4, DB)   # арка
    g.box(-1, 0, -4, 1, 2, -4, AM)                         # дверь из кристалла
    g.mark("door", -1, 0, -4, "rift", (3, 3, 1))
    for sx in (-3, 3):
        g.set(sx, 0, -5, CO); g.set(sx, 1, -5, CO)         # светящиеся стойки у двери
    for (px, pz) in ((-6, -9), (6, -9), (-6, -5), (6, -5)):  # обломки колонн
        g.box(px, 0, pz, px, rng.choice((1, 2, 3)), pz, stone)
    for (px, pz) in ((-4, -7), (3, -8), (-2, -6), (5, -7), (-5, -8), (1, -9), (4, -6), (-6, -3)):
        g.set(px, 0, pz, bm("amethyst_cluster[facing=up]"))
    for (lx, lz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):   # фонари под крышей
        g.set(lx, 4, lz, bm("lantern[hanging=true]"))
    g.box(3, 0, -3, 3, 1, -3, PD)                          # жаровня №0 на пьедестале
    g.set(3, 2, -3, bm("soul_campfire[lit=false]"))
    g.mark("brazier", 3, 2, -3, "-1,-2,0")
    g.mark("start", 0, 0, -8)

    # ---------- залы (сначала оболочки, потом выемки, чтобы переходы пробивали стены)
    # зал 1: x -10..10, z 18..39; зал 2: x -8..8, z 43..59; логово: x -7..7, z 61..76; пол — y=-21
    g.box(-10, -28, 18, 10, -12, 39, deep)
    g.box(-8, -28, 43, 8, -12, 59, deep)
    g.box(-7, -28, 61, 7, -12, 76, deep)
    g.air(-9, -20, 19, 9, -14, 38); g.box(-9, -21, 19, 9, -21, 38, checker)
    g.air(-7, -20, 44, 7, -14, 58);  g.box(-7, -21, 44, 7, -21, 58, checker)
    g.air(-6, -20, 62, 6, -14, 75);  g.box(-6, -21, 62, 6, -21, 75, checker)

    # зал 1: провал z 26..33 на всю ширину, кристальные ступени, пандус для упавших
    g.air(-9, -26, 26, 9, -21, 33)
    g.box(-9, -27, 26, 9, -27, 33, lambda x, y, z: CO if rng.randrange(7) == 0 else OBS)
    for (x1, x2, z1, z2) in ((-1, 0, 27, 28), (0, 1, 30, 31), (-1, 0, 33, 33)):
        g.box(x1, -21, z1, x2, -21, z2, AM)
    for (x, z) in ((-5, 28), (4, 30), (-3, 32), (6, 27), (2, 33)):
        g.set(x, -26, z, bm("amethyst_cluster[facing=up]"))
    for i in range(6):                                      # пандус вдоль западной стены: z 32..27
        for x in (-9, -8):
            if i > 0: g.box(x, -26, 32 - i, x, -26 + i - 1, 32 - i, DB)
            g.set(x, -26 + i, 32 - i, bm("deepslate_brick_stairs[facing=north]"))
    g.box(-9, -26, 26, -8, -21, 26, DB)                     # площадка пандуса вровень с полом
    for z in (21, 25, 29, 36):                              # светящиеся вставки в стенах
        g.set(-10, -17, z, CO); g.set(10, -17, z, CO)
    g.set(-4, -13, 29, bm("shroomlight")); g.set(4, -13, 29, bm("shroomlight"))
    g.set(0, -13, 22, bm("shroomlight")); g.set(0, -13, 36, bm("shroomlight"))

    # коридор зал 1 -> зал 2
    g.box(-2, -22, 39, 2, -17, 43, deep); g.air(-1, -20, 39, 1, -18, 43)
    g.box(-1, -21, 39, 1, -21, 43, checker)
    g.set(0, -18, 41, bm("lantern[hanging=true]"))

    # зал 2: колонны с огнями, жаровня №1
    for (px, pz) in ((-4, 48), (4, 48), (-4, 54), (4, 54)):
        g.box(px, -20, pz, px, -14, pz, DB); g.set(px, -17, pz, CO)
    g.box(0, -20, 46, 0, -19, 46, PD)
    g.set(0, -18, 46, bm("soul_campfire[lit=false]"))
    g.mark("brazier", 0, -18, 46, "0,-2,-1")
    g.set(-7, -16, 51, CO); g.set(7, -16, 51, CO); g.set(-7, -16, 57, CO); g.set(7, -16, 57, CO)
    g.set(0, -13, 51, bm("shroomlight")); g.set(0, -13, 57, bm("shroomlight"))

    # коридор зал 2 -> логово
    g.box(-2, -22, 59, 2, -17, 61, deep); g.air(-1, -20, 59, 1, -18, 61)
    g.box(-1, -21, 59, 1, -21, 61, checker)
    g.set(0, -18, 60, bm("lantern[hanging=true]"))

    # логово: светящиеся колонны, кристаллы
    for (px, pz) in ((-4, 66), (4, 66), (-4, 71), (4, 71)):
        g.box(px, -20, pz, px, -16, pz, AM); g.set(px, -15, pz, CO)
    g.box(-1, -20, 72, 1, -20, 74, AM)
    for (x, z) in ((-6, 63), (6, 63), (-6, 75), (6, 75)): g.set(x, -20, z, bm("amethyst_cluster[facing=up]"))
    g.set(0, -13, 66, bm("shroomlight")); g.set(0, -13, 72, bm("shroomlight"))
    g.set(-7, -16, 68, CO); g.set(7, -16, 68, CO)
    g.mark("chest", 0, -19, 73, "Тестовый разлом")

    # ---------- лестница вниз: 20 ступеней на юг от z=-1 (поверх оболочек залов)
    for k in range(20):
        z, ys = -1 + k, -1 - k
        if z <= 3:                                          # внутри комнаты входа
            g.box(-2, ys - 1, z, 2, -1, z, DB)
            g.box(-1, ys + 1, z, 1, 4, z, AIR)
        else:                                               # тоннель в земле
            top = ys + 3
            g.box(-2, ys - 1, z, 2, top + 1, z, DB)
            g.air(-1, ys + 1, z, 1, top, z)
            if k % 4 == 1: g.set(1 if k % 8 == 1 else -1, top, z, bm("lantern[hanging=true]"))
        for x in (-1, 0, 1): g.set(x, ys, z, bm("deepslate_brick_stairs[facing=north]"))

    # ---------- залы и мобы
    g.mark("hall", -9, -27, 19, "1", (19, 14, 20))
    g.mark("hall", -7, -20, 44, "2", (15, 7, 15))
    g.mark("hall", -6, -20, 62, "3", (13, 7, 14))
    for (x, z, t) in ((-5, 22, "zombie"), (5, 22, "zombie"), (-5, 36, "skeleton"), (5, 36, "skeleton"),
                      (-4, 51, "zombie"), (4, 51, "zombie"), (-5, 56, "skeleton"), (5, 56, "skeleton"),
                      (-3, 66, "zombie"), (3, 66, "zombie"), (0, 70, "skeleton"), (-4, 70, "zombie")):
        g.mark("spawn", x, -20, z, t)
    return g


# ---------------------------------------------------------------- разрезание на части и запись
def split(g):
    parts = {"rift_test_entrance": {}, "rift_test_halls": {}}
    for pos, b in g.cells.items(): parts[part_of(pos)][pos] = b
    return parts


def part_of(pos): return "rift_test_entrance" if pos[2] < SPLIT_Z else "rift_test_halls"


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
    g = build_rift_test()
    parts = split(g)
    files = {}
    for name, cells in parts.items():
        marks = [m for m in g.marks if part_of(m[2]) == name]
        files[name + ".nbt"] = encode(name, cells, marks)
    return g, files


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


# ---------------------------------------------------------------- проверки
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
        if t == "spawn" and a not in reg["entity_type"]: bad.append("моба нет в реестре: " + a)
        if t not in ("start", "door", "brazier", "hall", "spawn", "chest"): bad.append("неизвестная метка: " + t)
    halls = [(int(a), p, s) for (t, a, p, s) in g.marks if t == "hall"]
    for (t, a, p, s) in g.marks:
        if t in ("spawn", "chest") and not any(all(h[1][i] <= p[i] < h[1][i] + h[2][i] for i in range(3)) for h in halls):
            bad.append(f"метка {t} {p} вне залов")
    if len({h[0] for h in halls}) != len(halls): bad.append("повтор номеров залов")
    return bad


def main():
    check = "--check" in sys.argv
    g, files = generate()
    bad = validate(g)
    tex = shard_png()
    if check:
        for name, data in files.items():
            f = OUT / name
            if not f.exists() or gzip.decompress(f.read_bytes()) != gzip.decompress(data): bad.append(f"{name}: устарел, запустите python tools/gen_dungeons.py")
        f = TEX / "seal_shard_rift.png"
        if not f.exists() or f.read_bytes() != tex: bad.append("seal_shard_rift.png: устарел, запустите python tools/gen_dungeons.py")
        print(f"[gen_dungeons] файлов {len(files)}, проблем {len(bad)}")
        if bad: sys.exit("\n".join(bad))
        return
    if bad: sys.exit("\n".join(bad))
    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in files.items():
        (OUT / name).write_bytes(data); print("записан", name, len(data), "байт")
    TEX.mkdir(parents=True, exist_ok=True)
    (TEX / "seal_shard_rift.png").write_bytes(tex); print("записана текстура seal_shard_rift.png")


if __name__ == "__main__":
    main()
