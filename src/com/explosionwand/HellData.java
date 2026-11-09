package com.explosionwand;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Данные Ада (5.1): высота пола площадки у обратного портала. Ад теперь настоящий generated-мир, поэтому высота не константа:
 * вычисляется при первом входе (HellGate.pickFloor) и хранится в обычном мире (файл data/explosionwand_hell.dat). 0 - ещё не определена.
 */
final class HellData extends SavedData {
    static final String NAME = "explosionwand_hell";

    /** Y верхнего блока пола площадки (ноги игрока стоят на floorY + 1). */
    int floorY;

    @Override
    public CompoundTag m_7176_(CompoundTag tag) {
        tag.m_128405_("floorY", floorY);
        return tag;
    }

    static HellData load(CompoundTag tag) {
        HellData d = new HellData();
        d.floorY = tag.m_128451_("floorY");
        return d;
    }

    void set(int y) { floorY = y; m_77762_(); }

    static HellData get(ServerLevel any) {
        ServerLevel ow = Dims.get(any, Level.f_46428_);
        return ow.m_8895_().m_164861_(HellData::load, HellData::new, NAME);
    }
}
