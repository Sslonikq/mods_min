package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.*;

/**
 * Мобы Города Спящих (фаза 4.6, часть A): Лунатик, Дремотник, Страж Шва, Ночная моль.
 * Каркас общий с мобами Разлома (RiftMobs): невидимый хост-NoAI + пассажиры-block_display, теги ewd/зал, реестр, sweep, перезапуск, уборка.
 * Здесь только поведение; облик — {@link CityModels}, чистые правила — {@link CityLogic}.
 *
 * Хуки для палочек (появятся в 4.11): {@link #registerLight} — висящий Огонёк (моли слетаются к нему и гасят, Лунатики от него просыпаются),
 * {@link #gust} — порыв Ветра (рядом моли разгоняются). Сейчас в игре этих палочек нет, хуки вызывать некому.
 */
final class CityMobs {
    private CityMobs() {}

    static ParticleOptions TEAL, PALE;

    static void init() {
        if (TEAL != null) return;
        TEAL = new DustParticleOptions(new Vector3f(0.2F, 0.9F, 0.85F), 0.7F);
        PALE = new DustParticleOptions(new Vector3f(0.75F, 0.95F, 1.0F), 0.7F);
    }

    // ================================================================ хуки: огоньки и порывы
    /** Висящий огонёк палочки: освещает, пока не истечёт срок или его не погасит моль. */
    static final class Light {
        final ServerLevel level; final double x, y, z, radius; final long until; boolean snuffed;
        Light(ServerLevel level, double x, double y, double z, double radius, long until) { this.level = level; this.x = x; this.y = y; this.z = z; this.radius = radius; this.until = until; }
        boolean alive() { return !snuffed && RiftMobs.now < until; }
        void snuff() { snuffed = true; }
    }

    static final class Gust {
        final ServerLevel level; final double x, y, z; final long until;
        Gust(ServerLevel level, double x, double y, double z, long until) { this.level = level; this.x = x; this.y = y; this.z = z; this.until = until; }
    }

    static final List<Light> LIGHTS = new ArrayList<>();
    static final List<Gust> GUSTS = new ArrayList<>();

    /** Зарегистрировать огонёк на ticks тиков (время мобов: RiftMobs.now). Возвращает ручку: {@link Light#alive()} покажет, жив ли он. */
    static Light registerLight(ServerLevel level, double x, double y, double z, double radius, int ticks) {
        LIGHTS.removeIf(l -> !l.alive());
        Light l = new Light(level, x, y, z, radius, RiftMobs.now + ticks);
        if (LIGHTS.size() < 256) LIGHTS.add(l);
        return l;
    }

    /** Порыв Ветра в точке: моли в {@link CityLogic#GUST_RADIUS} разгоняются на {@link CityLogic#GUST_TICKS} тиков. */
    static void gust(ServerLevel level, double x, double y, double z) {
        GUSTS.removeIf(g -> RiftMobs.now >= g.until);
        if (GUSTS.size() < 64) GUSTS.add(new Gust(level, x, y, z, RiftMobs.now + CityLogic.GUST_TICKS));
    }

    static Light nearestLight(ServerLevel level, double x, double y, double z, double range) {
        Light best = null; double bd = range * range;
        for (Light l : LIGHTS) {
            if (l.level != level || !l.alive()) continue;
            double dx = l.x - x, dy = l.y - y, dz = l.z - z, d = dx * dx + dy * dy + dz * dz;
            if (d <= bd) { bd = d; best = l; }
        }
        return best;
    }

    static boolean gustNear(ServerLevel level, double x, double y, double z) {
        for (Gust g : GUSTS) {
            if (g.level != level || RiftMobs.now >= g.until) continue;
            double dx = g.x - x, dy = g.y - y, dz = g.z - z;
            if (dx * dx + dy * dy + dz * dz <= CityLogic.GUST_RADIUS * CityLogic.GUST_RADIUS) return true;
        }
        return false;
    }

    static void clear() { LIGHTS.clear(); GUSTS.clear(); LIGHT_ITEMS = null; }

    // ================================================================ свет в руке
    private static Set<Object> LIGHT_ITEMS;

    static boolean holdsLight(ServerPlayer p) {
        if (LIGHT_ITEMS == null) {
            LIGHT_ITEMS = new HashSet<>();
            for (String id : CityLogic.LIGHT_ITEMS) {
                Object it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", id));
                if (it != null) LIGHT_ITEMS.add(it);
            }
        }
        for (ItemStack s : new ItemStack[]{p.m_21205_(), p.m_21206_()})
            if (s != null && !s.m_41619_() && LIGHT_ITEMS.contains(s.m_41720_())) return true;
        return false;
    }

