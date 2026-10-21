package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;
import com.explosionwand.RiftLogic.EaterState;
import com.explosionwand.RiftLogic.GolemArmor;
import com.explosionwand.RiftLogic.ShadeState;
import com.explosionwand.RiftModels.Kind;
import com.explosionwand.RiftModels.Pose;

import java.util.*;

/**
 * Мобы Разлома (фаза 4.3), чистая логика на заглушках:
 *  - Осколочник рассыпается на двух мелких, мелкие не делятся;
 *  - окно неуязвимости лучника, прицел не начинается перед закрытием;
 *  - слух Пожирателя: бег, ходьба, красться, шум ударов;
 *  - броня голема: щит, кристаллы со спины, раскрытие, возврат;
 *  - Тень уязвима только на виду, точка засады за спиной;
 *  - жуки не превышают предел;
 *  - облик: поза каждого моба конечна, число частей совпадает, команда summon целая;
 *  - расстановка по залам Разлома: все шесть видов, лучники на колоннах, голем в нужных залах.
 */
public class RiftMobsTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }

    public static void main(String[] args) throws Exception {
        splitTests();
        archerTests();
        hearingTests();
        golemTests();
        shadeTests();
        geometryTests();
        beetleTests();
        modelTests();
        summonTests();
        hallTests();
        knockTests();
        knockLeashLandTests();
        System.out.println(fails == 0 ? "мобы Разлома ok" : fails + " проверок не прошли");
        if (fails > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- импульс палочек
    static void knockTests() {
        // стена в одну клетку (x от 1 до 2): импульс 1,4 из x=0,9 не должен её перепрыгнуть
        RiftLogic.Step wall = (ox, oy, oz, nx, ny, nz) -> !(nx >= 1 && nx < 2);
        double[] r = RiftLogic.knockMove(0.9, 5, 0, 1.4, 0, 0, wall);
        check(r[0] < 1 && r[3] == 0, "стена в 1 блок: моб остался перед стеной, x=" + r[0]);
        r = RiftLogic.knockMove(0.9, 5, 0, -1.4, 0, 0, wall);
        check(near(r[0], -0.5) && r[3] == -1.4, "в обратную сторону летит целиком");
        // дверь-печать на оси z (z от 3 до 4), вдоль x свободно: скользит и не проходит
        RiftLogic.Step seal = (ox, oy, oz, nx, ny, nz) -> !(nz >= 3 && nz < 4);
        r = RiftLogic.knockMove(0, 5, 2.9, 0.5, 0, 1.4, seal);
        check(r[2] < 3 && r[5] == 0 && near(r[0], 0.5) && r[3] == 0.5, "печать: не проходит, вдоль неё скользит, z=" + r[2]);
        // потолок и пол
        RiftLogic.Step ceil = (ox, oy, oz, nx, ny, nz) -> ny < 6 && ny >= 4;
        r = RiftLogic.knockMove(0, 5.5, 0, 0, 1.0, 0, ceil);
        check(r[1] <= 6 && r[4] == 0, "потолок останавливает");
        // свободный путь проходит целиком
        r = RiftLogic.knockMove(0, 5, 0, 1.4, 0.3, -0.7, (a, b, c, d, e, f) -> true);
        check(near(r[0], 1.4) && near(r[1], 5.3) && near(r[2], -0.7), "без преград смещение полное");
    }

    static void knockLeashLandTests() {
        // поводок: дом (0,0), leash 5; моб на 4,8 -> наружу за предел нельзя, внутрь можно, вдоль y свободно
        double hx = 0, hz = 0, leash = 5;
        RiftLogic.Step st = (ox, oy, oz, nx, ny, nz) -> !RiftLogic.leashBlocked(Math.hypot(ox - hx, oz - hz), Math.hypot(nx - hx, nz - hz), leash);
        double[] r = RiftLogic.knockMove(4.8, 5, 0, 1.4, 0, 0, st);
        check(r[0] <= 5.0 + 1e-9 && r[3] == 0, "поводок: наружу не дальше leash, x=" + r[0]);
        r = RiftLogic.knockMove(4.8, 5, 0, -1.4, 0, 0, st);
        check(near(r[0], 3.4) && r[3] == -1.4, "поводок: к дому летит целиком");
        r = RiftLogic.knockMove(6, 5, 0, 1.0, 0, 0, st);   // уже за поводком: дальше от дома нельзя
        check(near(r[0], 6) && r[3] == 0, "поводок: за пределом наружу стоит");
        r = RiftLogic.knockMove(6, 5, 0, -1.0, 0, 0, st);
        check(near(r[0], 5) && r[3] == -1.0, "поводок: из-за предела возвращается");
        r = RiftLogic.knockMove(4.8, 5, 0, 0, -1.0, 0, st);
        check(near(r[1], 4) && r[4] == -1.0, "поводок: падение по y не блокируется");
        // предел падения
        double v = 0; for (int i = 0; i < 100; i++) v = RiftLogic.gravity(v);
        check(v == -3.0 && near(RiftLogic.gravity(-0.5), -0.58), "падение: предел -3, малая скорость растёт на 0,08");
        // пол на y=4: падение с 4,6 со скоростью -1 упирается, снап на целый y
        RiftLogic.Step floor = (ox, oy, oz, nx, ny, nz) -> ny >= 4;
        r = RiftLogic.knockMove(0, 4.6, 0, 0, -1.0, 0, floor);
        check(r[4] == 0 && r[1] >= 4 && r[1] < 4.6, "приземление: упёрлись в пол");
        check(RiftLogic.landY(4.3, -1, 0, true) == 4.0, "снап: на целый y");
        check(RiftLogic.landY(4.3, -1, 0, false) == 4.3, "снап: клетка занята - остаёмся");
        check(RiftLogic.landY(4.3, -1, -0.5, true) == 4.3 && RiftLogic.landY(4.3, 0.5, 0, true) == 4.3, "снап: только при падении и остановке");
    }

    // ---------------------------------------------------------------- Осколочник
    static void splitTests() {
        check(RiftLogic.splitsOnDeath(false) && !RiftLogic.splitsOnDeath(true), "рассыпается только обычный Осколочник, мелкий — нет");
        check(RiftLogic.SPLIT_COUNT == 2, "на двух мелких");
        for (double yaw = -3; yaw <= 3; yaw += 0.7) {
            double[][] o = RiftLogic.splitOffsets(yaw);
            double fx = Math.sin(yaw), fz = Math.cos(yaw);
            check(o.length == 2 && near(o[0][0], -o[1][0]) && near(o[0][1], -o[1][1]), "осколки разлетаются в противоположные стороны, yaw " + yaw);
            check(near(Math.hypot(o[0][0], o[0][1]), 0.6) && near(o[0][0] * fx + o[0][1] * fz, 0), "в стороны от взгляда на 0,6, yaw " + yaw);
        }
        check(Kind.SHARDLING_MINI.hp < Kind.SHARDLING.hp && Kind.SHARDLING_MINI.sc < 1, "мелкий слабее и меньше");
    }

    // ---------------------------------------------------------------- Кристальный лучник
    static void archerTests() {
        int open = 0, safe = 0;
        for (int c = 0; c < RiftLogic.ARCHER_CYCLE; c++) {
            boolean inv = RiftLogic.archerInvulnerable(c);
            if (inv) safe++;
            if (RiftLogic.archerPhase(c) == 0) open++;
            check(inv == (c >= 160 && c <= 250), "окно неуязвимости 160..250, тик " + c);
            double k = RiftLogic.archerCocoon(c);
            check(k >= 0 && k <= 1, "кокон в пределах 0..1, тик " + c);
            if (inv) check(k >= 0.5, "неуязвим только при закрытом наполовину коконе, тик " + c);
            if (RiftLogic.archerPhase(c) == 2) check(k == 1 && inv, "спрятан: кокон закрыт и неуязвим, тик " + c);
            if (RiftLogic.archerPhase(c) == 0) check(k == 0 && !inv, "открыт: кокона нет и бить можно, тик " + c);
            if (RiftLogic.archerMayAim(c)) check(RiftLogic.archerPhase(c) == 0 && c + 25 <= RiftLogic.ARCHER_CLOSE, "прицел 25 тиков успевает до начала закрытия, тик " + c);
        }
        check(open == 150 && safe == 91, "открыт 150 тиков, неуязвим 91: " + open + "/" + safe);
        check(RiftLogic.archerInvulnerable(260 + 200) == RiftLogic.archerInvulnerable(200) && RiftLogic.archerInvulnerable(-60) == RiftLogic.archerInvulnerable(200), "цикл замкнут, отрицательные тики тоже");
        // моб: окно считается по t + off
        RiftMobs.Archer a = new RiftMobs.Archer();
        a.off = 0; a.t = 100;
        check(a.vulnerable(), "лучник в открытой фазе уязвим");
        a.t = 200;
        check(!a.vulnerable(), "лучник в коконе неуязвим");
        a.off = 100; a.t = 100;
        check(!a.vulnerable(), "смещение цикла учитывается");
        check(RiftLogic.archerCocoon(150) == 0 && RiftLogic.archerCocoon(170) == 1 && RiftLogic.archerCocoon(240) == 1 && near(RiftLogic.archerCocoon(250), 0.5) && RiftLogic.archerCocoon(259) < 0.1, "кокон закрывается 150..170 и раскрывается 240..260");
    }

    // ---------------------------------------------------------------- Пожиратель эха
    static void hearingTests() {
        double sprint = RiftLogic.hearRadius(true, false, true), walk = RiftLogic.hearRadius(false, false, true);
        double sneak = RiftLogic.hearRadius(false, true, true), still = RiftLogic.hearRadius(false, false, false);
        check(sprint > walk && walk > sneak && sneak == RiftLogic.TOUCH_RADIUS && still == RiftLogic.TOUCH_RADIUS, "бег слышно дальше шага, красться и стоять — только на ощупь");
        check(RiftLogic.hearRadius(true, true, true) == RiftLogic.TOUCH_RADIUS, "присед важнее бега (красться на бегу нельзя, но шифт гасит звук)");
        check(RiftLogic.hearRadius(true, false, false) == RiftLogic.TOUCH_RADIUS, "бег на месте не слышен");
        check(RiftLogic.hears(0, 0, 11, sprint) && !RiftLogic.hears(0, 0, 13, sprint), "бег слышно с 11, не слышно с 13");
        check(!RiftLogic.hears(6, 0, 0, walk) && RiftLogic.hears(4, 0, 0, walk), "шаг слышно с 4, не слышно с 6");
        check(RiftLogic.hears(1, 0, 1, sneak) && !RiftLogic.hears(2, 0, 0, sneak), "крадущегося слышно только вплотную (1,5)");
        check(!RiftLogic.hears(0, 3, 0, still) && RiftLogic.hears(0, 1, 0, still), "высота считается: этажом выше не слышно");
        check(RiftLogic.NOISE_RADIUS > sprint && RiftLogic.NOISE_TTL >= 20, "удар слышно дальше бега, шум живёт больше секунды");
        check(RiftLogic.eaterDamageMult(EaterState.IDLE) == 2.0 && RiftLogic.eaterDamageMult(EaterState.SEARCH) == 1.5
                && RiftLogic.eaterDamageMult(EaterState.ALERT) == 1.0 && RiftLogic.eaterDamageMult(EaterState.CHARGE) == 1.0, "удар из тишины сильнее, в бою — обычный");
        check(Kind.EATER.hp >= 40, "Пожиратель крепкий");
    }

    // ---------------------------------------------------------------- Аметистовый голем
    static void golemTests() {
        check(GolemArmor.sector(1.0) == 0 && GolemArmor.sector(0.51) == 0 && GolemArmor.sector(0.0) == 1 && GolemArmor.sector(-0.51) == 2 && GolemArmor.sector(-1) == 2, "сектора: спереди, сбоку, со спины");
        GolemArmor g = new GolemArmor();
        check(g.intact() == 3 && !g.open() && !g.stunned(), "начало: 3 кристалла, закрыт");
        check(g.hit(100, 0) == GolemArmor.FRONT_MULT && g.intact() == 3, "щит: удар спереди почти не ранит и кристаллы не трогает");
        check(g.hit(100, 1) == GolemArmor.SIDE_MULT && g.intact() == 3, "удар сбоку кристаллы не трогает");
        check(g.hit(10, 2) == GolemArmor.BACK_MULT && g.hp[0] == GolemArmor.CRYSTAL_HP - 10 && g.justBroken == -1, "со спины урон забирает первый кристалл");
        check(g.hit(1000, 2) == GolemArmor.BACK_MULT && g.hp[0] == 0 && g.hp[1] == GolemArmor.CRYSTAL_HP && g.justBroken == 0 && g.intact() == 2, "один удар ломает не больше одного кристалла");
        check(!g.open(), "два кристалла целы — не раскрыт");
        g.hit(1000, 2);
        check(g.justBroken == 1 && g.intact() == 1 && !g.open(), "второй сломан");
        double m = g.hit(1000, 2);
        check(g.justBroken == 2 && g.intact() == 0 && g.open() && g.stunned() && m == GolemArmor.BACK_MULT, "третий сломан — раскрыт и оглушён");
        check(g.hit(5, 0) == GolemArmor.OPEN_MULT && g.hit(5, 2) == GolemArmor.OPEN_MULT && g.justBroken == -1, "раскрытый получает полный урон с любой стороны");
        for (int i = 0; i < GolemArmor.STUN_TICKS; i++) g.tick();
        check(!g.stunned() && g.open(), "оглушение 50 тиков, окно раскрытия дольше");
        int closed = 0, ticks = GolemArmor.STUN_TICKS;
        while (g.open() && ticks < 2000) { if (g.tick()) closed++; ticks++; }
        check(closed == 1 && ticks == GolemArmor.OPEN_TICKS && g.intact() == 3 && !g.open(), "окно раскрытия " + GolemArmor.OPEN_TICKS + " тиков, потом кристаллы отрастают (" + ticks + ")");
        check(g.hit(100, 0) == GolemArmor.FRONT_MULT, "после закрытия щит снова держит");
        check(GolemArmor.OPEN_TICKS > GolemArmor.STUN_TICKS * 3 && GolemArmor.OPEN_MULT >= 1.0, "окно раскрытия заметное");
        // голем в целом: шесть ударов по спине обязательны (3 кристалла по 28 при удара 14)
        GolemArmor h = new GolemArmor();
        int hits = 0;
        while (!h.open() && hits < 100) { h.hit(14, 2); hits++; }
        check(hits == 6, "три кристалла по 28 ХП при ударе 14 — 6 ударов: " + hits);
        check(Kind.GOLEM.hp >= 150 && Kind.GOLEM.hp <= 1024, "ХП голема в пределах лимита 1024");
    }

    // ---------------------------------------------------------------- Тень Разлома
    static void shadeTests() {
        for (ShadeState s : ShadeState.values()) check(RiftLogic.shadeVulnerable(s) == (s == ShadeState.VISIBLE), "Тень уязвима только на виду: " + s);
        RiftMobs.Shade sh = new RiftMobs.Shade();
        check(!sh.vulnerable() && sh.st == ShadeState.HIDDEN, "Тень начинает спрятанной");
        sh.st = ShadeState.VISIBLE;
        check(sh.vulnerable(), "на виду её можно бить");
        // за спиной: игрок смотрит на +z (yaw 0) — точка позади на -z; yaw 90 смотрит на -x — позади +x
        double[] b = RiftLogic.behind(10, 20, 0, 3);
        check(near(b[0], 10) && near(b[1], 17), "за спиной при yaw 0: z-3: " + Arrays.toString(b));
        b = RiftLogic.behind(10, 20, 90, 3);
        check(near(b[0], 13) && near(b[1], 20), "за спиной при yaw 90: x+3: " + Arrays.toString(b));
        b = RiftLogic.behind(0, 0, 180, 2);
        check(near(b[0], 0) && near(b[1], 2), "за спиной при yaw 180 (смотрит на -z): z+2");
        double[] s1 = RiftLogic.beside(0, 0, 0, 2.5, 1), s2 = RiftLogic.beside(0, 0, 0, 2.5, -1);
        check(near(s1[0], 2.5) && near(s2[0], -2.5) && near(s1[1], 0) && near(s2[1], 0), "сбоку — по обе руки");
    }

    static void geometryTests() {
        check(near(RiftLogic.segDist(0, 0, 0, 10, 0, 0, 5, 3, 0), 3) && near(RiftLogic.segDist(0, 0, 0, 10, 0, 0, -4, 3, 0), 5) && near(RiftLogic.segDist(0, 0, 0, 10, 0, 0, 14, 0, 3), 5), "расстояние до отрезка луча (внутри и за концами)");
        check(near(RiftLogic.segDist(1, 1, 1, 1, 1, 1, 2, 1, 1), 1), "отрезок-точка");
        check(near(RiftLogic.turn(0, 1, 0.06), 0.06) && near(RiftLogic.turn(0, 0.03, 0.06), 0.03) && near(RiftLogic.turn(3.0, -3.0, 0.5), 3.0 + (2 * Math.PI - 6.0)), "поворот не быстрее предела и через ±π");
        check(RiftLogic.BUFF_DAMAGE > 1 && RiftLogic.BUFF_SPEED > 1 && RiftLogic.BUFF_TICKS >= 40, "усиление Призмы заметно и держится дольше её опроса (20 тиков)");
    }

    // ---------------------------------------------------------------- Кристальные жуки
    static void beetleTests() {
        for (int have = 0; have <= 10; have++)
            for (int roll = -5; roll < 200; roll++) {
                int n = RiftLogic.beetleFlock(have, roll);
                check(n >= 0 && have + n <= Math.max(have, RiftLogic.BEETLE_CAP), "предел жуков: было " + have + " + " + n);
                if (have == 0) check(n >= RiftLogic.BEETLE_FLOCK_MIN && n <= RiftLogic.BEETLE_FLOCK_MAX, "стайка 2..3: " + n);
            }
        check(RiftLogic.beetleFlock(RiftLogic.BEETLE_CAP, 7) == 0 && RiftLogic.beetleFlock(RiftLogic.BEETLE_CAP - 1, 7) == 1, "у предела — не больше, чем осталось");
        check(RiftLogic.beetleGone(true, 5, 110) && RiftLogic.beetleGone(false, 111, 110) && !RiftLogic.beetleGone(false, 110, 110), "жук уходит ночью или когда игроки дальше 110");
        check(RiftLogic.renderEvery(10) == 3 && RiftLogic.renderEvery(16) == 3 && RiftLogic.renderEvery(20) == 6 && RiftLogic.renderEvery(33) == 0, "облик реже и не шлётся издалека");
        check(RiftLogic.GOLEM_FIST_R == 2.5, "круг-телеграф и зона удара голема — один радиус");
        check(RiftLogic.capHit(12, false) == 12 && RiftLogic.capHit(22.5, false) == 22.5, "не на Сложной урон как есть");
        check(RiftLogic.capHit(12, true) * RiftLogic.HARD_MULT <= RiftLogic.HARD_CAP + 1e-9 && RiftLogic.capHit(22.5, true) * RiftLogic.HARD_MULT <= RiftLogic.HARD_CAP + 1e-9
                && RiftLogic.capHit(6, true) == 6 && RiftLogic.capHit(9.3, true) == 9.3, "на Сложной один удар не выше 14, мелкие не режутся");
        check(RiftMobs.make(Kind.GOLEM).half == 0.5 && RiftMobs.make(Kind.SHARDLING).half == 0, "широкий хост (голем) проверяет 4 угла, остальные — центр");
        check(RiftMobs.make(Kind.SHARDLING).leash == Dungeons.ALIVE_PAD - 2 && RiftMobs.make(Kind.BEETLE).leash == 15, "поводок: рамка поиска зала минус 2, у жуков 15");
        check(RiftLogic.leashBlocked(25, 29, 28) && !RiftLogic.leashBlocked(25, 27, 28) && !RiftLogic.leashBlocked(30, 29, 28) && RiftLogic.leashBlocked(30, 31, 28), "поводок: наружу за предел нельзя, обратно к дому можно");
        RiftMobs.Shade hid = new RiftMobs.Shade();
        check(hid.quiet() && !RiftMobs.make(Kind.SHARDLING).quiet(), "спрятанная Тень ничего не шлёт, остальные шлют");
        check(Kind.BEETLE.id.equals("crystal_beetle") && Kind.BEETLE.hp <= 8, "жук хрупкий и мирный");
    }

    // ---------------------------------------------------------------- облик
    static void modelTests() {
        for (Kind k : Kind.values()) {
            check(k.blocks.length == k.light.length && k.parts() > 0, k + ": блоки и свет совпадают по числу");
            check(k.hp > 0 && k.hp <= 1024, k + ": ХП в пределах лимита 1024");
            check(Kind.byId(k.id) == k, k + ": поиск по id");
            for (String b : k.blocks) check(b.startsWith("block_state:{Name:\"minecraft:") && b.endsWith("\"}"), k + ": блок облика " + b);
            for (int l : k.light) check(l >= 0 && l <= 15, k + ": свет 0..15");
            Pose p = new Pose();
            for (int t = 0; t < 120; t += 7) {
                p.t = t; p.ph = t * 0.4; p.atk = (t % 21) / 21.0; p.strike = (t % 13) / 13.0; p.jaw = 0.5; p.ears = 0.5; p.lift = 0.5; p.open = (t % 30) / 30.0; p.cocoon = (t % 40) / 40.0;
                p.charge = (t % 17) / 17.0; p.stun = (t % 9) / 9.0; p.sc = k.sc;
                double[][] o = RiftModels.pose(k, p);
                check(o.length == k.parts(), k + ": частей в позе " + o.length + " вместо " + k.parts());
                for (int i = 0; i < o.length; i++) {
                    for (int j = 0; j < RiftModels.W; j++) check(!Double.isNaN(o[i][j]) && !Double.isInfinite(o[i][j]), k + " часть " + i + " поле " + j + ": число");
                    for (int j = 0; j < 3; j++) check(Math.abs(o[i][j]) < 6, k + " часть " + i + ": центр рядом с мобом");
                    for (int j = 3; j < 6; j++) check(o[i][j] >= 0 && o[i][j] < 6, k + " часть " + i + ": размер " + o[i][j]);
                    double ql = Math.sqrt(o[i][6] * o[i][6] + o[i][7] * o[i][7] + o[i][8] * o[i][8] + o[i][9] * o[i][9]);
                    check(Math.abs(ql - 1) < 1e-6, k + " часть " + i + ": кватернион единичный");
                }
                String[] tf = RiftMobs.transforms(o, t * 0.3, 1.2);
                check(tf.length == k.parts(), k + ": строк transformation");
                for (String s : tf) check(s.startsWith("transformation:{") && !s.contains("NaN") && !s.contains("Infinity"), k + ": transformation цел");
            }
        }
        // лучник: открыт — кокона нет; закрыт — кокон скрывает фигуру
        Pose p = new Pose();
        p.cocoon = 0;
        double[][] open = RiftModels.pose(Kind.ARCHER, p);
        check(open[10][3] == 0 && open[10][4] == 0 && open[11][4] == 0, "лучник открыт: кокон и клинки нулевого размера");
        p.cocoon = 1;
        double[][] closed = RiftModels.pose(Kind.ARCHER, p);
        check(closed[10][3] >= 0.9 && closed[10][4] >= 1.9 && closed[11][4] >= 1.9, "лучник закрыт: оболочка и клинки выше человека");
        p.cocoon = 0;
        double[][] st = RiftModels.pose(Kind.ARCHER, p);
        double minY = 9;
        for (int i = 0; i < 9; i++) minY = Math.min(minY, st[i][1] - st[i][4] / 2);
        check(minY >= -0.01, "лучник стоит на колонне: ни одна часть тела не ниже верха блока (парения и плит нет): " + minY);
        // Тень: до появления нет ни фигуры, ни трещины
        p = new Pose(); p.e = 0; p.crack = 0;
        double[][] hid = RiftModels.pose(Kind.SHADE, p);
        double sum = 0;
        for (double[] r : hid) sum += r[3] * r[4] * r[5];
        check(sum < 1e-9, "Тень спрятана: объём всех частей нулевой");
        check(RiftModels.Kind.SHADE.parts() == 8, "у Тени нет плит под ногами: 8 частей");
        p.e = 1;
        double[][] full = RiftModels.pose(Kind.SHADE, p);
        check(full[2][1] > 2.0, "Тень выше двух блоков");
        // Голем: сломанный кристалл исчезает, остальные на месте
        p = new Pose(); p.cr = new double[]{0, 1, 1};
        double[][] gg = RiftModels.pose(Kind.GOLEM, p);
        check(gg[11][4] == 0 && gg[12][4] > 0.5 && gg[13][4] > 0.5, "сломанный кристалл голема убран, соседние на месте");
        // щит открыт — опущен ниже, закрыт — спереди на уровне груди
        p = new Pose(); p.open = 0;
        double[][] sc = RiftModels.pose(Kind.GOLEM, p);
        p.open = 1;
        double[][] so = RiftModels.pose(Kind.GOLEM, p);
        check(sc[9][1] > 1.5 && sc[9][2] > 0.5 && so[9][1] < 0.8, "щит спереди на уровне груди, при раскрытии падает на землю");
        // Осколочник: мелкий в два раза меньше
        Pose a = new Pose(), b = new Pose(); b.sc = Kind.SHARDLING_MINI.sc;
        check(RiftModels.pose(Kind.SHARDLING_MINI, b)[0][3] < RiftModels.pose(Kind.SHARDLING, a)[0][3] * 0.7, "мелкий осколочек заметно меньше");
        // Призма: куб вращается (поворот меняется со временем)
        Pose p1 = new Pose(), p2 = new Pose(); p2.t = 50;
        double[][] r1 = RiftModels.pose(Kind.PRISM, p1), r2 = RiftModels.pose(Kind.PRISM, p2);
        check(Math.abs(r1[0][6] - r2[0][6]) + Math.abs(r1[0][7] - r2[0][7]) > 1e-3, "ядро Призмы вращается");
    }

    /** Команда summon: скобки сбалансированы, у хоста нужные теги, частей облика ровно по числу в виде. */
    static void summonTests() {
        for (Kind k : Kind.values()) {
            RiftMobs.Mob m = RiftMobs.make(k);
            m.setup(k, "abc123", null);
            m.x = 10.5; m.y = 20; m.z = -3.5; m.yaw = 0.7;
            m.initPose();
            String s = RiftMobs.summon(m, "ewd1f2h7");
            check(s.startsWith("summon minecraft:" + k.host + " 10.500 20.000 -3.500 {"), k + ": начало команды " + s.substring(0, 40));
            int depth = 0, sq = 0, dq = 0; boolean ok = true;
            for (char c : s.toCharArray()) {
                if (c == '"') dq++;
                if (c == '\'') sq++;
                if (c == '{' || c == '[') depth++;
                if (c == '}' || c == ']') { depth--; if (depth < 0) ok = false; }
            }
            check(ok && depth == 0 && dq % 2 == 0 && sq % 2 == 0, k + ": скобки и кавычки сбалансированы");
            for (String tag : new String[]{"\"ewr_h\"", "\"ewrk_" + k.id + "\"", "\"ewriabc123\"", "\"ewd\"", "\"ewd1f2h7\""})
                check(s.contains(tag), k + ": у хоста тег " + tag);
            check(s.contains("NoAI:1b") && s.contains("NoGravity:1b") && s.contains("Silent:1b") && s.contains("Id:14") && s.contains("DeathLootTable:\"minecraft:empty\""), k + ": невидимый хост без ИИ");
            int parts = s.split("minecraft:block_display", -1).length - 1;
            check(parts == k.parts(), k + ": пассажиров " + parts + " из " + k.parts());
            for (int i = 0; i < k.parts(); i++) check(s.contains("\"ewrn" + i + "\""), k + ": у части " + i + " есть номер");
            int tagged = s.split("\"ewrpabc123\"", -1).length - 1;
            check(tagged == k.parts(), k + ": у каждой части тег хозяина");
            check(s.contains("translate\":\"explosionwand.mob." + k.id), k + ": имя по ключу перевода");
            // части не несут тегов зала: «живыми мобами зала» не считаются
            int idx = s.indexOf("Passengers:[");
            check(!s.substring(idx).contains("\"ewd\"") && !s.substring(idx).contains("ewd1f2h7"), k + ": у частей облика нет тегов зала");
            String free = RiftMobs.summon(m, null);
            check(!free.contains("\"ewd\""), k + ": вне данжа тега ewd нет");
            check(s.contains("ewrh21_40_-7"), k + ": точка появления в тегах (в полублоках)");
        }
        check(RiftMobs.kindOfMark("explosionwand:echo_eater") == Kind.EATER && RiftMobs.kindOfMark("zombie") == null && RiftMobs.kindOfMark("explosionwand:nope") == null, "метки spawn: свои и ванильные");
        check(RiftMobs.lift(Kind.ARCHER) == 0 && RiftMobs.lift(Kind.SHARDLING) == 0, "лучник стоит на метке (верх колонны), без подъёма");
        check(RiftMobs.PFX.equals("explosionwand:"), "префикс меток");
    }

    // ---------------------------------------------------------------- залы Разлома
    static void hallTests() throws Exception {
        Dungeons.Def d = Dungeons.RIFT;
        Map<Kind, Integer> total = new EnumMap<>(Kind.class);
        Map<Integer, Set<String>> byHall = new TreeMap<>();
        int marks = 0;
        for (Dungeons.Hall h : d.halls()) {
            for (Mark m : h.spawns) {
                marks++;
                Kind k = RiftMobs.kindOfMark(m.a);
                if (m.a.startsWith(RiftMobs.PFX)) check(k != null && k != Kind.SHARDLING_MINI && k != Kind.BEETLE, "зал " + h.id + ": неизвестный моб " + m.a);
                byHall.computeIfAbsent(h.id, x -> new TreeSet<>()).add(k == null ? m.a : k.id);
                if (k != null) total.merge(k, 1, Integer::sum);
                if (k == Kind.ARCHER) {
                    // под лучником — колонна из блока аметиста, метка стоит на её верху
                    boolean pillar = false;
                    for (DungeonTemplate t : d.templates())
                        for (int i = 0; i < t.count(); i++)
                            if (t.ox + t.bx[i] == m.x && t.oy + t.by[i] == m.y - 1 && t.oz + t.bz[i] == m.z) pillar = t.palette[t.bs[i]].equals("minecraft:amethyst_block");
                    check(pillar, "зал " + h.id + ": лучник " + m.x + "," + m.y + "," + m.z + " стоит на колонне из аметиста");
                }
            }
        }
        check(marks == 77, "меток мобов " + marks);
        for (Kind k : new Kind[]{Kind.SHARDLING, Kind.ARCHER, Kind.SHADE, Kind.PRISM, Kind.EATER, Kind.GOLEM})
            check(total.getOrDefault(k, 0) > 0, "в Разломе есть " + k.id);
        check(total.getOrDefault(Kind.GOLEM, 0) == 1 && total.getOrDefault(Kind.EATER, 0) == 3, "големы и пожиратели редки: " + total);
        for (int h : new int[]{1, 2}) check(Set.of("explosionwand:shardling", "explosionwand:crystal_archer").equals(prefixed(byHall.get(h))), "залы 1-2: осколочники и лучники: " + byHall.get(h));
        for (int h : new int[]{3, 4, 5}) {
            Set<String> s = byHall.get(h);
            check(s.contains("prism") && s.contains("rift_shade") && !s.contains("echo_eater") && !s.contains("amethyst_golem"), "залы 3-5: призмы и тени: " + h + " " + s);
        }
        check(byHall.get(6).contains("echo_eater"), "преддверие: пожиратели");
        check(byHall.get(7) == null, "логово: обычных мобов нет, там босс");
        check(byHall.get(11).contains("amethyst_golem") && byHall.get(11).contains("rift_shade"), "Логово Тени: голем-элита и тени");
        check(byHall.get(8).equals(Set.of("shardling")), "Пещера Осколочников: осколочники");
        check(byHall.get(9).equals(Set.of("drowned")), "Затопленный грот остался ванильным");
        check(byHall.get(10).contains("rift_shade"), "Гнездо Теней: тени");
        check(d.layout == 4 && d.signature().endsWith(":v4"), "раскладка Разлома повышена до 4");
    }

    static Set<String> prefixed(Set<String> s) {
        Set<String> out = new TreeSet<>();
        for (String x : s) out.add(RiftMobs.PFX + x);
        return out;
    }
}
