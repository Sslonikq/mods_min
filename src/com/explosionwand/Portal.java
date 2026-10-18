package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/** Портал: 15 сек затягивает всех (кроме хозяина, но тот может войти сам) и уносит в измерение. */
public class Portal {
    enum Kind { VOID, HAUNT, INVERTED }

    static final List<Portal> ACTIVE = new ArrayList<>();
    static final Random RNG = Fx.RNG;
    static final int LIFE = 300;
    static final double PULL_RADIUS = 22.0D, RING = 3.2D, SWALLOW = 1.9D;

    static DustParticleOptions dust(float r, float g, float b, float s) { return new DustParticleOptions(new Vector3f(r, g, b), s); }
    static ParticleOptions BLACK, WHITE, GRAY, RED, BLOOD, DARKRED;

    final ServerLevel level; final Entity owner; final Kind kind;
    final double cx, cy, cz, px, pz;
    int t = 0;

    Portal(ServerLevel level, Vec3 c, double lookX, double lookZ, Entity owner, Kind kind) {
        this.level = level; this.owner = owner; this.kind = kind;
        this.cx = c.f_82479_; this.cy = c.f_82480_; this.cz = c.f_82481_;
        double l = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (l < 1.0E-4D) { lookX = 1; lookZ = 0; l = 1; }
        this.px = -lookZ / l; this.pz = lookX / l;
        if (BLACK == null) {
            BLACK = dust(0, 0, 0, 3.0F); WHITE = dust(1, 1, 1, 1.6F); GRAY = dust(0.45F, 0.45F, 0.45F, 2.0F);
            RED = dust(0.85F, 0, 0, 1.3F); BLOOD = dust(0.55F, 0.0F, 0.02F, 2.4F); DARKRED = dust(0.25F, 0.0F, 0.0F, 2.8F);
        }
        switch (kind) {
            case VOID -> { Fx.sound(level, cx, cy, cz, "block.end_portal.spawn", 6.0F, 0.5F); Fx.sound(level, cx, cy, cz, "entity.warden.emerge", 6.0F, 0.6F); }
            case HAUNT -> { Fx.sound(level, cx, cy, cz, "entity.warden.heartbeat", 6.0F, 0.5F); Fx.sound(level, cx, cy, cz, "entity.elder_guardian.curse", 4.0F, 0.4F); }
            case INVERTED -> { Fx.sound(level, cx, cy, cz, "block.end_portal.spawn", 6.0F, 1.8F); Fx.sound(level, cx, cy, cz, "block.amethyst_block.chime", 6.0F, 0.5F); }
        }
        Fx.send(level, Fx.particle("sonic_boom"), cx, cy, cz, 1, 0, 0, 0, 0);
    }

    ResourceKey<Level> dest() {
        return switch (kind) { case VOID -> Dims.VOID; case HAUNT -> Dims.MAZE; case INVERTED -> Dims.INVERTED; };
    }

    double X(double u) { return cx + px * u; }
    double Z(double u) { return cz + pz * u; }
    void p(ParticleOptions o, double u, double v) { Fx.send(level, o, X(u), cy + v, Z(u), 1, 0, 0, 0, 0); }

    boolean tick() {
        t++;
        double grow = Math.min(1.0D, t / 25.0D);
        double close = t > LIFE - 20 ? Math.max(0.05D, (LIFE - t) / 20.0D) : 1.0D;
        double r = RING * grow * close;
        switch (kind) { case VOID -> renderVoid(r); case HAUNT -> renderHaunt(r); case INVERTED -> renderInverted(r); }
        if (t % 40 == 1) Fx.sound(level, cx, cy, cz, kind == Kind.HAUNT ? "entity.warden.heartbeat" : "block.portal.ambient", 6.0F, kind == Kind.INVERTED ? 1.6F : 0.4F);
        pullAndSwallow(grow * close);
        if (t >= LIFE) {
            Fx.send(level, Fx.particle("flash"), cx, cy, cz, 1, 0, 0, 0, 0);
            Fx.send(level, kind == Kind.INVERTED ? WHITE : BLACK, cx, cy, cz, 150, 1.5, 1.5, 1.5, 0);
            Fx.sound(level, cx, cy, cz, "block.respawn_anchor.deplete", 6.0F, 0.5F);
            Fx.sound(level, cx, cy, cz, "entity.warden.sonic_boom", 6.0F, kind == Kind.INVERTED ? 1.5F : 0.4F);
            return true;
        }
        return false;
    }

    void renderVoid(double r) {
        double rot = t * 0.18D;
        for (int i = 0; i < 26; i++) { double a = RNG.nextDouble() * 6.283, rr = r * 0.95 * Math.sqrt(RNG.nextDouble()); p(BLACK, Math.cos(a) * rr, Math.sin(a) * rr); }
        for (int i = 0; i < 28; i++) { double a = rot + i * 6.283 / 28; p(i % 3 == 0 ? GRAY : WHITE, Math.cos(a) * r, Math.sin(a) * r); }
        for (int k = 0; k < 4; k++) for (double rr = r * 1.1; rr < r * 2.6; rr += 0.45) {
            double a = -rot * 1.4 + k * Math.PI / 2 + rr * 0.9; p(RNG.nextInt(14) == 0 ? RED : GRAY, Math.cos(a) * rr, Math.sin(a) * rr);
        }
        inflow(Fx.particle("smoke"));
        if (t % 3 == 0) Fx.send(level, Fx.particle("white_ash"), cx, cy, cz, 20, 4, 3, 4, 0.02);
    }

