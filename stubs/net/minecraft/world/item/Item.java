package net.minecraft.world.item;
import net.minecraft.world.*; import net.minecraft.world.level.*; import net.minecraft.world.entity.player.Player;
public class Item implements ItemLike {
  public Item(Properties p){}
  public InteractionResultHolder<ItemStack> m_7203_(Level l, Player p, InteractionHand h){return null;}
  public boolean m_5812_(ItemStack s){return false;}
  public void m_7373_(ItemStack s, Level l, java.util.List<net.minecraft.network.chat.Component> c, TooltipFlag f){}
  public void m_6883_(ItemStack s, Level l, net.minecraft.world.entity.Entity e, int slot, boolean selected){}
  public static class Properties { public Properties(){} public Properties m_41487_(int i){return this;} public Properties m_41497_(Rarity r){return this;} }
}
