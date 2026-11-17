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

/** Парашют: ПКМ в воздухе раскрывает купол (медленное падение и планирование), Shift обрезает стропы. Логика - Parachute. */
public class ParachuteItem extends Item {
    public ParachuteItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            if (Parachute.isOpen(sp)) return InteractionResultHolder.m_19092_(stack, false);
            if (Parachute.canOpen(sp)) {
                Parachute.ACTIVE.add(new Parachute(sl, sp));
                stack.m_41622_(1, sp, (ServerPlayer x) -> x.m_21190_(hand));
                player.m_21011_(hand, true);
            } else sp.m_5661_(Component.m_237115_("explosionwand.parachute.air"), true);
        }
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237115_("item.explosionwand.parachute.desc1").m_130940_(ChatFormatting.valueOf("GRAY")));
        lines.add(Component.m_237115_("item.explosionwand.parachute.desc2").m_130940_(ChatFormatting.valueOf("GRAY")));
    }
}
