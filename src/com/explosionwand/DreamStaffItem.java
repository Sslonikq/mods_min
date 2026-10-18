package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public class DreamStaffItem extends Item {
    public DreamStaffItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            DreamStaff.use(sl, sp);
            player.m_36335_().m_41524_(this, 10);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237113_("ПКМ — Кольцо сегментов (10 сек)").m_130940_(ChatFormatting.valueOf("AQUA")));
        lines.add(Component.m_237113_("Shift+ПКМ — Взгляд Сна: усыпляет мобов").m_130940_(ChatFormatting.valueOf("AQUA")));
        lines.add(Component.m_237113_("Ночью сильнее на 10%").m_130940_(ChatFormatting.valueOf("DARK_GRAY")));
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
