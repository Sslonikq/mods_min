package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Конвейер данжей в обычном мире (фаза 4.1).
 *
 * Откуда берутся данжи: мир поделён на клетки cell×cell; в каждой клетке для каждого типа данжа есть одна детерминированная
 * точка (по сиду мира). Когда игрок подходит ближе PREPARE_RANGE, данж регистрируется в DungeonData (с высотой земли),
 * а его части (.nbt, tools/gen_dungeons.py) по очереди ставятся в мир, когда игрок подходит ближе PART_RANGE, —
 * по BUDGET блоков за тик. Так данж в 150-250 блоков не требует прогрузки всего сразу.
 * Внутри: мобы по залам (метки spawn в залах), жаровни-якоря, дверь-осколок, защита (DungeonGuard).
 */
final class Dungeons {
    private Dungeons() {}

    // ================================================================ определения (чистая логика)
    /** Зал: прямоугольник в координатах данжа, мобы и сундук награды. */
    static final class Hall {
        final int id, x, y, z, sx, sy, sz;
        final List<Mark> spawns = new ArrayList<>();
        Mark chest;
        /** Метка босса зала (логово): зал будит босса, а не мобов; зачищен, когда босс убит. */
        Mark boss;
        Hall(Mark m) { id = Integer.parseInt(m.a); x = m.x; y = m.y; z = m.z; sx = m.sx; sy = m.sy; sz = m.sz; }
        boolean contains(double px, double py, double pz) { return px >= x && px < x + sx && py >= y && py < y + sy + 1 && pz >= z && pz < z + sz; }
        /** Коробка зала с запасом pad по всем сторонам. */
        boolean containsPad(double px, double py, double pz, int pad) {
            return px >= x - pad && px < x + sx + pad && py >= y - pad && py < y + sy + 1 + pad && pz >= z - pad && pz < z + sz + pad;
        }
    }

    /**
     * Маска клеток данжа: точный BitSet по габаритной рамке (Разлом: 187x69x197 = 2,5 млн бит, ~320 КБ) плюс грубая сетка
     * 16x16 с диапазоном высот по каждой колонне (для проверок «рядом» с большим запасом). Координаты — относительно начала данжа.
     */
    static final class Mask {
        final int x0, y0, z0, sx, sy, sz, gx, gz;
        private final BitSet bits;
        private final int[] cmin, cmax;
        private int count;

        Mask(int[] b) {
            x0 = b[0]; y0 = b[1]; z0 = b[2]; sx = b[3] - b[0]; sy = b[4] - b[1]; sz = b[5] - b[2];
            bits = new BitSet(Math.max(1, sx * sy * sz));
            gx = (sx + 15) >> 4; gz = (sz + 15) >> 4;
            cmin = new int[gx * gz]; cmax = new int[gx * gz];
            Arrays.fill(cmin, Integer.MAX_VALUE); Arrays.fill(cmax, Integer.MIN_VALUE);
        }

        private int idx(int x, int y, int z) {
            int rx = x - x0, ry = y - y0, rz = z - z0;
            if (rx < 0 || ry < 0 || rz < 0 || rx >= sx || ry >= sy || rz >= sz) return -1;
            return (rx * sy + ry) * sz + rz;
        }

        void add(int x, int y, int z) {
            int i = idx(x, y, z);
            if (i < 0 || bits.get(i)) return;
            bits.set(i); count++;
            int g = ((x - x0) >> 4) * gz + ((z - z0) >> 4);
            if (y < cmin[g]) cmin[g] = y;
            if (y > cmax[g]) cmax[g] = y;
        }

        void addBox(int x, int y, int z, int w, int h, int d) {
            for (int a = 0; a < w; a++) for (int b = 0; b < h; b++) for (int c = 0; c < d; c++) add(x + a, y + b, z + c);
        }

        boolean has(int x, int y, int z) { int i = idx(x, y, z); return i >= 0 && bits.get(i); }
        int count() { return count; }
        /** Низшая клетка колонны сетки 16x16 (gi, gk) относительно начала данжа; Integer.MAX_VALUE - колонна пуста. */
        int colMin(int gi, int gk) { return cmin[gi * gz + gk]; }

        /** Есть ли клетка маски в кубе радиуса r вокруг клетки (точно, для малого r). */
        boolean hasNear(int x, int y, int z, int r) {
            for (int a = x - r; a <= x + r; a++) for (int b = y - r; b <= y + r; b++) for (int c = z - r; c <= z + r; c++) if (has(a, b, c)) return true;
            return false;
        }

        /** Грубо: есть ли колонна 16x16 с подходящим диапазоном высот не дальше r блоков (для больших запасов). */
        boolean near(int x, int y, int z, int r) {
            int g0x = Math.max(0, (x - r - x0) >> 4), g1x = Math.min(gx - 1, (x + r - x0) >> 4);
            int g0z = Math.max(0, (z - r - z0) >> 4), g1z = Math.min(gz - 1, (z + r - z0) >> 4);
            for (int a = g0x; a <= g1x; a++)
                for (int c = g0z; c <= g1z; c++) {
                    int g = a * gz + c;
                    if (cmin[g] <= y + r && cmax[g] >= y - r) return true;
                }
            return false;
        }
    }

    /** Тип данжа: сетка расстановки, имена частей. Данные частей читаются лениво. */
    static final class Def {
        final String id; final int cell, margin, layout; final String[] parts;
        /** Допустимый перепад высот земли под данжем (0 — не проверять) и жёсткий предел, после которого клетка пропускается; наземные данжи. */
        int flat = 0, maxVar = Integer.MAX_VALUE;
        /** Подземный данж: кровля (y относительно входа), которая не должна торчать над землёй по всей рамке (0 - не проверять), и число запасных точек. */
        int roof = 0, tries = 8;
        /** Данж, с которым этот не должен пересекаться (по рамкам с запасом): Город Спящих обходит Разломы. */
        Def avoid;
        private List<DungeonTemplate> tpl;
        private int[] bounds;
        private final List<Hall> halls = new ArrayList<>();
        private final List<Mark> doors = new ArrayList<>(), braziers = new ArrayList<>();
        private Mark start;
        private final Map<String, List<Mark>> byType = new HashMap<>();
        private Mask mask;
        private String sig;

        /**
         * layout — номер версии раскладки: поднимать вручную, когда перегенерация шаблонов делает старые записи в мирах негодными
         * (иначе прогресс игроков сохраняется, даже если .nbt поменялись).
         */
        Def(String id, int cell, int margin, int layout, String... parts) { this.id = id; this.cell = cell; this.margin = margin; this.layout = layout; this.parts = parts; }

        String key(int cx, int cz) { return id + "_" + cx + "_" + cz; }

        /** Наземный данж: предпочтительный перепад высот flat и предел maxVar (выше — в клетке данжа не будет). */
        Def ground(int flat, int maxVar) { this.flat = flat; this.maxVar = maxVar; return this; }
        Def avoiding(Def other) { this.avoid = other; return this; }
        /** Подземный данж: крыша на roof (отрицательное) должна быть под землёй по всей рамке (DungeonSite), maxCover - предел, больше которого точка отвергается; пробуем tries точек. */
        Def underground(int roof, int maxCover, int tries) { this.roof = roof; this.maxVar = maxCover; this.tries = tries; return this; }

        synchronized List<DungeonTemplate> templates() {
            if (tpl == null) {
                List<DungeonTemplate> l = new ArrayList<>();
                try { for (String p : parts) l.add(DungeonTemplate.load(p)); }
                catch (IOException e) { throw new IllegalStateException("данж " + id + ": " + e.getMessage(), e); }
                index(l);
                tpl = l;
            }
            return tpl;
        }

        /** Подгрузка заранее заданных частей (для тестов). */
        synchronized void setTemplates(List<DungeonTemplate> l) { index(l); tpl = l; }

        private void index(List<DungeonTemplate> l) {
            int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            halls.clear(); doors.clear(); braziers.clear(); byType.clear(); start = null;
            List<Mark> all = new ArrayList<>();
            for (DungeonTemplate t : l) {
                b[0] = Math.min(b[0], t.ox); b[1] = Math.min(b[1], t.oy); b[2] = Math.min(b[2], t.oz);
                b[3] = Math.max(b[3], t.ox + t.sx); b[4] = Math.max(b[4], t.oy + t.sy); b[5] = Math.max(b[5], t.oz + t.sz);
                all.addAll(t.marksInDungeon());
            }
            bounds = b;
            sig = String.join("+", parts) + ":v" + layout;
            for (Mark m : all) {
                byType.computeIfAbsent(m.t, k -> new ArrayList<>()).add(m);
                switch (m.t) {
                    case "hall": halls.add(new Hall(m)); break;
                    case "door": doors.add(m); break;
                    case "brazier": braziers.add(m); break;
                    case "start": start = m; break;
                    default: break;
                }
            }
            for (Mark m : all) {
                for (Hall h : halls) {
                    if (h.contains(m.x, m.y, m.z)) {
                        if (m.t.equals("spawn")) h.spawns.add(m);
                        else if (m.t.equals("chest")) h.chest = m;
                        else if (m.t.equals("boss")) h.boss = m;
                    }
                }
            }
            // маска «внутри данжа»: клетки шаблонов + коробки залов и меток (воздух залов и проходов тоже внутри)
            Mask mk = new Mask(b);
            for (DungeonTemplate t : l)
                for (int i = 0; i < t.count(); i++) mk.add(t.ox + t.bx[i], t.oy + t.by[i], t.oz + t.bz[i]);
            for (Hall h : halls) mk.addBox(h.x, h.y, h.z, h.sx, h.sy + 1, h.sz);
            for (Mark m : all) mk.addBox(m.x, m.y, m.z, m.sx, m.sy, m.sz);
            mask = mk;
        }

