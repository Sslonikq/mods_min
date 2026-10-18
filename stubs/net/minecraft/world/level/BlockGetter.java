package net.minecraft.world.level; import net.minecraft.world.phys.BlockHitResult; import net.minecraft.core.BlockPos; import net.minecraft.world.level.block.state.BlockState;
public interface BlockGetter { default BlockHitResult m_45547_(ClipContext c){return null;} BlockState m_8055_(BlockPos p); }
