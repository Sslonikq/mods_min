package com.explosionwand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/** Материалы данжей (4.11): шансы, состав сундуков, согласованность регистрации и рецептов. */
public class MatsTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    public static void main(String[] a) throws Exception {
        Random r = new Random(7);
        int n = 20000, none = 0, one = 0, two = 0, sMin = 9, sMax = 0, bug = 0;
        for (int i = 0; i < n; i++) {
            int c = Mats.commonCount(false, false, r);
            if (c == 0) none++; else if (c == 1) one++; else if (c == 2) two++; else check(false, "обычный моб: не больше 2");
            int s = Mats.commonCount(true, false, r); sMin = Math.min(sMin, s); sMax = Math.max(sMax, s);
            int b = Mats.commonCount(false, true, r); check(b <= 1, "жук: не больше 1"); bug += b;
        }
        check(Math.abs(none / (double) n - 0.4) < 0.03 && Math.abs(one / (double) n - 0.5) < 0.03 && Math.abs(two / (double) n - 0.1) < 0.02, "шансы обычного моба 40/50/10");
        check(sMin == 2 && sMax == 3, "сильный моб: 2-3");
        check(Math.abs(bug / (double) n - 0.5) < 0.03, "жук 50%");

        for (Mats.Dungeon d : Mats.Dungeon.values()) {
            for (int tier = 1; tier <= 3; tier++) {
                boolean veryRare = false;
                for (int i = 0; i < 300; i++) {
                    List<String> l = Mats.chestLoot(d, tier, r);
                    boolean vr = l.stream().anyMatch(s -> s.endsWith(Mats.id(d, 2)));
                    check(l.stream().anyMatch(s -> s.endsWith(Mats.id(d, 0))), "сундук всегда с обычным");
                    check(tier >= 3 || !vr, "очень редкий только в секретных (тир 3): " + d + " тир " + tier);
                    if (tier == 3) check(vr && l.stream().anyMatch(s -> s.endsWith(Mats.id(d, 1))), "секретный: очень редкий и редкие");
                    if (tier == 2) check(l.stream().anyMatch(s -> s.endsWith(Mats.id(d, 1))), "логово: редкие всегда");
                    for (String s : l) check(s.matches("[0-9]+ explosionwand:[a-z_]+"), "формат строки Loot.chest: " + s);
                }
            }
        }
        check(Mats.chestLoot(null, 3, r).isEmpty(), "данж без материалов (стенд механик): пусто");

        // каждое имя материала зарегистрировано в ExplosionWandMod, у него есть модель, текстура и записи lang
        String mod = Files.readString(Path.of("src/com/explosionwand/ExplosionWandMod.java"));
        String ru = Files.readString(Path.of("resources/assets/explosionwand/lang/ru_ru.json"));
        String en = Files.readString(Path.of("resources/assets/explosionwand/lang/en_us.json"));
        List<String> all = new java.util.ArrayList<>(List.of("magic_rod", "simple_core", "crystal_core", "wind_core", "fire_core"));
        for (Mats.Dungeon d : Mats.Dungeon.values()) for (String s : d.names) all.add(s);
        check(all.size() == 14, "14 предметов");
        for (String s : all) {
            check(mod.contains("ITEMS.register(\"" + s + "\""), "не зарегистрирован: " + s);
            check(Files.exists(Path.of("resources/assets/explosionwand/models/item/" + s + ".json")), "нет модели: " + s);
            check(Files.exists(Path.of("resources/assets/explosionwand/textures/item/" + s + ".png")), "нет текстуры: " + s);
            for (String l : new String[]{ru, en})
                check(l.contains("\"item.explosionwand." + s + "\"") && l.contains("\"explosionwand.jei." + s + "\""), "нет lang: " + s);
        }
        // рецепты ядер ссылаются на существующие материалы; старых рецептов палочек нет
        for (String s : List.of("magic_rod", "simple_core", "crystal_core", "wind_core", "fire_core"))
            check(Files.exists(Path.of("resources/data/explosionwand/recipes/" + s + ".json")), "нет рецепта: " + s);
        for (String s : List.of("black_hole_wand", "ice_storm_wand", "rift_wand", "chronos_wand", "mimic_wand", "tesseract_wand", "fist_wand",
                "timestop_wand", "comet_wand", "void_wand", "haunt_wand", "inverted_wand", "portal_gun", "possess_wand"))
            check(!Files.exists(Path.of("resources/data/explosionwand/recipes/" + s + ".json")), "старый рецепт остался: " + s);
        check(!mod.contains("possess_wand"), "Вселение убрано");
        System.out.println("MatsTest OK");
    }
}
