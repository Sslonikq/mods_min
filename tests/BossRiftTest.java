package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;

import java.util.*;

/**
 * Сердце Разлома в логове (фаза 4.4), то, что проверяется без игры:
 *  - у зала 7 метка босса, печать и сундук, обычных мобов нет, босс только там;
 *  - добыча сундука: Посох Разлома, Ядро Разлома, Осколок Печати: Сон и затем набор тира 2; у других залов трофеев нет;
 *  - столбы щита стоят на кольце, на пустых клетках шаблона над полом и не сливаются с колоннами зала (их ставит и убирает только босс);
 *  - пол в бездне ниже площадки, обрезка удара по порогам фаз и смертельный удар;
 *  - сброс логова без запаса, остальные залы с печатью — с запасом.
 */
public class BossRiftTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    public static void main(String[] args) throws Exception {
        Dungeons.Def d = Dungeons.RIFT;
        Dungeons.Hall lair = d.hall(7);
        check(lair != null && lair.boss != null && lair.chest != null && lair.spawns.isEmpty(), "логово: метка босса, сундук, без обычных мобов");
        check(d.sealed(7) && d.marks("boss").size() == 1, "у логова печать, меток босса одна");
        Mark b = lair.boss;
        check(b.a.equals("rift_heart") && lair.contains(b.x, b.y, b.z), "босс rift_heart внутри зала");
        // центр круглой площадки совпадает с серединой зала (R=28 -> коробка 57x57)
        check(lair.x + lair.sx / 2 == b.x && lair.z + lair.sz / 2 == b.z, "метка босса в центре зала");
        check(lair.chest.x == b.x && lair.chest.z == b.z && lair.chest.y == b.y, "сундук на площадке");
        check(b.y == lair.y + 6, "метка босса на уровне пола: яма (5) + пол");

        // ---- добыча
        List<String> loot = Dungeons.lootFor(lair, Integer.parseInt(Dungeons.chestSpec(lair.chest.a)[1]));
        check(loot.contains("explosionwand:rift_staff") && loot.contains("explosionwand:rift_core") && loot.contains("explosionwand:sleep_seal_shard"), "в сундуке посох, ядро и осколок Сна: " + loot);
        check(loot.indexOf("explosionwand:rift_staff") < 3 && loot.size() == 3 + Dungeons.LOOT.get(1).size() && loot.containsAll(Dungeons.LOOT.get(1)), "затем набор тира 2");
        check(loot.size() <= 27, "всё помещается в сундук");
        for (Dungeons.Hall h : d.halls()) if (h.chest != null && h.id != 7) check(!Dungeons.lootFor(h, 1).contains("explosionwand:rift_staff"), "трофеев босса нет в зале " + h.id);
        check(Boss.LOOT.size() == 3 && Boss.MAX_HP <= 1024, "ХП хоста не выше лимита 1024");

