package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Куст углеягод (5.1): age 0-3, растёт случайными тиками, при age >= 2 по ПКМ даёт углеягоды (как сладкая ягода) и откатывается в age 1.
 * Сломанный куст отдаёт ягоды по лут-таблице (age 3: 2-3, age 2: 1-2). Стоит на любом блоке с твёрдой верхней гранью.
 */
class CoalBerryBushBlock extends Block {
    static final IntegerProperty AGE = IntegerProperty.m_61631_("age", 0, 3);
    static final VoxelShape SMALL = Block.m_49796_(3.0D, 0.0D, 3.0D, 13.0D, 8.0D, 13.0D), BIG = Block.m_49796_(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

    CoalBerryBushBlock(Properties p) {
        super(p);
        m_49959_(m_49965_().m_61090_().m_61124_(AGE, 0));
    }

    @Override
    protected void m_7926_(StateDefinition.Builder<Block, BlockState> b) { b.m_61104_(AGE); }

    @Override
    public VoxelShape m_5940_(BlockState st, BlockGetter g, BlockPos pos, CollisionContext c) { return st.m_61143_(AGE) == 0 ? SMALL : BIG; }

    @Override
    public boolean m_7898_(BlockState st, LevelReader l, BlockPos pos) {
        BlockPos dn = pos.m_7495_();
        return l.m_8055_(dn).m_60783_(l, dn, HellBlocks.UP);
    }

    @Override
    public BlockState m_7417_(BlockState st, Direction d, BlockState nb, LevelAccessor l, BlockPos pos, BlockPos npos) {
        return !st.m_60710_(l, pos) ? Fx.state("air") : super.m_7417_(st, d, nb, l, pos, npos);
    }

    /** Рост: примерно раз в 5 случайных тиков, пока age < 3 (без света: в Аду солнца нет). Блоки данжей не трогаем. */
    @Override
    public void m_213898_(BlockState st, ServerLevel l, BlockPos pos, RandomSource r) {
        int age = st.m_61143_(AGE);
        if (age >= 3 || r.m_188503_(5) != 0 || Dungeons.protectedAt(l, pos)) return;
        l.m_7731_(pos, st.m_61124_(AGE, age + 1), 2);
    }

    /** Сколько ягод даёт сбор: age 2 - 1..2, age 3 - 2..3 (как лут-таблица). roll - 0 или 1. */
    static int berries(int age, int roll) { return age >= 3 ? 2 + roll : age == 2 ? 1 + roll : 0; }

    @Override
    public InteractionResult m_6227_(BlockState st, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        int age = st.m_61143_(AGE);
        if (age < 2) return InteractionResult.valueOf("PASS");
        if (!l.f_46443_) {
            Block.m_49840_(l, pos, new ItemStack(HellBlocks.COAL_BERRIES.get(), berries(age, l.f_46441_.m_188503_(2))));
            if (l instanceof ServerLevel sl) Fx.sound(sl, pos.m_123341_() + 0.5D, pos.m_123342_() + 0.5D, pos.m_123343_() + 0.5D, "block.sweet_berry_bush.pick_berries", 1.0F, 0.8F);
            l.m_7731_(pos, st.m_61124_(AGE, 1), 2);
        }
        return InteractionResult.valueOf(l.f_46443_ ? "SUCCESS" : "CONSUME");
    }
}
