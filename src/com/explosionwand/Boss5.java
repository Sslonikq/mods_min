package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * «ПЕПЕЛЬНЫЙ ФЕНИКС» - босс Гнезда Пепельного святилища (зал 23, фаза 4.10). Огромная птица (размах крыльев ~20 блоков) из block_display на невидимом
 * хосте-гиганте: тёмное тело из обугленных перьев (чёрный бетон и чёрный камень), тлеющие трещины из магмы, пламя на кончиках крыльев, хвоста и гребня;
 * в фазе 4 перья вспыхивают бело-голубым. Крылья машут (три сустава с отставанием), в пике складываются.
 *
 * Где бой: Гнездо - круглая пещера R=30 с центром в метке boss (на помосте), свод до 15 блоков в центре и 4 у края (Boss5.vault), три лавовых лужка по краю,
 * сталагмиты вне круга R=9,5. Бой начинается, когда игрок входит в зал (Dungeons.spawnHall -> Boss.wake -> Boss5.wake), печать (метка seal на входе) закрывается.
 * Яйцо из пепла, из которого птица вылупляется, рисует сам босс (в шаблоне его нет). Блоков босс не меняет вовсе: огонь, жар и смерчи - только урон и частицы.
 * Победа: Dungeons.lairWon в начале гибели - зал засчитан, ставится сундук с добычей, снимается барьер Святилища (Rec.bossDead), печать открывается.
 *
 *  1 Полёт (красная полоска)    - кружит под сводом, пикирует на игрока с огненным следом по полу (после пике выдыхается: бей!), залпы перьев.
 *  2 Жар (жёлтая, 55% ХП)       - пол раскаляется секторами (видно за 1,8 с), огненные смерчи, приходят Искры.
 *  3 Пепел (белая, 0 ХП)        - падает и рассыпается в яйцо; 15 с оно «дышит», вокруг Пеплоходы. Разбил яйцо до таймера - Феникс возрождается ослабленным
 *                                  (700 ХП), не успел - полным (1500 ХП).
 *  4 Возрождённый (голубая)     - бело-голубое пламя, быстрее, двойные пике, самонаводящиеся перья; второй раз не возрождается.
 *
 * Хост - гигант на 1024 ХП (лимит мобов). Настоящие ХП: фазы 1-2 - 2000, яйцо - 150, возрождение - 1500 / 700; всё через множитель урона DMG_K = HOST_HP / MAX_HP,
 * ХП хоста подставляется по шкале (ХП x DMG_K). Один удар не перескакивает фазу (обрезка по порогу), смертельный удар оставляет 1 ХП и запускает следующую стадию.
 * Теги хоста: ewb5, ewb5_host, ewbk&lt;ключ&gt;, ewd, тег зала. Части облика: ewb5, ewb5_p&lt;i&gt;, ewbk&lt;ключ&gt;. Приспешники: ewb5_min, ewbk&lt;ключ&gt;, ewd, тег зала.
 */
public class Boss5 {
    static final Map<String, Boss5> BOSSES = new HashMap<>();
    static final Random RNG = Fx.RNG;
    /** У мобов max_health ограничен 1024: хост живёт на 1024, настоящие ХП даёт множитель урона DMG_K (запас жизни 1 = MAX_HP). */
    static final float MAX_HP = 2000, HOST_HP = 1024, DMG_K = HOST_HP / MAX_HP;
    /** Настоящие ХП яйца и возрождённого Феникса (ослабленного / полного). */
    static final float EGG_HP = 150, REBORN_FULL = 1500, REBORN_WEAK = 700;
    /** Фаза 2 наступает на 55% ХП жизни 1. */
    static final float P2_AT = 0.55F;
    /** Яйцо «дышит» 15 секунд. */
    static final int EGG_TICKS = 300;
    static final String KEY = "explosionwand.boss.phoenix.";
    static final String BAR = "explosionwand:ash_phoenix_";
    static final String TAG = "ewb5", MIN = "ewb5_min";
    /** Трофеи босса в сундуке Гнезда (затем набор тира 2 и материалы Святилища). Предметы мода; Посох Феникса регистрирует mod-engineer (до тех пор слот пуст). */
    static final List<String> LOOT = List.of(id("phoenix_staff"), id("phoenix_feather"));
    static String id(String n) { return "explosionwand:" + n; }
    /** Цвета полоски по фазам: Полёт, Жар, Пепел (яйцо), Возрождённый. */
    static final String[] BAR_COLOR = {"red", "yellow", "white", "blue"};
    /** Суффиксы ключей lang (explosionwand.boss.phoenix.*), которые нужны в ru_ru.json и en_us.json. */
    static final String[] LANG = {"name", "spawn.sub", "phase1", "phase1.sub", "phase2", "phase2.sub", "phase3", "phase3.sub", "phase4", "phase4.sub.weak", "phase4.sub.full",
            "egg", "egg.hint", "egg.timer", "egg.broken", "egg.hatch", "hint", "dive", "volley", "perch", "heat", "whirl", "sparks", "dead", "dead.sub"};

    // ---- геометрия Гнезда (tools/gen_ash.py: NR=30, cave_h) и полёта
    /** Радиус пещеры. */
    static final int R = 30;
    /**
     * Круг облёта и высота полёта над меткой boss (площадкой). Крылья по 10 блоков, а свод у стен низкий (12 на r=20, 7 на r=28), поэтому птица кружит над центром
     * на малом круге: кончики крыльев (с кренем и взмахами) остаются под сводом - проверяет BossAshTest.
     */
    static final double ORBIT_R = 6, CRUISE_Y = 6.0;
    /** Центр тела над полом при посадке/пике и над площадкой при яйце. */
    static final double PERCH_Y = 1.4, EGG_Y = 3.2;
    /** Доля пещеры, куда не залетают пике и не растекается жар (центр: помост у яйца). */
    static final double PLAT_SAFE = 5.5;
    /** Искры появляются высоко (сталагмиты Гнезда не выше 8 над полом пещеры), Пеплоходы - на полу у помоста (r 8,5: сталагмитов в круге 9,5 нет). */
    static final double SPARK_Y = 8, WALKER_R = 8.5, SPARK_ARC = 2.2;
    /** Сколько живых приспешников одновременно. */
    static final int MAX_MIN = 6;
    /** Высота свода над уровнем пола пещеры (клетка воздуха сверху) на расстоянии r от центра: 4 + int(11 sqrt(1 - (r/R)^2)). */
    static int vault(double r) { return 4 + (int) (11 * Math.sqrt(Math.max(0, 1 - (r / R) * (r / R)))); }

    // ---- части облика: 0..43 птица, 44..57 яйцо
    static final int EGG0 = 44, N = 58, WING0 = 22, WING_N = 11;
    static final int R_DARK = 0, R_DARK2 = 1, R_GLOW = 2, R_FLAME = 3, R_EYE = 4, R_BEAK = 5, R_FEATH = 6, R_FEATH2 = 7, R_EGGA = 8, R_EGGB = 9, R_CRACK = 10;
    /** Блоки по ролям: 0 - обычная палитра (тёмные перья и тлеющие трещины), 1 - бело-голубая (фаза 4). */
    static final String[][] PAL = {
            {"black_concrete", "blackstone", "magma_block", "fire", "glowstone", "orange_concrete", "black_concrete", "blackstone", "gray_concrete", "light_gray_concrete", "magma_block"},
            {"black_concrete", "blackstone", "sea_lantern", "soul_fire", "sea_lantern", "white_concrete", "light_blue_concrete", "white_concrete", "gray_concrete", "light_gray_concrete", "magma_block"}};
    static final int[] ROLE = new int[N];
    static {
        int[] r = ROLE;
        r[0] = R_DARK; r[1] = R_GLOW; r[2] = R_GLOW; r[3] = R_DARK2; r[4] = R_DARK; r[5] = R_DARK2; r[6] = R_BEAK; r[7] = r[8] = R_EYE; r[9] = R_DARK; r[10] = R_FLAME;
        for (int i = 11; i <= 13; i++) { r[i] = R_FEATH; r[i + 3] = R_GLOW; r[i + 6] = R_FLAME; }
        r[20] = r[21] = R_DARK2;
        for (int w = 0; w < 2; w++) {
            int b = WING0 + w * WING_N;
            r[b] = R_DARK; r[b + 1] = R_GLOW; r[b + 2] = R_DARK2; r[b + 3] = R_GLOW;
            for (int k = 0; k < 5; k++) r[b + 4 + k] = k % 2 == 0 ? R_FEATH : R_FEATH2;
            r[b + 9] = r[b + 10] = R_FLAME;
        }
        for (int L = 0; L < 5; L++) { r[EGG0 + 2 * L] = L % 2 == 0 ? R_EGGA : R_EGGB; r[EGG0 + 2 * L + 1] = L % 2 == 0 ? R_EGGB : R_EGGA; }
        for (int c = 0; c < 4; c++) r[EGG0 + 10 + c] = R_CRACK;
    }
    // крыло: плечо, плечо-локоть, локоть-запястье, длины маховых перьев и их разброс назад
    static final double SX = 1.3, SY = 0.7, SZ = 0.4, L1 = 2.6, L2 = 2.6;
    static final double[] LK = {3.2, 3.6, 3.9, 3.6, 3.1};
    static final double[] PSI = {-0.25, -0.02, 0.2, 0.42, 0.65};
    // яйцо: диаметры пяти слоёв снизу вверх, высота слоя
    static final double[] EGG_D = {3.4, 4.4, 4.4, 3.6, 2.2};
    static final double EGG_LH = 1.32;
    /** Локальный сдвиг по высоте: хост стоит на 6 ниже центра тела, пассажиры едут на 9 выше ног (3 ниже центра). */
    static final double C = -3.0;
    /** Размах взмаха крыла (рад на первом суставе). */
    static final double AMP = 0.12, ROLL_MAX = 0.08;

    static ParticleOptions ORANGE, RED, GOLD, BLUE, WHITE;

