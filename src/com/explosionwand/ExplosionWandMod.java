package com.explosionwand;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod("explosionwand")
public class ExplosionWandMod {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "explosionwand");
    public static final RegistryObject<Item> EXPLOSION_WAND = ITEMS.register("explosion_wand", () -> new ExplosionWandItem(props()));
    public static final RegistryObject<Item> BLACK_HOLE_WAND = ITEMS.register("black_hole_wand", () -> new BlackHoleWandItem(props()));
    public static final RegistryObject<Item> ICE_STORM_WAND = ITEMS.register("ice_storm_wand", () -> new IceStormWandItem(props()));
    public static final RegistryObject<Item> RIFT_WAND = ITEMS.register("rift_wand", () -> new RiftWandItem(props()));

    public static final RegistryObject<Item> VOID_WAND = ITEMS.register("void_wand", () -> new PortalWandItem(props(), Portal.Kind.VOID));
    public static final RegistryObject<Item> HAUNT_WAND = ITEMS.register("haunt_wand", () -> new PortalWandItem(props(), Portal.Kind.HAUNT));
    public static final RegistryObject<Item> INVERTED_WAND = ITEMS.register("inverted_wand", () -> new PortalWandItem(props(), Portal.Kind.INVERTED));
    public static final RegistryObject<Item> CHRONOS_WAND = ITEMS.register("chronos_wand", () -> new ChronosWandItem(props()));

    public static final RegistryObject<Item> MIMIC_WAND = ITEMS.register("mimic_wand", () -> new MimicWandItem(props()));

    public static final RegistryObject<Item> TESSERACT_WAND = ITEMS.register("tesseract_wand", () -> new TesseractWandItem(props()));

    public static final RegistryObject<Item> RIFT_CORE = ITEMS.register("rift_core", () -> new RiftCoreItem(new Item.Properties().m_41487_(16).m_41497_(Rarity.valueOf("EPIC"))));
    public static final RegistryObject<Item> PORTAL_GUN = ITEMS.register("portal_gun", () -> new PortalGunItem(props()));
    public static final RegistryObject<Item> FIST_WAND = ITEMS.register("fist_wand", () -> new FistWandItem(props()));

    public static final RegistryObject<Item> TIMESTOP_WAND = ITEMS.register("timestop_wand", () -> new TimeStopWandItem(props()));
    public static final RegistryObject<Item> ECLIPSE_SEAL = ITEMS.register("eclipse_seal", () -> new EclipseSealItem(props()));
    public static final RegistryObject<Item> ECLIPSE_HEART = ITEMS.register("eclipse_heart", () -> new RiftCoreItem(new Item.Properties().m_41487_(16).m_41497_(Rarity.valueOf("EPIC"))));
    public static final RegistryObject<Item> ECLIPSE_STAFF = ITEMS.register("eclipse_staff", () -> new EclipseStaffItem(props()));

    public static final RegistryObject<Item> COMET_WAND = ITEMS.register("comet_wand", () -> new CometWandItem(props()));
    public static final RegistryObject<Item> DREAM_SEAL = ITEMS.register("dream_seal", () -> new DreamSealItem(props()));
    public static final RegistryObject<Item> DREAMING_EYE = ITEMS.register("dreaming_eye", () -> new RiftCoreItem(new Item.Properties().m_41487_(16).m_41497_(Rarity.valueOf("EPIC"))));

    public static final RegistryObject<Item> DREAM_STAFF = ITEMS.register("dream_staff", () -> new DreamStaffItem(props()));
    public static final RegistryObject<Item> SKY_SEAL = ITEMS.register("sky_seal", () -> new SkySealItem(props()));
    public static final RegistryObject<Item> WHALE_STAFF = ITEMS.register("whale_staff", () -> new WhaleStaffItem(props()));

    public static final RegistryObject<Item> ARCANE_HELMET = ITEMS.register("arcane_helmet", () -> new ArcaneArmorItem(ArmorItem.Type.valueOf("HELMET"), new Item.Properties().m_41497_(Rarity.valueOf("EPIC"))));
    public static final RegistryObject<Item> ARCANE_CHESTPLATE = ITEMS.register("arcane_chestplate", () -> new ArcaneArmorItem(ArmorItem.Type.valueOf("CHESTPLATE"), new Item.Properties().m_41497_(Rarity.valueOf("EPIC"))));
    public static final RegistryObject<Item> ARCANE_LEGGINGS = ITEMS.register("arcane_leggings", () -> new ArcaneArmorItem(ArmorItem.Type.valueOf("LEGGINGS"), new Item.Properties().m_41497_(Rarity.valueOf("EPIC"))));
    public static final RegistryObject<Item> ARCANE_BOOTS = ITEMS.register("arcane_boots", () -> new ArcaneArmorItem(ArmorItem.Type.valueOf("BOOTS"), new Item.Properties().m_41497_(Rarity.valueOf("EPIC"))));

    public static final RegistryObject<Item> RIFT_STAFF = ITEMS.register("rift_staff", () -> new RiftStaffItem(props()));
    public static final RegistryObject<Item> SEAL_SHARD_RIFT = ITEMS.register("seal_shard_rift", () -> new SealShardItem(new Item.Properties().m_41487_(1).m_41497_(Rarity.valueOf("RARE")), "rift"));
    public static final RegistryObject<Item> SLEEP_SEAL_SHARD = ITEMS.register("sleep_seal_shard", () -> new SealShardItem(new Item.Properties().m_41487_(1).m_41497_(Rarity.valueOf("RARE")), "sleep", "sleep_seal_shard"));

    /** Печать Храма (Город Спящих, 4.5): лежит в сундуке склепа, отпирает дверь храма. Не крафтится. */
    public static final RegistryObject<Item> TEMPLE_SEAL = ITEMS.register("temple_seal", () -> new SealShardItem(new Item.Properties().m_41487_(1).m_41497_(Rarity.valueOf("EPIC")), "sleep_temple", "temple_seal", true));

    // ---- 4.11: палочки Земли (16 штук, части B и C) регистрируются ЗДЕСЬ, до материалов: во вкладке «Магия» палочки идут раньше ингредиентов.

    /** Палочки Земли, часть B (4.11): стартовые и Копатель. Взрывная - EXPLOSION_WAND выше (переделана). Часть C регистрирует остальные ниже. */
    public static final RegistryObject<Item> LIGHT_WAND = ITEMS.register("light_wand", () -> new EarthWandItem(earthProps("UNCOMMON"), EarthLogic.Kind.LIGHT));
    public static final RegistryObject<Item> FROST_WAND = ITEMS.register("frost_wand", () -> new EarthWandItem(earthProps("UNCOMMON"), EarthLogic.Kind.FROST));
    public static final RegistryObject<Item> GROWTH_WAND = ITEMS.register("growth_wand", () -> new EarthWandItem(earthProps("UNCOMMON"), EarthLogic.Kind.GROWTH));
    public static final RegistryObject<Item> GATHER_WAND = ITEMS.register("gather_wand", () -> new EarthWandItem(earthProps("UNCOMMON"), EarthLogic.Kind.GATHER));
    public static final RegistryObject<Item> RETURN_WAND = ITEMS.register("return_wand", () -> new EarthWandItem(earthProps("UNCOMMON"), EarthLogic.Kind.RETURN));
    public static final RegistryObject<Item> SIGNAL_WAND = ITEMS.register("signal_wand", () -> new EarthWandItem(earthProps("UNCOMMON"), EarthLogic.Kind.SIGNAL));
    public static final RegistryObject<Item> DIGGER_WAND = ITEMS.register("digger_wand", () -> new EarthWandItem(earthProps("RARE"), EarthLogic.Kind.DIGGER));
    // ---- 4.11 часть C: палочки 9-16 (данжи Разлома, Города, Святилища).
    public static final RegistryObject<Item> SPIKES_WAND = ITEMS.register("crystal_spikes_wand", () -> new EarthWandItem(earthProps("RARE"), EarthLogic.Kind.SPIKES));
    public static final RegistryObject<Item> STONE_SKIN_WAND = ITEMS.register("stone_skin_wand", () -> new EarthWandItem(earthProps("RARE"), EarthLogic.Kind.SKIN));
    public static final RegistryObject<Item> WIND_WAND = ITEMS.register("wind_wand", () -> new EarthWandItem(earthProps("RARE"), EarthLogic.Kind.WIND));
    public static final RegistryObject<Item> VINE_WAND = ITEMS.register("vine_wand", () -> new EarthWandItem(earthProps("RARE"), EarthLogic.Kind.VINE));
    public static final RegistryObject<Item> PULL_WAND = ITEMS.register("pull_wand", () -> new EarthWandItem(earthProps("RARE"), EarthLogic.Kind.PULL));
    public static final RegistryObject<Item> DECOY_WAND = ITEMS.register("decoy_wand", () -> new EarthWandItem(earthProps("RARE"), EarthLogic.Kind.DECOY));
    public static final RegistryObject<Item> SPARK_WAND = ITEMS.register("spark_wand", () -> new EarthWandItem(earthProps("RARE"), EarthLogic.Kind.SPARKS));
    public static final RegistryObject<Item> HEAT_WAND = ITEMS.register("heat_wand", () -> new EarthWandItem(earthProps("RARE"), EarthLogic.Kind.HEAT));

    /** Основа крафта (4.11): Магический стержень и ядра. Схема: docs/world.md, «Крафт — в несколько шагов». */
    public static final RegistryObject<Item> MAGIC_ROD = ITEMS.register("magic_rod", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("UNCOMMON")), "magic_rod"));
    public static final RegistryObject<Item> SIMPLE_CORE = ITEMS.register("simple_core", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("UNCOMMON")), "simple_core"));
    public static final RegistryObject<Item> CRYSTAL_CORE = ITEMS.register("crystal_core", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("RARE")), "crystal_core"));
    public static final RegistryObject<Item> WIND_CORE = ITEMS.register("wind_core", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("RARE")), "wind_core"));
    public static final RegistryObject<Item> FIRE_CORE = ITEMS.register("fire_core", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("RARE")), "fire_core"));

    /** Материалы данжей (4.11): обычный (мобы) / редкий (сундуки побочных комнат) / очень редкий (секретные комнаты). Добыча — Mats. */
    public static final RegistryObject<Item> RIFT_DUST = ITEMS.register("rift_dust", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("COMMON")), "rift_dust"));
    public static final RegistryObject<Item> ECHO_CRYSTAL = ITEMS.register("echo_crystal", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("UNCOMMON")), "echo_crystal"));
    public static final RegistryObject<Item> RIFT_MARROW = ITEMS.register("rift_marrow", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("RARE")), "rift_marrow"));
    public static final RegistryObject<Item> SLEEP_THREAD = ITEMS.register("sleep_thread", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("COMMON")), "sleep_thread"));
    public static final RegistryObject<Item> TEAL_STITCH = ITEMS.register("teal_stitch", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("UNCOMMON")), "teal_stitch"));
    public static final RegistryObject<Item> SLEEPER_TEAR = ITEMS.register("sleeper_tear", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("RARE")), "sleeper_tear"));
    public static final RegistryObject<Item> ASH_COAL = ITEMS.register("ash_coal", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("COMMON")), "ash_coal"));
    public static final RegistryObject<Item> SMOLDERING_FEATHER = ITEMS.register("smoldering_feather", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("UNCOMMON")), "smoldering_feather"));
    public static final RegistryObject<Item> PHOENIX_SPARK = ITEMS.register("phoenix_spark", () -> new MaterialItem(new Item.Properties().m_41497_(Rarity.valueOf("RARE")), "phoenix_spark"));

    /** Отдельная вкладка «Магия» — всё содержимое мода в порядке регистрации. */
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.f_279569_, "explosionwand");
    public static final RegistryObject<CreativeModeTab> MAGIC_TAB = TABS.register("magic", () -> CreativeModeTab.builder()
            .m_257941_(Component.m_237115_("itemGroup.explosionwand.magic"))
            .m_257737_(() -> new ItemStack(ECLIPSE_STAFF.get()))
            .m_257501_((params, out) -> { for (RegistryObject<Item> r : ITEMS.getEntries()) out.m_246326_(r.get()); })
            .m_257652_());

    static Item.Properties earthProps(String rarity) { return new Item.Properties().m_41487_(1).m_41497_(Rarity.valueOf(rarity)); }

    static Item.Properties props() { return new Item.Properties().m_41487_(1).m_41497_(Rarity.valueOf("EPIC")); }

    public ExplosionWandMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        TABS.register(bus);
        MinecraftForge.EVENT_BUS.addListener(Ticker::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(Inverted::onHurt);
        MinecraftForge.EVENT_BUS.addListener(Inverted::onHeal);
        MinecraftForge.EVENT_BUS.addListener(Tesseract::onBreak);
        MinecraftForge.EVENT_BUS.addListener(Boss::onHurt);
        MinecraftForge.EVENT_BUS.addListener(Boss2::onHurt);
        MinecraftForge.EVENT_BUS.addListener(TimeStop::onHurt);
        MinecraftForge.EVENT_BUS.addListener(Boss3::onHurt);
        MinecraftForge.EVENT_BUS.addListener(Comet::onHurt);
        MinecraftForge.EVENT_BUS.addListener(Boss4::onHurt);
        MinecraftForge.EVENT_BUS.addListener(Maze::onBreak);
        MinecraftForge.EVENT_BUS.addListener(ArenaGuard::onBreak);
        MinecraftForge.EVENT_BUS.addListener(ArenaGuard::onPlace);
        MinecraftForge.EVENT_BUS.addListener(ArenaGuard::onDetonate);
        MinecraftForge.EVENT_BUS.addListener(ArcaneSet::onHurt);
        MinecraftForge.EVENT_BUS.addListener(Earth::onHurt);
        MinecraftForge.EVENT_BUS.addListener(Earth::onStopping);
        MinecraftForge.EVENT_BUS.addListener(DungeonGuard::onBreak);
        MinecraftForge.EVENT_BUS.addListener(DungeonGuard::onPlace);
        MinecraftForge.EVENT_BUS.addListener(DungeonGuard::onDetonate);
        MinecraftForge.EVENT_BUS.addListener(DungeonGuard::onPearl);
        MinecraftForge.EVENT_BUS.addListener(DungeonGuard::onChorus);
        MinecraftForge.EVENT_BUS.addListener(DungeonGuard::onUseItem);
        MinecraftForge.EVENT_BUS.addListener(DungeonGuard::onBucket);
        MinecraftForge.EVENT_BUS.addListener(DungeonGuard::onSpawn);
        MinecraftForge.EVENT_BUS.addListener(Dungeons::onRightClick);
        MinecraftForge.EVENT_BUS.addListener(Dungeons::onDeath);
        MinecraftForge.EVENT_BUS.addListener(Dungeons::onRespawn);
        MinecraftForge.EVENT_BUS.addListener(Dungeons::onStopped);
        MinecraftForge.EVENT_BUS.addListener(RiftMobs::onHurt);
        MinecraftForge.EVENT_BUS.addListener(RiftMobs::onDeath);
    }
}
