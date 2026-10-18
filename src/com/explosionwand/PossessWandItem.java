package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PossessWandItem extends Item {
    public PossessWandItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl) {
            if (player instanceof ServerPlayer sp) Possess.use(sl, sp);
            player.m_36335_().m_41524_(this, 10);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
