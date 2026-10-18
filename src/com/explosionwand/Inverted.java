package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.*;

/**
 * Перевёртыш: земля висит над головой, тебя тянет вверх. Урон лечит, лечение ранит.
 * Выход — «упасть» в небо через дыру в потолке мира: тебя выбросит домой.
 */
final class Inverted {
    static final Random RNG = Fx.RNG;
    static boolean GUARD = false;
    static MobEffect LEVITATION;
    static final String[] ODD = {"block.bell.use", "block.amethyst_block.chime", "entity.allay.ambient_without_item",
            "block.note_block.chime", "entity.illusioner.mirror_move", "block.beacon.ambient"};

    static boolean here(LivingEntity e) {
        Level l = e.m_9236_();
        return l instanceof ServerLevel sl && Dims.is(sl, Dims.INVERTED);
    }

    static void tick(ServerLevel level, long time) {
        if (LEVITATION == null) LEVITATION = Fx.effect("levitation");
        if (time % 5 != 0) return;
        List<Entity> all = new ArrayList<>();
        for (Entity e : level.m_8583_()) all.add(e);
        for (Entity e : all) {
            if (!(e instanceof LivingEntity le) || !e.m_6084_()) continue;
            double y = e.m_20182_().f_82480_;
            if (y > 262) { Dims.sendHome(e, level); continue; }          // упал в небо — домой
            if (y < -20) {                                                  // в бездне снизу — гибель
                GUARD = true; e.m_6469_(level.m_269111_().m_269425_(), 1000.0F); GUARD = false; continue;
            }
            if (time % 20 == 0 && LEVITATION != null) le.m_7292_(new MobEffectInstance(LEVITATION, 40, 1));
        }
        for (ServerPlayer p : level.m_6907_()) {
            Vec3 pos = p.m_20182_();
            Fx.sendTo(level, p, Fx.particle("warped_spore"), pos.f_82479_, pos.f_82480_, pos.f_82481_, 20, 8, 4, 8, 0);
            Fx.sendTo(level, p, Fx.particle("falling_obsidian_tear"), pos.f_82479_, pos.f_82480_ - 2, pos.f_82481_, 3, 6, 1, 6, 0);
            if (RNG.nextInt(80) == 0) Fx.sound(level, pos.f_82479_ + RNG.nextGaussian() * 8, pos.f_82480_, pos.f_82481_ + RNG.nextGaussian() * 8,
                    ODD[RNG.nextInt(ODD.length)], 1.2F, 0.5F + RNG.nextFloat());
        }
    }

    /** Урон лечит. */
    static void onHurt(LivingHurtEvent ev) {
        if (GUARD) return;
        LivingEntity e = ev.getEntity();
        if (e == null || !here(e) || e.m_20182_().f_82480_ < -20) return;
        ev.setCanceled(true);
        e.m_21153_(Math.min(e.m_21233_(), e.m_21223_() + ev.getAmount()));
        if (e.m_9236_() instanceof ServerLevel sl) {
            Vec3 p = e.m_20182_();
            Fx.send(sl, Fx.particle("heart"), p.f_82479_, p.f_82480_ + 1.5, p.f_82481_, 3, 0.4, 0.3, 0.4, 0);
        }
    }

    /** Лечение ранит (регенерация, еда, зелья — всё наоборот). */
    static void onHeal(LivingHealEvent ev) {
        if (GUARD) return;
        LivingEntity e = ev.getEntity();
        if (e == null || !here(e)) return;
        ev.setCanceled(true);
        if (!(e.m_9236_() instanceof ServerLevel sl)) return;
        GUARD = true;
        try { e.m_6469_(sl.m_269111_().m_269425_(), ev.getAmount()); } finally { GUARD = false; }
        Vec3 p = e.m_20182_();
        Fx.send(sl, Fx.particle("damage_indicator"), p.f_82479_, p.f_82480_ + 1.2, p.f_82481_, 3, 0.3, 0.3, 0.3, 0.1);
    }
}
