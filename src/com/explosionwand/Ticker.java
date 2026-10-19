package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;

import java.util.Iterator;
import java.util.List;

public final class Ticker {
    static long ticks = 0;

    static <T> void run(List<T> list, ServerLevel lvl, java.util.function.Function<T, ServerLevel> lf, java.util.function.Predicate<T> step) {
        Iterator<T> it = list.iterator();
        while (it.hasNext()) { T x = it.next(); if (lf.apply(x) == lvl && step.test(x)) it.remove(); }
    }

    public static void onLevelTick(TickEvent.LevelTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.level instanceof ServerLevel sl)) return;
        ticks++;
        run(BlackHole.ACTIVE, sl, h -> h.level, BlackHole::tick);
        run(IceStorm.ACTIVE, sl, s -> s.level, IceStorm::tick);
        run(Rift.ACTIVE, sl, r -> r.level, Rift::tick);
        run(Portal.ACTIVE, sl, p -> p.level, Portal::tick);
        run(Chronos.ACTIVE, sl, c -> c.level, Chronos::tick);
        run(Mimic.ACTIVE, sl, m -> m.level, Mimic::tick);
        run(Tesseract.PORTALS, sl, p -> p.level, Tesseract::tick);
        Tesseract.tickSessions(sl);
        run(Ember.ACTIVE, sl, e -> e.level, Ember::tick);
        run(Altar.ACTIVE, sl, a -> a.level, Altar::tick);
        run(Fist.ACTIVE, sl, f -> f.level, Fist::tick);
        if (Boss.ACTIVE != null && Boss.ACTIVE.level == sl && Boss.ACTIVE.tick()) Boss.ACTIVE = null;
        if (Boss2.ACTIVE != null && Boss2.ACTIVE.level == sl && Boss2.ACTIVE.tick()) Boss2.ACTIVE = null;
        run(TimeStop.ACTIVE, sl, s2 -> s2.level, TimeStop::tick);
        EclipseStaff.tick(sl);
        if (Boss3.ACTIVE != null && Boss3.ACTIVE.level == sl && Boss3.ACTIVE.tick()) Boss3.ACTIVE = null;
        if (Boss4.ACTIVE != null && Boss4.ACTIVE.level == sl && Boss4.ACTIVE.tick()) Boss4.ACTIVE = null;
        DreamStaff.tick(sl);
        ArcaneSet.tick(sl);
        WhaleStaff.tick(sl);
        run(Comet.ACTIVE, sl, c -> c.level, Comet::tick);
        PortalGun.tick(sl);
        Possess.tick(sl);
        Later.tick(sl);
        Dims.tickFixes(sl);
        Dungeons.tick(sl);
        if (Dims.is(sl, Dims.VOID)) VoidWorld.tick(sl, ticks);
        else if (Dims.is(sl, Dims.HAUNT)) Haunt.tick(sl);
        else if (Dims.is(sl, Dims.INVERTED)) Inverted.tick(sl, ticks);
        else if (Dims.is(sl, Dims.ARENA)) Arena.tick(sl);
        else if (Dims.is(sl, Dims.ECLIPSE)) Arena2.tick(sl);
        else if (Dims.is(sl, Dims.DREAM)) Arena3.tick(sl);
        else if (Dims.is(sl, Dims.SKY)) Arena4.tick(sl);
        else if (Dims.is(sl, Dims.MAZE)) Maze.tick(sl);
    }
}
