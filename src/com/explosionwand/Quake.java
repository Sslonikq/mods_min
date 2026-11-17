package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/**
 * Палочка Землетрясения. Удар о землю, дальше около 9 секунд:
 * 1) Гул: земля дрожит, из неё летит пыль, всех вокруг трясёт.
 * 2) Три сейсмические волны: верхний слой земли идёт рябью, как вода (блоки подпрыгивают и падают на своё место), мобов на гребне подбрасывает.
 * 3) Разлом: от эпицентра бегут зигзагом три трещины с ветками, на дне магма, из краёв летят камни, бьют лавовые гейзеры, стоящих над трещиной затягивает вниз.
 * 4) Смыкание: трещины захлопываются от концов к центру, земля возвращается как была; кто внутри, получает урон и вылетает наверх.
 * Хозяина не ранит. Блоки данжей, блоки с BlockEntity, бедрок и жидкости не трогает.
 */
public class Quake {
    static final List<Quake> ACTIVE = new ArrayList<>();
    static final Random RNG = Fx.RNG;

    static final int OPEN_AT = 30, CLOSE_AT = 170, CLOSE_TICKS = 16, END = 200;
    static final int[] WAVES = {30, 50, 70};
    static final double WAVE_MAX = 28.0, SHAKE_R = 30.0;
    static final int CRACKS = 3, CRACK_LEN = 30, MAX_LIFTS = 44, MAX_FLINGS = 70, SURFACE_DY = 12;

    static DustParticleOptions EMBER, DUST;

    final ServerLevel level; final ServerPlayer owner;
    final int bx, by, bz;
    final List<QuakeLogic.Cell> cells;
    final int[] cellTop, cellStart, cellEnd;
    final List<BlockPos> rPos = new ArrayList<>();
    final List<BlockState> rWas = new ArrayList<>(), rNow = new ArrayList<>();
    final Set<Long> planned = new HashSet<>(), crackCols = new HashSet<>();   // все клетки трещин / уже открытые
    final double[] waveR = new double[WAVES.length];
    final List<Set<Entity>> waveHit = new ArrayList<>();
    int t = 0, opened = 0, closed = 0, flings = 0;
    BlockState AIR, MAGMA;

    Quake(ServerLevel level, ServerPlayer owner, double x, double z, double angle, boolean selfCentered) {
        this.level = level; this.owner = owner;
        this.bx = (int) Math.floor(x); this.bz = (int) Math.floor(z);
        this.by = level.m_6924_(Heightmap.Types.valueOf("MOTION_BLOCKING_NO_LEAVES"), bx, bz) - 1;
        this.cells = QuakeLogic.cracks(RNG, angle, CRACKS, CRACK_LEN, !selfCentered);
        int n = cells.size();
        cellTop = new int[n]; cellStart = new int[n]; cellEnd = new int[n];
        for (QuakeLogic.Cell c : cells) planned.add(QuakeLogic.key(bx + c.x, bz + c.z));
        for (int i = 0; i < WAVES.length; i++) waveHit.add(Collections.newSetFromMap(new IdentityHashMap<>()));
        AIR = Fx.state("air"); MAGMA = Fx.state("magma_block");
        if (EMBER == null) {
            EMBER = new DustParticleOptions(new Vector3f(1.0F, 0.42F, 0.08F), 1.5F);
            DUST = new DustParticleOptions(new Vector3f(0.45F, 0.36F, 0.26F), 2.2F);
        }
        double cx = bx + 0.5, cy = by + 1, cz = bz + 0.5;
        Fx.sound(level, cx, cy, cz, "entity.warden.emerge", 5.0F, 0.6F);
        Fx.sound(level, cx, cy, cz, "entity.ravager.roar", 4.0F, 0.4F);
        Fx.send(level, DUST, cx, cy, cz, 40, 1.5, 0.3, 1.5, 0);
    }

