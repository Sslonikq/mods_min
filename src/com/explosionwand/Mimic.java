package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/**
 * Портал-мимик: красивый, золотой, зовущий... Никого не тянет — ждёт, пока зайдут сами.
 * Что будет внутри — неизвестно: укус, обмен телами, заживо погребён, выплюнут в небо или иной мир.
 */
public class Mimic {
    static final List<Mimic> ACTIVE = new ArrayList<>();
    static final Random RNG = Fx.RNG;
    static final int LIFE = 600;            // 30 сек ждёт
    static final double R = 2.6D, SWALLOW = 1.6D;
    static ParticleOptions GOLD, SOFT, RED, BLACK, TOOTH;

    final ServerLevel level; final Entity owner;
    final double cx, cy, cz, px, pz;
    int t = 0, reveal = 0, cooldown = 0;

    Mimic(ServerLevel level, Vec3 c, double lx, double lz, Entity owner) {
        this.level = level; this.owner = owner;
        this.cx = c.f_82479_; this.cy = c.f_82480_; this.cz = c.f_82481_;
        double l = Math.sqrt(lx * lx + lz * lz); if (l < 1e-4) { lx = 1; lz = 0; l = 1; }
        this.px = -lz / l; this.pz = lx / l;
        if (GOLD == null) {
            GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.3F), 1.8F);
            SOFT = new DustParticleOptions(new Vector3f(1.0F, 0.97F, 0.85F), 1.2F);
            RED = new DustParticleOptions(new Vector3f(0.75F, 0.0F, 0.0F), 2.2F);
            BLACK = new DustParticleOptions(new Vector3f(0, 0, 0), 2.6F);
            TOOTH = new DustParticleOptions(new Vector3f(0.95F, 0.93F, 0.85F), 1.0F);
        }
        Fx.sound(level, cx, cy, cz, "block.beacon.activate", 3.0F, 1.6F);
        Fx.sound(level, cx, cy, cz, "block.amethyst_block.chime", 3.0F, 1.8F);
    }

    void p(ParticleOptions o, double u, double v) { Fx.send(level, o, cx + px * u, cy + v, cz + pz * u, 1, 0, 0, 0, 0); }

    boolean tick() {
        t++;
        if (cooldown > 0) cooldown--;
        double grow = Math.min(1.0, t / 30.0);
        double r = R * grow;
        if (reveal > 0) { reveal--; renderTrue(r); } else renderFake(r);
        if (t % 50 == 3 && reveal == 0) {    // ласковые звуки, зовёт
            Fx.sound(level, cx, cy, cz, "block.amethyst_block.chime", 2.0F, 1.2F + RNG.nextFloat() * 0.6F);
            Fx.sound(level, cx, cy, cz, "entity.allay.ambient_with_item", 1.5F, 1.0F);
        }
        if (t > 20 && cooldown == 0) swallow();
        if (t >= LIFE) {
            // в конце показывает настоящее лицо
            for (int i = 0; i < 3; i++) renderTrue(R);
            Fx.sound(level, cx, cy, cz, "entity.ravager.roar", 4.0F, 0.6F);
            Fx.send(level, BLACK, cx, cy, cz, 120, 1.2, 1.2, 1.2, 0);
            return true;
        }
        return false;
    }

    void renderFake(double r) {
        double rot = t * 0.06;
        for (int i = 0; i < 30; i++) { double a = rot + i * 6.283 / 30; p(GOLD, Math.cos(a) * r, Math.sin(a) * r * 1.3); }
        for (int i = 0; i < 18; i++) { double a = RNG.nextDouble() * 6.283, rr = r * 0.9 * Math.sqrt(RNG.nextDouble()); p(SOFT, Math.cos(a) * rr, Math.sin(a) * rr * 1.3); }
        Fx.send(level, Fx.particle("end_rod"), cx, cy, cz, 2, r * 0.4, r * 0.5, r * 0.4, 0.01);
        if (t % 8 == 0) Fx.send(level, Fx.particle("happy_villager"), cx, cy - 1, cz, 4, 1.5, 1, 1.5, 0);
        if (t % 15 == 0) Fx.send(level, Fx.particle("note"), cx + px * r, cy + r, cz + pz * r, 1, 0, 0, 0, 0);
    }

    /** Настоящее лицо: пасть с зубами. */
    void renderTrue(double r) {
        for (int i = 0; i < 30; i++) { double a = i * 6.283 / 30; p(RED, Math.cos(a) * r, Math.sin(a) * r * 1.3); }
        for (int i = 0; i < 26; i++) { double a = RNG.nextDouble() * 6.283, rr = r * 0.9 * Math.sqrt(RNG.nextDouble()); p(BLACK, Math.cos(a) * rr, Math.sin(a) * rr * 1.3); }
        for (int i = 0; i < 16; i++) {             // зубы по кругу, острием внутрь
            double a = i * 6.283 / 16;
            for (double k = 0.0; k < 0.55; k += 0.18) p(TOOTH, Math.cos(a) * r * (1 - k), Math.sin(a) * r * 1.3 * (1 - k));
        }
    }

    void swallow() {
        AABB box = new AABB(cx - 3, cy - 3, cz - 3, cx + 3, cy + 3, cz + 3);
        for (Entity e : level.m_6249_(null, box, e -> e instanceof LivingEntity && e.m_6084_() && !e.m_5833_())) {
            Vec3 pp = e.m_20182_();
            double dx = cx - pp.f_82479_, dy = cy - (pp.f_82480_ + 0.9), dz = cz - pp.f_82481_;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > SWALLOW) continue;
            fate((LivingEntity) e);
            cooldown = 10;
            return;
        }
    }

    void fate(LivingEntity e) {
        boolean player = e instanceof ServerPlayer;
        int roll = RNG.nextInt(100);
        if (roll < 25) bite(e);
        else if (roll < 45 && player && swap((ServerPlayer) e)) { }
        else if (roll < 65 && player) bury((ServerPlayer) e);
        else if (roll < 80) sky(e);
        else otherworld(e);
    }

    /** Укус: пасть захлопывается. */
    void bite(LivingEntity e) {
        reveal = 40;
        Fx.sound(level, cx, cy, cz, "entity.evoker_fangs.attack", 4.0F, 0.6F);
        Fx.sound(level, cx, cy, cz, "entity.ravager.attack", 4.0F, 0.7F);
        Fx.sound(level, cx, cy, cz, "entity.ravager.roar", 3.0F, 1.0F);
        e.m_6469_(level.m_269111_().m_269425_(), 14.0F);
        Vec3 p = e.m_20182_();
        double dx = p.f_82479_ - cx, dz = p.f_82481_ - cz, l = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
        e.m_20256_(new Vec3(dx / l * 2.2, 1.4, dz / l * 2.2)); e.f_19864_ = true;
        Fx.send(level, RED, p.f_82479_, p.f_82480_ + 1, p.f_82481_, 40, 0.5, 0.8, 0.5, 0);
    }

    /** Обмен: оказываешься на месте другого игрока, а он — у портала. */
    boolean swap(ServerPlayer a) {
        List<ServerPlayer> others = new ArrayList<>(level.m_6907_());
        others.remove(a);
        others.removeIf(o -> !o.m_6084_() || o.m_5833_());
        if (others.isEmpty()) return false;
        ServerPlayer b = others.get(RNG.nextInt(others.size()));
        Vec3 pa = a.m_20182_(), pb = b.m_20182_();
        a.m_6021_(pb.f_82479_, pb.f_82480_, pb.f_82481_);
        b.m_6021_(pa.f_82479_ - px * 3, pa.f_82480_, pa.f_82481_ - pz * 3);
        var dark = Fx.effect("darkness"); var nausea = Fx.effect("nausea");
        for (ServerPlayer pl : new ServerPlayer[]{a, b}) {
            if (dark != null) pl.m_7292_(new MobEffectInstance(dark, 80, 0));
            if (nausea != null) pl.m_7292_(new MobEffectInstance(nausea, 160, 0));
            Vec3 q = pl.m_20182_();
            Fx.sound(level, q.f_82479_, q.f_82480_, q.f_82481_, "entity.illusioner.mirror_move", 2.0F, 0.5F);
        }
        Fx.sound(level, cx, cy, cz, "entity.witch.celebrate", 2.0F, 0.6F);
        return true;
    }

    /** Заживо погребён: тесная каменная коробка глубоко под землёй... и ты там не один. */
    void bury(ServerPlayer p) {
        Vec3 pos = p.m_20182_();
        int x = (int) Math.floor(pos.f_82479_), z = (int) Math.floor(pos.f_82481_);
        int y = level.m_141937_() + 7;
        BlockState wall = Fx.state("deepslate"), air = Fx.state("air");
        if (wall == null || air == null || Dungeons.protectedAt(level, new BlockPos(x, y, z))) { sky(p); return; }
        Dungeons.Shield shield = Dungeons.shield(level);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 2; dy++) {
            boolean inside = dx == 0 && dz == 0 && (dy == 0 || dy == 1);
            BlockPos wp = new BlockPos(x + dx, y + dy, z + dz);
            if (!shield.at(wp)) level.m_7731_(wp, inside ? air : wall, 3);
        }
        p.m_6021_(x + 0.5, y, z + 0.5);
        p.f_19789_ = 0;
        Fx.sound(level, cx, cy, cz, "entity.ravager.attack", 3.0F, 0.5F);
        var dark = Fx.effect("darkness");
        if (dark != null) p.m_7292_(new MobEffectInstance(dark, 300, 0));
        final double fx = x + 0.5, fy = y, fz = z + 0.5;
        Fx.sound(level, fx, fy, fz, "entity.warden.heartbeat", 1.5F, 0.8F);
        // через 2 сек — шорох за стеной, через 4 — он рядом
        Later.run(level, 40, () -> Fx.sound(level, fx + 1.5, fy, fz, "entity.warden.nearby_closer", 1.5F, 0.6F));
        for (int i = 0; i < 60; i++) {
            final int k = i;
            Later.run(level, 80 + i, () -> { if (p.m_6084_()) { Haunt.init(); Haunt.figure(level, p, fx + 1.1, fy - 0.2, fz, k > 40); } });
        }
        Later.run(level, 80, () -> Fx.sound(level, fx + 1.1, fy + 1, fz, "entity.warden.sniff", 2.0F, 0.6F));
        Later.run(level, 140, () -> Fx.sound(level, fx, fy + 1, fz, "entity.elder_guardian.curse", 1.2F, 0.6F));
    }

    /** Выплюнут в небо. Медленное падение дадут... не сразу. */
    void sky(LivingEntity e) {
        Vec3 p = e.m_20182_();
        reveal = 20;
        e.m_6021_(p.f_82479_, Math.min(level.m_151558_() - 5, p.f_82480_ + 160), p.f_82481_);
        e.f_19789_ = 0;
        Fx.sound(level, cx, cy, cz, "entity.ravager.roar", 3.0F, 1.4F);
        var sf = Fx.effect("slow_falling");
        if (sf != null && RNG.nextInt(3) != 0)   // в 2 случаях из 3 — спасёт, но в последний момент
            Later.run(level, 90, () -> { if (e.m_6084_()) e.m_7292_(new MobEffectInstance(sf, 400, 0)); });
    }

    /** Другой мир — неизвестно какой. */
    void otherworld(LivingEntity e) {
        reveal = 30;
        var k = switch (RNG.nextInt(3)) { case 0 -> Dims.VOID; case 1 -> Dims.HAUNT; default -> Dims.INVERTED; };
        Fx.sound(level, cx, cy, cz, "entity.enderman.teleport", 3.0F, 0.4F);
        Dims.send(e, level, k);
    }
}
