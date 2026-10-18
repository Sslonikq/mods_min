package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * «Сердце Разлома» — живой кристалл, парящий над ареной. Собран из 25 блоков-голограмм:
 * ядро из плачущего обсидиана в стеклянной оболочке, глаз со зрачком, кольца аметистов, корона и цепи.
 * Фазы: осколки и шипы → щит с 4 кристаллами-пилонами и вращающийся луч → ярость: удар о землю и ударные волны.
 */
public class Boss {
    static Boss ACTIVE;
    static final Random RNG = Fx.RNG;
    static final double AX = 0.5, AZ = 0.5;
    static final int FLOOR = 64, N = 25;
    static final float MAX_HP = 900;
    static final String BAR = "explosionwand:rift_heart";
    static final String[] BLOCKS = new String[N];
    static {
        BLOCKS[0] = "crying_obsidian"; BLOCKS[1] = "purple_stained_glass"; BLOCKS[2] = "sea_lantern"; BLOCKS[3] = "black_concrete";
        for (int i = 4; i < 12; i++) BLOCKS[i] = "amethyst_block";
        for (int i = 12; i < 18; i++) BLOCKS[i] = "magenta_stained_glass";
        for (int i = 18; i < 22; i++) BLOCKS[i] = "amethyst_cluster";
        for (int i = 22; i < 25; i++) BLOCKS[i] = "chain";
    }
    static ParticleOptions PURPLE, PINK, RED, WHITE, DARK, AMETHYST;

    final ServerLevel level;
    LivingEntity host;
    final String[] uuid = new String[N];
    int t = 0, stage = 0;            // 0 поиск, 1 появление, 2 бой, 3 гибель
    double bx = AX, by = FLOOR + 6, bz = AZ, orbit = 0;
    int phase = 1, stun = 0, dyingT = 0;
    boolean shield = false, shieldUsed = false, enraged = false;
    int volleyCd = 80, spikeCd = 140, laserCd = 160, slamCd = 120;
    int laserT = -1, slamT = -1; double laserStart = 0;
    float yaw = 0; double ex = 0, ez = 1;
    final List<double[]> shards = new ArrayList<>();      // x,y,z,vx,vy,vz,life
    final List<double[]> spikes = new ArrayList<>();      // x,z,timer
    final List<double[]> waves = new ArrayList<>();       // r
    final Map<ServerPlayer, Integer> hitCd = new WeakHashMap<>();
    final int[][] pylons = {{16, 0}, {-16, 0}, {0, 16}, {0, -16}};
    final boolean[] pylonAlive = new boolean[4];

