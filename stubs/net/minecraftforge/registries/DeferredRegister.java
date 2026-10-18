package net.minecraftforge.registries; import java.util.function.Supplier; import net.minecraftforge.eventbus.api.IEventBus;
public class DeferredRegister<T> { public static <B> DeferredRegister<B> create(IForgeRegistry<B> r, String modid){return null;}
 public static <B> DeferredRegister<B> create(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<B>> k, String modid){return null;}
 public java.util.Collection<RegistryObject<T>> getEntries(){return null;}
 public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> sup){return null;} public void register(IEventBus bus){} }
