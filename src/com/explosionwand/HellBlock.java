package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Блоки Ада (5.1) с поведением, не требующим своих свойств: гейзер, раскалённый камень, тлеющая листва и искровик (искры),
 * растения и лианы с опорой, огненный и призрачный цветок/коралл. Свойства (age, facing, layers, axis) - в своих классах/ванильных предках.
 * Вид блока (кресты, ковры) - из ресурсов; здесь только поведение.
 */
class HellBlock extends Block {
    enum Kind {
        /** Раскалённый камень: жжёт стоящих на нём, как магма (кроме присевших). */
        BLAZING,
        /** Гейзер: раз в 8 с столб огня вверх. */
        GEYSER,
        /** Тлеющая листва: изредка роняет искры. */
        LEAVES,
        /** Растение на твёрдом блоке (сухая трава, огнецвет). */
        PLANT,
        /** Искровик: растение, изредка искрит. */
        SPARK_FERN,
        /** Призрачный цветок: только на почве/песке душ и призрачном мхе. */
        GHOST_FLOWER,
        /** Лиана: висит с блока над собой. */
        VINE,
        /** Огненный коралл: на шлаке/магме или рядом с лавой. */
        CORAL
    }

    /** Период гейзера, секунд; за секунду до извержения - предупреждающий дым. */
    static final int GEYSER_PERIOD = 8, GEYSER_REACH = 7;
    static final VoxelShape SHAPE_PLANT = Block.m_49796_(3.0D, 0.0D, 3.0D, 13.0D, 12.0D, 13.0D);
    static final VoxelShape SHAPE_VINE = Block.m_49796_(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);

    final Kind kind;

    HellBlock(Properties p, Kind kind) { super(p); this.kind = kind; }

    boolean needsSupport() { return kind == Kind.PLANT || kind == Kind.SPARK_FERN || kind == Kind.GHOST_FLOWER || kind == Kind.VINE || kind == Kind.CORAL; }

    // ---- опора

    @Override
    public boolean m_7898_(BlockState st, LevelReader l, BlockPos pos) {
        BlockPos dn = pos.m_7495_();
        BlockState below = l.m_8055_(dn);
        switch (kind) {
            case PLANT: case SPARK_FERN:
                return below.m_60783_(l, dn, HellBlocks.UP);
            case GHOST_FLOWER:
                return HellBlocks.isVanilla(below, "soul_soil") || HellBlocks.isVanilla(below, "soul_sand") || HellBlocks.is(below, "ghost_moss");
            case VINE: {
                BlockPos up = pos.m_7494_();
                return l.m_8055_(up).m_60783_(l, up, HellBlocks.DOWN);
            }
            case CORAL:
                return below.m_60783_(l, dn, HellBlocks.UP) && (HellBlocks.is(below, "slag") || HellBlocks.isVanilla(below, "magma_block") || lavaNear(l, pos));
            default:
                return true;
        }
    }

