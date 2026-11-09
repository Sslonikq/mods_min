package com.explosionwand;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Блоки Ада (5.1): набор зарегистрированных ID совпадает с таблицей docs/hell_blocks.md; у каждого есть ресурсы; логика роста и гейзера. */
public class HellBlocksTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    public static void main(String[] a) throws Exception {
        // ID из таблицы документа: строки "| `id` | ... |"
        Set<String> doc = new LinkedHashSet<>();
        Matcher m = Pattern.compile("^\\| `([a-z_]+)` \\|", Pattern.MULTILINE).matcher(Files.readString(Path.of("docs/hell_blocks.md")));
        while (m.find()) doc.add(m.group(1));
        check(doc.size() == 34, "в docs/hell_blocks.md 34 блока, найдено " + doc.size());

        // регистрация через подменные реестры: имена фиксируем, поставщиков не вызываем (в тесте нет игры)
        List<String> blocks = new ArrayList<>(), items = new ArrayList<>();
        DeferredRegister<Block> br = new DeferredRegister<Block>() {
            @Override public <I extends Block> RegistryObject<I> register(String name, Supplier<? extends I> sup) { blocks.add(name); return null; }
        };
        DeferredRegister<Item> ir = new DeferredRegister<Item>() {
            @Override public <I extends Item> RegistryObject<I> register(String name, Supplier<? extends I> sup) { items.add(name); return null; }
        };
        HellBlocks.register(br, ir);
        check(new LinkedHashSet<>(blocks).equals(doc) && blocks.size() == doc.size(), "блоки: набор и порядок = таблица документа; лишнее/недостающее: " + blocks);
        check(blocks.equals(HellBlocks.ids()), "ids() совпадает с регистрацией");
        Set<String> wantItems = new LinkedHashSet<>(doc); wantItems.remove("coal_berry_bush"); wantItems.add("coal_berries");
        check(new LinkedHashSet<>(items).equals(wantItems) && items.size() == wantItems.size(), "предметы: BlockItem на каждый блок + coal_berries: " + items);

        // у каждого блока есть ресурсы (готовит asset-engineer), без них игра уронит загрузку/покажет фиолетовое
        String ru = Files.readString(Path.of("resources/assets/explosionwand/lang/ru_ru.json")), en = Files.readString(Path.of("resources/assets/explosionwand/lang/en_us.json"));
        for (String id : wantItems) {
            boolean berries = id.equals("coal_berries");
            if (!berries) {
                check(Files.exists(Path.of("resources/assets/explosionwand/blockstates/" + id + ".json")), "blockstate " + id);
                check(Files.exists(Path.of("resources/data/explosionwand/loot_tables/blocks/" + id + ".json")), "loot " + id);
                check(ru.contains("\"block.explosionwand." + id + "\"") && en.contains("\"block.explosionwand." + id + "\""), "lang block " + id);
            } else check(ru.contains("\"item.explosionwand.coal_berries\"") && en.contains("\"item.explosionwand.coal_berries\""), "lang item coal_berries");
            check(Files.exists(Path.of("resources/assets/explosionwand/models/item/" + id + ".json")), "item model " + id);
        }

        // лут куста ссылается на предмет, который мы регистрируем
        check(Files.readString(Path.of("resources/data/explosionwand/loot_tables/blocks/coal_berry_bush.json")).contains("explosionwand:" + HellBlocks.BERRIES_ID), "лут куста даёт coal_berries");

        // куст: age 2 -> 1..2, age 3 -> 2..3, age 0..1 -> ничего
        check(CoalBerryBushBlock.berries(0, 1) == 0 && CoalBerryBushBlock.berries(1, 1) == 0, "молодой куст пуст");
        check(CoalBerryBushBlock.berries(2, 0) == 1 && CoalBerryBushBlock.berries(2, 1) == 2 && CoalBerryBushBlock.berries(3, 0) == 2 && CoalBerryBushBlock.berries(3, 1) == 3, "урожай куста");
        // друза: свет 4/6/8/10
        check(EclipseClusterBlock.light(0) == 4 && EclipseClusterBlock.light(1) == 6 && EclipseClusterBlock.light(2) == 8 && EclipseClusterBlock.light(3) == 10, "свет друзы по age");

        // гейзер: фаза 0..7, у соседних гейзеров разный сдвиг, ровно одно извержение (фаза 0) и одно предупреждение (фаза 7) за 8 секунд
        net.minecraft.core.BlockPos p1 = new net.minecraft.core.BlockPos(10, 60, 10), p2 = new net.minecraft.core.BlockPos(11, 60, 10);
        int erupt = 0, warn = 0;
        for (long t = 0; t < 160; t += 20) {
            int ph = HellBlock.geyserPhase(1000 + t, p1);
            check(ph >= 0 && ph < 8, "фаза в 0..7");
            if (ph == 0) erupt++;
            if (ph == 7) warn++;
        }
        check(erupt == 1 && warn == 1, "гейзер: одно извержение и одно предупреждение за цикл, было " + erupt + "/" + warn);
        check(HellBlock.geyserPhase(1000, p1) != HellBlock.geyserPhase(1000, p2), "соседние гейзеры не бьют синхронно");
        check(HellBlock.GEYSER_PERIOD == 8 && HellBlock.GEYSER_REACH == 7, "период 8 с, столб до 7 блоков");
    }
}
