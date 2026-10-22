package com.explosionwand;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Материалы данжей (4.11): три данжа x три редкости.
 *   обычный (0)      - с мобов своего данжа (RiftMobs.onDeath -> onMobDeath);
 *   редкий (1)       - сундуки побочных комнат, залов и логов (тиры 1-2 в Dungeons.LOOT-метке сундука);
 *   очень редкий (2) - сундуки секретных комнат (тир 3).
 * Уникальные трофеи боссов (Ядро Разлома, Око, Перо) сюда не входят.
 *
 * ХУКИ ДЛЯ СЛЕДУЮЩИХ ФАЗ:
 *   - мобы Пепельного святилища (4.9): добавить их виды в {@link #dungeonOf} (-> Dungeon.ASH) и {@link #strong}; дроп сработает сам
 *     через RiftMobs.onDeath, если мобы - наследники RiftMobs.Mob. Другой путь - вызвать {@link #drop} напрямую.
 *   - сундуки Святилища (4.8): готово, {@link #dungeonOf(Dungeons.Def)} знает Dungeons.ASH (Dungeon.ASH), тиры 1/2/3 как у остальных.
 */
final class Mats {
    enum Dungeon {
        RIFT("rift_dust", "echo_crystal", "rift_marrow"),
        SLEEP("sleep_thread", "teal_stitch", "sleeper_tear"),
        ASH("ash_coal", "smoldering_feather", "phoenix_spark");
        final String[] names;
        Dungeon(String... n) { names = n; }
    }

    static final Random RNG = new Random();

    /** Имя предмета (без пространства имён): rarity 0 обычный, 1 редкий, 2 очень редкий. */
    static String name(Dungeon d, int rarity) { return d.names[rarity]; }

    /** ID предмета для команд и Loot.chest. */
    static String id(Dungeon d, int rarity) { return "explosionwand:" + d.names[rarity]; }

    // ================================================================ мобы
    /** К какому данжу относится моб (null - материалов не даёт: копии, мини-осколочники). */
    static Dungeon dungeonOf(RiftModels.Kind k) {
        switch (k) {
            case SHARDLING: case ARCHER: case SHADE: case PRISM: case EATER: case GOLEM: case BEETLE:
                return Dungeon.RIFT;
            case LUNATIC: case DROWSY: case STITCH_GUARD: case NIGHT_MOTH: case DREAMER: case BOOK_GUARD: case STATUE_GUARD: case CRADLER: case FOG_WALKER:
                return Dungeon.SLEEP;
            case ASH_WALKER: case ASH_SPARK: case ASH_HOUND: case ASH_RINGER: case ASH_WORM: case ASH_PRIEST: case ASH_GUARD: case ASH_KEEPER:
                return Dungeon.ASH;
            default:
                return null;   // SHARDLING_MINI, DREAM_CLONE, ASH_WISP
        }
    }

    /** Сильный моб (голем, колыбельщик, стражи): дроп щедрее. */
    static boolean strong(RiftModels.Kind k) {
        switch (k) {
            case EATER: case GOLEM: case STITCH_GUARD: case STATUE_GUARD: case CRADLER: case ASH_GUARD: case ASH_KEEPER: return true;
            default: return false;
        }
    }

    /** Сколько обычных материалов с моба: обычный - 0 (40%), 1 (50%), 2 (10%); сильный - 2, иногда 3; мирный жук - 0 или 1. */
    static int commonCount(boolean strong, boolean peaceful, Random r) {
        double x = r.nextDouble();
        if (peaceful) return x < 0.5 ? 1 : 0;
        if (strong) return x < 0.5 ? 3 : 2;
        return x < 0.4 ? 0 : x < 0.9 ? 1 : 2;
    }

    /** Смерть моба (от игрока): бросает обычный материал данжа. */
    static void onMobDeath(ServerLevel level, RiftModels.Kind k, double x, double y, double z) {
        Dungeon d = dungeonOf(k);
        if (d == null) return;
        drop(level, d, 0, commonCount(strong(k), k == RiftModels.Kind.BEETLE, RNG), x, y, z);
    }

    /** Бросить count предметов материала на землю (общий хук: можно звать и из мобов Святилища). */
    static void drop(ServerLevel level, Dungeon d, int rarity, int count, double x, double y, double z) {
        if (count <= 0) return;
        Object it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("explosionwand", name(d, rarity)));
        if (it instanceof Item item) level.m_7967_(new ItemEntity(level, x, y + 0.5, z, new ItemStack(item, count)));
    }

    // ================================================================ сундуки
    /** Данж по описателю (null - у этого данжа материалов нет). */
    static Dungeon dungeonOf(Dungeons.Def d) {
        if (d == Dungeons.RIFT) return Dungeon.RIFT;
        if (d == Dungeons.SLEEP) return Dungeon.SLEEP;
        if (d == Dungeons.ASH) return Dungeon.ASH;
        return null;
    }

    /**
     * Материалы в сундук: тир 1 (побочные комнаты, залы) - редкий 0 (35%), 1 (50%) или 2 (15%); тир 2 (логова, склады) - редких 2, иногда 3;
     * тир 3 (секретные комнаты) - 1 очень редкий (25%: два) и 2 редких. Сундуки всех тиров дают ещё немного обычного (1-3), чтобы
     * не ходить за ядром только по мобам.
     */
    static List<String> chestLoot(Dungeon d, int tier, Random r) {
        List<String> out = new ArrayList<>();
        if (d == null) return out;
        double x = r.nextDouble();
        int rare = tier >= 3 ? 2 : tier == 2 ? (x < 0.5 ? 3 : 2) : (x < 0.35 ? 0 : x < 0.85 ? 1 : 2);
        if (rare > 0) out.add(rare + " " + id(d, 1));
        if (tier >= 3) out.add((r.nextDouble() < 0.25 ? 2 : 1) + " " + id(d, 2));
        out.add((1 + r.nextInt(3)) + " " + id(d, 0));
        return out;
    }
}