        /** Подпись раскладки "части:vN" (хранится в DungeonData.Rec.layout), меняется только при ручном повышении номера или смене набора частей. */
        String signature() { templates(); return sig; }
        /** Маска клеток данжа (координаты относительно начала данжа). */
        Mask mask() { templates(); return mask; }
        Hall hall(int id) { for (Hall h : halls()) if (h.id == id) return h; return null; }
        /** У зала есть дверь-печать (метка seal). */
        boolean sealed(int hallId) {
            for (Mark m : marks("seal")) if (Integer.parseInt(m.a.split(",")[0].trim()) == hallId) return true;
            return false;
        }

        /** {minX, minY, minZ, maxX, maxY, maxZ} относительно начала данжа, max не включая. */
        int[] bounds() { templates(); return bounds; }
        List<Hall> halls() { templates(); return halls; }
        List<Mark> doors() { templates(); return doors; }
        List<Mark> braziers() { templates(); return braziers; }
        Mark start() { templates(); return start; }
        /** Метки типа t (координаты данжа): steps, beams, mirror, platform, lever, secret, seal — см. DungeonMech. */
        List<Mark> marks(String t) { templates(); return byType.getOrDefault(t, List.of()); }
        boolean hasMech() { templates(); for (String t : DungeonMech.TYPES) if (byType.containsKey(t)) return true; return false; }
        long allParts() { return (1L << parts.length) - 1; }
    }

    /**
     * Разлом: вход на поверхности, 3 уровня вниз, 16 залов (основной путь 1-7), ~190x200 блоков, три части по z (tools/gen_dungeons.py).
     * Клетка 768, отступ от края клетки 192: данжи одного типа не ближе ~290 блоков, запасные точки (±48) их не сближают.
     */
    static final Def RIFT = new Def("rift", 768, 192, 4, "rift_entrance", "rift_mid", "rift_deep").underground(-4, DungeonSite.COVER_MAX, 16);
    /**
     * Город Спящих (4.5): наземный, ~97x97 с рвом, шпиль Монолита до ~64 блоков, три части по z (tools/gen_city.py). Клетка 768, отступ 128:
     * города не ближе 256 блоков друг от друга (при размере 97 и запасных точках ±48 не пересекаются); Разломы обходит (Def.avoid).
     */
    static final Def SLEEP = new Def("sleep", 768, 128, 3, "sleep_north", "sleep_mid", "sleep_south").ground(5, 8).avoiding(RIFT);
    static final Def[] DEFS = {RIFT, SLEEP};
    /** Стенд механик для теста (все метки DungeonMech на малой площади). В мире не ставится: в DEFS его нет. */
    static final Def MECH_TEST = new Def("mechtest", 768, 128, 1, "mech_test");

    static Def def(String id) { for (Def d : DEFS) if (d.id.equals(id)) return d; return null; }

    // ---------------------------------------------------------------- расстановка по сетке (детерминированная)
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Запас вокруг рамки обходимого данжа: его запасные точки смещены на ±48 + зазор. */
    static final int AVOID_PAD = 52;

    /** Точка данжа в клетке (cx,cz); attempt > 0 — запасные точки в пределах ±48 блоков (если основная в воде). Данж с avoid не налезает на обходимый. */
    static int[] site(long seed, Def d, int cx, int cz, int attempt) {
        int[] s = site0(seed, d, cx, cz, 0, 0);
        if (d.avoid != null) {
            for (int salt = 1; salt < 32 && clashes(seed, d, s); salt++) s = site0(seed, d, cx, cz, 0, salt);
        }
        if (attempt > 0) {   // запасные точки смещены от основной (не от клетки): ближайшая к игроку, чанки рядом
            long h = hash(seed, d, cx, cz, 0);
            s = new int[]{s[0] + (int) Long.remainderUnsigned(mix(h + 10L * attempt), 97) - 48,
                    s[1] + (int) Long.remainderUnsigned(mix(h + 10L * attempt + 5), 97) - 48};
        }
        return s;
    }

    /** Точка без обхода (для теста: как часто обход вообще нужен). */
    static int[] site0Raw(long seed, Def d, int cx, int cz) { return site0(seed, d, cx, cz, 0, 0); }

    private static long hash(long seed, Def d, int cx, int cz, int salt) {
        return mix(seed + 0x9E3779B97F4A7C15L * (cx * 31L + 7) + 0xC2B2AE3D27D4EB4FL * (cz * 17L + 3) + d.id.hashCode() * 0x632BE59BD9B4E019L + salt * 0x2545F4914F6CDD1DL);
    }

    private static int[] site0(long seed, Def d, int cx, int cz, int attempt, int salt) {
        long h = hash(seed, d, cx, cz, salt);
        int span = d.cell - 2 * d.margin;
        int x = cx * d.cell + d.margin + (int) Long.remainderUnsigned(mix(h + 1), span);
        int z = cz * d.cell + d.margin + (int) Long.remainderUnsigned(mix(h + 2), span);
        return new int[]{x, z};
    }

    /** Рамка данжа d в точке s пересекается с рамкой обходимого данжа (с запасом AVOID_PAD) вокруг любой из его соседних точек. */
    static boolean clashes(long seed, Def d, int[] s) {
        Def o = d.avoid;
        int[] b, ob;
        try { b = d.bounds(); ob = o.bounds(); } catch (IllegalStateException e) { return false; }   // шаблонов нет: обход выключен
        int ca = Math.floorDiv(s[0], o.cell), za = Math.floorDiv(s[1], o.cell);
        for (int ax = ca - 1; ax <= ca + 1; ax++) {
            for (int az = za - 1; az <= za + 1; az++) {
                int[] r = site(seed, o, ax, az, 0);
                if (s[0] + b[0] < r[0] + ob[3] + AVOID_PAD && r[0] + ob[0] - AVOID_PAD < s[0] + b[3]
                        && s[1] + b[2] < r[1] + ob[5] + AVOID_PAD && r[1] + ob[2] - AVOID_PAD < s[1] + b[5]) return true;
            }
        }
        return false;
    }

    static final class Hit {
        final int x, z; final boolean registered; final double dist;
        Hit(int x, int z, boolean registered, double dist) { this.x = x; this.z = z; this.registered = registered; this.dist = dist; }
    }

    /** Ближайший данж типа d (по горизонтали): уже построенный (reg даёт его x,z по ключу) или расчётная точка клетки. */
    static Hit nearest(long seed, Def d, double px, double pz, Function<String, int[]> reg) { return nearest(seed, d, px, pz, reg, null); }

