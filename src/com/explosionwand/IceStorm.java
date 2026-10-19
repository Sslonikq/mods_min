package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/**
 * Ледяная буря: минута белой мглы, ветра и града. Шар радиусом 64 блока
 * (вместе с подземельем) постепенно превращается в лёд, всё живое замерзает.
 * В конце всё замёрзшее раскалывается, весь лёд разлетается. Хозяина не трогает.
 */
public class IceStorm {
    static final List<IceStorm> ACTIVE = new ArrayList<>();
    static final Random RNG = Fx.RNG;

    static final int LIFE = 1200;            // 60 сек бури
    static final int FREEZE_TICKS = 1000;    // за 50 сек замерзает весь шар
    static final double STORM_R = 96.0D;     // радиус бури (метель, холод, град)
    static final int FREEZE_R = 64;          // радиус шара, превращаемого в лёд
    static final int SHATTER_PER_TICK = 12000;

    static ParticleOptions FROST, WHITE, FOG, ICE_BITS, PACKED_BITS, SNOWFLAKE, CLOUD, ASH;
    static BlockState ICE, PACKED_ICE, AIR;
    static Block ICE_BLOCK, PACKED_BLOCK, SNOW_LAYER;
    static MobEffect SLOW;

    final ServerLevel level; final Entity owner;
    final double cx, cy, cz;
    final int bx, by, bz;
    int t = 0, freezeIdx = 0, shatterIdx = 0;
    boolean shattering = false;
    final List<BlockPos> spikes = new ArrayList<>();

    IceStorm(ServerLevel level, Vec3 c, Entity owner) {
        this.level = level; this.owner = owner;
        this.cx = c.f_82479_; this.cy = c.f_82480_; this.cz = c.f_82481_;
        this.bx = (int) Math.floor(cx); this.by = (int) Math.floor(cy); this.bz = (int) Math.floor(cz);
        if (FROST == null) {
            FROST = new DustParticleOptions(new Vector3f(0.55F, 0.85F, 1.0F), 1.8F);
            WHITE = new DustParticleOptions(new Vector3f(0.95F, 0.98F, 1.0F), 1.5F);
            FOG   = new DustParticleOptions(new Vector3f(0.92F, 0.95F, 1.0F), 4.0F);
            ICE = Fx.state("ice"); PACKED_ICE = Fx.state("packed_ice"); AIR = Fx.state("air");
            ICE_BLOCK = Fx.block("ice"); PACKED_BLOCK = Fx.block("packed_ice"); SNOW_LAYER = Fx.block("snow");
            ICE_BITS = Fx.blockParticle(ICE); PACKED_BITS = Fx.blockParticle(PACKED_ICE);
            SNOWFLAKE = Fx.particle("snowflake"); CLOUD = Fx.particle("cloud"); ASH = Fx.particle("white_ash");
            SLOW = Fx.effect("slowness");
        }
        Fx.sphere(FREEZE_R); // подготовить заранее
    }

    boolean tick() {
        if (shattering) return shatterStep();
        t++;
        double grow = Math.min(1.0D, t / 60.0D);   // буря разворачивается 3 сек
        double r = STORM_R * grow;

        if (t == 1) {
            Fx.send(level, SNOWFLAKE, cx, cy + 1, cz, 300, 3, 3, 3, 0.8);
            Fx.send(level, ICE_BITS, cx, cy + 1, cz, 150, 1.5, 1.5, 1.5, 1.0);
            Fx.sound(level, cx, cy, cz, "block.glass.break", 8.0F, 0.5F);
            Fx.sound(level, cx, cy, cz, "entity.evoker.prepare_summon", 10.0F, 0.5F);
        }
        if (t % 50 == 2) Fx.sound(level, cx, cy + 4, cz, "item.elytra.flying", 12.0F, 0.55F);   // вой ветра
        if (t % 23 == 0) Fx.sound(level, cx + (RNG.nextDouble() - 0.5) * r, cy, cz + (RNG.nextDouble() - 0.5) * r,
                "block.amethyst_block.chime", 6.0F, 0.5F + RNG.nextFloat() * 0.7F);
        if (t == LIFE - 80) Fx.sound(level, cx, cy, cz, "block.beacon.power_select", 12.0F, 0.5F);

        render(r);
        blizzardAroundPlayers(r);
        freeze();
        if (t % 6 == 3 && spikes.size() < 400) spike(r);
        entities(r);

        if (t >= LIFE) { startShatter(); }
        return false;
    }