    boolean tick() {
        t++;
        if (AIR == null) return true;
        double cx = bx + 0.5, cy = by + 1, cz = bz + 0.5;
        if (t < CLOSE_AT + CLOSE_TICKS) shake();
        if (t < CLOSE_AT) dust(t < OPEN_AT ? 10 + t / 2 : 14);
        if (t < OPEN_AT && t % 8 == 0) Fx.sound(level, cx, cy, cz, "entity.warden.dig", 4.0F, 0.5F);
        if (t == OPEN_AT) {
            Fx.send(level, Fx.particle("explosion_emitter"), cx, cy, cz, 1, 0, 0, 0, 0);
            Fx.sound(level, cx, cy, cz, "entity.generic.explode", 8.0F, 0.4F);
            Fx.sound(level, cx, cy, cz, "block.anvil.land", 4.0F, 0.4F);
        }
        for (int w = 0; w < WAVES.length; w++) {
            if (t == WAVES[w]) { waveR[w] = 1.0; Fx.sound(level, cx, cy, cz, "entity.warden.attack_impact", 6.0F, 0.5F); }
            if (waveR[w] > 0 && waveR[w] < WAVE_MAX) wave(w);
        }
        if (t >= OPEN_AT && t < CLOSE_AT) {
            int k = t - OPEN_AT;
            while (opened < cells.size() && cells.get(opened).tick <= k) { open(opened); opened++; }
            if (opened > 0) crackLife();
        }
        if (t >= CLOSE_AT) close();
        return t >= END && closed >= opened;
    }

    // ---------------------------------------------------------------- тряска и пыль
    boolean affects(Entity e) { return e instanceof LivingEntity && e.m_6084_() && !e.m_5833_() && (!Cmd.hasTagPrefix(e, "ew") || Minions.dungeonMob(e)); }

    List<Entity> around(double r) {
        AABB box = new AABB(bx - r, by - SURFACE_DY, bz - r, bx + r, by + SURFACE_DY + 4, bz + r);
        return level.m_6249_(null, box, this::affects);
    }

    void shake() {
        if (t % 2 != 0) return;
        double s = t < OPEN_AT ? t / (double) OPEN_AT : (t < CLOSE_AT ? 1.0 : Math.max(0.0, 1.0 - (t - CLOSE_AT) / (double) CLOSE_TICKS));
        MobEffect slow = Fx.effect("slowness");
        for (Entity e : around(SHAKE_R)) {
            if (!e.m_20096_() || Minions.dungeonMob(e)) continue;
            double k = e == owner ? 0.4 : 1.0;
            Vec3 v = e.m_20184_();
            e.m_20256_(v.m_82520_(RNG.nextGaussian() * 0.07 * s * k, 0.05 * s * k * RNG.nextDouble(), RNG.nextGaussian() * 0.07 * s * k));
            e.f_19864_ = true;
            if (e != owner && slow != null && t % 20 == 0 && e instanceof LivingEntity le) le.m_7292_(new MobEffectInstance(slow, 40, 1));
        }
    }

    void dust(int n) {
        for (int i = 0; i < n; i++) {
            double a = RNG.nextDouble() * Math.PI * 2, r = Math.sqrt(RNG.nextDouble()) * 24.0;
            int x = (int) Math.floor(bx + 0.5 + Math.cos(a) * r), z = (int) Math.floor(bz + 0.5 + Math.sin(a) * r);
            int top = surface(x, z);
            if (top == Integer.MIN_VALUE) continue;
            BlockState s = level.m_8055_(new BlockPos(x, top, z));
            Fx.send(level, Fx.blockParticle(s), x + 0.5, top + 1.05, z + 0.5, 4, 0.35, 0.05, 0.35, 0.1);
            if (RNG.nextInt(4) == 0) Fx.send(level, DUST, x + 0.5, top + 1.3, z + 0.5, 1, 0.3, 0.2, 0.3, 0);
        }
    }

