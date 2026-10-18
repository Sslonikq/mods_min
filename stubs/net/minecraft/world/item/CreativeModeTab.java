package net.minecraft.world.item; import net.minecraft.world.level.ItemLike;
public class CreativeModeTab {
  public interface Output { default void m_246326_(ItemLike i){} }
  public static class ItemDisplayParameters {}
  public interface DisplayItemsGenerator { void m_257865_(ItemDisplayParameters p, Output o); }
  public static Builder builder(){return null;}
  public static class Builder {
    public Builder m_257941_(net.minecraft.network.chat.Component c){return this;}
    public Builder m_257737_(java.util.function.Supplier<ItemStack> s){return this;}
    public Builder m_257501_(DisplayItemsGenerator g){return this;}
    public CreativeModeTab m_257652_(){return null;}
  }
}
