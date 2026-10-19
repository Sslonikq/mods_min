package com.explosionwand.jei;

import com.explosionwand.ExplosionWandMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * JEI-плагин. Класс грузит только сам JEI (находит по аннотации); основной код на него не ссылается,
 * поэтому без JEI мод работает как обычно. Описывает предметы-трофеи, у которых нет крафта.
 */
@JeiPlugin
public class ExplosionWandJei implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() { return new ResourceLocation("explosionwand", "jei"); }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        info(reg, new ItemStack(ExplosionWandMod.RIFT_CORE.get()), "rift_core");
        info(reg, new ItemStack(ExplosionWandMod.ECLIPSE_HEART.get()), "eclipse_heart");
        info(reg, new ItemStack(ExplosionWandMod.DREAM_STAFF.get()), "dream_staff");
        info(reg, new ItemStack(ExplosionWandMod.DREAMING_EYE.get()), "dreaming_eye");
        info(reg, new ItemStack(ExplosionWandMod.WHALE_STAFF.get()), "whale_staff");
        info(reg, new ItemStack(ExplosionWandMod.RIFT_STAFF.get()), "rift_staff");
        info(reg, new ItemStack(ExplosionWandMod.SLEEP_SEAL_SHARD.get()), "sleep_seal_shard");
        info(reg, new ItemStack(ExplosionWandMod.TEMPLE_SEAL.get()), "temple_seal");
    }

    private static void info(IRecipeRegistration reg, ItemStack stack, String key) {
        reg.addItemStackInfo(stack, Component.m_237115_("explosionwand.jei." + key));
    }
}
