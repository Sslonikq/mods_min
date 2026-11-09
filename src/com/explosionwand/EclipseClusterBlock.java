package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Друза затмения (5.1): age 0-3 (свет 4/6/8/10), facing - сторона, куда растёт (как у аметиста; крепится к блоку за спиной).
 * Растёт случайными тиками, если в 1 блоке (3x3x3) есть Кристалл затмения: age+1.
 */
class EclipseClusterBlock extends Block {
    static final IntegerProperty AGE = IntegerProperty.m_61631_("age", 0, 3);
    static final DirectionProperty FACING = BlockStateProperties.f_61372_;
    static final VoxelShape UP = Block.m_49796_(3.0D, 0.0D, 3.0D, 13.0D, 10.0D, 13.0D), DOWN = Block.m_49796_(3.0D, 6.0D, 3.0D, 13.0D, 16.0D, 13.0D),
            NORTH = Block.m_49796_(3.0D, 3.0D, 6.0D, 13.0D, 13.0D, 16.0D), SOUTH = Block.m_49796_(3.0D, 3.0D, 0.0D, 13.0D, 13.0D, 10.0D),
            EAST = Block.m_49796_(0.0D, 3.0D, 3.0D, 10.0D, 13.0D, 13.0D), WEST = Block.m_49796_(6.0D, 3.0D, 3.0D, 16.0D, 13.0D, 13.0D);

    EclipseClusterBlock(Properties p) {
        super(p);
        m_49959_(m_49965_().m_61090_().m_61124_(AGE, 0).m_61124_(FACING, HellBlocks.UP));
    }

    @Override
    protected void m_7926_(StateDefinition.Builder<Block, BlockState> b) { b.m_61104_(AGE, FACING); }

    /** Свет по возрасту: 4, 6, 8, 10 (используется в свойствах блока). */
    static int light(int age) { return 4 + 2 * age; }

    @Override
    public BlockState m_5573_(BlockPlaceContext c) {
        return m_49966_().m_61124_(FACING, c.m_43719_());
    }

    @Override
    public boolean m_7898_(BlockState st, LevelReader l, BlockPos pos) {
        Direction f = st.m_61143_(FACING);
        BlockPos back = pos.m_121945_(f.m_122424_());
        return l.m_8055_(back).m_60783_(l, back, f);
    }

    @Override
    public BlockState m_7417_(BlockState st, Direction d, BlockState nb, LevelAccessor l, BlockPos pos, BlockPos npos) {
        if (d == st.m_61143_(FACING).m_122424_() && !st.m_60710_(l, pos)) return Fx.state("air");
        return super.m_7417_(st, d, nb, l, pos, npos);
    }

    @Override
    public VoxelShape m_5940_(BlockState st, BlockGetter g, BlockPos pos, CollisionContext c) {
        switch (st.m_61143_(FACING).name()) {
            case "DOWN": return DOWN;
            case "NORTH": return NORTH;
            case "SOUTH": return SOUTH;
            case "EAST": return EAST;
            case "WEST": return WEST;
            default: return UP;
        }
    }

    /** Кристалл затмения в кубе 3x3x3 вокруг клетки. */
    static boolean crystalNear(LevelReader l, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            if (HellBlocks.is(l.m_8055_(new BlockPos(pos.m_123341_() + dx, pos.m_123342_() + dy, pos.m_123343_() + dz)), "eclipse_crystal_block")) return true;
        }
        return false;
    }

    @Override
    public void m_213898_(BlockState st, ServerLevel l, BlockPos pos, RandomSource r) {
        int age = st.m_61143_(AGE);
        if (age >= 3 || r.m_188503_(3) != 0 || !crystalNear(l, pos) || Dungeons.protectedAt(l, pos)) return;
        l.m_7731_(pos, st.m_61124_(AGE, age + 1), 2);
        HellBlocks.puff(l, Fx.particle("end_rod"), pos.m_123341_() + 0.5D, pos.m_123342_() + 0.5D, pos.m_123343_() + 0.5D, 3, 0.2D, 0.2D, 0.2D, 0.01D);
    }
}
