package com.explosionwand;

/** Админ-палочки: выбор цели на линии (rayBox), фильтр игроков и стоек, перпендикуляр зигзага. */
public class AdminWandsTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    public static void main(String[] a) {
        // фильтр: игроки, наблюдатели, стойки и мёртвые не цели
        check(AdminWands.targetable(true, false, true, false, false), "обычный моб - цель");
        check(!AdminWands.targetable(true, true, true, false, false), "игрок не цель");
        check(!AdminWands.targetable(true, false, true, true, false), "наблюдатель не цель");
        check(!AdminWands.targetable(true, false, true, false, true), "стойка (носитель block_display) не цель");
        check(!AdminWands.targetable(true, false, false, false, false), "мёртвый не цель");
        check(!AdminWands.targetable(false, false, true, false, false), "не живое существо не цель");

        double R = AdminWands.RANGE, G = AdminWands.GROW;
        // луч вдоль +X из (0,0,0); бокс 1x1 на расстоянии 10
        double t = AdminWands.rayBox(0, 0.5, 0.5, 1, 0, 0, 10, 0, 0, 11, 1, 1, R, 0);
        check(Math.abs(t - 10) < 1e-9, "вход в бокс на 10: " + t);
        check(AdminWands.rayBox(0, 5, 0.5, 1, 0, 0, 10, 0, 0, 11, 1, 1, R, 0) < 0, "выше бокса - мимо");
        check(AdminWands.rayBox(0, 1.2, 0.5, 1, 0, 0, 10, 0, 0, 11, 1, 1, R, 0) < 0, "чуть выше без допуска - мимо");
        check(AdminWands.rayBox(0, 1.2, 0.5, 1, 0, 0, 10, 0, 0, 11, 1, 1, R, G) >= 0, "чуть выше с допуском - попал");
        check(AdminWands.rayBox(0, 0.5, 0.5, 1, 0, 0, 100, 0, 0, 101, 1, 1, R, 0) < 0, "за дальностью - мимо");
        check(AdminWands.rayBox(0, 0.5, 0.5, -1, 0, 0, 10, 0, 0, 11, 1, 1, R, 0) < 0, "позади - мимо");
        check(AdminWands.rayBox(10.5, 0.5, 0.5, 1, 0, 0, 10, 0, 0, 11, 1, 1, R, 0) == 0, "начало внутри: расстояние 0");
        // диагональ
        double s = Math.sqrt(0.5);
        check(AdminWands.rayBox(0, 0, 0, s, s, 0, 10, 10, -1, 12, 12, 1, R, 0) > 0, "диагональ попадает");
        // пробивание: три цели на одной линии - все попадают, порядок по расстоянию
        double[] d = new double[3];
        double[] at = {30, 5, 17};
        for (int i = 0; i < 3; i++) d[i] = AdminWands.rayBox(0, 0.5, 0.5, 1, 0, 0, at[i], 0, 0, at[i] + 1, 1, 1, R, G);
        check(d[0] > 0 && d[1] > 0 && d[2] > 0, "луч пробивает всех на линии");
        check(d[1] < d[2] && d[2] < d[0], "расстояния растут с удалением: " + d[1] + " " + d[2] + " " + d[0]);
        // цель сбоку от линии не задета
        check(AdminWands.rayBox(0, 0.5, 0.5, 1, 0, 0, 20, 0, 4, 21, 1, 5, R, G) < 0, "цель в стороне не задета");

        // перпендикуляр: единичный и ортогональный для разных направлений, включая вертикаль
        double[][] dirs = {{1, 0, 0}, {0, 0, 1}, {0.6, 0.8, 0}, {0, 1, 0}, {0, -1, 0}, {0.1, 0.99, 0.1}};
        for (double[] v : dirs) {
            double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            double[] p = AdminWands.perp(v[0] / l, v[1] / l, v[2] / l);
            double dot = p[0] * v[0] / l + p[1] * v[1] / l + p[2] * v[2] / l, len = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
            check(Math.abs(dot) < 1e-9 && Math.abs(len - 1) < 1e-9, "perp ортогонален и единичен");
        }
        check(AdminWands.LIGHTNING_DAMAGE >= 1000.0F && AdminWands.LIGHTNING_CD == 10 && AdminWands.RANGE == 64.0D, "параметры молнии");
        System.out.println("AdminWandsTest OK");
    }
}
