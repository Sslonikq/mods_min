package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

/** Часть брони данжа (4.12): Кристальный доспех, Облачение Сновидца, Пепельный доспех. Не крафтится, падает с мобов своего данжа. */
public class SetArmorItem extends ArmorItem {
    final ArmorSets.Set set;

    public SetArmorItem(ArmorSets.Set set, ArmorItem.Type type, Item.Properties p) {
        super(set.material, type, p);
        this.set = set;
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237115_("explosionwand.set." + set.id + ".title").m_130940_(ChatFormatting.valueOf(set.color)));
        lines.add(Component.m_237115_("explosionwand.set.header2").m_130940_(ChatFormatting.valueOf("GRAY")));
        for (String k : set.p2) lines.add(Component.m_237115_("explosionwand.set." + set.id + "." + k).m_130940_(ChatFormatting.valueOf("AQUA")));
        lines.add(Component.m_237115_("explosionwand.set.header4").m_130940_(ChatFormatting.valueOf("GRAY")));
        for (String k : set.p4) lines.add(Component.m_237115_("explosionwand.set." + set.id + "." + k).m_130940_(ChatFormatting.valueOf("AQUA")));
        lines.add(Component.m_237115_("explosionwand.set." + set.id + ".dmg").m_130940_(ChatFormatting.valueOf("GREEN")));
        lines.add(Component.m_237115_("explosionwand.set.source").m_130940_(ChatFormatting.valueOf("DARK_GRAY")));
    }
}
