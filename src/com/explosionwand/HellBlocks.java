package com.explosionwand;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Блоки Ада (5.1): 34 блока из docs/hell_blocks.md, их BlockItem и еда `coal_berries`.
 * Таблица SPECS не трогает ни реестры, ни классы Minecraft (всё внутри лямбд), поэтому тест HellBlocksTest читает её без игры.
 * Регистрация - register(...) из конструктора мода, до BLOCKS.register(bus). Предметы идут после остальных (вкладка «Магия» берёт порядок регистрации).
 * Свойства блоков (axis, layers, age, facing) совпадают с blockstates из resources.
 */
final class HellBlocks {
    static final Direction UP = Direction.valueOf("UP"), DOWN = Direction.valueOf("DOWN");

    static final class Spec {
        final String id; final Supplier<Block> make;
        Spec(String id, Supplier<Block> make) { this.id = id; this.make = make; }
    }

    static final List<Spec> SPECS = new ArrayList<>();
    static final Map<String, RegistryObject<Block>> BLOCK = new LinkedHashMap<>();
    /** Углеягоды: еда, ставятся на землю как куст (ItemNameBlockItem). */
    static RegistryObject<Item> COAL_BERRIES;
    static final String BERRIES_ID = "coal_berries";

    static void add(String id, Supplier<Block> make) { SPECS.add(new Spec(id, make)); }

    // ---- заготовки свойств

    static BlockBehaviour.Properties base(float hard, float res, SoundType s) { return BlockBehaviour.Properties.m_284310_().m_60913_(hard, res).m_60918_(s); }
    /** Камень: нужна кирка для добычи (теги mineable/needs_*), иначе предмета не будет. */
    static BlockBehaviour.Properties rock(float hard, float res) { return BlockBehaviour.Properties.m_284310_().m_60913_(hard, res).m_60999_(); }
    /** Растение: без коллизии, ломается мгновенно, не скрывает соседние грани. */
    static BlockBehaviour.Properties plant(SoundType s) { return BlockBehaviour.Properties.m_284310_().m_60910_().m_60966_().m_60955_().m_60918_(s); }

    static {
        add("ash_soil", () -> new Block(base(0.5F, 0.5F, SoundType.f_56746_)));
        add("ash_layer", () -> new SnowLayerBlock(base(0.1F, 0.1F, SoundType.f_56746_)));
        add("charred_log", () -> new RotatedPillarBlock(base(2.0F, 2.0F, SoundType.f_56736_)));
        add("charred_planks", () -> new Block(base(2.0F, 3.0F, SoundType.f_56736_)));
        add("dry_grass", () -> new HellBlock(plant(SoundType.f_56740_), HellBlock.Kind.PLANT));
        add("ember_bloom", () -> new HellBlock(plant(SoundType.f_56740_).m_60953_(s -> 9), HellBlock.Kind.PLANT));
        add("coal_berry_bush", () -> new CoalBerryBushBlock(plant(SoundType.f_56757_).m_60977_()));
        add("petrified_bones", () -> new Block(base(1.5F, 1.5F, SoundType.f_56724_)));
        add("smoldering_log", () -> new RotatedPillarBlock(base(2.0F, 2.0F, SoundType.f_56736_).m_60953_(s -> 5)));
        add("smoldering_leaves", () -> new HellBlock(base(0.2F, 0.2F, SoundType.f_56740_).m_60955_().m_60977_().m_60953_(s -> 9), HellBlock.Kind.LEAVES));
        add("smoldering_planks", () -> new Block(base(2.0F, 3.0F, SoundType.f_56736_)));
        add("fire_vine", () -> new HellBlock(plant(SoundType.f_56714_).m_60953_(s -> 7), HellBlock.Kind.VINE));
        add("coal_mushroom_cap", () -> new Block(base(0.2F, 0.2F, SoundType.f_56759_).m_60953_(s -> 10)));
        add("coal_mushroom_stem", () -> new Block(base(0.2F, 0.2F, SoundType.f_56759_)));
        add("ember_moss", () -> new CarpetBlock(base(0.1F, 0.1F, SoundType.f_56740_).m_60953_(s -> 4)));
        add("spark_fern", () -> new HellBlock(plant(SoundType.f_56740_).m_60953_(s -> 5).m_60977_(), HellBlock.Kind.SPARK_FERN));
        add("eclipse_stone", () -> new Block(rock(1.5F, 6.0F)));
        add("eclipse_cobblestone", () -> new Block(rock(2.0F, 6.0F)));
        add("eclipse_bricks", () -> new Block(rock(2.0F, 6.0F)));
        add("carved_eclipse_bricks", () -> new Block(rock(2.0F, 6.0F).m_60953_(s -> 7)));
        add("blazing_stone", () -> new HellBlock(rock(1.5F, 6.0F).m_60953_(s -> 12), HellBlock.Kind.BLAZING));
        add("dark_obsidian", () -> new Block(rock(50.0F, 1200.0F)));
        add("eclipse_crystal_block", () -> new Block(rock(1.5F, 6.0F).m_60918_(SoundType.f_154655_).m_60953_(s -> 12)));
        add("eclipse_cluster", () -> new EclipseClusterBlock(base(1.5F, 1.5F, SoundType.f_154655_).m_60955_().m_60977_().m_60953_(s -> EclipseClusterBlock.light(s.m_61143_(EclipseClusterBlock.AGE)))));
        add("slag", () -> new Block(rock(1.0F, 3.0F)));
        add("pumice", () -> new Block(rock(0.6F, 1.0F)));
        add("geyser", () -> new HellBlock(rock(2.0F, 6.0F).m_60977_(), HellBlock.Kind.GEYSER));
        add("fire_coral", () -> new HellBlock(plant(SoundType.f_56714_).m_60953_(s -> 8), HellBlock.Kind.CORAL));
        add("blood_iron_ore", () -> new DropExperienceBlock(rock(3.0F, 3.0F), UniformInt.m_146622_(1, 3)));
        add("crown_gem_ore", () -> new DropExperienceBlock(rock(3.0F, 3.0F).m_60953_(s -> 9), UniformInt.m_146622_(3, 7)));
        add("ghost_moss", () -> new CarpetBlock(base(0.1F, 0.1F, SoundType.f_56740_)));
        add("ghost_flower", () -> new HellBlock(plant(SoundType.f_56740_).m_60953_(s -> 7), HellBlock.Kind.GHOST_FLOWER));
        add("ghost_vine", () -> new HellBlock(plant(SoundType.f_56714_), HellBlock.Kind.VINE));
        add("archmage_stone", () -> new Block(rock(2.0F, 6.0F)));
    }

