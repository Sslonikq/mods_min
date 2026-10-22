package com.explosionwand;

import java.util.List;

/**
 * Чистые правила мобов Пепельного святилища (4.9): урон и тайминги, стая гончих, волна Звонаря, лечение жреца, щит Стража и Хранителя,
 * появление и исход Блуждающего огонька. Без Minecraft: проверяется tests/AshMobsTest.java; AshMobs/AshMobsB только применяют эти правила.
 * Урон сырой (для Нормальной сложности); на Сложной режет RiftLogic.capHit (не больше 7 сердец без брони).
 */
final class AshLogic {
    private AshLogic() {}

    // ================================================================ урон
    static final double WALKER_HIT = 8, SPARK_BOLT = 2, HOUND_BITE = 3, RINGER_WAVE = 3, WORM_SPIT = 5, PRIEST_CIRCLE = 5,
            GUARD_SWING = 10, KEEPER_HAMMER = 11, KEEPER_SLAM = 9, KEEPER_CIRCLE = 6, PATCH_DMG = 2, TRAP_DMG = 3;

    // ================================================================ Пеплоход
    static final int WALKER_WIND = 18, WALKER_CD = 44, PATCH_TICKS = 120, PATCH_EVERY = 10;
    static final double WALKER_REACH = 2.7, WALKER_SPEED = 0.11, PATCH_R = 1.6;

    /** Игрок в горящем пепле: горизонтально в PATCH_R и по высоте не выше двух блоков над пеплом. */
    static boolean inPatch(double dx, double dy, double dz) { return dx * dx + dz * dz <= PATCH_R * PATCH_R && dy > -1.0 && dy < 2.0; }

    // ================================================================ Искра
    static final int SPARK_AIM = 16, SPARK_CD_MIN = 50, SPARK_CD_VAR = 40, SPARK_FIRE_SECONDS = 3;
    static final double SPARK_RANGE = 14, SPARK_HIT_R = 0.8, SPARK_KEEP = 4.5;

    // ================================================================ Гончая
    static final int HOUND_WIND = 10, HOUND_CD = 26, PACK_RADIUS = 20;
    static final double HOUND_SPEED = 0.25, HOUND_BITE_REACH = 2.1, HOUND_SURROUND_R = 3.6, HOUND_RUSH = 4.8;

    /** Место гончей в стае вокруг цели: угол слота (стая делит круг поровну, base - общий поворот), радиус r. Возвращает {dx, dz}. */
    static double[] houndSlot(int index, int count, double base, double r) {
        double a = base + index * Math.PI * 2 / Math.max(1, count);
        return new double[]{Math.cos(a) * r, Math.sin(a) * r};
    }

    /** Номер гончей среди стаи: id хостов по порядку (одинаков для всех членов стаи, поэтому слоты не пересекаются). */
    static int houndRank(String id, List<String> packIds) {
        int n = 0;
        for (String o : packIds) if (o.compareTo(id) < 0) n++;
        return n;
    }

    // ================================================================ Звонарь
    static final int RINGER_WIND = 26, RINGER_CD = 140, RINGER_CD_VAR = 40;
    static final double RINGER_R = 7.5, WAVE_JUMP = 1.0, WAVE_BELOW = -1.5, WAVE_KICK = 1.1;

    /** Волна бьёт по земле: прыжок выше WAVE_JUMP над полом звонаря её пропускает, игроки на этаже ниже (глубже WAVE_BELOW) не задеты. */
    static boolean waveHits(double dist, double dy, double radius) { return dist <= radius && dy > WAVE_BELOW && dy < WAVE_JUMP; }

    // ================================================================ Магмовый червь
    static final int WORM_HIDE = 50, WORM_HIDE_VAR = 60, WORM_BUBBLE = 22, WORM_UP = 90, WORM_SPIT_WIND = 18, WORM_SPIT_CD = 34;
    static final double WORM_RANGE = 16, WORM_SPIT_R = 1.9, WORM_MIN_DIST = 3.0, WORM_CELL_RANGE = 11;

