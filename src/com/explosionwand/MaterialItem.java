package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Материал или ядро (4.11): обычный предмет с одной строкой подсказки (ключ item.explosionwand.<id>.desc). */
public class MaterialItem extends Item {
    private final String key;

    public MaterialItem(Properties p, String id) { super(p); this.key = "item.explosionwand." + id + ".desc"; }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237115_(key).m_130940_(ChatFormatting.valueOf("GRAY")));
    }
}
