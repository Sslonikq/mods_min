package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
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
        // ядро столба щита Сердца Разлома ломается (иначе щит не снять); остальное в данже неприкосновенно
        if (!Dungeons.inside(p.m_9236_(), ev.getPos())) return;
        if (p.m_9236_() instanceof ServerLevel sl && Boss.isPylonCore(sl, ev.getPos())) {
            // ядро убираем сами, без дропа: аметист со столбов не фармится (бой можно сбросить и начать заново)
            sl.m_7731_(ev.getPos(), Fx.state("air"), 3);
            Fx.sound(sl, ev.getPos().m_123341_() + 0.5, ev.getPos().m_123342_() + 0.5, ev.getPos().m_123343_() + 0.5, "block.amethyst_block.break", 1.0F, 1.0F);
        }
        ev.setCanceled(true);
    }

    static void onPlace(BlockEvent.EntityPlaceEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || p.m_7500_()) return;
        if (Dungeons.inside(p.m_9236_(), ev.getPos())) ev.setCanceled(true);
    }

    /** Ведро воды/лавы (и черпание) с целью в данже или рядом с его границей: Forge шлёт FillBucketEvent, а не EntityPlaceEvent. */
    static void onBucket(FillBucketEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || p.m_7500_()) return;
        if (!(ev.getTarget() instanceof BlockHitResult hit)) return;
        BlockPos pos = hit.m_82425_();
        // запас 1: вода ставится в соседний с кликом блок
        if (Dungeons.near(p.m_9236_(), pos.m_123341_() + 0.5, pos.m_123342_() + 0.5, pos.m_123343_() + 0.5, 1)) ev.setCanceled(true);
    }

    /** Естественный спавн, подкрепления и патрули в границах данжа отменяются; мобы залов идут через /summon и сюда не попадают. */
    static void onSpawn(MobSpawnEvent.FinalizeSpawn ev) {
        try {
            String type = ev.getSpawnType().name();
            // CHUNK_GENERATION не трогаем: он приходит из потока генерации чанков (там нельзя читать реестр данжей), а спавнит только животных
            if (!type.equals("NATURAL") && !type.equals("REINFORCEMENT") && !type.equals("PATROL") && !type.equals("JOCKEY")) return;
            if (ev.getEntity().m_19880_().contains("ewd")) return;
            if (Dungeons.at(ev.getLevel().m_6018_(), ev.getX(), ev.getY(), ev.getZ()) != null) {
                try { ev.setSpawnCancelled(true); }
                catch (LinkageError e) { ev.setCanceled(true); }   // запасной путь, если нет setSpawnCancelled: только пропустит finalizeSpawn, моба не уберёт
            }
        } catch (LinkageError | RuntimeException e) {   // исключение в обработчике ушло бы дальше в натуральный спавн в тике
            if (!spawnWarned) { spawnWarned = true; System.err.println("[explosionwand] защита от спавна в данжах отключена: " + e); }
        }
    }

    private static boolean spawnWarned = false;

    static void onDetonate(ExplosionEvent.Detonate ev) {
        if (ev.getLevel() instanceof ServerLevel sl && Dims.is(sl, net.minecraft.world.level.Level.f_46428_)) {
            DungeonData data = DungeonData.get(sl);
            if (data.recs.isEmpty()) return;
            java.util.List<BlockPos> cores = new java.util.ArrayList<>();
            ev.getAffectedBlocks().removeIf(pos -> {
                if (Dungeons.at(data, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), 0) == null) return false;
                if (Boss.isPylonCore(sl, pos)) cores.add(pos.m_7949_());   // ядро столба: убираем сами, без дропа
                return true;
            });
            for (BlockPos c : cores) {
                sl.m_7731_(c, Fx.state("air"), 3);
                Fx.sound(sl, c.m_123341_() + 0.5, c.m_123342_() + 0.5, c.m_123343_() + 0.5, "block.amethyst_block.break", 1.0F, 1.0F);
            }
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
                || i instanceof RiftWandItem || i instanceof BlackHoleWandItem || i instanceof IceStormWandItem
                || i instanceof ChronosWandItem || i instanceof SkySealItem || i instanceof EclipseSealItem || i instanceof DreamSealItem
                || i instanceof EclipseStaffItem || i instanceof WhaleStaffItem || i instanceof RiftStaffItem
                || (i instanceof EarthWandItem w && w.kind.noDungeon);
    }

    static void onUseItem(PlayerInteractEvent.RightClickItem ev) {
        if (ev.getLevel().f_46443_ || !(ev.getEntity() instanceof ServerPlayer p) || p.m_7500_()) return;
        if (!forbidden(ev.getItemStack().m_41720_())) return;
        if (Dungeons.at(p.m_9236_(), p.m_20185_(), p.m_20186_(), p.m_20189_()) == null) return;
        ev.setCanceled(true);
        p.m_5661_(Component.m_237115_("explosionwand.dungeon.no_wand"), true);
    }
}
