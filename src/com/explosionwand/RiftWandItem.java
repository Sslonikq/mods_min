package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class RiftWandItem extends Item {
    public RiftWandItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl) {
            Vec3 look = player.m_20252_(1.0F);
            Rift.ACTIVE.add(new Rift(sl, player.m_20182_(), look.f_82479_, look.f_82481_, player));
            player.m_36335_().m_41524_(this, 160);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
