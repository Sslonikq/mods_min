package net.minecraftforge.registries; import net.minecraft.resources.ResourceLocation;
public interface IForgeRegistry<V> { V getValue(ResourceLocation key); ResourceLocation getKey(V value); }
