package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Посох Разлома (посох 1-го уровня, добыча Сердца Разлома).
 *  ПКМ        — короткая трещина по линии прицела (до 14 блоков): мобы рядом с линией затягиваются к ней и получают урон.
 *  Shift+ПКМ  — шаг сквозь разлом: перенос на 8 блоков вперёд (если там нет места — на ближайшую свободную точку ближе).
 * В данжах не работает (DungeonGuard.forbidden).
 */
final class RiftStaff {
    static final double RANGE = 14.0D, WIDTH = 2.6D, STEP = 8.0D;
    static final float DAMAGE = 6.0F;
    static final int CRACK_CD = 100, STEP_CD = 80;
    static DustParticleOptions VIOLET, DARK;

    static void init() {
        if (VIOLET != null) return;
        VIOLET = new DustParticleOptions(new Vector3f(0.6F, 0.2F, 1.0F), 1.4F);
        DARK = new DustParticleOptions(new Vector3f(0.12F, 0.0F, 0.25F), 1.6F);
    }

    /** @return перезарядка в тиках (0 - ничего не вышло) */
    static int use(ServerLevel l, ServerPlayer p) {
        init();
        if (p.m_6144_()) return step(l, p) ? STEP_CD : 0;
        crack(l, p);
        return CRACK_CD;
    }

    static void crack(ServerLevel l, ServerPlayer p) {
        Aim aim = Aim.of(l, p, RANGE);
        Vec3 a = aim.eye.m_82549_(aim.look.m_82490_(1.0D));
        Vec3 b = aim.target;
        double len = Math.max(1.0D, Math.sqrt(sq(b.f_82479_ - a.f_82479_) + sq(b.f_82480_ - a.f_82480_) + sq(b.f_82481_ - a.f_82481_)));
        double ux = (b.f_82479_ - a.f_82479_) / len, uy = (b.f_82480_ - a.f_82480_) / len, uz = (b.f_82481_ - a.f_82481_) / len;
        var rp = Fx.particle("reverse_portal");
        int n = 0;
        for (double s = 0; s <= len; s += 0.6D, n++) {
            double x = a.f_82479_ + ux * s, y = a.f_82480_ + uy * s, z = a.f_82481_ + uz * s;
            Fx.send(l, VIOLET, x, y, z, 2, 0.12, 0.3, 0.12, 0);
            Fx.send(l, DARK, x, y - 0.3, z, 1, 0.2, 0.4, 0.2, 0);
            if (n % 3 == 0) Fx.send(l, rp, x, y, z, 2, 0.2, 0.3, 0.2, 0.05);
        }
        Fx.sound(l, a.f_82479_, a.f_82480_, a.f_82481_, "block.end_portal.spawn", 1.0F, 1.8F);
        Fx.sound(l, a.f_82479_, a.f_82480_, a.f_82481_, "entity.enderman.teleport", 1.0F, 0.6F);
        AABB box = new AABB(Math.min(a.f_82479_, b.f_82479_) - WIDTH, Math.min(a.f_82480_, b.f_82480_) - WIDTH, Math.min(a.f_82481_, b.f_82481_) - WIDTH,
                Math.max(a.f_82479_, b.f_82479_) + WIDTH, Math.max(a.f_82480_, b.f_82480_) + WIDTH, Math.max(a.f_82481_, b.f_82481_) + WIDTH);
        for (Entity e : l.m_6249_(p, box, e -> e.m_6084_() && !e.m_5833_() && e instanceof LivingEntity && !(e instanceof Player))) {
            // сбой на одном мобе не должен ронять игру: логируем один раз и идём дальше
            try { pull(l, p, e, a, ux, uy, uz, len); }
            catch (RuntimeException | LinkageError ex) { if (!warned) { warned = true; System.err.println("[explosionwand] Посох Разлома: сбой на мобе " + e.m_19880_() + ": " + ex); ex.printStackTrace(); } }
        }
    }

    static boolean warned = false;

