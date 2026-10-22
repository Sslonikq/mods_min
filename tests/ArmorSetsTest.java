package com.explosionwand;

import com.explosionwand.ArmorSetLogic.Tier;
import com.explosionwand.RiftModels.Kind;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Сеты брони данжей (4.12), чистая логика: ранги и шансы, веса частей, множители бонусов, цифры материалов, имена текстур. */
public class ArmorSetsTest {
    static int fails = 0;

    static void check(boolean ok, String msg) { if (!ok) { fails++; System.out.println("FAIL: " + msg); } }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    public static void main(String[] args) throws Exception {
        tiers();
        chances();
        parts();
        bonuses();
        fire();
        materials();
        names();
        beams();
        if (fails > 0) { System.out.println(fails + " FAILED"); System.exit(1); }
        System.out.println("ArmorSetsTest OK");
    }

    static void tiers() {
        // каждый вид: часть сета даёт только моб данжа (без жука, копий, мелких, огонька)
        for (Kind k : Kind.values()) {
            boolean none = Mats.dungeonOf(k) == null || k == Kind.BEETLE;
            check((ArmorSetLogic.tier(k) == Tier.NONE) == none, "tier NONE у " + k);
        }
        check(ArmorSetLogic.tier(Kind.SHARDLING) == Tier.COMMON, "осколочник рядовой");
        check(ArmorSetLogic.tier(Kind.ASH_WALKER) == Tier.COMMON && ArmorSetLogic.tier(Kind.LUNATIC) == Tier.COMMON, "рядовые");
        check(ArmorSetLogic.tier(Kind.ARCHER) == Tier.RANGED && ArmorSetLogic.tier(Kind.ASH_PRIEST) == Tier.RANGED && ArmorSetLogic.tier(Kind.DREAMER) == Tier.RANGED, "стрелки и поддержка");
        check(ArmorSetLogic.tier(Kind.EATER) == Tier.ELITE && ArmorSetLogic.tier(Kind.ASH_GUARD) == Tier.ELITE && ArmorSetLogic.tier(Kind.STATUE_GUARD) == Tier.ELITE, "элита");
        check(ArmorSetLogic.tier(Kind.GOLEM) == Tier.MINI && ArmorSetLogic.tier(Kind.CRADLER) == Tier.MINI && ArmorSetLogic.tier(Kind.ASH_KEEPER) == Tier.MINI, "мини-боссы");
        // у каждого данжа есть мини-босс и рядовой
        for (Mats.Dungeon d : Mats.Dungeon.values()) {
            boolean mini = false, common = false;
            for (Kind k : Kind.values()) if (Mats.dungeonOf(k) == d) { mini |= ArmorSetLogic.tier(k) == Tier.MINI; common |= ArmorSetLogic.tier(k) == Tier.COMMON; }
            check(mini && common, "в данже " + d + " есть мини-босс и рядовой");
        }
        check(ArmorSets.Set.values().length == 3, "три сета");
        for (Mats.Dungeon d : Mats.Dungeon.values()) check(ArmorSets.Set.of(d) != null, "у данжа " + d + " есть сет");
    }

    static void chances() {
        check(near(ArmorSetLogic.chance(Tier.COMMON, 0), 0.015), "рядовой 1,5%");
        check(near(ArmorSetLogic.chance(Tier.RANGED, 0), 0.02), "стрелок 2%");
        check(near(ArmorSetLogic.chance(Tier.ELITE, 0), 0.06), "элита 6%");
        check(near(ArmorSetLogic.chance(Tier.MINI, 0), 0.20), "мини-босс 20%");
        check(ArmorSetLogic.chance(Tier.NONE, 3) == 0, "NONE не даёт даже с добычей");
        check(near(ArmorSetLogic.chance(Tier.COMMON, 3), 0.045), "добыча III: 4,5%");
        check(ArmorSetLogic.chance(Tier.MINI, 100) == 1.0, "шанс не больше 1");
        check(near(ArmorSetLogic.chance(Tier.COMMON, -2), 0.015), "отрицательная добыча игнорируется");
        // частота броска близка к шансу
        Random r = new Random(7);
        boolean[] have = new boolean[4];
        int hit = 0, n = 200000;
        for (int i = 0; i < n; i++) if (ArmorSetLogic.roll(Tier.ELITE, 0, have, r) >= 0) hit++;
        check(Math.abs(hit / (double) n - 0.06) < 0.004, "частота элиты ~6%: " + hit / (double) n);
        check(ArmorSetLogic.roll(Tier.NONE, 0, have, r) == -1, "NONE: -1");
    }

