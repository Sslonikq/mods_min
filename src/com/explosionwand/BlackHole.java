package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/** Чёрная дыра: тянет всё (хозяина — откуда угодно), потом волна на 200 блоков + кратер. */
public class BlackHole {
    static final List<BlackHole> ACTIVE = new ArrayList<>();
    static final Random RNG = Fx.RNG;

    static final int LIFE = 160, COLLAPSE = 20;
    static final double PULL_RADIUS = 30.0D, RIP_RADIUS = 8.0D;
    static final int MAX_BLOCKS = 350;
    static final double WAVE_RADIUS = 200.0D, WAVE_SPEED = 10.0D;
    static final int CRATER = 45, CARVE_PER_TICK = 7000;

    static final Vec3 UP = new Vec3(0, 1, 0);
    static ParticleOptions BLACK, HOT, PINK, PURPLE;

    final ServerLevel level; final Vec3 c; final Entity owner;
    final double cx, cy, cz;
    int t = 0, ripped = 0;
    boolean exploded = false;
    double waveR = 0;
    int carveIdx = 0;
    final Set<Entity> waveHit = Collections.newSetFromMap(new IdentityHashMap<>());
    BlockState AIR;

    BlackHole(ServerLevel level, Vec3 c, Entity owner) {
        this.level = level; this.c = c; this.owner = owner;
        this.cx = c.f_82479_; this.cy = c.f_82480_; this.cz = c.f_82481_;
        if (BLACK == null) {
            BLACK  = new DustParticleOptions(new Vector3f(0.0F, 0.0F, 0.0F), 3.0F);
            HOT    = new DustParticleOptions(new Vector3f(1.0F, 0.8F, 0.4F), 1.4F);
            PINK   = new DustParticleOptions(new Vector3f(0.95F, 0.25F, 0.85F), 1.6F);
            PURPLE = new DustParticleOptions(new Vector3f(0.4F, 0.1F, 0.85F), 1.8F);
        }
    }

    boolean tick() {
        if (exploded) return aftermath();
        t++;
        double grow = Math.min(1.0D, t / 20.0D);
        boolean collapsing = t > LIFE - COLLAPSE;
        double size = grow * (collapsing ? Math.max(0.08D, (LIFE - t) / (double) COLLAPSE) : 1.0D);
        if (t == 1) {
            Fx.send(level, Fx.particle("sonic_boom"), cx, cy, cz, 1, 0, 0, 0, 0);
            Fx.send(level, Fx.particle("reverse_portal"), cx, cy, cz, 80, 0.3, 0.3, 0.3, 1.2);
            Fx.sound(level, cx, cy, cz, "entity.warden.sonic_charge", 6.0F, 0.5F);
            Fx.sound(level, cx, cy, cz, "block.portal.trigger", 5.0F, 0.5F);
        }
        if (t % 30 == 5 && !collapsing) Fx.sound(level, cx, cy, cz, "block.beacon.ambient", 8.0F, 0.5F);
        if (t == LIFE - COLLAPSE + 1) Fx.sound(level, cx, cy, cz, "block.beacon.deactivate", 8.0F, 0.5F);
        render(size, collapsing);
        pull(grow, collapsing);
        if (!collapsing) ripBlocks(grow);
        if (t >= LIFE) burst();
        return false;
    }

    void render(double size, boolean collapsing) {
        double core = 1.8D * size, rot = t * 0.22D;
        for (int i = 0; i < 22; i++) {
            double[] u = Fx.unit(); double r = core * (0.55D + 0.45D * RNG.nextDouble());
            Fx.send(level, BLACK, cx + u[0] * r, cy + u[1] * r, cz + u[2] * r, 1, 0, 0, 0, 0);
        }
        var rod = Fx.particle("end_rod");
        for (int i = 0; i < 12; i++) {
            double a = -rot * 1.6D + i * Math.PI * 2 / 12, r = core * 1.2D;
            double x = Math.cos(a) * r, z = Math.sin(a) * r;
            Fx.send(level, rod, cx + x, cy + z * 0.25D, cz + z, 1, 0, 0, 0, 0);
        }
        double inner = core * 1.5D, outer = inner + 7.5D * size;
        for (int k = 0; k < 3; k++) for (double r = inner; r < outer; r += 0.6D) {
            double a = rot + k * (Math.PI * 2 / 3) + r * 0.5D;
            double x = Math.cos(a) * r, z = Math.sin(a) * r;
            double f = (r - inner) / Math.max(0.01D, outer - inner);
            Fx.send(level, f < 0.25D ? HOT : (f < 0.6D ? PINK : PURPLE), cx + x, cy + z * 0.25D, cz + z, 1, 0, 0, 0, 0);
        }
        var portal = Fx.particle("portal");
        int streams = collapsing ? 25 : 12;
        for (int i = 0; i < streams; i++) {
            double[] u = Fx.unit(); double r = 5.0D + RNG.nextDouble() * 12.0D * Math.max(size, 0.4D);
            Fx.send(level, portal, cx, cy, cz, 0, u[0] * r, u[1] * r, u[2] * r, 1.0D);
        }
        if (t % 4 == 0) Fx.send(level, Fx.particle("squid_ink"), cx, cy, cz, 3, core * 0.3, core * 0.3, core * 0.3, 0.01);
    }

