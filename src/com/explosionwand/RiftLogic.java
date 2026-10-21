package com.explosionwand;

/**
 * Чистые правила мобов Разлома (без Minecraft): цикл лучника, слух Пожирателя, броня голема, засада Тени, рассыпание Осколочника.
 * Всё, что можно проверить без игры, живёт здесь и проверяется tests/RiftMobsTest.java; RiftMobs только применяет эти правила.
 */
final class RiftLogic {
    private RiftLogic() {}

    // ================================================================ Осколочник
    /** Мелкий осколочек больше не рассыпается. */
    static boolean splitsOnDeath(boolean mini) { return !mini; }
    static final int SPLIT_COUNT = 2;

    /** Куда падают два осколка (dx,dz): по обе стороны от направления взгляда yaw (радианы, 0 = на +z). */
    static double[][] splitOffsets(double yaw) {
        double s = Math.sin(yaw), c = Math.cos(yaw);
        return new double[][]{{c * 0.6, -s * 0.6}, {-c * 0.6, s * 0.6}};
    }

    // ================================================================ Кристальный лучник
    /** Цикл: 0-149 открыт (стреляет), 150-169 закрывается, 170-239 спрятан, 240-259 раскрывается. */
    static final int ARCHER_CYCLE = 260, ARCHER_CLOSE = 150, ARCHER_HIDDEN = 170, ARCHER_REOPEN = 240, ARCHER_NO_AIM = ARCHER_CLOSE - 25;
    /** Неуязвим, пока кокон закрыт хотя бы наполовину. */
    static final int ARCHER_SAFE_FROM = 160, ARCHER_SAFE_TO = 250;

    /** 0 открыт, 1 закрывается, 2 спрятан, 3 раскрывается. */
    static int archerPhase(int c) {
        c = Math.floorMod(c, ARCHER_CYCLE);
        if (c < ARCHER_CLOSE) return 0;
        if (c < ARCHER_HIDDEN) return 1;
        if (c < ARCHER_REOPEN) return 2;
        return 3;
    }

    /** Доля закрытия кокона 0..1. */
    static double archerCocoon(int c) {
        c = Math.floorMod(c, ARCHER_CYCLE);
        if (c < ARCHER_CLOSE) return 0;
        if (c < ARCHER_HIDDEN) return (c - ARCHER_CLOSE) / (double) (ARCHER_HIDDEN - ARCHER_CLOSE);
        if (c < ARCHER_REOPEN) return 1;
        return 1 - (c - ARCHER_REOPEN) / (double) (ARCHER_CYCLE - ARCHER_REOPEN);
    }

    static boolean archerInvulnerable(int c) {
        c = Math.floorMod(c, ARCHER_CYCLE);
        return c >= ARCHER_SAFE_FROM && c <= ARCHER_SAFE_TO;
    }

    /** Новый выстрел не начинают незадолго до закрытия: прицел 25 тиков должен успеть закончиться. */
    static boolean archerMayAim(int c) {
        c = Math.floorMod(c, ARCHER_CYCLE);
        return c < ARCHER_NO_AIM;
    }

    // ================================================================ Тень Разлома
    enum ShadeState { HIDDEN, CRACK, EMERGE, VISIBLE, VANISH }

    /** Бить Тень можно, только пока она стоит на виду. */
    static boolean shadeVulnerable(ShadeState s) { return s == ShadeState.VISIBLE; }

    /** Точка за спиной игрока: yawDeg — рысканье игрока в градусах Minecraft (взгляд = (-sin, cos)). */
    static double[] behind(double px, double pz, double yawDeg, double dist) {
        double a = Math.toRadians(yawDeg);
        return new double[]{px + Math.sin(a) * dist, pz - Math.cos(a) * dist};
    }

    /** Точка сбоку (side = +1/-1) на расстоянии dist от игрока. */
    static double[] beside(double px, double pz, double yawDeg, double dist, int side) {
        double a = Math.toRadians(yawDeg);
        return new double[]{px + Math.cos(a) * dist * side, pz + Math.sin(a) * dist * side};
    }

    // ================================================================ Призма
    static final double BUFF_DAMAGE = 1.5, BUFF_SPEED = 1.25, BUFF_RADIUS = 9.0;
    static final int BUFF_TICKS = 40;

