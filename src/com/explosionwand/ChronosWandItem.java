package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public class ChronosWandItem extends Item {
    public ChronosWandItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl) {
            Chronos existing = Chronos.BY_OWNER.get(player.m_20148_());
            if (existing != null && Chronos.ACTIVE.contains(existing) && !existing.rewinding) {
                existing.trigger();                                   // второй клик — откат
                player.m_36335_().m_41524_(this, 300);
            } else {
                Aim aim = Aim.of(level, player, 48.0D);
                aim.beam(sl, Fx.particle("end_rod"), 0.6D);
                Chronos c = new Chronos(sl, aim.target, player);
                Chronos.ACTIVE.add(c);
                Chronos.BY_OWNER.put(player.m_20148_(), c);
                player.m_36335_().m_41524_(this, 20);
            }
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
