package net.minecraftforge.event.level;
public class ExplosionEvent extends net.minecraftforge.eventbus.api.Event {
 public net.minecraft.world.level.Level getLevel(){return null;}
 public net.minecraft.world.level.Explosion getExplosion(){return null;}
 public static class Detonate extends ExplosionEvent { public java.util.List<net.minecraft.core.BlockPos> getAffectedBlocks(){return null;} } }
