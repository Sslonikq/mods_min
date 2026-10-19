package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

import java.util.*;
import java.util.function.Function;

/** Наши измерения и безопасная переброска туда. */
final class Dims {
    static ResourceKey<Level> key(String id) { return ResourceKey.m_135785_(Registries.f_256858_, new ResourceLocation("explosionwand", id)); }
    static final ResourceKey<Level> VOID = key("void");
    static final ResourceKey<Level> HAUNT = key("haunt");
    static final ResourceKey<Level> INVERTED = key("inverted");
    static final ResourceKey<Level> ECLIPSE = key("eclipse");
    static final ResourceKey<Level> DREAM = key("dream");
    static final ResourceKey<Level> SKY = key("sky");
    static final ResourceKey<Level> MAZE = key("maze");
    /** Старое измерение арены (миры 2.6.x): больше не используется, игроков оттуда отправляем в обычный мир. */
    static final ResourceKey<Level> OLD_ARENA = key("arena");
    /** Уровень пола во всех аренах боссов (ECLIPSE, DREAM, SKY). */
    static final int ARENA_FLOOR = 64;
    static final Random RNG = Fx.RNG;

    static ServerLevel get(ServerLevel any, ResourceKey<Level> k) { return any.m_7654_().m_129880_(k); }
    static boolean is(ServerLevel l, ResourceKey<Level> k) { return get(l, k) == l; }
    static boolean isOurs(ServerLevel l) { return is(l, VOID) || is(l, HAUNT) || is(l, INVERTED) || is(l, ECLIPSE) || is(l, DREAM) || is(l, SKY) || is(l, MAZE); }

    /** Откуда игрок попал в Перевёртыш — чтобы вернуть обратно. */
    static final Map<UUID, Object[]> ORIGIN = new HashMap<>();

    static final class Fix { Entity e; ServerLevel level; int age; boolean inverted; }
    static final List<Fix> FIXES = new ArrayList<>();

    static Entity teleport(Entity e, ServerLevel dest, double x, double y, double z) {
        return e.changeDimension(dest, new ITeleporter() {
            @Override
            public PortalInfo getPortalInfo(Entity ent, ServerLevel d, Function<ServerLevel, PortalInfo> def) {
                return new PortalInfo(new Vec3(x, y, z), new Vec3(0, 0, 0), ent.m_146908_(), 0.0F);
            }
        });
    }

    /** Отправить в одно из наших измерений. */
    static boolean send(Entity e, ServerLevel from, ResourceKey<Level> k) {
        ServerLevel dest = get(from, k);
        if (dest == null || dest == from) return false;
        if (k == MAZE) {      // лабиринт Некто: свой лабиринт на каждого, без «вытаскивания на поверхность»
            Vec3 p = e.m_20182_();
            ORIGIN.put(e.m_20148_(), new Object[]{from.m_46472_(), p.f_82479_, p.f_82480_, p.f_82481_});
            Vec3 start = Maze.create(dest, e);
            return teleport(e, dest, start.f_82479_, start.f_82480_, start.f_82481_) != null;
        }
        boolean inv = (k == INVERTED), ecl = (k == ECLIPSE), dream = (k == DREAM), sky = (k == SKY);
        if (inv || ecl || dream || sky) {
            Vec3 p = e.m_20182_();
            ORIGIN.put(e.m_20148_(), new Object[]{from.m_46472_(), p.f_82479_, p.f_82480_, p.f_82481_});
        }
        int x = (ecl || dream || sky) ? 0 : RNG.nextInt(1200) - 600, z = ecl ? -26 : dream ? -28 : sky ? -24 : RNG.nextInt(1200) - 600;
        if (ecl) Arena2.ensureBuilt(dest);
        if (dream) Arena3.ensureBuilt(dest);
        if (sky) Arena4.ensureBuilt(dest);
        double y = inv ? 90 : (ecl || dream || sky) ? ARENA_FLOOR : safeY(dest, x, z);
        Entity moved = teleport(e, dest, x + 0.5D, y, z + 0.5D);
        if (moved != null) {
            Fix f = new Fix(); f.e = moved; f.level = dest; f.inverted = inv;
            FIXES.add(f);
        }
        return moved != null;
    }