    /** Всплывает ближе всего к игроку, но не под ногами: из клеток лавы берётся ближайшая не ближе WORM_MIN_DIST. Индекс или -1. */
    static int wormCell(double[][] cells, double px, double pz) {
        int best = -1; double bd = Double.MAX_VALUE;
        for (int i = 0; i < cells.length; i++) {
            double d = Math.hypot(cells[i][0] - px, cells[i][1] - pz);
            if (d < WORM_MIN_DIST) continue;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    // ================================================================ Пепельный жрец
    static final int PRIEST_HEAL_WIND = 14, PRIEST_HEAL_CD = 100, PRIEST_CIRCLE_WIND = 26, PRIEST_CIRCLE_CD = 120, PRIEST_CIRCLE_VAR = 40, MAX_CIRCLES = 3;
    static final double HEAL_BELOW = 0.7, HEAL_PART = 0.25, HEAL_CAP = 40, HEAL_RANGE = 14, CIRCLE_R = 2.3, PRIEST_KEEP_MIN = 6, PRIEST_KEEP_MAX = 11;

    static boolean needsHeal(double hp, double max) { return max > 0 && hp > 0 && hp < max * HEAL_BELOW; }

    /** Сколько здоровья даёт лечение (четверть максимума, но не больше HEAL_CAP: Хранителя на арене не вытянуть). */
    static double healAmount(double max) { return Math.min(max * HEAL_PART, HEAL_CAP); }

    // ================================================================ Угольный страж
    static final int GUARD_WIND = 22, GUARD_RECOVER = 26, GUARD_CD = 34;
    static final double GUARD_REACH = 3.4, GUARD_SHIELD = 0.25, GUARD_BACK = 1.3, GUARD_OPEN = 1.5, GUARD_SPEED = 0.1;

    /**
     * Множитель урона по стражу. cos - косинус угла между его взглядом и направлением на бьющего: щит поднят и бьют спереди - гасит;
     * сразу после размаха (open) стражем открыт со всех сторон; со спины уязвим всегда.
     */
    static double guardMult(double cos, boolean shieldUp, boolean open) {
        if (open) return GUARD_OPEN;
        if (cos > 0.5 && shieldUp) return GUARD_SHIELD;
        if (cos < -0.5) return GUARD_BACK;
        return 1.0;
    }

    // ================================================================ Хранитель Углей
    static final double KEEPER_SHIELD = 0.2, KEEPER_BACK = 1.3, KEEPER_STUN_MULT = 1.8, KEEPER_ENRAGE = 0.5, KEEPER_SPEED = 0.085;
    static final int KEEPER_HAMMER_WIND = 26, KEEPER_HAMMER_REC = 22, KEEPER_SLAM_WIND = 34, KEEPER_STUN = 70, KEEPER_STUN_RAGE = 55, KEEPER_CIRCLE_WIND = 28, KEEPER_CIRCLE_CD = 170, KEEPER_CD = 44;
    static final double KEEPER_REACH = 3.9, KEEPER_SLAM_R = 6.5, KEEPER_CIRCLE_R = 2.6;

    /** Множитель урона по Хранителю: оглушён (после удара о землю) - открыт со всех сторон; щит поднят и бьют спереди - гасит; со спины уязвим. */
    static double keeperMult(double cos, boolean shieldUp, boolean stunned) {
        if (stunned) return KEEPER_STUN_MULT;
        if (cos > 0.5 && shieldUp) return KEEPER_SHIELD;
        if (cos < -0.5) return KEEPER_BACK;
        return 1.0;
    }

    /** Один удар не перескакивает порог фазы: пока ХП выше порога, урон обрезается так, чтобы остаться на пороге. */
    static float clampPhase(float hp, float amt, float thr) {
        if (hp > thr && hp - amt < thr) return Math.max(0F, hp - thr + 0.01F);
        return amt;
    }

    static boolean enraged(double hp, double max) { return hp <= max * KEEPER_ENRAGE; }

    // ================================================================ Блуждающий огонёк
    static final int WISP_CAP = 2, WISP_PERIOD = 200, WISP_CHANCE = 20, TRAP_PERCENT = 55, WISP_LIFE = 6000, TRAP_WIND = 28, TRAP_SPARKS = 3, TRAP_SPARK_LIFE = 800;
    static final double WISP_RANGE = 110, WISP_MIN = 30, WISP_MAX = 70, DEST_MIN = 26, DEST_MAX = 44, LURE_RANGE = 12, WISP_SPEED = 0.085, TRAP_R = 5, ARRIVE = 2.5;

    /** Огонёк появляется ночью у святилища: не больше WISP_CAP, шанс WISP_CHANCE процентов за проверку. */
    static boolean wispSpawns(boolean night, int have, int roll) { return night && have < WISP_CAP && roll < WISP_CHANCE; }

    /** Исход выбирается при появлении: ловушка (TRAP_PERCENT процентов) или сундук. */
    static boolean wispIsTrap(int roll) { return roll < TRAP_PERCENT; }

    /** Огонёк ведёт за собой, пока игрок не дальше LURE_RANGE; дальше - ждёт. */
    static boolean wispLeads(double playerDist) { return playerDist <= LURE_RANGE; }

    /** Тег Искр ловушки огонька: оканчивается на _min, поэтому после выгрузки чанка adopt снова считает их призванными и задаёт срок жизни. */
    static final String TRAP_TAG = "ewash_trap_min";

    // ================================================================ приспешники Феникса и метки
    /** Искры (kind 0) и Пеплоходы (kind 1) Феникса: виды моба и поводок от точки появления (Искры летают по пещере, Пеплоходы у помоста). */
    static String minionKind(int kind) { return kind == 0 ? "ash_spark" : "ash_walker"; }
    static double minionLeash(int kind) { return kind == 0 ? 24 : 22; }
}