    /** Лава рядом с кораллом: в радиусе 2 по горизонтали, на уровне корала и ниже. */
    static boolean lavaNear(LevelReader l, BlockPos pos) {
        Block lava = Fx.block("lava");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 0; dy++)
            if (l.m_8055_(new BlockPos(pos.m_123341_() + dx, pos.m_123342_() + dy, pos.m_123343_() + dz)).m_60734_() == lava) return true;
        return false;
    }

    /** Потерял опору - исчезает (с выпадением: Block.updateOrDestroy). */
    @Override
    public BlockState m_7417_(BlockState st, Direction d, BlockState nb, LevelAccessor l, BlockPos pos, BlockPos npos) {
        if (needsSupport() && !st.m_60710_(l, pos)) return Fx.state("air");
        return super.m_7417_(st, d, nb, l, pos, npos);
    }

    @Override
    public VoxelShape m_5940_(BlockState st, BlockGetter g, BlockPos pos, CollisionContext c) {
        switch (kind) {
            case PLANT: case SPARK_FERN: case GHOST_FLOWER: case CORAL: return SHAPE_PLANT;
            case VINE: return SHAPE_VINE;
            default: return super.m_5940_(st, g, pos, c);
        }
    }

    // ---- раскалённый камень

    @Override
    public void m_141947_(Level l, BlockPos pos, BlockState st, Entity e) {
        if (kind == Kind.BLAZING && e instanceof LivingEntity && !e.m_5825_() && !e.m_6144_()) e.m_6469_(l.m_269111_().m_269047_(), 1.0F);
        super.m_141947_(l, pos, st, e);
    }

    // ---- случайные тики: искры, запуск гейзера

    @Override
    public void m_213898_(BlockState st, ServerLevel l, BlockPos pos, RandomSource r) {
        double x = pos.m_123341_() + 0.5D, y = pos.m_123342_(), z = pos.m_123343_() + 0.5D;
        switch (kind) {
            case LEAVES:
                // искры падают вниз из-под листвы
                if (l.m_8055_(pos.m_7495_()).m_60795_()) HellBlocks.puff(l, r.m_188503_(3) == 0 ? Fx.particle("lava") : Fx.particle("flame"), x, y - 0.1D, z, 2, 0.25D, 0.02D, 0.25D, 0.01D);
                break;
            case SPARK_FERN:
                HellBlocks.puff(l, Fx.particle("lava"), x, y + 0.6D, z, 2, 0.2D, 0.2D, 0.2D, 0.0D);
                break;
            case GEYSER:
                // цикл гейзера идёт запланированными тиками; случайный тик (повторный запуск после загрузки/генерации) просто запускает его. Дубль того же тика не ставится.
                l.m_186460_(pos, this, 20);
                break;
            default:
        }
    }

    @Override
    public void m_6807_(BlockState st, Level l, BlockPos pos, BlockState old, boolean moving) {
        if (kind == Kind.GEYSER && !l.f_46443_) l.m_186460_(pos, this, 20);
    }

    // ---- гейзер

    @Override
    public void m_213897_(BlockState st, ServerLevel l, BlockPos pos, RandomSource r) {
        if (kind != Kind.GEYSER) return;
        l.m_186460_(pos, this, 20);
        int phase = geyserPhase(l.m_46467_(), pos);
        if (phase == GEYSER_PERIOD - 1) erupt(l, pos, true);
        else if (phase == 0) erupt(l, pos, false);
    }

    /** Фаза гейзера 0..7 (секунды); у каждого гейзера свой сдвиг по координатам, чтобы они не били хором. 0 - извержение, 7 - дым-предупреждение. */
    static int geyserPhase(long gameTime, BlockPos pos) {
        long shift = pos.m_123341_() * 31L + pos.m_123342_() * 17L + pos.m_123343_() * 13L;
        return (int) Math.floorMod(gameTime / 20L + shift, (long) GEYSER_PERIOD);
    }

    /** Высота свободного столба над гейзером (до GEYSER_REACH): стоп на первом полном блоке. */
    static int columnHeight(Level l, BlockPos pos) {
        int h = 0;
        for (int i = 1; i <= GEYSER_REACH; i++) {
            BlockPos p = pos.m_6630_(i);
            if (l.m_8055_(p).m_60838_(l, p)) break;
            h = i;
        }
        return h;
    }

    static void erupt(ServerLevel l, BlockPos pos, boolean warning) {
        double cx = pos.m_123341_() + 0.5D, cz = pos.m_123343_() + 0.5D, y = pos.m_123342_();
        boolean near = false;
        for (ServerPlayer p : l.m_6907_()) if (p.m_20275_(cx, y, cz) < 48.0D * 48.0D) { near = true; break; }
        if (!near) return;     // без игроков рядом гейзер молчит: только перезаряжается
        int h = columnHeight(l, pos);
        if (h == 0) return;
        if (warning) {
            HellBlocks.puff(l, Fx.particle("smoke"), cx, y + 1.1D, cz, 6, 0.2D, 0.1D, 0.2D, 0.02D);
            HellBlocks.puff(l, Fx.particle("lava"), cx, y + 1.1D, cz, 2, 0.2D, 0.1D, 0.2D, 0.0D);
            Fx.sound(l, cx, y + 1.0D, cz, "block.lava.pop", 0.8F, 0.6F);
            return;
        }
        ParticleOptions flame = Fx.particle("flame");
        for (int i = 1; i <= h; i++) {
            HellBlocks.puff(l, flame, cx, y + i, cz, 8, 0.15D, 0.3D, 0.15D, 0.04D);
            if (i % 2 == 0) HellBlocks.puff(l, Fx.particle("lava"), cx, y + i, cz, 1, 0.2D, 0.2D, 0.2D, 0.0D);
        }
        HellBlocks.puff(l, Fx.particle("large_smoke"), cx, y + h + 0.5D, cz, 4, 0.3D, 0.2D, 0.3D, 0.02D);
        Fx.sound(l, cx, y + 1.0D, cz, "entity.blaze.shoot", 1.0F, 0.6F);
        List<LivingEntity> victims = l.m_6443_(LivingEntity.class, new AABB(pos.m_123341_(), y + 1.0D, pos.m_123343_(), pos.m_123341_() + 1.0D, y + 1.0D + h, pos.m_123343_() + 1.0D), e -> e.m_6084_());
        for (LivingEntity e : victims) {
            if (e.m_5825_()) continue;
            e.m_20254_(6);
            e.m_6469_(l.m_269111_().m_269387_(), 3.0F);
        }
    }
}
