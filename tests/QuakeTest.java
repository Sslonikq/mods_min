package com.explosionwand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Палочка Землетрясения (3.5.0): форма трещин QuakeLogic, согласованность регистрации и ресурсов. */
public class QuakeTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    public static void main(String[] a) throws Exception {
        int minN = Integer.MAX_VALUE, maxN = 0;
        for (int seed = 0; seed < 300; seed++) {
            for (boolean pit : new boolean[]{true, false}) {
                Random r = new Random(seed);
                double base = r.nextDouble() * Math.PI * 2;
                List<QuakeLogic.Cell> cells = QuakeLogic.cracks(r, base, Quake.CRACKS, Quake.CRACK_LEN, pit);
                Set<Long> keys = new HashSet<>();
                int prev = -1, far = 0;
                for (QuakeLogic.Cell c : cells) {
                    check(keys.add(QuakeLogic.key(c.x, c.z)), "повтор клетки, seed " + seed);
                    check(c.tick >= prev, "клетки не по порядку раскрытия, seed " + seed);
                    prev = c.tick;
                    check(c.depth >= 2 && c.depth <= QuakeLogic.MAX_DEPTH, "глубина " + c.depth);
                    check(Math.abs(c.x) <= Quake.CRACK_LEN + 6 && Math.abs(c.z) <= Quake.CRACK_LEN + 6, "трещина ушла дальше длины: " + c.x + "," + c.z);
                    far = Math.max(far, Math.max(Math.abs(c.x), Math.abs(c.z)));
                    if (!pit) check(Math.abs(c.x) > 2 || Math.abs(c.z) > 2, "у ног хозяина трещины нет");
                }
                // связность: каждая клетка, кроме первых у центра, соседствует (по 8 направлениям) с другой клеткой
                for (QuakeLogic.Cell c : cells) {
                    if (Math.abs(c.x) <= 3 && Math.abs(c.z) <= 3) continue;
                    boolean nb = false;
                    for (int dx = -1; dx <= 1 && !nb; dx++) for (int dz = -1; dz <= 1 && !nb; dz++)
                        if ((dx != 0 || dz != 0) && keys.contains(QuakeLogic.key(c.x + dx, c.z + dz))) nb = true;
                    check(nb, "оторванная клетка " + c.x + "," + c.z + ", seed " + seed);
                }
                check(pit == keys.contains(QuakeLogic.key(0, 0)), "провал в центре только при ударе в точку");
                check(far >= Quake.CRACK_LEN / 2, "трещины слишком короткие: " + far);
                check(cells.get(cells.size() - 1).tick < Quake.CLOSE_AT - Quake.OPEN_AT, "трещины не успевают раскрыться до смыкания");
                minN = Math.min(minN, cells.size()); maxN = Math.max(maxN, cells.size());
            }
        }
        check(minN >= 60 && maxN <= 260, "клеток " + minN + ".." + maxN);
        // волны укладываются до смыкания, конец после смыкания
        for (int w : Quake.WAVES) check(w + (int) Math.ceil(Quake.WAVE_MAX / 0.9) < Quake.CLOSE_AT, "волна не закончилась до смыкания");
        check(Quake.CLOSE_AT + Quake.CLOSE_TICKS <= Quake.END, "конец раньше смыкания");

        // регистрация и ресурсы
        String mod = Files.readString(Path.of("src/com/explosionwand/ExplosionWandMod.java"));
        check(mod.contains("ITEMS.register(\"earthquake_wand\""), "палочка не зарегистрирована");
        check(Files.readString(Path.of("src/com/explosionwand/Ticker.java")).contains("Quake.ACTIVE"), "Quake не тикает");
        check(Files.exists(Path.of("resources/assets/explosionwand/models/item/earthquake_wand.json")), "нет модели");
        check(Files.exists(Path.of("resources/assets/explosionwand/textures/item/earthquake_wand.png")), "нет текстуры");
        for (String f : new String[]{"ru_ru.json", "en_us.json"}) {
            String l = Files.readString(Path.of("resources/assets/explosionwand/lang/" + f));
            for (String k : new String[]{"", ".desc1", ".desc2"}) check(l.contains("\"item.explosionwand.earthquake_wand" + k + "\""), "нет lang " + f + k);
        }
        check(!Files.exists(Path.of("resources/data/explosionwand/recipes/earthquake_wand.json")), "палочка для веселья без рецепта");
        System.out.println("QuakeTest OK: клеток трещин " + minN + ".." + maxN);
    }
}
