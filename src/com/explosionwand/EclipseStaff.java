package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/**
 * Посох Затмения — сверх-палка. Shift+ПКМ — сменить режим, ПКМ — применить.
 *  1. Солнечное копьё: столп солнца с неба 4 секунды следует за прицелом и выжигает траншею.
 *  2. Свита лун: три луны 30 секунд кружат вокруг тебя и сами расстреливают враждебных мобов.
 *  3. Сверхновая: над целью зажигается чёрное солнце, стягивает всё к себе — и взрывается, выжигая сферу.
 */
final class EclipseStaff {
    static final Map<UUID, Integer> MODE = new HashMap<>();
    static final Map<UUID, long[]> CD = new HashMap<>();
    static final String[] NAMES = {"Солнечное копьё", "Свита лун", "Сверхновая"};
    static final int[] COOLDOWN = {160, 600, 900};
    static final List<Object> ACTIVE = new ArrayList<>();
    static long now = 0;
    static DustParticleOptions GOLD, WHITE, BLACK, ORANGE;

    static void init() {
        if (GOLD != null) return;
        GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.8F, 0.25F), 2.0F);
        WHITE = new DustParticleOptions(new Vector3f(1, 1, 0.95F), 1.4F);
        BLACK = new DustParticleOptions(new Vector3f(0.02F, 0.02F, 0.02F), 3.0F);
        ORANGE = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.05F), 2.0F);
    }

    static void bar(ServerLevel l, ServerPlayer p, String text, String color) {
        Cmd.run(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "title " + p.m_36316_().getName() + " actionbar {\"text\":\"" + text + "\",\"color\":\"" + color + "\"}");
    }

    static void use(ServerLevel l, ServerPlayer p) {
        init();
        now = time(l);
        UUID id = p.m_20148_();
        int mode = MODE.getOrDefault(id, 0);
        if (p.m_6144_()) {
            mode = (mode + 1) % 3; MODE.put(id, mode);
            bar(l, p, "Режим: " + NAMES[mode], "gold");
            Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.beacon.power_select", 0.8F, 1.2F + mode * 0.2F);
            return;
        }
        long[] cd = CD.computeIfAbsent(id, k -> new long[3]);
        if (now < cd[mode]) { bar(l, p, NAMES[mode] + " — перезарядка " + ((cd[mode] - now) / 20 + 1) + " сек", "red"); return; }
        cd[mode] = now + (long) (COOLDOWN[mode] * ArcaneSet.cd(p));
        switch (mode) {
            case 0 -> ACTIVE.add(new Spear(l, p));
            case 1 -> ACTIVE.add(new Moons(l, p));
            default -> { Aim a = Aim.of(l, p, 120.0D); ACTIVE.add(new Nova(l, p, a.target.m_82520_(0, 6, 0))); }
        }
        bar(l, p, NAMES[mode] + "!", "yellow");
    }

    static long time(ServerLevel l) { return l.m_7654_().m_129880_(net.minecraft.world.level.Level.f_46428_).m_46467_(); }

    static void tick(ServerLevel l) {
        now = time(l);
        Iterator<Object> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Object o = it.next();
            boolean done;
            if (o instanceof Spear s) { if (s.level != l) continue; done = s.tick(); }
            else if (o instanceof Moons m) { if (m.level != l) continue; done = m.tick(); }
            else if (o instanceof Nova n) { if (n.level != l) continue; done = n.tick(); }
            else done = true;
            if (done) it.remove();
        }
    }

    // ===== 1. Солнечное копьё =====
    static final class Spear {
        final ServerLevel level; final ServerPlayer owner; double hx, hy, hz; int t = 0;
        final Map<Entity, Integer> hit = new WeakHashMap<>();
        Spear(ServerLevel l, ServerPlayer p) {
            level = l; owner = p; Aim a = Aim.of(l, p, 100); hx = a.target.f_82479_; hy = a.target.f_82480_; hz = a.target.f_82481_;
            Fx.sound(l, hx, hy + 40, hz, "block.beacon.activate", 10.0F, 0.6F);
            Fx.sound(l, hx, hy, hz, "item.totem.use", 4.0F, 1.4F);
        }
        boolean tick() {
            t++;
            if (!owner.m_6084_() || owner.m_9236_() != level) return true;
            Aim a = Aim.of(level, owner, 100);
            hx += (a.target.f_82479_ - hx) * 0.25; hy += (a.target.f_82480_ - hy) * 0.25; hz += (a.target.f_82481_ - hz) * 0.25;
            double w = t < 8 ? t / 8.0 : (t > 72 ? (80 - t) / 8.0 : 1.0);
            for (double h = 0; h < 70; h += 1.2) {
                Fx.send(level, GOLD, hx, hy + h, hz, 2, 0.5 * w, 0.4, 0.5 * w, 0);
                if (((int) h) % 4 == 0) Fx.send(level, Fx.particle("end_rod"), hx, hy + h, hz, 1, 0.3 * w, 0.4, 0.3 * w, 0);
            }
            Fx.send(level, Fx.particle("flame"), hx, hy + 0.5, hz, 15, 1.2, 0.3, 1.2, 0.05);
            Fx.send(level, Fx.particle("lava"), hx, hy + 0.3, hz, 2, 0.8, 0.1, 0.8, 0);
            Fx.send(level, WHITE, hx, hy + 0.3, hz, 10, 1.6, 0.1, 1.6, 0);
            if (t % 2 == 0) burn();
            if (t % 5 == 0) for (Entity e : level.m_6249_(owner, new AABB(hx - 3, hy - 3, hz - 3, hx + 3, hy + 40, hz + 3), x -> x instanceof LivingEntity && x.m_6084_() && !Cmd.hasTagPrefix(x, "ew"))) {      // ew*-сущности (боссы, мобы данжей) столп не бьёт, как и Луны
                Vec3 v = e.m_20182_(); double dx = v.f_82479_ - hx, dz = v.f_82481_ - hz;
                if (dx * dx + dz * dz > 2.8 * 2.8) continue;
                e.m_6469_(level.m_269111_().m_269425_(), 6.0F); e.m_20254_(5);
            }
            if (t % 10 == 0) Fx.sound(level, hx, hy, hz, "block.fire.ambient", 4.0F, 0.6F);
            if (t % 16 == 0) Fx.sound(level, hx, hy + 10, hz, "block.beacon.ambient", 6.0F, 1.6F);
            return t >= 80;
        }
        void burn() {
            BlockState air = Fx.state("air"), fire = Fx.state("fire");
            int cx = (int) Math.floor(hx), cy = (int) Math.floor(hy), cz = (int) Math.floor(hz);
            Dungeons.Shield shield = Dungeons.shield(level);
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 1; y++) for (int z = -2; z <= 2; z++) {
                if (x * x + y * y + z * z > 4.5) continue;
                BlockPos p = new BlockPos(cx + x, cy + y, cz + z);
                if (shield.at(p)) continue;
                BlockState s = level.m_8055_(p);
                if (s.m_60795_() || s.m_155947_()) continue;
                float h = s.m_60800_(level, p);
                if (h < 0 || h >= 50) continue;
                level.m_7731_(p, air, 3);
            }
            if (fire != null && Fx.RNG.nextInt(3) == 0) {
                int fx = cx + Fx.RNG.nextInt(7) - 3, fz = cz + Fx.RNG.nextInt(7) - 3;
                for (int y = cy + 2; y > cy - 4; y--) {
                    BlockPos p = new BlockPos(fx, y, fz);
                    if (shield.at(p)) break;
                    if (level.m_8055_(p).m_60795_() && !level.m_8055_(new BlockPos(fx, y - 1, fz)).m_60795_()) { if (fire.m_60710_(level, p)) level.m_7731_(p, fire, 3); break; }
                }
            }
        }
    }

    // ===== 2. Свита лун =====
    static final class Moons {
        final ServerLevel level; final ServerPlayer owner; int t = 0;
        Moons(ServerLevel l, ServerPlayer p) { level = l; owner = p; Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.amethyst_block.resonate", 2.0F, 0.6F); }
        boolean tick() {
            t++;
            if (!owner.m_6084_() || owner.m_9236_() != level) return true;
            Vec3 p = owner.m_20182_();
            for (int k = 0; k < 3; k++) {
                double a = k * Math.PI * 2 / 3 + t * 0.08;
                double mx = p.f_82479_ + Math.cos(a) * 2.6, my = p.f_82480_ + 2.0 + Math.sin(t * 0.1 + k) * 0.3, mz = p.f_82481_ + Math.sin(a) * 2.6;
                Fx.send(level, WHITE, mx, my, mz, 4, 0.12, 0.12, 0.12, 0);
                Fx.send(level, GOLD, mx, my, mz, 1, 0.2, 0.2, 0.2, 0);
                if ((t + k * 4) % 12 == 0) shoot(mx, my, mz);
            }
            if (t % 40 == 0) Fx.sound(level, p.f_82479_, p.f_82480_, p.f_82481_, "block.amethyst_block.chime", 1.0F, 1.2F);
            return t >= 600;
        }
        void shoot(double mx, double my, double mz) {
            Entity best = null; double bd = 400;
            for (Entity e : level.m_6249_(owner, new AABB(mx - 20, my - 12, mz - 20, mx + 20, my + 12, mz + 20), x -> x instanceof Enemy && x.m_6084_() && !Cmd.hasTagPrefix(x, "ew"))) {
                Vec3 v = e.m_20182_(); double d = (v.f_82479_ - mx) * (v.f_82479_ - mx) + (v.f_82480_ - my) * (v.f_82480_ - my) + (v.f_82481_ - mz) * (v.f_82481_ - mz);
                if (d < bd) { bd = d; best = e; }
            }
            if (best == null) return;
            Vec3 v = best.m_20182_();
            double tx = v.f_82479_, ty = v.f_82480_ + best.m_20206_() / 2, tz = v.f_82481_;
            double dx = tx - mx, dy = ty - my, dz = tz - mz, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            for (double s = 0; s < l; s += 0.4) Fx.send(level, WHITE, mx + dx * s / l, my + dy * s / l, mz + dz * s / l, 1, 0, 0, 0, 0);
            best.m_6469_(level.m_269111_().m_269425_(), 6.0F);
            best.m_20256_(best.m_20184_().m_82520_(dx / l * 0.4, 0.2, dz / l * 0.4)); best.f_19864_ = true;
            Fx.send(level, Fx.particle("end_rod"), tx, ty, tz, 8, 0.2, 0.2, 0.2, 0.1);
            Fx.sound(level, tx, ty, tz, "entity.guardian.attack", 0.8F, 1.8F);
        }
    }

    // ===== 3. Сверхновая =====
    static final class Nova {
        final ServerLevel level; final ServerPlayer owner; final double x, y, z; int t = 0, carve = 0;
        static final int R = 16;
        Nova(ServerLevel l, ServerPlayer p, Vec3 at) {
            level = l; owner = p; x = at.f_82479_; y = at.f_82480_; z = at.f_82481_;
            Fx.sound(l, x, y, z, "entity.wither.spawn", 10.0F, 1.6F);
            Fx.sound(l, x, y, z, "block.end_portal.spawn", 10.0F, 0.5F);
        }
        boolean tick() {
            t++;
            if (t <= 70) {
                double r = Math.min(3.2, t * 0.08);
                for (int i = 0; i < 40; i++) { double[] u = Fx.unit(); double d = r * Math.cbrt(Fx.RNG.nextDouble()); Fx.send(level, BLACK, x + u[0] * d, y + u[1] * d, z + u[2] * d, 1, 0, 0, 0, 0); }
                for (int i = 0; i < 30; i++) { double a = i * Math.PI * 2 / 30 + t * 0.1; Fx.send(level, i % 2 == 0 ? GOLD : ORANGE, x + Math.cos(a) * (r + 1), y + Math.sin(a * 3) * 0.4, z + Math.sin(a) * (r + 1), 1, 0, 0, 0, 0); }
                for (int i = 0; i < 10; i++) { double[] u = Fx.unit(); Fx.send(level, Fx.particle("end_rod"), x + u[0] * 14, y + u[1] * 14, z + u[2] * 14, 0, -u[0], -u[1], -u[2], 0.9); }
                for (Entity e : level.m_6249_(owner, new AABB(x - 26, y - 26, z - 26, x + 26, y + 26, z + 26), q -> q.m_6084_() && !Cmd.hasTagPrefix(q, "ew"))) {
                    Vec3 v = e.m_20182_(); double dx = x - v.f_82479_, dy = y - v.f_82480_, dz = z - v.f_82481_, d = Math.max(1, Math.sqrt(dx * dx + dy * dy + dz * dz));
                    if (d > 26) continue;
                    double a = 0.09 * (1 - d / 30);
                    e.m_20256_(e.m_20184_().m_82520_(dx / d * a, dy / d * a, dz / d * a)); e.f_19864_ = true;
                }
                if (t % 6 == 0) Debris.fling(level, x, y - 6, z, 6, 12, 10, -0.6, 0.9);
                if (t % 10 == 0) Fx.sound(level, x, y, z, "block.note_block.pling", 10.0F, 0.5F + t / 90F);
                if (t == 70) { Fx.send(level, Fx.particle("flash"), x, y, z, 3, 1, 1, 1, 0); Fx.sound(level, x, y, z, "block.beacon.deactivate", 15.0F, 0.5F); }
                return false;
            }
            if (t == 76) blast();
            if (t > 76) return carveStep();
            return false;
        }
        void blast() {
            Fx.send(level, Fx.particle("flash"), x, y, z, 6, 3, 3, 3, 0);
            Fx.send(level, Fx.particle("explosion_emitter"), x, y, z, 8, 5, 5, 5, 0);
            for (int i = 0; i < 300; i++) { double[] u = Fx.unit(); Fx.send(level, i % 2 == 0 ? Fx.particle("end_rod") : Fx.particle("flame"), x, y, z, 0, u[0], u[1], u[2], 2.0); }
            for (int s = 0; s < 12; s++) {
                final double r = 3 + s * 3.5;
                Later.run(level, s + 1, () -> { for (int i = 0; i < 150; i++) { double[] u = Fx.unit(); Fx.send(level, i % 3 == 0 ? WHITE : (i % 3 == 1 ? GOLD : ORANGE), x + u[0] * r, y + u[1] * r, z + u[2] * r, 1, 0, 0, 0, 0); } });
            }
            Fx.sound(level, x, y, z, "entity.generic.explode", 40.0F, 0.4F);
            Fx.sound(level, x, y, z, "entity.lightning_bolt.thunder", 40.0F, 0.5F);
            Fx.sound(level, x, y, z, "entity.warden.sonic_boom", 30.0F, 0.5F);
            for (Entity e : level.m_6249_(owner, new AABB(x - 40, y - 40, z - 40, x + 40, y + 40, z + 40), q -> q.m_6084_() && !Cmd.hasTagPrefix(q, "ew"))) {
                Vec3 v = e.m_20182_(); double dx = v.f_82479_ - x, dy = v.f_82480_ - y, dz = v.f_82481_ - z, d = Math.max(0.5, Math.sqrt(dx * dx + dy * dy + dz * dz));
                if (d > 40) continue;
                double k = 1 - d / 40;
                if (e instanceof LivingEntity) { e.m_6469_(level.m_269111_().m_269425_(), (float) (4 + 50 * k * k)); e.m_20254_(8); }
                e.m_20256_(new Vec3(dx / d * 3 * k, 0.6 + 1.5 * k, dz / d * 3 * k)); e.f_19864_ = true;
            }
        }
        boolean carveStep() {
            long[] offs = Fx.sphere(R);
            BlockState air = Fx.state("air");
            int end = Math.min(offs.length, carve + 9000);
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            Dungeons.Shield shield = Dungeons.shield(level);
            for (; carve < end; carve++) {
                long v = offs[carve];
                BlockPos p = new BlockPos(bx + Fx.sx(v, R), by + Fx.sy(v, R), bz + Fx.sz(v, R));
                if (!level.m_46805_(p)) continue;
                BlockState s = level.m_8055_(p);
                if (s.m_60795_() || s.m_60800_(level, p) < 0 || shield.at(p)) continue;
                level.m_7731_(p, air, 18);
            }
            return carve >= offs.length;
        }
    }
}
