package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * «МОНОЛИТ СНА» — босс логова храма Города Спящих (зал 20, фаза 4.7; раньше жил в измерении dream). Блочная башня из 4 чёрных сегментов,
 * каждый крутится сам по себе; бирюзовые швы, кристаллы-корона, один огромный глаз и две парящие руки-обелиска. Стоит на помосте — нижний
 * сегмент можно бить мечом. Каждая четверть здоровья раскалывает нижний сегмент: башня оседает, начинается новая фаза и приходят приспешники.
 *
 * Где бой: логово — подземный квадратный зал 41x41 под центром Города (спуск по лестнице из храма), пол на уровне метки boss, потолок на 20 блоков выше,
 * четыре колонны 3x3 в углах (на 13 блоков от центра) и пилястры вдоль стен. Башня в полном масштабе S = 1,0, ходит по площади |dx|,|dz| <= 8 от центра.
 * Босс просыпается, когда игрок входит в зал (Dungeons.spawnHall -> Boss.wake -> Boss3.wake), печать в арке входа закрывается.
 * Тик — Boss.tickAll -> Boss3.tickAll; зал зачищен, когда босс убит и убран (хост носит тег зала), сундук ставит обычная зачистка (Dungeons.lairWon).
 *
 *  1 Четыре сегмента — удар-волна (перепрыгни кольцо), луч из глаза, вращение вплотную.   (полоска синяя)
 *  2 Три сегмента   — + руки-обелиски бьют сверху; приходят Дремотники.                  (зелёная)
 *  3 Два сегмента   — + сонный туман, двойная волна; приходят Стражи Шва.                (фиолетовая)
 *  4 Последний глаз — + осколки с потолка, нырок под пол; глаз открыт — урон выше.      (красная)
 *
 * Хост — гигант на 1024 ХП (лимит мобов), настоящие 1700 ХП дают множитель урона HOST_HP/MAX_HP: ровно 1700 урона убивают босса. Теги хоста: ewb3, ewb3_host, ewbk&lt;ключ&gt;, ewd, тег зала.
 * Части облика: ewb3, ewb3_p&lt;i&gt;, ewbk&lt;ключ&gt;. Приспешники: ewb3_min, ewbk&lt;ключ&gt; (+ теги мобов города и зала).
 * Блоки босс не меняет вовсе.
 */
public class Boss3 {
    static final Map<String, Boss3> BOSSES = new HashMap<>();
    static final Random RNG = Fx.RNG;
    /** Масштаб башни: полный, потолок логова на 20 блоков над полом (корона ~12,8). */
    static final double S = 1.0;
    static final int N = 26;
    /** У мобов max_health ограничен 1024: хост живёт на 1024, а настоящие 1700 ХП даёт множитель урона DMG_K (без других коэффициентов). */
    static final float MAX_HP = 1700, HOST_HP = 1024, DMG_K = HOST_HP / MAX_HP;
    static final double SEG_H = 2.4 * S, SEG_GAP = 0.3 * S;
    static final String KEY = "explosionwand.boss.monolith.";
    static final String BAR = "explosionwand:dream_monolith_";
    static final String TAG = "ewb3", MIN = "ewb3_min";
    /** Трофеи босса в сундуке логова (затем идёт набор тира 2 и материалы Города). Осколок Пепла ведёт в Пепельное святилище (4.8). */
    static final List<String> LOOT = List.of("explosionwand:dream_staff", "explosionwand:dreaming_eye", "explosionwand:ash_seal_shard");
    /** Цвета полоски по фазам. */
    static final String[] BAR_COLOR = {"blue", "green", "purple", "red"};
    /** Вращение вплотную; волна дотягивается до самого дальнего угла зала с учётом смещения башни (диагональ ~40). */
    static final double SPIN_R = 6.2, RING_MAX = 42;
    /** Башня скользит по площади |dx|,|dz| <= MOVE от центра (колонны зала на PILLAR +- PILLAR_HALF по обеим осям); круг-телеграф руки ARM_R. */
    static final double MOVE = 8.0, PILLAR = 13.0, PILLAR_HALF = 1.5, ARM_R = 3.4;
    /** Высота потолка над полом и точка падения осколков. */
    static final int CEIL = 20;
    /** Не больше приспешников одновременно. */
    static final int MAX_MIN = 8;
    static final String[] BLOCKS = new String[N];
    static {
        for (int k = 0; k < 4; k++) { BLOCKS[k * 4] = "black_concrete"; BLOCKS[k * 4 + 1] = "diamond_block"; BLOCKS[k * 4 + 2] = "diamond_block"; BLOCKS[k * 4 + 3] = "diamond_block"; }
        BLOCKS[16] = BLOCKS[17] = BLOCKS[18] = "diamond_block";                 // корона
        BLOCKS[19] = "diamond_block"; BLOCKS[20] = "white_concrete"; BLOCKS[21] = "black_concrete";   // глаз
        BLOCKS[22] = BLOCKS[23] = "obsidian"; BLOCKS[24] = BLOCKS[25] = "diamond_block";               // руки
    }
    static ParticleOptions TEAL_P, DEEP, WHITE, DARK;

    final ServerLevel level;
    final DungeonData.Rec rec;
    final Dungeons.Hall hall;
    final String hallTag, key, ktag, bar;
    /** Центр помоста (мир), уровень пола, полуразмеры зала. */
    final double ax, az, rx, rz;
    final int floor;
    LivingEntity host;
    final String[] uuid = new String[N];
    int t = 0, stage = -1;            // -1 пробуждение, 0 поиск хоста, 1 появление, 2 бой, 3 гибель
    double bx, bz, ex = 0, ez = 1;
    // сегменты
    final boolean[] alive = {true, true, true, true};
    final double[] segY = new double[4], segAng = new double[4], segSpd = {0.035, -0.05, 0.028, -0.042};
    double spin = 1, lift = 0, eyeOpen = 1, crownAng = 0;
    int broken = 0, phase = 1, breakT = -1, dyingT = 0;
    boolean lethal = false, won = false;
    // атаки
    int ringCd = 120, beamCd = 90, armCd = 140, shardCd = 80, spinCd = 160, nearT = 0;
    int stompT = -1, spinT = -1, fogCd = 160, sinkCd = 240, sinkT = -1;
    double sinkOff = 0;
    final List<double[]> fogs = new ArrayList<>();      // x,z,timer
    final List<double[]> rings = new ArrayList<>();      // r
    final List<double[]> lances = new ArrayList<>();     // sx,sy,sz,tx,ty,tz,timer
    final List<double[]> shards = new ArrayList<>();     // x,z,timer
    final Map<ServerPlayer, Integer> hitCd = new WeakHashMap<>();
    // руки: позиция x,y,z; режим (0 рядом, 1 подлёт, 2 удар, 3 пауза), таймер, цель
    final double[][] hand = new double[2][3];
    final int[] hMode = new int[2], hT = new int[2];
    final double[][] hTarget = new double[2][2];