    void pull(double grow, boolean collapsing) {
        double R = PULL_RADIUS * grow + 2.0D;
        AABB box = new AABB(cx - R, cy - R, cz - R, cx + R, cy + R, cz + R);
        boolean ownerDone = false;
        for (Entity e : level.m_6249_(null, box, e -> e.m_6084_() && !e.m_5833_())) {
            Vec3 d = c.m_82546_(e.m_20182_());
            double dist = d.m_82553_();
            if (dist > R) continue;
            if (e == owner) ownerDone = true;
            pullOne(e, d, dist, Math.min(0.75D, 2.4D / Math.max(dist, 0.6D)) * grow * (collapsing ? 1.7D : 1.0D));
        }
        if (!ownerDone && owner != null && owner.m_6084_() && !owner.m_5833_() && level.m_6907_().contains(owner)) {
            Vec3 d = c.m_82546_(owner.m_20182_());
            pullOne(owner, d, d.m_82553_(), 0.34D * grow * (collapsing ? 1.5D : 1.0D));
        }
    }

    void pullOne(Entity e, Vec3 d, double dist, double a) {
        Vec3 dir = dist < 1.0E-4D ? UP : d.m_82490_(1.0D / dist);
        Vec3 tan = dir.m_82537_(UP);
        double tl = tan.m_82553_();
        tan = tl < 1.0E-4D ? new Vec3(0, 0, 0) : tan.m_82490_(1.0D / tl);
        Vec3 v = e.m_20184_().m_82549_(dir.m_82490_(a)).m_82549_(tan.m_82490_(a * 0.6D));
        if (dist < 1.8D) v = dir.m_82490_(dist * 0.35D).m_82549_(tan.m_82490_(0.3D));
        double sp = v.m_82553_();
        if (sp > 2.4D) v = v.m_82490_(2.4D / sp);
        e.m_20256_(v); e.f_19864_ = true; e.f_19789_ = 0.0F;
        if (e instanceof FallingBlockEntity fb) fb.m_20242_(true);
        if (dist < 2.6D && t % 10 == 0 && e instanceof LivingEntity) e.m_6469_(level.m_269111_().m_269425_(), 4.0F);
    }

    void ripBlocks(double grow) {
        if (ripped >= MAX_BLOCKS) return;
        double r = RIP_RADIUS * grow;
        Dungeons.Shield shield = Dungeons.shield(level);
        for (int i = 0; i < 3; i++) {
            double[] u = Fx.unit(); double rr = r * Math.cbrt(RNG.nextDouble());
            BlockPos p = new BlockPos((int) Math.floor(cx + u[0] * rr), (int) Math.floor(cy + u[1] * rr), (int) Math.floor(cz + u[2] * rr));
            if (shield.at(p)) continue;
            BlockState s = level.m_8055_(p);
            if (s.m_60795_() || s.m_155947_() || !s.m_60819_().m_76178_()) continue;
            float h = s.m_60800_(level, p);
            if (h < 0.0F || h >= 50.0F) continue;
            FallingBlockEntity fb = FallingBlockEntity.m_201971_(level, p, s);
            if (fb != null) { fb.m_20242_(true); ripped++; }
        }
    }

