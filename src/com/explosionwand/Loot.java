package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

import java.util.List;

/** Ставит сундук с добычей (через серверные команды — без прямого NBT API). */
final class Loot {
    /** items: массив строк вида "4 minecraft:diamond" или "minecraft:netherite_ingot". */
    static void chest(ServerLevel level, int x, int y, int z, String name, List<String> items) {
        // старый (несобранный) сундук не затираем: новый ставим в ближайшую свободную клетку рядом по X;
        // пустой (уже обчищенный) сундук считается свободной клеткой и перезаписывается
        for (int dx : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            if (!isFilledChest(level, new BlockPos(x + dx, y, z))) { x += dx; break; }
        }
        pillar(level, x, y, z);
        Cmd.run(level, x, y, z, "setblock " + x + " " + y + " " + z + " minecraft:air");
        Cmd.run(level, x, y, z, "setblock " + x + " " + y + " " + z
                + " minecraft:chest{CustomName:'{\"text\":\"" + name + "\",\"color\":\"gold\",\"bold\":true}'}");
        int slot = 0;
        for (String it : items) {
            if (slot >= 27) break;
            // "4 minecraft:diamond", "minecraft:golden_apple" или предмет с NBT (в нём бывают пробелы): число впереди — количество
            String id = it.trim(); int count = 1;
            int sp = id.indexOf(' ');
            if (sp > 0 && id.substring(0, sp).matches("\\d+")) { count = Integer.parseInt(id.substring(0, sp)); id = id.substring(sp + 1).trim(); }
            Cmd.run(level, x, y, z, "item replace block " + x + " " + y + " " + z + " container." + slot + " with " + id + " " + count);
            slot++;
        }
        // маяк-луч, чтобы сундук было видно
        Fx.send(level, Fx.particle("end_rod"), x + 0.5, y + 1, z + 0.5, 30, 0.3, 1.5, 0.3, 0.05);
        Fx.sound(level, x, y, z, "block.chest.open", 2.0F, 0.8F);
    }

    /** Клетка занята сундуком (добыча): перестройка арены не должна её затирать. */
    static boolean isChest(ServerLevel level, BlockPos pos) {
        return level.m_8055_(pos).m_60734_() == Fx.state("chest").m_60734_();
    }

    /** Сундук, в котором ещё лежит добыча (пустой — не считается). */
    static boolean isFilledChest(ServerLevel level, BlockPos pos) {
        if (!isChest(level, pos)) return false;
        Object be = level.m_7702_(pos);
        return !(be instanceof Container c) || !c.m_7983_();
    }

    static void pillar(ServerLevel level, int x, int y, int z) {
        Cmd.run(level, x, y, z, "setblock " + x + " " + (y - 1) + " " + z + " minecraft:glowstone");
    }
}
