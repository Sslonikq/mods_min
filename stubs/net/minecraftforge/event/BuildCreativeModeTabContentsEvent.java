package net.minecraftforge.event; import net.minecraft.resources.ResourceKey; import net.minecraft.world.item.CreativeModeTab;
public class BuildCreativeModeTabContentsEvent extends net.minecraftforge.eventbus.api.Event implements CreativeModeTab.Output { public ResourceKey<CreativeModeTab> getTabKey(){return null;} }
