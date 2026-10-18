package net.minecraftforge.event.level; import net.minecraft.core.BlockPos;
public class BlockEvent extends net.minecraftforge.eventbus.api.Event { public BlockPos getPos(){return null;}
 public static class BreakEvent extends BlockEvent { public net.minecraft.world.entity.player.Player getPlayer(){return null;} }
 public static class EntityPlaceEvent extends BlockEvent { public net.minecraft.world.entity.Entity getEntity(){return null;} } }
