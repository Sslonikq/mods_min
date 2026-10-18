package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public class WhaleStaffItem extends Item {
    public WhaleStaffItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (!level.f_46443_ && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            WhaleStaff.use(sl, sp, stack);
            player.m_36335_().m_41524_(this, 10);
        }
        player.m_21011_(hand, true);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237113_("ПКМ — Нырок: вынырнуть в точке взгляда").m_130940_(ChatFormatting.valueOf("AQUA")));
        lines.add(Component.m_237113_("Shift+ПКМ — Пожирание: съесть посох из левой руки").m_130940_(ChatFormatting.valueOf("AQUA")));
        CompoundTag tag = stack.m_41783_();
        if (tag != null && tag.m_128441_("Devoured")) {
            String path = tag.m_128461_("Devoured");
            lines.add(Component.m_237113_("Поглощён: ").m_130940_(ChatFormatting.valueOf("GRAY"))
                    .m_7220_(Component.m_237115_("item.explosionwand." + path).m_130940_(ChatFormatting.valueOf("GOLD")))
                    .m_7220_(Component.m_237113_(" (эхо: " + tag.m_128451_("Echoes") + ")").m_130940_(ChatFormatting.valueOf("GRAY"))));
            lines.add(Component.m_237113_("Пока есть эхо: нырок вдвое быстрее, эхо ×2,").m_130940_(ChatFormatting.valueOf("DARK_AQUA")));
            lines.add(Component.m_237113_("срабатывает при нырке и выныривании + призрачный кит").m_130940_(ChatFormatting.valueOf("DARK_AQUA")));
        } else lines.add(Component.m_237113_("Ничего не поглощено").m_130940_(ChatFormatting.valueOf("DARK_GRAY")));
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
