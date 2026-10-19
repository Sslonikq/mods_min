package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/**
 * Палка Хроноса: ставит «якорь времени» — гигантские часы над областью радиусом 32.
 * Повторный клик (или через 60 сек) — время откатывается: разрушенные блоки
 * возвращаются, все существа отматываются туда, где стояли, со своим здоровьем.
 */
public class Chronos {
    static final List<Chronos> ACTIVE = new ArrayList<>();
    static final Map<UUID, Chronos> BY_OWNER = new HashMap<>();
    static final Random RNG = Fx.RNG;
    static final int R = 32, LIFE = 1200, RESTORE_PER_TICK = 6000;
    static ParticleOptions GOLD, CYAN, PALE;

    static final class Rec { Entity e; double x, y, z; float hp; }

    final ServerLevel level; final Entity owner;
    final double cx, cy, cz; final int bx, by, bz;
    final BlockState[] snap;
    final List<Rec> recs = new ArrayList<>();
    final Set<Entity> itemsBefore = Collections.newSetFromMap(new IdentityHashMap<>());
    int t = 0, restoreIdx = -1, rewindT = 0;
    boolean rewinding = false;

    Chronos(ServerLevel level, Vec3 c, Entity owner) {
        this.level = level; this.owner = owner;
        this.cx = c.f_82479_; this.cy = c.f_82480_; this.cz = c.f_82481_;
        this.bx = (int) Math.floor(cx); this.by = (int) Math.floor(cy); this.bz = (int) Math.floor(cz);
        if (GOLD == null) {
            GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.2F), 2.0F);
            CYAN = new DustParticleOptions(new Vector3f(0.3F, 0.95F, 1.0F), 1.6F);
            PALE = new DustParticleOptions(new Vector3f(1.0F, 0.95F, 0.75F), 1.2F);
        }
        // снимок мира
        long[] offs = Fx.sphere(R);
        snap = new BlockState[offs.length];
        for (int i = 0; i < offs.length; i++) {
            long v = offs[i];
            BlockPos p = new BlockPos(bx + Fx.sx(v, R), by + Fx.sy(v, R), bz + Fx.sz(v, R));
            if (level.m_46805_(p)) snap[i] = level.m_8055_(p);
        }
        AABB box = new AABB(cx - R, cy - R, cz - R, cx + R, cy + R, cz + R);
        for (Entity e : level.m_6249_(null, box, e -> e.m_6084_())) {
            if (e instanceof ItemEntity) { itemsBefore.add(e); continue; }
            Rec r = new Rec(); r.e = e; Vec3 p = e.m_20182_(); r.x = p.f_82479_; r.y = p.f_82480_; r.z = p.f_82481_;
            r.hp = (e instanceof LivingEntity le) ? le.m_21223_() : 0;
            recs.add(r);
        }
        Fx.sound(level, cx, cy, cz, "block.bell.use", 6.0F, 0.5F);
        Fx.sound(level, cx, cy, cz, "block.beacon.power_select", 6.0F, 1.2F);
        Fx.send(level, Fx.particle("flash"), cx, cy + 1, cz, 1, 0, 0, 0, 0);
    }

    void trigger() { if (!rewinding) startRewind(); }

    boolean tick() {
        t++;
        if (!rewinding) {
            if (t % 2 == 0) clock(t * 0.02, t * 0.0017, false);
            if (t % 20 == 0) Fx.sound(level, cx, cy, cz, "block.note_block.hat", 4.0F, 0.6F);
            if (t % 20 == 10) Fx.sound(level, cx, cy, cz, "block.note_block.hat", 4.0F, 0.5F);
            if (t >= LIFE) startRewind();
            return false;
        }
        rewindT++;
        clock(-rewindT * 0.35, -rewindT * 0.03, true);
        if (rewindT % 3 == 0) Fx.sound(level, cx, cy, cz, "block.note_block.hat", 5.0F, 1.0F + Math.min(1.0F, rewindT / 40F));
        return restoreBlocks();
    }

    void startRewind() {
        rewinding = true; rewindT = 0;
        restoreIdx = snap.length - 1;     // от краёв к центру
        Fx.sound(level, cx, cy, cz, "entity.illusioner.mirror_move", 8.0F, 0.5F);
        Fx.sound(level, cx, cy, cz, "block.beacon.deactivate", 8.0F, 0.5F);
        Fx.sound(level, cx, cy, cz, "block.portal.travel", 2.0F, 2.0F);
        var rp = Fx.particle("reverse_portal");
        // существа — обратно, где стояли, со своим здоровьем
        for (Rec r : recs) {
            Entity e = r.e;
            if (!e.m_6084_() || e.m_9236_() != level) continue;
            Vec3 p = e.m_20182_();
            double dx = r.x - p.f_82479_, dy = r.y - p.f_82480_, dz = r.z - p.f_82481_;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            for (double s = 0; s < d; s += 0.6) {
                double k = s / Math.max(d, 1e-4);
                Fx.send(level, CYAN, p.f_82479_ + dx * k, p.f_82480_ + 1 + dy * k, p.f_82481_ + dz * k, 1, 0, 0, 0, 0);
            }
            e.m_6021_(r.x, r.y, r.z);
            e.m_20256_(new Vec3(0, 0, 0)); e.f_19864_ = true; e.f_19789_ = 0.0F;
            if (e instanceof LivingEntity le && r.hp > 0) le.m_21153_(Math.min(le.m_21233_(), r.hp));
            Fx.send(level, rp, r.x, r.y + 1, r.z, 30, 0.3, 0.6, 0.3, 0.2);
        }
        // вещи, выпавшие после якоря, исчезают
        AABB box = new AABB(cx - R, cy - R, cz - R, cx + R, cy + R, cz + R);
        for (Entity e : level.m_6249_(null, box, e -> e instanceof ItemEntity)) if (!itemsBefore.contains(e)) e.m_146870_();
    }

    boolean restoreBlocks() {
        long[] offs = Fx.sphere(R);
        int end = Math.max(-1, restoreIdx - RESTORE_PER_TICK);
        double shell = 0; int changed = 0;
        Dungeons.Shield shield = Dungeons.shield(level);
        var rp = Fx.particle("reverse_portal");
        for (; restoreIdx > end; restoreIdx--) {
            BlockState want = snap[restoreIdx];
            if (want == null) continue;
            long v = offs[restoreIdx];
            shell = Fx.sd(v);
            BlockPos p = new BlockPos(bx + Fx.sx(v, R), by + Fx.sy(v, R), bz + Fx.sz(v, R));
            if (!level.m_46805_(p) || shield.at(p)) continue;
            if (level.m_8055_(p) != want) {
                level.m_7731_(p, want, 18);
                if (changed++ < 40 && RNG.nextInt(4) == 0) Fx.send(level, rp, bx + Fx.sx(v, R) + 0.5, by + Fx.sy(v, R) + 0.5, bz + Fx.sz(v, R) + 0.5, 4, 0.3, 0.3, 0.3, 0.1);
            }
        }
        for (int i = 0; i < 12; i++) {
            double[] u = Fx.unit();
            Fx.send(level, PALE, cx + u[0] * shell, cy + u[1] * shell, cz + u[2] * shell, 2, 0.4, 0.4, 0.4, 0);
        }
        if (restoreIdx < 0) {
            Fx.send(level, Fx.particle("flash"), cx, cy + 1, cz, 1, 0, 0, 0, 0);
            Fx.sound(level, cx, cy, cz, "block.bell.use", 8.0F, 1.5F);
            Fx.sound(level, cx, cy, cz, "block.amethyst_block.chime", 8.0F, 0.5F);
            return true;
        }
        return false;
    }

    /** Огромные часы: циферблат, 12 делений, стрелки. */
    void clock(double minute, double hour, boolean rewind) {
        double y = cy + 0.6;
        ParticleOptions ring = rewind ? CYAN : GOLD;
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            Fx.send(level, ring, cx + Math.cos(a) * R, y, cz + Math.sin(a) * R, 1, 0, 0, 0, 0);
        }
        for (int h = 0; h < 12; h++) {
            double a = h * Math.PI * 2 / 12;
            Fx.send(level, PALE, cx + Math.cos(a) * (R - 1.5), y + 1.2, cz + Math.sin(a) * (R - 1.5), 4, 0.05, 1.0, 0.05, 0);
        }
        for (double s = 1; s < R - 4; s += 1.0) Fx.send(level, CYAN, cx + Math.cos(minute) * s, y + 0.3, cz + Math.sin(minute) * s, 1, 0, 0, 0, 0);
        for (double s = 1; s < R * 0.55; s += 0.8) Fx.send(level, GOLD, cx + Math.cos(hour) * s, y + 0.5, cz + Math.sin(hour) * s, 1, 0, 0, 0, 0);
        Fx.send(level, Fx.particle("end_rod"), cx, y + 1, cz, 2, 0.2, 0.4, 0.2, 0.01);
    }
}
