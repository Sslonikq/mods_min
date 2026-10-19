package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * «МОНОЛИТ СНА» — 3-й босс (арена Сна). Блочная башня из 4 чёрных сегментов, каждый крутится сам
 * по себе; бирюзовые швы, кристаллы-корона, один огромный глаз и две парящие руки-обелиска.
 * Стоит на земле — нижний сегмент можно бить мечом. Каждая четверть здоровья раскалывает нижний
 * сегмент: башня оседает, начинается новая фаза и приходят приспешники.
 *
 *  1 Четыре сегмента — удар-волна (перепрыгни кольцо), луч из глаза, вращение вплотную.
 *  2 Три сегмента   — + руки-обелиски бьют сверху; приходят Дремотники (летучие).
 *  3 Два сегмента   — всё быстрее; приходят Стражи Шва (ближний бой).
 *  4 Последний глаз — с неба падают осколки снов; глаз открыт — урон по нему выше.
 */
public class Boss3 {
    static Boss3 ACTIVE;
    static final Random RNG = Fx.RNG;
    static final double AX = 0.5, AZ = 0.5;
    static final int FLOOR = 64, ARENA_R = 34, N = 26;
    /** У мобов max_health ограничен 1024: хост живёт на 1024, а «настоящие» 1700 ХП даёт снижение урона. */
    static final float MAX_HP = 1700, HOST_HP = 1024;
    static final double SEG_H = 2.4, SEG_GAP = 0.3;
    static final String BAR = "explosionwand:dream_monolith";
    static final String TAG = "ewb3";
    static final int TEAL = 3073750;
    static final String[] BLOCKS = new String[N];
    static {
        for (int k = 0; k < 4; k++) { BLOCKS[k * 4] = "black_concrete"; BLOCKS[k * 4 + 1] = "diamond_block"; BLOCKS[k * 4 + 2] = "diamond_block"; BLOCKS[k * 4 + 3] = "diamond_block"; }
        BLOCKS[16] = BLOCKS[17] = BLOCKS[18] = "diamond_block";                 // корона
        BLOCKS[19] = "diamond_block"; BLOCKS[20] = "white_concrete"; BLOCKS[21] = "black_concrete";   // глаз
        BLOCKS[22] = BLOCKS[23] = "obsidian"; BLOCKS[24] = BLOCKS[25] = "diamond_block";               // руки
    }
    static ParticleOptions TEAL_P, DEEP, WHITE, DARK;

    final ServerLevel level;
    LivingEntity host;
    final String[] uuid = new String[N];
    int t = 0, stage = 0, dyingT = 0;     // 0 поиск, 1 появление, 2 бой, 3 гибель
    double bx = AX, bz = AZ, ex = 0, ez = 1;
    // сегменты
    final boolean[] alive = {true, true, true, true};
    final double[] segY = new double[4], segAng = new double[4], segSpd = {0.035, -0.05, 0.028, -0.042};
    double spin = 1, lift = 0, eyeOpen = 1, crownAng = 0;
    int broken = 0, phase = 1, breakT = -1;
    // атаки
    int ringCd = 120, beamCd = 90, armCd = 140, shardCd = 80, spinCd = 160, nearT = 0;
    int stompT = -1, spinT = -1, fogCd = 160, sinkCd = 240, sinkT = -1;
    double sinkOff = 0;
    final List<double[]> fogs = new ArrayList<>();      // x,z,timer
    final List<double[]> rings = new ArrayList<>();      // r
    final List<double[]> lances = new ArrayList<>();     // sx,sy,sz,tx,ty,tz,timer
    final List<double[]> shards = new ArrayList<>();     // x,z,timer
    final Map<ServerPlayer, Integer> hitCd = new WeakHashMap<>();
    // руки: позиция x,y,z; режим (0 рядом, 1 подлёт, 2 удар, 3 пауза), таймер, цель
    final double[][] hand = new double[2][3];
    final int[] hMode = new int[2], hT = new int[2];
    final double[][] hTarget = new double[2][2];

