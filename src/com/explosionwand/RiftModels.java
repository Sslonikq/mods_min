package com.explosionwand;

/**
 * Облик мобов Разлома: набор block_display (тонкие повёрнутые детали, свечение) и поза по параметрам.
 * Координаты в позе — локальные, от ног моба (y вверх, +z вперёд); поворот по yaw и сдвиг на высоту посадки делает RiftMobs.
 * Чистая математика без Minecraft: проверяется tests/RiftMobsTest.java.
 */
final class RiftModels {
    private RiftModels() {}

    enum Kind {
        SHARDLING("shardling", "spider", 20, 1.0,
                new String[]{Disp.block("obsidian"), Disp.block("white_concrete"), Disp.block("amethyst_block"), Disp.block("amethyst_block"), Disp.block("smooth_basalt"), Disp.block("smooth_basalt")},
                new int[]{6, 15, 12, 12, 4, 4}),
        SHARDLING_MINI("shardling_mini", "cave_spider", 6, 0.55,
                new String[]{Disp.block("obsidian"), Disp.block("white_concrete"), Disp.block("amethyst_block"), Disp.block("amethyst_block"), Disp.block("smooth_basalt"), Disp.block("smooth_basalt")},
                new int[]{6, 15, 12, 12, 4, 4}),
        ARCHER("crystal_archer", "skeleton", 30, 1.0,
                new String[]{Disp.block("black_concrete"), Disp.block("purple_concrete"), Disp.block("purple_concrete"), Disp.block("amethyst_block"), Disp.block("amethyst_block"),
                        Disp.block("amethyst_block"), Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("white_concrete"), Disp.block("amethyst_block"),
                        Disp.block("purple_stained_glass"), Disp.block("amethyst_block"), Disp.block("amethyst_block"), Disp.block("amethyst_block")},
                new int[]{5, 8, 8, 13, 13, 13, 5, 5, 15, 13, 12, 12, 12, 12}),
        SHADE("rift_shade", "zombie", 26, 1.0,
                new String[]{Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("white_concrete"),
                        Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("amethyst_block"), Disp.block("amethyst_block")},
                new int[]{2, 2, 2, 15, 2, 2, 15, 15}),
        PRISM("prism", "phantom", 24, 1.0,
                new String[]{Disp.block("purple_stained_glass"), Disp.block("amethyst_block"), Disp.block("amethyst_block"), Disp.block("amethyst_block"), Disp.block("amethyst_block"),
                        Disp.block("amethyst_block"), Disp.block("sea_lantern")},
                new int[]{15, 15, 13, 13, 13, 13, 15}),
        EATER("echo_eater", "polar_bear", 60, 1.0,
                new String[]{Disp.block("calcite"), Disp.block("purple_terracotta"), Disp.block("black_concrete"), Disp.block("white_concrete"), Disp.block("magenta_stained_glass"),
                        Disp.block("magenta_stained_glass"), Disp.block("gray_concrete"), Disp.block("gray_concrete"), Disp.block("gray_concrete"), Disp.block("gray_concrete")},
                new int[]{6, 6, 2, 8, 10, 10, 4, 4, 4, 4}),
        GOLEM("amethyst_golem", "ravager", 200, 1.0,
                new String[]{Disp.block("deepslate_tiles"), Disp.block("deepslate_tiles"), Disp.block("polished_deepslate"), Disp.block("purple_stained_glass"), Disp.block("deepslate"),
                        Disp.block("magenta_stained_glass"), Disp.block("smooth_basalt"), Disp.block("deepslate"), Disp.block("smooth_basalt"),
                        Disp.block("purple_concrete"), Disp.block("purple_stained_glass"),
                        Disp.block("amethyst_block"), Disp.block("amethyst_block"), Disp.block("amethyst_block")},
                new int[]{3, 3, 4, 15, 4, 15, 4, 4, 4, 9, 15, 13, 13, 13}),
        BEETLE("crystal_beetle", "rabbit", 4, 1.0,
                new String[]{Disp.block("amethyst_block"), Disp.block("black_concrete"), Disp.block("sea_lantern"), Disp.block("black_concrete"), Disp.block("black_concrete")},
                new int[]{13, 4, 15, 4, 4}),
        // ---- Город Спящих (4.6): бирюзовый и чёрный, облик в CityModels
        LUNATIC("lunatic", "husk", 14, 1.0,
                new String[]{Disp.block("cyan_terracotta"), Disp.block("cyan_terracotta"), Disp.block("white_concrete"), Disp.block("black_concrete"), Disp.block("sea_lantern"),
                        Disp.block("white_concrete"), Disp.block("white_concrete"), Disp.block("sea_lantern"), Disp.block("sea_lantern"), Disp.block("sea_lantern")},
                new int[]{4, 4, 8, 0, 15, 8, 8, 15, 15, 15}),
        DROWSY("drowsy", "vex", 12, 1.0,
                new String[]{Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("sea_lantern"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass"),
                        Disp.block("cyan_concrete"), Disp.block("sea_lantern")},
                new int[]{3, 3, 15, 13, 13, 9, 15}),
        STITCH_GUARD("stitch_guard", "husk", 30, 1.0,
                new String[]{Disp.block("cyan_concrete"), Disp.block("cyan_concrete"), Disp.block("cyan_concrete"), Disp.block("prismarine_bricks"), Disp.block("sea_lantern"),
                        Disp.block("prismarine_bricks"), Disp.block("prismarine_bricks"), Disp.block("cyan_concrete"), Disp.block("iron_block"),
                        Disp.block("dark_prismarine"), Disp.block("sea_lantern"), Disp.block("cyan_concrete")},
                new int[]{6, 6, 8, 9, 15, 9, 9, 8, 10, 5, 15, 8}),
        NIGHT_MOTH("night_moth", "phantom", 10, 1.0,
                new String[]{Disp.block("cyan_concrete"), Disp.block("smooth_sandstone"), Disp.block("smooth_sandstone"),
                        Disp.block("cyan_concrete"), Disp.block("smooth_sandstone"), Disp.block("smooth_sandstone"),
                        Disp.block("cyan_concrete"), Disp.block("smooth_sandstone"), Disp.block("smooth_sandstone"),
                        Disp.block("cyan_concrete"), Disp.block("smooth_sandstone"), Disp.block("smooth_sandstone"),
                        Disp.block("cyan_concrete"), Disp.block("smooth_sandstone"), Disp.block("smooth_sandstone")},
                new int[]{15, 9, 9, 15, 9, 9, 15, 9, 9, 15, 9, 9, 15, 9, 9}),
        // ---- Город Спящих, часть B: Сновидец с копиями, Книжный страж, Статуя-страж, Колыбельщик, Туманный странник (облик в CityModels)
        DREAMER("dreamer", "evoker", 36, 1.0,
                new String[]{Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("sea_lantern"), Disp.block("cyan_stained_glass"), Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("sea_lantern"), Disp.block("sea_lantern"), Disp.block("sea_lantern"), Disp.block("cyan_concrete"), Disp.block("cyan_stained_glass")},
                new int[]{3, 3, 3, 15, 12, 3, 3, 15, 15, 15, 8, 12}),
        DREAM_CLONE("dream_clone", "evoker", 1, 1.0,
                new String[]{Disp.block("black_stained_glass"), Disp.block("black_stained_glass"), Disp.block("black_stained_glass"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass"), Disp.block("black_stained_glass"), Disp.block("black_stained_glass"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass")},
                new int[]{2, 2, 2, 10, 8, 2, 2, 10, 10, 10, 6, 8}),
        BOOK_GUARD("book_guard", "phantom", 14, 1.0,
                new String[]{Disp.block("smooth_sandstone"), Disp.block("smooth_sandstone"), Disp.block("dark_oak_planks"), Disp.block("sea_lantern"), Disp.block("smooth_sandstone"), Disp.block("smooth_sandstone"), Disp.block("dark_oak_planks"), Disp.block("sea_lantern"), Disp.block("smooth_sandstone"), Disp.block("smooth_sandstone"), Disp.block("dark_oak_planks"), Disp.block("sea_lantern"), Disp.block("smooth_sandstone"), Disp.block("smooth_sandstone"), Disp.block("smooth_sandstone")},
                new int[]{8, 8, 3, 15, 8, 8, 3, 15, 8, 8, 3, 15, 9, 9, 9}),
        STATUE_GUARD("statue_guard", "husk", 34, 1.0,
                new String[]{Disp.block("polished_deepslate"), Disp.block("cyan_terracotta"), Disp.block("light_gray_concrete"), Disp.block("sea_lantern"), Disp.block("gray_stained_glass"), Disp.block("gray_stained_glass"), Disp.block("cyan_terracotta"), Disp.block("cyan_terracotta"), Disp.block("sea_lantern"), Disp.block("cyan_stained_glass")},
                new int[]{4, 4, 4, 15, 6, 6, 4, 4, 15, 10}),
        CRADLER("cradler", "iron_golem", 140, 1.0,
                new String[]{Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("black_concrete"), Disp.block("sea_lantern"), Disp.block("black_concrete"), Disp.block("deepslate"), Disp.block("black_concrete"), Disp.block("gray_concrete"), Disp.block("sea_lantern"), Disp.block("dark_prismarine"), Disp.block("dark_prismarine"), Disp.block("cyan_stained_glass"), Disp.block("black_concrete"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass"), Disp.block("cyan_stained_glass")},
                new int[]{1, 1, 1, 1, 15, 1, 2, 1, 2, 15, 4, 4, 12, 1, 13, 13, 13, 13, 13, 13}),
        FOG_WALKER("fog_walker", "husk", 14, 1.0,
                new String[]{Disp.block("gray_concrete"), Disp.block("light_gray_concrete"), Disp.block("sea_lantern"), Disp.block("sea_lantern"), Disp.block("gray_concrete"), Disp.block("gray_concrete"), Disp.block("iron_block"), Disp.block("white_stained_glass"), Disp.block("white_stained_glass"), Disp.block("white_stained_glass"), Disp.block("white_stained_glass"), Disp.block("gray_concrete"), Disp.block("gray_concrete")},
                new int[]{2, 3, 15, 15, 2, 2, 5, 8, 8, 8, 8, 2, 2});

        final String id, host, hostId; final int hp; final double sc; final String[] blocks; final int[] light;
        Kind(String id, String host, int hp, double sc, String[] blocks, int[] light) {
            this.id = id; this.host = host; this.hostId = "minecraft:" + host; this.hp = hp; this.sc = sc; this.blocks = blocks; this.light = light;
        }
        int parts() { return blocks.length; }
        static Kind byId(String id) { for (Kind k : values()) if (k.id.equals(id)) return k; return null; }
    }

    /** Параметры позы; не нужные модели поля остаются нулями. */
    static final class Pose {
        double t, ph, atk, strike, e = 1, crack, charge, jaw, ears, lift, open, aim, stun, cocoon, wake;
        double[] cr = {1, 1, 1};
        double sc = 1;
    }

    static final int W = 10;   // cx, cy, cz, sx, sy, sz, qx, qy, qz, qw

    static void put(double[][] o, int i, double cx, double cy, double cz, double sx, double sy, double sz, double[] q) {
        double[] r = o[i];
        r[0] = cx; r[1] = cy; r[2] = cz; r[3] = sx; r[4] = sy; r[5] = sz; r[6] = q[0]; r[7] = q[1]; r[8] = q[2]; r[9] = q[3];
    }

    /** Конечность, подвешенная за плечо (sx,sy,sz): длина len вниз, поворот q вокруг плеча. */
    static void limb(double[][] o, int i, double sx, double sy, double sz, double len, double th, double[] q) {
        double[] c = Disp.rot(q, 0, -len / 2, 0);
        put(o, i, sx + c[0], sy + c[1], sz + c[2], th, len, th, q);
    }

    /** Клинок, растущий вверх от основания (bx,by,bz). */
    static void blade(double[][] o, int i, double bx, double by, double bz, double h, double w, double[] q) {
        double[] c = Disp.rot(q, 0, h / 2, 0);
        put(o, i, bx + c[0], by + c[1], bz + c[2], w, h, w, q);
    }

    static double[] qx(double a) { return Disp.q(1, 0, 0, a); }
    static double[] qy(double a) { return Disp.q(0, 1, 0, a); }
    static double[] qz(double a) { return Disp.q(0, 0, 1, a); }
    static double[] mul(double[]... qs) { double[] r = Disp.ID; for (double[] q : qs) r = Disp.mul(r, q); return r; }

    static double[][] pose(Kind k, Pose p) {
        double[][] o = new double[k.parts()][W];
        switch (k) {
            case SHARDLING, SHARDLING_MINI -> shardling(o, p);
            case ARCHER -> archer(o, p);
            case SHADE -> shade(o, p);
            case PRISM -> prism(o, p);
            case EATER -> eater(o, p);
            case GOLEM -> golem(o, p);
            case BEETLE -> beetle(o, p);
            case LUNATIC, DROWSY, STITCH_GUARD, NIGHT_MOTH, DREAMER, DREAM_CLONE, BOOK_GUARD, STATUE_GUARD, CRADLER, FOG_WALKER -> CityModels.pose(k, o, p);
        }
        if (p.sc != 1) for (double[] r : o) for (int j = 0; j < 6; j++) r[j] *= p.sc;
        return o;
    }

    // ---------------------------------------------------------------- Осколочник: тёмный ползун, на спине два кристалла
    private static void shardling(double[][] o, Pose p) {
        double bob = Math.sin(p.t * 0.3) * 0.02, a = p.atk, lunge = a * 0.25, sway = Math.sin(p.t * 0.12) * 0.05;
        put(o, 0, 0, 0.36 + bob - a * 0.06, lunge * 0.5, 0.8, 0.42, 1.0, Disp.ID);
        put(o, 1, 0, 0.42 - a * 0.06, 0.5 + lunge * 0.5, 0.6, 0.08, 0.05, Disp.ID);
        blade(o, 2, -0.2, 0.55, -0.25, 0.85 + a * 0.15, 0.2, mul(qz(0.45 + a * 0.5 + sway), qx(-0.35 - a * 0.4)));
        blade(o, 3, 0.2, 0.55, -0.1, 0.75 + a * 0.15, 0.17, mul(qz(-0.5 - a * 0.4 - sway), qx(-0.2 - a * 0.3)));
        double sw = Math.sin(p.ph) * 0.3;
        put(o, 4, -0.62, 0.2, 0, 0.5, 0.08, 1.1, mul(qy(sw), qz(0.35)));
        put(o, 5, 0.62, 0.2, 0, 0.5, 0.08, 1.1, mul(qy(-sw), qz(-0.35)));
    }

    // ---------------------------------------------------------------- Кристальный лучник: стройная фигура в мантии на колонне, кристальная корона
    // Ноги стоят на верху колонны (y = 0 - блок под меткой); парения и плит нет. Кокон: стеклянная оболочка и три аметистовых клинка, смыкающихся сверху.
    private static void archer(double[][] o, Pose p) {
        double c = Math.max(0, Math.min(1, p.cocoon));
        double bob = Math.sin(p.t * 0.1) * 0.015, pulse = 1 + Math.sin(p.t * 0.2) * 0.06;
        put(o, 0, 0, 0.55, 0, 0.36, 1.1, 0.26, Disp.ID);
        put(o, 1, 0, 0.07, 0, 0.5, 0.14, 0.38, Disp.ID);
        put(o, 2, 0, 1.08, 0, 0.54, 0.12, 0.28, Disp.ID);
        put(o, 3, 0, 1.36 + bob, 0, 0.3, 0.3, 0.3, qy(p.t * 0.03));
        blade(o, 4, 0, 1.5 + bob, 0, 0.62 * pulse, 0.1, Disp.ID);
        blade(o, 5, 0.08, 1.5 + bob, 0, 0.4 * pulse, 0.08, qz(-0.5));
        limb(o, 6, -0.31, 1.08, 0, 0.55, 0.09, qx(-0.2));
        limb(o, 7, 0.31, 1.08, 0.05, 0.5, 0.09, qx(-1.45));
        double len = 1.0 + p.charge * 0.3;
        put(o, 8, 0.31, 1.0, 0.3 + len / 2, 0.04 + p.charge * 0.03, 0.04 + p.charge * 0.03, len, Disp.ID);
        double oa = p.t * 0.06;
        put(o, 9, Math.cos(oa) * 0.6, 1.2 + Math.sin(p.t * 0.09) * 0.12, Math.sin(oa) * 0.6, 0.1, 0.28, 0.1, mul(qy(-oa), qz(0.4)));
        put(o, 10, 0, 0.95, 0, 0.95 * c, 1.9 * c, 0.95 * c, Disp.ID);
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3 + 0.4;
            blade(o, 11 + i, Math.sin(a) * 0.45 * c, 0, Math.cos(a) * 0.45 * c, 2.0 * c, 0.22 * c, Disp.q(-Math.cos(a), 0, Math.sin(a), 0.22));
        }
    }

    // ---------------------------------------------------------------- Тень Разлома: высокая, тонкая; встаёт прямо из пола (ни плит, ни трещины-модели)
    private static void shade(double[][] o, Pose p) {
        double h = Math.max(0, Math.min(1, p.e));
        put(o, 0, 0, 0.5 * h, 0, 0.2 * h, 1.0 * h, 0.2 * h, Disp.ID);
        put(o, 1, 0, 1.43 * h, 0, 0.42 * h, 0.85 * h, 0.22 * h, Disp.ID);
        put(o, 2, 0, 2.15 * h, 0.02 * h, 0.4 * h, 0.4 * h, 0.4 * h, Disp.ID);
        put(o, 3, 0, 2.17 * h, 0.22 * h, 0.28 * h * (1 + p.atk * 0.3), 0.06 * h * (1 + p.atk), 0.04 * h, Disp.ID);
        double sway = Math.sin(p.t * 0.1) * 0.06;
        double a = -0.12 - 2.5 * p.atk + 2.2 * p.strike;
        double[] ql = qx(a + sway), qr = qx(a * 0.85 - sway);
        double len = 1.15 * h;
        limb(o, 4, -0.32 * h, 1.8 * h, 0.1 * h, len, 0.07 * h, ql);
        limb(o, 5, 0.32 * h, 1.8 * h, 0.1 * h, len, 0.07 * h, qr);
        double[] cl = Disp.rot(ql, 0, -len - 0.15 * h, 0), cr2 = Disp.rot(qr, 0, -len - 0.15 * h, 0);
        put(o, 6, -0.32 * h + cl[0], 1.8 * h + cl[1], 0.1 * h + cl[2], 0.05 * h, 0.3 * h, 0.05 * h, ql);
        put(o, 7, 0.32 * h + cr2[0], 1.8 * h + cr2[1], 0.1 * h + cr2[2], 0.05 * h, 0.3 * h, 0.05 * h, qr);
    }

    // ---------------------------------------------------------------- Призма: вращающийся куб и осколки вокруг
    private static void prism(double[][] o, Pose p) {
        double bob = Math.sin(p.t * 0.08) * 0.08, cy = 0.25 + bob;
        put(o, 0, 0, cy, 0, 0.85, 0.85, 0.85, mul(qy(p.t * 0.05), qx(p.t * 0.037 + 0.6)));
        put(o, 1, 0, cy, 0, 0.42, 0.42, 0.42, mul(qy(-p.t * 0.08), qz(p.t * 0.05)));
        for (int i = 0; i < 4; i++) {
            double ang = p.t * 0.07 + i * Math.PI / 2, r = 0.8 + 0.1 * Math.sin(p.t * 0.1 + i);
            put(o, 2 + i, Math.cos(ang) * r, cy + 0.15 * Math.sin(ang * 2), Math.sin(ang) * r, 0.12, 0.34, 0.12, mul(qy(-ang), qz(0.4)));
        }
        double em = 0.16 + p.charge * 0.16;
        put(o, 6, 0, cy - 0.5, 0, em, em, em, qy(p.t * 0.2));
    }

    // ---------------------------------------------------------------- Пожиратель эха: бледный череп, чёрная челюсть, уши
    private static void eater(double[][] o, Pose p) {
        double lift = p.lift * 0.15;
        put(o, 0, 0, 0.95 + lift, 0.05, 0.9, 0.55, 1.0, qx(-p.lift * 0.2));
        put(o, 1, 0, 1.28 + lift, -0.1, 0.7, 0.2, 0.5, Disp.ID);
        double[] jq = qx(p.jaw), hinge = {0, 0.67 + lift, -0.4};
        double[] jc = Disp.rot(jq, 0, -0.11, 0.45), tc = Disp.rot(jq, 0, 0.0, 0.88);
        put(o, 2, hinge[0] + jc[0], hinge[1] + jc[1], hinge[2] + jc[2], 0.8, 0.22, 0.9, jq);
        put(o, 3, hinge[0] + tc[0], hinge[1] + tc[1], hinge[2] + tc[2], 0.7, 0.1, 0.07, jq);
        double ear = 0.5 + 0.6 * p.ears;
        put(o, 4, -0.5, 1.05 + lift, -0.05, 0.05, 0.5 + 0.2 * p.ears, 0.45 + 0.2 * p.ears, qz(ear));
        put(o, 5, 0.5, 1.05 + lift, -0.05, 0.05, 0.5 + 0.2 * p.ears, 0.45 + 0.2 * p.ears, qz(-ear));
        int n = 0;
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                double sw = Math.sin(p.ph + n * Math.PI / 2) * 0.5;
                limb(o, 6 + n, sx * 0.45, 0.7, sz * 0.35, 0.7, 0.1, qx(sw));
                n++;
            }
    }

