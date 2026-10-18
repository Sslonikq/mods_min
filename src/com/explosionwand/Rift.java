package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/**
 * Разлом (классика): огромная фиолетовая волна-серп режет пространство, оставляя каньон.
 * Разлом висит, затягивает — и схлопывается цепью взрывов вдоль всей линии, расшвыривая камни.
 */
public class Rift {
    static final List<Rift> ACTIVE = new ArrayList<>();
    static final Random RNG = Fx.RNG;
    static final double RANGE = 140.0D, SPEED = 3.0D, HALF = 22.0D, HEIGHT = 18.0D, CURVE = 30.0D;
    static final int CUT_HALF = 2, CUT_DOWN = 30, CUT_UP = 20, LINGER = 80;
    static ParticleOptions VIOLET, MAGENTA, CORE, DARK;

    final ServerLevel level; final Entity owner;
    final double ox, oy, oz, dx, dz, px, pz;
    double dist = 0; int t = 0, linger = 0, chain = -1;
    final Set<Entity> hit = Collections.newSetFromMap(new IdentityHashMap<>());
    BlockState AIR;

    Rift(ServerLevel level, Vec3 start, double lx, double lz, Entity owner) {
        this.level = level; this.owner = owner;
        double l = Math.sqrt(lx * lx + lz * lz); if (l < 1e-4) { lx = 1; lz = 0; l = 1; }
        this.dx = lx / l; this.dz = lz / l; this.px = -this.dz; this.pz = this.dx;
        this.ox = start.f_82479_ + dx; this.oy = start.f_82480_; this.oz = start.f_82481_ + dz;
        if (VIOLET == null) {
            VIOLET = new DustParticleOptions(new Vector3f(0.55F, 0.15F, 1.0F), 3.0F);
            MAGENTA = new DustParticleOptions(new Vector3f(0.9F, 0.3F, 1.0F), 2.2F);
            CORE = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 1.0F), 2.5F);
            DARK = new DustParticleOptions(new Vector3f(0.12F, 0.0F, 0.25F), 3.0F);
        }
        AIR = Fx.state("air");
        Fx.sound(level, ox, oy, oz, "entity.warden.sonic_boom", 6.0F, 1.3F);
        Fx.sound(level, ox, oy, oz, "block.end_portal.spawn", 4.0F, 1.4F);
        Fx.sound(level, ox, oy, oz, "entity.player.attack.sweep", 4.0F, 0.5F);
    }

    double back(double w) { return w * w / (2.0D * CURVE); }

    boolean tick() {
        t++;
        if (dist < RANGE) { waveStep(); return false; }
        if (chain < 0) return lingerStep();
        return chainStep();
    }

    void waveStep() {
        double prev = dist;
        dist = Math.min(RANGE, dist + SPEED);
        double fx = ox + dx * dist, fz = oz + dz * dist;
        var witch = Fx.particle("witch"); var portal = Fx.particle("reverse_portal"); var rod = Fx.particle("end_rod");
        for (double w = -HALF; w <= HALF; w += 0.8D) {
            double b = back(w);
            double x = fx - dx * b + px * w, z = fz - dz * b + pz * w;
            double hw = HEIGHT * (1.0D - 0.35D * Math.abs(w) / HALF);
            Fx.send(level, VIOLET, x, oy + hw / 2 - 2, z, 5, 0.12, hw / 4, 0.12, 0);
            if (((int) (w * 10)) % 16 == 0) Fx.send(level, witch, x, oy + hw / 2, z, 3, 0.2, hw / 4, 0.2, 0);
            if (Math.abs(w) < 4) Fx.send(level, CORE, x, oy + hw / 2 - 2, z, 4, 0.08, hw / 4, 0.08, 0);
            Fx.send(level, portal, x - dx * 1.5, oy + hw / 2, z - dz * 1.5, 1, 0.2, hw / 4, 0.2, 0.05);
        }
        for (double w = -HALF; w <= HALF; w += 2.0D) {
            double b = back(w);
            Fx.send(level, rod, fx - dx * b + px * w, oy + 1 + RNG.nextDouble() * HEIGHT * 0.7, fz - dz * b + pz * w, 1, 0, 0, 0, 0);
        }
        if (t % 4 == 1) Fx.sound(level, fx, oy, fz, "item.trident.riptide_3", 5.0F, 0.6F);
        cut(prev, dist);
        strike(prev, dist);
    }

    void cut(double from, double to) {
        if (AIR == null) return;
        for (double s = from; s < to; s += 0.5D) {
            double cx = ox + dx * s, cz = oz + dz * s;
            for (double l = -CUT_HALF; l <= CUT_HALF; l += 0.5D) {
                int x = (int) Math.floor(cx + px * l), z = (int) Math.floor(cz + pz * l);
                int base = (int) Math.floor(oy);
                if (!level.m_46805_(new BlockPos(x, base, z))) continue;
                for (int y = base - CUT_DOWN; y <= base + CUT_UP; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState st = level.m_8055_(p);
                    if (st.m_60795_() || st.m_60800_(level, p) < 0.0F) continue;
                    level.m_7731_(p, AIR, 18);
                }
            }
            if (RNG.nextInt(3) == 0) Fx.send(level, DARK, cx, oy - 2, cz, 6, 0.6, 4, 0.6, 0);
        }
    }

    void strike(double from, double to) {
        double R = to + HALF + 4;
        AABB box = new AABB(ox - R, oy - 10, oz - R, ox + R, oy + HEIGHT + 6, oz + R);
        for (Entity e : level.m_6249_(null, box, e -> e.m_6084_() && !e.m_5833_())) {
            if (e == owner || hit.contains(e) || Cmd.hasTagPrefix(e, "ew")) continue;
            Vec3 p = e.m_20182_();
            double rx = p.f_82479_ - ox, rz = p.f_82481_ - oz;
            double along = rx * dx + rz * dz, lat = rx * px + rz * pz;
            if (Math.abs(lat) > HALF) continue;
            double b = back(lat);
            if (along > to - b + 1.0D || along < from - b - 2.5D) continue;
            if (p.f_82480_ < oy - 10 || p.f_82480_ > oy + HEIGHT + 4) continue;
            hit.add(e);
            e.m_20256_(new Vec3(dx * 2.6D, 1.1D, dz * 2.6D)); e.f_19864_ = true;
            if (e instanceof LivingEntity) e.m_6469_(level.m_269111_().m_269425_(), 26.0F);
            Fx.send(level, MAGENTA, p.f_82479_, p.f_82480_ + 1, p.f_82481_, 30, 0.4, 0.8, 0.4, 0);
            Fx.sound(level, p.f_82479_, p.f_82480_, p.f_82481_, "entity.player.attack.sweep", 2.0F, 0.6F);
        }
    }

    boolean lingerStep() {
        linger++;
        var portal = Fx.particle("portal"); var rp = Fx.particle("reverse_portal");
        for (double s = 0; s < RANGE; s += 2.5D) {
            double x = ox + dx * s, z = oz + dz * s;
            Fx.send(level, VIOLET, x, oy + 3, z, 2, 0.15, 6, 0.15, 0);
            Fx.send(level, DARK, x, oy - 3, z, 2, 0.4, 5, 0.4, 0);
            if (linger % 2 == 0) Fx.send(level, rp, x, oy + 1, z, 2, 0.2, 4, 0.2, 0.05);
            if (linger % 3 == 0) { double[] u = Fx.unit(); Fx.send(level, portal, x, oy + 2, z, 0, u[0] * 5, u[1] * 3, u[2] * 5, 1.0); }
        }
        if (linger % 20 == 1) Fx.sound(level, ox + dx * RANGE / 2, oy, oz + dz * RANGE / 2, "block.portal.ambient", 10.0F, 0.6F);
        double R = RANGE + 10;
        for (Entity e : level.m_6249_(null, new AABB(ox - R, oy - 15, oz - R, ox + R, oy + 25, oz + R), e -> e.m_6084_() && !e.m_5833_())) {
            if (e == owner || Cmd.hasTagPrefix(e, "ew")) continue;
            Vec3 p = e.m_20182_();
            double rx = p.f_82479_ - ox, rz = p.f_82481_ - oz;
            double along = rx * dx + rz * dz, lat = rx * px + rz * pz;
            if (along < -2 || along > RANGE + 2 || Math.abs(lat) > 10) continue;
            double k = -Math.signum(lat) * Math.min(0.25D, 1.2D / Math.max(1.0D, Math.abs(lat)));
            e.m_20256_(e.m_20184_().m_82520_(px * k, -0.02D, pz * k)); e.f_19864_ = true;
        }
        if (linger >= LINGER) {
            Fx.sound(level, ox, oy, oz, "block.respawn_anchor.deplete", 10.0F, 0.5F);
            chain = 0;
        }
        return false;
    }

    /** Цепь взрывов по всей линии разлома, от начала к концу. */
    boolean chainStep() {
        double s = chain * 7.0;
        if (s > RANGE) {
            double mx = ox + dx * RANGE / 2, mz = oz + dz * RANGE / 2;
            Fx.sound(level, mx, oy, mz, "entity.warden.sonic_boom", 15.0F, 0.7F);
            return true;
        }
        double x = ox + dx * s, z = oz + dz * s;
        double y = oy - 1;
        level.m_255391_(owner, x, y, z, 4.0F, false, Level.ExplosionInteraction.valueOf("TNT"));
        Debris.fling(level, x, y, z, 2.5, 7.0, 45, 1.0, 0.9);
        Fx.send(level, Fx.particle("explosion_emitter"), x, y + 1, z, 1, 0, 0, 0, 0);
        Fx.send(level, Fx.particle("sonic_boom"), x, y + 2, z, 1, 0, 0, 0, 0);
        Fx.send(level, MAGENTA, x, y + 2, z, 40, 2, 3, 2, 0);
        Fx.send(level, Fx.particle("reverse_portal"), x, y + 2, z, 60, 1, 2, 1, 1.0);
        for (Entity e : level.m_6249_(owner, new AABB(x - 8, y - 4, z - 8, x + 8, y + 10, z + 8), e -> e.m_6084_() && !Cmd.hasTagPrefix(e, "ew"))) {
            Vec3 p = e.m_20182_(); double ddx = p.f_82479_ - x, ddz = p.f_82481_ - z, d = Math.max(0.5, Math.sqrt(ddx * ddx + ddz * ddz));
            e.m_20256_(new Vec3(ddx / d * 1.4, 1.0, ddz / d * 1.4)); e.f_19864_ = true;
            if (e instanceof LivingEntity) e.m_6469_(level.m_269111_().m_269425_(), 10.0F);
        }
        Fx.sound(level, x, y, z, "entity.generic.explode", 8.0F, 0.6F + RNG.nextFloat() * 0.3F);
        chain++;
        return false;
    }
}
