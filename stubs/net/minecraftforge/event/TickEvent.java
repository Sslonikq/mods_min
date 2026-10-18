package net.minecraftforge.event; import net.minecraft.world.level.Level;
public class TickEvent extends net.minecraftforge.eventbus.api.Event { public final Phase phase; TickEvent(){phase=null;}
 public enum Phase { START, END }
 public static class LevelTickEvent extends TickEvent { public final Level level; LevelTickEvent(){level=null;} } }
