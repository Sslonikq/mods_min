package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class IceStormWandItem extends Item {
    public IceStormWandItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl) {
            Aim aim = Aim.of(level, player, 64.0D);
            aim.beam(sl, Fx.particle("snowflake"), 0.4D);
            Fx.sound(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(), "entity.evoker.cast_spell", 1.5F, 0.7F);
            IceStorm.ACTIVE.add(new IceStorm(sl, aim.target, player));
            player.m_36335_().m_41524_(this, IceStorm.LIFE + 200);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
