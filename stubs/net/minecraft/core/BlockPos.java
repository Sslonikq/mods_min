package net.minecraft.core; public class BlockPos extends Vec3i { public BlockPos(int x,int y,int z){super(x,y,z);} public BlockPos m_7949_(){return this;}
 public BlockPos m_7494_(){return new BlockPos(m_123341_(), m_123342_() + 1, m_123343_());} public BlockPos m_7495_(){return new BlockPos(m_123341_(), m_123342_() - 1, m_123343_());} public BlockPos m_6630_(int n){return new BlockPos(m_123341_(), m_123342_() + n, m_123343_());}
 public BlockPos m_121945_(Direction d){return new BlockPos(m_123341_() + d.m_122429_(), m_123342_() + d.m_122430_(), m_123343_() + d.m_122431_());} }
