package com.explosionwand;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Чистая логика палочек Земли (4.11): параметры, геометрия, правила. Без классов Minecraft - проверяется тестом EarthTest.
 * Игровая часть - {@link Earth}, предмет - {@link EarthWandItem}.
 */
final class EarthLogic {
    private EarthLogic() {}

    /** Палочки Земли. cd - перезарядка в тиках (до множителя ArcaneSet.cd), noDungeon - в данжах не работает (DungeonGuard.forbidden). */
    enum Kind {
        LIGHT("light_wand", 10, false),
        FROST("frost_wand", 40, false),
        GROWTH("growth_wand", 100, false),
        GATHER("gather_wand", 100, false),
        RETURN("return_wand", 12000, true),
        SIGNAL("signal_wand", 200, false),
        DIGGER("digger_wand", 160, true),
        // часть C (палочки 9-16): блоков не меняют, поэтому работают и в данжах
        SPIKES("crystal_spikes_wand", 100, false),
        SKIN("stone_skin_wand", 400, false),
        WIND("wind_wand", 100, false),
        VINE("vine_wand", 160, false),
        PULL("pull_wand", 80, false),
        DECOY("decoy_wand", 300, false),
        SPARKS("spark_wand", 100, false),
        HEAT("heat_wand", 240, false);
        final String id; final int cd; final boolean noDungeon;
        Kind(String id, int cd, boolean noDungeon) { this.id = id; this.cd = cd; this.noDungeon = noDungeon; }
    }

    // ---- Огонёк
    static final int LIGHT_TICKS = 3600;      // 3 минуты
    static final int LIGHT_MAX = 20;          // огоньков-блоков на измерение: старый гаснет первым
    static final double LIGHT_RADIUS = 8.0D;  // радиус для хука CityMobs.registerLight (моли, Лунатики)
    static final double LIGHT_RANGE = 24.0D;

    // ---- Морозец
    static final double FROST_RANGE = 9.0D;
    static final double FROST_COS = 0.75D;    // конус около 80 градусов
    static final int FROST_SLOW_TICKS = 120, FROST_SLOW_AMP = 2;
    static final int ICE_TICKS = 400;         // 20 секунд, потом обратно вода
    static final int ICE_LENGTH = 6, ICE_HALF_WIDTH = 1;

    // ---- Рост
    static final int GROW_R = 5, GROW_UP = 3, GROW_DOWN = 2, GROW_PASSES = 10, GROW_MAX = 120;
    static final Set<String> GROWABLE = Set.of("wheat", "carrots", "potatoes", "beetroots", "melon_stem", "pumpkin_stem", "nether_wart",
            "sweet_berry_bush", "cocoa", "sugar_cane", "cactus", "bamboo", "bamboo_sapling", "torchflower_crop", "pitcher_crop", "mangrove_propagule");

    static boolean growable(String path) { return path != null && (GROWABLE.contains(path) || path.endsWith("_sapling")); }

    // ---- Сбор
    static final double GATHER_R = 10.0D;
    static final int GATHER_TICKS = 60;

    // ---- Возврат
    static final int CAST_TICKS = 100;        // 5 секунд
    static final int CAST_FAIL_CD = 40;

    /** Сдвиг от точки начала каста, прерывающий его: больше 0.6 блока по горизонтали или 1.2 по вертикали. */
    static boolean moved(double dx, double dy, double dz) { return dx * dx + dz * dz > 0.36D || Math.abs(dy) > 1.2D; }
    /** Урон прерывает каст: здоровье стало меньше, чем было. */
    static boolean hurt(float before, float now) { return now < before - 0.01F; }

    // ---- Сигнальная
    static final int SIGNAL_TICKS = 300, SIGNAL_MAX = 6, SIGNAL_HEIGHT = 110;

    // ---- Копатель
    static final int DIG_DEPTH = 8, DIG_RANGE = 6;

    /** Ось по взгляду: 0 +x, 1 -x, 2 +y, 3 -y, 4 +z, 5 -z. Вертикаль выбирается, когда вертикальная составляющая строго больше обеих горизонтальных. */
    static int axis(double lx, double ly, double lz) {
        double ax = Math.abs(lx), ay = Math.abs(ly), az = Math.abs(lz);
        if (ay > ax && ay > az) return ly >= 0 ? 2 : 3;
        if (ax >= az) return lx >= 0 ? 0 : 1;
        return lz >= 0 ? 4 : 5;
    }

    static int[] dir(int axis) {
        switch (axis) {
            case 0: return new int[]{1, 0, 0};
            case 1: return new int[]{-1, 0, 0};
            case 2: return new int[]{0, 1, 0};
            case 3: return new int[]{0, -1, 0};
            case 4: return new int[]{0, 0, 1};
            default: return new int[]{0, 0, -1};
        }
    }

