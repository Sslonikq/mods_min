package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Монолит Сна в логове храма Города Спящих (фаза 4.7), то, что проверяется без игры:
 *  - у зала 20 метка босса monolith, печать и сундук, обычных мобов нет, босс только там; общий жизненный цикл различает двух боссов;
 *  - добыча сундука: Посох Сна, Недремлющее Око, Осколок Печати: Пепел и набор тира 2; у других залов трофеев нет;
 *  - геометрия подземной арены 41x41 (воздух 20 блоков): башня (масштаб S = 1) помещается под потолок, зал свободен, кроме четырёх колонн, область
 *    скольжения башни и места приспешников свободны, пол сплошной, вход с печатью и жаровня перед ареной;
 *  - ХП: запас ровно 1700 (множитель только HOST_HP / MAX_HP);
 *  - обрезка удара по порогам сегментов 75/50/25% и смертельный удар;
 *  - ХП хоста не выше лимита 1024, приспешников немного; тексты босса есть в ru и en; удалённая Печать Сна не осталась в коде.
 */
public class BossCityTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    public static void main(String[] args) throws Exception {
        Dungeons.Def d = Dungeons.SLEEP;
        Dungeons.Hall lair = d.hall(20);
        check(lair != null && lair.boss != null && lair.chest != null && lair.spawns.isEmpty(), "логово: метка босса, сундук, без обычных мобов");
        check(d.sealed(20) && d.marks("boss").size() == 1, "у логова печать, меток босса одна");
        Mark b = lair.boss;
        check(b.a.equals("monolith") && lair.contains(b.x, b.y, b.z) && Boss.isMonolith(lair), "босс monolith внутри зала");
        check(lair.x + lair.sx / 2 == b.x && lair.z + lair.sz / 2 == b.z, "метка босса в центре зала");
        check(lair.chest.y == b.y && lair.chest.z != b.z && !Boss3.pillar(lair.chest.x - b.x, lair.chest.z - b.z, 0), "сундук на уровне пола, не под башней");
        for (Dungeons.Hall h : d.halls()) if (h.id != 20) check(h.boss == null, "босса нет в зале " + h.id);
        check(!Boss.isMonolith(Dungeons.RIFT.hall(7)), "Сердце Разлома — не Монолит");

        // ---- добыча
        List<String> loot = Dungeons.lootFor(d, lair, Integer.parseInt(Dungeons.chestSpec(lair.chest.a)[1]));
        check(loot.contains("explosionwand:dream_staff") && loot.contains("explosionwand:dreaming_eye") && loot.contains("explosionwand:ash_seal_shard"), "в сундуке Посох Сна, Око, Осколок Пепла: " + loot);
        check(loot.indexOf("explosionwand:dream_staff") < 3 && loot.subList(0, 3).equals(Boss3.LOOT) && loot.containsAll(Dungeons.LOOT.get(1)), "трофеи впереди, затем набор тира 2");
        check(loot.size() <= 27, "всё помещается в сундук: " + loot.size());
        check(Boss.lootOf(lair) == Boss3.LOOT && Boss.lootOf(Dungeons.RIFT.hall(7)) == Boss.LOOT, "трофеи по виду босса");
        for (Dungeons.Hall h : d.halls()) if (h.chest != null && h.id != 20) check(!Dungeons.lootFor(d, h, 1).contains("explosionwand:dream_staff"), "трофеев Монолита нет в зале " + h.id);
        check(!Dungeons.lootFor(Dungeons.RIFT, Dungeons.RIFT.hall(7), 2).contains("explosionwand:dream_staff"), "у Сердца Разлома нет трофеев Монолита");

        // ---- ХП и приспешники
        check(Boss3.HOST_HP <= 1024 && Boss3.MAX_HP == 1700 && Boss3.MAX_HP > Boss3.HOST_HP, "хост не выше лимита 1024, настоящие ХП 1700");
        // запас ровно 1700: урон игрока умножается только на HOST_HP / MAX_HP, 1700 урона = все 1024 ХП хоста, пороги фаз на 1275 / 850 / 425 урона
        check(Math.abs(Boss3.hostDamage(1700) - Boss3.HOST_HP) < 0.01F && Math.abs(Boss3.hostDamage(425) - Boss3.HOST_HP * 0.25F) < 0.01F && Math.abs(Boss3.hostDamage(1275) - Boss3.HOST_HP * 0.75F) < 0.01F, "1700 урона = 1024 ХП хоста (запас ровно 1700)");
        float acc = 0; int swings = 0;
        for (float raw = 0; swings < 100000 && acc < Boss3.HOST_HP; swings++) acc += Boss3.hostDamage(1);
        check(swings == 1700 || swings == 1701, "1700 ударов по 1 урону убивают хост: " + swings);
        for (int ph = 2; ph <= 4; ph++) for (int np = 1; np <= 6; np++) {
            int[] pl = Boss3.minionPlan(ph, np);
            check(pl[0] + pl[1] <= Boss3.MAX_MIN && pl[0] >= 1 && (ph == 2 ? pl[1] == 0 : pl[1] >= 1), "приспешников фазы " + ph + " на " + np + " игроков: " + Arrays.toString(pl));
        }

