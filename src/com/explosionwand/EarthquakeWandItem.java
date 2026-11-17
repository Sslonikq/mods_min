package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Палочка Землетрясения: ПКМ - землетрясение в точке прицела (до 48 блоков), Shift+ПКМ - вокруг себя. Логика - Quake. */
public class EarthquakeWandItem extends Item {
    static final double RANGE = 48.0D;
    static final int COOLDOWN = 300;

    public EarthquakeWandItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            Vec3 look = player.m_20252_(1.0F);
            double angle = Math.atan2(look.f_82481_, look.f_82479_);
            boolean self = player.m_6144_();
            Vec3 c = player.m_20182_();
            if (!self) {
                Aim aim = Aim.of(level, player, RANGE);
                if (aim.hit) { c = aim.target; aim.beam(sl, Fx.particle("crit"), 0.6D); }
                else self = true;                                    // в небо - трясёт под ногами
            }
            Fx.sound(sl, player.m_20185_(), player.m_20186_(), player.m_20189_(), "block.anvil.land", 1.0F, 0.5F);
            Quake.ACTIVE.add(new Quake(sl, sp, c.f_82479_, c.f_82481_, angle, self));
            player.m_36335_().m_41524_(this, COOLDOWN);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237115_("item.explosionwand.earthquake_wand.desc1").m_130940_(ChatFormatting.valueOf("GRAY")));
        lines.add(Component.m_237115_("item.explosionwand.earthquake_wand.desc2").m_130940_(ChatFormatting.valueOf("GRAY")));
    }
}
