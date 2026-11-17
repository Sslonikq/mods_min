package com.explosionwand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Перчатка Бесконечности: чистая логика (порядок камней, переключение, выбор половины для Щелчка). */
final class GauntletLogic {
    /** Камни из миров мода: Разлом, Сон, Пепел, Затмение, Небо, Глубины, Щелчок. */
    static final String[] KEYS = {"rift", "sleep", "ash", "eclipse", "sky", "deep", "snap"};
    /** Перезарядка способности каждого камня, тики. */
    static final int[] COOLDOWN = {20, 60, 40, 200, 80, 100, 1200};
    /** Цвет камня (RGB). */
    static final int[] COLOR = {0x9B30FF, 0x30E8D8, 0xFF8A1E, 0x1A2A8C, 0x6FC8FF, 0xE02020, 0xFFFFFF};
    static final String NBT_KEY = "ewStone";

    static int count() { return KEYS.length; }
    static int clamp(int i) { return i < 0 || i >= KEYS.length ? 0 : i; }
    static int next(int i) { return (clamp(i) + 1) % KEYS.length; }
    static String key(int i) { return KEYS[clamp(i)]; }
    static int cooldown(int i) { return COOLDOWN[clamp(i)]; }
    static int color(int i) { return COLOR[clamp(i)]; }

    /** Общая перезарядка предмета после применения камня (против спама), тики. Смена камня от перезарядок не зависит. */
    static final int ITEM_COOLDOWN = 10;
    /** Ванильные боссы, которых Щелчок не трогает (id типа без пространства имён). Боссы и мобы данжей мода исключаются отдельно. */
    static final String[] SNAP_SPARED = {"wither", "ender_dragon", "warden", "elder_guardian"};

    /** NBT-ключ времени окончания перезарядки камня (время игры, тики). */
    static String cdKey(int i) { return "ewCd" + clamp(i); }
    /** Время окончания перезарядки камня, применённого в момент now. */
    static long readyAt(long now, int stone) { return now + cooldown(stone); }
    /** Сколько тиков осталось (0 - готов). Время «откатилось» назад больше чем на перезарядку - считаем готовым. */
    static int left(long readyAt, long now, int stone) {
        long d = readyAt - now;
        return d <= 0 || d > cooldown(stone) ? 0 : (int) d;
    }
    /** Остаток в целых секундах с округлением вверх (для actionbar). */
    static int secs(int ticks) { return ticks <= 0 ? 0 : (ticks + 19) / 20; }
    /** Щелчок не трогает ванильных боссов. */
    static boolean snapSpared(String typeId) {
        for (String s : SNAP_SPARED) if (s.equals(typeId)) return true;
        return false;
    }

    /** Половина с округлением вверх: 0 -> 0, 1 -> 1, 5 -> 3, 6 -> 3. */
    static int half(int n) { return n <= 0 ? 0 : (n + 1) / 2; }

    /** Случайная половина списка (с округлением вверх), без повторов; исходный список не меняется. */
    static <T> List<T> pickHalf(List<T> all, Random rng) {
        List<T> copy = new ArrayList<>(all);
        Collections.shuffle(copy, rng);
        return new ArrayList<>(copy.subList(0, half(copy.size())));
    }
}
