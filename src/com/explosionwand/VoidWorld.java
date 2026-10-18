package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/** Пустота: красная вода, голод, жуть и далёкие молчаливые наблюдатели. Выход — только смерть. */
final class VoidWorld {
    static final Random RNG = Fx.RNG;
    static final Map<Entity, Integer> IN_WATER = new WeakHashMap<>();
    static final Map<Entity, Integer> NEXT_SCARE = new WeakHashMap<>();
    static final Map<ServerPlayer, double[]> WATCHER = new WeakHashMap<>();   // x,y,z,ticksLeft,seen,cooldown
    static ParticleOptions RED, BLACK;
    static MobEffect HUNGER, DARKNESS, SLOW;

    static final String[] SCARES = {
            "entity.warden.heartbeat", "ambient.cave", "entity.elder_guardian.curse", "entity.warden.nearby_closer",
            "entity.enderman.stare", "ambient.soul_sand_valley.mood", "entity.warden.listening_angry",
            "block.sculk_shrieker.shriek", "entity.ghast.ambient", "entity.warden.ambient", "entity.warden.sniff"
    };

    static void init() {
        if (RED != null) return;
        RED = new DustParticleOptions(new Vector3f(0.9F, 0.0F, 0.0F), 1.5F);
        BLACK = new DustParticleOptions(new Vector3f(0.0F, 0.0F, 0.0F), 2.0F);
        HUNGER = Fx.effect("hunger"); DARKNESS = Fx.effect("darkness"); SLOW = Fx.effect("slowness");
        Haunt.init();
    }

    static void tick(ServerLevel level, long time) {
        init();
        for (ServerPlayer p : level.m_6907_()) watcher(level, p);
        if (time % 5 != 0) return;
        List<Entity> all = new ArrayList<>();
        for (Entity e : level.m_8583_()) all.add(e);
        for (Entity e : all) {
            if (!(e instanceof LivingEntity le) || !e.m_6084_()) continue;
            if (e.m_20069_()) {
                int ticks = IN_WATER.getOrDefault(e, 0) + 5;
                IN_WATER.put(e, ticks);
                e.m_6469_(level.m_269111_().m_269425_(), 2.0F + ticks / 8.0F);
                Vec3 p = e.m_20182_();
                Fx.send(level, RED, p.f_82479_, p.f_82480_ + 0.5, p.f_82481_, 12, 0.4, 0.4, 0.4, 0);
                if (SLOW != null) le.m_7292_(new MobEffectInstance(SLOW, 30, 2));
            } else IN_WATER.remove(e);
        }
        for (ServerPlayer p : level.m_6907_()) {
            if (HUNGER != null && time % 100 == 0) p.m_7292_(new MobEffectInstance(HUNGER, 220, 1));
            int next = NEXT_SCARE.getOrDefault(p, 200);
            if (next <= 0) {
                Vec3 pos = p.m_20182_();
                double a = RNG.nextDouble() * Math.PI * 2, d = 5 + RNG.nextDouble() * 12;
                Fx.sound(level, pos.f_82479_ + Math.cos(a) * d, pos.f_82480_, pos.f_82481_ + Math.sin(a) * d,
                        SCARES[RNG.nextInt(SCARES.length)], 1.6F, 0.4F + RNG.nextFloat() * 0.5F);
                if (RNG.nextBoolean() && DARKNESS != null) p.m_7292_(new MobEffectInstance(DARKNESS, 200, 0));
                NEXT_SCARE.put(p, 200 + RNG.nextInt(500));
            } else NEXT_SCARE.put(p, next - 5);
            Vec3 pos = p.m_20182_();
            Fx.sendTo(level, p, BLACK, pos.f_82479_, pos.f_82480_ + 2, pos.f_82481_, 10, 8, 4, 8, 0);
            Fx.sendTo(level, p, Fx.particle("ash"), pos.f_82479_, pos.f_82480_ + 2, pos.f_82481_, 15, 8, 4, 8, 0);
        }
    }

    /** Далёкая фигура, которая просто стоит и смотрит. Исчезает, если присмотреться. */
    static void watcher(ServerLevel level, ServerPlayer p) {
        double[] w = WATCHER.computeIfAbsent(p, k -> new double[]{0, 0, 0, 0, 0, 600 + RNG.nextInt(600)});
        Vec3 pos = p.m_20182_();
        if (w[3] > 0) {
            Haunt.figure(level, p, w[0], w[1], w[2], false);
            w[3]--;
            Vec3 look = p.m_20252_(1.0F);
            double dx = w[0] - pos.f_82479_, dy = w[1] - pos.f_82480_, dz = w[2] - pos.f_82481_;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if ((dx * look.f_82479_ + dy * look.f_82480_ + dz * look.f_82481_) / Math.max(1e-4, d) > 0.94) w[4]++;
            if (w[4] > 12 || d < 10) { w[3] = 0; w[5] = 500 + RNG.nextInt(900); }
            return;
        }
        if (--w[5] > 0) return;
        double a = RNG.nextDouble() * Math.PI * 2, d = 22 + RNG.nextDouble() * 12;
        w[0] = pos.f_82479_ + Math.cos(a) * d; w[2] = pos.f_82481_ + Math.sin(a) * d;
        w[1] = Haunt.groundY(level, w[0], w[2], pos.f_82480_);
        w[3] = 200 + RNG.nextInt(200); w[4] = 0;
    }
}
