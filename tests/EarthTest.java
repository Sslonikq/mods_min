package com.explosionwand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Палочки Земли (4.11, часть B): геометрия тоннеля, конус, каст Возврата, шансы, согласованность регистрации и ресурсов. */
public class EarthTest {
    static final Set<String> STARTERS = Set.of("light_wand", "frost_wand", "growth_wand", "gather_wand", "return_wand", "signal_wand");
    static final Set<String> PART_C = Set.of("crystal_spikes_wand", "stone_skin_wand", "wind_wand", "vine_wand", "pull_wand", "decoy_wand", "spark_wand", "heat_wand");

    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    public static void main(String[] a) throws Exception {
        // оси
        check(EarthLogic.axis(1, 0.2, 0.1) == 0 && EarthLogic.axis(-1, 0, 0) == 1, "ось x");
        check(EarthLogic.axis(0.1, 0.9, 0.2) == 2 && EarthLogic.axis(0.1, -0.9, 0.2) == 3, "вертикаль");
        check(EarthLogic.axis(0.1, 0.1, -1) == 5 && EarthLogic.axis(0, 0, 1) == 4, "ось z");
        check(EarthLogic.axis(0.7, 0.7, 0.0) == 0, "ничья: горизонталь");

        // тоннель: 9 уникальных клеток в срезе, 8 срезов, поперёк оси не дальше 1, вдоль оси - номер среза
        int[] o = {10, 20, 30};
        for (int axis = 0; axis < 6; axis++) {
            List<int[]> t = EarthLogic.tunnel(o[0], o[1], o[2], axis, EarthLogic.DIG_DEPTH);
            check(t.size() == 9 * EarthLogic.DIG_DEPTH, "размер тоннеля, ось " + axis);
            Set<String> u = new HashSet<>();
            int[] d = EarthLogic.dir(axis);
            for (int i = 0; i < t.size(); i++) {
                int[] c = t.get(i);
                check(u.add(c[0] + "," + c[1] + "," + c[2]), "клетки не повторяются, ось " + axis);
                int slice = i / 9;
                for (int k = 0; k < 3; k++) {
                    int off = c[k] - o[k];
                    if (d[k] == 0) check(Math.abs(off) <= 1, "поперёк оси не больше 1, ось " + axis);
                    else check(off * d[k] == slice, "вдоль оси - номер среза, ось " + axis);
                }
            }
        }

        // конус Морозца
        check(EarthLogic.inCone(5, 0, 0, 1, 0, 0, 9, 0.75), "прямо впереди");
        check(!EarthLogic.inCone(-5, 0, 0, 1, 0, 0, 9, 0.75), "сзади нет");
        check(!EarthLogic.inCone(10, 0, 0, 1, 0, 0, 9, 0.75), "дальше радиуса нет");
        check(!EarthLogic.inCone(2, 5, 0, 1, 0, 0, 9, 0.75), "сбоку нет");
        check(EarthLogic.inCone(0, 0, 0, 1, 0, 0, 9, 0.75), "вплотную есть");

        // каст Возврата
        check(!EarthLogic.moved(0.3, 0, 0.3) && EarthLogic.moved(0.7, 0, 0.1) && EarthLogic.moved(0, 1.5, 0), "прерывание движением");
        check(EarthLogic.hurt(20, 18) && !EarthLogic.hurt(20, 20) && !EarthLogic.hurt(18, 20), "прерывание уроном");
        check(EarthLogic.CAST_TICKS == 100 && EarthLogic.Kind.RETURN.cd == 12000, "5 с каст, 10 мин перезарядка");
        check(EarthLogic.cooldown(12000, 0.7) == 8400 && EarthLogic.cooldown(100, 1.0) == 100, "множитель ArcaneSet");

        // Взрывная: 2-4 блока, слабее динамита
        Random r = new Random(3);
        int[] cnt = new int[5];
        for (int i = 0; i < 3000; i++) { int n = EarthLogic.blocksToBreak(r); check(n >= 2 && n <= 4, "2-4 блока"); cnt[n]++; }
        check(cnt[2] > 0 && cnt[3] > 0 && cnt[4] > 0, "все значения встречаются");
        check(EarthLogic.BLAST_POWER < 4.0F, "взрыв слабее динамита");

        // растущее
        check(EarthLogic.growable("wheat") && EarthLogic.growable("oak_sapling") && EarthLogic.growable("cherry_sapling"), "растёт");
        check(!EarthLogic.growable("stone") && !EarthLogic.growable("grass_block") && !EarthLogic.growable(null), "не растёт");

        // данжи: Возврат и Копатель молчат, остальные нет
        for (EarthLogic.Kind k : EarthLogic.Kind.values())
            check(k.noDungeon == (k == EarthLogic.Kind.RETURN || k == EarthLogic.Kind.DIGGER), "noDungeon: " + k);

        // ---- часть C
        check(EarthLogic.Kind.values().length == 15, "15 палочек Земли (без Взрывной)");
        // шипы: линия идёт вдоль направления, клетки идут по порядку
        double[] hz = EarthLogic.horizontal(0.0, -0.7);
        check(hz[0] == 0.0 && hz[1] == -1.0, "горизонталь из взгляда");
        double[] up = EarthLogic.horizontal(0.0, 0.01);
        check(Math.abs(up[0] * up[0] + up[1] * up[1] - 1.0) < 1e-9, "вертикальный взгляд: единичный вектор");
        int[] prev = EarthLogic.spikeCell(10.5, 20.5, 1, 0, 1);
        check(prev[0] == 11 && prev[1] == 20, "шип 1 вперёд по x");
        for (int i = 2; i <= EarthLogic.SPIKE_LEN; i++) { int[] c = EarthLogic.spikeCell(10.5, 20.5, 1, 0, i); check(c[0] == prev[0] + 1 && c[1] == 20, "шипы идут подряд"); prev = c; }
        check(EarthLogic.spikeCell(0.5, 0.5, 0, -1, 3)[1] == -3, "шипы по -z");
        check(EarthLogic.SPIKE_LEN == 10 && EarthLogic.SPIKE_DAMAGE > 0, "параметры шипов");
        // кожа: ровно вдвое меньше, срок 6 секунд
        check(EarthLogic.skinned(10.0F) == 5.0F && EarthLogic.SKIN_TICKS == 120, "кожа: -50%, 6 с");
        // ветер: отбрасывание вдоль взгляда с подъёмом, длина совпадает
        double[] wv = EarthLogic.windVel(3, 4);
        check(Math.abs(wv[0] - 0.6 * EarthLogic.WIND_PUSH) < 1e-9 && Math.abs(wv[2] - 0.8 * EarthLogic.WIND_PUSH) < 1e-9 && wv[1] > 0, "ветер: направление");
        check(EarthLogic.WIND_SELF_LIFT > 0.8, "подброс себя");
        // лоза: круг
        check(EarthLogic.inCircle(3, 0, 3, 4.5, 3) && !EarthLogic.inCircle(4, 0, 4, 4.5, 3) && !EarthLogic.inCircle(1, 5, 1, 4.5, 3), "круг лозы");
        check(EarthLogic.VINE_TICKS == 60, "лоза 3 с");
        // притяжение: расстояние вдоль и поперёк луча, скорость ограничена
        double[] ray = EarthLogic.toRay(10, 1, 0, 1, 0, 0);
        check(Math.abs(ray[0] - 10) < 1e-9 && Math.abs(ray[1] - 1) < 1e-9, "луч притяжения");
        check(EarthLogic.pullSpeed(1) < EarthLogic.pullSpeed(10) && EarthLogic.pullSpeed(1000) == 1.6D, "скорость притяжения растёт и ограничена");
        // двойник: 5 секунд
        check(EarthLogic.DECOY_TICKS == 100 && EarthLogic.DECOY_R >= 8, "двойник 5 с");
        // искры: длина скорости сохраняется, поворот к цели, сходимость
        double[] sv = {1, 0, 0};
        double[] to = {0, 0, 10};
        double[] n1 = EarthLogic.steer(sv, to, EarthLogic.SPARK_TURN, EarthLogic.SPARK_SPEED);
        check(Math.abs(Math.sqrt(n1[0] * n1[0] + n1[1] * n1[1] + n1[2] * n1[2]) - EarthLogic.SPARK_SPEED) < 1e-9, "скорость искры постоянна");
        check(n1[2] > 0 && n1[0] > 0, "искра поворачивает к цели");
        double[] pos = {0, 0, 0}, vel = {EarthLogic.SPARK_SPEED, 0, 0};
        boolean reached = false;
        for (int i = 0; i < EarthLogic.SPARK_STEPS * 2 && !reached; i++) {
            vel = EarthLogic.steer(vel, new double[]{5 - pos[0], 0, 5 - pos[2]}, EarthLogic.SPARK_TURN, EarthLogic.SPARK_SPEED);
            for (int k = 0; k < 3; k++) pos[k] += vel[k];
            reached = Math.hypot(5 - pos[0], 5 - pos[2]) < EarthLogic.SPARK_HIT;
        }
        check(reached, "искра долетает до цели сбоку за отведённое время");
        double[] zero = EarthLogic.steer(new double[]{0, 0, 0}, new double[]{0, 0, 0}, 0.5, 1.0);
        check(zero[0] == 0 && zero[1] == 0 && zero[2] == 0, "нулевой вектор не даёт NaN");
        double[] fn = EarthLogic.fan(1, 0, 0, 90);
        check(Math.abs(fn[0]) < 1e-9 && Math.abs(fn[2] - 1) < 1e-9, "веер: поворот на 90");
        check(EarthLogic.SPARK_COUNT == 3 && EarthLogic.SPARK_FIRE_SECONDS > 0, "три искры, поджигают");
        // тепло
        check(EarthLogic.HEAT_HEAL >= 6 && EarthLogic.HEAT_REGEN_TICKS > 0, "тепло лечит");
        // кулдауны части C
        for (EarthLogic.Kind k : EarthLogic.Kind.values()) check(k.cd > 0, "перезарядка > 0: " + k);

        // регистрация, модели, текстуры, рецепты, lang, JEI
        String mod = Files.readString(Path.of("src/com/explosionwand/ExplosionWandMod.java"));
        String jei = Files.readString(Path.of("src/com/explosionwand/jei/ExplosionWandJei.java"));
        String ru = Files.readString(Path.of("resources/assets/explosionwand/lang/ru_ru.json"));
        String en = Files.readString(Path.of("resources/assets/explosionwand/lang/en_us.json"));
        String guard = Files.readString(Path.of("src/com/explosionwand/DungeonGuard.java"));
        check(guard.contains("EarthWandItem") && guard.contains("noDungeon"), "DungeonGuard.forbidden знает палочки Земли");
        check(guard.contains("static boolean teleports") && guard.contains("static boolean breaksBalance") && guard.contains("EarthLogic.Kind.RETURN"), "DungeonGuard делит запреты: телепорты всегда, баланс - до смерти босса");
        List<String> ids = new java.util.ArrayList<>();
        for (EarthLogic.Kind k : EarthLogic.Kind.values()) ids.add(k.id);
        ids.add("explosion_wand");
        for (String s : ids) {
            check(mod.contains("ITEMS.register(\"" + s + "\""), "не зарегистрирован: " + s);
            check(Files.exists(Path.of("resources/assets/explosionwand/models/item/" + s + ".json")), "нет модели: " + s);
            check(Files.exists(Path.of("resources/assets/explosionwand/textures/item/" + s + ".png")), "нет текстуры: " + s);
            check(Files.exists(Path.of("resources/data/explosionwand/recipes/" + s + ".json")), "нет рецепта: " + s);
            check(jei.contains("\"" + s + "\""), "нет страницы JEI: " + s);
            for (String l : new String[]{ru, en})
                for (String key : new String[]{"item.explosionwand." + s, "item.explosionwand." + s + ".desc1", "item.explosionwand." + s + ".desc2", "explosionwand.jei." + s})
                    check(l.contains("\"" + key + "\""), "нет lang " + key);
            String rec = Files.readString(Path.of("resources/data/explosionwand/recipes/" + s + ".json"));
            check(rec.contains("explosionwand:magic_rod"), "в рецепте нужен Магический стержень: " + s);
            if (STARTERS.contains(s)) check(rec.contains("explosionwand:simple_core"), "стартовая: Простое ядро: " + s);
            if (PART_C.contains(s)) {      // палочки данжей: ядро стихии + редкий материал данжа
                check(rec.contains("explosionwand:crystal_core") || rec.contains("explosionwand:wind_core") || rec.contains("explosionwand:fire_core"), "ядро стихии: " + s);
                check(!rec.contains("explosionwand:simple_core"), "палочка данжа без Простого ядра: " + s);
                check(rec.contains("echo_crystal") || rec.contains("rift_marrow") || rec.contains("teal_stitch") || rec.contains("sleeper_tear")
                        || rec.contains("smoldering_feather") || rec.contains("phoenix_spark"), "материал данжа в рецепте: " + s);
            }
        }
        String dig = Files.readString(Path.of("resources/data/explosionwand/recipes/digger_wand.json"));
        check(dig.contains("minecraft:diamond") && dig.contains("explosionwand:rift_dust"), "Копатель: алмазы и Пыль Разлома");
        String ex = Files.readString(Path.of("resources/data/explosionwand/recipes/explosion_wand.json"));
        check(ex.contains("explosionwand:crystal_core") && ex.contains("explosionwand:echo_crystal"), "Взрывная: Кристальное ядро и Кристалл эха");
        for (String k : new String[]{"no_pull_target", "no_room", "blocked", "nothing_grows", "no_target", "return_start", "return_moved", "return_hurt", "return_nobed", "return_blocked"})
            for (String l : new String[]{ru, en}) check(l.contains("\"explosionwand.earth." + k + "\""), "нет lang earth." + k);
        // орбитальный удар убран
        String item = Files.readString(Path.of("src/com/explosionwand/ExplosionWandItem.java"));
        check(!item.contains("orbital(") && !item.contains("new Ember"), "орбитальный удар и осколки убраны");
        System.out.println("EarthTest OK");
    }
}
