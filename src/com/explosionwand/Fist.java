package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Кулак Небес 2.0. Над целью раскрывается кольцо облаков с золотыми рунами, на земле загорается печать.
 * Из облаков медленно выходит мраморная рука с золотым браслетом, светящимися прожилками и кулаком
 * из отдельных пальцев. Замах — и удар: воронка, разлетающиеся камни, три ударные волны, трещины по земле.
 * Потом пальцы разжимаются, ладонь ложится на землю, и рука уходит обратно в небо.
 */
public class Fist {
    static final List<Fist> ACTIVE = new ArrayList<>();
    static int counter = 0;
    static DustParticleOptions SHADOW, GOLD, WHITE, CRACK;

    // блок; сжатый: центр xyz, размер xyz, ось+угол; разжатый: центр xyz, ось+угол (null = как сжатый)
    static final List<Object[]> P = new ArrayList<>();
    static void part(String b, double cx, double cy, double cz, double sx, double sy, double sz, double[] rot, double[] open) {
        P.add(new Object[]{b, cx, cy, cz, sx, sy, sz, rot, open});
    }
    static double[] r(double ax, double ay, double az, double ang) { return new double[]{ax, ay, az, ang}; }
    static {
        part("smooth_quartz", 0, 3.4, -0.3, 5.6, 4.2, 4.6, null, null);                         // тыльная сторона
        double[] xs = {-2.0, -0.68, 0.68, 2.0};
        for (double xi : xs) {
            part("quartz_block", xi, 1.0, 1.6, 1.25, 2.0, 1.3, null, new double[]{xi, 0.65, 2.6, 1, 0, 0, -Math.PI / 2});
            part("quartz_block", xi, 0.45, 0.4, 1.2, 0.9, 1.6, null, new double[]{xi, 0.6, 4.2, 0, 0, 0, 0});
            part("gold_block", xi, 1.85, 2.28, 1.3, 0.3, 0.12, null, new double[]{xi, 1.2, 2.0, 1, 0, 0, -Math.PI / 2});
        }
        part("smooth_quartz", -3.0, 2.6, 0.9, 1.3, 2.4, 1.5, r(0, 0, 1, 0.35), new double[]{-3.8, 1.4, 1.2, 0, 0, 1, 1.1});
        part("quartz_block", -2.3, 1.2, 1.7, 1.2, 1.2, 1.3, null, new double[]{-4.9, 0.7, 1.8, 0, 0, 1, 1.4});
        part("gold_block", 0, 6.2, -0.3, 6.2, 1.4, 5.2, null, null);                            // браслет
        part("sea_lantern", 0, 6.2, 2.33, 1.3, 1.3, 0.3, r(0, 0, 1, Math.PI / 4), null);          // камень
        part("glowstone", 3.12, 6.2, -0.3, 0.08, 0.5, 4.2, null, null);
        part("glowstone", -3.12, 6.2, -0.3, 0.08, 0.5, 4.2, null, null);
        part("smooth_quartz", 0, 21.0, -0.3, 4.6, 28.0, 4.2, null, null);                        // предплечье
        part("gold_block", 0, 14.0, -0.3, 4.9, 0.6, 4.5, null, null);
        part("gold_block", 0, 25.0, -0.3, 4.9, 0.6, 4.5, null, null);
        part("shroomlight", 0.7, 18.0, 1.82, 0.12, 6.0, 0.1, r(0, 0, 1, 0.12), null);           // светящиеся прожилки
        part("shroomlight", -1.0, 28.0, 1.82, 0.12, 7.0, 0.1, r(0, 0, 1, -0.15), null);
        part("shroomlight", 2.32, 21.0, 0.2, 0.1, 9.0, 0.12, r(1, 0, 0, 0.1), null);
    }

    final ServerLevel level; final Entity owner; final double x, y, z; final double[] q; final String tag;
    final double fx, fz;   // «вперёд» кулака
    final List<String> uuids = new ArrayList<>();
    int t = 0;

