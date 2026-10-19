package net.minecraftforge.event.entity.living;
public abstract class MobSpawnEvent extends net.minecraftforge.eventbus.api.Event {
 public net.minecraft.world.entity.Mob getEntity(){return null;}
 public net.minecraft.world.level.ServerLevelAccessor getLevel(){return null;}
 public double getX(){return 0;} public double getY(){return 0;} public double getZ(){return 0;}
 public static class FinalizeSpawn extends MobSpawnEvent {
  public net.minecraft.world.entity.MobSpawnType getSpawnType(){return null;}
  public void setSpawnCancelled(boolean c){} } }