    static void effect(ServerLevel level, ServerPlayer p, String id, int ticks, int amp) {
        if (ArmorSets.sleepImmune(p)) return;   // Облачение Сновидца, 4 части: усыпление и замедление Города не действуют
        give(level, p, id, ticks, amp);
    }

    /** Эффект без проверки иммунитета Сновидца (слепота укуса Ночной моли действует и в полном сете). */
    static void give(ServerLevel level, ServerPlayer p, String id, int ticks, int amp) {
        Cmd.run(level, p.m_20185_(), p.m_20186_(), p.m_20189_(), "effect give " + p.m_20148_() + " minecraft:" + id + " " + Math.max(1, ticks / 20) + " " + amp + " true");
    }

    // ================================================================ Лунатик
    /** Спит с вытянутыми руками и безобиден. Будят: удар, свет в руке, бег рядом, шум боя, проснувшийся сосед. Проснувшийся бешеный, пока рядом кто-то есть. */
    static final class Lunatic extends RiftMobs.Mob {
        boolean awake = false;
        int idle = 0, wind = 0, cd = 0, wanderT = 0;
        double wx, wz;

        @Override void think() {
            init();
            pose.t = t;
            pose.wake += ((awake ? 1 : 0) - pose.wake) * 0.2;
            if (!awake) { sleepStep(); return; }
            rage();
        }

        void sleepStep() {
            pose.atk = 0;
            if (t % 2 == 0) checkWake();
            if (awake) return;
            if (wanderT > 0) { wanderT--; face(wx, wz, 0.05); if (walk(wx, wz, 0.035, 2)) pose.ph += 0.25; }
            else if (RiftMobs.RNG.nextInt(90) == 0) { wx = hx + (RiftMobs.RNG.nextDouble() - 0.5) * 10; wz = hz + (RiftMobs.RNG.nextDouble() - 0.5) * 10; wanderT = 60; }
        }

