package com.explosionwand;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Выполнение серверных команд от имени мира (тихо, с полными правами). */
final class Cmd {
    static void run(ServerLevel level, double x, double y, double z, String cmd) {
        try {
            CommandSourceStack src = level.m_7654_().m_129893_().m_81327_(level).m_81348_(new Vec3(x, y, z)).m_81324_();
            level.m_7654_().m_129892_().m_230957_(src, cmd);
        } catch (Exception ignored) {}
    }

    static String f(double d) { return String.format(Locale.ROOT, "%.3f", d); }
    static String ff(double d) { return f(d) + "f"; }

    /** Найти сущности с тегом рядом с точкой. */
    static List<Entity> tagged(ServerLevel level, double x, double y, double z, double r, String tag) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : level.m_6249_(null, new AABB(x - r, y - r, z - r, x + r, y + r, z + r), e -> e.m_19880_().contains(tag))) out.add(e);
        return out;
    }

    static boolean hasTagPrefix(Entity e, String prefix) {
        for (String t : e.m_19880_()) if (t.startsWith(prefix)) return true;
        return false;
    }
}
