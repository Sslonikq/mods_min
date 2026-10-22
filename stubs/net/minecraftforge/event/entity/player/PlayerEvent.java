package net.minecraftforge.event.entity.player;
public class PlayerEvent extends net.minecraftforge.eventbus.api.Event {
 public net.minecraft.world.entity.player.Player getEntity(){return null;}
 public static class PlayerRespawnEvent extends PlayerEvent {}
 public static class PlayerLoggedOutEvent extends PlayerEvent {}
 public static class PlayerLoggedInEvent extends PlayerEvent {} }
