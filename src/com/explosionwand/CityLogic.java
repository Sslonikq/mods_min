package com.explosionwand;

import java.util.Set;

/**
 * Чистые правила мобов Города Спящих (фаза 4.6): пробуждение Лунатика, щит Стража Шва, дальность и урон мобов (часть A);
 * копии Сновидца, страницы Книжного стража, взгляд на Статую-стража, фонарь Колыбельщика, кража Туманного странника (часть B).
 * Без Minecraft: проверяется tests/CityMobsTest.java; CityMobs только применяет эти правила.
 */
final class CityLogic {
    private CityLogic() {}

    // ================================================================ урон (сырой, для Нормальной сложности; на Сложной режет RiftLogic.capHit)
    static final double LUNATIC_HIT = 4, GUARD_SLASH = 7, GUARD_LUNGE = 8, MOTH_HIT = 2, DROWSY_RAY = 3;

    // ================================================================ Лунатик
    /** Радиус слуха спящего: бег рядом, шаг почти вплотную (задел плечом), крадущийся и стоящий не будят. */
    static final double WAKE_SPRINT = 6.0, WAKE_WALK = 2.0, WAKE_LIGHT = 5.0, WAKE_NOISE = 7.0, WAKE_CHAIN = 3.5;
    /** Ярость: после последнего контакта бешеный успокаивается через CALM_AFTER тиков и засыпает снова. */
    static final int CALM_AFTER = 160;
    /** Удар по спящему больнее (он не ждал). */
    static final double SLEEP_HIT_MULT = 2.0;

    /** Игрок разбудил шагами: бег (с движением) на WAKE_SPRINT, ходьба на WAKE_WALK, присед и стояние не будят. */
    static boolean stepWakes(boolean sneaking, boolean sprinting, boolean moved, double dist) {
        if (sneaking || !moved) return false;
        return dist <= (sprinting ? WAKE_SPRINT : WAKE_WALK);
    }

    /** Свет в руке (факел, фонарь) будит с WAKE_LIGHT (присед свет не прячет). */
    static boolean lightWakes(boolean holdsLight, double dist) { return holdsLight && dist <= WAKE_LIGHT; }

    /** Шум (удар по любому мобу рядом, взрыв). */
    static boolean noiseWakes(double dist) { return dist <= WAKE_NOISE; }

    /** Проснувшийся будит соседей: толпа поднимается цепочкой. */
    static boolean chainWakes(boolean neighbourAwake, double dist) { return neighbourAwake && dist <= WAKE_CHAIN; }

    static double sleeperHitMult(boolean asleep) { return asleep ? SLEEP_HIT_MULT : 1.0; }

    /** Бешеный успокаивается, когда долго никого рядом (idle — тиков без игрока в радиусе). */
    static boolean calmsDown(int idleTicks) { return idleTicks >= CALM_AFTER; }

    /** Предметы, дающие свет: будят Лунатика. */
    static final Set<String> LIGHT_ITEMS = Set.of("torch", "soul_torch", "redstone_torch", "lantern", "soul_lantern", "glowstone", "sea_lantern",
            "shroomlight", "jack_o_lantern", "end_rod", "campfire", "soul_campfire", "glow_berries", "glowstone_dust", "blaze_rod", "lava_bucket");

    static boolean isLightItem(String id) { return id != null && LIGHT_ITEMS.contains(id); }

    // ================================================================ Страж Шва
    /** Щит спереди (cos > 0,5) гасит удар до SHIELD_MULT, пока страж не в замахе; со спины шов открыт (BACK_MULT). */
    static final double SHIELD_MULT = 0.4, BACK_MULT = 1.3;

    static double shieldMult(double cos, boolean guardUp) {
        if (cos > 0.5 && guardUp) return SHIELD_MULT;
        if (cos < -0.5) return BACK_MULT;
        return 1.0;
    }

    /** Каждый четвёртый взмах — выпад: длинный телеграф и рывок на несколько блоков. */
    static boolean isLunge(int swing) { return swing > 0 && swing % 4 == 0; }
    static final int SLASH_WIND = 12, LUNGE_WIND = 22, LUNGE_DASH = 4;
    static final double SLASH_REACH = 2.6, LUNGE_RANGE = 5.0;

