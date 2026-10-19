package net.minecraftforge.event.entity.player;
public class PlayerInteractEvent extends net.minecraftforge.eventbus.api.Event {
 public net.minecraft.world.entity.player.Player getEntity(){return null;}
 public net.minecraft.world.level.Level getLevel(){return null;}
 public net.minecraft.core.BlockPos getPos(){return null;}
 public net.minecraft.world.InteractionHand getHand(){return null;}
 public net.minecraft.world.item.ItemStack getItemStack(){return null;}
 public static class RightClickBlock extends PlayerInteractEvent {}
 public static class RightClickItem extends PlayerInteractEvent {} }