    // ---------------- Анимация ----------------
    void render(double r) {
        // огромный ледяной смерч
        for (int k = 0; k < 4; k++) for (double h = 0; h < 34; h += 2.0) {
            double a = t * 0.3 + k * (Math.PI / 2) + h * 0.35;
            double rr = 1.5 + h * 0.45;
            Fx.send(level, (k % 2 == 0) ? WHITE : FROST, cx + Math.cos(a) * rr, cy + h, cz + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            if (((int) h) % 4 == 0) Fx.send(level, SNOWFLAKE, cx + Math.cos(a) * rr, cy + h, cz + Math.sin(a) * rr, 0,
                    -Math.sin(a) * 0.8, 0.1, Math.cos(a) * 0.8, 1.0);
        }
        // граница бури
        int n = 32;
        for (int i = 0; i < n; i++) {
            double a = t * 0.03 + i * Math.PI * 2 / n;
            Fx.send(level, FROST, cx + Math.cos(a) * r, cy + 1, cz + Math.sin(a) * r, 3, 0.2, 1.5, 0.2, 0);
        }
        // тяжёлые тучи
        for (int i = 0; i < 14; i++) {
            double a = RNG.nextDouble() * Math.PI * 2, rr = r * Math.sqrt(RNG.nextDouble());
            Fx.send(level, CLOUD, cx + Math.cos(a) * rr, cy + 26 + RNG.nextDouble() * 4, cz + Math.sin(a) * rr, 2, 1.5, 0.3, 1.5, 0.01);
        }
        // фронт замерзания
        long[] offs = Fx.sphere(FREEZE_R);
        if (freezeIdx > 0 && freezeIdx < offs.length) {
            double shell = Fx.sd(offs[freezeIdx - 1]);
            for (int i = 0; i < 14; i++) {
                double[] u = Fx.unit();
                Fx.send(level, FROST, cx + u[0] * shell, cy + u[1] * shell, cz + u[2] * shell, 2, 0.5, 0.5, 0.5, 0);
            }
        }
    }

    /** Плотная белая мгла вокруг каждого игрока внутри бури — еле-еле видно. */
    void blizzardAroundPlayers(double r) {
        for (ServerPlayer p : level.m_6907_()) {
            Vec3 pos = p.m_20182_();
            double dx = pos.f_82479_ - cx, dz = pos.f_82481_ - cz;
            if (Math.sqrt(dx * dx + dz * dz) > r) continue;
            double px = pos.f_82479_, py = pos.f_82480_ + 1.6, pz = pos.f_82481_;
            double wa = t * 0.02;                                    // направление ветра медленно меняется
            double wx = Math.cos(wa) * 0.9, wz = Math.sin(wa) * 0.9;
            for (int i = 0; i < 32; i++) {                           // метель
                double ox = (RNG.nextDouble() - 0.5) * 24, oy = (RNG.nextDouble() - 0.4) * 12, oz = (RNG.nextDouble() - 0.5) * 24;
                Fx.sendTo(level, p, SNOWFLAKE, px + ox, py + oy, pz + oz, 0, wx, -0.3, wz, 1.0);
            }
            Fx.sendTo(level, p, FOG, px, py, pz, 45, 6, 2.5, 6, 0);          // белая мгла (один пакет)
            Fx.sendTo(level, p, SNOWFLAKE, px, py, pz, 40, 9, 4, 9, 0.15);
            Fx.sendTo(level, p, ASH, px, py, pz, 25, 6, 3, 6, 0.05);
            if (t % 3 == 0) Fx.sendTo(level, p, ICE_BITS, px + (RNG.nextDouble() - 0.5) * 10, py + 3, pz + (RNG.nextDouble() - 0.5) * 10,
                    0, wx * 1.5, -0.4, wz * 1.5, 1.0);
        }
    }

    // ---------------- Всё в лёд ----------------
    void freeze() {
        if (ICE == null) return;
        long[] offs = Fx.sphere(FREEZE_R);
        int target = (int) Math.min(offs.length, (long) offs.length * t / FREEZE_TICKS);
        Dungeons.Shield shield = Dungeons.shield(level);
        for (; freezeIdx < target; freezeIdx++) {
            long v = offs[freezeIdx];
            BlockPos p = new BlockPos(bx + Fx.sx(v, FREEZE_R), by + Fx.sy(v, FREEZE_R), bz + Fx.sz(v, FREEZE_R));
            if (!level.m_46805_(p)) continue;
            BlockState s = level.m_8055_(p);
            if (s.m_60795_()) continue;
            Block b = s.m_60734_();
            if (b == ICE_BLOCK || b == PACKED_BLOCK) continue;
            if (s.m_60800_(level, p) < 0.0F || shield.at(p)) continue;     // бедрок и т.п., данжи
            level.m_7731_(p, ICE, 18);
        }
    }

    void spike(double r) {
        if (PACKED_ICE == null || r < 8) return;
        double a = RNG.nextDouble() * Math.PI * 2, rr = 5 + (r - 5) * Math.sqrt(RNG.nextDouble());
        int x = (int) Math.floor(cx + Math.cos(a) * rr), z = (int) Math.floor(cz + Math.sin(a) * rr);
        int top = by + 24, bottom = by - 24, ground = Integer.MIN_VALUE;
        if (!level.m_46805_(new BlockPos(x, by, z))) return;
        Dungeons.Shield shield = Dungeons.shield(level);
        for (int y = top; y >= bottom; y--) {
            BlockState s = level.m_8055_(new BlockPos(x, y, z));
            if (s.m_60795_() || s.m_60734_() == SNOW_LAYER) continue;
            ground = y; break;
        }
        if (ground == Integer.MIN_VALUE) return;
        int h = 3 + RNG.nextInt(6);
        for (int i = 1; i <= h; i++) {
            BlockPos p = new BlockPos(x, ground + i, z);
            BlockState s = level.m_8055_(p);
            if (!s.m_60795_() && s.m_60734_() != SNOW_LAYER) break;
            if (shield.at(p)) break;
            level.m_46597_(p, PACKED_ICE);
            spikes.add(p);
        }
        Fx.send(level, PACKED_BITS, x + 0.5, ground + 1.5, z + 0.5, 30, 0.4, 1.0, 0.4, 0.3);
        Fx.sound(level, x + 0.5, ground + 1, z + 0.5, "block.glass.place", 3.0F, 0.5F);
    }

    // ---------------- Существа ----------------
    void entities(double r) {
        AABB box = new AABB(cx - r - 2, cy - 30, cz - r - 2, cx + r + 2, cy + 40, cz + r + 2);
        List<LivingEntity> victims = new ArrayList<>();
        for (Entity e : level.m_6249_(null, box, e -> e.m_6084_() && !e.m_5833_())) {
            if (e == owner) continue;
            Vec3 pos = e.m_20182_();
            double dx = pos.f_82479_ - cx, dz = pos.f_82481_ - cz;
            double hd = Math.sqrt(dx * dx + dz * dz);
            if (hd > r) continue;
            if (hd > 0.5D && t % 2 == 0) {     // ветер
                e.m_20256_(e.m_20184_().m_82520_(-dz / hd * 0.1D, 0.0D, dx / hd * 0.1D));
                e.f_19864_ = true;
            }
            if (e instanceof LivingEntity le) {
                victims.add(le);
                e.m_146917_(Math.min(e.m_146888_() + 5, e.m_146891_() + 120));
                if (t % 20 == 0 && e.m_146890_() && SLOW != null) le.m_7292_(new MobEffectInstance(SLOW, 50, 5));
                if (t % 10 == 0 && e.m_146890_())
                    Fx.send(level, ICE_BITS, pos.f_82479_, pos.f_82480_ + 1, pos.f_82481_, 6, 0.3, 0.6, 0.3, 0.05);
            }
        }
        if (t % 6 == 0) {
            for (int i = 0; i < 4 && !victims.isEmpty(); i++) hail(victims.get(RNG.nextInt(victims.size())));
        }
    }

    void hail(LivingEntity e) {
        Vec3 p = e.m_20182_();
        double x = p.f_82479_, y = p.f_82480_, z = p.f_82481_;
        for (double h = 14; h > 0; h -= 0.8) Fx.send(level, ICE_BITS, x, y + h, z, 1, 0, 0, 0, 0);
        Fx.send(level, ICE_BITS, x, y + 1, z, 20, 0.3, 0.5, 0.3, 0.25);
        Fx.sound(level, x, y, z, "block.glass.break", 1.5F, 1.4F);
        e.m_6469_(level.m_269111_().m_269109_(), 2.5F);
    }

    // ---------------- Раскол ----------------
    void startShatter() {
        shattering = true;
        double r = STORM_R;
        AABB box = new AABB(cx - r - 4, cy - 40, cz - r - 4, cx + r + 4, cy + 50, cz + r + 4);
        for (Entity e : level.m_6249_(null, box, e -> e.m_6084_() && !e.m_5833_())) {
            if (e == owner || !(e instanceof LivingEntity)) continue;
            int ft = e.m_146888_();
            if (ft <= 0) continue;
            double k = Math.min(1.0D, ft / (double) Math.max(1, e.m_146891_()));
            Vec3 p = e.m_20182_();
            Fx.send(level, ICE_BITS, p.f_82479_, p.f_82480_ + 1, p.f_82481_, 60, 0.4, 0.8, 0.4, 0.4);
            Fx.send(level, PACKED_BITS, p.f_82479_, p.f_82480_ + 1, p.f_82481_, 30, 0.4, 0.8, 0.4, 0.5);
            Fx.sound(level, p.f_82479_, p.f_82480_, p.f_82481_, "block.glass.break", 2.0F, 0.7F);
            e.m_6469_(level.m_269111_().m_269109_(), (float) (8.0D + 22.0D * k)); // полностью замёрзший: 30 урона
            e.m_146917_(0);
        }
        for (BlockPos p : spikes) if (level.m_8055_(p).m_60734_() == PACKED_BLOCK) level.m_46961_(p, false);
        spikes.clear();
        Fx.send(level, SNOWFLAKE, cx, cy + 2, cz, 600, 10, 5, 10, 1.0);
        Fx.sound(level, cx, cy, cz, "block.glass.break", 30.0F, 0.4F);
        Fx.sound(level, cx, cy, cz, "block.amethyst_cluster.break", 30.0F, 0.4F);
        Fx.sound(level, cx, cy, cz, "entity.player.hurt_freeze", 20.0F, 0.5F);
    }

    /** Весь лёд в шаре разлетается — от центра к краю. */
    boolean shatterStep() {
        long[] offs = Fx.sphere(FREEZE_R);
        if (AIR == null) return true;
        int end = Math.min(offs.length, shatterIdx + SHATTER_PER_TICK);
        double shell = 0;
        for (; shatterIdx < end; shatterIdx++) {
            long v = offs[shatterIdx];
            shell = Fx.sd(v);
            BlockPos p = new BlockPos(bx + Fx.sx(v, FREEZE_R), by + Fx.sy(v, FREEZE_R), bz + Fx.sz(v, FREEZE_R));
            if (!level.m_46805_(p)) continue;
            if (level.m_8055_(p).m_60734_() == ICE_BLOCK) level.m_7731_(p, AIR, 18);
        }
        for (int i = 0; i < 24; i++) {
            double[] u = Fx.unit();
            Fx.send(level, ICE_BITS, cx + u[0] * shell, cy + u[1] * shell, cz + u[2] * shell, 8, 1.0, 1.0, 1.0, 0.4);
        }
        if (shatterIdx % (SHATTER_PER_TICK * 4) < SHATTER_PER_TICK)
            Fx.sound(level, cx, cy, cz, "block.glass.break", 25.0F, 0.4F + RNG.nextFloat() * 0.4F);
        return shatterIdx >= offs.length;
    }
}