    /** Затянуть одного моба к трещине и ударить (урон indirect_magic от игрока: броню игнорирует, как до 2.14.0). */
    private static void pull(ServerLevel l, ServerPlayer p, Entity e, Vec3 a, double ux, double uy, double uz, double len) {
        if (!Minions.controllable(e)) return;
        if (e.m_19880_().contains("ewd")) return;   // мобы данжа, хосты боссов и ew-существа не трогаем (в данже посох и так запрещён)
        if (e instanceof AbstractVillager) return;               // жители и торговцы
        if (e instanceof TamableAnimal t && (t.m_21830_(p) || t.m_21824_())) return;   // свои и чужие ручные питомцы
        Vec3 c = e.m_20182_();
        double ex = c.f_82479_, ey = c.f_82480_ + e.m_20206_() / 2.0D, ez = c.f_82481_;
        double t = Math.max(0.0D, Math.min(len, (ex - a.f_82479_) * ux + (ey - a.f_82480_) * uy + (ez - a.f_82481_) * uz));
        double qx = a.f_82479_ + ux * t, qy = a.f_82480_ + uy * t, qz = a.f_82481_ + uz * t;
        double dx = qx - ex, dy = qy - ey, dz = qz - ez, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > WIDTH) return;
        double k = Math.min(1.1D, 0.25D + d * 0.35D) / Math.max(0.3D, d);
        e.m_20256_(new Vec3(dx * k, dy * k * 0.6D + 0.1D, dz * k)); e.f_19864_ = true;
        e.m_6469_(l.m_269111_().m_269104_(p, p), DAMAGE);
        Fx.send(l, VIOLET, ex, ey, ez, 12, 0.3, 0.5, 0.3, 0);
    }

    /** Шаг сквозь разлом: ищем самую дальнюю точку до 8 блоков, где помещается игрок (2 клетки воздуха). */
    static boolean step(ServerLevel l, ServerPlayer p) {
        Vec3 look = p.m_20252_(1.0F);
        double hx = look.f_82479_, hz = look.f_82481_, hl = Math.sqrt(hx * hx + hz * hz);
        double dx = look.f_82479_, dy = look.f_82480_, dz = look.f_82481_;
        if (hl >= 0.35D) { dx = hx / hl; dz = hz / hl; }          // обычный взгляд - шагаем по горизонту (с наклоном взгляда)
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len; dy /= len; dz /= len;
        double sx = p.m_20185_(), sy = p.m_20186_(), sz = p.m_20189_();
        final double fdx = dx, fdy = dy, fdz = dz;
        double s = pick(sx, sy, sz, dx, dy, dz, new Probe() {
            public boolean free(double x, double y, double z) { return RiftStaff.free(l, x, y, z); }
            public boolean dungeon(double x, double y, double z) { return Dungeons.near(l, x, y, z, 1); }
        });
        if (s == NO_ROOM || s == DUNGEON) {
            Cmd.run(l, sx, sy, sz, "title " + p.m_36316_().getName() + " actionbar {\"translate\":\""
                    + (s == DUNGEON ? "explosionwand.rift_staff.no_dungeon" : "explosionwand.rift_staff.no_room") + "\",\"color\":\"red\"}");
            return false;
        }
        double x = sx + fdx * s, y = sy + fdy * s, z = sz + fdz * s;
        rift(l, sx, sy, sz);
        p.m_6021_(x, y, z);
        p.f_19789_ = 0.0F;
        rift(l, x, y, z);
        Fx.sound(l, x, y, z, "entity.enderman.teleport", 1.0F, 0.5F);
        return true;
    }

    static final double NO_ROOM = -1.0D, DUNGEON = -2.0D;

    /** Проверки мира для выбора точки шага (в тестах подменяются). */
    interface Probe {
        boolean free(double x, double y, double z);
        boolean dungeon(double x, double y, double z);
    }

    /**
     * Дальность шага: самая дальняя свободная точка от STEP до 2 блоков, у которой ни сама точка,
     * ни путь к ней (через ~1 блок) не задевает данж. NO_ROOM - нет места, DUNGEON - место есть, но только через данж.
     */
    static double pick(double sx, double sy, double sz, double dx, double dy, double dz, Probe pr) {
        boolean blocked = false;
        for (double s = STEP; s >= 2.0D; s -= 1.0D) {
            if (!pr.free(sx + dx * s, sy + dy * s, sz + dz * s)) continue;
            boolean bad = false;
            for (double t = 1.0D; t < s + 0.01D && !bad; t += 1.0D) bad = pr.dungeon(sx + dx * t, sy + dy * t, sz + dz * t);
            if (!bad) bad = pr.dungeon(sx + dx * s, sy + dy * s, sz + dz * s);
            if (bad) { blocked = true; continue; }
            return s;
        }
        return blocked ? DUNGEON : NO_ROOM;
    }

    /** Клетки ног и головы свободны (воздух), в пределах мира. */
    static boolean free(ServerLevel l, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        BlockPos feet = new BlockPos(bx, by, bz), head = new BlockPos(bx, by + 1, bz);
        if (by <= l.m_141937_() + 1 || by + 2 >= l.m_151558_() || !l.m_46805_(feet)) return false;
        return l.m_8055_(feet).m_60795_() && l.m_8055_(head).m_60795_();
    }

    static void rift(ServerLevel l, double x, double y, double z) {
        Fx.send(l, Fx.particle("reverse_portal"), x, y + 1, z, 40, 0.4, 0.8, 0.4, 0.3);
        Fx.send(l, VIOLET, x, y + 1, z, 20, 0.4, 0.8, 0.4, 0);
    }

    static double sq(double v) { return v * v; }
}
