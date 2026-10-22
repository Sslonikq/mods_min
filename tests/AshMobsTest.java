package com.explosionwand;

import com.explosionwand.RiftModels.Kind;
import com.explosionwand.RiftModels.Pose;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Мобы Пепельного святилища (4.9), чистая логика на заглушках: урон и тайминги, щиты, стая гончих, волна Звонаря, лечение жреца, червь,
 * Хранитель (обрезка порога фазы), Блуждающий огонёк, облик, каркас (хосты, теги, метки), расстановка по шаблонам, lang.
 */
public class AshMobsTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    static final Kind[] ASH = {Kind.ASH_WALKER, Kind.ASH_SPARK, Kind.ASH_HOUND, Kind.ASH_RINGER, Kind.ASH_WORM, Kind.ASH_PRIEST, Kind.ASH_GUARD, Kind.ASH_KEEPER, Kind.ASH_WISP};

    public static void main(String[] args) throws Exception {
        balanceTests();
        shieldTests();
        packTests();
        supportTests();
        wispTests();
        modelTests();
        frameTests();
        templateTests();
        langTests();
        System.out.println(fails == 0 ? "мобы Святилища ok" : fails + " проверок не прошли");
        if (fails > 0) System.exit(1);
    }

    static void balanceTests() {
        double[] hits = {AshLogic.WALKER_HIT, AshLogic.SPARK_BOLT, AshLogic.HOUND_BITE, AshLogic.RINGER_WAVE, AshLogic.WORM_SPIT, AshLogic.PRIEST_CIRCLE,
                AshLogic.GUARD_SWING, AshLogic.KEEPER_HAMMER, AshLogic.KEEPER_SLAM, AshLogic.KEEPER_CIRCLE};
        for (double h : hits) check(RiftLogic.capHit(h, true) * RiftLogic.HARD_MULT <= 14.0 + 1e-9, "на Сложной удар " + h + " не больше 7 сердец");
        check(AshLogic.WALKER_HIT > AshLogic.HOUND_BITE && AshLogic.GUARD_SWING > AshLogic.WALKER_HIT && AshLogic.KEEPER_HAMMER > AshLogic.GUARD_SWING, "сила растёт: гончая < Пеплоход < страж < Хранитель");
        // телеграфы опасных атак не короче 0,7 с (14 тиков)
        check(AshLogic.WALKER_WIND >= 14 && AshLogic.RINGER_WIND >= 20 && AshLogic.WORM_SPIT_WIND >= 14 && AshLogic.WORM_BUBBLE >= 14 && AshLogic.PRIEST_CIRCLE_WIND >= 20
                && AshLogic.GUARD_WIND >= 14 && AshLogic.KEEPER_HAMMER_WIND >= 20 && AshLogic.KEEPER_SLAM_WIND >= 28 && AshLogic.KEEPER_CIRCLE_WIND >= 20 && AshLogic.SPARK_AIM >= 14 && AshLogic.TRAP_WIND >= 20,
                "телеграф каждой опасной атаки не короче 0,7 с");
        check(AshLogic.KEEPER_SLAM_WIND >= 30 && AshLogic.KEEPER_STUN >= 60 && AshLogic.KEEPER_STUN_RAGE < AshLogic.KEEPER_STUN, "удар о землю: телеграф 1,5 с+, окно открытости 3 с+, в ярости короче");
        check(AshLogic.inPatch(0, 0, 0) && AshLogic.inPatch(1.0, 0.5, 1.0) && !AshLogic.inPatch(2.0, 0, 0) && !AshLogic.inPatch(0, 3.0, 0) && AshLogic.PATCH_TICKS == 120, "горящий пепел: круг 1,6, 6 секунд");
        check(AshLogic.WALKER_SPEED < AshLogic.GUARD_SPEED * 1.2 && AshLogic.HOUND_SPEED > AshLogic.WALKER_SPEED * 2, "Пеплоход медленный, гончая быстрая");
    }

    static void shieldTests() {
        check(AshLogic.guardMult(1, true, false) == AshLogic.GUARD_SHIELD && AshLogic.GUARD_SHIELD <= 0.25, "страж: щит спереди гасит до 25%");
        check(AshLogic.guardMult(1, false, false) == 1.0 && AshLogic.guardMult(0, true, false) == 1.0, "страж: в замахе щит опущен, сбоку не защищает");
        check(AshLogic.guardMult(-1, true, false) > 1.0 && AshLogic.guardMult(-1, false, false) > 1.0, "страж: спина уязвима");
        check(AshLogic.guardMult(1, true, true) > 1.0 && AshLogic.guardMult(0, true, true) > 1.0, "страж: после размаха открыт со всех сторон");
        check(AshLogic.keeperMult(1, true, false) == AshLogic.KEEPER_SHIELD && AshLogic.keeperMult(-1, true, false) == AshLogic.KEEPER_BACK && AshLogic.keeperMult(0, true, false) == 1.0, "Хранитель: щит спереди, спина уязвима");
        check(AshLogic.keeperMult(1, true, true) == AshLogic.KEEPER_STUN_MULT && AshLogic.KEEPER_STUN_MULT > AshLogic.KEEPER_BACK, "Хранитель: после удара о землю открыт со всех сторон сильнее, чем со спины");
        check(AshLogic.clampPhase(200, 500, 180) < 21 && AshLogic.clampPhase(200, 500, 180) > 19.9 && AshLogic.clampPhase(100, 50, 180) == 50 && AshLogic.clampPhase(200, 10, 180) == 10, "один удар не перескакивает порог ярости");
        check(AshLogic.enraged(180, 360) && !AshLogic.enraged(181, 360), "ярость на 50% здоровья");
        check(Kind.ASH_KEEPER.hp == 360 && Kind.ASH_KEEPER.hp <= 1024 && Kind.ASH_GUARD.hp == 150 && Kind.ASH_KEEPER.hp > Kind.ASH_GUARD.hp * 2, "ХП: Хранитель 360, страж 150, в пределах лимита");
    }

    static void packTests() {
        List<String> ids = List.of("c3", "a1", "b2");
        Set<Integer> ranks = new TreeSet<>();
        for (String id : ids) ranks.add(AshLogic.houndRank(id, ids));
        check(ranks.equals(Set.of(0, 1, 2)), "ранги стаи 0..2 без повторов: " + ranks);
        double[] a = AshLogic.houndSlot(0, 3, 0, 3.6), b = AshLogic.houndSlot(1, 3, 0, 3.6), c = AshLogic.houndSlot(2, 3, 0, 3.6);
        double ab = Math.hypot(a[0] - b[0], a[1] - b[1]), bc = Math.hypot(b[0] - c[0], b[1] - c[1]), ca = Math.hypot(c[0] - a[0], c[1] - a[1]);
        check(Math.abs(ab - bc) < 1e-9 && Math.abs(bc - ca) < 1e-9 && ab > 5, "три гончие делят круг поровну (окружают)");
        check(Math.abs(Math.hypot(a[0], a[1]) - 3.6) < 1e-9 && AshLogic.houndSlot(0, 0, 1, 2).length == 2, "радиус слота; пустая стая не делит на ноль");
        check(AshLogic.HOUND_RUSH > AshLogic.HOUND_BITE_REACH && AshLogic.HOUND_SURROUND_R < AshLogic.HOUND_RUSH + 0.0 + 0.1, "обход по кругу, ближе радиуса броска идут напрямую");
    }

    static void supportTests() {
        check(AshLogic.waveHits(5, 0, AshLogic.RINGER_R) && !AshLogic.waveHits(8, 0, AshLogic.RINGER_R) && !AshLogic.waveHits(5, 1.2, AshLogic.RINGER_R), "волна: в радиусе и на земле; прыжок или дальний игрок целы");
        check(AshLogic.waveHits(AshLogic.RINGER_R, -1.0, AshLogic.RINGER_R) && !AshLogic.waveHits(3, -4, AshLogic.RINGER_R) && !AshLogic.waveHits(3, -14, AshLogic.RINGER_R), "игрок на ступень ниже задет, на этаже ниже (сквозь перекрытие) нет");
        check(AshLogic.needsHeal(10, 30) && !AshLogic.needsHeal(25, 30) && !AshLogic.needsHeal(0, 30) && !AshLogic.needsHeal(5, 0), "лечат только раненых (ниже 70%), мёртвых и пустых нет");
        check(AshLogic.healAmount(28) == 7 && AshLogic.healAmount(360) == AshLogic.HEAL_CAP && AshLogic.healAmount(150) == 37.5, "лечение 25%, но не больше 40 ХП");
        double[][] cells = {{0, 0}, {2, 0}, {5, 0}, {9, 0}};
        check(AshLogic.wormCell(cells, 0, 0) == 2 && AshLogic.wormCell(cells, 100, 0) == 3 && AshLogic.wormCell(new double[][]{{1, 0}}, 0, 0) == -1, "червь всплывает у игрока, но не под ногами (не ближе 3)");
        check(AshLogic.WORM_RANGE > AshLogic.WORM_SPIT_R * 5 && AshLogic.WORM_UP > AshLogic.WORM_SPIT_WIND * 3, "высунут достаточно долго для плевка");
    }

    static void wispTests() {
        check(AshLogic.wispSpawns(true, 0, 0) && !AshLogic.wispSpawns(false, 0, 0) && !AshLogic.wispSpawns(true, AshLogic.WISP_CAP, 0) && !AshLogic.wispSpawns(true, 0, AshLogic.WISP_CHANCE), "огонёк: ночью, не выше лимита, редко");
        int traps = 0;
        for (int i = 0; i < 100; i++) if (AshLogic.wispIsTrap(i)) traps++;
        check(traps == AshLogic.TRAP_PERCENT && traps > 50 && traps < 60, "исход: чаще ловушка, но сундук тоже бывает (" + traps + "%)");
        check(AshLogic.wispLeads(AshLogic.LURE_RANGE) && !AshLogic.wispLeads(AshLogic.LURE_RANGE + 1), "ведёт, пока игрок рядом");
        check(AshLogic.DEST_MIN >= 20 && AshLogic.DEST_MAX < AshLogic.WISP_RANGE && AshLogic.TRAP_SPARKS == 3 && AshLogic.TRAP_SPARK_LIFE <= 1200, "цель далеко, искры ловушки временные");
        check(AshLogic.TRAP_TAG.endsWith("_min") && AshLogic.TRAP_TAG.startsWith("ew"), "тег Искр ловушки оканчивается на _min: adopt считает их призванными");
        try {
            String rm = Files.readString(Path.of("src/com/explosionwand/RiftMobs.java")), mb = Files.readString(Path.of("src/com/explosionwand/AshMobsB.java")), dm = Files.readString(Path.of("src/com/explosionwand/DungeonMech.java"));
            check(rm.contains("t.equals(AshLogic.TRAP_TAG)") && rm.contains("am.expire = now + AshLogic.TRAP_SPARK_LIFE"), "adopt задаёт срок жизни Искре ловушки после перезагрузки");
            check(mb.contains("AshLogic.TRAP_TAG") && mb.contains("am.summoned = true") && mb.contains("Loot.wild("), "Искры ловушки с тегом ловушки, сундук огонька без светокамня");
            check(dm.contains("ewdw_min"), "мобы волн жаровен помечены _min (без материалов)");
        } catch (java.io.IOException e) { check(false, "чтение исходников: " + e); }
        check(Kind.ASH_WISP.host.equals("bat"), "хост огонька - летучая мышь (мирный режим её не стирает)");
        check(Mats.dungeonOf(Kind.ASH_WISP) == null && Mats.dungeonOf(Kind.ASH_WALKER) == Mats.Dungeon.ASH && Mats.dungeonOf(Kind.ASH_KEEPER) == Mats.Dungeon.ASH
                && Mats.strong(Kind.ASH_KEEPER) && Mats.strong(Kind.ASH_GUARD) && !Mats.strong(Kind.ASH_SPARK), "материалы Святилища с мобов (огонёк не даёт), элита щедрее");
    }

    static void modelTests() {
        int[] parts = {10, 8, 10, 13, 11, 18, 15, 24, 5};
        for (int n = 0; n < ASH.length; n++) {
            Kind k = ASH[n];
            check(k.parts() == parts[n] && k.blocks.length == k.light.length, k + ": частей " + k.parts());
            Pose q = new Pose(); q.sc = k.sc;
            for (int t = 0; t < 140; t += 7) {
                q.t = t; q.ph = t * 0.4; q.atk = (t % 20) / 20.0; q.strike = (t % 10) / 10.0; q.charge = (t % 11) / 11.0; q.stun = (t % 13) / 13.0;
                q.lift = (t % 9) / 9.0; q.open = (t % 8) / 8.0; q.crack = (t % 6) / 6.0; q.aim = t % 2;
                double[][] o = RiftModels.pose(k, q);
                for (int i = 0; i < o.length; i++) {
                    for (int j = 0; j < 10; j++) check(!Double.isNaN(o[i][j]) && !Double.isInfinite(o[i][j]), k + " часть " + i + ": конечное число");
                    check(o[i][1] - o[i][4] / 2 > -0.4, k + " часть " + i + ": не провалена под пол (" + (o[i][1] - o[i][4] / 2) + ")");
                }
            }
        }
        Pose s0 = new Pose(), s1 = new Pose(); s1.stun = 1;
        check(RiftModels.pose(Kind.ASH_GUARD, s1)[14][3] > RiftModels.pose(Kind.ASH_GUARD, s0)[14][3] * 2, "ядро на спине стража разгорается, когда он открыт");
        Pose w0 = new Pose(), w1 = new Pose(); w1.open = 1;
        check(RiftModels.pose(Kind.ASH_WORM, w0)[5][4] < 0.001 + 1e-9 && RiftModels.pose(Kind.ASH_WORM, w1)[5][4] > 0.3, "червь спрятан под лавой, пока не высунулся");
        Pose k0 = new Pose(), k1 = new Pose(); k0.sc = k1.sc = Kind.ASH_KEEPER.sc; k1.crack = 1;
        check(RiftModels.pose(Kind.ASH_KEEPER, k1)[16][4] > RiftModels.pose(Kind.ASH_KEEPER, k0)[16][4], "кристаллы Хранителя растут в ярости");
        Pose h0 = new Pose(), h1 = new Pose(); h1.atk = 1;
        check(RiftModels.pose(Kind.ASH_HOUND, h1)[0][1] < RiftModels.pose(Kind.ASH_HOUND, h0)[0][1], "гончая приседает перед прыжком");
    }

    static void frameTests() {
        for (Kind k : ASH) {
            check(k.hp > 0 && k.hp <= 1024 && RiftMobs.kindOfMark("explosionwand:" + k.id) == k && Kind.byId(k.id) == k && k.ash(), k + ": лимит ХП, метка, поиск по id");
            RiftMobs.Mob m = RiftMobs.make(k);
            check(m instanceof AshMobs.AMob, k + ": наследник AMob (срок жизни, общий каркас)");
            m.setup(k, "aa11bb", null);
            m.x = 1; m.y = 2; m.z = 3; m.initPose();
            String s = RiftMobs.summon(m, "ewd1h20");
            check(s.contains("\"ewr_h\"") && s.contains("\"ewd\"") && s.contains("DeathLootTable:\"minecraft:empty\"") && s.contains("explosionwand.mob." + k.id) && s.contains("NoAI:1b"), k + ": теги хоста, пустая добыча, имя");
            check(s.contains("zombie.spawn_reinforcements") == k.host.equals("husk"), k + ": подкрепление отключено у зомби-хостов");
            check(!m.summoned && m.tags.isEmpty(), k + ": по умолчанию не приспешник");
        }
        RiftMobs.Mob w = RiftMobs.make(Kind.ASH_WALKER);
        w.setup(Kind.ASH_WALKER, "aa11cc", null);
        w.tags = ",\"ewbk1\",\"ewb5_min\""; w.summoned = true; w.x = 1; w.y = 2; w.z = 3; w.initPose();
        String s = RiftMobs.summon(w, "ewd1h23");
        check(s.contains("\"ewbk1\",\"ewb5_min\"") && s.indexOf("\"ewb5_min\"") < s.indexOf("],NoAI"), "приспешник Феникса: теги ewb5_min и ключ боя внутри списка Tags");
        check(RiftMobs.make(Kind.ASH_KEEPER) instanceof AshMobsB.Keeper kp && !kp.rage && kp.up() && RiftMobs.make(Kind.ASH_GUARD) instanceof AshMobsB.Guard g && g.up() && !g.open(), "страж и Хранитель создаются со щитом");
        check(RiftMobs.make(Kind.ASH_WORM) instanceof AshMobsB.Worm wm && !wm.vulnerable(), "червь создаётся спрятанным и неуязвимым");
        check(RiftMobs.make(Kind.ASH_WISP) instanceof AshMobsB.Wisp, "огонёк создан");
        check(Kind.ASH_WALKER.host.equals("husk") && Kind.ASH_SPARK.host.equals("vex") && Kind.ASH_WORM.host.equals("strider") && Kind.ASH_GUARD.host.equals("iron_golem") && Kind.ASH_HOUND.host.equals("fox"), "хосты по роли");
        check(AshLogic.minionKind(0).equals("ash_spark") && AshLogic.minionKind(1).equals("ash_walker") && Kind.byId(AshLogic.minionKind(0)) == Kind.ASH_SPARK, "приспешники Феникса: Искра и Пеплоход");
        check(!Kind.ASH_SPARK.id.equals(Kind.LUNATIC.id) && Arrays.stream(Kind.values()).map(k -> k.id).distinct().count() == Kind.values().length, "id видов уникальны");
        check(RiftMobs.make(Kind.ASH_SPARK) instanceof AshMobs.AMob am && am.expire == 0, "Искра без срока жизни, пока не призвана ловушкой");
    }

    static void templateTests() {
        Map<Kind, Integer> total = new EnumMap<>(Kind.class);
        Set<String> other = new TreeSet<>();
        try {
            for (Dungeons.Hall h : Dungeons.ASH.halls())
                for (DungeonTemplate.Mark m : h.spawns) {
                    Kind k = RiftMobs.kindOfMark(m.a);
                    if (k != null) total.merge(k, 1, Integer::sum); else other.add(m.a);
                }
        } catch (IllegalStateException e) { check(false, "шаблоны Святилища: " + e.getMessage()); return; }
        check(other.isEmpty(), "ванильных заглушек в Святилище нет: " + other);
        for (Kind k : new Kind[]{Kind.ASH_WALKER, Kind.ASH_SPARK, Kind.ASH_HOUND, Kind.ASH_RINGER, Kind.ASH_WORM, Kind.ASH_PRIEST, Kind.ASH_GUARD, Kind.ASH_KEEPER}) check(total.getOrDefault(k, 0) > 0, "в Святилище есть " + k.id);
        check(total.getOrDefault(Kind.ASH_KEEPER, 0) == 1 && Dungeons.ASH.hall(Ash.GUARD_HALL).spawns.stream().anyMatch(m -> m.a.equals("explosionwand:ash_keeper")), "Хранитель один и стоит на арене (зал 20)");
        check(!total.containsKey(Kind.ASH_WISP) && total.getOrDefault(Kind.ASH_WALKER, 0) > total.getOrDefault(Kind.ASH_PRIEST, 0), "огонёк метками не ставится; Пеплоходов больше жрецов");
        long ringers = Dungeons.ASH.halls().stream().filter(h -> h.id <= 4).flatMap(h -> h.spawns.stream()).filter(m -> m.a.endsWith("ash_ringer")).count();
        check(ringers >= 3, "Звонари на этажах башни (залы 1-4): " + ringers);
        long worms = Dungeons.ASH.hall(12).spawns.stream().filter(m -> m.a.endsWith("ash_worm")).count();
        check(worms >= 2, "Магмовые черви на Огненной переправе (зал 12): " + worms);
        long hounds = total.getOrDefault(Kind.ASH_HOUND, 0);
        check(hounds >= 3, "гончих хватает на стаю: " + hounds);
    }

    static void langTests() throws Exception {
        String ru = Files.readString(Path.of("resources/assets/explosionwand/lang/ru_ru.json")), en = Files.readString(Path.of("resources/assets/explosionwand/lang/en_us.json"));
        List<String> keys = new ArrayList<>();
        for (Kind k : ASH) keys.add("explosionwand.mob." + k.id);
        for (String s : new String[]{"ash_ringer_wave", "ash_priest_circle", "ash_keeper_rage", "ash_keeper_rage.sub", "ash_keeper_slam", "ash_keeper_open", "ash_keeper_circle", "ash_keeper_dead", "ash_wisp_trap", "ash_wisp_chest"})
            keys.add("explosionwand.mob." + s);
        for (String k : keys) check(ru.contains("\"" + k + "\"") && en.contains("\"" + k + "\""), "текст есть в ru и en: " + k);
        check(!ru.contains("explosionwand.ash.spark") && !en.contains("explosionwand.ash.walker") && !ru.contains("explosionwand.ash.guard"), "ключи заглушек убраны");
        String all = Files.readString(Path.of("src/com/explosionwand/AshMobs.java")) + Files.readString(Path.of("src/com/explosionwand/AshMobsB.java"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(explosionwand\\.mob\\.[a-z_.]+)\"").matcher(all);
        while (m.find()) check(ru.contains("\"" + m.group(1) + "\"") && en.contains("\"" + m.group(1) + "\""), "ключ из кода есть в lang: " + m.group(1));
    }
}
