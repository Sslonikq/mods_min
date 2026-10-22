package com.explosionwand;

import java.util.Random;

/**
 * Сеты брони данжей (4.12), чистая логика без обращений к игре: шансы выпадения, веса частей, множители бонусов.
 * Цифры - docs/world.md, «Сеты брони данжей». Игровая часть (события, эффекты) - {@link ArmorSets}.
 */
final class ArmorSetLogic {
    private ArmorSetLogic() {}

    /** Ранг моба для шанса выпадения части. NONE - части не даёт (жук, копии, мелкие, огонёк, моб вне данжа). */
    enum Tier { NONE, COMMON, RANGED, ELITE, MINI }

    static final double CH_COMMON = 0.015, CH_RANGED = 0.02, CH_ELITE = 0.06, CH_MINI = 0.20;
    /** «Добыча»: +1% к шансу за уровень (как у редких предметов в ванильных таблицах). */
    static final double LOOT_STEP = 0.01;

    /** Рядовой / стрелок и поддержка / элита / мини-босс. Боссы сюда не попадают (у них своя добыча, они не RiftMobs.Mob). */
    static Tier tier(RiftModels.Kind k) {
        if (k == null || Mats.dungeonOf(k) == null || k == RiftModels.Kind.BEETLE) return Tier.NONE;
        switch (k) {
            case GOLEM: case CRADLER: case ASH_KEEPER:
                return Tier.MINI;
            case EATER: case STITCH_GUARD: case STATUE_GUARD: case ASH_GUARD:
                return Tier.ELITE;
            case ARCHER: case PRISM: case DREAMER: case DROWSY: case BOOK_GUARD: case ASH_SPARK: case ASH_RINGER: case ASH_PRIEST: case ASH_WORM:
                return Tier.RANGED;
            default:
                return Tier.COMMON;
        }
    }

    static double baseChance(Tier t) {
        switch (t) {
            case COMMON: return CH_COMMON;
            case RANGED: return CH_RANGED;
            case ELITE: return CH_ELITE;
            case MINI: return CH_MINI;
            default: return 0;
        }
    }

    /** Шанс части с учётом «Добычи» (уровень looting >= 0), не больше 1. */
    static double chance(Tier t, int looting) {
        double b = baseChance(t);
        return b <= 0 ? 0 : Math.min(1.0, b + LOOT_STEP * Math.max(0, looting));
    }

    /** Какая из четырёх частей (0 шлем, 1 нагрудник, 2 поножи, 3 сапоги) выпала: недостающая весит вдвое больше. */
    static int pickPart(boolean[] have, Random r) {
        int total = 0;
        for (boolean h : have) total += h ? 1 : 2;
        int x = r.nextInt(total);
        for (int i = 0; i < have.length; i++) {
            x -= have[i] ? 1 : 2;
            if (x < 0) return i;
        }
        return have.length - 1;
    }

    /** Бросок на часть: номер части (0 шлем .. 3 сапоги) или -1, если не выпало. */
    static int roll(Tier t, int looting, boolean[] have, Random r) {
        double ch = chance(t, looting);
        if (ch <= 0 || r.nextDouble() >= ch) return -1;
        return pickPart(have, r);
    }

    // ---------------------------------------------------------------- бонусы
    static final int PARTS = 4;

    /** Против своего данжа: +3,75% за часть сета, у полного сета +15%. */
    static double dmgBonus(int parts) { return 0.0375 * Math.max(0, Math.min(PARTS, parts)); }

    /** Кристальный доспех, 2 части: отбрасывание слабее на 10% (множитель силы толчка). */
    static float knockMul(int crystalParts) { return crystalParts >= 2 ? 0.9F : 1.0F; }

    static final double REFLECT_CHANCE = 0.20;
    static final int EDGE_TICKS = 60, EDGE_COOLDOWN = 160;
    static final double EDGE_ARMOR = 2.0;

    /** Кристальный доспех, 4 части: сработало ли отражение снаряда (r - случайное число [0,1)). */
    static boolean reflects(int crystalParts, double r) { return crystalParts >= 4 && r < REFLECT_CHANCE; }

    /** Ускорение огненного шара (xPower,yPower,zPower) после разворота: та же длина, направление dir (единичный). */
    static double[] turnPower(double px, double py, double pz, double dx, double dy, double dz) {
        double len = Math.sqrt(px * px + py * py + pz * pz);
        return new double[]{dx * len, dy * len, dz * len};
    }

    /** «Грань»: бонус брони можно включить, если с прошлого раза прошло не меньше перезарядки (last < 0 - ещё не включали). */
    static boolean edgeReady(long now, long last) { return last < 0 || now - last >= EDGE_COOLDOWN; }

    /** Облачение Сновидца: во сколько раз меньше расстояние, с которого тебя замечают. 2 части 0,75; присед при 4 частях ещё x0,6. */
    static double noticeMul(int dreamerParts, boolean crouching) {
        if (dreamerParts < 2) return 1.0;
        double m = 0.75;
        if (dreamerParts >= 4 && crouching) m *= 0.6;
        return m;
    }

    static final double SPEED_BONUS = 0.08;

    // ---------------------------------------------------------------- Пепельный доспех
    static final int ASH_BURN_CAP = 60;

    /**
     * Остаток горения после этого тика. 2 части: горение убывает вдвое быстрее (минус ещё 1 тик к ванильному), 4 части: не больше 3 с.
     * Вызывается каждый тик; cur - остаток после ванильного уменьшения.
     */
    static int fireNext(int cur, int ashParts) {
        if (ashParts < 2 || cur <= 0) return cur;
        int n = cur - 1;
        if (ashParts >= 4) n = Math.min(n, ASH_BURN_CAP);
        return n;
    }

    /** Огненный урон (не лава): костёр и огонь, горение, магма, огненные шары. */
    static boolean isFireSource(String id) {
        return id != null && (id.equals("inFire") || id.equals("onFire") || id.equals("hotFloor") || id.equals("fireball"));
    }

    /** Пепельный доспех, 4 части: огненный урон -40%. Лава (`lava`) не смягчается. */
    static float fireDamage(String id, float amount, int ashParts) {
        return ashParts >= 4 && isFireSource(id) ? amount * 0.6F : amount;
    }

    static final int IGNITE_SECONDS = 3;
}
