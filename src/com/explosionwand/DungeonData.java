package com.explosionwand;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Реестр данжей в мире (хранится в обычном мире, файл data/explosionwand_dungeons.dat) и точки возрождения игроков. */
final class DungeonData extends SavedData {
    static final String NAME = "explosionwand_dungeons";

    /** Один данж в мире: где стоит, какие части построены, какие залы разбужены и зачищены. */
    static final class Rec {
        final String key, def; final int cx, cz, x, y, z;
        /** Подпись раскладки шаблонов, по которой данж строился (Def.signature). Пусто — запись из старой версии. */
        String layout = "";
        long partsDone;       // бит i — часть i построена
        int hallsSpawned;     // бит n — в зале n появились мобы
        int hallsCleared;     // бит n — зал n зачищен (мобы больше не появляются)
        /** Открытые секреты и решённые головоломки (id меток lever/mirror/secret). */
        final Set<String> opened = new LinkedHashSet<>();
        /** Повёрнутые зеркала: "x,y,z" (координаты данжа) -> сторона 0..3 (N,E,S,W); нет записи — сторона из метки. */
        final Map<String, Integer> mirrors = new HashMap<>();
        Rec(String key, String def, int cx, int cz, int x, int y, int z) {
            this.key = key; this.def = def; this.cx = cx; this.cz = cz; this.x = x; this.y = y; this.z = z;
        }
        Dungeons.Def def() { return Dungeons.def(def); }

        /**
         * Точка внутри данжа (с запасом pad): рамка как быстрый предфильтр, затем маска клеток шаблона.
         * pad 0 — точная клетка, 1..2 — куб вокруг клетки, больше — грубо (колонны 16x16 с диапазоном высот).
         */
        boolean contains(double px, double py, double pz, int pad) {
            Dungeons.Def d = def();
            if (d == null) return false;
            int[] b; Dungeons.Mask m;
            try { b = d.bounds(); m = d.mask(); } catch (IllegalStateException e) { Dungeons.noTemplate(d, e); return false; }   // нет шаблона: защита выключена, но игра не падает
            if (!(px >= x + b[0] - pad && px < x + b[3] + pad && py >= y + b[1] - pad && py < y + b[4] + pad && pz >= z + b[2] - pad && pz < z + b[5] + pad)) return false;
            int rx = (int) Math.floor(px - x), ry = (int) Math.floor(py - y), rz = (int) Math.floor(pz - z);
            if (pad <= 0) return m.has(rx, ry, rz);
            return pad <= 2 ? m.hasNear(rx, ry, rz, pad) : m.near(rx, ry, rz, pad);
        }

        /** Только рамка (с запасом pad), без маски: для грубых предфильтров. */
        boolean inBox(double px, double py, double pz, int pad) {
            Dungeons.Def d = def();
            if (d == null) return false;
            int[] b;
            try { b = d.bounds(); } catch (IllegalStateException e) { Dungeons.noTemplate(d, e); return false; }
            return px >= x + b[0] - pad && px < x + b[3] + pad && py >= y + b[1] - pad && py < y + b[4] + pad && pz >= z + b[2] - pad && pz < z + b[5] + pad;
        }
    }

    final Map<String, Rec> recs = new LinkedHashMap<>();
    /** "uuid|ключДанжа" -> "x|y|z" последней зажжённой жаровни. */
    final Map<String, String> respawn = new HashMap<>();
    /** Клетки, где данж не поставлен (все точки в воде): ключ -> 1. */
    final Map<String, Integer> dead = new HashMap<>();