    static void init() {
        if (TEAL_P != null) return;
        TEAL_P = new DustParticleOptions(new Vector3f(0.25F, 0.95F, 0.9F), 1.6F);
        DEEP = new DustParticleOptions(new Vector3f(0.05F, 0.45F, 0.6F), 1.3F);
        WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 1.4F);
        DARK = new DustParticleOptions(new Vector3f(0.03F, 0.03F, 0.05F), 2.6F);
    }

    Boss3(ServerLevel level) { this.level = level; }

    static void spawn(ServerLevel level) {
        init();
        Boss3 b = new Boss3(level);
        ACTIVE = b;
        StringBuilder sb = new StringBuilder();
        sb.append("summon minecraft:giant ").append(Cmd.f(AX)).append(" ").append(FLOOR).append(" ").append(Cmd.f(AZ))
          .append(" {Tags:[\"" + TAG + "\",\"" + TAG + "_host\"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,")
          .append("Attributes:[{Name:\"minecraft:generic.max_health\",Base:").append((int) HOST_HP).append("d},{Name:\"minecraft:generic.knockback_resistance\",Base:1d}],")
          .append("Health:").append((int) HOST_HP).append("f,ActiveEffects:[{Id:14,Amplifier:0b,Duration:999999,ShowParticles:0b}],Passengers:[");
        for (int i = 0; i < N; i++) {
            if (i > 0) sb.append(",");
            sb.append("{id:\"minecraft:block_display\",Tags:[\"" + TAG + "\",\"" + TAG + "_p").append(i).append("\"],")
              .append(Disp.block(BLOCKS[i])).append(",view_range:5f,brightness:{sky:15,block:15},")
              .append(Disp.tf(0, -8, 0, 0.01, 0.01, 0.01, Disp.ID)).append("}");
        }
        sb.append("]}");
        Cmd.run(level, AX, FLOOR, AZ, sb.toString());
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
        Cmd.run(level, AX, FLOOR, AZ, "bossbar add " + BAR + " {\"text\":\"Монолит Сна\",\"color\":\"aqua\",\"bold\":true}");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " color blue");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " style notched_10");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " max 1000");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " value 1000");
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " times 20 90 30");
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " subtitle {\"text\":\"Четыре сна, сложенные в камень\",\"color\":\"dark_gray\",\"italic\":true}");
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " title {\"text\":\"МОНОЛИТ СНА\",\"color\":\"aqua\",\"bold\":true}");
        Fx.sound(level, AX, FLOOR + 6, AZ, "entity.elder_guardian.curse", 30.0F, 0.5F);
        Fx.sound(level, AX, FLOOR + 6, AZ, "block.respawn_anchor.charge", 30.0F, 0.5F);
        for (int k = 0; k < 4; k++) { b.segY[k] = -SEG_H * (k + 1); b.segAng[k] = RNG.nextDouble() * Math.PI; }
        for (int i = 0; i < 2; i++) { b.hand[i][0] = AX + (i == 0 ? -5 : 5); b.hand[i][1] = FLOOR - 4; b.hand[i][2] = AZ; }
    }

    static String sel(String cmd) { return cmd + " @a[x=0,y=64,z=0,distance=..220]"; }

    List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) if (p.m_6084_() && !p.m_5833_()) out.add(p);
        return out;
    }
    ServerPlayer nearest() {
        ServerPlayer best = null; double bd = 1e9;
        for (ServerPlayer p : players()) { double d = hdist2(p); if (d < bd) { bd = d; best = p; } }
        return best;
    }
    double hdist2(Entity p) { Vec3 v = p.m_20182_(); return (v.f_82479_ - bx) * (v.f_82479_ - bx) + (v.f_82481_ - bz) * (v.f_82481_ - bz); }
    void hurt(ServerPlayer p, float d) { p.m_6469_(level.m_269111_().m_269425_(), d); }
    void actionbar(String s, String c) { Cmd.run(level, AX, FLOOR, AZ, sel("title") + " actionbar {\"text\":\"" + s + "\",\"color\":\"" + c + "\"}"); }
    double rate() { return phase >= 4 ? 0.6 : phase == 3 ? 0.75 : phase == 2 ? 0.9 : 1.0; }

    // ---- геометрия башни ----
    int aliveCount() { int n = 0; for (boolean a : alive) if (a) n++; return n; }
    int bottom() { for (int k = 0; k < 4; k++) if (alive[k]) return k; return 3; }
    int top() { for (int k = 3; k >= 0; k--) if (alive[k]) return k; return 3; }
    double width(int k) { return 3.4 - k * 0.18; }
    /** Целевая высота центра сегмента k над полом (живые лежат стопкой). */
    double targetY(int k) { int j = 0; for (int i = 0; i < k; i++) if (alive[i]) j++; return SEG_H / 2 + 0.02 + j * (SEG_H + SEG_GAP); }
    double topY() { return FLOOR + segY[top()] + SEG_H / 2; }
    double[] eyeWorld() { int k = top(); double r = width(k) / 2 + 0.25; return new double[]{bx + ex * r, FLOOR + segY[k], bz + ez * r}; }

    // ================= Тик =================
    boolean tick() {
        t++;
        if (stage == 0) {
            List<Entity> h = Cmd.tagged(level, AX, FLOOR + 3, AZ, 20, TAG + "_host");
            if (h.isEmpty() || !(h.get(0) instanceof LivingEntity le)) { if (t > 40) { reset(); return true; } return false; }
            host = le;
            for (int i = 0; i < N; i++) { List<Entity> p = Cmd.tagged(level, AX, FLOOR + 9, AZ, 30, TAG + "_p" + i); if (!p.isEmpty()) uuid[i] = p.get(0).m_20149_(); }
            stage = 1; t = 0;
            return false;
        }
        if (host == null) { reset(); return true; }
        if (stage == 3) return dying();
        if (host.m_21223_() <= 0 || host.m_21224_()) { startDeath(); return false; }
        if (!host.m_6084_()) { reset(); return true; }
        if (host.m_6060_()) host.m_20095_();

        animate();
        if (t % 3 == 0) pose(3);
        if (t % 2 == 0) poseArms(2);
        ambience();
        if (t % 10 == 0) bar();
        if (stage == 1) {     // башня поднимается из пола
            if (t % 6 == 0) Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx, FLOOR + 0.5, bz, 30, 1.6, 0.3, 1.6, 0.1);
            if (t % 15 == 0) Fx.sound(level, bx, FLOOR, bz, "block.deepslate.place", 15.0F, 0.5F);
            if (t >= 70) { stage = 2; t = 0; actionbar("Бей по нижнему сегменту!", "aqua"); }
            return false;
        }

        if (breakT >= 0) { tickBreak(); tickRings(); return false; }
        float frac = host.m_21223_() / HOST_HP;
        if (broken < 3 && frac <= 0.75F - broken * 0.25F) { startBreak(); return false; }

        move();
        tickRings(); tickLances(); tickArms(); tickShards();
        if (stompT >= 0) tickStomp();
        if (spinT >= 0) tickSpin();
        if (sinkT >= 0) tickSink();
        tickFogs();
        attacks();
        return false;
    }

    // ================= Движение и облик =================
    void move() {
        ServerPlayer n = nearest();
        if (n == null) return;
        Vec3 p = n.m_20182_();
        double dx = p.f_82479_ - bx, dz = p.f_82481_ - bz, l = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
        ex += (dx / l - ex) * 0.08; ez += (dz / l - ez) * 0.08;
        double el = Math.max(1e-3, Math.sqrt(ex * ex + ez * ez)); ex /= el; ez /= el;
        // медленно скользит к ближайшему, не покидая центра арены
        if (spinT < 0 && stompT < 0 && l > 7) {
            double sp = 0.03 + phase * 0.008;
            double nx = bx + dx / l * sp, nz = bz + dz / l * sp;
            if ((nx - AX) * (nx - AX) + (nz - AZ) * (nz - AZ) < (ARENA_R - 9) * (ARENA_R - 9)) { bx = nx; bz = nz; }
            if (t % 20 == 0) Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx, FLOOR + 0.1, bz, 8, 1.4, 0.05, 1.4, 0.05);
        }
        host.m_6034_(bx, FLOOR, bz); host.m_20256_(new Vec3(0, 0, 0));
    }

    void animate() {
        double boost = spinT >= 20 ? 6.0 : spinT >= 0 ? 0.3 : 1.0;
        spin += (boost - spin) * 0.15;
        for (int k = 0; k < 4; k++) {
            segAng[k] += segSpd[k] * spin * (1 + (4 - aliveCount()) * 0.25);
            double target = alive[k] ? targetY(k) + lift * (k - bottom() + 1) * 0.3 - sinkOff : -SEG_H;
            double kk = stage == 1 ? 0.04 : 0.15;
            segY[k] += (target - segY[k]) * kk;
        }
        crownAng += 0.02;
        lift *= 0.8;
        boolean blink = (t % 140) > 132 || breakT >= 0;
        double want = blink ? 0.05 : (phase >= 4 ? 1.15 : 1.0);
        eyeOpen += (want - eyeOpen) * 0.3;
    }

    void send(int i, int dur, double cx, double cy, double cz, double sx, double sy, double sz, double[] q) {
        if (uuid[i] == null) return;
        Cmd.run(level, bx, FLOOR, bz, "data merge entity " + uuid[i] + " {start_interpolation:0,interpolation_duration:" + dur + "," + Disp.tf(cx, cy, cz, Math.max(0.001, sx), Math.max(0.001, sy), Math.max(0.001, sz), q) + "}");
    }
    void setBlock(int i, String id) { if (uuid[i] != null) Cmd.run(level, bx, FLOOR, bz, "data merge entity " + uuid[i] + " {" + Disp.block(id) + "}"); }
    /** Локальная высота относительно точки посадки (гигант везёт пассажиров на 9 блоков выше ног). */
    static double ly(double worldAboveFloor) { return worldAboveFloor - 9.0; }

    void pose(int dur) {
        double gs = stage == 1 ? Math.min(1, 0.3 + t / 70.0) : 1.0;
        for (int k = 0; k < 4; k++) {
            int b = k * 4;
            if (!alive[k] && segY[k] < -SEG_H / 2) { for (int j = 0; j < 4; j++) send(b + j, dur, 0, ly(-4), 0, 0.001, 0.001, 0.001, Disp.ID); continue; }
            double w = width(k) * gs, h = SEG_H * gs, y = ly(segY[k]);
            double shake = (spinT >= 0 && spinT < 20) ? (RNG.nextDouble() - 0.5) * 0.12 : 0;
            double[] q = Disp.q(0, 1, 0, segAng[k]);
            send(b, dur, shake, y, 0, w, h, w, q);
            send(b + 1, dur, 0, y + h / 2 + 0.08, 0, w + 0.22, 0.16, w + 0.22, q);
            double[] s1 = Disp.rot(q, 0.55 * gs, 0, w / 2 + 0.03);
            send(b + 2, dur, s1[0], y + s1[1], s1[2], 0.18, h * 0.8, 0.06, q);
            double[] s2 = Disp.rot(q, -w / 2 - 0.03, -0.3 * gs, -0.45 * gs);
            send(b + 3, dur, s2[0], y + s2[1], s2[2], 0.06, h * 0.65, 0.18, q);
        }
        // корона из кристаллов над верхним сегментом
        double cy = ly(segY[top()] + SEG_H / 2 + 0.75);
        for (int i = 0; i < 3; i++) {
            double a = crownAng + i * Math.PI * 2 / 3, s = Math.sin(a), c = Math.cos(a);
            double[] q = Disp.mul(Disp.q(s, 0, -c, 0.4), Disp.q(0, 1, 0, a));
            double hh = (1.0 + (i == 0 ? 0.5 : 0.1)) * gs;
            send(16 + i, dur, c * 0.85, cy + (i == 0 ? 0.2 : 0), s * 0.85, 0.45 * gs, hh, 0.45 * gs, q);
        }
        // глаз смотрит на ближайшего игрока
        int k = top();
        double r = width(k) / 2 + 0.06, yawF = Math.atan2(ex, ez), y = ly(segY[k]);
        double[] qF = Disp.q(0, 1, 0, yawF);
        double open = Math.max(0.02, eyeOpen) * gs;
        send(19, dur, ex * r, y, ez * r, 1.9 * gs, 1.9 * gs, 0.08, qF);
        send(20, dur, ex * (r + 0.05), y, ez * (r + 0.05), 1.5 * gs, 1.5 * open, 0.1, qF);
        double look = Math.sin(t * 0.05) * 0.25;
        double[] pp = Disp.rot(qF, look, 0, 0);
        send(21, dur, ex * (r + 0.1) + pp[0], y, ez * (r + 0.1) + pp[2], 0.42 * gs, 1.05 * open, 0.12, qF);
    }

    void poseArms(int dur) {
        double yawF = Math.atan2(ex, ez);
        for (int h = 0; h < 2; h++) {
            double[] H = hand[h];
            double tilt = (h == 0 ? 0.18 : -0.18) * (hMode[h] == 2 ? 0.2 : 1.0);
            double[] q = Disp.mul(Disp.q(0, 1, 0, yawF), Disp.q(0, 0, 1, tilt));
            double hx = H[0] - bx, hz = H[2] - bz, hy = ly(H[1] - FLOOR);
            send(22 + h, dur, hx, hy, hz, 0.9, 5.0, 0.9, q);
            double[] tip = Disp.rot(q, 0, -2.6, 0);
            send(24 + h, dur, hx + tip[0], hy + tip[1], hz + tip[2], 1.15, 1.15, 1.15, Disp.mul(q, Disp.q(0, 1, 0, t * 0.08)));
        }
    }

    void ambience() {
        // светящиеся швы
        if (t % 2 == 0) for (int k = 0; k < 4; k++) if (alive[k]) {
            double a = RNG.nextDouble() * Math.PI * 2, w = width(k) / 2 + 0.1;
            Fx.send(level, TEAL_P, bx + Math.cos(a) * w, FLOOR + segY[k] + SEG_H / 2 + 0.1, bz + Math.sin(a) * w, 1, 0, 0, 0, 0);
        }
        if (t % 5 == 0) { double[] e = eyeWorld(); Fx.send(level, Fx.particle("glow"), e[0], e[1], e[2], 1, 0.3, 0.3, 0.3, 0); }
        if (t % 60 == 0) Fx.sound(level, bx, FLOOR + 5, bz, "block.beacon.ambient", 10.0F, 0.5F);
        if (t % 110 == 0) Fx.sound(level, bx, FLOOR + 5, bz, "entity.warden.heartbeat", 12.0F, 0.5F);
    }

    void bar() {
        int v = (int) Math.max(0, Math.min(1000, host.m_21223_() / HOST_HP * 1000));
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " value " + v);
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " players @a[x=0,y=64,z=0,distance=..220]");
    }

    // ================= Атаки =================
    void attacks() {
        List<ServerPlayer> ps = players();
        if (ps.isEmpty()) return;
        boolean near = false;
        for (ServerPlayer p : ps) if (hdist2(p) < 6 * 6) near = true;
        nearT = near ? nearT + 1 : Math.max(0, nearT - 2);
        if (spinCd > 0) spinCd--;
        if (stompT < 0 && spinT < 0) {
            if (nearT > 50 && spinCd <= 0) { spinT = 0; nearT = 0; spinCd = (int) (220 * rate()); return; }
            if (--ringCd <= 0) { stompT = 0; ringCd = (int) (170 * rate()); }
        }
        if (--beamCd <= 0 && eyeOpen > 0.5) {
            double[] e = eyeWorld();
            int shots = phase >= 4 ? 3 : phase >= 2 ? 2 : 1;
            for (int i = 0; i < shots; i++) {
                ServerPlayer p = ps.get(RNG.nextInt(ps.size())); Vec3 v = p.m_20182_();
                double off = i == 0 ? 0 : 2.5;
                lances.add(new double[]{e[0], e[1], e[2], v.f_82479_ + RNG.nextGaussian() * off, v.f_82480_ + 1, v.f_82481_ + RNG.nextGaussian() * off, -i * 6});
            }
            Fx.sound(level, e[0], e[1], e[2], "block.conduit.attack.target", 6.0F, 0.8F);
            beamCd = (int) (110 * rate());
            if (phase >= 3) beamCd -= 15;
        }
        if (phase >= 2 && --armCd <= 0) {
            int h = hMode[0] == 0 ? 0 : (hMode[1] == 0 ? 1 : -1);
            if (h >= 0) {
                ServerPlayer p = ps.get(RNG.nextInt(ps.size())); Vec3 v = p.m_20182_();
                hMode[h] = 1; hT[h] = 0; hTarget[h][0] = v.f_82479_; hTarget[h][1] = v.f_82481_;
                Fx.sound(level, hand[h][0], hand[h][1], hand[h][2], "entity.iron_golem.attack", 6.0F, 0.5F);
            }
            armCd = (int) (120 * rate());
        }
        if (phase >= 3 && --fogCd <= 0) {
            for (ServerPlayer p : ps) { Vec3 v = p.m_20182_(); fogs.add(new double[]{v.f_82479_, v.f_82481_, 0}); }
            double a = RNG.nextDouble() * Math.PI * 2, r = 6 + RNG.nextDouble() * 12;
            fogs.add(new double[]{bx + Math.cos(a) * r, bz + Math.sin(a) * r, 0});
            Fx.sound(level, bx, FLOOR + 4, bz, "entity.elder_guardian.curse", 8.0F, 1.4F);
            fogCd = phase >= 4 ? 170 : 220;
        }
        if (phase >= 4 && sinkT < 0 && stompT < 0 && spinT < 0 && --sinkCd <= 0) { sinkT = 0; sinkCd = 260; }
        if (phase >= 4 && --shardCd <= 0) {
            for (ServerPlayer p : ps) for (int i = 0; i < 3; i++) {
                Vec3 v = p.m_20182_();
                shards.add(new double[]{v.f_82479_ + (i == 0 ? 0 : RNG.nextGaussian() * 3), v.f_82481_ + (i == 0 ? 0 : RNG.nextGaussian() * 3), 0});
            }
            Fx.sound(level, bx, FLOOR + 12, bz, "block.amethyst_block.resonate", 10.0F, 0.6F);
            shardCd = 70;
        }
    }

    // --- удар-волна: башня подпрыгивает и бьёт о пол; по полу расходится кольцо ---
    void tickStomp() {
        stompT++;
        if (stompT == 1) actionbar("Монолит поднимается — ПРЫГАЙ через волну!", "aqua");
        if (stompT < 10) lift = 1.2;
        if (stompT == 14) {
            rings.add(new double[]{2.0});
            if (phase >= 3) Later.run(level, 12, () -> rings.add(new double[]{2.0}));
            Fx.send(level, Fx.particle("explosion"), bx, FLOOR + 0.5, bz, 6, 2, 0.2, 2, 0);
            Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx, FLOOR + 0.3, bz, 80, 3, 0.2, 3, 0.2);
            Fx.sound(level, bx, FLOOR, bz, "entity.generic.explode", 10.0F, 0.5F);
            Fx.sound(level, bx, FLOOR, bz, "block.anvil.land", 10.0F, 0.4F);
            stompT = -1;
        }
    }

    void tickRings() {
        Iterator<double[]> it = rings.iterator();
        while (it.hasNext()) {
            double[] w = it.next(); w[0] += 0.55;
            int n = (int) (20 + w[0] * 5);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                Fx.send(level, i % 2 == 0 ? TEAL_P : DEEP, bx + Math.cos(a) * w[0], FLOOR + 0.25, bz + Math.sin(a) * w[0], 1, 0, 0, 0, 0);
            }
            for (ServerPlayer p : players()) {
                Vec3 v = p.m_20182_(); double d = Math.sqrt(hdist2(p));
                if (Math.abs(d - w[0]) < 0.8 && v.f_82480_ < FLOOR + 0.7) {
                    int last = hitCd.getOrDefault(p, -100);
                    if (t - last > 10) { hitCd.put(p, t); hurt(p, 9); p.m_20256_(new Vec3((v.f_82479_ - bx) / Math.max(d, 0.1) * 0.8, 0.5, (v.f_82481_ - bz) / Math.max(d, 0.1) * 0.8)); p.f_19864_ = true; }
                }
            }
            if (w[0] > ARENA_R) it.remove();
        }
    }

    // --- луч из глаза: тонкая линия-прицел, затем выстрел ---
    void tickLances() {
        Iterator<double[]> it = lances.iterator();
        while (it.hasNext()) {
            double[] b = it.next();
            if (++b[6] < 0) continue;
            double dx = b[3] - b[0], dy = b[4] - b[1], dz = b[5] - b[2], l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
            boolean fire = b[6] > 18;
            double ext = 1.6;
            for (double s = 0; s < l * ext; s += fire ? 0.35 : 1.2) Fx.send(level, fire ? WHITE : TEAL_P, b[0] + dx * s / l, b[1] + dy * s / l, b[2] + dz * s / l, 1, 0, 0, 0, 0);
            if (b[6] == 19) {
                Fx.sound(level, b[0], b[1], b[2], "entity.guardian.attack", 6.0F, 1.1F);
                for (ServerPlayer p : players()) {
                    Vec3 v = p.m_20182_();
                    double rx = v.f_82479_ - b[0], ry = v.f_82480_ + 1 - b[1], rz = v.f_82481_ - b[2];
                    double al = (rx * dx + ry * dy + rz * dz) / l; if (al < 0 || al > l * ext) continue;
                    double qx = rx - dx / l * al, qy = ry - dy / l * al, qz = rz - dz / l * al;
                    if (qx * qx + qy * qy + qz * qz < 1.3) {
                        hurt(p, 10);
                        var sl = Fx.effect("slowness"); if (sl != null) p.m_7292_(new MobEffectInstance(sl, 50, 1));
                    }
                }
            }
            if (b[6] > 24) it.remove();
        }
    }

    // --- вращение вплотную: сегменты раскручиваются и отшвыривают ---
    void tickSpin() {
        spinT++;
        if (spinT == 1) { actionbar("Сегменты раскручиваются — ОТОЙДИ!", "yellow"); Fx.sound(level, bx, FLOOR + 3, bz, "block.beacon.power_select", 10.0F, 0.6F); }
        if (spinT < 20 && spinT % 2 == 0) Fx.send(level, WHITE, bx, FLOOR + 3, bz, 10, 2, 3, 2, 0.05);
        if (spinT >= 20) {
            if (spinT % 4 == 0) Fx.sound(level, bx, FLOOR + 3, bz, "entity.player.attack.sweep", 6.0F, 0.6F);
            Fx.send(level, Fx.particle("sweep_attack"), bx, FLOOR + 1 + RNG.nextDouble() * 6, bz, 2, 2.2, 0.5, 2.2, 0);
            if (spinT % 10 == 0) for (ServerPlayer p : players()) {
                double d2 = hdist2(p);
                if (d2 < 6.2 * 6.2 && p.m_20186_() < FLOOR + 11) {
                    Vec3 v = p.m_20182_(); double d = Math.max(0.3, Math.sqrt(d2));
                    hurt(p, 7);
                    p.m_20256_(new Vec3((v.f_82479_ - bx) / d * 1.4, 0.55, (v.f_82481_ - bz) / d * 1.4)); p.f_19864_ = true;
                }
            }
        }
        if (spinT >= 50) spinT = -1;
    }

    // --- руки-обелиски ---
    void tickArms() {
        double yawF = Math.atan2(ex, ez);
        double px = Math.cos(yawF), pz = -Math.sin(yawF);       // перпендикуляр к взгляду
        for (int h = 0; h < 2; h++) {
            double[] H = hand[h];
            double sgn = h == 0 ? 1 : -1;
            double tx, ty, tz, k = 0.12;
            switch (hMode[h]) {
                case 1 -> { tx = hTarget[h][0]; tz = hTarget[h][1]; ty = FLOOR + 8; k = 0.16;
                    if (++hT[h] % 3 == 0) for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8; Fx.send(level, TEAL_P, tx + Math.cos(a) * 2.6, FLOOR + 0.2, tz + Math.sin(a) * 2.6, 1, 0, 0, 0, 0); }
                    if (hT[h] >= 30) { hMode[h] = 2; hT[h] = 0; } }
                case 2 -> { tx = hTarget[h][0]; tz = hTarget[h][1]; ty = FLOOR + 3.4; k = 0.55;
                    if (++hT[h] == 5) { armImpact(hTarget[h][0], hTarget[h][1]); hMode[h] = 3; hT[h] = 0; } }
                case 3 -> { tx = H[0]; tz = H[2]; ty = FLOOR + 3.4; if (++hT[h] >= 18) { hMode[h] = 0; hT[h] = 0; } }
                default -> { tx = bx + px * 4.8 * sgn; tz = bz + pz * 4.8 * sgn; ty = FLOOR + 6.5 + Math.sin(t * 0.06 + h * 2) * 0.5; }
            }
            if (stage == 1) { ty = FLOOR + 6.5 - (70 - t) * 0.15; k = 0.2; }
            H[0] += (tx - H[0]) * k; H[1] += (ty - H[1]) * k; H[2] += (tz - H[2]) * k;
        }
    }

    void armImpact(double x, double z) {
        Fx.send(level, Fx.particle("explosion"), x, FLOOR + 0.5, z, 3, 1, 0.2, 1, 0);
        Fx.send(level, Fx.blockParticle(Fx.state("obsidian")), x, FLOOR + 0.3, z, 50, 1.5, 0.2, 1.5, 0.2);
        Fx.send(level, TEAL_P, x, FLOOR + 0.5, z, 30, 2, 0.2, 2, 0.1);
        Fx.sound(level, x, FLOOR, z, "entity.iron_golem.damage", 8.0F, 0.4F);
        Fx.sound(level, x, FLOOR, z, "entity.generic.explode", 6.0F, 0.7F);
        for (ServerPlayer p : players()) {
            Vec3 v = p.m_20182_(); double dx = v.f_82479_ - x, dz = v.f_82481_ - z;
            if (dx * dx + dz * dz < 3.0 * 3.0 && v.f_82480_ < FLOOR + 4) { hurt(p, 12); p.m_20256_(new Vec3(dx * 0.2, 0.9, dz * 0.2)); p.f_19864_ = true; }
        }
    }

    // --- фаза 4: осколки снов падают с неба ---
    void tickShards() {
        Iterator<double[]> it = shards.iterator();
        while (it.hasNext()) {
            double[] s = it.next(); s[2]++;
            if (s[2] % 4 == 0) for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; Fx.send(level, DEEP, s[0] + Math.cos(a) * 1.8, FLOOR + 0.2, s[1] + Math.sin(a) * 1.8, 1, 0, 0, 0, 0); }
            double fall = FLOOR + 18 - s[2] * 0.6;
            if (s[2] > 10) { Fx.send(level, TEAL_P, s[0], fall, s[1], 3, 0.15, 0.4, 0.15, 0); Fx.send(level, Fx.particle("end_rod"), s[0], fall, s[1], 1, 0, 0, 0, 0); }
            if (fall <= FLOOR + 0.5) {
                Fx.send(level, Fx.particle("flash"), s[0], FLOOR + 0.6, s[1], 1, 0, 0, 0, 0);
                Fx.send(level, TEAL_P, s[0], FLOOR + 0.5, s[1], 25, 1, 0.3, 1, 0.1);
                Fx.sound(level, s[0], FLOOR, s[1], "block.amethyst_block.break", 3.0F, 0.6F);
                for (ServerPlayer p : players()) { Vec3 v = p.m_20182_(); double dx = v.f_82479_ - s[0], dz = v.f_82481_ - s[1]; if (dx * dx + dz * dz < 2.0 * 2.0 && v.f_82480_ < FLOOR + 3) hurt(p, 8); }
                it.remove();
            }
        }
    }

    // --- фаза 3+: сонный туман — зоны, где клонит в сон и жжёт ---
    void tickFogs() {
        Iterator<double[]> it = fogs.iterator();
        while (it.hasNext()) {
            double[] f = it.next(); f[2]++;
            if (f[2] < 25) { if (f[2] % 3 == 0) for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8; Fx.send(level, DEEP, f[0] + Math.cos(a) * 3, FLOOR + 0.2, f[1] + Math.sin(a) * 3, 1, 0, 0, 0, 0); } continue; }
            if (f[2] % 2 == 0) { Fx.send(level, TEAL_P, f[0], FLOOR + 0.8, f[1], 6, 1.6, 0.5, 1.6, 0); Fx.send(level, Fx.particle("cloud"), f[0], FLOOR + 0.6, f[1], 2, 1.5, 0.3, 1.5, 0.005); }
            if (f[2] % 10 == 0) for (ServerPlayer p : players()) {
                Vec3 v = p.m_20182_(); double dx = v.f_82479_ - f[0], dz = v.f_82481_ - f[1];
                if (dx * dx + dz * dz < 9 && v.f_82480_ < FLOOR + 3) {
                    hurt(p, 2);
                    var sl = Fx.effect("slowness"); if (sl != null) p.m_7292_(new MobEffectInstance(sl, 40, 1));
                    var na = Fx.effect("nausea"); if (na != null && RNG.nextInt(3) == 0) p.m_7292_(new MobEffectInstance(na, 80, 0));
                }
            }
            if (f[2] > 110) it.remove();
        }
    }

    // --- фаза 4: башня уходит под пол и выныривает рядом с игроком ---
    void tickSink() {
        sinkT++;
        if (sinkT == 1) { actionbar("Монолит уходит в пол — СЛЕДИ ЗА ТРЕЩИНАМИ!", "aqua"); Fx.sound(level, bx, FLOOR, bz, "block.deepslate.break", 15.0F, 0.4F); }
        if (sinkT <= 20) { sinkOff = sinkT * 0.6; Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx, FLOOR + 0.2, bz, 15, 1.6, 0.1, 1.6, 0.1); }
        if (sinkT == 20) {
            List<ServerPlayer> ps = players();
            if (!ps.isEmpty()) {
                Vec3 v = ps.get(RNG.nextInt(ps.size())).m_20182_();
                double a = RNG.nextDouble() * Math.PI * 2, nx = v.f_82479_ + Math.cos(a) * 4, nz = v.f_82481_ + Math.sin(a) * 4;
                double cl = Math.sqrt((nx - AX) * (nx - AX) + (nz - AZ) * (nz - AZ)), lim = ARENA_R - 9;
                if (cl > lim) { nx = AX + (nx - AX) / cl * lim; nz = AZ + (nz - AZ) / cl * lim; }
                bx = nx; bz = nz; host.m_6034_(bx, FLOOR, bz);
            }
        }
        if (sinkT > 20 && sinkT < 34) for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; Fx.send(level, TEAL_P, bx + Math.cos(a) * 3, FLOOR + 0.2, bz + Math.sin(a) * 3, 1, 0, 0, 0, 0); }
        if (sinkT == 34) {
            sinkOff = 0;
            rings.add(new double[]{2.0});
            Fx.send(level, Fx.particle("explosion_emitter"), bx, FLOOR + 1, bz, 1, 0, 0, 0, 0);
            Fx.sound(level, bx, FLOOR, bz, "entity.generic.explode", 12.0F, 0.5F);
            for (ServerPlayer p : players()) { Vec3 v = p.m_20182_(); double d2 = hdist2(p); if (d2 < 4.5 * 4.5 && v.f_82480_ < FLOOR + 4) { hurt(p, 11); double d = Math.max(0.3, Math.sqrt(d2)); p.m_20256_(new Vec3((v.f_82479_ - bx) / d, 1.0, (v.f_82481_ - bz) / d)); p.f_19864_ = true; } }
        }
        if (sinkT >= 50) sinkT = -1;
    }

    // ================= Раскол сегмента =================
    void startBreak() {
        int k = bottom();
        breakT = 0; broken++; phase = broken + 1;
        stompT = -1; spinT = -1; sinkT = -1; sinkOff = 0; lances.clear(); shards.clear(); fogs.clear();
        String[] sub = {"", "Руки-обелиски пробудились", "Сонный туман стелется по арене", "Глаз открыт. Монолит ныряет под пол"};
        String[] rom = {"", "II", "III", "IV"};
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " times 5 50 15");
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " subtitle {\"text\":\"" + sub[Math.min(3, broken)] + "\",\"color\":\"gray\",\"italic\":true}");
        Cmd.run(level, AX, FLOOR, AZ, sel("title") + " title {\"text\":\"ФАЗА " + rom[Math.min(3, broken)] + "\",\"color\":\"aqua\",\"bold\":true}");
        var dk = Fx.effect("darkness");
        if (dk != null) for (ServerPlayer p : players()) p.m_7292_(new MobEffectInstance(dk, 40, 0, false, false));
        for (int h = 0; h < 2; h++) { hMode[h] = 0; hT[h] = 0; }
        double y = FLOOR + segY[k];
        Fx.send(level, Fx.particle("flash"), bx, y, bz, 2, 1, 1, 1, 0);
        Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx, y, bz, 200, 1.8, 1.2, 1.8, 0.3);
        Fx.send(level, Fx.blockParticle(Fx.state("diamond_block")), bx, y, bz, 80, 1.8, 1.2, 1.8, 0.3);
        Fx.send(level, Fx.particle("explosion_emitter"), bx, y, bz, 1, 0, 0, 0, 0);
        Fx.sound(level, bx, y, bz, "entity.generic.explode", 20.0F, 0.6F);
        Fx.sound(level, bx, y, bz, "block.glass.break", 20.0F, 0.5F);
        Fx.sound(level, bx, y, bz, "entity.elder_guardian.curse", 20.0F, 0.8F);
        alive[k] = false;
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " name {\"text\":\"Монолит Сна — сегментов: " + aliveCount() + "\",\"color\":\"aqua\",\"bold\":true}");
        String[] msg = {"", "Сегмент раскололся! Руки-обелиски пробудились!", "Второй сегмент пал! Стражи Шва идут!", "Остался последний — ГЛАЗ открыт!"};
        actionbar(msg[Math.min(3, broken)], "aqua");
        if (broken == 3) Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " color purple");
    }

    void tickBreak() {
        breakT++;
        if (breakT % 5 == 0) Fx.send(level, TEAL_P, bx, FLOOR + 2, bz, 15, 2, 1, 2, 0.05);
        if (breakT == 30) spawnMinions();
        if (breakT >= 60) { breakT = -1; actionbar("Бей по нижнему сегменту!", "aqua"); }
    }

    void spawnMinions() {
        int vex = phase == 2 ? 3 : phase == 3 ? 1 : 2, guards = phase == 3 ? 3 : phase == 4 ? 2 : 0;
        int np = Math.max(1, players().size());
        vex = Math.min(6, vex + (np - 1)); guards = Math.min(6, guards + (np - 1) / 2);
        int i = 0;
        for (int v = 0; v < vex; v++, i++) {
            double a = i * 2.1 + RNG.nextDouble(), x = bx + Math.cos(a) * 5, z = bz + Math.sin(a) * 5;
            Minions.spawn(level, "vex", x, FLOOR + 3, z, Minions.common(TAG, TAG + "_min", 20) + "," + Minions.name("Дремотник", "aqua")
                    + ",HandItems:[{id:\"minecraft:echo_shard\",Count:1b},{}],HandDropChances:[0f,0f]");
        }
        for (int g = 0; g < guards; g++, i++) {
            double a = i * 2.1 + RNG.nextDouble(), x = bx + Math.cos(a) * 6, z = bz + Math.sin(a) * 6;
            Minions.spawn(level, "zombie", x, FLOOR, z, Minions.common(TAG, TAG + "_min", 36) + "," + Minions.name("Страж Шва", "dark_aqua")
                    + ",IsBaby:0b,CanPickUpLoot:0b,ArmorItems:[" + Minions.dyed("boots", TEAL) + "," + Minions.dyed("leggings", TEAL) + "," + Minions.dyed("chestplate", TEAL)
                    + ",{id:\"minecraft:warped_wart_block\",Count:1b}],ArmorDropChances:[0f,0f,0f,0f],HandItems:[{id:\"minecraft:diamond_sword\",Count:1b},{}],HandDropChances:[0f,0f]");
        }
        Fx.sound(level, bx, FLOOR + 2, bz, "entity.evoker.prepare_summon", 15.0F, 0.6F);
    }

    // ================= Гибель =================
    void startDeath() {
        stage = 3; dyingT = 0;
        rings.clear(); lances.clear(); shards.clear(); fogs.clear(); sinkOff = 0;
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
        Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=" + TAG + "_min]");
        Fx.sound(level, bx, FLOOR + 3, bz, "entity.elder_guardian.death", 30.0F, 0.5F);
        Fx.sound(level, bx, FLOOR + 3, bz, "entity.warden.death", 30.0F, 0.6F);
    }

    boolean dying() {
        dyingT++;
        eyeOpen = Math.max(0, eyeOpen - 0.02);
        spin = 3 + dyingT * 0.1;
        for (int k = 0; k < 4; k++) segAng[k] += segSpd[k] * spin;
        if (dyingT % 3 == 0) pose(3);
        if (dyingT % 4 == 0) {
            double[] u = Fx.unit();
            Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx + u[0] * 2, FLOOR + 1.5 + Math.abs(u[1]) * 3, bz + u[2] * 2, 20, 0.5, 0.5, 0.5, 0.2);
            Fx.send(level, TEAL_P, bx, FLOOR + 2, bz, 20, 1.8, 1.5, 1.8, 0.1);
            Fx.sound(level, bx, FLOOR + 2, bz, "block.deepslate.break", 8.0F, 0.4F + dyingT / 120F);
        }
        if (dyingT == 100) {
            Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=" + TAG + "]");
            double c = FLOOR + 2;
            Fx.send(level, Fx.particle("flash"), bx, c, bz, 5, 2, 2, 2, 0);
            Fx.send(level, Fx.particle("explosion_emitter"), bx, c, bz, 4, 2, 2, 2, 0);
            for (int i = 0; i < 220; i++) { double[] u = Fx.unit(); Fx.send(level, i % 2 == 0 ? WHITE : TEAL_P, bx, c, bz, 0, u[0], u[1], u[2], 1.6); }
            Fx.sound(level, bx, c, bz, "entity.generic.explode", 30.0F, 0.6F);
            Fx.sound(level, bx, c, bz, "ui.toast.challenge_complete", 20.0F, 1.0F);
            Loot.chest(level, 0, FLOOR, 5, "Добыча Монолита Сна", List.of(
                    "explosionwand:dream_staff", "explosionwand:dreaming_eye", "2 minecraft:nether_star", "minecraft:netherite_block",
                    "4 minecraft:enchanted_golden_apple", "16 minecraft:diamond", "8 minecraft:echo_shard", "6 minecraft:experience_bottle"));
            String at = Cmd.f(AX) + " " + (FLOOR + 1) + " " + Cmd.f(AZ + 3);
            for (int i = 0; i < 12; i++) Cmd.run(level, AX, FLOOR, AZ, "summon experience_orb " + at + " {Value:400}");
            Cmd.run(level, AX, FLOOR, AZ, sel("title") + " subtitle {\"text\":\"Добыча в сундуке, портал домой — в центре\",\"color\":\"gray\"}");
            Cmd.run(level, AX, FLOOR, AZ, sel("title") + " title {\"text\":\"Монолит Сна рассыпался\",\"color\":\"aqua\",\"bold\":true}");
            Arena3.cleared = true;
            return true;
        }
        return false;
    }

    void reset() { Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=" + TAG + "]"); Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR); }

    static boolean environmental(LivingHurtEvent ev) {
        String id = ev.getSource() == null ? "" : ev.getSource().m_19385_();
        return id != null && (id.equals("inWall") || id.equals("fall") || id.equals("cramming") || id.equals("drown") || id.equals("flyIntoWall") || id.equals("inFire") || id.equals("onFire") || id.equals("lightningBolt"));
    }

    static void onHurt(LivingHurtEvent ev) {
        Boss3 b = ACTIVE;
        if (b == null) return;
        LivingEntity target = ev.getEntity();
        // приспешники не бьют друг друга и не страдают от среды
        if (target != b.host) { if (Cmd.hasTagPrefix(target, TAG + "_min") && ev.getSource() != null && ev.getSource().m_7639_() != null && Cmd.hasTagPrefix(ev.getSource().m_7639_(), TAG)) ev.setCanceled(true); return; }
        if (environmental(ev) || b.stage != 2 || b.breakT >= 0) {
            ev.setCanceled(true);
            Fx.send(b.level, WHITE, b.bx, FLOOR + 2, b.bz, 12, 1.5, 1.5, 1.5, 0);
            Fx.sound(b.level, b.bx, FLOOR + 2, b.bz, "block.amethyst_block.hit", 3.0F, 1.8F);
            return;
        }
        float amt = ev.getAmount() * 0.6F * (HOST_HP / MAX_HP);
        if (b.phase >= 4 && b.eyeOpen > 0.6) amt *= 1.35F;     // открытый глаз уязвимее
        // один удар не может расколоть сразу два сегмента
        if (b.broken < 3) {
            float next = HOST_HP * (0.75F - b.broken * 0.25F);
            float hp = b.host.m_21223_();
            if (hp - amt < next) amt = Math.max(0.5F, hp - next);
        }
        ev.setAmount(amt);
        int k = b.bottom();
        double y = FLOOR + b.segY[k];
        Fx.send(b.level, Fx.blockParticle(Fx.state("black_concrete")), b.bx, y, b.bz, 12, 1.4, 0.8, 1.4, 0.1);
        Fx.send(b.level, TEAL_P, b.bx, y + SEG_H / 2, b.bz, 8, 1.6, 0.1, 1.6, 0.05);
        Fx.sound(b.level, b.bx, y, b.bz, "block.deepslate.hit", 2.0F, 0.6F + RNG.nextFloat() * 0.3F);
        Fx.sound(b.level, b.bx, y, b.bz, "block.amethyst_block.hit", 1.2F, 1.2F);
    }
}
