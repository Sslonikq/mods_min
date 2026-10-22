package net.minecraftforge.event.entity.living; import net.minecraft.world.entity.LivingEntity;
public class LivingEvent extends net.minecraftforge.eventbus.api.Event { public LivingEntity getEntity(){return null;}
 public static class LivingVisibilityEvent extends LivingEvent { public void modifyVisibility(double m){} public double getVisibilityModifier(){return 1;} } }
