package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/**
 * Посох Сна — добыча Монолита Сна.
 *  ПКМ        — «Кольцо сегментов»: 10 сек вокруг тебя крутятся 4 сегмента Монолита; бьют и отшвыривают
 *               мобов, каждый гасит один снаряд и рассыпается.
 *  Shift+ПКМ  — «Взгляд Сна»: луч на 24 блока, все задетые мобы засыпают на 3 сек (боссы — только замедляются).
 *  Ночью (по времени Верхнего мира) способности сильнее на 10%.
 */
final class DreamStaff {
    static final Map<UUID, long[]> CD = new HashMap<>();
    static final int RING_CD = 400, GAZE_CD = 300, RING_LIFE = 200;
    static final List<Ring> ACTIVE = new ArrayList<>();
    static DustParticleOptions TEAL, DEEP;
    static int seq = 0;

    static void init() {
        if (TEAL != null) return;
        TEAL = new DustParticleOptions(new Vector3f(0.25F, 0.95F, 0.9F), 1.3F);
        DEEP = new DustParticleOptions(new Vector3f(0.05F, 0.45F, 0.6F), 1.1F);
    }

    static boolean night(ServerLevel l) {
        ServerLevel ow = l.m_7654_().m_129880_(Level.f_46428_);
        return ow != null && ow.m_46462_();
    }
    static float power(ServerLevel l) { return night(l) ? 1.1F : 1.0F; }

    static void use(ServerLevel l, ServerPlayer p) {
        init();
        long now = EclipseStaff.time(l);
        long[] cd = CD.computeIfAbsent(p.m_20148_(), k -> new long[2]);
        int mode = p.m_6144_() ? 1 : 0;
        String[] names = {"Кольцо сегментов", "Взгляд Сна"};
        if (now < cd[mode]) { EclipseStaff.bar(l, p, names[mode] + " — перезарядка " + ((cd[mode] - now) / 20 + 1) + " сек", "red"); return; }
        cd[mode] = now + (long) ((mode == 0 ? RING_CD : GAZE_CD) * ArcaneSet.cd(p));
        if (mode == 0) {
            for (Ring r : ACTIVE) if (r.owner == p) r.life = 0;     // старое кольцо — убрать
            ACTIVE.add(new Ring(l, p));
        } else gaze(l, p);
        EclipseStaff.bar(l, p, names[mode] + (night(l) ? "! (ночь: +10%)" : "!"), "aqua");
    }

    // ================= Взгляд Сна =================
    static void gaze(ServerLevel l, ServerPlayer p) {
        Aim aim = Aim.of(l, p, 24.0D);
        Vec3 eye = aim.eye, look = aim.look;
        double len = aim.block != null && aim.hit && aim.entity == null ? eye.m_82554_(aim.target) : 24.0;
        for (double d = 1.2; d < len; d += 0.4) {
            Vec3 v = eye.m_82549_(look.m_82490_(d));
            Fx.send(l, d % 1.2 < 0.4 ? TEAL : DEEP, v.f_82479_, v.f_82480_, v.f_82481_, 1, 0.02, 0.02, 0.02, 0);
        }
        Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.elder_guardian.curse", 0.7F, 1.6F);
        Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.beacon.power_select", 0.8F, 1.4F);
        Vec3 end = eye.m_82549_(look.m_82490_(len));
        AABB box = new AABB(Math.min(eye.f_82479_, end.f_82479_) - 1.5, Math.min(eye.f_82480_, end.f_82480_) - 1.5, Math.min(eye.f_82481_, end.f_82481_) - 1.5,
                Math.max(eye.f_82479_, end.f_82479_) + 1.5, Math.max(eye.f_82480_, end.f_82480_) + 1.5, Math.max(eye.f_82481_, end.f_82481_) + 1.5);
        int ticks = (int) (60 * power(l));
        for (Entity e : l.m_6249_(p, box, x -> x instanceof LivingEntity && x.m_6084_() && !(x instanceof Player))) {
            Vec3 c = e.m_20182_().m_82520_(0, e.m_20206_() / 2, 0);
            Vec3 r = c.m_82546_(eye);
            double al = r.m_82526_(look);
            if (al < 0 || al > len) continue;
            Vec3 q = r.m_82546_(look.m_82490_(al));
            double rad = 0.9 + e.m_20205_() / 2;
            if (q.m_82556_() < rad * rad) sleep(l, (LivingEntity) e, ticks);
        }
    }

    /** Усыпить: обычные мобы замирают, боссы и их части только замедляются. */
    static void sleep(ServerLevel l, LivingEntity e, int ticks) {
        var slow = Fx.effect("slowness"); var weak = Fx.effect("weakness");
        Vec3 v = e.m_20182_();
        Fx.send(l, TEAL == null ? null : TEAL, v.f_82479_, v.f_82480_ + e.m_20206_() + 0.3, v.f_82481_, 8, 0.3, 0.2, 0.3, 0);
        Fx.send(l, Fx.particle("cloud"), v.f_82479_, v.f_82480_ + e.m_20206_() / 2, v.f_82481_, 6, 0.3, 0.3, 0.3, 0.01);
        if (Minions.bossPart(e)) {
            if (slow != null) e.m_7292_(new MobEffectInstance(slow, ticks, 1));
            return;
        }
        if (slow != null) e.m_7292_(new MobEffectInstance(slow, ticks + 20, 5));
        if (weak != null) e.m_7292_(new MobEffectInstance(weak, ticks + 20, 2));
        if (e instanceof Mob m && !m.m_21525_() && Minions.controllable(m)) {
            m.m_21557_(true);
            Later.run(l, ticks, () -> { if (m.m_6084_()) m.m_21557_(false); });
            for (int i = 1; i < ticks; i += 20) {
                final int k = i;
                Later.run(l, k, () -> { if (m.m_6084_()) { Vec3 w = m.m_20182_(); Fx.send(l, Fx.particle("cloud"), w.f_82479_, w.f_82480_ + m.m_20206_() + 0.4, w.f_82481_, 2, 0.15, 0.1, 0.15, 0.005); } });
            }
        }
    }

