package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TesseractWandItem extends Item {
    public TesseractWandItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl && !Dims.isOurs(sl)) {
            Aim aim = Aim.of(level, player, 32.0D);
            Vec3 c = aim.hit ? aim.target.m_82520_(0.0D, 1.8D, 0.0D) : aim.target;
            aim.beam(sl, Fx.particle("enchant"), 0.5D);
            Vec3 look = player.m_20252_(1.0F);
            Tesseract.PORTALS.add(new Tesseract(sl, c, look.f_82479_, look.f_82481_));
            player.m_36335_().m_41524_(this, 600);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
