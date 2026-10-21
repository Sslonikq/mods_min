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
        info(reg, new ItemStack(ExplosionWandMod.ASH_SEAL_SHARD.get()), "ash_seal_shard");
        info(reg, new ItemStack(ExplosionWandMod.TEMPLE_SEAL.get()), "temple_seal");
        info(reg, new ItemStack(ExplosionWandMod.ASH_KEY.get()), "ash_key");
        info(reg, new ItemStack(ExplosionWandMod.EMBER_KEY.get()), "ember_key");
        info(reg, new ItemStack(ExplosionWandMod.MAGIC_ROD.get()), "magic_rod");
        info(reg, new ItemStack(ExplosionWandMod.SIMPLE_CORE.get()), "simple_core");
        info(reg, new ItemStack(ExplosionWandMod.CRYSTAL_CORE.get()), "crystal_core");
        info(reg, new ItemStack(ExplosionWandMod.WIND_CORE.get()), "wind_core");
        info(reg, new ItemStack(ExplosionWandMod.FIRE_CORE.get()), "fire_core");
        info(reg, new ItemStack(ExplosionWandMod.RIFT_DUST.get()), "rift_dust");
        info(reg, new ItemStack(ExplosionWandMod.ECHO_CRYSTAL.get()), "echo_crystal");
        info(reg, new ItemStack(ExplosionWandMod.RIFT_MARROW.get()), "rift_marrow");
        info(reg, new ItemStack(ExplosionWandMod.SLEEP_THREAD.get()), "sleep_thread");
        info(reg, new ItemStack(ExplosionWandMod.TEAL_STITCH.get()), "teal_stitch");
        info(reg, new ItemStack(ExplosionWandMod.SLEEPER_TEAR.get()), "sleeper_tear");
        info(reg, new ItemStack(ExplosionWandMod.ASH_COAL.get()), "ash_coal");
        info(reg, new ItemStack(ExplosionWandMod.SMOLDERING_FEATHER.get()), "smoldering_feather");
        info(reg, new ItemStack(ExplosionWandMod.PHOENIX_SPARK.get()), "phoenix_spark");
        // палочки Земли: часть B (4.11). Часть C добавляет страницы своих палочек сюда же
        info(reg, new ItemStack(ExplosionWandMod.EXPLOSION_WAND.get()), "explosion_wand");
        info(reg, new ItemStack(ExplosionWandMod.LIGHT_WAND.get()), "light_wand");
        info(reg, new ItemStack(ExplosionWandMod.FROST_WAND.get()), "frost_wand");
        info(reg, new ItemStack(ExplosionWandMod.GROWTH_WAND.get()), "growth_wand");
        info(reg, new ItemStack(ExplosionWandMod.GATHER_WAND.get()), "gather_wand");
        info(reg, new ItemStack(ExplosionWandMod.RETURN_WAND.get()), "return_wand");
        info(reg, new ItemStack(ExplosionWandMod.SIGNAL_WAND.get()), "signal_wand");
        info(reg, new ItemStack(ExplosionWandMod.DIGGER_WAND.get()), "digger_wand");
        info(reg, new ItemStack(ExplosionWandMod.SPIKES_WAND.get()), "crystal_spikes_wand");
        info(reg, new ItemStack(ExplosionWandMod.STONE_SKIN_WAND.get()), "stone_skin_wand");
        info(reg, new ItemStack(ExplosionWandMod.WIND_WAND.get()), "wind_wand");
        info(reg, new ItemStack(ExplosionWandMod.VINE_WAND.get()), "vine_wand");
        info(reg, new ItemStack(ExplosionWandMod.PULL_WAND.get()), "pull_wand");
        info(reg, new ItemStack(ExplosionWandMod.DECOY_WAND.get()), "decoy_wand");
        info(reg, new ItemStack(ExplosionWandMod.SPARK_WAND.get()), "spark_wand");
        info(reg, new ItemStack(ExplosionWandMod.HEAT_WAND.get()), "heat_wand");
    }

    private static void info(IRecipeRegistration reg, ItemStack stack, String key) {
        reg.addItemStackInfo(stack, Component.m_237115_("explosionwand.jei." + key));
    }
}