    Fist(ServerLevel level, Entity owner, Vec3 at, float yawDeg) {
        this.level = level; this.owner = owner; this.x = at.f_82479_; this.y = at.f_82480_; this.z = at.f_82481_;
        double yr = Math.toRadians(yawDeg);
        this.q = Disp.q(0, 1, 0, -yr);
        this.fx = -Math.sin(yr); this.fz = Math.cos(yr);
        this.tag = "ewf" + (counter++);
        if (SHADOW == null) {
            SHADOW = new DustParticleOptions(new Vector3f(0.05F, 0.05F, 0.08F), 3.0F);
            GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.3F), 1.6F);
            WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 2.4F);
            CRACK = new DustParticleOptions(new Vector3f(0.25F, 0.12F, 0.05F), 1.6F);
        }
        Fx.sound(level, x, y + 30, z, "entity.lightning_bolt.thunder", 10.0F, 0.4F);
        Fx.sound(level, x, y, z, "block.beacon.activate", 6.0F, 0.6F);
    }

    String tfAt(int i, double lift, boolean open) {
        Object[] p = P.get(i);
        double cx = (Double) p[1], cy = (Double) p[2], cz = (Double) p[3];
        double[] rot = (double[]) p[7], op = (double[]) p[8];
        double[] lq = rot == null ? Disp.ID : Disp.q(rot[0], rot[1], rot[2], rot[3]);
        if (open && op != null) { cx = op[0]; cy = op[1]; cz = op[2]; lq = op[6] == 0 ? Disp.ID : Disp.q(op[3], op[4], op[5], op[6]); }
        double[] c = Disp.rot(q, cx, cy, cz);
        return Disp.tf(c[0], c[1] + lift, c[2], (Double) p[4], (Double) p[5], (Double) p[6], Disp.mul(q, lq));
    }

    void merge(int dur, double lift, boolean open) {
        for (int i = 0; i < uuids.size(); i++)
            Cmd.run(level, x, y, z, "data merge entity " + uuids.get(i) + " {start_interpolation:0,interpolation_duration:" + dur + "," + tfAt(i, lift, open) + "}");
    }

    boolean tick() {
        t++;
        double cloudY = y + 46;
        if (t < 130) {                       // кольцо облаков в небе
            double rr = Math.min(9, t * 0.3);
            for (int i = 0; i < 26; i++) {
                double a = i * Math.PI * 2 / 26 + t * 0.04;
                Fx.send(level, Fx.particle("cloud"), x + Math.cos(a) * rr, cloudY + Math.sin(a * 3 + t * 0.1) * 0.5, z + Math.sin(a) * rr, 1, 0.3, 0.2, 0.3, 0.01);
                if (i % 3 == 0) Fx.send(level, GOLD, x + Math.cos(-a) * (rr - 1.5), cloudY - 0.5, z + Math.sin(-a) * (rr - 1.5), 1, 0, 0, 0, 0);
            }
            if (t < 80 && t % 2 == 0) Fx.send(level, Fx.particle("end_rod"), x, cloudY - 10, z, 4, rr * 0.5, 10, rr * 0.5, 0);
        }
        if (t < 84) {                        // печать на земле
            double rr = 8;
            for (int i = 0; i < 36; i++) {
                double a = i * Math.PI * 2 / 36 - t * 0.03;
                Fx.send(level, GOLD, x + Math.cos(a) * rr, y + 0.15, z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                if (i % 4 == 0) Fx.send(level, GOLD, x + Math.cos(a) * rr * 0.7, y + 0.15, z + Math.sin(a) * rr * 0.7, 1, 0, 0, 0, 0);
            }
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + t * 0.03;
                for (double d = 1; d < rr * 0.7; d += 0.7) Fx.send(level, GOLD, x + Math.cos(a) * d, y + 0.15, z + Math.sin(a) * d, 1, 0, 0, 0, 0);
            }
            double sh = 1.5 + t * 0.06;
            Fx.send(level, SHADOW, x, y + 0.12, z, 14, sh * 0.5, 0, sh * 0.5, 0);
            if (t % 15 == 0) Fx.sound(level, x, cloudY, z, "entity.lightning_bolt.thunder", 6.0F, 0.4F + t / 150F);
        }
        if (t == 40) {
            for (int i = 0; i < P.size(); i++)
                Cmd.run(level, x, y, z, "summon minecraft:block_display " + Cmd.f(x) + " " + Cmd.f(y) + " " + Cmd.f(z)
                        + " {Tags:[\"ewf\",\"" + tag + "\",\"" + tag + "_" + i + "\"]," + Disp.block((String) P.get(i)[0]) + ",view_range:5f," + tfAt(i, 62, false) + "}");
            Fx.sound(level, x, cloudY, z, "block.end_portal.spawn", 8.0F, 1.4F);
        }
        if (t == 43) {
            for (int i = 0; i < P.size(); i++) {
                List<Entity> f = Cmd.tagged(level, x, y, z, 4, tag + "_" + i);
                uuids.add(f.isEmpty() ? null : f.get(0).m_20149_());
            }
            uuids.removeIf(java.util.Objects::isNull);
            merge(25, 30, false);                                      // медленно выходит из облаков
        }
        if (t == 68) { merge(12, 38, false); Fx.sound(level, x, y + 30, z, "block.beacon.power_select", 8.0F, 0.5F); }  // замах
        if (t > 68 && t < 80) {
            for (int i = 0; i < 6; i++) {        // свет стягивается к кулаку
                double[] u = Fx.unit();
                double sx = x + u[0] * 10, sy = y + 38 + u[1] * 6, sz = z + u[2] * 10;
                Fx.send(level, Fx.particle("end_rod"), sx, sy, sz, 0, (x - sx) * 0.1, (y + 40 - sy) * 0.1, (z - sz) * 0.1, 1.0);
            }
        }
        if (t == 80) { merge(4, 0, false); Fx.sound(level, x, y + 20, z, "item.trident.riptide_3", 10.0F, 0.5F); }   // удар
        if (t > 80 && t < 84) Fx.send(level, WHITE, x, y + 10 + (84 - t) * 8, z, 20, 3, 4, 3, 0);
        if (t == 84) impact();
        if (t > 84 && t < 124) {
            if (t % 2 == 0) cracks(Math.min(1.0, (t - 84) / 8.0));
            if (t % 4 == 0) Fx.send(level, Fx.particle("campfire_cosy_smoke"), x, y + 0.5, z, 5, 6, 0.2, 6, 0.015);
        }
        if (t == 108) { merge(12, 0, true); Fx.sound(level, x, y + 3, z, "block.stone.break", 4.0F, 0.5F); }   // пальцы разжимаются
        if (t == 128) { merge(30, 75, true); Fx.sound(level, x, y + 10, z, "block.beacon.deactivate", 6.0F, 0.6F); }
        if (t >= 160) { Cmd.run(level, x, y, z, "kill @e[tag=" + tag + "]"); return true; }
        return false;
    }

    final double[] crackAng = new double[10];
    { for (int i = 0; i < 10; i++) crackAng[i] = i * Math.PI * 2 / 10 + Fx.RNG.nextDouble() * 0.4; }

    void cracks(double grow) {
        for (int i = 0; i < 10; i++) {
            double a = crackAng[i], len = 15 * grow;
            double cx = x, cz = z;
            for (double d = 2; d < len; d += 0.5) {
                double wob = Math.sin(d * 1.3 + i) * 0.5;
                Fx.send(level, CRACK, x + Math.cos(a) * d - Math.sin(a) * wob, y + 0.12, z + Math.sin(a) * d + Math.cos(a) * wob, 1, 0, 0, 0, 0);
                if (Fx.RNG.nextInt(25) == 0) Fx.send(level, Fx.particle("lava"), x + Math.cos(a) * d, y + 0.2, z + Math.sin(a) * d, 1, 0, 0, 0, 0);
            }
        }
    }

    void impact() {
        level.m_255391_(owner, x, y + 0.5, z, 7.0F, false, Level.ExplosionInteraction.valueOf("TNT"));
        Debris.fling(level, x, y, z, 4.0, 9.0, 140, 1.15, 0.8);
        for (Entity e : level.m_6249_(owner, new AABB(x - 16, y - 4, z - 16, x + 16, y + 10, z + 16), e -> e.m_6084_())) {
            if (Cmd.hasTagPrefix(e, "ew")) continue;
            Vec3 p = e.m_20182_(); double dx = p.f_82479_ - x, dz = p.f_82481_ - z, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            double k = Math.max(0, 1 - d / 16);
            e.m_20256_(new Vec3(dx / d * 1.8 * k, 0.6 + 1.3 * k, dz / d * 1.8 * k)); e.f_19864_ = true;
        }
        Fx.send(level, Fx.particle("flash"), x, y + 2, z, 3, 1, 1, 1, 0);
        Fx.send(level, Fx.particle("explosion_emitter"), x, y + 1, z, 3, 2.5, 0.3, 2.5, 0);
        Fx.send(level, Fx.particle("firework"), x, y + 2, z, 150, 1, 1, 1, 0.6);
        for (int i = 0; i < 80; i++) { double[] u = Fx.unit(); Fx.send(level, Fx.particle("end_rod"), x, y + 2, z, 0, u[0], Math.abs(u[1]), u[2], 1.2); }
        Fx.send(level, Fx.blockParticle(level.m_8055_(new net.minecraft.core.BlockPos((int) Math.floor(x), (int) Math.floor(y) - 1, (int) Math.floor(z)))), x, y + 0.5, z, 250, 5, 0.6, 5, 0.5);
        double[][] rings = {{1.0, 0}, {0.65, 3}, {0.4, 7}};   // скорость, задержка
        for (double[] rg : rings) {
            for (int s = 0; s < 22; s++) {
                final double rad = 2 + s * rg[0] * 0.9 + 0.01;
                Later.run(level, (int) rg[1] + s + 1, () -> {
                    int n = (int) (24 + rad * 5);
                    for (int i = 0; i < n; i++) {
                        double a = i * Math.PI * 2 / n;
                        Fx.send(level, Fx.particle("cloud"), x + Math.cos(a) * rad, y + 0.4, z + Math.sin(a) * rad, 0, Math.cos(a) * 0.2, 0.05, Math.sin(a) * 0.2, 1.0);
                        if (i % 3 == 0) Fx.send(level, GOLD, x + Math.cos(a) * rad, y + 0.6, z + Math.sin(a) * rad, 1, 0, 0, 0, 0);
                    }
                });
            }
        }
        Fx.sound(level, x, y, z, "entity.generic.explode", 25.0F, 0.45F);
        Fx.sound(level, x, y, z, "block.anvil.land", 25.0F, 0.35F);
        Fx.sound(level, x, y, z, "entity.lightning_bolt.thunder", 25.0F, 0.7F);
        Fx.sound(level, x, y, z, "item.totem.use", 8.0F, 0.6F);
        Fx.sound(level, x, y, z, "block.bell.resonate", 10.0F, 0.6F);
    }
}