        void checkWake() {
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_() || Math.abs(p.m_20186_() - y) > 3) continue;
                double d = hd(p.m_20185_(), p.m_20189_());
                if (CityLogic.stepWakes(p.m_6144_(), p.m_20142_(), RiftMobs.MOVED.contains(p.m_20148_()), d)) { wake(false); return; }
                if (d <= CityLogic.WAKE_LIGHT && CityLogic.lightWakes(holdsLight(p), d)) { wake(false); return; }
            }
            Light l = nearestLight(level, x, y, z, CityLogic.WAKE_LIGHT);
            if (l != null) { wake(false); return; }
            for (RiftMobs.Noise n : RiftMobs.NOISES) {
                if (n.level != level) continue;
                double dx = n.x - x, dy = n.y - y, dz = n.z - z;
                if (CityLogic.noiseWakes(Math.sqrt(dx * dx + dy * dy + dz * dz))) { wake(false); return; }
            }
            for (RiftMobs.Mob m : RiftMobs.MOBS.values()) {
                if (m == this || m.level != level || !(m instanceof Lunatic o)) continue;
                double dx = o.x - x, dy = o.y - y, dz = o.z - z;
                if (CityLogic.chainWakes(o.awake, Math.sqrt(dx * dx + dy * dy + dz * dz))) { wake(true); return; }
            }
        }

        void wake(boolean chain) {
            if (awake) return;
            awake = true; idle = 0; wind = 0; cd = chain ? 14 : 8;
            sound("entity.vex.charge", 1.1F, 0.6F);
            sound("block.amethyst_block.resonate", 0.9F, 0.5F);
            Fx.send(level, TEAL, x, y + 1.7, z, 14, 0.25, 0.2, 0.25, 0.05);
        }

        void sleep() {
            awake = false; wind = 0;
            sound("block.amethyst_block.chime", 0.7F, 0.5F);
            Fx.send(level, PALE, x, y + 1.7, z, 8, 0.25, 0.2, 0.25, 0.02);
        }

        void rage() {
            ServerPlayer p = notice(24, 6);
            if (p == null) idle++; else idle = 0;
            if (CityLogic.calmsDown(idle)) { sleep(); return; }
            if (wind > 0) {
                pose.atk = 1 - wind / 10.0;
                if (--wind == 0) strike();
                return;
            }
            pose.atk = Math.max(0, pose.atk - 0.15);
            if (cd > 0) cd--;
            if (p == null) return;
            face(p.m_20185_(), p.m_20189_(), 0.3);
            double d = hd(p.m_20185_(), p.m_20189_());
            if (d < 1.9 && cd == 0 && Math.abs(p.m_20186_() - y) < 2.2) { wind = 10; sound("entity.zombie.attack_wooden_door", 0.6F, 1.6F); return; }
            if (d > 1.2 && walk(p.m_20185_(), p.m_20189_(), 0.21 * speedMult(), 2)) pose.ph += 0.9;
        }

        void strike() {
            cd = 20; pose.atk = 0;
            for (ServerPlayer p : ps)
                if (p.m_6084_() && !p.m_5833_() && hd(p.m_20185_(), p.m_20189_()) < 2.3 && Math.abs(p.m_20186_() - y) < 2.2) hitPlayer(p, CityLogic.LUNATIC_HIT, 0.25);
            sound("entity.player.attack.knockback", 0.9F, 1.3F);
        }

        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            if (!awake) {
                ev.setAmount((float) (ev.getAmount() * CityLogic.sleeperHitMult(true)));
                wake(false);
            }
            super.hurt(ev, atk);
        }

        @Override void die() {
            Fx.send(level, TEAL, x, y + 0.9, z, 25, 0.3, 0.5, 0.3, 0.1);
            Fx.send(level, PALE, x, y + 1.2, z, 12, 0.3, 0.4, 0.3, 0.05);
            sound("block.amethyst_block.chime", 1.0F, 0.6F);
        }
    }

    // ================================================================ Дремотник
    /** Парит над игроком, держит эхо-осколок: луч дремоты (прицел 1,5 с) замедляет, облако дремоты вокруг тоже. */
    static final class Drowsy extends RiftMobs.Mob {
        double ang = RiftMobs.RNG.nextDouble() * Math.PI * 2, lx, ly, lz;
        int cd = 50 + RiftMobs.RNG.nextInt(40), aim = -1;

        @Override void think() {
            init();
            pose.t = t;
            if (t % 20 == 0) aura();
            ServerPlayer p = notice(22, 8);
            double tx, tz, ty = hy + 2.5;
            if (p != null) { ang += 0.02; tx = p.m_20185_() + Math.cos(ang) * 5; tz = p.m_20189_() + Math.sin(ang) * 5; ty = Math.max(ty, p.m_20186_() + 3); }
            else { ang += 0.012; tx = hx + Math.cos(ang) * 3; tz = hz + Math.sin(ang) * 3; }
            while (ty > hy + 1 && !(air(tx, ty, tz) && air(tx, ty + 1, tz))) ty -= 1;
            if (aim < 0) fly(tx, ty, tz, 0.08 * speedMult());
            if (p != null) face(p.m_20185_(), p.m_20189_(), aim < 0 ? 0.15 : 0.3);
            if (aim < 0) {
                pose.charge = Math.max(0, pose.charge - 0.1);
                if (cd > 0) cd--;
                if (cd == 0 && p != null && hd(p.m_20185_(), p.m_20189_()) < 20 && clear(x, y + 0.7, z, p.m_20185_(), p.m_20186_() + 1.2, p.m_20189_())) {
                    aim = CityLogic.RAY_AIM; lx = p.m_20185_(); ly = p.m_20186_() + 1.0; lz = p.m_20189_();
                    sound("block.amethyst_block.chime", 1.4F, 0.7F);
                }
                return;
            }
            aim--;
            pose.charge = 1 - aim / (double) CityLogic.RAY_AIM;
            face(lx, lz, 0.3);
            double[] tip = tip(), dir = dir(tip), end = rayEnd(tip[0], tip[1], tip[2], dir[0], dir[1], dir[2], CityLogic.RAY_LENGTH);
            if (aim % 2 == 0) line(TEAL, tip[0], tip[1], tip[2], end[0], end[1], end[2], 1.5);
            if (aim == 14) sound("block.amethyst_block.chime", 1.2F, 1.3F);
            if (aim == 0) { aim = -1; pose.charge = 0; cd = 80 + RiftMobs.RNG.nextInt(40); fire(tip, end); }
        }

        double[] tip() { return new double[]{x + Math.sin(yaw) * 0.5, y + 0.7, z + Math.cos(yaw) * 0.5}; }

        double[] dir(double[] tip) {
            double dx = lx - tip[0], dy = ly - tip[1], dz = lz - tip[2], l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
            return new double[]{dx / l, dy / l, dz / l};
        }

        void fire(double[] tip, double[] end) {
            for (int i = 0; i <= 20; i++) {
                double u = i / 20.0;
                Fx.send(level, Fx.particle("end_rod"), tip[0] + (end[0] - tip[0]) * u, tip[1] + (end[1] - tip[1]) * u, tip[2] + (end[2] - tip[2]) * u, 1, 0, 0, 0, 0);
            }
            sound("block.amethyst_cluster.break", 1.4F, 1.5F);
            sound("entity.evoker.cast_spell", 1.0F, 0.7F);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                if (RiftLogic.segDist(tip[0], tip[1], tip[2], end[0], end[1], end[2], p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_()) < 0.9) {
                    if (!hitBeam(p, CityLogic.DROWSY_RAY, 0.1)) continue;   // «Грань»: отражённый луч не усыпляет
                    effect(level, p, "slowness", CityLogic.RAY_SLOW_TICKS, CityLogic.RAY_SLOW_AMP);
                    Fx.send(level, TEAL, p.m_20185_(), p.m_20186_() + 1, p.m_20189_(), 15, 0.3, 0.4, 0.3, 0.1);
                }
            }
        }

        /** Облако дремоты: раз в секунду видимое кольцо, внутри которого игроки замедлены. */
        void aura() {
            ring(PALE, x, y + 0.1, z, CityLogic.AURA_RADIUS, 24);
            Fx.send(level, TEAL, x, y + 0.3, z, 6, 0.5, 0.4, 0.5, 0.01);
            for (ServerPlayer p : ps)
                if (p.m_6084_() && !p.m_5833_() && hd(p.m_20185_(), p.m_20189_()) <= CityLogic.AURA_RADIUS && Math.abs(p.m_20186_() - y) < 3)
                    effect(level, p, "slowness", CityLogic.AURA_SLOW_TICKS, CityLogic.AURA_SLOW_AMP);
        }

        @Override void die() {
            Fx.send(level, TEAL, x, y + 0.5, z, 25, 0.3, 0.4, 0.3, 0.12);
            Fx.send(level, Fx.particle("end_rod"), x, y + 0.5, z, 10, 0.3, 0.3, 0.3, 0.05);
            sound("block.amethyst_block.break", 1.0F, 1.4F);
        }
    }

    // ================================================================ Страж Шва
    /** Мечник: щит спереди гасит удар (шов на спине открыт), взмах телеграфом 0,6 с; каждый 4-й удар — выпад с рывком и длинной линией-телеграфом. */
    static final class StitchGuard extends RiftMobs.Mob {
        int wind = 0, windType = 0, cd = 20, swing = 0, dash = 0, recover = 0;
        double dx, dz;
        boolean dashHit = false;

        boolean guardUp() { return wind == 0 && dash == 0 && recover == 0; }

        @Override void think() {
            init();
            pose.t = t;
            pose.lift += ((guardUp() ? 0 : 1) - pose.lift) * 0.25;
            pose.strike = Math.max(0, pose.strike - 0.1);
            if (recover > 0) recover--;
            if (dash > 0) { dashStep(); return; }
            if (wind > 0) { windStep(); return; }
            pose.atk = Math.max(0, pose.atk - 0.15);
            pose.charge = Math.max(0, pose.charge - 0.1);
            ServerPlayer p = notice(26, 5);
            if (p == null) return;
            if (cd > 0) cd--;
            face(p.m_20185_(), p.m_20189_(), recover > 0 ? 0.08 : 0.25);
            double d = hd(p.m_20185_(), p.m_20189_());
            boolean level_ = Math.abs(p.m_20186_() - y) < 2.2;
            if (recover == 0 && cd == 0 && level_) {
                if (CityLogic.isLunge(swing + 1)) {
                    if (d <= CityLogic.LUNGE_RANGE) { beginWind(2, p); return; }
                } else if (d < 2.3) { beginWind(1, p); return; }
            }
            if (d > 1.8 && recover == 0 && walk(p.m_20185_(), p.m_20189_(), 0.15 * speedMult(), 2)) pose.ph += 0.6;
        }

        void beginWind(int type, ServerPlayer p) {
            swing++; windType = type;
            wind = type == 2 ? CityLogic.LUNGE_WIND : CityLogic.SLASH_WIND;
            double ddx = p.m_20185_() - x, ddz = p.m_20189_() - z, l = Math.max(0.3, Math.sqrt(ddx * ddx + ddz * ddz));
            dx = ddx / l; dz = ddz / l;
            yaw = Math.atan2(dx, dz);
            sound(type == 2 ? "entity.iron_golem.attack" : "item.armor.equip_chain", 1.0F, type == 2 ? 0.7F : 1.2F);
        }

        void windStep() {
            int tot = windType == 2 ? CityLogic.LUNGE_WIND : CityLogic.SLASH_WIND;
            double f = 1 - wind / (double) tot;
            if (windType == 1) {
                pose.atk = f;
                if (wind % 2 == 0) arc();
            } else {
                pose.charge = f;
                if (wind % 2 == 0) lane();
                if (wind == tot / 2) sound("block.respawn_anchor.charge", 1.2F, 1.4F);
            }
            if (--wind == 0) {
                if (windType == 1) slash();
                else { dash = CityLogic.LUNGE_DASH; dashHit = false; sound("entity.player.attack.sweep", 1.2F, 0.6F); }
            }
        }

        void arc() {
            for (int i = -3; i <= 3; i++) {
                double a = yaw + i * 0.26;
                dust(TEAL, x + Math.sin(a) * 2.0, y + 1.0, z + Math.cos(a) * 2.0);
            }
        }

        /** Полоса выпада: линия частиц вперёд на длину рывка. */
        void lane() { line(TEAL, x, y + 0.15, z, x + dx * (CityLogic.LUNGE_RANGE + 0.5), y + 0.15, z + dz * (CityLogic.LUNGE_RANGE + 0.5), 0.8); }

        void slash() {
            pose.atk = 0; pose.strike = 1; recover = 12; cd = 24;
            sound("entity.player.attack.sweep", 1.2F, 0.8F);
            double fx = Math.sin(yaw), fz = Math.cos(yaw);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                double px = p.m_20185_() - x, pz = p.m_20189_() - z, d = Math.sqrt(px * px + pz * pz);
                if (d < CityLogic.SLASH_REACH && Math.abs(p.m_20186_() - y) < 2.5 && (d < 0.4 || (px * fx + pz * fz) / d > 0.1)) hitPlayer(p, CityLogic.GUARD_SLASH, 0.45);
            }
            for (int i = -3; i <= 3; i++) {
                double a = yaw + i * 0.26;
                Fx.send(level, Fx.particle("crit"), x + Math.sin(a) * 1.8, y + 1.0, z + Math.cos(a) * 1.8, 3, 0.1, 0.2, 0.1, 0.1);
            }
        }

        void dashStep() {
            pose.strike = 1; pose.charge = 0;
            walk(x + dx * 3, z + dz * 3, 0.8, 2);
            for (ServerPlayer p : ps) {
                if (dashHit || !p.m_6084_() || p.m_5833_()) continue;
                if (hd(p.m_20185_(), p.m_20189_()) < 1.7 && Math.abs(p.m_20186_() - y) < 2.2) {
                    dashHit = true;
                    hitPlayer(p, CityLogic.GUARD_LUNGE, 0.7);
                    sound("entity.iron_golem.damage", 1.2F, 0.8F);
                }
            }
            Fx.send(level, TEAL, x, y + 0.9, z, 5, 0.3, 0.5, 0.3, 0.02);
            if (--dash == 0 || dashHit) { dash = 0; recover = 18; cd = 40; }
        }

        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            double cos = 1;
            if (atk != null) {
                double ax = atk.m_20185_() - x, az = atk.m_20189_() - z, d = Math.sqrt(ax * ax + az * az);
                if (d > 1e-3) cos = (ax * Math.sin(yaw) + az * Math.cos(yaw)) / d;
            }
            double m = CityLogic.shieldMult(cos, guardUp());
            ev.setAmount((float) (ev.getAmount() * m));
            if (m < 1) {
                Fx.send(level, PALE, x + Math.sin(yaw) * 0.7, y + 1.2, z + Math.cos(yaw) * 0.7, 8, 0.3, 0.4, 0.3, 0.02);
                sound("item.shield.block", 1.2F, 1.0F);
            } else {
                super.hurt(ev, atk);
                if (m > 1) Fx.send(level, TEAL, x - Math.sin(yaw) * 0.4, y + 1.2, z - Math.cos(yaw) * 0.4, 10, 0.2, 0.4, 0.2, 0.05);
            }
        }

        @Override void die() {
            Fx.send(level, TEAL, x, y + 1.0, z, 30, 0.4, 0.6, 0.4, 0.15);
            Fx.send(level, Fx.blockParticle(Fx.state("prismarine_bricks")), x, y + 1.0, z, 25, 0.4, 0.6, 0.4, 0.1);
            sound("entity.iron_golem.death", 1.0F, 1.2F);
        }
    }

    // ================================================================ Ночная моль
    /** Рой мотыльков: слетается на огоньки и гасит их; к игроку стягивается (телеграф 0,8 с), потом ослепляет; Ветер рядом разгоняет рой. */
    static final class Moth extends RiftMobs.Mob {
        int bite = -1, cd = 30;
        long boostUntil = 0;
        double ang = RiftMobs.RNG.nextDouble() * Math.PI * 2;

        @Override void think() {
            init();
            pose.t = t;
            if (cd > 0) cd--;
            if (gustNear(level, x, y, z)) boostUntil = RiftMobs.now + CityLogic.GUST_TICKS;
            boolean windy = RiftMobs.now < boostUntil;
            double sp = CityLogic.mothSpeed(windy) * speedMult();
            if (windy && t % 4 == 0) Fx.send(level, PALE, x, y + 0.5, z, 3, 0.6, 0.4, 0.6, 0.05);
            Light l = nearestLight(level, x, y, z, CityLogic.MOTH_SEEK_LIGHT);
            if (l != null) { bite = -1; pose.atk = Math.max(0, pose.atk - 0.1); seekLight(l, sp); return; }
            ServerPlayer p = notice(22, 8);
            if (bite >= 0) { biteStep(p); return; }
            pose.atk = Math.max(0, pose.atk - 0.08);
            if (p == null) { wander(sp * 0.5); return; }
            double d = hd(p.m_20185_(), p.m_20189_());
            face(p.m_20185_(), p.m_20189_(), 0.2);
            if (d < 3.0 && cd == 0 && Math.abs(p.m_20186_() - y) < 3) {
                bite = CityLogic.MOTH_WIND;
                sound("entity.bee.loop_aggressive", 0.9F, 0.6F);
                return;
            }
            if (d > 1.5) fly(p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_(), sp);
        }

        void seekLight(Light l, double sp) {
            double dx = l.x - x, dy = l.y - y, dz = l.z - z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (CityLogic.snuffs(d)) {
                l.snuff();
                Fx.send(level, Fx.particle("smoke"), l.x, l.y, l.z, 14, 0.2, 0.2, 0.2, 0.03);
                Fx.sound(level, l.x, l.y, l.z, "block.fire.extinguish", 1.0F, 1.2F);
                return;
            }
            fly(l.x, l.y, l.z, sp);
            face(l.x, l.z, 0.2);
        }

        void biteStep(ServerPlayer p) {
            pose.atk = 1 - bite / (double) CityLogic.MOTH_WIND;
            if (bite % 2 == 0) ring(PALE, x, y + 0.3, z, CityLogic.MOTH_BITE_R, 16);
            if (--bite > 0) return;
            bite = -1; cd = CityLogic.MOTH_CD;
            for (ServerPlayer q : ps) {
                if (!q.m_6084_() || q.m_5833_() || hd(q.m_20185_(), q.m_20189_()) > CityLogic.MOTH_BITE_R || Math.abs(q.m_20186_() + 1.0 - y) > 3) continue;
                hitPlayer(q, CityLogic.MOTH_HIT, 0.2);
                give(level, q, "blindness", CityLogic.MOTH_BLIND_TICKS, 0);   // не усыпление: Облачение Сновидца не защищает
                Fx.send(level, PALE, q.m_20185_(), q.m_20186_() + 1.4, q.m_20189_(), 14, 0.3, 0.3, 0.3, 0.05);
            }
            sound("entity.bee.sting", 0.9F, 0.7F);
        }

        void wander(double sp) {
            ang += 0.015;
            double tx = hx + Math.cos(ang) * 3, tz = hz + Math.sin(ang) * 3, ty = hy + 1.8;
            while (ty > hy + 0.5 && !(air(tx, ty, tz) && air(tx, ty + 1, tz))) ty -= 0.5;
            fly(tx, ty, tz, sp);
        }

        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            pose.atk = Math.max(pose.atk, 0.5);
            super.hurt(ev, atk);
        }

        @Override void die() {
            Fx.send(level, PALE, x, y + 0.6, z, 25, 0.5, 0.4, 0.5, 0.08);
            Fx.send(level, TEAL, x, y + 0.6, z, 12, 0.5, 0.4, 0.5, 0.05);
            sound("entity.bee.death", 0.9F, 1.6F);
        }
    }
}
