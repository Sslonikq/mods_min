package com.explosionwand;

import com.explosionwand.RiftLogic.EaterState;
import com.explosionwand.RiftLogic.GolemArmor;
import com.explosionwand.RiftLogic.ShadeState;
import com.explosionwand.RiftModels.Kind;
import com.explosionwand.RiftModels.Pose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * Мобы Разлома (фаза 4.3): облик из block_display на невидимом хосте-NoAI (как у Небесного Кита), своё поведение у каждого.
 *
 * Как устроено:
 *  - Хост — ванильный моб (даёт хитбокс, ХП, события урона) с NoAI/NoGravity/Silent и невидимостью; двигаем его сами (setPos), потому что
 *    ванильный ИИ не умеет «слепоту», засаду из трещины и окна неуязвимости. Теги хоста: ewd, ewd&lt;хеш&gt;h&lt;зал&gt; (как у ванильных мобов зала:
 *    Dungeons.alive/removeStrays/зачистка работают без изменений), ewr_h, ewrk_&lt;вид&gt;, ewri&lt;id&gt;.
 *  - Облик — пассажиры-block_display с тегами ewr_p, ewrp&lt;id&gt;, ewrn&lt;номер&gt; (тегов зала у них нет, «живыми мобами зала» они не считаются).
 *    Позу шлём командой data merge с интерполяцией раз в 3 тика, только изменившиеся части.
 *  - Тикаем из Dungeons.tick (только пока в мире есть игроки, и только мобов рядом с ними). Реестр MOBS восстанавливается сканированием
 *    хостов с тегом ewr_h (после перезапуска), части без хоста подметаются (sweep) и убираются вместе с хостом (remove).
 *  - Чистые правила (цикл лучника, слух, броня голема) — в RiftLogic, поза — в RiftModels.
 */
final class RiftMobs {
    private RiftMobs() {}

    static final String PFX = "explosionwand:";
    static final Random RNG = Fx.RNG;
    static final Map<UUID, Mob> MOBS = new HashMap<>();
    static final Map<String, Mob> BY_ID = new HashMap<>();
    static final List<Noise> NOISES = new ArrayList<>();
    static final Map<UUID, double[]> PREV = new HashMap<>();
    static final Set<UUID> MOVED = new HashSet<>();
    static long now = 0;
    static ParticleOptions VIO, WHT, DRK, PNK;

    static void init() {
        if (VIO != null) return;
        VIO = new DustParticleOptions(new Vector3f(0.7F, 0.35F, 1.0F), 1.2F);
        WHT = new DustParticleOptions(new Vector3f(1, 1, 1), 1.3F);
        DRK = new DustParticleOptions(new Vector3f(0.25F, 0.05F, 0.4F), 1.7F);
        PNK = new DustParticleOptions(new Vector3f(0.95F, 0.4F, 0.9F), 1.2F);
    }

    static final class Noise {
        final ServerLevel level; final double x, y, z; final long at;
        Noise(ServerLevel level, double x, double y, double z, long at) { this.level = level; this.x = x; this.y = y; this.z = z; this.at = at; }
    }

    /** Конец сервера: статика не должна перейти в следующий мир. */
    static void clear() { warned = false; MOBS.clear(); BY_ID.clear(); NOISES.clear(); PREV.clear(); MOVED.clear(); now = 0; }

    /** id метки spawn без префикса -> вид, или null (ванильные мобы). */
    static Kind kindOfMark(String a) {
        if (a == null || !a.startsWith(PFX)) return null;
        return Kind.byId(a.substring(PFX.length()));
    }

    // ================================================================ создание
    /** Моб по метке spawn (префикс PFX + вид); hallTag — тег зала (или null вне данжа). */
    static boolean spawn(ServerLevel sl, String mark, double x, double y, double z, String hallTag) {
        Kind k = kindOfMark(mark);
        if (k == null) return false;
        return spawnKind(sl, k, x, y, z, hallTag) != null;
    }

    static Mob make(Kind k) {
        switch (k) {
            case SHARDLING: return new Shardling(false);
            case SHARDLING_MINI: return new Shardling(true);
            case ARCHER: return new Archer();
            case SHADE: return new Shade();
            case PRISM: return new Prism();
            case EATER: return new Eater();
            case GOLEM: return new Golem();
            default: return new RiftBeetles.Beetle();
        }
    }

    static String newId() { return Long.toHexString(RNG.nextLong() & 0xFFFFFFFFFFL); }

    /** Часть, поднятая над точкой метки (лучник стоит на колонне выше пола). */
    static double lift(Kind k) { return k == Kind.ARCHER ? 0.9 : 0.0; }

    static Mob spawnKind(ServerLevel sl, Kind k, double x, double y, double z, String hallTag) {
        init();
        String id = newId();
        Mob m = make(k);
        m.setup(k, id, sl);
        m.x = x; m.y = y + lift(k); m.z = z;
        m.yaw = RNG.nextDouble() * Math.PI * 2;
        m.initPose();
        Cmd.run(sl, x, y, z, summon(m, hallTag));
        Fx.send(sl, Fx.particle("poof"), x, y + 0.6, z, 8, 0.3, 0.4, 0.3, 0.02);
        for (Entity e : Cmd.tagged(sl, x, y + lift(k), z, 3, "ewri" + id)) {
            if (e instanceof LivingEntity le && e.m_19880_().contains("ewr_h")) { adopt(sl, le); break; }
        }
        return BY_ID.get(id);
    }

    /** Команда summon хоста с пассажирами-частями облика (поза по умолчанию, высота посадки уточняется после появления). */
    static String summon(Mob m, String hallTag) {
        Kind k = m.kind;
        String[] tf = transforms(RiftModels.pose(k, m.pose), m.yaw, 0);
        StringBuilder sb = new StringBuilder("summon minecraft:").append(k.host).append(" ").append(Cmd.f(m.x)).append(" ").append(Cmd.f(m.y)).append(" ").append(Cmd.f(m.z)).append(" {");
        sb.append("Tags:[\"ewr_h\",\"ewrk_").append(k.id).append("\",\"ewri").append(m.id).append("\"");
        sb.append(",\"ewrh").append(Math.round(m.hx * 2)).append("_").append(Math.round(m.hy * 2)).append("_").append(Math.round(m.hz * 2)).append("\"");
        if (hallTag != null) sb.append(",\"ewd\",\"").append(hallTag).append("\"");
        sb.append("],NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,CanPickUpLoot:0b,DeathLootTable:\"minecraft:empty\",");
        sb.append("Attributes:[{Name:\"minecraft:generic.max_health\",Base:").append(k.hp).append("d},{Name:\"minecraft:generic.knockback_resistance\",Base:1d}],Health:").append(k.hp).append("f,");
        sb.append("ActiveEffects:[{Id:14,Amplifier:0b,Duration:-1,ShowParticles:0b}],CustomName:'{\"translate\":\"explosionwand.mob.").append(k.id).append("\"}',Passengers:[");
        for (int i = 0; i < k.parts(); i++) {
            if (i > 0) sb.append(",");
            int l = k.light[i];
            sb.append("{id:\"minecraft:block_display\",Tags:[\"ewr_p\",\"ewrp").append(m.id).append("\",\"ewrn").append(i).append("\"],").append(k.blocks[i])
              .append(",view_range:1f,brightness:{sky:").append(l).append(",block:").append(l).append("},").append(tf[i]).append("}");
        }
        return sb.append("]}").toString();
    }

