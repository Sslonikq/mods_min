package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Цитадель Затмения (5.3), чистая логика на заглушках: потоковый разбор .nbt (равен разбору через Map), Def CITADEL и метки шаблонов
 * (двери, маяки, лампы, часы, камни - блоки под метками, куда бьёт код), поиск места, головоломка часов, ресурсы (lang, предметы, версия).
 */
public class CitadelTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    static String read(String path) throws IOException { return Files.readString(Path.of(path)); }

    /** Блок шаблона по координатам данжа (по частям); null - не записан (воздух вне комнат). */
    static String blockAt(List<DungeonTemplate> ts, int x, int y, int z) {
        for (DungeonTemplate t : ts) {
            int lx = x - t.ox, ly = y - t.oy, lz = z - t.oz;
            if (lx < 0 || ly < 0 || lz < 0 || lx >= t.sx || ly >= t.sy || lz >= t.sz) continue;
            for (int i = 0; i < t.count(); i++) if (t.bx[i] == lx && t.by[i] == ly && t.bz[i] == lz) return t.palette[t.bs[i]];
        }
        return null;
    }

    /** Блоки по набору позиций за один проход (быстрее, чем blockAt по одной): ключ x,y,z -> строка состояния. */
    static Map<String, String> blocksAt(List<DungeonTemplate> ts, Set<String> want) {
        Map<String, String> out = new HashMap<>();
        for (DungeonTemplate t : ts)
            for (int i = 0; i < t.count(); i++) {
                String k = (t.bx[i] + t.ox) + "," + (t.by[i] + t.oy) + "," + (t.bz[i] + t.oz);
                if (want.contains(k)) out.put(k, t.palette[t.bs[i]]);
            }
        return out;
    }

    static String key(Mark m, int dy) { return m.x + "," + (m.y + dy) + "," + m.z; }

    public static void main(String[] args) throws Exception {
        // ---------- потоковый разбор равен разбору через Map
        DungeonTemplate viaMap, viaStream;
        try (InputStream in = DungeonTemplate.open("citadel_p6")) { viaMap = DungeonTemplate.parse("citadel_p6", Nbt.readGz(in)); }
        try (InputStream in = DungeonTemplate.open("citadel_p6")) { viaStream = DungeonTemplate.parseStream("citadel_p6", in); }
        check(viaMap.count() == viaStream.count() && viaMap.count() > 100000, "потоковый разбор: число блоков " + viaStream.count() + " / " + viaMap.count());
        check(Arrays.equals(viaMap.bx, viaStream.bx) && Arrays.equals(viaMap.by, viaStream.by) && Arrays.equals(viaMap.bz, viaStream.bz) && Arrays.equals(viaMap.bs, viaStream.bs), "потоковый разбор: массивы совпадают");
        check(Arrays.equals(viaMap.palette, viaStream.palette) && viaMap.marks.size() == viaStream.marks.size() && viaMap.ox == viaStream.ox && viaMap.sz == viaStream.sz, "потоковый разбор: палитра, метки, размеры");

        // ---------- Def CITADEL
        Dungeons.Def d = Dungeons.CITADEL;
        check(Dungeons.def("citadel") == d && d.hell && d.passive && d.clearR == Citadel.CLEAR_R && d.clearH == Citadel.CLEAR_H, "Def: id, Ад, passive, расчистка");
        check(d.parts.length == 6 && d.allParts() == (63L | Dungeons.CLEAR_BIT), "Def: 6 частей, бит расчистки в allParts");
        check(Dungeons.DEFS.length == 4, "DEFS: 4 данжа");
        check(d.signature().equals("citadel_p1+citadel_p2+citadel_p3+citadel_p4+citadel_p5+citadel_p6:v1"), "подпись раскладки без чтения шаблонов: " + d.signature());
        List<DungeonTemplate> ts = d.templates();
        int total = 0;
        for (DungeonTemplate t : ts) total += t.count();
        check(total == 1485126, "блоков в шаблонах: " + total);
        int[] b = d.bounds();
        check(b[0] >= -116 && b[3] <= 117 && b[2] >= -116 && b[5] <= 117 && b[0] < -112 && b[3] > 112 && b[1] == -7, "габарит: " + Arrays.toString(b));
        check(d.start() != null && d.start().x == -97 && d.start().y == 0 && d.start().z == -56, "метка start");
        check(d.hall(21) != null && d.hall(21).boss == null && d.marks("boss").size() == 1, "метка boss не привязана к залу (passive), но есть в byType");
        check(d.halls().size() >= 22 && d.hall(17) != null && d.sealed(17) && d.sealed(18) && d.sealed(19) && d.sealed(21), "залы 17-21 с печатями");
        check(d.mask().count() > 1400000, "маска данжа: " + d.mask().count());

        // двери
        Set<String> doors = new TreeSet<>();
        for (Mark m : d.doors()) doors.add(m.a);
        check(doors.equals(new TreeSet<>(List.of("gate1", "gate2", "sun_e", "sun_w", "prethrone", "throne_seal"))), "двери: " + doors);
        check(d.marks("beacon").size() == 3 && d.marks("moonstone").size() == 3 && d.marks("moon_socket").size() == 3, "маяки, камни, сокеты по 3");
        check(d.marks("lamp").size() == 33 && d.marks("sun_mirror").size() == 4 && d.marks("sun_plate").size() == 4 && d.marks("sun_src").size() == 1, "лампы 33, часы 4+4+1");
        check(d.braziers().size() == 3 && d.marks("seal").size() == 4 && d.marks("secret").size() >= 2, "жаровни 3, печати 4, секреты");

        // блоки под метками
        Set<String> want = new HashSet<>();
        for (Mark m : d.marks("lamp")) want.add(key(m, 0));
        for (Mark m : d.marks("beacon")) want.add(key(m, 0));
        for (Mark m : d.marks("sun_mirror")) want.add(key(m, 0));
        for (Mark m : d.marks("sun_plate")) want.add(key(m, 0));
        for (Mark m : d.marks("moonstone")) { want.add(key(m, 0)); want.add(key(m, -1)); }
        for (Mark m : d.marks("moon_socket")) { want.add(key(m, 0)); want.add(key(m, -1)); }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) want.add((35 + dx) + ",65," + dz);
        want.add("35,67,0"); want.add("35,68,0");
        Map<String, String> blk = blocksAt(ts, want);
        for (Mark m : d.marks("lamp")) check("minecraft:amethyst_cluster[facing=up]".equals(blk.get(key(m, 0))), "лампа " + m.a + ": заглушка на метке, а там " + blk.get(key(m, 0)));
        for (Mark m : d.marks("beacon")) check("minecraft:soul_campfire[lit=false]".equals(blk.get(key(m, 0))), "маяк " + m.a + ": костёр на метке, а там " + blk.get(key(m, 0)));
        for (Mark m : d.marks("sun_mirror")) check("minecraft:amethyst_block".equals(blk.get(key(m, 0))), "зеркало " + m.a + ": блок на метке");
        for (Mark m : d.marks("sun_plate")) check("minecraft:gold_block".equals(blk.get(key(m, 0))), "печать часов " + m.a + ": золотой блок на метке, а там " + blk.get(key(m, 0)));
        for (Mark m : d.marks("moonstone")) check(blk.get(key(m, 0)) == null && "minecraft:crying_obsidian".equals(blk.get(key(m, -1))), "камень " + m.a + ": воздух над плачущим обсидианом, а там " + blk.get(key(m, 0)) + " / " + blk.get(key(m, -1)));
        for (Mark m : d.marks("moon_socket")) check(blk.get(key(m, 0)) == null && blk.get(key(m, -1)) != null, "сокет " + m.a + ": воздух над постаментом");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) check(blk.get((35 + dx) + ",65," + dz) != null, "парапет (y 65) под основанием маяка " + (35 + dx) + "," + dz);
        check(blk.get("35,67,0") == null && blk.get("35,68,0") == null, "над основанием маяка свободно");
        // над маяком до верха шаблона пусто (луч в небо)
        Map<String, String> col = new HashMap<>();
        Set<String> above = new HashSet<>();
        for (int y = 67; y < 76; y++) above.add("35," + y + ",0");
        col = blocksAt(ts, above);
        check(col.isEmpty(), "столб света свободен: " + col);
        // нужные доли: лампа tower3, мобов-колосса метка
        boolean tower3 = false;
        for (Mark m : d.marks("lamp")) if (m.a.equals("tower3")) tower3 = true;
        check(tower3, "метка lamp tower3");
        check(!d.marks("mob").isEmpty() && d.marks("spawn").isEmpty(), "метки mob остались маркерами (spawn нет: ванильного спавна нет)");

        // зеркала часов в одну линию, печати в колонках
        List<Mark> mir = new ArrayList<>(d.marks("sun_mirror")), pl = new ArrayList<>(d.marks("sun_plate"));
        mir.sort(Comparator.comparing(m -> m.a)); pl.sort(Comparator.comparing(m -> m.a));
        boolean line = true;
        for (int i = 0; i < 4; i++) {
            line &= mir.get(i).z == mir.get(0).z && mir.get(i).y == mir.get(0).y && mir.get(i).x == pl.get(i).x && pl.get(i).a.startsWith((i + 1) + ",");
            line &= (pl.get(i).a.endsWith("N") && pl.get(i).z < mir.get(i).z) || (pl.get(i).a.endsWith("S") && pl.get(i).z > mir.get(i).z);
        }
        check(line, "зеркала на одной линии, печати в своих колонках, сторона совпадает с положением");

        // ---------- головоломка часов
        int[] sides = {Citadel.N, Citadel.S, Citadel.N, Citadel.S};
        int solutions = 0;
        for (int c = 0; c < 256; c++) {
            int[] dirs = {c & 3, (c >> 2) & 3, (c >> 4) & 3, (c >> 6) & 3};
            if (Citadel.all(Citadel.sunLit(dirs, sides))) { solutions++; check(Arrays.equals(dirs, sides), "решение только одно"); }
        }
        check(solutions == 1, "решений часов ровно одно: " + solutions);
        boolean[] none = Citadel.sunLit(new int[]{Citadel.E, Citadel.E, Citadel.E, Citadel.E}, sides);
        check(!none[0] && !none[1] && !none[2] && !none[3], "все на восток: ничего не горит");
        boolean[] cut = Citadel.sunLit(new int[]{Citadel.N, Citadel.W, Citadel.N, Citadel.S}, sides);
        check(cut[0] && !cut[1] && !cut[2] && !cut[3], "зеркало на запад гасит луч за собой");
        check(Citadel.stoneReady(1, false) && Citadel.stoneReady(2, false) && !Citadel.stoneReady(3, false) && Citadel.stoneReady(3, true), "камни: 3-й ждёт лампу");
        check(DungeonMech.turn(Citadel.W) == Citadel.N && DungeonMech.turn(Citadel.E) == Citadel.S, "поворот зеркала по часовой");

        // ---------- «внутри данжа» = весь цилиндр r<=116, y от -7 до +125 (открытый воздух над склоном, двором, стеной)
        DungeonData.Rec rec = new DungeonData.Rec("citadel_0_0", "citadel", 0, 0, 1000, 70, -500);
        check(rec.contains(1000, 70 + 23, -500 + 80, 0), "воздух над склоном серпантина внутри");
        check(rec.contains(1000.5, 70 + 36, -500 - 93, 0), "воздух над двором внутри");
        check(rec.contains(1000, 70 + 124, -500, 0) && !rec.contains(1000, 70 + 125, -500, 0), "верх цилиндра: +124 внутри, +125 нет");
        check(rec.contains(1000, 70 - 7, -500, 0) && !rec.contains(1000, 70 - 8, -500, 0), "низ цилиндра: -7 внутри, -8 нет");
        check(rec.contains(1000 + 116, 70 + 40, -500, 0) && !rec.contains(1000 + 117, 70 + 40, -500, 0), "край цилиндра по радиусу 116");
        check(rec.contains(1000 + 118, 70 + 40, -500, 2) && !rec.contains(1000 + 120, 70 + 40, -500, 2), "запас pad расширяет цилиндр");
        check(!rec.contains(1000 + 90, 70 + 40, -500 + 90, 0), "угол квадрата вне круга снаружи");
        check(rec.inBox(1000, 70 + 60, -500 + 100, 0), "inBox: цилиндр");
        Dungeons.Shield cyl = new Dungeons.Shield(List.of(), null, List.of(new int[]{1000, 70, -500, d.clearR, d.clearBelow, d.clearH}));
        check(cyl.at(1000, 70 + 22, -500 + 80) && cyl.at(1000 + 116, 70, -500) && !cyl.at(1000 + 117, 70, -500) && !cyl.at(1000, 70 + 125, -500) && !cyl.at(1000, 70 - 8, -500), "Shield: цилиндр защищает открытый воздух");
        check(cyl.at(new net.minecraft.core.BlockPos(1000, 70 + 5, -500)) && !Dungeons.Shield.NONE.at(new net.minecraft.core.BlockPos(1000, 70, -500)), "Shield.at(BlockPos) с одним цилиндром");
        Dungeons.Def fresh = new Dungeons.Def("tmp", 1, 0, 1, "citadel_p1").inHell(116, 125);
        check(!fresh.ready() && fresh.inCylinder(0, 0, 0, 0) && !fresh.inCylinder(120, 0, 0, 0), "Def без шаблонов: не готов, но цилиндр считается");
        check(d.ready() && Dungeons.RIFT.ready(), "готовность: шаблоны Цитадели прочитаны, у земных данжей всегда да");
        check(Citadel.isPillar(35, 67, 0) && Citadel.isPillar(35, 68, 0) && !Citadel.isPillar(35, 66, 0) && !Citadel.isPillar(34, 67, 0), "ПКМ по маяку столба и стеклу над ним отменяется");
        String cit = read("src/com/explosionwand/Citadel.java");
        check(cit.contains("rec.opened.contains(\"pillar\")") && cit.contains("rec.opened.add(\"pillar\")"), "столб света ставится один раз");

        // ---------- место Цитадели
        int[] last = null;
        for (long seed : new long[]{1L, 2L, 12345L, -77L}) {
            int[] s = Citadel.findSite(seed, 0.5, -6, (x, z) -> true);
            double dist = Math.hypot(s[0] - 0.5, s[1] + 6);
            check(dist >= Citadel.MIN_DIST && dist <= Citadel.MAX_DIST, "место в кольце 500-1500: " + Arrays.toString(s) + " dist " + dist);
            check(Arrays.equals(s, Citadel.findSite(seed, 0.5, -6, (x, z) -> true)), "место детерминировано");
            if (last != null && Arrays.equals(last, s)) check(false, "сиды дают разные места");
            last = s;
        }
        // пятно пиков в одном месте: оно и выбирается (все 5 проб); место внутри кольца
        int[] spot = {-900, 700};
        int[] hit = Citadel.findSite(9L, 0.5, -6, (x, z) -> Math.abs(x - spot[0]) < 250 && Math.abs(z - spot[1]) < 250);
        check(Math.abs(hit[0] - spot[0]) < 250 - Citadel.SAMPLE && Math.abs(hit[1] - spot[1]) < 250 - Citadel.SAMPLE, "место внутри пятна биома: " + Arrays.toString(hit));
        // нет пиков вообще: всё равно есть место
        int[] none2 = Citadel.findSite(9L, 0.5, -6, (x, z) -> false);
        check(none2 != null && Math.hypot(none2[0], none2[1]) >= Citadel.MIN_DIST, "без пиков место всё равно есть");
        check(Citadel.pickY(new int[]{10, 12}) == Citadel.Y_MIN && Citadel.pickY(new int[]{300}) == Citadel.Y_MAX && Citadel.pickY(new int[]{70, 80, 90}) == 80, "высота дна в пределах");

        // ---------- ресурсы и регистрация
        String mod = read("src/com/explosionwand/ExplosionWandMod.java");
        check(mod.contains("\"eclipse_seal_shard\", () -> new SealShardItem(") && mod.contains("\"citadel\", \"eclipse_seal_shard\", false, \"gate1\")"), "Осколок Печати: Затмение зарегистрирован, ведёт к citadel, открывает gate1");
        check(mod.contains("\"throne_seal\", () -> new SealShardItem(") && mod.contains("\"throne_seal\", \"throne_seal\", true)"), "Печать Трона зарегистрирована как ключ двери");
        String jei = read("src/com/explosionwand/jei/ExplosionWandJei.java");
        check(jei.contains("ECLIPSE_SEAL_SHARD") && jei.contains("\"eclipse_seal_shard\"") && jei.contains("THRONE_SEAL"), "страницы JEI");
        for (String f : new String[]{"ru_ru", "en_us"}) {
            String lang = read("resources/assets/explosionwand/lang/" + f + ".json");
            for (String k : new String[]{"explosionwand.dungeon.citadel", "explosionwand.shard.silent_hell", "explosionwand.shard.searching",
                    "explosionwand.dungeon.locked.gate1", "explosionwand.dungeon.locked.gate2", "explosionwand.dungeon.locked.sun_e", "explosionwand.dungeon.locked.sun_w",
                    "explosionwand.dungeon.locked.prethrone", "explosionwand.dungeon.locked.throne_seal", "explosionwand.citadel.beacon_lit", "explosionwand.citadel.gate2_open",
                    "explosionwand.citadel.mirror", "explosionwand.citadel.dir.north", "explosionwand.citadel.dir.east", "explosionwand.citadel.dir.south", "explosionwand.citadel.dir.west",
                    "explosionwand.citadel.sun_solved", "explosionwand.citadel.sun_frozen", "explosionwand.citadel.stone_sleeps.1", "explosionwand.citadel.stone_sleeps.2",
                    "explosionwand.citadel.stone_sleeps.3", "explosionwand.citadel.stone_taken", "explosionwand.citadel.prethrone_open",
                    "item.explosionwand.eclipse_seal_shard", "item.explosionwand.eclipse_seal_shard.desc", "explosionwand.jei.eclipse_seal_shard",
                    "item.explosionwand.throne_seal", "item.explosionwand.throne_seal.desc", "explosionwand.jei.throne_seal"})
                check(lang.contains("\"" + k + "\""), f + ": нет ключа " + k);
        }
        for (String s : new String[]{"explosionwand/models/item/eclipse_seal_shard.json", "explosionwand/textures/item/eclipse_seal_shard.png",
                "explosionwand/models/item/throne_seal.json", "explosionwand/textures/item/throne_seal.png"})
            check(Files.exists(Path.of("resources/assets/" + s)), "нет файла " + s);
        check(Files.exists(Path.of("resources/data/explosionwand/recipes/eclipse_seal_shard.json")), "рецепт осколка");
        check(read("resources/META-INF/mods.toml").contains("version=\"3.4.1\""), "версия мода 3.4.1");
        check(read("docs/history.md").contains("## 3.4.1") && read("docs/history.md").contains("## 3.4.0"), "записи 3.4.1 и 3.4.0 в docs/history.md");

        if (fails > 0) { System.out.println(fails + " FAIL"); System.exit(1); }
        System.out.println("citadel ok: " + total + " блоков в 6 частях, потоковый разбор, метки и блоки под ними, часы (одно решение), место в кольце, ресурсы");
    }
}
