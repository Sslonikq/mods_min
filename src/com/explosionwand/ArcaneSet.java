package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * Бонус полного «Облачения Архимага»: перезарядка посохов −30%, магический урон −35%,
 * скорость I, ночное зрение и «щит маны» — 2 сердца поглощения, восстанавливается раз в 30 секунд.
 */
final class ArcaneSet {
    static final Map<UUID, Long> SHIELD = new HashMap<>();
    static DustParticleOptions PURPLE;

    static boolean full(LivingEntity p) {
        if (p == null) return false;
        return is(p, "HEAD", ExplosionWandMod.ARCANE_HELMET.get()) && is(p, "CHEST", ExplosionWandMod.ARCANE_CHESTPLATE.get())
                && is(p, "LEGS", ExplosionWandMod.ARCANE_LEGGINGS.get()) && is(p, "FEET", ExplosionWandMod.ARCANE_BOOTS.get());
    }
    static boolean is(LivingEntity p, String slot, Item item) {
        ItemStack s = p.m_6844_(EquipmentSlot.valueOf(slot));
        return s != null && !s.m_41619_() && s.m_41720_() == item;
    }
    /** Множитель перезарядки посохов. */
    static double cd(LivingEntity p) { return full(p) ? 0.7 : 1.0; }

    static void tick(ServerLevel l) {
        if (EclipseStaff.time(l) % 20 != 0) return;
        if (PURPLE == null) PURPLE = new DustParticleOptions(new Vector3f(0.7F, 0.3F, 1.0F), 1.0F);
        long now = EclipseStaff.time(l);
        for (ServerPlayer p : l.m_6907_()) {
            if (!p.m_6084_() || !full(p)) continue;
            var sp = Fx.effect("speed"); if (sp != null) p.m_7292_(new MobEffectInstance(sp, 50, 0, true, false));
            if (!Dims.is(l, Dims.MAZE)) { var nv = Fx.effect("night_vision"); if (nv != null) p.m_7292_(new MobEffectInstance(nv, 300, 0, true, false)); }
            Long last = SHIELD.get(p.m_20148_());
            if (last == null || now - last >= 600) {
                var ab = Fx.effect("absorption"); if (ab != null) p.m_7292_(new MobEffectInstance(ab, 600, 0, true, false));
                SHIELD.put(p.m_20148_(), now);
                Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.amethyst_block.resonate", 0.5F, 1.4F);
            }
            Fx.send(l, PURPLE, p.m_20185_(), p.m_20186_() + 1, p.m_20189_(), 4, 0.4, 0.6, 0.4, 0);
            Fx.send(l, Fx.particle("enchant"), p.m_20185_(), p.m_20186_() + 1.2, p.m_20189_(), 6, 0.5, 0.6, 0.5, 0.3);
        }
    }

    static void onHurt(LivingHurtEvent ev) {
        if (!full(ev.getEntity()) || ev.getSource() == null) return;
        String id = ev.getSource().m_19385_();
        if (id != null && (id.equals("magic") || id.equals("indirectMagic") || id.equals("sonic_boom") || id.equals("lightningBolt") || id.equals("wither") || id.equals("dragonBreath")))
            ev.setAmount(ev.getAmount() * 0.65F);
    }
}
