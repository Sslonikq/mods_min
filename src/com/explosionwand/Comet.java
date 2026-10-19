package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * Палочка Кометы: ты сам становишься кометой. Подлетаешь вверх, зависаешь — и несёшься туда, куда смотришь,
 * в огне и звёздной пыли, сбивая всех на пути. Врезаешься — кратер, разлетающиеся камни, огненное кольцо.
 * Пока летишь, ты неуязвим и не получаешь урона от падения.
 */
public class Comet {
    static final List<Comet> ACTIVE = new ArrayList<>();
    static DustParticleOptions BLUE, WHITE, ORANGE;
    final ServerLevel level; final ServerPlayer p; int t = 0; boolean done = false;
    final Set<Entity> hit = Collections.newSetFromMap(new IdentityHashMap<>());

    Comet(ServerLevel level, ServerPlayer p) {
        this.level = level; this.p = p;
        if (BLUE == null) {
            BLUE = new DustParticleOptions(new Vector3f(0.4F, 0.7F, 1.0F), 2.2F);
            WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 1.6F);
            ORANGE = new DustParticleOptions(new Vector3f(1.0F, 0.5F, 0.1F), 2.2F);
        }
        p.m_20256_(new Vec3(0, 1.4, 0)); p.f_19864_ = true;
        Fx.sound(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.firework_rocket.launch", 3.0F, 0.5F);
        Fx.sound(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.beacon.activate", 2.0F, 1.6F);
    }

    static boolean flying(Entity e) { for (Comet c : ACTIVE) if (c.p == e && !c.done) return true; return false; }

    boolean tick() {
        t++;
        if (!p.m_6084_() || p.m_9236_() != level) return true;
        Vec3 pos = p.m_20182_();
        double x = pos.f_82479_, y = pos.f_82480_ + 1, z = pos.f_82481_;
        p.f_19789_ = 0;
        if (t < 12) {                                     // взлёт и зависание
            if (t > 7) { p.m_20256_(new Vec3(0, 0.02, 0)); p.f_19864_ = true; }
            Fx.send(level, BLUE, x, y, z, 12, 0.6, 0.6, 0.6, 0);
            Fx.send(level, Fx.particle("end_rod"), x, y, z, 6, 0.5, 0.5, 0.5, 0.05);
            if (t == 11) { Fx.sound(level, x, y, z, "item.trident.riptide_3", 3.0F, 0.6F); Fx.send(level, Fx.particle("flash"), x, y, z, 1, 0, 0, 0, 0); }
            return false;
        }
        Vec3 lk = p.m_20252_(1.0F);
        double sp = Math.min(3.2, 1.6 + t * 0.05);
        p.m_20256_(new Vec3(lk.f_82479_ * sp, Math.min(lk.f_82480_ * sp, 0.3), lk.f_82481_ * sp)); p.f_19864_ = true;
        // хвост кометы
        for (int i = 0; i < 6; i++) {
            double k = i * 0.7;
            Fx.send(level, i < 2 ? WHITE : (i < 4 ? BLUE : ORANGE), x - lk.f_82479_ * k, y - lk.f_82480_ * k, z - lk.f_82481_ * k, 6, 0.4 + k * 0.1, 0.4 + k * 0.1, 0.4 + k * 0.1, 0);
        }
        Fx.send(level, Fx.particle("flame"), x, y, z, 10, 0.5, 0.5, 0.5, 0.05);
        Fx.send(level, Fx.particle("firework"), x, y, z, 4, 0.3, 0.3, 0.3, 0.05);
        if (t % 5 == 0) Fx.sound(level, x, y, z, "entity.blaze.burn", 2.0F, 0.5F);
        // сбивает всех на пути
        for (Entity e : level.m_6249_(p, new AABB(x - 2.5, y - 2.5, z - 2.5, x + 2.5, y + 2.5, z + 2.5), q -> q instanceof LivingEntity && q.m_6084_() && !Cmd.hasTagPrefix(q, "ew"))) {
            if (!hit.add(e)) continue;
            e.m_6469_(level.m_269111_().m_269425_(), 12.0F); e.m_20254_(4);
            e.m_20256_(new Vec3(lk.f_82479_ * 2, 0.8, lk.f_82481_ * 2)); e.f_19864_ = true;
            Fx.sound(level, x, y, z, "entity.player.attack.knockback", 2.0F, 0.6F);
        }
        // столкновение
        boolean blocked = false;
        for (double s = 0.5; s < 2.2 && !blocked; s += 0.5) {
            BlockPos b = new BlockPos((int) Math.floor(x + lk.f_82479_ * s), (int) Math.floor(pos.f_82480_ + lk.f_82480_ * s - 0.3), (int) Math.floor(z + lk.f_82481_ * s));
            if (!level.m_8055_(b).m_60795_()) blocked = true;
        }
        if (blocked || t > 70) { impact(x, pos.f_82480_, z); return true; }
        return false;
    }

    void impact(double x, double y, double z) {
        done = true;
        p.m_20256_(new Vec3(0, 0.3, 0)); p.f_19864_ = true; p.f_19789_ = 0;
        level.m_255391_(p, x, y, z, 6.0F, false, Level.ExplosionInteraction.valueOf("TNT"));
        Debris.fling(level, x, y, z, 2.5, 8.0, 110, 1.1, 0.8);
        for (Entity e : level.m_6249_(p, new AABB(x - 10, y - 4, z - 10, x + 10, y + 8, z + 10), q -> q.m_6084_() && !Cmd.hasTagPrefix(q, "ew"))) {
            Vec3 v = e.m_20182_(); double dx = v.f_82479_ - x, dz = v.f_82481_ - z, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            e.m_20256_(new Vec3(dx / d * 1.5, 0.9, dz / d * 1.5)); e.f_19864_ = true;
        }
        Fx.send(level, Fx.particle("flash"), x, y + 1, z, 2, 0.5, 0.5, 0.5, 0);
        Fx.send(level, Fx.particle("explosion_emitter"), x, y + 1, z, 2, 1, 0.3, 1, 0);
        for (int s = 0; s < 14; s++) {
            final double r = 1.5 + s * 0.9;
            Later.run(level, s + 1, () -> {
                int n = (int) (20 + r * 6);
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    Fx.send(level, Fx.particle("flame"), x + Math.cos(a) * r, y + 0.3, z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.01);
                    if (i % 3 == 0) Fx.send(level, BLUE, x + Math.cos(a) * r, y + 0.5, z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            });
        }
        for (int i = 0; i < 60; i++) { double[] u = Fx.unit(); Fx.send(level, Fx.particle("end_rod"), x, y + 1, z, 0, u[0], Math.abs(u[1]), u[2], 1.0); }
        Fx.sound(level, x, y, z, "entity.generic.explode", 10.0F, 0.6F);
        Fx.sound(level, x, y, z, "entity.lightning_bolt.thunder", 8.0F, 1.2F);
        Fx.sound(level, x, y, z, "block.anvil.land", 6.0F, 0.6F);
    }

    /** Пока летишь — неуязвим. */
    static void onHurt(LivingHurtEvent ev) {
        if (flying(ev.getEntity())) ev.setCanceled(true);
        else for (Comet c : ACTIVE) if (c.p == ev.getEntity() && c.done) ev.setCanceled(true);
    }
}
