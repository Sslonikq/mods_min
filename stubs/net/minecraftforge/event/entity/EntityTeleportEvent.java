package net.minecraftforge.event.entity;
public class EntityTeleportEvent extends net.minecraftforge.eventbus.api.Event {
 public net.minecraft.world.entity.Entity getEntity(){return null;}
 public double getTargetX(){return 0;} public double getTargetY(){return 0;} public double getTargetZ(){return 0;}
 public static class EnderPearl extends EntityTeleportEvent {}
 public static class ChorusFruit extends EntityTeleportEvent {} }
