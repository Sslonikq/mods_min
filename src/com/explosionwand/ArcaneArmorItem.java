package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public class ArcaneArmorItem extends ArmorItem {
    public ArcaneArmorItem(ArmorItem.Type type, Item.Properties p) { super(ArcaneMaterial.INSTANCE, type, p); }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237113_("Облачение Архимага").m_130940_(ChatFormatting.valueOf("LIGHT_PURPLE")));
        lines.add(Component.m_237113_("Полный сет:").m_130940_(ChatFormatting.valueOf("GRAY")));
        lines.add(Component.m_237113_(" • перезарядка посохов −30%").m_130940_(ChatFormatting.valueOf("AQUA")));
        lines.add(Component.m_237113_(" • магический урон −35%").m_130940_(ChatFormatting.valueOf("AQUA")));
        lines.add(Component.m_237113_(" • скорость, ночное зрение, щит маны").m_130940_(ChatFormatting.valueOf("AQUA")));
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
