package com.explosionwand;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;

/**
 * Данжи в обычном мире честные: блоки внутри границ не ломаются и не ставятся (кроме творческого режима), взрывы их не рушат,
 * эндер-жемчуг, плод хоруса и палочки с телепортом или перестройкой местности не работают. Элитры гасит Dungeons.tick.
 * Свои эффекты данжа идут через setBlock/команды и этих событий не вызывают.
 */
final class DungeonGuard {
    private DungeonGuard() {}

    static void onBreak(BlockEvent.BreakEvent ev) {
        if (!(ev.getPlayer() instanceof ServerPlayer p) || p.m_7500_()) return;
        if (Dungeons.inside(p.m_9236_(), ev.getPos())) ev.setCanceled(true);
    }

    static void onPlace(BlockEvent.EntityPlaceEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || p.m_7500_()) return;
        if (Dungeons.inside(p.m_9236_(), ev.getPos())) ev.setCanceled(true);
    }

    static void onDetonate(ExplosionEvent.Detonate ev) {
        if (ev.getLevel() instanceof ServerLevel sl && Dims.is(sl, net.minecraft.world.level.Level.f_46428_)) {
            DungeonData data = DungeonData.get(sl);
            if (data.recs.isEmpty()) return;
            ev.getAffectedBlocks().removeIf(pos -> Dungeons.at(data, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), 0) != null);
        }
    }

    private static void teleport(EntityTeleportEvent ev) {
        Entity e = ev.getEntity();
        if (!(e instanceof ServerPlayer p) || p.m_7500_()) return;
        boolean here = Dungeons.at(p.m_9236_(), p.m_20185_(), p.m_20186_(), p.m_20189_()) != null;
        boolean there = Dungeons.at(p.m_9236_(), ev.getTargetX(), ev.getTargetY(), ev.getTargetZ()) != null;
        if (here || there) {
            ev.setCanceled(true);
            p.m_5661_(Component.m_237115_("explosionwand.dungeon.no_teleport"), true);
        }
    }

    static void onPearl(EntityTeleportEvent.EnderPearl ev) { teleport(ev); }
    static void onChorus(EntityTeleportEvent.ChorusFruit ev) { teleport(ev); }

    /** Палочки и посохи, которые телепортируют, открывают порталы или правят блоки напрямую (взрывные работают: блоки держит onDetonate). */
    static boolean forbidden(Item i) {
        return i instanceof PortalWandItem || i instanceof PortalGunItem || i instanceof TesseractWandItem || i instanceof MimicWandItem
                || i instanceof PossessWandItem || i instanceof RiftWandItem || i instanceof BlackHoleWandItem || i instanceof IceStormWandItem
                || i instanceof ChronosWandItem || i instanceof SkySealItem || i instanceof EclipseSealItem || i instanceof DreamSealItem
                || i instanceof RiftKeyItem || i instanceof EclipseStaffItem || i instanceof WhaleStaffItem;
    }

    static void onUseItem(PlayerInteractEvent.RightClickItem ev) {
        if (ev.getLevel().f_46443_ || !(ev.getEntity() instanceof ServerPlayer p) || p.m_7500_()) return;
        if (!forbidden(ev.getItemStack().m_41720_())) return;
        if (Dungeons.at(p.m_9236_(), p.m_20185_(), p.m_20186_(), p.m_20189_()) == null) return;
        ev.setCanceled(true);
        p.m_5661_(Component.m_237115_("explosionwand.dungeon.no_wand"), true);
    }
}
