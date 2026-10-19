package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;

/**
 * Арены боссов (ECLIPSE, DREAM, SKY) нельзя ломать и застраивать (кроме творческого режима),
 * взрывы в них не разрушают блоки. Эффекты боссов идут через команды setblock/fill — этих событий не вызывают.
 */
final class ArenaGuard {
    private ArenaGuard() {}

    static boolean isArena(ServerLevel l) {
        return Dims.is(l, Dims.ECLIPSE) || Dims.is(l, Dims.DREAM) || Dims.is(l, Dims.SKY);
    }

    static void onBreak(BlockEvent.BreakEvent ev) {
        if (!(ev.getPlayer() instanceof ServerPlayer p) || p.m_7500_()) return;
        if (p.m_9236_() instanceof ServerLevel sl && isArena(sl)) ev.setCanceled(true);
    }

    static void onPlace(BlockEvent.EntityPlaceEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || p.m_7500_()) return;
        if (p.m_9236_() instanceof ServerLevel sl && isArena(sl)) ev.setCanceled(true);
    }

    static void onDetonate(ExplosionEvent.Detonate ev) {
        if (ev.getLevel() instanceof ServerLevel sl && isArena(sl)) {
            ev.getAffectedBlocks().clear();
        }
    }
}
