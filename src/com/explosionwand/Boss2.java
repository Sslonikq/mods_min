package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * «Король Затмения» — чёрное солнце с золотой короной, восемью лучами, тремя лунами и двумя
 * огромными парящими руками. 5 фаз:
 *  1 Рассвет — удары руками по игрокам, лучи из лун.
 *  2 Затмение — + метеоритный дождь, хлопок ладонями, накатывающая тьма.
 *  3 Луны — король под щитом, три луны спускаются на арену: их надо разбить.
 *  4 Сверхновая — из пола встают стены; периодически вспышка испепеляет всех, кто не спрятался.
 *  5 Коллапс — арена рушится в бездну от краёв, всё тянет к королю, атаки быстрее.
 */
public class Boss2 {
    static Boss2 ACTIVE;
    static final Random RNG = Fx.RNG;
    static final double AX = 0.5, AZ = 0.5;
    static final int FLOOR = 64, N = 33, ARENA_R = 32;
    static final float MAX_HP = 1024;
    static final String BAR = "explosionwand:eclipse_king";
    static final String[] BLOCKS = new String[N];
    static {
        BLOCKS[0] = BLOCKS[1] = BLOCKS[2] = "black_concrete"; BLOCKS[3] = "shroomlight";
        for (int i = 4; i < 16; i++) BLOCKS[i] = (i % 2 == 0) ? "gold_block" : "shroomlight";
        for (int i = 16; i < 24; i++) BLOCKS[i] = "glowstone";
        for (int i = 24; i < 27; i++) BLOCKS[i] = "calcite";
        BLOCKS[27] = BLOCKS[30] = "polished_blackstone"; BLOCKS[28] = BLOCKS[31] = "polished_blackstone_bricks"; BLOCKS[29] = BLOCKS[32] = "gold_block";
    }
    static ParticleOptions GOLD, ORANGE, RED, WHITE, BLACK, PALE;

    final ServerLevel level;
    LivingEntity host;
    final String[] uuid = new String[N];
    int t = 0, stage = 0, dyingT = 0;
    double bx = AX, by = FLOOR + 1, bz = AZ, orbit = 0;
    double ex = 0, ez = 1;
    int phase = 1, stun = 0;
    boolean shield = false, moonsDone = false, novaPhase = false, collapse = false;
    int moonsT = 0;
    int slamCd = 60, beamCd = 90, meteorCd = 120, clapCd = 200, novaCd = 100, novaT = -1, collapseR = ARENA_R, collapseCd = 0;
    // руки: x,y,z текущие; tx,ty,tz цель; режим, таймер, цель-x, цель-z
    final double[][] hand = new double[2][6];
    final int[] hMode = new int[2], hT = new int[2];
    final double[][] hTarget = new double[2][2];
    double clapX, clapZ, clapAx, clapAz; int clapT = -1;
    final List<double[]> beams = new ArrayList<>();    // sx,sy,sz,tx,ty,tz,timer
    final List<double[]> meteors = new ArrayList<>();  // x,y,z,vx,vy,vz,tx,tz
    final List<LivingEntity> moons = new ArrayList<>();
    final List<double[]> moonPos = new ArrayList<>();
    final Map<ServerPlayer, Integer> hitCd = new WeakHashMap<>();
    final List<BlockPos> walls = new ArrayList<>();

