package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Механики данжей (фаза 4.2, шаг 2): ловушки и головоломки по меткам ew_marks. Всё это «свои эффекты» данжа: блоки ставятся
 * напрямую (setBlock, события защиты не вызываются), сущностей и block_display нет — только блоки и частицы,
 * поэтому убирать при выходе игроков нечего; в памяти лежат лишь кэши (clear() из Dungeons.clearState).
 *
 * Метки (координаты данжа, поля — в tools/gen_dungeons.py):
 *  steps    — кристальные ступени над провалом: появляются, когда игрок рядом, и убираются, когда он ушёл; пока скрыты —
 *             слабое мерцание частиц на месте ступени (подсказка);
 *  beams    — плитка-ловушка: наступил — через полсекунды из кристалла бьёт луч (урон и частицы); успей отпрыгнуть из луча;
 *  mirror   — головоломка: src (излучатель), mir (зеркало, ПКМ поворачивает на 90°), rcv (приёмник); луч идёт по прямой на высоте
 *             метки, зеркало отправляет его в свою сторону; когда луч достиг всех приёмников — открывается секрет с тем же id;
 *  platform — маленькая платформа ездит туда-сюда по оси: блоки ставит движок, игроков на ней сдвигает вместе с ней;
 *  lever    — ПКМ по рычагу открывает секрет с его id;
 *  secret   — стена, которая становится воздухом по id (от рычага или решённой головоломки; осколок не нужен);
 *  seal     — дверь логова: закрывается, пока зал разбужен и не зачищен, и открывается после зачистки.
 * Пепельное святилище (4.8):
 *  bell     — колокол: ПКМ звонит и открывает секрет с id метки (крышка шахты в башне);
 *  jets     — сопло в стене: по таймеру (период, сдвиг) вдыхает 25 тиков (дымок, звук), потом 25 тиков выдыхает огонь вдоль линии
 *             (частицы, урон и поджог тем, кто на линии); блоков не меняет;
 *  crumble  — платформа: через 1,5 с после шага игрока блоки пропадают, через 7 с возвращаются (если на месте никого нет);
 *  geyser   — жерло: по таймеру булькает 30 тиков, потом 14 тиков бьёт столбом огня и подбрасывает игрока над ним (вверх или в сторону).
 * Достройка Святилища (4.8, шаг 2б):
 *  braziers — «Четыре жаровни» (a = "id,роль,n"): b — костёр жаровни n (ПКМ зажигает), f — панель n фрески (роспись ставит движок: n-я по порядку
 *             зажигания жаровня, цвет и узор), w — точка волны мобов. Порядок один на данж и считается из его позиции (fireOrder): верный порядок
 *             открывает секрет с id, неверный гасит все жаровни и зовёт волну ванильных мобов. Состояние - сами костры в мире + Rec.opened.
 * Состояние: DungeonData.Rec.opened (открытые секреты) и Rec.mirrors (повороты зеркал) — в NBT; всё остальное восстанавливается из мира.
 */
final class DungeonMech {
    private DungeonMech() {}

    static final String[] TYPES = {"steps", "beams", "mirror", "platform", "lever", "secret", "seal", "bell", "jets", "crumble", "geyser", "braziers"};
    static final String[] FACING = {"north", "east", "south", "west"};
    static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
    static final int STEP_SHOW = 4, STEP_HIDE = 10, BEAM_DELAY = 10, BEAM_COOLDOWN = 40, PLAT_PERIOD = 8, PLAT_PAUSE = 3, MAX_BEAM = 80;
    static final double BEAM_HIT = 1.1;
    /** Струи огня: предупреждение и выдох (тики); гейзеры; осыпающиеся платформы: до обвала и до возвращения (тики). */
    static final int JET_WARN = 25, JET_BURST = 25, GEYSER_WARN = 30, GEYSER_BURST = 14, CRUMBLE_AFTER = 30, CRUMBLE_BACK = 140;

    // ================================================================ чистая логика (без мира)
    /** Твёрдый ли блок на позиции (координаты данжа). */
    interface Solid { boolean at(int x, int y, int z); }

    static final class Trace {
        final List<int[]> cells = new ArrayList<>();
        final Set<Long> hit = new HashSet<>();
    }

    static final class Puzzle {
        final String id;
        final List<Mark> srcs = new ArrayList<>(), mirs = new ArrayList<>(), rcvs = new ArrayList<>();
        Puzzle(String id) { this.id = id; }
    }

    /** Головоломки данжа по метке mirror: a = "id,роль,сторона". */
    static Map<String, Puzzle> puzzles(Dungeons.Def d) {
        Map<String, Puzzle> out = new LinkedHashMap<>();
        for (Mark m : d.marks("mirror")) {
            String[] a = m.a.split(",");
            if (a.length != 3) continue;
            Puzzle pz = out.computeIfAbsent(a[0], Puzzle::new);
            switch (a[1]) {
                case "src": pz.srcs.add(m); break;
                case "mir": pz.mirs.add(m); break;
                case "rcv": pz.rcvs.add(m); break;
                default: break;
            }
        }
        return out;
    }

    static int initDir(Mark m) { return Math.max(0, "NESW".indexOf(m.a.split(",")[2])); }
    static String mirKey(Mark m) { return m.x + "," + m.y + "," + m.z; }
    static int dirNow(DungeonData.Rec rec, Mark m) { Integer v = rec.mirrors.get(mirKey(m)); return v != null ? v : initDir(m); }
    static int turn(int dir) { return (dir + 1) & 3; }

    /** Один луч: от излучателя шагами по прямой; зеркало меняет сторону, приёмник останавливает и засчитывается, твёрдый блок гасит. */
    static void trace(int sx, int sy, int sz, int dir, Map<Long, Integer> mirrors, Set<Long> rcvs, Solid solid, Trace out) {
        int x = sx, z = sz, d = dir;
        for (int i = 0; i < MAX_BEAM * 4; i++) {
            x += DX[d]; z += DZ[d];
            long k = Dungeons.pack(x, sy, z);
            if (rcvs.contains(k)) { out.hit.add(k); out.cells.add(new int[]{x, sy, z}); return; }
            Integer md = mirrors.get(k);
            if (md != null) { out.cells.add(new int[]{x, sy, z}); d = md; continue; }
            if (solid.at(x, sy, z)) return;
            out.cells.add(new int[]{x, sy, z});
        }
    }

