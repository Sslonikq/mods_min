package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/**
 * Мир «Некто»: за каждым игроком следит фигура, которую видит только он.
 * Появляется всё ближе, исчезает, когда на неё смотрят... и в какой-то момент начинает охоту.
 */
final class Haunt {
    static final Random RNG = Fx.RNG;
    static ParticleOptions BODY, EYE, EYE_BIG;
    static MobEffect DARKNESS, SLOW, HUNGER;

    static final class S {
        int phase = 0, timer = 300 + RNG.nextInt(300), appear = 0, visible = 0, seen = 0, hitCd = 0, hunt = 0;
        double fx, fy, fz;
    }
    static final Map<ServerPlayer, S> MAP = new WeakHashMap<>();

    static void init() {
        if (BODY != null) return;
        BODY = new DustParticleOptions(new Vector3f(0, 0, 0), 2.6F);
        EYE = new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.05F), 0.7F);
        EYE_BIG = new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 1.1F);
        DARKNESS = Fx.effect("darkness"); SLOW = Fx.effect("slowness"); HUNGER = Fx.effect("hunger");
    }

    static void tick(ServerLevel level) {
        init();
        List<ServerPlayer> players = level.m_6907_();
        MAP.keySet().removeIf(p -> !players.contains(p));
        for (ServerPlayer p : players) {
            if (!p.m_6084_()) { MAP.remove(p); continue; }
            step(level, p, MAP.computeIfAbsent(p, k -> new S()));
        }
    }

    static double groundY(ServerLevel l, double x, double z, double py) {
        int h = l.m_6924_(Heightmap.Types.valueOf("MOTION_BLOCKING_NO_LEAVES"), (int) Math.floor(x), (int) Math.floor(z));
        return Math.abs(h - py) <= 8 ? h : py;
    }

    static void sound(ServerLevel l, double x, double y, double z, String id, float v, float pitch) { Fx.sound(l, x, y, z, id, v, pitch); }

    static void step(ServerLevel level, ServerPlayer p, S s) {
        Vec3 pos = p.m_20182_();
        Vec3 look = p.m_20252_(1.0F);
        double px = pos.f_82479_, py = pos.f_82480_, pz = pos.f_82481_;
        double lx = look.f_82479_, lz = look.f_82481_, ll = Math.max(1e-4, Math.sqrt(lx * lx + lz * lz));
        lx /= ll; lz /= ll;

        // фоновые звуки: шаги сзади, шёпот, сердце
        if (s.phase < 2) {
            if (RNG.nextInt(260) == 0) for (int i = 0; i < 3; i++)
                sound(level, px - lx * (5 + i), py, pz - lz * (5 + i), "block.grass.step", 0.7F, 0.7F);
            if (RNG.nextInt(500) == 0) sound(level, px + RNG.nextGaussian() * 6, py + 1, pz + RNG.nextGaussian() * 6, "ambient.cave", 1.2F, 0.6F);
            if (RNG.nextInt(700) == 0) sound(level, px - lx * 3, py, pz - lz * 3, "entity.warden.nearby_closer", 1.0F, 0.7F);
            if (RNG.nextInt(900) == 0 && DARKNESS != null) p.m_7292_(new MobEffectInstance(DARKNESS, 120, 0));
        }
        if (HUNGER != null && RNG.nextInt(200) == 0) p.m_7292_(new MobEffectInstance(HUNGER, 200, 0));

        switch (s.phase) {
            case 0 -> { if (--s.timer <= 0) { s.phase = 1; s.timer = 100 + RNG.nextInt(200); } }
            case 1 -> stalk(level, p, s, px, py, pz, lx, lz, look);
            case 2 -> hunt(level, p, s, px, py, pz);
        }
    }

    static void stalk(ServerLevel level, ServerPlayer p, S s, double px, double py, double pz, double lx, double lz, Vec3 look) {
        if (s.visible > 0) {
            figure(level, p, s.fx, s.fy, s.fz, false);
            s.visible--;
            double dx = s.fx - px, dy = (s.fy + 1.6) - (py + 1.6), dz = s.fz - pz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double dot = (dx * look.f_82479_ + dy * look.f_82480_ + dz * look.f_82481_) / Math.max(1e-4, d);
            if (dot > 0.93 && d > 4) s.seen++;
            if (s.seen > 8 || s.visible == 0) {          // посмотрели — исчез
                s.visible = 0; s.appear++;
                s.timer = 120 + RNG.nextInt(240);
                if (s.seen > 8 && RNG.nextBoolean()) sound(level, px, py, pz, "entity.warden.heartbeat", 1.5F, 0.6F);
            }
            return;
        }
        if (--s.timer > 0) return;
        // пора охотиться?
        if (s.appear >= 8 || (s.appear >= 4 && RNG.nextInt(4) == 0)) { startHunt(level, p, s, px, py, pz, lx, lz); return; }
        double dist = Math.max(8.0, 34.0 - s.appear * 4.0);
        double base = Math.atan2(-lz, -lx);                 // за спиной
        double a = base + (RNG.nextDouble() - 0.5) * 2.4;
        s.fx = px + Math.cos(a) * dist; s.fz = pz + Math.sin(a) * dist;
        s.fy = groundY(level, s.fx, s.fz, py);
        s.visible = 100 + RNG.nextInt(100); s.seen = 0;
        if (RNG.nextInt(3) == 0) sound(level, s.fx, s.fy, s.fz, "entity.warden.nearby_close", 1.0F, 0.5F);
    }

    static void startHunt(ServerLevel level, ServerPlayer p, S s, double px, double py, double pz, double lx, double lz) {
        s.phase = 2; s.hunt = 0;
        s.fx = px - lx * 16; s.fz = pz - lz * 16; s.fy = groundY(level, s.fx, s.fz, py);
        sound(level, s.fx, s.fy, s.fz, "entity.warden.roar", 3.0F, 0.6F);
        sound(level, px, py, pz, "entity.elder_guardian.curse", 1.0F, 0.5F);
        if (DARKNESS != null) p.m_7292_(new MobEffectInstance(DARKNESS, 400, 0));
    }

    static void hunt(ServerLevel level, ServerPlayer p, S s, double px, double py, double pz) {
        s.hunt++;
        double dx = px - s.fx, dy = py - s.fy, dz = pz - s.fz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double speed = 0.33 + Math.min(0.12, s.hunt * 0.0006);   // чуть быстрее спринта и всё быстрее
        if (d > 0.01) { double k = Math.min(speed, d) / d; s.fx += dx * k; s.fy += dy * k; s.fz += dz * k; }
        figure(level, p, s.fx, s.fy, s.fz, true);
        if (s.hunt % 18 == 0) sound(level, px, py, pz, "entity.warden.heartbeat", 2.0F, 1.3F);
        if (s.hunt % 30 == 0) sound(level, s.fx, s.fy, s.fz, "entity.warden.step", 2.0F, 0.6F);
        if (s.hitCd > 0) s.hitCd--;
        if (d < 1.7 && s.hitCd == 0) {
            s.hitCd = 14;
            p.m_6469_(level.m_269111_().m_269425_(), 7.0F);
            if (SLOW != null) p.m_7292_(new MobEffectInstance(SLOW, 40, 1));
            if (DARKNESS != null) p.m_7292_(new MobEffectInstance(DARKNESS, 100, 0));
            sound(level, px, py, pz, "entity.warden.attack_impact", 2.0F, 0.6F);
            Fx.sendTo(level, p, EYE_BIG, px, py + 1.2, pz, 25, 0.4, 0.6, 0.4, 0);
        }
    }

    /** Высокая чёрная фигура с длинными руками и красными глазами — видна только этому игроку. */
    static void figure(ServerLevel l, ServerPlayer p, double x, double y, double z, boolean hunting) {
        Vec3 pp = p.m_20182_();
        double tx = pp.f_82479_ - x, tz = pp.f_82481_ - z, tl = Math.max(1e-4, Math.sqrt(tx * tx + tz * tz));
        tx /= tl; tz /= tl;
        double sx = -tz, sz = tx;
        Fx.sendTo(l, p, BODY, x, y + 1.15, z, 16, 0.15, 0.6, 0.15, 0);
        Fx.sendTo(l, p, BODY, x, y + 2.35, z, 7, 0.13, 0.13, 0.13, 0);
        Fx.sendTo(l, p, BODY, x + sx * 0.42, y + 1.2, z + sz * 0.42, 5, 0.04, 0.6, 0.04, 0);
        Fx.sendTo(l, p, BODY, x - sx * 0.42, y + 1.2, z - sz * 0.42, 5, 0.04, 0.6, 0.04, 0);
        Fx.sendTo(l, p, BODY, x + sx * 0.14, y + 0.35, z + sz * 0.14, 3, 0.04, 0.3, 0.04, 0);
        Fx.sendTo(l, p, BODY, x - sx * 0.14, y + 0.35, z - sz * 0.14, 3, 0.04, 0.3, 0.04, 0);
        ParticleOptions eye = hunting ? EYE_BIG : EYE;
        double ex = x + tx * 0.2, ez = z + tz * 0.2;
        Fx.sendTo(l, p, eye, ex + sx * 0.1, y + 2.42, ez + sz * 0.1, 1, 0, 0, 0, 0);
        Fx.sendTo(l, p, eye, ex - sx * 0.1, y + 2.42, ez - sz * 0.1, 1, 0, 0, 0, 0);
        if (hunting) Fx.sendTo(l, p, Fx.particle("smoke"), x, y + 1.2, z, 4, 0.3, 0.8, 0.3, 0.02);
    }
}
