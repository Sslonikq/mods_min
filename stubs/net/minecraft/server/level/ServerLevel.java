package net.minecraft.server.level; import net.minecraft.core.particles.ParticleOptions; import java.util.List;
public abstract class ServerLevel extends net.minecraft.world.level.Level {
 public <T extends ParticleOptions> int m_8767_(T t,double x,double y,double z,int c,double a,double b,double d,double s){return 0;}
 public <T extends ParticleOptions> boolean m_8624_(ServerPlayer p,T t,boolean f,double x,double y,double z,int c,double a,double b,double d,double s){return false;}
 public List<ServerPlayer> m_6907_(){return null;}
 public net.minecraft.server.MinecraftServer m_7654_(){return null;}
 public net.minecraft.world.level.storage.DimensionDataStorage m_8895_(){return null;}
 public long m_7328_(){return 0;}
 public boolean m_143319_(long chunk){return false;}
 public Iterable<net.minecraft.world.entity.Entity> m_8583_(){return null;} }