    // ================================================================ Пожиратель эха
    enum EaterState { IDLE, ALERT, CHARGE, SEARCH }

    static final double NOISE_RADIUS = 14.0, TOUCH_RADIUS = 1.5;
    static final int NOISE_TTL = 30;

    /** Радиус, с которого Пожиратель слышит игрока: бег 12, ходьба 5, крадётся или стоит — только на ощупь. */
    static double hearRadius(boolean sprinting, boolean sneaking, boolean moved) {
        if (sneaking) return TOUCH_RADIUS;
        if (sprinting && moved) return 12.0;
        if (moved) return 5.0;
        return TOUCH_RADIUS;
    }

    static boolean hears(double dx, double dy, double dz, double radius) { return dx * dx + dy * dy + dz * dz <= radius * radius; }

    /** Удар по слепому из тишины (он не слышал) больнее. */
    static double eaterDamageMult(EaterState s) {
        return s == EaterState.IDLE ? 2.0 : s == EaterState.SEARCH ? 1.5 : 1.0;
    }

    // ================================================================ Аметистовый голем
    /** Броня голема: щит спереди, кристаллы на спине; сломаны все — раскрыт. */
    static final class GolemArmor {
        static final int CRYSTALS = 3, OPEN_TICKS = 260, STUN_TICKS = 50;
        static final double CRYSTAL_HP = 28, FRONT_MULT = 0.1, SIDE_MULT = 0.3, BACK_MULT = 0.25, OPEN_MULT = 1.2;
        final double[] hp = new double[CRYSTALS];
        int openT = 0, stunT = 0;
        /** Индекс кристалла, сломанного последним ударом (-1 — нет). */
        int justBroken = -1;

        GolemArmor() { restore(); }

        void restore() { for (int i = 0; i < CRYSTALS; i++) hp[i] = CRYSTAL_HP; }
        boolean open() { return openT > 0; }
        int intact() { int n = 0; for (double h : hp) if (h > 0) n++; return n; }
        boolean stunned() { return stunT > 0; }

        /** Сектор удара: 0 спереди, 1 сбоку, 2 со спины. cos — косинус угла между взглядом голема и направлением на атакующего. */
        static int sector(double cos) { return cos > 0.5 ? 0 : cos < -0.5 ? 2 : 1; }

        /** Множитель урона по хосту. Со спины урон забирает первый целый кристалл (за один удар ломается не больше одного). */
        double hit(double amount, int sector) {
            justBroken = -1;
            if (open()) return OPEN_MULT;
            if (sector == 0) return FRONT_MULT;
            if (sector == 1) return SIDE_MULT;
            for (int i = 0; i < CRYSTALS; i++) {
                if (hp[i] <= 0) continue;
                hp[i] -= amount;
                if (hp[i] <= 0) {
                    hp[i] = 0; justBroken = i;
                    if (intact() == 0) { openT = OPEN_TICKS; stunT = STUN_TICKS; }
                }
                break;
            }
            return BACK_MULT;
        }

        /** Один тик: окно «раскрыт» тает, кристаллы отрастают заново. Возвращает true в тик закрытия. */
        boolean tick() {
            if (stunT > 0) stunT--;
            if (openT > 0 && --openT == 0) { restore(); return true; }
            return false;
        }
    }