    // ================================================================ Дремотник
    /** Луч дремоты: прицел 30 тиков, замедление II на 4 с. Облако дремоты рядом: замедление I на 3 с, раз в секунду. */
    static final int RAY_AIM = 30, RAY_SLOW_TICKS = 80, RAY_SLOW_AMP = 1, AURA_SLOW_TICKS = 60, AURA_SLOW_AMP = 0;
    static final double AURA_RADIUS = 3.5, RAY_LENGTH = 22;

    // ================================================================ Ночная моль
    /** Рой: скорость (блоков/тик) и разгон Ветром; ослепление после замаха MOTH_WIND тиков. */
    static final double MOTH_SPEED = 0.07, MOTH_WIND_MULT = 2.4, MOTH_SEEK_LIGHT = 18, GUST_RADIUS = 14, MOTH_BITE_R = 2.3;
    static final int MOTH_WIND = 16, MOTH_BLIND_TICKS = 60, GUST_TICKS = 80, MOTH_CD = 30;

    static double mothSpeed(boolean windy) { return windy ? MOTH_SPEED * MOTH_WIND_MULT : MOTH_SPEED; }

    /** Моль гасит огонёк, подлетев ближе этого радиуса. */
    static final double SNUFF_RADIUS = 1.6;

    static boolean snuffs(double dist) { return dist <= SNUFF_RADIUS; }

    // ================================================================ взгляд и луч (Статуя-страж, Туманный странник, фонарь Колыбельщика)
    /** Игрок «видит» точку, если угол между его взглядом и направлением на неё не больше ~57 градусов (cos 0,55: поле зрения с запасом на края экрана) и она не дальше SEEN_RANGE. */
    static final double SEEN_COS = 0.55, SEEN_RANGE = 48;

