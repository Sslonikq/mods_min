package com.explosionwand;

import com.explosionwand.RiftModels.Kind;
import com.explosionwand.RiftModels.Pose;

import java.util.*;

/**
 * Мобы Города Спящих, часть A (фаза 4.6), чистая логика на заглушках:
 *  - Лунатик: шаги, бег, присед, свет, шум, цепочка пробуждения, успокоение, двойной урон по спящему;
 *  - Страж Шва: щит спереди, открытая спина, замах без щита, каждый четвёртый удар — выпад;
 *  - Дремотник и Ночная моль: константы, разгон Ветром, огоньки (хуки для палочек: регистрация, срок, гашение, порыв);
 *  - Сложная сложность: ни один удар не превышает 7 сердец без брони;
 *  - облик: числа частей, позы конечны, глаза Лунатика открываются, у Стража 12 частей;
 *  - каркас: метки explosionwand:<вид>, хосты и атрибуты, расстановка по шаблонам Города;
 * часть B (Сновидец и копии, Книжный страж, Статуя-страж, Колыбельщик, Туманный странник): взгляд, луч по фонарю, копии, веер страниц, кража, баланс, облик, шаблоны.
 */
public class CityMobsTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }

    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    public static void main(String[] args) throws Exception {
        lunaticTests();
        guardTests();
        flyerTests();
        balanceTests();
        modelTests();
        frameTests();
        hookTests();
        templateTests();
        gazeTests();
        dreamerTests();
        bookTests();
        cradlerTests();
        fogTests();
        partBModelTests();
        System.out.println(fails == 0 ? "мобы Города (части A и B) ok" : fails + " проверок не прошли");
        if (fails > 0) System.exit(1);
    }

    // ---------------------------------------------------------------- Лунатик
    static void lunaticTests() {
        check(!CityLogic.stepWakes(false, false, false, 0.5) && !CityLogic.stepWakes(false, true, false, 0.5), "стоящий игрок не будит, даже вплотную");
        check(!CityLogic.stepWakes(true, true, true, 1.0) && !CityLogic.stepWakes(true, false, true, 1.0), "крадущийся не будит");
        check(CityLogic.stepWakes(false, false, true, 1.5) && !CityLogic.stepWakes(false, false, true, 2.5), "шаг будит только вплотную (2 блока)");
        check(CityLogic.stepWakes(false, true, true, 5.5) && !CityLogic.stepWakes(false, true, true, 6.5), "бег будит с 6 блоков");
        check(CityLogic.lightWakes(true, 4.9) && !CityLogic.lightWakes(true, 5.1) && !CityLogic.lightWakes(false, 1.0), "свет в руке будит с 5 блоков, без света нет");
        check(CityLogic.noiseWakes(6.9) && !CityLogic.noiseWakes(7.1), "шум боя будит с 7 блоков");
        check(CityLogic.chainWakes(true, 3.0) && !CityLogic.chainWakes(true, 4.0) && !CityLogic.chainWakes(false, 1.0), "цепочка: только от проснувшегося и близко");
        check(CityLogic.sleeperHitMult(true) == 2.0 && CityLogic.sleeperHitMult(false) == 1.0, "спящий получает двойной урон");
        check(!CityLogic.calmsDown(0) && !CityLogic.calmsDown(CityLogic.CALM_AFTER - 1) && CityLogic.calmsDown(CityLogic.CALM_AFTER), "успокаивается через 8 с без игроков");
        check(CityLogic.isLightItem("torch") && CityLogic.isLightItem("lantern") && CityLogic.isLightItem("sea_lantern") && !CityLogic.isLightItem("stick") && !CityLogic.isLightItem(null),
                "предметы со светом");
        // ходьба слышна на 2 блока, бег на 6 — бег всегда не меньше ходьбы
        for (double d = 0; d < 10; d += 0.25)
            check(!CityLogic.stepWakes(false, false, true, d) || CityLogic.stepWakes(false, true, true, d), "бег слышнее ходьбы на " + d);
        Kind k = Kind.LUNATIC;
        check(k.id.equals("lunatic") && k.host.equals("husk") && k.hp >= 10 && k.hp <= 20, "Лунатик: хаск, ХП рядового");
        RiftMobs.Mob m = RiftMobs.make(Kind.LUNATIC);
        check(m instanceof CityMobs.Lunatic l && !l.awake, "Лунатик создаётся спящим");
    }

    // ---------------------------------------------------------------- Страж Шва
    static void guardTests() {
        check(near(CityLogic.shieldMult(1.0, true), CityLogic.SHIELD_MULT) && CityLogic.SHIELD_MULT < 0.5, "щит спереди гасит удар");
        check(CityLogic.shieldMult(1.0, false) == 1.0, "в замахе щит опущен: урон полный");
        check(CityLogic.shieldMult(0.0, true) == 1.0, "сбоку щит не защищает");
        check(CityLogic.shieldMult(-1.0, true) > 1.0 && CityLogic.shieldMult(-1.0, false) > 1.0, "со спины шов открыт: урон выше");
        check(CityLogic.shieldMult(0.5, true) == 1.0 && CityLogic.shieldMult(-0.5, true) == 1.0, "границы секторов не защищены");
        int lunges = 0;
        for (int s = 0; s <= 12; s++) if (CityLogic.isLunge(s)) lunges++;
        check(lunges == 3 && CityLogic.isLunge(4) && CityLogic.isLunge(8) && !CityLogic.isLunge(0) && !CityLogic.isLunge(3), "каждый четвёртый удар — выпад");
        check(CityLogic.LUNGE_WIND > CityLogic.SLASH_WIND && CityLogic.SLASH_WIND >= 12 && CityLogic.LUNGE_WIND >= 20, "телеграф: замах не короче 0,6 с, выпад не короче 1 с");
        check(Kind.STITCH_GUARD.hp == 30 && Kind.STITCH_GUARD.host.equals("husk"), "Страж Шва: 30 ХП как у заглушки, хаск");
        check(RiftMobs.make(Kind.STITCH_GUARD) instanceof CityMobs.StitchGuard g && g.guardUp(), "щит поднят в покое");
    }

    // ---------------------------------------------------------------- Дремотник и Ночная моль
    static void flyerTests() {
        check(Kind.DROWSY.host.equals("vex") && Kind.DROWSY.hp <= 20, "Дремотник: векс, хрупкий");
        check(CityLogic.RAY_AIM >= 20 && CityLogic.RAY_SLOW_TICKS > CityLogic.AURA_SLOW_TICKS && CityLogic.RAY_SLOW_AMP > CityLogic.AURA_SLOW_AMP, "луч дремоты телеграфируется и замедляет сильнее облака");
        check(CityLogic.RAY_SLOW_TICKS % 20 == 0 && CityLogic.AURA_SLOW_TICKS % 20 == 0 && CityLogic.MOTH_BLIND_TICKS % 20 == 0, "длительности эффектов в целых секундах (команда effect give)");
        check(CityLogic.mothSpeed(true) > CityLogic.mothSpeed(false) * 2 && near(CityLogic.mothSpeed(false), CityLogic.MOTH_SPEED), "Ветер разгоняет рой больше чем вдвое");
        check(CityLogic.snuffs(1.0) && !CityLogic.snuffs(2.0), "моль гасит огонёк, подлетев вплотную");
        check(CityLogic.MOTH_WIND >= 14, "замах роя не короче 0,7 с");
        check(Kind.NIGHT_MOTH.host.equals("phantom") && Kind.NIGHT_MOTH.parts() == 15 && Kind.NIGHT_MOTH.hp <= 12, "рой: фантом, 5 мотыльков по 3 части");
        check(RiftMobs.make(Kind.DROWSY) instanceof CityMobs.Drowsy && RiftMobs.make(Kind.NIGHT_MOTH) instanceof CityMobs.Moth, "виды создают свои классы");
    }

    // ---------------------------------------------------------------- баланс: Сложная
    static void balanceTests() {
        double[] raw = {CityLogic.LUNATIC_HIT, CityLogic.GUARD_SLASH, CityLogic.GUARD_LUNGE, CityLogic.MOTH_HIT, CityLogic.DROWSY_RAY};
        for (double r : raw) {
            double hard = RiftLogic.capHit(r, true) * RiftLogic.HARD_MULT;
            check(hard <= 14.0 + 1e-9, "удар " + r + " на Сложной " + hard + " не больше 7 сердец");
            check(RiftLogic.capHit(r, false) == r, "на Нормальной урон " + r + " не режется");
        }
        // даже усиленный в полтора раза не должен перескакивать порог (на Сложной режет capHit)
        for (double r : raw) check(RiftLogic.capHit(r * RiftLogic.BUFF_DAMAGE, true) * RiftLogic.HARD_MULT <= 14.0 + 1e-9, "усиленный удар " + r + " на Сложной");
        check(CityLogic.GUARD_LUNGE > CityLogic.GUARD_SLASH && CityLogic.GUARD_SLASH > CityLogic.LUNATIC_HIT && CityLogic.LUNATIC_HIT > CityLogic.MOTH_HIT, "выпад больнее взмаха, толпа и рой слабее стража");
    }

    // ---------------------------------------------------------------- облик
    static void modelTests() {
        check(Kind.LUNATIC.parts() == 10 && Kind.DROWSY.parts() == 7 && Kind.STITCH_GUARD.parts() == 12 && Kind.NIGHT_MOTH.parts() == 15, "число частей облика");
        Pose p = new Pose();
        p.t = 40; p.wake = 0;
        double[][] sleep = RiftModels.pose(Kind.LUNATIC, p);
        p.wake = 1;
        double[][] rage = RiftModels.pose(Kind.LUNATIC, p);
        check(sleep[3][3] > 0.25 && sleep[4][3] < 0.01, "спящий: глаза закрыты (щель есть, светящихся нет)");
        check(rage[4][3] > 0.25 && rage[3][3] < 0.01, "бешеный: глаза горят (щели нет)");
        check(sleep[5][7] != rage[5][7] || sleep[5][6] != rage[5][6] || sleep[5][9] != rage[5][9], "руки меняют положение с пробуждением");
        check(sleep[7][3] > 0.05 && rage[7][3] < 0.01, "кубики сна над головой гаснут при пробуждении");
        // щит опущен в замахе: пластина щита сдвигается
        Pose g1 = new Pose(), g2 = new Pose(); g2.lift = 1;
        double[][] up = RiftModels.pose(Kind.STITCH_GUARD, g1), down = RiftModels.pose(Kind.STITCH_GUARD, g2);
        check(Math.abs(up[9][1] - down[9][1]) > 0.1, "щит Стража опускается при замахе");
        // стоит на земле: ни одна часть не ниже пола (хост у ног), у летунов низ не уходит глубоко
        for (Kind k : new Kind[]{Kind.LUNATIC, Kind.STITCH_GUARD}) {
            Pose q = new Pose();
            for (int t = 0; t < 100; t += 5) {
                q.t = t; q.ph = t * 0.4; q.atk = (t % 20) / 20.0; q.strike = (t % 10) / 10.0; q.wake = (t % 30) / 30.0; q.lift = (t % 7) / 7.0; q.charge = (t % 11) / 11.0;
                double[][] o = RiftModels.pose(k, q);
                for (int i = 0; i < o.length; i++) {
                    double bottom = o[i][1] - o[i][4] / 2;
                    check(bottom > -0.3, k + " часть " + i + ": не провалена под пол (" + bottom + ")");
                }
            }
        }
        // рой стягивается при замахе
        Pose s1 = new Pose(), s2 = new Pose(); s1.t = s2.t = 10; s2.atk = 1;
        double[][] loose = RiftModels.pose(Kind.NIGHT_MOTH, s1), tight = RiftModels.pose(Kind.NIGHT_MOTH, s2);
        double r1 = 0, r2 = 0;
        for (int i = 0; i < 5; i++) { r1 += Math.hypot(loose[i * 3][0], loose[i * 3][2]); r2 += Math.hypot(tight[i * 3][0], tight[i * 3][2]); }
        check(r2 < r1 * 0.7, "рой стягивается к цели при замахе");
    }

    // ---------------------------------------------------------------- каркас
    static void frameTests() {
        for (Kind k : new Kind[]{Kind.LUNATIC, Kind.DROWSY, Kind.STITCH_GUARD, Kind.NIGHT_MOTH}) {
            check(RiftMobs.kindOfMark("explosionwand:" + k.id) == k, "метка explosionwand:" + k.id);
            check(Kind.byId(k.id) == k && k.hp > 0 && k.hp <= 1024, k + ": поиск по id и лимит ХП");
            RiftMobs.Mob m = RiftMobs.make(k);
            m.setup(k, "ff00aa", null);
            m.x = 1; m.y = 2; m.z = 3;
            m.initPose();
            String s = RiftMobs.summon(m, "ewd1h5");
            check(s.contains("\"ewr_h\"") && s.contains("\"ewd\"") && s.contains("DeathLootTable:\"minecraft:empty\"") && s.contains("explosionwand.mob." + k.id), k + ": теги хоста, пустая добыча, имя");
            boolean zombie = k.host.equals("husk");
            check(s.contains("zombie.spawn_reinforcements") == zombie, k + ": подкрепление отключено у зомби-хостов");
            check(s.contains("Size:3") == (k == Kind.NIGHT_MOTH), k + ": размер фантома только у роя");
        }
        check(RiftMobs.hostAttrs(Kind.SHADE).contains("spawn_reinforcements") && RiftMobs.hostAttrs(Kind.GOLEM).isEmpty(), "Тень (зомби) тоже без подкрепления, голем (разоритель) без лишних атрибутов");
        // имена мобов для lang — по id вида
        Set<String> ids = new HashSet<>();
        for (Kind k : Kind.values()) check(ids.add(k.id), "id вида уникален: " + k.id);
    }

    // ---------------------------------------------------------------- хуки для палочек (Огоньки, Ветер)
    static void hookTests() {
        RiftMobs.now = 100;
        CityMobs.LIGHTS.clear(); CityMobs.GUSTS.clear();
        CityMobs.Light l = CityMobs.registerLight(null, 10, 5, 10, 8, 200);
        check(l.alive() && CityMobs.nearestLight(null, 12, 5, 12, 10) == l, "огонёк виден мобам");
        check(CityMobs.nearestLight(null, 100, 5, 100, 10) == null, "далёкий огонёк не виден");
        l.snuff();
        check(!l.alive() && CityMobs.nearestLight(null, 12, 5, 12, 10) == null, "погашенный огонёк пропал");
        CityMobs.Light l2 = CityMobs.registerLight(null, 0, 0, 0, 5, 50);
        RiftMobs.now = 151;
        check(!l2.alive() && CityMobs.nearestLight(null, 0, 0, 0, 5) == null, "огонёк гаснет по сроку");
        check(CityMobs.LIGHTS.size() <= 256, "список огоньков ограничен");
        CityMobs.gust(null, 0, 0, 0);
        check(CityMobs.gustNear(null, 5, 0, 5) && !CityMobs.gustNear(null, 30, 0, 0), "порыв разгоняет только рядом");
        RiftMobs.now += CityLogic.GUST_TICKS + 1;
        check(!CityMobs.gustNear(null, 1, 0, 0), "порыв проходит");
        CityMobs.clear();
        check(CityMobs.LIGHTS.isEmpty() && CityMobs.GUSTS.isEmpty(), "clear чистит статику");
        RiftMobs.now = 0;
    }

    // ---------------------------------------------------------------- шаблоны города
    static void templateTests() {
        Map<Kind, Integer> total = new EnumMap<>(Kind.class);
        Set<String> stubs = new TreeSet<>();
        try {
            for (Dungeons.Hall h : Dungeons.SLEEP.halls())
                for (DungeonTemplate.Mark m : h.spawns) {
                    Kind k = RiftMobs.kindOfMark(m.a);
                    if (k != null) total.merge(k, 1, Integer::sum); else stubs.add(m.a);
                }
        } catch (IllegalStateException e) { check(false, "шаблоны города: " + e.getMessage()); return; }
        for (Kind k : new Kind[]{Kind.LUNATIC, Kind.DROWSY, Kind.STITCH_GUARD, Kind.NIGHT_MOTH}) check(total.getOrDefault(k, 0) > 0, "в Городе есть " + k.id);
        check(total.getOrDefault(Kind.LUNATIC, 0) > total.getOrDefault(Kind.STITCH_GUARD, 0), "Лунатиков больше стражей (толпа)");
        check(total.getOrDefault(Kind.NIGHT_MOTH, 0) < total.getOrDefault(Kind.LUNATIC, 0), "моли реже толпы");
        check(stubs.isEmpty(), "заглушек в шаблонах Города нет: " + stubs);
        for (Kind k : new Kind[]{Kind.DREAMER, Kind.BOOK_GUARD, Kind.STATUE_GUARD, Kind.CRADLER}) check(total.getOrDefault(k, 0) > 0, "в Городе есть " + k.id);
        check(total.getOrDefault(Kind.CRADLER, 0) == 1, "Колыбельщик один на город: " + total.getOrDefault(Kind.CRADLER, 0));
        check(!total.containsKey(Kind.DREAM_CLONE) && !total.containsKey(Kind.FOG_WALKER), "копии и странник не ставятся метками");
        check(!total.containsKey(Kind.SHARDLING) && !total.containsKey(Kind.GOLEM), "мобов Разлома в Городе нет");
    }

    // ---------------------------------------------------------------- взгляд (Статуя-страж, Туманный странник)
    static void gazeTests() {
        // игрок в начале координат смотрит вдоль +z; глаза на высоте 1,6
        check(CityLogic.seenBy(0, 1.6, 0, 0, 0, 1, 0, 1.1, 10), "прямо перед глазами: видна");
        check(!CityLogic.seenBy(0, 1.6, 0, 0, 0, 1, 0, 1.1, -10), "за спиной: не видна");
        check(!CityLogic.seenBy(0, 1.6, 0, 0, 0, 1, 10, 1.1, 0.5), "строго сбоку (90 градусов): не видна");
        check(CityLogic.seenBy(0, 1.6, 0, 0, 0, 1, 5, 1.1, 10), "на краю экрана (27 градусов): видна");
        check(!CityLogic.seenBy(0, 1.6, 0, 0, 0, 1, 10, 1.1, 5), "63 градуса от взгляда: не видна");
        check(!CityLogic.seenBy(0, 1.6, 0, 0, 0, 1, 0, 1.1, CityLogic.SEEN_RANGE + 1), "дальше SEEN_RANGE не видна");
        check(CityLogic.seenBy(0, 1.6, 0, 0, 0, 1, 0, 1.1, 0.3), "вплотную видна всегда");
        check(!CityLogic.seenBy(0, 1.6, 0, 0, 0, 0, 0, 1.1, 5), "нулевой взгляд: не видна, без деления на ноль");
        check(CityLogic.seenBy(0, 1.6, 0, 0, -0.5, 1, 0, 0.2, 3), "взгляд вниз на статую у пола");
        check(CityLogic.statueMoves(false) && !CityLogic.statueMoves(true), "статуя идёт, только когда на неё не смотрят");
        check(near(CityLogic.SEEN_COS, 0.55), "порог видимости");
        check(CityLogic.STATUE_WIND >= 14 && CityLogic.STATUE_TOUCH <= 9, "касание статуи телеграфируется 0,7 с и не слишком больно");
        double[] w = CityLogic.toWorld(0, 1, 2);
        check(near(w[0], 1) && near(w[1], 2), "yaw 0: вперёд = +z, вправо = +x");
        w = CityLogic.toWorld(Math.PI / 2, 0, 1);
        check(near(w[0], 1) && Math.abs(w[1]) < 1e-9, "yaw 90: вперёд = +x");
    }

    // ---------------------------------------------------------------- Сновидец
    static void dreamerTests() {
        check(CityLogic.cloneSpawn(0, 1.0) == 2 && CityLogic.cloneSpawn(0, 0.4) == 3, "копий 2, ниже половины ХП 3");
        check(CityLogic.cloneSpawn(2, 1.0) == 1 && CityLogic.cloneSpawn(3, 0.2) == 0 && CityLogic.cloneSpawn(5, 0.2) == 0, "лимит копий: не больше " + CityLogic.CLONE_CAP);
        int alive = 0;
        for (int i = 0; i < 50; i++) alive += CityLogic.cloneSpawn(alive, 0.3);
        check(alive == CityLogic.CLONE_CAP, "бесконечного размножения нет: " + alive);
        check(CityLogic.sleepAffects(6.0, true) && !CityLogic.sleepAffects(7.0, true) && !CityLogic.sleepAffects(3.0, false), "усыпление: круг и прямая видимость");
        check(CityLogic.SLEEP_WIND >= 28 && CityLogic.CLONE_LIFE <= 300 && CityLogic.CLONE_WORLD_CAP >= CityLogic.CLONE_CAP, "телеграф 1,4 с, копии живут не дольше 15 с");
        check(CityLogic.SLEEP_SLOW_TICKS % 20 == 0 && CityLogic.SLEEP_WEAK_TICKS % 20 == 0 && CityLogic.SLEEP_SLOW_AMP <= 1, "замедление II и слабость целыми секундами, без блокировки");
        check(Kind.DREAMER.host.equals("evoker") && Kind.DREAM_CLONE.host.equals("evoker") && Kind.DREAMER.hp == 36 && Kind.DREAMER.parts() == 12 && Kind.DREAM_CLONE.parts() == 12 && Kind.DREAM_CLONE.hp == 1, "Сновидец: хост, ХП, копия 1 ХП той же формы");
        check(RiftMobs.make(Kind.DREAMER) instanceof CityMobsB.Dreamer && RiftMobs.make(Kind.DREAM_CLONE) instanceof CityMobsB.Clone, "виды создают свои классы");
        check(!Kind.DREAM_CLONE.blocks[0].equals(Kind.DREAMER.blocks[0]) && Kind.DREAM_CLONE.blocks[0].contains("glass"), "копия стеклянная, настоящий нет");
        check(((CityMobsB.Clone) RiftMobs.make(Kind.DREAM_CLONE)).owner == null, "копия без хозяина (после перезапуска) не знает владельца и уберётся при первом тике");
        check(CityMobsB.QUEUE.isEmpty(), "очередь отложенных действий пуста");
        int[] ran = {0};
        CityMobsB.QUEUE.add(() -> ran[0]++);
        CityMobsB.QUEUE.add(() -> { throw new IllegalStateException("тест: сбой одного действия"); });
        CityMobsB.QUEUE.add(() -> ran[0]++);
        RiftMobs.warned = true;
        CityMobsB.flush();
        RiftMobs.warned = false;
        check(CityMobsB.QUEUE.isEmpty() && ran[0] == 2, "flush выполняет все действия и чистит очередь, сбой одного не мешает: " + ran[0]);
        CityMobsB.QUEUE.add(() -> {});
        CityMobsB.clear();
        check(CityMobsB.QUEUE.isEmpty(), "clear чистит очередь");
    }

    // ---------------------------------------------------------------- Книжный страж
    static void bookTests() {
        double[] f = CityLogic.fan(CityLogic.PAGE_COUNT, CityLogic.PAGE_SPREAD);
        check(f.length == 3 && near(f[1], 0) && near(f[0], -f[2]) && f[2] > 0, "веер: средняя страница прямо, остальные симметрично");
        check(CityLogic.fan(1, 0.5)[0] == 0 && CityLogic.fan(2, 0.2).length == 2, "веер из одной и двух страниц");
        check(CityLogic.PAGE_AIM >= 20 && CityLogic.PAGE_SPEED * CityLogic.PAGE_LIFE >= 20 && CityLogic.PAGE_SPEED * CityLogic.PAGE_LIFE <= CityLogic.PAGE_RANGE * 1.1, "прицел не короче 1 с, дальность страницы ~22 блока");
        check(CityLogic.PAGE_SPEED <= 0.6, "страница не быстрее 12 блоков в секунду: её видно и можно уйти");
        double hard = RiftLogic.capHit(CityLogic.PAGE_HIT, true) * RiftLogic.HARD_MULT;
        check(hard <= 14.0 + 1e-9 && CityLogic.PAGE_HIT <= 4, "страница на Сложной не больше 7 сердец");
        check(CityLogic.PAGE_COUNT * CityLogic.PAGE_HIT <= 9 && CityLogic.PAGE_CD >= 100, "залп 9 урона в худшем случае, перезарядка >= 5 с");
        check(Kind.BOOK_GUARD.host.equals("phantom") && Kind.BOOK_GUARD.parts() == 15 && Kind.BOOK_GUARD.hp <= 20, "Книжный страж: фантом, три книги по 4 части и три листа");
        check(RiftMobs.hostExtra(Kind.BOOK_GUARD).equals("Size:2,") && RiftMobs.hostExtra(Kind.NIGHT_MOTH).equals("Size:3,") && RiftMobs.hostExtra(Kind.STATUE_GUARD).isEmpty(), "размер фантома-хоста");
        check(RiftMobs.make(Kind.BOOK_GUARD) instanceof CityMobsB.BookGuard, "класс Книжного стража");
        Pose a = new Pose(), b = new Pose(); a.t = b.t = 10; b.charge = 1;
        double[][] idle = RiftModels.pose(Kind.BOOK_GUARD, a), aim = RiftModels.pose(Kind.BOOK_GUARD, b);
        double spread = Math.abs(aim[0][0] - aim[8][0]), idleSpread = Math.abs(idle[0][0] - idle[8][0]);
        check(spread > 0.5 && Math.abs(spread - idleSpread) > 0.01, "прицел: книги веером вперёд");
        check(aim[3][3] > idle[3][3], "прицел: руна светится сильнее");
    }

    // ---------------------------------------------------------------- Колыбельщик
    static void cradlerTests() {
        check(CityLogic.rayNear(0, 1.6, 0, 0, 0, 1, 0, 1.6, 5, 0.1), "луч точно в цель");
        check(CityLogic.rayNear(0, 1.6, 0, 0, 0, 1, 0.5, 1.6, 5, 0.75) && !CityLogic.rayNear(0, 1.6, 0, 0, 0, 1, 1.2, 1.6, 5, 0.75), "луч рядом с целью и мимо");
        check(!CityLogic.rayNear(0, 1.6, 0, 0, 0, 1, 0, 1.6, -5, 0.75), "цель за спиной луча не задета");
        check(!CityLogic.rayNear(0, 1.6, 0, 0, 0, 0, 0, 1.6, 5, 0.75), "нулевой взгляд не задевает");
        check(CityLogic.rayNear(0, 5, 0, 0, -1, 1, 0, 2, 3, 0.2), "луч под наклоном");
        Pose p = new Pose(); p.t = 20;
        double[] l0 = CityModels.lanternLocal(p);
        check(l0[1] > 0.3 && l0[1] < 1.6 && l0[0] > 0.3 && l0[2] > 0.2, "фонарь у правого бедра впереди: " + Arrays.toString(l0));
        p.charge = 1;
        double[] l1 = CityModels.lanternLocal(p);
        check(Math.abs(l1[0] - l0[0]) + Math.abs(l1[1] - l0[1]) + Math.abs(l1[2] - l0[2]) > 0.2, "при звоне фонарь поднят и качается");
        double lo = 9, hi = -9;
        for (int t = 0; t < 60; t++) { p.t = t; double x = CityModels.lanternLocal(p)[0]; lo = Math.min(lo, x); hi = Math.max(hi, x); }
        check(hi - lo > 0.5, "размах качания фонаря " + (hi - lo));
        Pose q = new Pose(); q.t = 0;
        double[] lq = CityModels.lanternLocal(q);
        check(Math.hypot(lq[0], lq[2]) < 1.4, "фонарь не дальше 1,4 от оси хоста");
        check(CityLogic.cradlerBodyMult(true) == 0.25 && CityLogic.cradlerBodyMult(false) == 1.0, "пока фонарь цел, тело получает четверть");
        check(CityLogic.LANTERN_HP >= 20 && CityLogic.LANTERN_HP <= 60 && Kind.CRADLER.hp > CityLogic.LANTERN_HP * 3, "ХП фонаря 20..60 и намного меньше тела");
        check(CityLogic.chimeAffects(12, true) && !CityLogic.chimeAffects(14, true) && !CityLogic.chimeAffects(5, false), "звон: радиус 13, стена спасает");
        check(CityLogic.CHIME_WIND >= 28 && CityLogic.FIST_WIND >= 20 && CityLogic.FIST_WIND2 >= 14, "телеграфы звона и кулака не короче 0,7 с");
        check(CityLogic.CHIME_SLOW_TICKS % 20 == 0 && CityLogic.CHIME_WEAK_TICKS % 20 == 0 && CityLogic.CHIME_SLOW_AMP <= 2, "эффекты звона целыми секундами, без остановки игрока");
        check(CityLogic.cradlerSpeed(false) > CityLogic.cradlerSpeed(true), "без фонаря быстрее");
        double hard = RiftLogic.capHit(CityLogic.CRADLER_FIST, true) * RiftLogic.HARD_MULT;
        check(hard <= 14.0 + 1e-9 && RiftLogic.capHit(CityLogic.CRADLER_FIST * RiftLogic.BUFF_DAMAGE, true) * RiftLogic.HARD_MULT <= 14.0 + 1e-9, "кулак на Сложной не больше 7 сердец");
        check(CityLogic.CRADLER_FIST > CityLogic.GUARD_LUNGE, "элита бьёт больнее стража");
        check(Kind.CRADLER.host.equals("iron_golem") && Kind.CRADLER.parts() == 20 && Kind.CRADLER.hp <= 1024, "Колыбельщик: голем-хост, 20 частей");
        check(RiftMobs.make(Kind.CRADLER) instanceof CityMobsB.Cradler c && c.lampHp == CityLogic.LANTERN_HP && c.lamp(), "создаётся с целым фонарём");
        Pose g1 = new Pose(), g2 = new Pose(); g1.t = g2.t = 5; g2.stun = 1;
        double[][] on = RiftModels.pose(Kind.CRADLER, g1), off = RiftModels.pose(Kind.CRADLER, g2);
        check(on[9][3] > 0.3 && off[9][3] < 0.1 && off[12][3] < 0.01 && on[14][4] > 0.1 && off[14][4] < 0.01, "разбитый фонарь гаснет, осколки опадают");
        check(Math.abs(on[9][0] - CityModels.lanternLocal(g1)[0]) < 1e-9 && Math.abs(on[9][1] - CityModels.lanternLocal(g1)[1]) < 1e-9, "лампа нарисована там, где проверяется удар");
    }

    // ---------------------------------------------------------------- Туманный странник
    static void fogTests() {
        check(CityLogic.canSteal("minecraft:apple", false) && CityLogic.canSteal("minecraft:cobblestone", false) && CityLogic.canSteal("minecraft:iron_sword", false), "обычные вещи красть можно");
        check(!CityLogic.canSteal("minecraft:apple", true), "вещи с NBT (чары, имя, данные) нельзя");
        check(!CityLogic.canSteal("explosionwand:temple_seal", false) && !CityLogic.canSteal("explosionwand:seal_shard_rift", false) && !CityLogic.canSteal("explosionwand:fire_wand", false), "предметы мода (Печати, Осколки, палочки) нельзя");
        for (String bad : new String[]{"minecraft:netherite_sword", "minecraft:diamond", "minecraft:elytra", "minecraft:totem_of_undying", "minecraft:nether_star", "minecraft:shulker_box",
                "minecraft:written_book", "minecraft:enchanted_golden_apple", "minecraft:ender_chest", "minecraft:trident", "minecraft:echo_shard", "minecraft:air", "minecraft:bundle"})
            check(!CityLogic.canSteal(bad, false), "ценное не крадётся: " + bad);
        check(!CityLogic.canSteal(null, false) && !CityLogic.canSteal("", false), "пусто нельзя");
        check(CityLogic.fogSpawns(true, false, 0, 10) && CityLogic.fogSpawns(false, true, 1, 24), "в тумане (ночь или дождь) появляется");
        check(!CityLogic.fogSpawns(false, false, 0, 0), "днём в ясную погоду нет");
        check(!CityLogic.fogSpawns(true, false, CityLogic.FOG_CAP, 0) && !CityLogic.fogSpawns(true, false, 0, 25), "лимит у города и редкость (25%)");
        int spawned = 0, have = 0;
        for (int i = 0; i < 1000; i++) if (CityLogic.fogSpawns(true, false, have, i % 100)) { spawned++; have++; }
        check(spawned == CityLogic.FOG_CAP, "без убыли не больше FOG_CAP: " + spawned);
        check(CityLogic.FOG_FLEE >= 200 && CityLogic.FOG_RUN > CityLogic.FOG_SPEED * 2 && CityLogic.FOG_SPEED_SEEN < CityLogic.FOG_SPEED, "бегство 15 с, при взгляде почти замирает");
        check(CityLogic.FOG_GRAB_WIND >= 14 && CityLogic.FOG_GRAB_R < CityLogic.FOG_GRAB_RANGE, "замах кражи не короче 0,7 с, можно отойти");
        check(Kind.FOG_WALKER.host.equals("husk") && Kind.FOG_WALKER.hp <= 20 && Kind.FOG_WALKER.parts() == 13, "Странник: хаск-хост, хрупкий, 13 частей");
        check(RiftMobs.make(Kind.FOG_WALKER) instanceof CityMobsB.FogWalker fw && !fw.carrying && fw.leash >= 100, "создаётся без вещи, с большим поводком для бегства");
        RiftMobs.Mob w = RiftMobs.make(Kind.FOG_WALKER);
        w.setup(Kind.FOG_WALKER, "ff00ab", null);
        w.x = 1; w.y = 2; w.z = 3; w.initPose();
        String s = RiftMobs.summon(w, null);
        check(s.contains("ewrk_fog_walker") && !s.contains("\"ewd\"") && s.contains("DeathLootTable:\"minecraft:empty\""), "странник без тега зала: зачистка залов его не касается");
        check(!s.contains("Id:12") && RiftMobs.hostEffects(Kind.NIGHT_MOTH).contains("Id:12") && RiftMobs.hostEffects(Kind.BOOK_GUARD).contains("Id:12")
                && RiftMobs.hostEffects(Kind.PRISM).contains("Id:12") && RiftMobs.hostEffects(Kind.DREAMER).isEmpty() && RiftMobs.hostEffects(Kind.LUNATIC).isEmpty(), "огнестойкость только у фантомов-хостов");
        RiftMobs.Mob mo = RiftMobs.make(Kind.NIGHT_MOTH);
        mo.setup(Kind.NIGHT_MOTH, "ff00ac", null);
        mo.x = 1; mo.y = 2; mo.z = 3; mo.initPose();
        String ms = RiftMobs.summon(mo, null);
        check(ms.contains("Duration:-1,ShowParticles:0b},{Id:12,Amplifier:0b,Duration:-1,ShowParticles:0b}],CustomName"), "в summon моли огнестойкость внутри ActiveEffects");
        check(Kind.DREAMER.host.equals(Kind.DREAM_CLONE.host) && !Kind.DREAMER.host.equals("illusioner"), "иллюзионист-хост рисует 4 ванильных копии - запрещён");
        check(RiftMobs.kindOfMark("explosionwand:fog_walker") == Kind.FOG_WALKER && Kind.byId("dream_clone") == Kind.DREAM_CLONE, "виды находятся по id (нужно для adopt после перезапуска)");
    }

    // ---------------------------------------------------------------- облик и каркас части B
    static void partBModelTests() {
        check(Kind.DREAMER.parts() == 12 && Kind.BOOK_GUARD.parts() == 15 && Kind.STATUE_GUARD.parts() == 10 && Kind.CRADLER.parts() == 20 && Kind.FOG_WALKER.parts() == 13, "число частей облика части B");
        for (Kind k : new Kind[]{Kind.DREAMER, Kind.DREAM_CLONE, Kind.BOOK_GUARD, Kind.STATUE_GUARD, Kind.CRADLER, Kind.FOG_WALKER}) {
            check(k.blocks.length == k.light.length, k + ": блоков и яркостей поровну");
            Pose q = new Pose();
            for (int t = 0; t < 120; t += 7) {
                q.t = t; q.ph = t * 0.4; q.atk = (t % 20) / 20.0; q.strike = (t % 10) / 10.0; q.charge = (t % 11) / 11.0; q.stun = (t % 13) / 13.0; q.lift = 0;
                double[][] o = RiftModels.pose(k, q);
                for (int i = 0; i < o.length; i++) {
                    for (int j = 0; j < 10; j++) check(!Double.isNaN(o[i][j]) && !Double.isInfinite(o[i][j]), k + " часть " + i + ": конечное число");
                    if (k != Kind.BOOK_GUARD) check(o[i][1] - o[i][4] / 2 > -0.35, k + " часть " + i + ": не провалена под пол (" + (o[i][1] - o[i][4] / 2) + ")");
                }
            }
            check(RiftMobs.kindOfMark("explosionwand:" + k.id) == k && Kind.byId(k.id) == k && k.hp > 0 && k.hp <= 1024, k + ": метка, поиск по id, лимит ХП");
            RiftMobs.Mob m = RiftMobs.make(k);
            m.setup(k, "ee11cc", null);
            m.x = 1; m.y = 2; m.z = 3; m.initPose();
            String s = RiftMobs.summon(m, "ewd1h6");
            check(s.contains("\"ewr_h\"") && s.contains("DeathLootTable:\"minecraft:empty\"") && s.contains("explosionwand.mob." + k.id) && s.contains("NoAI:1b"), k + ": теги хоста, пустая добыча, имя");
            check(s.contains("zombie.spawn_reinforcements") == k.host.equals("husk"), k + ": подкрепление отключено у зомби-хостов");
        }
        Pose a = new Pose(), b = new Pose(); a.ph = 0; b.ph = 1.3;
        double[][] s1 = RiftModels.pose(Kind.STATUE_GUARD, a), s2 = RiftModels.pose(Kind.STATUE_GUARD, b);
        double diff = 0;
        for (int i = 0; i < s1.length; i++) for (int j = 0; j < 10; j++) diff += Math.abs(s1[i][j] - s2[i][j]);
        check(diff > 0.3, "статуя в новой позе после шага");
        Pose c = new Pose(); c.t = 30; c.ph = 2.0; c.atk = 0;
        Pose d = new Pose(); d.t = 99; d.ph = 2.0; d.atk = 0;
        double[][] f1 = RiftModels.pose(Kind.STATUE_GUARD, c), f2 = RiftModels.pose(Kind.STATUE_GUARD, d);
        double same = 0;
        for (int i = 0; i < f1.length; i++) for (int j = 0; j < 10; j++) same += Math.abs(f1[i][j] - f2[i][j]);
        check(same < 1e-9, "замершая статуя неподвижна (поза не зависит от времени)");
        Pose e0 = new Pose(), e1 = new Pose(); e1.atk = 1;
        check(RiftModels.pose(Kind.STATUE_GUARD, e1)[8][3] > RiftModels.pose(Kind.STATUE_GUARD, e0)[8][3], "глаза горят, когда статуя идёт");
        Pose h0 = new Pose(), h1 = new Pose(); h1.charge = 1;
        check(RiftModels.pose(Kind.DREAMER, h1)[5][1] != RiftModels.pose(Kind.DREAMER, h0)[5][1], "Сновидец поднимает руки при заклинании");
        Pose f0 = new Pose(), fs = new Pose(); fs.charge = 1;
        check(RiftModels.pose(Kind.FOG_WALKER, fs)[1][1] < RiftModels.pose(Kind.FOG_WALKER, f0)[1][1] - 0.1, "Странник припадает, когда на него смотрят");
    }
}
