package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class BlackHoleWandItem extends Item {
    public BlackHoleWandItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl) {
            Aim aim = Aim.of(level, player, 64.0D);
            Vec3 c = aim.hit ? aim.target.m_82520_(0.0D, 3.0D, 0.0D) : aim.target;
            aim.beam(sl, Fx.particle("reverse_portal"), 0.5D);
            Fx.sound(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(), "entity.enderman.teleport", 1.0F, 0.5F);
            BlackHole.ACTIVE.add(new BlackHole(sl, c, player));
            player.m_36335_().m_41524_(this, 300);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
