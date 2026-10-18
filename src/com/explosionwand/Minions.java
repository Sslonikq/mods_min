package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;

/** Приспешники боссов — обычные мобы, призванные командой с тегами босса (убираются вместе с ним). */
final class Minions {
    /** Сколько живых приспешников с тегом вокруг точки. */
    static int count(ServerLevel level, double x, double y, double z, String tag) {
        int n = 0;
        for (Entity e : Cmd.tagged(level, x, y, z, 80, tag)) if (e.m_6084_()) n++;
        return n;
    }

    static String name(String text, String color) {
        return "CustomName:'{\"text\":\"" + text + "\",\"color\":\"" + color + "\",\"bold\":true}'";
    }

    static String common(String bossTag, String minTag, int hp, String... extraTags) {
        StringBuilder tags = new StringBuilder("\"" + bossTag + "\",\"" + minTag + "\"");
        for (String x : extraTags) tags.append(",\"").append(x).append("\"");
        return "Tags:[" + tags + "],PersistenceRequired:1b,DeathLootTable:\"minecraft:empty\","
                + "Attributes:[{Name:\"minecraft:generic.max_health\",Base:" + hp + "d}],Health:" + hp + "f";
    }

    static void spawn(ServerLevel level, String type, double x, double y, double z, String nbt) {
        Cmd.run(level, x, y, z, "summon minecraft:" + type + " " + Cmd.f(x) + " " + Cmd.f(y) + " " + Cmd.f(z) + " {" + nbt + "}");
        Fx.send(level, Fx.particle("poof"), x, y + 1, z, 12, 0.4, 0.6, 0.4, 0.02);
    }

    /** Кожаная броня, окрашенная в цвет (int RGB). */
    static String dyed(String piece, int rgb) { return "{id:\"minecraft:leather_" + piece + "\",Count:1b,tag:{display:{color:" + rgb + "}}}"; }

    /** Тело босса или его хитбокс (не приспешник). */
    static boolean bossPart(Entity e) {
        for (String t : e.m_19880_()) if (t.startsWith("ewb") && (t.endsWith("_host") || t.endsWith("_hit"))) return true;
        return false;
    }
    /** Приспешник босса — с ним посохи обращаются как с обычным мобом. */
    static boolean minion(Entity e) {
        for (String t : e.m_19880_()) if (t.startsWith("ewb") && t.endsWith("_min")) return true;
        return false;
    }
    /** Можно ли посоху менять ИИ/двигать этого моба. */
    static boolean controllable(Entity e) { return minion(e) || !Cmd.hasTagPrefix(e, "ew"); }

    static void killAll(List<Entity> es) { for (Entity e : es) if (e.m_6084_()) e.m_146870_(); }
}
