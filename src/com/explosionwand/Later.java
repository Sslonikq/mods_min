package com.explosionwand;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Отложенные действия (через N тиков). */
final class Later {
    static final class Task { ServerLevel level; int ticks; Runnable r; }
    static final List<Task> TASKS = new ArrayList<>();

    static void run(ServerLevel level, int ticks, Runnable r) {
        Task t = new Task(); t.level = level; t.ticks = ticks; t.r = r; TASKS.add(t);
    }

    static void tick(ServerLevel level) {
        List<Runnable> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) { Task t = it.next(); if (t.level == level && --t.ticks <= 0) { due.add(t.r); it.remove(); } }
        for (Runnable r : due) try { r.run(); } catch (Exception ignored) {}
    }
}