    enum St { SPAWN, FIND, WAKE, HATCH, FIGHT, FALL, EGG, REBORN, DYING }
    enum Mode { CIRCLE, WARN, DIVE, SKIM, PERCH, RISE, RAGE }

    final ServerLevel level;
    final DungeonData.Rec rec;
    final Dungeons.Hall hall;
    final String hallTag, key, ktag, bar;
    /** Центр Гнезда (мир) и уровень метки boss (верх помоста; пол пещеры вокруг на 1 ниже). */
    final double ax, az;
    final int floor;
    LivingEntity host;
    final String[] uuid = new String[N], last = new String[N];
    St st = St.SPAWN; Mode mode = Mode.CIRCLE;
    int t = 0, stT = 0, modeT = 0, phase = 1, life = 1, pal = 0;
    boolean lethal = false, won = false, paid = false, eggBroken = false;
    // тело птицы
    double px, py, pz, vx, vy, vz, yaw = 0, pitch = 0, roll = 0, fold = 0, flapPh = 0, gs = 0, eg = 1, orbA = 0, orbDir = 1;
    // пике
    double tgtX, tgtZ, dvx, dvy, dvz, hx, hz, diveSpd, skimLeft;
    int warnLen = 24, doubleLeft = 0;
    // кулдауны
    int diveCd = 100, volleyCd = 60, heatCd = 130, whirlCd = 170, sparkCd = 40, volleyT = -1;
    double vYaw, vPitch;
    final List<double[]> feathers = new ArrayList<>();   // x,y,z,vx,vy,vz,жизнь,самонаведение
    final List<double[]> trails = new ArrayList<>();     // x,z,ttl
    final List<int[]> heat = new ArrayList<>();          // сектор, таймер
    final List<double[]> whirls = new ArrayList<>();     // x,z,таймер
    final List<double[]> spawns = new ArrayList<>();     // x,y,z,таймер,вид (0 Искра, 1 Пеплоход)
    final Map<ServerPlayer, Integer> hitCd = new WeakHashMap<>();

