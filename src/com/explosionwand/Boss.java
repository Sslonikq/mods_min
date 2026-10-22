package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * «Сердце Разлома» — живой кристалл, парящий над логовом Разлома (зал 7 данжа, фаза 4.4). Собран из 25 блоков-голограмм:
 * ядро из плачущего обсидиана в стеклянной оболочке, глаз со зрачком, кольца аметистов, корона и цепи.
 *
 * Где бой: зал 7 — круглый зал R=28 над бездной. Центр — круглая площадка (R&lt;9,5, пол на уровне метки boss), вокруг неё бездна глубиной 5
 * (R 9,5..20,5), дальше кольцо (R 20,5..28,5) на уровне площадки; с севера через бездну идёт мост. Босс просыпается, когда игрок входит в зал
 * (Dungeons.spawnHall -> wake), дверь seal за спиной закрывается. Тик — Boss.tickAll из Dungeons.tick; зал зачищен, когда босс убит и убран
 * (хост носит тег зала), поэтому сундук с добычей ставит обычная зачистка зала (Dungeons.onCleared + Boss.LOOT).
 *
 * Фазы:
 *  1 Осколки и шипы: залп осколков (телеграф 16 тиков линиями прицела), шипы под игроками (кружок 30 тиков).
 *  2 Щит (60% ХП): 4 кристалла на столбах по краю кольца, луч-вертушка (25 тиков предупреждения), осколочники-приспешники.
 *  3 Ярость (30% ХП): быстрее, два луча, удар о землю с волной.
 *
 * Хост — гигант на 900 ХП (лимит мобов 1024), невидимый; облик едет на нём пассажирами. Теги хоста: ewb, ewb_host, ewbk&lt;ключ&gt;, ewd, тег зала.
 * Части облика: ewb, ewb_p&lt;i&gt;, ewbk&lt;ключ&gt;. Приспешники: ewb_min, ewbk&lt;ключ&gt; (+ теги мобов Разлома и зала).
 * Блоки босс меняет только на 4 столбах (пустые клетки шаблона, проверяет DungeonTest); взрывы и ломание в данже запрещены, кроме ядер столбов.
 */
public class Boss {
    static final Map<String, Boss> BOSSES = new HashMap<>();
    /** Записи, у которых полоска босса уже снята после загрузки мира (пользовательские bossbar переживают перезапуск). */
    static final Set<String> SWEPT = new HashSet<>();
    static final Random RNG = Fx.RNG;
    static final int N = 25;
    static final float MAX_HP = 900;
    static final String KEY = "explosionwand.boss.rift.";
    static final String BAR = "explosionwand:rift_heart_";
    /** Трофеи босса в сундуке логова (затем идёт временный набор тира 2). */
    static final List<String> LOOT = List.of("explosionwand:rift_staff", "explosionwand:rift_core", "explosionwand:sleep_seal_shard");
    /** Геометрия зала 7 (tools/gen_dungeons.py: RR=28, in_pit 9.5..20.5, глубина бездны 5, потолок S+14). */
    static final double PLAT_R = 9.5, PIT_R = 20.5, HALL_R = 28.5, PYLON_R = 23.5;
    static final int PIT_DEPTH = 5, CEIL = 13;
    static final String[] BLOCKS = new String[N];
    static {
        BLOCKS[0] = "crying_obsidian"; BLOCKS[1] = "purple_stained_glass"; BLOCKS[2] = "sea_lantern"; BLOCKS[3] = "black_concrete";
        for (int i = 4; i < 12; i++) BLOCKS[i] = "amethyst_block";
        for (int i = 12; i < 18; i++) BLOCKS[i] = "magenta_stained_glass";
        for (int i = 18; i < 22; i++) BLOCKS[i] = "amethyst_cluster";
        for (int i = 22; i < 25; i++) BLOCKS[i] = "chain";
    }
    static ParticleOptions PURPLE, PINK, RED, WHITE, DARK, AMETHYST;

    final ServerLevel level;
    final DungeonData.Rec rec;
    final Dungeons.Hall hall;
    final String hallTag, key, ktag, bar;
    /** Центр площадки (мир) и уровень пола на площадке/кольце. */
    final double ax, az;
    final int floor, cxi, czi;
    LivingEntity host;
    final String[] uuid = new String[N];
    int t = 0, stage = -1;            // -1 пробуждение, 0 поиск хоста, 1 появление, 2 бой, 3 гибель
    double bx, by, bz, orbit = 0;
    int phase = 1, stun = 0, dyingT = 0;
    boolean shield = false, shieldUsed = false, enraged = false, lethal = false;
    int volleyCd = 80, spikeCd = 140, laserCd = 160, slamCd = 120, minionCd = 120;
    int volleyT = -1, laserT = -1, slamT = -1; double laserStart = 0, volleyA = 0;
    final List<ServerPlayer> volleyTargets = new ArrayList<>();
    float yaw = 0; double ex = 0, ez = 1;
    final List<double[]> shards = new ArrayList<>();      // x,y,z,vx,vy,vz,life
    final List<double[]> spikes = new ArrayList<>();      // x,z,timer,пол
    final List<double[]> waves = new ArrayList<>();       // r
    final Map<ServerPlayer, Integer> hitCd = new WeakHashMap<>();
    /** Победа: хост погиб и убран; tickAll засчитает зал (Dungeons.lairWon) и поставит сундук. Сама отметка зачистки хранится в Rec.hallsCleared. */
    boolean won = false;
    final int[][] pylons;
    final boolean[] pylonAlive = new boolean[4];

