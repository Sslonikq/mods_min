package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Дневник Архимага (4.13): ПКМ выводит в чат открытые страницы (Journal). */
public class JournalItem extends Item {
    public JournalItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && player instanceof ServerPlayer sp) Journal.show(sp);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237115_("item.explosionwand.archmage_journal.desc").m_130940_(ChatFormatting.valueOf("GRAY")));
    }
}
