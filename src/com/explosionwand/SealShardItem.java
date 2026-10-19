package com.explosionwand;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Осколок Печати: в руке тянется к ближайшему данжу своего типа (частицы + расстояние над хотбаром), рядом дрожит и звенит,
 * у двери данжа отпирает её (правый клик по двери, см. Dungeons.onRightClick).
 */
public class SealShardItem extends Item {
    final String dungeon, key;
    /** Только ключ от двери (Печать Храма): не указывает дорогу, отпирает дверь с меткой door a=dungeon. */
    final boolean doorOnly;
    public SealShardItem(Properties p, String dungeon) { this(p, dungeon, "seal_shard_" + dungeon); }
    /** key — id предмета (по нему ищется описание в lang). */
    public SealShardItem(Properties p, String dungeon, String key) { this(p, dungeon, key, false); }
    public SealShardItem(Properties p, String dungeon, String key, boolean doorOnly) { super(p); this.dungeon = dungeon; this.key = key; this.doorOnly = doorOnly; }

    /** Данжа этого типа нет в игре: осколок только сообщает, что дорога не открыта. Ключ-печать (doorOnly) дорогу не указывает и не «заперт». */
    boolean locked() { return !doorOnly && Dungeons.def(dungeon) == null; }

    @Override
    public void m_6883_(ItemStack stack, Level level, Entity e, int slot, boolean selected) {
        if (level.f_46443_ || !(level instanceof ServerLevel sl) || !(e instanceof ServerPlayer p)) return;
        if (doorOnly || !(selected || p.m_21206_() == stack)) return;
        if (locked()) {
            if (sl.m_46467_() % 40 == 0) p.m_5661_(Component.m_237115_("explosionwand.dungeon.locked." + dungeon), true);
            return;
        }
        Dungeons.pointer(sl, p, dungeon);
    }

    /** Shift + ПКМ в творческом режиме пишет в чат координаты ближайшего данжа (для проверки). */
    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.m_21120_(hand);
        if (doorOnly) return InteractionResultHolder.m_19098_(stack);   // дверь отпирает серверный RightClickBlock
        if (locked()) {
            if (!level.f_46443_) player.m_5661_(Component.m_237115_("explosionwand.dungeon.locked." + dungeon), true);
            return InteractionResultHolder.m_19092_(stack, level.f_46443_);
        }
        if (!level.f_46443_ && level instanceof ServerLevel sl && player instanceof ServerPlayer sp && sp.m_7500_() && sp.m_6144_()) {
            Dungeons.locate(sl, sp, dungeon);
            return InteractionResultHolder.m_19092_(stack, level.f_46443_);
        }
        // на сервере: вне творческого режима клик ничего не делает, если данж не рядом. На клиенте данных о данжах нет (near всегда false),
        // поэтому там рука не машет; дверь обрабатывает серверный RightClickBlock
        if (!player.m_7500_() && !Dungeons.near(level, player, 8)) return InteractionResultHolder.m_19098_(stack);
        return InteractionResultHolder.m_19092_(stack, level.f_46443_);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.m_237115_("item.explosionwand." + key + ".desc"));
    }

    @Override public boolean m_5812_(ItemStack stack) { return true; }
}