    static void parts() {
        Random r = new Random(3);
        int[] cnt = new int[4];
        boolean[] have = {true, false, false, false};
        for (int i = 0; i < 40000; i++) cnt[ArmorSetLogic.pickPart(have, r)]++;
        check(cnt[0] > 5000 && cnt[0] < 6500, "имеющаяся часть весит 1/7: " + cnt[0]);
        for (int i = 1; i < 4; i++) check(cnt[i] > 10500 && cnt[i] < 12500, "недостающая часть весит 2/7: " + cnt[i]);
        boolean[] all = {true, true, true, true};
        int[] c2 = new int[4];
        for (int i = 0; i < 20000; i++) c2[ArmorSetLogic.pickPart(all, r)]++;
        for (int i = 0; i < 4; i++) check(c2[i] > 4500 && c2[i] < 5500, "все части равны: " + c2[i]);
        for (int i = 0; i < 1000; i++) { int p = ArmorSetLogic.pickPart(have, r); check(p >= 0 && p < 4, "часть в диапазоне"); }
    }

    static void bonuses() {
        check(near(ArmorSetLogic.dmgBonus(4), 0.15) && near(ArmorSetLogic.dmgBonus(2), 0.075) && ArmorSetLogic.dmgBonus(0) == 0, "урон по своему данжу: 15% у полного сета");
        check(near(ArmorSetLogic.dmgBonus(9), 0.15), "урон не растёт выше 4 частей");
        check(ArmorSetLogic.knockMul(1) == 1.0F && ArmorSetLogic.knockMul(2) == 0.9F && ArmorSetLogic.knockMul(4) == 0.9F, "отбрасывание -10% с 2 частей");
        check(!ArmorSetLogic.reflects(3, 0.0) && ArmorSetLogic.reflects(4, 0.19) && !ArmorSetLogic.reflects(4, 0.2), "отражение 20% только при 4 частях");
        check(ArmorSetLogic.edgeReady(0, -1) && !ArmorSetLogic.edgeReady(100, 0) && ArmorSetLogic.edgeReady(160, 0) && !ArmorSetLogic.edgeReady(159, 0), "перезарядка «Грани» 8 с");
        check(ArmorSetLogic.EDGE_TICKS == 60 && ArmorSetLogic.EDGE_ARMOR == 2.0, "+2 брони на 3 с");
        check(ArmorSetLogic.noticeMul(0, false) == 1.0 && ArmorSetLogic.noticeMul(1, true) == 1.0, "меньше 2 частей: без эффекта");
        check(near(ArmorSetLogic.noticeMul(2, false), 0.75) && near(ArmorSetLogic.noticeMul(2, true), 0.75), "2 части: -25%, присед не помогает");
        check(near(ArmorSetLogic.noticeMul(4, false), 0.75) && near(ArmorSetLogic.noticeMul(4, true), 0.45), "4 части: присед сильнее прячет");
    }

    static void fire() {
        check(ArmorSetLogic.fireNext(100, 1) == 100 && ArmorSetLogic.fireNext(100, 0) == 100, "до 2 частей горение не меняется");
        check(ArmorSetLogic.fireNext(100, 2) == 99, "2 части: убывает на 1 тик больше");
        // горение 8 с (160 тиков): ваниль -1 за тик, с 2 частями -2 за тик: втрое... ровно вдвое короче
        int t = 160, ticks = 0;
        while (t > 0) { t -= 1; t = ArmorSetLogic.fireNext(t, 2); ticks++; }
        check(Math.abs(ticks - 80) <= 1, "горение вдвое короче: " + ticks);
        check(ArmorSetLogic.fireNext(300, 4) == 60 && ArmorSetLogic.fireNext(40, 4) == 39, "4 части: горение не дольше 3 с");
        check(ArmorSetLogic.fireNext(0, 4) == 0 && ArmorSetLogic.fireNext(-20, 4) == -20, "нет огня - не трогаем");
        check(near(ArmorSetLogic.fireDamage("onFire", 10F, 4), 6.0) && near(ArmorSetLogic.fireDamage("inFire", 10F, 4), 6.0), "огонь -40%");
        check(ArmorSetLogic.fireDamage("lava", 10F, 4) == 10F, "лава не смягчается");
        check(ArmorSetLogic.fireDamage("onFire", 10F, 3) == 10F, "до 4 частей урон огня прежний");
        check(ArmorSetLogic.fireDamage("mob", 10F, 4) == 10F && ArmorSetLogic.fireDamage(null, 10F, 4) == 10F, "не огонь - прежний");
    }