    /** Зарегистрировать хост (свежий или найденный после загрузки мира). */
    static Mob adopt(ServerLevel sl, LivingEntity host) {
        if (MOBS.containsKey(host.m_20148_())) return MOBS.get(host.m_20148_());
        String id = null; Kind kind = null;
        for (String t : host.m_19880_()) {
            if (t.startsWith("ewri")) id = t.substring(4);
            else if (t.startsWith("ewrk_")) kind = Kind.byId(t.substring(5));
        }
        if (id == null || kind == null) return null;
        Mob m = make(kind);
        m.setup(kind, id, sl);
        m.host = host;
        m.x = host.m_20185_(); m.y = host.m_20186_(); m.z = host.m_20189_();
        m.home(m.x, m.y, m.z);
        for (String t : host.m_19880_()) {
            if (t.startsWith("ewd") && t.length() > 3) m.hallTag = t;
            else if (t.startsWith("ewrh")) {   // точка появления (в полублоках): у Тени хост прячется под полом, оттуда её не взять
                String[] c = t.substring(4).split("_");
                if (c.length == 3) m.home(Integer.parseInt(c[0]) / 2.0, Integer.parseInt(c[1]) / 2.0, Integer.parseInt(c[2]) / 2.0);
            }
        }
        for (Entity e : Cmd.tagged(sl, m.x, m.y, m.z, 8, "ewrp" + id)) {
            for (String t : e.m_19880_()) if (t.startsWith("ewrn")) {
                int i = Integer.parseInt(t.substring(4));
                if (i >= 0 && i < m.pe.length) m.pe[i] = e;
            }
        }
        MOBS.put(host.m_20148_(), m);
        BY_ID.put(id, m);
        return m;
    }

    /** Локальные позы -> строки transformation: поворот по yaw и сдвиг на высоту посадки rideY. */
    static String[] transforms(double[][] L, double yaw, double rideY) {
        double[] Q = Disp.q(0, 1, 0, yaw);
        String[] out = new String[L.length];
        for (int i = 0; i < L.length; i++) {
            double[] r = Disp.rot(Q, L[i][0], L[i][1], L[i][2]);
            double[] q2 = Disp.mul(Q, new double[]{L[i][6], L[i][7], L[i][8], L[i][9]});
            out[i] = Disp.tf(r[0], r[1] - rideY, r[2], Math.max(0.001, L[i][3]), Math.max(0.001, L[i][4]), Math.max(0.001, L[i][5]), q2);
        }
        return out;
    }

    // ================================================================ уборка
    /** Убрать сущность зала; если это моб Разлома — вместе с частями облика. */
    static void remove(Entity e) {
        if (e.m_19880_().contains("ewr_h") && e.m_9236_() instanceof ServerLevel sl) {
            String id = idOf(e);
            if (id != null) for (Entity p : Cmd.tagged(sl, e.m_20185_(), e.m_20186_(), e.m_20189_(), 8, "ewrp" + id)) p.m_146870_();
            Mob m = MOBS.remove(e.m_20148_());
            if (m != null) BY_ID.remove(m.id);
        }
        e.m_146870_();
    }

    static String idOf(Entity e) {
        for (String t : e.m_19880_()) if (t.startsWith("ewri")) return t.substring(4);
        return null;
    }

    // ================================================================ тик
    static void tick(ServerLevel sl, DungeonData data, List<ServerPlayer> ps, long tk) {
        init();
        now++;
        trackPlayers(ps);
        if (now % 20 == 0) {   // жуки: ночью или без игроков рядом уходят, даже если сами не тикают (56 блоков)
            for (Mob m : MOBS.values()) {
                if (m.kind != Kind.BEETLE || m.level != sl || m.host == null || m.host.m_213877_()) continue;
                double best = Double.MAX_VALUE;
                for (ServerPlayer p : ps) best = Math.min(best, Math.hypot(p.m_20185_() - m.x, p.m_20189_() - m.z));
                if (RiftLogic.beetleGone(sl.m_46462_(), best, RiftBeetles.GONE_RANGE)) retire(m);
            }
        }
        if (now % 20 == 0) { scan(sl, ps); sweep(sl, ps); }
        NOISES.removeIf(n -> now - n.at > RiftLogic.NOISE_TTL);
        Iterator<Map.Entry<UUID, Mob>> it = MOBS.entrySet().iterator();
        while (it.hasNext()) {
            Mob m = it.next().getValue();
            if (m.level != sl) continue;
            if (m.host == null || m.host.m_213877_()) { BY_ID.remove(m.id); it.remove(); continue; }
            if (m.host.m_21224_() || m.host.m_21223_() <= 0) continue;   // умирает: уберёт onDeath
            if (!m.near(ps)) continue;
            try { m.step(ps); }
            catch (RuntimeException e) { if (!warned) { warned = true; e.printStackTrace(); } }   // сбой одного моба не обрывает остальных
        }
        RiftBeetles.tick(sl, data, ps, now);
    }

    static boolean warned = false;

    /** Убрать моба (хост и части); из реестра его уберёт цикл тика. */
    static void retire(Mob m) {
        Fx.send(m.level, Fx.particle("poof"), m.x, m.y + 0.2, m.z, 6, 0.2, 0.1, 0.2, 0.01);
        m.discardParts();
        if (m.host != null) m.host.m_146870_();
    }

    private static void trackPlayers(List<ServerPlayer> ps) {
        MOVED.clear();
        Set<UUID> here = new HashSet<>();
        for (ServerPlayer p : ps) here.add(p.m_20148_());
        PREV.keySet().retainAll(here);
        for (ServerPlayer p : ps) {
            double[] pv = PREV.get(p.m_20148_());
            if (pv != null && Math.abs(p.m_20185_() - pv[0]) + Math.abs(p.m_20189_() - pv[1]) > 0.04) MOVED.add(p.m_20148_());
            PREV.put(p.m_20148_(), new double[]{p.m_20185_(), p.m_20189_()});
        }
    }

