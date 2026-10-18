package mezz.jei.api;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
public interface IModPlugin {
    ResourceLocation getPluginUid();
    default void registerRecipes(IRecipeRegistration registration) {}
}