    /** Игрока из устаревшего измерения arena (старый мир 2.6.x) отправляет в обычный мир на точку возрождения (или мировой спавн). */
    static void evacuateOldArena(ServerLevel sl) {
        if (!is(sl, OLD_ARENA)) return;
        ServerLevel dest = get(sl, Level.f_46428_);
        if (dest == null) return;
        for (net.minecraft.server.level.ServerPlayer p : new ArrayList<>(sl.m_6907_())) {
            BlockPos bed = Level.f_46428_.equals(p.m_8963_()) ? p.m_8961_() : null;
            BlockPos sp = bed != null ? bed : dest.m_220360_();
            int x = sp.m_123341_(), z = sp.m_123343_();
            int y = bed != null ? nearY(dest, x, bed.m_123342_(), z) : safeY(dest, x, z);
            teleport(p, dest, x + 0.5D, y, z + 0.5D);
        }
    }

    /** Вытаскивает застрявших в блоках после переноса. */
    static void tickFixes(ServerLevel level) {
        Iterator<Fix> it = FIXES.iterator();
        while (it.hasNext()) {
            Fix f = it.next();
            if (f.level != level) continue;
            f.age++;
            if (f.age == 1 || f.age == 5 || f.age == 15 || f.age == 40) unstick(f);
            if (f.age >= 40 || !f.e.m_6084_()) it.remove();
        }
    }

    /** Высота рядом с точкой возрождения (кроватью) sy: сама точка или ближайший свободный проём в 3 блоках выше; иначе поверхность (safeY). */
    static int nearY(ServerLevel l, int x, int sy, int z) {
        l.m_6325_(x >> 4, z >> 4);
        for (int dy = 0; dy <= 3; dy++) {
            int yy = sy + dy;
            if (yy > l.m_141937_() + 1 && air(l, x, yy, z) && air(l, x, yy + 1, z) && !air(l, x, yy - 1, z)) return yy;
        }
        return safeY(l, x, z);
    }

    /** Высота поверхности: сначала гарантированно грузим участок мира, потом ищем место, где можно стоять. */
    static int safeY(ServerLevel l, int x, int z) {
        l.m_6325_(x >> 4, z >> 4);
        int h = l.m_6924_(Heightmap.Types.valueOf("MOTION_BLOCKING_NO_LEAVES"), x, z);
        if (h > l.m_141937_() + 1 && air(l, x, h, z) && air(l, x, h + 1, z)) return h;
        for (int yy = l.m_151558_() - 3; yy > l.m_141937_(); yy--)
            if (!air(l, x, yy, z) && air(l, x, yy + 1, z) && air(l, x, yy + 2, z)) return yy + 1;
        return Math.max(h, l.m_141937_() + 80);
    }

    static boolean air(ServerLevel l, int x, int y, int z) { return l.m_8055_(new BlockPos(x, y, z)).m_60795_(); }

    static void unstick(Fix f) {
        Entity e = f.e; ServerLevel l = f.level;
        Vec3 p = e.m_20182_();
        int x = (int) Math.floor(p.f_82479_), y = (int) Math.floor(p.f_82480_), z = (int) Math.floor(p.f_82481_);
        if (f.inverted) {
            if (air(l, x, y, z) && air(l, x, y + 1, z)) return;
            for (int yy = Math.min(y, 120); yy > 4; yy--) if (air(l, x, yy, z) && air(l, x, yy + 1, z)) { e.m_6021_(x + 0.5D, yy, z + 0.5D); e.f_19789_ = 0.0F; return; }
            return;
        }
        // всегда на настоящую поверхность (не в пещеру и не в блоки)
        int h = safeY(l, x, z);
        boolean stuck = !(air(l, x, y, z) && air(l, x, y + 1, z));
        if (stuck || y < h - 1) { e.m_6021_(x + 0.5D, h, z + 0.5D); e.f_19789_ = 0.0F; }
    }

    /** Вернуть из Перевёртыша туда, откуда пришёл. */
    static void sendHome(Entity e, ServerLevel from) {
        Object[] o = ORIGIN.remove(e.m_20148_());
        ServerLevel dest = null; double x = 0, y = 100, z = 0;
        if (o != null) {
            @SuppressWarnings("unchecked") ResourceKey<Level> k = (ResourceKey<Level>) o[0];
            dest = get(from, k); x = (Double) o[1]; y = (Double) o[2]; z = (Double) o[3];
        }
        if (dest == null) {
            dest = get(from, Level.f_46428_);
            y = safeY(dest, 0, 0);
        }
        Entity moved = teleport(e, dest, x, y + 0.2D, z);
        if (moved instanceof net.minecraft.world.entity.LivingEntity le) {
            var sf = Fx.effect("slow_falling");
            if (sf != null) le.m_7292_(new net.minecraft.world.effect.MobEffectInstance(sf, 200, 0));
            Fx.sound(dest, x, y, z, "block.portal.travel", 0.6F, 1.6F);
        }
    }
}
