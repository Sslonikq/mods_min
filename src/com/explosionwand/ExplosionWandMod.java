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

    public static final RegistryObject<Item> RIFT_KEY = ITEMS.register("rift_key", () -> new RiftKeyItem(props()));
    public static final RegistryObject<Item> RIFT_CORE = ITEMS.register("rift_core", () -> new RiftCoreItem(new Item.Properties().m_41487_(16).m_41497_(Rarity.valueOf("EPIC"))));
    public static final RegistryObject<Item> PORTAL_GUN = ITEMS.register("portal_gun", () -> new PortalGunItem(props()));
    public static final RegistryObject<Item> POSSESS_WAND = ITEMS.register("possess_wand", () -> new PossessWandItem(props()));
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

    /** Отдельная вкладка «Магия» — всё содержимое мода в порядке регистрации. */
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.f_279569_, "explosionwand");
    public static final RegistryObject<CreativeModeTab> MAGIC_TAB = TABS.register("magic", () -> CreativeModeTab.builder()
            .m_257941_(Component.m_237115_("itemGroup.explosionwand.magic"))
            .m_257737_(() -> new ItemStack(ECLIPSE_STAFF.get()))
            .m_257501_((params, out) -> { for (RegistryObject<Item> r : ITEMS.getEntries()) out.m_246326_(r.get()); })
            .m_257652_());

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
        MinecraftForge.EVENT_BUS.addListener(ArcaneSet::onHurt);
    }
}