        // ---- геометрия арены: подземный зал 41x41, воздух 20 блоков, башня в масштабе 1,0
        Map<Long, String> world = Dungeons.assemble(d, 0, 0, 0);
        int fy = b.y, cx = b.x, cz = b.z;
        check(lair.sx >= 41 && lair.sz >= 41 && lair.sy >= 21, "зал 20 не меньше 41x41 с потолком от 20: " + lair.sx + "x" + lair.sz + "x" + lair.sy);
        int ceil = Integer.MIN_VALUE;
        for (int y = fy + 1; y < fy + 40; y++) if (!isAir(world, cx, y, cz)) { ceil = y; break; }
        check(ceil == fy + Boss3.CEIL && Boss3.CEIL >= 20, "потолок арены на " + Boss3.CEIL + " над полом: " + (ceil - fy));
        check(Boss3.towerTop() + 0.5 <= Boss3.CEIL && Boss3.towerTop() + 2.0 <= Boss3.CEIL, "башня и корона с запасом под потолком: " + Boss3.towerTop());
        check(Boss3.SEG_H > 0 && Boss3.S >= 0.9, "башня в полном масштабе");
        // весь пол сплошной, воздух до потолка везде, кроме колонн (3x3 в углах на +-13) и пилястр; пол арены на одной высоте
        int rxh = lair.sx / 2, rzh = lair.sz / 2, floorHoles = 0, pillars = 0, blocked = 0;
        for (int dx = -rxh; dx <= rxh; dx++) for (int dz = -rzh; dz <= rzh; dz++) {
            if (isAir(world, cx + dx, fy - 1, cz + dz)) floorHoles++;
            boolean col = Boss3.pillar(dx, dz, 0.4);
            for (int y = 0; y < Boss3.CEIL - 2; y++) {
                boolean air = isAir(world, cx + dx, fy + y, cz + dz);
                if (col && !air) { pillars++; break; }
                if (!col && !air && Math.abs(dx) < rxh && Math.abs(dz) < rzh) { blocked++; break; }
            }
        }
        check(floorHoles == 0, "пол арены сплошной: дыр " + floorHoles);
        check(pillars == 4 * 9, "четыре колонны 3x3: " + pillars + " столбцов");
        check(blocked == 0, "кроме колонн, вне краёв зал свободен от пола до потолка: занято " + blocked + " столбцов");
        // область скольжения башни |dx|,|dz| <= MOVE (+ полуширина хитбокса гиганта 1,8): без колонн
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
            check(!Boss3.pillar(dx, dz, 0.0), "башня не задевает колонны: " + dx + "," + dz);
            for (int y = 0; y <= (int) Boss3.towerTop(); y++) check(isAir(world, cx + dx, fy + y, cz + dz), "пространство башни пусто: " + dx + "," + y + "," + dz + " = " + world.get(Dungeons.pack(cx + dx, fy + y, cz + dz)));
        }
        check(Boss3.MOVE + 1.8 < Boss3.PILLAR - Boss3.PILLAR_HALF, "ход башни с хитбоксом не доходит до колонн");
        // места приспешников: под ногами пол, над ними три клетки воздуха, внутри зала, не в колоннах
        for (double[] s : Boss3.SLOTS) {
            int sx = (int) Math.floor(cx + 0.5 + s[0]), sz = (int) Math.floor(cz + 0.5 + s[1]);
            check(lair.contains(sx, fy, sz) && !isAir(world, sx, fy - 1, sz) && !Boss3.pillar(s[0], s[1], 0.5), "место приспешника на полу зала: " + s[0] + "," + s[1]);
            for (int y = 0; y < 3; y++) check(isAir(world, sx, fy + y, sz), "над приспешником воздух: " + s[0] + "," + s[1]);
        }
        check(Boss3.SLOTS.length >= Boss3.MAX_MIN, "мест приспешников не меньше предела");
        // вход: арка под печать (3x3) вне зала, ход к арене и жаровня внизу; печать вне зала
        for (Mark m : d.marks("seal")) if (m.a.startsWith("20,")) {
            check(m.sx == 3 && m.sy == 3 && m.sz == 1 && m.y == fy && m.z < lair.z && m.x + 1 == cx, "печать в арке входа арены, на уровне пола: " + m.x + "," + m.y + "," + m.z);
        }
        boolean lowBrazier = false;
        for (Mark br : d.braziers()) if (br.y == fy && br.z < lair.z) lowBrazier = true;
        check(lowBrazier, "перед ареной жаровня-контрольная точка");
        // зал вмещает прямоугольник атак
        check(Boss3.clampAxis(100, 0, 20.5, 1) == 19.5 && Boss3.clampAxis(-100, 0, 20.5, 1) == -19.5 && Boss3.clampAxis(2, 0, 20.5, 1) == 2, "точка зажимается внутрь зала");
        check(Boss3.RING_MAX >= Math.hypot(lair.sx / 2.0 + Boss3.MOVE, lair.sz / 2.0 + Boss3.MOVE) - 0.5, "волна доходит до дальнего угла зала при сдвинутой башне: " + Boss3.RING_MAX);
        check(Boss3.pillar(13, 13, 0) && Boss3.pillar(-13, 13, 0) && Boss3.pillar(13, -13, 0) && !Boss3.pillar(0, 0, 0) && !Boss3.pillar(13, 0, 0), "колонны только в углах");

        // ---- обрезка удара по порогам сегментов
        DungeonData.Rec rec = new DungeonData.Rec("sleep_0_0", "sleep", 0, 0, 100, 64, 200);
        Boss3 boss = new Boss3(null, rec, lair, "ewdxh20");
        float max = Boss3.HOST_HP;
        check(boss.clampHit(max, 5000) == max - max * 0.75F, "удар из полного ХП не пролетает порог первого сегмента (75%)");
        check(boss.clampHit(max * 0.75F + 3, 100) == 3, "до порога осталось 3 — снимет 3");
        check(boss.clampHit(max * 0.75F, 50) == 0.5F, "ровно на пороге: 0,5, пока тик не расколет сегмент");
        boss.broken = 1;
        check(boss.clampHit(max * 0.75F, 5000) == max * 0.25F, "второй порог — 50%");
        boss.broken = 2;
        check(boss.clampHit(max * 0.5F, 5000) == max * 0.25F, "третий порог — 25%");
        boss.broken = 3;
        check(boss.clampHit(max * 0.25F, 5000) == max * 0.25F - 1.0F && boss.lethal, "на последнем сегменте смертельный удар оставляет 1 ХП и запускает гибель");
        boss.lethal = false;
        check(boss.clampHit(100, 10) == 10 && !boss.lethal, "обычный удар не обрезается");
        check(boss.clampHit(1.0F, 10) == 0 && boss.lethal, "на последнем ХП урон 0, гибель");
        check(Boss3.BAR_COLOR.length == 4 && new HashSet<>(Arrays.asList(Boss3.BAR_COLOR)).size() == 4, "четыре фазы — четыре цвета полоски");
        check(Boss3.barOf(rec).startsWith(Boss3.BAR) && !Boss3.barOf(rec).equals(Boss.barOf(rec)), "полоска Монолита отдельная от Сердца");

        // ---- тексты и остатки Печати Сна
        String ru = Files.readString(Path.of("resources/assets/explosionwand/lang/ru_ru.json"));
        String en = Files.readString(Path.of("resources/assets/explosionwand/lang/en_us.json"));
        for (String k : new String[]{"name", "spawn.sub", "segments", "hint", "stomp", "spin", "sink", "phase1", "phase1.sub", "phase2", "phase2.sub", "phase3", "phase3.sub",
                "break1", "break2", "break3", "dead", "dead.sub"}) {
            check(ru.contains("\"" + Boss3.KEY + k + "\"") && en.contains("\"" + Boss3.KEY + k + "\""), "текст босса есть в ru и en: " + k);
        }
        for (String k : new String[]{"item.explosionwand.ash_seal_shard", "item.explosionwand.ash_seal_shard.desc", "explosionwand.jei.ash_seal_shard", "explosionwand.dungeon.locked.ash"})
            check(ru.contains("\"" + k + "\"") && en.contains("\"" + k + "\""), "текст Осколка Пепла: " + k);
        check(!ru.contains("item.explosionwand.dream_seal") && !en.contains("item.explosionwand.dream_seal"), "Печати Сна нет в lang");
        for (String s : new String[]{"explosionwand/models/item/ash_seal_shard.json", "explosionwand/textures/item/ash_seal_shard.png"})
            check(Files.exists(Path.of("resources/assets/" + s)), "есть файл: " + s);
        for (String s : new String[]{"explosionwand/models/item/dream_seal.json", "explosionwand/textures/item/dream_seal.png"})
            check(!Files.exists(Path.of("resources/assets/" + s)), "убран файл: " + s);
        check(!Files.exists(Path.of("resources/data/explosionwand/recipes/dream_seal.json")) && !Files.exists(Path.of("src/com/explosionwand/Arena3.java")) && !Files.exists(Path.of("src/com/explosionwand/DreamSealItem.java")), "рецепт Печати Сна и арена Монолита убраны");
        String mod = Files.readString(Path.of("src/com/explosionwand/ExplosionWandMod.java"));
        check(!mod.contains("dream_seal") && mod.contains("\"ash_seal_shard\""), "Печать Сна не зарегистрирована, Осколок Пепла — да");
        check(Files.exists(Path.of("resources/data/explosionwand/dimension/dream.json")), "JSON старого измерения dream оставлен для старых миров");

        System.out.println(fails == 0 ? "Монолит в храме ok" : fails + " проверок не прошли");
        if (fails > 0) System.exit(1);
    }

    static boolean isAir(Map<Long, String> w, int x, int y, int z) {
        String s = w.get(Dungeons.pack(x, y, z));
        return s == null || s.equals("minecraft:air");
    }
}
