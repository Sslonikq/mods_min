package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public class EclipseSealItem extends Item {
    public EclipseSealItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl) {
            if (Dims.isOurs(sl)) return InteractionResultHolder.m_19092_(stack, false);
            Aim aim = Aim.of(sl, player, 8.0D);
            if (aim.block == null || !aim.hit || aim.block.m_6662_() == net.minecraft.world.phys.HitResult.Type.valueOf("MISS")) {
                Fx.sound(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(), "block.fire.extinguish", 0.6F, 1.5F);
                return InteractionResultHolder.m_19092_(stack, false);
            }
            var bp = aim.block.m_82425_();
            Altar.ACTIVE.add(new Altar(sl, bp.m_123341_(), bp.m_123342_() + 1, bp.m_123343_(), true));
            player.m_36335_().m_41524_(this, 600);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