    static void init() {
        if (ORANGE != null) return;
        ORANGE = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.08F), 1.6F);
        RED = new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.05F), 1.4F);
        GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.3F), 1.3F);
        BLUE = new DustParticleOptions(new Vector3f(0.55F, 0.85F, 1.0F), 1.6F);
        WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 1.4F);
    }

    Boss5(ServerLevel level, DungeonData.Rec rec, Dungeons.Hall hall, String hallTag) {
        this.level = level; this.rec = rec; this.hall = hall; this.hallTag = hallTag;
        key = Integer.toHexString(rec.key.hashCode()); ktag = "ewbk" + key; bar = BAR + key;
        ax = rec.x + hall.boss.x + 0.5; az = rec.z + hall.boss.z + 0.5; floor = rec.y + hall.boss.y;
        px = ax; py = floor + EGG_Y; pz = az;
    }

    // ================= Пробуждение, сброс, общие тики =================
    /** Зал разбужен: босс начинает пробуждаться (яйцо шевелится, потом вылупляется птица). */
    static void wake(ServerLevel sl, DungeonData.Rec rec, Dungeons.Hall h, String hallTag) {
        init();
        if (BOSSES.containsKey(rec.key)) return;
        BOSSES.put(rec.key, new Boss5(sl, rec, h, hallTag));
    }

    /** Сброс боя (зал опустел, мирный режим, смена раскладки): убрать босса, части, приспешников и полоску. */
    static void reset(ServerLevel sl, DungeonData.Rec rec, Dungeons.Hall h) {
        init();
        Boss5 b = BOSSES.remove(rec.key);
        if (b == null) b = new Boss5(sl, rec, h, Dungeons.tagOf(rec, h.id));
        b.cleanup();
    }

    static void clear() { BOSSES.clear(); }

    static String barOf(DungeonData.Rec rec) { return BAR + Integer.toHexString(rec.key.hashCode()); }

    static void tickAll(ServerLevel sl, DungeonData data) {
        if (BOSSES.isEmpty()) return;
        Iterator<Boss5> it = BOSSES.values().iterator();
        while (it.hasNext()) {
            Boss5 b = it.next();
            if (b.level != sl) continue;
            boolean done;
            try { done = b.tick(); }
            catch (RuntimeException e) { b.cleanup(); it.remove(); throw e; }
            if (b.won && !b.paid) { b.paid = true; Dungeons.lairWon(sl, data, b.rec, b.hall, b.players()); }   // победа засчитана в начале гибели (startDeath)
            if (done) it.remove();
        }
    }

    /** Убрать всё, что принадлежит этому бою: хост и части (discard), приспешников, полоску. */
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
    /** Живые игроки в Гнезде: в круге R и по высоте от пола до верха свода. */
    List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) {
            if (!p.m_6084_() || p.m_5833_()) continue;
            if (inLair(p.m_20185_(), p.m_20186_(), p.m_20189_())) out.add(p);
        }
        return out;
    }

    boolean inLair(double x, double y, double z) {
        return Math.hypot(x - ax, z - az) <= R + 0.5 && y >= floor - 3 && y <= floor + 20;
    }

    /** Игроки в Гнезде: заголовки и полоска не видны в тоннеле за печатью. */
    String sel() {
        return "@a[gamemode=!spectator,x=" + Cmd.f(ax - R - 1) + ",y=" + Cmd.f(floor - 3) + ",z=" + Cmd.f(az - R - 1) + ",dx=" + Cmd.f(2 * R + 2) + ",dy=24,dz=" + Cmd.f(2 * R + 2) + "]";
    }

    ServerPlayer nearest() {
        ServerPlayer best = null; double bd = 1e9;
        for (ServerPlayer p : players()) { double d = (p.m_20185_() - px) * (p.m_20185_() - px) + (p.m_20189_() - pz) * (p.m_20189_() - pz); if (d < bd) { bd = d; best = p; } }
        return best;
    }

    void hurt(ServerPlayer p, float d) { p.m_6469_(level.m_269111_().m_269425_(), d); }
    void burn(ServerPlayer p, float d, int sec) { hurt(p, d); if (sec > 0) p.m_20254_(sec); }
    void shove(ServerPlayer p, double fromX, double fromZ, double h, double up) {
        Vec3 v = p.m_20182_(); double dx = v.f_82479_ - fromX, dz = v.f_82481_ - fromZ, d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
        p.m_20256_(new Vec3(dx / d * h, up, dz / d * h)); p.f_19864_ = true;
    }
    void actionbar(String k, String c) { Cmd.run(level, ax, floor, az, "title " + sel() + " actionbar {\"translate\":\"" + KEY + k + "\",\"color\":\"" + c + "\"}"); }

    /** Заголовок на экране: title и sub - суффиксы ключей explosionwand.boss.phoenix.* (lang ru/en). */
    void title(String title, String color, String sub, int stay) {
        Cmd.run(level, ax, floor, az, "title " + sel() + " times 10 " + stay + " 20");
        Cmd.run(level, ax, floor, az, "title " + sel() + " subtitle {\"translate\":\"" + KEY + sub + "\",\"color\":\"gray\",\"italic\":true}");
        Cmd.run(level, ax, floor, az, "title " + sel() + " title {\"translate\":\"" + KEY + title + "\",\"color\":\"" + color + "\",\"bold\":true}");
    }

    boolean blue() { return life == 2; }
    ParticleOptions flame() { return Fx.particle(blue() ? "soul_fire_flame" : "flame"); }
    ParticleOptions dust() { return blue() ? BLUE : ORANGE; }
    static double ease(double k) { k = Math.max(0, Math.min(1, k)); return k * k * (3 - 2 * k); }
    static double wrap(double a) { while (a > Math.PI) a -= 2 * Math.PI; while (a < -Math.PI) a += 2 * Math.PI; return a; }
    double rad(double x, double z) { return Math.hypot(x - ax, z - az); }

    /** Урон по хосту из урона игрока: ровно MAX_HP урона убивают босса в жизни 1 (настоящие ХП = ХП хоста / DMG_K). */
    static float hostDamage(float raw) { return raw * DMG_K; }

    /** Сектор пола (0..7, по 45 градусов вокруг центра) для точки со смещением dx, dz от центра. Чистая функция для теста. */
    static int sectorOf(double dx, double dz) {
        double a = Math.atan2(dz, dx); if (a < 0) a += 2 * Math.PI;
        return Math.min(7, (int) (a / (Math.PI / 4)));
    }

    /** Приспешники по стадиям: {Искры, Пеплоходы}; на каждых двух лишних игроков +1, всего не больше MAX_MIN живых. Чистая функция для теста. */
    static int[] minionPlan(int phase, int players) {
        int extra = (Math.max(1, players) - 1) / 2;
        if (phase == 3) return new int[]{0, Math.min(5, 4 + extra)};
        if (phase == 4) return new int[]{Math.min(4, 3 + extra), 0};
        if (phase == 2) return new int[]{Math.min(4, 2 + extra), 0};
        return new int[]{0, 0};
    }

    // ================= Появление хоста =================
    void spawnHost() {
        StringBuilder sb = new StringBuilder();
        double hy = floor + EGG_Y - 6;
        sb.append("summon minecraft:giant ").append(Cmd.f(ax)).append(" ").append(Cmd.f(hy)).append(" ").append(Cmd.f(az))
          .append(" {Tags:[\"" + TAG + "\",\"" + TAG + "_host\",\"").append(ktag).append("\",\"ewd\",\"").append(hallTag).append("\"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,CanPickUpLoot:0b,DeathLootTable:\"minecraft:empty\",")
          .append("Attributes:[{Name:\"minecraft:generic.max_health\",Base:").append((int) HOST_HP).append("d},{Name:\"minecraft:generic.knockback_resistance\",Base:1d}],")
          .append("Health:").append((int) HOST_HP).append("f,ActiveEffects:[{Id:14,Amplifier:0b,Duration:-1,ShowParticles:0b}],Passengers:[");
        for (int i = 0; i < N; i++) {
            if (i > 0) sb.append(",");
            sb.append("{id:\"minecraft:block_display\",Tags:[\"" + TAG + "\",\"" + TAG + "_p").append(i).append("\",\"").append(ktag).append("\"],")
              .append(Disp.block(PAL[0][ROLE[i]])).append(",view_range:3f,brightness:{sky:15,block:15},")
              .append(Disp.tf(0, C, 0, 0.01, 0.01, 0.01, Disp.ID)).append("}");
        }
        sb.append("]}");
        Cmd.run(level, ax, floor, az, sb.toString());
        Cmd.run(level, ax, floor, az, "bossbar remove " + bar);
        Cmd.run(level, ax, floor, az, "bossbar add " + bar + " {\"translate\":\"" + KEY + "name\",\"color\":\"gold\",\"bold\":true}");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " color " + BAR_COLOR[0]);
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " style notched_10");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " max 1000");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " value 1000");
        title("name", "gold", "spawn.sub", 90);
        Fx.sound(level, ax, floor + 4, az, "entity.warden.heartbeat", 30.0F, 0.4F);
        Fx.sound(level, ax, floor + 4, az, "block.respawn_anchor.charge", 30.0F, 0.5F);
    }

    /** Хост и части найдены одним проходом по тегам боя (хост появляется на следующий тик после summon). */
    boolean findParts() {
        int found = 0;
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_19880_().contains(ktag))) {
            for (String tg : e.m_19880_()) {
                if (tg.equals(TAG + "_host") && e instanceof LivingEntity le) { host = le; found++; }
                else if (tg.startsWith(TAG + "_p")) { try { uuid[Integer.parseInt(tg.substring(TAG.length() + 2))] = e.m_20149_(); found++; } catch (NumberFormatException ignored) {} }
            }
        }
        return host != null && found >= N + 1;
    }

    // ================= Тик =================
    boolean tick() {
        t++; stT++;
        long bit = 1L << hall.id;
        if ((rec.hallsSpawned & bit) == 0 || ((rec.hallsCleared & bit) != 0 && st != St.DYING)) { cleanup(); return true; }   // зал сброшен или засчитан без нас
        if (st == St.SPAWN) { purge(); spawnHost(); st = St.FIND; stT = 0; return false; }
        if (st == St.FIND) {
            if (!findParts()) { if (stT > 40) { cleanup(); return true; } return false; }
            st = St.WAKE; stT = 0; return false;
        }
        if (host == null || host.m_213877_() || !host.m_6084_()) { cleanup(); return true; }   // хоста убрали снаружи (сброс зала, команда)
        if (host.m_6060_()) host.m_20095_();
        List<ServerPlayer> ps = players();
        switch (st) {
            case WAKE -> wakeTick();
            case HATCH, REBORN -> hatchTick();
            case FIGHT -> fightTick(ps);
            case FALL -> fallTick();
            case EGG -> eggTick(ps);
            case DYING -> { if (dying()) return true; }
            default -> { }
        }
        face();
        host.m_6034_(px, py - (st == St.EGG ? 9 : 6), pz);   // в яйце гигант на 3 ниже: хитбокс не торчит над скорлупой host.m_20256_(new Vec3(0, 0, 0));
        if (t % 3 == 0) poseAll(3);
        if (t % 10 == 0) bar();
        return false;
    }

    // ================= Бар и палитра =================
    float barMax() { return st == St.EGG ? EGG_HP * DMG_K : life == 2 ? rebornMax * DMG_K : HOST_HP; }
    float rebornMax = REBORN_FULL;   // запас жизни 2 при появлении (700 или 1500): шкала полоски от фактического запаса

    void bar() {
        int v = (int) Math.max(0, Math.min(1000, host.m_21223_() / barMax() * 1000));
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " value " + v);
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " players " + sel());
    }

    void barStyle(String nameKey, String color) {
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " name {\"translate\":\"" + KEY + nameKey + "\",\"color\":\"" + (color.equals("blue") ? "aqua" : color.equals("white") ? "gray" : "gold") + "\",\"bold\":true}");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " color " + color);
    }

    void applyPalette(int p) {
        pal = p;
        for (int i = 0; i < EGG0; i++) if (uuid[i] != null) Cmd.run(level, ax, floor, az, "data merge entity " + uuid[i] + " {" + Disp.block(PAL[p][ROLE[i]]) + "}");
    }

    // ================= Позирование =================
    void send(int i, int dur, double cx, double cy, double cz, double sx, double sy, double sz, double[] q) {
        if (uuid[i] == null) return;
        String tf = Disp.tf(cx, cy, cz, Math.max(0.001, sx), Math.max(0.001, sy), Math.max(0.001, sz), q);
        if (tf.equals(last[i])) return;       // не менялось: команду не шлём
        last[i] = tf;
        Cmd.run(level, ax, floor, az, "data merge entity " + uuid[i] + " {start_interpolation:0,interpolation_duration:" + dur + "," + tf + "}");
    }

    double[] Q = Disp.ID;

    /** Часть птицы: центр (lx,ly,lz) и размеры в системе тела (z - вперёд), поворот lq относительно тела. */
    void put(int i, int dur, double lx, double ly, double lz, double sx, double sy, double sz, double[] lq) {
        double[] r = Disp.rot(Q, lx * gs, ly * gs, lz * gs);
        send(i, dur, r[0], r[1] + C, r[2], sx * gs, sy * gs, sz * gs, Disp.mul(Q, lq));
    }

    void poseAll(int dur) {
        if (gs > 0.01) poseBird(dur); else for (int i = 0; i < EGG0; i++) send(i, dur, 0, C, 0, 0.001, 0.001, 0.001, Disp.ID);
        if (eg > 0.01) poseEgg(dur); else for (int i = EGG0; i < N; i++) send(i, dur, 0, C, 0, 0.001, 0.001, 0.001, Disp.ID);
    }

    /** Углы крыла для фазы взмаха ph и степени складывания fold: {a1, a2, a3, sweep1, sweep2}. Чистая функция для теста. */
    static double[] wingAngles(double ph, double fold, double amp) {
        double a1 = (amp * Math.sin(ph) + 0.04) * (1 - fold) - 1.0 * fold;                 // углы относительные: к концу крыла складываются (по ~±0,4 рад на конце)
        double a2 = amp * 0.75 * Math.sin(ph - 0.8) * (1 - fold) - 0.5 * fold;
        double a3 = amp * 0.6 * Math.sin(ph - 1.5) * (1 - fold) - 0.4 * fold;
        return new double[]{a1, a2, a3, 0.1 + 0.55 * fold, 0.1 + 0.4 * fold};
    }

    /** Ключевые точки крыла в системе тела: {плечо, локоть, запястье, концы пяти маховых перьев...}. Чистая функция для теста (размах крыльев). */
    static double[][] wingPoints(int sg, double[] ang) {
        double[] r1 = Disp.mul(Disp.q(0, 1, 0, sg * ang[3]), Disp.q(0, 0, 1, sg * ang[0]));
        double[] r2 = Disp.mul(r1, Disp.mul(Disp.q(0, 1, 0, sg * ang[4]), Disp.q(0, 0, 1, sg * ang[1])));
        double[] d1 = Disp.rot(r1, sg, 0, 0), d2 = Disp.rot(r2, sg, 0, 0);
        double[] s = {sg * SX, SY, SZ};
        double[] e = {s[0] + d1[0] * L1, s[1] + d1[1] * L1, s[2] + d1[2] * L1};
        double[] w = {e[0] + d2[0] * L2, e[1] + d2[1] * L2, e[2] + d2[2] * L2};
        double[][] out = new double[3 + LK.length][];
        out[0] = s; out[1] = e; out[2] = w;
        for (int k = 0; k < LK.length; k++) {
            double[] r3 = Disp.mul(r2, Disp.mul(Disp.q(0, 1, 0, sg * PSI[k]), Disp.q(0, 0, 1, sg * ang[2])));
            double[] d3 = Disp.rot(r3, sg, 0, 0);
            out[3 + k] = new double[]{w[0] + d3[0] * LK[k], w[1] + d3[1] * LK[k], w[2] + d3[2] * LK[k]};
        }
        return out;
    }

    void poseBird(int dur) {
        Q = Disp.mul(Disp.q(0, 1, 0, yaw), Disp.mul(Disp.q(1, 0, 0, pitch), Disp.q(0, 0, 1, roll)));
        double bob = Math.cos(flapPh) * 0.25 * (1 - fold);
        // тело, грудь, шея, голова, клюв, глаза, гребень
        double[] torsoQ = Disp.q(1, 0, 0, 0.04 * Math.sin(flapPh));
        put(0, dur, 0, bob, 0, 3.0, 2.8, 5.2, torsoQ);
        put(1, dur, 0, bob - 1.35, 0.3, 2.0, 0.5, 3.2, Disp.ID);
        put(2, dur, 0, bob + 1.43, -0.2, 0.5, 0.12, 3.6, Disp.ID);
        put(3, dur, 0, bob + 0.1, 2.9, 2.4, 2.2, 2.0, Disp.ID);
        put(4, dur, 0, bob + 1.4, 3.8, 1.2, 2.0, 1.2, Disp.q(1, 0, 0, -0.45));
        put(5, dur, 0, bob + 2.4, 4.6, 1.6, 1.5, 1.9, Disp.ID);
        put(6, dur, 0, bob + 2.15, 5.9, 0.7, 0.5, 1.4, Disp.ID);
        put(7, dur, 0.82, bob + 2.65, 5.1, 0.28, 0.28, 0.28, Disp.ID);
        put(8, dur, -0.82, bob + 2.65, 5.1, 0.28, 0.28, 0.28, Disp.ID);
        put(9, dur, 0, bob + 3.4, 3.9, 0.4, 1.6, 0.4, Disp.q(1, 0, 0, 0.5));
        put(10, dur, 0, bob + 4.6, 3.5 + 0.2 * Math.sin(t * 0.4), 1.0, 1.8, 1.0, Disp.ID);
        // хвост: три длинных пера, вьются
        double tail = 6.5;
        for (int i = 0; i < 3; i++) {
            double ya = (i - 1) * 0.3 + Math.sin(t * 0.1 + i) * 0.05, tp = -0.1 + Math.sin(t * 0.12 + i * 1.3) * 0.08 - fold * 0.1;
            double[] q = Disp.mul(Disp.q(0, 1, 0, ya), Disp.q(1, 0, 0, tp));
            double[] c = Disp.rot(q, 0, 0, -tail / 2), tip = Disp.rot(q, 0, 0, -tail), up = Disp.rot(q, 0, 0.19, 0);
            put(11 + i, dur, c[0], bob + 0.1 + c[1], -2.6 + c[2], 0.7, 0.3, tail, q);
            put(14 + i, dur, c[0] + up[0], bob + 0.1 + c[1] + up[1], -2.6 + c[2], 0.2, 0.1, tail * 0.7, q);
            put(17 + i, dur, tip[0], bob + 0.1 + tip[1] + 0.9, -2.6 + tip[2], 1.3, 2.0, 1.3, Disp.ID);
        }
        // лапы: подобраны в полёте, вытянуты на посадке
        double leg = mode == Mode.PERCH || mode == Mode.SKIM ? 0.5 : -0.35;
        for (int s = 0; s < 2; s++) put(20 + s, dur, (s == 0 ? 0.8 : -0.8), bob - 1.9, 0.9, 0.4, 1.1, 0.4, Disp.q(1, 0, 0, leg));
        // крылья
        double amp = AMP * (mode == Mode.RISE || st == St.HATCH || st == St.REBORN ? 1.3 : 1.0);
        double[] ang = wingAngles(flapPh, fold, amp);
        for (int w = 0; w < 2; w++) {
            int sg = w == 0 ? 1 : -1, b = WING0 + w * WING_N;
            double[] r1 = Disp.mul(Disp.q(0, 1, 0, sg * ang[3]), Disp.q(0, 0, 1, sg * ang[0]));
            double[] r2 = Disp.mul(r1, Disp.mul(Disp.q(0, 1, 0, sg * ang[4]), Disp.q(0, 0, 1, sg * ang[1])));
            double[][] pts = wingPoints(sg, ang);
            double[] s = pts[0], e = pts[1], wr = pts[2];
            put(b, dur, (s[0] + e[0]) / 2, bob + (s[1] + e[1]) / 2, (s[2] + e[2]) / 2, L1 + 0.1, 0.5, 2.4, r1);
            double[] c1 = Disp.rot(r1, 0, 0.27, 0);
            put(b + 1, dur, (s[0] + e[0]) / 2 + c1[0], bob + (s[1] + e[1]) / 2 + c1[1], (s[2] + e[2]) / 2 + c1[2], L1 * 0.8, 0.12, 0.35, r1);
            put(b + 2, dur, (e[0] + wr[0]) / 2, bob + (e[1] + wr[1]) / 2, (e[2] + wr[2]) / 2, L2 + 0.1, 0.4, 2.0, r2);
            double[] c2 = Disp.rot(r2, 0, 0.23, 0);
            put(b + 3, dur, (e[0] + wr[0]) / 2 + c2[0], bob + (e[1] + wr[1]) / 2 + c2[1], (e[2] + wr[2]) / 2 + c2[2], L2 * 0.8, 0.1, 0.3, r2);
            for (int k = 0; k < LK.length; k++) {
                double[] r3 = Disp.mul(r2, Disp.mul(Disp.q(0, 1, 0, sg * PSI[k]), Disp.q(0, 0, 1, sg * ang[2])));
                double[] tip = pts[3 + k];
                put(b + 4 + k, dur, (wr[0] + tip[0]) / 2, bob + (wr[1] + tip[1]) / 2, (wr[2] + tip[2]) / 2, LK[k], 0.22, 0.75, r3);
                if (k == 2 || k == 4) put(b + (k == 2 ? 9 : 10), dur, tip[0], bob + tip[1] + 0.9, tip[2], 1.2, 1.9, 1.2, Disp.ID);
            }
        }
    }

    /** Яйцо из пепла: пять слоёв (два квадрата под 45 градусов дают восьмиугольник), магмовые трещины; «дышит» и вздрагивает. Центр - центр хоста. */
    void poseEgg(int dur) {
        double pulse = eg * (1 + 0.035 * Math.sin(stT * 0.25));
        double glow = st == St.EGG ? Math.min(1, stT / (double) EGG_TICKS) : st == St.WAKE ? Math.min(1, stT / 70.0) * 0.6 : 0.5;
        double[] wob = Disp.q(0, 0, 1, 0.025 * Math.sin(stT * 0.37) * (0.4 + glow));
        for (int L = 0; L < 5; L++) {
            double a = EGG_D[L] / 1.15, y = (-2 + L) * EGG_LH;
            double[] q = Disp.mul(wob, Disp.ID), q45 = Disp.mul(wob, Disp.q(0, 1, 0, Math.PI / 4));
            double[] o = Disp.rot(wob, 0, y * pulse, 0);
            send(EGG0 + 2 * L, dur, o[0], o[1] + C, o[2], a * pulse, EGG_LH * 1.04 * pulse, a * pulse, q);
            send(EGG0 + 2 * L + 1, dur, o[0], o[1] + C, o[2], a * pulse, EGG_LH * 1.04 * pulse, a * pulse, q45);
        }
        double[] ang = {0.5, 2.0, 3.5, 5.0}, hy = {-1.3, 0.6, -0.2, 1.6};
        int[] layer = {1, 2, 2, 3};
        for (int c = 0; c < 4; c++) {
            double rr = EGG_D[layer[c]] * 0.56 * pulse, thick = (0.12 + 0.2 * glow) * pulse;
            double[] q = Disp.mul(wob, Disp.q(0, 1, 0, -ang[c]));
            double[] o = Disp.rot(wob, Math.cos(ang[c]) * rr, hy[c] * pulse, Math.sin(ang[c]) * rr);
            send(EGG0 + 10 + c, dur, o[0], o[1] + C, o[2], thick, (0.8 + 0.5 * glow) * pulse, 0.5 * pulse, q);
        }
    }

    // ================= Движение =================
    void setPos(double x, double y, double z) {
        double dx = x - ax, dz = z - az, r = Math.hypot(dx, dz);
        if (r > R - 1.0) { x = ax + dx / r * (R - 1.0); z = az + dz / r * (R - 1.0); }
        vx = x - px; vy = y - py; vz = z - pz;
        px = x; py = y; pz = z;
    }

    void flyTo(double tx, double ty, double tz, double k) { setPos(px + (tx - px) * k, py + (ty - py) * k, pz + (tz - pz) * k); }

    /** Направление и крен по скорости: нос по ходу, наклон по вертикали, крен в повороте; на посадке смотрит на игрока. */
    void face() {
        double sp = Math.hypot(vx, vz), ty = yaw, tp = 0;
        if (st == St.EGG || st == St.WAKE) return;
        if (mode == Mode.PERCH && st == St.FIGHT) {
            ServerPlayer n = nearest();
            if (n != null) ty = Math.atan2(n.m_20185_() - px, n.m_20189_() - pz);
        } else if (sp > 0.04) {
            ty = Math.atan2(vx, vz);
            tp = Math.max(-0.7, Math.min(0.9, Math.atan2(-vy, Math.max(0.25, sp)) * 0.8));
        }
        double d = wrap(ty - yaw);
        yaw += d * 0.2;
        pitch += (tp - pitch) * 0.2;
        if (st == St.FALL) roll += 0.25;                                  // падая, кувыркается
        else if (st == St.DYING) roll += (0.5 * Math.sin(stT * 0.3) - roll) * 0.2;
        else roll += (Math.max(-ROLL_MAX, Math.min(ROLL_MAX, -d * 2)) - roll) * 0.15;   // крен в повороте: внешнее крыло поднимается, у свода его высота ограничена
    }

    double flapRate() {
        double r = switch (mode) { case CIRCLE -> 0.26; case WARN -> 0.5; case RISE, RAGE -> 0.45; case PERCH -> 0.12; default -> 0.0; };
        if (st == St.HATCH || st == St.REBORN) r = 0.42;
        return r * (blue() ? 1.25 : 1.0);
    }

    double[] orbit(double a) { double r = ORBIT_R + Math.sin(t * 0.02) * 1.0; return new double[]{ax + Math.cos(a) * r, floor + CRUISE_Y + Math.sin(t * 0.05) * 0.6, az + Math.sin(a) * r}; }
    double orbW() { return blue() ? 0.06 : phase == 2 ? 0.05 : 0.045; }

    // ================= Пробуждение и вылупление =================
    void wakeTick() {
        gs = 0; eg = 1;
        if (stT == 2) title("name", "gold", "spawn.sub", 90);
        if (stT % 14 == 0) Fx.sound(level, ax, floor + 3, az, "entity.warden.heartbeat", 16.0F, 0.35F + stT / 200F);
        if (stT % 3 == 0) Fx.send(level, flame(), ax + (RNG.nextDouble() - 0.5) * 3, floor + 0.5 + RNG.nextDouble() * 6, az + (RNG.nextDouble() - 0.5) * 3, 3, 0.2, 0.3, 0.2, 0.02);
        if (stT >= 70) { st = St.HATCH; stT = 0; }
    }

    /** Птица вылупляется из яйца (жизнь 1) или возрождается из него (жизнь 2): яйцо рассыпается, птица растёт и взмывает к кругу. Неуязвима. */
    void hatchTick() {
        int n = stT;
        double k = ease(n / 70.0);
        eg = Math.max(0, 1 - n / 25.0);
        gs = 0.15 + 0.85 * k;
        if (n == 1) {
            if (st == St.HATCH) title("phase1", "red", "phase1.sub", 70);
            Fx.send(level, Fx.particle("flash"), ax, floor + 3, az, 2, 1, 1, 1, 0);
            Fx.send(level, Fx.particle("explosion_emitter"), ax, floor + 2, az, 2, 1, 1, 1, 0);
            Fx.send(level, Fx.blockParticle(Fx.state("gray_concrete")), ax, floor + 3, az, 120, 1.5, 2.5, 1.5, 0.25);
            Fx.send(level, Fx.particle("lava"), ax, floor + 3, az, 30, 1.5, 2.5, 1.5, 0);
            Fx.sound(level, ax, floor + 3, az, "entity.generic.explode", 30.0F, 0.7F);
            Fx.sound(level, ax, floor + 3, az, "entity.ender_dragon.growl", 30.0F, 0.9F);
            orbA = Math.atan2(0, 1);
        }
        double[] o = orbit(orbA);
        setPos(ax + (o[0] - ax) * k, floor + EGG_Y + (o[1] - floor - EGG_Y) * k, az + (o[2] - az) * k);
        flapPh += flapRate();
        if (n % 2 == 0) Fx.send(level, flame(), px, py, pz, 6, 1.4 * gs, 1.0 * gs, 1.4 * gs, 0.04);
        if (n % 10 == 0) Fx.sound(level, px, py, pz, "entity.ender_dragon.flap", 10.0F, 1.2F);
        if (n >= 70) {
            st = St.FIGHT; mode = Mode.CIRCLE; modeT = 0; gs = 1; eg = 0; stT = 0;
            actionbar("hint", blue() ? "aqua" : "gold");
        }
    }

    // ================= Бой =================
    void fightTick(List<ServerPlayer> ps) {
        float hp = host.m_21223_();
        if (lethal || hp <= 1.01F) { lethal = false; if (life == 1) startFall(); else startDeath(); return; }
        if (life == 1 && phase == 1 && hp <= HOST_HP * P2_AT + 0.01F) { startPhase2(); return; }
        modeT++;
        flapPh += flapRate();
        double tf = mode == Mode.DIVE || mode == Mode.SKIM ? 1.0 : mode == Mode.PERCH ? 0.5 : 0.0;
        fold += (tf - fold) * 0.15;
        if (mode == Mode.CIRCLE || mode == Mode.RISE) { if (Math.sin(flapPh) > 0.98 && t % 6 < 3) Fx.sound(level, px, py, pz, "entity.ender_dragon.flap", 8.0F, 1.1F); }
        switch (mode) {
            case CIRCLE -> {
                orbA += orbW() * orbDir;
                double[] o = orbit(orbA);
                flyTo(o[0], o[1], o[2], 0.12);
                if (!ps.isEmpty()) circleAttacks(ps);
            }
            case WARN -> warnTick();
            case DIVE -> diveTick(ps);
            case SKIM -> skimTick(ps);
            case PERCH -> {
                if (modeT == 1) actionbar("perch", "yellow");
                if (modeT % 4 == 0) Fx.send(level, flame(), px, py + 1, pz, 4, 1.4, 0.8, 1.4, 0.03);
                setPos(px, py, pz);
                if (modeT >= (life == 2 ? 45 : 70)) { mode = Mode.RISE; modeT = 0; }
            }
            case RISE -> {
                double a = Math.atan2(pz - az, px - ax) + 0.4 * orbDir;
                double[] o = orbit(a);
                flyTo(o[0], o[1], o[2], 0.07);
                if (modeT >= 40) { mode = Mode.CIRCLE; modeT = 0; orbA = Math.atan2(pz - az, px - ax); }
            }
            case RAGE -> rageTick();
        }
        if (volleyT >= 0 && mode != Mode.CIRCLE) volleyT = -1;
        tickFeathers(ps); tickTrails(ps);
        if (mode != Mode.RAGE) supportAttacks(ps);
        tickHeat(ps); tickWhirls(ps); tickSpawns();
    }

    /** Атаки в круге: залп перьев (телеграф линиями 1 с) или пике. Начинаются только когда игроки в Гнезде. */
    void circleAttacks(List<ServerPlayer> ps) {
        if (volleyT >= 0) { tickVolley(ps); return; }
        if (--diveCd <= 0) { startWarn(ps, false); return; }
        if (--volleyCd <= 0) volleyT = 0;
    }

    // --- залп перьев ---
    double[] head() { double[] r = Disp.rot(Q, 0, 2.3 * gs, 5.4 * gs); return new double[]{px + r[0], py + r[1], pz + r[2]}; }

    void tickVolley(List<ServerPlayer> ps) {
        int n = life == 2 ? 7 : phase == 2 ? 7 : 5;
        int warn = life == 2 ? 16 : 20;
        double[] h = head();
        if (volleyT == 0) {
            Vec3 v = ps.get(RNG.nextInt(ps.size())).m_20182_();
            double dx = v.f_82479_ - h[0], dy = v.f_82480_ + 1.0 - h[1], dz = v.f_82481_ - h[2];
            vYaw = Math.atan2(dx, dz); vPitch = Math.atan2(dy, Math.max(1.0, Math.hypot(dx, dz)));
            Fx.sound(level, h[0], h[1], h[2], "entity.blaze.shoot", 10.0F, 0.6F);
            actionbar("volley", "gold");
        }
        volleyT++;
        if (volleyT % 2 == 0) {
            for (int side : new int[]{-1, 0, 1}) {
                double a = vYaw + side * (n - 1) / 2.0 * 0.14;
                for (double s = 2; s < 26; s += 2.0) {
                    double hxz = Math.cos(vPitch) * s;
                    Fx.send(level, RED, h[0] + Math.sin(a) * hxz, h[1] + Math.sin(vPitch) * s, h[2] + Math.cos(a) * hxz, 1, 0, 0, 0, 0);
                }
            }
        }
        if (volleyT >= warn) {
            boolean homing = life == 2;
            double sp = homing ? 0.7 : 0.9;
            for (int k = 0; k < n; k++) {
                double a = vYaw + (k - (n - 1) / 2.0) * 0.14;
                double c = Math.cos(vPitch);
                feathers.add(new double[]{h[0], h[1], h[2], Math.sin(a) * c * sp, Math.sin(vPitch) * sp, Math.cos(a) * c * sp, 80, homing ? 1 : 0});
            }
            Fx.sound(level, h[0], h[1], h[2], "item.firecharge.use", 10.0F, 0.7F);
            volleyT = -1;
            volleyCd = (int) ((life == 2 ? 85 : phase == 2 ? 100 : 120) * (0.8 + RNG.nextDouble() * 0.4));
        }
    }

    void tickFeathers(List<ServerPlayer> ps) {
        Iterator<double[]> it = feathers.iterator();
        while (it.hasNext()) {
            double[] f = it.next();
            if (f[7] > 0 && f[6] > 30 && !ps.isEmpty()) {      // самонаведение первые 50 тиков жизни
                ServerPlayer tg = null; double bd = 1e9;
                for (ServerPlayer p : ps) { double d = p.m_20275_(f[0], f[1], f[2]); if (d < bd) { bd = d; tg = p; } }
                if (tg != null) {
                    Vec3 v = tg.m_20182_(); double dx = v.f_82479_ - f[0], dy = v.f_82480_ + 1 - f[1], dz = v.f_82481_ - f[2], l = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz)), sp = 0.7;
                    f[3] += (dx / l * sp - f[3]) * 0.08; f[4] += (dy / l * sp - f[4]) * 0.08; f[5] += (dz / l * sp - f[5]) * 0.08;
                }
            }
            f[0] += f[3]; f[1] += f[4]; f[2] += f[5]; f[6]--;
            Fx.send(level, flame(), f[0], f[1], f[2], 2, 0.08, 0.08, 0.08, 0.01);
            if (f[6] % 2 == 0) Fx.send(level, dust(), f[0], f[1], f[2], 1, 0.05, 0.05, 0.05, 0);
            boolean hit = false;
            for (ServerPlayer p : ps) {
                Vec3 v = p.m_20182_();
                if (Math.abs(v.f_82479_ - f[0]) < 0.9 && Math.abs(v.f_82481_ - f[2]) < 0.9 && f[1] > v.f_82480_ - 0.3 && f[1] < v.f_82480_ + 2.1) { burn(p, life == 2 ? 5 : 6, 2); hit = true; break; }
            }
            double r = rad(f[0], f[2]);
            boolean out = f[1] < floor - 1 || r > R - 0.5 || f[1] > floor + vault(r) - 1;
            if (hit || out || f[6] <= 0) {
                Fx.send(level, Fx.particle("small_flame"), f[0], f[1], f[2], 10, 0.3, 0.3, 0.3, 0.05);
                Fx.sound(level, f[0], f[1], f[2], "block.fire.extinguish", 1.5F, 1.4F);
                it.remove();
            }
        }
    }

    // --- пике ---
    void startWarn(List<ServerPlayer> ps, boolean quick) {
        if (ps.isEmpty()) { mode = Mode.PERCH; modeT = 0; return; }
        mode = Mode.WARN; modeT = 0; warnLen = quick ? 16 : life == 2 ? 20 : 24;
        Vec3 v = ps.get(RNG.nextInt(ps.size())).m_20182_();
        double dx = v.f_82479_ - ax, dz = v.f_82481_ - az, d = Math.hypot(dx, dz), lim = R - 5;
        if (d > lim) { dx *= lim / d; dz *= lim / d; }
        tgtX = ax + dx; tgtZ = az + dz;
        if (!quick) { doubleLeft = life == 2 ? 1 : 0; Fx.sound(level, px, py, pz, "entity.ender_dragon.growl", 14.0F, 0.8F); actionbar("dive", "red"); }
        else Fx.sound(level, px, py, pz, "entity.blaze.shoot", 10.0F, 0.5F);
    }

    /** Точка конца пике по полу: от цели вдоль направления полёта, не дальше R - 3 от центра и не больше 12 блоков. */
    double skimLength(double fromX, double fromZ, double dirX, double dirZ) {
        double len = 0;
        while (len < 12 && rad(fromX + dirX * (len + 1), fromZ + dirZ * (len + 1)) <= R - 3) len++;
        return len;
    }

    void warnTick() {
        boolean quick = warnLen <= 16;
        flyTo(px, floor + (quick ? 7.5 : CRUISE_Y + 1.0), pz, quick ? 0.2 : 0.08);
        double sx = px, sz = pz, dx = tgtX - sx, dz = tgtZ - sz, l = Math.max(0.1, Math.hypot(dx, dz)), ux = dx / l, uz = dz / l;
        double sk = skimLength(tgtX, tgtZ, ux, uz);
        if (modeT % 2 == 0) {
            for (int i = 0; i < 14; i++) { double a = i * Math.PI / 7; Fx.send(level, dust(), tgtX + Math.cos(a) * 3.2, floor + 0.3, tgtZ + Math.sin(a) * 3.2, 1, 0, 0, 0, 0); }
            for (double s = 0; s < l + sk; s += 1.5) Fx.send(level, RED, sx + ux * s, floor + 0.3, sz + uz * s, 1, 0, 0, 0, 0);
        }
        if (modeT >= warnLen) startDive(ux, uz, sk);
    }

    void startDive(double ux, double uz, double sk) {
        double dx = tgtX - px, dy = floor + PERCH_Y - py, dz = tgtZ - pz, l = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
        diveSpd = life == 2 ? 2.0 : 1.6;
        dvx = dx / l; dvy = dy / l; dvz = dz / l; hx = ux; hz = uz; skimLeft = sk;
        mode = Mode.DIVE; modeT = 0;
        Fx.sound(level, px, py, pz, "entity.phantom.swoop", 12.0F, 0.6F);
    }

    void diveTick(List<ServerPlayer> ps) {
        double dx = tgtX - px, dy = floor + PERCH_Y - py, dz = tgtZ - pz, rem = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (rem <= diveSpd) { setPos(tgtX, floor + PERCH_Y, tgtZ); impact(ps); mode = Mode.SKIM; modeT = 0; return; }
        setPos(px + dvx * diveSpd, py + dvy * diveSpd, pz + dvz * diveSpd);
        Fx.send(level, flame(), px, py, pz, 6, 1.5, 1.0, 1.5, 0.05);
        bodyHit(ps);
        if (py < floor + 4) addTrail(px, pz);
    }

    void impact(List<ServerPlayer> ps) {
        Fx.send(level, Fx.particle("explosion_emitter"), px, floor + 0.6, pz, 1, 0, 0, 0, 0);
        Fx.send(level, flame(), px, floor + 0.6, pz, 80, 2.5, 0.3, 2.5, 0.2);
        Fx.send(level, Fx.blockParticle(Fx.state("magma_block")), px, floor + 0.5, pz, 40, 2.5, 0.3, 2.5, 0.2);
        Fx.sound(level, px, floor, pz, "entity.generic.explode", 14.0F, 0.6F);
        for (ServerPlayer p : ps) {
            Vec3 v = p.m_20182_(); double ddx = v.f_82479_ - px, ddz = v.f_82481_ - pz;
            if (ddx * ddx + ddz * ddz < 3.2 * 3.2 && v.f_82480_ < floor + 3) { burn(p, 10, 3); shove(p, px, pz, 1.0, 0.6); }
        }
    }

    void bodyHit(List<ServerPlayer> ps) {
        for (ServerPlayer p : ps) {
            Vec3 v = p.m_20182_(); double d2 = (v.f_82479_ - px) * (v.f_82479_ - px) + (v.f_82481_ - pz) * (v.f_82481_ - pz);
            if (d2 < 2.4 * 2.4 && v.f_82480_ > py - 3.5 && v.f_82480_ < py + 2.5 && t - hitCd.getOrDefault(p, -100) > 30) {
                hitCd.put(p, t); burn(p, 8, 2); shove(p, px, pz, 1.2, 0.5);
            }
        }
    }

    void skimTick(List<ServerPlayer> ps) {
        double step = Math.min(diveSpd, skimLeft);
        if (step > 0.01) setPos(px + hx * step, floor + PERCH_Y, pz + hz * step); else setPos(px, py, pz);
        skimLeft -= diveSpd;
        addTrail(px, pz);
        Fx.send(level, flame(), px, floor + 0.8, pz, 8, 1.2, 0.5, 1.2, 0.04);
        bodyHit(ps);
        if (skimLeft <= 0) {
            if (life == 2 && doubleLeft > 0) { doubleLeft--; startWarn(ps, true); return; }
            mode = Mode.PERCH; modeT = 0; orbDir = -orbDir;
            diveCd = (int) ((life == 2 ? 110 : phase == 2 ? 150 : 170) * (0.85 + RNG.nextDouble() * 0.3));
            volleyCd = Math.max(volleyCd, 70); doubleLeft = 0;
        }
    }

    void addTrail(double x, double z) {
        if (!trails.isEmpty()) { double[] l = trails.get(trails.size() - 1); if ((l[0] - x) * (l[0] - x) + (l[1] - z) * (l[1] - z) < 2.25) return; }
        trails.add(new double[]{x, z, 120});
    }

    void tickTrails(List<ServerPlayer> ps) {
        Iterator<double[]> it = trails.iterator();
        while (it.hasNext()) {
            double[] f = it.next(); f[2]--;
            if ((int) f[2] % 3 == 0) Fx.send(level, flame(), f[0], floor + 0.3, f[1], 3, 0.6, 0.15, 0.6, 0.01);
            if ((int) f[2] % 10 == 0) for (ServerPlayer p : ps) {
                Vec3 v = p.m_20182_(); double dx = v.f_82479_ - f[0], dz = v.f_82481_ - f[1];
                if (dx * dx + dz * dz < 1.7 * 1.7 && v.f_82480_ < floor + 2.5) burn(p, 3, 2);
            }
            if (f[2] <= 0) it.remove();
        }
    }

    // --- поддержка: жар, смерчи, Искры (фазы 2 и 4) ---
    void supportAttacks(List<ServerPlayer> ps) {
        if (phase < 2 || ps.isEmpty()) return;
        if (--heatCd <= 0) {
            int n = life == 2 ? 3 : 2;
            List<Integer> free = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7));
            for (int[] h : heat) free.remove((Integer) h[0]);
            for (int i = 0; i < n && !free.isEmpty(); i++) heat.add(new int[]{free.remove(RNG.nextInt(free.size())), 0});
            actionbar("heat", "gold");
            Fx.sound(level, ax, floor, az, "block.lava.ambient", 20.0F, 0.6F);
            heatCd = (int) ((life == 2 ? 190 : 230) * (0.85 + RNG.nextDouble() * 0.3));
        }
        if (--whirlCd <= 0) {
            if (whirls.size() < (life == 2 ? 4 : 3)) {
                double a = RNG.nextDouble() * Math.PI * 2, r = 8 + RNG.nextDouble() * 12;
                whirls.add(new double[]{ax + Math.cos(a) * r, az + Math.sin(a) * r, 0});
                actionbar("whirl", "gold");
                Fx.sound(level, ax + Math.cos(a) * r, floor, az + Math.sin(a) * r, "entity.blaze.ambient", 12.0F, 0.5F);
            }
            whirlCd = (int) ((life == 2 ? 220 : 270) * (0.85 + RNG.nextDouble() * 0.3));
        }
        if (--sparkCd <= 0) { summon(0, minionPlan(phase, ps.size())[0]); sparkCd = life == 2 ? 280 : 330; }
    }

    /** Сектор пола, раскалённый секторами: телеграф 1,8 с (края и дуги), потом 3,5 с жара. Урон тем, кто стоит на полу пещеры (ноги ниже floor - 0,5: метка floor - верх помоста, пол на блок ниже); прыжок и блок над полом от жара спасают. */
    static final int HEAT_WARN = 36, HEAT_LEN = 70;

    void tickHeat(List<ServerPlayer> ps) {
        Iterator<int[]> it = heat.iterator();
        while (it.hasNext()) {
            int[] h = it.next(); h[1]++;
            double a0 = h[0] * Math.PI / 4, a1 = a0 + Math.PI / 4;
            if (h[1] < HEAT_WARN) {
                if (h[1] % 2 == 0) {
                    for (double r = PLAT_SAFE; r <= R - 0.5; r += 2.5) for (double a : new double[]{a0, a1}) Fx.send(level, dust(), ax + Math.cos(a) * r, floor + 0.3, az + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    for (double a = a0; a <= a1; a += 0.09) { Fx.send(level, dust(), ax + Math.cos(a) * (R - 0.5), floor + 0.3, az + Math.sin(a) * (R - 0.5), 1, 0, 0, 0, 0); Fx.send(level, dust(), ax + Math.cos(a) * PLAT_SAFE, floor + 0.3, az + Math.sin(a) * PLAT_SAFE, 1, 0, 0, 0, 0); }
                }
                if (h[1] % 3 == 0) for (int i = 0; i < 6; i++) { double a = a0 + RNG.nextDouble() * (a1 - a0), r = PLAT_SAFE + RNG.nextDouble() * (R - 1 - PLAT_SAFE); Fx.send(level, Fx.particle("lava"), ax + Math.cos(a) * r, floor + 0.3, az + Math.sin(a) * r, 1, 0, 0, 0, 0); }
                continue;
            }
            if (h[1] % 2 == 0) for (int i = 0; i < 14; i++) { double a = a0 + RNG.nextDouble() * (a1 - a0), r = PLAT_SAFE + RNG.nextDouble() * (R - 1 - PLAT_SAFE); Fx.send(level, flame(), ax + Math.cos(a) * r, floor + 0.2 + RNG.nextDouble() * 0.5, az + Math.sin(a) * r, 1, 0.1, 0.2, 0.1, 0.02); }
            if (h[1] % 12 == 0) Fx.sound(level, ax + Math.cos(a0 + 0.4) * 15, floor, az + Math.sin(a0 + 0.4) * 15, "block.fire.ambient", 8.0F, 0.8F);
            if (h[1] % 10 == 0) for (ServerPlayer p : ps) {
                Vec3 v = p.m_20182_(); double dx = v.f_82479_ - ax, dz = v.f_82481_ - az, r = Math.hypot(dx, dz);
                if (r >= PLAT_SAFE && r <= R && sectorOf(dx, dz) == h[0] && v.f_82480_ < floor - 0.5) burn(p, 3, 2);
            }
            if (h[1] >= HEAT_WARN + HEAT_LEN) it.remove();
        }
    }

    /** Огненные смерчи: круг на полу 1,25 с, потом идут к ближайшему игроку 12 с; касание бьёт и подбрасывает. */
    void tickWhirls(List<ServerPlayer> ps) {
        Iterator<double[]> it = whirls.iterator();
        while (it.hasNext()) {
            double[] w = it.next(); w[2]++;
            if (w[2] < 25) {
                if ((int) w[2] % 2 == 0) for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; Fx.send(level, dust(), w[0] + Math.cos(a) * 2.0, floor + 0.3, w[1] + Math.sin(a) * 2.0, 1, 0, 0, 0, 0); }
                continue;
            }
            ServerPlayer n = null; double bd = 1e9;
            for (ServerPlayer p : ps) { double d = (p.m_20185_() - w[0]) * (p.m_20185_() - w[0]) + (p.m_20189_() - w[1]) * (p.m_20189_() - w[1]); if (d < bd) { bd = d; n = p; } }
            if (n != null) {
                double dx = n.m_20185_() - w[0], dz = n.m_20189_() - w[1], l = Math.max(0.1, Math.hypot(dx, dz)), sp = life == 2 ? 0.15 : 0.11;
                double nx = w[0] + dx / l * sp, nz = w[1] + dz / l * sp;
                if (rad(nx, nz) < R - 3) { w[0] = nx; w[1] = nz; }
            }
            for (int k = 0; k < 7; k++) {
                double a = w[2] * 0.5 + k * 0.9, r = 0.5 + k * 0.28, y = floor + 0.2 + k * 0.95;
                Fx.send(level, flame(), w[0] + Math.cos(a) * r, y, w[1] + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.02);
            }
            if ((int) w[2] % 6 == 0) Fx.send(level, Fx.particle("large_smoke"), w[0], floor + 6.5, w[1], 2, 1.0, 0.3, 1.0, 0.02);
            if ((int) w[2] % 20 == 0) Fx.sound(level, w[0], floor + 2, w[1], "block.fire.ambient", 6.0F, 0.6F);
            if ((int) w[2] % 8 == 0) for (ServerPlayer p : ps) {
                Vec3 v = p.m_20182_(); double dx = v.f_82479_ - w[0], dz = v.f_82481_ - w[1];
                if (dx * dx + dz * dz < 1.9 * 1.9 && v.f_82480_ > floor - 2 && v.f_82480_ < floor + 7) { burn(p, 5, 2); p.m_20256_(new Vec3(dx * 0.15, 0.9, dz * 0.15)); p.f_19864_ = true; }
            }
            if (w[2] > 265) it.remove();
        }
    }

    // --- приспешники ---
    int aliveMinions() {
        int n = 0;
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_6084_() && en.m_19880_().contains(MIN) && en.m_19880_().contains(ktag))) n++;
        return n;
    }

    /** Телеграф 1 с (кольцо на месте появления), затем настоящие Искры и Пеплоходы (Ash.minion). kind 0 - Искры (в воздухе), 1 - Пеплоходы (на полу у помоста). */
    void summon(int kind, int count) {
        int have = aliveMinions() + spawns.size();
        for (int i = 0; i < count && have < MAX_MIN; i++, have++) {
            double a = kind == 0 ? (RNG.nextDouble() * 2 - 1) * SPARK_ARC : RNG.nextDouble() * Math.PI * 2, r = kind == 0 ? 9 + RNG.nextDouble() * 6 : WALKER_R;
            spawns.add(new double[]{ax + Math.cos(a) * r, kind == 0 ? floor + SPARK_Y : floor - 1, az + Math.sin(a) * r, 0, kind});
        }
        if (kind == 0) actionbar("sparks", "gold");
        Fx.sound(level, ax, floor + 3, az, "entity.evoker.prepare_summon", 20.0F, 0.6F);
    }

    void tickSpawns() {
        Iterator<double[]> it = spawns.iterator();
        while (it.hasNext()) {
            double[] s = it.next(); s[3]++;
            if ((int) s[3] % 3 == 0) for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; Fx.send(level, dust(), s[0] + Math.cos(a) * 1.2, s[1] + 0.2, s[2] + Math.sin(a) * 1.2, 1, 0, 0, 0, 0); }
            if (s[3] >= 20) {
                Ash.minion(level, (int) s[4], s[0], s[1], s[2], hallTag, ktag, MIN);
                it.remove();
            }
        }
    }

    /** Приспешники уходят вместе с боссом. */
    void minionsOff() {
        spawns.clear();
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_19880_().contains(MIN) && en.m_19880_().contains(ktag))) {
            Fx.send(level, Fx.particle("poof"), e.m_20185_(), e.m_20186_() + 0.4, e.m_20189_(), 6, 0.2, 0.2, 0.2, 0.02);
            RiftMobs.remove(e);
        }
    }

    void clearAttacks() {
        feathers.clear(); trails.clear(); heat.clear(); whirls.clear(); volleyT = -1; doubleLeft = 0;
    }

    // ================= Фаза 2: Жар =================
    void startPhase2() {
        phase = 2; mode = Mode.RAGE; modeT = 0;
        clearAttacks();
        title("phase2", "gold", "phase2.sub", 70);
        barStyle("name", BAR_COLOR[1]);
        Fx.send(level, Fx.particle("flash"), px, py, pz, 2, 1, 1, 1, 0);
        Fx.send(level, Fx.particle("explosion_emitter"), px, py, pz, 2, 1, 1, 1, 0);
        Fx.sound(level, px, py, pz, "entity.ender_dragon.growl", 30.0F, 0.6F);
        Fx.sound(level, px, py, pz, "entity.generic.explode", 20.0F, 0.7F);
        heatCd = 90; whirlCd = 170; sparkCd = 60; diveCd = 160; volleyCd = 100;
    }

    /** Ярость при смене фазы (2,5 с): Феникс взмывает в центр свода и вспыхивает; неуязвим, не атакует. */
    void rageTick() {
        flyTo(ax, floor + CRUISE_Y + 2, az, 0.1);
        if (modeT % 2 == 0) Fx.send(level, flame(), px, py, pz, 10, 3.0, 1.5, 3.0, 0.05);
        if (modeT % 10 == 0) for (int i = 0; i < 24; i++) { double a = i * Math.PI / 12 + modeT * 0.05, r = 2 + modeT * 0.35; Fx.send(level, dust(), ax + Math.cos(a) * r, floor + 0.3, az + Math.sin(a) * r, 1, 0, 0, 0, 0); }
        if (modeT % 8 == 0) Fx.sound(level, px, py, pz, "entity.ender_dragon.flap", 12.0F, 0.9F);
        if (modeT >= 50) { mode = Mode.CIRCLE; modeT = 0; orbA = Math.atan2(pz - az, px - ax); }
    }

    // ================= Фаза 3: Пепел (падение, яйцо) =================
    void startFall() {
        st = St.FALL; stT = 0; phase = 3; mode = Mode.CIRCLE;
        clearAttacks(); minionsOff();
        title("phase3", "white", "phase3.sub", 80);
        barStyle("egg", BAR_COLOR[2]);
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " style notched_6");
        Fx.sound(level, px, py, pz, "entity.blaze.death", 30.0F, 0.5F);
        Fx.sound(level, px, py, pz, "entity.ender_dragon.death", 20.0F, 1.4F);
        fallFromX = px; fallFromY = py; fallFromZ = pz;
    }
    double fallFromX, fallFromY, fallFromZ;

    void fallTick() {
        int n = stT;
        double k = Math.min(1, n / 40.0);
        fold += (1 - fold) * 0.3;
        flapPh += 0.15;
        double kk = k * k;
        setPos(fallFromX + (ax - fallFromX) * kk, fallFromY + (floor + EGG_Y - fallFromY) * kk, fallFromZ + (az - fallFromZ) * kk);
        gs = n < 28 ? 1 : Math.max(0.0, 1 - (n - 28) / 12.0);
        eg = n < 28 ? 0 : Math.min(1, (n - 28) / 12.0);
        if (n % 2 == 0) { Fx.send(level, flame(), px, py, pz, 14, 1.8, 1.0, 1.8, 0.05); Fx.send(level, Fx.particle("ash"), px, py, pz, 12, 1.8, 1.0, 1.8, 0.02); }
        if (n >= 40) {
            gs = 0; eg = 1;
            Fx.send(level, Fx.particle("flash"), ax, floor + 3, az, 2, 1, 1, 1, 0);
            Fx.send(level, Fx.blockParticle(Fx.state("gray_concrete")), ax, floor + 3, az, 150, 1.8, 2.5, 1.8, 0.2);
            Fx.send(level, Fx.particle("ash"), ax, floor + 3, az, 100, 2.5, 3, 2.5, 0.05);
            Fx.sound(level, ax, floor + 2, az, "block.respawn_anchor.deplete", 30.0F, 0.6F);
            st = St.EGG; stT = 0; lethal = false; eggBroken = false; fold = 0; roll = 0; pitch = 0;
            px = ax; py = floor + EGG_Y; pz = az; vx = vy = vz = 0;
            host.m_21153_(EGG_HP * DMG_K);
            summon(1, minionPlan(3, players().size())[1]);
            actionbar("egg.hint", "white");
        }
    }

    void eggTick(List<ServerPlayer> ps) {
        gs = 0; eg = 1;
        int left = EGG_TICKS - stT;
        if (lethal || host.m_21223_() <= 1.01F) { eggShattered(); return; }
        if (stT >= EGG_TICKS) { eggHatches(ps); return; }
        if (stT == 150) summon(1, Math.max(0, minionPlan(3, ps.size())[1] - 2));   // подмога: Пеплоходы вдогонку
        if (stT % 20 == 0) {
            Cmd.run(level, ax, floor, az, "title " + sel() + " actionbar {\"translate\":\"" + KEY + "egg.timer\",\"with\":[\"" + (left / 20) + "\"],\"color\":\"" + (left < 100 ? "red" : "white") + "\"}");
            Fx.sound(level, ax, floor + 3, az, "entity.warden.heartbeat", 18.0F, 0.5F + stT / (float) EGG_TICKS * 0.9F);
        }
        int every = left < 100 ? 3 : 6;
        if (stT % every == 0) Fx.send(level, Fx.particle("ash"), ax, floor + 3, az, 6, 1.5, 2.8, 1.5, 0.02);
        if (stT % (every * 2) == 0) Fx.send(level, flame(), ax + (RNG.nextDouble() - 0.5) * 3, floor + 1 + RNG.nextDouble() * 5, az + (RNG.nextDouble() - 0.5) * 3, 2, 0.1, 0.1, 0.1, 0.02);
        if (left < 100 && stT % 10 == 0) for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8; double r = 7 * (1 - (left % 40) / 40.0) + 1.5; Fx.send(level, dust(), ax + Math.cos(a) * r, floor + 0.3, az + Math.sin(a) * r, 1, 0, 0, 0, 0); }
    }

    /** Яйцо разбито до таймера: Феникс возродится ослабленным. */
    void eggShattered() {
        eggBroken = true;
        Fx.send(level, Fx.particle("explosion_emitter"), ax, floor + 3, az, 2, 1, 1, 1, 0);
        Fx.send(level, Fx.blockParticle(Fx.state("gray_concrete")), ax, floor + 3, az, 200, 2.0, 3.0, 2.0, 0.3);
        Fx.sound(level, ax, floor + 3, az, "block.glass.break", 30.0F, 0.5F);
        Fx.sound(level, ax, floor + 3, az, "entity.generic.explode", 20.0F, 0.9F);
        actionbar("egg.broken", "green");
        startReborn(true);
    }

    /** Время вышло: вспышка (урон вблизи) и Феникс возрождается полным. */
    void eggHatches(List<ServerPlayer> ps) {
        Fx.send(level, Fx.particle("explosion_emitter"), ax, floor + 3, az, 3, 1, 1, 1, 0);
        Fx.send(level, Fx.particle("flash"), ax, floor + 3, az, 3, 1, 1, 1, 0);
        Fx.sound(level, ax, floor + 3, az, "entity.generic.explode", 30.0F, 0.6F);
        for (ServerPlayer p : ps) {
            Vec3 v = p.m_20182_(); double dx = v.f_82479_ - ax, dz = v.f_82481_ - az;
            if (dx * dx + dz * dz < 8 * 8) { burn(p, 8, 3); shove(p, ax, az, 1.4, 0.7); }
        }
        actionbar("egg.hatch", "red");
        startReborn(false);
    }

    void startReborn(boolean weak) {
        st = St.REBORN; stT = 0; life = 2; phase = 4; lethal = false; mode = Mode.CIRCLE;
        clearAttacks(); minionsOff();
        rebornMax = weak ? REBORN_WEAK : REBORN_FULL;
        host.m_21153_(rebornMax * DMG_K);
        applyPalette(1);
        title("phase4", "aqua", weak ? "phase4.sub.weak" : "phase4.sub.full", 90);
        barStyle("name", BAR_COLOR[3]);
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " style notched_10");
        Fx.sound(level, ax, floor + 3, az, "entity.ender_dragon.growl", 30.0F, 1.2F);
        Fx.sound(level, ax, floor + 3, az, "block.beacon.activate", 30.0F, 1.5F);
        diveCd = 140; volleyCd = 80; heatCd = 160; whirlCd = 150; sparkCd = 60; fold = 0; flapPh = 0;
    }

    // ================= Гибель =================
    void startDeath() {
        st = St.DYING; stT = 0;
        won = true;     // зал засчитывает tickAll сразу (lairWon: сундук, печать, барьер Святилища); эффект гибели доигрывается 5,5 с
        clearAttacks(); mode = Mode.PERCH;
        Cmd.run(level, ax, floor, az, "bossbar remove " + bar);
        minionsOff();
        Fx.sound(level, px, py, pz, "entity.ender_dragon.death", 30.0F, 0.8F);
        Fx.sound(level, px, py, pz, "entity.blaze.death", 30.0F, 0.5F);
        fallFromX = px; fallFromY = py; fallFromZ = pz;
    }

    boolean dying() {
        int n = stT;
        double k = Math.min(1, n / 40.0);
        fold += (0.6 - fold) * 0.2; flapPh += 0.18;
        setPos(fallFromX + (ax - fallFromX) * k, fallFromY + (floor + PERCH_Y - fallFromY) * ease(k), fallFromZ + (az - fallFromZ) * k);
        gs = n < 70 ? 1 : Math.max(0.0, 1 - (n - 70) / 40.0);
        if (n % 3 == 0) { Fx.send(level, flame(), px, py, pz, 18, 3.0 * gs, 1.5 * gs, 3.0 * gs, 0.08); Fx.send(level, Fx.particle("ash"), px, py + 1, pz, 12, 3.0 * gs, 1.5 * gs, 3.0 * gs, 0.03); }
        if (n % 6 == 0) Fx.sound(level, px, py, pz, "block.fire.extinguish", 10.0F, 0.6F + n / 150F);
        if (n >= 110) {
            purge();   // хост и части уходят; победу уже засчитал tickAll (hallsCleared, сундук, барьер)
            double c = floor + 2;
            Fx.send(level, Fx.particle("flash"), px, c, pz, 5, 2, 2, 2, 0);
            Fx.send(level, Fx.particle("explosion_emitter"), px, c, pz, 4, 2, 2, 2, 0);
            for (int i = 0; i < 240; i++) { double[] u = Fx.unit(); Fx.send(level, i % 2 == 0 ? WHITE : BLUE, px, c, pz, 0, u[0], u[1], u[2], 1.6); }
            Fx.send(level, Fx.particle("ash"), px, c, pz, 200, 4, 3, 4, 0.05);
            Fx.sound(level, px, c, pz, "entity.generic.explode", 30.0F, 0.6F);
            Fx.sound(level, px, c, pz, "ui.toast.challenge_complete", 20.0F, 1.0F);
            String at = Cmd.f(ax) + " " + (floor + 1) + " " + Cmd.f(az + 3);
            for (int i = 0; i < 12; i++) Cmd.run(level, ax, floor, az, "summon experience_orb " + at + " {Value:400}");
            title("dead", "aqua", "dead.sub", 100);
            return true;
        }
        return false;
    }

    // ================= Урон =================
    static boolean environmental(LivingHurtEvent ev) {
        String id = ev.getSource() == null ? "" : ev.getSource().m_19385_();
        return Boss3.environmental(ev) || (id != null && (id.equals("lava") || id.equals("hotFloor") || id.equals("freeze") || id.equals("cactus") || id.equals("sweetBerryBush")));
    }

    boolean vulnerable() { return (st == St.FIGHT && mode != Mode.RAGE) || st == St.EGG; }

    /**
     * Урон, обрезанный до порога: в фазе 1 - до 55% ХП (один удар не перескакивает Жар); в остальных - до 1 ХП, и смертельный удар оставляет 1 ХП и
     * ставит lethal (следующая стадия: падение в яйцо, разбитое яйцо, гибель).
     */
    float clampHit(float hp, float amt) {
        float floorHp = st == St.FIGHT && life == 1 && phase == 1 ? HOST_HP * P2_AT : 1.0F;
        if (hp - amt < floorHp) amt = hp > floorHp ? hp - floorHp : (floorHp > 1.0F ? 0.5F : 0.0F);
        if (floorHp <= 1.0F && hp - amt <= 1.0F) { amt = Math.max(0, hp - 1.0F); lethal = true; }
        return amt;
    }

    static void onHurt(LivingHurtEvent ev) {
        if (BOSSES.isEmpty()) return;
        LivingEntity target = ev.getEntity();
        Boss5 b = null;
        for (Boss5 x : BOSSES.values()) if (x.host != null && x.host == target) { b = x; break; }
        // приспешники не бьют друг друга и босса
        if (b == null) {
            // окно между summon хоста и его поиском (host == null): удар прошёл бы без множителя и обрезки
            if (target.m_19880_().contains(TAG + "_host")) {
                for (Boss5 x : BOSSES.values()) if (x.host == null) { ev.setCanceled(true); return; }
            }
            if (Cmd.hasTagPrefix(target, MIN) && ev.getSource() != null && ev.getSource().m_7639_() != null && Cmd.hasTagPrefix(ev.getSource().m_7639_(), TAG)) ev.setCanceled(true);
            return;
        }
        if (environmental(ev) || !b.vulnerable()) {
            ev.setCanceled(true);
            if (!environmental(ev)) {
                Fx.send(b.level, b.blue() ? BLUE : GOLD, b.px, b.py, b.pz, 12, 1.5, 1.5, 1.5, 0);
                Fx.sound(b.level, b.px, b.py, b.pz, "block.fire.extinguish", 3.0F, 1.6F);
            }
            return;
        }
        float k = b.st == St.FIGHT && b.mode == Mode.PERCH ? 1.5F : 1.0F;      // выдохшийся на земле Феникс уязвимее
        ev.setAmount(b.clampHit(b.host.m_21223_(), hostDamage(ev.getAmount()) * k));
        Fx.send(b.level, b.st == St.EGG ? Fx.blockParticle(Fx.state("gray_concrete")) : b.flame(), b.px, b.py, b.pz, 12, 1.1, 1.0, 1.1, 0.1);
        Fx.sound(b.level, b.px, b.py, b.pz, b.st == St.EGG ? "block.stone.hit" : "entity.blaze.hurt", 2.0F, 0.6F + RNG.nextFloat() * 0.3F);
    }
}