    static boolean seenBy(double ex, double ey, double ez, double lx, double ly, double lz, double tx, double ty, double tz) {
        double dx = tx - ex, dy = ty - ey, dz = tz - ez, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > SEEN_RANGE) return false;
        if (d < 0.6) return true;
        double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (ll < 1e-6) return false;
        return (dx * lx + dy * ly + dz * lz) / (d * ll) >= SEEN_COS;
    }

    /** Расстояние от точки до луча (только вперёд от начала) не больше r: удар «по фонарю». */
    static boolean rayNear(double ox, double oy, double oz, double dx, double dy, double dz, double px, double py, double pz, double r) {
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6) return false;
        dx /= l; dy /= l; dz /= l;
        double vx = px - ox, vy = py - oy, vz = pz - oz, t = vx * dx + vy * dy + vz * dz;
        if (t < 0) return false;
        double cx = vx - dx * t, cy = vy - dy * t, cz = vz - dz * t;
        return cx * cx + cy * cy + cz * cz <= r * r;
    }

    /** Локальная точка (lx вправо, lz вперёд) -> сдвиг по миру {dx, dz} при повороте yaw (вперёд = (sin yaw, cos yaw)). */
    static double[] toWorld(double yaw, double lx, double lz) {
        double s = Math.sin(yaw), c = Math.cos(yaw);
        return new double[]{lx * c + lz * s, -lx * s + lz * c};
    }

    // ================================================================ Сновидец
    /** Усыпление: круг SLEEP_RADIUS, телеграф SLEEP_WIND тиков, замедление II и слабость I (без полной блокировки). Копии: не больше CLONE_CAP на Сновидца, живут CLONE_LIFE тиков, урона не наносят. */
    static final double SLEEP_RADIUS = 6.5, KEEP_MIN = 4.5, KEEP_MAX = 9.5;
    static final int SLEEP_WIND = 28, SLEEP_SLOW_TICKS = 80, SLEEP_SLOW_AMP = 1, SLEEP_WEAK_TICKS = 100, SLEEP_WEAK_AMP = 0;
    static final int SLEEP_CD = 150, CLONE_CD = 220, CLONE_CAP = 3, CLONE_LIFE = 240, CLONE_WORLD_CAP = 12;

    /** Сколько копий создать сейчас: 2 (3 при ХП ниже половины), но так, чтобы вместе с живыми не больше CLONE_CAP. */
    static int cloneSpawn(int existing, double hpFrac) {
        int want = hpFrac < 0.5 ? 3 : 2;
        return Math.max(0, Math.min(want, CLONE_CAP - existing));
    }

    /** Круг усыпления задевает игрока: внутри радиуса и на линии нет стены. */
    static boolean sleepAffects(double dist, boolean clearLine) { return clearLine && dist <= SLEEP_RADIUS; }

    // ================================================================ Книжный страж
    /** Залп страницами: прицел PAGE_AIM тиков (линия частиц), PAGE_COUNT страниц веером, скорость PAGE_SPEED блоков/тик, урон PAGE_HIT каждая. */
    static final double PAGE_HIT = 3, PAGE_SPEED = 0.55, PAGE_R = 0.8, PAGE_SPREAD = 0.14, PAGE_RANGE = 22;
    static final int PAGE_COUNT = 3, PAGE_LIFE = 40, PAGE_AIM = 26, PAGE_CD = 110;

    /** Отклонения страниц залпа по горизонтали (радианы): симметрично, одна страница прямо. */
    static double[] fan(int n, double spread) {
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = (i - (n - 1) / 2.0) * spread;
        return r;
    }

    // ================================================================ Статуя-страж
    /** Двигается, только когда на неё не смотрят (statueMoves); касание телеграфируется STATUE_WIND тиков (счёт идёт, только пока на неё не смотрят). */
    static final double STATUE_TOUCH = 6, STATUE_SPEED = 0.26, STATUE_RANGE = 22, STATUE_REACH = 2.3, STATUE_NEAR = 2.4;
    static final int STATUE_WIND = 16, STATUE_CD = 36;

    static boolean statueMoves(boolean seen) { return !seen; }

    // ================================================================ Колыбельщик
    /** Фонарь (LANTERN_HP) принимает удары, если луч прицела проходит от его центра не дальше LANTERN_R; пока он цел, тело получает CRADLER_BODY_MULT от урона.
     *  Звон: телеграф CHIME_WIND тиков, радиус CHIME_RADIUS, замедление III и слабость II (сильнее, чем у Сновидца, но без блокировки). */
    static final double LANTERN_R = 0.75, LANTERN_PROJ_R = 1.0, CRADLER_BODY_MULT = 0.25, CRADLER_FIST = 9, CHIME_RADIUS = 13, FIST_R = 2.4, FIST_R2 = 3.0;
    static final int LANTERN_HP = 36, CHIME_WIND = 32, CHIME_CD = 170, FIST_WIND = 24, FIST_WIND2 = 20, FIST_CD = 70, FIST_CD2 = 40;
    static final int CHIME_SLOW_TICKS = 100, CHIME_SLOW_AMP = 2, CHIME_WEAK_TICKS = 120, CHIME_WEAK_AMP = 1;

    static double cradlerBodyMult(boolean lanternAlive) { return lanternAlive ? CRADLER_BODY_MULT : 1.0; }
    static boolean chimeAffects(double dist, boolean clearLine) { return clearLine && dist <= CHIME_RADIUS; }
    static double cradlerSpeed(boolean lanternAlive) { return lanternAlive ? 0.06 : 0.1; }

    // ================================================================ Туманный странник
    /** Крадёт предмет из основной руки, только если это безопасная вещь: ванильная, без NBT (значит, без чар, имени, данных) и не ценная. */
    static final int FOG_CAP = 2, FOG_FLEE = 300, FOG_PERIOD = 100, FOG_GRAB_WIND = 14, FOG_CITY_RANGE = 110;
    static final double FOG_SPEED = 0.1, FOG_SPEED_SEEN = 0.03, FOG_RUN = 0.24, FOG_GRAB_RANGE = 2.6, FOG_GRAB_R = 2.0, FOG_SPAWN_MIN = 22, FOG_SPAWN_MAX = 34;
    static final String[] FOG_DENY = {"netherite", "diamond", "elytra", "totem", "nether_star", "shulker", "beacon", "dragon", "trident", "spawn_egg", "command_block",
            "barrier", "enchanted", "heart_of_the_sea", "bundle", "book", "map", "ender_chest", "sculk", "spyglass", "recovery_compass", "echo_shard", "knowledge", "debug", "structure", "jigsaw"};

    static boolean canSteal(String itemId, boolean hasTag) {
        if (itemId == null || hasTag || !itemId.startsWith("minecraft:") || itemId.equals("minecraft:air")) return false;
        for (String d : FOG_DENY) if (itemId.contains(d)) return false;
        return true;
    }

    /** Странник появляется только в тумане (ночью или в дождь), не больше FOG_CAP у города, редко (25% за попытку). */
    static boolean fogSpawns(boolean night, boolean raining, int have, int roll100) { return (night || raining) && have < FOG_CAP && roll100 < 25; }
}