    @Override
    public CompoundTag m_7176_(CompoundTag tag) {
        CompoundTag rs = new CompoundTag();
        for (Rec r : recs.values()) {
            CompoundTag t = new CompoundTag();
            t.m_128359_("def", r.def);
            t.m_128405_("cx", r.cx); t.m_128405_("cz", r.cz);
            t.m_128405_("x", r.x); t.m_128405_("y", r.y); t.m_128405_("z", r.z);
            t.m_128359_("layout", r.layout);
            t.m_128356_("parts", r.partsDone);
            t.m_128405_("spawned", r.hallsSpawned); t.m_128405_("cleared", r.hallsCleared);
            CompoundTag op = new CompoundTag();
            for (String k : r.opened) op.m_128405_(k, 1);
            t.m_128365_("opened", op);
            CompoundTag mr = new CompoundTag();
            for (Map.Entry<String, Integer> e : r.mirrors.entrySet()) mr.m_128405_(e.getKey(), e.getValue());
            t.m_128365_("mirrors", mr);
            rs.m_128365_(r.key, t);
        }
        tag.m_128365_("recs", rs);
        CompoundTag rp = new CompoundTag();
        for (Map.Entry<String, String> e : respawn.entrySet()) rp.m_128359_(e.getKey(), e.getValue());
        tag.m_128365_("respawn", rp);
        CompoundTag dd = new CompoundTag();
        for (String k : dead.keySet()) dd.m_128405_(k, 1);
        tag.m_128365_("dead", dd);
        return tag;
    }

    static DungeonData load(CompoundTag tag) {
        DungeonData d = new DungeonData();
        CompoundTag rs = tag.m_128469_("recs");
        for (String k : rs.m_128431_()) {
            CompoundTag t = rs.m_128469_(k);
            Rec r = new Rec(k, t.m_128461_("def"), t.m_128451_("cx"), t.m_128451_("cz"), t.m_128451_("x"), t.m_128451_("y"), t.m_128451_("z"));
            r.layout = t.m_128461_("layout");   // нет поля -> "" -> не совпадёт с подписью
            r.partsDone = t.m_128454_("parts");
            r.hallsSpawned = t.m_128451_("spawned"); r.hallsCleared = t.m_128451_("cleared");
            r.opened.addAll(t.m_128469_("opened").m_128431_());
            CompoundTag mr = t.m_128469_("mirrors");
            for (String mk : mr.m_128431_()) r.mirrors.put(mk, mr.m_128451_(mk) & 3);
            d.recs.put(k, r);
        }
        CompoundTag rp = tag.m_128469_("respawn");
        for (String k : rp.m_128431_()) d.respawn.put(k, rp.m_128461_(k));
        for (String k : tag.m_128469_("dead").m_128431_()) d.dead.put(k, 1);
        d.checkLayouts();
        return d;
    }

    /**
     * Записи, построенные по другой раскладке шаблонов (подпись не совпала, у старых миров её нет), сбрасываются:
     * части, залы, секреты, зеркала и точки возрождения — данж достроится заново на том же месте. Возвращает число сброшенных.
     */
    int checkLayouts() {
        int n = 0;
        for (Rec r : recs.values()) {
            Dungeons.Def def = r.def();
            if (def == null) continue;
            String sig;
            try { sig = def.signature(); } catch (IllegalStateException e) { Dungeons.noTemplate(def, e); continue; }
            if (sig.equals(r.layout)) continue;
            reset(r);
            r.layout = sig;
            n++;
        }
        if (n > 0) m_77762_();
        return n;
    }

    /** Сброс прогресса записи: данж строится заново. */
    void reset(Rec r) {
        r.partsDone = 0; r.hallsSpawned = 0; r.hallsCleared = 0;
        r.opened.clear(); r.mirrors.clear();
        String suffix = "|" + r.key;
        for (Iterator<String> it = respawn.keySet().iterator(); it.hasNext();) if (it.next().endsWith(suffix)) it.remove();
        m_77762_();
    }

    /** Данные хранятся в обычном мире, откуда бы ни спросили. */
    static DungeonData get(ServerLevel any) {
        ServerLevel ow = Dims.get(any, Level.f_46428_);
        return ow.m_8895_().m_164861_(DungeonData::load, DungeonData::new, NAME);
    }
}
