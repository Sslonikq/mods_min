package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * Лабиринт Некто — куда ведёт портал Палки Некто.
 * Каждому вошедшему строится свой лабиринт 17×17 клеток (коридоры шириной 2, потолок, стены не ломаются).
 * Через 10 секунд появляется Некто и идёт по кратчайшему пути за тобой; со временем быстрее, иногда
 * впадает в ярость. Три удара — смерть. Выход — в самой дальней клетке. Иногда накатывают слепота,
 * тьма, тошнота; раз в 45 секунд огоньки душ подсказывают несколько шагов к выходу.
 */
final class Maze {
    static final int CELLS = 17, CS = 3, SIZE = CELLS * CS + 1, Y0 = 64, H = 4;
    static final Random RNG = Fx.RNG;
    static final Map<UUID, Session> SESSIONS = new HashMap<>();
    static int slotSeq = 0;
    static DustParticleOptions RED, BLACK;
    static final String[] PARTS = {"black_concrete", "black_concrete", "black_concrete", "black_concrete", "black_concrete", "black_concrete", "redstone_block", "redstone_block"};

    static void init() {
        if (RED != null) return;
        RED = new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.05F), 0.8F);
        BLACK = new DustParticleOptions(new Vector3f(0.02F, 0.02F, 0.02F), 2.2F);
    }

    static final class Session {
        final ServerPlayer p; final ServerLevel level; final int ox, oz; final String tag;
        boolean[][] open = new boolean[SIZE][SIZE];
        int[][] toPlayer, toExit;
        int sx, sz, ex, ez, t = 0;
        boolean monster = false; double mx, mz, yaw = 0, speed = 0.125, walk = 0;
        Entity host; final String[] uuid = new String[PARTS.length];
        int hitCd = 0, pause = 0, rageT = -1, rageCd = 600, eventCd = 400, hintCd = 600, hintT = -1;
        Session(ServerPlayer p, ServerLevel level, int slot) { this.p = p; this.level = level; this.ox = slot * 128; this.oz = 0; this.tag = "ewmz" + slot; }
    }

    // ================= Вход =================
    /** Строит лабиринт для игрока и возвращает точку старта (мировые координаты). */
    static Vec3 create(ServerLevel level, Entity e) {
        init();
        if (!(e instanceof ServerPlayer p)) return new Vec3(0.5, Y0, 0.5);
        Session old = SESSIONS.remove(p.m_20148_());
        if (old != null) end(old, false);
        Session s = new Session(p, level, (slotSeq++) % 48);
        generate(s);
        build(s);
        SESSIONS.put(p.m_20148_(), s);
        return new Vec3(s.ox + s.sx + 1.0, Y0, s.oz + s.sz + 1.0);
    }

    /** Recursive backtracker по клеткам + ~10% лишних проходов (петли). */
    static void generate(Session s) {
        boolean[][] seen = new boolean[CELLS][CELLS];
        Deque<int[]> st = new ArrayDeque<>();
        st.push(new int[]{0, 0}); seen[0][0] = true; openCell(s, 0, 0);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!st.isEmpty()) {
            int[] c = st.peek();
            List<int[]> nb = new ArrayList<>();
            for (int[] d : dirs) { int nx = c[0] + d[0], nz = c[1] + d[1]; if (nx >= 0 && nz >= 0 && nx < CELLS && nz < CELLS && !seen[nx][nz]) nb.add(new int[]{nx, nz}); }
            if (nb.isEmpty()) { st.pop(); continue; }
            int[] n = nb.get(RNG.nextInt(nb.size()));
            seen[n[0]][n[1]] = true; openCell(s, n[0], n[1]); openBetween(s, c[0], c[1], n[0], n[1]);
            st.push(n);
        }
        for (int i = 0; i < CELLS; i++) for (int j = 0; j < CELLS; j++) {
            if (i + 1 < CELLS && RNG.nextInt(10) == 0) openBetween(s, i, j, i + 1, j);
            if (j + 1 < CELLS && RNG.nextInt(10) == 0) openBetween(s, i, j, i, j + 1);
        }
        s.sx = 1; s.sz = 1;
        int[][] d = bfs(s.open, s.sx, s.sz);
        int best = -1;
        for (int i = 0; i < CELLS; i++) for (int j = 0; j < CELLS; j++) { int bx = 1 + i * CS, bz = 1 + j * CS; if (d[bx][bz] > best) { best = d[bx][bz]; s.ex = bx; s.ez = bz; } }
        s.toExit = bfs(s.open, s.ex, s.ez);
    }

    static void openCell(Session s, int i, int j) { int bx = 1 + i * CS, bz = 1 + j * CS; for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) s.open[bx + a][bz + b] = true; }
    static void openBetween(Session s, int i, int j, int i2, int j2) {
        if (i2 != i) { int wx = CS * (Math.min(i, i2) + 1), bz = 1 + j * CS; s.open[wx][bz] = s.open[wx][bz + 1] = true; }
        else { int wz = CS * (Math.min(j, j2) + 1), bx = 1 + i * CS; s.open[bx][wz] = s.open[bx + 1][wz] = true; }
    }

    /** Расстояния по коридорам от точки до всех блоков (−1 — стена/недостижимо). */
    static int[][] bfs(boolean[][] open, int x0, int z0) {
        int[][] d = new int[SIZE][SIZE];
        for (int[] r : d) Arrays.fill(r, -1);
        if (x0 < 0 || z0 < 0 || x0 >= SIZE || z0 >= SIZE || !open[x0][z0]) return d;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        d[x0][z0] = 0; q.add(new int[]{x0, z0});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int[][] nb = {{c[0] + 1, c[1]}, {c[0] - 1, c[1]}, {c[0], c[1] + 1}, {c[0], c[1] - 1}};
            for (int[] n : nb) if (n[0] >= 0 && n[1] >= 0 && n[0] < SIZE && n[1] < SIZE && open[n[0]][n[1]] && d[n[0]][n[1]] < 0) { d[n[0]][n[1]] = d[c[0]][c[1]] + 1; q.add(n); }
        }
        return d;
    }

    static void build(Session s) {
        ServerLevel l = s.level;
        for (int cx = (s.ox - 2) >> 4; cx <= (s.ox + SIZE + 2) >> 4; cx++) for (int cz = (s.oz - 2) >> 4; cz <= (s.oz + SIZE + 2) >> 4; cz++) l.m_6325_(cx, cz);
        BlockState bricks = Fx.state("deepslate_bricks"), cracked = Fx.state("cracked_deepslate_bricks"), tiles = Fx.state("deepslate_tiles"),
                floor = Fx.state("polished_deepslate"), sculk = Fx.state("sculk"), bedrock = Fx.state("bedrock"), air = Fx.state("air"),
                cry = Fx.state("crying_obsidian"), shroom = Fx.state("shroomlight");
        for (int x = -1; x <= SIZE; x++) for (int z = -1; z <= SIZE; z++) {
            int wx = s.ox + x, wz = s.oz + z;
            boolean in = x >= 0 && z >= 0 && x < SIZE && z < SIZE;
            boolean path = in && s.open[x][z];
            l.m_7731_(new BlockPos(wx, Y0 - 2, wz), bedrock, 18);
            l.m_7731_(new BlockPos(wx, Y0 - 1, wz), !in ? bedrock : RNG.nextInt(16) == 0 ? sculk : floor, 18);
            for (int y = 0; y < H; y++) {
                BlockState w = !in ? bedrock : path ? air : (RNG.nextInt(8) == 0 ? cracked : RNG.nextInt(12) == 0 ? tiles : bricks);
                l.m_7731_(new BlockPos(wx, Y0 + y, wz), w, 18);
            }
            l.m_7731_(new BlockPos(wx, Y0 + H, wz), in ? tiles : bedrock, 18);
            l.m_7731_(new BlockPos(wx, Y0 + H + 1, wz), bedrock, 18);
        }
        // выход: плачущий обсидиан в полу и свет в потолке
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
            l.m_7731_(new BlockPos(s.ox + s.ex + a, Y0 - 1, s.oz + s.ez + b), cry, 18);
            l.m_7731_(new BlockPos(s.ox + s.ex + a, Y0 + H, s.oz + s.ez + b), shroom, 18);
        }
        // редкие фонари душ под потолком
        for (int i = 0; i < CELLS; i++) for (int j = 0; j < CELLS; j++) {
            if (RNG.nextInt(8) != 0) continue;
            int x = s.ox + 1 + i * CS + RNG.nextInt(2), z = s.oz + 1 + j * CS + RNG.nextInt(2);
            Cmd.run(l, x, Y0, z, "setblock " + x + " " + (Y0 + H - 1) + " " + z + " minecraft:soul_lantern[hanging=true]");
        }
    }

    // ================= Тик =================
    static void tick(ServerLevel level) {
        init();
        Iterator<Session> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            Session s = it.next();
            if (s.level != level) continue;
            if (!s.p.m_6084_() || s.p.m_213877_() || s.p.m_9236_() != level) { cleanup(s); it.remove(); continue; }
            if (step(s)) { cleanup(s); it.remove(); }
        }
    }

    static void title(Session s, String title, String color, String sub) {
        String n = s.p.m_36316_().getName();
        Cmd.run(s.level, s.p.m_20185_(), Y0, s.p.m_20189_(), "title " + n + " times 10 60 20");
        Cmd.run(s.level, s.p.m_20185_(), Y0, s.p.m_20189_(), "title " + n + " subtitle {\"text\":\"" + sub + "\",\"color\":\"gray\",\"italic\":true}");
        Cmd.run(s.level, s.p.m_20185_(), Y0, s.p.m_20189_(), "title " + n + " title {\"text\":\"" + title + "\",\"color\":\"" + color + "\",\"bold\":true}");
    }
    static void bar(Session s, String text, String color) {
        Cmd.run(s.level, s.p.m_20185_(), Y0, s.p.m_20189_(), "title " + s.p.m_36316_().getName() + " actionbar {\"text\":\"" + text + "\",\"color\":\"" + color + "\"}");
    }
    static void effect(Session s, String id, int ticks, int amp) { var e = Fx.effect(id); if (e != null) s.p.m_7292_(new MobEffectInstance(e, ticks, amp)); }

    /** true — сессия закончена (игрок выбрался). */
    static boolean step(Session s) {
        s.t++;
        ServerLevel l = s.level;
        Vec3 pv = s.p.m_20182_();
        int pbx = (int) Math.floor(pv.f_82479_) - s.ox, pbz = (int) Math.floor(pv.f_82481_) - s.oz;
        if (s.t == 5) title(s, "ЛАБИРИНТ НЕКТО", "dark_red", "Найди выход. Не останавливайся.");
        if (s.t % 20 == 0) { var nv = Fx.effect("night_vision"); if (nv != null) s.p.m_21195_(nv); }      // никакого ночного зрения внутри
        // выход
        double exc = s.ox + s.ex + 1.0, ezc = s.oz + s.ez + 1.0;
        if (s.t % 3 == 0) { Fx.send(l, Fx.particle("reverse_portal"), exc, Y0 + 1, ezc, 8, 0.5, 0.8, 0.5, 0.05); Fx.send(l, Fx.particle("soul"), exc, Y0 + 0.3, ezc, 1, 0.5, 0.1, 0.5, 0.01); }
        if (s.t % 60 == 0) Fx.sound(l, exc, Y0 + 1, ezc, "block.portal.ambient", 0.6F, 1.4F);
        double dxe = pv.f_82479_ - exc, dze = pv.f_82481_ - ezc;
        if (s.t > 20 && dxe * dxe + dze * dze < 1.3 * 1.3) { escape(s); return true; }

        // монстр появляется через 10 секунд где-то далеко
        if (s.t == 200) spawnMonster(s, pbx, pbz, 34);
        if (s.monster) tickMonster(s, pv, pbx, pbz);

        // случайные напасти
        if (--s.eventCd <= 0) {
            s.eventCd = 360 + RNG.nextInt(300);
            switch (RNG.nextInt(5)) {
                case 0 -> { effect(s, "blindness", 70, 0); Fx.sound(l, pv.f_82479_, pv.f_82480_, pv.f_82481_, "ambient.cave", 1.0F, 0.6F); }
                case 1 -> { effect(s, "darkness", 160, 0); bar(s, "Фонари гаснут...", "dark_gray"); }
                case 2 -> effect(s, "nausea", 120, 0);
                case 3 -> { effect(s, "slowness", 80, 0); bar(s, "Ноги наливаются свинцом...", "dark_gray"); }
                default -> { Vec3 look = s.p.m_20252_(1.0F); for (int i = 0; i < 4; i++) Fx.sound(l, pv.f_82479_ - look.f_82479_ * (3 + i), pv.f_82480_, pv.f_82481_ - look.f_82481_ * (3 + i), "entity.warden.step", 0.8F, 0.7F); }
            }
        }
        // подсказка: огоньки душ на несколько шагов к выходу
        if (--s.hintCd <= 0) { s.hintCd = 900; s.hintT = 60; bar(s, "Огоньки душ указывают путь...", "aqua"); }
        if (s.hintT > 0) {
            s.hintT--;
            if (s.hintT % 5 == 0) {
                int x = pbx, z = pbz;
                for (int k = 0; k < 9; k++) {
                    int[] n = downhill(s.toExit, s.open, x, z);
                    if (n == null) break;
                    x = n[0]; z = n[1];
                    if (k % 2 == 1) Fx.send(l, Fx.particle("soul_fire_flame"), s.ox + x + 0.5, Y0 + 0.6, s.oz + z + 0.5, 2, 0.1, 0.1, 0.1, 0.005);
                }
            }
        }
        if (RNG.nextInt(500) == 0) Fx.sound(l, pv.f_82479_ + RNG.nextGaussian() * 8, Y0 + 1, pv.f_82481_ + RNG.nextGaussian() * 8, "ambient.cave", 0.9F, 0.5F);
        return false;
    }

    static int[] downhill(int[][] d, boolean[][] open, int x, int z) {
        if (x < 0 || z < 0 || x >= SIZE || z >= SIZE || d[x][z] <= 0) return null;
        int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
        for (int[] n : nb) if (n[0] >= 0 && n[1] >= 0 && n[0] < SIZE && n[1] < SIZE && open[n[0]][n[1]] && d[n[0]][n[1]] >= 0 && d[n[0]][n[1]] < d[x][z]) return n;
        return null;
    }

    // ================= Некто =================
    static void spawnMonster(Session s, int pbx, int pbz, int minDist) {
        int[][] d = bfs(s.open, clamp(pbx), clamp(pbz));
        List<int[]> far = new ArrayList<>();
        for (int i = 0; i < CELLS; i++) for (int j = 0; j < CELLS; j++) { int bx = 1 + i * CS, bz = 1 + j * CS; if (d[bx][bz] >= minDist && (bx != s.ex || bz != s.ez)) far.add(new int[]{bx, bz}); }
        if (far.isEmpty()) for (int i = 0; i < CELLS; i++) for (int j = 0; j < CELLS; j++) { int bx = 1 + i * CS, bz = 1 + j * CS; if (d[bx][bz] >= minDist / 2) far.add(new int[]{bx, bz}); }
        if (far.isEmpty()) far.add(new int[]{s.ex, s.ez});
        int[] c = far.get(RNG.nextInt(far.size()));
        s.mx = s.ox + c[0] + 1.0; s.mz = s.oz + c[1] + 1.0;
        if (!s.monster) {
            StringBuilder sb = new StringBuilder("summon minecraft:armor_stand " + Cmd.f(s.mx) + " " + Y0 + " " + Cmd.f(s.mz)
                    + " {Tags:[\"" + s.tag + "\",\"" + s.tag + "_host\"],Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,Passengers:[");
            for (int i = 0; i < PARTS.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("{id:\"minecraft:block_display\",Tags:[\"").append(s.tag).append("\",\"").append(s.tag).append("_p").append(i).append("\"],")
                  .append(Disp.block(PARTS[i])).append(",view_range:2f,brightness:{sky:").append(i >= 6 ? 15 : 0).append(",block:").append(i >= 6 ? 15 : 0).append("},")
                  .append(Disp.tf(0, 1, 0, 0.01, 0.01, 0.01, Disp.ID)).append("}");
            }
            sb.append("]}");
            Cmd.run(s.level, s.mx, Y0, s.mz, sb.toString());
            s.monster = true;
            title(s, "Беги.", "dark_red", "Некто вошёл в лабиринт");
            Fx.sound(s.level, s.p.m_20185_(), s.p.m_20186_(), s.p.m_20189_(), "entity.warden.emerge", 0.8F, 0.6F);
            Later.run(s.level, 2, () -> {
                for (Entity e : Cmd.tagged(s.level, s.mx, Y0, s.mz, 6, s.tag + "_host")) s.host = e;
                for (int i = 0; i < PARTS.length; i++) for (Entity e : Cmd.tagged(s.level, s.mx, Y0, s.mz, 6, s.tag + "_p" + i)) s.uuid[i] = e.m_20149_();
            });
        }
    }

    static int clamp(int v) { return Math.max(0, Math.min(SIZE - 1, v)); }

    static void tickMonster(Session s, Vec3 pv, int pbx, int pbz) {
        ServerLevel l = s.level;
        if (s.t % 5 == 0 && pbx >= 0 && pbz >= 0 && pbx < SIZE && pbz < SIZE && s.open[pbx][pbz]) s.toPlayer = bfs(s.open, pbx, pbz);
        if (s.toPlayer == null) return;
        // скорость: растёт со временем; ярость — короткий рывок почти со скоростью бега
        s.speed = Math.min(0.2, 0.125 + s.t * 0.000025);
        if (s.rageT < 0 && --s.rageCd <= 0) { s.rageT = 0; s.rageCd = 520 + RNG.nextInt(260); Fx.sound(l, s.mx, Y0 + 2, s.mz, "entity.warden.roar", 3.0F, 0.8F); bar(s, "Некто в ярости!", "dark_red"); }
        double sp = s.speed;
        if (s.rageT >= 0) { sp = 0.255; if (++s.rageT > 80) s.rageT = -1; }
        if (s.hitCd > 0) s.hitCd--;
        int mbx = (int) Math.floor(s.mx) - s.ox, mbz = (int) Math.floor(s.mz) - s.oz;
        int pd = (mbx >= 0 && mbz >= 0 && mbx < SIZE && mbz < SIZE) ? s.toPlayer[mbx][mbz] : -1;
        // отстал слишком сильно — переносится ближе (позади игрока)
        if ((pd < 0 || pd > 46) && s.rageT < 0 && RNG.nextInt(300) == 0) {
            spawnMonster(s, pbx, pbz, 16);
            Fx.sound(l, pv.f_82479_, pv.f_82480_, pv.f_82481_, "entity.enderman.teleport", 0.5F, 0.4F);
            return;
        }
        double tx, tz;
        double ddx = pv.f_82479_ - s.mx, ddz = pv.f_82481_ - s.mz, de = Math.sqrt(ddx * ddx + ddz * ddz);
        if (pd >= 0 && pd <= 2) { tx = pv.f_82479_; tz = pv.f_82481_; }
        else {
            int[] n = pd > 0 ? downhill(s.toPlayer, s.open, mbx, mbz) : null;
            if (n == null) { tx = s.mx; tz = s.mz; } else { tx = s.ox + n[0] + 0.5; tz = s.oz + n[1] + 0.5; }
        }
        if (s.pause > 0) s.pause--;
        else {
            double mx = tx - s.mx, mz = tz - s.mz, ml = Math.sqrt(mx * mx + mz * mz);
            if (ml > 1e-3) { double st = Math.min(sp, ml); s.mx += mx / ml * st; s.mz += mz / ml * st; s.walk += st; }
        }
        if (de > 0.01) s.yaw = Math.atan2(ddx, ddz);
        if (s.host != null) { s.host.m_6034_(s.mx, Y0, s.mz); s.host.m_20256_(new Vec3(0, 0, 0)); }
        if (s.t % 3 == 0) pose(s);
        if (s.t % 2 == 0) Fx.send(l, BLACK, s.mx, Y0 + 1.2, s.mz, 2, 0.25, 0.6, 0.25, 0);
        // звуки приближения
        if (pd >= 0 && pd < 14 && s.t % (pd < 6 ? 12 : 24) == 0) Fx.sound(l, s.p.m_20185_(), s.p.m_20186_(), s.p.m_20189_(), "entity.warden.heartbeat", pd < 6 ? 1.0F : 0.6F, 0.7F);
        if (s.t % 14 == 0 && s.pause <= 0) Fx.sound(l, s.mx, Y0, s.mz, "entity.warden.step", 1.2F, 0.6F);
        // удар
        if (de < 1.15 && Math.abs(pv.f_82480_ - Y0) < 2 && s.hitCd <= 0) {
            s.hitCd = 30; s.pause = 26;
            s.p.m_6469_(l.m_269111_().m_269425_(), 8.0F);
            effect(s, "darkness", 70, 0); effect(s, "slowness", 40, 1);
            s.p.m_20256_(new Vec3(ddx / Math.max(de, 0.2) * 0.7, 0.3, ddz / Math.max(de, 0.2) * 0.7)); s.p.f_19864_ = true;
            Fx.sound(l, s.mx, Y0 + 2, s.mz, "entity.warden.attack_impact", 1.5F, 0.6F);
            Fx.send(l, RED, pv.f_82479_, pv.f_82480_ + 1, pv.f_82481_, 20, 0.3, 0.5, 0.3, 0.05);
        }
    }

    static void pose(Session s) {
        double[] qY = Disp.q(0, 1, 0, s.yaw);
        double sw = Math.sin(s.walk * 2.2), arm = Math.sin(s.t * 0.08) * 0.15 + (s.rageT >= 0 ? -0.9 : 0);
        Object[][] p = {
                {-0.15, 0.75, 0.0, 0.2, 1.5, 0.2, Disp.q(1, 0, 0, sw * 0.4)},
                {0.15, 0.75, 0.0, 0.2, 1.5, 0.2, Disp.q(1, 0, 0, -sw * 0.4)},
                {0.0, 2.0, 0.0, 0.55, 1.0, 0.3, Disp.ID},
                {-0.4, 1.75, 0.0, 0.14, 1.7, 0.14, Disp.q(1, 0, 0, arm - sw * 0.2)},
                {0.4, 1.75, 0.0, 0.14, 1.7, 0.14, Disp.q(1, 0, 0, arm + sw * 0.2)},
                {0.0, 2.78, 0.02, 0.45, 0.52, 0.4, Disp.q(0, 0, 1, Math.sin(s.t * 0.05) * 0.12)},
                {-0.1, 2.82, 0.225, 0.08, 0.05, 0.02, Disp.ID},
                {0.1, 2.82, 0.225, 0.08, 0.05, 0.02, Disp.ID}};
        for (int i = 0; i < p.length; i++) {
            if (s.uuid[i] == null) continue;
            double[] r = Disp.rot(qY, (double) p[i][0], (double) p[i][1], (double) p[i][2]);
            double[] q = Disp.mul(qY, (double[]) p[i][6]);
            Cmd.run(s.level, s.mx, Y0, s.mz, "data merge entity " + s.uuid[i] + " {start_interpolation:0,interpolation_duration:3,"
                    + Disp.tf(r[0], r[1], r[2], (double) p[i][3], (double) p[i][4], (double) p[i][5], q) + "}");
        }
    }

    // ================= Выход и уборка =================
    static void escape(Session s) {
        String n = s.p.m_36316_().getName();
        title(s, "Ты выбрался", "gold", "Некто остался в темноте... пока");
        Cmd.run(s.level, s.p.m_20185_(), Y0, s.p.m_20189_(), "give " + n + " minecraft:diamond 6");
        Cmd.run(s.level, s.p.m_20185_(), Y0, s.p.m_20189_(), "give " + n + " minecraft:echo_shard 4");
        Cmd.run(s.level, s.p.m_20185_(), Y0, s.p.m_20189_(), "give " + n + " minecraft:golden_apple 2");
        Cmd.run(s.level, s.p.m_20185_(), Y0, s.p.m_20189_(), "xp add " + n + " 10 levels");
        Fx.sound(s.level, s.p.m_20185_(), s.p.m_20186_(), s.p.m_20189_(), "ui.toast.challenge_complete", 1.0F, 1.0F);
        cleanup(s);
        Dims.sendHome(s.p, s.level);
    }

    static void cleanup(Session s) {
        if (s.host != null) s.host.m_146870_();
        Cmd.run(s.level, s.ox, Y0, s.oz, "kill @e[tag=" + s.tag + "]");
    }

    static void end(Session s, boolean ok) { cleanup(s); }

    /** Стены лабиринта не ломаются (кроме творческого режима). */
    static void onBreak(BlockEvent.BreakEvent ev) {
        if (!(ev.getPlayer() instanceof ServerPlayer p) || p.m_7500_()) return;
        if (p.m_9236_() instanceof ServerLevel sl && Dims.is(sl, Dims.MAZE)) {
            ev.setCanceled(true);
            Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.deepslate_bricks.hit", 0.8F, 0.5F);
        }
    }
}
