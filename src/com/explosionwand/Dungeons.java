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
        Hall(Mark m) { id = Integer.parseInt(m.a); x = m.x; y = m.y; z = m.z; sx = m.sx; sy = m.sy; sz = m.sz; }
        boolean contains(double px, double py, double pz) { return px >= x && px < x + sx && py >= y && py < y + sy + 1 && pz >= z && pz < z + sz; }
    }

    /** Тип данжа: сетка расстановки, имена частей. Данные частей читаются лениво. */
    static final class Def {
        final String id; final int cell, margin; final String[] parts;
        private List<DungeonTemplate> tpl;
        private int[] bounds;
        private final List<Hall> halls = new ArrayList<>();
        private final List<Mark> doors = new ArrayList<>(), braziers = new ArrayList<>();
        private Mark start;

        Def(String id, int cell, int margin, String... parts) { this.id = id; this.cell = cell; this.margin = margin; this.parts = parts; }

        String key(int cx, int cz) { return id + "_" + cx + "_" + cz; }

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
            halls.clear(); doors.clear(); braziers.clear(); start = null;
            List<Mark> all = new ArrayList<>();
            for (DungeonTemplate t : l) {
                b[0] = Math.min(b[0], t.ox); b[1] = Math.min(b[1], t.oy); b[2] = Math.min(b[2], t.oz);
                b[3] = Math.max(b[3], t.ox + t.sx); b[4] = Math.max(b[4], t.oy + t.sy); b[5] = Math.max(b[5], t.oz + t.sz);
                all.addAll(t.marksInDungeon());
            }
            bounds = b;
            for (Mark m : all) {
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
                    }
                }
            }
        }

        /** {minX, minY, minZ, maxX, maxY, maxZ} относительно начала данжа, max не включая. */
        int[] bounds() { templates(); return bounds; }
        List<Hall> halls() { templates(); return halls; }
        List<Mark> doors() { templates(); return doors; }
        List<Mark> braziers() { templates(); return braziers; }
        Mark start() { templates(); return start; }
        long allParts() { return (1L << parts.length) - 1; }
    }

    /** Тестовый разлом: вход на поверхности, провал, жаровня, логово. Клетка 768, отступ от края клетки 128. Настоящий Разлом (4.2) заменит его. */
    static final Def RIFT = new Def("rift", 768, 128, "rift_test_entrance", "rift_test_halls");
    static final Def[] DEFS = {RIFT};

    static Def def(String id) { for (Def d : DEFS) if (d.id.equals(id)) return d; return null; }

    // ---------------------------------------------------------------- расстановка по сетке (детерминированная)
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Точка данжа в клетке (cx,cz); attempt > 0 — запасные точки в пределах ±48 блоков (если основная в воде). */
    static int[] site(long seed, Def d, int cx, int cz, int attempt) {
        long h = mix(seed + 0x9E3779B97F4A7C15L * (cx * 31L + 7) + 0xC2B2AE3D27D4EB4FL * (cz * 17L + 3) + d.id.hashCode() * 0x632BE59BD9B4E019L);
        int span = d.cell - 2 * d.margin;
        int x = cx * d.cell + d.margin + (int) Long.remainderUnsigned(mix(h + 1), span);
        int z = cz * d.cell + d.margin + (int) Long.remainderUnsigned(mix(h + 2), span);
        if (attempt > 0) {
            x += (int) Long.remainderUnsigned(mix(h + 10L * attempt), 97) - 48;
            z += (int) Long.remainderUnsigned(mix(h + 10L * attempt + 5), 97) - 48;
        }
        return new int[]{x, z};
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
    static boolean warned = false;
    static final int PREPARE_RANGE = 80, PART_RANGE = 96, BUDGET = 1500, EMPTY_TICKS = 600;
    static final Map<String, Long> SPAWNED_AT = new HashMap<>(), LAST_INSIDE = new HashMap<>();
    static final Map<UUID, String> PENDING = new HashMap<>();
    static final List<Placing> PLACING = new ArrayList<>();

    /** Часть данжа, которая сейчас ставится в мир порциями. */
    static final class Placing {
        final ServerLevel level; final DungeonData.Rec rec; final int part; final DungeonTemplate t; final int ox, oy, oz;
        int idx = 0; BlockState[] states; long lastChunk = Long.MIN_VALUE;
        Placing(ServerLevel level, DungeonData.Rec rec, int part, DungeonTemplate t) {
            this.level = level; this.rec = rec; this.part = part; this.t = t;
            ox = rec.x + t.ox; oy = rec.y + t.oy; oz = rec.z + t.oz;
        }
        boolean done() { return idx >= t.count(); }
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
            return n;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState with(BlockState st, Property pr, String v) {
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
    /** Границы всех данжей обычного мира для быстрой проверки позиции в цикле (получать один раз на проход). */
    static final class Shield {
        static final Shield NONE = new Shield(List.of());
        private final List<int[]> boxes;
        Shield(List<int[]> boxes) { this.boxes = boxes; }
        boolean at(int x, int y, int z) {
            for (int[] b : boxes) if (x >= b[0] && x < b[3] && y >= b[1] && y < b[4] && z >= b[2] && z < b[5]) return true;
            return false;
        }
        boolean at(BlockPos p) { return !boxes.isEmpty() && at(p.m_123341_(), p.m_123342_(), p.m_123343_()); }
    }

    static Shield shield(Level l) {
        if (!(l instanceof ServerLevel sl) || !Dims.is(sl, Level.f_46428_)) return Shield.NONE;
        DungeonData data = DungeonData.get(sl);
        if (data.recs.isEmpty()) return Shield.NONE;
        List<int[]> l2 = new ArrayList<>();
        for (DungeonData.Rec r : data.recs.values()) {
            Def d = r.def();
            if (d == null) continue;
            try { int[] b = d.bounds(); l2.add(new int[]{r.x + b[0], r.y + b[1], r.z + b[2], r.x + b[3], r.y + b[4], r.z + b[5]}); }
            catch (IllegalStateException e) { /* нет шаблона: защита выключена */ }
        }
        return l2.isEmpty() ? Shield.NONE : new Shield(l2);
    }

    /** Позиция внутри данжа: эффекты палочек пропускают её так же, как бедрок. Для циклов лучше один раз взять shield(level). */
    static boolean protectedAt(Level l, BlockPos p) { return shield(l).at(p); }

    // ================================================================ поиск данжа по позиции
    static DungeonData.Rec at(DungeonData data, double x, double y, double z, int pad) {
        for (DungeonData.Rec r : data.recs.values()) if (r.contains(x, y, z, pad)) return r;
        return null;
    }

    /** Данж, в границах которого лежит позиция (только обычный мир). */
    static DungeonData.Rec at(Level l, double x, double y, double z) {
        if (!(l instanceof ServerLevel sl) || !Dims.is(sl, Level.f_46428_)) return null;
        return at(DungeonData.get(sl), x, y, z, 0);
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
        PLACING.clear(); SPAWNED_AT.clear(); LAST_INSIDE.clear(); PENDING.clear();
        Later.TASKS.clear();
        warned = false;
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
        if (tk % 10 != 0) return;
        discover(sl, data, ps);
        schedule(sl, data, ps);
        for (DungeonData.Rec rec : new ArrayList<>(data.recs.values())) inDungeon(sl, data, rec, ps);
        if (tk % 200 == 0 && !data.recs.isEmpty()) removeStrays(sl, data);
    }

    /** Мобы залов, ушедшие дальше 24 блоков от границ любого данжа, не нужны: PersistenceRequired не даёт им исчезнуть самим. */
    private static void removeStrays(ServerLevel sl, DungeonData data) {
        List<Entity> kill = new ArrayList<>();
        for (Entity e : sl.m_8583_()) {
            if (!e.m_19880_().contains("ewd")) continue;
            if (at(data, e.m_20185_(), e.m_20186_(), e.m_20189_(), 24) == null) kill.add(e);
        }
        for (Entity e : kill) e.m_146870_();
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

    /** Фиксирует место данжа: первая из 8 точек клетки, где центр не в воде; высота земли запоминается навсегда. */
    private static void register(ServerLevel sl, DungeonData data, Def d, int cx, int cz, long seed) {
        int bx = 0, by = 0, bz = 0; boolean dry = false;
        for (int a = 0; a < 8 && !dry; a++) {
            int[] s = site(seed, d, cx, cz, a);
            sl.m_6325_(s[0] >> 4, s[1] >> 4);
            int y = groundY(sl, s[0], s[1]);
            bx = s[0]; by = y; bz = s[1];
            dry = !wet(sl, s[0], y - 1, s[1]);
        }
        if (!dry) {   // океан: в этой клетке данжа не будет
            data.dead.put(d.key(cx, cz), 1);
            data.m_77762_();
            return;
        }
        data.recs.put(d.key(cx, cz), new DungeonData.Rec(d.key(cx, cz), d.id, cx, cz, bx, by, bz));
        data.m_77762_();
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
                    if (distToRect(p.m_20185_(), p.m_20189_(), r.x + t.ox, r.z + t.oz, t.sx, t.sz) <= PART_RANGE) {
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
        if (d == null || rec.partsDone != d.allParts()) return;
        List<ServerPlayer> in = new ArrayList<>();
        for (ServerPlayer p : ps) if (p.m_6084_() && rec.contains(p.m_20185_(), p.m_20186_(), p.m_20189_(), 1)) in.add(p);
        if (!in.isEmpty()) LAST_INSIDE.put(rec.key, tk);
        long last = LAST_INSIDE.computeIfAbsent(rec.key, k -> tk);
        boolean peaceful = sl.m_46791_() == Difficulty.valueOf("PEACEFUL");
        if (peaceful && !in.isEmpty() && tk % 200 == 0) for (ServerPlayer p : in) p.m_5661_(Component.m_237115_("explosionwand.dungeon.peaceful"), true);
        for (Hall h : d.halls()) {
            int bit = 1 << h.id;
            boolean spawned = (rec.hallsSpawned & bit) != 0, cleared = (rec.hallsCleared & bit) != 0;
            if (cleared) continue;
            String tag = tagOf(rec, h.id);
            if (peaceful) {   // мирный режим: данж спит, зал не оживает и не засчитывается (мобы-призраки не дадут сундук)
                if (spawned && entitiesLoaded(sl, rec, h)) {
                    for (Entity e : alive(sl, rec, d, tag)) e.m_146870_();
                    rec.hallsSpawned &= ~bit; SPAWNED_AT.remove(tag); data.m_77762_();
                }
                continue;
            }
            if (!entitiesLoaded(sl, rec, h)) continue;   // сущности зала ещё не подгружены: не будить и не засчитывать
            BlockPos center = new BlockPos(rec.x + h.x + h.sx / 2, rec.y + h.y + 1, rec.z + h.z + h.sz / 2);
            if (!spawned) {
                for (ServerPlayer p : in) {
                    if (h.contains(p.m_20185_() - rec.x, p.m_20186_() - rec.y + 0.5, p.m_20189_() - rec.z)) {
                        spawnHall(sl, rec, d, h, tag);
                        rec.hallsSpawned |= bit; SPAWNED_AT.put(tag, tk); data.m_77762_();
                        break;
                    }
                }
            } else if (sl.m_46805_(center)) {
                if (!in.isEmpty()) {
                    // записи нет (после перезапуска) — отсрочка 30 тиков с этого момента
                    if (tk - SPAWNED_AT.computeIfAbsent(tag, k -> tk) > 30 && alive(sl, rec, d, tag).isEmpty()) {
                        rec.hallsCleared |= bit; data.m_77762_();
                        onCleared(sl, rec, h, in);
                    }
                } else if (tk - last > EMPTY_TICKS) {   // все ушли: убрать недобитых, зал разбудится заново
                    for (Entity e : alive(sl, rec, d, tag)) e.m_146870_();
                    rec.hallsSpawned &= ~bit; SPAWNED_AT.remove(tag); data.m_77762_();
                }
            }
        }
    }

    /** Загружены ли сущности во всех чанках зала (areEntitiesLoaded): иначе список живых мобов может быть пуст по ошибке. */
    static boolean entitiesLoaded(ServerLevel sl, DungeonData.Rec rec, Hall h) {
        int x0 = (rec.x + h.x) >> 4, x1 = (rec.x + h.x + h.sx - 1) >> 4, z0 = (rec.z + h.z) >> 4, z1 = (rec.z + h.z + h.sz - 1) >> 4;
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++)
                if (!sl.m_143319_(((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32))) return false;
        return true;
    }

    static final List<String> LOOT = List.of("8 minecraft:amethyst_shard", "2 minecraft:diamond", "6 minecraft:gold_ingot", "minecraft:golden_apple");

    private static void onCleared(ServerLevel sl, DungeonData.Rec rec, Hall h, List<ServerPlayer> in) {
        if (h.chest == null) return;
        Loot.chest(sl, rec.x + h.chest.x, rec.y + h.chest.y, rec.z + h.chest.z, "Тестовый разлом", LOOT);
        for (ServerPlayer p : in) p.m_5661_(Component.m_237115_("explosionwand.dungeon.cleared"), false);
    }

    static List<Entity> alive(ServerLevel sl, DungeonData.Rec rec, Def d, String tag) {
        int[] b = d.bounds();
        AABB box = new AABB(rec.x + b[0] - 24, rec.y + b[1] - 24, rec.z + b[2] - 24, rec.x + b[3] + 24, rec.y + b[4] + 24, rec.z + b[5] + 24);
        return sl.m_6249_(null, box, e -> e.m_6084_() && e.m_19880_().contains(tag));
    }

    private static void spawnHall(ServerLevel sl, DungeonData.Rec rec, Def d, Hall h, String tag) {
        int have = alive(sl, rec, d, tag).size();   // недобитые с прошлого раза не дублируются
        for (int i = have; i < h.spawns.size(); i++) {
            Mark m = h.spawns.get(i);
            double x = rec.x + m.x + 0.5, y = rec.y + m.y, z = rec.z + m.z + 0.5;
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
                p.m_5661_(Component.m_237115_("explosionwand.dungeon.locked." + d.id), true);
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
