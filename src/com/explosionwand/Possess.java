package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Палка Вселения: вселяешься в моба — ты невидим, а моб ходит, крутит головой и смотрит туда же, куда ты.
 * ПКМ — способность того, в кого вселился (крипер взрывается, скелет стреляет, гаст плюётся огнём...).
 * Shift+ПКМ — покинуть тело.
 */
final class Possess {
    static final class S { Mob mob; int fuse = -1; }
    static final Map<ServerPlayer, S> MAP = new HashMap<>();
    static MobEffect INVIS;

    static String type(Entity e) {
        ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(e.m_6095_());
        return k == null ? "" : k.m_135815_();
    }
    static String name(ServerPlayer p) { return p.m_36316_().getName(); }

    static void use(ServerLevel level, ServerPlayer p) {
        if (INVIS == null) INVIS = Fx.effect("invisibility");
        S s = MAP.get(p);
        if (s != null) {
            if (p.m_6144_()) release(level, p, s, true);
            else ability(level, p, s);
            return;
        }
        Aim aim = Aim.of(level, p, 24.0D);
        if (!(aim.entity instanceof Mob m) || !m.m_6084_() || Cmd.hasTagPrefix(m, "ew")) {
            Fx.sound(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.fire.extinguish", 0.6F, 1.5F);
            return;
        }
        for (S o : MAP.values()) if (o.mob == m) return;
        s = new S(); s.mob = m; MAP.put(p, s);
        m.m_21557_(true);
        Cmd.run(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "team add ew_possess");
        Cmd.run(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "team modify ew_possess collisionRule never");
        Cmd.run(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "team modify ew_possess seeFriendlyInvisibles false");
        Cmd.run(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "team join ew_possess " + name(p) + " " + m.m_20149_());
        Vec3 mp = m.m_20182_(), pp = p.m_20182_();
        for (double k = 0; k < 1; k += 0.05)
            Fx.send(level, Fx.particle("soul"), pp.f_82479_ + (mp.f_82479_ - pp.f_82479_) * k, pp.f_82480_ + 1 + (mp.f_82480_ - pp.f_82480_) * k, pp.f_82481_ + (mp.f_82481_ - pp.f_82481_) * k, 1, 0, 0, 0, 0.01);
        p.m_6021_(mp.f_82479_, mp.f_82480_, mp.f_82481_);
        Fx.sound(level, mp.f_82479_, mp.f_82480_, mp.f_82481_, "entity.evoker.prepare_wololo", 1.0F, 0.6F);
        Fx.sound(level, mp.f_82479_, mp.f_82480_, mp.f_82481_, "particle.soul_escape", 2.0F, 0.8F);
        if (INVIS != null) p.m_7292_(new MobEffectInstance(INVIS, 60, 0, false, false));
    }

    static void release(ServerLevel level, ServerPlayer p, S s, boolean voluntary) {
        MAP.remove(p);
        if (INVIS != null) p.m_21195_(INVIS);
        Cmd.run(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "team leave " + name(p));
        if (s.mob != null && s.mob.m_6084_()) {
            s.mob.m_21557_(false);
            Cmd.run(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "team leave " + s.mob.m_20149_());
            Vec3 v = s.mob.m_20182_();
            Fx.send(level, Fx.particle("soul"), v.f_82479_, v.f_82480_ + 1, v.f_82481_, 20, 0.3, 0.6, 0.3, 0.05);
        }
        if (!voluntary) {
            var n = Fx.effect("nausea");
            if (n != null) p.m_7292_(new MobEffectInstance(n, 100, 0));
        }
        Fx.sound(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "particle.soul_escape", 2.0F, 1.2F);
    }

    static void tick(ServerLevel level) {
        Iterator<Map.Entry<ServerPlayer, S>> it = MAP.entrySet().iterator();
        List<Object[]> drop = new ArrayList<>();
        while (it.hasNext()) {
            var en = it.next();
            ServerPlayer p = en.getKey(); S s = en.getValue();
            if (p.m_9236_() != level) continue;
            if (!p.m_6084_() || s.mob == null || !s.mob.m_6084_() || s.mob.m_9236_() != level) { drop.add(new Object[]{p, s}); continue; }
            Vec3 pp = p.m_20182_();
            Mob m = s.mob;
            float yaw = p.m_146908_(), pitch = p.m_146909_();
            m.m_6034_(pp.f_82479_, pp.f_82480_, pp.f_82481_);
            m.m_146922_(yaw); m.m_5616_(yaw); m.f_20885_ = yaw; m.f_20883_ = yaw; m.m_146926_(pitch);
            m.m_20256_(new Vec3(0, 0, 0)); m.f_19789_ = 0;
            if (INVIS != null && level.m_46467_() % 20 == 0) p.m_7292_(new MobEffectInstance(INVIS, 60, 0, false, false));
            if (s.fuse >= 0) {
                s.fuse--;
                Fx.send(level, Fx.particle("smoke"), pp.f_82479_, pp.f_82480_ + 1.8, pp.f_82481_, 2, 0.2, 0.1, 0.2, 0.01);
                if (s.fuse == 0) {
                    level.m_255391_(p, pp.f_82479_, pp.f_82480_ + 0.5, pp.f_82481_, 4.5F, false, Level.ExplosionInteraction.valueOf("TNT"));
                    m.m_146870_();
                    drop.add(new Object[]{p, s});
                }
            }
        }
        for (Object[] o : drop) release(level, (ServerPlayer) o[0], (S) o[1], false);
    }

    static List<Entity> around(ServerLevel l, ServerPlayer p, Mob self, double r) {
        Vec3 v = p.m_20182_();
        return l.m_6249_(p, new AABB(v.f_82479_ - r, v.f_82480_ - r, v.f_82481_ - r, v.f_82479_ + r, v.f_82480_ + r, v.f_82481_ + r), e -> e != self && e.m_6084_() && e instanceof LivingEntity);
    }

    static String vec(double x, double y, double z) { return "[" + Cmd.f(x) + "d," + Cmd.f(y) + "d," + Cmd.f(z) + "d]"; }

    static void ability(ServerLevel l, ServerPlayer p, S s) {
        Mob m = s.mob;
        String t = type(m);
        Vec3 eye = p.m_146892_(), lk = p.m_20252_(1.0F), pos = p.m_20182_();
        double x = pos.f_82479_, y = pos.f_82480_, z = pos.f_82481_;
        double sx = eye.f_82479_ + lk.f_82479_ * 1.5, sy = eye.f_82480_ + lk.f_82480_ * 1.5, sz = eye.f_82481_ + lk.f_82481_ * 1.5;
        switch (t) {
            case "creeper" -> {
                if (s.fuse < 0) { s.fuse = 30; Fx.sound(l, x, y, z, "entity.creeper.primed", 2.0F, 0.8F); }
            }
            case "skeleton", "stray", "pillager", "piglin" -> {
                for (int i = -1; i <= 1; i++) {
                    double a = Math.toRadians(i * 6);
                    double dx = lk.f_82479_ * Math.cos(a) - lk.f_82481_ * Math.sin(a), dz = lk.f_82479_ * Math.sin(a) + lk.f_82481_ * Math.cos(a);
                    Cmd.run(l, sx, sy, sz, "summon arrow " + Cmd.f(sx) + " " + Cmd.f(sy) + " " + Cmd.f(sz) + " {pickup:0b,damage:4d,Motion:" + vec(dx * 3, lk.f_82480_ * 3, dz * 3) + "}");
                }
                Fx.sound(l, x, y, z, "entity.skeleton.shoot", 1.5F, 0.9F);
            }
            case "blaze" -> {
                for (int i = 0; i < 3; i++) {
                    final int k = i;
                    Later.run(l, k * 5 + 1, () -> {
                        Vec3 e2 = p.m_146892_(), l2 = p.m_20252_(1.0F);
                        double ax = e2.f_82479_ + l2.f_82479_ * 1.5, ay = e2.f_82480_ + l2.f_82480_ * 1.5, az = e2.f_82481_ + l2.f_82481_ * 1.5;
                        Cmd.run(l, ax, ay, az, "summon small_fireball " + Cmd.f(ax) + " " + Cmd.f(ay) + " " + Cmd.f(az) + " {power:" + vec(l2.f_82479_ * 0.1, l2.f_82480_ * 0.1, l2.f_82481_ * 0.1) + ",Motion:" + vec(l2.f_82479_, l2.f_82480_, l2.f_82481_) + "}");
                        Fx.sound(l, ax, ay, az, "entity.blaze.shoot", 1.5F, 1.0F);
                    });
                }
            }
            case "ghast" -> {
                Cmd.run(l, sx, sy, sz, "summon fireball " + Cmd.f(sx + lk.f_82479_ * 2) + " " + Cmd.f(sy + lk.f_82480_ * 2) + " " + Cmd.f(sz + lk.f_82481_ * 2) + " {ExplosionPower:3b,power:" + vec(lk.f_82479_ * 0.1, lk.f_82480_ * 0.1, lk.f_82481_ * 0.1) + ",Motion:" + vec(lk.f_82479_, lk.f_82480_, lk.f_82481_) + "}");
                Fx.sound(l, x, y, z, "entity.ghast.shoot", 3.0F, 0.8F);
            }
            case "enderman" -> {
                Aim a = Aim.of(l, p, 64.0D);
                if (a.hit) {
                    Fx.send(l, Fx.particle("portal"), x, y + 1, z, 60, 0.3, 1, 0.3, 0.5);
                    p.m_6021_(a.target.f_82479_, a.target.f_82480_ + 0.1, a.target.f_82481_);
                    Fx.sound(l, a.target.f_82479_, a.target.f_82480_, a.target.f_82481_, "entity.enderman.teleport", 2.0F, 1.0F);
                }
            }
            case "spider", "cave_spider" -> {
                p.m_20256_(new Vec3(lk.f_82479_ * 1.6, 0.6 + Math.max(0, lk.f_82480_), lk.f_82481_ * 1.6)); p.f_19864_ = true;
                Fx.sound(l, x, y, z, "entity.spider.ambient", 2.0F, 0.7F);
            }
            case "iron_golem", "ravager" -> {
                for (Entity e : around(l, p, m, 7)) { e.m_20256_(e.m_20184_().m_82520_(0, 1.3, 0)); e.f_19864_ = true; e.m_6469_(l.m_269111_().m_269425_(), 10.0F); }
                Fx.send(l, Fx.particle("explosion"), x, y, z, 8, 3, 0.2, 3, 0);
                Fx.sound(l, x, y, z, t.equals("ravager") ? "entity.ravager.roar" : "entity.iron_golem.attack", 3.0F, 0.6F);
            }
            case "warden" -> {
                for (double d = 1; d < 20; d += 1.5) Fx.send(l, Fx.particle("sonic_boom"), eye.f_82479_ + lk.f_82479_ * d, eye.f_82480_ + lk.f_82480_ * d, eye.f_82481_ + lk.f_82481_ * d, 1, 0, 0, 0, 0);
                for (Entity e : around(l, p, m, 20)) {
                    Vec3 ev = e.m_20182_();
                    double rx = ev.f_82479_ - eye.f_82479_, ry = ev.f_82480_ + 1 - eye.f_82480_, rz = ev.f_82481_ - eye.f_82481_;
                    double al = rx * lk.f_82479_ + ry * lk.f_82480_ + rz * lk.f_82481_;
                    if (al < 0) continue;
                    double px = rx - lk.f_82479_ * al, py = ry - lk.f_82480_ * al, pz = rz - lk.f_82481_ * al;
                    if (px * px + py * py + pz * pz < 2.5) { e.m_6469_(l.m_269111_().m_269425_(), 12.0F); e.m_20256_(new Vec3(lk.f_82479_ * 2, 0.5, lk.f_82481_ * 2)); e.f_19864_ = true; }
                }
                Fx.sound(l, x, y, z, "entity.warden.sonic_boom", 3.0F, 1.0F);
            }
            case "evoker" -> {
                double yr = Math.toRadians(p.m_146908_());
                double fx = -Math.sin(yr), fz = Math.cos(yr);
                for (int i = 1; i <= 10; i++) {
                    double ax = x + fx * i * 1.3, az = z + fz * i * 1.3;
                    final int k = i;
                    Later.run(l, k * 2, () -> Cmd.run(l, ax, y, az, "summon evoker_fangs " + Cmd.f(ax) + " " + Cmd.f(y) + " " + Cmd.f(az)));
                }
                Fx.sound(l, x, y, z, "entity.evoker.cast_spell", 2.0F, 1.0F);
            }
            case "chicken" -> {
                Cmd.run(l, x, y, z, "summon item " + Cmd.f(x) + " " + Cmd.f(y + 0.5) + " " + Cmd.f(z) + " {Item:{id:\"minecraft:egg\",Count:1b}}");
                var sf = Fx.effect("slow_falling");
                if (sf != null) p.m_7292_(new MobEffectInstance(sf, 100, 0));
                p.m_20256_(new Vec3(0, 0.6, 0)); p.f_19864_ = true;
                Fx.sound(l, x, y, z, "entity.chicken.egg", 2.0F, 1.0F);
            }
            case "pig" -> {
                p.m_20256_(new Vec3(lk.f_82479_ * 0.6, 1.6, lk.f_82481_ * 0.6)); p.f_19864_ = true;
                Fx.send(l, Fx.particle("cloud"), x, y, z, 20, 0.3, 0.1, 0.3, 0.05);
                Fx.sound(l, x, y, z, "entity.pig.ambient", 3.0F, 0.6F);
                Fx.sound(l, x, y, z, "entity.firework_rocket.launch", 2.0F, 0.8F);
            }
            case "cow", "mooshroom", "goat" -> {
                for (Entity e : around(l, p, m, 8)) {
                    Vec3 ev = e.m_20182_(); double dx = ev.f_82479_ - x, dz = ev.f_82481_ - z, d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
                    if ((dx * lk.f_82479_ + dz * lk.f_82481_) / d < 0.3) continue;
                    e.m_20256_(new Vec3(dx / d * 2.0, 0.7, dz / d * 2.0)); e.f_19864_ = true;
                }
                for (int i = 0; i < 3; i++) Fx.sound(l, x, y, z, t.equals("goat") ? "entity.goat.screaming.ambient" : "entity.cow.ambient", 4.0F, 0.4F + i * 0.1F);
                for (double d = 1; d < 8; d += 0.7) Fx.send(l, Fx.particle("cloud"), x + lk.f_82479_ * d, y + 1, z + lk.f_82481_ * d, 3, d * 0.08, d * 0.08, d * 0.08, 0.02);
            }
            case "sheep" -> {
                Cmd.run(l, x, y, z, "data merge entity " + m.m_20149_() + " {Color:" + Fx.RNG.nextInt(16) + "b}");
                Fx.send(l, Fx.particle("happy_villager"), x, y + 1, z, 15, 0.5, 0.5, 0.5, 0);
                Fx.sound(l, x, y, z, "entity.sheep.ambient", 2.0F, 1.0F);
            }
            case "slime", "magma_cube" -> {
                p.m_20256_(new Vec3(lk.f_82479_ * 0.8, 1.3, lk.f_82481_ * 0.8)); p.f_19864_ = true;
                Fx.sound(l, x, y, z, "entity.slime.jump", 2.0F, 0.8F);
            }
            default -> {
                for (Entity e : around(l, p, m, 4)) {
                    Vec3 ev = e.m_20182_(); double dx = ev.f_82479_ - x, dz = ev.f_82481_ - z, d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
                    if ((dx * lk.f_82479_ + dz * lk.f_82481_) / d < 0.4) continue;
                    e.m_6469_(l.m_269111_().m_269425_(), 8.0F);
                    e.m_20256_(new Vec3(dx / d * 0.9, 0.4, dz / d * 0.9)); e.f_19864_ = true;
                }
                Fx.send(l, Fx.particle("sweep_attack"), sx, sy - 0.4, sz, 1, 0, 0, 0, 0);
                Fx.sound(l, x, y, z, "entity.player.attack.sweep", 1.5F, 0.8F);
                Fx.sound(l, x, y, z, "entity.zombie.ambient", 1.5F, 0.8F);
            }
        }
    }
}
