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
        net.minecraft.core.BlockPos s = HellGate.HELL_SOCKET;
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
        check(HellGate.inReturnGate(s) && HellGate.inReturnGate(HellGate.at(s, 0, 2, -4)) && HellGate.inReturnGate(HellGate.at(s, 0, 0, -2)), "return gate cells protected");
        check(!HellGate.inReturnGate(new net.minecraft.core.BlockPos(0, 63, 0)), "floor is not protected");
        check(HellGate.HELL_Y == 64.0D && HellGate.HELL_SOCKET_Y - 4 == 64, "hell portal stands on the platform (y=64)");
    }
}
