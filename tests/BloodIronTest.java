package com.explosionwand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Руды Ада и инструменты из кровавого железа (5.2): ступень между алмазом и незеритом, ресурсы и lang для каждого нового предмета. */
public class BloodIronTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    public static void main(String[] a) throws Exception {
        check(BloodIron.USES > 1561 && BloodIron.USES < 2031, "прочнее алмаза, слабее незерита");
        check(BloodIron.SPEED > 8.0F && BloodIron.SPEED < 9.0F && BloodIron.DAMAGE > 3.0F && BloodIron.DAMAGE < 4.0F && BloodIron.ENCHANT > 10 && BloodIron.ENCHANT < 15, "скорость, урон, зачаровываемость между алмазом и незеритом");
        check(BloodIron.LEVEL == 3, "уровень добычи как у алмаза (блоки needs_diamond_tool берутся)");
        check(BloodIron.TIER.m_6609_() == BloodIron.USES && BloodIron.TIER.m_6604_() == 3, "Tier отдаёт константы");
        check(BloodIron.HELL_BONUS > 1.0F && BloodIron.speed(1.0F, null) == 1.0F, "бонус только там, где инструмент вообще ускорен (на неподходящем блоке 1.0 остаётся 1.0)");

        List<String> ids = List.of("raw_blood_iron", "blood_iron_ingot", "crown_gem", "eclipse_shard", "blood_iron_pickaxe", "blood_iron_axe", "blood_iron_shovel", "blood_iron_hoe", "blood_iron_sword");
        String mod = Files.readString(Path.of("src/com/explosionwand/ExplosionWandMod.java"));
        String ru = Files.readString(Path.of("resources/assets/explosionwand/lang/ru_ru.json")), en = Files.readString(Path.of("resources/assets/explosionwand/lang/en_us.json"));
        String jei = Files.readString(Path.of("src/com/explosionwand/jei/ExplosionWandJei.java"));
        for (String id : ids) {
            check(mod.contains("ITEMS.register(\"" + id + "\""), "зарегистрирован " + id);
            check(jei.contains("\"" + id + "\""), "страница JEI " + id);
            check(Files.exists(Path.of("resources/assets/explosionwand/models/item/" + id + ".json")), "модель " + id);
            check(Files.exists(Path.of("resources/assets/explosionwand/textures/item/" + id + ".png")), "текстура " + id);
            for (String lang : new String[]{ru, en}) {
                check(lang.contains("\"item.explosionwand." + id + "\""), "имя " + id);
                check(lang.contains("\"explosionwand.jei." + id + "\""), "текст JEI " + id);
            }
        }
        for (String id : new String[]{"raw_blood_iron", "blood_iron_ingot", "crown_gem", "eclipse_shard"})
            check(ru.contains("\"item.explosionwand." + id + ".desc\"") && en.contains("\"item.explosionwand." + id + ".desc\""), "подсказка материала " + id);
        // лут руд ссылается на наши предметы (а не на ванильное железо)
        String ore = Files.readString(Path.of("resources/data/explosionwand/loot_tables/blocks/blood_iron_ore.json"));
        check(ore.contains("explosionwand:raw_blood_iron") && !ore.contains("minecraft:raw_iron"), "руда даёт сырое кровавое железо");
        check(Files.readString(Path.of("resources/data/explosionwand/loot_tables/blocks/crown_gem_ore.json")).contains("explosionwand:crown_gem\""), "руда самоцвета даёт самоцвет");
        check(Files.readString(Path.of("resources/data/explosionwand/loot_tables/blocks/eclipse_cluster.json")).contains("explosionwand:eclipse_shard"), "друза даёт осколки");
        System.out.println("BloodIronTest OK");
    }
}
