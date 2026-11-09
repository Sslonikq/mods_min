#!/usr/bin/env python3
"""
Физическая проходимость Цитадели (используется tools/gen_citadel.py --check).

Модель: мир режется на четверти блока по горизонтали (2x2 четверти на клетку) и на полублоки по высоте. Узел - (четверть x, четверть z, k),
где k - высота ног в полублоках от YMIN (ноги на y = YMIN + k/2). Узел существует, если под ногами твёрдый слой, а над ними 4 свободных
полублока (игрок 1,8 высотой - нужно 2 блока потолка). Форма блоков: полный, плита (нижняя/верхняя), ступень (нижняя половина целиком,
верхняя - только на стороне facing: facing = направление подъёма), лестница-ладдер (без коллизии), лава (смертельна, стоять нельзя).

Рёбра (шаг в четверть вбок, N/E/S/W):
  - подъём на полблока (ступень, плита) - без прыжка; подъём на блок (r=2) - прыжок, над стартом нужно 6 свободных полублоков;
  - спуск до DROP блоков (по умолчанию 3, без урона); на слизь - с любой высоты (падение гасится);
  - прыжок через разрыв по прямой: по ровной и вниз - до 3 блоков разрыва, на +0,5 - до 2,5, на +1 - до 2; над разрывом свободно 3,5 блока;
  - ладдер: вверх/вниз по клеткам с ладдером, вход и выход вбок.
Двери и тайники: правки edits (открыть/закрыть объём). Результат - граф (CSR вперёд и назад), BFS в обе стороны.
"""
import numpy as np

PASS, FULL, SLAB_B, SLAB_T, ST_N, ST_E, ST_S, ST_W, LAVA, LADDER, SLIME = range(11)
PASSABLE = {"air", "cave_air", "void_air", "lever", "amethyst_cluster", "soul_campfire", "campfire", "water", "lectern", "ladder",
            "torch", "wall_torch", "glow_lichen", "polished_blackstone_button"}
FACING = {"north": ST_N, "east": ST_E, "south": ST_S, "west": ST_W}
DX = (1, -1, 0, 0)
DZ = (0, 0, 1, -1)
JUMP_FLAT, JUMP_UP1, JUMP_UP2 = 7, 6, 5          # макс. расстояние в четвертях: 2g+1 при разрыве g=3 / 2,5 / 2 блока
JUMP_DOWN_MAX = 4                                  # прыжок через разрыв вниз - не глубже 2 блоков


def classify(b):
    if not b: return PASS
    name = b.split(":", 1)[-1]
    props = {}
    if "[" in name:
        name, p = name[:-1].split("[", 1)
        props = dict(kv.split("=") for kv in p.split(","))
    if name == "ladder": return LADDER
    if name in PASSABLE or name.endswith("_pressure_plate"): return PASS
    if name == "lava": return LAVA
    if name == "slime_block": return SLIME
    if name.endswith("_stairs"):
        if props.get("half", "bottom") == "top": return FULL
        return FACING[props.get("facing", "north")]
    if name.endswith("_slab"):
        t = props.get("type", "bottom")
        return SLAB_B if t == "bottom" else SLAB_T if t == "top" else FULL
    return FULL


class Graph:
    pass


