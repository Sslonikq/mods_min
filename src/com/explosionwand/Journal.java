package com.explosionwand;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Дневник Архимага (4.13): завязка и подсказки. Страницы открываются по прогрессу игрока; тексты - ключи
 * explosionwand.journal.pN. Прогресс хранится в getPersistentData().PlayerPersisted (переживает смерть):
 * победы над боссами Земли (ставит Dungeons.onCleared), зажжённый портал Ада (HellGate.activate).
 * Страница открыта и по трофею в инвентаре (посох босса, Ключ Ада): так не теряется прогресс, если игрока не было в зале.
 */
final class Journal {
    static final String GIVEN = "ewJournalGiven", RIFT = "ewJRift", MONOLITH = "ewJMonolith", PHOENIX = "ewJPhoenix", HELL = "ewJHell";
    static final int PAGES = 6;

    private Journal() {}

    /**
     * Какие страницы открыты (номера по возрастанию). 0 завязка и 1 первый осколок - всегда; 2 после Сердца Разлома;
     * 3 после Монолита Сна; 4 после Феникса или с Ключом Ада в руках; 5 когда портал в Ад зажжён.
     */
    static List<Integer> pages(boolean rift, boolean monolith, boolean phoenix, boolean key, boolean portal) {
        List<Integer> out = new ArrayList<>();
        out.add(0); out.add(1);
        if (rift) out.add(2);
        if (monolith) out.add(3);
        if (phoenix || key || portal) out.add(4);
        if (portal) out.add(5);
        return out;
    }

    static CompoundTag saved(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.m_128441_("PlayerPersisted")) root.m_128365_("PlayerPersisted", new CompoundTag());
        return root.m_128469_("PlayerPersisted");
    }

    static boolean flag(ServerPlayer p, String k) { return saved(p).m_128471_(k); }

    static void set(ServerPlayer p, String k) { saved(p).m_128379_(k, true); }

    /** Победа над боссом зала: boss-метка зала "monolith" / "phoenix", иначе Сердце Разлома. */
    static void won(ServerPlayer p, String bossMark) {
        set(p, "monolith".equals(bossMark) ? MONOLITH : "phoenix".equals(bossMark) ? PHOENIX : RIFT);
    }

    private static boolean has(ServerPlayer p, Item it) { return p.m_150109_().m_18947_(it) > 0; }

    static List<Integer> pagesOf(ServerPlayer p) {
        return pages(flag(p, RIFT) || has(p, ExplosionWandMod.RIFT_STAFF.get()),
                flag(p, MONOLITH) || has(p, ExplosionWandMod.DREAM_STAFF.get()),
                flag(p, PHOENIX) || has(p, ExplosionWandMod.PHOENIX_STAFF.get()),
                has(p, ExplosionWandMod.HELL_KEY.get()),
                flag(p, HELL));
    }

    /** ПКМ: открытые страницы в чат. */
    static void show(ServerPlayer p) {
        p.m_5661_(Component.m_237115_("explosionwand.journal.title").m_130940_(ChatFormatting.valueOf("GOLD")), false);
        List<Integer> open = pagesOf(p);
        for (int n : open) {
            p.m_5661_(Component.m_237115_("explosionwand.journal.p" + n).m_130940_(ChatFormatting.valueOf(n == 0 ? "GRAY" : "YELLOW")), false);
        }
        if (open.size() < PAGES) p.m_5661_(Component.m_237115_("explosionwand.journal.more").m_130940_(ChatFormatting.valueOf("DARK_GRAY")), false);
    }

    /** Первый вход в мир: выдать Дневник один раз (флаг в PlayerPersisted); нет места в инвентаре - упадёт под ноги. */
    static void onLogin(PlayerEvent.PlayerLoggedInEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || flag(p, GIVEN)) return;
        set(p, GIVEN);
        ItemStack book = new ItemStack(ExplosionWandMod.ARCHMAGE_JOURNAL.get());
        if (!p.m_150109_().m_36054_(book)) p.m_19983_(book);
        p.m_5661_(Component.m_237115_("explosionwand.journal.given"), false);
    }
}
