package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Взрывная палочка 2.0:
 *  ПКМ — луч и мощный взрыв, из которого разлетаются горящие осколки и взрываются там, где упадут.
 *  Shift+ПКМ — «Орбитальный удар»: цель помечается кругом, с неба бьёт столп света и испепеляет всё.
 */
public class ExplosionWandItem extends Item {
    public ExplosionWandItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl) {
            if (player.m_6144_()) orbital(sl, player);
            else shot(sl, player);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    void shot(ServerLevel sl, Player player) {
        Aim aim = Aim.of(sl, player, 128.0D);
        aim.beam(sl, Fx.particle("end_rod"), 0.75D);
        Fx.sound(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(), "item.firecharge.use", 1.0F, 0.6F);
        Vec3 t = aim.target;
        sl.m_255391_(player, t.f_82479_, t.f_82480_, t.f_82481_, 16.0F, false, Level.ExplosionInteraction.valueOf("TNT"));
        int n = 7;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + Fx.RNG.nextDouble() * 0.5;
            double sp = 0.7 + Fx.RNG.nextDouble() * 0.6;
            Ember.ACTIVE.add(new Ember(sl, player, t.f_82479_, t.f_82480_ + 2, t.f_82481_,
                    Math.cos(a) * sp, 1.0 + Fx.RNG.nextDouble() * 0.7, Math.sin(a) * sp, 4.0F));
        }
        player.m_36335_().m_41524_(this, 40);
    }

    void orbital(ServerLevel sl, Player player) {
        Aim aim = Aim.of(sl, player, 160.0D);
        final double x = aim.target.f_82479_, y = aim.target.f_82480_, z = aim.target.f_82481_;
        var red = new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.05F), 2.0F);
        var gold = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.4F), 2.5F);
        Fx.sound(sl, x, y, z, "block.beacon.power_select", 8.0F, 0.6F);
        Fx.sound(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(), "block.respawn_anchor.charge", 1.0F, 0.6F);
        for (int k = 0; k < 50; k++) {
            final int i = k;
            Later.run(sl, i + 1, () -> {
                double r = 9.0 * (1 - i / 55.0);
                for (int j = 0; j < 40; j++) {             // сужающийся круг прицела
                    double a = j * Math.PI * 2 / 40 + i * 0.05;
                    Fx.send(sl, red, x + Math.cos(a) * r, y + 0.2, z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                for (int j = 0; j < 4; j++) {               // перекрестие
                    double a = j * Math.PI / 2 + i * 0.05;
                    for (double d = r * 0.3; d < r; d += 0.8) Fx.send(sl, red, x + Math.cos(a) * d, y + 0.2, z + Math.sin(a) * d, 1, 0, 0, 0, 0);
                }
                if (i > 25) {                               // столп света с неба разгорается
                    for (double h = 0; h < 90; h += 2.5) Fx.send(sl, gold, x, y + h, z, 1, (i - 25) * 0.02, 0.5, (i - 25) * 0.02, 0);
                    Fx.send(sl, Fx.particle("end_rod"), x, y + 40, z, 6, 0.4, 30, 0.4, 0);
                }
                if (i % 10 == 0) Fx.sound(sl, x, y, z, "block.beacon.ambient", 8.0F, 0.5F + i / 60F);
            });
        }
        Later.run(sl, 52, () -> {
            sl.m_255391_(player, x, y, z, 22.0F, true, Level.ExplosionInteraction.valueOf("TNT"));
            for (int j = 0; j < 8; j++) {
                double a = j * Math.PI * 2 / 8;
                sl.m_255391_(player, x + Math.cos(a) * 9, y, z + Math.sin(a) * 9, 5.0F, false, Level.ExplosionInteraction.valueOf("TNT"));
            }
            Fx.send(sl, Fx.particle("flash"), x, y + 2, z, 3, 2, 2, 2, 0);
            for (double h = 0; h < 90; h += 1.5) Fx.send(sl, Fx.particle("end_rod"), x, y + h, z, 3, 1.2, 0.5, 1.2, 0.05);
            Fx.sound(sl, x, y, z, "entity.lightning_bolt.thunder", 30.0F, 0.6F);
            Fx.sound(sl, x, y, z, "entity.generic.explode", 30.0F, 0.5F);
        });
        player.m_36335_().m_41524_(this, 160);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
