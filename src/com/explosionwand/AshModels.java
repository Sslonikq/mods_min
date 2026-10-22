package com.explosionwand;

import com.explosionwand.RiftModels.Kind;
import com.explosionwand.RiftModels.Pose;

import static com.explosionwand.RiftModels.blade;
import static com.explosionwand.RiftModels.limb;
import static com.explosionwand.RiftModels.mul;
import static com.explosionwand.RiftModels.put;
import static com.explosionwand.RiftModels.qx;
import static com.explosionwand.RiftModels.qy;
import static com.explosionwand.RiftModels.qz;

/**
 * Облик мобов Пепельного святилища (4.9) по docs/concepts/mobs_ash.png: уголь и камень, горячие швы (magma_block, glowstone) в стиле Кита и Разлома.
 * Координаты локальные от ног хоста (y вверх, +z вперёд). Поля позы: atk - замах, strike - удар, charge - зарядка/каст, lift - щит опущен,
 * stun - открыт (спина Стража и Хранителя горит), open - высунулся (Червь), aim - «ловушка» у Огонька, crack - ярость Хранителя.
 */
final class AshModels {
    private AshModels() {}

    static void pose(Kind k, double[][] o, Pose p) {
        switch (k) {
            case ASH_WALKER -> walker(o, p);
            case ASH_SPARK -> spark(o, p);
            case ASH_HOUND -> hound(o, p);
            case ASH_RINGER -> ringer(o, p);
            case ASH_WORM -> worm(o, p);
            case ASH_PRIEST -> priest(o, p);
            case ASH_GUARD -> guard(o, p);
            case ASH_KEEPER -> keeper(o, p);
            case ASH_WISP -> wisp(o, p);
            default -> { }
        }
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    // ---------------------------------------------------------------- Пеплоход: медленная обугленная глыба, в груди тлеющая трещина, в правой руке каменная дубина
    private static void walker(double[][] o, Pose p) {
        double sw = Math.sin(p.ph) * 0.4, bob = Math.sin(p.t * 0.08) * 0.015, at = clamp(p.atk), st = clamp(p.strike);
        double a = -0.15 - 2.6 * at - 1.3 * st, lean = 0.1 * st - 0.06 * at;
        limb(o, 0, -0.2, 0.8, 0, 0.8, 0.26, qx(sw));
        limb(o, 1, 0.2, 0.8, 0, 0.8, 0.26, qx(-sw));
        double[] tq = qx(lean), hq = qx(lean * 1.5);
        put(o, 2, 0, 1.2 + bob, 0, 0.72, 0.8, 0.42, tq);
        put(o, 3, 0, 1.22 + bob, 0.215, 0.07, 0.6, 0.02, tq);
        put(o, 4, 0, 1.8 + bob, 0.03, 0.44, 0.44, 0.44, hq);
        put(o, 5, 0, 1.82 + bob, 0.255, 0.32, 0.06, 0.02, hq);
        limb(o, 6, -0.48, 1.55 + bob, 0, 0.85, 0.22, qx(-sw * 0.6));
        double[] qa = qx(a);
        limb(o, 7, 0.48, 1.55 + bob, 0, 0.85, 0.22, qa);
        double[] h = Disp.rot(qa, 0, -1.05, 0);
        put(o, 8, 0.48 + h[0], 1.55 + bob + h[1], h[2], 0.36, 0.36, 0.36, qa);
        put(o, 9, -0.4, 1.66 + bob, 0, 0.16, 0.16, 0.16, qy(p.t * 0.05));
    }

    // ---------------------------------------------------------------- Искра: огонёк-ядро в стеклянной оболочке, крылышки, три искры по орбите; charge - прицел
    private static void spark(double[][] o, Pose p) {
        double fl = Math.sin(p.t * 0.7) * 0.5, bob = Math.sin(p.t * 0.1) * 0.05, ch = clamp(p.charge), cs = 0.28 + 0.16 * ch;
        put(o, 0, 0, 0.3 + bob, 0, cs, cs, cs, mul(qy(p.t * 0.07), qx(p.t * 0.05)));
        double sh = 0.5 + 0.12 * ch + 0.03 * Math.sin(p.t * 0.3);
        put(o, 1, 0, 0.3 + bob, 0, sh, sh, sh, mul(qy(-p.t * 0.05), qz(p.t * 0.04)));
        put(o, 2, -0.36, 0.34 + bob, 0, 0.45, 0.03, 0.25, qz(-(0.3 + fl)));
        put(o, 3, 0.36, 0.34 + bob, 0, 0.45, 0.03, 0.25, qz(0.3 + fl));
        put(o, 4, 0, 0.1 + bob, 0, 0.09, 0.14 + 0.1 * ch, 0.09, qy(p.t * 0.2));
        for (int i = 0; i < 3; i++) {
            double ang = p.t * (0.09 + 0.08 * ch) + i * Math.PI * 2 / 3, r = 0.52 - 0.12 * ch;
            put(o, 5 + i, Math.cos(ang) * r, 0.3 + bob + 0.1 * Math.sin(ang * 2), Math.sin(ang) * r, 0.1, 0.1, 0.1, qy(p.t * 0.2 + i));
        }
    }

    // ---------------------------------------------------------------- Пепельная гончая: низкая угольная собака, по хребту тлеет линия; atk - присед перед прыжком
    private static void hound(double[][] o, Pose p) {
        double sw = Math.sin(p.ph) * 0.6, cr = clamp(p.atk), low = 0.14 * cr, tw = Math.sin(p.t * 0.3) * 0.15;
        put(o, 0, 0, 0.58 - low, 0, 0.5, 0.46, 0.95, qx(-0.05 * cr));
        put(o, 1, 0, 0.78 - low * 0.5, 0.62 + 0.08 * cr, 0.38, 0.34, 0.4, Disp.ID);
        put(o, 2, 0, 0.7 - low * 0.5, 0.88 + 0.08 * cr, 0.2, 0.16, 0.22, Disp.ID);
        put(o, 3, 0, 0.84 - low * 0.5, 0.84 + 0.08 * cr, 0.32, 0.05, 0.02, Disp.ID);
        limb(o, 4, -0.17, 0.4, 0.35, 0.4, 0.12, qx(sw));
        limb(o, 5, 0.17, 0.4, 0.35, 0.4, 0.12, qx(-sw));
        limb(o, 6, -0.17, 0.4, -0.35, 0.4, 0.12, qx(-sw));
        limb(o, 7, 0.17, 0.4, -0.35, 0.4, 0.12, qx(sw));
        put(o, 8, 0, 0.84 - low, -0.02, 0.08, 0.08, 0.9 + 0.1 * Math.sin(p.t * 0.2), Disp.ID);
        blade(o, 9, 0, 0.7 - low, -0.48, 0.55, 0.07, mul(qx(-0.9 + tw), qz(tw * 0.5)));
    }

    // ---------------------------------------------------------------- Звонарь: сутулый в плаще, в руке шест с перекладиной и колоколом; charge - раскачка колокола
    private static void ringer(double[][] o, Pose p) {
        double ch = clamp(p.charge), bob = Math.sin(p.t * 0.07) * 0.02, sway = Math.sin(p.t * 0.6) * 0.55 * ch;
        put(o, 0, 0, 0.65 + bob, 0, 0.52, 1.3, 0.34, Disp.ID);
        put(o, 1, 0, 1.5 + bob, 0, 0.46, 0.4, 0.46, qx(0.05));
        put(o, 2, 0, 1.5 + bob, 0.235, 0.28, 0.06, 0.02, Disp.ID);
        limb(o, 3, -0.32, 1.25 + bob, 0, 0.7, 0.12, qx(-0.2));
        limb(o, 4, 0.32, 1.25 + bob, 0, 0.7, 0.12, qx(-0.8 - 0.6 * ch));
        blade(o, 5, 0.45, 0, 0.35, 2.3, 0.07, Disp.ID);
        put(o, 6, 0.45, 2.3, 0.35, 0.9, 0.06, 0.06, Disp.ID);
        double bx = 0.45 + Math.sin(sway) * 0.25;
        put(o, 7, bx, 1.98, 0.35, 0.3, 0.32, 0.3, qz(sway));
        put(o, 8, bx + Math.sin(sway) * 0.1, 1.76, 0.35, 0.1, 0.1, 0.1, qz(sway));
        for (int i = 0; i < 4; i++) {
            double f = (p.t * 0.012 + i / 4.0) % 1.0, a = p.t * 0.03 + i * Math.PI / 2, r = 0.9 + 0.5 * ch * f;
            double s = 0.06 * (1 - f * 0.5);
            put(o, 9 + i, Math.cos(a) * r, 0.3 + 1.3 * f, Math.sin(a) * r, s, s, s, qy(p.t * 0.1 + i));
        }
    }

    // ---------------------------------------------------------------- Магмовый червь: столб из каменных колец с магмовыми швами, пасть-жерло; open - высунулся, charge - плевок
    private static void worm(double[][] o, Pose p) {
        double e = clamp(p.open), ch = clamp(p.charge), sway = Math.sin(p.t * 0.1) * 0.06;
        double[] w = {0.95, 0.85, 0.75, 0.65, 0.55};
        for (int i = 0; i < 5; i++) put(o, i, sway * i * 0.4, (0.3 + 0.42 * i) * e, 0, w[i] * e, 0.45 * e, w[i] * e, qy(i * 0.5 + p.t * 0.01));
        double[] hq = qx(0.45 * ch);
        put(o, 5, sway * 2.2, 2.55 * e, 0.05 * e, 0.7 * e, 0.6 * e, 0.7 * e, hq);
        put(o, 6, sway * 2.2, (2.32 - 0.12 * ch) * e, 0.3 * e, 0.5 * e, 0.1 * e, 0.5 * e, qx(0.6 * ch));
        double core = (0.12 + 0.3 * ch) * e;
        put(o, 7, sway * 2.2, 2.5 * e, 0.34 * e, core, core, core, qy(p.t * 0.2));
        for (int i = 0; i < 3; i++) put(o, 8 + i, sway * (i + 0.5) * 0.4, (0.3 + 0.42 * i + 0.21) * e, 0, (w[i + 1] + 0.1) * e, 0.05 * e, (w[i + 1] + 0.1) * e, qy(i * 0.5 + p.t * 0.01));
    }

    // ---------------------------------------------------------------- Пепельный жрец: высокий в длинном одеянии, светящаяся полоса, посох с углём; charge - лечение (зелёный шар), atk - огненные круги
    private static void priest(double[][] o, Pose p) {
        double ch = clamp(p.charge), at = clamp(p.atk), bob = Math.sin(p.t * 0.06) * 0.02;
        put(o, 0, 0, 0.6, 0, 0.66, 1.2, 0.42, Disp.ID);
        put(o, 1, 0, 1.45 + bob, 0, 0.52, 0.7, 0.32, Disp.ID);
        put(o, 2, 0, 1.98 + bob, 0.02, 0.4, 0.4, 0.4, Disp.ID);
        put(o, 3, 0, 2.0 + bob, 0.225, 0.3, 0.05, 0.02, Disp.ID);
        put(o, 4, 0, 1.0, 0.215, 0.08, 1.0, 0.02, Disp.ID);
        double[] ql = qx(-0.3 - 1.2 * ch);
        limb(o, 5, -0.34, 1.7 + bob, 0, 0.75, 0.1, ql);
        limb(o, 6, 0.34, 1.7 + bob, 0, 0.75, 0.1, qx(-0.4 - 0.5 * at));
        blade(o, 7, 0.45, 0, 0.35, 2.4, 0.06, Disp.ID);
        put(o, 8, 0.45, 2.55 + 0.05 * Math.sin(p.t * 0.1), 0.35, 0.24, 0.24, 0.24, mul(qy(p.t * 0.06), qx(0.6)));
        double[] hand = Disp.rot(ql, 0, -0.85, 0.2);
        double os = 0.12 + 0.24 * ch;
        put(o, 9, -0.34 + hand[0], 1.7 + bob + hand[1], hand[2], os, os, os, qy(p.t * 0.15));
        for (int i = 0; i < 8; i++) {
            double a = p.t * 0.03 * (1 + 2 * at) + i * Math.PI / 4, r = 1.4, s = 0.2 * (0.5 + 0.7 * at);
            put(o, 10 + i, Math.cos(a) * r, 0.04, Math.sin(a) * r, s, 0.03, s, qy(-a));
        }
    }

    // ---------------------------------------------------------------- Угольный страж: квадратный доспех, на груди щит с горящим крестом, булава; со спины - горячее ядро
    // atk - замах булавы (щит опущен: lift), strike - удар, stun - открыт (ядро на спине растёт), charge - присед перед ударом о землю
    static void guard(double[][] o, Pose p) {
        double sw = Math.sin(p.ph) * 0.3, bob = Math.sin(p.t * 0.06) * 0.015, at = clamp(p.atk), stk = clamp(p.strike), dn = clamp(p.lift), st = clamp(p.stun), ch = clamp(p.charge);
        double slump = 0.12 * st + 0.18 * ch;
        limb(o, 0, -0.3, 0.95, 0, 0.95, 0.34, qx(sw));
        limb(o, 1, 0.3, 0.95, 0, 0.95, 0.34, qx(-sw));
        double[] tq = qx(0.1 * stk - 0.1 * ch + 0.2 * st);
        put(o, 2, 0, 1.5 + bob - slump * 0.5, 0, 0.95, 1.0, 0.55, tq);
        double[] pq = qx(0.35 * dn);
        put(o, 3, 0, 1.5 + bob - slump * 0.5 - 0.5 * dn, 0.32, 0.86, 0.9, 0.08, pq);
        put(o, 4, 0, 1.5 + bob - slump * 0.5 - 0.5 * dn, 0.37, 0.08, 0.7, 0.02, pq);
        put(o, 5, 0, 1.55 + bob - slump * 0.5 - 0.5 * dn, 0.37, 0.55, 0.08, 0.02, pq);
        double[] hq = qx(0.2 * st + 0.15 * ch);
        put(o, 6, 0, 2.2 + bob - slump, 0.03, 0.5, 0.46, 0.5, hq);
        put(o, 7, 0, 2.22 + bob - slump, 0.285, 0.38, 0.06, 0.02, hq);
        limb(o, 8, -0.64, 1.9 + bob - slump * 0.5, 0, 0.9, 0.3, qx(-0.5 + 0.3 * dn));
        double a = -0.3 - 2.5 * at - 1.2 * stk - 0.6 * ch;
        double[] qa = qx(a);
        limb(o, 9, 0.64, 1.9 + bob - slump * 0.5, 0, 0.95, 0.3, qa);
        double[] h = Disp.rot(qa, 0, -0.95, 0), hd = Disp.rot(qa, 0, -1.85, 0), q2 = mul(qa, qx(Math.PI));
        double hx = 0.64, hy = 1.9 + bob - slump * 0.5;
        blade(o, 10, hx + h[0], hy + h[1], h[2], 0.9, 0.08, q2);
        put(o, 11, hx + hd[0], hy + hd[1], hd[2], 0.42, 0.42, 0.42, qa);
        put(o, 12, hx + hd[0], hy + hd[1], hd[2], 0.5, 0.14, 0.14, qa);
        put(o, 13, 0.7, 2.05 + bob - slump * 0.5, 0, 0.45, 0.18, 0.5, qz(-0.15));
        double cs = 0.55 * (0.25 + 0.75 * st) + 0.001;
        put(o, 14, 0, 1.55 + bob - slump * 0.5, -0.3, cs, cs, 0.06, tq);
    }

    // ---------------------------------------------------------------- Хранитель Углей: тот же доспех в 1,45 раза больше, на плечах оранжевые кристаллы, вокруг ног кольцо углей; crack - ярость
    private static void keeper(double[][] o, Pose p) {
        guard(o, p);
        double rage = clamp(p.crack), ch = clamp(p.charge), bob = Math.sin(p.t * 0.06) * 0.015;
        double[][] cb = {{-0.35, 2.3, -0.3, 0.7, 0.2, 0.3}, {0.05, 2.35, -0.35, 0.95, 0.24, -0.08}, {0.4, 2.3, -0.3, 0.6, 0.18, -0.3}};
        for (int i = 0; i < 3; i++) {
            double s = 1 + 0.5 * rage;
            blade(o, 15 + i, cb[i][0], cb[i][1] + bob, cb[i][2], cb[i][3] * s, cb[i][4], mul(qz(cb[i][5]), qx(-0.3 + Math.sin(p.t * 0.1 + i) * 0.03)));
        }
        for (int i = 0; i < 6; i++) {
            double a = p.t * 0.03 * (1 + rage + 2 * ch) + i * Math.PI / 3, r = 1.7 + 1.0 * ch, s = 0.3 * (0.6 + 0.6 * rage + 0.5 * ch);
            put(o, 18 + i, Math.cos(a) * r, 0.04, Math.sin(a) * r, s, 0.03, s, qy(-a));
        }
    }

    // ---------------------------------------------------------------- Блуждающий огонёк: ядро в стеклянной оболочке и хвост; aim - «ловушка»: оболочка дрожит; charge - вспышка
    private static void wisp(double[][] o, Pose p) {
        double bob = Math.sin(p.t * 0.12) * 0.06, ch = clamp(p.charge);
        double f = p.aim > 0.5 ? 0.85 + 0.2 * Math.sin(p.t * 1.3) : 1.0;
        double cs = 0.2 * f * (1 + ch);
        put(o, 0, 0, 0.3 + bob, 0, cs, cs, cs, mul(qy(p.t * 0.08), qx(p.t * 0.06)));
        double sh = 0.36 * (1 + 0.6 * ch);
        put(o, 1, 0, 0.3 + bob, 0, sh, sh, sh, mul(qy(-p.t * 0.05), qz(p.t * 0.04)));
        for (int i = 0; i < 3; i++) put(o, 2 + i, 0, 0.28 + bob - 0.02 * i, -0.3 - 0.2 * i, 0.09 - 0.02 * i, 0.09 - 0.02 * i, 0.09 - 0.02 * i, qy(p.t * 0.2 + i));
    }
}
