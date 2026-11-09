package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Цитадель Затмения (фаза 5.3): первый данж Ада. Постройка - tools/gen_citadel.py (контракт docs/citadel_layout.md), общий конвейер - Dungeons
 * (Def.CITADEL, Def.hell: записи в хранилище Ада, расчистка площадки, вставка по времени). Здесь:
 *  - выбор места: одна на мир в Базальтовых пиках, 500-1500 блоков от портала (чистая findSite + поиск в фоновом потоке по генератору биомов);
 *  - механики по меткам шаблона: Кристальные лампы, маяки двора (ворота gate2), Солнечные часы (4 зеркала, 4 печати), Лунные камни и сокеты
 *    (врата prethrone), секрет-оружейная, фиолетовый столб света над вершиной (маяк на парапете Тронного двора);
 *  - Осколок Печати: Затмение открывает gate1, Печать Трона - throne_seal (общий путь дверей Dungeons.onRightClick).
 * Мобов Цитадели пока нет (5.4): метки mob/wave остаются маркерами, залы с печатью не будятся (Def.passive). Босс - 5.5.
 */
final class Citadel {
    private Citadel() {}

    /** Расчистка: цилиндр радиуса CLEAR_R и высоты CLEAR_H над y данжа; заполнение под подошвой - в круге FOOT_R. */
    static final int CLEAR_R = 116, CLEAR_H = 125, FOOT_R = 114;
    /** Место: расстояние от портала Ада, шаг сетки поиска, вынос проб от центра (пятно биома должно накрывать гору). */
    static final int MIN_DIST = 500, MAX_DIST = 1500, STEP = 64, SAMPLE = 56;
    /** Высота дна подошвы (y данжа) не выше/не ниже этих значений в Аду (высота мира 0..255, над горой нужно ~125 блоков). */
    static final int Y_MIN = 48, Y_MAX = 112;
    /** Свет Кристальной лампы для механик (радиус, блоков). Блок лампы светит как eclipse_crystal_block (12), для мобов Цитадели (5.4) освещённым считается круг 8. */
    static final double LAMP_RADIUS = 8.0;
    /** Дальность работы механик: игрок ближе этого расстояния по горизонтали. */
    static final double WORK_RANGE = 120.0;

    // ================================================================ место (чистая логика)
    /** Пятно Базальтовых пиков в точке (x, z)? */
    interface Probe { boolean basalt(int x, int z); }

    static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    /**
     * Место Цитадели: центр горы {x, z}. Клетки сетки STEP в кольце MIN_DIST+40..MAX_DIST-40 от портала перебираются в порядке, заданном сидом
     * (стартовая клетка и шаг, взаимно простой с числом клеток), со смещением внутри клетки. Берётся первая, где все 5 проб (центр и углы квадрата
     * SAMPLE) - Базальтовые пики; если таких нет - клетка с наибольшим числом проб (даже без пиков Цитадель появится: данж обязан быть в каждом мире).
     */
    static int[] findSite(long seed, double portalX, double portalZ, Probe probe) {
        List<int[]> cells = new ArrayList<>();
        for (int x = -MAX_DIST; x <= MAX_DIST; x += STEP)
            for (int z = -MAX_DIST; z <= MAX_DIST; z += STEP) {
                double d = Math.hypot(x - portalX, z - portalZ);
                if (d >= MIN_DIST + 40 && d <= MAX_DIST - 40) cells.add(new int[]{x, z});
            }
        int n = cells.size();
        long h = Dungeons.mix(seed ^ 0x43495441L);
        int start = (int) Long.remainderUnsigned(h, n);
        int stride = 1 + (int) Long.remainderUnsigned(Dungeons.mix(h + 1), n - 1);
        while (gcd(stride, n) != 1) stride++;
        int[] best = null; int bestScore = -1;
        for (int i = 0; i < n; i++) {
            int[] c = cells.get((int) (((long) start + (long) i * stride) % n));
            long j = Dungeons.mix(h + 17L * i + 5);
            int x = c[0] + (int) Long.remainderUnsigned(j, 49) - 24, z = c[1] + (int) Long.remainderUnsigned(Dungeons.mix(j), 49) - 24;
            int score = (probe.basalt(x, z) ? 1 : 0) + (probe.basalt(x - SAMPLE, z - SAMPLE) ? 1 : 0) + (probe.basalt(x + SAMPLE, z - SAMPLE) ? 1 : 0)
                    + (probe.basalt(x - SAMPLE, z + SAMPLE) ? 1 : 0) + (probe.basalt(x + SAMPLE, z + SAMPLE) ? 1 : 0);
            if (score == 5) return new int[]{x, z};
            if (score > bestScore) { bestScore = score; best = new int[]{x, z}; }
        }
        return best;
    }