    private static AABB around(ServerPlayer p) {
        double x = p.m_20185_(), y = p.m_20186_(), z = p.m_20189_();
        return new AABB(x - 64, y - 48, z - 64, x + 64, y + 48, z + 64);
    }

    /** Хосты с тегом ewr_h, которых нет в реестре (после перезапуска или загрузки чанка), берём на учёт. */
    private static void scan(ServerLevel sl, List<ServerPlayer> ps) {
        for (ServerPlayer p : ps)
            for (Entity e : sl.m_6249_(null, around(p), en -> en.m_19880_().contains("ewr_h")))
                if (e instanceof LivingEntity le && le.m_6084_() && !MOBS.containsKey(le.m_20148_())) adopt(sl, le);
    }

    /** Части облика без живого хоста — остатки (сброс зала издалека, гибель, смена раскладки): убираем. */
    private static void sweep(ServerLevel sl, List<ServerPlayer> ps) {
        for (ServerPlayer p : ps)
            for (Entity e : sl.m_6249_(null, around(p), en -> en.m_19880_().contains("ewr_p"))) {
                String id = null;
                for (String t : e.m_19880_()) if (t.startsWith("ewrp")) id = t.substring(4);
                Mob m = id == null ? null : BY_ID.get(id);
                if (m == null || m.host == null || m.host.m_213877_() || !m.host.m_6084_()) e.m_146870_();
            }
    }

    // ================================================================ события
    static void noise(ServerLevel sl, double x, double y, double z) { NOISES.add(new Noise(sl, x, y, z, now)); }

    static void onHurt(LivingHurtEvent ev) {
        if (MOBS.isEmpty()) return;
        LivingEntity target = ev.getEntity();
        Entity atk = ev.getSource() == null ? null : ev.getSource().m_7639_();
        if (atk instanceof ServerPlayer && target.m_9236_() instanceof ServerLevel sl) {
            NOISES.removeIf(n -> now - n.at > RiftLogic.NOISE_TTL);   // тик может не идти (другое измерение): подрезаем здесь
            if (NOISES.size() < 64) noise(sl, target.m_20185_(), target.m_20186_(), target.m_20189_());
        }
        Mob m = MOBS.get(target.m_20148_());
        if (m == null) return;
        if (Boss3.environmental(ev)) { ev.setCanceled(true); return; }
        m.hurt(ev, atk);
    }

    static void onDeath(LivingDeathEvent ev) {
        Mob m = MOBS.remove(ev.getEntity().m_20148_());
        if (m == null) return;
        BY_ID.remove(m.id);
        try { m.die(); }
        catch (RuntimeException e) { if (!warned) { warned = true; e.printStackTrace(); } }
        m.discardParts();
    }

    // ================================================================ база моба
    abstract static class Mob {
        Kind kind; String id; ServerLevel level; LivingEntity host; String hallTag;
        Entity[] pe; String[] last;
        int t = 0, calib = 0;
        boolean ready = false;
        double rideY = 0, x, y, z, yaw, hx, hy, hz, leash = 28;
        boolean quietSent = false;
        long buffUntil = 0;
        List<ServerPlayer> ps = List.of();
        final Pose pose = new Pose();

        void setup(Kind k, String id, ServerLevel level) {
            this.kind = k; this.id = id; this.level = level;
            this.pe = new Entity[k.parts()]; this.last = new String[k.parts()];
            pose.sc = k.sc;
        }

        /** Точка появления (для засад и патрулей). */
        void home(double x, double y, double z) { hx = x; hy = y; hz = z; }
        void initPose() { home(x, y, z); }

        abstract void think();
        /** Поза на этот тик (поля Pose, зависящие от состояния). */
        void fillPose() {}
        /** Можно ли сейчас ранить (иначе удар отменяется). */
        boolean vulnerable() { return true; }
        /** Поправить урон по хосту (множители, окна неуязвимости). */
        void hurt(LivingHurtEvent ev, Entity atk) {
            if (!vulnerable()) { ev.setCanceled(true); deflect(); return; }
            Fx.send(level, Fx.particle("crit"), x, y + kind.sc, z, 4, 0.3, 0.3, 0.3, 0.1);
        }
        void die() {
            Fx.send(level, VIO, x, y + 0.6, z, 25, 0.4, 0.5, 0.4, 0.2);
            Fx.send(level, Fx.blockParticle(Fx.state("amethyst_block")), x, y + 0.6, z, 20, 0.3, 0.4, 0.3, 0.1);
            Fx.sound(level, x, y, z, "block.amethyst_block.break", 1.2F, 0.8F + RNG.nextFloat() * 0.4F);
        }

        /** Поза не меняется, слать нечего. */
        boolean quiet() { return false; }
        static double sq(double v) { return v * v; }

        void deflect() {
            Fx.send(level, WHT, x, y + 1.0, z, 8, 0.4, 0.5, 0.4, 0.02);
            Fx.sound(level, x, y + 1, z, "block.amethyst_block.hit", 1.5F, 1.8F);
        }

        double dmgMult() { return buffed() ? RiftLogic.BUFF_DAMAGE : 1.0; }
        double speedMult() { return buffed() ? RiftLogic.BUFF_SPEED : 1.0; }
        boolean buffed() { return now < buffUntil; }

        boolean near(List<ServerPlayer> ps) {
            for (ServerPlayer p : ps)
                if (p.m_6084_() && Math.abs(p.m_20185_() - x) <= 56 && Math.abs(p.m_20189_() - z) <= 56 && Math.abs(p.m_20186_() - y) <= 32) return true;
            return false;
        }

        void step(List<ServerPlayer> ps) {
            this.ps = ps;
            t++;
            x = host.m_20185_(); y = host.m_20186_(); z = host.m_20189_();
            if (host.m_6060_()) host.m_20095_();
            if (!ready) { calibrate(); if (!ready) return; }
            think();
            fillPose();
            if (quiet()) {   // нечего показывать (спрятанная Тень): последнюю позу шлём один раз
                if (!quietSent) { render(); quietSent = true; }
                return;
            }
            quietSent = false;
            double near = Double.MAX_VALUE;
            for (ServerPlayer p : ps) near = Math.min(near, Math.sqrt(sq(p.m_20185_() - x) + sq(p.m_20186_() - y) + sq(p.m_20189_() - z)));
            int every = RiftLogic.renderEvery(near);
            if (every > 0 && t % every == 0) render();
        }

        void calibrate() {
            calib++;
            if (calib < 3) return;
            Entity p0 = null;
            for (Entity e : pe) if (e != null && !e.m_213877_()) { p0 = e; break; }
            double r = p0 == null ? 0 : p0.m_20186_() - host.m_20186_();
            if (r > 0.05 && r < 8) rideY = r;
            else if (calib >= 8) rideY = host.m_20206_() * 0.75;
            else return;
            ready = true;
            Arrays.fill(last, null);
        }

