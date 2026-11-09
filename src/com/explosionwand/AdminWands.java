package com.explosionwand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Тестовые админ-палочки (3.2.0): Палочка Молнии (луч, пробивающий всех, 1000 урона) и Палочка Смерти (мгновенная смерть цели в прицеле,
 * боссов - через их собственную цепочку гибели). Не крафтятся. Игроков не трогают, блоков не ломают.
 * Чистая математика (targetable, rayBox, perp) отделена от мира и покрыта AdminWandsTest.
 */
final class AdminWands {
    static final double RANGE = 64.0D;
    static final float LIGHTNING_DAMAGE = 1000.0F;
    static final int LIGHTNING_CD = 10, DEATH_CD = 10;
    /** Допуск луча по хитбоксу: луч шириной ~0,7 блока, чтобы не промахиваться по тонким мобам. */
    static final double GROW = 0.35D;
    private static final Random RNG = new Random();

    private AdminWands() {}

    // ================================================================ чистая логика
    /** Цель обеих палочек: живое существо, не игрок, не наблюдатель, не стойка (стойки - носители чужих block_display). */
    static boolean targetable(boolean living, boolean player, boolean alive, boolean spectator, boolean armorStand) {
        return living && !player && alive && !spectator && !armorStand;
    }

    /**
     * Пересечение луча (начало o, единичное направление d) с боксом, раздутым на grow. Возвращает расстояние входа вдоль луча
     * (0, если начало внутри) или -1, если мимо или дальше range.
     */
    static double rayBox(double ox, double oy, double oz, double dx, double dy, double dz,
                         double x0, double y0, double z0, double x1, double y1, double z1, double range, double grow) {
        double[] o = {ox, oy, oz}, d = {dx, dy, dz};
        double[] lo = {x0 - grow, y0 - grow, z0 - grow}, hi = {x1 + grow, y1 + grow, z1 + grow};
        double tmin = 0.0D, tmax = range;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-9D) {
                if (o[i] < lo[i] || o[i] > hi[i]) return -1.0D;
            } else {
                double t1 = (lo[i] - o[i]) / d[i], t2 = (hi[i] - o[i]) / d[i];
                if (t1 > t2) { double s = t1; t1 = t2; t2 = s; }
                tmin = Math.max(tmin, t1); tmax = Math.min(tmax, t2);
                if (tmin > tmax) return -1.0D;
            }
        }
        return tmin;
    }

    /** Единичный вектор, перпендикулярный (dx,dy,dz): для смещения узлов зигзага. */
    static double[] perp(double dx, double dy, double dz) {
        double ux = 0, uy = 1, uz = 0;
        if (Math.abs(dy) > 0.95D) { ux = 1; uy = 0; }
        double cx = dy * uz - dz * uy, cy = dz * ux - dx * uz, cz = dx * uy - dy * ux;
        double l = Math.sqrt(cx * cx + cy * cy + cz * cz);
        return new double[]{cx / l, cy / l, cz / l};
    }

    // ================================================================ Палочка Молнии
    /** ПКМ: зигзаг-луч до первого блока (не дальше RANGE), урон всем живым на линии. Возвращает число пораженных. */
    static int lightning(ServerLevel sl, ServerPlayer p) {
        Aim aim = Aim.of(sl, p, RANGE);
        double len = Math.min(RANGE, aim.eye.m_82554_(aim.block.m_82450_()));
        Vec3 eye = aim.eye, dir = aim.look;
        Vec3 end = eye.m_82549_(dir.m_82490_(len));
        AABB box = new AABB(Math.min(eye.f_82479_, end.f_82479_), Math.min(eye.f_82480_, end.f_82480_), Math.min(eye.f_82481_, end.f_82481_),
                Math.max(eye.f_82479_, end.f_82479_), Math.max(eye.f_82480_, end.f_82480_), Math.max(eye.f_82481_, end.f_82481_)).m_82400_(4.0D);
        List<LivingEntity> near = sl.m_6443_(LivingEntity.class, box, e -> targetable(true, e instanceof Player, e.m_6084_(), e.m_5833_(), e instanceof ArmorStand));
        List<double[]> order = new ArrayList<>();       // {расстояние, индекс}
        for (int i = 0; i < near.size(); i++) {
            AABB b = near.get(i).m_20191_();
            double t = rayBox(eye.f_82479_, eye.f_82480_, eye.f_82481_, dir.f_82479_, dir.f_82480_, dir.f_82481_,
                    b.f_82288_, b.f_82289_, b.f_82290_, b.f_82291_, b.f_82292_, b.f_82293_, len, GROW);
            if (t >= 0) order.add(new double[]{t, i});
        }
        order.sort((a, c) -> Double.compare(a[0], c[0]));
        drawBolt(sl, eye, dir, len);
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.lightning_bolt.thunder", 2.0F, 1.6F);
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.lightning_bolt.impact", 2.0F, 1.8F);
        DamageSource src = sl.m_269111_().m_269104_(p, p);
        int n = 0;
        for (double[] o : order) {
            LivingEntity v = near.get((int) o[1]);
            if (!v.m_6084_()) continue;      // уже добит предыдущим попаданием (хитбокс и хост босса)
            v.f_19802_ = 0;                  // кадры неуязвимости не съедают выстрел
            v.m_6469_(src, LIGHTNING_DAMAGE);
            Vec3 at = v.m_20182_();
            double cy = at.f_82480_ + v.m_20206_() / 2.0D;
            Fx.send(sl, Fx.particle("electric_spark"), at.f_82479_, cy, at.f_82481_, 40, 0.5, 0.6, 0.5, 0.4);
            Fx.send(sl, Fx.particle("flash"), at.f_82479_, cy, at.f_82481_, 1, 0, 0, 0, 0);
            Fx.sound(sl, at.f_82479_, at.f_82480_, at.f_82481_, "entity.lightning_bolt.impact", 1.0F, 1.4F);
            n++;
        }
        return n;
    }

    /** Яркий бело-голубой зигзаг: узлы каждые ~2,5 блока со случайным смещением, между узлами плотная линия частиц. */
    static void drawBolt(ServerLevel sl, Vec3 eye, Vec3 dir, double len) {
        double[] pp = perp(dir.f_82479_, dir.f_82480_, dir.f_82481_);
        double[] qq = {dir.f_82480_ * pp[2] - dir.f_82481_ * pp[1], dir.f_82481_ * pp[0] - dir.f_82479_ * pp[2], dir.f_82479_ * pp[1] - dir.f_82480_ * pp[0]};
        double px = eye.f_82479_ + dir.f_82479_ * 1.2D, py = eye.f_82480_ - 0.15D + dir.f_82480_ * 1.2D, pz = eye.f_82481_ + dir.f_82481_ * 1.2D;
        for (double d = 3.7D; ; d += 2.5D) {
            boolean last = d >= len;
            double dd = last ? len : d;
            double a = last ? 0 : (RNG.nextDouble() - 0.5D) * 1.6D, b = last ? 0 : (RNG.nextDouble() - 0.5D) * 1.6D;
            double nx = eye.f_82479_ + dir.f_82479_ * dd + pp[0] * a + qq[0] * b;
            double ny = eye.f_82480_ + dir.f_82480_ * dd + pp[1] * a + qq[1] * b;
            double nz = eye.f_82481_ + dir.f_82481_ * dd + pp[2] * a + qq[2] * b;
            double seg = Math.sqrt((nx - px) * (nx - px) + (ny - py) * (ny - py) + (nz - pz) * (nz - pz));
            int steps = Math.max(1, (int) (seg / 0.35D));
            for (int i = 0; i <= steps; i++) {
                double f = i / (double) steps;
                double x = px + (nx - px) * f, y = py + (ny - py) * f, z = pz + (nz - pz) * f;
                Fx.send(sl, Fx.particle("end_rod"), x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
                if (i % 2 == 0) Fx.send(sl, Fx.particle("electric_spark"), x, y, z, 1, 0.12, 0.12, 0.12, 0.05);
            }
            px = nx; py = ny; pz = nz;
            if (last) break;
        }
        Fx.send(sl, Fx.particle("flash"), px, py, pz, 1, 0, 0, 0, 0);
        Fx.send(sl, Fx.particle("electric_spark"), px, py, pz, 30, 0.6, 0.6, 0.6, 0.5);
    }

    // ================================================================ Палочка Смерти
    /** ПКМ: зелёный луч, существо в прицеле гибнет. Возвращает true, если цель погибла (или начала гибнуть, как босс). */
    static boolean death(ServerLevel sl, ServerPlayer p) {
        Aim aim = Aim.of(sl, p, RANGE);
        aim.beam(sl, Fx.particle("happy_villager"), 0.25D);
        aim.beam(sl, Fx.blockParticle(Fx.state("lime_concrete")), 1.0D);
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.evoker.cast_spell", 2.0F, 0.5F);
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.wither.shoot", 1.0F, 1.6F);
        Entity e = aim.entity;
        if (!(e instanceof LivingEntity le) || !targetable(true, e instanceof Player, e.m_6084_(), e.m_5833_(), e instanceof ArmorStand)) return false;
        Vec3 at = le.m_20182_();
        double y = at.f_82480_ + le.m_20206_() / 2.0D;
        Fx.send(sl, Fx.particle("sneeze"), at.f_82479_, y, at.f_82481_, 40, 0.5, 0.6, 0.5, 0.15);
        Fx.send(sl, Fx.particle("happy_villager"), at.f_82479_, y, at.f_82481_, 60, 0.6, 0.8, 0.6, 0.1);
        Fx.sound(sl, at.f_82479_, at.f_82480_, at.f_82481_, "entity.wither.hurt", 1.5F, 0.6F);
        return kill(sl, le);
    }

    /** Убить существо. Боссов завершает их собственная логика (фазы, добыча, арена); прочих - genericKill в обход тотема и брони. */
    static boolean kill(ServerLevel sl, LivingEntity e) {
        int boss = finishBoss(e);
        if (boss == 1) return true;
        if (boss == 2) return false;       // босс уже умирает, или его часть, не привязанная к бою: не трогаем
        DamageSource ks = sl.m_269111_().m_287172_();
        e.f_19802_ = 0;
        e.m_6469_(ks, Float.MAX_VALUE);
        if (e.m_6084_()) { e.m_6667_(ks); e.m_21153_(0.0F); }       // обработчик отменил урон: умираем принудительно
        return true;
    }

    /** 0 - не босс; 1 - гибель босса запущена; 2 - часть босса, но запустить гибель нельзя (уже умирает, хоста нет в бое). */
    static int finishBoss(Entity e) {
        for (Boss b : Boss.BOSSES.values()) if (b.host == e) return b.adminKill() ? 1 : 2;
        for (Boss3 b : Boss3.BOSSES.values()) if (b.host == e) return b.adminKill() ? 1 : 2;
        for (Boss5 b : Boss5.BOSSES.values()) if (b.host == e) return b.adminKill() ? 1 : 2;
        Boss2 b2 = Boss2.ACTIVE;
        if (b2 != null && b2.host == e) return b2.adminKill() ? 1 : 2;
        Boss4 b4 = Boss4.ACTIVE;
        if (b4 != null && (b4.host == e || (e instanceof LivingEntity le && b4.hits.contains(le)))) return b4.adminKill() ? 1 : 2;
        return Minions.bossPart(e) ? 2 : 0;
    }
}
