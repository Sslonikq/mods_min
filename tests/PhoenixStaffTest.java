package com.explosionwand;

/** Посох Феникса: перезарядка пассивки «Из пепла», отсутствие повторного спасения, рывок. */
public class PhoenixStaffTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    public static void main(String[] a) {
        long N = PhoenixStaff.ASH_CD, NONE = Long.MIN_VALUE;
        check(N == 12000L, "10 минут = 12000 тиков");
        check(PhoenixStaff.remaining(NONE, 5000) == 0, "ещё не спасало: готово");
        check(PhoenixStaff.remaining(1000, 1000) == N, "сразу после спасения: полная перезарядка");
        check(PhoenixStaff.remaining(1000, 1000 + N - 1) == 1, "за тик до конца: 1 тик");
        check(PhoenixStaff.remaining(1000, 1000 + N) == 0, "ровно через 10 минут: готово");
        check(PhoenixStaff.remaining(1000, 500) == N, "время назад не даёт больше 10 минут");

        check(PhoenixStaff.rescues(true, "mob", 10, 10, NONE, 100), "смертельный удар (урон = здоровью) спасён");
        check(PhoenixStaff.rescues(true, "fall", 4, 30, NONE, 100), "урон больше здоровья спасён");
        check(!PhoenixStaff.rescues(true, "mob", 10, 9.9F, NONE, 100), "не смертельный удар не тратит пассивку");
        check(!PhoenixStaff.rescues(false, "mob", 10, 99, NONE, 100), "без посоха не спасает");
        check(!PhoenixStaff.rescues(true, "genericKill", 10, 99, NONE, 100), "/kill не обходится");
        check(!PhoenixStaff.rescues(true, "outOfWorld", 10, 99, NONE, 100), "бездна не обходится");

        // нет повторного спасения: после спасения в момент t второй смертельный удар в любой момент до t+10 минут убивает
        long t = 50000;
        check(PhoenixStaff.rescues(true, "mob", 20, 25, NONE, t), "первое спасение");
        long last = t;
        check(!PhoenixStaff.rescues(true, "mob", 8, 25, last, t), "в тот же тик второго спасения нет");
        check(!PhoenixStaff.rescues(true, "mob", 8, 25, last, t + 20 * 60 * 9), "через 9 минут нет");
        check(!PhoenixStaff.rescues(true, "mob", 8, 25, last, t + N - 1), "за тик до конца нет");
        check(PhoenixStaff.rescues(true, "mob", 8, 25, last, t + N), "через 10 минут снова спасает");

        check(PhoenixStaff.clock(12000).equals("10:00"), "10:00");
        check(PhoenixStaff.clock(1).equals("0:01"), "0:01 (вверх)");
        check(PhoenixStaff.clock(1230).equals("1:02"), "1:02");

        double[] v = PhoenixStaff.dashVel(0, 0, 1);
        check(v[0] == 0 && v[2] == 1.5D && v[1] == 0.1D, "рывок по горизонту");
        double[] up = PhoenixStaff.dashVel(0, 1, 0);
        check(up[0] == 0 && up[2] == 0 && up[1] == 0.3D, "взгляд вверх: только подъём, vy не больше 0,3");
        check(PhoenixStaff.dashVel(0, 0.1, 1)[1] <= 0.3D, "vy сверху ограничен");
        double[] dn = PhoenixStaff.dashVel(0.6, -0.8, 0);
        check(dn[0] == 1.5D && dn[1] < 0 && dn[1] >= -0.2D, "взгляд вниз: слабый наклон");
        double[] d = PhoenixStaff.dashVel(3, 0, 4);
        check(Math.abs(Math.hypot(d[0], d[2]) - 1.5D) < 1e-9, "горизонтальная скорость всегда 1,5");
        check(PhoenixStaff.DASH_CD == 120, "кулдаун рывка 6 с");
        System.out.println("PhoenixStaffTest OK");
    }
}
