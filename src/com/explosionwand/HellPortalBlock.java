package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Проём портала в Ад (4.13): без коллизии, не ломается и не выпадает; игрок, простоявший внутри, переносится (HellGate.inside). */
public class HellPortalBlock extends Block {
    public HellPortalBlock(Properties p) { super(p); }

    /** Без коллизии, но "твёрдый" (forceSolidOn): иначе вода и лава могут заменить блок портала. */
    @Override
    public boolean m_6104_(BlockState st, BlockState adj, net.minecraft.core.Direction d) { return adj.m_60734_() == this; }

    @Override
    public void m_7892_(BlockState st, Level level, BlockPos pos, Entity e) {
        if (level.f_46443_ || !(level instanceof ServerLevel sl) || !(e instanceof ServerPlayer p) || !p.m_6084_()) return;
        HellGate.inside(sl, p, pos);
    }
}