    static void init() {
        if (TEAL_P != null) return;
        TEAL_P = new DustParticleOptions(new Vector3f(0.25F, 0.95F, 0.9F), 1.6F);
        DEEP = new DustParticleOptions(new Vector3f(0.05F, 0.45F, 0.6F), 1.3F);
        WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 1.4F);
        DARK = new DustParticleOptions(new Vector3f(0.03F, 0.03F, 0.05F), 2.6F);
    }

    Boss3(ServerLevel level, DungeonData.Rec rec, Dungeons.Hall hall, String hallTag) {
        this.level = level; this.rec = rec; this.hall = hall; this.hallTag = hallTag;
        key = Integer.toHexString(rec.key.hashCode()); ktag = "ewbk" + key; bar = BAR + key;
        ax = rec.x + hall.boss.x + 0.5; az = rec.z + hall.boss.z + 0.5; floor = rec.y + hall.boss.y;
        rx = hall.sx / 2.0; rz = hall.sz / 2.0;
        bx = ax; bz = az;
        for (int k = 0; k < 4; k++) { segY[k] = -SEG_H * (k + 1); segAng[k] = RNG.nextDouble() * Math.PI; }
        for (int i = 0; i < 2; i++) { hand[i][0] = ax + (i == 0 ? -4 : 4); hand[i][1] = floor - 3; hand[i][2] = az; }
    }

    // ================= Пробуждение, сброс, общие тики =================
    /** Зал разбужен: босс начинает пробуждаться (само появление через 3 секунды в tick). */
    static void wake(ServerLevel sl, DungeonData.Rec rec, Dungeons.Hall h, String hallTag) {
        init();
        if (BOSSES.containsKey(rec.key)) return;
        BOSSES.put(rec.key, new Boss3(sl, rec, h, hallTag));
    }

    /** Сброс боя (зал опустел, мирный режим, смена раскладки): убрать босса, части, приспешников и полоску. */
    static void reset(ServerLevel sl, DungeonData.Rec rec, Dungeons.Hall h) {
        init();
        Boss3 b = BOSSES.remove(rec.key);
        if (b == null) b = new Boss3(sl, rec, h, Dungeons.tagOf(rec, h.id));
        b.cleanup();
    }

    static void clear() { BOSSES.clear(); }

    static String barOf(DungeonData.Rec rec) { return BAR + Integer.toHexString(rec.key.hashCode()); }

    static void tickAll(ServerLevel sl, DungeonData data) {
        if (BOSSES.isEmpty()) return;
        Iterator<Boss3> it = BOSSES.values().iterator();
        while (it.hasNext()) {
            Boss3 b = it.next();
            if (b.level != sl) continue;
            boolean done;
            try { done = b.tick(); }
            catch (RuntimeException e) { b.cleanup(); it.remove(); throw e; }
            if (done) {
                it.remove();
                if (b.won) Dungeons.lairWon(sl, data, b.rec, b.hall, b.players());
            }
        }
    }

    /** Убрать всё, что принадлежит этому бою: хост и части (discard), приспешников (вместе с частями облика), полоску. */
    void cleanup() {
        purge();
        Cmd.run(level, ax, floor, az, "bossbar remove " + bar);
    }

    void purge() {
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_19880_().contains(ktag) || en.m_19880_().contains(hallTag))) {
            if (e.m_19880_().contains(MIN) || !e.m_19880_().contains(ktag)) RiftMobs.remove(e);
            else e.m_146870_();
        }
    }

    // ================= Помощники =================
    /** Живые игроки в логове: в коробке зала (с запасом 1 блок) и по высоте до потолка. */
    List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) {
            if (!p.m_6084_() || p.m_5833_()) continue;
            if (inLair(p.m_20185_(), p.m_20186_(), p.m_20189_())) out.add(p);
        }
        return out;
    }

    boolean inLair(double x, double y, double z) {
        return Math.abs(x - ax) <= rx + 1.0 && Math.abs(z - az) <= rz + 1.0 && y >= floor - 3 && y <= floor + CEIL + 2;
    }

    /** Игроки в логове (коробка зала с запасом, потолок включая): заголовки и полоска не видны в коридоре за печатью. */
    String sel() {
        return "@a[x=" + Cmd.f(ax - rx - 1) + ",y=" + Cmd.f(floor - 2) + ",z=" + Cmd.f(az - rz - 1) + ",dx=" + Cmd.f(2 * rx + 2) + ",dy=" + Cmd.f(CEIL + 4) + ",dz=" + Cmd.f(2 * rz + 2) + "]";
    }

    ServerPlayer nearest() {
        ServerPlayer best = null; double bd = 1e9;
        for (ServerPlayer p : players()) { double d = hdist2(p); if (d < bd) { bd = d; best = p; } }
        return best;
    }
    double hdist2(Entity p) { Vec3 v = p.m_20182_(); return (v.f_82479_ - bx) * (v.f_82479_ - bx) + (v.f_82481_ - bz) * (v.f_82481_ - bz); }
    void hurt(ServerPlayer p, float d) { p.m_6469_(level.m_269111_().m_269425_(), d); }
    void actionbar(String k, String c) { Cmd.run(level, ax, floor, az, "title " + sel() + " actionbar {\"translate\":\"" + KEY + k + "\",\"color\":\"" + c + "\"}"); }
    double rate() { return phase >= 4 ? 0.6 : phase == 3 ? 0.75 : phase == 2 ? 0.9 : 1.0; }

    /** Точка (dx, dz от центра зала) внутри колонны 3x3 с запасом m (колонны в углах на +-PILLAR). */
    static boolean pillar(double dx, double dz, double m) { return Math.abs(Math.abs(dx) - PILLAR) < PILLAR_HALF + m && Math.abs(Math.abs(dz) - PILLAR) < PILLAR_HALF + m; }
    boolean pillarAt(double x, double z, double m) { return pillar(x - ax, z - az, m); }
    /** Урон по хосту из урона игрока: ровно MAX_HP урона убивают босса (пороги фаз 75/50/25% считаются по ХП хоста, то есть по шкале 1700). */
    static float hostDamage(float raw) { return raw * DMG_K; }

    /** Точка внутри зала: x и z прижимаются к полу зала с отступом m от стен (чистая функция для теста). */
    static double clampAxis(double v, double center, double half, double m) { return Math.max(center - half + m, Math.min(center + half - m, v)); }
    double inX(double x, double m) { return clampAxis(x, ax, rx, m); }
    double inZ(double z, double m) { return clampAxis(z, az, rz, m); }

    /** Заголовок на экране: title и sub — суффиксы ключей explosionwand.boss.monolith.* (lang ru/en). */
    void title(String title, String color, String sub, int stay) {
        Cmd.run(level, ax, floor, az, "title " + sel() + " times 10 " + stay + " 20");
        Cmd.run(level, ax, floor, az, "title " + sel() + " subtitle {\"translate\":\"" + KEY + sub + "\",\"color\":\"gray\",\"italic\":true}");
        Cmd.run(level, ax, floor, az, "title " + sel() + " title {\"translate\":\"" + KEY + title + "\",\"color\":\"" + color + "\",\"bold\":true}");
    }

    // ================= Геометрия башни =================
    int aliveCount() { int n = 0; for (boolean a : alive) if (a) n++; return n; }
    int bottom() { for (int k = 0; k < 4; k++) if (alive[k]) return k; return 3; }
    int top() { for (int k = 3; k >= 0; k--) if (alive[k]) return k; return 3; }
    double width(int k) { return (3.4 - k * 0.18) * S; }
    /** Целевая высота центра сегмента k над полом (живые лежат стопкой). */
    double targetY(int k) { int j = 0; for (int i = 0; i < k; i++) if (alive[i]) j++; return SEG_H / 2 + 0.02 + j * (SEG_H + SEG_GAP); }
    double[] eyeWorld() { int k = top(); double r = width(k) / 2 + 0.25 * S; return new double[]{bx + ex * r, floor + segY[k], bz + ez * r}; }
    /** Высота верха короны над полом при четырёх целых сегментах (должна помещаться под потолок). */
    static double towerTop() { return SEG_H / 2 + 0.02 + 3 * (SEG_H + SEG_GAP) + SEG_H / 2 + 0.75 * S + 1.5 * S; }

    // ================= Появление хоста =================
    void spawnHost() {
        StringBuilder sb = new StringBuilder();
        sb.append("summon minecraft:giant ").append(Cmd.f(ax)).append(" ").append(floor).append(" ").append(Cmd.f(az))
          .append(" {Tags:[\"" + TAG + "\",\"" + TAG + "_host\",\"").append(ktag).append("\",\"ewd\",\"").append(hallTag).append("\"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,CanPickUpLoot:0b,DeathLootTable:\"minecraft:empty\",")
          .append("Attributes:[{Name:\"minecraft:generic.max_health\",Base:").append((int) HOST_HP).append("d},{Name:\"minecraft:generic.knockback_resistance\",Base:1d}],")
          .append("Health:").append((int) HOST_HP).append("f,ActiveEffects:[{Id:14,Amplifier:0b,Duration:-1,ShowParticles:0b}],Passengers:[");
        for (int i = 0; i < N; i++) {
            if (i > 0) sb.append(",");
            sb.append("{id:\"minecraft:block_display\",Tags:[\"" + TAG + "\",\"" + TAG + "_p").append(i).append("\",\"").append(ktag).append("\"],")
              .append(Disp.block(BLOCKS[i])).append(",view_range:5f,brightness:{sky:15,block:15},")
              .append(Disp.tf(0, -8, 0, 0.01, 0.01, 0.01, Disp.ID)).append("}");
        }
        sb.append("]}");
        Cmd.run(level, ax, floor, az, sb.toString());
        Cmd.run(level, ax, floor, az, "bossbar remove " + bar);
        Cmd.run(level, ax, floor, az, "bossbar add " + bar + " {\"translate\":\"" + KEY + "name\",\"color\":\"aqua\",\"bold\":true}");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " color blue");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " style notched_10");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " max 1000");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " value 1000");
        title("name", "aqua", "spawn.sub", 90);
        Fx.sound(level, ax, floor + 6, az, "entity.elder_guardian.curse", 30.0F, 0.5F);
        Fx.sound(level, ax, floor + 6, az, "block.respawn_anchor.charge", 30.0F, 0.5F);
    }

    // ================= Тик =================
    boolean tick() {
        t++;
        long bit = 1L << hall.id;
        if ((rec.hallsSpawned & bit) == 0 || (rec.hallsCleared & bit) != 0) { cleanup(); return true; }   // зал сброшен или засчитан без нас
        if (stage == -1) {      // пробуждение: сердцебиение и бирюзовые искры над помостом, босс ещё не виден
            if (t == 1) purge();
            if (t == 1 || (t % 15 == 0 && t < 60)) Fx.sound(level, ax, floor + 4, az, "entity.warden.heartbeat", 20.0F, 0.3F + t / 180F);
            if (t > 20 && t < 60) {
                if (TEAL_P == null) init();
                Fx.send(level, TEAL_P, ax, floor + 3, az, 20, 1, 2, 1, 0.3);
            }
            if (t >= 60) { spawnHost(); stage = 0; t = 0; }
            return false;
        }
        if (stage == 0) {
            Entity h = find(TAG + "_host");
            if (!(h instanceof LivingEntity le)) { if (t > 40) { cleanup(); return true; } return false; }
            host = le;
            for (int i = 0; i < N; i++) {
                Entity p = find(TAG + "_p" + i);
                if (p != null) uuid[i] = p.m_20149_();
            }
            stage = 1; t = 0;
            return false;
        }
        if (host == null) { cleanup(); return true; }
        if (stage == 3) return dying();
        if (host.m_213877_()) { cleanup(); return true; }            // хоста убрали снаружи (сброс зала, команда)
        if (lethal || host.m_21223_() <= 1.0F || host.m_21224_()) { startDeath(); return false; }
        if (!host.m_6084_()) { cleanup(); return true; }
        if (host.m_6060_()) host.m_20095_();

        animate();
        if (t % 3 == 0) pose(3);
        if (t % 2 == 0) poseArms(2);
        ambience();
        if (t % 10 == 0) bar();
        if (stage == 1) {     // башня поднимается из пола
            if (t % 6 == 0) Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx, floor + 0.5, bz, 30, 1.2, 0.3, 1.2, 0.1);
            if (t % 15 == 0) Fx.sound(level, bx, floor, bz, "block.deepslate.place", 15.0F, 0.5F);
            if (t >= 70) { stage = 2; t = 0; actionbar("hint", "aqua"); }
            return false;
        }

        if (breakT >= 0) { tickBreak(); tickRings(); return false; }
        if (broken < 3 && host.m_21223_() <= HOST_HP * (0.75F - broken * 0.25F) + 0.01F) { startBreak(); return false; }

        move();
        tickRings(); tickLances(); tickArms(); tickShards();
        if (stompT >= 0) tickStomp();
        if (spinT >= 0) tickSpin();
        if (sinkT >= 0) tickSink();
        tickFogs();
        attacks();
        return false;
    }

    /** Сущность этого боя с тегом (хост или часть облика). */
    Entity find(String tag) {
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_19880_().contains(ktag) && en.m_19880_().contains(tag))) return e;
        return null;
    }

    // ================= Движение и облик =================
    void move() {
        ServerPlayer n = nearest();
        if (n == null) return;
        Vec3 p = n.m_20182_();
        double dx = p.f_82479_ - bx, dz = p.f_82481_ - bz, l = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
        ex += (dx / l - ex) * 0.08; ez += (dz / l - ez) * 0.08;
        double el = Math.max(1e-3, Math.sqrt(ex * ex + ez * ez)); ex /= el; ez /= el;
        // скользит к ближайшему, не уходя от центра дальше MOVE (колонны зала в углах, на 13 блоков)
        if (spinT < 0 && stompT < 0 && l > 5) {
            double sp = 0.05 + phase * 0.012;
            double nx = bx + dx / l * sp, nz = bz + dz / l * sp;
            bx = clampAxis(nx, ax, MOVE, 0); bz = clampAxis(nz, az, MOVE, 0);
            if (t % 20 == 0) Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx, floor + 0.1, bz, 8, 1.0, 0.05, 1.0, 0.05);
        }
        host.m_6034_(bx, floor, bz); host.m_20256_(new Vec3(0, 0, 0));
    }

    void animate() {
        double boost = spinT >= 20 ? 6.0 : spinT >= 0 ? 0.3 : 1.0;
        spin += (boost - spin) * 0.15;
        for (int k = 0; k < 4; k++) {
            segAng[k] += segSpd[k] * spin * (1 + (4 - aliveCount()) * 0.25);
            double target = alive[k] ? targetY(k) + lift * (k - bottom() + 1) * 0.3 - sinkOff : -SEG_H;
            double kk = stage == 1 ? 0.04 : 0.15;
            segY[k] += (target - segY[k]) * kk;
        }
        crownAng += 0.02;
        lift *= 0.8;
        boolean blink = (t % 140) > 132 || breakT >= 0;
        double want = blink ? 0.05 : (phase >= 4 ? 1.15 : 1.0);
        eyeOpen += (want - eyeOpen) * 0.3;
    }

    void send(int i, int dur, double cx, double cy, double cz, double sx, double sy, double sz, double[] q) {
        if (uuid[i] == null) return;
        Cmd.run(level, bx, floor, bz, "data merge entity " + uuid[i] + " {start_interpolation:0,interpolation_duration:" + dur + "," + Disp.tf(cx, cy, cz, Math.max(0.001, sx), Math.max(0.001, sy), Math.max(0.001, sz), q) + "}");
    }
    void setBlock(int i, String id) { if (uuid[i] != null) Cmd.run(level, bx, floor, bz, "data merge entity " + uuid[i] + " {" + Disp.block(id) + "}"); }
    /** Локальная высота относительно точки посадки (гигант везёт пассажиров на 9 блоков выше ног). */
    static double ly(double worldAboveFloor) { return worldAboveFloor - 9.0; }

    void pose(int dur) {
        double gs = stage == 1 ? Math.min(1, 0.3 + t / 70.0) : 1.0;
        for (int k = 0; k < 4; k++) {
            int b = k * 4;
            if (!alive[k] && segY[k] < -SEG_H / 2) { for (int j = 0; j < 4; j++) send(b + j, dur, 0, ly(-4), 0, 0.001, 0.001, 0.001, Disp.ID); continue; }
            double w = width(k) * gs, h = SEG_H * gs, y = ly(segY[k]);
            double shake = (spinT >= 0 && spinT < 20) ? (RNG.nextDouble() - 0.5) * 0.12 : 0;
            double[] q = Disp.q(0, 1, 0, segAng[k]);
            send(b, dur, shake, y, 0, w, h, w, q);
            send(b + 1, dur, 0, y + h / 2 + 0.08 * S, 0, w + 0.22 * S, 0.16 * S, w + 0.22 * S, q);
            double[] s1 = Disp.rot(q, 0.55 * gs * S, 0, w / 2 + 0.03 * S);
            send(b + 2, dur, s1[0], y + s1[1], s1[2], 0.18 * S, h * 0.8, 0.06 * S, q);
            double[] s2 = Disp.rot(q, -w / 2 - 0.03 * S, -0.3 * gs * S, -0.45 * gs * S);
            send(b + 3, dur, s2[0], y + s2[1], s2[2], 0.06 * S, h * 0.65, 0.18 * S, q);
        }
        // корона из кристаллов над верхним сегментом
        double cy = ly(segY[top()] + SEG_H / 2 + 0.75 * S);
        for (int i = 0; i < 3; i++) {
            double a = crownAng + i * Math.PI * 2 / 3, s = Math.sin(a), c = Math.cos(a);
            double[] q = Disp.mul(Disp.q(s, 0, -c, 0.4), Disp.q(0, 1, 0, a));
            double hh = (1.0 + (i == 0 ? 0.5 : 0.1)) * gs * S;
            send(16 + i, dur, c * 0.85 * S, cy + (i == 0 ? 0.2 * S : 0), s * 0.85 * S, 0.45 * gs * S, hh, 0.45 * gs * S, q);
        }
        // глаз смотрит на ближайшего игрока
        int k = top();
        double r = width(k) / 2 + 0.06 * S, yawF = Math.atan2(ex, ez), y = ly(segY[k]);
        double[] qF = Disp.q(0, 1, 0, yawF);
        double open = Math.max(0.02, eyeOpen) * gs;
        send(19, dur, ex * r, y, ez * r, 1.9 * gs * S, 1.9 * gs * S, 0.08 * S, qF);
        send(20, dur, ex * (r + 0.05 * S), y, ez * (r + 0.05 * S), 1.5 * gs * S, 1.5 * open * S, 0.1 * S, qF);
        double look = Math.sin(t * 0.05) * 0.25 * S;
        double[] pp = Disp.rot(qF, look, 0, 0);
        send(21, dur, ex * (r + 0.1 * S) + pp[0], y, ez * (r + 0.1 * S) + pp[2], 0.42 * gs * S, 1.05 * open * S, 0.12 * S, qF);
    }

    void poseArms(int dur) {
        double yawF = Math.atan2(ex, ez);
        for (int h = 0; h < 2; h++) {
            double[] H = hand[h];
            double tilt = (h == 0 ? 0.18 : -0.18) * (hMode[h] == 2 ? 0.2 : 1.0);
            double[] q = Disp.mul(Disp.q(0, 1, 0, yawF), Disp.q(0, 0, 1, tilt));
            double hx = H[0] - bx, hz = H[2] - bz, hy = ly(H[1] - floor);
            send(22 + h, dur, hx, hy, hz, 0.9 * S, 5.0 * S, 0.9 * S, q);
            double[] tip = Disp.rot(q, 0, -2.6 * S, 0);
            send(24 + h, dur, hx + tip[0], hy + tip[1], hz + tip[2], 1.15 * S, 1.15 * S, 1.15 * S, Disp.mul(q, Disp.q(0, 1, 0, t * 0.08)));
        }
    }

    void ambience() {
        // светящиеся швы
        if (t % 2 == 0) for (int k = 0; k < 4; k++) if (alive[k]) {
            double a = RNG.nextDouble() * Math.PI * 2, w = width(k) / 2 + 0.1;
            Fx.send(level, TEAL_P, bx + Math.cos(a) * w, floor + segY[k] + SEG_H / 2 + 0.1, bz + Math.sin(a) * w, 1, 0, 0, 0, 0);
        }
        if (t % 5 == 0) { double[] e = eyeWorld(); Fx.send(level, Fx.particle("glow"), e[0], e[1], e[2], 1, 0.3, 0.3, 0.3, 0); }
        if (t % 60 == 0) Fx.sound(level, bx, floor + 5, bz, "block.beacon.ambient", 10.0F, 0.5F);
        if (t % 110 == 0) Fx.sound(level, bx, floor + 5, bz, "entity.warden.heartbeat", 12.0F, 0.5F);
    }

    void bar() {
        int v = (int) Math.max(0, Math.min(1000, host.m_21223_() / HOST_HP * 1000));
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " value " + v);
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " players " + sel());
    }

    // ================= Атаки =================
    void attacks() {
        List<ServerPlayer> ps = players();
        if (ps.isEmpty()) return;
        boolean near = false;
        for (ServerPlayer p : ps) if (hdist2(p) < SPIN_R * SPIN_R) near = true;
        nearT = near ? nearT + 1 : Math.max(0, nearT - 2);
        if (spinCd > 0) spinCd--;
        if (stompT < 0 && spinT < 0) {
            if (nearT > 50 && spinCd <= 0) { spinT = 0; nearT = 0; spinCd = (int) (220 * rate()); return; }
            if (--ringCd <= 0) { stompT = 0; ringCd = (int) (170 * rate()); }
        }
        if (--beamCd <= 0 && eyeOpen > 0.5) {
            double[] e = eyeWorld();
            int shots = phase >= 4 ? 3 : phase >= 2 ? 2 : 1;
            for (int i = 0; i < shots; i++) {
                ServerPlayer p = ps.get(RNG.nextInt(ps.size())); Vec3 v = p.m_20182_();
                double off = i == 0 ? 0 : 2.0;
                lances.add(new double[]{e[0], e[1], e[2], v.f_82479_ + RNG.nextGaussian() * off, v.f_82480_ + 1, v.f_82481_ + RNG.nextGaussian() * off, -i * 6});
            }
            Fx.sound(level, e[0], e[1], e[2], "block.conduit.attack.target", 6.0F, 0.8F);
            beamCd = (int) (110 * rate());
            if (phase >= 3) beamCd -= 15;
        }
        if (phase >= 2 && --armCd <= 0) {
            int h = hMode[0] == 0 ? 0 : (hMode[1] == 0 ? 1 : -1);
            if (h >= 0) {
                ServerPlayer p = ps.get(RNG.nextInt(ps.size())); Vec3 v = p.m_20182_();
                hMode[h] = 1; hT[h] = 0; hTarget[h][0] = inX(v.f_82479_, 1.0); hTarget[h][1] = inZ(v.f_82481_, 1.0);
                if (pillarAt(hTarget[h][0], hTarget[h][1], 0.5)) { hTarget[h][0] = ax + Math.signum(hTarget[h][0] - ax) * (PILLAR - PILLAR_HALF - 1.5); }
                Fx.sound(level, hand[h][0], hand[h][1], hand[h][2], "entity.iron_golem.attack", 6.0F, 0.5F);
            }
            armCd = (int) (120 * rate());
        }
        if (phase >= 3 && --fogCd <= 0) {
            for (ServerPlayer p : ps) { Vec3 v = p.m_20182_(); fogs.add(new double[]{inX(v.f_82479_, 2.0), inZ(v.f_82481_, 2.0), 0}); }
            for (int i = 0; i < (phase >= 4 ? 3 : 2); i++) {      // случайные пятна по залу (не в колоннах)
                double fx = ax, fz = az;
                for (int tr = 0; tr < 8; tr++) {
                    fx = ax + (RNG.nextDouble() * 2 - 1) * (rx - 3); fz = az + (RNG.nextDouble() * 2 - 1) * (rz - 3);
                    if (!pillarAt(fx, fz, 2.5)) break;
                }
                fogs.add(new double[]{fx, fz, 0});
            }
            Fx.sound(level, bx, floor + 4, bz, "entity.elder_guardian.curse", 8.0F, 1.4F);
            fogCd = phase >= 4 ? 170 : 220;
        }
        if (phase >= 4 && sinkT < 0 && stompT < 0 && spinT < 0 && --sinkCd <= 0) { sinkT = 0; sinkCd = 260; }
        if (phase >= 4 && --shardCd <= 0) {
            for (ServerPlayer p : ps) for (int i = 0; i < 3; i++) {
                Vec3 v = p.m_20182_();
                double sx = inX(v.f_82479_ + (i == 0 ? 0 : RNG.nextGaussian() * 3.5), 1.5), sz = inZ(v.f_82481_ + (i == 0 ? 0 : RNG.nextGaussian() * 3.5), 1.5);
                if (!pillarAt(sx, sz, 0.5)) shards.add(new double[]{sx, sz, 0});
            }
            for (int i = 0; i < 2; i++) {                          // и пара случайных по залу
                double sx = ax + (RNG.nextDouble() * 2 - 1) * (rx - 2), sz = az + (RNG.nextDouble() * 2 - 1) * (rz - 2);
                if (!pillarAt(sx, sz, 0.5)) shards.add(new double[]{sx, sz, 0});
            }
            Fx.sound(level, bx, floor + 10, bz, "block.amethyst_block.resonate", 10.0F, 0.6F);
            shardCd = 70;
        }
    }

    // --- удар-волна: башня подпрыгивает (0,9 с, сжимающийся круг у основания) и бьёт о пол; по полу расходится кольцо ---
    static final int STOMP_WIND = 18;
    void tickStomp() {
        stompT++;
        if (stompT == 1) { actionbar("stomp", "aqua"); Fx.sound(level, bx, floor + 2, bz, "block.deepslate.step", 10.0F, 0.5F); }
        if (stompT < STOMP_WIND - 4) lift = 1.2 * S;
        if (stompT < STOMP_WIND) {
            double r = 1.8 + 3.6 * (1 - stompT / (double) STOMP_WIND);
            for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8 + stompT * 0.2; Fx.send(level, TEAL_P, bx + Math.cos(a) * r, floor + 0.2, bz + Math.sin(a) * r, 1, 0, 0, 0, 0); }
        }
        if (stompT == STOMP_WIND) {
            rings.add(new double[]{2.0});
            if (phase >= 3) Later.run(level, 12, () -> rings.add(new double[]{2.0}));
            Fx.send(level, Fx.particle("explosion"), bx, floor + 0.5, bz, 6, 2, 0.2, 2, 0);
            Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx, floor + 0.3, bz, 80, 2.5, 0.2, 2.5, 0.2);
            Fx.sound(level, bx, floor, bz, "entity.generic.explode", 10.0F, 0.5F);
            Fx.sound(level, bx, floor, bz, "block.anvil.land", 10.0F, 0.4F);
            stompT = -1;
        }
    }

    void tickRings() {
        Iterator<double[]> it = rings.iterator();
        while (it.hasNext()) {
            double[] w = it.next(); w[0] += 0.55;
            int n = Math.min(100, (int) (20 + w[0] * 3));   // TPS: не больше 100 частиц на кольцо, и только через тик
            for (int i = 0; i < n && (t & 1) == 0; i++) {
                double a = i * Math.PI * 2 / n;
                double px = bx + Math.cos(a) * w[0], pz = bz + Math.sin(a) * w[0];
                if (Math.abs(px - ax) > rx + 0.5 || Math.abs(pz - az) > rz + 0.5) continue;   // за стеной зала частиц не нужно
                Fx.send(level, i % 2 == 0 ? TEAL_P : DEEP, px, floor + 0.25, pz, 1, 0, 0, 0, 0);
            }
            for (ServerPlayer p : players()) {
                Vec3 v = p.m_20182_(); double d = Math.sqrt(hdist2(p));
                if (Math.abs(d - w[0]) < 0.8 && v.f_82480_ < floor + 0.7) {
                    int last = hitCd.getOrDefault(p, -100);
                    if (t - last > 10) { hitCd.put(p, t); hurt(p, 9); p.m_20256_(new Vec3((v.f_82479_ - bx) / Math.max(d, 0.1) * 0.8, 0.5, (v.f_82481_ - bz) / Math.max(d, 0.1) * 0.8)); p.f_19864_ = true; }
                }
            }
            if (w[0] > RING_MAX) it.remove();
        }
    }

    // --- луч из глаза: тонкая линия-прицел, затем выстрел ---
    void tickLances() {
        Iterator<double[]> it = lances.iterator();
        while (it.hasNext()) {
            double[] b = it.next();
            if (++b[6] < 0) continue;
            double dx = b[3] - b[0], dy = b[4] - b[1], dz = b[5] - b[2], l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
            boolean fire = b[6] > 18;
            double ext = 1.6;
            for (double s = 0; s < l * ext; s += fire ? 0.35 : 1.2) Fx.send(level, fire ? WHITE : TEAL_P, b[0] + dx * s / l, b[1] + dy * s / l, b[2] + dz * s / l, 1, 0, 0, 0, 0);
            if (b[6] == 19) {
                Fx.sound(level, b[0], b[1], b[2], "entity.guardian.attack", 6.0F, 1.1F);
                for (ServerPlayer p : players()) {
                    Vec3 v = p.m_20182_();
                    double rx0 = v.f_82479_ - b[0], ry = v.f_82480_ + 1 - b[1], rz0 = v.f_82481_ - b[2];
                    double al = (rx0 * dx + ry * dy + rz0 * dz) / l; if (al < 0 || al > l * ext) continue;
                    double qx = rx0 - dx / l * al, qy = ry - dy / l * al, qz = rz0 - dz / l * al;
                    if (qx * qx + qy * qy + qz * qz < 1.3) {
                        hurt(p, 10);
                        var sl = Fx.effect("slowness"); if (sl != null) p.m_7292_(new MobEffectInstance(sl, 50, 1));
                    }
                }
            }
            if (b[6] > 24) it.remove();
        }
    }

    // --- вращение вплотную: сегменты раскручиваются (1 с) и отшвыривают ---
    void tickSpin() {
        spinT++;
        if (spinT == 1) { actionbar("spin", "yellow"); Fx.sound(level, bx, floor + 3, bz, "block.beacon.power_select", 10.0F, 0.6F); }
        if (spinT < 20) {
            if (spinT % 2 == 0) Fx.send(level, WHITE, bx, floor + 3, bz, 10, 1.5, 3, 1.5, 0.05);
            if (spinT % 4 == 0) for (int i = 0; i < 24; i++) { double a = i * Math.PI / 12; Fx.send(level, WHITE, bx + Math.cos(a) * SPIN_R, floor + 0.2, bz + Math.sin(a) * SPIN_R, 1, 0, 0, 0, 0); }
        }
        if (spinT >= 20) {
            if (spinT % 4 == 0) Fx.sound(level, bx, floor + 3, bz, "entity.player.attack.sweep", 6.0F, 0.6F);
            Fx.send(level, Fx.particle("sweep_attack"), bx, floor + 1 + RNG.nextDouble() * 5, bz, 2, 1.8, 0.5, 1.8, 0);
            if (spinT % 10 == 0) for (ServerPlayer p : players()) {
                double d2 = hdist2(p);
                if (d2 < SPIN_R * SPIN_R && p.m_20186_() < floor + 9) {
                    Vec3 v = p.m_20182_(); double d = Math.max(0.3, Math.sqrt(d2));
                    hurt(p, 7);
                    p.m_20256_(new Vec3((v.f_82479_ - bx) / d * 1.4, 0.55, (v.f_82481_ - bz) / d * 1.4)); p.f_19864_ = true;
                }
            }
        }
        if (spinT >= 50) spinT = -1;
    }

    // --- руки-обелиски ---
    void tickArms() {
        double yawF = Math.atan2(ex, ez);
        double px = Math.cos(yawF), pz = -Math.sin(yawF);       // перпендикуляр к взгляду
        for (int h = 0; h < 2; h++) {
            double[] H = hand[h];
            double sgn = h == 0 ? 1 : -1;
            double tx, ty, tz, k = 0.12;
            switch (hMode[h]) {
                case 1 -> { tx = hTarget[h][0]; tz = hTarget[h][1]; ty = floor + 8.5 * S; k = 0.16;
                    if (++hT[h] % 3 == 0) for (int i = 0; i < 20; i++) { double a = i * Math.PI / 10; Fx.send(level, TEAL_P, tx + Math.cos(a) * ARM_R, floor + 0.2, tz + Math.sin(a) * ARM_R, 1, 0, 0, 0, 0); }
                    if (hT[h] >= 30) { hMode[h] = 2; hT[h] = 0; } }
                case 2 -> { tx = hTarget[h][0]; tz = hTarget[h][1]; ty = floor + 3.2 * S; k = 0.55;
                    if (++hT[h] == 5) { armImpact(hTarget[h][0], hTarget[h][1]); hMode[h] = 3; hT[h] = 0; } }
                case 3 -> { tx = H[0]; tz = H[2]; ty = floor + 3.2 * S; if (++hT[h] >= 18) { hMode[h] = 0; hT[h] = 0; } }
                default -> { tx = bx + px * 3.8 * sgn; tz = bz + pz * 3.8 * sgn; ty = floor + 5.0 + Math.sin(t * 0.06 + h * 2) * 0.4; }
            }
            tx = inX(tx, 1.0); tz = inZ(tz, 0.8);
            if (stage == 1) { ty = floor + 5.0 - (70 - t) * 0.12; k = 0.2; }
            H[0] += (tx - H[0]) * k; H[1] += (ty - H[1]) * k; H[2] += (tz - H[2]) * k;
        }
    }

    void armImpact(double x, double z) {
        Fx.send(level, Fx.particle("explosion"), x, floor + 0.5, z, 3, 1, 0.2, 1, 0);
        Fx.send(level, Fx.blockParticle(Fx.state("obsidian")), x, floor + 0.3, z, 50, 1.5, 0.2, 1.5, 0.2);
        Fx.send(level, TEAL_P, x, floor + 0.5, z, 30, 2, 0.2, 2, 0.1);
        Fx.sound(level, x, floor, z, "entity.iron_golem.damage", 8.0F, 0.4F);
        Fx.sound(level, x, floor, z, "entity.generic.explode", 6.0F, 0.7F);
        for (ServerPlayer p : players()) {
            Vec3 v = p.m_20182_(); double dx = v.f_82479_ - x, dz = v.f_82481_ - z;
            if (dx * dx + dz * dz < (ARM_R - 0.2) * (ARM_R - 0.2) && v.f_82480_ < floor + 4) { hurt(p, 12); p.m_20256_(new Vec3(dx * 0.2, 0.9, dz * 0.2)); p.f_19864_ = true; }
        }
    }

    // --- фаза 4: осколки снов падают с потолка зала (круг на полу 1,3 с, потом удар) ---
    static final double SHARD_FALL = 0.55;
    void tickShards() {
        Iterator<double[]> it = shards.iterator();
        while (it.hasNext()) {
            double[] s = it.next(); s[2]++;
            if (s[2] % 4 == 0) for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; Fx.send(level, DEEP, s[0] + Math.cos(a) * 2.2, floor + 0.2, s[1] + Math.sin(a) * 2.2, 1, 0, 0, 0, 0); }
            double fall = floor + CEIL - 1 - s[2] * SHARD_FALL;
            if (s[2] > 6) { Fx.send(level, TEAL_P, s[0], fall, s[1], 3, 0.15, 0.4, 0.15, 0); Fx.send(level, Fx.particle("end_rod"), s[0], fall, s[1], 1, 0, 0, 0, 0); }
            if (fall <= floor + 0.5) {
                Fx.send(level, Fx.particle("flash"), s[0], floor + 0.6, s[1], 1, 0, 0, 0, 0);
                Fx.send(level, TEAL_P, s[0], floor + 0.5, s[1], 25, 1, 0.3, 1, 0.1);
                Fx.sound(level, s[0], floor, s[1], "block.amethyst_block.break", 3.0F, 0.6F);
                for (ServerPlayer p : players()) { Vec3 v = p.m_20182_(); double dx = v.f_82479_ - s[0], dz = v.f_82481_ - s[1]; if (dx * dx + dz * dz < 2.0 * 2.0 && v.f_82480_ < floor + 3) hurt(p, 8); }
                it.remove();
            }
        }
    }

    // --- фаза 3+: сонный туман — зоны, где клонит в сон и жжёт ---
    void tickFogs() {
        Iterator<double[]> it = fogs.iterator();
        while (it.hasNext()) {
            double[] f = it.next(); f[2]++;
            if (f[2] < 25) { if (f[2] % 3 == 0) for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8; Fx.send(level, DEEP, f[0] + Math.cos(a) * 3, floor + 0.2, f[1] + Math.sin(a) * 3, 1, 0, 0, 0, 0); } continue; }
            if (f[2] % 2 == 0) { Fx.send(level, TEAL_P, f[0], floor + 0.8, f[1], 6, 1.6, 0.5, 1.6, 0); Fx.send(level, Fx.particle("cloud"), f[0], floor + 0.6, f[1], 2, 1.5, 0.3, 1.5, 0.005); }
            if (f[2] % 10 == 0) for (ServerPlayer p : players()) {
                Vec3 v = p.m_20182_(); double dx = v.f_82479_ - f[0], dz = v.f_82481_ - f[1];
                if (dx * dx + dz * dz < 9 && v.f_82480_ < floor + 3) {
                    hurt(p, 2);
                    var sl = Fx.effect("slowness"); if (sl != null) p.m_7292_(new MobEffectInstance(sl, 40, 1));
                    var na = Fx.effect("nausea"); if (na != null && RNG.nextInt(3) == 0) p.m_7292_(new MobEffectInstance(na, 80, 0));
                }
            }
            if (f[2] > 110) it.remove();
        }
    }

    // --- фаза 4: башня уходит под пол и выныривает рядом с игроком (круг-телеграф 0,7 с перед ударом) ---
    void tickSink() {
        sinkT++;
        if (sinkT == 1) { actionbar("sink", "aqua"); Fx.sound(level, bx, floor, bz, "block.deepslate.break", 15.0F, 0.4F); }
        if (sinkT <= 20) { sinkOff = sinkT * 0.45 * S; Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx, floor + 0.2, bz, 15, 1.2, 0.1, 1.2, 0.1); }
        if (sinkT == 20) {
            List<ServerPlayer> ps = players();
            if (!ps.isEmpty()) {
                Vec3 v = ps.get(RNG.nextInt(ps.size())).m_20182_();
                double a = RNG.nextDouble() * Math.PI * 2;
                bx = clampAxis(v.f_82479_ + Math.cos(a) * 3.5, ax, MOVE, 0); bz = clampAxis(v.f_82481_ + Math.sin(a) * 3.5, az, MOVE, 0);
                host.m_6034_(bx, floor, bz);
            }
        }
        if (sinkT > 20 && sinkT < 34) for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; Fx.send(level, TEAL_P, bx + Math.cos(a) * 4, floor + 0.2, bz + Math.sin(a) * 4, 1, 0, 0, 0, 0); }
        if (sinkT == 34) {
            sinkOff = 0;
            rings.add(new double[]{2.0});
            Fx.send(level, Fx.particle("explosion_emitter"), bx, floor + 1, bz, 1, 0, 0, 0, 0);
            Fx.sound(level, bx, floor, bz, "entity.generic.explode", 12.0F, 0.5F);
            for (ServerPlayer p : players()) { Vec3 v = p.m_20182_(); double d2 = hdist2(p); if (d2 < 4.0 * 4.0 && v.f_82480_ < floor + 4) { hurt(p, 11); double d = Math.max(0.3, Math.sqrt(d2)); p.m_20256_(new Vec3((v.f_82479_ - bx) / d, 1.0, (v.f_82481_ - bz) / d)); p.f_19864_ = true; } }
        }
        if (sinkT >= 50) sinkT = -1;
    }

    // ================= Раскол сегмента =================
    void startBreak() {
        int k = bottom();
        breakT = 0; broken++; phase = broken + 1;
        stompT = -1; spinT = -1; sinkT = -1; sinkOff = 0; lances.clear(); shards.clear(); fogs.clear();
        title("phase" + broken, "aqua", "phase" + broken + ".sub", 50);
        var dk = Fx.effect("darkness");
        if (dk != null) for (ServerPlayer p : players()) p.m_7292_(new MobEffectInstance(dk, 40, 0, false, false));
        for (int h = 0; h < 2; h++) { hMode[h] = 0; hT[h] = 0; }
        double y = floor + segY[k];
        Fx.send(level, Fx.particle("flash"), bx, y, bz, 2, 1, 1, 1, 0);
        Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx, y, bz, 200, 1.5, 1.0, 1.5, 0.3);
        Fx.send(level, Fx.blockParticle(Fx.state("diamond_block")), bx, y, bz, 80, 1.5, 1.0, 1.5, 0.3);
        Fx.send(level, Fx.particle("explosion_emitter"), bx, y, bz, 1, 0, 0, 0, 0);
        Fx.sound(level, bx, y, bz, "entity.generic.explode", 20.0F, 0.6F);
        Fx.sound(level, bx, y, bz, "block.glass.break", 20.0F, 0.5F);
        Fx.sound(level, bx, y, bz, "entity.elder_guardian.curse", 20.0F, 0.8F);
        alive[k] = false;
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " name {\"translate\":\"" + KEY + "segments\",\"with\":[\"" + aliveCount() + "\"],\"color\":\"aqua\",\"bold\":true}");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " color " + BAR_COLOR[Math.min(3, broken)]);
        actionbar("break" + Math.min(3, broken), "aqua");
    }

    void tickBreak() {
        breakT++;
        if (breakT % 5 == 0) Fx.send(level, TEAL_P, bx, floor + 2, bz, 15, 1.5, 1, 1.5, 0.05);
        if (breakT == 30) spawnMinions();
        if (breakT >= 60) { breakT = -1; actionbar("hint", "aqua"); }
    }

    /** Места приспешников относительно центра зала: кольцо радиуса 7..9 вокруг башни, между колоннами (колонны в углах на +-13). */
    static final double[][] SLOTS = {{-9, 0}, {9, 0}, {-6, 7}, {6, 7}, {-6, -7}, {6, -7}, {0, 9}, {0, -9}};

    /** Сколько каких приспешников приходит в фазе (в зале 41x41 всего до MAX_MIN живых): {Дремотники, Стражи Шва}. */
    static int[] minionPlan(int phase, int players) {
        int np = Math.max(1, players);
        int drowsy = phase == 2 ? 3 : phase == 3 ? 1 : 2, guards = phase == 3 ? 3 : phase == 4 ? 2 : 0;
        return new int[]{Math.min(4, drowsy + (np - 1) / 2), guards == 0 ? 0 : Math.min(4, guards + (np - 1) / 2)};
    }

    void spawnMinions() {
        int have = 0;
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_6084_() && en.m_19880_().contains(MIN) && en.m_19880_().contains(ktag))) have++;
        int[] plan = minionPlan(phase, players().size());
        int slot = RNG.nextInt(SLOTS.length);
        for (int i = 0; i < plan[0] + plan[1] && have < MAX_MIN; i++, have++) {
            double[] s = SLOTS[(slot + i) % SLOTS.length];
            RiftModels.Kind kind = i < plan[0] ? RiftModels.Kind.DROWSY : RiftModels.Kind.STITCH_GUARD;
            RiftMobs.Mob m = RiftMobs.spawnKind(level, kind, ax + s[0], floor, az + s[1], hallTag);
            if (m != null && m.host != null) { m.host.m_20049_(MIN); m.host.m_20049_(ktag); }
        }
        Fx.sound(level, bx, floor + 2, bz, "entity.evoker.prepare_summon", 15.0F, 0.6F);
    }

    /** Приспешники уходят вместе с боссом. */
    void minionsOff() {
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_19880_().contains(MIN) && en.m_19880_().contains(ktag))) {
            Fx.send(level, Fx.particle("poof"), e.m_20185_(), e.m_20186_() + 0.4, e.m_20189_(), 6, 0.2, 0.2, 0.2, 0.02);
            RiftMobs.remove(e);
        }
    }

    // ================= Гибель =================
    void startDeath() {
        stage = 3; dyingT = 0;
        rings.clear(); lances.clear(); shards.clear(); fogs.clear(); sinkOff = 0; stompT = -1; spinT = -1; sinkT = -1;
        Cmd.run(level, ax, floor, az, "bossbar remove " + bar);
        minionsOff();
        Fx.sound(level, bx, floor + 3, bz, "entity.elder_guardian.death", 30.0F, 0.5F);
        Fx.sound(level, bx, floor + 3, bz, "entity.warden.death", 30.0F, 0.6F);
    }

    boolean dying() {
        dyingT++;
        eyeOpen = Math.max(0, eyeOpen - 0.02);
        spin = 3 + dyingT * 0.1;
        for (int k = 0; k < 4; k++) segAng[k] += segSpd[k] * spin;
        if (dyingT % 3 == 0) pose(3);
        if (dyingT % 4 == 0) {
            double[] u = Fx.unit();
            Fx.send(level, Fx.blockParticle(Fx.state("black_concrete")), bx + u[0] * 2, floor + 1.5 + Math.abs(u[1]) * 3, bz + u[2] * 2, 20, 0.5, 0.5, 0.5, 0.2);
            Fx.send(level, TEAL_P, bx, floor + 2, bz, 20, 1.5, 1.5, 1.5, 0.1);
            Fx.sound(level, bx, floor + 2, bz, "block.deepslate.break", 8.0F, 0.4F + dyingT / 120F);
        }
        if (dyingT == 100) {
            purge();   // хост и части уходят; победу засчитывает tickAll (Dungeons.lairWon: hallsCleared + сундук)
            won = true;
            double c = floor + 2;
            Fx.send(level, Fx.particle("flash"), bx, c, bz, 5, 2, 2, 2, 0);
            Fx.send(level, Fx.particle("explosion_emitter"), bx, c, bz, 4, 2, 2, 2, 0);
            for (int i = 0; i < 220; i++) { double[] u = Fx.unit(); Fx.send(level, i % 2 == 0 ? WHITE : TEAL_P, bx, c, bz, 0, u[0], u[1], u[2], 1.6); }
            Fx.sound(level, bx, c, bz, "entity.generic.explode", 30.0F, 0.6F);
            Fx.sound(level, bx, c, bz, "ui.toast.challenge_complete", 20.0F, 1.0F);
            String at = Cmd.f(ax) + " " + (floor + 1) + " " + Cmd.f(az + 3);
            for (int i = 0; i < 12; i++) Cmd.run(level, ax, floor, az, "summon experience_orb " + at + " {Value:400}");
            title("dead", "aqua", "dead.sub", 90);
            return true;
        }
        return false;
    }

    // ================= Урон =================
    static boolean environmental(LivingHurtEvent ev) {
        String id = ev.getSource() == null ? "" : ev.getSource().m_19385_();
        return id != null && (id.equals("inWall") || id.equals("fall") || id.equals("cramming") || id.equals("drown") || id.equals("flyIntoWall") || id.equals("inFire") || id.equals("onFire") || id.equals("lightningBolt"));
    }

    boolean vulnerable() { return stage == 2 && breakT < 0; }

    /**
     * Урон, обрезанный до ближайшего порога сегмента (75%, 50%, 25% ХП хоста): один удар не раскалывает два сегмента; последний удар не убивает
     * хост, а оставляет 1 ХП и запускает гибель (lethal).
     */
    float clampHit(float hp, float amt) {
        float floorHp = broken < 3 ? HOST_HP * (0.75F - broken * 0.25F) : 1.0F;
        if (hp - amt < floorHp) amt = hp > floorHp ? hp - floorHp : (floorHp > 1.0F ? 0.5F : 0.0F);
        if (broken >= 3 && hp - amt <= 1.0F) { amt = Math.max(0, hp - 1.0F); lethal = true; }
        return amt;
    }

    static void onHurt(LivingHurtEvent ev) {
        if (BOSSES.isEmpty()) return;
        LivingEntity target = ev.getEntity();
        Boss3 b = null;
        for (Boss3 x : BOSSES.values()) if (x.host != null && x.host == target) { b = x; break; }
        // приспешники не бьют друг друга и босса
        if (b == null) {
            if (Cmd.hasTagPrefix(target, MIN) && ev.getSource() != null && ev.getSource().m_7639_() != null && Cmd.hasTagPrefix(ev.getSource().m_7639_(), TAG)) ev.setCanceled(true);
            return;
        }
        if (environmental(ev) || !b.vulnerable()) {
            ev.setCanceled(true);
            if (!environmental(ev)) {
                Fx.send(b.level, WHITE, b.bx, b.floor + 2, b.bz, 12, 1.5, 1.5, 1.5, 0);
                Fx.sound(b.level, b.bx, b.floor + 2, b.bz, "block.amethyst_block.hit", 3.0F, 1.8F);
            }
            return;
        }
        ev.setAmount(b.clampHit(b.host.m_21223_(), hostDamage(ev.getAmount())));      // без других множителей: запас ровно MAX_HP
        int k = b.bottom();
        double y = b.floor + b.segY[k];
        Fx.send(b.level, Fx.blockParticle(Fx.state("black_concrete")), b.bx, y, b.bz, 12, 1.1, 0.8, 1.1, 0.1);
        Fx.send(b.level, TEAL_P, b.bx, y + SEG_H / 2, b.bz, 8, 1.3, 0.1, 1.3, 0.05);
        Fx.sound(b.level, b.bx, y, b.bz, "block.deepslate.hit", 2.0F, 0.6F + RNG.nextFloat() * 0.3F);
        Fx.sound(b.level, b.bx, y, b.bz, "block.amethyst_block.hit", 1.2F, 1.2F);
    }
}
