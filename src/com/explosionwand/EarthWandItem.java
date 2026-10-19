package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Палочки Земли (4.11, часть B): Огонька, Морозца, Роста, Сбора, Возврата, Сигнальная, Копателя. Один класс, вид - {@link EarthLogic.Kind};
 * логика - {@link Earth}. Возврат и Копатель в данжах молчат (DungeonGuard.forbidden смотрит на {@code kind.noDungeon}).
 * Тултип: item.explosionwand.&lt;id&gt;.desc1 и .desc2.
 */
public class EarthWandItem extends Item {
    final EarthLogic.Kind kind;

    public EarthWandItem(Properties p, EarthLogic.Kind kind) { super(p); this.kind = kind; }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            int cd = Earth.use(kind, sl, sp);
            if (cd > 0) player.m_36335_().m_41524_(this, EarthLogic.cooldown(cd, ArcaneSet.cd(sp)));
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237115_("item.explosionwand." + kind.id + ".desc1").m_130940_(ChatFormatting.valueOf("AQUA")));
        lines.add(Component.m_237115_("item.explosionwand." + kind.id + ".desc2").m_130940_(ChatFormatting.valueOf("DARK_GRAY")));
    }
}
