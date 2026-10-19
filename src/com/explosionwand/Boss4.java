package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * «НЕБЕСНЫЙ КИТ» — 4-й босс (арена Неба). Огромный блочный кит плывёт по воздуху над облачной
 * платформой, ныряет сквозь пол, проходит под ареной (по полу бежит рябь) и выныривает под игроком.
 * После выныривания зависает низко — тогда его можно бить мечом.
 *
 *  1 Песня глубин   — круги над ареной, ледяные искры из светящихся узоров, нырки.
 *  2 Прилипалы      — приходят фантомы-прилипалы, песня кита бьёт кольцом по полу.
 *  3 Медузки        — из узоров вылетают медузки и взрываются при касании; нырки чаще.
 *  4 Шторм          — двойные нырки подряд, искры веером.
 */
public class Boss4 {
    static Boss4 ACTIVE;
    static final Random RNG = Fx.RNG;
    static final double AX = 0.5, AZ = 0.5;
    static final int FLOOR = 64, ARENA_R = 30, N = 23;
    /** max_health моба ограничен 1024 — «настоящие» 2300 ХП даём через снижение урона. */
    static final float MAX_HP = 2300, HOST_HP = 1024;
    static final String BAR = "explosionwand:sky_whale";
    static final String TAG = "ewb4";
    static final double C = -3.0;      // центр кита относительно точки посадки (гигант стоит на 6 ниже центра)
    static final String[] BLOCKS = new String[N];
    // тело: z, ширина, высота, длина
    static final double[][] BODY = {{6.4, 4.0, 3.3, 3.0}, {3.6, 4.4, 3.7, 2.8}, {0.9, 4.2, 3.5, 2.8}, {-1.8, 3.5, 2.9, 2.8}, {-4.3, 2.6, 2.2, 2.4}, {-6.3, 1.8, 1.5, 2.0}, {-8.0, 1.1, 0.9, 1.8}};
    static {
        for (int i = 0; i < 7; i++) BLOCKS[i] = "blue_concrete";
        for (int i = 7; i < 11; i++) BLOCKS[i] = "light_gray_concrete";
        BLOCKS[11] = "black_concrete";
        for (int i = 12; i < 17; i++) BLOCKS[i] = "light_blue_concrete";
        BLOCKS[17] = BLOCKS[18] = "sea_lantern";
        BLOCKS[19] = BLOCKS[20] = BLOCKS[21] = BLOCKS[22] = "blue_concrete";
    }
    static ParticleOptions SKY, ICE, WHITE, PINK;

    enum S { CRUISE, DIVE, UNDER, WARN, BREACH, EXPOSED }

    final ServerLevel level;
    LivingEntity host;
    final List<LivingEntity> hits = new ArrayList<>();
    final String[] uuid = new String[N];
    int t = 0, stage = 0, dyingT = 0, phase = 1;
    double wx = AX, wy = FLOOR + 30, wz = AZ - 20;   // центр кита
    double vx = 0, vy = -0.3, vz = 1;                // направление (единичное)
    double speed = 0.35, cruiseAng = -Math.PI / 2, gs = 1.0;
    S state = S.CRUISE; int st = 0;
    ServerPlayer prey; double lockX, lockZ, diveX, diveZ;
    int diveCd = 220, sparkCd = 60, songCd = 160, jellyCd = 0, pendingDives = 0;
    boolean breached = false;
    final List<double[]> sparks = new ArrayList<>();   // x,y,z,vx,vy,vz,life
    final List<double[]> songs = new ArrayList<>();    // cx,cz,r
    final Map<String, LivingEntity> jellies = new HashMap<>();
    final Map<ServerPlayer, Integer> hitCd = new WeakHashMap<>();
    int jellySeq = 0;
    int stormCd = 60, gustCd = 200, gustT = -1; double gx, gz;
    final List<double[]> strikes = new ArrayList<>();   // x,z,timer

