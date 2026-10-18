package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** ПКМ — синий портал, Shift+ПКМ — оранжевый, ПКМ в небо — убрать оба. */
public class PortalGunItem extends Item {
    public PortalGunItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl) {
            PortalGun.fire(sl, player, player.m_6144_());
            player.m_36335_().m_41524_(this, 5);
        }
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }
}
