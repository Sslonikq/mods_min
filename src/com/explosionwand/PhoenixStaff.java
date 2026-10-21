package com.explosionwand;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

import java.util.*;

/**
 * Посох Феникса (посох 1-го уровня, добыча Пепельного Феникса).
 *  ПКМ        - «Огненный рывок»: импульс вперёд (скорость, не телепорт: сквозь блоки не проходит, работает и в данжах);
 *               за рывком остаётся огненный след из частиц на 3 секунды, мобы на нём получают урон и горят. Блоков не ставит.
 *  Shift+ПКМ  - показать состояние пассивки.
 *  Пассивка «Из пепла» - пока посох в инвентаре, раз в 10 минут смертельный урон не убивает: вспышка, 4 сердца, отталкивание.
 *               Момент последнего спасения (игровое время) хранится в persistent data игрока под «PlayerPersisted» (переживает смерть).
 */
final class PhoenixStaff {
    static final int DASH_CD = 120;                 // 6 с
    static final int DASH_TICKS = 8;                // сколько тиков после рывка ставим точки следа
    static final int TRAIL_TICKS = 60;              // жизнь точки следа (3 с)
    static final double TRAIL_R = 1.4D;
    static final float DASH_DAMAGE = 3.0F;
    static final int BURN_SECONDS = 4;
    static final long ASH_CD = 12000L;              // 10 минут
    static final float ASH_HP = 8.0F;               // 4 сердца
    static final double ASH_R = 6.0D;
    static final String KEY = "ew_ash_last";

    // ---------------------------------------------------------------- чистая логика (тесты)

    /** Оставшаяся перезарядка пассивки в тиках (0 - готова). last = момент спасения; Long.MIN_VALUE - ещё не спасало. */
    static long remaining(long last, long now) {
        if (last == Long.MIN_VALUE) return 0L;
        long left = last + ASH_CD - now;
        return Math.max(0L, Math.min(ASH_CD, left));      // игровое время "назад" (чужой мир) не даёт больше 10 минут
    }

    /** Спасает ли пассивка: посох в инвентаре, урон смертелен, это не /kill и не бездна, перезарядка прошла. */
    static boolean rescues(boolean hasStaff, String sourceId, float health, float damage, long last, long now) {
        if (!hasStaff || damage < health) return false;
        if ("genericKill".equals(sourceId) || "outOfWorld".equals(sourceId)) return false;
        return remaining(last, now) == 0L;
    }

    /** Скорость рывка: горизонт по взгляду, вертикаль ограничена, чтобы не улетать в небо и не бить в землю. */
    static double[] dashVel(double lx, double ly, double lz) {
        double h = Math.sqrt(lx * lx + lz * lz);
        double hx = h < 1.0E-4 ? 0.0D : lx / h, hz = h < 1.0E-4 ? 0.0D : lz / h;
        double vy = Math.max(-0.2D, Math.min(0.3D, ly * 0.9D + 0.1D));
        return new double[] { hx * 1.5D, vy, hz * 1.5D };
    }

    /** "м:сс" из тиков. */
    static String clock(long ticks) {
        long s = (ticks + 19L) / 20L;
        return (s / 60L) + ":" + String.format(Locale.ROOT, "%02d", s % 60L);
    }

    // ---------------------------------------------------------------- рывок

    /** Точка следа. */
    static final class Pt {
        final double x, y, z; final long until;
        Pt(double x, double y, double z, long until) { this.x = x; this.y = y; this.z = z; this.until = until; }
    }

    static final class Dash {
        final ServerLevel level; final UUID id; final List<Pt> pts = new ArrayList<>(); final Set<Integer> hit = new HashSet<>();
        int left = DASH_TICKS;
        Dash(ServerLevel l, UUID id) { level = l; this.id = id; }
    }

    static final Map<UUID, Dash> DASHES = new HashMap<>();
    static final Set<UUID> WAITING = new HashSet<>();      // ждут сообщения «Из пепла готов» (только в памяти)

    /** Сброс состояния (конец сервера, Dungeons.clearState). */
    static void clear() { DASHES.clear(); WAITING.clear(); }