    static void materials() {
        check(SetMaterial.CRYSTAL.total() == 15 && SetMaterial.DREAMER.total() == 16 && SetMaterial.ASH.total() == 19, "защита 15/16/19");
        check(java.util.Arrays.equals(SetMaterial.CRYSTAL.def, new int[]{2, 6, 5, 2}), "кристальный 2/6/5/2");
        check(java.util.Arrays.equals(SetMaterial.DREAMER.def, new int[]{3, 6, 5, 2}), "сновидец 3/6/5/2");
        check(java.util.Arrays.equals(SetMaterial.ASH.def, new int[]{3, 7, 6, 3}), "пепельный 3/7/6/3");
        check(SetMaterial.CRYSTAL.tough == 1 && SetMaterial.DREAMER.tough == 1 && SetMaterial.ASH.tough == 2, "твёрдость 1/1/2");
        check(SetMaterial.CRYSTAL.knock == 0 && SetMaterial.DREAMER.knock == 0 && near(SetMaterial.ASH.knock, 0.05), "отбрасывание 0/0/0,05");
        check(SetMaterial.CRYSTAL.durMul == 18 && SetMaterial.DREAMER.durMul == 22 && SetMaterial.ASH.durMul == 26, "прочность x18/22/26");
        check(SetMaterial.CRYSTAL.enchant == 12 && SetMaterial.DREAMER.enchant == 18 && SetMaterial.ASH.enchant == 14, "зачаровываемость 12/18/14");
        // прочность: шлем 11*18=198, нагрудник 16*18=288 (как у ванили, умноженное на множитель)
        check(SetMaterial.CRYSTAL.m_266425_(net.minecraft.world.item.ArmorItem.Type.valueOf("HELMET")) == 198, "шлем 198");
        check(SetMaterial.ASH.m_266425_(net.minecraft.world.item.ArmorItem.Type.valueOf("CHESTPLATE")) == 416, "нагрудник пепельного 416");
        check(SetMaterial.ASH.total() < 20 && SetMaterial.ASH.total() > 15, "Земля в вилке 15-19");
        for (ArmorSets.Set s : ArmorSets.Set.values())
            check(s.material.m_6082_().equals("explosionwand:" + s.id), "имя материала " + s.id);
        check(SetMaterial.CRYSTAL.repair.equals(Mats.name(Mats.Dungeon.RIFT, 0)) && SetMaterial.DREAMER.repair.equals(Mats.name(Mats.Dungeon.SLEEP, 0))
                && SetMaterial.ASH.repair.equals(Mats.name(Mats.Dungeon.ASH, 0)), "ремонт обычным материалом данжа");
    }

    /** id предметов совпадают с именами готовых текстур docs/concepts/armor_textures. */
    static void names() throws Exception {
        Path dir = Path.of("docs/concepts/armor_textures");
        if (!Files.isDirectory(dir)) { System.out.println("(текстуры не найдены, пропуск)"); return; }
        for (ArmorSets.Set s : ArmorSets.Set.values()) {
            for (String p : ArmorSets.PART) check(Files.exists(dir.resolve(s.id + "_" + p + ".png")), "текстура " + s.id + "_" + p);
            check(Files.exists(dir.resolve(s.id + "_layer_1.png")) && Files.exists(dir.resolve(s.id + "_layer_2.png")), "слои брони " + s.id);
        }
        // регистрация: 12 предметов с теми же id
        String reg = Files.readString(Path.of("src/com/explosionwand/ExplosionWandMod.java"));
        for (ArmorSets.Set s : ArmorSets.Set.values())
            for (String p : ArmorSets.PART) check(reg.contains("ITEMS.register(\"" + s.id + "_" + p + "\""), "регистрация " + s.id + "_" + p);
    }

    /** «Грань»: все лучевые атаки мобов данжей идут через единую точку отражения; слепота моли не защищена иммунитетом сна. */
    static void beams() throws Exception {
        String src = "src/com/explosionwand/";
        String rm = Files.readString(Path.of(src + "RiftMobs.java")), am = Files.readString(Path.of(src + "AshMobs.java")), ab = Files.readString(Path.of(src + "AshMobsB.java"));
        String cm = Files.readString(Path.of(src + "CityMobs.java")), cb = Files.readString(Path.of(src + "CityMobsB.java"));
        check(rm.contains("ArmorSets.tryReflectBeam(p, host, scaled(dmg))"), "Mob.hitBeam отражает через ArmorSets.tryReflectBeam");
        check(rm.contains("hitBeam(p, 6, 0.2)"), "Кристальный лучник бьёт через hitBeam");
        check(rm.contains("ArmorSets.tryReflectBeam(p, host, scaled(7))"), "луч Призмы отражается");
        check(am.contains("hitBeam(p, AshLogic.SPARK_BOLT"), "луч Искры отражается");
        check(ab.contains("ArmorSets.tryReflectBeam(p, host, scaled(AshLogic.WORM_SPIT))"), "плевок Магмового червя отражается");
        check(cm.contains("hitBeam(p, CityLogic.DROWSY_RAY"), "луч Дремотника отражается");
        check(cb.contains("hitBeam(p, CityLogic.PAGE_HIT"), "страницы Книжного стража отражаются");
        check(cm.contains("give(level, q, \"blindness\"") && !cm.contains("effect(level, q, \"blindness\""), "слепота моли действует и в сете Сновидца");
        check(ArmorSetLogic.reflects(4, 0.19) && !ArmorSetLogic.reflects(4, 0.2) && !ArmorSetLogic.reflects(3, 0.0), "отражение: 20% и только при 4 частях");
    }
}