    void burst() {
        exploded = true;
        AIR = Fx.state("air");
        level.m_255391_(owner, cx, cy, cz, 10.0F, false, Level.ExplosionInteraction.valueOf("NONE"));
        Fx.send(level, Fx.particle("flash"), cx, cy, cz, 3, 2, 2, 2, 0);
        Fx.send(level, Fx.particle("explosion_emitter"), cx, cy, cz, 12, 6, 6, 6, 0);
        var boom = Fx.particle("sonic_boom");
        for (int i = 0; i < 24; i++) { double a = i * Math.PI * 2 / 24; Fx.send(level, boom, cx + Math.cos(a) * 5, cy, cz + Math.sin(a) * 5, 1, 0, 0, 0, 0); }
        var rod = Fx.particle("end_rod");
        for (int i = 0; i < 80; i++) { double[] u = Fx.unit(); Fx.send(level, rod, cx, cy, cz, 0, u[0], u[1], u[2], 1.4); }
        Fx.send(level, Fx.particle("squid_ink"), cx, cy, cz, 150, 2, 2, 2, 0.5);
        Fx.send(level, Fx.particle("reverse_portal"), cx, cy, cz, 300, 1, 1, 1, 3.0);
        Fx.sound(level, cx, cy, cz, "entity.warden.sonic_boom", 30.0F, 0.5F);
        Fx.sound(level, cx, cy, cz, "entity.generic.explode", 30.0F, 0.4F);
        Fx.sound(level, cx, cy, cz, "entity.lightning_bolt.thunder", 30.0F, 0.5F);
        Fx.sound(level, cx, cy, cz, "entity.wither.death", 30.0F, 0.5F);
    }

    boolean aftermath() {
        boolean waveDone = waveR >= WAVE_RADIUS;
        if (!waveDone) wave();
        return carve() && waveDone;
    }

    void wave() {
        waveR = Math.min(WAVE_RADIUS, waveR + WAVE_SPEED);
        double r = waveR;
        int n = (int) Math.min(180, 16 + r * 0.9);
        var exp = Fx.particle("explosion"); var cloud = Fx.particle("cloud");
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + RNG.nextDouble() * 0.05;
            double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
            Fx.send(level, exp, x, cy, z, 1, 0, 0, 0, 0);
            if (i % 2 == 0) Fx.send(level, cloud, x, cy + 0.5, z, 0, Math.cos(a), 0.05, Math.sin(a), 0.8);
        }
        if ((int) (r / WAVE_SPEED) % 4 == 0) Fx.sound(level, cx, cy, cz, "entity.generic.explode", 20.0F, 0.3F);
        AABB box = new AABB(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
        for (Entity e : level.m_6249_(null, box, e -> e.m_6084_() && !e.m_5833_())) {
            if (waveHit.contains(e)) continue;
            Vec3 d = e.m_20182_().m_82546_(c);
            double dist = d.m_82553_();
            if (dist > r) continue;
            waveHit.add(e);
            double k = 1.0D - Math.min(1.0D, dist / WAVE_RADIUS);
            Vec3 dir;
            if (dist < 0.3D) { double[] u = Fx.unit(); dir = new Vec3(u[0], Math.abs(u[1]), u[2]); }
            else dir = d.m_82490_(1.0D / dist);
            Vec3 v = dir.m_82490_(0.8D + 4.5D * k).m_82520_(0.0D, 0.5D + 0.9D * k, 0.0D);
            if (e instanceof FallingBlockEntity fb) fb.m_20242_(false);
            e.m_20256_(v); e.f_19864_ = true;
            if (e instanceof LivingEntity) e.m_6469_(level.m_269111_().m_269425_(), (float) (6.0D + 64.0D * k * k));
        }
    }

    boolean carve() {
        long[] offs = Fx.sphere(CRATER);
        if (carveIdx >= offs.length || AIR == null) return true;
        int end = Math.min(offs.length, carveIdx + CARVE_PER_TICK);
        int bx = (int) Math.floor(cx), by = (int) Math.floor(cy), bz = (int) Math.floor(cz);
        double shell = 0;
        Dungeons.Shield shield = Dungeons.shield(level);
        for (; carveIdx < end; carveIdx++) {
            long v = offs[carveIdx];
            shell = Fx.sd(v);
            BlockPos p = new BlockPos(bx + Fx.sx(v, CRATER), by + Fx.sy(v, CRATER), bz + Fx.sz(v, CRATER));
            if (!level.m_46805_(p)) continue;
            BlockState s = level.m_8055_(p);
            if (s.m_60795_() || s.m_60800_(level, p) < 0.0F || shield.at(p)) continue;
            level.m_7731_(p, AIR, 18);
        }
        var exp = Fx.particle("explosion"); var smoke = Fx.particle("large_smoke");
        for (int i = 0; i < 10; i++) {
            double[] u = Fx.unit();
            Fx.send(level, exp, cx + u[0] * shell, cy + u[1] * shell, cz + u[2] * shell, 1, 0, 0, 0, 0);
            Fx.send(level, smoke, cx + u[0] * shell, cy + u[1] * shell, cz + u[2] * shell, 2, 0.5, 0.5, 0.5, 0.05);
        }
        return carveIdx >= offs.length;
    }
}