    // ================================================================ геометрия
    /** Расстояние от точки p до отрезка a-b. */
    static double segDist(double ax, double ay, double az, double bx, double by, double bz, double px, double py, double pz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, l2 = dx * dx + dy * dy + dz * dz;
        double u = l2 < 1e-9 ? 0 : ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / l2;
        u = Math.max(0, Math.min(1, u));
        double cx = ax + dx * u - px, cy = ay + dy * u - py, cz = az + dz * u - pz;
        return Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /** Поворот yaw к цели want не быстрее rate (радианы, с обходом через ±π). */
    static double turn(double yaw, double want, double rate) {
        double d = want - yaw;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return yaw + Math.max(-rate, Math.min(rate, d));
    }

    // ================================================================ Кристальные жуки
    /** Жук исчезает ночью или когда ближайший игрок дальше range блоков. */
    static boolean beetleGone(boolean night, double nearestPlayer, double range) { return night || nearestPlayer > range; }

    /** Как часто слать позу облика: каждые 3 тика в 16 блоках, каждые 6 в 32, дальше не слать (0). */
    static int renderEvery(double nearestPlayer) { return nearestPlayer <= 16 ? 3 : nearestPlayer <= 32 ? 6 : 0; }

    /** Радиус удара кулаком голема: и круг-телеграф, и зона урона (4.4, nit 12: раньше круг 2,4, удар 2,5). */
    static final double GOLEM_FIST_R = 2.5;

    /**
     * Сложность и урон мобов Разлома (nit 8). Ванильный mobAttack масштабируется: Легко min(d/2+1, d), Нормально d, Сложно d*1,5.
     * Цифры в коде даны для Нормальной. На Сложной один удар не должен превышать HARD_CAP (7 сердец без брони): иначе голем (12*1,5 = 18)
     * и усиленный Призмой Пожиратель (10*1,5*1,5 = 22,5) убивали бы одним ударом почти с полного здоровья.
     */
    static final double HARD_MULT = 1.5, HARD_CAP = 14.0;

    /** Урон до масштабирования сложностью, обрезанный так, чтобы на Сложной итог не превысил HARD_CAP. */
    static double capHit(double raw, boolean hard) { return hard ? Math.min(raw, HARD_CAP / HARD_MULT) : raw; }

    /** Поводок: шаг к точке, которая дальше leash от дома и дальше текущего положения, запрещён. */
    static boolean leashBlocked(double homeDist, double newDist, double leash) { return newDist > leash && newDist > homeDist; }

    /** Предел скорости падения от толчка (блоков за тик). */
    static final double FALL_MAX = -3.0;
    /** Гравитация наземного моба в полёте от толчка, не быстрее предела. */
    static double gravity(double ky) { return Math.max(ky - 0.08, FALL_MAX); }
    /** Приземление: упёрлись в пол при падении и клетка на целом y свободна - встаём на целый y. */
    static double landY(double cy, double oky, double kyAfter, boolean floorFree) { return kyAfter == 0 && oky < 0 && floorFree ? Math.floor(cy) : cy; }

    /** Можно ли перейти из (ox,oy,oz) в (nx,ny,nz): один подшаг импульса. */
    interface Step { boolean ok(double ox, double oy, double oz, double nx, double ny, double nz); }

    /** Подшаг импульса не длиннее 0,45 блока: тоньше стены в 1 блок, шаг не перепрыгивает её. */
    static final double KNOCK_SUBSTEP = 0.45;

    /**
     * Смещение по импульсу подшагами (по осям отдельно, скользит вдоль стен). Возвращает {x, y, z, kx, ky, kz}; упёршаяся ось
     * получает скорость 0 и дальше не двигается.
     */
    static double[] knockMove(double x, double y, double z, double kx, double ky, double kz, Step st) {
        int n = (int) Math.ceil(Math.max(Math.abs(kx), Math.max(Math.abs(ky), Math.abs(kz))) / KNOCK_SUBSTEP);
        if (n < 1) n = 1;
        double sx = kx / n, sy = ky / n, sz = kz / n;
        for (int i = 0; i < n; i++) {
            if (sx != 0) { if (st.ok(x, y, z, x + sx, y, z)) x += sx; else { sx = 0; kx = 0; } }
            if (sz != 0) { if (st.ok(x, y, z, x, y, z + sz)) z += sz; else { sz = 0; kz = 0; } }
            if (sy != 0) { if (st.ok(x, y, z, x, y + sy, z)) y += sy; else { sy = 0; ky = 0; } }
        }
        return new double[]{x, y, z, kx, ky, kz};
    }

    static final int BEETLE_CAP = 6, BEETLE_FLOCK_MIN = 2, BEETLE_FLOCK_MAX = 3;

    /** Сколько жуков добавить стайкой: не выше предела на Разлом. */
    static int beetleFlock(int have, int roll) {
        if (have >= BEETLE_CAP) return 0;
        int want = BEETLE_FLOCK_MIN + Math.floorMod(roll, BEETLE_FLOCK_MAX - BEETLE_FLOCK_MIN + 1);
        return Math.min(want, BEETLE_CAP - have);
    }
}
