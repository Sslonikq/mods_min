package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PortalWandItem extends Item {
    final Portal.Kind kind;
    public PortalWandItem(Properties p, Portal.Kind kind) { super(p); this.kind = kind; }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl) {
            if (Dims.isOurs(sl)) {   // из наших миров порталы не открываются
                Fx.sound(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(), "block.fire.extinguish", 1.0F, 0.5F);
                return InteractionResultHolder.m_19092_(stack, false);
            }
            Aim aim = Aim.of(level, player, 48.0D);
            Vec3 c = aim.hit ? aim.target.m_82520_(0.0D, 2.5D, 0.0D) : aim.target;
            aim.beam(sl, Fx.particle(kind == Portal.Kind.INVERTED ? "end_rod" : "smoke"), 0.5D);
            Vec3 look = player.m_20252_(1.0F);
            Portal.ACTIVE.add(new Portal(sl, c, look.f_82479_, look.f_82481_, player, kind));
            player.m_36335_().m_41524_(this, 400);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