    static void init() {
        if (SKY != null) return;
        SKY = new DustParticleOptions(new Vector3f(0.45F, 0.8F, 1.0F), 1.6F);
        ICE = new DustParticleOptions(new Vector3f(0.75F, 0.95F, 1.0F), 1.2F);
        WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 2.0F);
        PINK = new DustParticleOptions(new Vector3f(0.95F, 0.35F, 0.85F), 1.3F);
    }

    Boss4(ServerLevel level) { this.level = level; }

    static void spawn(ServerLevel level) {
        init();
        Boss4 b = new Boss4(level);
        ACTIVE = b;
        StringBuilder sb = new StringBuilder();
        sb.append("summon minecraft:giant ").append(Cmd.f(b.wx)).append(" ").append(Cmd.f(b.wy - 6)).append(" ").append(Cmd.f(b.wz))
          .append(" {Tags:[\"" + TAG + "\",\"" + TAG + "_host\"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,")
          .append("Attributes:[{Name:\"minecraft:generic.max_health\",Base:").append((int) HOST_HP).append("d},{Name:\"minecraft:generic.knockback_resistance\",Base:1d}],")
          .append("Health:").append((int) HOST_HP).append("f,ActiveEffects:[{Id:14,Amplifier:0b,Duration:999999,ShowParticles:0b}],Passengers:[");
        for (int i = 0; i < N; i++) {
            if (i > 0) sb.append(",");
            sb.append("{id:\"minecraft:block_display\",Tags:[\"" + TAG + "\",\"" + TAG + "_p").append(i).append("\"],")
              .append(Disp.block(BLOCKS[i])).append(",view_range:6f,brightness:{sky:15,block:").append(i >= 12 && i <= 18 ? 15 : 10).append("},")
              .append(Disp.tf(0, C, 0, 0.01, 0.01, 0.01, Disp.ID)).append("}");
        }
        sb.append("]}");
        Cmd.run(level, AX, FLOOR, AZ, sb.toString());
        // дополнительные хитбоксы вдоль тела (голова и хвост), урон по ним уходит киту
        for (int i = 0; i < 2; i++)
            Cmd.run(level, AX, FLOOR, AZ, "summon minecraft:slime " + Cmd.f(b.wx) + " " + Cmd.f(b.wy) + " " + Cmd.f(b.wz)
                    + " {Tags:[\"" + TAG + "\",\"" + TAG + "_hit\"],Size:4,NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:0b,"
                    + "Attributes:[{Name:\"minecraft:generic.max_health\",Base:1000d},{Name:\"minecraft:generic.knockback_resistance\",Base:1d}],Health:1000f,"
                    + "DeathLootTable:\"minecraft:empty\",ActiveEffects:[{Id:14,Amplifier:0b,Duration:999999,ShowParticles:0b}]}");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
        Cmd.run(level, AX, FLOOR, AZ, "bossbar add " + BAR + " {\"text\":\"Небесный Кит\",\"color\":\"aqua\",\"bold\":true}");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " color blue");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " style notched_12");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " max 1000");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " value 1000");
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " times 20 90 30");
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " subtitle {\"text\":\"Тот, кто плавает между облаков\",\"color\":\"gray\",\"italic\":true}");
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " title {\"text\":\"НЕБЕСНЫЙ КИТ\",\"color\":\"aqua\",\"bold\":true}");
        Fx.sound(level, AX, FLOOR + 20, AZ, "entity.elder_guardian.ambient", 30.0F, 0.4F);
        Fx.sound(level, AX, FLOOR + 20, AZ, "block.conduit.activate", 30.0F, 0.5F);
    }

    static String sel(String cmd) { return cmd + " @a[x=0,y=64,z=0,distance=..220]"; }

    List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) if (p.m_6084_() && !p.m_5833_()) out.add(p);
        return out;
    }
    void hurt(ServerPlayer p, float d) { p.m_6469_(level.m_269111_().m_269425_(), d); }
    void actionbar(String s, String c) { Cmd.run(level, AX, FLOOR, AZ, sel("title") + " actionbar {\"text\":\"" + s + "\",\"color\":\"" + c + "\"}"); }
    boolean vulnerable() { return stage == 2 && (state == S.CRUISE || state == S.EXPOSED); }

    double[] quat() {
        double yaw = Math.atan2(vx, vz), h = Math.sqrt(vx * vx + vz * vz), pitch = Math.atan2(-vy, Math.max(1e-4, h));
        return Disp.mul(Disp.q(0, 1, 0, yaw), Disp.q(1, 0, 0, pitch));
    }
    double[] world(double lx, double lyy, double lz) { double[] r = Disp.rot(quat(), lx, lyy, lz); return new double[]{wx + r[0], wy + r[1], wz + r[2]}; }

    // ================= Тик =================
    boolean tick() {
        t++;
        if (stage == 0) {
            List<Entity> h = Cmd.tagged(level, wx, wy, wz, 30, TAG + "_host");
            if (h.isEmpty() || !(h.get(0) instanceof LivingEntity le)) { if (t > 40) { reset(); return true; } return false; }
            host = le;
            for (int i = 0; i < N; i++) { List<Entity> p = Cmd.tagged(level, wx, wy, wz, 30, TAG + "_p" + i); if (!p.isEmpty()) uuid[i] = p.get(0).m_20149_(); }
            for (Entity e : Cmd.tagged(level, wx, wy, wz, 30, TAG + "_hit")) if (e instanceof LivingEntity le2) hits.add(le2);
            stage = 1; t = 0;
            return false;
        }
        if (host == null) { reset(); return true; }
        if (stage == 3) return dying();
        if (host.m_21223_() <= 0 || host.m_21224_()) { startDeath(); return false; }
        if (!host.m_6084_()) { reset(); return true; }
        if (host.m_6060_()) host.m_20095_();

        if (stage == 1) {
            steer(AX, FLOOR + 16, AZ, 0.35, 0.05);
            place(); if (t % 3 == 0) pose(3);
            if (t % 20 == 0) Fx.sound(level, wx, wy, wz, "entity.dolphin.ambient", 15.0F, 0.4F);
            if (t >= 80) { stage = 2; t = 0; actionbar("Когда кит вынырнет и зависнет — бей!", "aqua"); }
            return false;
        }

        float frac = host.m_21223_() / HOST_HP;
        if (phase == 1 && frac <= 0.7F) startPhase(2);
        if (phase == 2 && frac <= 0.4F) startPhase(3);
        if (phase == 3 && frac <= 0.15F) startPhase(4);

        behave();
        place();
        if (t % 3 == 0) pose(3);
        ambience();
        tickSparks(); tickSongs(); tickJellies();
        if (phase >= 4) tickStorm();
        if (t % 10 == 0) bar();
        return false;
    }

    /** Плавный поворот к цели и движение вперёд. */
    void steer(double tx, double ty, double tz, double sp, double turn) {
        double dx = tx - wx, dy = ty - wy, dz = tz - wz, l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
        vx += (dx / l - vx) * turn; vy += (dy / l - vy) * turn; vz += (dz / l - vz) * turn;
        double vl = Math.max(1e-3, Math.sqrt(vx * vx + vy * vy + vz * vz)); vx /= vl; vy /= vl; vz /= vl;
        speed += (sp - speed) * 0.1;
        double step = Math.min(speed, l);
        wx += vx * step; wy += vy * step; wz += vz * step;
    }

    void place() {
        host.m_6034_(wx, wy - 6, wz); host.m_20256_(new Vec3(0, 0, 0));
        for (int i = 0; i < hits.size(); i++) {
            LivingEntity e = hits.get(i);
            if (e == null || !e.m_6084_()) continue;
            double[] p = world(0, 0, i == 0 ? 5.2 : -4.8);
            e.m_6034_(p[0], p[1] - 1.3, p[2]); e.m_20256_(new Vec3(0, 0, 0));
        }
    }

    ServerPlayer pickPrey() {
        List<ServerPlayer> ps = players();
        if (ps.isEmpty()) return null;
        return ps.get(RNG.nextInt(ps.size()));
    }

    // ================= Поведение =================
    void behave() {
        st++;
        switch (state) {
            case CRUISE -> {
                cruiseAng += 0.011 + phase * 0.002;
                double r = 17;
                steer(AX + Math.cos(cruiseAng) * r, FLOOR + 13 + Math.sin(t * 0.03) * 2.5, AZ + Math.sin(cruiseAng) * r, 0.35 + phase * 0.04, 0.05);
                if (--sparkCd <= 0) { sparkVolley(); sparkCd = phase >= 4 ? 40 : 70; }
                if (phase >= 2 && --songCd <= 0) { song(); songCd = phase >= 3 ? 150 : 200; }
                if (phase >= 3 && --jellyCd <= 0) { releaseJellies(); jellyCd = 420; }
                if (--diveCd <= 0) { startDive(); diveCd = phase >= 4 ? 170 : phase >= 3 ? 200 : 240; pendingDives = phase >= 4 ? 1 : 0; }
            }
            case DIVE -> {
                double ty = FLOOR - 7;
                steer(diveX, ty, diveZ, 0.75, 0.11);
                if (wy < FLOOR && !breached) {   // пересёк пол
                    breached = true;
                    splash(wx, wz, 3.0, 6, 0.6);
                    actionbar("Кит нырнул под пол! Следи за рябью!", "aqua");
                }
                if (wy < FLOOR - 4 || st > 120) { state = S.UNDER; st = 0; }
            }
            case UNDER -> {
                if (prey == null || !prey.m_6084_()) prey = pickPrey();
                if (prey == null) { state = S.BREACH; st = 0; lockX = wx; lockZ = wz; break; }
                Vec3 p = prey.m_20182_();
                steer(p.f_82479_, FLOOR - 7, p.f_82481_, 0.42 + phase * 0.03, 0.12);
                wy += (FLOOR - 7 - wy) * 0.2;
                if (st % 2 == 0) ripple(wx, wz);
                if (st % 12 == 0) Fx.sound(level, wx, FLOOR, wz, "block.bubble_column.upwards_ambient", 4.0F, 0.5F);
                double dx = p.f_82479_ - wx, dz = p.f_82481_ - wz;
                if (dx * dx + dz * dz < 2.5 || st > 150) { state = S.WARN; st = 0; lockX = p.f_82479_; lockZ = p.f_82481_; }
            }
            case WARN -> {
                steer(lockX, FLOOR - 9, lockZ, 0.15, 0.2);
                if (vy > -0.6) { vy += (1 - vy) * 0.1; }
                int warn = phase >= 4 ? 18 : 26;
                if (st % 2 == 0) {
                    for (int i = 0; i < 24; i++) { double a = i * Math.PI / 12; Fx.send(level, i % 2 == 0 ? SKY : WHITE, lockX + Math.cos(a) * 4.5, FLOOR + 0.2, lockZ + Math.sin(a) * 4.5, 1, 0, 0, 0, 0); }
                    Fx.send(level, Fx.particle("cloud"), lockX, FLOOR + 0.2, lockZ, 6, 2, 0.05, 2, 0.02);
                    Fx.send(level, Fx.particle("bubble_pop"), lockX, FLOOR + 0.2, lockZ, 6, 2.5, 0.1, 2.5, 0.05);
                }
                if (st == 1) { actionbar("ПОД ТОБОЙ! Уходи из круга!", "red"); Fx.sound(level, lockX, FLOOR, lockZ, "entity.elder_guardian.ambient", 10.0F, 0.7F); }
                if (st >= warn) { state = S.BREACH; st = 0; breached = false; wx = lockX; wz = lockZ; vx = 0; vz = 0.001; vy = 1; }
            }
            case BREACH -> {
                vx = 0; vz = 0.001; vy = 1; speed = 0.95;
                wy += 0.95; wx += (lockX - wx) * 0.3; wz += (lockZ - wz) * 0.3;
                if (!breached && wy + 7 >= FLOOR) { breached = true; erupt(lockX, lockZ); }
                if (wy >= FLOOR + 7) {
                    breached = false;
                    if (pendingDives > 0) { pendingDives--; prey = pickPrey(); startDiveFrom(); }
                    else { state = S.EXPOSED; st = 0; vx = RNG.nextBoolean() ? 1 : -1; vy = -0.2; vz = 0; actionbar("Кит оглушён и завис — БЕЙ!", "gold"); Fx.sound(level, wx, wy, wz, "entity.dolphin.hurt", 15.0F, 0.4F); }
                }
            }
            case EXPOSED -> {
                double a = st * 0.03;
                steer(lockX + Math.cos(a) * 4, FLOOR + 3.7, lockZ + Math.sin(a) * 4, 0.12, 0.06);
                wy += (FLOOR + 3.7 - wy) * 0.1;
                if (st % 6 == 0) Fx.send(level, Fx.particle("falling_water"), wx, wy - 1.5, wz, 12, 2.5, 0.3, 3, 0);
                if (st % 20 == 0) Fx.send(level, Fx.particle("dolphin"), wx, wy + 2, wz, 20, 2, 0.5, 3, 0.1);
                if (st >= 110) { state = S.CRUISE; st = 0; actionbar("Кит поднимается в небо...", "gray"); }
            }
        }
    }

    void startDive() { prey = pickPrey(); if (prey == null) return; startDiveFrom(); }

    void startDiveFrom() {
        if (prey == null) { state = S.CRUISE; return; }
        Vec3 p = prey.m_20182_();
        double dx = wx - p.f_82479_, dz = wz - p.f_82481_, l = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
        double r = Math.min(10, ARENA_R - 6);
        diveX = p.f_82479_ + dx / l * r; diveZ = p.f_82481_ + dz / l * r;
        double cl = Math.sqrt((diveX - AX) * (diveX - AX) + (diveZ - AZ) * (diveZ - AZ));
        if (cl > ARENA_R - 4) { diveX = AX + (diveX - AX) / cl * (ARENA_R - 4); diveZ = AZ + (diveZ - AZ) / cl * (ARENA_R - 4); }
        state = S.DIVE; st = 0; breached = false;
        if (wy < FLOOR + 6) { vy = 0.6; wy = Math.max(wy, FLOOR + 2); }
        Fx.sound(level, wx, wy, wz, "entity.elder_guardian.ambient", 20.0F, 0.5F);
        actionbar("Кит заходит на нырок!", "aqua");
    }

    void splash(double x, double z, double r, float dmg, double up) {
        Fx.send(level, Fx.particle("cloud"), x, FLOOR + 0.5, z, 60, r * 0.6, 0.5, r * 0.6, 0.15);
        Fx.send(level, Fx.particle("splash"), x, FLOOR + 0.5, z, 120, r * 0.6, 0.6, r * 0.6, 0.4);
        Fx.send(level, Fx.blockParticle(Fx.state("white_concrete")), x, FLOOR + 0.3, z, 40, r * 0.5, 0.2, r * 0.5, 0.2);
        Fx.sound(level, x, FLOOR, z, "entity.generic.splash", 10.0F, 0.5F);
        Fx.sound(level, x, FLOOR, z, "entity.player.splash.high_speed", 10.0F, 0.6F);
        for (ServerPlayer p : players()) {
            Vec3 v = p.m_20182_(); double dx = v.f_82479_ - x, dz = v.f_82481_ - z, d = Math.sqrt(dx * dx + dz * dz);
            if (d < r && v.f_82480_ < FLOOR + 4) { hurt(p, dmg); p.m_20256_(new Vec3(dx / Math.max(d, 0.3) * 0.6, up, dz / Math.max(d, 0.3) * 0.6)); p.f_19864_ = true; }
        }
    }

    void erupt(double x, double z) {
        splash(x, z, 4.6, 15, 1.3);
        Fx.send(level, Fx.particle("explosion_emitter"), x, FLOOR + 1, z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 40; i++) { double[] u = Fx.unit(); Fx.send(level, Fx.particle("bubble_column_up"), x, FLOOR + 0.5, z, 0, u[0] * 2, Math.abs(u[1]) * 4, u[2] * 2, 1.0); }
        Fx.sound(level, x, FLOOR, z, "entity.elder_guardian.curse", 15.0F, 0.6F);
        Fx.sound(level, x, FLOOR, z, "entity.generic.explode", 10.0F, 0.6F);
    }

    void ripple(double x, double z) {
        Fx.send(level, Fx.particle("splash"), x, FLOOR + 0.1, z, 10, 1.5, 0.05, 1.5, 0.1);
        Fx.send(level, Fx.particle("bubble_pop"), x, FLOOR + 0.1, z, 4, 1.2, 0.05, 1.2, 0.02);
        if (t % 4 == 0) Fx.send(level, Fx.particle("cloud"), x, FLOOR + 0.1, z, 3, 1.5, 0.02, 1.5, 0.01);
        for (int i = 0; i < 10; i++) { double a = i * Math.PI / 5 + t * 0.2; Fx.send(level, SKY, x + Math.cos(a) * 2.5, FLOOR + 0.15, z + Math.sin(a) * 2.5, 1, 0, 0, 0, 0); }
    }

    // ================= Облик =================
    void send(int i, int dur, double cx, double cy, double cz, double sx, double sy, double sz, double[] q) {
        if (uuid[i] == null) return;
        Cmd.run(level, wx, wy, wz, "data merge entity " + uuid[i] + " {start_interpolation:0,interpolation_duration:" + dur + "," + Disp.tf(cx, cy, cz, Math.max(0.001, sx), Math.max(0.001, sy), Math.max(0.001, sz), q) + "}");
    }

    double wave(double z) { double amp = Math.max(0, (2.5 - z) / 11.0) * 0.7; return Math.sin(t * 0.13 - z * 0.28) * amp; }

    /** Часть кита: локальный центр, размер, локальный поворот. */
    void part(int i, int dur, double lx, double lyy, double lz, double sx, double sy, double sz, double[] lq) {
        double[] Q = quat();
        double w = wave(lz);
        double[] q2 = Disp.mul(Q, Disp.mul(Disp.q(1, 0, 0, -w * 0.35), lq));
        double[] r = Disp.rot(Q, lx * gs, (lyy + w) * gs, lz * gs);
        send(i, dur, r[0], r[1] + C, r[2], sx * gs, sy * gs, sz * gs, q2);
    }

    void pose(int dur) {
        for (int i = 0; i < 7; i++) { double[] b = BODY[i]; part(i, dur, 0, i == 0 ? -0.15 : 0.05, b[0], b[1], b[2], b[3], Disp.ID); }
        for (int i = 0; i < 4; i++) { double[] b = BODY[i]; part(7 + i, dur, 0, -b[2] / 2 - 0.02, b[0], b[1] - 0.5, 0.26, b[3] * 0.95, Disp.ID); }
        part(11, dur, 0, -0.55, 6.7, BODY[0][1] + 0.05, 0.14, 2.6, Disp.ID);
        double[][] spots = {{0.9, 1}, {-0.8, 2}, {0.5, 3}, {-0.6, 0}, {0.6, 0}};
        for (int i = 0; i < 5; i++) {
            double[] b = BODY[(int) spots[i][1]];
            double pulse = 0.5 + Math.sin(t * 0.2 + i) * 0.08;
            part(12 + i, dur, spots[i][0], b[2] / 2 + 0.05, b[0] + (i >= 3 ? -0.6 : 0), pulse, pulse * 0.5, pulse, Disp.ID);
        }
        for (int s = 0; s < 2; s++) part(17 + s, dur, (s == 0 ? 1 : -1) * (BODY[0][1] / 2 + 0.02), 0.25, 6.9, 0.5, 0.5, 0.5, Disp.ID);
        double flap = Math.sin(t * 0.1) * 0.25;
        for (int s = 0; s < 2; s++) { double sg = s == 0 ? 1 : -1; part(19 + s, dur, sg * 3.0, -1.0, 3.4, 2.8, 0.25, 1.4, Disp.q(0, 0, 1, sg * (0.45 + flap))); }
        for (int s = 0; s < 2; s++) { double sg = s == 0 ? 1 : -1; part(21 + s, dur, sg * 1.3, 0.0, -9.3, 2.6, 0.22, 1.6, Disp.q(0, 1, 0, sg * 0.4)); }
    }

    void ambience() {
        if (t % 4 == 0) for (int i = 0; i < 3; i++) {
            double[] p = world((RNG.nextDouble() - 0.5) * 2, 1.8, 3 - RNG.nextDouble() * 8);
            Fx.send(level, SKY, p[0], p[1], p[2], 1, 0.05, 0.05, 0.05, 0);
        }
        if (state == S.CRUISE && t % 9 == 0) { double[] tail = world(0, 0, -9); Fx.send(level, Fx.particle("cloud"), tail[0], tail[1], tail[2], 2, 0.4, 0.2, 0.4, 0.01); }
        if (t % 140 == 0) Fx.sound(level, wx, wy, wz, "entity.elder_guardian.ambient", 25.0F, 0.35F + RNG.nextFloat() * 0.1F);
    }

    void bar() {
        int v = (int) Math.max(0, Math.min(1000, host.m_21223_() / HOST_HP * 1000));
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " value " + v);
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " players @a[x=0,y=64,z=0,distance=..220]");
    }

    // ================= Фазы и приспешники =================
    void startPhase(int ph) {
        phase = ph;
        String[] sub = {"", "", "Прилипалы и песня глубин", "Медузки из светящихся узоров", "Шторм: молнии и шквальный ветер"};
        String[] rom = {"", "", "II", "III", "IV"};
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " times 5 50 15");
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " subtitle {\"text\":\"" + sub[ph] + "\",\"color\":\"gray\",\"italic\":true}");
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " title {\"text\":\"ФАЗА " + rom[ph] + "\",\"color\":\"aqua\",\"bold\":true}");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " name {\"text\":\"Небесный Кит — " + sub[ph] + "\",\"color\":\"aqua\",\"bold\":true}");
        int np = Math.max(1, players().size());
        if (ph == 2) {
            actionbar("Кит зовёт прилипал!", "aqua");
            Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " color green");
            int n = Math.min(7, 3 + np);
            for (int i = 0; i < n; i++) {
                double[] p = world((i % 2 == 0 ? 3 : -3), 0, 4 - i * 2);
                Minions.spawn(level, "phantom", p[0], p[1], p[2], Minions.common(TAG, TAG + "_min", 20) + "," + Minions.name("Прилипала", "aqua")
                        + ",Size:1,ActiveEffects:[{Id:12,Amplifier:0b,Duration:999999,ShowParticles:0b}]");
            }
            Fx.sound(level, wx, wy, wz, "entity.phantom.swoop", 20.0F, 0.6F);
        }
        if (ph == 3) {
            actionbar("Светящиеся узоры рождают медузок!", "light_purple");
            Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " color purple");
            jellyCd = 20;
        }
        if (ph == 4) {
            actionbar("ШТОРМ! Кит ныряет без передышки!", "red");
            Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " color red");
            Fx.sound(level, wx, wy, wz, "entity.lightning_bolt.thunder", 20.0F, 0.6F);
            int n = Math.min(4, 1 + np);
            for (int i = 0; i < n; i++) {
                double[] p = world(i % 2 == 0 ? 3 : -3, 0, -i * 2);
                Minions.spawn(level, "phantom", p[0], p[1], p[2], Minions.common(TAG, TAG + "_min", 20) + "," + Minions.name("Прилипала", "aqua")
                        + ",Size:1,ActiveEffects:[{Id:12,Amplifier:0b,Duration:999999,ShowParticles:0b}]");
            }
            diveCd = Math.min(diveCd, 40);
        }
    }

    void releaseJellies() {
        int have = 0; for (LivingEntity j : jellies.values()) if (j != null && j.m_6084_()) have++;
        int want = Math.min(8, 4 + players().size()) - have;
        for (int i = 0; i < want; i++) {
            double[] p = world((RNG.nextDouble() - 0.5) * 3, 1.5, 4 - RNG.nextDouble() * 8);
            String id = TAG + "_j" + (jellySeq++);
            String disp = ",{id:\"minecraft:block_display\",Tags:[\"" + TAG + "\",\"" + id + "\"],brightness:{sky:15,block:15},view_range:3f,";
            Cmd.run(level, p[0], p[1], p[2], "summon minecraft:bat " + Cmd.f(p[0]) + " " + Cmd.f(p[1]) + " " + Cmd.f(p[2])
                    + " {" + Minions.common(TAG, TAG + "_min", 8, TAG + "_jelly", id) + ",NoAI:1b,NoGravity:1b,Silent:1b,"
                    + Minions.name("Медузка", "light_purple") + ",ActiveEffects:[{Id:14,Amplifier:0b,Duration:999999,ShowParticles:0b}],Passengers:["
                    + disp.substring(1) + Disp.block("magenta_stained_glass") + "," + Disp.tf(0, -0.25, 0, 0.9, 0.55, 0.9, Disp.ID) + "}"
                    + disp + Disp.block("purple_concrete") + "," + Disp.tf(0, -0.35, 0, 0.4, 0.4, 0.4, Disp.ID) + "}"
                    + disp + Disp.block("light_blue_stained_glass") + "," + Disp.tf(0.22, -0.95, 0, 0.1, 0.75, 0.1, Disp.ID) + "}"
                    + disp + Disp.block("light_blue_stained_glass") + "," + Disp.tf(-0.22, -0.9, 0.1, 0.1, 0.65, 0.1, Disp.ID) + "}]}");
            Fx.send(level, PINK, p[0], p[1], p[2], 15, 0.3, 0.3, 0.3, 0.05);
            final String fid = id;
            Later.run(level, 2, () -> {
                for (Entity e : Cmd.tagged(level, wx, wy, wz, 40, fid)) if (e instanceof LivingEntity le && Cmd.hasTagPrefix(le, TAG + "_jelly")) jellies.put(fid, le);
            });
        }
        Fx.sound(level, wx, wy, wz, "block.amethyst_block.chime", 15.0F, 0.6F);
    }

    void tickJellies() {
        Iterator<Map.Entry<String, LivingEntity>> it = jellies.entrySet().iterator();
        List<ServerPlayer> ps = players();
        while (it.hasNext()) {
            Map.Entry<String, LivingEntity> en = it.next();
            LivingEntity j = en.getValue();
            if (j == null || !j.m_6084_()) { Cmd.run(level, wx, wy, wz, "kill @e[tag=" + en.getKey() + "]"); it.remove(); continue; }
            ServerPlayer best = null; double bd = 1e9;
            Vec3 jp = j.m_20182_();
            for (ServerPlayer p : ps) { Vec3 v = p.m_20182_(); double d = (v.f_82479_ - jp.f_82479_) * (v.f_82479_ - jp.f_82479_) + (v.f_82480_ + 1 - jp.f_82480_) * (v.f_82480_ + 1 - jp.f_82480_) + (v.f_82481_ - jp.f_82481_) * (v.f_82481_ - jp.f_82481_); if (d < bd) { bd = d; best = p; } }
            if (best == null) continue;
            Vec3 v = best.m_20182_();
            double dx = v.f_82479_ - jp.f_82479_, dy = v.f_82480_ + 1.2 - jp.f_82480_, dz = v.f_82481_ - jp.f_82481_, l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double sp = 0.085 + (phase >= 4 ? 0.03 : 0);
            double bob = Math.sin((t + en.getKey().hashCode()) * 0.15) * 0.04;
            j.m_6034_(jp.f_82479_ + dx / l * sp, jp.f_82480_ + dy / l * sp + bob, jp.f_82481_ + dz / l * sp);
            j.m_20256_(new Vec3(0, 0, 0));
            if (t % 5 == 0) Fx.send(level, PINK, jp.f_82479_, jp.f_82480_ - 0.4, jp.f_82481_, 1, 0.1, 0.1, 0.1, 0);
            if (l < 1.4) {
                Fx.send(level, Fx.particle("explosion"), jp.f_82479_, jp.f_82480_, jp.f_82481_, 1, 0, 0, 0, 0);
                Fx.send(level, PINK, jp.f_82479_, jp.f_82480_, jp.f_82481_, 30, 0.6, 0.6, 0.6, 0.1);
                Fx.sound(level, jp.f_82479_, jp.f_82480_, jp.f_82481_, "entity.generic.explode", 2.0F, 1.4F);
                for (ServerPlayer p : ps) { Vec3 pv = p.m_20182_(); double ddx = pv.f_82479_ - jp.f_82479_, ddz = pv.f_82481_ - jp.f_82481_, ddy = pv.f_82480_ + 1 - jp.f_82480_;
                    if (ddx * ddx + ddy * ddy + ddz * ddz < 3.0 * 3.0) { hurt(p, 7); p.m_20256_(new Vec3(ddx * 0.3, 0.5, ddz * 0.3)); p.f_19864_ = true; } }
                Cmd.run(level, wx, wy, wz, "kill @e[tag=" + en.getKey() + "]");
                it.remove();
            }
        }
    }

    // ================= Фаза 4: шторм =================
    void tickStorm() {
        List<ServerPlayer> ps = players();
        if (--stormCd <= 0 && !ps.isEmpty()) {
            for (ServerPlayer p : ps) for (int i = 0; i < 2; i++) { Vec3 v = p.m_20182_(); strikes.add(new double[]{v.f_82479_ + RNG.nextGaussian() * (i == 0 ? 0.5 : 3), v.f_82481_ + RNG.nextGaussian() * (i == 0 ? 0.5 : 3), 0}); }
            stormCd = 70;
        }
        Iterator<double[]> it = strikes.iterator();
        while (it.hasNext()) {
            double[] s = it.next(); s[2]++;
            if (s[2] % 3 == 0) for (int i = 0; i < 14; i++) { double a = i * Math.PI / 7; Fx.send(level, WHITE, s[0] + Math.cos(a) * 2, FLOOR + 0.2, s[1] + Math.sin(a) * 2, 1, 0, 0, 0, 0); }
            if (s[2] % 4 == 0) Fx.send(level, Fx.particle("electric_spark"), s[0], FLOOR + 0.5, s[1], 6, 1, 0.3, 1, 0.05);
            if (s[2] >= 30) {
                Cmd.run(level, s[0], FLOOR, s[1], "summon minecraft:lightning_bolt " + Cmd.f(s[0]) + " " + FLOOR + " " + Cmd.f(s[1]));
                for (ServerPlayer p : ps) { Vec3 v = p.m_20182_(); double dx = v.f_82479_ - s[0], dz = v.f_82481_ - s[1]; if (dx * dx + dz * dz < 4 && v.f_82480_ < FLOOR + 3) hurt(p, 6); }
                it.remove();
            }
        }
        // шквал: ветер сдувает к краю платформы; присед почти спасает
        if (gustT < 0 && --gustCd <= 0) { gustT = 0; double a = RNG.nextDouble() * Math.PI * 2; gx = Math.cos(a); gz = Math.sin(a); gustCd = 320; }
        if (gustT >= 0) {
            gustT++;
            if (gustT == 1) { actionbar("ШКВАЛ! Присядь (Shift), чтобы не сдуло!", "red"); Fx.sound(level, AX, FLOOR + 5, AZ, "item.elytra.flying", 8.0F, 0.6F); }
            for (int i = 0; i < 10; i++) { double x = AX + (RNG.nextDouble() - 0.5) * ARENA_R * 2, z = AZ + (RNG.nextDouble() - 0.5) * ARENA_R * 2; Fx.send(level, Fx.particle("cloud"), x, FLOOR + 1 + RNG.nextDouble() * 3, z, 0, gx, 0, gz, 0.6); }
            if (gustT > 25) for (ServerPlayer p : ps) {
                double k = p.m_6144_() ? 0.012 : 0.06;
                p.m_20256_(p.m_20184_().m_82520_(gx * k, 0, gz * k)); p.f_19864_ = true;
            }
            if (gustT >= 95) gustT = -1;
        }
    }

    // ================= Атаки с высоты =================
    void sparkVolley() {
        List<ServerPlayer> ps = players();
        if (ps.isEmpty()) return;
        int n = phase >= 4 ? 9 : phase >= 2 ? 6 : 4;
        Fx.sound(level, wx, wy, wz, "block.amethyst_block.chime", 10.0F, 1.6F);
        for (int i = 0; i < n; i++) {
            ServerPlayer p = ps.get(RNG.nextInt(ps.size())); Vec3 v = p.m_20182_();
            double[] s = world((RNG.nextDouble() - 0.5) * 2, 1.8, 3 - RNG.nextDouble() * 7);
            double tx = v.f_82479_ + RNG.nextGaussian() * (i == 0 ? 0 : 2), ty = v.f_82480_ + 1, tz = v.f_82481_ + RNG.nextGaussian() * (i == 0 ? 0 : 2);
            double dx = tx - s[0], dy = ty - s[1], dz = tz - s[2], l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz)), sp = 0.7;
            sparks.add(new double[]{s[0], s[1], s[2], dx / l * sp, dy / l * sp, dz / l * sp, 0});
        }
    }

    void tickSparks() {
        Iterator<double[]> it = sparks.iterator();
        List<ServerPlayer> ps = players();
        outer:
        while (it.hasNext()) {
            double[] s = it.next();
            for (int k = 0; k < 2; k++) {
                s[0] += s[3] / 2; s[1] += s[4] / 2; s[2] += s[5] / 2;
                for (ServerPlayer p : ps) {
                    Vec3 v = p.m_20182_();
                    double dx = v.f_82479_ - s[0], dy = v.f_82480_ + 1 - s[1], dz = v.f_82481_ - s[2];
                    if (dx * dx + dy * dy + dz * dz < 1.1) {
                        hurt(p, 6); p.m_146917_(Math.min(200, 60 + p.m_146888_()));
                        Fx.send(level, Fx.particle("snowflake"), s[0], s[1], s[2], 15, 0.3, 0.3, 0.3, 0.1);
                        Fx.sound(level, s[0], s[1], s[2], "block.glass.break", 1.0F, 1.6F); it.remove(); continue outer;
                    }
                }
                if (s[1] < FLOOR + 0.2 || ++s[6] > 140) { Fx.send(level, Fx.particle("snowflake"), s[0], s[1] + 0.2, s[2], 8, 0.2, 0.1, 0.2, 0.05); it.remove(); continue outer; }
            }
            Fx.send(level, ICE, s[0], s[1], s[2], 1, 0, 0, 0, 0);
            Fx.send(level, Fx.particle("snowflake"), s[0], s[1], s[2], 1, 0, 0, 0, 0);
        }
    }

    /** Песня кита: звуковое кольцо расходится по полу от точки под китом. */
    void song() {
        songs.add(new double[]{wx, wz, 1.5});
        Fx.sound(level, wx, wy, wz, "entity.elder_guardian.ambient", 25.0F, 0.6F);
        Fx.sound(level, wx, FLOOR, wz, "entity.warden.sonic_boom", 10.0F, 1.4F);
        actionbar("Песня кита! Прыгай через волну!", "aqua");
    }

    void tickSongs() {
        Iterator<double[]> it = songs.iterator();
        while (it.hasNext()) {
            double[] w = it.next(); w[2] += 0.5;
            int n = (int) (18 + w[2] * 4);
            for (int i = 0; i < n; i++) { double a = i * Math.PI * 2 / n; Fx.send(level, i % 3 == 0 ? WHITE : SKY, w[0] + Math.cos(a) * w[2], FLOOR + 0.3, w[1] + Math.sin(a) * w[2], 1, 0, 0, 0, 0); }
            for (ServerPlayer p : players()) {
                Vec3 v = p.m_20182_(); double d = Math.sqrt((v.f_82479_ - w[0]) * (v.f_82479_ - w[0]) + (v.f_82481_ - w[1]) * (v.f_82481_ - w[1]));
                if (Math.abs(d - w[2]) < 0.8 && v.f_82480_ < FLOOR + 0.7) { int last = hitCd.getOrDefault(p, -100); if (t - last > 10) { hitCd.put(p, t); hurt(p, 8); p.m_20256_(new Vec3(0, 0.6, 0)); p.f_19864_ = true; } }
            }
            if (w[2] > ARENA_R + 4) it.remove();
        }
    }

    // ================= Гибель =================
    void startDeath() {
        stage = 3; dyingT = 0;
        sparks.clear(); songs.clear(); strikes.clear(); gustT = -1;
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
        Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=" + TAG + "_min]");
        for (String id : jellies.keySet()) Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=" + id + "]");
        jellies.clear();
        Fx.sound(level, wx, wy, wz, "entity.elder_guardian.death", 30.0F, 0.4F);
        Fx.sound(level, wx, wy, wz, "entity.dolphin.death", 30.0F, 0.4F);
    }

    boolean dying() {
        dyingT++;
        // медленно опускается на платформу, переворачиваясь
        wy += (FLOOR + 5 - wy) * 0.05;
        double a = dyingT * 0.03; vx = Math.cos(a) * 0.3; vz = Math.sin(a) * 0.3; vy = -0.95;
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz); vx /= vl; vy /= vl; vz /= vl;
        gs = Math.max(0.05, 1.0 - Math.max(0, dyingT - 40) / 80.0);
        place();
        if (dyingT % 3 == 0) pose(3);
        if (dyingT % 4 == 0) {
            double[] u = Fx.unit();
            Fx.send(level, Fx.particle("splash"), wx + u[0] * 4, wy + u[1] * 3, wz + u[2] * 4, 30, 0.6, 0.6, 0.6, 0.3);
            Fx.send(level, SKY, wx, wy, wz, 20, 3, 2, 3, 0.1);
            Fx.sound(level, wx, wy, wz, "entity.generic.splash", 6.0F, 0.4F + dyingT / 150F);
        }
        if (dyingT == 120) {
            dropHitboxes();
            Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=" + TAG + "]");
            Fx.send(level, Fx.particle("flash"), wx, wy, wz, 5, 2, 2, 2, 0);
            Fx.send(level, Fx.particle("explosion_emitter"), wx, wy, wz, 3, 2, 2, 2, 0);
            for (int i = 0; i < 250; i++) { double[] u = Fx.unit(); Fx.send(level, i % 2 == 0 ? WHITE : SKY, wx, wy, wz, 0, u[0], u[1], u[2], 1.6); }
            Fx.sound(level, wx, wy, wz, "entity.generic.explode", 30.0F, 0.6F);
            Fx.sound(level, wx, wy, wz, "ui.toast.challenge_complete", 20.0F, 1.0F);
            Loot.chest(level, 0, FLOOR, 5, "Добыча Небесного Кита", List.of(
                    "explosionwand:whale_staff", "3 minecraft:nether_star", "2 minecraft:netherite_block", "2 minecraft:heart_of_the_sea",
                    "minecraft:trident", "minecraft:elytra", "6 minecraft:enchanted_golden_apple", "24 minecraft:diamond", "16 minecraft:prismarine_crystals"));
            String at = Cmd.f(AX) + " " + (FLOOR + 1) + " " + Cmd.f(AZ + 3);
            for (int i = 0; i < 16; i++) Cmd.run(level, AX, FLOOR, AZ, "summon experience_orb " + at + " {Value:500}");
            Cmd.run(level, AX, FLOOR, AZ, sel("title") + " subtitle {\"text\":\"Добыча в сундуке, портал домой — в центре\",\"color\":\"gray\"}");
            Cmd.run(level, AX, FLOOR, AZ, sel("title") + " title {\"text\":\"Небесный Кит уснул в облаках\",\"color\":\"aqua\",\"bold\":true}");
            Arena4.cleared = true;
            return true;
        }
        return false;
    }

    /** Слаймы-хитбоксы убираем через discard: при kill большой слайм делится на маленьких. */
    void dropHitboxes() {
        for (LivingEntity e : hits) if (e != null) e.m_146870_();
        for (Entity e : Cmd.tagged(level, AX, FLOOR, AZ, 120, TAG + "_hit")) e.m_146870_();
        hits.clear();
    }

    void reset() {
        dropHitboxes();
        Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=" + TAG + "]");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
    }

    static void onHurt(LivingHurtEvent ev) {
        Boss4 b = ACTIVE;
        if (b == null) return;
        LivingEntity target = ev.getEntity();
        boolean hitbox = Cmd.hasTagPrefix(target, TAG + "_hit");
        if (hitbox) {
            ev.setCanceled(true);
            if (!Boss3.environmental(ev) && b.host != null && b.host.m_6084_()) b.host.m_6469_(ev.getSource(), ev.getAmount());
            return;
        }
        if (target != b.host) {
            if (Cmd.hasTagPrefix(target, TAG + "_min") && (Boss3.environmental(ev) || (ev.getSource() != null && ev.getSource().m_7639_() != null && Cmd.hasTagPrefix(ev.getSource().m_7639_(), TAG)))) ev.setCanceled(true);
            return;
        }
        if (Boss3.environmental(ev) || !b.vulnerable()) {
            ev.setCanceled(true);
            Fx.send(b.level, WHITE, b.wx, b.wy, b.wz, 10, 2, 1.5, 3, 0);
            return;
        }
        float k = b.state == S.EXPOSED ? 1.3F : 0.5F;
        ev.setAmount(ev.getAmount() * k * (HOST_HP / MAX_HP));
        Fx.send(b.level, Fx.particle("splash"), b.wx, b.wy, b.wz, 30, 2, 1.5, 3, 0.2);
        Fx.send(b.level, SKY, b.wx, b.wy, b.wz, 10, 2, 1.5, 3, 0.05);
        Fx.sound(b.level, b.wx, b.wy, b.wz, "entity.dolphin.hurt", 3.0F, 0.5F + RNG.nextFloat() * 0.2F);
    }
}