    // ---------------------------------------------------------------- Аметистовый голем: камень, щит спереди, кристаллы на спине
    private static void golem(double[][] o, Pose p) {
        double sw = Math.sin(p.ph) * 0.35, st = p.stun, bob = Math.sin(p.t * 0.07) * 0.02;
        limb(o, 0, -0.4, 1.0, 0, 1.0, 0.5, qx(sw));
        limb(o, 1, 0.4, 1.0, 0, 1.0, 0.5, qx(-sw));
        double lean = st * 0.12;
        put(o, 2, 0, 1.65 + bob, 0, 1.5, 1.2, 0.9, qx(lean));
        put(o, 3, 0, 1.75 + bob, 0.47, 0.6, 0.5, 0.05, Disp.ID);
        put(o, 4, 0, 2.5 + bob - st * 0.12, 0.1 + st * 0.1, 0.65, 0.6, 0.65, qx(st * 0.4));
        put(o, 5, 0, 2.52 + bob - st * 0.12, 0.45 + st * 0.1, 0.5 * (1 - st), 0.1, 0.05, Disp.ID);
        double a = -0.1 - 2.3 * p.atk + 2.0 * p.strike;
        double[] qa = qx(a);
        limb(o, 6, 0.95, 2.1, 0, 1.3, 0.45, qa);
        double[] f = Disp.rot(qa, 0, -1.55, 0);
        put(o, 7, 0.95 + f[0], 2.1 + f[1], f[2], 0.6, 0.6, 0.6, qa);
        limb(o, 8, -0.95, 2.1, 0, 1.3, 0.45, qx(-1.2 * (1 - p.open) - 0.1));
        double up = 1 - p.open;
        double ox = -0.7 * p.open, oy = 1.9 - 1.45 * p.open, oz = 0.8 - 0.1 * p.open;
        put(o, 9, ox, oy, oz, 1.5, 1.9, 0.22, qz(1.3 * p.open));
        put(o, 10, ox, oy, oz + 0.13, 1.2 * (0.4 + 0.6 * up) + 0.001, 1.6 * (0.4 + 0.6 * up) + 0.001, 0.06, qz(1.3 * p.open));
        double[][] cb = {{-0.45, 2.1, -0.5, 0.35, 1.1, 0.25}, {0.1, 2.2, -0.55, 0.38, 1.3, -0.1}, {0.5, 2.05, -0.45, 0.32, 0.95, -0.3}};
        for (int i = 0; i < 3; i++) {
            double s = Math.max(0, Math.min(1, p.cr[i]));
            double[] q = mul(qz(cb[i][5]), qx(-0.35 + Math.sin(p.t * 0.1 + i) * 0.03));
            blade(o, 11 + i, cb[i][0], cb[i][1] + bob, cb[i][2], cb[i][4] * s, cb[i][3] * s, q);
        }
    }

    // ---------------------------------------------------------------- Кристальный жук: мирный, светится
    private static void beetle(double[][] o, Pose p) {
        double wob = Math.sin(p.t * 0.5 + p.ph) * 0.03 * p.atk;
        put(o, 0, 0, 0.14, 0, 0.3, 0.16, 0.4, qy(wob));
        put(o, 1, 0, 0.12, 0.26, 0.16, 0.12, 0.14, Disp.ID);
        double glow = 0.18 + Math.sin(p.t * 0.15) * 0.03;
        put(o, 2, 0, 0.23, -0.02, glow, 0.05, glow + 0.04, Disp.ID);
        double sw = Math.sin(p.ph) * 0.25;
        put(o, 3, -0.2, 0.06, 0, 0.3, 0.04, 0.34, mul(qy(sw), qz(0.3)));
        put(o, 4, 0.2, 0.06, 0, 0.3, 0.04, 0.34, mul(qy(-sw), qz(-0.3)));
    }
}
