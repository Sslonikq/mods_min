package com.explosionwand;

/** Шаг Посоха Разлома: не проходит в данж и сквозь его угол. */
public class RiftStaffTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    static RiftStaff.Probe probe(double dungeonFrom, double dungeonTo) {
        return new RiftStaff.Probe() {
            public boolean free(double x, double y, double z) { return true; }
            public boolean dungeon(double x, double y, double z) { return x >= dungeonFrom && x <= dungeonTo; }
        };
    }

    public static void main(String[] a) {
        check(RiftStaff.pick(0, 0, 0, 1, 0, 0, probe(100, 200)) == 8.0D, "open field: full step");
        check(RiftStaff.pick(0, 0, 0, 1, 0, 0, probe(5, 6)) == 4.0D, "corner in the path: stop before it, never beyond");
        check(RiftStaff.pick(0, 0, 0, 1, 0, 0, probe(8, 20)) == 7.0D, "target inside: shorter step before dungeon");
        check(RiftStaff.pick(0, 0, 0, 1, 0, 0, probe(2, 20)) == RiftStaff.DUNGEON, "dungeon right ahead: refused");
        RiftStaff.Probe none = new RiftStaff.Probe() {
            public boolean free(double x, double y, double z) { return false; }
            public boolean dungeon(double x, double y, double z) { return false; }
        };
        check(RiftStaff.pick(0, 0, 0, 1, 0, 0, none) == RiftStaff.NO_ROOM, "no room");
        System.out.println("RiftStaffTest OK");
    }
}
