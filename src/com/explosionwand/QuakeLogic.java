package com.explosionwand;

import java.util.*;

/** Чистая логика Палочки Землетрясения: форма трещин (проверяется тестом без игры). */
final class QuakeLogic {
    static final int MAX_DEPTH = 8;

    /** Клетка трещины: смещение от эпицентра (x, z), глубина в блоках, тик раскрытия (от начала разлома). */
    static final class Cell {
        final int x, z, depth, tick;
        Cell(int x, int z, int depth, int tick) { this.x = x; this.z = z; this.depth = depth; this.tick = tick; }
    }

    /**
     * Трещины от эпицентра: провал 3x3 в центре (если pit), count лучей (первый смотрит по baseAngle) длиной около len,
     * у каждого луча одна ветка. Луч идёт зигзагом, у центра шириной 2 и глубже, к концу сужается и мелеет.
     * Клетки отсортированы по тику раскрытия, повторов нет.
     */
    static List<Cell> cracks(Random r, double baseAngle, int count, int len, boolean pit) {
        List<Cell> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (pit) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) add(out, seen, dx, dz, MAX_DEPTH, 0, -1);
        for (int k = 0; k < count; k++) {
            double a = baseAngle + k * Math.PI * 2 / count + (k == 0 ? 0 : (r.nextDouble() - 0.5) * 0.6);
            int L = Math.max(6, len - 4 + r.nextInt(9));
            int branchAt = L / 3 + r.nextInt(Math.max(1, L / 3));
            walk(r, out, seen, 0, 0, a, L, 1, MAX_DEPTH - 1, true, branchAt, pit ? 0 : 2);
        }
        out.sort(Comparator.comparingInt(c -> c.tick));
        return out;
    }

    static void walk(Random r, List<Cell> out, Set<Long> seen, double x, double z, double a, int L, int t0, int depth0,
                     boolean main, int branchAt, int skipNear) {
        final double base = a;
        for (int i = 0; i < L; i++) {
            a += r.nextGaussian() * 0.3;
            a = base + Math.max(-0.7, Math.min(0.7, a - base));          // зигзаг, но в целом по своему лучу
            x += Math.cos(a); z += Math.sin(a);
            double f = 1.0 - i / (double) L;
            int depth = Math.max(2, (int) Math.round(depth0 * (0.4 + 0.6 * f)));
            int tick = t0 + i;
            add(out, seen, (int) Math.floor(x), (int) Math.floor(z), depth, tick, skipNear);
            if (main && f > 0.4)                                      // у центра трещина шириной 2
                add(out, seen, (int) Math.floor(x - Math.sin(a)), (int) Math.floor(z + Math.cos(a)), Math.max(2, depth - 1), tick, skipNear);
            if (main && i == branchAt) {
                double side = r.nextBoolean() ? 1 : -1;
                walk(r, out, seen, x, z, a + side * (0.6 + r.nextDouble() * 0.5), L / 3 + 3, tick + 1, Math.max(3, depth - 1), false, -1, skipNear);
            }
        }
    }

    /** skipNear >= 0: клетки ближе skipNear к центру (у ног хозяина) не открываем. */
    static void add(List<Cell> out, Set<Long> seen, int x, int z, int depth, int tick, int skipNear) {
        if (Math.abs(x) <= skipNear && Math.abs(z) <= skipNear) return;
        if (seen.add(key(x, z))) out.add(new Cell(x, z, Math.min(MAX_DEPTH, depth), tick));
    }

    static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
}