    static void init() {
        if (PURPLE != null) return;
        PURPLE = new DustParticleOptions(new Vector3f(0.6F, 0.2F, 1.0F), 1.6F);
        PINK = new DustParticleOptions(new Vector3f(1.0F, 0.4F, 0.9F), 1.4F);
        RED = new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.15F), 1.8F);
        WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 1.2F);
        DARK = new DustParticleOptions(new Vector3f(0.15F, 0.0F, 0.25F), 2.5F);
        AMETHYST = Fx.blockParticle(Fx.state("amethyst_block"));
    }

    // ================= Появление =================
    static void spawn(ServerLevel level) {
        init();
        Boss b = new Boss(level);
        ACTIVE = b;
        StringBuilder sb = new StringBuilder();
        sb.append("summon minecraft:giant ").append(Cmd.f(AX)).append(" ").append(FLOOR + 6).append(" ").append(Cmd.f(AZ))
          .append(" {Tags:[\"ewb\",\"ewb_host\"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,")
          .append("Attributes:[{Name:\"minecraft:generic.max_health\",Base:").append((int) MAX_HP).append("d},{Name:\"minecraft:generic.knockback_resistance\",Base:1d}],")
          .append("Health:").append((int) MAX_HP).append("f,ActiveEffects:[{Id:14,Amplifier:0b,Duration:999999,ShowParticles:0b}],Passengers:[");
        for (int i = 0; i < N; i++) {
            if (i > 0) sb.append(",");
            sb.append("{id:\"minecraft:block_display\",Tags:[\"ewb\",\"ewb_p").append(i).append("\"],")
              .append(Disp.block(BLOCKS[i])).append(",view_range:4f,brightness:{sky:15,block:15},")
              .append(Disp.tf(0, -3, 0, 0.01, 0.01, 0.01, Disp.ID)).append("}");
        }
        sb.append("]}");
        Cmd.run(level, AX, FLOOR, AZ, sb.toString());
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
        Cmd.run(level, AX, FLOOR, AZ, "bossbar add " + BAR + " {\"text\":\"Сердце Разлома\",\"color\":\"light_purple\",\"bold\":true}");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " color purple");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " style notched_10");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " max 1000");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " value 1000");
        Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..150] times 10 70 20");
        Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..150] subtitle {\"text\":\"Хранитель между мирами\",\"color\":\"gray\",\"italic\":true}");
        Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..150] title {\"text\":\"Сердце Разлома\",\"color\":\"dark_purple\",\"bold\":true}");
        Fx.sound(level, AX, FLOOR + 6, AZ, "entity.wither.spawn", 20.0F, 0.6F);
        Fx.sound(level, AX, FLOOR + 6, AZ, "block.end_portal.spawn", 20.0F, 0.5F);
        Fx.send(level, Fx.particle("flash"), AX, FLOOR + 6, AZ, 2, 1, 1, 1, 0);
        Fx.send(level, Fx.particle("reverse_portal"), AX, FLOOR + 6, AZ, 300, 1, 1, 1, 2.0);
    }

    Boss(ServerLevel level) { this.level = level; }

    double cy() { return by + 6; }   // центр тела

    List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) if (p.m_6084_() && !p.m_5833_()) out.add(p);
        return out;
    }

    boolean vulnerable() { return stage == 2 && !shield; }

    // ================= Тик =================
    boolean tick() {
        t++;
        if (stage == 0) {
            List<Entity> h = Cmd.tagged(level, AX, FLOOR + 6, AZ, 20, "ewb_host");
            if (h.isEmpty() || !(h.get(0) instanceof LivingEntity le)) { if (t > 40) { reset(); return true; } return false; }
            host = le;
            for (int i = 0; i < N; i++) {
                List<Entity> p = Cmd.tagged(level, AX, FLOOR + 6, AZ, 30, "ewb_p" + i);
                if (!p.isEmpty()) uuid[i] = p.get(0).m_20149_();
            }
            stage = 1; t = 0;
            pose(40, 1.0);
            return false;
        }
        if (host == null) { reset(); return true; }
        if (stage == 3) return dying();
        if (host.m_21223_() <= 0 || host.m_21224_()) { startDeath(); return false; }
        if (!host.m_6084_()) { reset(); return true; }
        if (host.m_6060_()) host.m_20095_();

        move();
        if (t % 4 == 0) pose(4, 1.0);
        aura();
        if (t % 10 == 0) bar();

        if (stage == 1) {
            if (t >= 50) { stage = 2; t = 0; Fx.sound(level, AX, cy(), AZ, "entity.ender_dragon.growl", 20.0F, 0.6F); }
            return false;
        }

        float frac = host.m_21223_() / MAX_HP;
        if (!shieldUsed && frac <= 0.6F) startShield();
        if (!enraged && frac <= 0.3F && !shield) enrage();

        tickShards(); tickSpikes(); tickWaves();
        if (shield) tickShield();
        if (stun > 0) { stun--; dizzy(); return false; }
        attacks();
        return false;
    }

    // ================= Движение и поза =================
    void move() {
        double tx, ty, tz, k = 0.06;
        if (shield) { tx = AX; tz = AZ; ty = FLOOR + 8; }
        else if (stun > 0) { tx = bx; tz = bz; ty = FLOOR - 1; k = 0.25; }
        else if (laserT >= 0) { tx = AX; tz = AZ; ty = FLOOR + 1; }
        else if (slamT >= 0 && slamT < 30) { tx = bx; tz = bz; ty = FLOOR + 10; k = 0.1; }
        else if (slamT >= 30) { tx = bx; tz = bz; ty = FLOOR - 1; k = 0.45; }
        else {
            orbit += enraged ? 0.012 : 0.006;
            double r = enraged ? 6 : 9;
            tx = AX + Math.cos(orbit) * r; tz = AZ + Math.sin(orbit) * r;
            ty = FLOOR + 0.6 + Math.sin(t * 0.05) * 0.6;
        }
        bx += (tx - bx) * k; by += (ty - by) * k; bz += (tz - bz) * k;
        host.m_6034_(bx, by, bz);
        host.m_20256_(new Vec3(0, 0, 0));
        // глаз смотрит на ближайшего
        ServerPlayer tgt = nearest();
        if (tgt != null) {
            Vec3 p = tgt.m_20182_();
            double dx = p.f_82479_ - bx, dz = p.f_82481_ - bz, l = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
            double nx = dx / l, nz = dz / l;
            ex += (nx - ex) * 0.15; ez += (nz - ez) * 0.15;
            double el = Math.max(1e-3, Math.sqrt(ex * ex + ez * ez)); ex /= el; ez /= el;
        }
    }

    ServerPlayer nearest() {
        ServerPlayer best = null; double bd = 1e9;
        for (ServerPlayer p : players()) { Vec3 v = p.m_20182_(); double d = (v.f_82479_ - bx) * (v.f_82479_ - bx) + (v.f_82481_ - bz) * (v.f_82481_ - bz); if (d < bd) { bd = d; best = p; } }
        return best;
    }

    /** Отправить новую позу всех 25 частей (клиент плавно интерполирует). */
    void pose(int dur, double gs) {
        double T = (t + (stage == 3 ? dyingT * 6 : 0)) * (enraged ? 2.0 : 1.0);
        double yawE = Math.atan2(ex, ez);
        for (int i = 0; i < N; i++) {
            if (uuid[i] == null) continue;
            double cx = 0, cyy = 0, cz = 0, sx, sy, sz; double[] q;
            if (i == 0) { sx = sy = sz = 3.0; q = Disp.q(0.3, 1, 0.2, T * 0.04); }
            else if (i == 1) { sx = sy = sz = 3.9; q = Disp.q(1, 0.3, 0.1, -T * 0.025); }
            else if (i == 2 || i == 3) {
                double d = i == 2 ? 2.25 : 2.85;
                cx = ex * d; cz = ez * d; cyy = -0.2;
                sx = sy = sz = i == 2 ? 1.15 : 0.6; if (i == 3) sz = 0.3;
                q = Disp.q(0, 1, 0, yawE);
            } else if (i < 12) {
                double a = (i - 4) * Math.PI * 2 / 8 + T * 0.05;
                cx = Math.cos(a) * 4.4; cz = Math.sin(a) * 4.4; cyy = Math.sin(a * 2 + T * 0.1) * 0.4;
                sx = sy = sz = 0.95; q = Disp.mul(Disp.q(0, 1, 0, -a), Disp.q(1, 0, 1, Math.PI / 4));
            } else if (i < 18) {
                double a = (i - 12) * Math.PI * 2 / 6 - T * 0.07;
                double[] p = Disp.rot(Disp.mul(Disp.q(0, 1, 0, T * 0.01), Disp.q(1, 0, 0, 1.1)), Math.cos(a) * 3.2, 0, Math.sin(a) * 3.2);
                cx = p[0]; cyy = p[1]; cz = p[2];
                sx = sy = sz = 0.7; q = Disp.q(1, 1, 0, T * 0.1 + i);
            } else if (i < 22) {
                double a = (i - 18) * Math.PI / 2 + T * 0.02;
                cx = Math.cos(a) * 1.1; cz = Math.sin(a) * 1.1; cyy = 2.3;
                sx = 1.3; sy = 1.8; sz = 1.3; q = Disp.q(-Math.sin(a), 0, Math.cos(a), 0.35);
            } else {
                double a = (i - 22) * Math.PI * 2 / 3 + T * 0.015;
                cx = Math.cos(a) * 0.9; cz = Math.sin(a) * 0.9; cyy = -3.4 + Math.sin(T * 0.08 + i) * 0.2;
                sx = 0.9; sy = 3.2; sz = 0.9; q = Disp.q(1, 0, 0, Math.sin(T * 0.06 + i) * 0.15);
            }
            String tf = Disp.tf(cx * gs, cyy * gs - 3, cz * gs, Math.max(0.001, sx * gs), Math.max(0.001, sy * gs), Math.max(0.001, sz * gs), q);
            Cmd.run(level, bx, by, bz, "data merge entity " + uuid[i] + " {start_interpolation:0,interpolation_duration:" + dur + "," + tf + "}");
        }
    }

    void setBlock(int i, String id) { if (uuid[i] != null) Cmd.run(level, bx, by, bz, "data merge entity " + uuid[i] + " {" + Disp.block(id) + "}"); }

    void aura() {
        double c = cy();
        Fx.send(level, Fx.particle("reverse_portal"), bx, c, bz, 4, 2, 2, 2, 0.05);
        if (t % 3 == 0) Fx.send(level, enraged ? RED : PURPLE, bx, c, bz, 6, 2.5, 2.5, 2.5, 0);
        if (t % 60 == 0) Fx.sound(level, bx, c, bz, "block.beacon.ambient", 6.0F, enraged ? 0.9F : 0.5F);
        if (shield) for (int i = 0; i < 30; i++) {
            double[] u = Fx.unit();
            Fx.send(level, WHITE, bx + u[0] * 5.2, c + u[1] * 5.2, bz + u[2] * 5.2, 1, 0, 0, 0, 0);
        }
    }

    void bar() {
        int v = (int) Math.max(0, Math.min(1000, host.m_21223_() / MAX_HP * 1000));
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " value " + v);
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " players @a[x=0,y=64,z=0,distance=..150]");
    }

    void actionbar(String text, String color) {
        Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..150] actionbar {\"text\":\"" + text + "\",\"color\":\"" + color + "\"}");
    }

    void hurtPlayer(ServerPlayer p, float dmg) { p.m_6469_(level.m_269111_().m_269425_(), dmg); }

    // ================= Атаки =================
    void attacks() {
        List<ServerPlayer> ps = players();
        if (ps.isEmpty()) return;
        if (--volleyCd <= 0) { volley(ps); volleyCd = enraged ? 38 : (phase >= 2 ? 55 : 70); }
        if (--spikeCd <= 0) { for (ServerPlayer p : ps) { Vec3 v = p.m_20182_(); spikes.add(new double[]{v.f_82479_, v.f_82481_, 30}); } spikeCd = enraged ? 90 : 140; }
        if (shieldUsed && laserT < 0 && slamT < 0 && --laserCd <= 0) { laserT = 0; laserStart = RNG.nextDouble() * Math.PI * 2; laserCd = enraged ? 230 : 280; }
        if (laserT >= 0) laser(ps);
        if (enraged && laserT < 0 && slamT < 0 && --slamCd <= 0) { slamT = 0; slamCd = 240; }
        if (slamT >= 0) slam(ps);
    }

    void volley(List<ServerPlayer> ps) {
        double c = cy();
        int n = enraged ? 8 : 5;
        double sp = enraged ? 0.95 : 0.75;
        Fx.sound(level, bx, c, bz, "block.amethyst_block.resonate", 6.0F, 0.6F);
        for (int i = 0; i < n; i++) {
            ServerPlayer p = ps.get(RNG.nextInt(ps.size()));
            double a = i * Math.PI * 2 / n + t;
            double sx = bx + Math.cos(a) * 4.4, sy = c + (RNG.nextDouble() - 0.5), sz = bz + Math.sin(a) * 4.4;
            Vec3 pv = p.m_20182_(), vel = p.m_20184_();
            double dist = Math.sqrt((pv.f_82479_ - sx) * (pv.f_82479_ - sx) + (pv.f_82481_ - sz) * (pv.f_82481_ - sz));
            double lead = dist / sp;
            double tx = pv.f_82479_ + vel.f_82479_ * lead + (RNG.nextDouble() - 0.5) * 2, ty = pv.f_82480_ + 1.2, tz = pv.f_82481_ + vel.f_82481_ * lead + (RNG.nextDouble() - 0.5) * 2;
            double dx = tx - sx, dy = ty - sy, dz = tz - sz, l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
            shards.add(new double[]{sx, sy, sz, dx / l * sp, dy / l * sp, dz / l * sp, 0});
        }
    }

    void tickShards() {
        Iterator<double[]> it = shards.iterator();
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
                        hurtPlayer(p, enraged ? 8 : 6);
                        p.m_20256_(p.m_20184_().m_82520_(s[3] * 0.7, 0.35, s[5] * 0.7)); p.f_19864_ = true;
                        Fx.send(level, AMETHYST, s[0], s[1], s[2], 25, 0.3, 0.3, 0.3, 0.2);
                        Fx.sound(level, s[0], s[1], s[2], "block.amethyst_cluster.break", 2.0F, 0.8F);
                        it.remove(); continue outer;
                    }
                }
                if (!level.m_8055_(new BlockPos((int) Math.floor(s[0]), (int) Math.floor(s[1]), (int) Math.floor(s[2]))).m_60795_() || ++s[6] > 180) {
                    Fx.send(level, AMETHYST, s[0], s[1], s[2], 12, 0.2, 0.2, 0.2, 0.15);
                    Fx.sound(level, s[0], s[1], s[2], "block.amethyst_block.break", 1.0F, 1.2F);
                    it.remove(); continue outer;
                }
            }
            Fx.send(level, AMETHYST, s[0], s[1], s[2], 2, 0.05, 0.05, 0.05, 0);
            Fx.send(level, enraged ? RED : PINK, s[0], s[1], s[2], 1, 0, 0, 0, 0);
            Fx.send(level, Fx.particle("end_rod"), s[0] - s[3], s[1] - s[4], s[2] - s[5], 1, 0, 0, 0, 0);
        }
    }

    void tickSpikes() {
        Iterator<double[]> it = spikes.iterator();
        while (it.hasNext()) {
            double[] s = it.next();
            s[2]--;
            double r = 0.4 + 2.2 * (s[2] / 30.0);
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI * 2 / 20 + t * 0.1;
                Fx.send(level, enraged ? RED : PURPLE, s[0] + Math.cos(a) * r, FLOOR + 0.15, s[1] + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (s[2] % 6 == 0) Fx.send(level, Fx.particle("witch"), s[0], FLOOR + 0.3, s[1], 4, 0.6, 0.1, 0.6, 0);
            if (s[2] <= 0) {
                Fx.send(level, AMETHYST, s[0], FLOOR + 1.5, s[1], 60, 0.6, 1.5, 0.6, 0.3);
                for (int h = 0; h < 6; h++) Fx.send(level, Fx.particle("end_rod"), s[0], FLOOR + h * 0.6, s[1], 3, 0.4, 0.2, 0.4, 0.15);
                Fx.sound(level, s[0], FLOOR, s[1], "entity.evoker_fangs.attack", 2.0F, 0.7F);
                Fx.sound(level, s[0], FLOOR, s[1], "block.amethyst_cluster.break", 2.0F, 0.6F);
                for (ServerPlayer p : players()) {
                    Vec3 v = p.m_20182_();
                    double dx = v.f_82479_ - s[0], dz = v.f_82481_ - s[1];
                    if (dx * dx + dz * dz < 2.4 * 2.4 && v.f_82480_ < FLOOR + 3) { hurtPlayer(p, 7); p.m_20256_(new Vec3(dx * 0.2, 1.1, dz * 0.2)); p.f_19864_ = true; }
                }
                it.remove();
            }
        }
    }

    void laser(List<ServerPlayer> ps) {
        laserT++;
        double y = FLOOR + 0.6;
        if (laserT < 25) {          // заряд + предупреждение
            for (double d = 2; d < 27; d += 1.2)
                Fx.send(level, WHITE, bx + Math.cos(laserStart) * d, y, bz + Math.sin(laserStart) * d, 1, 0, 0, 0, 0);
            Fx.send(level, enraged ? RED : PINK, bx, cy(), bz, 10, 0.5, 0.5, 0.5, 0);
            if (laserT == 1) { Fx.sound(level, bx, cy(), bz, "block.beacon.power_select", 10.0F, 0.5F); actionbar("Прыгай через луч!", "red"); }
            return;
        }
        int beams = enraged ? 2 : 1;
        double ang0 = laserStart + (laserT - 25) * 0.0628 * (enraged ? 1.3 : 1.0);
        for (int b = 0; b < beams; b++) {
            double ang = ang0 + b * Math.PI;
            double cx = Math.cos(ang), cz = Math.sin(ang);
            for (double d = 1; d < 27; d += 0.6) {
                Fx.send(level, enraged ? RED : PURPLE, bx + cx * d, y, bz + cz * d, 1, 0, 0, 0, 0);
                if (((int) (d * 10)) % 18 == 0) Fx.send(level, Fx.particle("end_rod"), bx + cx * d, y, bz + cz * d, 1, 0, 0, 0, 0);
            }
            for (ServerPlayer p : ps) {
                Vec3 v = p.m_20182_();
                double rx = v.f_82479_ - bx, rz = v.f_82481_ - bz;
                double along = rx * cx + rz * cz, lat = Math.abs(-rx * cz + rz * cx);
                if (along > 0 && along < 28 && lat < 0.8 && v.f_82480_ < FLOOR + 0.9) {
                    int last = hitCd.getOrDefault(p, -100);
                    if (t - last > 10) { hitCd.put(p, t); hurtPlayer(p, 6); Fx.send(level, RED, v.f_82479_, v.f_82480_ + 0.5, v.f_82481_, 15, 0.3, 0.3, 0.3, 0); }
                }
            }
        }
        for (double h = 0; h < cy() - FLOOR; h += 0.5) Fx.send(level, enraged ? RED : PURPLE, bx, FLOOR + h, bz, 1, 0.1, 0, 0.1, 0);
        if (laserT % 8 == 0) Fx.sound(level, bx, FLOOR, bz, "block.beacon.ambient", 6.0F, 1.6F);
        if (laserT >= 125) laserT = -1;
    }

    void slam(List<ServerPlayer> ps) {
        slamT++;
        var lev = Fx.effect("levitation");
        if (slamT == 1) {
            Fx.sound(level, bx, cy(), bz, "entity.wither.shoot", 10.0F, 0.5F);
            actionbar("Гравитация рушится...", "light_purple");
            for (ServerPlayer p : ps) if (lev != null) p.m_7292_(new MobEffectInstance(lev, 28, 2));
        }
        if (slamT < 30 && slamT % 3 == 0) for (ServerPlayer p : ps) { Vec3 v = p.m_20182_(); Fx.send(level, PURPLE, v.f_82479_, v.f_82480_, v.f_82481_, 4, 0.3, 0.1, 0.3, 0); }
        if (slamT == 30) for (ServerPlayer p : ps) { if (lev != null) p.m_21195_(lev); p.m_20256_(new Vec3(0, -1.6, 0)); p.f_19864_ = true; }
        if (slamT == 36) {
            waves.add(new double[]{2});
            Fx.send(level, Fx.particle("explosion_emitter"), bx, FLOOR + 1, bz, 2, 1, 0.2, 1, 0);
            Fx.sound(level, bx, FLOOR, bz, "entity.generic.explode", 20.0F, 0.5F);
            Fx.sound(level, bx, FLOOR, bz, "block.anvil.land", 10.0F, 0.5F);
            actionbar("Прыгай через волну!", "red");
        }
        if (slamT >= 70) slamT = -1;
    }

    void tickWaves() {
        Iterator<double[]> it = waves.iterator();
        while (it.hasNext()) {
            double[] w = it.next();
            w[0] += 0.9;
            int n = (int) (20 + w[0] * 6);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                Fx.send(level, RED, bx + Math.cos(a) * w[0], FLOOR + 0.3, bz + Math.sin(a) * w[0], 1, 0, 0, 0, 0);
                if (i % 4 == 0) Fx.send(level, Fx.particle("cloud"), bx + Math.cos(a) * w[0], FLOOR + 0.2, bz + Math.sin(a) * w[0], 1, 0, 0, 0, 0);
            }
            for (ServerPlayer p : players()) {
                Vec3 v = p.m_20182_();
                double dx = v.f_82479_ - bx, dz = v.f_82481_ - bz, d = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(d - w[0]) < 0.9 && v.f_82480_ < FLOOR + 0.5) {
                    int last = hitCd.getOrDefault(p, -100);
                    if (t - last > 10) { hitCd.put(p, t); hurtPlayer(p, 7); p.m_20256_(new Vec3(dx / Math.max(d, 0.1), 0.7, dz / Math.max(d, 0.1))); p.f_19864_ = true; }
                }
            }
            if (w[0] > 30) it.remove();
        }
    }

    // ================= Щит и пилоны =================
    void startShield() {
        shieldUsed = true; shield = true; phase = 2;
        setBlock(1, "white_stained_glass");
        BlockState obs = Fx.state("obsidian"), am = Fx.state("amethyst_block");
        for (int i = 0; i < 4; i++) {
            int x = pylons[i][0], z = pylons[i][1];
            level.m_7731_(new BlockPos(x, FLOOR, z), obs, 3);
            level.m_7731_(new BlockPos(x, FLOOR + 1, z), obs, 3);
            level.m_7731_(new BlockPos(x, FLOOR + 2, z), am, 3);
            pylonAlive[i] = true;
            Fx.send(level, Fx.particle("flash"), x + 0.5, FLOOR + 2.5, z + 0.5, 1, 0, 0, 0, 0);
        }
        Fx.sound(level, AX, cy(), AZ, "block.beacon.activate", 20.0F, 0.6F);
        Fx.sound(level, AX, cy(), AZ, "entity.ender_dragon.growl", 20.0F, 0.8F);
        actionbar("Сердце под щитом! Разбей 4 аметистовых кристалла на столбах!", "light_purple");
    }

    /** Аметистовое ядро пилона активного щита: его должны уметь разбить игроки/взрывы. */
    static boolean isPylonCore(ServerLevel lvl, BlockPos pos) {
        Boss b = ACTIVE;
        if (b == null || b.level != lvl || !b.shield || pos.m_123342_() != FLOOR + 2) return false;
        for (int i = 0; i < 4; i++)
            if (b.pylonAlive[i] && b.pylons[i][0] == pos.m_123341_() && b.pylons[i][1] == pos.m_123343_()) return true;
        return false;
    }

    void tickShield() {
        int alive = 0;
        for (int i = 0; i < 4; i++) {
            if (!pylonAlive[i]) continue;
            int x = pylons[i][0], z = pylons[i][1];
            BlockPos core = new BlockPos(x, FLOOR + 2, z);
            if (level.m_8055_(core).m_60734_() != Fx.block("amethyst_block")) {
                pylonAlive[i] = false;
                Fx.send(level, Fx.particle("explosion"), x + 0.5, FLOOR + 2.5, z + 0.5, 3, 0.3, 0.3, 0.3, 0);
                Fx.sound(level, x, FLOOR + 2, z, "block.glass.break", 6.0F, 0.6F);
                Fx.sound(level, AX, cy(), AZ, "entity.wither.hurt", 10.0F, 0.7F);
                continue;
            }
            alive++;
            if (t % 2 == 0) {
                double sx = x + 0.5, sy = FLOOR + 2.5, sz = z + 0.5, dx = bx - sx, dy = cy() - sy, dz = bz - sz;
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                for (double s = 0; s < l; s += 0.7) Fx.send(level, PINK, sx + dx * s / l, sy + dy * s / l, sz + dz * s / l, 1, 0, 0, 0, 0);
                Fx.send(level, Fx.particle("end_rod"), sx, sy + 0.6, sz, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }
        if (alive == 0) {
            shield = false; stun = 140;
            setBlock(1, enraged ? "red_stained_glass" : "purple_stained_glass");
            Fx.send(level, WHITE, bx, cy(), bz, 200, 3, 3, 3, 0.2);
            Fx.send(level, Fx.particle("flash"), bx, cy(), bz, 2, 1, 1, 1, 0);
            Fx.sound(level, bx, cy(), bz, "block.glass.break", 20.0F, 0.4F);
            Fx.sound(level, bx, cy(), bz, "entity.ender_dragon.hurt", 20.0F, 0.6F);
            actionbar("Щит разбит! Бей его, пока оглушён!", "gold");
            clearPylons();
        }
    }

    void clearPylons() {
        BlockState air = Fx.state("air");
        for (int[] p : pylons) for (int y = 0; y < 3; y++) level.m_7731_(new BlockPos(p[0], FLOOR + y, p[1]), air, 3);
    }

    void dizzy() {
        double c = cy() + 4;
        for (int i = 0; i < 5; i++) {
            double a = t * 0.3 + i * Math.PI * 2 / 5;
            Fx.send(level, Fx.particle("crit"), bx + Math.cos(a) * 2, c, bz + Math.sin(a) * 2, 1, 0, 0, 0, 0);
        }
    }

    void enrage() {
        enraged = true; phase = 3;
        setBlock(0, "redstone_block"); setBlock(1, "red_stained_glass"); setBlock(2, "shroomlight");
        Fx.sound(level, bx, cy(), bz, "entity.ravager.roar", 20.0F, 0.5F);
        Fx.sound(level, bx, cy(), bz, "entity.wither.spawn", 20.0F, 1.2F);
        Fx.send(level, RED, bx, cy(), bz, 200, 4, 4, 4, 0);
        Cmd.run(level, AX, FLOOR, AZ, "bossbar set " + BAR + " color red");
        actionbar("Сердце в ярости!", "dark_red");
    }

    // ================= Гибель =================
    void startDeath() {
        stage = 3; dyingT = 0;
        shards.clear(); spikes.clear(); waves.clear();
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
        Fx.sound(level, bx, cy(), bz, "entity.wither.death", 20.0F, 0.6F);
        Fx.sound(level, bx, cy(), bz, "entity.ender_dragon.death", 20.0F, 0.8F);
        clearPylons();
    }

    boolean dying() {
        dyingT++;
        double c = cy();
        if (dyingT == 1) pose(80, 0.0);
        if (dyingT % 3 == 0) {
            double[] u = Fx.unit();
            Fx.send(level, Fx.particle("explosion"), bx + u[0] * 4, c + u[1] * 4, bz + u[2] * 4, 1, 0, 0, 0, 0);
            Fx.send(level, AMETHYST, bx, c, bz, 20, 2, 2, 2, 0.3);
            Fx.sound(level, bx, c, bz, "block.amethyst_cluster.break", 6.0F, 0.5F + dyingT / 100F);
        }
        Fx.send(level, Fx.particle("end_rod"), bx, c, bz, 4, 0.3, 0.3, 0.3, 0.3);
        if (dyingT == 90) {
            Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=ewb]");
            Fx.send(level, Fx.particle("flash"), bx, c, bz, 3, 1, 1, 1, 0);
            Fx.send(level, Fx.particle("explosion_emitter"), bx, c, bz, 3, 2, 2, 2, 0);
            Fx.send(level, Fx.particle("reverse_portal"), bx, c, bz, 400, 1, 1, 1, 3.0);
            Fx.sound(level, bx, c, bz, "entity.generic.explode", 20.0F, 0.6F);
            Fx.sound(level, bx, c, bz, "ui.toast.challenge_complete", 10.0F, 1.0F);
            Loot.pillar(level, 0, FLOOR, 5);
            Loot.chest(level, 0, FLOOR, 5, "Добыча Сердца Разлома", List.of(
                    "explosionwand:rift_core", "8 minecraft:diamond", "minecraft:netherite_ingot", "32 minecraft:amethyst_shard"));
            String at = Cmd.f(AX) + " " + (FLOOR + 1) + " " + Cmd.f(AZ + 3);
            for (int i = 0; i < 6; i++) Cmd.run(level, AX, FLOOR, AZ, "summon experience_orb " + at + " {Value:250}");
            Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..150] subtitle {\"text\":\"Добыча в сундуке, портал домой — в центре\",\"color\":\"gray\"}");
            Cmd.run(level, AX, FLOOR, AZ, "title @a[x=0,y=64,z=0,distance=..150] title {\"text\":\"Сердце Разлома повержено\",\"color\":\"gold\",\"bold\":true}");
            Arena.cleared = true;
            return true;
        }
        return false;
    }

    void reset() {
        Cmd.run(level, AX, FLOOR, AZ, "kill @e[tag=ewb]");
        Cmd.run(level, AX, FLOOR, AZ, "bossbar remove " + BAR);
        clearPylons();
    }

    /** Урон по сердцу: щит, вспышки и звон. */
    static void onHurt(LivingHurtEvent ev) {
        Boss b = ACTIVE;
        if (b == null || ev.getEntity() != b.host) return;
        if (!b.vulnerable()) {
            ev.setCanceled(true);
            Fx.send(b.level, WHITE, b.bx, b.cy(), b.bz, 20, 2, 2, 2, 0);
            Fx.sound(b.level, b.bx, b.cy(), b.bz, "block.amethyst_block.hit", 4.0F, 0.5F);
            return;
        }
        Fx.send(b.level, AMETHYST, b.bx, b.cy(), b.bz, 15, 1.5, 1.5, 1.5, 0.2);
        Fx.sound(b.level, b.bx, b.cy(), b.bz, "block.amethyst_block.hit", 4.0F, 0.7F + RNG.nextFloat() * 0.3F);
    }
}
