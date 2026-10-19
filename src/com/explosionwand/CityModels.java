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
 * Облик мобов Города Спящих: бирюзовое свечение на тёмном, тонкие повёрнутые детали (в стиле Кита и Разлома).
 * Координаты локальные от ног хоста; pose.wake — «глаза открыты» у Лунатика, pose.atk/strike — замах и удар, pose.charge — зарядка.
 */
final class CityModels {
    private CityModels() {}

    static void pose(Kind k, double[][] o, Pose p) {
        switch (k) {
            case LUNATIC -> lunatic(o, p);
            case DROWSY -> drowsy(o, p);
            case STITCH_GUARD -> guard(o, p);
            case NIGHT_MOTH -> moth(o, p);
            case DREAMER, DREAM_CLONE -> dreamer(o, p);
            case BOOK_GUARD -> bookGuard(o, p);
            case STATUE_GUARD -> statue(o, p);
            case CRADLER -> cradler(o, p);
            case FOG_WALKER -> fogWalker(o, p);
            default -> { }
        }
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    // ---------------------------------------------------------------- Лунатик: бледный в ночной рубахе; спит с вытянутыми вперёд руками, бодрствуя — руки вскинуты, глаза горят
    private static void lunatic(double[][] o, Pose p) {
        double w = clamp(p.wake), z = 1 - w, bob = Math.sin(p.t * 0.08) * 0.02 * z;
        put(o, 0, 0, 0.38, 0, 0.62, 0.76, 0.4, qx(0.04 * z));
        put(o, 1, 0, 1.1 + bob, 0, 0.5, 0.7, 0.3, qx(0.1 * z - 0.05 * w));
        double[] hq = qx(0.4 * z - 0.05 * w);
        double hy = 1.68 + bob - 0.04 * z, hz = 0.05 * z;
        put(o, 2, 0, hy, hz, 0.4, 0.4, 0.4, hq);
        double[] c1 = Disp.rot(hq, 0, 0, 0.21);
        put(o, 3, c1[0], hy + c1[1], hz + c1[2], 0.3 * z + 0.001, 0.035, 0.02, hq);
        put(o, 4, c1[0], hy + c1[1], hz + c1[2], 0.3 * w + 0.001, 0.08 * w + 0.001, 0.02, hq);
        double sleepA = -1.5 + Math.sin(p.t * 0.06) * 0.05;
        double flail = -0.9 + Math.sin(p.t * 0.5) * 0.5 - 1.1 * p.atk;
        limb(o, 5, -0.31, 1.38 + bob, 0, 0.7, 0.12, qx(sleepA * z + flail * w));
        limb(o, 6, 0.31, 1.38 + bob, 0, 0.7, 0.12, qx(sleepA * z + (flail + 0.6 * Math.sin(p.t * 0.5 + 2)) * w));
        for (int i = 0; i < 3; i++) {   // «z-z-z»: бирюзовые кубики плывут над головой и гаснут с пробуждением
            double f = (p.t * 0.012 + i / 3.0) % 1.0;
            double s = 0.1 * z * (1 - f * 0.6);
            put(o, 7 + i, Math.sin(p.t * 0.07 + i * 2.1) * 0.12 + i * 0.05, 2.05 + f * 0.7, 0.05, s, s, s, qy(p.t * 0.1 + i));
        }
    }

    // ---------------------------------------------------------------- Дремотник: тёмный силуэт с широкими бирюзовыми крыльями, в руке эхо-осколок
    private static void drowsy(double[][] o, Pose p) {
        double flap = Math.sin(p.t * 0.35) * 0.35, bob = Math.sin(p.t * 0.1) * 0.05, ch = clamp(p.charge);
        put(o, 0, 0, 0.35 + bob, 0, 0.26, 0.7, 0.2, Disp.ID);
        put(o, 1, 0, 0.88 + bob, 0.02, 0.3, 0.3, 0.28, Disp.ID);
        put(o, 2, 0, 0.9 + bob, 0.17, 0.22 * (1 + 0.5 * ch), 0.05 * (1 + ch), 0.02, Disp.ID);
        put(o, 3, -0.5, 0.65 + bob, -0.05, 0.8, 0.04, 0.35, qz(-(0.45 + flap)));
        put(o, 4, 0.5, 0.65 + bob, -0.05, 0.8, 0.04, 0.35, qz(0.45 + flap));
        put(o, 5, 0, -0.08 + bob, 0, 0.12, 0.3, 0.12, Disp.ID);
        blade(o, 6, 0.22, 0.25 + bob, 0.1, 0.45 + 0.45 * ch, 0.04 + 0.04 * ch, qx(1.1));
    }

    // ---------------------------------------------------------------- Страж Шва: бирюзовый доспех, щит со светящимся швом, меч
    // atk — замах (меч уходит за голову), strike — удар (меч вперёд), charge — приседание перед выпадом, lift — щит опущен (идёт замах)
    private static void guard(double[][] o, Pose p) {
        double sw = Math.sin(p.ph) * 0.45, bob = Math.sin(p.t * 0.1) * 0.01, lean = 0.12 * p.strike - 0.12 * p.charge, crouch = 0.1 * p.charge;
        limb(o, 0, -0.15, 0.75, 0, 0.75, 0.24, qx(sw));
        limb(o, 1, 0.15, 0.75, 0, 0.75, 0.24, qx(-sw));
        double[] tq = qx(lean);
        put(o, 2, 0, 1.12 + bob - crouch, 0, 0.62, 0.72, 0.34, tq);
        put(o, 3, 0, 1.72 + bob - crouch, 0.02, 0.42, 0.42, 0.42, tq);
        put(o, 4, 0, 1.74 + bob - crouch, 0.225, 0.34, 0.07, 0.02, tq);
        put(o, 5, -0.42, 1.44 + bob - crouch, 0, 0.26, 0.22, 0.42, tq);
        put(o, 6, 0.42, 1.44 + bob - crouch, 0, 0.26, 0.22, 0.42, tq);
        double a = -0.25 - 2.5 * p.atk - 1.25 * p.strike;
        double[] qa = qx(a), qs = mul(qa, qx(1.45));
        double sy = 1.4 + bob - crouch;
        limb(o, 7, 0.4, sy, 0, 0.7, 0.2, qa);
        double[] h = Disp.rot(qa, 0, -0.7, 0);
        blade(o, 8, 0.4 + h[0], sy + h[1], h[2], 1.1, 0.1, qs);
        double dn = clamp(p.lift), sx = -0.38 - 0.1 * dn, syy = 1.12 - 0.25 * dn, szz = 0.32 - 0.1 * dn, tilt = 0.5 * dn;
        put(o, 9, sx, syy, szz, 0.75, 1.0, 0.1, qz(tilt));
        put(o, 10, sx, syy, szz + 0.07, 0.07, 0.9, 0.03, qz(tilt));
        limb(o, 11, -0.4, sy, 0, 0.6, 0.18, qx(-1.0 + 0.5 * dn));
    }

    // ---------------------------------------------------------------- Ночная моль: пять мотыльков по орбите вокруг центра роя; atk стягивает рой к цели
    private static void moth(double[][] o, Pose p) {
        double tight = 1 - 0.45 * clamp(p.atk);
        for (int i = 0; i < 5; i++) {
            double a = p.t * 0.09 + i * Math.PI * 2 / 5, r = (0.55 + 0.2 * Math.sin(p.t * 0.05 + i * 1.3)) * tight;
            double cx = Math.cos(a) * r, cy = 0.35 + 0.35 * Math.sin(p.t * 0.07 + i * 1.7) + 0.2 * (1 - tight), cz = Math.sin(a) * r;
            double[] q = qy(-a);
            double fl = 0.3 + 0.9 * Math.sin(p.t * 0.9 + i * 2.0);
            put(o, i * 3, cx, cy, cz, 0.1, 0.1, 0.16, q);
            double[] l = Disp.rot(q, -0.17, 0, 0), rr = Disp.rot(q, 0.17, 0, 0);
            put(o, i * 3 + 1, cx + l[0], cy + l[1], cz + l[2], 0.3, 0.02, 0.2, mul(q, qz(-fl)));
            put(o, i * 3 + 2, cx + rr[0], cy + rr[1], cz + rr[2], 0.3, 0.02, 0.2, mul(q, qz(fl)));
        }
    }

    // ---------------------------------------------------------------- Сновидец (и его копия из стекла): мантия, светящаяся щель глаз, три кубика сна по орбите
    // charge — заклинание: руки вскинуты, кубики быстрее и ближе, основание светится шире
    private static void dreamer(double[][] o, Pose p) {
        double ch = clamp(p.charge), bob = Math.sin(p.t * 0.07) * 0.04;
        put(o, 0, 0, 0.6 + bob, 0, 0.54, 1.2, 0.34, Disp.ID);
        put(o, 1, 0, 1.42 + bob, 0, 0.46, 0.5, 0.3, Disp.ID);
        put(o, 2, 0, 1.86 + bob, 0.02, 0.4, 0.4, 0.4, qx(0.08 * ch));
        put(o, 3, 0, 1.88 + bob, 0.225, 0.3, 0.05, 0.02, qx(0.08 * ch));
        put(o, 4, 0, 0.03, 0, 1.1 + 0.4 * ch, 0.04, 1.1 + 0.4 * ch, qy(p.t * 0.03));
        double a = -0.35 - 1.7 * ch;
        limb(o, 5, -0.32, 1.62 + bob, 0, 0.8, 0.1, qx(a));
        limb(o, 6, 0.32, 1.62 + bob, 0, 0.8, 0.1, qx(a * 0.9 + 0.12 * Math.sin(p.t * 0.2)));
        for (int i = 0; i < 3; i++) {
            double ang = p.t * (0.06 + 0.1 * ch) + i * Math.PI * 2 / 3, r = 0.78 - 0.22 * ch;
            put(o, 7 + i, Math.cos(ang) * r, 1.3 + bob + 0.25 * Math.sin(p.t * 0.1 + i * 2), Math.sin(ang) * r, 0.15, 0.15, 0.15, qy(p.t * 0.1 + i));
        }
        put(o, 10, 0, 1.02 + bob, 0, 0.56, 0.1, 0.36, Disp.ID);
        put(o, 11, 0, 1.92 + bob, -0.22, 0.55, 0.55, 0.03, Disp.ID);
    }

    // ---------------------------------------------------------------- Книжный страж: три раскрытые книги над землёй и три листа вокруг (у хитбокса фантома размера 2 высота ~0,65)
    // charge — прицел: книги выстраиваются веером вперёд и раскрываются шире; atk — отдача (книги откатываются назад)
    private static void bookGuard(double[][] o, Pose p) {
        double ch = clamp(p.charge), rec = clamp(p.atk);
        for (int i = 0; i < 3; i++) {
            double orb = p.t * 0.04 + i * Math.PI * 2 / 3;
            double ox = Math.cos(orb) * 0.7, oz = Math.sin(orb) * 0.7;
            double fx = (i - 1) * 0.62, fz = 0.5 - 0.12 * Math.abs(i - 1);
            double cx = ox * (1 - ch) + fx * ch, cz = oz * (1 - ch) + fz * ch - 0.25 * rec;
            double cy = 0.42 + 0.1 * Math.sin(p.t * 0.08 + i * 2.0) + 0.08 * ch;
            double[] q = qy(-orb * (1 - ch));
            double open = 0.3 + 0.4 * ch + 0.12 * Math.sin(p.t * 0.5 + i);
            double[] l = Disp.rot(q, -0.2, 0.0, 0), r = Disp.rot(q, 0.2, 0.0, 0);
            put(o, i * 4, cx + l[0], cy + l[1], cz + l[2], 0.38, 0.03, 0.5, mul(q, qz(-open)));
            put(o, i * 4 + 1, cx + r[0], cy + r[1], cz + r[2], 0.38, 0.03, 0.5, mul(q, qz(open)));
            put(o, i * 4 + 2, cx, cy - 0.03, cz, 0.06, 0.06, 0.5, q);
            double[] u = Disp.rot(q, 0, 0.1 + 0.05 * ch, 0);
            put(o, i * 4 + 3, cx + u[0], cy + u[1], cz + u[2], 0.07 + 0.04 * ch, 0.07 + 0.04 * ch, 0.07 + 0.04 * ch, qy(p.t * 0.2 + i));
        }
        for (int j = 0; j < 3; j++) {
            double a = -p.t * 0.07 + j * Math.PI * 2 / 3;
            put(o, 12 + j, Math.cos(a) * 0.45, 0.7 + 0.2 * Math.sin(p.t * 0.11 + j * 2.4), Math.sin(a) * 0.45, 0.2, 0.01, 0.28, mul(qy(a), qx(0.4 * Math.sin(p.t * 0.2 + j))));
        }
    }

    // ---------------------------------------------------------------- Статуя-страж: ангел на плинте; новая поза на каждый шаг (ph растёт только при движении), заморожена при взгляде
    // atk — «активна» (шов и глаза горят), charge — замах касания (руки вперёд), strike — наклон при ударе
    private static void statue(double[][] o, Pose p) {
        double act = clamp(p.atk), ch = clamp(p.charge), st = p.ph;
        double arm = (-0.5 + 0.55 * Math.sin(st * 1.7)) * (1 - ch) - 1.9 * ch, arm2 = (-1.0 + 0.5 * Math.sin(st * 2.3 + 1)) * (1 - ch) - 1.7 * ch;
        double turn = 0.35 * Math.sin(st * 2.9), lean = 0.12 * clamp(p.strike);
        put(o, 0, 0, 0.15, 0, 0.95, 0.3, 0.95, Disp.ID);
        put(o, 1, 0, 0.95, 0, 0.5, 1.1, 0.34, qx(lean));
        double[] hq = qy(turn);
        put(o, 2, 0, 1.78, 0.02, 0.4, 0.4, 0.4, hq);
        blade(o, 3, -0.12, 0.55, 0.19, 1.0, 0.05 + 0.04 * act, qz(-0.28));
        double wf = 0.04 * Math.sin(st);
        put(o, 4, -0.55, 1.5, -0.22, 0.9, 0.04, 0.65, mul(qz(0.55 + wf), qy(-0.2)));
        put(o, 5, 0.55, 1.5, -0.22, 0.9, 0.04, 0.65, mul(qz(-0.55 - wf), qy(0.2)));
        limb(o, 6, -0.33, 1.55, 0.05, 0.75, 0.1, qx(arm));
        limb(o, 7, 0.33, 1.55, 0.05, 0.75, 0.1, qx(arm2));
        double[] e = Disp.rot(hq, 0, 0, 0.21);
        put(o, 8, e[0], 1.8 + e[1], 0.02 + e[2], 0.3 * (0.4 + 0.6 * act), 0.05, 0.02, hq);
        put(o, 9, 0, 0.32, 0, 1.05 * (0.4 + 0.6 * act), 0.03, 1.05 * (0.4 + 0.6 * act), Disp.ID);
    }

    // ---------------------------------------------------------------- Колыбельщик: высокий тёмный гигант, в правой руке фонарь-колыбель на цепи, вокруг ног бирюзовые осколки
    // charge — раскачивание фонаря (звон), atk — замах кулака, strike — удар, stun — фонарь разбит (гаснет, осколки опадают)
    /** Центр фонаря в локальных координатах (от ног хоста): по нему же проверяется удар «по фонарю». */
    static double[] lanternLocal(Pose p) {
        double ch = clamp(p.charge);
        double[] h = Disp.rot(qx(-0.5 - 0.7 * ch), 0, -1.4, 0);
        double sw = Math.sin(p.t * 0.55) * 0.8 * ch + Math.sin(p.t * 0.07) * 0.06;
        return new double[]{0.62 + h[0] + Math.sin(sw) * 0.5, 2.45 + h[1] - Math.cos(sw) * 0.5, h[2]};
    }

    private static void cradler(double[][] o, Pose p) {
        double ch = clamp(p.charge), br = clamp(p.stun), live = 1 - br, sw = Math.sin(p.ph) * 0.4, bob = Math.sin(p.t * 0.05) * 0.02;
        limb(o, 0, -0.25, 1.35, 0, 1.35, 0.28, qx(sw));
        limb(o, 1, 0.25, 1.35, 0, 1.35, 0.28, qx(-sw));
        double[] tq = qx(0.05 + 0.1 * clamp(p.strike) - 0.08 * ch);
        put(o, 2, 0, 1.95 + bob, 0, 0.9, 1.2, 0.5, tq);
        put(o, 3, 0, 3.0 + bob, 0.05, 0.55, 0.55, 0.55, qx(0.12));
        put(o, 4, 0, 3.0 + bob, 0.33, 0.3 * (0.4 + 0.6 * live), 0.06, 0.02, qx(0.12));
        double a2 = -0.15 - 2.6 * clamp(p.atk) + 2.4 * clamp(p.strike);
        double[] q2 = qx(a2);
        limb(o, 5, -0.62, 2.45 + bob, 0, 1.7, 0.22, q2);
        double[] hand = Disp.rot(q2, 0, -1.8, 0);
        put(o, 6, -0.62 + hand[0], 2.45 + bob + hand[1], hand[2], 0.4, 0.4, 0.4, q2);
        double[] qa = qx(-0.5 - 0.7 * ch);
        limb(o, 7, 0.62, 2.45 + bob, 0, 1.4, 0.2, qa);
        double[] lamp = lanternLocal(p);
        double[] hnd = Disp.rot(qa, 0, -1.4, 0);
        double hx = 0.62 + hnd[0], hy = 2.45 + hnd[1], hz = hnd[2];
        double ang = Math.atan2(lamp[0] - hx, hy - lamp[1]);
        double cl = Math.sqrt((lamp[0] - hx) * (lamp[0] - hx) + (hy - lamp[1]) * (hy - lamp[1]));
        put(o, 8, (hx + lamp[0]) / 2, (hy + lamp[1]) / 2 - 0.1, hz, 0.04, Math.max(0.05, cl - 0.2), 0.04, qz(-ang));
        double lw = 0.34 * (1 - 0.85 * br);
        put(o, 9, lamp[0], lamp[1], lamp[2], lw, lw * 1.15, lw, qy(p.t * 0.05));
        put(o, 10, lamp[0], lamp[1] + 0.26 - 0.1 * br, lamp[2], 0.4, 0.06, 0.4, qz(0.7 * br));
        put(o, 11, lamp[0], lamp[1] - 0.26, lamp[2], 0.4, 0.06, 0.4, qz(-0.5 * br));
        put(o, 12, lamp[0], lamp[1], lamp[2], 0.44 * live, 0.46 * live, 0.44 * live, qy(p.t * 0.05));
        put(o, 13, 0, 1.6 + bob, -0.35, 0.8, 2.0, 0.1, qx(0.05 + 0.1 * sw));
        for (int i = 0; i < 6; i++) {
            double an = p.t * 0.03 * (1 + ch * 2) + i * Math.PI / 3, r = 1.5 + 0.2 * Math.sin(p.t * 0.05 + i);
            double sz = (0.7 + ch * 0.6) * live;
            put(o, 14 + i, Math.cos(an) * r, 0.3 + 0.5 * (0.5 + 0.5 * Math.sin(p.t * 0.05 + i)), Math.sin(an) * r, 0.08 * sz, 0.25 * sz, 0.04 * sz, mul(qy(-an), qz(0.3)));
        }
    }

    // ---------------------------------------------------------------- Туманный странник: тонкий бледный силуэт с крюком, вокруг ног полупрозрачные пласты тумана
    // charge — «припал» (на него смотрят), atk — рука тянется за вещью
    private static void fogWalker(double[][] o, Pose p) {
        double cr = clamp(p.charge), at = clamp(p.atk), sw = Math.sin(p.ph) * 0.4, bob = Math.sin(p.t * 0.08) * 0.015, low = 0.25 * cr;
        double[] tq = qx(0.15 * cr + 0.05);
        put(o, 0, 0, 1.15 - low + bob, 0, 0.4, 1.2, 0.3, tq);
        put(o, 1, 0, 1.95 - low + bob, 0.04, 0.36, 0.36, 0.36, qx(0.2 * cr));
        put(o, 2, -0.08, 1.97 - low + bob, 0.225, 0.07, 0.07, 0.02, qx(0.2 * cr));
        put(o, 3, 0.08, 1.97 - low + bob, 0.225, 0.07, 0.07, 0.02, qx(0.2 * cr));
        limb(o, 4, -0.28, 1.65 - low, 0, 0.9, 0.1, qx(-0.15 + sw * 0.5));
        double ra = -0.2 - 1.5 * at - sw * 0.5;
        limb(o, 5, 0.28, 1.65 - low, 0, 0.9, 0.1, qx(ra));
        double[] q = qx(ra), h = Disp.rot(q, 0, -0.95, 0);
        blade(o, 6, 0.28 + h[0], 1.65 - low + h[1], h[2], 0.32, 0.05, mul(q, qx(2.2)));
        for (int i = 0; i < 4; i++) {
            double a = p.t * 0.02 + i * Math.PI / 2;
            put(o, 7 + i, Math.cos(a) * 0.55, 0.08 + 0.04 * Math.sin(p.t * 0.06 + i), Math.sin(a) * 0.55, 0.8, 0.05, 0.8, qy(a * 0.7));
        }
        limb(o, 11, -0.1, 0.62 - low * 0.5, 0, 0.62, 0.13, qx(-sw));
        limb(o, 12, 0.1, 0.62 - low * 0.5, 0, 0.62, 0.13, qx(sw));
    }
}
