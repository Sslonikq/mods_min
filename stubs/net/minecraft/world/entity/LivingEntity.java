package net.minecraft.world.entity; import net.minecraft.world.*; import net.minecraft.world.item.ItemStack;
public abstract class LivingEntity extends Entity { public float f_20883_; public float f_20885_; public net.minecraft.world.item.ItemStack m_6844_(net.minecraft.world.entity.EquipmentSlot s){return null;} public ItemStack m_21120_(InteractionHand h){return null;} public void m_21011_(InteractionHand h, boolean b){}
 public float m_21223_(){return 0;} public ItemStack m_21206_(){return null;} public ItemStack m_21205_(){return null;} public void m_21153_(float f){} public float m_21233_(){return 0;}
 public boolean m_21224_(){return false;} public boolean m_21195_(net.minecraft.world.effect.MobEffect e){return false;}
 public boolean m_7292_(net.minecraft.world.effect.MobEffectInstance e){return false;} }
