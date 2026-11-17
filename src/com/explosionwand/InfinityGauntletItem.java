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

/** Перчатка Бесконечности: Shift+ПКМ - следующий камень, ПКМ - способность камня. Логика - Gauntlet, порядок камней - GauntletLogic. */
public class InfinityGauntletItem extends Item {
    public InfinityGauntletItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            if (player.m_6144_()) Gauntlet.cycle(sl, sp, stack);
            else {
                int stone = Gauntlet.stone(stack);
                long now = Gauntlet.now(sl);
                int left = Gauntlet.left(stack, stone, now);
                if (left > 0) player.m_5661_(Component.m_237110_("explosionwand.gauntlet.cooldown", Gauntlet.stoneName(stone), GauntletLogic.secs(left)), true);
                else if (Gauntlet.use(sl, sp, stone)) {
                    stack.m_41784_().m_128356_(GauntletLogic.cdKey(stone), GauntletLogic.readyAt(now, stone));
                    player.m_36335_().m_41524_(this, GauntletLogic.ITEM_COOLDOWN);
                }
            }
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237115_("item.explosionwand.infinity_gauntlet.desc1").m_130940_(ChatFormatting.valueOf("GRAY")));
        lines.add(Component.m_237115_("item.explosionwand.infinity_gauntlet.desc2").m_130940_(ChatFormatting.valueOf("GRAY")));
        lines.add(Component.m_237110_("explosionwand.gauntlet.current", Component.m_237115_("explosionwand.gauntlet.stone." + GauntletLogic.key(Gauntlet.stone(stack)))).m_130940_(ChatFormatting.valueOf("GOLD")));
        if (level != null) {
            int stone = Gauntlet.stone(stack), left = Gauntlet.left(stack, stone, Gauntlet.now(level));
            if (left > 0) lines.add(Component.m_237110_("explosionwand.gauntlet.cooldown", Gauntlet.stoneName(stone), GauntletLogic.secs(left)).m_130940_(ChatFormatting.valueOf("RED")));
        }
    }
}
