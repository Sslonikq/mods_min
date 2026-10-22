package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * Пепельное святилище (фаза 4.8): общие правила данжа `ash` (Dungeons.ASH, tools/gen_ash.py), которых нет в общем конвейере:
 * особая добыча (ключи в сундуках залов-хранилищ), повторная выдача потерянного ключа.
 * Мобы Святилища - AshMobs/AshMobsB (4.9), у арены Хранитель Углей (метка spawn Хранителя, роняет ключ через зачистку зала); Пепельный Феникс (4.10) - Boss5, его барьер Святилища снимает победа (Dungeons.lairWon -> bossDead).
 */
final class Ash {
    private Ash() {}

    /** Зал Кладовой (ловушки уровня «Зал Пепла»): в его сундуке лежит Пепельный ключ от двери вниз (метка door a=ash_gate). */
    static final int KEY_HALL = 9;
    /** Арена Хранителя (уровень 4): в сундуке после зачистки Угольный ключ от двери Гнезда (метка door a=ember_gate). */
    static final int GUARD_HALL = 20;

    /** Ключ уровня: зал, в сундуке которого он лежит, дверь, которую он открывает (a метки door), предмет. */
    static final class KeyDef {
        final int hall; final String door, item; final Supplier<Item> stack;
        KeyDef(int hall, String door, String item, Supplier<Item> stack) { this.hall = hall; this.door = door; this.item = item; this.stack = stack; }
    }

    static final List<KeyDef> KEYS = List.of(
            new KeyDef(KEY_HALL, "ash_gate", "ash_key", () -> ExplosionWandMod.ASH_KEY.get()),
            new KeyDef(GUARD_HALL, "ember_gate", "ember_key", () -> ExplosionWandMod.EMBER_KEY.get()));

    /** Особые предметы сундука зала (кроме обычной добычи тира и материалов): ключи уровней. */
    static List<String> extra(int hall) {
        for (KeyDef k : KEYS) if (k.hall == hall) return List.of("explosionwand:" + k.item);
        return List.of();
    }

    /**
     * Приспешник Феникса (Boss5): kind 0 - Искра, 1 - Пеплоход. Настоящие мобы Святилища через ту же фабрику, что и мобы залов (RiftMobs.spawnKind):
     * теги ewd, тег зала, ключ боя и ewb5_min (убираются вместе с боссом, друг друга и босса не бьют), без материалов, поводок не пускает их далеко от точки появления.
     */
    static void minion(ServerLevel sl, int kind, double x, double y, double z, String hallTag, String ktag, String minTag) {
        RiftModels.Kind k = RiftModels.Kind.byId(AshLogic.minionKind(kind));
        RiftMobs.Mob m = RiftMobs.spawnKind(sl, k, x, y, z, hallTag, "\"" + ktag + "\",\"" + minTag + "\"");
        if (m != null) m.leash = AshLogic.minionLeash(kind);
    }

    /** Ключ выдаётся заново: зал зачищен, дверь ещё закрыта, ключа нет ни у игрока, ни в сундуке (иначе его просто возьмут из сундука). */
    static boolean reissueWanted(boolean cleared, boolean doorClosed, boolean hasKey, boolean inChest) { return cleared && doorClosed && !hasKey && !inChest; }

    /**
     * ПКМ по сундуку-хранилищу в зачищенном зале: потерянный в лаве или огне ключ возвращается (как Печать Храма, Dungeons.reissueSeal).
     * Сундук может стоять чуть в стороне от метки (Loot.chest сдвигает его по x до 3, если рядом лежит старая добыча). true - клик занят.
     */
    static boolean reissue(ServerLevel sl, DungeonData.Rec rec, ServerPlayer p, int rx, int ry, int rz) {
        Dungeons.Def d = rec.def();
        for (KeyDef k : KEYS) {
            Dungeons.Hall h = d.hall(k.hall);
            if (h == null || h.chest == null || ry != h.chest.y || rz != h.chest.z || Math.abs(rx - h.chest.x) > 3) continue;
            BlockPos pos = new BlockPos(rec.x + rx, rec.y + ry, rec.z + rz);
            if (!Loot.isChest(sl, pos)) continue;
            Mark door = null;
            for (Mark m : d.doors()) if (m.a.equals(k.door)) door = m;
            if (door == null) continue;
            Item item = k.stack.get();
            boolean closed = !sl.m_8055_(new BlockPos(rec.x + door.x, rec.y + door.y, rec.z + door.z)).m_60795_();
            boolean inChest = false;
            if (sl.m_7702_(pos) instanceof Container c)
                for (int i = 0; i < c.m_6643_(); i++) {
                    ItemStack s = c.m_8020_(i);
                    if (s != null && s.m_41720_() == item) inChest = true;
                }
            if (!reissueWanted((rec.hallsCleared & (1L << h.id)) != 0, closed, p.m_150109_().m_18947_(item) > 0, inChest)) return false;
            Cmd.run(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "give " + p.m_20148_() + " explosionwand:" + k.item);
            p.m_5661_(Component.m_237115_("explosionwand.dungeon.key_reissued"), true);
            Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.amethyst_block.chime", 1.0F, 0.7F);
            return true;
        }
        return false;
    }
}
