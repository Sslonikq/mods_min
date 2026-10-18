package mezz.jei.api.registration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
public interface IRecipeRegistration {
    void addItemStackInfo(ItemStack itemStack, Component... description);
    void addItemStackInfo(List<ItemStack> itemStacks, Component... description);
}
