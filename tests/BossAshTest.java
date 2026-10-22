package com.explosionwand;

import com.explosionwand.DungeonTemplate.Mark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Пепельный Феникс в Гнезде Святилища (фаза 4.10), то, что проверяется без игры:
 *  - метка boss phoenix в центре Гнезда (на помосте), печать на входе вне зала и закрывающая тоннель целиком, сундук добычи, босс только в зале 23;
 *  - добыча: Посох Феникса и Перо Феникса впереди набора тира, у других залов их нет; барьер Святилища снимает победа (lairWon -> bossDead);
 *  - ХП: хост не выше 1024, множитель урона, жизни (2000 / яйцо 150 / 1500 или 700), обрезка удара по порогу фазы 2 и смертельный удар;
 *  - геометрия: свод (Boss5.vault совпадает с шаблоном), круг облёта и размах крыльев (~20) под сводом при любых взмахах, пике и скольжение внутри пещеры,
 *    места появления Искр и Пеплоходов свободны, помост пуст до свода;
 *  - все 58 частей облика получают позу, блоки палитр есть в реестре; секторы жара делят круг на 8; приспешников не больше предела;
 *  - предмет Перо Феникса зарегистрирован, подключение в Ticker/Boss/ExplosionWandMod, ключи lang (когда asset-engineer их добавит - на обоих языках).
 */