    /** Все ID блоков (они же ID предметов-блоков) в порядке регистрации. */
    static List<String> ids() {
        List<String> r = new ArrayList<>();
        for (Spec s : SPECS) r.add(s.id);
        return r;
    }

    /** Регистрация блоков и их BlockItem; углеягоды - последним предметом. Вызывать до BLOCKS.register(bus) / ITEMS.register(bus). */
    static void register(DeferredRegister<Block> blocks, DeferredRegister<Item> items) {
        for (Spec s : SPECS) {
            RegistryObject<Block> o = blocks.register(s.id, s.make);
            BLOCK.put(s.id, o);
            if (!s.id.equals("coal_berry_bush")) items.register(s.id, () -> new BlockItem(o.get(), new Item.Properties()));     // куст ставится углеягодами (ItemNameBlockItem)
        }
        COAL_BERRIES = items.register(BERRIES_ID, () -> new ItemNameBlockItem(get("coal_berry_bush"),
                new Item.Properties().m_41489_(new FoodProperties.Builder().m_38760_(3).m_38758_(0.4F).m_38767_())));
    }

    // ---- помощники для поведения блоков

    static Block get(String id) { return BLOCK.get(id).get(); }
    static BlockState state(String id) { return get(id).m_49966_(); }

    /** Состояние - наш блок с таким ID. */
    static boolean is(BlockState st, String id) {
        RegistryObject<Block> o = BLOCK.get(id);
        return o != null && st != null && st.m_60734_() == o.get();
    }

    /** Состояние - ванильный блок с таким ID. */
    static boolean isVanilla(BlockState st, String id) { return st != null && st.m_60734_() == Fx.block(id); }

    /** Частицы только игрокам в 48 блоках (в отличие от Fx.send: тиков у блоков много, рассылать всем по миру не нужно). */
    static void puff(ServerLevel l, ParticleOptions o, double x, double y, double z, int n, double dx, double dy, double dz, double speed) {
        if (o == null) return;
        for (ServerPlayer p : l.m_6907_()) if (p.m_20275_(x, y, z) < 48.0D * 48.0D) Fx.sendTo(l, p, o, x, y, z, n, dx, dy, dz, speed);
    }

    private HellBlocks() {}
}
