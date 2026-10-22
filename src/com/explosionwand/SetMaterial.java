package com.explosionwand;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Материалы брони данжей Земли (4.12). Защита, твёрдость, отбрасывание, множитель прочности и зачаровываемость - из docs/world.md;
 * ремонт на наковальне материалом данжа (рецептов брони нет).
 */
final class SetMaterial implements ArmorMaterial {
    static final SetMaterial CRYSTAL = new SetMaterial("crystal", new int[]{2, 6, 5, 2}, 18, 12, 1.0F, 0.0F, "rift_dust", "item.armor.equip_iron");
    static final SetMaterial DREAMER = new SetMaterial("dreamer", new int[]{3, 6, 5, 2}, 22, 18, 1.0F, 0.0F, "sleep_thread", "item.armor.equip_leather");
    static final SetMaterial ASH = new SetMaterial("ash", new int[]{3, 7, 6, 3}, 26, 14, 2.0F, 0.05F, "ash_coal", "item.armor.equip_chain");

    /** Базовая прочность частей (шлем, нагрудник, поножи, сапоги), как у ванильной брони. */
    static final int[] DUR = {11, 16, 15, 13};

    final String id; final int[] def; final int durMul, enchant; final float tough, knock; final String repair, sound;

    SetMaterial(String id, int[] def, int durMul, int enchant, float tough, float knock, String repair, String sound) {
        this.id = id; this.def = def; this.durMul = durMul; this.enchant = enchant; this.tough = tough; this.knock = knock; this.repair = repair; this.sound = sound;
    }

    /** Сумма защиты четырёх частей. */
    int total() { int s = 0; for (int d : def) s += d; return s; }

    @Override public int m_266425_(ArmorItem.Type t) { return DUR[t.ordinal()] * durMul; }
    @Override public int m_7366_(ArmorItem.Type t) { return def[t.ordinal()]; }
    @Override public int m_6646_() { return enchant; }
    @Override public SoundEvent m_7344_() { return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("minecraft", sound)); }
    @Override public Ingredient m_6230_() { return Ingredient.m_43929_(ForgeRegistries.ITEMS.getValue(new ResourceLocation("explosionwand", repair))); }
    @Override public String m_6082_() { return "explosionwand:" + id; }
    @Override public float m_6651_() { return tough; }
    @Override public float m_6649_() { return knock; }
}