    // ================= Кольцо сегментов =================
    static final class Ring {
        final ServerLevel level; final ServerPlayer owner; final String tag;
        int life = RING_LIFE, t = 0;
        final boolean[] seg = {true, true, true, true};
        final List<List<Entity>> parts = new ArrayList<>();
        final Map<Entity, Integer> hitCd = new WeakHashMap<>();

        Ring(ServerLevel level, ServerPlayer owner) {
            this.level = level; this.owner = owner; this.tag = "ewds" + (seq++);
            for (int k = 0; k < 4; k++) {
                String tk = tag + "_" + k;
                String base = "summon minecraft:block_display " + Cmd.f(owner.m_20185_()) + " " + Cmd.f(owner.m_20186_() + 1) + " " + Cmd.f(owner.m_20189_())
                        + " {Tags:[\"ewds\",\"" + tag + "\",\"" + tk + "\"],brightness:{sky:15,block:15},view_range:3f,";
                Cmd.run(level, owner.m_20185_(), owner.m_20186_(), owner.m_20189_(), base + Disp.block("black_concrete") + "," + Disp.tf(0, 0, 0, 0.62, 0.5, 0.62, Disp.ID) + "}");
                Cmd.run(level, owner.m_20185_(), owner.m_20186_(), owner.m_20189_(), base + Disp.block("diamond_block") + "," + Disp.tf(0, 0.29, 0, 0.7, 0.08, 0.7, Disp.ID) + "}");
                parts.add(new ArrayList<>());
            }
            Fx.sound(level, owner.m_20185_(), owner.m_20186_(), owner.m_20189_(), "block.respawn_anchor.charge", 0.8F, 1.3F);
        }

        boolean tick() {
            t++;
            if (t == 2) for (int k = 0; k < 4; k++) parts.get(k).addAll(Cmd.tagged(level, owner.m_20185_(), owner.m_20186_(), owner.m_20189_(), 12, tag + "_" + k));
            if (!owner.m_6084_() || owner.m_9236_() != level || --life <= 0 || (t > 2 && !any())) { remove(); return true; }
            if (t < 2) return false;
            double ox = owner.m_20185_(), oy = owner.m_20186_() + 1.0, oz = owner.m_20189_();
            float dmg = 5.0F * power(level);
            for (int k = 0; k < 4; k++) {
                if (!seg[k]) continue;
                double a = t * 0.22 + k * Math.PI / 2, r = 2.2;
                double x = ox + Math.cos(a) * r, y = oy + Math.sin(t * 0.1 + k) * 0.25, z = oz + Math.sin(a) * r;
                for (Entity e : parts.get(k)) { e.m_6034_(x, y, z); e.m_146922_((float) (t * 18 + k * 45)); }
                if (t % 3 == 0) Fx.send(level, TEAL, x, y + 0.3, z, 1, 0.1, 0.05, 0.1, 0);
                AABB box = new AABB(x - 0.9, y - 0.9, z - 0.9, x + 0.9, y + 0.9, z + 0.9);
                for (Entity e : level.m_6249_(owner, box, ee -> ee.m_6084_())) {
                    if (e instanceof Projectile pr && !pr.m_150171_(owner)) {      // гасит снаряд и рассыпается
                        e.m_146870_();
                        breakSeg(k, x, y, z);
                        break;
                    }
                    if (!(e instanceof LivingEntity le) || e instanceof Player || Cmd.hasTagPrefix(e, "ewds")) continue;
                    int last = hitCd.getOrDefault(e, -100);
                    if (t - last < 10) continue;
                    hitCd.put(e, t);
                    le.m_6469_(level.m_269111_().m_269075_(owner), dmg);
                    Vec3 v = e.m_20182_(); double dx = v.f_82479_ - ox, dz = v.f_82481_ - oz, l = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
                    if (!Minions.bossPart(e)) { e.m_20256_(new Vec3(dx / l * 0.9, 0.35, dz / l * 0.9)); e.f_19864_ = true; }
                    Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), x, y, z, 8, 0.2, 0.2, 0.2, 0.1);
                    Fx.sound(level, x, y, z, "block.deepslate.hit", 0.8F, 1.2F);
                }
            }
            if (t % 20 == 0) Fx.sound(level, ox, oy, oz, "block.beacon.ambient", 0.4F, 1.6F);
            return false;
        }

        boolean any() { for (boolean b : seg) if (b) return true; return false; }

        void breakSeg(int k, double x, double y, double z) {
            seg[k] = false;
            for (Entity e : parts.get(k)) e.m_146870_();
            Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), x, y, z, 20, 0.3, 0.3, 0.3, 0.1);
            Fx.send(level, TEAL, x, y, z, 12, 0.3, 0.3, 0.3, 0.05);
            Fx.sound(level, x, y, z, "block.glass.break", 0.8F, 1.3F);
        }

        void remove() {
            for (List<Entity> l : parts) for (Entity e : l) e.m_146870_();
            Cmd.run(level, owner.m_20185_(), owner.m_20186_(), owner.m_20189_(), "kill @e[tag=" + tag + "]");
        }
    }

    static void tick(ServerLevel l) {
        Iterator<Ring> it = ACTIVE.iterator();
        while (it.hasNext()) { Ring r = it.next(); if (r.level != l) continue; if (r.tick()) it.remove(); }
    }
}
