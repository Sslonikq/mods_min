package com.explosionwand;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Инструменты из кровавого железа (5.2): ступень между алмазом и незеритом. Прочнее алмаза (1800 против 1561), быстрее копают, зачарываются лучше;
 * уровень добычи 3 (как алмаз; блоки Ада с needs_diamond_tool берутся). Блоки Ада (пространство имён мода) копают ещё на 25% быстрее.
 * Починка - Слиток кровавого железа. Числа: алмаз 1561 / 8.0 / +3 / 10, незерит 2031 / 9.0 / +4 / 15.
 */
final class BloodIron {
    static final int USES = 1800, LEVEL = 3, ENCHANT = 12;
    static final float SPEED = 8.5F, DAMAGE = 3.5F, HELL_BONUS = 1.25F;
    /** Модификаторы урона/скорости атаки как у алмазных инструментов того же вида (базовый урон складывается с DAMAGE). */
    static final int SWORD_DMG = 3, PICK_DMG = 1, HOE_DMG = -3;
    static final float AXE_DMG = 5.0F, SHOVEL_DMG = 1.5F, SWORD_SPD = -2.4F, PICK_SPD = -2.8F, AXE_SPD = -3.0F, SHOVEL_SPD = -3.0F, HOE_SPD = 0.0F;

    static final Tier TIER = new Tier() {
        @Override public int m_6609_() { return USES; }
        @Override public float m_6624_() { return SPEED; }
        @Override public float m_6631_() { return DAMAGE; }
        @Override public int m_6604_() { return LEVEL; }
        @Override public int m_6601_() { return ENCHANT; }
        @Override public Ingredient m_6282_() { return repair(); }
    };

    private static Ingredient repairIngredient;
    /** Ингредиент ремонта: слиток ищем в реестре один раз (предметы к первому вызову уже зарегистрированы). */
    static Ingredient repair() {
        if (repairIngredient == null) repairIngredient = Ingredient.m_43929_(ForgeRegistries.ITEMS.getValue(new ResourceLocation("explosionwand", "blood_iron_ingot")));
        return repairIngredient;
    }

    /** Скорость добычи: на блоках мода (Ад) - бонус, но только если инструмент вообще копает этот блок быстрее 1. */
    static float speed(float base, BlockState st) {
        if (base <= 1.0F) return base;
        ResourceLocation k = ForgeRegistries.BLOCKS.getKey(st.m_60734_());
        return k != null && k.m_135827_().equals("explosionwand") ? base * HELL_BONUS : base;
    }

    static final class Pick extends PickaxeItem {
        Pick(Item.Properties p) { super(TIER, PICK_DMG, PICK_SPD, p); }
        @Override public float m_8102_(ItemStack s, BlockState st) { return speed(super.m_8102_(s, st), st); }
    }
    static final class Axe extends AxeItem {
        Axe(Item.Properties p) { super(TIER, AXE_DMG, AXE_SPD, p); }
        @Override public float m_8102_(ItemStack s, BlockState st) { return speed(super.m_8102_(s, st), st); }
    }
    static final class Shovel extends ShovelItem {
        Shovel(Item.Properties p) { super(TIER, SHOVEL_DMG, SHOVEL_SPD, p); }
        @Override public float m_8102_(ItemStack s, BlockState st) { return speed(super.m_8102_(s, st), st); }
    }
    static final class Hoe extends HoeItem {
        Hoe(Item.Properties p) { super(TIER, HOE_DMG, HOE_SPD, p); }
        @Override public float m_8102_(ItemStack s, BlockState st) { return speed(super.m_8102_(s, st), st); }
    }
    static final class Sword extends SwordItem {
        Sword(Item.Properties p) { super(TIER, SWORD_DMG, SWORD_SPD, p); }
    }

    private BloodIron() {}
}