    /** Портал-глаз: кровавое кольцо, чёрная бездна, белый зрачок-щель, который «смотрит». */
    void renderHaunt(double r) {
        double rot = t * 0.08D;
        for (int i = 0; i < 26; i++) { double a = RNG.nextDouble() * 6.283, rr = r * 0.95 * Math.sqrt(RNG.nextDouble()); p(BLACK, Math.cos(a) * rr, Math.sin(a) * rr * 0.75); }
        for (int i = 0; i < 30; i++) { double a = rot + i * 6.283 / 30; p(i % 2 == 0 ? BLOOD : DARKRED, Math.cos(a) * r, Math.sin(a) * r * 0.75); }
        // радужка
        for (int i = 0; i < 14; i++) { double a = i * 6.283 / 14; p(DARKRED, Math.cos(a) * r * 0.45, Math.sin(a) * r * 0.45); }
        // зрачок-щель, мигает
        boolean blink = (t % 70) > 64;
        if (!blink) {
            double shift = Math.sin(t * 0.05) * r * 0.25;   // зрачок бегает
            for (double v = -r * 0.42; v <= r * 0.42; v += 0.2) p(WHITE, shift, v);
        }
        for (int k = 0; k < 2; k++) for (double rr = r * 1.1; rr < r * 2.2; rr += 0.5) {
            double a = rot * 1.2 + k * Math.PI + rr * 0.7; p(DARKRED, Math.cos(a) * rr, Math.sin(a) * rr * 0.75);
        }
        inflow(Fx.particle("smoke"));
        if (t % 4 == 0) Fx.send(level, Fx.particle("ash"), cx, cy, cz, 25, 5, 3, 5, 0.02);
    }

    /** Перевёрнутый портал: белая середина, чёрное кольцо, всё летит НАРУЖУ и ВВЕРХ. */
    void renderInverted(double r) {
        double rot = -t * 0.22D;
        for (int i = 0; i < 26; i++) { double a = RNG.nextDouble() * 6.283, rr = r * 0.95 * Math.sqrt(RNG.nextDouble()); p(WHITE, Math.cos(a) * rr, -Math.sin(a) * rr); }
        for (int i = 0; i < 28; i++) { double a = rot + i * 6.283 / 28; p(i % 2 == 0 ? BLACK : GRAY, Math.cos(a) * r, Math.sin(a) * r); }
        for (int k = 0; k < 4; k++) for (double rr = r * 1.1; rr < r * 2.6; rr += 0.45) {
            double a = rot * 1.4 + k * Math.PI / 2 - rr * 0.9; p(k % 2 == 0 ? BLACK : WHITE, Math.cos(a) * rr, Math.sin(a) * rr);
        }
        var rp = Fx.particle("reverse_portal");
        Fx.send(level, rp, cx, cy, cz, 25, 0.3, 0.3, 0.3, 0.6);
        Fx.send(level, Fx.particle("warped_spore"), cx, cy, cz, 30, 6, 3, 6, 0);
        var rod = Fx.particle("end_rod");
        for (int i = 0; i < 4; i++) Fx.send(level, rod, cx + (RNG.nextDouble() - 0.5) * 8, cy - 3, cz + (RNG.nextDouble() - 0.5) * 8, 0, 0, 1, 0, 0.3); // «падает» вверх
    }

    void inflow(ParticleOptions o) {
        for (int i = 0; i < 10; i++) {
            double[] u = Fx.unit(); double d = 6 + RNG.nextDouble() * 10;
            double sx = cx + u[0] * d, sy = cy + u[1] * d * 0.6, sz = cz + u[2] * d;
            Fx.send(level, o, sx, sy, sz, 0, (cx - sx) / d, (cy - sy) / d, (cz - sz) / d, 0.6);
        }
    }

    void pullAndSwallow(double power) {
        double R = PULL_RADIUS;
        AABB box = new AABB(cx - R, cy - R, cz - R, cx + R, cy + R, cz + R);
        for (Entity e : level.m_6249_(null, box, e -> e.m_6084_() && !e.m_5833_())) {
            Vec3 pp = e.m_20182_();
            double ex = pp.f_82479_, ey = pp.f_82480_ + 0.9, ez = pp.f_82481_;
            double dx = cx - ex, dy = cy - ey, dz = cz - ez;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > R) continue;
            if (e instanceof LivingEntity && dist < SWALLOW && t > 15) {
                Fx.send(level, kind == Kind.INVERTED ? WHITE : BLACK, ex, ey, ez, 40, 0.4, 0.8, 0.4, 0);
                Fx.send(level, Fx.particle("flash"), ex, ey, ez, 1, 0, 0, 0, 0);
                Fx.sound(level, ex, ey, ez, "entity.enderman.teleport", 3.0F, kind == Kind.INVERTED ? 1.8F : 0.3F);
                Dims.send(e, level, dest());
                continue;
            }
            if (e == owner) continue;
            double a = Math.min(0.5D, 1.8D / Math.max(dist, 0.8D)) * power;
            Vec3 v = e.m_20184_().m_82520_(dx / dist * a, dy / dist * a + 0.03D, dz / dist * a);
            double sp = v.m_82553_();
            if (sp > 1.8D) v = v.m_82490_(1.8D / sp);
            e.m_20256_(v); e.f_19864_ = true; e.f_19789_ = 0.0F;
        }
    }
}