    static void init() {
        if (PURPLE != null) return;
        PURPLE = new DustParticleOptions(new Vector3f(0.6F, 0.2F, 1.0F), 1.6F);
        PINK = new DustParticleOptions(new Vector3f(1.0F, 0.4F, 0.9F), 1.4F);
        RED = new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.15F), 1.8F);
        WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 1.2F);
        DARK = new DustParticleOptions(new Vector3f(0.15F, 0.0F, 0.25F), 2.5F);
        AMETHYST = Fx.blockParticle(Fx.state("amethyst_block"));
    }

    /** Столбы щита: на кольце, по углам 30, 120, 210, 300 градусов (между колоннами зала, подальше от моста). Чистая функция для теста. */
    static int[][] pylonSpots(int cx, int cz) {
        int[][] out = new int[4][];
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(30 + 90 * k);
            out[k] = new int[]{cx + (int) Math.round(Math.cos(a) * PYLON_R), cz + (int) Math.round(Math.sin(a) * PYLON_R)};
        }
        return out;
    }

    /** Пол под точкой относительно уровня площадки: в бездне на PIT_DEPTH ниже (чистая функция для теста). */
    static double groundOffset(double dx, double dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        return r >= PLAT_R && r < PIT_R ? -PIT_DEPTH : 0;
    }

    Boss(ServerLevel level, DungeonData.Rec rec, Dungeons.Hall hall, String hallTag) {
        this.level = level; this.rec = rec; this.hall = hall; this.hallTag = hallTag;
        key = Integer.toHexString(rec.key.hashCode()); ktag = "ewbk" + key; bar = BAR + key;
        cxi = rec.x + hall.boss.x; czi = rec.z + hall.boss.z;
        ax = cxi + 0.5; az = czi + 0.5; floor = rec.y + hall.boss.y;
        bx = ax; by = floor + 6; bz = az;
        pylons = pylonSpots(cxi, czi);
    }

    // ================= Пробуждение и сброс =================
    /** Зал разбужен: босс начинает пробуждаться (само появление через 3 секунды в tick). */
    static void wake(ServerLevel sl, DungeonData.Rec rec, Dungeons.Hall h, String hallTag) {
        if (isMonolith(h)) { Boss3.wake(sl, rec, h, hallTag); return; }
        if (isPhoenix(h)) { Boss5.wake(sl, rec, h, hallTag); return; }
        init();
        if (BOSSES.containsKey(rec.key)) return;
        BOSSES.put(rec.key, new Boss(sl, rec, h, hallTag));
    }

    /** Сброс боя (зал опустел, мирный режим, смена раскладки): убрать босса, части, приспешников, столбы и полоску. */
    static void reset(ServerLevel sl, DungeonData.Rec rec, Dungeons.Hall h) {
        if (isMonolith(h)) { Boss3.reset(sl, rec, h); return; }
        if (isPhoenix(h)) { Boss5.reset(sl, rec, h); return; }
        init();
        Boss b = BOSSES.remove(rec.key);
        if (b == null) b = new Boss(sl, rec, h, Dungeons.tagOf(rec, h.id));
        b.cleanup();
    }

    static void clear() { BOSSES.clear(); SWEPT.clear(); Boss3.clear(); Boss5.clear(); }

    /** Метка boss зала — Монолит Сна (Город Спящих, 4.7), а не Сердце Разлома: общий жизненный цикл (wake/reset/tickAll/orphans) делит эти два босса. */
    static boolean isMonolith(Dungeons.Hall h) { return h.boss != null && "monolith".equals(h.boss.a); }

    /** Метка boss зала - Пепельный Феникс (Пепельное святилище, 4.10, Boss5). */
    static boolean isPhoenix(Dungeons.Hall h) { return h.boss != null && "phoenix".equals(h.boss.a); }

    /** Трофеи босса зала в сундуке (перед набором тира). */
    static List<String> lootOf(Dungeons.Hall h) { return isMonolith(h) ? Boss3.LOOT : isPhoenix(h) ? Boss5.LOOT : LOOT; }

    /** Имя полоски босса зала (для снятия после перезапуска мира). */
    static String barFor(DungeonData.Rec rec, Dungeons.Hall h) { return isMonolith(h) ? Boss3.barOf(rec) : isPhoenix(h) ? Boss5.barOf(rec) : barOf(rec); }

    /** Убрать всё, что принадлежит этому бою: хост и части (discard), приспешников (вместе с частями облика), столбы, полоску. */
    void cleanup() {
        purge();
        clearPylons();
        Cmd.run(level, ax, floor, az, "bossbar remove " + bar);
    }

    void purge() {
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_19880_().contains(ktag))) {
            if (e.m_19880_().contains("ewb_min")) RiftMobs.remove(e);
            else e.m_146870_();
        }
    }

    // ================= Тики всех боссов =================
    static void tickAll(ServerLevel sl, DungeonData data, List<ServerPlayer> ps, long tk) {
        Boss3.tickAll(sl, data);
        Boss5.tickAll(sl, data);
        if (!BOSSES.isEmpty()) {
            Iterator<Boss> it = BOSSES.values().iterator();
            while (it.hasNext()) {
                Boss b = it.next();
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
        if (tk % 20 == 0) orphans(sl, data, ps);
    }

    /**
     * Зал отмечен «разбужен», а управляющего боя нет (перезапуск мира, сбой, смена раскладки): остатки убираем, отметку «разбужен» снимаем —
     * Dungeons разбудит зал заново, когда игрок окажется внутри (бой начнётся с полным ХП). Остатки без отметки тоже убираем.
     */
    static void orphans(ServerLevel sl, DungeonData data, List<ServerPlayer> ps) {
        for (DungeonData.Rec rec : data.recs.values()) {
            Dungeons.Def d = rec.def();
            if (d == null || rec.partsDone != d.allParts() || BOSSES.containsKey(rec.key) || Boss3.BOSSES.containsKey(rec.key) || Boss5.BOSSES.containsKey(rec.key)) continue;
            for (Dungeons.Hall h : d.halls()) {
                if (h.boss == null) continue;
                long bit = 1L << h.id;
                boolean spawned = (rec.hallsSpawned & bit) != 0, cleared = (rec.hallsCleared & bit) != 0;
                if (SWEPT.add(rec.key)) Cmd.run(sl, rec.x, rec.y, rec.z, "bossbar remove " + barFor(rec, h));   // полоска после перезапуска
                if (!Dungeons.hallOccupied(rec, h, ps, 60) || !Dungeons.entitiesLoaded(sl, rec, h)) continue;
                String tag = Dungeons.tagOf(rec, h.id);
                boolean junk = !sl.m_6249_(null, Dungeons.hallBox(rec, h), en -> en.m_19880_().stream().anyMatch(s -> s.startsWith("ewbk"))).isEmpty();
                if (junk || (spawned && !cleared)) reset(sl, rec, h);   // и столбы без сущностей (nit 8)
                if (spawned && !cleared) {
                    rec.hallsSpawned &= ~bit; Dungeons.SPAWNED_AT.remove(tag); Dungeons.LAST_HALL.remove(tag); data.m_77762_();
                }
            }
        }
    }

    static String barOf(DungeonData.Rec rec) { return BAR + Integer.toHexString(rec.key.hashCode()); }

    // ================= Помощники =================
    /** Живые игроки в логове: в круге зала и по высоте от дна бездны до потолка. */
    List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) {
            if (!p.m_6084_() || p.m_5833_()) continue;
            double dx = p.m_20185_() - ax, dz = p.m_20189_() - az, y = p.m_20186_();
            if (dx * dx + dz * dz <= (HALL_R + 1.5) * (HALL_R + 1.5) && y >= floor - PIT_DEPTH - 3 && y <= floor + CEIL + 2) out.add(p);
        }
        return out;
    }

    String sel() { return "@a[x=" + Cmd.f(ax) + ",y=" + Cmd.f(floor) + ",z=" + Cmd.f(az) + ",distance=..45]"; }

    double cy() { return by + 6; }   // центр тела

    /** Уровень пола под точкой (мир). */
    double gy(double x, double z) { return floor + groundOffset(x - ax, z - az); }

    boolean vulnerable() { return stage == 2 && !shield; }

    /** Заголовок на экране: title и sub — суффиксы ключей explosionwand.boss.rift.* (lang ru/en). */
    void title(String title, String color, String sub) {
        Cmd.run(level, ax, floor, az, "title " + sel() + " times 10 60 20");
        Cmd.run(level, ax, floor, az, "title " + sel() + " subtitle {\"translate\":\"" + KEY + sub + "\",\"color\":\"gray\",\"italic\":true}");
        Cmd.run(level, ax, floor, az, "title " + sel() + " title {\"translate\":\"" + KEY + title + "\",\"color\":\"" + color + "\",\"bold\":true}");
    }

    // ================= Появление хоста =================
    void spawnHost() {
        StringBuilder sb = new StringBuilder();
        sb.append("summon minecraft:giant ").append(Cmd.f(ax)).append(" ").append(floor + 6).append(" ").append(Cmd.f(az))
          .append(" {Tags:[\"ewb\",\"ewb_host\",\"").append(ktag).append("\",\"ewd\",\"").append(hallTag).append("\"],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,CanPickUpLoot:0b,DeathLootTable:\"minecraft:empty\",")
          .append("Attributes:[{Name:\"minecraft:generic.max_health\",Base:").append((int) MAX_HP).append("d},{Name:\"minecraft:generic.knockback_resistance\",Base:1d}],")
          .append("Health:").append((int) MAX_HP).append("f,ActiveEffects:[{Id:14,Amplifier:0b,Duration:-1,ShowParticles:0b}],Passengers:[");
        for (int i = 0; i < N; i++) {
            if (i > 0) sb.append(",");
            sb.append("{id:\"minecraft:block_display\",Tags:[\"ewb\",\"ewb_p").append(i).append("\",\"").append(ktag).append("\"],")
              .append(Disp.block(BLOCKS[i])).append(",view_range:4f,brightness:{sky:15,block:15},")
              .append(Disp.tf(0, -3, 0, 0.01, 0.01, 0.01, Disp.ID)).append("}");
        }
        sb.append("]}");
        Cmd.run(level, ax, floor, az, sb.toString());
        Cmd.run(level, ax, floor, az, "bossbar remove " + bar);
        Cmd.run(level, ax, floor, az, "bossbar add " + bar + " {\"translate\":\"" + KEY + "name\",\"color\":\"light_purple\",\"bold\":true}");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " color purple");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " style notched_10");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " max 1000");
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " value 1000");
        title("name", "dark_purple", "spawn.sub");
        Fx.sound(level, ax, floor + 6, az, "entity.wither.spawn", 20.0F, 0.6F);
        Fx.sound(level, ax, floor + 6, az, "block.end_portal.spawn", 20.0F, 0.5F);
        Fx.send(level, Fx.particle("flash"), ax, floor + 6, az, 2, 1, 1, 1, 0);
        Fx.send(level, Fx.particle("reverse_portal"), ax, floor + 6, az, 300, 1, 1, 1, 2.0);
    }

    // ================= Тик =================
    boolean tick() {
        t++;
        long bit = 1L << hall.id;
        if ((rec.hallsSpawned & bit) == 0 || (rec.hallsCleared & bit) != 0) { cleanup(); return true; }   // зал сброшен или засчитан без нас
        if (stage == -1) {
            if (t == 1) { purge(); clearPylons(); }
            if (t == 1 || (t % 15 == 0 && t < 60)) Fx.sound(level, ax, floor + 6, az, "entity.warden.heartbeat", 20.0F, 0.5F + t / 120F);
            if (t > 20 && t < 60) Fx.send(level, Fx.particle("reverse_portal"), ax, floor + 6, az, 40, 0.2, 0.2, 0.2, 1.5);
            if (t >= 60) { spawnHost(); stage = 0; t = 0; }
            return false;
        }
        if (stage == 0) {
            Entity h = find("ewb_host");
            if (!(h instanceof LivingEntity le)) { if (t > 40) { cleanup(); return true; } return false; }
            host = le;
            for (int i = 0; i < N; i++) {
                Entity p = find("ewb_p" + i);
                if (p != null) uuid[i] = p.m_20149_();
            }
            stage = 1; t = 0;
            pose(40, 1.0);
            return false;
        }
        if (host == null) { cleanup(); return true; }
        if (stage == 3) return dying();
        if (host.m_213877_()) { cleanup(); return true; }            // хоста убрали снаружи (сброс зала, команда)
        if (lethal || host.m_21223_() <= 1.0F || host.m_21224_()) { startDeath(); return false; }
        if (!host.m_6084_()) { cleanup(); return true; }
        if (host.m_6060_()) host.m_20095_();

        move();
        if (t % 4 == 0) pose(4, 1.0);
        aura();
        if (t % 10 == 0) bar();

        if (stage == 1) {
            if (t >= 50) { stage = 2; t = 0; Fx.sound(level, ax, cy(), az, "entity.ender_dragon.growl", 20.0F, 0.6F); }
            return false;
        }

        float hp = host.m_21223_();
        if (!shieldUsed && hp <= MAX_HP * 0.6F + 0.01F) startShield();
        if (!enraged && hp <= MAX_HP * 0.3F + 0.01F && !shield) enrage();

        tickShards(); tickSpikes(); tickWaves();
        if (shield) { tickShield(); minions(); }
        if (stun > 0) { stun--; dizzy(); return false; }
        attacks();
        return false;
    }

    /** Сущность этого боя с тегом (хост или часть облика). */
    Entity find(String tag) {
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_19880_().contains(ktag) && en.m_19880_().contains(tag))) return e;
        return null;
    }

    // ================= Движение и поза =================
    void move() {
        double tx, ty, tz, k = 0.06;
        if (shield) { tx = ax; tz = az; ty = floor + 3; }
        else if (stun > 0) { tx = bx; tz = bz; ty = floor - 1; k = 0.25; }
        else if (laserT >= 0) { tx = ax; tz = az; ty = floor + 1; }
        else if (slamT >= 0 && slamT < 30) { tx = bx; tz = bz; ty = floor + 4; k = 0.1; }
        else if (slamT >= 30) { tx = bx; tz = bz; ty = floor - 1; k = 0.45; }
        else {
            orbit += enraged ? 0.012 : 0.006;
            double r = enraged ? 5 : 7;   // над площадкой (R 9,5): хитбокс 3,6 шириной не уходит за край, в ближнем бою достаточно стоять на площадке
            tx = ax + Math.cos(orbit) * r; tz = az + Math.sin(orbit) * r;
            ty = floor + 0.6 + Math.sin(t * 0.05) * 0.6;
        }
        bx += (tx - bx) * k; by += (ty - by) * k; bz += (tz - bz) * k;
        host.m_6034_(bx, by, bz);
        host.m_20256_(new Vec3(0, 0, 0));
        // глаз смотрит на ближайшего
        ServerPlayer tgt = nearest();
        if (tgt != null) {
            Vec3 p = tgt.m_20182_();
            double dx = p.f_82479_ - bx, dz = p.f_82481_ - bz, l = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
            double nx = dx / l, nz = dz / l;
            ex += (nx - ex) * 0.15; ez += (nz - ez) * 0.15;
            double el = Math.max(1e-3, Math.sqrt(ex * ex + ez * ez)); ex /= el; ez /= el;
        }
    }

    ServerPlayer nearest() {
        ServerPlayer best = null; double bd = 1e9;
        for (ServerPlayer p : players()) { Vec3 v = p.m_20182_(); double d = (v.f_82479_ - bx) * (v.f_82479_ - bx) + (v.f_82481_ - bz) * (v.f_82481_ - bz); if (d < bd) { bd = d; best = p; } }
        return best;
    }

    /** Отправить новую позу всех 25 частей (клиент плавно интерполирует). */
    void pose(int dur, double gs) {
        double T = (t + (stage == 3 ? dyingT * 6 : 0)) * (enraged ? 2.0 : 1.0);
        double yawE = Math.atan2(ex, ez);
        for (int i = 0; i < N; i++) {
            if (uuid[i] == null) continue;
            double cx = 0, cyy = 0, cz = 0, sx, sy, sz; double[] q;
            if (i == 0) { sx = sy = sz = 3.0; q = Disp.q(0.3, 1, 0.2, T * 0.04); }
            else if (i == 1) { sx = sy = sz = 3.9; q = Disp.q(1, 0.3, 0.1, -T * 0.025); }
            else if (i == 2 || i == 3) {
                double d = i == 2 ? 2.25 : 2.85;
                cx = ex * d; cz = ez * d; cyy = -0.2;
                sx = sy = sz = i == 2 ? 1.15 : 0.6; if (i == 3) sz = 0.3;
                q = Disp.q(0, 1, 0, yawE);
            } else if (i < 12) {
                double a = (i - 4) * Math.PI * 2 / 8 + T * 0.05;
                cx = Math.cos(a) * 4.4; cz = Math.sin(a) * 4.4; cyy = Math.sin(a * 2 + T * 0.1) * 0.4;
                sx = sy = sz = 0.95; q = Disp.mul(Disp.q(0, 1, 0, -a), Disp.q(1, 0, 1, Math.PI / 4));
            } else if (i < 18) {
                double a = (i - 12) * Math.PI * 2 / 6 - T * 0.07;
                double[] p = Disp.rot(Disp.mul(Disp.q(0, 1, 0, T * 0.01), Disp.q(1, 0, 0, 1.1)), Math.cos(a) * 3.2, 0, Math.sin(a) * 3.2);
                cx = p[0]; cyy = p[1]; cz = p[2];
                sx = sy = sz = 0.7; q = Disp.q(1, 1, 0, T * 0.1 + i);
            } else if (i < 22) {
                double a = (i - 18) * Math.PI / 2 + T * 0.02;
                cx = Math.cos(a) * 1.1; cz = Math.sin(a) * 1.1; cyy = 2.3;
                sx = 1.3; sy = 1.8; sz = 1.3; q = Disp.q(-Math.sin(a), 0, Math.cos(a), 0.35);
            } else {
                double a = (i - 22) * Math.PI * 2 / 3 + T * 0.015;
                cx = Math.cos(a) * 0.9; cz = Math.sin(a) * 0.9; cyy = -3.4 + Math.sin(T * 0.08 + i) * 0.2;
                sx = 0.9; sy = 3.2; sz = 0.9; q = Disp.q(1, 0, 0, Math.sin(T * 0.06 + i) * 0.15);
            }
            String tf = Disp.tf(cx * gs, cyy * gs - 3, cz * gs, Math.max(0.001, sx * gs), Math.max(0.001, sy * gs), Math.max(0.001, sz * gs), q);
            Cmd.run(level, bx, by, bz, "data merge entity " + uuid[i] + " {start_interpolation:0,interpolation_duration:" + dur + "," + tf + "}");
        }
    }

    void setBlock(int i, String id) { if (uuid[i] != null) Cmd.run(level, bx, by, bz, "data merge entity " + uuid[i] + " {" + Disp.block(id) + "}"); }

    void aura() {
        double c = cy();
        Fx.send(level, Fx.particle("reverse_portal"), bx, c, bz, 4, 2, 2, 2, 0.05);
        if (t % 3 == 0) Fx.send(level, enraged ? RED : PURPLE, bx, c, bz, 6, 2.5, 2.5, 2.5, 0);
        if (t % 60 == 0) Fx.sound(level, bx, c, bz, "block.beacon.ambient", 6.0F, enraged ? 0.9F : 0.5F);
        if (shield) for (int i = 0; i < 30; i++) {
            double[] u = Fx.unit();
            Fx.send(level, WHITE, bx + u[0] * 4.6, c + u[1] * 4.6, bz + u[2] * 4.6, 1, 0, 0, 0, 0);
        }
    }

    void bar() {
        int v = (int) Math.max(0, Math.min(1000, host.m_21223_() / MAX_HP * 1000));
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " value " + v);
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " players " + sel());
    }

    void actionbar(String text, String color) {
        Cmd.run(level, ax, floor, az, "title " + sel() + " actionbar {\"translate\":\"" + KEY + text + "\",\"color\":\"" + color + "\"}");
    }

    void hurtPlayer(ServerPlayer p, float dmg) { p.m_6469_(level.m_269111_().m_269425_(), dmg); }

    // ================= Атаки =================
    void attacks() {
        List<ServerPlayer> ps = players();
        if (ps.isEmpty()) return;
        if (volleyT >= 0) volley(ps);
        else if (--volleyCd <= 0) { startVolley(ps); }
        if (--spikeCd <= 0) {
            for (ServerPlayer p : ps) { Vec3 v = p.m_20182_(); spikes.add(new double[]{v.f_82479_, v.f_82481_, 30, gy(v.f_82479_, v.f_82481_)}); }
            spikeCd = enraged ? 90 : 140;
        }
        if (shieldUsed && laserT < 0 && slamT < 0 && --laserCd <= 0) { laserT = 0; laserStart = RNG.nextDouble() * Math.PI * 2; laserCd = enraged ? 230 : 280; }
        if (laserT >= 0) laser(ps);
        if (enraged && laserT < 0 && slamT < 0 && --slamCd <= 0) { slamT = 0; slamCd = 240; }
        if (slamT >= 0) slam(ps);
    }

    int volleyCount = 5;
    int volleyN() { return volleyCount; }
    int volleyCharge() { return enraged ? 14 : 16; }

    /** Залп осколков: 16 тиков (0,8 с) заряда — звук и линии прицела из точек на кольце к выбранным целям, потом выстрел с упреждением. */
    void startVolley(List<ServerPlayer> ps) {
        volleyT = 0; volleyA = RNG.nextDouble() * Math.PI * 2;
        volleyCount = enraged ? 8 : 5;
        volleyTargets.clear();
        for (int i = 0; i < volleyN(); i++) volleyTargets.add(ps.get(RNG.nextInt(ps.size())));
        Fx.sound(level, bx, cy(), bz, "block.amethyst_block.resonate", 6.0F, 0.6F);
    }

    void volley(List<ServerPlayer> ps) {
        double c = cy();
        int n = volleyN();
        volleyT++;
        if (volleyT < volleyCharge()) {
            double prog = volleyT / (double) volleyCharge();
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + volleyA;
                double sx = bx + Math.cos(a) * 4.4, sz = bz + Math.sin(a) * 4.4;
                Fx.send(level, enraged ? RED : PINK, sx, c, sz, 3, 0.15, 0.15, 0.15, 0);
                ServerPlayer p = volleyTargets.get(i);
                if (p.m_6084_() && volleyT % 2 == 0) {
                    Vec3 pv = p.m_20182_();
                    double dx = pv.f_82479_ - sx, dy = pv.f_82480_ + 1.2 - c, dz = pv.f_82481_ - sz, l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
                    for (double s = 1.0; s < 1.0 + 5.0 * prog; s += 1.0) Fx.send(level, WHITE, sx + dx / l * s, c + dy / l * s, sz + dz / l * s, 1, 0, 0, 0, 0);
                }
            }
            if (volleyT == volleyCharge() / 2) Fx.sound(level, bx, c, bz, "block.amethyst_block.chime", 6.0F, 1.4F);
            return;
        }
        double sp = enraged ? 0.95 : 0.75;
        Fx.sound(level, bx, c, bz, "block.amethyst_cluster.break", 6.0F, 0.6F);
        for (int i = 0; i < n; i++) {
            ServerPlayer p = volleyTargets.get(i);
            if (!p.m_6084_()) p = ps.get(RNG.nextInt(ps.size()));
            double a = i * Math.PI * 2 / n + volleyA;
            double sx = bx + Math.cos(a) * 4.4, sy = c + (RNG.nextDouble() - 0.5), sz = bz + Math.sin(a) * 4.4;
            Vec3 pv = p.m_20182_(), vel = p.m_20184_();
            double dist = Math.sqrt((pv.f_82479_ - sx) * (pv.f_82479_ - sx) + (pv.f_82481_ - sz) * (pv.f_82481_ - sz));
            double lead = dist / sp;
            double tx = pv.f_82479_ + vel.f_82479_ * lead + (RNG.nextDouble() - 0.5) * 2, ty = pv.f_82480_ + 1.2, tz = pv.f_82481_ + vel.f_82481_ * lead + (RNG.nextDouble() - 0.5) * 2;
            double dx = tx - sx, dy = ty - sy, dz = tz - sz, l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
            shards.add(new double[]{sx, sy, sz, dx / l * sp, dy / l * sp, dz / l * sp, 0});
        }
        volleyT = -1; volleyTargets.clear();
        volleyCd = enraged ? 38 : (phase >= 2 ? 55 : 70);
    }

    void tickShards() {
        Iterator<double[]> it = shards.iterator();
        List<ServerPlayer> ps = players();
        outer:
        while (it.hasNext()) {
            double[] s = it.next();
            for (int k = 0; k < 2; k++) {
                s[0] += s[3] / 2; s[1] += s[4] / 2; s[2] += s[5] / 2;
                for (ServerPlayer p : ps) {
                    Vec3 v = p.m_20182_();
                    double dx = v.f_82479_ - s[0], dy = v.f_82480_ + 1 - s[1], dz = v.f_82481_ - s[2];
                    if (dx * dx + dy * dy + dz * dz < 1.1) {
                        hurtPlayer(p, enraged ? 8 : 6);
                        p.m_20256_(p.m_20184_().m_82520_(s[3] * 0.7, 0.35, s[5] * 0.7)); p.f_19864_ = true;
                        Fx.send(level, AMETHYST, s[0], s[1], s[2], 25, 0.3, 0.3, 0.3, 0.2);
                        Fx.sound(level, s[0], s[1], s[2], "block.amethyst_cluster.break", 2.0F, 0.8F);
                        it.remove(); continue outer;
                    }
                }
                if (!level.m_8055_(new BlockPos((int) Math.floor(s[0]), (int) Math.floor(s[1]), (int) Math.floor(s[2]))).m_60795_() || ++s[6] > 180) {
                    Fx.send(level, AMETHYST, s[0], s[1], s[2], 12, 0.2, 0.2, 0.2, 0.15);
                    Fx.sound(level, s[0], s[1], s[2], "block.amethyst_block.break", 1.0F, 1.2F);
                    it.remove(); continue outer;
                }
            }
            Fx.send(level, AMETHYST, s[0], s[1], s[2], 2, 0.05, 0.05, 0.05, 0);
            Fx.send(level, enraged ? RED : PINK, s[0], s[1], s[2], 1, 0, 0, 0, 0);
            Fx.send(level, Fx.particle("end_rod"), s[0] - s[3], s[1] - s[4], s[2] - s[5], 1, 0, 0, 0, 0);
        }
    }

    void tickSpikes() {
        Iterator<double[]> it = spikes.iterator();
        while (it.hasNext()) {
            double[] s = it.next();
            s[2]--;
            double g = s[3];
            double r = 0.4 + 2.2 * (s[2] / 30.0);
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI * 2 / 20 + t * 0.1;
                Fx.send(level, enraged ? RED : PURPLE, s[0] + Math.cos(a) * r, g + 0.15, s[1] + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (s[2] % 6 == 0) Fx.send(level, Fx.particle("witch"), s[0], g + 0.3, s[1], 4, 0.6, 0.1, 0.6, 0);
            if (s[2] <= 0) {
                Fx.send(level, AMETHYST, s[0], g + 1.5, s[1], 60, 0.6, 1.5, 0.6, 0.3);
                for (int h = 0; h < 6; h++) Fx.send(level, Fx.particle("end_rod"), s[0], g + h * 0.6, s[1], 3, 0.4, 0.2, 0.4, 0.15);
                Fx.sound(level, s[0], g, s[1], "entity.evoker_fangs.attack", 2.0F, 0.7F);
                Fx.sound(level, s[0], g, s[1], "block.amethyst_cluster.break", 2.0F, 0.6F);
                for (ServerPlayer p : players()) {
                    Vec3 v = p.m_20182_();
                    double dx = v.f_82479_ - s[0], dz = v.f_82481_ - s[1];
                    if (dx * dx + dz * dz < 2.4 * 2.4 && v.f_82480_ < g + 3) { hurtPlayer(p, 7); p.m_20256_(new Vec3(dx * 0.2, 1.1, dz * 0.2)); p.f_19864_ = true; }
                }
                it.remove();
            }
        }
    }

    void laser(List<ServerPlayer> ps) {
        laserT++;
        if (laserT < 25) {          // заряд + предупреждение (1,25 с)
            for (double d = 2; d < 27; d += 1.2) {
                double x = bx + Math.cos(laserStart) * d, z = bz + Math.sin(laserStart) * d;
                Fx.send(level, WHITE, x, gy(x, z) + 0.6, z, 1, 0, 0, 0, 0);
            }
            Fx.send(level, enraged ? RED : PINK, bx, cy(), bz, 10, 0.5, 0.5, 0.5, 0);
            if (laserT == 1) { Fx.sound(level, bx, cy(), bz, "block.beacon.power_select", 10.0F, 0.5F); actionbar("laser", "red"); }
            return;
        }
        int beams = enraged ? 2 : 1;
        double ang0 = laserStart + (laserT - 25) * 0.0628 * (enraged ? 1.3 : 1.0);
        for (int b = 0; b < beams; b++) {
            double ang = ang0 + b * Math.PI;
            double cx = Math.cos(ang), cz = Math.sin(ang);
            for (double d = 1; d < 27; d += 0.6) {
                double x = bx + cx * d, z = bz + cz * d, y = gy(x, z) + 0.6;
                Fx.send(level, enraged ? RED : PURPLE, x, y, z, 1, 0, 0, 0, 0);
                if (((int) (d * 10)) % 18 == 0) Fx.send(level, Fx.particle("end_rod"), x, y, z, 1, 0, 0, 0, 0);
            }
            for (ServerPlayer p : ps) {
                Vec3 v = p.m_20182_();
                double rx = v.f_82479_ - bx, rz = v.f_82481_ - bz;
                double along = rx * cx + rz * cz, lat = Math.abs(-rx * cz + rz * cx);
                if (along > 0 && along < 28 && lat < 0.8 && v.f_82480_ < gy(v.f_82479_, v.f_82481_) + 0.9) {
                    int last = hitCd.getOrDefault(p, -100);
                    if (t - last > 10) { hitCd.put(p, t); hurtPlayer(p, 6); Fx.send(level, RED, v.f_82479_, v.f_82480_ + 0.5, v.f_82481_, 15, 0.3, 0.3, 0.3, 0); }
                }
            }
        }
        for (double h = 0; h < cy() - floor; h += 0.5) Fx.send(level, enraged ? RED : PURPLE, bx, floor + h, bz, 1, 0.1, 0, 0.1, 0);
        if (laserT % 8 == 0) Fx.sound(level, bx, floor, bz, "block.beacon.ambient", 6.0F, 1.6F);
        if (laserT >= 125) laserT = -1;
    }

    void slam(List<ServerPlayer> ps) {
        slamT++;
        var lev = Fx.effect("levitation");
        if (slamT == 1) {
            Fx.sound(level, bx, cy(), bz, "entity.wither.shoot", 10.0F, 0.5F);
            actionbar("slam", "light_purple");
            for (ServerPlayer p : ps) if (lev != null) p.m_7292_(new MobEffectInstance(lev, 28, 2));
        }
        if (slamT < 30 && slamT % 3 == 0) for (ServerPlayer p : ps) { Vec3 v = p.m_20182_(); Fx.send(level, PURPLE, v.f_82479_, v.f_82480_, v.f_82481_, 4, 0.3, 0.1, 0.3, 0); }
        if (slamT == 30) for (ServerPlayer p : ps) { if (lev != null) p.m_21195_(lev); p.m_20256_(new Vec3(0, -1.6, 0)); p.f_19864_ = true; }
        if (slamT == 36) {
            waves.add(new double[]{2});
            Fx.send(level, Fx.particle("explosion_emitter"), bx, floor + 1, bz, 2, 1, 0.2, 1, 0);
            Fx.sound(level, bx, floor, bz, "entity.generic.explode", 20.0F, 0.5F);
            Fx.sound(level, bx, floor, bz, "block.anvil.land", 10.0F, 0.5F);
            actionbar("wave", "red");
        }
        if (slamT >= 70) slamT = -1;
    }

    void tickWaves() {
        Iterator<double[]> it = waves.iterator();
        while (it.hasNext()) {
            double[] w = it.next();
            w[0] += 0.9;
            int n = (int) (20 + w[0] * 6);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                double x = bx + Math.cos(a) * w[0], z = bz + Math.sin(a) * w[0], g = gy(x, z);
                Fx.send(level, RED, x, g + 0.3, z, 1, 0, 0, 0, 0);
                if (i % 4 == 0) Fx.send(level, Fx.particle("cloud"), x, g + 0.2, z, 1, 0, 0, 0, 0);
            }
            for (ServerPlayer p : players()) {
                Vec3 v = p.m_20182_();
                double dx = v.f_82479_ - bx, dz = v.f_82481_ - bz, d = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(d - w[0]) < 0.9 && v.f_82480_ < gy(v.f_82479_, v.f_82481_) + 0.5) {
                    int last = hitCd.getOrDefault(p, -100);
                    if (t - last > 10) { hitCd.put(p, t); hurtPlayer(p, 7); p.m_20256_(new Vec3(dx / Math.max(d, 0.1), 0.7, dz / Math.max(d, 0.1))); p.f_19864_ = true; }
                }
            }
            if (w[0] > HALL_R + 2) it.remove();
        }
    }

    // ================= Щит, пилоны и приспешники =================
    void startShield() {
        shieldUsed = true; shield = true; phase = 2;
        setBlock(1, "white_stained_glass");
        BlockState obs = Fx.state("obsidian"), am = Fx.state("amethyst_block");
        for (int i = 0; i < 4; i++) {
            int x = pylons[i][0], z = pylons[i][1];
            level.m_7731_(new BlockPos(x, floor, z), obs, 3);
            level.m_7731_(new BlockPos(x, floor + 1, z), obs, 3);
            level.m_7731_(new BlockPos(x, floor + 2, z), am, 3);
            pylonAlive[i] = true;
            Fx.send(level, Fx.particle("flash"), x + 0.5, floor + 2.5, z + 0.5, 1, 0, 0, 0, 0);
        }
        Fx.sound(level, ax, cy(), az, "block.beacon.activate", 20.0F, 0.6F);
        Fx.sound(level, ax, cy(), az, "entity.ender_dragon.growl", 20.0F, 0.8F);
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " color blue");
        title("shield", "aqua", "shield.sub");
        actionbar("shield.hint", "light_purple");
        volleyT = -1; volleyTargets.clear();
        minionCd = 60;
    }

    /** Приспешники щита: каждые 10 с по два осколочника-малыша у столбов (не больше 4 живых). Тег ewb_min, убираются вместе с боссом. */
    void minions() {
        if (--minionCd > 0) return;
        minionCd = 200;
        int have = 0;
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_6084_() && en.m_19880_().contains("ewb_min"))) have++;
        if (have > 2) return;
        int made = 0;
        for (int k = 0; k < 4 && made < 2; k++) {
            int i = RNG.nextInt(4);
            if (!pylonAlive[i]) continue;
            double a = Math.toRadians(30 + 90 * i);
            double x = cxi + 0.5 + Math.cos(a) * (PYLON_R - 2), z = czi + 0.5 + Math.sin(a) * (PYLON_R - 2);
            RiftMobs.Mob m = RiftMobs.spawnKind(level, RiftModels.Kind.SHARDLING_MINI, x, floor, z, hallTag);
            if (m != null && m.host != null) { m.host.m_20049_("ewb_min"); m.host.m_20049_(ktag); made++; }
        }
        if (made > 0) Fx.sound(level, ax, floor + 2, az, "block.amethyst_block.resonate", 4.0F, 1.6F);
    }

    /** Аметистовое ядро столба активного щита: его должны уметь разбить игроки и взрывы (иначе блоки данжа неприкосновенны). */
    static boolean isPylonCore(ServerLevel lvl, BlockPos pos) {
        if (BOSSES.isEmpty()) return false;
        for (Boss b : BOSSES.values()) {
            if (b.level != lvl || !b.shield || pos.m_123342_() != b.floor + 2) continue;
            for (int i = 0; i < 4; i++)
                if (b.pylonAlive[i] && b.pylons[i][0] == pos.m_123341_() && b.pylons[i][1] == pos.m_123343_()) return true;
        }
        return false;
    }

    void tickShield() {
        int alive = 0;
        for (int i = 0; i < 4; i++) {
            if (!pylonAlive[i]) continue;
            int x = pylons[i][0], z = pylons[i][1];
            BlockPos core = new BlockPos(x, floor + 2, z);
            if (level.m_8055_(core).m_60734_() != Fx.block("amethyst_block")) {
                pylonAlive[i] = false;
                Fx.send(level, Fx.particle("explosion"), x + 0.5, floor + 2.5, z + 0.5, 3, 0.3, 0.3, 0.3, 0);
                Fx.sound(level, x, floor + 2, z, "block.glass.break", 6.0F, 0.6F);
                Fx.sound(level, ax, cy(), az, "entity.wither.hurt", 10.0F, 0.7F);
                continue;
            }
            alive++;
            if (t % 2 == 0) {
                double sx = x + 0.5, sy = floor + 2.5, sz = z + 0.5, dx = bx - sx, dy = cy() - sy, dz = bz - sz;
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                for (double s = 0; s < l; s += 0.7) Fx.send(level, PINK, sx + dx * s / l, sy + dy * s / l, sz + dz * s / l, 1, 0, 0, 0, 0);
                Fx.send(level, Fx.particle("end_rod"), sx, sy + 0.6, sz, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }
        if (alive == 0) {
            shield = false; stun = 140;
            setBlock(1, enraged ? "red_stained_glass" : "purple_stained_glass");
            Fx.send(level, WHITE, bx, cy(), bz, 200, 3, 3, 3, 0.2);
            Fx.send(level, Fx.particle("flash"), bx, cy(), bz, 2, 1, 1, 1, 0);
            Fx.sound(level, bx, cy(), bz, "block.glass.break", 20.0F, 0.4F);
            Fx.sound(level, bx, cy(), bz, "entity.ender_dragon.hurt", 20.0F, 0.6F);
            actionbar("shield.broken", "gold");
            clearPylons();
            minionsOff();
        }
    }

    /** Приспешники уходят, когда щит разбит. */
    void minionsOff() {
        for (Entity e : level.m_6249_(null, Dungeons.hallBox(rec, hall), en -> en.m_19880_().contains("ewb_min") && en.m_19880_().contains(ktag))) {
            Fx.send(level, Fx.particle("poof"), e.m_20185_(), e.m_20186_() + 0.4, e.m_20189_(), 6, 0.2, 0.2, 0.2, 0.02);
            RiftMobs.remove(e);
        }
    }

    /** Убрать столбы: только наши блоки (обсидиан и аметист), клетки в шаблоне пустые (DungeonTest). */
    void clearPylons() {
        BlockState air = Fx.state("air");
        for (int[] p : pylons)
            for (int y = 0; y < 3; y++) {
                BlockPos bp = new BlockPos(p[0], floor + y, p[1]);
                net.minecraft.world.level.block.Block b = level.m_8055_(bp).m_60734_();
                if (b == Fx.block("obsidian") || b == Fx.block("amethyst_block")) level.m_7731_(bp, air, 3);
            }
    }

    void dizzy() {
        double c = cy() + 4;
        for (int i = 0; i < 5; i++) {
            double a = t * 0.3 + i * Math.PI * 2 / 5;
            Fx.send(level, Fx.particle("crit"), bx + Math.cos(a) * 2, c, bz + Math.sin(a) * 2, 1, 0, 0, 0, 0);
        }
    }

    void enrage() {
        enraged = true; phase = 3;
        setBlock(0, "redstone_block"); setBlock(1, "red_stained_glass"); setBlock(2, "shroomlight");
        Fx.sound(level, bx, cy(), bz, "entity.ravager.roar", 20.0F, 0.5F);
        Fx.sound(level, bx, cy(), bz, "entity.wither.spawn", 20.0F, 1.2F);
        Fx.send(level, RED, bx, cy(), bz, 200, 4, 4, 4, 0);
        Cmd.run(level, ax, floor, az, "bossbar set " + bar + " color red");
        title("rage", "dark_red", "rage.sub");
        actionbar("rage.hint", "dark_red");
    }

    // ================= Гибель =================
    void startDeath() {
        stage = 3; dyingT = 0;
        shards.clear(); spikes.clear(); waves.clear(); volleyT = -1; laserT = -1; slamT = -1;
        Cmd.run(level, ax, floor, az, "bossbar remove " + bar);
        Fx.sound(level, bx, cy(), bz, "entity.wither.death", 20.0F, 0.6F);
        Fx.sound(level, bx, cy(), bz, "entity.ender_dragon.death", 20.0F, 0.8F);
        clearPylons();
        minionsOff();
    }

    boolean dying() {
        dyingT++;
        double c = cy();
        if (dyingT == 1) pose(80, 0.0);
        if (dyingT % 3 == 0) {
            double[] u = Fx.unit();
            Fx.send(level, Fx.particle("explosion"), bx + u[0] * 4, c + u[1] * 4, bz + u[2] * 4, 1, 0, 0, 0, 0);
            Fx.send(level, AMETHYST, bx, c, bz, 20, 2, 2, 2, 0.3);
            Fx.sound(level, bx, c, bz, "block.amethyst_cluster.break", 6.0F, 0.5F + dyingT / 100F);
        }
        Fx.send(level, Fx.particle("end_rod"), bx, c, bz, 4, 0.3, 0.3, 0.3, 0.3);
        if (dyingT == 90) {
            purge();   // хост и части уходят; победу засчитывает tickAll (Dungeons.lairWon: hallsCleared + сундук)
            won = true;
            Fx.send(level, Fx.particle("flash"), bx, c, bz, 3, 1, 1, 1, 0);
            Fx.send(level, Fx.particle("explosion_emitter"), bx, c, bz, 3, 2, 2, 2, 0);
            Fx.send(level, Fx.particle("reverse_portal"), bx, c, bz, 400, 1, 1, 1, 3.0);
            Fx.sound(level, bx, c, bz, "entity.generic.explode", 20.0F, 0.6F);
            Fx.sound(level, bx, c, bz, "ui.toast.challenge_complete", 10.0F, 1.0F);
            String at = Cmd.f(ax) + " " + (floor + 1) + " " + Cmd.f(az + 3);
            for (int i = 0; i < 6; i++) Cmd.run(level, ax, floor, az, "summon experience_orb " + at + " {Value:250}");
            title("dead", "gold", "dead.sub");
            return true;
        }
        return false;
    }

    /** Урон по сердцу: щит, вспышки и звон; удар не перескакивает фазу и не добивает (смерть — через startDeath). */
    static void onHurt(LivingHurtEvent ev) {
        if (BOSSES.isEmpty()) return;
        Boss b = null;
        for (Boss x : BOSSES.values()) if (x.host != null && x.host == ev.getEntity()) { b = x; break; }
        if (b == null) return;
        if (Boss3.environmental(ev) || !b.vulnerable()) {
            ev.setCanceled(true);
            if (!Boss3.environmental(ev)) {
                Fx.send(b.level, WHITE, b.bx, b.cy(), b.bz, 20, 2, 2, 2, 0);
                Fx.sound(b.level, b.bx, b.cy(), b.bz, "block.amethyst_block.hit", 4.0F, 0.5F);
            }
            return;
        }
        ev.setAmount(b.clampHit(b.host.m_21223_(), ev.getAmount()));
        Fx.send(b.level, AMETHYST, b.bx, b.cy(), b.bz, 15, 1.5, 1.5, 1.5, 0.2);
        Fx.sound(b.level, b.bx, b.cy(), b.bz, "block.amethyst_block.hit", 4.0F, 0.7F + RNG.nextFloat() * 0.3F);
    }

    /** Урон, обрезанный до ближайшего порога фазы (щит 60%, ярость 30%); смертельный удар оставляет 1 ХП и запускает гибель. */
    float clampHit(float hp, float amt) {
        float floorHp = !shieldUsed ? MAX_HP * 0.6F : !enraged ? MAX_HP * 0.3F : 1.0F;
        if (hp - amt < floorHp) amt = hp > floorHp ? hp - floorHp : (floorHp > 1.0F ? 0.5F : 0.0F);
        if (enraged && hp - amt <= 1.0F) { amt = Math.max(0, hp - 1.0F); lethal = true; }
        return amt;
    }
}
