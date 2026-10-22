package com.explosionwand;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

/** Материал «Облачения Архимага»: на уровне незерита, лучше зачаровывается. */
final class ArcaneMaterial implements ArmorMaterial {
    static final ArcaneMaterial INSTANCE = new ArcaneMaterial();
    static final int[] DUR = {11, 16, 15, 13}, DEF = {3, 8, 6, 3};   // по ordinal Type: шлем, нагрудник, поножи, сапоги (прочность как у незерита)

    @Override public int m_266425_(ArmorItem.Type t) { return DUR[t.ordinal()] * 37; }
    @Override public int m_7366_(ArmorItem.Type t) { return DEF[t.ordinal()]; }
    @Override public int m_6646_() { return 25; }
    @Override public SoundEvent m_7344_() { return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("minecraft", "item.armor.equip_netherite")); }
    @Override public Ingredient m_6230_() { return Ingredient.m_43929_(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "amethyst_shard"))); }
    @Override public String m_6082_() { return "explosionwand:arcane"; }
    @Override public float m_6651_() { return 3.0F; }
    @Override public float m_6649_() { return 0.1F; }
}
