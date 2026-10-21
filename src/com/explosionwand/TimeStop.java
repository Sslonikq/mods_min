package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * Палочка Стоп-Кадра: «Мир, замри». От тебя расходится серая волна — и в радиусе 24 блоков время
 * останавливается: мобы, игроки, стрелы, падающие блоки зависают. Ты двигаешься свободно.
 * Всё, что ты сделаешь с застывшими, накапливается — и когда время пойдёт, обрушится разом.
 */
public class TimeStop {
    static final List<TimeStop> ACTIVE = new ArrayList<>();
    static final double R = 24;
    static final int LIFE = 120;
    static boolean GUARD = false;
    static DustParticleOptions GREY, WHITE, PALE;

    static final class F {
        Entity e; double x, y, z; Vec3 vel; boolean grav, ai; float dmg; double kx, ky, kz; int hits; float yaw, pitch;
    }

    final ServerLevel level; final ServerPlayer owner; final double cx, cy, cz;
    final Map<Entity, F> frozen = new IdentityHashMap<>();
    int t = 0; boolean ending = false;

    TimeStop(ServerLevel level, ServerPlayer owner) {
        this.level = level; this.owner = owner;
        Vec3 p = owner.m_20182_(); cx = p.f_82479_; cy = p.f_82480_ + 1; cz = p.f_82481_;
        if (GREY == null) {
            GREY = new DustParticleOptions(new Vector3f(0.55F, 0.55F, 0.6F), 2.0F);
            WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 1.6F);
            PALE = new DustParticleOptions(new Vector3f(0.85F, 0.88F, 1.0F), 0.8F);
        }
        Fx.sound(level, cx, cy, cz, "block.bell.use", 6.0F, 0.5F);
        Fx.sound(level, cx, cy, cz, "entity.illusioner.cast_spell", 6.0F, 0.5F);
        Fx.sound(level, cx, cy, cz, "block.beacon.deactivate", 6.0F, 0.5F);
    }

    static TimeStop of(ServerPlayer p) { for (TimeStop s : ACTIVE) if (s.owner == p) return s; return null; }
    static F frozenOf(Entity e) { for (TimeStop s : ACTIVE) { F f = s.frozen.get(e); if (f != null) return f; } return null; }

    boolean inside(Vec3 v) { double dx = v.f_82479_ - cx, dy = v.f_82480_ - cy, dz = v.f_82481_ - cz; return dx * dx + dy * dy + dz * dz < R * R; }

    void freeze(Entity e) {
        if (e == owner || frozen.containsKey(e) || (Cmd.hasTagPrefix(e, "ew") && !Minions.dungeonMob(e)) || !e.m_6084_()) return;   // мобы данжей застывают, боссы нет
        F f = new F(); f.e = e; Vec3 p = e.m_20182_(); f.x = p.f_82479_; f.y = p.f_82480_; f.z = p.f_82481_;
        f.vel = e.m_20184_(); f.grav = e.m_20068_(); f.yaw = e.m_146908_(); f.pitch = e.m_146909_();
        if (e instanceof Mob m) { f.ai = m.m_21525_(); m.m_21557_(true); }
        e.m_20242_(true);
        e.m_20256_(new Vec3(0, 0, 0)); e.f_19864_ = true;
        frozen.put(e, f);
        Fx.send(level, PALE, f.x, f.y + e.m_20206_() / 2, f.z, 10, e.m_20205_() / 2, e.m_20206_() / 2, e.m_20205_() / 2, 0);
    }

    boolean tick() {
        t++;
        // волна при запуске
        if (t <= 12) {
            double r = R * t / 12.0;
            for (int i = 0; i < 70; i++) { double[] u = Fx.unit(); Fx.send(level, t % 2 == 0 ? WHITE : GREY, cx + u[0] * r, cy + u[1] * r, cz + u[2] * r, 1, 0, 0, 0, 0); }
        }
        // захват всего внутри (стрелы, пущенные тобой, застывают, отлетев от тебя)
        if (t <= LIFE) {
            Vec3 op = owner.m_20182_();
            for (Entity e : level.m_6249_(owner, new AABB(cx - R, cy - R, cz - R, cx + R, cy + R, cz + R), x -> x.m_6084_())) {
                if (frozen.containsKey(e) || !inside(e.m_20182_())) continue;
                Vec3 v = e.m_20182_();
                double dx = v.f_82479_ - op.f_82479_, dy = v.f_82480_ - op.f_82480_, dz = v.f_82481_ - op.f_82481_;
                if (!(e instanceof LivingEntity) && dx * dx + dy * dy + dz * dz < 4.0) continue;
                freeze(e);
            }
        }
        // удержание
        for (F f : frozen.values()) {
            Entity e = f.e;
            if (!e.m_6084_()) continue;
            if (e instanceof ServerPlayer sp) sp.m_8999_(level, f.x, f.y, f.z, sp.m_146908_(), sp.m_146909_());
            else { e.m_6034_(f.x, f.y, f.z); e.m_146922_(f.yaw); e.m_146926_(f.pitch); }
            e.m_20256_(new Vec3(0, 0, 0)); e.f_19789_ = 0;
            if (t % 10 == 0) Fx.send(level, PALE, f.x, f.y + e.m_20206_() + 0.3, f.z, 2, 0.2, 0.05, 0.2, 0);
        }
        // атмосфера
        if (t % 2 == 0) for (int i = 0; i < 25; i++) {
            double[] u = Fx.unit(); double d = Math.cbrt(Fx.RNG.nextDouble()) * R;
            Fx.send(level, GREY, cx + u[0] * d, cy + u[1] * d, cz + u[2] * d, 1, 0, 0, 0, 0);
        }
        if (t % 4 == 0) for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40, b = t * 0.01;
            Fx.send(level, PALE, cx + Math.cos(a + b) * R, cy, cz + Math.sin(a + b) * R, 1, 0, 0, 0, 0);
        }
        if (t % 20 == 0) Fx.sound(level, cx, cy, cz, "block.note_block.hat", 4.0F, 0.5F);
        if (t % 20 == 10) Fx.sound(level, cx, cy, cz, "block.note_block.hat", 4.0F, 0.45F);
        if (t % 40 == 1) Fx.sound(level, cx, cy, cz, "entity.warden.heartbeat", 3.0F, 0.5F);
        if (t == LIFE - 20) Fx.sound(level, cx, cy, cz, "block.beacon.power_select", 6.0F, 0.6F);
        if (t >= LIFE || ending) { resume(); return true; }
        return false;
    }

    void resume() {
        Fx.sound(level, cx, cy, cz, "block.bell.resonate", 8.0F, 0.6F);
        Fx.sound(level, cx, cy, cz, "entity.warden.sonic_boom", 6.0F, 0.5F);
        Fx.sound(level, cx, cy, cz, "block.beacon.activate", 6.0F, 1.4F);
        for (int s = 0; s < 8; s++) {
            final double r = R * (1 - s / 8.0);
            Later.run(level, s + 1, () -> { for (int i = 0; i < 60; i++) { double[] u = Fx.unit(); Fx.send(level, WHITE, cx + u[0] * r, cy + u[1] * r, cz + u[2] * r, 1, 0, 0, 0, 0); } });
        }
        for (F f : frozen.values()) {
            Entity e = f.e;
            if (!e.m_6084_()) continue;
            e.m_20242_(f.grav);
            if (e instanceof Mob m) m.m_21557_(f.ai);
            Vec3 v = f.vel;
            if (f.hits > 0) {
                double kl = Math.max(1e-3, Math.sqrt(f.kx * f.kx + f.kz * f.kz));
                double pow = Math.min(3.5, 0.6 + 0.35 * f.hits);
                v = v.m_82520_(f.kx / kl * pow, 0.4 + 0.12 * f.hits, f.kz / kl * pow);
                Vec3 p = e.m_20182_();
                Fx.send(level, Fx.particle("crit"), p.f_82479_, p.f_82480_ + 1, p.f_82481_, 20 + f.hits * 5, 0.4, 0.6, 0.4, 0.5);
                Fx.send(level, Fx.particle("sweep_attack"), p.f_82479_, p.f_82480_ + 1, p.f_82481_, Math.min(6, f.hits), 0.3, 0.4, 0.3, 0);
            }
            e.m_20256_(v); e.f_19864_ = true;
            if (f.dmg > 0 && e instanceof LivingEntity) {
                GUARD = true;
                try { e.m_6469_(level.m_269111_().m_269425_(), f.dmg); } finally { GUARD = false; }
            }
        }
        frozen.clear();
        try { owner.m_36335_().m_41524_(ExplosionWandMod.TIMESTOP_WAND.get(), 400); } catch (Exception ignored) {}
    }

    /** Удары по застывшим не проходят сразу — копятся. */
    static void onHurt(LivingHurtEvent ev) {
        if (GUARD) return;
        F f = frozenOf(ev.getEntity());
        if (f == null) return;
        ev.setCanceled(true);
        f.dmg += ev.getAmount(); f.hits++;
        Entity src = ev.getSource().m_7639_();
        Vec3 p = f.e.m_20182_();
        if (src != null) { Vec3 s = src.m_20182_(); f.kx += p.f_82479_ - s.f_82479_; f.kz += p.f_82481_ - s.f_82481_; }
        else { f.kx += Fx.RNG.nextGaussian(); f.kz += Fx.RNG.nextGaussian(); }
        if (f.e.m_9236_() instanceof ServerLevel sl) {
            Fx.send(sl, Fx.particle("crit"), p.f_82479_, p.f_82480_ + 1, p.f_82481_, 6, 0.3, 0.4, 0.3, 0);
            Fx.send(sl, WHITE, p.f_82479_, p.f_82480_ + 1, p.f_82481_, 4, 0.3, 0.4, 0.3, 0);
            Fx.sound(sl, p.f_82479_, p.f_82480_, p.f_82481_, "block.amethyst_block.hit", 1.0F, 1.6F);
        }
    }
}