    /** dead — клетки, где данж не поставлен (океан): их пропускаем. */
    static Hit nearest(long seed, Def d, double px, double pz, Function<String, int[]> reg, Predicate<String> dead) {
        int cx0 = Math.floorDiv((int) Math.floor(px), d.cell), cz0 = Math.floorDiv((int) Math.floor(pz), d.cell);
        Hit best = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = cx0 + dx, cz = cz0 + dz;
                if (dead != null && dead.test(d.key(cx, cz))) continue;
                int[] r = reg == null ? null : reg.apply(d.key(cx, cz));
                int[] s = r != null ? r : site(seed, d, cx, cz, 0);
                double ddx = s[0] - px, ddz = s[1] - pz, dist = Math.sqrt(ddx * ddx + ddz * ddz);
                if (best == null || dist < best.dist) best = new Hit(s[0], s[1], r != null, dist);
            }
        }
        if (best == null) { int[] s = site(seed, d, cx0, cz0, 0); best = new Hit(s[0], s[1], false, Math.hypot(s[0] - px, s[1] - pz)); }
        return best;
    }

    /** Расстояние от точки до прямоугольника (0, если внутри). */
    static double distToRect(double px, double pz, int minx, int minz, int sx, int sz) {
        double dx = Math.max(Math.max(minx - px, 0), px - (minx + sx)), dz = Math.max(Math.max(minz - pz, 0), pz - (minz + sz));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Блоки всех частей в мировых координатах: ключ — упакованные x,y,z, значение — строка состояния ("minecraft:stone_bricks[...]"). */
    static Map<Long, String> assemble(Def d, int ox, int oy, int oz) {
        Map<Long, String> out = new HashMap<>();
        for (DungeonTemplate t : d.templates()) {
            for (int i = 0; i < t.count(); i++) out.put(pack(ox + t.ox + t.bx[i], oy + t.oy + t.by[i], oz + t.oz + t.bz[i]), t.palette[t.bs[i]]);
        }
        return out;
    }

    static long pack(int x, int y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) ((y + 2048) & 0xFFF); }

    // ================================================================ состояние в памяти
    static long tk = 0;
    static boolean warned = false, warnedMech = false, warnedMobs = false, warnedBoss = false;
    static final int PREPARE_RANGE = 80, PART_RANGE = 96, BUDGET = 1500, EMPTY_TICKS = 600;
    static final Map<String, Long> SPAWNED_AT = new HashMap<>(), LAST_INSIDE = new HashMap<>(), LAST_HALL = new HashMap<>();
    static final Map<UUID, String> PENDING = new HashMap<>();
    static final List<Placing> PLACING = new ArrayList<>();
    static final Set<String> NO_TEMPLATE = new HashSet<>();

    /** Часть данжа, которая сейчас ставится в мир порциями. */
    static final class Placing {
        final ServerLevel level; final DungeonData.Rec rec; final int part; final DungeonTemplate t; final int ox, oy, oz;
        int idx = 0; BlockState[] states; long lastChunk = Long.MIN_VALUE;
        Placing(ServerLevel level, DungeonData.Rec rec, int part, DungeonTemplate t) {
            this.level = level; this.rec = rec; this.part = part; this.t = t;
            ox = rec.x + t.ox; oy = rec.y + t.oy; oz = rec.z + t.oz;
        }
        // Вторая фаза: обновление соседей по внешней оболочке части (вода, лава, песок на стыке «просыпаются», а не висят).
        int sidx = 0, oidx = 0; BitSet occ;
        boolean done() { return idx >= t.count() && sidx >= t.count(); }
        int step(int budget) {
            if (states == null) {
                states = new BlockState[t.palette.length];
                for (int i = 0; i < states.length; i++) states[i] = state(t.palette[i]);
            }
            int n = 0;
            while (idx < t.count() && n < budget) {
                int x = ox + t.bx[idx], z = oz + t.bz[idx];
                long ck = ((long) (x >> 4) << 32) ^ (z >> 4);
                if (ck != lastChunk) { level.m_6325_(x >> 4, z >> 4); lastChunk = ck; }
                BlockState st = states[t.bs[idx]];
                if (st != null) level.m_7731_(new BlockPos(x, oy + t.by[idx], z), st, 18);
                idx++; n++;
            }
            if (idx >= t.count()) n += settle(budget - n);
            return n;
        }

        private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

        private int cell(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= t.sx || y >= t.sy || z >= t.sz) return -1;
            return (x * t.sy + y) * t.sz + z;
        }

        /** occ строится порциями в счёт бюджета (8 клеток за единицу), затем обход оболочки (4 клетки за единицу, обновление соседей — 3). */
        private int settle(int budget) {
            if (occ == null) occ = new BitSet(Math.max(1, t.sx * t.sy * t.sz));
            if (oidx < t.count()) {
                int lim = Math.min(t.count(), oidx + Math.max(1, budget) * 8);
                int done = lim - oidx;
                for (; oidx < lim; oidx++) {
                    int c = cell(t.bx[oidx], t.by[oidx], t.bz[oidx]);
                    if (c >= 0) occ.set(c);
                }
                return Math.max(1, done / 8);
            }
            int n = 0, scanned = 0;
            while (sidx < t.count() && n < budget) {
                int i = sidx++;
                if ((++scanned & 3) == 0) n++;
                BlockState st = states[t.bs[i]];
                if (st == null) continue;
                boolean edge = false;
                for (int[] d : DIRS) {
                    int c = cell(t.bx[i] + d[0], t.by[i] + d[1], t.bz[i] + d[2]);
                    if (c < 0 || !occ.get(c)) { edge = true; break; }
                }
                if (!edge) continue;
                level.m_46672_(new BlockPos(ox + t.bx[i], oy + t.by[i], oz + t.bz[i]), st.m_60734_());
                n += 3;
            }
            return Math.max(1, n);
        }
    }

    // ================================================================ блоки
    /** Состояние по строке "stone_bricks" или "minecraft:soul_campfire[lit=true]" (свойства через StateDefinition, без парсера команд). */
    static BlockState state(String spec) {
        String name = spec, props = null;
        int i = spec.indexOf('[');
        if (i >= 0) { name = spec.substring(0, i); props = spec.substring(i + 1, spec.length() - 1); }
        if (name.startsWith("minecraft:")) name = name.substring(10);
        Block b = Fx.block(name);
        if (b == null) return null;
        BlockState st = b.m_49966_();
        if (props != null) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                if (p.length != 2) continue;
                Property<?> pr = b.m_49965_().m_61081_(p[0]);
                if (pr != null) st = with(st, pr, p[1]);
            }
        }
        return st;
    }

    /** То же состояние с другим значением свойства (если у блока такого свойства нет — без изменений). */
    static BlockState setProp(BlockState st, String name, String value) {
        Property<?> pr = st.m_60734_().m_49965_().m_61081_(name);
        return pr == null ? st : with(st, pr, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static BlockState with(BlockState st, Property pr, String v) {
        Optional o = pr.m_6215_(v);
        return o.isPresent() ? (BlockState) st.m_61124_(pr, (Comparable) o.get()) : st;
    }

    static boolean woody(BlockState st) {
        ResourceLocation k = ForgeRegistries.BLOCKS.getKey(st.m_60734_());
        if (k == null) return false;
        String p = k.m_135815_();
        return p.endsWith("_log") || p.endsWith("_wood") || p.contains("leaves") || p.contains("vine") || p.endsWith("_stem") || p.contains("bee_nest");
    }

    /** Поверхность земли в колонне: первый свободный y над землёй (деревья и листва пропускаются). */
    static int groundY(ServerLevel sl, int x, int z) {
        int y = sl.m_6924_(Heightmap.Types.valueOf("MOTION_BLOCKING_NO_LEAVES"), x, z);
        for (int i = 0; i < 24 && y > sl.m_141937_() + 2; i++) {
            if (!woody(sl.m_8055_(new BlockPos(x, y - 1, z)))) break;
            y--;
        }
        return y;
    }

    static boolean wet(ServerLevel sl, int x, int y, int z) { return !sl.m_8055_(new BlockPos(x, y, z)).m_60819_().m_76178_(); }

    // ================================================================ защита от эффектов палочек
    /**
     * Данжи обычного мира для быстрой проверки позиции в цикле (получать один раз на проход). Рамка — быстрый предфильтр,
     * затем точная маска клеток шаблона: на поверхности вокруг данжа защиты нет.
     * Рамка: {minX, minY, minZ, maxX, maxY, maxZ} (мировые, max не включая) [+ {ox, oy, oz} начала данжа, если есть маска].
     */
    static final class Shield {
        static final Shield NONE = new Shield(List.of());
        private final List<int[]> boxes;
        private final List<Mask> masks;   // null — только рамки
        Shield(List<int[]> boxes) { this(boxes, null); }
        Shield(List<int[]> boxes, List<Mask> masks) { this.boxes = boxes; this.masks = masks; }
        boolean at(int x, int y, int z) {
            for (int i = 0; i < boxes.size(); i++) {
                int[] b = boxes.get(i);
                if (x < b[0] || x >= b[3] || y < b[1] || y >= b[4] || z < b[2] || z >= b[5]) continue;
                Mask m = masks == null ? null : masks.get(i);
                if (m == null || m.has(x - b[6], y - b[7], z - b[8])) return true;
            }
            return false;
        }
        boolean at(BlockPos p) { return !boxes.isEmpty() && at(p.m_123341_(), p.m_123342_(), p.m_123343_()); }
    }

    static Shield shield(Level l) {
        if (!(l instanceof ServerLevel sl) || !Dims.is(sl, Level.f_46428_)) return Shield.NONE;
        DungeonData data = DungeonData.get(sl);
        if (data.recs.isEmpty()) return Shield.NONE;
        List<int[]> l2 = new ArrayList<>();
        List<Mask> ms = new ArrayList<>();
        for (DungeonData.Rec r : data.recs.values()) {
            Def d = r.def();
            if (d == null || r.bossDead) continue;   // босс убит: блоки данжа больше не защищены
            try {
                int[] b = d.bounds();
                l2.add(new int[]{r.x + b[0], r.y + b[1], r.z + b[2], r.x + b[3], r.y + b[4], r.z + b[5], r.x, r.y, r.z});
                ms.add(d.mask());
            } catch (IllegalStateException e) { noTemplate(d, e); }
        }
        return l2.isEmpty() ? Shield.NONE : new Shield(l2, ms);
    }

    /** Позиция внутри данжа: эффекты палочек пропускают её так же, как бедрок. Для циклов лучше один раз взять shield(level). */
    static boolean protectedAt(Level l, BlockPos p) { return shield(l).at(p); }

    /** Нет шаблона данжа: защита выключена. Строка в лог — один раз на тип данжа. */
    static void noTemplate(Def d, Exception e) {
        if (NO_TEMPLATE.add(d.id)) System.err.println("[explosionwand] данж " + d.id + ": нет шаблона, защита выключена (" + e.getMessage() + ")");
    }

    // ================================================================ поиск данжа по позиции
    static DungeonData.Rec at(DungeonData data, double x, double y, double z, int pad) {
        for (DungeonData.Rec r : data.recs.values()) if (r.contains(x, y, z, pad)) return r;
        return null;
    }

    /** Как at, но без данжей с убитым боссом: защита блоков (ломать и ставить нельзя) действует, пока босс жив. */
    static DungeonData.Rec guardedAt(DungeonData data, double x, double y, double z, int pad) {
        for (DungeonData.Rec r : data.recs.values()) if (!r.bossDead && r.contains(x, y, z, pad)) return r;
        return null;
    }

    /** Блок под защитой данжа (босс этого данжа жив), только обычный мир. */
    static boolean guarded(Level l, BlockPos p) { return guarded(l, p.m_123341_() + 0.5, p.m_123342_() + 0.5, p.m_123343_() + 0.5, 0); }

    static boolean guarded(Level l, double x, double y, double z, int pad) {
        if (!(l instanceof ServerLevel sl) || !Dims.is(sl, Level.f_46428_)) return false;
        return guardedAt(DungeonData.get(sl), x, y, z, pad) != null;
    }

    /** Данж, в границах которого лежит позиция (только обычный мир). */
    static DungeonData.Rec at(Level l, double x, double y, double z) {
        if (!(l instanceof ServerLevel sl) || !Dims.is(sl, Level.f_46428_)) return null;
        return at(DungeonData.get(sl), x, y, z, 0);
    }

    /** Сущность внутри данжа или не дальше pad блоков от его границ (для Осколка Печати). */
    static boolean near(Level l, Entity e, int pad) { return near(l, e.m_20185_(), e.m_20186_(), e.m_20189_(), pad); }

    static boolean near(Level l, double x, double y, double z, int pad) {
        if (!(l instanceof ServerLevel sl) || !Dims.is(sl, Level.f_46428_)) return false;
        return at(DungeonData.get(sl), x, y, z, pad) != null;
    }

    static boolean inside(Level l, BlockPos p) { return at(l, p.m_123341_(), p.m_123342_(), p.m_123343_()) != null; }

    // ================================================================ тик
    static void tick(ServerLevel sl) {
        if (!Dims.is(sl, Level.f_46428_)) return;
        tk++;
        try { tick0(sl); }
        catch (Exception e) { if (!warned) { warned = true; e.printStackTrace(); } }   // данжи не должны ронять игру
    }

    /** Конец сервера (в одиночке — выход в меню): статическое состояние не должно перейти в следующий мир. */
    static void clearState() {
        PLACING.clear(); SITES.clear(); SPAWNED_AT.clear(); LAST_INSIDE.clear(); PENDING.clear(); NO_TEMPLATE.clear();
        DungeonMech.clear();
        City.clear();
        RiftMobs.clear();
        Boss.clear();
        Later.TASKS.clear();
        Earth.clear();
        warned = false; warnedMech = false; warnedMobs = false; warnedBoss = false;
        LAST_HALL.clear();
    }

    static void onStopped(ServerStoppedEvent ev) { clearState(); }

    private static void tick0(ServerLevel sl) {
        List<ServerPlayer> ps = sl.m_6907_();
        if (ps.isEmpty()) return;
        DungeonData data = DungeonData.get(sl);
        if (!data.recs.isEmpty()) {
            for (ServerPlayer p : ps)   // элитры в данже не работают
                if (p.m_6084_() && p.m_21255_() && at(data, p.m_20185_(), p.m_20186_(), p.m_20189_(), 1) != null) p.m_36321_();
        }
        stepPlacing(sl, data);
        try { DungeonMech.tick(sl, data, ps, tk); }
        catch (Exception e) { if (!warnedMech) { warnedMech = true; e.printStackTrace(); } }   // сбой механики не обрывает залы и достройку
        try { RiftMobs.tick(sl, data, ps, tk); }
        catch (Exception e) { if (!warnedMobs) { warnedMobs = true; e.printStackTrace(); } }   // сбой мобов Разлома не обрывает залы
        try { Boss.tickAll(sl, data, ps, tk); }
        catch (Exception e) { if (!warnedBoss) { warnedBoss = true; e.printStackTrace(); } }   // сбой босса не обрывает залы
        if (tk % 10 != 0) return;
        purgeFalling(sl, data, ps);
        City.tick(sl, data, ps);
        discover(sl, data, ps);
        schedule(sl, data, ps);
        if (tk % 100 == 0) purgeUnbuilt(sl, data, ps);
        for (DungeonData.Rec rec : new ArrayList<>(data.recs.values())) inDungeon(sl, data, rec, ps);
        if (tk % 200 == 0 && !data.recs.isEmpty()) removeStrays(sl, data);
    }

    /**
     * Падающие блоки (обломки Кулака и Кометы, Чёрная дыра) внутри данжа (по маске) +2 убираются: они приземляются без события
     * и закупорили бы проход, а ломать блоки данжа нельзя. Дёшево: один запрос по рамке данжа, только если рядом игрок.
     */
    private static void purgeFalling(ServerLevel sl, DungeonData data, List<ServerPlayer> ps) {
        for (DungeonData.Rec rec : data.recs.values()) {
            if (rec.partsDone == 0 || rec.bossDead) continue;   // после победы над боссом блоки ломаются, падающие тоже не мешают
            Def d = rec.def();
            if (d == null) continue;
            int[] b;
            try { b = d.bounds(); } catch (IllegalStateException e) { noTemplate(d, e); continue; }
            boolean near = false;
            for (ServerPlayer p : ps)
                if (distToRect(p.m_20185_(), p.m_20189_(), rec.x + b[0], rec.z + b[2], b[3] - b[0], b[5] - b[2]) <= 160) { near = true; break; }
            if (!near) continue;
            AABB box = new AABB(rec.x + b[0] - 2, rec.y + b[1] - 2, rec.z + b[2] - 2, rec.x + b[3] + 2, rec.y + b[4] + 2, rec.z + b[5] + 2);
            for (Entity e : sl.m_6249_(null, box, en -> en instanceof FallingBlockEntity && rec.contains(en.m_20185_(), en.m_20186_(), en.m_20189_(), 2))) e.m_146870_();
        }
    }

    /**
     * Запись, сброшенная по смене раскладки (DungeonData.reset), достраивается заново; её старые мобы (тег ewd&lt;hash&gt;h*) в это время
     * убираются: мобы залов до полной достройки появиться не могут, так что всё с этим префиксом в недостроенном данже — остатки.
     */
    private static void purgeUnbuilt(ServerLevel sl, DungeonData data, List<ServerPlayer> ps) {
        for (DungeonData.Rec rec : data.recs.values()) {
            Def d = rec.def();
            if (d == null || rec.partsDone == d.allParts()) continue;
            int[] b;
            try { b = d.bounds(); } catch (IllegalStateException e) { noTemplate(d, e); continue; }
            boolean near = false;
            for (ServerPlayer p : ps)
                if (distToRect(p.m_20185_(), p.m_20189_(), rec.x + b[0], rec.z + b[2], b[3] - b[0], b[5] - b[2]) <= PART_RANGE) { near = true; break; }
            if (!near) continue;
            String prefix = "ewd" + Integer.toHexString(rec.key.hashCode()) + "h";
            AABB box = new AABB(rec.x + b[0] - ALIVE_PAD, rec.y + b[1] - ALIVE_PAD, rec.z + b[2] - ALIVE_PAD,
                    rec.x + b[3] + ALIVE_PAD, rec.y + b[4] + ALIVE_PAD, rec.z + b[5] + ALIVE_PAD);
            for (Entity e : sl.m_6249_(null, box, en -> hasTagPrefix(en, prefix))) RiftMobs.remove(e);
        }
    }

    static boolean hasTagPrefix(Entity e, String prefix) {
        for (String t : e.m_19880_()) if (t.startsWith(prefix)) return true;
        return false;
    }

    /** Мобы залов, ушедшие дальше 24 блоков от границ любого данжа, не нужны: PersistenceRequired не даёт им исчезнуть самим. */
    private static void removeStrays(ServerLevel sl, DungeonData data) {
        List<Entity> kill = new ArrayList<>();
        for (Entity e : sl.m_8583_()) {
            if (!e.m_19880_().contains("ewd")) continue;
            if (at(data, e.m_20185_(), e.m_20186_(), e.m_20189_(), 24) == null) kill.add(e);
        }
        for (Entity e : kill) RiftMobs.remove(e);
    }

    private static void discover(ServerLevel sl, DungeonData data, List<ServerPlayer> ps) {
        long seed = sl.m_7328_();
        for (Def d : DEFS) {
            for (ServerPlayer p : ps) {
                int cx0 = Math.floorDiv((int) Math.floor(p.m_20185_()), d.cell), cz0 = Math.floorDiv((int) Math.floor(p.m_20189_()), d.cell);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int cx = cx0 + dx, cz = cz0 + dz;
                        if (data.recs.containsKey(d.key(cx, cz)) || data.dead.containsKey(d.key(cx, cz))) continue;
                        int[] s = site(seed, d, cx, cz, 0);
                        double ddx = s[0] - p.m_20185_(), ddz = s[1] - p.m_20189_();
                        if (ddx * ddx + ddz * ddz <= (double) PREPARE_RANGE * PREPARE_RANGE) register(sl, data, d, cx, cz, seed);
                    }
                }
            }
        }
    }

    /**
     * Фиксирует место данжа: первая из 8 точек клетки, где центр не в воде; высота земли запоминается навсегда.
     * Наземный данж (Def.flat > 0) берёт самую ровную из сухих точек (перепад высот по 9 точкам рамки не больше flat, иначе минимальный)
     * и пропускает клетку, если перепад больше maxVar.
     * Запасные точки берутся от основной (site с a = 0); незагруженные и налезающие на обходимый данж (clashes) пропускаются,
     * а клетка при пропуске по незагрузке без годной точки не становится dead, а ждёт игрока.
     */
    private static void register(ServerLevel sl, DungeonData data, Def d, int cx, int cz, long seed) {
        if (d.roof < 0) { registerCover(sl, data, d, cx, cz, seed); return; }
        int[] best = null; int bestVar = Integer.MAX_VALUE;
        boolean skipped = false;   // хоть одна точка не оценена из-за незагруженного чанка
        for (int a = 0; a < 8; a++) {
            int[] s = site(seed, d, cx, cz, a);
            if (d.avoid != null && clashes(seed, d, s)) continue;   // налезает на обходимый данж
            if (d.flat > 0 && !sl.m_46805_(new BlockPos(s[0], 64, s[1]))) {   // незагруженный чанк не генерируем синхронно
                if (a == 0) return;   // основная точка: клетка подождёт следующего прохода
                skipped = true; continue;   // запасная: пропустить, клетка не должна зависнуть
            }
            sl.m_6325_(s[0] >> 4, s[1] >> 4);
            int y = groundY(sl, s[0], s[1]);
            if (wet(sl, s[0], y - 1, s[1])) continue;
            int var = d.flat > 0 ? variation(sl, d, s[0], s[1]) : 0;
            if (var == UNLOADED) { if (a == 0) return; skipped = true; continue; }
            if (var == SETTLED) continue;   // обжитое место (постройки игрока): эта точка не годится
            if (best == null || var < bestVar) { best = new int[]{s[0], y, s[1]}; bestVar = var; }
            if (var <= d.flat) break;
        }
        if (!keepCell(best, bestVar, d.maxVar)) {
            if (skipped) return;   // не все точки оценены: клетка подождёт игрока, dead не ставим
            // океан или слишком неровно: в этой клетке данжа не будет
            data.dead.put(d.key(cx, cz), 1);
            data.m_77762_();
            return;
        }
        DungeonData.Rec rec = new DungeonData.Rec(d.key(cx, cz), d.id, cx, cz, best[0], best[1], best[2]);
        try { rec.layout = d.signature(); } catch (IllegalStateException e) { noTemplate(d, e); }
        data.recs.put(d.key(cx, cz), rec);
        data.m_77762_();
    }

    /** Оценка точек подземного данжа, растянутая на несколько проходов discover: сколько точек оценено, их якоря и «выставленность». */
    static final class SiteState {
        int a = 0, unknown = 0; boolean done = false;
        final List<int[]> sites = new ArrayList<>();
        final List<Integer> exposures = new ArrayList<>();
    }
    static final Map<String, SiteState> SITES = new HashMap<>();
    /** Сколько точек подземного данжа оценивается за один проход discover (раз в 10 тиков). */
    static final int SITES_PER_PASS = 3;

    /**
     * Подземный данж (Def.roof < 0): точки оцениваются без загрузки чанков (по загруженным или оценкой генератора), по SITES_PER_PASS за проход;
     * выбор - DungeonSite.choose. Чанк грузится один раз, только у выбранной точки: там заново берётся настоящая высота и проверяется
     * вода и выставленность. Нет годной точки - клетка dead.
     */
    private static void registerCover(ServerLevel sl, DungeonData data, Def d, int cx, int cz, long seed) {
        String key = d.key(cx, cz);
        SiteState st = SITES.computeIfAbsent(key, k -> new SiteState());
        for (int n = 0; n < SITES_PER_PASS && !st.done && st.a < d.tries; n++) {
            int[] s = site(seed, d, cx, cz, st.a++);
            if (d.avoid != null && clashes(seed, d, s)) continue;
            int[] col = column(sl, s[0], s[1]);
            if (col[0] == DungeonSite.UNKNOWN) { st.unknown++; continue; }   // оценить нельзя
            if (col[1] != 0) continue;   // вода у входа
            int var = exposure(sl, d, s[0], col[0], s[1]);
            st.sites.add(new int[]{s[0], col[0], s[1]});
            st.exposures.add(var);
            if (var <= DungeonSite.COVER_OK) st.done = true;
        }
        if (!st.done && st.a < d.tries) return;   // остальные точки - в следующем проходе
        SITES.remove(key);
        int[] ex = new int[st.exposures.size()];
        for (int i = 0; i < ex.length; i++) ex[i] = st.exposures.get(i);
        int pick = DungeonSite.choose(ex);
        int[] best = null;
        if (st.sites.isEmpty() && st.unknown > 0) {   // оценки недоступны (генератор не отвечает): старое поведение - загрузить основную точку
            int[] s0 = site(seed, d, cx, cz, 0);
            sl.m_6325_(s0[0] >> 4, s0[1] >> 4);
            int y0 = groundY(sl, s0[0], s0[1]);
            if (!wet(sl, s0[0], y0 - 1, s0[1])) best = new int[]{s0[0], y0, s0[1]};
        } else if (pick >= 0) {   // настоящая высота выбранной точки: один чанк
            int[] s = st.sites.get(pick);
            sl.m_6325_(s[0] >> 4, s[2] >> 4);
            int y = groundY(sl, s[0], s[2]);
            if (!wet(sl, s[0], y - 1, s[2]) && exposure(sl, d, s[0], y, s[2]) <= DungeonSite.COVER_MAX) best = new int[]{s[0], y, s[2]};
        }
        if (best == null) { data.dead.put(key, 1); data.m_77762_(); return; }
        DungeonData.Rec rec = new DungeonData.Rec(key, d.id, cx, cz, best[0], best[1], best[2]);
        try { rec.layout = d.signature(); } catch (IllegalStateException e) { noTemplate(d, e); }
        data.recs.put(key, rec);
        data.m_77762_();
    }

    /** Годна ли лучшая из оценённых точек (есть и перепад не больше maxVar). Иначе клетка dead, если оценены все 8 точек. */
    static boolean keepCell(int[] best, int bestVar, int maxVar) { return best != null && bestVar <= maxVar; }

    /**
     * Насколько крыша подземного данжа торчит над землёй при входе на высоте y (DungeonSite.exposure): высоты берутся по центрам колонн 16x16
     * маски, где есть блоки ниже кровли. Загруженные чанки читаем как есть, незагруженные оцениваем генератором мира (без их генерации).
     */
    private static int exposure(ServerLevel sl, Def d, int x, int y, int z) {
        Mask mk;
        try { mk = d.mask(); } catch (IllegalStateException e) { return 0; }
        List<Integer> hs = new ArrayList<>();
        for (int i = 0; i < mk.gx; i += 2) {   // через колонну (шаг 32 блока): дешевле вдвое-вчетверо, допуск COVER_OK это покрывает
            for (int k = 0; k < mk.gz; k += 2) {
                if (mk.colMin(i, k) >= d.roof) continue;   // колонна без подземных блоков (вход и обломки на поверхности)
                int px = x + mk.x0 + i * 16 + Math.min(8, (mk.sx - i * 16) / 2), pz = z + mk.z0 + k * 16 + Math.min(8, (mk.sz - k * 16) / 2);
                int[] c = column(sl, px, pz);
                hs.add(c[1] != 0 ? DungeonSite.WET : c[0]);   // озеро или река над данжем: кровля упирается в воду, считаем выставленной
            }
        }
        int[] g = new int[hs.size()];
        for (int i = 0; i < g.length; i++) g[i] = hs.get(i);
        return DungeonSite.exposure(y, d.roof, g);
    }

    /**
     * Колонна {первый свободный y над землёй, 1 если над землёй вода}: по загруженному чанку (деревья пропускаются) или оценкой
     * генератора мира без загрузки чанка (WORLD_SURFACE_WG против OCEAN_FLOOR_WG); {UNKNOWN, 0}, если оценить нельзя.
     */
    static int[] column(ServerLevel sl, int px, int pz) {
        if (sl.m_46805_(new BlockPos(px, 64, pz))) {
            int y = groundY(sl, px, pz);
            return new int[]{y, wet(sl, px, y - 1, pz) ? 1 : 0};
        }
        try {
            net.minecraft.server.level.ServerChunkCache cc = sl.m_7726_();
            int surf = cc.m_8481_().m_223221_(px, pz, Heightmap.Types.valueOf("WORLD_SURFACE_WG"), sl, cc.m_214994_());
            if (surf >= 66) return new int[]{surf, 0};   // выше уровня моря (63) воды над землёй нет: вторая оценка шума не нужна
            int floor = cc.m_8481_().m_223221_(px, pz, Heightmap.Types.valueOf("OCEAN_FLOOR_WG"), sl, cc.m_214994_());
            return new int[]{floor, surf > floor ? 1 : 0};
        } catch (RuntimeException | LinkageError e) {
            if (!columnWarned) { columnWarned = true; System.err.println("[explosionwand] оценка высоты генератором недоступна, незагруженные колонны пропускаются: " + e); }
            return new int[]{DungeonSite.UNKNOWN, 0};
        }
    }
    private static boolean columnWarned = false;

    /** Результаты variation: чанк не загружен (отложить), место обжито (пропустить точку). */
    static final int UNLOADED = -1, SETTLED = -2;
    /** Чанк обжит, если в нём игроки провели больше 5 минут (тики): стройка города поверх такого места ломала бы постройки. */
    static final long SETTLED_TICKS = 20L * 60 * 5;

    /** Перепад высот земли по 9 точкам рамки данжа (углы с отступом 6, середины, центр); вода в любой точке — 99; чанк не загружен — UNLOADED, обжит — SETTLED. */
    private static int variation(ServerLevel sl, Def d, int x, int z) {
        int[] b;
        try { b = d.bounds(); } catch (IllegalStateException e) { return 0; }
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int px : new int[]{x + b[0] + 6, x, x + b[3] - 6}) {
            for (int pz : new int[]{z + b[2] + 6, z, z + b[5] - 6}) {
                if (!sl.m_46805_(new BlockPos(px, 64, pz))) return UNLOADED;
                if (sl.m_6325_(px >> 4, pz >> 4).m_6319_() > SETTLED_TICKS) return SETTLED;
                int y = groundY(sl, px, pz);
                if (wet(sl, px, y - 1, pz)) return 99;
                lo = Math.min(lo, y); hi = Math.max(hi, y);
            }
        }
        return hi - lo;
    }

    private static void schedule(ServerLevel sl, DungeonData data, List<ServerPlayer> ps) {
        for (DungeonData.Rec r : data.recs.values()) {
            Def d = r.def();
            if (d == null || r.partsDone == d.allParts()) continue;
            List<DungeonTemplate> tp = d.templates();
            for (int i = 0; i < tp.size(); i++) {
                if (((r.partsDone >> i) & 1L) != 0 || placing(sl, r, i)) continue;
                DungeonTemplate t = tp.get(i);
                for (ServerPlayer p : ps) {
                    // игрок уже внутри данжа: достраиваем все части сразу (залы спят до полной достройки), без ожидания 96 блоков до части
                    if (r.contains(p.m_20185_(), p.m_20186_(), p.m_20189_(), 1)
                            || distToRect(p.m_20185_(), p.m_20189_(), r.x + t.ox, r.z + t.oz, t.sx, t.sz) <= PART_RANGE) {
                        PLACING.add(new Placing(sl, r, i, t));
                        break;
                    }
                }
            }
        }
    }

    private static boolean placing(ServerLevel sl, DungeonData.Rec r, int part) {
        for (Placing p : PLACING) if (p.level == sl && p.rec.key.equals(r.key) && p.part == part) return true;
        return false;
    }

    private static void stepPlacing(ServerLevel sl, DungeonData data) {
        int budget = BUDGET;
        Iterator<Placing> it = PLACING.iterator();
        while (it.hasNext() && budget > 0) {
            Placing p = it.next();
            if (p.level != sl) continue;
            budget -= Math.max(1, p.step(budget));
            if (p.done()) { it.remove(); p.rec.partsDone |= 1L << p.part; data.m_77762_(); }
        }
    }

    // ---------------------------------------------------------------- внутри данжа: залы, мобы, награда
    static String tagOf(DungeonData.Rec rec, int hall) { return "ewd" + Integer.toHexString(rec.key.hashCode()) + "h" + hall; }

    private static void inDungeon(ServerLevel sl, DungeonData data, DungeonData.Rec rec, List<ServerPlayer> ps) {
        Def d = rec.def();
        if (d == null) return;
        if (rec.partsDone != d.allParts()) {   // достройка ещё идёт: залы спят, игроку об этом говорим
            if (tk % 100 == 0 && rec.partsDone != 0)
                for (ServerPlayer p : ps)
                    if (p.m_6084_() && rec.contains(p.m_20185_(), p.m_20186_(), p.m_20189_(), 1)) p.m_5661_(Component.m_237115_("explosionwand.dungeon.building"), true);
            return;
        }
        List<ServerPlayer> in = new ArrayList<>();
        for (ServerPlayer p : ps) if (p.m_6084_() && rec.contains(p.m_20185_(), p.m_20186_(), p.m_20189_(), 1)) in.add(p);
        if (!in.isEmpty()) LAST_INSIDE.put(rec.key, tk);
        long last = LAST_INSIDE.computeIfAbsent(rec.key, k -> tk);
        boolean peaceful = sl.m_46791_() == Difficulty.valueOf("PEACEFUL");
        if (peaceful && !in.isEmpty() && tk % 200 == 0) for (ServerPlayer p : in) p.m_5661_(Component.m_237115_("explosionwand.dungeon.peaceful"), true);
        for (Hall h : d.halls()) {
            long bit = 1L << h.id;
            boolean spawned = (rec.hallsSpawned & bit) != 0, cleared = (rec.hallsCleared & bit) != 0;
            if (cleared) continue;
            String tag = tagOf(rec, h.id);
            boolean sealed = d.sealed(h.id);
            // залы с печатью сбрасываются по залу с запасом ALIVE_PAD (там же ищутся его мобы), а не по данжу; сама печать — по коробке без запаса.
            // Логово босса — без запаса: погибшие стоят у жаровни в ALIVE_PAD от зала, но босс не должен ждать их вечно (30 с, и он восстановится)
            if (sealed && hallOccupied(rec, h, in, h.boss != null ? 0 : ALIVE_PAD)) LAST_HALL.put(tag, tk);
            long ref = sealed ? LAST_HALL.computeIfAbsent(tag, k -> tk) : last;
            if (peaceful) {   // мирный режим: данж спит, зал не оживает и не засчитывается (мобы-призраки не дадут сундук)
                if (spawned && entitiesLoaded(sl, rec, h)) {
                    for (Entity e : alive(sl, rec, h, tag)) RiftMobs.remove(e);
                    if (h.boss != null) Boss.reset(sl, rec, h);
                    rec.hallsSpawned &= ~bit; SPAWNED_AT.remove(tag); data.m_77762_();
                }
                continue;
            }
            if (!entitiesLoaded(sl, rec, h)) continue;   // сущности зала ещё не подгружены: не будить и не засчитывать
            BlockPos center = new BlockPos(rec.x + h.x + h.sx / 2, rec.y + h.y + 1, rec.z + h.z + h.sz / 2);
            if (!spawned) {
                for (ServerPlayer p : in) {
                    if (h.contains(p.m_20185_() - rec.x, p.m_20186_() - rec.y + 0.5, p.m_20189_() - rec.z)) {
                        spawnHall(sl, rec, h, tag);
                        rec.hallsSpawned |= bit; SPAWNED_AT.put(tag, tk); data.m_77762_();
                        break;
                    }
                }
            } else if (sl.m_46805_(center)) {
                if (!in.isEmpty()) {
                    // записи нет (после перезапуска) — отсрочка 30 тиков с этого момента
                    if (clearable(h.boss != null, tk - SPAWNED_AT.computeIfAbsent(tag, k -> tk) > 30, () -> alive(sl, rec, h, tag).isEmpty())) {
                        rec.hallsCleared |= bit; data.m_77762_();
                        onCleared(sl, rec, h, in);
                        continue;
                    }
                }
                if (hallStale(tk, ref)) {   // никого (в зале с печатью — в зале +ALIVE_PAD, иначе в данже) 30 с: убрать недобитых, зал разбудится заново
                    for (Entity e : alive(sl, rec, h, tag)) RiftMobs.remove(e);
                    if (h.boss != null) Boss.reset(sl, rec, h);
                    rec.hallsSpawned &= ~bit; SPAWNED_AT.remove(tag); LAST_HALL.remove(tag); data.m_77762_();
                }
            }
        }
    }

    /** Зал давно пуст (последний раз кто-то был в нём или в данже ref тиков назад) — пора сбрасывать. */
    static boolean hallStale(long now, long ref) { return now - ref > EMPTY_TICKS; }

    /** Кто-то из игроков (координаты мира) стоит в коробке зала. */
    static boolean hallOccupied(DungeonData.Rec rec, Hall h, List<ServerPlayer> in) { return hallOccupied(rec, h, in, 0); }

    /** То же с запасом pad вокруг коробки (для сброса зала: игрок рядом с залом и его мобами ещё «здесь»). */
    static boolean hallOccupied(DungeonData.Rec rec, Hall h, List<ServerPlayer> in, int pad) {
        for (ServerPlayer p : in)
            if (h.containsPad(p.m_20185_() - rec.x, p.m_20186_() - rec.y + 0.5, p.m_20189_() - rec.z, pad)) return true;
        return false;
    }

    /** Запас вокруг зала, где ищутся его мобы (и чьи чанки обязаны быть загружены). */
    static final int ALIVE_PAD = 24;

    /** Загружены ли сущности во всех чанках рамки поиска мобов зала (areEntitiesLoaded): иначе список живых может быть пуст по ошибке. */
    static boolean entitiesLoaded(ServerLevel sl, DungeonData.Rec rec, Hall h) {
        int x0 = (rec.x + h.x - ALIVE_PAD) >> 4, x1 = (rec.x + h.x + h.sx + ALIVE_PAD - 1) >> 4;
        int z0 = (rec.z + h.z - ALIVE_PAD) >> 4, z1 = (rec.z + h.z + h.sz + ALIVE_PAD - 1) >> 4;
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++)
                if (!sl.m_143319_(((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32))) return false;
        return true;
    }

    /**
     * Ванильная часть добычи сундуков (материалы данжей добавляет Mats.chestLoot). Тир берётся из метки сундука
     * ("Название|тир"): 1 — боковые комнаты и залы, 2 — логова и склад, 3 — секретные комнаты.
     */
    static final List<List<String>> LOOT = List.of(
            List.of("6 minecraft:amethyst_shard", "3 minecraft:gold_ingot", "2 minecraft:emerald", "2 minecraft:experience_bottle"),
            List.of("10 minecraft:amethyst_shard", "6 minecraft:gold_ingot", "2 minecraft:diamond", "minecraft:golden_apple", "4 minecraft:experience_bottle"),
            List.of("16 minecraft:amethyst_shard", "12 minecraft:gold_ingot", "4 minecraft:diamond", "6 minecraft:emerald", "2 minecraft:golden_apple", "8 minecraft:experience_bottle"));

    /** Метка сундука "Название|тир" -> {название, тир 1..3}. Без тира — 1. */
    static String[] chestSpec(String a) {
        int i = a.lastIndexOf('|');
        if (i < 0) return new String[]{a, "1"};
        String t = a.substring(i + 1).trim();
        return new String[]{a.substring(0, i), t.equals("2") || t.equals("3") ? t : "1"};
    }

    /** Добыча сундука зала: у логова босса — трофеи босса, затем временный набор тира. */
    static List<String> lootFor(Hall h, int tier) { return lootFor(null, h, tier); }

    /** То же с учётом данжа: у Города Спящих в ряде сундуков лежат особые предметы (книги Архимага, Печать Храма). */
    static List<String> lootFor(Def d, Hall h, int tier) {
        List<String> out = new ArrayList<>();
        if (h.boss != null) out.addAll(Boss.lootOf(h));
        if (d != null && d == SLEEP) out.addAll(City.extra(h.id));
        out.addAll(LOOT.get(tier - 1));
        if (d != null) out.addAll(Mats.chestLoot(Mats.dungeonOf(d), tier, Mats.RNG));   // материалы данжа (4.11)
        return out;
    }

    /**
     * Можно ли засчитать зал по «мобов не осталось». Логово с боссом так не засчитывается никогда: хост появляется через 60 тиков после входа
     * (и после перезапуска мира его ещё нет), поэтому победу ставит только сам босс (lairWon).
     */
    static boolean clearable(boolean hasBoss, boolean waited, java.util.function.BooleanSupplier aliveEmpty) {
        return !hasBoss && waited && aliveEmpty.getAsBoolean();
    }

    /** Босс логова побеждён: зал засчитывается (хранится в Rec.hallsCleared, переживает перезапуск) и ставится сундук. */
    static void lairWon(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Hall h, List<ServerPlayer> in) {
        rec.hallsCleared |= 1L << h.id;
        markCleared(data, rec);
        onCleared(sl, rec, h, in);
    }

    /**
     * Босс данжа убит: барьер снят (блоки данжа можно ломать и ставить: кирка, взрывы, палочки), флаг хранится в Rec.bossDead.
     * Зовёт lairWon (общий для Сердца Разлома и Монолита Сна); остальные игровые правила данжа (телепорты, спавн, элитры) не меняются.
     */
    static void markCleared(DungeonData data, DungeonData.Rec rec) {
        if (!rec.bossDead) { rec.bossDead = true; }
        data.m_77762_();
    }

    private static void onCleared(ServerLevel sl, DungeonData.Rec rec, Hall h, List<ServerPlayer> in) {
        if (h.chest == null) return;
        String[] spec = chestSpec(h.chest.a);
        Loot.chest(sl, rec.x + h.chest.x, rec.y + h.chest.y, rec.z + h.chest.z, spec[0], lootFor(rec.def(), h, Integer.parseInt(spec[1])));
        for (ServerPlayer p : in) p.m_5661_(Component.m_237115_("explosionwand.dungeon.cleared"), false);
    }

    /** Живые мобы зала: тег зала в рамке «зал + ALIVE_PAD». Рамка совпадает с проверкой entitiesLoaded. */
    static List<Entity> alive(ServerLevel sl, DungeonData.Rec rec, Hall h, String tag) {
        return sl.m_6249_(null, hallBox(rec, h), e -> e.m_6084_() && e.m_19880_().contains(tag));
    }

    /** Рамка поиска мобов зала (зал + ALIVE_PAD) в мировых координатах. */
    static AABB hallBox(DungeonData.Rec rec, Hall h) {
        return new AABB(rec.x + h.x - ALIVE_PAD, rec.y + h.y - ALIVE_PAD, rec.z + h.z - ALIVE_PAD,
                rec.x + h.x + h.sx + ALIVE_PAD, rec.y + h.y + h.sy + ALIVE_PAD, rec.z + h.z + h.sz + ALIVE_PAD);
    }

    private static void spawnHall(ServerLevel sl, DungeonData.Rec rec, Hall h, String tag) {
        if (h.boss != null) {   // логово: вместо мобов просыпается босс (его тики — Boss.tickAll)
            Boss.wake(sl, rec, h, tag);
            return;
        }
        int have = alive(sl, rec, h, tag).size();   // недобитые с прошлого раза не дублируются
        for (int i = have; i < h.spawns.size(); i++) {
            Mark m = h.spawns.get(i);
            double x = rec.x + m.x + 0.5, y = rec.y + m.y, z = rec.z + m.z + 0.5;
            if (m.a.startsWith(RiftMobs.PFX)) {   // моб Разлома: облик из block_display, свои тики (RiftMobs)
                RiftMobs.spawn(sl, m.a, x, y, z, tag);
                continue;
            }
            String gear = m.a.equals("skeleton") ? ",HandItems:[{id:\"minecraft:bow\",Count:1b},{}]" : "";
            Cmd.run(sl, x, y, z, "summon minecraft:" + m.a + " " + Cmd.f(x) + " " + Cmd.f(y) + " " + Cmd.f(z)
                    + " {Tags:[\"ewd\",\"" + tag + "\"],PersistenceRequired:1b,CanPickUpLoot:0b" + gear + "}");
            Fx.send(sl, Fx.particle("poof"), x, y + 1, z, 8, 0.3, 0.5, 0.3, 0.02);
        }
        double cx = rec.x + h.x + h.sx / 2.0, cy = rec.y + h.y + 2, cz = rec.z + h.z + h.sz / 2.0;
        Fx.sound(sl, cx, cy, cz, "block.amethyst_block.resonate", 2.0F, 0.6F);
    }

    // ================================================================ события: дверь, жаровни, возрождение
    static boolean holdsShard(net.minecraft.world.entity.player.Player p, String dungeon) {
        for (ItemStack s : new ItemStack[]{p.m_21205_(), p.m_21206_()})
            if (s != null && s.m_41720_() instanceof SealShardItem ss && ss.dungeon.equals(dungeon)) return true;
        return false;
    }

    static void onRightClick(PlayerInteractEvent.RightClickBlock ev) {
        if (ev.getLevel().f_46443_ || !(ev.getLevel() instanceof ServerLevel sl) || !Dims.is(sl, Level.f_46428_)) return;
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        DungeonData data = DungeonData.get(sl);
        BlockPos pos = ev.getPos();
        DungeonData.Rec rec = at(data, pos.m_123341_() + 0.5, pos.m_123342_() + 0.5, pos.m_123343_() + 0.5, 0);
        if (rec == null) return;
        Def d = rec.def();
        boolean main = ev.getHand() == InteractionHand.valueOf("MAIN_HAND");
        int rx = pos.m_123341_() - rec.x, ry = pos.m_123342_() - rec.y, rz = pos.m_123343_() - rec.z;
        for (Mark m : d.doors()) {
            if (!m.inside(rx, ry, rz)) continue;
            ev.setCanceled(true);
            if (!main) return;
            if (holdsShard(p, m.a)) openDoor(sl, rec, m);
            else {
                p.m_5661_(Component.m_237115_("explosionwand.dungeon.locked." + m.a), true);
                Fx.sound(sl, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), "block.amethyst_block.hit", 1.0F, 0.5F);
            }
            return;
        }
        for (Mark m : d.braziers()) {
            if (m.x != rx || m.y != ry || m.z != rz) continue;
            ev.setCanceled(true);
            if (main) lightBrazier(sl, data, rec, m, p);
            return;
        }
        if (main && d == SLEEP && reissueSeal(sl, rec, p, rx, ry, rz)) return;
        if (DungeonMech.onClick(sl, data, rec, d, p, rx, ry, rz, main)) ev.setCanceled(true);
    }

    /** Печать Храма потеряна (лава, кактус): ПКМ по саркофагу в зачищенном склепе выдаёт новую, пока у игрока её нет и дверь храма закрыта. Саркофаги: x -42/-35 (+1), z 18/22/26 (+1), на уровне пола склепа. */
    private static boolean reissueSeal(ServerLevel sl, DungeonData.Rec rec, ServerPlayer p, int rx, int ry, int rz) {
        Hall h = rec.def().hall(21);
        if (h == null || (rec.hallsCleared & (1L << h.id)) == 0 || ry != h.y + 1) return false;
        boolean sx = rx == -42 || rx == -41 || rx == -35 || rx == -34;
        boolean szz = rz == 18 || rz == 19 || rz == 22 || rz == 23 || rz == 26 || rz == 27;
        if (!sx || !szz) return false;
        Mark door = null;
        for (Mark m : rec.def().doors()) if (m.a.equals("sleep_temple")) door = m;
        if (door == null || sl.m_8055_(new BlockPos(rec.x + door.x, rec.y + door.y, rec.z + door.z)).m_60795_()) return false;   // храм уже открыт
        if (p.m_150109_().m_18947_(ExplosionWandMod.TEMPLE_SEAL.get()) > 0) return false;
        Cmd.run(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "give " + p.m_20148_() + " explosionwand:temple_seal");
        p.m_5661_(Component.m_237115_("explosionwand.dungeon.seal_reissued"), true);
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.amethyst_block.chime", 1.0F, 0.7F);
        return true;
    }

    private static void openDoor(ServerLevel sl, DungeonData.Rec rec, Mark m) {
        BlockState air = Fx.state("air");
        for (int x = 0; x < m.sx; x++)
            for (int y = 0; y < m.sy; y++)
                for (int z = 0; z < m.sz; z++) sl.m_7731_(new BlockPos(rec.x + m.x + x, rec.y + m.y + y, rec.z + m.z + z), air, 3);
        double cx = rec.x + m.x + m.sx / 2.0, cy = rec.y + m.y + m.sy / 2.0, cz = rec.z + m.z + m.sz / 2.0;
        Fx.send(sl, Fx.particle("end_rod"), cx, cy, cz, 40, m.sx / 2.0, m.sy / 2.0, m.sz / 2.0, 0.08);
        Fx.sound(sl, cx, cy, cz, "block.amethyst_block.break", 2.0F, 0.6F);
        Fx.sound(sl, cx, cy, cz, "block.beacon.activate", 1.5F, 1.4F);
    }

    private static void lightBrazier(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Mark m, ServerPlayer p) {
        BlockPos bp = new BlockPos(rec.x + m.x, rec.y + m.y, rec.z + m.z);
        String[] off = m.a.split(",");
        int sx = bp.m_123341_() + Integer.parseInt(off[0]), sy = bp.m_123342_() + Integer.parseInt(off[1]), sz = bp.m_123343_() + Integer.parseInt(off[2]);
        String val = sx + "|" + sy + "|" + sz, key = p.m_20148_() + "|" + rec.key;
        boolean same = val.equals(data.respawn.get(key));
        BlockState lit = Dungeons.state("soul_campfire[lit=true]");
        if (lit != null) sl.m_7731_(bp, lit, 3);
        data.respawn.put(key, val); data.m_77762_();
        p.m_5661_(Component.m_237115_(same ? "explosionwand.dungeon.brazier_same" : "explosionwand.dungeon.brazier_lit"), true);
        Fx.send(sl, Fx.particle("soul_fire_flame"), bp.m_123341_() + 0.5, bp.m_123342_() + 0.8, bp.m_123343_() + 0.5, 25, 0.2, 0.3, 0.2, 0.03);
        Fx.sound(sl, bp.m_123341_(), bp.m_123342_(), bp.m_123343_(), "item.firecharge.use", 1.0F, 0.7F);
        Fx.sound(sl, bp.m_123341_(), bp.m_123342_(), bp.m_123343_(), "block.respawn_anchor.set_spawn", 1.0F, 1.2F);
    }

    /** Смерть внутри данжа: запоминаем, что возрождать у жаровни (если она зажжена). */
    static void onDeath(LivingDeathEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !(p.m_9236_() instanceof ServerLevel sl) || !Dims.is(sl, Level.f_46428_)) return;
        DungeonData data = DungeonData.get(sl);
        DungeonData.Rec rec = at(data, p.m_20185_(), p.m_20186_(), p.m_20189_(), 1);
        if (rec == null) return;
        String spot = data.respawn.get(p.m_20148_() + "|" + rec.key);
        if (spot != null) PENDING.put(p.m_20148_(), spot);
    }

    static void onRespawn(PlayerEvent.PlayerRespawnEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !(p.m_9236_() instanceof ServerLevel sl0)) return;
        String spot = PENDING.remove(p.m_20148_());
        if (spot == null) return;
        ServerLevel ow = Dims.get(sl0, Level.f_46428_);
        String[] s = spot.split("\\|");
        double x = Integer.parseInt(s[0]) + 0.5, y = Integer.parseInt(s[1]), z = Integer.parseInt(s[2]) + 0.5;
        Later.run(ow, 2, () -> {
            if (!p.m_6084_()) return;
            p.m_8999_(ow, x, y, z, p.m_146908_(), 0.0F);
            p.f_19789_ = 0.0F;
            p.m_5661_(Component.m_237115_("explosionwand.dungeon.respawned"), true);
        });
    }

    // ================================================================ Осколок Печати: указатель
    /** Вызывается из SealShardItem, пока осколок в руке: частицы к ближайшему данжу, расстояние над хотбаром, звон рядом. */
    static void pointer(ServerLevel sl, ServerPlayer p, String defId) {
        long t = sl.m_46467_();
        if (t % 5 != 0) return;
        Def d = def(defId);
        if (d == null) return;
        if (!Dims.is(sl, Level.f_46428_)) {
            if (t % 20 == 0) p.m_5661_(Component.m_237115_("explosionwand.shard.silent"), true);
            return;
        }
        DungeonData data = DungeonData.get(sl);
        Hit h = nearest(sl.m_7328_(), d, p.m_20185_(), p.m_20189_(), key -> {
            DungeonData.Rec r = data.recs.get(key);
            return r == null ? null : new int[]{r.x, r.z};
        }, data.dead::containsKey);
        double dx = h.x + 0.5 - p.m_20185_(), dz = h.z + 0.5 - p.m_20189_(), len = Math.max(1.0E-3, h.dist);
        double ux = dx / len, uz = dz / len;
        boolean near = h.dist < 40;
        if (t % 10 == 0) {
            int shown = h.dist < 100 ? (int) Math.round(h.dist / 5) * 5 : (int) Math.round(h.dist / 10) * 10;
            p.m_5661_(near ? Component.m_237110_("explosionwand.shard.near", Component.m_237115_("explosionwand.dungeon." + d.id))
                    : Component.m_237110_("explosionwand.shard.point", Component.m_237115_("explosionwand.dungeon." + d.id), shown), true);
        }
        double ex = p.m_20185_(), ey = p.m_20186_() + 1.2, ez = p.m_20189_();
        for (int i = 0; i < 6; i++) {   // цепочка частиц, уходящая к данжу
            double r = 1.2 + i * 0.6;
            Fx.sendTo(sl, p, Fx.particle("end_rod"), ex + ux * r, ey - 0.2 * i * 0.3, ez + uz * r, 0, ux, 0.02, uz, 0.12);
        }
        if (near) {   // «дрожит и звенит»: чем ближе, тем чаще
            int period = Math.max(10, (int) (h.dist / 2) / 5 * 5);
            if (t % period == 0) Fx.sound(sl, ex, ey, ez, "block.amethyst_block.chime", 1.0F, 0.7F + Fx.RNG.nextFloat() * 0.8F);
            Fx.sendTo(sl, p, Fx.particle("crit"), ex, ey, ez, 6, 0.4, 0.4, 0.4, 0.15);
        }
    }

    /** Отладка для творческого режима: координаты ближайшего данжа в чат. */
    static void locate(ServerLevel sl, ServerPlayer p, String defId) {
        Def d = def(defId);
        if (d == null || !Dims.is(sl, Level.f_46428_)) return;
        DungeonData data = DungeonData.get(sl);
        Hit h = nearest(sl.m_7328_(), d, p.m_20185_(), p.m_20189_(), key -> {
            DungeonData.Rec r = data.recs.get(key);
            return r == null ? null : new int[]{r.x, r.z};
        }, data.dead::containsKey);
        p.m_5661_(Component.m_237110_("explosionwand.shard.locate", Component.m_237115_("explosionwand.dungeon." + d.id), h.x, h.z, (int) h.dist), false);
    }
}