    /** Высота дна: среднее по пробам высоты земли, в пределах Y_MIN..Y_MAX. */
    static int pickY(int[] heights) {
        long sum = 0;
        for (int h : heights) sum += h;
        int avg = (int) Math.round((double) sum / Math.max(1, heights.length));
        return Math.max(Y_MIN, Math.min(Y_MAX, avg));
    }

    // ================================================================ поиск места (фон) и регистрация
    private static volatile int[] found;
    private static volatile long foundSeed;
    private static volatile boolean searching;

    static void clear() { found = null; searching = false; }

    /** Место Цитадели: запись в мире, иначе результат поиска; null - ещё ищется (поиск идёт в фоновом потоке по биомному источнику генератора, без загрузки чанков). */
    static int[] siteOf(ServerLevel sl, DungeonData data) {
        DungeonData.Rec rec = data.recs.get(Dungeons.CITADEL.key(0, 0));
        if (rec != null) return new int[]{rec.x, rec.z};
        long seed = sl.m_7328_();
        int[] f = found;
        if (f != null && foundSeed == seed) return f;
        if (searching) return null;
        searching = true;
        BiomeSource src = null; Climate.Sampler sampler = null;
        try {
            src = sl.m_7726_().m_8481_().m_62218_();
            sampler = sl.m_7726_().m_214994_().m_224579_();
        } catch (RuntimeException | LinkageError e) { System.err.println("[explosionwand] Цитадель: биомный источник недоступен, место по сиду: " + e); }
        final BiomeSource fsrc = src; final Climate.Sampler fsam = sampler;
        final ResourceKey<Biome> peaks = ResourceKey.m_135785_(Registries.f_256952_, new ResourceLocation("explosionwand", "hell_basalt_peaks"));
        Thread t = new Thread(() -> {
            int[] r;
            try {
                Probe probe = fsrc == null ? (x, z) -> true : (x, z) -> fsrc.m_203407_(x >> 2, 16, z >> 2, fsam).m_203565_(peaks);
                r = findSite(seed, HellGate.HELL_X, HellGate.HELL_Z, probe);
            } catch (Throwable e) {
                System.err.println("[explosionwand] Цитадель: поиск места не удался, место по сиду: " + e);
                r = findSite(seed, HellGate.HELL_X, HellGate.HELL_Z, (x, z) -> true);
            }
            foundSeed = seed; found = r; searching = false;
        }, "explosionwand-citadel-site");
        t.setDaemon(true);
        t.start();
        return null;
    }

    /** Регистрация Цитадели, когда игрок в Аду подошёл ближе PREPARE (запись одна на мир). */
    static final int PREPARE = 260;