    static void init() {
        if (GOLD != null) return;
        GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.8F, 0.2F), 1.8F);
        ORANGE = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.05F), 1.8F);
        RED = new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.05F), 1.8F);
        WHITE = new DustParticleOptions(new Vector3f(1, 1, 0.95F), 1.6F);
        BLACK = new DustParticleOptions(new Vector3f(0.02F, 0.02F, 0.02F), 3.0F);
        PALE = new DustParticleOptions(new Vector3f(0.9F, 0.9F, 1.0F), 1.4F);
    }

    Boss2(ServerLevel level) { this.level = level; }
    double cy() { return by + 8; }
    double anchorY() { return by + 9; }

    static void spawn(ServerLevel level) {
        init();
        Boss2 b = new Boss2(level);
        ACTIVE = b;
        StringBuilder sb = new StringBuilder();
        sb.append("summon minecraft:giant ").append(Cmd.f(AX)).append(" ").append(FLOOR + 1).append(" ").append(Cmd.f(AZ))
          .append(" {Tags:[\"ewb2\",\"ewb2_host\"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,")
          .append("Attributes:[{Name:\"minecraft:generic.max_health\",Base:").append((int) MAX_HP).append("d},{Name:\"minecraft:generic.knockback_resistance\",Base:1d}],")
          .append("Health:").append((int) MAX_HP).append("f,ActiveEffects:[{Id:14,Amplifier:0b,Duration:999999,ShowParticles:0b}],Passengers:[");
        for (int i = 0; i < N; i++) {
            if (i > 0) sb.append(",");
            sb.append("{id:\"minecraft:block_display\",Tags:[\"ewb2\",\"ewb2_p").append(i).append("\"],")
              .append(Disp.block(BLOCKS[i])).append(",view_range:5f,brightness:{sky:15,block:15},")
              .append(Disp.tf(0, -1, 0, 0.01, 0.01, 0.01, Disp.ID)).append("}");
        }
        sb.append("]}");
        Cmd.run(level, AX, FLOOR, AZ, sb.toString());
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
        Cmd.run(level, AX, FLOOR, AZ, "bossbar add " + BAR + " {\"text\":\"Король Затмения\",\"color\":\"gold\",\"bold\":true}");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " color yellow");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " style notched_20");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " max 1000");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " value 1000");
        Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..200] times 10 80 20");
        Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..200] subtitle {\"text\":\"Солнце, которое съело свет\",\"color\":\"gray\",\"italic\":true}");
        Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..200] title {\"text\":\"Король Затмения\",\"color\":\"gold\",\"bold\":true}");
        Fx.sound(level, AX, FLOOR + 9, AZ, "entity.wither.spawn", 30.0F, 0.4F);
        Fx.sound(level, AX, FLOOR + 9, AZ, "entity.ender_dragon.growl", 30.0F, 0.5F);
        Fx.send(level, Fx.particle("flash"), AX, FLOOR + 9, AZ, 3, 2, 2, 2, 0);
        for (int i = 0; i < 2; i++) { b.hand[i][0] = AX + (i == 0 ? -8 : 8); b.hand[i][1] = FLOOR + 8; b.hand[i][2] = AZ; }
    }

    List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) if (p.m_6084_() && !p.m_5833_()) out.add(p);
        return out;
    }
    boolean vulnerable() { return stage == 2 && !shield; }
    void hurt(ServerPlayer p, float d) { p.m_6469_(level.m_269111_().m_269425_(), d); }
    double rate() { return collapse ? 0.6 : 1.0; }
    void actionbar(String s, String c) { Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..200] actionbar {\"text\":\"" + s + "\",\"color\":\"" + c + "\"}"); }

    // ================= Тик =================
    boolean tick() {
        t++;
        if (stage == 0) {
            List<Entity> h = Cmd.tagged(level, AX, FLOOR + 1, AZ, 20, "ewb2_host");
            if (h.isEmpty() || !(h.get(0) instanceof LivingEntity le)) { if (t > 40) { reset(); return true; } return false; }
            host = le;
            for (int i = 0; i < N; i++) { List<Entity> p = Cmd.tagged(level, AX, FLOOR + 5, AZ, 30, "ewb2_p" + i); if (!p.isEmpty()) uuid[i] = p.get(0).m_20149_(); }
            stage = 1; t = 0; pose(50, 1.0, true);
            return false;
        }
        if (host == null) { reset(); return true; }
        if (stage == 3) return dying();
        if (host.m_21223_() <= 0 || host.m_21224_()) { startDeath(); return false; }
        if (!host.m_6084_()) { reset(); return true; }
        if (host.m_6060_()) host.m_20095_();
        move();
        moveHands();
        if (t % 4 == 0) pose(4, 1.0, false);
        poseHands();
        aura();
        if (t % 10 == 0) bar();
        if (stage == 1) { if (t >= 60) { stage = 2; t = 0; } return false; }

        float frac = host.m_21223_() / MAX_HP;
        if (phase == 1 && frac <= 0.8F) { phase = 2; actionbar("Затмение начинается...", "gold"); Fx.sound(level, AX, cy(), AZ, "entity.wither.ambient", 20.0F, 0.5F); }
        if (phase == 2 && frac <= 0.6F && !moonsDone) startMoons();
        if (phase == 3 && moonsDone && !shield && frac <= 0.4F) startNova();
        if (phase == 4 && frac <= 0.2F) startCollapse();

        tickBeams(); tickMeteors(); tickClap();
        if (phase == 3 && shield) tickMoons();
        if (collapse) tickCollapse();
        if (novaT >= 0) tickNova();
        if (stun > 0) { stun--; dizzy(); return false; }
        attacks();
        return false;
    }

    // ================= Движение и поза =================
    void move() {
        double tx, ty, tz, k = 0.04;
        if (shield) { tx = AX; tz = AZ; ty = FLOOR + 14; }
        else if (stun > 0) { tx = bx; tz = bz; ty = FLOOR - 3; k = 0.2; }
        else if (novaT >= 0) { tx = AX; tz = AZ; ty = FLOOR + 4; }
        else {
            orbit += collapse ? 0.008 : 0.004;
            double r = collapse ? 5 : 10;
            tx = AX + Math.cos(orbit) * r; tz = AZ + Math.sin(orbit) * r;
            ty = FLOOR + 1 + Math.sin(t * 0.04) * 0.8;
        }
        bx += (tx - bx) * k; by += (ty - by) * k; bz += (tz - bz) * k;
        host.m_6034_(bx, by, bz);
        host.m_20256_(new Vec3(0, 0, 0));
        ServerPlayer n = nearest();
        if (n != null) {
            Vec3 p = n.m_20182_(); double dx = p.f_82479_ - bx, dz = p.f_82481_ - bz, l = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
            ex += (dx / l - ex) * 0.08; ez += (dz / l - ez) * 0.08;
            double el = Math.max(1e-3, Math.sqrt(ex * ex + ez * ez)); ex /= el; ez /= el;
        }
    }

    ServerPlayer nearest() {
        ServerPlayer best = null; double bd = 1e9;
        for (ServerPlayer p : players()) { Vec3 v = p.m_20182_(); double d = (v.f_82479_ - bx) * (v.f_82479_ - bx) + (v.f_82481_ - bz) * (v.f_82481_ - bz); if (d < bd) { bd = d; best = p; } }
        return best;
    }

    void send(int i, int dur, double cx, double cyy, double cz, double sx, double sy, double sz, double[] q) {
        if (uuid[i] == null) return;
        Cmd.run(level, bx, by, bz, "data merge entity " + uuid[i] + " {start_interpolation:0,interpolation_duration:" + dur + "," + Disp.tf(cx, cyy, cz, Math.max(0.001, sx), Math.max(0.001, sy), Math.max(0.001, sz), q) + "}");
    }

    /** Тело: ядро, глаз, корона, лучи, луны (руки — отдельно). */
    void pose(int dur, double gs, boolean withHands) {
        double T = (t + (stage == 3 ? dyingT * 8 : 0)) * (collapse ? 1.8 : 1.0);
        double yawE = Math.atan2(ex, ez);
        double[] qY = Disp.q(0, 1, 0, yawE);
        double C = -1;   // центр тела относительно точки посадки
        double core = (novaT >= 0 ? 5.6 + Math.sin(t * 0.8) * 0.3 : 5.0) * gs;
        double[] spin = Disp.q(0, 1, 0, T * 0.02);
        send(0, dur, 0, C, 0, core, core, core, spin);
        send(1, dur, 0, C, 0, core, core, core, Disp.mul(spin, Disp.q(1, 0, 0, Math.PI / 4)));
        send(2, dur, 0, C, 0, core, core, core, Disp.mul(spin, Disp.q(0, 0, 1, Math.PI / 4)));
        double[] eye = Disp.rot(qY, 0, 0, 3.0 * gs);
        send(3, dur, eye[0], C + eye[1], eye[2], 0.7 * gs, 3.0 * gs, 0.4 * gs, qY);
        double cr = (stage == 3 ? 4.6 + dyingT * 0.08 : 4.6) * gs;
        for (int i = 4; i < 16; i++) {
            double a = (i - 4) * Math.PI * 2 / 12 + T * 0.03;
            double[] p = Disp.rot(qY, Math.cos(a) * cr, Math.sin(a) * cr, 0);
            send(i, dur, p[0], C + p[1], p[2], 1.3 * gs, 0.55 * gs, 0.8 * gs, Disp.mul(qY, Disp.q(0, 0, 1, a + Math.PI / 2)));
        }
        double rr = 6.6 * gs;
        for (int i = 16; i < 24; i++) {
            double a = (i - 16) * Math.PI * 2 / 8 - T * 0.02;
            double len = (2.6 + Math.sin(T * 0.1 + i) * 0.5) * gs;
            double[] p = Disp.rot(qY, Math.cos(a) * rr, Math.sin(a) * rr, 0);
            send(i, dur, p[0], C + p[1], p[2], 0.45 * gs, len, 0.45 * gs, Disp.mul(qY, Disp.q(0, 0, 1, a - Math.PI / 2)));
        }
        double ms = (shield || moonsDone) ? 0.001 : 1.6 * gs;
        for (int i = 24; i < 27; i++) {
            double[] mp = moonLocal(i - 24, T);
            send(i, dur, mp[0] * gs, C + mp[1] * gs, mp[2] * gs, ms, ms, ms, Disp.q(1, 1, 0, T * 0.05 + i));
        }
        if (withHands) for (int h = 0; h < 2; h++) poseHand(h, dur, gs);
    }

    double[] moonLocal(int k, double T) {
        double a = k * Math.PI * 2 / 3 + T * 0.025;
        return new double[]{Math.cos(a) * 9, Math.sin(a * 2 + T * 0.02) * 1.5, Math.sin(a) * 9};
    }
    double[] moonWorld(int k) { double[] m = moonLocal(k, t * (collapse ? 1.8 : 1.0)); return new double[]{bx + m[0], anchorY() - 1 + m[1], bz + m[2]}; }

    void poseHands() { for (int h = 0; h < 2; h++) poseHand(h, 2, 1.0); }

    void poseHand(int h, int dur, double gs) {
        int base = 27 + h * 3;
        double hx = hand[h][0] - bx, hy = hand[h][1] - anchorY(), hz = hand[h][2] - bz;
        double yawE = Math.atan2(ex, ez);
        double[] qY = Disp.q(0, 1, 0, yawE + (h == 0 ? 0.3 : -0.3));
        double[] f = Disp.rot(qY, 0, 0, 1);
        send(base, dur, hx * gs, hy * gs, hz * gs, 3.2 * gs, 1.0 * gs, 3.4 * gs, qY);
        send(base + 1, dur, (hx + f[0] * 2.5) * gs, (hy - 0.1) * gs, (hz + f[2] * 2.5) * gs, 3.2 * gs, 0.8 * gs, 1.6 * gs, qY);
        send(base + 2, dur, (hx - f[0] * 2.2) * gs, hy * gs, (hz - f[2] * 2.2) * gs, 3.4 * gs, 1.1 * gs, 1.0 * gs, qY);
    }

    void moveHands() {
        double yawE = Math.atan2(ex, ez);
        for (int h = 0; h < 2; h++) {
            double[] H = hand[h];
            double tx, ty, tz, k = 0.12;
            switch (hMode[h]) {
                case 1 -> { tx = hTarget[h][0]; tz = hTarget[h][1]; ty = FLOOR + 10; k = 0.18; }
                case 2 -> { tx = hTarget[h][0]; tz = hTarget[h][1]; ty = FLOOR + 0.5; k = 0.55; }
                case 3 -> { tx = H[0]; ty = FLOOR + 0.5; tz = H[2]; }
                case 4 -> { tx = hTarget[h][0]; ty = H[1]; tz = hTarget[h][1]; k = 0.35; }
                default -> {
                    double[] side = Disp.rot(Disp.q(0, 1, 0, yawE), h == 0 ? 8 : -8, 0, 2.5);
                    tx = bx + side[0]; tz = bz + side[2]; ty = cy() - 2 + Math.sin(t * 0.07 + h * 2) * 0.8;
                }
            }
            H[0] += (tx - H[0]) * k; H[1] += (ty - H[1]) * k; H[2] += (tz - H[2]) * k;
            if (hMode[h] == 1 || hMode[h] == 2 || hMode[h] == 3) {
                hT[h]++;
                if (hMode[h] == 1) {
                    double r = 3.8 * (1 - hT[h] / 28.0) + 0.6;
                    for (int i = 0; i < 24; i++) { double a = i * Math.PI * 2 / 24; Fx.send(level, collapse ? RED : ORANGE, hTarget[h][0] + Math.cos(a) * 3.8, FLOOR + 0.15, hTarget[h][1] + Math.sin(a) * 3.8, 1, 0, 0, 0, 0); }
                    Fx.send(level, BLACK, hTarget[h][0], FLOOR + 0.12, hTarget[h][1], 8, r * 0.5, 0, r * 0.5, 0);
                    if (hT[h] >= 28) { hMode[h] = 2; hT[h] = 0; }
                } else if (hMode[h] == 2 && hT[h] >= 4) {
                    hMode[h] = 3; hT[h] = 0; slamImpact(hTarget[h][0], hTarget[h][1]);
                } else if (hMode[h] == 3 && hT[h] >= 14) { hMode[h] = 0; hT[h] = 0; }
            }
        }
    }

    void slamImpact(double x, double z) {
        Fx.send(level, Fx.particle("explosion_emitter"), x, FLOOR + 0.5, z, 1, 0, 0, 0, 0);
        Fx.send(level, Fx.particle("campfire_cosy_smoke"), x, FLOOR + 0.3, z, 20, 2, 0.2, 2, 0.03);
        for (int i = 0; i < 40; i++) { double a = i * Math.PI * 2 / 40; Fx.send(level, Fx.particle("cloud"), x + Math.cos(a) * 2, FLOOR + 0.3, z + Math.sin(a) * 2, 0, Math.cos(a), 0.05, Math.sin(a), 0.8); }
        Fx.sound(level, x, FLOOR, z, "entity.generic.explode", 10.0F, 0.5F);
        Fx.sound(level, x, FLOOR, z, "block.anvil.land", 8.0F, 0.4F);
        for (ServerPlayer p : players()) {
            Vec3 v = p.m_20182_(); double dx = v.f_82479_ - x, dz = v.f_82481_ - z, d = Math.sqrt(dx * dx + dz * dz);
            if (d < 4.0 && v.f_82480_ < FLOOR + 3.5) { hurt(p, collapse ? 15 : 12); p.m_20256_(new Vec3(dx / Math.max(d, 0.3) * 1.2, 0.9, dz / Math.max(d, 0.3) * 1.2)); p.f_19864_ = true; }
        }
    }

    void aura() {
        double c = cy();
        if (t % 2 == 0) Fx.send(level, collapse ? RED : GOLD, bx, c, bz, 6, 4, 4, 4, 0);
        Fx.send(level, BLACK, bx, c, bz, 3, 2.5, 2.5, 2.5, 0);
        Fx.send(level, Fx.particle("flame"), bx, c, bz, 2, 4, 4, 4, 0.02);
        if (t % 50 == 0) Fx.sound(level, bx, c, bz, "block.beacon.ambient", 10.0F, collapse ? 0.8F : 0.5F);
        if (shield) for (int i = 0; i < 40; i++) { double[] u = Fx.unit(); Fx.send(level, PALE, bx + u[0] * 8, c + u[1] * 8, bz + u[2] * 8, 1, 0, 0, 0, 0); }
    }

    void bar() {
        int v = (int) Math.max(0, Math.min(1000, host.m_21223_() / MAX_HP * 1000));
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " value " + v);
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " players @a[x=0,y=64,z=0,distance=..200]");
    }

    void dizzy() {
        for (int i = 0; i < 6; i++) { double a = t * 0.3 + i * Math.PI / 3; Fx.send(level, Fx.particle("crit"), bx + Math.cos(a) * 3, cy() + 6, bz + Math.sin(a) * 3, 1, 0, 0, 0, 0); }
    }

    // ================= Атаки =================
    void attacks() {
        List<ServerPlayer> ps = players();
        if (ps.isEmpty()) return;
        if (--slamCd <= 0) {
            int h = hMode[0] == 0 ? 0 : (hMode[1] == 0 ? 1 : -1);
            if (h >= 0) {
                ServerPlayer p = ps.get(RNG.nextInt(ps.size())); Vec3 v = p.m_20182_();
                hMode[h] = 1; hT[h] = 0; hTarget[h][0] = v.f_82479_; hTarget[h][1] = v.f_82481_;
                Fx.sound(level, v.f_82479_, FLOOR, v.f_82481_, "entity.warden.roar", 3.0F, 1.4F);
                if (collapse && hMode[1 - h] == 0) {
                    ServerPlayer p2 = ps.get(RNG.nextInt(ps.size())); Vec3 v2 = p2.m_20182_();
                    hMode[1 - h] = 1; hT[1 - h] = -10; hTarget[1 - h][0] = v2.f_82479_ + (RNG.nextDouble() - 0.5) * 6; hTarget[1 - h][1] = v2.f_82481_ + (RNG.nextDouble() - 0.5) * 6;
                }
            }
            slamCd = (int) ((phase >= 4 ? 70 : 95) * rate());
        }
        if (!moonsDone && phase <= 2 && --beamCd <= 0) {
            for (int k = 0; k < 3; k++) { ServerPlayer p = ps.get(RNG.nextInt(ps.size())); double[] m = moonWorld(k); Vec3 v = p.m_20182_(); beams.add(new double[]{m[0], m[1], m[2], v.f_82479_, v.f_82480_ + 1, v.f_82481_, 0}); }
            Fx.sound(level, bx, cy(), bz, "block.beacon.power_select", 10.0F, 1.6F);
            beamCd = 100;
        }
        if (phase >= 2 && --meteorCd <= 0) {
            int n = collapse ? 8 : 5;
            for (int i = 0; i < n; i++) {
                ServerPlayer p = ps.get(RNG.nextInt(ps.size())); Vec3 v = p.m_20182_();
                double tx = v.f_82479_ + (RNG.nextDouble() - 0.5) * 9, tz = v.f_82481_ + (RNG.nextDouble() - 0.5) * 9;
                double sx = tx + (RNG.nextDouble() - 0.5) * 30, sz = tz + (RNG.nextDouble() - 0.5) * 30, sy = FLOOR + 45 + RNG.nextDouble() * 10;
                double steps = 30 + RNG.nextInt(20);
                meteors.add(new double[]{sx, sy, sz, (tx - sx) / steps, (FLOOR - sy) / steps, (tz - sz) / steps, tx, tz});
            }
            var dark = Fx.effect("darkness");
            if (dark != null && phase == 2) for (ServerPlayer p : ps) p.m_7292_(new MobEffectInstance(dark, 80, 0));
            Fx.sound(level, bx, cy(), bz, "entity.lightning_bolt.thunder", 15.0F, 0.5F);
            meteorCd = (int) (170 * rate());
        }
        if (phase >= 4 && novaT < 0 && --novaCd <= 0) { novaT = 0; novaCd = (int) (520 * rate()); }
        if (phase >= 2 && clapT < 0 && hMode[0] == 0 && hMode[1] == 0 && --clapCd <= 0) {
            ServerPlayer p = ps.get(RNG.nextInt(ps.size())); Vec3 v = p.m_20182_();
            clapX = v.f_82479_; clapZ = v.f_82481_;
            double a = RNG.nextDouble() * Math.PI; clapAx = Math.cos(a); clapAz = Math.sin(a);
            clapT = 0; clapCd = (int) (260 * rate());
            actionbar("Руки сходятся — беги вдоль золотой линии!", "gold");
        }
    }

    void tickClap() {
        if (clapT < 0) return;
        clapT++;
        double y = FLOOR + 1.2;
        if (clapT < 28) {
            for (int h = 0; h < 2; h++) { double s = h == 0 ? 13 : -13; hMode[h] = 4; hTarget[h][0] = clapX + clapAx * s; hTarget[h][1] = clapZ + clapAz * s; hand[h][1] += (y - hand[h][1]) * 0.2; }
            for (double d = -13; d <= 13; d += 0.8) Fx.send(level, GOLD, clapX + clapAx * d, FLOOR + 0.2, clapZ + clapAz * d, 1, 0, 0, 0, 0);
            if (clapT == 1) Fx.sound(level, clapX, FLOOR, clapZ, "block.beacon.activate", 6.0F, 1.6F);
        } else if (clapT < 34) {
            for (int h = 0; h < 2; h++) { hTarget[h][0] = clapX + clapAx * (h == 0 ? 0.8 : -0.8); hTarget[h][1] = clapZ + clapAz * (h == 0 ? 0.8 : -0.8); }
        } else if (clapT == 34) {
            Fx.send(level, Fx.particle("explosion_emitter"), clapX, y, clapZ, 1, 0, 0, 0, 0);
            Fx.send(level, Fx.particle("sonic_boom"), clapX, y, clapZ, 1, 0, 0, 0, 0);
            Fx.sound(level, clapX, y, clapZ, "entity.generic.explode", 12.0F, 0.6F);
            Fx.sound(level, clapX, y, clapZ, "entity.warden.sonic_boom", 8.0F, 0.6F);
            for (ServerPlayer p : players()) {
                Vec3 v = p.m_20182_();
                double rx = v.f_82479_ - clapX, rz = v.f_82481_ - clapZ;
                double along = Math.abs(rx * clapAx + rz * clapAz), lat = Math.abs(-rx * clapAz + rz * clapAx);
                if (along < 12 && lat < 2.5 && v.f_82480_ < FLOOR + 5) { hurt(p, 14); p.m_20256_(new Vec3(-clapAz * Math.signum(-rx * clapAz + rz * clapAx) * 1.5, 0.8, clapAx * Math.signum(-rx * clapAz + rz * clapAx) * 1.5)); p.f_19864_ = true; }
            }
        } else if (clapT > 44) { hMode[0] = hMode[1] = 0; clapT = -1; }
    }

    void tickBeams() {
        Iterator<double[]> it = beams.iterator();
        while (it.hasNext()) {
            double[] b = it.next();
            b[6]++;
            double dx = b[3] - b[0], dy = b[4] - b[1], dz = b[5] - b[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            boolean fire = b[6] > 18;
            double ext = 1.6;   // луч бьёт чуть дальше цели
            for (double s = 0; s < l * ext; s += fire ? 0.4 : 1.0)
                Fx.send(level, fire ? (collapse ? RED : WHITE) : GOLD, b[0] + dx * s / l, b[1] + dy * s / l, b[2] + dz * s / l, 1, 0, 0, 0, 0);
            if (b[6] == 19) {
                Fx.sound(level, b[3], b[4], b[5], "entity.guardian.attack", 4.0F, 1.6F);
                for (ServerPlayer p : players()) {
                    Vec3 v = p.m_20182_();
                    double rx = v.f_82479_ - b[0], ry = v.f_82480_ + 1 - b[1], rz = v.f_82481_ - b[2];
                    double al = (rx * dx + ry * dy + rz * dz) / l;
                    if (al < 0 || al > l * ext) continue;
                    double qx = rx - dx / l * al, qy = ry - dy / l * al, qz = rz - dz / l * al;
                    if (qx * qx + qy * qy + qz * qz < 1.1) { hurt(p, 8); p.m_20254_(3); }
                }
            }
            if (b[6] > 24) it.remove();
        }
    }

    void tickMeteors() {
        Iterator<double[]> it = meteors.iterator();
        while (it.hasNext()) {
            double[] m = it.next();
            m[0] += m[3]; m[1] += m[4]; m[2] += m[5];
            Fx.send(level, Fx.particle("flame"), m[0], m[1], m[2], 6, 0.3, 0.3, 0.3, 0.02);
            Fx.send(level, Fx.particle("large_smoke"), m[0], m[1], m[2], 2, 0.3, 0.3, 0.3, 0.01);
            Fx.send(level, ORANGE, m[0], m[1], m[2], 3, 0.4, 0.4, 0.4, 0);
            for (int i = 0; i < 16; i++) { double a = i * Math.PI * 2 / 16; Fx.send(level, RED, m[6] + Math.cos(a) * 2.5, FLOOR + 0.15, m[7] + Math.sin(a) * 2.5, 1, 0, 0, 0, 0); }
            if (m[1] <= FLOOR + 0.5) {
                level.m_255391_(null, m[6], FLOOR + 0.5, m[7], 3.0F, false, Level.ExplosionInteraction.valueOf("NONE"));
                Fx.send(level, Fx.particle("lava"), m[6], FLOOR + 0.5, m[7], 20, 1, 0.3, 1, 0);
                Fx.send(level, Fx.particle("flame"), m[6], FLOOR + 0.5, m[7], 40, 1.5, 0.3, 1.5, 0.1);
                it.remove();
            }
        }
    }

    // ================= Фаза 3: луны =================
    void startMoons() {
        phase = 3; shield = true; moonsT = t;
        actionbar("Король под щитом! Разбей три луны!", "yellow");
        Fx.sound(level, AX, cy(), AZ, "block.beacon.activate", 20.0F, 0.5F);
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI * 2 / 3;
            double x = AX + Math.cos(a) * 15, z = AZ + Math.sin(a) * 15;
            Cmd.run(level, x, FLOOR, z, "summon minecraft:iron_golem " + Cmd.f(x) + " " + (FLOOR + 0.5) + " " + Cmd.f(z)
                    + " {Tags:[\"ewb2\",\"ewb2_moon\",\"ewb2_moon" + k + "\"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,"
                    + "Attributes:[{Name:\"minecraft:generic.max_health\",Base:90d},{Name:\"minecraft:generic.knockback_resistance\",Base:1d}],Health:90f,"
                    + "ActiveEffects:[{Id:14,Amplifier:0b,Duration:999999,ShowParticles:0b}],Passengers:["
                    + "{id:\"minecraft:block_display\",Tags:[\"ewb2\"],block_state:{Name:\"minecraft:calcite\"},view_range:4f,brightness:{sky:15,block:15}," + Disp.tf(0, -0.7, 0, 2.6, 2.6, 2.6, Disp.q(1, 1, 0, 0.6)) + "},"
                    + "{id:\"minecraft:block_display\",Tags:[\"ewb2\"],block_state:{Name:\"minecraft:redstone_block\"},view_range:4f,brightness:{sky:15,block:15}," + Disp.tf(0, -0.7, 0, 2.8, 0.5, 0.5, Disp.ID) + "}]}");
            moonPos.add(new double[]{a});
            Fx.send(level, Fx.particle("flash"), x, FLOOR + 2, z, 1, 0, 0, 0, 0);
        }
        Later.run(level, 3, () -> {
            for (int k = 0; k < 3; k++) {
                List<Entity> f = Cmd.tagged(level, AX, FLOOR, AZ, 30, "ewb2_moon" + k);
                if (!f.isEmpty() && f.get(0) instanceof LivingEntity le) moons.add(le);
            }
        });
    }

    void tickMoons() {
        if (t < 5) return;
        int alive = 0;
        for (int k = 0; k < moons.size(); k++) {
            LivingEntity m = moons.get(k);
            if (m == null || !m.m_6084_()) continue;
            alive++;
            double a = moonPos.get(k)[0] += 0.004;
            double x = AX + Math.cos(a) * 15, z = AZ + Math.sin(a) * 15;
            m.m_6034_(x, FLOOR + 0.5, z); m.m_20256_(new Vec3(0, 0, 0));
            if (t % 3 == 0) {     // лучи-нити к королю
                double sx = x, sy = FLOOR + 2, sz = z, dx = bx - sx, dy = cy() - sy, dz = bz - sz, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                for (double s = 0; s < l; s += 1.0) Fx.send(level, PALE, sx + dx * s / l, sy + dy * s / l, sz + dz * s / l, 1, 0, 0, 0, 0);
            }
            if ((t + k * 23) % 75 == 0) {
                List<ServerPlayer> ps = players();
                if (!ps.isEmpty()) { Vec3 v = ps.get(RNG.nextInt(ps.size())).m_20182_(); beams.add(new double[]{x, FLOOR + 2, z, v.f_82479_, v.f_82480_ + 1, v.f_82481_, 0}); }
            }
        }
        if (alive == 0 && t - moonsT > 60) {
            shield = false; moonsDone = true; stun = 160;
            Fx.send(level, PALE, bx, cy(), bz, 250, 5, 5, 5, 0.2);
            Fx.send(level, Fx.particle("flash"), bx, cy(), bz, 2, 1, 1, 1, 0);
            Fx.sound(level, bx, cy(), bz, "block.glass.break", 20.0F, 0.4F);
            Fx.sound(level, bx, cy(), bz, "entity.ender_dragon.hurt", 20.0F, 0.5F);
            actionbar("Щит пал! Король рухнул — бей!", "gold");
        }
    }

    // ================= Фаза 4: сверхновая =================
    void startNova() {
        phase = 4; novaCd = 60;
        actionbar("Король копит сверхновую! Прячься за стенами!", "gold");
        BlockState obs = Fx.state("obsidian"), cry = Fx.state("crying_obsidian");
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4;
            double cx = AX + Math.cos(a) * 17, cz = AZ + Math.sin(a) * 17, tx = -Math.sin(a), tz = Math.cos(a);
            for (int w = -3; w <= 3; w++) for (int h = 0; h < 4; h++) {
                BlockPos p = new BlockPos((int) Math.floor(cx + tx * w), FLOOR + h, (int) Math.floor(cz + tz * w));
                level.m_7731_(p, (h == 3 || Math.abs(w) == 3) ? cry : obs, 3); walls.add(p);
            }
            Fx.send(level, Fx.particle("explosion"), cx, FLOOR + 1, cz, 6, 2, 1, 2, 0);
        }
        Fx.sound(level, AX, FLOOR, AZ, "block.piston.extend", 10.0F, 0.5F);
        Fx.sound(level, AX, FLOOR, AZ, "entity.wither.break_block", 10.0F, 0.5F);
    }

    void tickNova() {
        novaT++;
        double c = cy();
        for (int i = 0; i < 12; i++) {
            double[] u = Fx.unit(); double d = 16 - novaT * 0.12;
            Fx.send(level, Fx.particle("end_rod"), bx + u[0] * d, c + u[1] * d, bz + u[2] * d, 0, -u[0], -u[1], -u[2], 0.6);
        }
        if (novaT % 10 == 0) Fx.sound(level, bx, c, bz, "block.note_block.pling", 15.0F, 0.5F + novaT / 140F);
        if (novaT == 1) { for (int i = 0; i < 3; i++) setBlock(i, "shroomlight"); actionbar("СВЕРХНОВАЯ! Прячься за стену!", "red"); }
        if (novaT == 120) {
            Fx.send(level, Fx.particle("flash"), bx, c, bz, 5, 3, 3, 3, 0);
            Fx.sound(level, bx, c, bz, "entity.generic.explode", 30.0F, 0.4F);
            Fx.sound(level, bx, c, bz, "entity.lightning_bolt.thunder", 30.0F, 0.5F);
            Fx.sound(level, bx, c, bz, "item.totem.use", 15.0F, 0.5F);
            for (int s = 0; s < 10; s++) {
                final double r = 4 + s * 4;
                Later.run(level, s + 1, () -> { for (int i = 0; i < 120; i++) { double[] u = Fx.unit(); Fx.send(level, i % 2 == 0 ? WHITE : ORANGE, bx + u[0] * r, c + u[1] * r, bz + u[2] * r, 1, 0, 0, 0, 0); } });
            }
            for (ServerPlayer p : players()) {
                Vec3 eye = p.m_146892_();
                BlockHitResult h = level.m_45547_(new ClipContext(new Vec3(bx, c, bz), eye, ClipContext.Block.valueOf("COLLIDER"), ClipContext.Fluid.valueOf("NONE"), p));
                if (h.m_6662_() == HitResult.Type.valueOf("MISS")) {
                    hurt(p, 22); p.m_20254_(6);
                    Vec3 v = p.m_20182_(); double dx = v.f_82479_ - bx, dz = v.f_82481_ - bz, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                    p.m_20256_(new Vec3(dx / d * 1.5, 0.6, dz / d * 1.5)); p.f_19864_ = true;
                }
            }
            for (int i = 0; i < 3; i++) setBlock(i, "black_concrete");
        }
        if (novaT >= 140) novaT = -1;
    }

    void setBlock(int i, String id) { if (uuid[i] != null) Cmd.run(level, bx, by, bz, "data merge entity " + uuid[i] + " {" + Disp.block(id) + "}"); }

    // ================= Фаза 5: коллапс =================
    void startCollapse() {
        phase = 5; collapse = true;
        setBlock(3, "redstone_block");
        for (int i = 5; i < 16; i += 2) setBlock(i, "redstone_block");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " color red");
        actionbar("КОЛЛАПС! Арена рушится в бездну!", "dark_red");
        Fx.sound(level, bx, cy(), bz, "entity.ravager.roar", 30.0F, 0.4F);
        Fx.sound(level, bx, cy(), bz, "entity.wither.spawn", 30.0F, 0.7F);
    }

    void tickCollapse() {
        // гравитация звезды
        for (ServerPlayer p : players()) {
            Vec3 v = p.m_20182_(); double dx = bx - v.f_82479_, dz = bz - v.f_82481_, d = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            if (d > 4) { p.m_20256_(p.m_20184_().m_82520_(dx / d * 0.025, 0, dz / d * 0.025)); p.f_19864_ = true; }
        }
        if (++collapseCd < 18 || collapseR <= 19) return;
        collapseCd = 0;
        int r = collapseR--;
        for (int x = -r - 1; x <= r + 1; x++) for (int z = -r - 1; z <= r + 1; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d < r - 0.5 || d >= r + 0.5) continue;
            for (int y = FLOOR - 2; y <= FLOOR + 1; y++) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level.m_8055_(p);
                if (s.m_60795_()) continue;
                FallingBlockEntity fb = FallingBlockEntity.m_201971_(level, p, s);
                if (fb != null) { fb.f_31943_ = false; fb.m_20256_(new Vec3(x / d * 0.1, -0.1, z / d * 0.1)); }
            }
            if (RNG.nextInt(4) == 0) Fx.send(level, Fx.particle("campfire_cosy_smoke"), x, FLOOR, z, 1, 0.3, 0.2, 0.3, 0.02);
        }
        Fx.sound(level, AX + r, FLOOR, AZ, "entity.generic.explode", 8.0F, 0.3F);
        Fx.sound(level, AX, FLOOR, AZ, "block.basalt.break", 15.0F, 0.4F);
    }

    void clearWalls() { BlockState air = Fx.state("air"); for (BlockPos p : walls) level.m_7731_(p, air, 3); walls.clear(); }

    // ================= Гибель =================
    void startDeath() {
        stage = 3; dyingT = 0;
        beams.clear(); meteors.clear(); clapT = -1;
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
        Fx.sound(level, bx, cy(), bz, "entity.wither.death", 30.0F, 0.5F);
        Fx.sound(level, bx, cy(), bz, "entity.ender_dragon.death", 30.0F, 0.6F);
        for (LivingEntity m : moons) if (m != null && m.m_6084_()) m.m_146870_();
    }

    boolean dying() {
        dyingT++;
        double c = cy();
        if (dyingT % 5 == 0 && dyingT < 100) pose(5, Math.max(0.05, 1.0 - dyingT / 110.0), true);
        if (dyingT % 3 == 0) {
            double[] u = Fx.unit();
            Fx.send(level, Fx.particle("explosion"), bx + u[0] * 6, c + u[1] * 6, bz + u[2] * 6, 1, 0, 0, 0, 0);
            Fx.send(level, GOLD, bx, c, bz, 30, 4, 4, 4, 0.1);
            Fx.sound(level, bx, c, bz, "entity.generic.explode", 8.0F, 0.4F + dyingT / 150F);
        }
        Fx.send(level, Fx.particle("end_rod"), bx, c, bz, 8, 0.5, 0.5, 0.5, 0.5);
        if (dyingT == 120) {
            Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=ewb2]");
            Fx.send(level, Fx.particle("flash"), bx, c, bz, 5, 2, 2, 2, 0);
            Fx.send(level, Fx.particle("explosion_emitter"), bx, c, bz, 5, 3, 3, 3, 0);
            for (int i = 0; i < 200; i++) { double[] u = Fx.unit(); Fx.send(level, Fx.particle("end_rod"), bx, c, bz, 0, u[0], u[1], u[2], 1.5); }
            Fx.sound(level, bx, c, bz, "entity.generic.explode", 30.0F, 0.5F);
            Fx.sound(level, bx, c, bz, "ui.toast.challenge_complete", 15.0F, 1.0F);
            Loot.chest(level, 0, FLOOR, 5, "Добыча Короля Затмения", List.of(
                    "explosionwand:eclipse_heart", "minecraft:nether_star", "16 minecraft:diamond", "3 minecraft:netherite_ingot",
                    "2 minecraft:enchanted_golden_apple"));
            String at = Cmd.f(AX) + " " + (FLOOR + 1) + " " + Cmd.f(AZ + 3);
            for (int i = 0; i < 10; i++) Cmd.run(level, AX, FLOOR, AZ, "summon experience_orb " + at + " {Value:300}");
            Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..200] subtitle {\"text\":\"Добыча в сундуке, портал домой — в центре\",\"color\":\"gray\"}");
            Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..200] title {\"text\":\"Король Затмения пал\",\"color\":\"gold\",\"bold\":true}");
            clearWalls();
            Arena2.cleared = true;
            return true;
        }
        return false;
    }

    void reset() {
        Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=ewb2]");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
        clearWalls();
    }

    static void onHurt(LivingHurtEvent ev) {
        Boss2 b = ACTIVE;
        if (b == null || ev.getEntity() != b.host) return;
        if (!b.vulnerable()) {
            ev.setCanceled(true);
            Fx.send(b.level, PALE, b.bx, b.cy(), b.bz, 20, 3, 3, 3, 0);
            Fx.sound(b.level, b.bx, b.cy(), b.bz, "block.anvil.place", 3.0F, 1.8F);
            return;
        }
        if (b.stun <= 0) ev.setAmount(ev.getAmount() * 0.55F);
        Fx.send(b.level, GOLD, b.bx, b.cy(), b.bz, 15, 2.5, 2.5, 2.5, 0.1);
        Fx.sound(b.level, b.bx, b.cy(), b.bz, "block.anvil.land", 1.5F, 1.6F + RNG.nextFloat() * 0.3F);
    }
}