        void render() {
            String[] tf = transforms(RiftModels.pose(kind, pose), yaw, rideY);
            for (int i = 0; i < tf.length; i++) {
                Entity e = pe[i];
                if (e == null || e.m_213877_() || tf[i].equals(last[i])) continue;
                last[i] = tf[i];
                Cmd.run(level, x, y, z, "data merge entity " + e.m_20149_() + " {start_interpolation:0,interpolation_duration:3," + tf[i] + "}");
            }
        }

        void discardParts() { for (Entity e : pe) if (e != null && !e.m_213877_()) e.m_146870_(); }

        // ---------------- движение
        /** Клетка проходима: воздух или блок без коллизии (трава, цветы, вода). */
        boolean air(double px, double py, double pz) {
            BlockPos bp = new BlockPos((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
            BlockState st = level.m_8055_(bp);
            return st.m_60795_() || st.m_60812_(level, bp).m_83281_();
        }

        boolean cellFree(int cx, int cy, int cz, int height) {
            for (int h = 0; h < height; h++) if (!air(cx, cy + h, cz)) return false;
            return true;
        }

        /** Пол под клеткой (nx,nz): тот же уровень, шаг вверх на 1 или спуск до 2; null — идти нельзя. */
        Double groundAt(double nx, double nz, int height) {
            int cx = (int) Math.floor(nx), cz = (int) Math.floor(nz), fy = (int) Math.floor(y + 0.01);
            for (int dy : new int[]{0, 1, -1, -2}) {
                int cy = fy + dy;
                if (cellFree(cx, cy, cz, height) && !air(cx, cy - 1, cz)) return (double) cy;
            }
            return null;
        }

        void setPos(double nx, double ny, double nz) {
            host.m_6034_(nx, ny, nz);
            host.m_20256_(new Vec3(0, 0, 0));
            x = nx; y = ny; z = nz;
        }

        /** Шаг по земле к (tx,tz) со скоростью speed блоков/тик; скользит вдоль стен. true — сдвинулся. */
        boolean walk(double tx, double tz, double speed, int height) {
            double dx = tx - x, dz = tz - z, d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1e-4) return false;
            double st = Math.min(speed, d), ux = dx / d, uz = dz / d;
            double[][] tries = {{ux, uz}, {Math.signum(ux), 0}, {0, Math.signum(uz)}};
            for (int i = 0; i < tries.length; i++) {
                if (tries[i][0] == 0 && tries[i][1] == 0) continue;
                double k = i == 0 ? st : st * Math.abs(i == 1 ? ux : uz);
                double nx = x + tries[i][0] * k, nz = z + tries[i][1] * k;
                if (leashBlocked(nx, nz)) continue;
                Double ny = groundAt(nx, nz, height);
                if (ny != null) { setPos(nx, ny, nz); return true; }
            }
            return false;
        }

        /** Поводок к точке появления: дальше leash от неё моб не уходит (зал не засчитается без убийств, соседний сброс его найдёт). */
        boolean leashBlocked(double nx, double nz) { return RiftLogic.leashBlocked(Math.hypot(x - hx, z - hz), Math.hypot(nx - hx, nz - hz), leash); }

        /** Шаг по воздуху к точке (нужны две свободные клетки). */
        boolean fly(double tx, double ty, double tz, double speed) {
            double dx = tx - x, dy = ty - y, dz = tz - z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1e-3) return false;
            double st = Math.min(speed, d);
            double[][] tries = {{dx / d, dy / d, dz / d}, {dx / d, 0, dz / d}, {0, dy / d, 0}};
            for (double[] v : tries) {
                double nx = x + v[0] * st, ny = y + v[1] * st, nz = z + v[2] * st;
                if (leashBlocked(nx, nz)) continue;
                if (air(nx, ny, nz) && air(nx, ny + 1, nz)) { setPos(nx, ny, nz); return true; }
            }
            return false;
        }

        /** Нет твёрдых блоков на отрезке (шаг 0.4). */
        boolean clear(double ax, double ay, double az, double bx, double by, double bz) {
            double dx = bx - ax, dy = by - ay, dz = bz - az, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int n = (int) (d / 0.4);
            for (int i = 1; i < n; i++) if (!air(ax + dx * i / n, ay + dy * i / n, az + dz * i / n)) return false;
            return true;
        }

        /** Конец луча из (ax,ay,az) в направлении (ux,uy,uz) длиной до max: останавливается у первого блока. */
        double[] rayEnd(double ax, double ay, double az, double ux, double uy, double uz, double max) {
            double d = 0;
            while (d < max) {
                double nx = ax + ux * (d + 0.5), ny = ay + uy * (d + 0.5), nz = az + uz * (d + 0.5);
                if (!air(nx, ny, nz)) break;
                d += 0.5;
            }
            return new double[]{ax + ux * d, ay + uy * d, az + uz * d};
        }

        void face(double tx, double tz, double rate) { yaw = RiftLogic.turn(yaw, Math.atan2(tx - x, tz - z), rate); }

        double hd(double px, double pz) { double dx = px - x, dz = pz - z; return Math.sqrt(dx * dx + dz * dz); }

        /** Ближайший живой игрок не дальше range по горизонтали и dy по вертикали. */
        ServerPlayer nearest(double range, double dy) { return nearestFrom(x, y, z, range, dy); }

