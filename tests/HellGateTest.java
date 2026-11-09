package com.explosionwand;

import java.util.HashSet;
import java.util.Set;

/** Раскладка алтаря-портала в Ад: 16 блоков рамки, проём 3x3, гнездо не входит в рамку, обе оси. */
public class HellGateTest {
    static void check(boolean ok, String m) { if (!ok) { System.out.println("FAIL: " + m); System.exit(1); } }

    public static void main(String[] a) {
        check(HellGate.FRAME.length == 15, "frame 15 blocks + socket = 16, got " + HellGate.FRAME.length);
        check(HellGate.INNER.length == 9, "inner 3x3");
        Set<String> seen = new HashSet<>();
        for (int[] o : HellGate.FRAME) { check(seen.add(o[0] + "," + o[1]), "frame cell unique"); check(!(o[0] == 0 && o[1] == 0), "socket not in frame"); check(Math.abs(o[0]) <= 2 && o[1] <= 0 && o[1] >= -4, "frame in 5x5"); }
        for (int[] o : HellGate.INNER) { check(seen.add(o[0] + "," + o[1]), "inner does not overlap frame"); check(Math.abs(o[0]) <= 1 && o[1] <= -1 && o[1] >= -3, "inner in 3x3 under socket"); }
        check(seen.size() == 24, "24 cells + socket = 5x5");
        int[] o0 = HellGate.off(0, 2, -4), o1 = HellGate.off(1, -2, -1);
        check(o0[0] == 2 && o0[1] == -4 && o0[2] == 0, "axis 0");
        check(o1[0] == 0 && o1[1] == -1 && o1[2] == -2, "axis 1");
        // восстановление обратного портала: целая раскладка -> intact; любая потеря -> нет
        java.util.Map<String, String> w = new java.util.HashMap<>();
        java.util.function.Function<net.minecraft.core.BlockPos, String> kind = b -> w.getOrDefault(b.m_123341_() + "," + b.m_123342_() + "," + b.m_123343_(), "");
        net.minecraft.core.BlockPos s = HellGate.socketAt(63);
        java.util.function.BiConsumer<net.minecraft.core.BlockPos, String> put = (b, k) -> w.put(b.m_123341_() + "," + b.m_123342_() + "," + b.m_123343_(), k);
        put.accept(s, "socket");
        for (int[] o : HellGate.FRAME) put.accept(HellGate.at(s, 0, o[0], o[1]), "frame");
        for (int[] o : HellGate.INNER) put.accept(HellGate.at(s, 0, o[0], o[1]), "portal");
        check(HellGate.intact(s, 0, kind), "full portal is intact");
        check(!HellGate.intact(s, 1, kind), "wrong axis is not intact");
        for (int[] o : HellGate.INNER) { net.minecraft.core.BlockPos c = HellGate.at(s, 0, o[0], o[1]); String old = kind.apply(c); w.remove(c.m_123341_() + "," + c.m_123342_() + "," + c.m_123343_()); check(!HellGate.intact(s, 0, kind), "washed-away portal cell detected"); put.accept(c, old); }
        for (int[] o : HellGate.FRAME) { net.minecraft.core.BlockPos c = HellGate.at(s, 0, o[0], o[1]); w.remove(c.m_123341_() + "," + c.m_123342_() + "," + c.m_123343_()); check(!HellGate.intact(s, 0, kind), "missing frame cell detected"); put.accept(c, "frame"); }
        w.put(s.m_123341_() + "," + s.m_123342_() + "," + s.m_123343_(), "");
        check(!HellGate.intact(s, 0, kind), "missing socket detected");
        // зона защиты обратного портала
        check(HellGate.inReturnGate(63, s) && HellGate.inReturnGate(63, HellGate.at(s, 0, 2, -4)) && HellGate.inReturnGate(63, HellGate.at(s, 0, 0, -2)), "return gate cells protected");
        check(!HellGate.inReturnGate(63, new net.minecraft.core.BlockPos(0, 63, 0)), "floor is not protected");
        java.util.List<net.minecraft.core.BlockPos> boom = new java.util.ArrayList<>(java.util.List.of(HellGate.at(s, 0, 2, -4), s, HellGate.at(s, 0, 0, -2), new net.minecraft.core.BlockPos(0, 63, 0)));
        HellGate.stripReturnGate(63, boom);
        check(boom.size() == 1 && boom.get(0).m_123342_() == 63, "explosion keeps only cells outside the return gate");
        check(HellGate.spawnY(63) == 64.0D && HellGate.socketAt(63).m_123342_() - 4 == 64, "legacy hell portal stands on the platform (y=64)");
        // высота считается, а не хардкодится: гнездо/защита следуют за полом
        for (int f : new int[]{41, 63, 90, 119}) {
            net.minecraft.core.BlockPos sk = HellGate.socketAt(f);
            check(sk.m_123342_() == f + 5 && sk.m_123341_() == 0 && sk.m_123343_() == -6, "socket follows floor " + f);
            check(HellGate.at(sk, 0, 0, -4).m_123342_() == f + 1, "frame bottom stands on the floor " + f);
            check(HellGate.spawnY(f) == f + 1, "player stands on the floor " + f);
            check(HellGate.inReturnGate(f, sk) && HellGate.inReturnGate(f, HellGate.at(sk, 0, -2, -4)), "gate protected at floor " + f);
            check(!HellGate.inReturnGate(f, new net.minecraft.core.BlockPos(0, f, -6)) && !HellGate.inReturnGate(f, new net.minecraft.core.BlockPos(5, f + 3, -6)), "floor and outside not protected at " + f);
        }
        check(!HellGate.inReturnGate(0, HellGate.socketAt(0)) && !HellGate.inReturnGate(90, HellGate.socketAt(63)), "no gate before platform; other floor is not the gate");
        // поиск пола: 0 свободно, 1 твёрдо, 2 жидкость; y=40 - уровень лавы
        java.util.function.Function<int[], java.util.function.IntUnaryOperator> col = c -> y -> { int v = 0; for (int i = 0; i + 2 < c.length; i += 3) if (y >= c[i] && y <= c[i + 1]) v = c[i + 2]; return v; };
        check(HellGate.pickFloor(col.apply(new int[]{0, 40, 2, 41, 55, 1})) == 55, "solid ground from 41 up: top surface at 55");
        check(HellGate.pickFloor(col.apply(new int[]{0, 40, 2, 52, 53, 1, 54, 54, 1})) == 54, "island above the lava sea");
        check(HellGate.pickFloor(col.apply(new int[]{0, 47, 2, 48, 60, 1})) == 60, "lava lake up to 47 is skipped");
        check(HellGate.pickFloor(col.apply(new int[]{0, 70, 2, 71, 71, 1})) == 71, "single block above a lava lake is a floor");
        check(HellGate.pickFloor(col.apply(new int[]{0, 40, 2})) == HellGate.FLOOR_FALLBACK && HellGate.FLOOR_FALLBACK == 42, "all lava: fallback 42 (just above the lava)");
        check(HellGate.pickFloor(col.apply(new int[]{0, 100, 1, 101, 101, 0, 102, 102, 2})) == 102, "lava lying on top of the land: the floor is the lava surface (102), not a walk through it");
        check(HellGate.pickFloor(col.apply(new int[]{0, 90, 1, 91, 99, 2})) == 99, "lava pool over the land: floor at its surface");
        check(HellGate.pickFloor(col.apply(new int[]{0, 40, 2, 41, 41, 1})) == 41, "solid at 41 right above the lava: floor 41, not lower");
        check(HellGate.pickFloor(col.apply(new int[]{0, 39, 1, 40, 40, 2})) == HellGate.FLOOR_FALLBACK, "rock under the lava does not count (below 41)");
        check(HellGate.pickFloor(col.apply(new int[]{0, 45, 1, 46, 50, 0, 51, 70, 1})) == 70, "cave 46-50 under rock: top surface 70, not the cave floor 45");
        check(HellGate.pickFloor(col.apply(new int[]{0, 40, 2, 41, 44, 1, 45, 52, 0, 53, 70, 1, 71, 80, 0})) == 70, "cave floor 44 below, surface 70 chosen");
        check(HellGate.pickFloor(col.apply(new int[]{0, 100, 1, 101, 101, 0, 102, 110, 1, 111, 200, 0})) == 110, "overhang: first from top is the roof of the rock (110)");
        check(HellGate.FLOOR_MIN == 41, "never below 41");
        // классификация по блокам: полом только рельеф
        java.util.function.BiFunction<String, Boolean, Integer> cl = (id, empty) -> HellGate.classifyId(id, false, empty);
        check(cl.apply("explosionwand:ash_soil", false) == 1 && cl.apply("minecraft:soul_sand", false) == 1 && cl.apply("explosionwand:eclipse_stone", false) == 1, "relief is solid");
        check(cl.apply("explosionwand:smoldering_log", false) == 0 && cl.apply("explosionwand:charred_log", false) == 0 && cl.apply("explosionwand:coal_mushroom_cap", false) == 0 && cl.apply("explosionwand:coal_mushroom_stem", false) == 0, "trunk and mushroom are not a floor");
        check(cl.apply("explosionwand:bone_block", false) == 0 && cl.apply("minecraft:bone_block", false) == 0 && cl.apply("explosionwand:dark_obsidian", false) == 0 && cl.apply("explosionwand:eclipse_crystal_block", false) == 0 && cl.apply("explosionwand:archmage_stone", false) == 0, "bone tree, spire and pillar are not a floor");
        check(cl.apply("explosionwand:smoldering_leaves", false) == 0 && cl.apply("explosionwand:dry_grass", true) == 0 && cl.apply("minecraft:air", true) == 0, "leaves, plants and air are free");
        check(HellGate.classifyId("minecraft:lava", true, true) == 2 && HellGate.classifyId("explosionwand:ash_soil", true, false) == 2, "liquid is 2");
        // колонна: земля 68, ствол 69-74, листва 75-78 -> 68
        java.util.function.IntUnaryOperator tree = y -> y <= 68 ? HellGate.classifyId("explosionwand:ash_soil", false, false)
                : y <= 74 ? HellGate.classifyId("explosionwand:smoldering_log", false, false)
                : y <= 78 ? HellGate.classifyId("explosionwand:smoldering_leaves", false, false) : 0;
        check(HellGate.pickFloor(tree) == 68, "ground 68, trunk 69-74, leaves 75-78 -> floor 68");
        java.util.function.IntUnaryOperator spire = y -> y <= 60 ? HellGate.classifyId("explosionwand:eclipse_stone", false, false) : y <= 90 ? HellGate.classifyId("explosionwand:dark_obsidian", false, false) : 0;
        check(HellGate.pickFloor(spire) == 60, "spire on the ground does not lift the platform");
    }
}