    /** Решена ли головоломка при текущих поворотах зеркал: все приёмники достигнуты. out получает пути лучей. */
    static boolean solve(DungeonData.Rec rec, Puzzle pz, Solid solid, Trace out) {
        Map<Long, Integer> mirrors = new HashMap<>();
        for (Mark m : pz.mirs) mirrors.put(Dungeons.pack(m.x, m.y, m.z), dirNow(rec, m));
        Set<Long> rcvs = new HashSet<>();
        for (Mark m : pz.rcvs) rcvs.add(Dungeons.pack(m.x, m.y, m.z));
        for (Mark s : pz.srcs) trace(s.x, s.y, s.z, initDir(s), mirrors, rcvs, solid, out);
        return !rcvs.isEmpty() && out.hit.containsAll(rcvs);
    }

    /** Игрок (координаты данжа) стоит на плитке-ловушке: в блоке плитки по x и z и ногами на её верхней грани. */
    static boolean onPlate(Mark m, double lx, double ly, double lz) {
        return (int) Math.floor(lx) == m.x && (int) Math.floor(lz) == m.z && ly >= m.y + 0.9 && ly <= m.y + 1.7;
    }

    /** Расстояние от точки до отрезка AB. */
    static double segDist(double px, double py, double pz, double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, l2 = dx * dx + dy * dy + dz * dz;
        double t = l2 < 1.0E-9 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / l2));
        double qx = ax + dx * t - px, qy = ay + dy * t - py, qz = az + dz * t - pz;
        return Math.sqrt(qx * qx + qy * qy + qz * qz);
    }

    /**
     * Дверь-печать закрыта, пока зал разбужен (бит spawned), не зачищен (бит cleared) и в нём стоит живой игрок (inside).
     * Погиб и возродился у жаровни — в зале никого, дверь открыта: можно войти снова (или зал сбросится сам, Dungeons.hallStale).
     */
    static boolean sealWanted(long spawned, long cleared, int hall, boolean inside) { long bit = 1L << hall; return inside && (spawned & bit) != 0 && (cleared & bit) == 0; }

    /** Платформа: a = "±x|±z,длина,блок" -> {dx, dz, длина}; null при ошибке. */
    static int[] platCfg(String a) {
        String[] v = a.split(",");
        if (v.length != 3 || v[0].length() != 2) return null;
        int sign = v[0].charAt(0) == '-' ? -1 : 1;
        char ax = v[0].charAt(1);
        try { return new int[]{ax == 'x' ? sign : 0, ax == 'z' ? sign : 0, Integer.parseInt(v[1])}; }
        catch (NumberFormatException e) { return null; }
    }

    static String platBlock(String a) { return a.split(",")[2]; }

    static final class Plat { int off = 0, dir = 1, pause = 0; int[] cfg; BlockState blk; }

    /** Шаг платформы: новое смещение или -1, если стоит (пауза на краю). На краях разворот и пауза PLAT_PAUSE шагов. */
    static int advance(Plat st, int len) {
        if (st.pause > 0) { st.pause--; return -1; }
        int next = st.off + st.dir;
        st.off = next;
        if (next >= len) { st.dir = -1; st.pause = PLAT_PAUSE; }
        else if (next <= 0) { st.dir = 1; st.pause = PLAT_PAUSE; }
        return next;
    }

    // ---------------------------------------------------------------- чистая логика Святилища (4.8)
    /** Цикл по таймеру: 0 покой, 1 предупреждение (warn тиков), 2 выброс (burst тиков, в конце периода); off сдвигает фазу. */
    static int cycle(long tk, int period, int warn, int burst, int off) {
        if (period <= warn + burst) return 2;
        long t = Math.floorMod(tk + off, (long) period);
        if (t >= period - burst) return 2;
        if (t >= period - burst - warn) return 1;
        return 0;
    }

    /** a = "±x|±z,длина,период,сдвиг" -> {dx, dz, длина, период, сдвиг}; null при ошибке. */
    static int[] jetCfg(String a) {
        String[] v = a.split(",");
        if (v.length != 4 || v[0].length() != 2) return null;
        int sign = v[0].charAt(0) == '-' ? -1 : 1;
        char ax = v[0].charAt(1);
        if (ax != 'x' && ax != 'z') return null;
        try { return new int[]{ax == 'x' ? sign : 0, ax == 'z' ? sign : 0, Integer.parseInt(v[1].trim()), Integer.parseInt(v[2].trim()), Integer.parseInt(v[3].trim())}; }
        catch (NumberFormatException e) { return null; }
    }

    /** a = "±x|±z|up,период" -> {dx, dz, период}; null при ошибке. */
    static int[] geyserCfg(String a) {
        String[] v = a.split(",");
        if (v.length != 2) return null;
        int dx = 0, dz = 0;
        if (!v[0].equals("up")) {
            if (v[0].length() != 2) return null;
            int sign = v[0].charAt(0) == '-' ? -1 : 1;
            if (v[0].charAt(1) == 'x') dx = sign; else if (v[0].charAt(1) == 'z') dz = sign; else return null;
        }
        try { return new int[]{dx, dz, Integer.parseInt(v[1].trim())}; }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * Игрок на линии струи: rx, rz — от центра блока-сопла по горизонтали, feetRel — ноги относительно центра сопла по вертикали.
     * Линия идёт от сопла по оси (dx, dz) на len клеток; по бокам допуск 0,9, по высоте тело (центр на 0,9 над ногами) в 1,3 от оси.
     */
    static boolean jetHit(double rx, double feetRel, double rz, int dx, int dz, int len) {
        double along = rx * dx + rz * dz, lat = Math.abs(rx * dz - rz * dx);
        return along >= 0.4 && along <= len + 0.6 && lat <= 0.9 && Math.abs(feetRel + 0.9) <= 1.3;
    }

    /** Игрок в столбе гейзера над жерлом: rx, rz — от центра блока жерла, feetRel — ноги относительно верха жерла (блок m.y + 1). */
    static boolean inGeyser(double rx, double feetRel, double rz) { return Math.abs(rx) < 0.95 && Math.abs(rz) < 0.95 && feetRel >= -0.2 && feetRel <= 2.5; }

    /** Игрок стоит на осыпающейся платформе (метка crumble: верх на m.y + m.sy). Координаты данжа. */
    static boolean onPad(Mark m, double lx, double ly, double lz) {
        double top = m.y + m.sy;
        return lx >= m.x - 0.25 && lx <= m.x + m.sx + 0.25 && lz >= m.z - 0.25 && lz <= m.z + m.sz + 0.25 && ly >= top - 0.1 && ly <= top + 0.6;
    }

    /** Игрок телом пересекает место платформы (ставить блоки нельзя: замуровали бы). */
    static boolean inPad(Mark m, double lx, double ly, double lz) {
        return lx >= m.x - 0.4 && lx <= m.x + m.sx + 0.4 && lz >= m.z - 0.4 && lz <= m.z + m.sz + 0.4 && ly >= m.y - 1.8 && ly <= m.y + 0.9;
    }

    /** Состояние платформы: 0 целая, 1 осыпается, 2 нет. Шаг автомата; since — когда состояние началось. */
    static int crumbNext(int st, long since, long now, boolean stepped, boolean blocked) {
        if (st == 0) return stepped ? 1 : 0;
        if (st == 1) return now - since >= CRUMBLE_AFTER ? 2 : 1;
        return now - since >= CRUMBLE_BACK && !blocked ? 0 : 2;
    }

    static final class Crumb { int st; long since; }

    // ---------------------------------------------------------------- чистая логика «Четырёх жаровен» (4.8, шаг 2б)
    /** Цвет жаровни i (площадка вокруг костра и краска её узора на фреске), фон фрески, узоры 3 столбца x 5 строк сверху вниз («#» - краска). */
    static final String[] FIRE_COLORS = {"red_concrete", "blue_concrete", "yellow_concrete", "lime_concrete"};
    static final String FIRE_BG = "black_concrete";
    static final String[][] GLYPHS = {
            {"###", "#.#", "#.#", "#.#", "###"},
            {"#.#", "#.#", "###", "#.#", "#.#"},
            {".#.", ".#.", "###", ".#.", ".#."},
            {"#.#", ".#.", "#.#", ".#.", "#.#"}};
    /** Результат клика по жаровне: ничего (уже горит), верно, верно и последняя, ошибка. */
    static final int FIRE_NONE = 0, FIRE_OK = 1, FIRE_DONE = 2, FIRE_WRONG = -1;
    /** Сколько мобов волны уже стоит, чтобы новую не звать; ступени времени между кликами не нужны: после ошибки жаровни погашены. */
    static final int WAVE_CAP = 4;

    /** Зерно порядка из позиции данжа в мире. */
    static long fireSeed(int x, int z) { return Dungeons.mix(((long) x << 32) ^ (z & 0xFFFFFFFFL) ^ 0x46495245L); }

    /** Порядок зажигания: order[k] — номер жаровни, которую зажигают k-й; перестановка 0..3 по зерну. */
    static int[] fireOrder(long seed) {
        int[] o = {0, 1, 2, 3};
        long h = seed;
        for (int i = 3; i >= 1; i--) {
            h = Dungeons.mix(h + i);
            int j = (int) Long.remainderUnsigned(h, i + 1);
            int t = o[i]; o[i] = o[j]; o[j] = t;
        }
        return o;
    }

    /** Закрашена ли клетка узора жаровни b (col 0..2, row 0..4 сверху). */
    static boolean fireInk(int b, int col, int row) { return b >= 0 && b < GLYPHS.length && col >= 0 && col < 3 && row >= 0 && row < 5 && GLYPHS[b][row].charAt(col) == '#'; }

    /** Блок клетки панели: краска цвета жаровни или фон. */
    static String fireBlock(int b, int col, int row) { return fireInk(b, col, row) ? FIRE_COLORS[b] : FIRE_BG; }

    /** Горящие жаровни - ровно первые k по порядку (иначе состояние нарушено: гасим всё). */
    static boolean firePrefix(int[] order, boolean[] lit) {
        int k = 0;
        for (boolean l : lit) if (l) k++;
        for (int i = 0; i < k; i++) if (!lit[order[i]]) return false;
        return true;
    }

    /** Клик по жаровне clicked при уже горящих (первые k по порядку). */
    static int fireStep(int[] order, boolean[] lit, int clicked) {
        if (lit[clicked]) return FIRE_NONE;
        int k = 0;
        for (boolean l : lit) if (l) k++;
        if (clicked != order[k]) return FIRE_WRONG;
        return k + 1 == order.length ? FIRE_DONE : FIRE_OK;
    }

    static String fireWaveTag(DungeonData.Rec rec) { return "ewdw" + Integer.toHexString(rec.key.hashCode()); }

    /** Виды мобов волны по номеру точки: мобы Святилища (4.9) - Пеплоходы, Искра, гончая и Угольный страж. */
    static String waveMob(int i) { return new String[]{"ash_walker", "ash_walker", "ash_spark", "ash_walker", "ash_hound", "ash_guard"}[i % 6]; }

    // ================================================================ состояние в памяти (кэши, восстанавливаются из мира)
    static final Map<String, Boolean> STEPS = new HashMap<>();
    static final Map<String, Plat> PLAT = new HashMap<>();
    static final Map<String, Long> BEAM_AT = new HashMap<>(), BEAM_COOL = new HashMap<>();

    static final Map<String, Crumb> CRUMB = new HashMap<>();
    static final Map<String, Long> HIT_CD = new HashMap<>();

    static void clear() { STEPS.clear(); PLAT.clear(); BEAM_AT.clear(); BEAM_COOL.clear(); CRUMB.clear(); HIT_CD.clear(); }

    // ================================================================ тик
    static void tick(ServerLevel sl, DungeonData data, List<ServerPlayer> ps, long tk) {
        if (data.recs.isEmpty()) return;
        for (DungeonData.Rec rec : data.recs.values()) {
            Dungeons.Def d = rec.def();
            if (d == null || rec.partsDone != d.allParts()) continue;
            try { if (!d.hasMech()) continue; } catch (IllegalStateException e) { continue; }
            List<ServerPlayer> in = new ArrayList<>();
            for (ServerPlayer p : ps)
                if (p.m_6084_() && !p.m_5833_() && rec.contains(p.m_20185_(), p.m_20186_(), p.m_20189_(), 24)) in.add(p);
            if (in.isEmpty()) continue;
            run(sl, data, rec, d, in, tk);
        }
    }

    private static void run(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> in, long tk) {
        platforms(sl, rec, d, in, tk);
        if (tk % 5 == 0) { steps(sl, rec, d, in, tk); seals(sl, rec, d, in); }
        if (tk % 2 == 0) { beams(sl, rec, d, in, tk); jets(sl, rec, d, in, tk); geysers(sl, rec, d, in, tk); }
        if (tk % 4 == 0) crumbles(sl, rec, d, in, tk);
        if (tk % 20 == 0) fires(sl, rec, d, in);
        if (tk % 1200 == 0 && HIT_CD.size() > 64) HIT_CD.values().removeIf(t -> t < tk);
        if (tk % 10 == 0) puzzles(sl, data, rec, d, in);
    }

    private static boolean nearAny(List<ServerPlayer> in, double x, double y, double z, double r, double dy) {
        for (ServerPlayer p : in) {
            double ddx = p.m_20185_() - x, ddz = p.m_20189_() - z;
            if (ddx * ddx + ddz * ddz <= r * r && Math.abs(p.m_20186_() - y) <= dy) return true;
        }
        return false;
    }

    private static void fill(ServerLevel sl, DungeonData.Rec rec, Mark m, BlockState st) {
        if (st == null) return;
        for (int x = 0; x < m.sx; x++)
            for (int y = 0; y < m.sy; y++)
                for (int z = 0; z < m.sz; z++) sl.m_7731_(new BlockPos(rec.x + m.x + x, rec.y + m.y + y, rec.z + m.z + z), st, 3);
    }

    // ---------------------------------------------------------------- ступени над провалом
    private static void steps(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> in, long tk) {
        for (Mark m : d.marks("steps")) {
            String key = rec.key + "|" + mirKey(m);
            double cx = rec.x + m.x + m.sx / 2.0, cy = rec.y + m.y + 1, cz = rec.z + m.z + m.sz / 2.0;
            Boolean was = STEPS.get(key);
            boolean want = nearAny(in, cx, cy, cz, was != null && was ? STEP_HIDE : STEP_SHOW, 6);
            if (was == null || was != want) {   // первый проход в сессии ставит состояние безусловно: от прошлого запуска могли остаться блоки
                STEPS.put(key, want);
                fill(sl, rec, m, want ? Dungeons.state(m.a) : Fx.state("air"));
                if (was != null) {
                    Fx.send(sl, Fx.particle("end_rod"), cx, cy, cz, want ? 12 : 6, m.sx / 2.0, 0.1, m.sz / 2.0, 0.02);
                    if (want) Fx.sound(sl, cx, cy, cz, "block.amethyst_block.chime", 1.0F, 1.2F);
                }
            } else if (!want && tk % 20 == 0 && nearAny(in, cx, cy, cz, 9, 6)) {
                Fx.send(sl, Fx.particle("end_rod"), cx, cy + 0.2, cz, 2, m.sx / 2.0 - 0.2, 0.05, m.sz / 2.0 - 0.2, 0.0);   // призрачный контур ступени
            }
        }
    }

    // ---------------------------------------------------------------- плитки-ловушки и лучи
    private static void beams(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> in, long tk) {
        boolean peaceful = sl.m_46791_() == Difficulty.valueOf("PEACEFUL");
        for (Mark m : d.marks("beams")) {
            String key = rec.key + "|" + mirKey(m);
            String[] o = m.a.split(",");
            double cx = rec.x + m.x + Integer.parseInt(o[0]) + 0.5, cy = rec.y + m.y + Integer.parseInt(o[1]) + 0.5, cz = rec.z + m.z + Integer.parseInt(o[2]) + 0.5;
            double tx = rec.x + m.x + 0.5, ty = rec.y + m.y + 1.2, tz = rec.z + m.z + 0.5;
            Long t0 = BEAM_AT.get(key);
            if (t0 != null) {
                if (tk - t0 >= BEAM_DELAY) {
                    BEAM_AT.remove(key); BEAM_COOL.put(key, tk + BEAM_COOLDOWN);
                    fireBeam(sl, in, peaceful, cx, cy, cz, tx, ty, tz);
                } else {
                    Fx.send(sl, Fx.particle("end_rod"), cx, cy, cz, 3, 0.25, 0.25, 0.25, 0.02);   // кристалл заряжается
                    Fx.send(sl, Fx.particle("crit"), tx, rec.y + m.y + 1.1, tz, 4, 0.3, 0.05, 0.3, 0.02);
                }
                continue;
            }
            if (BEAM_COOL.getOrDefault(key, 0L) > tk) continue;
            boolean stepped = false;
            for (ServerPlayer p : in)
                if (onPlate(m, p.m_20185_() - rec.x, p.m_20186_() - rec.y, p.m_20189_() - rec.z)) { stepped = true; break; }
            if (stepped) {
                BEAM_AT.put(key, tk);
                Fx.sound(sl, tx, ty, tz, "block.amethyst_block.resonate", 1.5F, 1.8F);
            } else if (tk % 20 == 0 && nearAny(in, tx, rec.y + m.y + 1, tz, 14, 6)) {
                Fx.send(sl, Fx.particle("end_rod"), tx, rec.y + m.y + 1.1, tz, 1, 0.3, 0.0, 0.3, 0.0);   // плитка подсвечена
            }
        }
    }

    private static void fireBeam(ServerLevel sl, List<ServerPlayer> in, boolean peaceful, double cx, double cy, double cz, double tx, double ty, double tz) {
        double len = Math.sqrt((tx - cx) * (tx - cx) + (ty - cy) * (ty - cy) + (tz - cz) * (tz - cz));
        int n = Math.max(2, (int) Math.ceil(len / 0.4));
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n;
            Fx.send(sl, Fx.particle("end_rod"), cx + (tx - cx) * f, cy + (ty - cy) * f, cz + (tz - cz) * f, 1, 0.0, 0.0, 0.0, 0.0);
        }
        Fx.send(sl, Fx.particle("crit"), tx, ty, tz, 10, 0.3, 0.3, 0.3, 0.1);
        Fx.sound(sl, tx, ty, tz, "block.amethyst_cluster.break", 1.5F, 0.8F);
        if (peaceful) return;   // мирный режим: данж спит, ловушки не бьют
        for (ServerPlayer p : in)
            if (segDist(p.m_20185_(), p.m_20186_() + 0.9, p.m_20189_(), cx, cy, cz, tx, ty, tz) <= BEAM_HIT)
                Cmd.run(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "damage " + p.m_20148_() + " 5 minecraft:magic");
    }

    // ---------------------------------------------------------------- струи огня (Святилище)
    private static void jets(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> in, long tk) {
        List<Mark> ms = d.marks("jets");
        if (ms.isEmpty()) return;
        boolean peaceful = sl.m_46791_() == Difficulty.valueOf("PEACEFUL");
        for (Mark m : ms) {
            int[] c = jetCfg(m.a);
            if (c == null) continue;
            double nx = rec.x + m.x + 0.5, ny = rec.y + m.y + 0.5, nz = rec.z + m.z + 0.5;
            if (!nearAny(in, nx, ny, nz, 48, 24)) continue;
            int ph = cycle(tk, c[3], JET_WARN, JET_BURST, c[4]);
            if (ph == 0) continue;
            long t = Math.floorMod(tk + c[4], (long) c[3]);
            if (ph == 1) {   // «вдох»: дымок у сопла и тихий треск
                Fx.send(sl, Fx.particle("smoke"), nx + c[0] * 0.6, ny, nz + c[1] * 0.6, 2, 0.15, 0.15, 0.15, 0.01);
                if (t - (c[3] - JET_BURST - JET_WARN) < 2) Fx.sound(sl, nx, ny, nz, "block.fire.ambient", 1.3F, 0.5F);
                continue;
            }
            if (t - (c[3] - JET_BURST) < 2) Fx.sound(sl, nx, ny, nz, "entity.blaze.shoot", 1.2F, 0.6F);
            int stepLen = c[2] > 8 ? 2 : 1;
            for (int i = 1; i <= c[2]; i += stepLen)
                Fx.send(sl, Fx.particle("flame"), nx + c[0] * i, ny, nz + c[1] * i, 2, 0.2, 0.25, 0.2, 0.03);
            if (peaceful) continue;   // мирный режим: данж спит, ловушки не бьют
            for (ServerPlayer p : in) {
                if (!jetHit(p.m_20185_() - nx, p.m_20186_() - ny, p.m_20189_() - nz, c[0], c[1], c[2])) continue;
                String key = "j|" + p.m_20148_() + "|" + mirKey(m);
                if (HIT_CD.getOrDefault(key, 0L) > tk) continue;
                HIT_CD.put(key, tk + 12);
                Cmd.run(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "damage " + p.m_20148_() + " 4 minecraft:in_fire");
                p.m_20254_(3);
            }
        }
    }

    // ---------------------------------------------------------------- гейзеры
    private static void geysers(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> in, long tk) {
        List<Mark> ms = d.marks("geyser");
        if (ms.isEmpty() || sl.m_46791_() == Difficulty.valueOf("PEACEFUL")) return;
        for (Mark m : ms) {
            int[] c = geyserCfg(m.a);
            if (c == null || c[2] < GEYSER_WARN + GEYSER_BURST + 1) continue;
            double vx = rec.x + m.x + 0.5, vy = rec.y + m.y + 1.0, vz = rec.z + m.z + 0.5;
            if (!nearAny(in, vx, vy, vz, 40, 20)) continue;
            int off = Math.floorMod(m.x * 7 + m.z * 13, c[2]);
            int ph = cycle(tk, c[2], GEYSER_WARN, GEYSER_BURST, off);
            if (ph == 0) continue;
            long t = Math.floorMod(tk + off, (long) c[2]);
            if (ph == 1) {   // бульканье
                Fx.send(sl, Fx.particle("lava"), vx, vy + 0.1, vz, 1, 0.3, 0.05, 0.3, 0.0);
                if (t - (c[2] - GEYSER_BURST - GEYSER_WARN) < 2) Fx.sound(sl, vx, vy, vz, "block.lava.pop", 1.2F, 0.6F);
                continue;
            }
            if (t - (c[2] - GEYSER_BURST) < 2) Fx.sound(sl, vx, vy, vz, "entity.blaze.shoot", 1.0F, 0.5F);
            for (double h = 0; h <= 5; h += 1.25) Fx.send(sl, Fx.particle("flame"), vx, vy + h, vz, 3, 0.25, 0.3, 0.25, 0.04);
            for (ServerPlayer p : in) {
                if (!inGeyser(p.m_20185_() - vx, p.m_20186_() - vy, p.m_20189_() - vz)) continue;
                String key = "g|" + p.m_20148_() + "|" + mirKey(m);
                if (HIT_CD.getOrDefault(key, 0L) > tk) continue;
                HIT_CD.put(key, tk + GEYSER_BURST + 40);
                p.m_20256_(new Vec3(c[0] * 0.42, 1.05, c[1] * 0.42));
                p.f_19864_ = true; p.f_19789_ = 0.0F;
                Cmd.run(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "damage " + p.m_20148_() + " 2 minecraft:in_fire");
                p.m_20254_(2);
            }
        }
    }

    // ---------------------------------------------------------------- осыпающиеся платформы
    private static void crumbles(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> in, long tk) {
        for (Mark m : d.marks("crumble")) {
            double cx = rec.x + m.x + m.sx / 2.0, cy = rec.y + m.y, cz = rec.z + m.z + m.sz / 2.0;
            if (!nearAny(in, cx, cy, cz, 48, 40)) continue;
            BlockPos origin = new BlockPos(rec.x + m.x, rec.y + m.y, rec.z + m.z);
            if (!sl.m_46805_(origin)) continue;
            String key = rec.key + "|" + mirKey(m);
            Crumb c = CRUMB.get(key);
            if (c == null) {   // первый проход в сессии: состояние читается из мира (платформа могла остаться сломанной с прошлого раза)
                c = new Crumb();
                c.st = sl.m_8055_(origin).m_60795_() ? 2 : 0;
                c.since = tk;
                CRUMB.put(key, c);
            }
            boolean stepped = false, blocked = false;
            for (ServerPlayer p : in) {
                double lx = p.m_20185_() - rec.x, ly = p.m_20186_() - rec.y, lz = p.m_20189_() - rec.z;
                if (onPad(m, lx, ly, lz)) stepped = true;
                if (inPad(m, lx, ly, lz)) blocked = true;
            }
            if (c.st == 2 && !blocked && tk - c.since >= CRUMBLE_BACK) {   // пора возвращаться: моб в клетках платформы тоже мешает (иначе замуруем)
                AABB box = new AABB(rec.x + m.x - 0.1, rec.y + m.y + 0.01, rec.z + m.z - 0.1, rec.x + m.x + m.sx + 0.1, rec.y + m.y + m.sy - 0.01, rec.z + m.z + m.sz + 0.1);
                if (!sl.m_6249_(null, box, e -> e instanceof LivingEntity && e.m_6084_() && !e.m_5833_()).isEmpty()) blocked = true;
            }
            int next = crumbNext(c.st, c.since, tk, stepped, blocked);
            if (next == c.st) {
                if (c.st == 1 && tk % 8 == 0) Fx.send(sl, Fx.particle("smoke"), cx, cy + 1.0, cz, 6, m.sx / 2.0, 0.1, m.sz / 2.0, 0.01);
                continue;
            }
            c.st = next; c.since = tk;
            if (next == 1) {
                Fx.sound(sl, cx, cy, cz, "block.basalt.break", 0.8F, 0.6F);
                Fx.send(sl, Fx.particle("smoke"), cx, cy + 1.0, cz, 10, m.sx / 2.0, 0.1, m.sz / 2.0, 0.02);
            } else if (next == 2) {
                fill(sl, rec, m, Fx.state("air"));
                Fx.sound(sl, cx, cy, cz, "block.basalt.break", 1.4F, 0.5F);
                Fx.send(sl, Fx.particle("large_smoke"), cx, cy + 0.5, cz, 14, m.sx / 2.0, 0.3, m.sz / 2.0, 0.03);
            } else {
                fill(sl, rec, m, Dungeons.state(m.a));
                Fx.sound(sl, cx, cy, cz, "block.basalt.place", 1.0F, 0.8F);
                Fx.send(sl, Fx.particle("ash"), cx, cy + 1.0, cz, 12, m.sx / 2.0, 0.3, m.sz / 2.0, 0.02);
            }
        }
    }

    // ---------------------------------------------------------------- «Четыре жаровни» (Святилище, 2б)
    /** Метки одной роли головоломки id, разложенные по номеру n (0..size-1); пропуски - null. */
    static Mark[] fireSlots(Dungeons.Def d, String id, String role, int size) {
        Mark[] out = new Mark[size];
        for (Mark m : d.marks("braziers")) {
            String[] a = m.a.split(",");
            if (a.length != 3 || !a[0].equals(id) || !a[1].equals(role)) continue;
            int n = Integer.parseInt(a[2].trim());
            if (n >= 0 && n < size) out[n] = m;
        }
        return out;
    }

    static Set<String> fireIds(Dungeons.Def d) {
        Set<String> out = new LinkedHashSet<>();
        for (Mark m : d.marks("braziers")) out.add(m.a.split(",")[0]);
        return out;
    }

    private static boolean fireLitAt(ServerLevel sl, BlockPos pos) {
        BlockState st = sl.m_8055_(pos);
        return st.m_60734_() == Fx.block("campfire") && st.equals(Dungeons.setProp(st, "lit", "true"));
    }

    private static BlockPos firePos(DungeonData.Rec rec, Mark m) { return new BlockPos(rec.x + m.x, rec.y + m.y, rec.z + m.z); }

    private static boolean[] fireLit(ServerLevel sl, DungeonData.Rec rec, Mark[] bs) {
        boolean[] lit = new boolean[4];
        for (int i = 0; i < 4; i++) lit[i] = fireLitAt(sl, firePos(rec, bs[i]));
        return lit;
    }

    private static void fireSet(ServerLevel sl, DungeonData.Rec rec, Mark m, boolean on) {
        BlockPos pos = firePos(rec, m);
        BlockState st = sl.m_8055_(pos);
        if (st.m_60734_() != Fx.block("campfire")) return;
        sl.m_7731_(pos, Dungeons.setProp(st, "lit", on ? "true" : "false"), 3);
    }

    /** Гасит все жаровни головоломки (ошибка в порядке или нарушенное состояние). */
    private static void fireOut(ServerLevel sl, DungeonData.Rec rec, Mark[] bs) {
        for (Mark m : bs) {
            if (!fireLitAt(sl, firePos(rec, m))) continue;
            fireSet(sl, rec, m, false);
            Fx.send(sl, Fx.particle("large_smoke"), rec.x + m.x + 0.5, rec.y + m.y + 0.8, rec.z + m.z + 0.5, 8, 0.2, 0.2, 0.2, 0.02);
        }
    }

    /** Панель фрески n показывает узор жаровни, которую зажигают n-й по порядку; рисуется движком, пока рядом игрок, и только если в мире не так. */
    private static void fireFresco(ServerLevel sl, DungeonData.Rec rec, Mark f, int brazier) {
        if (f == null || !sl.m_46805_(firePos(rec, f))) return;
        boolean alongX = f.sx >= f.sz;
        for (int ix = 0; ix < f.sx; ix++)
            for (int iy = 0; iy < f.sy; iy++)
                for (int iz = 0; iz < f.sz; iz++) {
                    BlockState want = Fx.state(fireBlock(brazier, alongX ? ix : iz, f.sy - 1 - iy));
                    BlockPos pos = new BlockPos(rec.x + f.x + ix, rec.y + f.y + iy, rec.z + f.z + iz);
                    if (want != null && !sl.m_8055_(pos).equals(want)) sl.m_7731_(pos, want, 3);
                }
    }

    /** Раз в секунду, пока рядом игрок: фреска расписана, потухшие после решения жаровни горят, при нарушенном состоянии всё гаснет. */
    private static void fires(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> in) {
        for (String id : fireIds(d)) {
            Mark[] bs = fireSlots(d, id, "b", 4), fs = fireSlots(d, id, "f", 4);
            if (bs[0] == null || bs[1] == null || bs[2] == null || bs[3] == null) continue;
            if (!nearAny(in, rec.x + bs[0].x, rec.y + bs[0].y, rec.z + bs[0].z, 48, 24)) continue;
            if (!sl.m_46805_(firePos(rec, bs[0])) || !sl.m_46805_(firePos(rec, bs[3]))) continue;
            int[] order = fireOrder(fireSeed(rec.x, rec.z));
            for (int k = 0; k < 4; k++) fireFresco(sl, rec, fs[k], order[k]);
            boolean[] lit = fireLit(sl, rec, bs);
            if (rec.opened.contains(id)) {
                for (int i = 0; i < 4; i++) if (!lit[i]) fireSet(sl, rec, bs[i], true);
            } else if (!firePrefix(order, lit)) {
                fireOut(sl, rec, bs);
                for (ServerPlayer p : in)
                    if (nearAny(java.util.Collections.singletonList(p), rec.x + bs[0].x, rec.y + bs[0].y, rec.z + bs[0].z, 48, 24)) p.m_5661_(Component.m_237115_("explosionwand.dungeon.fires_wrong"), true);
                fireWave(sl, rec, d, id);   // зажжено не кликом (стрела, огненный шар): ошибка наказывается так же
            }
        }
    }

    private static void fireClick(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, ServerPlayer p, String id, int n) {
        Mark[] bs = fireSlots(d, id, "b", 4);
        if (n < 0 || n > 3 || bs[0] == null || bs[1] == null || bs[2] == null || bs[3] == null) return;
        Mark m = bs[n];
        double bx = rec.x + m.x + 0.5, by = rec.y + m.y + 0.5, bz = rec.z + m.z + 0.5;
        if (rec.opened.contains(id)) {   // решено: костры горят насовсем
            Fx.sound(sl, bx, by, bz, "block.fire.ambient", 1.0F, 0.8F);
            return;
        }
        int[] order = fireOrder(fireSeed(rec.x, rec.z));
        boolean[] lit = fireLit(sl, rec, bs);
        if (!firePrefix(order, lit)) { fireOut(sl, rec, bs); lit = new boolean[4]; }
        int res = fireStep(order, lit, n);
        if (res == FIRE_NONE) {
            Fx.sound(sl, bx, by, bz, "block.fire.ambient", 1.0F, 0.8F);
            return;
        }
        if (res == FIRE_WRONG) {
            fireOut(sl, rec, bs);
            Fx.sound(sl, bx, by, bz, "block.fire.extinguish", 1.5F, 0.6F);
            Fx.send(sl, Fx.particle("smoke"), bx, by + 0.4, bz, 20, 0.3, 0.3, 0.3, 0.04);
            p.m_5661_(Component.m_237115_("explosionwand.dungeon.fires_wrong"), true);
            fireWave(sl, rec, d, id);
            return;
        }
        fireSet(sl, rec, m, true);
        Fx.send(sl, Fx.particle("flame"), bx, by + 0.4, bz, 18, 0.2, 0.3, 0.2, 0.03);
        Fx.sound(sl, bx, by, bz, "item.firecharge.use", 1.2F, 0.8F + 0.15F * (res == FIRE_DONE ? 3 : 0));
        if (res == FIRE_DONE) {
            for (Entity e : Cmd.tagged(sl, bx, by, bz, 60, fireWaveTag(rec))) RiftMobs.remove(e);   // волна не должна мешать спуску
            List<ServerPlayer> one = new ArrayList<>();
            one.add(p);
            openId(sl, data, rec, d, id, one, "explosionwand.dungeon.fires_solved");
        }
    }

    /** Ошибка: из тьмы выходят мобы Святилища (waveMob) в точках волны; пока прежняя волна жива, новая не зовётся. */
    private static void fireWave(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, String id) {
        if (sl.m_46791_() == Difficulty.valueOf("PEACEFUL")) return;
        Mark[] ws = fireSlots(d, id, "w", 16);
        String tag = fireWaveTag(rec);
        int i = 0;
        for (Mark w : ws) {
            if (w == null) continue;
            double x = rec.x + w.x + 0.5, y = rec.y + w.y, z = rec.z + w.z + 0.5;
            if (i == 0 && Cmd.tagged(sl, x, y, z, 40, tag).size() >= WAVE_CAP) return;
            String kind = waveMob(i++);
            RiftMobs.spawnKind(sl, RiftModels.Kind.byId(kind), x, y, z, tag, "\"ewdw_min\"");   // _min: материалов с волны нет (не ферма), и после перезапуска тоже
        }
    }

    // ---------------------------------------------------------------- головоломка зеркал
    private static void puzzles(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> in) {
        for (Puzzle pz : puzzles(d).values()) {
            if (pz.srcs.isEmpty()) continue;
            Mark s0 = pz.srcs.get(0);
            if (!nearAny(in, rec.x + s0.x, rec.y + s0.y, rec.z + s0.z, 40, 12)) continue;
            Trace tr = new Trace();
            boolean solved = solve(rec, pz, (x, y, z) -> !sl.m_8055_(new BlockPos(rec.x + x, rec.y + y, rec.z + z)).m_60795_(), tr);
            if (solved && !rec.opened.contains(pz.id)) openId(sl, data, rec, d, pz.id, in, true);
            String particle = rec.opened.contains(pz.id) ? "electric_spark" : "end_rod";
            for (int[] c : tr.cells) Fx.send(sl, Fx.particle(particle), rec.x + c[0] + 0.5, rec.y + c[1] + 0.5, rec.z + c[2] + 0.5, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    // ---------------------------------------------------------------- секреты: рычаг, решённая головоломка
    /** Открывает все секреты с id (стена -> воздух); false, если уже были открыты. */
    static boolean openId(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, String id, List<ServerPlayer> in, boolean puzzle) {
        return openId(sl, data, rec, d, id, in, puzzle ? "explosionwand.dungeon.solved" : "explosionwand.dungeon.lever");
    }

    /** То же с произвольным ключом сообщения (колокол Святилища: explosionwand.dungeon.bell). */
    static boolean openId(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, String id, List<ServerPlayer> in, String msgKey) {
        if (!rec.opened.add(id)) return false;
        data.m_77762_();
        BlockState air = Fx.state("air");
        for (Mark m : d.marks("secret")) {
            if (!m.a.equals(id)) continue;
            fill(sl, rec, m, air);
            double cx = rec.x + m.x + m.sx / 2.0, cy = rec.y + m.y + m.sy / 2.0, cz = rec.z + m.z + m.sz / 2.0;
            Fx.send(sl, Fx.particle("end_rod"), cx, cy, cz, 30, m.sx / 2.0, m.sy / 2.0, m.sz / 2.0, 0.06);
            Fx.sound(sl, cx, cy, cz, "block.amethyst_block.break", 2.0F, 0.6F);
            Fx.sound(sl, cx, cy, cz, "block.beacon.activate", 1.5F, 1.4F);
        }
        for (ServerPlayer p : in) p.m_5661_(Component.m_237115_(msgKey), true);
        return true;
    }

    /** ПКМ по блоку данжа: рычаг или зеркало. true — клик занят механикой (событие надо отменить). */
    static boolean onClick(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, ServerPlayer p, int rx, int ry, int rz, boolean main) {
        for (Mark m : d.marks("bell")) {
            if (m.x != rx || m.y != ry || m.z != rz) continue;
            if (!main) return true;
            double bx = rec.x + rx + 0.5, by = rec.y + ry + 0.5, bz = rec.z + rz + 0.5;
            Fx.sound(sl, bx, by, bz, "block.bell.use", 6.0F, 0.6F);
            Fx.send(sl, Fx.particle("note"), bx, by + 0.6, bz, 6, 0.6, 0.3, 0.6, 1.0);
            List<ServerPlayer> one = new ArrayList<>();
            one.add(p);
            openId(sl, data, rec, d, m.a, one, "explosionwand.dungeon.bell");
            return true;
        }
        for (Mark m : d.marks("braziers")) {
            String[] a = m.a.split(",");
            if (a.length != 3 || !a[1].equals("b") || m.x != rx || m.y != ry || m.z != rz) continue;
            if (!main) return true;
            fireClick(sl, data, rec, d, p, a[0], Integer.parseInt(a[2].trim()));
            return true;
        }
        for (Mark m : d.marks("lever")) {
            if (m.x != rx || m.y != ry || m.z != rz) continue;
            if (!main) return true;
            BlockPos pos = new BlockPos(rec.x + rx, rec.y + ry, rec.z + rz);
            sl.m_7731_(pos, Dungeons.setProp(sl.m_8055_(pos), "powered", "true"), 3);
            Fx.sound(sl, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), "block.lever.click", 1.0F, 0.7F);
            List<ServerPlayer> one = new ArrayList<>();
            one.add(p);
            openId(sl, data, rec, d, m.a, one, false);
            return true;
        }
        for (Puzzle pz : puzzles(d).values()) {
            for (Mark m : pz.mirs) {
                if (m.x != rx || m.y != ry || m.z != rz) continue;
                if (!main) return true;
                BlockPos pos = new BlockPos(rec.x + rx, rec.y + ry, rec.z + rz);
                if (rec.opened.contains(pz.id)) {   // решено: зеркала застыли
                    Fx.sound(sl, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), "block.amethyst_block.hit", 1.0F, 0.5F);
                    return true;
                }
                int nd = turn(dirNow(rec, m));
                rec.mirrors.put(mirKey(m), nd);
                data.m_77762_();
                sl.m_7731_(pos, Dungeons.setProp(sl.m_8055_(pos), "facing", FACING[nd]), 3);
                Fx.sound(sl, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), "block.amethyst_block.chime", 1.2F, 0.8F + 0.2F * nd);
                Fx.send(sl, Fx.particle("end_rod"), pos.m_123341_() + 0.5 + DX[nd] * 0.6, pos.m_123342_() + 0.5, pos.m_123343_() + 0.5 + DZ[nd] * 0.6, 4, 0.1, 0.1, 0.1, 0.02);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- движущиеся платформы
    private static void platforms(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> in, long tk) {
        for (Mark m : d.marks("platform")) {
            String key = rec.key + "|" + mirKey(m);
            Plat st = PLAT.get(key);
            int[] cfg = st != null ? st.cfg : platCfg(m.a);
            BlockState blk = st != null ? st.blk : (cfg == null ? null : Dungeons.state(platBlock(m.a)));
            if (blk == null || cfg[2] < 1) continue;
            double cx = rec.x + m.x + m.sx / 2.0 + cfg[0] * cfg[2] / 2.0, cz = rec.z + m.z + m.sz / 2.0 + cfg[1] * cfg[2] / 2.0;
            if (!nearAny(in, cx, rec.y + m.y, cz, 32 + cfg[2] / 2.0, 32)) continue;   // рядом никого: стоит где стоит
            if (st == null) {   // первый проход в сессии: дорожка очищается (могла остаться платформа от прошлого запуска), платформа в начало
                st = new Plat();
                st.cfg = cfg; st.blk = blk;
                PLAT.put(key, st);
                int x1 = Math.min(0, cfg[0] * cfg[2]), x2 = Math.max(0, cfg[0] * cfg[2]), z1 = Math.min(0, cfg[1] * cfg[2]), z2 = Math.max(0, cfg[1] * cfg[2]);
                BlockState air = Fx.state("air");
                for (int x = x1; x < x2 + m.sx; x++)
                    for (int y = 0; y < m.sy; y++)
                        for (int z = z1; z < z2 + m.sz; z++) sl.m_7731_(new BlockPos(rec.x + m.x + x, rec.y + m.y + y, rec.z + m.z + z), air, 2);
                place(sl, rec, m, 0, 0, blk, null);
                continue;
            }
            if (tk % PLAT_PERIOD != 0) continue;
            int from = st.off, to = advance(st, cfg[2]);
            if (to < 0) continue;
            place(sl, rec, m, from, to, blk, cfg);
            if (st.pause > 0) Fx.sound(sl, cx, rec.y + m.y, cz, "block.piston.extend", 0.6F, 0.6F);
        }
    }

    /**
     * Платформа сдвигается с from на to (на 1 блок): новые блоки спереди ставятся до того, как уберутся задние, игроки на ней
     * переносятся на тот же блок вперёд. cfg == null — просто поставить платформу в положение to.
     */
    private static void place(ServerLevel sl, DungeonData.Rec rec, Mark m, int from, int to, BlockState blk, int[] cfg) {
        int dx = cfg == null ? 0 : cfg[0], dz = cfg == null ? 0 : cfg[1];
        int x0 = rec.x + m.x + dx * from, z0 = rec.z + m.z + dz * from, x1 = rec.x + m.x + dx * to, z1 = rec.z + m.z + dz * to, y = rec.y + m.y;
        for (int x = x1; x < x1 + m.sx; x++)
            for (int z = z1; z < z1 + m.sz; z++)
                for (int yy = 0; yy < m.sy; yy++)
                    if (cfg == null || x < x0 || x >= x0 + m.sx || z < z0 || z >= z0 + m.sz) sl.m_7731_(new BlockPos(x, y + yy, z), blk, 3);
        if (cfg == null) return;
        BlockState air = Fx.state("air");
        for (int x = x0; x < x0 + m.sx; x++)
            for (int z = z0; z < z0 + m.sz; z++)
                for (int yy = 0; yy < m.sy; yy++)
                    if (x < x1 || x >= x1 + m.sx || z < z1 || z >= z1 + m.sz) sl.m_7731_(new BlockPos(x, y + yy, z), air, 3);
        double top = y + m.sy, sgn = to - from;
        for (ServerPlayer p : sl.m_6907_()) {
            double px = p.m_20185_(), py = p.m_20186_(), pz = p.m_20189_();
            // центр игрока строго над блоками платформы: стоящий на берегу вплотную не сдвигается
            if (!p.m_6084_() || px < x0 || px >= x0 + m.sx || pz < z0 || pz >= z0 + m.sz || py < top - 0.3 || py > top + 0.6) continue;
            p.m_8999_(sl, px + dx * sgn, py, pz + dz * sgn, p.m_146908_(), p.m_146909_());
            p.f_19789_ = 0.0F;
        }
    }

    // ---------------------------------------------------------------- дверь логова
    private static void seals(ServerLevel sl, DungeonData.Rec rec, Dungeons.Def d, List<ServerPlayer> in) {
        for (Mark m : d.marks("seal")) {
            String[] a = m.a.split(",");
            int hid = Integer.parseInt(a[0].trim());
            Dungeons.Hall hall = d.hall(hid);
            boolean want = sealWanted(rec.hallsSpawned, rec.hallsCleared, hid, hall != null && Dungeons.hallOccupied(rec, hall, in));
            boolean closed = !sl.m_8055_(new BlockPos(rec.x + m.x, rec.y + m.y, rec.z + m.z)).m_60795_();
            if (want == closed) continue;
            double cx = rec.x + m.x + m.sx / 2.0, cy = rec.y + m.y + m.sy / 2.0, cz = rec.z + m.z + m.sz / 2.0;
            if (want) {
                boolean blocked = false;   // не замуровывать игрока в проёме
                for (ServerPlayer p : in)
                    if (p.m_20185_() > rec.x + m.x - 0.7 && p.m_20185_() < rec.x + m.x + m.sx + 0.7 && p.m_20189_() > rec.z + m.z - 0.7 && p.m_20189_() < rec.z + m.z + m.sz + 0.7
                            && p.m_20186_() > rec.y + m.y - 1 && p.m_20186_() < rec.y + m.y + m.sy + 1) blocked = true;
                if (blocked) continue;
            }
            fill(sl, rec, m, want ? Dungeons.state(a[1]) : Fx.state("air"));
            Fx.send(sl, Fx.particle("end_rod"), cx, cy, cz, 25, m.sx / 2.0, m.sy / 2.0, m.sz / 2.0, 0.05);
            Fx.sound(sl, cx, cy, cz, want ? "block.iron_door.close" : "block.iron_door.open", 1.5F, 0.6F);
            for (ServerPlayer p : in)
                if (nearAny(List.of(p), cx, cy, cz, 40, 20)) p.m_5661_(Component.m_237115_(want ? "explosionwand.dungeon.seal_shut" : "explosionwand.dungeon.seal_open"), true);
        }
    }
}
