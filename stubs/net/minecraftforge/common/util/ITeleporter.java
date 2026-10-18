package net.minecraftforge.common.util; import net.minecraft.world.entity.Entity; import net.minecraft.server.level.ServerLevel; import net.minecraft.world.level.portal.PortalInfo; import java.util.function.Function;
public interface ITeleporter { default PortalInfo getPortalInfo(Entity e, ServerLevel dest, Function<ServerLevel, PortalInfo> def){return null;} }