    /** Верхний блок колонки или MIN_VALUE, если чанк не загружен или колонка слишком далеко по высоте от эпицентра. */
    int surface(int x, int z) {
        if (!level.m_46805_(new BlockPos(x, by, z))) return Integer.MIN_VALUE;
        int top = level.m_6924_(Heightmap.Types.valueOf("MOTION_BLOCKING_NO_LEAVES"), x, z) - 1;
        return Math.abs(top - by) > SURFACE_DY ? Integer.MIN_VALUE : top;
    }

    void push(Entity e, double vx, double vy, double vz) {
        if (RiftMobs.knock(e, vx, vy, vz)) return;
        e.m_20256_(new Vec3(vx, vy, vz)); e.f_19864_ = true;
    }

    // ---------------------------------------------------------------- волны: земля идёт рябью
    void wave(int w) {
        waveR[w] += 0.9;
        double r = waveR[w], k = 1.0 - r / WAVE_MAX;
        Dungeons.Shield sh = Dungeons.shield(level);
        int n = (int) Math.min(MAX_LIFTS, 2 * Math.PI * r * 0.8);
        double off = RNG.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = off + i * Math.PI * 2 / n;
            lift((int) Math.floor(bx + 0.5 + Math.cos(a) * r), (int) Math.floor(bz + 0.5 + Math.sin(a) * r), k, sh);
        }
        if (((int) r) % 5 == 0) Fx.sound(level, bx + 0.5, by + 1, bz + 0.5, "block.gravel.break", 4.0F, 0.5F);
        for (Entity e : around(r + 1.5)) {
            Vec3 p = e.m_20182_();
            double dx = p.f_82479_ - (bx + 0.5), dz = p.f_82481_ - (bz + 0.5), d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - r) > 1.4 || !waveHit.get(w).add(e)) continue;
            if (e == owner) { push(e, e.m_20184_().f_82479_, 0.45, e.m_20184_().f_82481_); continue; }
            double ux = d < 0.3 ? 0 : dx / d, uz = d < 0.3 ? 0 : dz / d;
            e.m_6469_(src(), (float) (3.0 + 6.0 * k));
            push(e, ux * 0.35, 0.55 + 0.4 * k, uz * 0.35);
        }
    }

    void lift(int x, int z, double k, Dungeons.Shield sh) {
        if (planned.contains(QuakeLogic.key(x, z))) return;    // над будущей трещиной рябь не поднимаем
        int top = surface(x, z);
        if (top == Integer.MIN_VALUE) return;
        BlockPos p = new BlockPos(x, top, z), ap = new BlockPos(x, top + 1, z);
        if (sh.at(p) || sh.at(ap)) return;
        BlockState s = level.m_8055_(p);
        if (s.m_60795_() || s.m_155947_() || !s.m_60819_().m_76178_() || !s.m_60838_(level, p)) return;
        float h = s.m_60800_(level, p);
        if (h < 0.0F || h >= 50.0F) return;
        BlockState as = level.m_8055_(ap);
        if (!as.m_60795_()) {                                   // трава и снег сверху сбиваются, факелы и цветы держат колонку
            if (!as.m_247087_() || as.m_155947_() || !as.m_60819_().m_76178_()) return;
            level.m_7731_(ap, AIR, 18);
        }
        FallingBlockEntity fb = FallingBlockEntity.m_201971_(level, p, s);
        if (fb == null) return;
        fb.m_20256_(new Vec3(0, 0.22 + 0.16 * k, 0)); fb.f_19864_ = true;
        if (RNG.nextInt(3) == 0) Fx.send(level, Fx.blockParticle(s), x + 0.5, top + 1.0, z + 0.5, 3, 0.3, 0.1, 0.3, 0.05);
    }

    // ---------------------------------------------------------------- трещины
    void open(int i) {
        QuakeLogic.Cell c = cells.get(i);
        int x = bx + c.x, z = bz + c.z;
        cellStart[i] = rPos.size();
        int top = surface(x, z);
        cellTop[i] = top;
        if (top == Integer.MIN_VALUE) { cellEnd[i] = rPos.size(); return; }
        Dungeons.Shield sh = Dungeons.shield(level);
        BlockPos ap = new BlockPos(x, top + 1, z);
        BlockState as = level.m_8055_(ap);
        if (!as.m_60795_() && as.m_247087_() && !as.m_155947_() && as.m_60819_().m_76178_() && !sh.at(ap)) carve(ap, as, AIR);
        BlockState face = null;
        for (int d = 0; d < c.depth; d++) {
            BlockPos p = new BlockPos(x, top - d, z);
            if (sh.at(p)) break;
            BlockState s = level.m_8055_(p);
            if (!s.m_60819_().m_76178_() || s.m_155947_()) break;      // вода, лава, сундуки - стоп
            float h = s.m_60800_(level, p);
            if (h < 0.0F || h >= 50.0F) break;                         // бедрок, обсидиан
            if (face == null && !s.m_60795_()) face = s;
            carve(p, s, d == c.depth - 1 && MAGMA != null ? MAGMA : AIR);
        }
        cellEnd[i] = rPos.size();
        if (cellEnd[i] == cellStart[i]) return;
        crackCols.add(QuakeLogic.key(x, z));
        if (face != null) Fx.send(level, Fx.blockParticle(face), x + 0.5, top + 0.8, z + 0.5, 14, 0.4, 0.3, 0.4, 0.2);
        Fx.send(level, Fx.particle("large_smoke"), x + 0.5, top + 0.5, z + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
        if (i % 3 == 0) Fx.sound(level, x + 0.5, top, z + 0.5, "block.deepslate.break", 2.0F, 0.5F);
        if (flings < MAX_FLINGS && RNG.nextInt(3) == 0) flings += Debris.fling(level, x + 0.5, top, z + 0.5, 1.2, 2.5, 2, 0.35, 0.55);
    }

    void carve(BlockPos p, BlockState was, BlockState now) {
        rPos.add(p); rWas.add(was); rNow.add(now);
        level.m_7731_(p, now, 18);
    }

    /** Пока трещины открыты: жар со дна, гейзеры, стоящих над трещиной затягивает вниз. */
    void crackLife() {
        for (int j = 0; j < 3; j++) {
            int i = RNG.nextInt(opened);
            if (cellTop[i] == Integer.MIN_VALUE || cellEnd[i] == cellStart[i]) continue;
            QuakeLogic.Cell c = cells.get(i);
            double x = bx + c.x + 0.5, z = bz + c.z + 0.5, bot = cellTop[i] - c.depth + 2;
            Fx.send(level, EMBER, x, bot, z, 2, 0.3, 0.3, 0.3, 0);
            if (RNG.nextInt(3) == 0) Fx.send(level, Fx.particle("lava"), x, bot, z, 1, 0.2, 0.1, 0.2, 0);
            Fx.send(level, Fx.particle("smoke"), x, cellTop[i] + 1, z, 2, 0.3, 0.1, 0.3, 0.02);
        }
        if (t % 14 == 0) geyser();
        if (t % 2 == 0) for (Entity e : around(SHAKE_R + 8)) {
            if (e == owner) continue;
            Vec3 p = e.m_20182_();
            if (!crackCols.contains(QuakeLogic.key((int) Math.floor(p.f_82479_), (int) Math.floor(p.f_82481_)))) continue;
            Vec3 v = e.m_20184_();
            if (!RiftMobs.knock(e, 0, -0.5, 0)) { e.m_20256_(new Vec3(v.f_82479_ * 0.5, Math.min(v.f_82480_, -0.5), v.f_82481_ * 0.5)); e.f_19864_ = true; }
        }
    }

    void geyser() {
        int i = RNG.nextInt(opened);
        if (cellTop[i] == Integer.MIN_VALUE || cellEnd[i] == cellStart[i]) return;
        QuakeLogic.Cell c = cells.get(i);
        double x = bx + c.x + 0.5, z = bz + c.z + 0.5, bot = cellTop[i] - c.depth + 1, top = cellTop[i] + 1;
        for (double y = bot; y < top + 6; y += 0.5) {
            Fx.send(level, Fx.particle("flame"), x, y, z, 3, 0.25, 0.2, 0.25, 0.02);
            Fx.send(level, EMBER, x, y, z, 2, 0.3, 0.2, 0.3, 0);
        }
        Fx.send(level, Fx.particle("lava"), x, top, z, 18, 0.4, 0.4, 0.4, 0);
        Fx.send(level, Fx.particle("large_smoke"), x, top + 5, z, 12, 0.6, 0.6, 0.6, 0.05);
        Fx.sound(level, x, top, z, "block.lava.pop", 3.0F, 0.6F);
        Fx.sound(level, x, top, z, "entity.blaze.shoot", 2.0F, 0.5F);
        AABB col = new AABB(x - 1.6, bot, z - 1.6, x + 1.6, top + 6, z + 1.6);
        for (Entity e : level.m_6249_(owner, col, this::affects)) {
            e.m_6469_(src(), 6.0F);
            RiftMobs.ignite(e, 4);
            push(e, 0, 1.1, 0);
        }
    }

    /** Смыкание: от концов трещин к центру; кто внутри - урон и наверх, земля как была. */
    void close() {
        if (t == CLOSE_AT) {
            Fx.sound(level, bx + 0.5, by + 1, bz + 0.5, "entity.warden.roar", 5.0F, 0.5F);
            Fx.sound(level, bx + 0.5, by + 1, bz + 0.5, "block.deepslate.break", 5.0F, 0.4F);
        }
        int per = Math.max(1, (opened + CLOSE_TICKS - 1) / CLOSE_TICKS);
        for (int n = 0; n < per && closed < opened; n++) {
            int i = opened - 1 - closed;
            closed++;
            if (cellEnd[i] == cellStart[i]) continue;
            QuakeLogic.Cell c = cells.get(i);
            int x = bx + c.x, z = bz + c.z, top = cellTop[i];
            AABB box = new AABB(x - 0.3, top - c.depth, z - 0.3, x + 1.3, top + 2.0, z + 1.3);
            for (Entity e : level.m_6249_(null, box, this::affects)) {
                e.m_6021_(x + 0.5, top + 1.1, z + 0.5);
                if (e != owner) e.m_6469_(src(), 14.0F);
                push(e, 0, e == owner ? 0.5 : 0.9, 0);
                e.f_19789_ = 0.0F;
            }
            for (int j = cellEnd[i] - 1; j >= cellStart[i]; j--) {
                BlockPos p = rPos.get(j);
                if (level.m_46805_(p) && level.m_8055_(p) == rNow.get(j)) level.m_7731_(p, rWas.get(j), 18);
            }
            Fx.send(level, Fx.blockParticle(rWas.get(cellStart[i])), x + 0.5, top + 1.0, z + 0.5, 10, 0.4, 0.2, 0.4, 0.15);
            if (closed % 4 == 0) Fx.sound(level, x + 0.5, top, z + 0.5, "block.stone.place", 3.0F, 0.5F);
        }
        if (t == CLOSE_AT + CLOSE_TICKS && opened > 0) {
            Fx.sound(level, bx + 0.5, by + 1, bz + 0.5, "entity.generic.explode", 6.0F, 0.5F);
            Fx.sound(level, bx + 0.5, by + 1, bz + 0.5, "block.anvil.land", 5.0F, 0.5F);
            Fx.send(level, DUST, bx + 0.5, by + 1.5, bz + 0.5, 80, 3, 0.6, 3, 0);
        }
    }

    net.minecraft.world.damagesource.DamageSource src() {
        return owner != null && owner.m_6084_() ? level.m_269111_().m_269075_(owner) : level.m_269111_().m_269425_();
    }
}