public class BossAshTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    static boolean isAir(Map<Long, String> w, int x, int y, int z) {
        String s = w.get(Dungeons.pack(x, y, z));
        return s == null || s.equals("minecraft:air");
    }

    public static void main(String[] args) throws Exception {
        Dungeons.Def d = Dungeons.ASH;
        Dungeons.Hall nest = d.hall(23);
        check(nest != null && nest.boss != null && nest.chest != null && nest.spawns.isEmpty(), "Гнездо: метка босса, сундук, без обычных мобов");
        Mark b = nest.boss;
        check(b.a.equals("phoenix") && Boss.isPhoenix(nest) && !Boss.isMonolith(nest) && nest.contains(b.x, b.y, b.z), "босс phoenix внутри зала 23");
        check(b.x == 34 && b.z == 4 && b.y == -73, "метка boss в центре Гнезда (34, -73, 4), на помосте: " + b.x + "," + b.y + "," + b.z);
        check(d.marks("boss").size() == 1, "метка босса одна на Святилище");
        for (Dungeons.Hall h : d.halls()) if (h.id != 23) check(h.boss == null, "босса нет в зале " + h.id);
        check(!Boss.isPhoenix(Dungeons.RIFT.hall(7)) && !Boss.isPhoenix(Dungeons.SLEEP.hall(20)), "Сердце Разлома и Монолит - не Феникс");
        Map<Long, String> world = Dungeons.assemble(d, 0, 0, 0);
        int fy = b.y, cx = b.x, cz = b.z;

        // ---- печать входа: вне зала, закрывает тоннель целиком (воздух внутри, стены, пол и кровля вокруг)
        Mark seal = null;
        for (Mark m : d.marks("seal")) if (m.a.startsWith("23,")) seal = m;
        check(d.sealed(23) && seal != null && seal.sx == 1 && seal.sy == 5 && seal.sz == 3 && seal.y == fy - 1 && seal.x + seal.sx <= nest.x, "печать зала 23 на входе, вне зала: " + (seal == null ? "нет" : seal.x + "," + seal.y + "," + seal.z));
        if (seal != null) {
            for (int y = 0; y < seal.sy; y++) for (int z = 0; z < seal.sz; z++) check(isAir(world, seal.x, seal.y + y, seal.z + z), "проём печати свободен в шаблоне: " + y + "," + z);
            for (int y = -1; y <= seal.sy; y++) { check(!isAir(world, seal.x, seal.y + y, seal.z - 1), "стена у печати (север) y" + y); check(!isAir(world, seal.x, seal.y + y, seal.z + seal.sz), "стена у печати (юг) y" + y); }
            for (int z = 0; z < seal.sz; z++) { check(!isAir(world, seal.x, seal.y - 1, seal.z + z), "пол у печати"); check(!isAir(world, seal.x, seal.y + seal.sy, seal.z + z), "кровля у печати"); }
        }

        // ---- добыча
        List<String> loot = Dungeons.lootFor(d, nest, Integer.parseInt(Dungeons.chestSpec(nest.chest.a)[1]));
        check(Boss5.LOOT.equals(List.of("explosionwand:phoenix_staff", "explosionwand:phoenix_feather")), "трофеи: Посох Феникса и Перо Феникса");
        check(loot.subList(0, 2).equals(Boss5.LOOT) && loot.containsAll(Dungeons.LOOT.get(1)) && loot.size() <= 27, "трофеи впереди, затем набор тира 2, всё помещается в сундук: " + loot.size());
        check(Boss.lootOf(nest) == Boss5.LOOT && Boss.lootOf(Dungeons.RIFT.hall(7)) == Boss.LOOT && Boss.lootOf(Dungeons.SLEEP.hall(20)) == Boss3.LOOT, "трофеи по виду босса");
        for (Dungeons.Hall h : d.halls()) if (h.chest != null && h.id != 23) check(!Dungeons.lootFor(d, h, 1).contains("explosionwand:phoenix_feather"), "перо Феникса не в сундуке зала " + h.id);
        check(!Dungeons.lootFor(Dungeons.RIFT, Dungeons.RIFT.hall(7), 2).contains("explosionwand:phoenix_feather") && !Dungeons.lootFor(Dungeons.SLEEP, Dungeons.SLEEP.hall(20), 2).contains("explosionwand:phoenix_staff"), "у других боссов трофеев Феникса нет");
        check(nest.chest.y == fy - 1 && Math.hypot(nest.chest.x - cx, nest.chest.z - cz) > 9.5 && !isAir(world, nest.chest.x, nest.chest.y - 1, nest.chest.z)
                && isAir(world, nest.chest.x, nest.chest.y, nest.chest.z) && isAir(world, nest.chest.x, nest.chest.y + 1, nest.chest.z), "сундук на полу пещеры вне помоста, есть опора и воздух над ним");

        // ---- ХП, обрезка ударов
        check(Boss5.HOST_HP <= 1024 && Boss5.MAX_HP > Boss5.HOST_HP && Math.abs(Boss5.DMG_K - 1024F / 2000F) < 1e-6, "хост на лимите 1024, настоящие ХП 2000, множитель HOST_HP / MAX_HP");
        check(Math.abs(Boss5.hostDamage(2000) - Boss5.HOST_HP) < 0.01F, "2000 урона = 1024 ХП хоста (запас ровно 2000)");
        check(Boss5.REBORN_FULL * Boss5.DMG_K <= Boss5.HOST_HP && Boss5.REBORN_WEAK < Boss5.REBORN_FULL && Boss5.EGG_HP * Boss5.DMG_K < 100 && Boss5.EGG_HP < Boss5.REBORN_WEAK, "ХП возрождения (ослабленный < полный) и яйца помещаются в хост");
        check(Boss5.EGG_TICKS == 300, "яйцо дышит 15 секунд");
        DungeonData.Rec rec = new DungeonData.Rec("ash_0_0", "ash", 0, 0, 100, 64, 200);
        Boss5 boss = new Boss5(null, rec, nest, "ewdxh23");
        float max = Boss5.HOST_HP;
        boss.st = Boss5.St.FIGHT; boss.life = 1; boss.phase = 1;
        check(boss.clampHit(max, 5000) == max - max * Boss5.P2_AT && !boss.lethal, "удар из полного ХП не перескакивает фазу 2 (55%)");
        check(boss.clampHit(max * Boss5.P2_AT + 3, 100) == 3 && boss.clampHit(max * Boss5.P2_AT, 50) == 0.5F, "до порога осталось 3 - снимет 3; ровно на пороге - 0,5");
        boss.phase = 2;
        check(boss.clampHit(max * 0.5F, 5000) == max * 0.5F - 1.0F && boss.lethal, "в фазе 2 смертельный удар оставляет 1 ХП и ставит lethal (падение в яйцо)");
        boss.lethal = false;
        check(boss.clampHit(100, 10) == 10 && !boss.lethal && boss.clampHit(1.0F, 10) == 0 && boss.lethal, "обычный удар не обрезается, на последнем ХП урон 0");
        boss.lethal = false; boss.st = Boss5.St.EGG;
        float egg = Boss5.EGG_HP * Boss5.DMG_K;
        check(boss.clampHit(egg, 9999) == egg - 1.0F && boss.lethal, "яйцо: смертельный удар оставляет 1 ХП, яйцо разбито");
        boss.lethal = false; boss.st = Boss5.St.FIGHT; boss.life = 2; boss.phase = 4;
        check(boss.clampHit(300, 9999) == 299 && boss.lethal, "возрождённый: порога фазы нет, смертельный удар запускает гибель");
        check(Boss5.BAR_COLOR.length == 4 && new HashSet<>(Arrays.asList(Boss5.BAR_COLOR)).size() == 4, "четыре фазы - четыре цвета полоски");
        check(Boss5.barOf(rec).startsWith(Boss5.BAR) && !Boss5.barOf(rec).equals(Boss.barOf(rec)) && !Boss5.barOf(rec).equals(Boss3.barOf(rec)), "полоска Феникса отдельная");
        for (int ph = 2; ph <= 4; ph++) for (int np = 1; np <= 8; np++) {
            int[] pl = Boss5.minionPlan(ph, np);
            check(pl[0] + pl[1] <= Boss5.MAX_MIN && (ph == 3 ? pl[1] >= 4 && pl[0] == 0 : pl[0] >= 2 && pl[1] == 0), "приспешников фазы " + ph + " на " + np + " игроков: " + Arrays.toString(pl));
        }
        check(Arrays.equals(Boss5.minionPlan(1, 4), new int[]{0, 0}), "в фазе 1 приспешников нет");

        // ---- свод: Boss5.vault совпадает с шаблоном (воздух до S+h-1, S+h - кровля), S = fy - 1
        int S = fy - 1;
        int mism = 0, samples = 0;
        for (int dx = -29; dx <= 29; dx += 4) for (int dz = -29; dz <= 29; dz += 4) {
            double r = Math.hypot(dx, dz);
            if (r > 28) continue;
            int h = Boss5.vault(r);
            samples++;
            if (isAir(world, cx + dx, S + h, cz + dz)) mism++;
            if (r < 9.0 && !isAir(world, cx + dx, S + h - 1, cz + dz)) mism++;   // в круге 9,5 сталагмитов нет, под сводом воздух
        }
        check(samples > 100 && mism == 0, "свод Гнезда совпадает с Boss5.vault: расхождений " + mism + " из " + samples);
        check(Boss5.vault(0) == 15 && Boss5.vault(Boss5.R) == 4, "свод 15 в центре и 4 у края");

        // ---- помост пуст до свода (яйцо рисует босс), вокруг сплошной пол
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int y = 0; y < 13; y++) check(isAir(world, cx + dx, fy + y, cz + dz), "над помостом пусто (яйцо - дисплеи): " + dx + "," + y + "," + dz);
        int floorHoles = 0;
        for (int dx = -29; dx <= 29; dx++) for (int dz = -29; dz <= 29; dz++) if (Math.hypot(dx, dz) <= 28 && isAir(world, cx + dx, S - 1, cz + dz)) floorHoles++;
        check(floorHoles == 0, "пол пещеры сплошной: дыр " + floorHoles);

        // ---- полёт: круг облёта, крылья под сводом при любых взмахах и крене; размах ~20
        double[] neutral = Boss5.wingAngles(0, 0, 0);
        double span = 0;
        for (int sg : new int[]{1, -1}) for (double[] p : Boss5.wingPoints(sg, neutral)) span = Math.max(span, Math.abs(p[0]));
        check(span * 2 >= 19.0 && span * 2 <= 22.0, "размах крыльев ~20 блоков: " + span * 2);
        double worstClear = 1e9;
        for (int ai = 0; ai < 36; ai++) {
            double a = ai * Math.PI / 18, r0 = Boss5.ORBIT_R;
            for (double dr : new double[]{-1.0, 0, 1.0}) for (double bob : new double[]{-0.85, 0, 0.85}) {
                double bxx = Math.cos(a) * (r0 + dr), bzz = Math.sin(a) * (r0 + dr), byy = Boss5.CRUISE_Y + bob;
                double yaw = Math.atan2(-Math.sin(a), Math.cos(a));     // по ходу круга (против часовой стрелки по a)
                for (double roll : new double[]{-Boss5.ROLL_MAX, 0, Boss5.ROLL_MAX}) for (double ph = 0; ph < 6.3; ph += 0.45) {
                    double[] q = Disp.mul(Disp.q(0, 1, 0, yaw), Disp.q(0, 0, 1, roll));
                    double[] ang = Boss5.wingAngles(ph, 0, Boss5.AMP * 1.3);
                    for (int sg : new int[]{1, -1}) for (double[] p : Boss5.wingPoints(sg, ang)) {
                        double[] w = Disp.rot(q, p[0], p[1], p[2]);
                        double wx = bxx + w[0], wz = bzz + w[2], wy = byy + w[1] + 1.0;      // +1: толщина перьев и пламя на кончике ниже 0,9 над ним
                        double rr = Math.hypot(wx, wz);
                        worstClear = Math.min(worstClear, (Boss5.vault(rr) - 1) - wy);
                        if (rr > Boss5.R - 1.0) worstClear = Math.min(worstClear, -99);
                    }
                }
            }
        }
        check(worstClear >= 0.0, "кончики крыльев (с пламенем) под сводом при любых взмахах: запас " + worstClear);

        // ---- пике и скольжение внутри пещеры
        Boss5 bb = new Boss5(null, rec, nest, "ewdxh23");
        double worstEnd = 0;
        for (int i = 0; i < 360; i += 7) for (double rt = 0; rt <= Boss5.R - 5; rt += 4) for (int j = 0; j < 360; j += 31) {
            double tx = Math.cos(Math.toRadians(i)) * rt, tz = Math.sin(Math.toRadians(i)) * rt, ux = Math.cos(Math.toRadians(j)), uz = Math.sin(Math.toRadians(j));
            double len = bb.skimLength(bb.ax + tx, bb.az + tz, ux, uz);
            double ex = tx + ux * len, ez = tz + uz * len;
            worstEnd = Math.max(worstEnd, Math.hypot(ex, ez));
            check(len >= 0 && len <= 12, "длина скольжения 0..12: " + len);
        }
        check(worstEnd <= Boss5.R - 3 + 1e-9, "скольжение не выходит за R-3 от центра: " + worstEnd);
        check(Boss5.PERCH_TICKS == 60, "окно после пике ровно 3 с (60 тиков): " + Boss5.PERCH_TICKS);
        try {
            String b5 = java.nio.file.Files.readString(java.nio.file.Path.of("src/com/explosionwand/Boss5.java"));
            check(b5.contains("modeT >= PERCH_TICKS") && !b5.contains("life == 2 ? 45 : 70"), "посадка держится PERCH_TICKS, без отдельных чисел по жизням");
            check(b5.contains("if (--diveCd <= 0) { startWarn") && b5.indexOf("--diveCd") == b5.lastIndexOf("--diveCd"), "таймер пике идёт только в круге (в окне Феникс не пикирует)");
        } catch (java.io.IOException e) { check(false, "Boss5.java не читается"); }
        check(Boss5.PERCH_Y < 3 && Boss5.CRUISE_Y >= 5 && Boss5.CRUISE_Y + 3 < Boss5.vault(Boss5.ORBIT_R + 10) + 1, "посадка у пола, круг под сводом");

        // ---- места Пеплоходов (кольцо r 8,5 на полу, сталагмитов нет) и Искр (высота 8 над меткой: выше любого сталагмита)
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            int x = (int) Math.floor(cx + 0.5 + Math.cos(a) * Boss5.WALKER_R), z = (int) Math.floor(cz + 0.5 + Math.sin(a) * Boss5.WALKER_R);
            check(!isAir(world, x, S - 1, z) && isAir(world, x, S, z) && isAir(world, x, S + 1, z) && isAir(world, x, S + 2, z), "место Пеплохода на полу, над ним воздух: " + x + "," + z);
        }
        for (double rr = 9; rr <= 15; rr += 2) for (int i = -12; i <= 12; i++) {
            double a = i / 12.0 * Boss5.SPARK_ARC;
            int x = (int) Math.floor(cx + 0.5 + Math.cos(a) * rr), z = (int) Math.floor(cz + 0.5 + Math.sin(a) * rr);
            int y = fy + (int) Boss5.SPARK_Y;
            check(isAir(world, x, y, z) && isAir(world, x, y + 1, z), "место Искры в воздухе: " + x + "," + y + "," + z);
        }

        // ---- секторы жара
        int[] cnt = new int[8];
        for (int i = 0; i < 3600; i++) { double a = Math.toRadians(i / 10.0); cnt[Boss5.sectorOf(Math.cos(a) * 10, Math.sin(a) * 10)]++; }
        for (int c : cnt) check(c >= 440 && c <= 460, "восемь секторов поровну: " + Arrays.toString(cnt));
        check(Boss5.sectorOf(1, 0) == 0 && Boss5.sectorOf(0, 1) == 2 && Boss5.sectorOf(-1, 0) == 4 && Boss5.sectorOf(0, -1) == 6 && Boss5.sectorOf(1, -0.01) == 7, "сектор считается по углу от оси x");
        check(Boss5.PLAT_SAFE < 7.5 && Boss5.PLAT_SAFE > 4.8, "центр помоста вне жара");

        // ---- облик: каждая из 58 частей получает позу, блоки палитр есть в реестре
        Boss5 pose = new Boss5(null, rec, nest, "ewdxh23");
        for (int i = 0; i < Boss5.N; i++) pose.uuid[i] = "p" + i;
        pose.gs = 1; pose.eg = 1; pose.st = Boss5.St.EGG;
        pose.poseBird(3); pose.poseEgg(3);
        int unposed = 0;
        for (int i = 0; i < Boss5.N; i++) if (pose.last[i] == null) unposed++;
        check(unposed == 0, "все части облика позируются: без позы " + unposed);
        check(Boss5.N == 58 && Boss5.ROLE.length == Boss5.N && Boss5.PAL.length == 2 && Boss5.PAL[0].length == 11 && Boss5.PAL[1].length == 11, "58 частей, две палитры по 11 ролей");
        for (int i = 0; i < Boss5.N; i++) check(Boss5.ROLE[i] >= 0 && Boss5.ROLE[i] <= Boss5.R_CRACK, "роль части " + i);
        Path regPath = Path.of(".cache/maps/reg.json");
        if (Files.exists(regPath)) {
            String reg = Files.readString(regPath);
            for (String[] row : Boss5.PAL) for (String blk : row) check(reg.contains("\"" + blk + "\""), "блок палитры есть в реестре 1.20.1: " + blk);
        }
        check(!Boss5.PAL[0][Boss5.R_FLAME].equals(Boss5.PAL[1][Boss5.R_FLAME]) && Boss5.PAL[1][Boss5.R_FLAME].equals("soul_fire") && Boss5.PAL[0][Boss5.R_GLOW].equals("magma_block"), "в фазе 4 пламя бело-голубое (soul_fire), трещины - магма до неё");

        // ---- подключение и предмет
        String mod = Files.readString(Path.of("src/com/explosionwand/ExplosionWandMod.java"));
        String bossJ = Files.readString(Path.of("src/com/explosionwand/Boss.java"));
        check(mod.contains("Boss5::onHurt") && mod.contains("\"phoenix_feather\""), "обработчик урона Феникса и Перо Феникса зарегистрированы");
        check(bossJ.contains("Boss5.tickAll(sl, data)") && bossJ.contains("Boss5.wake(sl, rec, h, hallTag)") && bossJ.contains("Boss5.reset(sl, rec, h)") && bossJ.contains("Boss5.clear()"), "жизненный цикл Феникса подключён в Boss (wake/reset/tickAll/clear)");
        for (String s : new String[]{"explosionwand/models/item/phoenix_feather.json", "explosionwand/textures/item/phoenix_feather.png"})
            check(Files.exists(Path.of("resources/assets/" + s)), "есть файл: " + s);
        check(!Files.readString(Path.of("src/com/explosionwand/Ash.java")).contains("остаётся под защитой, пока"), "заглушки про барьер до Феникса нет");

        // ---- тексты: когда ключи появятся (asset-engineer), они обязаны быть в обоих lang полностью
        String ru = Files.readString(Path.of("resources/assets/explosionwand/lang/ru_ru.json"));
        String en = Files.readString(Path.of("resources/assets/explosionwand/lang/en_us.json"));
        boolean anyRu = ru.contains("\"" + Boss5.KEY), anyEn = en.contains("\"" + Boss5.KEY);
        check(anyRu == anyEn, "тексты Феникса: ru и en добавляются вместе");
        if (anyRu) {
            for (String k : Boss5.LANG) check(ru.contains("\"" + Boss5.KEY + k + "\"") && en.contains("\"" + Boss5.KEY + k + "\""), "текст босса есть в ru и en: " + k);
            for (String k : new String[]{"item.explosionwand.phoenix_feather", "item.explosionwand.phoenix_feather.desc"})
                check(ru.contains("\"" + k + "\"") && en.contains("\"" + k + "\""), "текст есть в ru и en: " + k);
        } else System.out.println("note: тексты Феникса (" + Boss5.LANG.length + " ключей + 4) ещё не добавлены в lang - дело asset-engineer");
        check(RiftModels.Kind.byId(AshLogic.minionKind(0)) == RiftModels.Kind.ASH_SPARK && RiftModels.Kind.byId(AshLogic.minionKind(1)) == RiftModels.Kind.ASH_WALKER, "приспешники Феникса - настоящие Искра и Пеплоход");

        System.out.println(fails == 0 ? "Феникс в Гнезде ok" : fails + " проверок не прошли");
        if (fails > 0) System.exit(1);
    }
}
