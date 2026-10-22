package net.minecraft.world.level; import net.minecraft.world.entity.Entity; import net.minecraft.world.entity.player.Player; import net.minecraft.sounds.*;
import net.minecraft.world.phys.AABB; import java.util.List; import java.util.function.Predicate; import net.minecraft.core.BlockPos; import net.minecraft.world.level.block.state.BlockState;
public abstract class Level implements LevelAccessor { public boolean f_46443_; public net.minecraft.util.RandomSource f_46441_;
 public static final net.minecraft.resources.ResourceKey<Level> f_46428_ = null;
 public net.minecraft.resources.ResourceKey<Level> m_46472_(){return null;}
 public BlockPos m_220360_(){return null;}
 public void m_6263_(Player p,double x,double y,double z,SoundEvent s,SoundSource src,float v,float pi){}
 public Explosion m_255391_(Entity e,double x,double y,double z,float pow,boolean fire,ExplosionInteraction i){return null;}
 public List<Entity> m_6249_(Entity e, AABB b, Predicate<? super Entity> p){return null;}
 public BlockState m_8055_(BlockPos p){return null;}
 public net.minecraft.world.damagesource.DamageSources m_269111_(){return null;}
 public boolean m_7731_(BlockPos p, BlockState s, int f){return false;}
 public boolean m_46597_(BlockPos p, BlockState s){return false;}
 public void m_46672_(BlockPos p, net.minecraft.world.level.block.Block b){}
 public boolean m_7967_(Entity e){return false;} public long m_46467_(){return 0;} public long m_46468_(){return 0;} public boolean m_46462_(){return false;} public boolean m_46471_(){return false;}
 public boolean m_46961_(BlockPos p, boolean drop){return false;}
 public net.minecraft.world.Difficulty m_46791_(){return null;}
 public enum ExplosionInteraction { TNT, NONE } }