        ServerPlayer nearestFrom(double fx, double fy, double fz, double range, double dy) {
            ServerPlayer best = null; double bd = range * range;
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_() || Math.abs(p.m_20186_() - fy) > dy) continue;
                double dx = p.m_20185_() - fx, dz = p.m_20189_() - fz, d = dx * dx + dz * dz;
                if (d <= bd) { bd = d; best = p; }
            }
            return best;
        }

        void hitPlayer(ServerPlayer p, double dmg, double kb) {
            p.m_6469_(level.m_269111_().m_269333_(host), (float) (dmg * dmgMult()));
            double dx = p.m_20185_() - x, dz = p.m_20189_() - z, l = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
            p.m_20256_(new Vec3(dx / l * kb, 0.32, dz / l * kb));
            p.f_19864_ = true;
        }

        void dust(ParticleOptions o, double px, double py, double pz) { Fx.send(level, o, px, py, pz, 1, 0, 0, 0, 0); }
        void sound(String id, float vol, float pitch) { Fx.sound(level, x, y + 1, z, id, vol, pitch); }

        /** Линия частиц от a к b каждые step блоков (телеграф луча). */
        void line(ParticleOptions o, double ax, double ay, double az, double bx, double by, double bz, double step) {
            double dx = bx - ax, dy = by - ay, dz = bz - az, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int n = Math.max(1, (int) (d / step));
            for (int i = 0; i <= n; i++) dust(o, ax + dx * i / n, ay + dy * i / n, az + dz * i / n);
        }

        void ring(ParticleOptions o, double cx, double cy, double cz, double r, int n) {
            for (int i = 0; i < n; i++) { double a = i * Math.PI * 2 / n; dust(o, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r); }
        }
    }

    // ================================================================ Осколочник
    /** Кристальный ползун: бежит на игрока, замах 8 тиков, удар 4 (мелкий 2); при смерти рассыпается на двух мелких. */
    static final class Shardling extends Mob {
        final boolean mini; int wind = 0, cd = 0;
        Shardling(boolean mini) { this.mini = mini; }

        @Override void think() {
            pose.t = t;
            if (wind > 0) {
                pose.atk = 1 - wind / 8.0;
                if (--wind == 0) strike();
                return;
            }
            pose.atk = Math.max(0, pose.atk - 0.15);
            if (cd > 0) cd--;
            ServerPlayer p = nearest(24, 4);
            if (p == null) return;
            face(p.m_20185_(), p.m_20189_(), 0.3);
            double d = hd(p.m_20185_(), p.m_20189_());
            if (d < 1.7 && cd == 0 && Math.abs(p.m_20186_() - y) < 2.2) {
                wind = 8;
                sound("block.amethyst_block.chime", 0.8F, mini ? 1.9F : 1.4F);
                return;
            }
            if (d > 1.2 && walk(p.m_20185_(), p.m_20189_(), (mini ? 0.22 : 0.17) * speedMult(), 1)) pose.ph += 0.9;
        }

        void strike() {
            cd = 22;
            for (ServerPlayer p : ps)
                if (p.m_6084_() && !p.m_5833_() && hd(p.m_20185_(), p.m_20189_()) < 2.3 && Math.abs(p.m_20186_() - y) < 2.2) hitPlayer(p, mini ? 2 : 4, 0.25);
            sound("block.amethyst_cluster.hit", 1.0F, 1.2F);
        }

        @Override void die() {
            super.die();
            if (!RiftLogic.splitsOnDeath(mini)) return;
            for (double[] o : RiftLogic.splitOffsets(yaw))
                spawnKind(level, Kind.SHARDLING_MINI, x + o[0], y, z + o[1], hallTag);
            Fx.send(level, PNK, x, y + 0.5, z, 20, 0.4, 0.3, 0.4, 0.1);
            sound("block.amethyst_block.resonate", 1.2F, 1.5F);
        }
    }

    // ================================================================ Кристальный лучник
    /** Стоит на колонне, бьёт лучом-осколком с прицелом 25 тиков; раз в 13 с прячется в кристалл (неуязвим 5 с). */
    static final class Archer extends Mob {
        int off = RNG.nextInt(100), aim = -1, cd = 30;   // старт в открытой фазе, лучники в разных точках цикла
        double lx, ly, lz;

        @Override void think() {
            pose.t = t;
            int c = t + off, ph = RiftLogic.archerPhase(c);
            pose.cocoon = RiftLogic.archerCocoon(c);
            if (Math.floorMod(c, RiftLogic.ARCHER_CYCLE) == RiftLogic.ARCHER_CLOSE) {
                sound("block.amethyst_block.resonate", 1.5F, 0.6F);
                Fx.send(level, VIO, x, y + 1.2, z, 25, 0.6, 0.9, 0.6, 0.02);
            }
            if (Math.floorMod(c, RiftLogic.ARCHER_CYCLE) == RiftLogic.ARCHER_REOPEN) {
                sound("block.amethyst_block.break", 1.5F, 1.2F);
                Fx.send(level, Fx.particle("end_rod"), x, y + 1.2, z, 20, 0.6, 0.9, 0.6, 0.05);
            }
            if (ph != 0) { aim = -1; pose.charge = 0; return; }
            if (cd > 0) cd--;
            ServerPlayer p = nearest(26, 8);
            double[] tip = tip();
            if (aim < 0) {
                if (p == null) { yaw += 0.01; return; }
                face(p.m_20185_(), p.m_20189_(), 0.12);
                if (cd == 0 && RiftLogic.archerMayAim(c) && clear(tip[0], tip[1], tip[2], p.m_20185_(), p.m_20186_() + 1.4, p.m_20189_())) {
                    aim = 25; lx = p.m_20185_(); ly = p.m_20186_() + 1.0; lz = p.m_20189_();
                    sound("block.amethyst_block.chime", 1.4F, 0.8F);
                }
                return;
            }
            aim--;
            pose.charge = 1 - aim / 25.0;
            face(lx, lz, 0.25);
            tip = tip();
            double[] dir = dir(tip);
            double[] end = rayEnd(tip[0], tip[1], tip[2], dir[0], dir[1], dir[2], 30);
            if (aim % 2 == 0) line(VIO, tip[0], tip[1], tip[2], end[0], end[1], end[2], 1.5);
            if (aim == 12) sound("block.amethyst_block.chime", 1.2F, 1.4F);
            if (aim == 0) {
                aim = -1; pose.charge = 0; cd = 40 + RNG.nextInt(25);
                fire(tip, dir, end);
            }
        }

        double[] tip() {
            double fx = Math.sin(yaw), fz = Math.cos(yaw);
            return new double[]{x + Math.cos(yaw) * 0.3 + fx * 1.6, y + 1.5, z - Math.sin(yaw) * 0.3 + fz * 1.6};
        }

        double[] dir(double[] tip) {
            double dx = lx - tip[0], dy = ly - tip[1], dz = lz - tip[2], l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
            return new double[]{dx / l, dy / l, dz / l};
        }

        void fire(double[] tip, double[] dir, double[] end) {
            for (int i = 0; i <= 20; i++) {
                double u = i / 20.0;
                Fx.send(level, Fx.particle("end_rod"), tip[0] + (end[0] - tip[0]) * u, tip[1] + (end[1] - tip[1]) * u, tip[2] + (end[2] - tip[2]) * u, 1, 0, 0, 0, 0);
            }
            sound("block.amethyst_cluster.break", 1.6F, 1.7F);
            sound("entity.firework_rocket.blast", 1.0F, 1.6F);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                if (RiftLogic.segDist(tip[0], tip[1], tip[2], end[0], end[1], end[2], p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_()) < 0.9) {
                    hitPlayer(p, 6, 0.2);
                    Fx.send(level, VIO, p.m_20185_(), p.m_20186_() + 1, p.m_20189_(), 15, 0.3, 0.4, 0.3, 0.1);
                }
            }
        }

        @Override boolean vulnerable() { return !RiftLogic.archerInvulnerable(t + off); }
    }

    // ================================================================ Тень Разлома
    /** Прячется под полом; вылезает из трещины за спиной игрока (телеграф 28 тиков), бьёт когтями (замах 14 тиков) и исчезает. */
    static final class Shade extends Mob {
        ShadeState st = ShadeState.HIDDEN; int tm = 0, cd = 40 + RNG.nextInt(40);
        boolean second = false;

        @Override void initPose() { super.initPose(); pose.e = 0; pose.crack = 0; }

        @Override void think() {
            pose.t = t;
            switch (st) {
                case HIDDEN -> {
                    pose.e = 0; pose.crack = 0; pose.atk = 0; pose.strike = 0;
                    if (Math.abs(y - (hy - 3)) > 0.01 || Math.abs(x - hx) > 0.01 || Math.abs(z - hz) > 0.01) setPos(hx, hy - 3, hz);
                    if (cd > 0) { cd--; return; }
                    ServerPlayer p = nearestFrom(hx, hy, hz, 14, 5);
                    if (p == null) return;
                    double[] a = ambush(p);
                    if (a == null) { cd = 20; return; }
                    setPos(a[0], a[1], a[2]);
                    yaw = Math.atan2(p.m_20185_() - a[0], p.m_20189_() - a[2]);
                    st = ShadeState.CRACK; tm = 0;
                    sound("entity.enderman.stare", 1.2F, 0.5F);
                }
                case CRACK -> {
                    tm++;
                    pose.crack = Math.min(1, tm / 12.0); pose.e = 0;
                    if (tm % 2 == 0) {
                        ring(DRK, x, y + 0.1, z, 1.2, 14);
                        Fx.send(level, Fx.particle("reverse_portal"), x, y + 0.2, z, 3, 0.5, 0.1, 0.5, 0.05);
                    }
                    if (tm == 14) sound("block.respawn_anchor.charge", 1.2F, 0.6F);
                    if (tm >= 28) { st = ShadeState.EMERGE; tm = 0; sound("entity.enderman.teleport", 1.0F, 0.6F); }
                }
                case EMERGE -> {
                    tm++;
                    pose.e = Math.min(1, tm / 8.0); pose.crack = 1;
                    if (tm >= 8) { st = ShadeState.VISIBLE; tm = 0; second = false; }
                }
                case VISIBLE -> visible();
                case VANISH -> {
                    tm++;
                    double f = Math.max(0, 1 - tm / 10.0);
                    pose.e = f; pose.crack = f;
                    if (tm >= 10) {
                        st = ShadeState.HIDDEN; cd = 90 + RNG.nextInt(50);
                        setPos(hx, hy - 3, hz);
                    }
                }
            }
        }

        /** Атаки: замах 6..20, удар на 20; если рядом всё ещё игрок — второй замах 36..48; уход на 56/64. */
        void visible() {
            tm++;
            pose.strike = Math.max(0, pose.strike - 0.12);
            ServerPlayer p = nearest(10, 4);
            boolean w1 = tm >= 6 && tm < 20, w2 = second && tm >= 36 && tm < 48;
            if (p != null && (tm < 18 || (second && tm < 46))) face(p.m_20185_(), p.m_20189_(), 0.3);
            if (w1) pose.atk = (tm - 6) / 14.0;
            if (w2) pose.atk = (tm - 36) / 12.0;
            if (tm == 6 || (second && tm == 36)) sound("entity.enderman.scream", 0.8F, 1.8F);
            if ((w1 || w2) && tm % 2 == 0) arc();
            if (tm == 20 || (second && tm == 48)) slash();
            if (tm == 36 && !second) second = nearest(3.5, 3) != null;
            if (tm >= (second ? 64 : 56)) { st = ShadeState.VANISH; tm = 0; pose.atk = 0; sound("entity.enderman.teleport", 1.0F, 0.4F); }
        }

        void arc() {
            for (int i = -3; i <= 3; i++) {
                double a = yaw + i * 0.26;
                dust(WHT, x + Math.sin(a) * 2.0, y + 1.0, z + Math.cos(a) * 2.0);
            }
        }

        void slash() {
            pose.atk = 0; pose.strike = 1;
            sound("entity.player.attack.sweep", 1.2F, 0.7F);
            double fx = Math.sin(yaw), fz = Math.cos(yaw);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                double dx = p.m_20185_() - x, dz = p.m_20189_() - z, d = Math.sqrt(dx * dx + dz * dz);
                if (d < 2.7 && Math.abs(p.m_20186_() - y) < 2.5 && (d < 0.4 || (dx * fx + dz * fz) / d > 0.1)) hitPlayer(p, 8, 0.45);
            }
            for (int i = -3; i <= 3; i++) {
                double a = yaw + i * 0.26;
                Fx.send(level, Fx.particle("crit"), x + Math.sin(a) * 1.8, y + 1.0, z + Math.cos(a) * 1.8, 3, 0.1, 0.2, 0.1, 0.1);
            }
        }

        /** Точка за спиной игрока (потом сбоку): свободная стоячая клетка высотой 2. */
        double[] ambush(ServerPlayer p) {
            double yawDeg = p.m_146908_();
            double[][] cand = {RiftLogic.behind(p.m_20185_(), p.m_20189_(), yawDeg, 3.0), RiftLogic.behind(p.m_20185_(), p.m_20189_(), yawDeg, 2.2),
                    RiftLogic.behind(p.m_20185_(), p.m_20189_(), yawDeg, 4.0), RiftLogic.beside(p.m_20185_(), p.m_20189_(), yawDeg, 2.5, 1),
                    RiftLogic.beside(p.m_20185_(), p.m_20189_(), yawDeg, 2.5, -1)};
            int fy = (int) Math.floor(p.m_20186_() + 0.01);
            for (double[] c : cand) {
                for (int dy : new int[]{0, 1, -1}) {
                    int cy = fy + dy;
                    if (cellFree((int) Math.floor(c[0]), cy, (int) Math.floor(c[1]), 2) && !air(c[0], cy - 1, c[1])) return new double[]{c[0], cy, c[1]};
                }
            }
            return null;
        }

        @Override boolean vulnerable() { return RiftLogic.shadeVulnerable(st); }
        @Override boolean quiet() { return st == ShadeState.HIDDEN; }
    }

    // ================================================================ Призма
    /** Летает над залом, каждые 20 тиков усиливает мобов вокруг (x1.5 урона, x1.25 скорости), бьёт лучом сверху (телеграф 30 тиков). */
    static final class Prism extends Mob {
        double ang = RNG.nextDouble() * Math.PI * 2; int cd = 50 + RNG.nextInt(40), aim = -1;
        double lx, ly, lz;

        @Override void think() {
            pose.t = t;
            if (t % 20 == 0) buff();
            ServerPlayer p = nearest(24, 9);
            double tx, tz, ty = hy + 4.5;
            if (p != null) { ang += 0.02; tx = p.m_20185_() + Math.cos(ang) * 4; tz = p.m_20189_() + Math.sin(ang) * 4; ty = Math.max(ty, p.m_20186_() + 3.5); }
            else { ang += 0.012; tx = hx + Math.cos(ang) * 5; tz = hz + Math.sin(ang) * 5; }
            while (ty > hy + 2 && !(air(tx, ty, tz) && air(tx, ty + 1, tz))) ty -= 1;
            fly(tx, ty, tz, 0.09 * speedMult());
            if (aim < 0) {
                pose.charge = Math.max(0, pose.charge - 0.1);
                if (cd > 0) cd--;
                if (cd == 0 && p != null && clear(x, y, z, p.m_20185_(), p.m_20186_() + 1, p.m_20189_())) {
                    aim = 30; lx = p.m_20185_(); ly = p.m_20186_(); lz = p.m_20189_();
                    sound("block.respawn_anchor.charge", 1.4F, 1.3F);
                }
                return;
            }
            aim--;
            pose.charge = 1 - aim / 30.0;
            if (aim % 2 == 0) { ring(VIO, lx, ly + 0.15, lz, 1.4, 16); }
            if (aim % 3 == 0) line(WHT, x, y, z, lx, ly + 0.15, lz, 1.2);
            if (aim == 0) { aim = -1; cd = 70 + RNG.nextInt(30); beam(); }
        }

        void beam() {
            for (double yy = ly; yy < y + 0.5; yy += 0.5) Fx.send(level, Fx.particle("end_rod"), lx, yy, lz, 1, 0.05, 0, 0.05, 0);
            Fx.send(level, Fx.particle("explosion"), lx, ly + 0.3, lz, 1, 0, 0, 0, 0);
            Fx.sound(level, lx, ly, lz, "block.amethyst_cluster.break", 1.6F, 0.8F);
            Fx.sound(level, lx, ly, lz, "entity.generic.explode", 0.8F, 1.6F);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                double dx = p.m_20185_() - lx, dz = p.m_20189_() - lz;
                if (dx * dx + dz * dz < 1.4 * 1.4 && Math.abs(p.m_20186_() - ly) < 3) {
                    p.m_6469_(level.m_269111_().m_269333_(host), (float) (7 * dmgMult()));
                    p.m_20256_(new Vec3(dx * 0.3, 0.4, dz * 0.3)); p.f_19864_ = true;
                }
            }
        }

        /** Усиление мобов рядом: наши — множителем (buffUntil), ванильные мобы зала — эффектами. */
        void buff() {
            int n = 0;
            for (Mob m : MOBS.values()) {
                if (m == this || m.level != level || m.kind == Kind.PRISM || m.kind == Kind.BEETLE || m.host == null || !m.host.m_6084_()) continue;
                double dx = m.x - x, dy = m.y - y, dz = m.z - z;
                if (dx * dx + dy * dy + dz * dz > RiftLogic.BUFF_RADIUS * RiftLogic.BUFF_RADIUS) continue;
                m.buffUntil = now + RiftLogic.BUFF_TICKS;
                line(PNK, x, y, z, m.x, m.y + 0.8, m.z, 1.5);
                n++;
            }
            Cmd.run(level, x, y, z, "effect give @e[tag=ewd,distance=.." + (int) RiftLogic.BUFF_RADIUS + ",type=!minecraft:phantom] minecraft:strength 3 0 true");
            Cmd.run(level, x, y, z, "effect give @e[tag=ewd,distance=.." + (int) RiftLogic.BUFF_RADIUS + ",type=!minecraft:phantom] minecraft:speed 3 0 true");
            if (n > 0) sound("block.amethyst_block.chime", 0.7F, 1.9F);
        }
    }

    // ================================================================ Пожиратель эха
    /** Слепой: слышит бег (12 блоков), шаг (5), удары (14); крадущегося и стоящего — только на ощупь (1.5). Услышал — замирает 18 тиков и бросается. */
    static final class Eater extends Mob {
        EaterState st = EaterState.IDLE; int tm = 0, stuck = 0, wanderT = 0;
        double gx, gz, wx, wz;

        @Override void think() {
            pose.t = t;
            double[] h = hear();
            switch (st) {
                case IDLE -> {
                    pose.ears = Math.max(0, pose.ears - 0.1); pose.jaw = 0.1 + Math.sin(t * 0.1) * 0.05; pose.lift = Math.max(0, pose.lift - 0.1);
                    if (h != null) { alert(h); return; }
                    if (wanderT > 0) { wanderT--; if (walk(wx, wz, 0.04, 2)) pose.ph += 0.35; face(wx, wz, 0.05); }
                    else if (t % 90 == 0) { wx = hx + (RNG.nextDouble() - 0.5) * 8; wz = hz + (RNG.nextDouble() - 0.5) * 8; wanderT = 50; }
                }
                case ALERT -> {
                    tm++;
                    pose.ears = Math.min(1, tm / 8.0); pose.lift = Math.min(1, tm / 8.0); pose.jaw = 0.3;
                    if (h != null) { gx = h[0]; gz = h[1]; }
                    face(gx, gz, 0.2);
                    if (tm >= 18) { st = EaterState.CHARGE; tm = 0; stuck = 0; sound("entity.ravager.roar", 1.6F, 0.7F); }
                }
                case CHARGE -> {
                    tm++;
                    pose.jaw = 0.95; pose.ears = 0.6; pose.lift = 0.3;
                    if (h != null) { gx = h[0]; gz = h[1]; }
                    face(gx, gz, 0.3);
                    boolean moved = walk(gx, gz, 0.3 * speedMult(), 2);
                    if (moved) { pose.ph += 1.1; stuck = 0; } else stuck++;
                    for (ServerPlayer p : ps) {
                        if (!p.m_6084_() || p.m_5833_()) continue;
                        if (hd(p.m_20185_(), p.m_20189_()) < 1.7 && Math.abs(p.m_20186_() - y) < 2.2) { bite(p); return; }
                    }
                    if (hd(gx, gz) < 1.2 || tm > 70 || stuck > 8) { st = EaterState.SEARCH; tm = 0; }
                }
                case SEARCH -> {
                    tm++;
                    pose.jaw = 0.5 + Math.sin(tm * 0.3) * 0.2; pose.ears = 1; pose.lift = 0.5;
                    yaw += Math.sin(tm * 0.15) * 0.04;
                    if (h != null) { alert(h); return; }
                    if (tm >= 50) { st = EaterState.IDLE; tm = 0; }
                }
            }
        }

        void alert(double[] h) {
            st = EaterState.ALERT; tm = 0; gx = h[0]; gz = h[1];
            sound("entity.warden.listening", 1.4F, 0.6F);
            Fx.send(level, Fx.particle("sonic_boom"), x, y + 1, z, 1, 0, 0, 0, 0);
        }

        void bite(ServerPlayer p) {
            hitPlayer(p, 10, 0.8);
            pose.jaw = 0.0;
            sound("entity.generic.eat", 1.6F, 0.5F);
            sound("block.amethyst_block.break", 1.2F, 0.5F);
            st = EaterState.SEARCH; tm = 0;
        }

        /** Куда смотреть: ближайший слышимый игрок или недавний шум, иначе null. */
        double[] hear() {
            double best = Double.MAX_VALUE; double[] res = null;
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                double dx = p.m_20185_() - x, dy = p.m_20186_() - y, dz = p.m_20189_() - z;
                double r = RiftLogic.hearRadius(p.m_20142_(), p.m_6144_(), MOVED.contains(p.m_20148_()));
                if (RiftLogic.hears(dx, dy, dz, r)) { double d = dx * dx + dy * dy + dz * dz; if (d < best) { best = d; res = new double[]{p.m_20185_(), p.m_20189_()}; } }
            }
            for (Noise n : NOISES) {
                if (n.level != level) continue;
                double dx = n.x - x, dy = n.y - y, dz = n.z - z;
                if (RiftLogic.hears(dx, dy, dz, RiftLogic.NOISE_RADIUS)) { double d = dx * dx + dy * dy + dz * dz; if (d < best) { best = d; res = new double[]{n.x, n.z}; } }
            }
            return res;
        }

        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            double k = RiftLogic.eaterDamageMult(st);
            if (k > 1) { ev.setAmount((float) (ev.getAmount() * k)); Fx.send(level, Fx.particle("crit"), x, y + 1.2, z, 12, 0.4, 0.4, 0.4, 0.2); }
            super.hurt(ev, atk);
        }
    }

    // ================================================================ Аметистовый голем
    /** Элита: медленный, щит спереди (урон x0.1), со спины ломаются 3 кристалла; сломаны все — оглушён 2,5 с и раскрыт 13 с (урон x1.2). */
    static final class Golem extends Mob {
        final GolemArmor ar = new GolemArmor();
        int wind = -1, cd = 40; double sx, sz;

        @Override void think() {
            pose.t = t;
            if (ar.tick()) {
                sound("block.amethyst_block.resonate", 2.0F, 0.6F);
                Fx.send(level, VIO, x, y + 2, z, 30, 0.7, 1.0, 0.7, 0.05);
            }
            pose.open += ((ar.open() ? 1 : 0) - pose.open) * 0.12;
            pose.stun += ((ar.stunned() ? 1 : 0) - pose.stun) * 0.2;
            for (int i = 0; i < GolemArmor.CRYSTALS; i++) pose.cr[i] += ((ar.hp[i] > 0 ? 1 : 0) - pose.cr[i]) * 0.25;
            pose.strike = Math.max(0, pose.strike - 0.1);
            if (ar.stunned()) { wind = -1; pose.atk = 0; return; }
            ServerPlayer p = nearest(30, 6);
            if (p == null) return;
            if (wind < 0) {
                face(p.m_20185_(), p.m_20189_(), 0.06);
                if (cd > 0) cd--;
                double d = hd(p.m_20185_(), p.m_20189_());
                double want = Math.atan2(p.m_20185_() - x, p.m_20189_() - z), off = Math.abs(RiftLogic.turn(yaw, want, 10) - yaw);
                if (d < 3.6 && cd == 0 && off < 0.7) {
                    wind = 24; sx = x + Math.sin(yaw) * 1.9; sz = z + Math.cos(yaw) * 1.9;
                    sound("block.amethyst_block.resonate", 2.0F, 0.5F);
                } else if (d > 2.2 && walk(p.m_20185_(), p.m_20189_(), (ar.open() ? 0.075 : 0.065) * speedMult(), 3)) pose.ph += 0.12;
                pose.atk = Math.max(0, pose.atk - 0.1);
                return;
            }
            wind--;
            pose.atk = 1 - wind / 24.0;
            if (wind % 2 == 0) { ring(VIO, sx, y + 0.1, sz, 2.4, 20); ring(WHT, sx, y + 0.1, sz, 1.2, 10); }
            if (wind == 0) { wind = -1; cd = 70; slam(); }
        }

        void slam() {
            pose.atk = 0; pose.strike = 1;
            Fx.send(level, Fx.blockParticle(Fx.state("deepslate")), sx, y + 0.3, sz, 40, 1.2, 0.2, 1.2, 0.2);
            Fx.send(level, Fx.particle("explosion"), sx, y + 0.5, sz, 1, 0, 0, 0, 0);
            Fx.sound(level, sx, y, sz, "entity.generic.explode", 1.6F, 0.6F);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                double dx = p.m_20185_() - sx, dz = p.m_20189_() - sz;
                if (dx * dx + dz * dz < 2.5 * 2.5 && Math.abs(p.m_20186_() - y) < 2.5) hitPlayer(p, 12, 0.9);
            }
        }

        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            int sector = 1;
            if (atk != null) {
                double dx = atk.m_20185_() - x, dz = atk.m_20189_() - z, d = Math.sqrt(dx * dx + dz * dz);
                if (d > 1e-3) sector = GolemArmor.sector((dx * Math.sin(yaw) + dz * Math.cos(yaw)) / d);
            }
            boolean wasOpen = ar.open();
            double m = ar.hit(ev.getAmount(), sector);
            ev.setAmount((float) (ev.getAmount() * m));
            if (!wasOpen && sector == 0) {
                Fx.send(level, Fx.particle("crit"), x + Math.sin(yaw) * 1.0, y + 1.8, z + Math.cos(yaw) * 1.0, 8, 0.4, 0.5, 0.4, 0.2);
                sound("block.amethyst_block.hit", 1.6F, 0.5F);
            }
            if (ar.justBroken >= 0) {
                Fx.send(level, Fx.blockParticle(Fx.state("amethyst_block")), x - Math.sin(yaw) * 0.5, y + 2.2, z - Math.cos(yaw) * 0.5, 30, 0.4, 0.5, 0.4, 0.15);
                sound("block.amethyst_cluster.break", 2.0F, 0.7F);
                if (ar.open()) {
                    sound("entity.warden.sonic_boom", 1.5F, 1.4F);
                    for (ServerPlayer p : ps)
                        if (p.m_6084_() && hd(p.m_20185_(), p.m_20189_()) < 30) p.m_5661_(Component.m_237115_("explosionwand.mob.golem_open"), true);
                }
            }
        }

        @Override void die() {
            super.die();
            Fx.send(level, Fx.blockParticle(Fx.state("deepslate")), x, y + 1.5, z, 60, 0.7, 1.0, 0.7, 0.2);
            sound("entity.generic.explode", 1.4F, 0.7F);
        }
    }
}