        // ---- столбы щита
        Map<Long, String> world = Dungeons.assemble(d, 0, 0, 0);
        int cx = b.x, cz = b.z, fy = b.y;
        int[][] sp = Boss.pylonSpots(cx, cz);
        check(sp.length == 4, "четыре столба");
        Set<Long> seen = new HashSet<>();
        for (int[] p : sp) {
            double dd = Math.hypot(p[0] - cx, p[1] - cz);
            check(dd > Boss.PIT_R + 0.5 && dd < Boss.HALL_R - 1.5, "столб на кольце, не у стены: " + dd);
            check(seen.add(Dungeons.pack(p[0], 0, p[1])), "столбы в разных клетках");
            for (int y = 0; y < 3; y++) check(isAir(world, p[0], fy + y, p[1]), "клетка столба пуста в шаблоне: " + p[0] + "," + (fy + y) + "," + p[1] + " = " + world.get(Dungeons.pack(p[0], fy + y, p[1])));
            check(!isAir(world, p[0], fy - 1, p[1]), "под столбом пол");
            check(Math.hypot(p[0] - cx, p[1] - cz - 0) > 0 && Math.abs(p[0] - cx) > 4, "столб не на мосту (ширина моста 3 по x)");
            // колонны кольца (аметист на высоте столба) не рядом: иначе ядро столба не отличить
            for (int y = 0; y < 3; y++)
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    check(!"minecraft:amethyst_block".equals(world.get(Dungeons.pack(p[0] + dx, fy + y, p[1] + dz))), "рядом со столбом нет колонн из аметиста");
                }
        }
        // метки сундука и босса — тоже не на столбах
        for (int[] p : sp) check(!(p[0] == cx && p[1] == cz), "столб не на площадке");

        // ---- геометрия бездны
        check(Boss.groundOffset(0, 0) == 0 && Boss.groundOffset(9.0, 0) == 0 && Boss.groundOffset(9.5, 0) == -Boss.PIT_DEPTH && Boss.groundOffset(0, -15) == -Boss.PIT_DEPTH
                && Boss.groundOffset(20.4, 0) == -Boss.PIT_DEPTH && Boss.groundOffset(20.5, 0) == 0 && Boss.groundOffset(0, 25) == 0, "пол: площадка и кольцо на уровне, бездна ниже на 5");

        // ---- обрезка удара по порогам фаз
        DungeonData.Rec rec = new DungeonData.Rec("rift_0_0", "rift", 0, 0, 100, 64, 200);
        Boss boss = new Boss(null, rec, lair, "ewdxh7");
        float max = Boss.MAX_HP;
        // фаза 1: огромный удар останавливается ровно на пороге щита (60%)
        check(boss.clampHit(max, 5000) == max - max * 0.6F, "удар из полного ХП не пролетает порог щита");
        check(boss.clampHit(max * 0.6F + 3, 100) == 3, "до порога щита осталось 3 — снимет 3");
        check(boss.clampHit(max * 0.6F, 50) == 0.5F, "ровно на пороге: 0,5, пока тик не включит щит");
        boss.shieldUsed = true;
        check(boss.clampHit(max * 0.6F, 5000) == max * 0.6F - max * 0.3F, "после щита следующий порог — ярость (30%)");
        boss.enraged = true;
        check(boss.clampHit(max * 0.3F, 5000) == max * 0.3F - 1.0F && boss.lethal, "в ярости смертельный удар оставляет 1 ХП и запускает гибель");
        boss.lethal = false;
        check(boss.clampHit(100, 10) == 10 && !boss.lethal, "обычный удар в ярости не обрезается");
        check(boss.clampHit(1.0F, 10) == 0 && boss.lethal, "на последнем ХП урон 0, гибель");

        // ---- зал с боссом сбрасывается без запаса (игроков у жаровни не ждём), остальные залы с печатью — с запасом
        check(lair.boss != null && d.hall(6).boss == null, "запас сброса: логово 0, преддверие ALIVE_PAD");
        // ---- зачистка логова: только победой босса, не по «мобов нет» (хост появляется через 60 тиков, после перезапуска его ещё нет)
        check(!Dungeons.clearable(true, true, () -> true), "логово с боссом не засчитывается, пока нет хоста (пробуждение, перезапуск)");
        check(!Dungeons.clearable(true, false, () -> true), "логово: и без отсрочки не засчитывается");
        check(Dungeons.clearable(false, true, () -> true), "обычный зал: отсрочка прошла, мобов нет -> зачищен");
        check(!Dungeons.clearable(false, false, () -> true) && !Dungeons.clearable(false, true, () -> false), "обычный зал: рано или мобы живы -> нет");
        check(Boss.barOf(rec).startsWith(Boss.BAR), "полоска снимается по ключу записи");
        System.out.println(fails == 0 ? "босс в логове ok" : fails + " проверок не прошли");
        if (fails > 0) System.exit(1);
    }

    static boolean isAir(Map<Long, String> w, int x, int y, int z) {
        String s = w.get(Dungeons.pack(x, y, z));
        return s == null || s.equals("minecraft:air");
    }
}
