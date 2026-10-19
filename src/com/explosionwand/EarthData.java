package com.explosionwand;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Временные блоки палочек Земли (огонёк-свет, ледяная дорожка Морозца). Хранятся в данных измерения, чтобы после выхода из игры
 * блок всё равно убрался: срок считается в игровом времени мира (Level.getGameTime).
 */
final class EarthData extends SavedData {
    static final String NAME = "explosionwand_earth";
    static final int LIGHT = 0, ICE = 1;

    static final class Temp {
        final int x, y, z, kind; final long until;
        /** Ручка огонька для хука CityMobs (не сохраняется): моль её гасит. */
        CityMobs.Light handle;
        Temp(int x, int y, int z, int kind, long until) { this.x = x; this.y = y; this.z = z; this.kind = kind; this.until = until; }
    }

    final List<Temp> temps = new ArrayList<>();

    @Override
    public CompoundTag m_7176_(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        int i = 0;
        for (Temp t : temps) {
            CompoundTag c = new CompoundTag();
            c.m_128405_("x", t.x); c.m_128405_("y", t.y); c.m_128405_("z", t.z); c.m_128405_("k", t.kind); c.m_128356_("u", t.until);
            all.m_128365_(Integer.toString(i++), c);
        }
        tag.m_128365_("temps", all);
        return tag;
    }

    static EarthData load(CompoundTag tag) {
        EarthData d = new EarthData();
        CompoundTag all = tag.m_128469_("temps");
        for (String k : all.m_128431_()) {
            CompoundTag c = all.m_128469_(k);
            d.temps.add(new Temp(c.m_128451_("x"), c.m_128451_("y"), c.m_128451_("z"), c.m_128451_("k"), c.m_128454_("u")));
        }
        return d;
    }

    static EarthData get(ServerLevel l) { return l.m_8895_().m_164861_(EarthData::load, EarthData::new, NAME); }
}
