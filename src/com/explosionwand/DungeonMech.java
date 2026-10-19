package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.state.BlockState;

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
 * Состояние: DungeonData.Rec.opened (открытые секреты) и Rec.mirrors (повороты зеркал) — в NBT; всё остальное восстанавливается из мира.
 */
final class DungeonMech {
    private DungeonMech() {}

    static final String[] TYPES = {"steps", "beams", "mirror", "platform", "lever", "secret", "seal"};
    static final String[] FACING = {"north", "east", "south", "west"};
    static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
    static final int STEP_SHOW = 4, STEP_HIDE = 10, BEAM_DELAY = 10, BEAM_COOLDOWN = 40, PLAT_PERIOD = 8, PLAT_PAUSE = 3, MAX_BEAM = 80;
    static final double BEAM_HIT = 1.1;

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

    // ================================================================ состояние в памяти (кэши, восстанавливаются из мира)
    static final Map<String, Boolean> STEPS = new HashMap<>();
    static final Map<String, Plat> PLAT = new HashMap<>();
    static final Map<String, Long> BEAM_AT = new HashMap<>(), BEAM_COOL = new HashMap<>();

    static void clear() { STEPS.clear(); PLAT.clear(); BEAM_AT.clear(); BEAM_COOL.clear(); }

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
        if (tk % 2 == 0) beams(sl, rec, d, in, tk);
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
        for (ServerPlayer p : in) p.m_5661_(Component.m_237115_(puzzle ? "explosionwand.dungeon.solved" : "explosionwand.dungeon.lever"), true);
        return true;
    }

    /** ПКМ по блоку данжа: рычаг или зеркало. true — клик занят механикой (событие надо отменить). */
    static boolean onClick(ServerLevel sl, DungeonData data, DungeonData.Rec rec, Dungeons.Def d, ServerPlayer p, int rx, int ry, int rz, boolean main) {
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