    /** @return перезарядка рывка в тиках (0 - ничего не вышло) */
    static int use(ServerLevel l, ServerPlayer p) {
        if (p.m_6144_()) { status(l, p); return 0; }
        Vec3 look = p.m_20252_(1.0F);
        double[] v = dashVel(look.f_82479_, look.f_82480_, look.f_82481_);
        p.m_20256_(new Vec3(v[0], v[1], v[2]));
        p.f_19864_ = true;
        p.f_19789_ = 0.0F;
        DASHES.put(p.m_20148_(), new Dash(l, p.m_20148_()));
        Fx.send(l, Fx.particle("flame"), p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_(), 24, 0.3, 0.5, 0.3, 0.12);
        Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.blaze.shoot", 1.0F, 0.8F);
        Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "item.firecharge.use", 1.0F, 1.0F);
        return DASH_CD;
    }

    static void tick(ServerLevel l) {
        long now = l.m_46467_();
        if (!DASHES.isEmpty()) {
            Iterator<Dash> it = DASHES.values().iterator();
            while (it.hasNext()) {
                Dash d = it.next();
                if (d.level != l) continue;
                ServerPlayer p = null;
                for (ServerPlayer q : l.m_6907_()) if (q.m_20148_().equals(d.id)) { p = q; break; }
                if (p == null || !p.m_6084_()) { it.remove(); continue; }     // игрок умер, вышел или сменил измерение: след гаснет
                if (d.left > 0) { d.left--; d.pts.add(new Pt(p.m_20185_(), p.m_20186_(), p.m_20189_(), now + TRAIL_TICKS)); }
                d.pts.removeIf(pt -> pt.until <= now);
                if (d.pts.isEmpty() && d.left <= 0) { it.remove(); continue; }
                boolean burn = now % 4 == 0;
                for (Pt pt : d.pts) {
                    if (now % 2 == 0) {
                        Fx.send(l, Fx.particle("flame"), pt.x, pt.y + 0.3, pt.z, 2, 0.35, 0.15, 0.35, 0.01);
                        if (now % 6 == 0) Fx.send(l, Fx.particle("lava"), pt.x, pt.y + 0.5, pt.z, 1, 0.3, 0.2, 0.3, 0);
                    }
                    if (burn) scorch(l, p, d, pt);
                }
            }
        }
        if (now % 20 == 0 && !WAITING.isEmpty()) tickWaiting(l, now);
    }

    /** Поджечь мобов у точки следа; урон - один раз на моба за рывок. Блоков не трогает. */
    private static void scorch(ServerLevel l, ServerPlayer p, Dash d, Pt pt) {
        AABB box = new AABB(pt.x - TRAIL_R, pt.y - 0.5, pt.z - TRAIL_R, pt.x + TRAIL_R, pt.y + 2.0, pt.z + TRAIL_R);
        for (Entity e : l.m_6249_(p, box, (Entity en) -> Earth.foe(en, p))) {
            if (Minions.bossPart(e)) continue;                         // тела боссов огонь не ставит и не бьёт
            if (!Minions.controllable(e) && !Minions.dungeonMob(e)) continue;
            if (!Minions.dungeonMob(e) && !(e instanceof Enemy)) continue;     // вне данжей - только враждебные
            e.m_20254_(BURN_SECONDS);
            if (d.hit.add(e.m_19879_())) e.m_6469_(l.m_269111_().m_269075_(p), DASH_DAMAGE);
        }
    }

    // ---------------------------------------------------------------- «Из пепла»

    static boolean has(ServerPlayer p) { return p.m_150109_().m_18947_(ExplosionWandMod.PHOENIX_STAFF.get()) > 0; }

    private static CompoundTag saved(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.m_128441_("PlayerPersisted")) root.m_128365_("PlayerPersisted", new CompoundTag());
        return root.m_128469_("PlayerPersisted");
    }

    static long last(ServerPlayer p) {
        CompoundTag t = saved(p);
        return t.m_128441_(KEY) ? t.m_128454_(KEY) : Long.MIN_VALUE;
    }

    /** Хук LivingDamageEvent (урон уже после брони и поглощения): смертельный удар отменяем и поднимаем игрока. */
    static void onDamage(LivingDamageEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || !(p.m_9236_() instanceof ServerLevel l) || p.m_7500_() || p.m_5833_()) return;
        if (!has(p)) return;
        String id = ev.getSource() == null ? null : ev.getSource().m_19385_();
        long now = l.m_46467_();
        if (!rescues(true, id, p.m_21223_(), ev.getAmount(), last(p), now)) return;
        ev.setAmount(0.0F);
        ev.setCanceled(true);
        saved(p).m_128356_(KEY, now);
        WAITING.add(p.m_20148_());
        rebirth(l, p);
    }

    private static void rebirth(ServerLevel l, ServerPlayer p) {
        p.m_21153_(Math.min(ASH_HP, p.m_21233_()));
        p.m_20095_();
        p.f_19789_ = 0.0F;
        MobEffect fire = Fx.effect("fire_resistance"), res = Fx.effect("resistance");
        if (fire != null) p.m_7292_(new MobEffectInstance(fire, 100, 0));
        if (res != null) p.m_7292_(new MobEffectInstance(res, 60, 1));
        double x = p.m_20185_(), y = p.m_20186_(), z = p.m_20189_();
        ParticleOptions flame = Fx.particle("flame");
        Fx.send(l, flame, x, y + 1.0, z, 120, 0.6, 0.9, 0.6, 0.25);
        Fx.send(l, Fx.particle("lava"), x, y + 1.0, z, 30, 0.8, 0.8, 0.8, 0.0);
        Fx.send(l, Fx.particle("explosion"), x, y + 1.0, z, 3, 0.8, 0.5, 0.8, 0.0);
        for (int i = 0; i < 40; i++) {                                 // кольцо пламени по радиусу толчка
            double a = i * (Math.PI * 2 / 40);
            Fx.send(l, flame, x + Math.cos(a) * ASH_R * 0.6, y + 0.3, z + Math.sin(a) * ASH_R * 0.6, 1, 0.1, 0.1, 0.1, 0.02);
        }
        Fx.sound(l, x, y, z, "entity.generic.explode", 1.0F, 1.3F);
        Fx.sound(l, x, y, z, "item.totem.use", 1.0F, 0.8F);
        AABB box = new AABB(x - ASH_R, y - 2.0, z - ASH_R, x + ASH_R, y + 3.0, z + ASH_R);
        for (Entity e : l.m_6249_(p, box, (Entity en) -> Earth.shovable(en, p))) {
            Vec3 c = e.m_20182_();
            double dx = c.f_82479_ - x, dz = c.f_82481_ - z, h = Math.max(0.5D, Math.sqrt(dx * dx + dz * dz));
            if (h > ASH_R) continue;
            double k = 1.4D / h;
            Earth.shove(e, dx * k, 0.5D, dz * k);
            e.m_20254_(3);
        }
        Cmd.run(l, x, y, z, "title " + p.m_36316_().getName() + " actionbar {\"translate\":\"explosionwand.phoenix_staff.rebirth\",\"color\":\"gold\"}");
    }

    /** Состояние пассивки в экшнбар (Shift+ПКМ). */
    static void status(ServerLevel l, ServerPlayer p) {
        long left = remaining(last(p), l.m_46467_());
        String msg = left == 0L ? "{\"translate\":\"explosionwand.phoenix_staff.ready\",\"color\":\"gold\"}"
                : "{\"translate\":\"explosionwand.phoenix_staff.cooldown\",\"with\":[\"" + clock(left) + "\"],\"color\":\"gray\"}";
        Cmd.run(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "title " + p.m_36316_().getName() + " actionbar " + msg);
    }

    /** Раз в секунду: кому спасение восстановилось - тихо сообщить. */
    private static void tickWaiting(ServerLevel l, long now) {
        for (ServerPlayer p : l.m_6907_()) {
            if (!WAITING.contains(p.m_20148_())) continue;
            if (!p.m_6084_()) continue;
            if (remaining(last(p), now) > 0L) continue;
            WAITING.remove(p.m_20148_());
            if (has(p)) {
                Cmd.run(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "title " + p.m_36316_().getName() + " actionbar {\"translate\":\"explosionwand.phoenix_staff.recharged\",\"color\":\"gold\"}");
                Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.fire.ambient", 0.8F, 1.4F);
            }
        }
    }
}
