package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Ключ Ада (4.13): ПКМ по гнезду из Углей Феникса в рамке Печатного камня зажигает портал навсегда; ключ остаётся в алтаре (тратится). */
public class HellKeyItem extends Item {
    public HellKeyItem(Properties p) { super(p); }

    @Override
    public InteractionResult m_6225_(UseOnContext ctx) {
        Level level = ctx.m_43725_();
        BlockPos pos = ctx.m_8083_();
        if (!(ctx.m_43723_() instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) {
            var cs = level.m_8055_(pos);
            return HellGate.is(cs, ExplosionWandMod.HELL_SOCKET.get()) || HellGate.is(cs, ExplosionWandMod.PHOENIX_EMBERS.get()) ? InteractionResult.valueOf("SUCCESS") : InteractionResult.valueOf("PASS");
        }
        var st = sl.m_8055_(pos);
        if (HellGate.is(st, ExplosionWandMod.HELL_SOCKET.get())) { p.m_5661_(Component.m_237115_("explosionwand.hellgate.already"), true); return InteractionResult.valueOf("CONSUME"); }
        if (!HellGate.is(st, ExplosionWandMod.PHOENIX_EMBERS.get())) return InteractionResult.valueOf("PASS");
        if (Dims.isOurs(sl)) { p.m_5661_(Component.m_237115_("explosionwand.hellgate.here"), true); return InteractionResult.valueOf("CONSUME"); }
        if (HellGate.activate(sl, pos, p) && !p.m_7500_()) ctx.m_43722_().m_41774_(1);
        return InteractionResult.valueOf("CONSUME");
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.m_237115_("item.explosionwand.hell_key.desc").m_130940_(ChatFormatting.valueOf("GRAY")));
    }
}