def build(A, PAL, off, edits=(), drop=6, slime_any=True, diag_regions=()):
    """A - массив id блоков (x,z,y); off=(O,O,YO); edits - [(x1,y1,z1,x2,y2,z2,'open'|'shut')] в мировых координатах; drop - макс. безопасный спуск в полублоках."""
    ox, oz, oy = off
    lut = np.array([classify(b) for b in PAL], dtype=np.uint8)
    C = lut[A]
    for (x1, y1, z1, x2, y2, z2, op) in edits:
        C[x1 + ox:x2 + ox + 1, z1 + oz:z2 + oz + 1, y1 + oy:y2 + oy + 1] = PASS if op == "open" else FULL
    nx, nz, ny = C.shape
    Qx, Qz, L = 2 * nx, 2 * nz, 2 * ny
    full = (C == FULL) | (C == SLIME) | (C == LAVA)
    lower = full | (C == SLAB_B) | ((C >= ST_N) & (C <= ST_W))
    occ = np.zeros((Qx, Qz, L), dtype=bool)
    for a in (0, 1):
        for b in (0, 1):
            cover = ((C == ST_N) & (b == 0)) | ((C == ST_S) & (b == 1)) | ((C == ST_W) & (a == 0)) | ((C == ST_E) & (a == 1))
            occ[a::2, b::2, 0::2] = lower
            occ[a::2, b::2, 1::2] = full | (C == SLAB_T) | cover
    free = ~occ
    t = free[:, :, :-1] & free[:, :, 1:]
    c4 = np.zeros_like(free)
    c4[:, :, :-3] = t[:, :, :-2] & t[:, :, 2:]            # k..k+3 свободны
    surf = np.zeros_like(occ)
    surf[:, :, 1:] = occ[:, :, :-1] & c4[:, :, 1:]
    haz = np.repeat(np.repeat(np.repeat(C == LAVA, 2, 0), 2, 1), 2, 2)
    surf[:, :, 1:] &= ~haz[:, :, :-1]
    del haz
    virt = np.zeros_like(occ)
    for a in (0, 1):
        for b in (0, 1):
            virt[a::2, b::2, 0::2] = (C == LADDER) & c4[a::2, b::2, 0::2]
    nodes = surf | virt
    run = np.zeros((Qx, Qz, L), dtype=np.uint8)
    run[:, :, L - 1] = free[:, :, L - 1]
    for k in range(L - 2, -1, -1):
        run[:, :, k] = np.where(free[:, :, k], np.minimum(run[:, :, k + 1], 250) + 1, 0)
    idx = np.where(occ, np.arange(L, dtype=np.int16)[None, None, :], np.int16(-1))
    below = np.maximum.accumulate(idx, axis=2)
    del idx
    del occ, free, t, c4

    flat = np.flatnonzero(nodes)
    px = (flat // (Qz * L)).astype(np.int32)
    pz = ((flat // L) % Qz).astype(np.int32)
    pk = (flat % L).astype(np.int32)
    S, D = [], []
    ledge = np.zeros(len(flat), dtype=bool)
    ledge_dir = {}

    def add(sel, qx, qz, qk):
        S.append(flat[sel]); D.append((qx.astype(np.int64) * Qz + qz) * L + qk)

    for d in range(4):
        dx, dz = DX[d], DZ[d]
        qx, qz = px + dx, pz + dz
        inb = (qx >= 0) & (qx < Qx) & (qz >= 0) & (qz < Qz) & (pk < L - 8)
        sel = np.flatnonzero(inb)
        x1, z1, k1 = qx[sel], qz[sel], pk[sel]
        # 1) ступень/плита: +0,5 без прыжка
        m = surf[x1, z1, k1 + 1]
        add(sel[m], x1[m], z1[m], k1[m] + 1)
        # 2) прыжок на блок
        m = surf[x1, z1, k1 + 2] & (run[px[sel], pz[sel], k1] >= 6)
        add(sel[m], x1[m], z1[m], k1[m] + 2)
        # 3) вровень и спуск: первая опора под ногами в соседней четверти
        okq = run[x1, z1, k1] >= 4
        s = below[x1, z1, np.maximum(k1 - 1, 0)].astype(np.int32)
        kk = s + 1
        m = okq & (s >= 0) & (kk <= k1) & surf[x1, z1, np.clip(kk, 0, L - 1)]
        dr = k1 - kk
        sl = (C[x1 // 2, z1 // 2, np.clip(s, 0, L - 1) // 2] == SLIME) if slime_any else np.zeros_like(m)
        m &= (dr <= drop) | (sl & (dr <= 160))
        add(sel[m], x1[m], z1[m], kk[m])
        # 4) ладдер: вбок в клетку с ладдером на той же высоте
        for dk in (0, -1, -2):
            kv = k1 + dk
            m = virt[x1, z1, np.maximum(kv, 0)] & (kv >= 0)
            add(sel[m], x1[m], z1[m], kv[m])
        # 5) край: соседняя четверть свободна над ногами и там нет пола на 0..+1 блок (обычный шаг невозможен) - кандидат на прыжок
        walk_nb = surf[x1, z1, k1] | surf[x1, z1, k1 + 1] | surf[x1, z1, k1 + 2]
        led = (run[x1, z1, k1] >= 7) & ~walk_nb
        ledge[sel[led]] = True
        ledge_dir[d] = (sel[led])
    # 5) прыжки через разрыв: по осям везде, по диагонали - только в областях diag_regions (паркур башни, провал обсерватории)
    def jumps(cidx, offs):
        ax, az, ak = px[cidx], pz[cidx], pk[cidx]
        for (a, b) in offs:
            dq = (a * a + b * b) ** 0.5
            n = int(max(abs(a), abs(b)) * 4)
            cells = []
            for i in range(1, n):
                t = i / n
                c = (int(np.floor(0.5 + a * t)), int(np.floor(0.5 + b * t)))
                if c != (0, 0) and c != (a, b) and c not in cells: cells.append(c)
            alive = np.ones(len(cidx), dtype=bool)
            jx, jz = ax + a, az + b
            alive &= (jx >= 0) & (jx < Qx) & (jz >= 0) & (jz < Qz)
            for (ox, oz) in cells:
                mx, mz = ax + ox, az + oz
                alive &= (mx >= 0) & (mx < Qx) & (mz >= 0) & (mz < Qz)
                alive &= run[np.clip(mx, 0, Qx - 1), np.clip(mz, 0, Qz - 1), ak] >= 7
                if not alive.any(): break
            if not alive.any(): continue
            jx = np.clip(jx, 0, Qx - 1); jz = np.clip(jz, 0, Qz - 1)
            for r in range(-JUMP_DOWN_MAX, 3):
                if r == 2 and dq > JUMP_UP2: continue
                if r == 1 and dq > JUMP_UP1: continue
                if dq > JUMP_FLAT: continue
                kq = ak + r
                m = alive & (kq >= 0) & (kq < L) & surf[jx, jz, np.clip(kq, 0, L - 1)]
                if r <= 0: m &= run[jx, jz, ak] >= 4
                if r < 0:
                    sup = below[jx, jz, np.maximum(ak - 1, 0)].astype(np.int32) + 1
                    m &= sup == kq
                if m.any():
                    S.append(flat[cidx[m]]); D.append((jx[m].astype(np.int64) * Qz + jz[m]) * L + kq[m])

    axis_offs = [(dx * j, dz * j) for (dx, dz) in zip(DX, DZ) for j in range(2, JUMP_FLAT + 1)]
    jumps(np.flatnonzero(ledge), axis_offs)
    if diag_regions:
        inreg = np.zeros(len(flat), dtype=bool)
        wx, wz = px // 2 - ox, pz // 2 - oz
        for (x1, z1, x2, z2) in diag_regions: inreg |= (wx >= x1) & (wx <= x2) & (wz >= z1) & (wz <= z2)
        diag_offs = [(sa * a, sb * b) for a in range(1, 7) for b in range(1, 7) if (a * a + b * b) ** 0.5 <= JUMP_FLAT for sa in (1, -1) for sb in (1, -1)]
        jumps(np.flatnonzero(inreg & ledge), diag_offs)
    # 6) ладдер вверх и вниз
    lx, lz, lk = np.nonzero(virt)
    for dk in (2, -2):
        k2 = lk + dk
        m = (k2 >= 0) & (k2 < L)
        k2c = np.clip(k2, 0, L - 1)
        m &= nodes[lx, lz, k2c]
        a = (lx[m].astype(np.int64) * Qz + lz[m]) * L + lk[m]
        b = (lx[m].astype(np.int64) * Qz + lz[m]) * L + k2[m]
        S.append(a); D.append(b)
    src = np.concatenate(S); dst = np.concatenate(D)
    g = Graph()
    g.flat = flat
    g.n = len(flat)
    ids = lambda a: np.searchsorted(flat, a)
    s_id, d_id = ids(src), ids(dst)
    ok = (d_id < g.n) & (flat[np.minimum(d_id, g.n - 1)] == dst)
    s_id, d_id = s_id[ok], d_id[ok]
    g.fwd = _csr(s_id, d_id, g.n)
    g.bwd = _csr(d_id, s_id, g.n)
    g.edges = len(s_id)
    g.X = (px // 2 - ox).astype(np.int32)
    g.Z = (pz // 2 - oz).astype(np.int32)
    g.K = pk
    g.virt = virt
    g.C = C
    g.off = off
    g.L = L
    g.Qx, g.Qz = Qx, Qz
    return g


def _csr(s, d, n):
    order = np.argsort(s, kind="stable")
    s, d = s[order], d[order]
    indptr = np.concatenate([[0], np.cumsum(np.bincount(s, minlength=n))]).astype(np.int64)
    return indptr, d.astype(np.int32)


def bfs(g, seeds, backward=False):
    indptr, ind = g.bwd if backward else g.fwd
    seen = np.zeros(g.n, dtype=bool)
    fr = np.unique(np.asarray(seeds, dtype=np.int64))
    seen[fr] = True
    while len(fr):
        st, en = indptr[fr], indptr[fr + 1]
        cnt = en - st
        tot = int(cnt.sum())
        if tot == 0: break
        base = np.repeat(st - (np.cumsum(cnt) - cnt), cnt)
        nb = ind[base + np.arange(tot)]
        nb = np.unique(nb[~seen[nb]])
        seen[nb] = True
        fr = nb
    return seen


def feet_y(g, ids, ymin):
    return ymin + g.K[ids] / 2.0


def near_ids(g, ymin, x, y, z, near=2, dy=1.0):
    """Номера узлов: клетки в пределах near по x,z и по высоте ног |ноги - y| <= dy."""
    fy = ymin + g.K / 2.0
    m = (np.abs(g.X - x) <= near) & (np.abs(g.Z - z) <= near) & (np.abs(fy - y) <= dy)
    return np.flatnonzero(m)


def solid_cell(g, x, y, z):
    ox, oz, oy = g.off
    try:
        c = g.C[x + ox, z + oz, y + oy]
    except IndexError:
        return False
    return c not in (PASS, LADDER)


def sees(g, ex, ey, ez, tx, ty, tz):
    """Луч из глаз (мировые координаты) в центр целевого блока: пока не упёрся в твёрдую клетку, кроме целевой. Грубо: шаг 0,1."""
    dx, dy, dz = tx - ex, ty - ey, tz - ez
    n = max(2, int(np.sqrt(dx * dx + dy * dy + dz * dz) / 0.1))
    tgt = (int(np.floor(tx)), int(np.floor(ty)), int(np.floor(tz)))
    for i in range(1, n):
        f = i / n
        c = (int(np.floor(ex + dx * f)), int(np.floor(ey + dy * f)), int(np.floor(ez + dz * f)))
        if c == tgt: return True
        if solid_cell(g, *c): return False
    return True


def can_touch(g, ymin, ids, target, reach=4.0):
    """Есть ли среди узлов ids точка, откуда виден и достижим рукой (reach) блок target=(x,y,z)."""
    tx, ty, tz = target[0] + 0.5, target[1] + 0.5, target[2] + 0.5
    ids = np.asarray(ids)
    if not len(ids): return False
    ex = (g.X[ids] + 0.5)
    ez = (g.Z[ids] + 0.5)
    ey = ymin + g.K[ids] / 2.0 + 1.62
    d = np.sqrt((ex - tx) ** 2 + (ey - ty) ** 2 + (ez - tz) ** 2)
    for i in np.argsort(d):
        if d[i] > reach: break
        if sees(g, ex[i], ey[i], ez[i], tx, ty, tz): return True
    return False
