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

/**
 * Взрывная палочка (4.11, урезанная): ПКМ - тычок уроном 8 (перезарядка 1,5 с), слабый взрыв (слабее динамита),
 * ломает 2-4 блока. Орбитальный удар и горящие осколки (Shift+ПКМ, Ember) убраны. Логика - {@link Earth#blast}.
 */
public class ExplosionWandItem extends Item {
    public ExplosionWandItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            int cd = Earth.blast(sl, sp);
            if (cd > 0) player.m_36335_().m_41524_(this, EarthLogic.cooldown(cd, ArcaneSet.cd(sp)));
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237115_("item.explosionwand.explosion_wand.desc1").m_130940_(ChatFormatting.valueOf("AQUA")));
        lines.add(Component.m_237115_("item.explosionwand.explosion_wand.desc2").m_130940_(ChatFormatting.valueOf("DARK_GRAY")));
    }
}