    /** Клетки тоннеля 3x3 глубиной depth от (sx,sy,sz) вдоль оси: срез за срезом, 9 клеток в срезе. */
    static List<int[]> tunnel(int sx, int sy, int sz, int axis, int depth) {
        int[] d = dir(axis);
        boolean vert = axis == 2 || axis == 3, alongX = axis == 0 || axis == 1;
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < depth; i++)
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                int x = sx + d[0] * i, y = sy + d[1] * i, z = sz + d[2] * i;
                if (vert) { x += a; z += b; } else if (alongX) { y += a; z += b; } else { x += a; y += b; }
                out.add(new int[]{x, y, z});
            }
        return out;
    }

    /** Точка (dx,dy,dz от игрока) в конусе взгляда (lx,ly,lz - единичный). */
    static boolean inCone(double dx, double dy, double dz, double lx, double ly, double lz, double range, double cosMin) {
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 > range * range) return false;
        if (d2 < 1.0E-6D) return true;
        return (dx * lx + dy * ly + dz * lz) / Math.sqrt(d2) >= cosMin;
    }

    // ---- Взрывная
    static final double SHOT_RANGE = 48.0D;
    static final float SHOT_DAMAGE = 6.0F;    // тычок как стрела с Силой I
    static final float BLAST_POWER = 1.4F;    // динамит - 4.0
    static final float BREAK_MAX_HARDNESS = 4.0F;
    static final int SHOT_CD = 25;

    /** Взрыв ломает 2-4 блока. */
    static int blocksToBreak(Random r) { return 2 + r.nextInt(3); }

    static int cooldown(int base, double mult) { return (int) Math.round(base * mult); }

    // ================================================================ часть C: палочки 9-16
    /** Единичный горизонтальный вектор по взгляду (lx,lz); если смотрим почти вертикально - вперёд по +z. */
    static double[] horizontal(double lx, double lz) {
        double l = Math.sqrt(lx * lx + lz * lz);
        return l < 0.05D ? new double[]{0, 1} : new double[]{lx / l, lz / l};
    }

    // ---- Кристальные шипы: линия на SPIKE_LEN клеток, по шипу за тик
    static final int SPIKE_LEN = 10;
    static final float SPIKE_DAMAGE = 5.0F;
    static final double SPIKE_LIFT = 0.8D, SPIKE_REACH = 1.2D;
    /** Клетка (x,z) i-го шипа от игрока в (px,pz) по направлению (hx,hz). */
    static int[] spikeCell(double px, double pz, double hx, double hz, int i) {
        return new int[]{(int) Math.floor(px + hx * i), (int) Math.floor(pz + hz * i)};
    }

    // ---- Каменная кожа
    static final int SKIN_TICKS = 120, SKIN_SLOW_AMP = 1;
    static final float SKIN_MULT = 0.5F;
    static float skinned(float damage) { return damage * SKIN_MULT; }

    // ---- Ветер
    static final double WIND_RANGE = 8.0D, WIND_COS = 0.5D, WIND_PUSH = 1.5D, WIND_LIFT = 0.4D, WIND_SELF_LIFT = 1.1D;
    static final int WIND_FALL_TICKS = 100;
    /** Скорость отброса моба: вдоль горизонтального взгляда и немного вверх. */
    static double[] windVel(double lx, double lz) {
        double[] h = horizontal(lx, lz);
        return new double[]{h[0] * WIND_PUSH, WIND_LIFT, h[1] * WIND_PUSH};
    }

    // ---- Лоза
    static final double VINE_R = 4.0D, VINE_H = 3.0D, VINE_RANGE = 18.0D;
    static final int VINE_TICKS = 60, VINE_SLOW_AMP = 6;
    /** Точка в круге радиуса r вокруг центра (горизонтально), по высоте не дальше h. */
    static boolean inCircle(double dx, double dy, double dz, double r, double h) { return dx * dx + dz * dz <= r * r && Math.abs(dy) <= h; }

    // ---- Притяжение
    static final double PULL_RANGE = 32.0D, PULL_WIDTH = 1.3D;
    /** Расстояние вдоль луча t и поперёк perp для точки (dx,dy,dz) от глаз и единичного взгляда. */
    static double[] toRay(double dx, double dy, double dz, double lx, double ly, double lz) {
        double t = dx * lx + dy * ly + dz * lz;
        double px = dx - lx * t, py = dy - ly * t, pz = dz - lz * t;
        return new double[]{t, Math.sqrt(px * px + py * py + pz * pz)};
    }
    static double pullSpeed(double dist) { return Math.min(1.6D, 0.5D + dist * 0.05D); }

    // ---- Двойник
    static final int DECOY_TICKS = 100, DECOY_RETARGET = 10;
    static final double DECOY_R = 16.0D, DECOY_AHEAD = 3.0D;

    // ---- Искры
    static final int SPARK_COUNT = 3, SPARK_STEPS = 40, SPARK_MAX = 24, SPARK_FIRE_SECONDS = 4;
    static final double SPARK_SPEED = 0.6D, SPARK_TURN = 0.5D, SPARK_HIT = 0.9D, SPARK_SEEK = 24.0D;
    static final float SPARK_DAMAGE = 4.0F;
    /** Новая скорость: старое направление плюс притяжение к цели (turn - сила), длина остаётся speed. */
    static double[] steer(double[] v, double[] to, double turn, double speed) {
        double tl = Math.sqrt(to[0] * to[0] + to[1] * to[1] + to[2] * to[2]);
        double x = v[0], y = v[1], z = v[2];
        double vl = Math.sqrt(x * x + y * y + z * z);
        if (vl > 1.0E-6D) { x /= vl; y /= vl; z /= vl; }
        if (tl > 1.0E-6D) { x += to[0] / tl * turn; y += to[1] / tl * turn; z += to[2] / tl * turn; }
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1.0E-6D) return new double[]{0, 0, 0};
        return new double[]{x / l * speed, y / l * speed, z / l * speed};
    }
    /** Поворот взгляда вокруг вертикали на deg градусов (веер искр). */
    static double[] fan(double lx, double ly, double lz, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new double[]{lx * c - lz * s, ly, lx * s + lz * c};
    }

    // ---- Тепло
    static final double HEAT_R = 8.0D;
    static final float HEAT_HEAL = 8.0F;
    static final int HEAT_REGEN_TICKS = 100, HEAT_REGEN_AMP = 1;
}
