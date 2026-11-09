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
        for (Runnable r : due) try { r.run(); } catch (Exception e) { fail(e); }
    }

    private static int failures;

    /** Сбой отложенной задачи: поведение прежнее (задача пропущена), но след в логе - по 1, 10, 100... */
    private static void fail(Exception e) {
        int n = ++failures;
        if (n == 1 || n == 10 || n == 100 || n == 1000) {
            System.err.println("[explosionwand] Later: сбой отложенной задачи №" + n + ": " + e);
            if (n == 1) e.printStackTrace();
        }
    }
}
