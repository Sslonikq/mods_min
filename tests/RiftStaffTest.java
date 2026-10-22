package com.explosionwand;

import java.nio.file.*;
import java.util.stream.*;

/** Шаг Посоха Разлома: только в видимую точку; посохи без рецептов и разрешены в данжах. */
public class RiftStaffTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    /** Все клетки свободны; видима точка при x <= visibleUpTo (дальше стена/печать/чужой зал). */
    static RiftStaff.Probe probe(double visibleUpTo) {
        return new RiftStaff.Probe() {
            public boolean free(double x, double y, double z) { return true; }
            public boolean clear(double x, double y, double z) { return x <= visibleUpTo; }
        };
    }

    public static void main(String[] a) throws Exception {
        check(RiftStaff.pick(0, 0, 0, 1, 0, 0, probe(100)) == 8.0D, "open field: full step");
        check(RiftStaff.pick(0, 0, 0, 1, 0, 0, probe(5.5)) == 5.0D, "wall behind 5.5: stop at the last visible point");
        check(RiftStaff.pick(0, 0, 0, 1, 0, 0, probe(1)) == RiftStaff.BLOCKED, "wall right ahead: refused (blocked)");
        RiftStaff.Probe none = new RiftStaff.Probe() {
            public boolean free(double x, double y, double z) { return false; }
            public boolean clear(double x, double y, double z) { return true; }
        };
        check(RiftStaff.pick(0, 0, 0, 1, 0, 0, none) == RiftStaff.NO_ROOM, "no room");
        check(RiftStaff.DAMAGE == 8.0F && RiftStaff.CRACK_CD == 80, "crack: damage 8, cooldown 4 s");
        // залы: чужой зал нельзя, коридор (-1) и свой зал можно
        check(Dungeons.otherHall(1, 2) && !Dungeons.otherHall(1, 1) && !Dungeons.otherHall(-1, 2) && !Dungeons.otherHall(3, -1) && !Dungeons.otherHall(-1, -1), "otherHall");
        // посохи разрешены в данжах
        String g = Files.readString(Path.of("src/com/explosionwand/DungeonGuard.java"));
        String tp = g.substring(g.indexOf("static boolean teleports"), g.indexOf("static boolean forbidden"));
        check(!tp.contains("StaffItem"), "ни один посох не в списке запретов данжей");
        // посохи не крафтятся
        try (Stream<Path> st = Files.list(Path.of("resources/data/explosionwand/recipes"))) {
            check(st.noneMatch(p -> p.getFileName().toString().contains("staff")), "нет файлов рецептов посохов");
        }
        try (Stream<Path> st = Files.list(Path.of("resources/data/explosionwand/recipes"))) {
            for (Path p : st.collect(Collectors.toList())) {
                String s = Files.readString(p);
                int i = s.indexOf("\"result\"");
                check(i < 0 || !s.substring(i).matches("(?s).*\"item\"\s*:\s*\"explosionwand:[a-z_]*staff\".*"), "рецепт " + p.getFileName() + " не выдаёт посох");
            }
        }
        // Король Затмения отдаёт посох
        check(Files.readString(Path.of("src/com/explosionwand/Boss2.java")).contains("\"explosionwand:eclipse_staff\""), "Посох Затмения лежит в сундуке Короля Затмения");
        // Грань: ускорение огненного шара разворачивается с той же длиной
        double[] w = ArmorSetLogic.turnPower(0.0, 0.0, 0.2, 1, 0, 0);
        check(Math.abs(w[0] - 0.2) < 1e-9 && w[1] == 0 && w[2] == 0, "turnPower: длина сохраняется, направление новое");
        System.out.println("RiftStaffTest OK");
    }
}