    static void discover(ServerLevel sl, DungeonData data, Dungeons.Def d, List<ServerPlayer> ps) {
        String key = d.key(0, 0);
        if (data.recs.containsKey(key)) return;
        int[] s = siteOf(sl, data);
        if (s == null) return;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : ps) best = Math.min(best, Math.hypot(p.m_20185_() - s[0], p.m_20189_() - s[1]));
        if (best > 600) return;
        d.preload();                      // шаблоны читаются заранее, пока игрок идёт
        if (best > PREPARE) return;
        int[] hs = new int[9];
        for (int i = 0; i < 9; i++) {     // центр и 8 точек на круге 60: колонки по загруженным чанкам или по оценке генератора
            double a = i == 0 ? 0 : Math.PI / 4 * (i - 1), r = i == 0 ? 0 : 60;
            int[] c = Dungeons.column(sl, s[0] + (int) Math.round(Math.cos(a) * r), s[1] + (int) Math.round(Math.sin(a) * r));
            if (c[0] == DungeonSite.UNKNOWN) return;   // оценить нельзя: ждём загрузки чанков
            hs[i] = c[0];
        }
        DungeonData.Rec rec = new DungeonData.Rec(key, d.id, 0, 0, s[0], pickY(hs), s[1]);
        rec.layout = d.signature();
        data.recs.put(key, rec);
        data.m_77762_();
    }

    // ================================================================ блоки
    static Block modBlock(String id) { return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("explosionwand", id)); }
    static BlockState lampOn() { Block b = modBlock("eclipse_crystal_block"); return b == null ? null : b.m_49966_(); }
    static BlockState lampOff() { return Fx.state("tinted_glass"); }
    static BlockState moon() { return Fx.state("sea_lantern"); }

    static BlockPos pos(DungeonData.Rec rec, Mark m) { return new BlockPos(rec.x + m.x, rec.y + m.y, rec.z + m.z); }

    static boolean is(BlockState st, String id) { return st != null && st.m_60734_() == Fx.block(id); }

    static boolean lampLit(ServerLevel sl, DungeonData.Rec rec, Mark m) {
        BlockPos p = pos(rec, m);
        BlockState on = lampOn();
        return on != null && sl.m_46805_(p) && sl.m_8055_(p).m_60734_() == on.m_60734_();
    }

    /** Освещена ли точка зажжённой Кристальной лампой Цитадели (круг LAMP_RADIUS): хук для мобов Цитадели (5.4). */
    static boolean isLit(ServerLevel sl, DungeonData.Rec rec, double x, double y, double z) {
        Dungeons.Def d = rec.def();
        if (d == null) return false;
        for (Mark m : d.marks("lamp")) {
            double dx = rec.x + m.x + 0.5 - x, dy = rec.y + m.y + 0.5 - y, dz = rec.z + m.z + 0.5 - z;
            if (dx * dx + dy * dy + dz * dz <= LAMP_RADIUS * LAMP_RADIUS && lampLit(sl, rec, m)) return true;
        }
        return false;
    }

    // ================================================================ чистая логика головоломок
    /** Сторона зеркала (как в DungeonMech.FACING): 0 N, 1 E, 2 S, 3 W. Начальная - E (пропускает луч). */
    static final int N = 0, E = 1, S = 2, W = 3, INIT_DIR = E;

    /**
     * Солнечные часы: луч идёт на восток по линии зеркал. Зеркало, повёрнутое на N или S, ставит ветвь в свою печать (луч идёт дальше), на E пропускает,
     * на W гасит луч (дальше зеркала не освещаются, своей ветви нет). sides[i] - верная сторона печати i (N или S). Печать горит, если луч дошёл до
     * зеркала i и оно повёрнуто в верную сторону.
     */
    static boolean[] sunLit(int[] dirs, int[] sides) {
        boolean[] lit = new boolean[dirs.length];
        boolean reach = true;
        for (int i = 0; i < dirs.length; i++) {
            if (reach && dirs[i] == sides[i]) lit[i] = true;
            if (dirs[i] == W) reach = false;
        }
        return lit;
    }

    static boolean all(boolean[] a) { for (boolean b : a) if (!b) return false; return a.length > 0; }

    /** Лунный камень N готов к взятию: камни 1 и 2 - когда дойдёшь до крыши; камень 3 - когда зажжена лампа башни (защита лампы). Испытания с мобами - 5.4. */
    static boolean stoneReady(int n, boolean towerLampLit) { return n != 3 || towerLampLit; }

    // ================================================================ тик механик
    /** Раз в 10 тиков: в зоне игроков поддерживаются лампы, часы, камни, столб света; ворота gate2 по маякам. */
    static void tick(ServerLevel sl, DungeonData data, List<ServerPlayer> ps, long tk) {
        for (DungeonData.Rec rec : data.recs.values()) {
            Dungeons.Def d = rec.def();
            if (d != Dungeons.CITADEL || rec.partsDone != d.allParts()) continue;
            List<ServerPlayer> near = new ArrayList<>();
            for (ServerPlayer p : ps)
                if (p.m_6084_() && Math.hypot(p.m_20185_() - rec.x, p.m_20189_() - rec.z) <= CLEAR_R + WORK_RANGE) near.add(p);
            if (near.isEmpty()) continue;
            lamps(sl, rec, d, near);
            sun(sl, data, rec, d, near, tk);
            moons(sl, data, rec, d, near);
            if (tk % 20 == 0) gate2(sl, data, rec, d, near);
            if (tk % 100 == 0) pillar(sl, data, rec);
        }
    }

    private static boolean nearMark(List<ServerPlayer> ps, DungeonData.Rec rec, Mark m, double r) {
        for (ServerPlayer p : ps) {
            double dx = p.m_20185_() - (rec.x + m.x), dz = p.m_20189_() - (rec.z + m.z);
            if (dx * dx + dz * dz <= r * r && Math.abs(p.m_20186_() - (rec.y + m.y)) <= 60) return true;
        }
        return false;
    }

    /** Заглушки ламп из шаблона (аметистовые друзы, светят всегда) заменяются погасшей лампой. */
    private static void lamps(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> near) {
        BlockState off = lampOff();
        if (off == null) return;
        for (Mark m : d.marks("lamp")) {
            if (!nearMark(near, rec, m, 72)) continue;
            BlockPos p = pos(rec, m);
            if (sl.m_46805_(p) && is(sl.m_8055_(p), "amethyst_cluster")) sl.m_7731_(p, off, 3);
        }
    }

    static void lightLamp(ServerLevel sl, DungeonData.Rec rec, Mark m) {
        BlockPos p = pos(rec, m);
        BlockState on = lampOn();
        if (on == null || lampLit(sl, rec, m)) return;
        sl.m_7731_(p, on, 3);
        Fx.sound(sl, p.m_123341_() + 0.5, p.m_123342_() + 0.5, p.m_123343_() + 0.5, "block.amethyst_block.resonate", 1.5F, 1.4F);
        Fx.sound(sl, p.m_123341_() + 0.5, p.m_123342_() + 0.5, p.m_123343_() + 0.5, "block.beacon.activate", 0.6F, 1.8F);
        Fx.send(sl, Fx.particle("end_rod"), p.m_123341_() + 0.5, p.m_123342_() + 0.6, p.m_123343_() + 0.5, 24, 0.5, 0.5, 0.5, 0.08);
    }

    // ---------------------------------------------------------------- маяки двора -> ворота кольца 2
    static boolean beaconLit(ServerLevel sl, BlockPos p) {
        BlockState st = sl.m_8055_(p);
        return is(st, "soul_campfire") && st.equals(Dungeons.setProp(st, "lit", "true"));
    }

    static int beaconsLit(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d) {
        int n = 0;
        for (Mark m : d.marks("beacon")) if (sl.m_46805_(pos(rec, m)) && beaconLit(sl, pos(rec, m))) n++;
        return n;
    }

    private static Mark door(Dungeons.Def d, String id) {
        for (Mark m : d.doors()) if (m.a.equals(id)) return m;
        return null;
    }

    private static void openNamed(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, String id, List<ServerPlayer> near, String msg) {
        if (!rec.opened.add(id)) return;
        data.m_77762_();
        Mark door = door(d, id);
        if (door != null) Dungeons.openDoor(sl, rec, door);
        if (msg != null) for (ServerPlayer p : near) p.m_5661_(Component.m_237115_(msg), true);
    }

    /** Три маяка горят - ворота gate2 открываются (раз в секунду проверяются и зажжённые не кликом, а, например, стрелой). */
    private static void gate2(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> near) {
        if (rec.opened.contains("gate2")) return;
        if (beaconsLit(sl, rec, d) >= d.marks("beacon").size() && !d.marks("beacon").isEmpty())
            openNamed(sl, data, rec, d, "gate2", near, "explosionwand.citadel.gate2_open");
    }

    // ---------------------------------------------------------------- Солнечные часы
    static String[] dirKeys = {"north", "east", "south", "west"};

    private static List<Mark> sorted(List<Mark> l) {
        List<Mark> out = new ArrayList<>(l);
        out.sort((a, b) -> a.a.compareTo(b.a));
        return out;
    }

    private static int sideOf(String a) { return a.toUpperCase().endsWith("S") ? S : N; }

    private static int mirrorDir(DungeonData.Rec rec, Mark m) { Integer v = rec.mirrors.get(DungeonMech.mirKey(m)); return v != null ? v : INIT_DIR; }

    /** Печати часов: a = "n,N|S" -> {n, верная сторона}. */
    private static int[] plateCfg(Mark m) {
        String[] a = m.a.split(",");
        return new int[]{Integer.parseInt(a[0].trim()), sideOf(a.length > 1 ? a[1] : "N")};
    }

    private static void sun(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> near, long tk) {
        List<Mark> mirrors = sorted(d.marks("sun_mirror")), plates = sorted(d.marks("sun_plate"));
        if (mirrors.isEmpty() || plates.size() != mirrors.size()) return;
        if (!nearMark(near, rec, mirrors.get(0), 56)) return;
        BlockPos p0 = pos(rec, mirrors.get(0));
        if (!sl.m_46805_(p0)) return;
        int n = mirrors.size();
        int[] dirs = new int[n], sides = new int[n];
        for (int i = 0; i < n; i++) { dirs[i] = mirrorDir(rec, mirrors.get(i)); sides[i] = plateCfg(plates.get(i))[1]; }
        boolean solved = rec.opened.contains("sun");
        boolean[] lit = solved ? allTrue(n) : sunLit(dirs, sides);
        BlockState gold = Fx.state("gold_block"), glow = Fx.state("glowstone");
        for (int i = 0; i < n; i++) {
            BlockPos pp = pos(rec, plates.get(i));
            if (!sl.m_46805_(pp)) continue;
            BlockState cur = sl.m_8055_(pp);
            if (lit[i] && !is(cur, "glowstone") && glow != null) { sl.m_7731_(pp, glow, 3); Fx.sound(sl, pp.m_123341_() + 0.5, pp.m_123342_(), pp.m_123343_() + 0.5, "block.amethyst_block.chime", 1.2F, 1.3F); }
            else if (!lit[i] && is(cur, "glowstone") && gold != null) sl.m_7731_(pp, gold, 3);
        }
        if (!solved && all(lit)) {
            openNamed(sl, data, rec, d, "sun", near, "explosionwand.citadel.sun_solved");   // одно сообщение; двери ниже открываются молча
            openNamed(sl, data, rec, d, "sun_w", near, null);
            openNamed(sl, data, rec, d, "sun_e", near, null);
        }
        if (tk % 10 == 0) sunBeams(sl, rec, d, mirrors, plates, dirs, lit);
    }

    private static boolean[] allTrue(int n) { boolean[] a = new boolean[n]; java.util.Arrays.fill(a, true); return a; }

    /** Лучи часов частицами: от окна на восток вдоль линии зеркал (до зеркала, повёрнутого на запад), ветви - в печати. */
    private static void sunBeams(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, List<Mark> mirrors, List<Mark> plates, int[] dirs, boolean[] lit) {
        List<Mark> src = d.marks("sun_src");
        if (src.isEmpty()) return;
        Mark s = src.get(0);
        double y = rec.y + s.y + 0.5, z = rec.z + s.z + 0.5;
        double x = rec.x + s.x + 0.5, end = x;
        boolean blocked = false;
        for (int i = 0; i < mirrors.size() && !blocked; i++) {
            Mark mi = mirrors.get(i);
            end = rec.x + mi.x + 0.5;
            if (dirs[i] == W) blocked = true;
        }
        if (!blocked && !mirrors.isEmpty()) end += 3;
        for (double cx = x + 1; cx <= end; cx += 1.0) Fx.send(sl, Fx.particle("end_rod"), cx, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        boolean reach = true;
        for (int i = 0; i < mirrors.size(); i++) {
            Mark mi = mirrors.get(i);
            double mx = rec.x + mi.x + 0.5;
            Fx.send(sl, Fx.particle("end_rod"), mx + DungeonMech.DX[dirs[i]] * 0.7, y + 0.1, rec.z + mi.z + 0.5 + DungeonMech.DZ[dirs[i]] * 0.7, 2, 0.05, 0.05, 0.05, 0.01);   // куда смотрит зеркало
            if (reach && (dirs[i] == N || dirs[i] == S)) {
                double pz = rec.z + plates.get(i).z + 0.5, from = rec.z + mi.z + 0.5, st = pz >= from ? 1.0 : -1.0;
                for (double cz = from + st; st > 0 ? cz <= pz : cz >= pz; cz += st)
                    Fx.send(sl, Fx.particle(lit[i] ? "electric_spark" : "end_rod"), mx, y, cz, 1, 0.0, 0.0, 0.0, 0.0);
            }
            if (dirs[i] == W) reach = false;
        }
    }

    private static void turnMirror(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Mark m, ServerPlayer p) {
        if (rec.opened.contains("sun")) {
            p.m_5661_(Component.m_237115_("explosionwand.citadel.sun_frozen"), true);
            Fx.sound(sl, rec.x + m.x + 0.5, rec.y + m.y + 0.5, rec.z + m.z + 0.5, "block.amethyst_block.hit", 1.0F, 0.5F);
            return;
        }
        int nd = DungeonMech.turn(mirrorDir(rec, m));
        rec.mirrors.put(DungeonMech.mirKey(m), nd);
        data.m_77762_();
        double x = rec.x + m.x + 0.5, y = rec.y + m.y + 0.5, z = rec.z + m.z + 0.5;
        Fx.sound(sl, x, y, z, "block.amethyst_block.chime", 1.2F, 0.8F + 0.2F * nd);
        Fx.send(sl, Fx.particle("end_rod"), x + DungeonMech.DX[nd] * 0.7, y, z + DungeonMech.DZ[nd] * 0.7, 6, 0.1, 0.1, 0.1, 0.02);
        p.m_5661_(Component.m_237110_("explosionwand.citadel.mirror", Component.m_237115_("explosionwand.citadel.dir." + dirKeys[nd])), true);
    }

    // ---------------------------------------------------------------- Лунные камни и сокеты
    private static int num(Mark m) { try { return Integer.parseInt(m.a.trim()); } catch (NumberFormatException e) { return 0; } }

    private static Mark towerLamp(Dungeons.Def d) {
        for (Mark m : d.marks("lamp")) if (m.a.equals("tower3")) return m;
        return null;
    }

    private static boolean ready(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, int n) {
        Mark lm = towerLamp(d);
        return stoneReady(n, lm != null && lampLit(sl, rec, lm));
    }

    private static void moons(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> near) {
        BlockState stone = moon();
        if (stone == null) return;
        for (Mark m : d.marks("moonstone")) {
            if (!nearMark(near, rec, m, 72)) continue;
            BlockPos p = pos(rec, m);
            if (!sl.m_46805_(p)) continue;
            int n = num(m);
            boolean taken = rec.opened.contains("moon" + n);
            boolean here = is(sl.m_8055_(p), "sea_lantern");
            if (!taken && !here && sl.m_8055_(p).m_60795_() && ready(sl, rec, d, n)) {
                sl.m_7731_(p, stone, 3);
                Fx.send(sl, Fx.particle("end_rod"), p.m_123341_() + 0.5, p.m_123342_() + 0.5, p.m_123343_() + 0.5, 20, 0.4, 0.4, 0.4, 0.05);
                Fx.sound(sl, p.m_123341_() + 0.5, p.m_123342_(), p.m_123343_() + 0.5, "block.amethyst_block.resonate", 1.5F, 1.6F);
            } else if (taken && here) sl.m_7731_(p, Fx.state("air"), 3);
        }
        for (Mark m : d.marks("moon_socket")) {   // сокет горит, если камень взят (после перезапуска или если свет сломали - возвращается)
            if (!nearMark(near, rec, m, 72)) continue;
            BlockPos p = pos(rec, m);
            if (!sl.m_46805_(p)) continue;
            boolean taken = rec.opened.contains("moon" + num(m)), here = is(sl.m_8055_(p), "sea_lantern");
            if (taken && !here) sl.m_7731_(p, stone, 3);
        }
    }

    private static void takeStone(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, Mark m, ServerPlayer p) {
        int n = num(m);
        BlockPos pos = pos(rec, m);
        if (rec.opened.contains("moon" + n)) return;
        if (!ready(sl, rec, d, n) || !is(sl.m_8055_(pos), "sea_lantern")) {
            p.m_5661_(Component.m_237115_("explosionwand.citadel.stone_sleeps." + n), true);
            Fx.sound(sl, pos.m_123341_() + 0.5, pos.m_123342_(), pos.m_123343_() + 0.5, "block.amethyst_block.hit", 1.0F, 0.5F);
            return;
        }
        rec.opened.add("moon" + n);
        data.m_77762_();
        sl.m_7731_(pos, Fx.state("air"), 3);
        Fx.sound(sl, pos.m_123341_() + 0.5, pos.m_123342_(), pos.m_123343_() + 0.5, "block.amethyst_block.break", 2.0F, 1.2F);
        Fx.send(sl, Fx.particle("end_rod"), pos.m_123341_() + 0.5, pos.m_123342_() + 0.5, pos.m_123343_() + 0.5, 30, 0.5, 0.5, 0.5, 0.1);
        p.m_5661_(Component.m_237110_("explosionwand.citadel.stone_taken", n), true);
        for (Mark so : d.marks("moon_socket"))
            if (num(so) == n) {   // камень уходит в свой сокет у Лунных врат
                BlockPos sp = pos(rec, so);
                if (sl.m_46805_(sp)) sl.m_7731_(sp, moon(), 3);
                Fx.send(sl, Fx.particle("end_rod"), sp.m_123341_() + 0.5, sp.m_123342_() + 0.5, sp.m_123343_() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
                Fx.sound(sl, sp.m_123341_() + 0.5, sp.m_123342_(), sp.m_123343_() + 0.5, "block.amethyst_block.resonate", 1.5F, 1.2F);
            }
        int taken = 0;
        for (Mark st : d.marks("moonstone")) if (rec.opened.contains("moon" + num(st))) taken++;
        if (taken >= d.marks("moonstone").size()) {
            List<ServerPlayer> one = new ArrayList<>();
            one.add(p);
            openNamed(sl, data, rec, d, "prethrone", one, "explosionwand.citadel.prethrone_open");
        }
    }

    // ---------------------------------------------------------------- столб света над вершиной
    /** Маяк на парапете Тронного двора (восток, x 35): основание 3x3 из железа, маяк и фиолетовое стекло - луч в небо. Поддерживается раз в 5 секунд, пока рядом игрок. */
    static final int BEACON_X = 35, BEACON_Y = 67, BEACON_Z = 0;

    /** Столб ставится один раз (отметка "pillar" в Rec.opened, сбрасывается вместе с раскладкой): сломанный маяк не возвращается, дюпа нет. */
    static void pillar(ServerLevel sl, DungeonData data, DungeonData.Rec rec) {
        if (rec.opened.contains("pillar")) return;
        BlockPos b = new BlockPos(rec.x + BEACON_X, rec.y + BEACON_Y, rec.z + BEACON_Z);
        if (!sl.m_46805_(b)) return;
        BlockState iron = Fx.state("iron_block"), beacon = Fx.state("beacon"), glass = Fx.state("purple_stained_glass");
        if (iron == null || beacon == null || glass == null) return;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) sl.m_7731_(new BlockPos(b.m_123341_() + dx, b.m_123342_() - 1, b.m_123343_() + dz), iron, 3);
        sl.m_7731_(b, beacon, 3);
        sl.m_7731_(b.m_7494_(), glass, 3);
        rec.opened.add("pillar");
        data.m_77762_();
    }

    /** Клетка маяка столба света или стекла над ним (координаты данжа): ПКМ по ней отменяется (меню маяка на арене не нужно). */
    static boolean isPillar(int rx, int ry, int rz) { return rx == BEACON_X && rz == BEACON_Z && (ry == BEACON_Y || ry == BEACON_Y + 1); }

    // ================================================================ клики и секреты
    /** ПКМ по блоку Цитадели (координаты данжа). true - клик занят механикой (событие надо отменить). */
    static boolean onClick(ServerLevel sl, DungeonData data, DungeonData.Rec rec, ServerPlayer p, int rx, int ry, int rz, boolean main) {
        Dungeons.Def d = rec.def();
        if (isPillar(rx, ry, rz)) return true;
        for (Mark m : d.marks("lamp"))
            if (m.x == rx && m.z == rz && (m.y == ry || m.y - 1 == ry)) {   // сама лампа или её пьедестал
                if (main) lightLamp(sl, rec, m);
                return true;
            }
        for (Mark m : d.marks("beacon"))
            if (m.x == rx && m.y == ry && m.z == rz) {
                if (!main) return true;
                BlockPos bp = pos(rec, m);
                BlockState st = sl.m_8055_(bp);
                if (is(st, "soul_campfire") && !beaconLit(sl, bp)) {
                    sl.m_7731_(bp, Dungeons.setProp(st, "lit", "true"), 3);
                    Fx.sound(sl, bp.m_123341_() + 0.5, bp.m_123342_(), bp.m_123343_() + 0.5, "item.firecharge.use", 1.2F, 0.7F);
                    Fx.send(sl, Fx.particle("soul_fire_flame"), bp.m_123341_() + 0.5, bp.m_123342_() + 0.8, bp.m_123343_() + 0.5, 30, 0.25, 0.4, 0.25, 0.04);
                    p.m_5661_(Component.m_237110_("explosionwand.citadel.beacon_lit", beaconsLit(sl, rec, d), d.marks("beacon").size()), true);
                    List<ServerPlayer> one = new ArrayList<>();
                    one.add(p);
                    if (beaconsLit(sl, rec, d) >= d.marks("beacon").size()) openNamed(sl, data, rec, d, "gate2", one, "explosionwand.citadel.gate2_open");
                }
                return true;
            }
        for (Mark m : d.marks("sun_mirror"))
            if (m.x == rx && m.y == ry && m.z == rz) {
                if (main) turnMirror(sl, data, rec, m, p);
                return true;
            }
        for (Mark m : d.marks("moonstone"))
            if (m.x == rx && m.z == rz && (m.y == ry || m.y - 1 == ry)) {
                if (main) takeStone(sl, data, rec, d, m, p);
                return true;
            }
        return false;
    }

    /** Взрыв или кирка по оружейной (секрет armory, трещина в стене двора): открывает тайник. blocks - клетки взрыва или одна клетка удара. */
    static boolean secretHit(ServerLevel sl, DungeonData data, DungeonData.Rec rec, List<BlockPos> blocks, List<ServerPlayer> near) {
        Dungeons.Def d = rec.def();
        if (d != Dungeons.CITADEL || rec.opened.contains("armory") || !d.ready()) return false;
        for (Mark m : d.marks("secret")) {
            if (!m.a.equals("armory")) continue;
            for (BlockPos b : blocks)
                if (m.inside(b.m_123341_() - rec.x, b.m_123342_() - rec.y, b.m_123343_() - rec.z)) {
                    DungeonMech.openId(sl, data, rec, d, "armory", near, true);
                    return true;
                }
        }
        return false;
    }

    /** Взрыв в Аду: если задел оружейную Цитадели, тайник открывается (блоки данжа взрыв всё равно не ломает). */
    static void onDetonate(ServerLevel sl, DungeonData data, List<BlockPos> blocks) {
        for (DungeonData.Rec rec : data.recs.values()) {
            if (rec.def() != Dungeons.CITADEL || rec.bossDead) continue;
            List<ServerPlayer> near = new ArrayList<>();
            for (ServerPlayer p : sl.m_6907_()) if (rec.contains(p.m_20185_(), p.m_20186_(), p.m_20189_(), 30)) near.add(p);
            secretHit(sl, data, rec, blocks, near);
        }
    }
}
