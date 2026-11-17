package com.explosionwand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Перчатка Бесконечности (порядок камней, Щелчок) и Парашют (форма купола), согласованность регистрации и ресурсов. */
public class GauntletTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    public static void main(String[] a) throws Exception {
        check(GauntletLogic.count() == 7, "камней должно быть 7");
        check(GauntletLogic.COOLDOWN.length == 7 && GauntletLogic.COLOR.length == 7, "таблицы камней разной длины");
        check(GauntletLogic.key(0).equals("rift") && GauntletLogic.key(6).equals("snap"), "порядок камней");
        int i = 3;
        Set<Integer> seen = new HashSet<>();
        for (int k = 0; k < 7; k++) { seen.add(i); i = GauntletLogic.next(i); }
        check(seen.size() == 7 && i == 3, "переключение не замыкается в круг");
        check(GauntletLogic.next(6) == 0, "после snap идёт rift");
        check(GauntletLogic.clamp(-5) == 0 && GauntletLogic.clamp(99) == 0, "мусор в NBT -> 0");
        check(GauntletLogic.cooldown(6) == 1200 && GauntletLogic.cooldown(0) == 20, "перезарядки");
        check(GauntletLogic.cdKey(3).equals("ewCd3") && GauntletLogic.cdKey(-1).equals("ewCd0"), "ключ перезарядки");
        for (int st = 0; st < 7; st++) {
            long now = 1_000_000L + st;
            long ready = GauntletLogic.readyAt(now, st);
            check(GauntletLogic.left(ready, now, st) == GauntletLogic.cooldown(st), "остаток сразу после применения " + st);
            check(GauntletLogic.left(ready, ready - 5, st) == 5 && GauntletLogic.left(ready, ready, st) == 0 && GauntletLogic.left(ready, ready + 99, st) == 0, "остаток по ходу " + st);
            check(GauntletLogic.left(ready, now - 100_000L, st) == 0, "время откатилось - камень готов " + st);
        }
        check(GauntletLogic.secs(0) == 0 && GauntletLogic.secs(1) == 1 && GauntletLogic.secs(20) == 1 && GauntletLogic.secs(21) == 2 && GauntletLogic.secs(1200) == 60, "секунды");
        check(GauntletLogic.left(0L, 5000L, 6) == 0, "старая метка - готов");
        for (String b : new String[]{"wither", "ender_dragon", "warden", "elder_guardian"}) check(GauntletLogic.snapSpared(b), "боссов Щелчок щадит " + b);
        check(!GauntletLogic.snapSpared("zombie") && !GauntletLogic.snapSpared("wither_skeleton") && !GauntletLogic.snapSpared("guardian"), "обычных мобов Щелчок берёт");
        // перезарядки по камням
        check(GauntletLogic.cdKey(3).equals("ewCd3") && GauntletLogic.readyAt(1000, 6) == 2200, "readyAt/cdKey");
        check(GauntletLogic.left(2200, 1000, 6) == 1200 && GauntletLogic.left(2200, 2199, 6) == 1 && GauntletLogic.left(2200, 2200, 6) == 0, "left");
        check(GauntletLogic.left(2200, 5000, 6) == 0 && GauntletLogic.left(9999999L, 1000, 6) == 0, "left: прошло / время откатилось");
        check(GauntletLogic.secs(0) == 0 && GauntletLogic.secs(1) == 1 && GauntletLogic.secs(20) == 1 && GauntletLogic.secs(21) == 2 && GauntletLogic.secs(1200) == 60, "secs");
        check(GauntletLogic.ITEM_COOLDOWN > 0 && GauntletLogic.ITEM_COOLDOWN <= 20, "общая пауза короткая");
        for (String b : new String[]{"wither", "ender_dragon", "warden", "elder_guardian"}) check(GauntletLogic.snapSpared(b), "Щелчок щадит " + b);
        check(!GauntletLogic.snapSpared("zombie") && !GauntletLogic.snapSpared("creeper"), "Щелчок бьёт обычных");
        int[] want = {0, 1, 1, 2, 2, 3, 3, 4, 4, 5};
        for (int n = 0; n < want.length; n++) check(GauntletLogic.half(n) == want[n], "половина " + n);
        for (int n = 0; n < 40; n++) {
            List<Integer> all = new ArrayList<>();
            for (int k = 0; k < n; k++) all.add(k);
            List<Integer> pick = GauntletLogic.pickHalf(all, new Random(n));
            check(pick.size() == GauntletLogic.half(n), "размер выбора " + n);
            check(new HashSet<>(pick).size() == pick.size() && all.size() == n, "повторы или порча исходного списка " + n);
        }

        List<String[]> shape = Parachute.shape();
        check(shape.size() == 8 + 8 + 1 + 4, "частей купола " + shape.size());
        for (String[] s : shape) {
            check(s[0].matches("[a-z_]+") && s[1].startsWith("transformation:{"), "часть купола: " + Arrays.toString(s));
            check(!s[1].contains("NaN") && !s[1].contains("Infinity"), "NaN в трансформации: " + s[1]);
        }

        String mod = Files.readString(Path.of("src/com/explosionwand/ExplosionWandMod.java"));
        check(mod.contains("ITEMS.register(\"infinity_gauntlet\"") && mod.contains("ITEMS.register(\"parachute\""), "предметы не зарегистрированы");
        String tick = Files.readString(Path.of("src/com/explosionwand/Ticker.java"));
        check(tick.contains("Parachute.ACTIVE") && tick.contains("Gauntlet.tick"), "Ticker не тикает");
        for (String id : new String[]{"infinity_gauntlet", "parachute"}) {
            check(Files.exists(Path.of("resources/assets/explosionwand/models/item/" + id + ".json")), "нет модели " + id);
            check(Files.exists(Path.of("resources/assets/explosionwand/textures/item/" + id + ".png")), "нет текстуры " + id);
        }
        check(!Files.exists(Path.of("resources/data/explosionwand/recipes/infinity_gauntlet.json")), "перчатка без рецепта");
        List<String> keys = new ArrayList<>(List.of("item.explosionwand.infinity_gauntlet", "item.explosionwand.infinity_gauntlet.desc1", "item.explosionwand.infinity_gauntlet.desc2",
                "explosionwand.gauntlet.current", "explosionwand.gauntlet.cooldown", "explosionwand.gauntlet.cooldown", "explosionwand.jei.infinity_gauntlet", "item.explosionwand.parachute", "item.explosionwand.parachute.desc1",
                "item.explosionwand.parachute.desc2", "explosionwand.parachute.air", "explosionwand.jei.parachute"));
        for (String k : GauntletLogic.KEYS) keys.add("explosionwand.gauntlet.stone." + k);
        for (String f : new String[]{"ru_ru.json", "en_us.json"}) {
            String l = Files.readString(Path.of("resources/assets/explosionwand/lang/" + f));
            for (String k : keys) check(l.contains("\"" + k + "\""), "нет lang " + f + " " + k);
        }
        System.out.println("GauntletTest OK: камней " + GauntletLogic.count() + ", частей купола " + shape.size());
    }
}
