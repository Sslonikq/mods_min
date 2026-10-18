package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/**
 * Портал-ган. Синий и оранжевый порталы на любых поверхностях; всё, что входит в один, вылетает из другого
 * с сохранением скорости. Внутри портала — «живое окно»: лучи из глаз игрока идут сквозь второй портал,
 * и каждая точка окрашивается в цвет того, что там видно.
 */
final class PortalGun {
    static final class End {
        ServerLevel level; double cx, cy, cz; double[] n, u, r;
    }
    static final class Pair { End blue, orange; }
    static final Map<UUID, Pair> MAP = new HashMap<>();
    static final Map<Entity, Long> CD = new WeakHashMap<>();
    static long now = 0;
    static final double HW = 0.55, HH = 1.0;
    static DustParticleOptions BLUE, ORANGE, BLUE2, ORANGE2;

    static void init() {
        if (BLUE != null) return;
        BLUE = new DustParticleOptions(new Vector3f(0.15F, 0.55F, 1.0F), 1.3F);
        ORANGE = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.08F), 1.3F);
        BLUE2 = new DustParticleOptions(new Vector3f(0.5F, 0.8F, 1.0F), 0.8F);
        ORANGE2 = new DustParticleOptions(new Vector3f(1.0F, 0.8F, 0.4F), 0.8F);
    }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double dot(double[] a, double x, double y, double z) { return a[0] * x + a[1] * y + a[2] * z; }

    static boolean solid(ServerLevel l, int x, int y, int z) { return !l.m_8055_(new BlockPos(x, y, z)).m_60795_(); }

    static void fire(ServerLevel level, Player player, boolean orange) {
        init();
        Vec3 eye = player.m_146892_(), look = player.m_20252_(1.0F);
        BlockHitResult bh = level.m_45547_(new ClipContext(eye, eye.m_82549_(look.m_82490_(80)),
                ClipContext.Block.valueOf("COLLIDER"), ClipContext.Fluid.valueOf("NONE"), player));
        Pair pair = MAP.computeIfAbsent(player.m_20148_(), k -> new Pair());
        if (bh.m_6662_() == HitResult.Type.valueOf("MISS")) {
            MAP.remove(player.m_20148_());
            Fx.sound(level, player.m_20185_(), player.m_20186_(), player.m_20189_(), "block.beacon.deactivate", 1.0F, 1.6F);
            return;
        }
        Direction d = bh.m_82434_(); BlockPos bp = bh.m_82425_();
        int bx = bp.m_123341_(), by = bp.m_123342_(), bz = bp.m_123343_();
        End e = new End(); e.level = level;
        e.n = new double[]{d.m_122429_(), d.m_122430_(), d.m_122431_()};
        if (e.n[1] == 0) {
            e.u = new double[]{0, 1, 0};
            int fx = bx + (int) e.n[0], fz = bz + (int) e.n[2];
            boolean upOk = solid(level, bx, by + 1, bz) && !solid(level, fx, by + 1, fz);
            e.cy = upOk ? by + 1.0 : by;
        } else {
            double lx = look.f_82479_, lz = look.f_82481_;
            e.u = Math.abs(lx) > Math.abs(lz) ? new double[]{Math.signum(lx), 0, 0} : new double[]{0, 0, Math.signum(lz) == 0 ? 1 : Math.signum(lz)};
            e.cy = by + 0.5 + e.n[1] * 0.5;
        }
        e.r = cross(e.u, e.n);
        e.cx = bx + 0.5 + e.n[0] * 0.5; e.cz = bz + 0.5 + e.n[2] * 0.5;
        if (e.n[1] == 0) { e.cx = bx + 0.5 + e.n[0] * 0.51; e.cz = bz + 0.5 + e.n[2] * 0.51; } else e.cy += e.n[1] * 0.01;
        if (orange) pair.orange = e; else pair.blue = e;
        // выстрел
        DustParticleOptions c = orange ? ORANGE : BLUE;
        double dx = e.cx - eye.f_82479_, dy = e.cy - eye.f_82480_, dz = e.cz - eye.f_82481_, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (double s = 1; s < l; s += 0.5) Fx.send(level, c, eye.f_82479_ + dx * s / l, eye.f_82480_ - 0.2 + dy * s / l, eye.f_82481_ + dz * s / l, 1, 0, 0, 0, 0);
        Fx.sound(level, player.m_20185_(), player.m_20186_(), player.m_20189_(), "block.beacon.power_select", 0.6F, orange ? 1.4F : 1.8F);
        Fx.sound(level, e.cx, e.cy, e.cz, "block.respawn_anchor.set_spawn", 1.0F, orange ? 1.2F : 1.6F);
    }

    static void tick(ServerLevel level) {
        now++;
        init();
        for (Pair p : MAP.values()) {
            if (p.blue != null && p.blue.level == level) frame(p.blue, BLUE, BLUE2);
            if (p.orange != null && p.orange.level == level) frame(p.orange, ORANGE, ORANGE2);
            if (p.blue == null || p.orange == null || p.blue.level != level || p.orange.level != level) continue;
            transfer(p.blue, p.orange); transfer(p.orange, p.blue);
            if (now % 2 == 0) for (ServerPlayer pl : level.m_6907_()) { view(pl, p.blue, p.orange); view(pl, p.orange, p.blue); }
        }
    }

    static void frame(End e, DustParticleOptions c, DustParticleOptions c2) {
        for (int i = 0; i < 30; i++) {
            double a = i * Math.PI * 2 / 30;
            double lr = Math.cos(a) * HW, lu = Math.sin(a) * HH;
            Fx.send(e.level, c, e.cx + e.r[0] * lr + e.u[0] * lu, e.cy + e.r[1] * lr + e.u[1] * lu, e.cz + e.r[2] * lr + e.u[2] * lu, 1, 0, 0, 0, 0);
        }
        double a = now * 0.25;
        for (int k = 0; k < 2; k++) {
            double lr = Math.cos(a + k * Math.PI) * HW * 0.85, lu = Math.sin(a + k * Math.PI) * HH * 0.85;
            Fx.send(e.level, c2, e.cx + e.r[0] * lr + e.u[0] * lu, e.cy + e.r[1] * lr + e.u[1] * lu, e.cz + e.r[2] * lr + e.u[2] * lu, 1, 0, 0, 0, 0);
        }
    }

    static double ext(Entity e, double[] n) { return n[1] == 0 ? e.m_20205_() / 2 : e.m_20206_() / 2; }

    /** Перенос сущностей из A в B. */
    static void transfer(End A, End B) {
        ServerLevel level = A.level;
        for (Entity e : level.m_6249_(null, new AABB(A.cx - 2, A.cy - 2.5, A.cz - 2, A.cx + 2, A.cy + 2.5, A.cz + 2), x -> x.m_6084_() && !x.m_5833_())) {
            if (Cmd.hasTagPrefix(e, "ew")) continue;
            Long cd = CD.get(e);
            if (cd != null && now < cd) continue;
            Vec3 p = e.m_20182_(), v = e.m_20184_();
            double h = e.m_20206_();
            double ccx = p.f_82479_, ccy = p.f_82480_ + h / 2, ccz = p.f_82481_;
            double rx = ccx - A.cx, ry = ccy - A.cy, rz = ccz - A.cz;
            double lr = dot(A.r, rx, ry, rz), lu = dot(A.u, rx, ry, rz), ln = dot(A.n, rx, ry, rz);
            double ea = ext(e, A.n);
            if (ln > ea + 0.15 || ln < -0.6) continue;
            if ((lr / HW) * (lr / HW) + (lu / (HH + 0.1)) * (lu / (HH + 0.1)) > 1.0) continue;
            double vr = dot(A.r, v.f_82479_, v.f_82480_, v.f_82481_), vu = dot(A.u, v.f_82479_, v.f_82480_, v.f_82481_), vn = dot(A.n, v.f_82479_, v.f_82480_, v.f_82481_);
            if (vn > 0.08) continue;
            // выход
            double eb = ext(e, B.n);
            double olr = -lr, olu = lu;
            if (B.n[1] == 0) { double lim = Math.max(0, HH - h / 2); olu = Math.max(-lim, Math.min(lim, olu)); double limr = Math.max(0, HW - e.m_20205_() / 2); olr = Math.max(-limr, Math.min(limr, olr)); }
            double off = eb + 0.08;
            double ox = B.cx + B.r[0] * olr + B.u[0] * olu + B.n[0] * off;
            double oy = B.cy + B.r[1] * olr + B.u[1] * olu + B.n[1] * off;
            double oz = B.cz + B.r[2] * olr + B.u[2] * olu + B.n[2] * off;
            double out = Math.max(-vn, B.n[1] == 0 ? 0.25 : 0.12);
            double nvx = B.r[0] * -vr + B.u[0] * vu + B.n[0] * out;
            double nvy = B.r[1] * -vr + B.u[1] * vu + B.n[1] * out;
            double nvz = B.r[2] * -vr + B.u[2] * vu + B.n[2] * out;
            double feetY = oy - h / 2;
            CD.put(e, now + 8);
            e.f_19789_ = 0;
            if (e instanceof ServerPlayer sp) {
                Vec3 lk = sp.m_20252_(1.0F);
                double kr = dot(A.r, lk.f_82479_, lk.f_82480_, lk.f_82481_), ku = dot(A.u, lk.f_82479_, lk.f_82480_, lk.f_82481_), kn = dot(A.n, lk.f_82479_, lk.f_82480_, lk.f_82481_);
                double lx = B.r[0] * -kr + B.u[0] * ku + B.n[0] * -kn, ly = B.r[1] * -kr + B.u[1] * ku + B.n[1] * -kn, lz = B.r[2] * -kr + B.u[2] * ku + B.n[2] * -kn;
                float yaw = (float) Math.toDegrees(Math.atan2(-lx, lz));
                float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, ly))));
                sp.m_8999_(level, ox, feetY, oz, yaw, pitch);
            } else e.m_6021_(ox, feetY, oz);
            e.m_20256_(new Vec3(nvx, nvy, nvz));
            e.f_19864_ = true;
            Fx.sound(level, A.cx, A.cy, A.cz, "entity.enderman.teleport", 0.5F, 1.7F);
            Fx.sound(level, ox, oy, oz, "entity.enderman.teleport", 0.5F, 1.9F);
        }
    }

    /** «Живое окно»: что видно сквозь портал A (из второго портала B) для конкретного игрока. */
    static void view(ServerPlayer pl, End A, End B) {
        ServerLevel level = A.level;
        Vec3 eye = pl.m_146892_();
        double ex = eye.f_82479_ - A.cx, ey = eye.f_82480_ - A.cy, ez = eye.f_82481_ - A.cz;
        if (ex * ex + ey * ey + ez * ez > 28 * 28) return;
        if (dot(A.n, ex, ey, ez) < 0.25) return;
        boolean sky = level.m_46472_() == net.minecraft.world.level.Level.f_46428_;
        for (double lr = -HW + 0.08; lr <= HW - 0.05; lr += 0.13) {
            for (double lu = -HH + 0.07; lu <= HH - 0.05; lu += 0.13) {
                if ((lr / HW) * (lr / HW) + (lu / HH) * (lu / HH) > 0.82) continue;
                double sx = A.cx + A.r[0] * lr + A.u[0] * lu + A.n[0] * 0.04;
                double sy = A.cy + A.r[1] * lr + A.u[1] * lu + A.n[1] * 0.04;
                double sz = A.cz + A.r[2] * lr + A.u[2] * lu + A.n[2] * 0.04;
                double dx = sx - eye.f_82479_, dy = sy - eye.f_82480_, dz = sz - eye.f_82481_, dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl; dy /= dl; dz /= dl;
                double dr = dot(A.r, dx, dy, dz), du = dot(A.u, dx, dy, dz), dn = dot(A.n, dx, dy, dz);
                double qx = B.r[0] * -dr + B.u[0] * du + B.n[0] * -dn, qy = B.r[1] * -dr + B.u[1] * du + B.n[1] * -dn, qz = B.r[2] * -dr + B.u[2] * du + B.n[2] * -dn;
                double px = B.cx + B.r[0] * -lr + B.u[0] * lu + B.n[0] * 0.06;
                double py = B.cy + B.r[1] * -lr + B.u[1] * lu + B.n[1] * 0.06;
                double pz = B.cz + B.r[2] * -lr + B.u[2] * lu + B.n[2] * 0.06;
                Vec3 start = new Vec3(px, py, pz), end = new Vec3(px + qx * 48, py + qy * 48, pz + qz * 48);
                BlockHitResult h = level.m_45547_(new ClipContext(start, end, ClipContext.Block.valueOf("COLLIDER"), ClipContext.Fluid.valueOf("ANY"), pl));
                float r, g, b;
                if (h.m_6662_() == HitResult.Type.valueOf("MISS")) {
                    if (sky) { r = 0.47F; g = 0.65F; b = 1.0F; } else { r = 0.06F; g = 0.06F; b = 0.09F; }
                } else {
                    BlockPos hp = h.m_82425_();
                    BlockState st = level.m_8055_(hp);
                    MapColor mc = st.m_284242_(level, hp);
                    int col = mc == null ? 0 : mc.f_283871_;
                    if (col == 0) col = 0x9a9a9a;
                    Direction f = h.m_82434_();
                    double shade = f.m_122430_() > 0 ? 1.0 : f.m_122430_() < 0 ? 0.55 : (f.m_122429_() != 0 ? 0.72 : 0.86);
                    Vec3 hv = h.m_82450_();
                    double dist = Math.sqrt((hv.f_82479_ - px) * (hv.f_82479_ - px) + (hv.f_82480_ - py) * (hv.f_82480_ - py) + (hv.f_82481_ - pz) * (hv.f_82481_ - pz));
                    shade *= 1.0 - Math.min(dist, 48) / 48 * 0.5;
                    r = (float) (((col >> 16) & 255) / 255.0 * shade); g = (float) (((col >> 8) & 255) / 255.0 * shade); b = (float) ((col & 255) / 255.0 * shade);
                }
                Fx.sendTo(level, pl, new DustParticleOptions(new Vector3f(r, g, b), 0.75F), sx, sy, sz, 1, 0, 0, 0, 0);
            }
        }
    }
}
