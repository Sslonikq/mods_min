package com.explosionwand;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Осколок Печати: в руке тянется к ближайшему данжу своего типа (частицы + расстояние над хотбаром), рядом дрожит и звенит,
 * у двери данжа отпирает её (правый клик по двери, см. Dungeons.onRightClick).
 */
public class SealShardItem extends Item {
    final String dungeon;
    public SealShardItem(Properties p, String dungeon) { super(p); this.dungeon = dungeon; }

    @Override
    public void m_6883_(ItemStack stack, Level level, Entity e, int slot, boolean selected) {
        if (level.f_46443_ || !(level instanceof ServerLevel sl) || !(e instanceof ServerPlayer p)) return;
        if (selected || p.m_21206_() == stack) Dungeons.pointer(sl, p, dungeon);
    }

    /** Shift + ПКМ в творческом режиме пишет в чат координаты ближайшего данжа (для проверки). */
    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl && player instanceof ServerPlayer sp && sp.m_7500_() && sp.m_6144_()) Dungeons.locate(sl, sp, dungeon);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.m_237115_("item.explosionwand.seal_shard_" + dungeon + ".desc"));
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
