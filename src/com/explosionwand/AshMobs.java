package com.explosionwand;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.joml.Vector3f;

import java.util.*;

/**
 * Мобы Пепельного святилища, часть A (4.9): Пеплоход, Искра, Пепельная гончая, Звонарь + общие хуки (горящий пепел, тик мира).
 * Каркас общий с RiftMobs (хост-NoAI + block_display), облик - AshModels, правила - AshLogic. Часть B (червь, жрец, стражи, огонёк) - AshMobsB.
 * Мобы данжа блоков не ставят и не ломают: только урон игрокам, частицы и звуки.
 */
final class AshMobs {
    private AshMobs() {}

    static ParticleOptions EMBER, ASH, GOLD, GREEN;

    static void init() {
        if (EMBER != null) return;
        EMBER = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.1F), 0.7F);
        ASH = new DustParticleOptions(new Vector3f(0.45F, 0.42F, 0.4F), 0.7F);
        GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.3F), 0.7F);
        GREEN = new DustParticleOptions(new Vector3f(0.4F, 1.0F, 0.4F), 0.7F);
    }

    // ================================================================ горящий пепел (след Пеплохода)
    static final class Patch {
        final ServerLevel level; final double x, y, z; final long until;
        Patch(ServerLevel level, double x, double y, double z, long until) { this.level = level; this.x = x; this.y = y; this.z = z; this.until = until; }
    }

    static final List<Patch> PATCHES = new ArrayList<>();

    static void clear() { PATCHES.clear(); AshMobsB.clear(); }

    static void addPatch(ServerLevel level, double x, double y, double z) {
        if (PATCHES.size() < 64) PATCHES.add(new Patch(level, x, y, z, RiftMobs.now + AshLogic.PATCH_TICKS));
    }

    /** Тик мира (из RiftMobs.tick): горящий пепел и огоньки. */
    static void tick(ServerLevel sl, DungeonData data, List<ServerPlayer> ps, long now) {
        if (!PATCHES.isEmpty()) {
            PATCHES.removeIf(p -> now >= p.until);
            for (Patch pt : PATCHES) {
                if (pt.level != sl) continue;
                init();
                if (now % 4 == 0) {
                    Fx.send(sl, Fx.particle("flame"), pt.x, pt.y + 0.1, pt.z, 3, AshLogic.PATCH_R * 0.5, 0.05, AshLogic.PATCH_R * 0.5, 0.01);
                    Fx.send(sl, ASH, pt.x, pt.y + 0.2, pt.z, 2, AshLogic.PATCH_R * 0.5, 0.1, AshLogic.PATCH_R * 0.5, 0.01);
                }
                if (now % AshLogic.PATCH_EVERY != 0) continue;
                for (ServerPlayer p : ps) {
                    if (!p.m_6084_() || p.m_5833_() || p.m_7500_()) continue;
                    if (!AshLogic.inPatch(p.m_20185_() - pt.x, p.m_20186_() - pt.y, p.m_20189_() - pt.z)) continue;
                    Cmd.run(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "damage " + p.m_20148_() + " " + AshLogic.PATCH_DMG + " minecraft:in_fire");
                    p.m_20254_(2);
                }
            }
        }
        AshMobsB.tickWorld(sl, data, ps, now);
    }

    static void bar(ServerPlayer p, String key) { p.m_5661_(Component.m_237115_(key), true); }

    /** Толчок игрока от точки (ox, oz): горизонтально kb, вверх up. */
    static void kick(ServerPlayer p, double ox, double oz, double kb, double up) {
        double dx = p.m_20185_() - ox, dz = p.m_20189_() - oz, l = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
        p.m_20256_(new Vec3(dx / l * kb, up, dz / l * kb));
        p.f_19864_ = true;
    }

    // ================================================================ база: срок жизни (мобы-приспешники ловушки)
    abstract static class AMob extends RiftMobs.Mob {
        /** Тик RiftMobs.now, когда моб растворяется сам (0 - не растворяется). */
        long expire = 0;

        @Override final void think() {
            init();
            pose.t = t;
            if (expire > 0 && RiftMobs.now >= expire) { RiftMobs.retire(this); return; }
            act();
        }

        abstract void act();

        /** Игрок на расстоянии dist по горизонтали и не выше dy по вертикали, живой, не наблюдатель. */
        boolean hittable(ServerPlayer p, double reach, double dy) {
            return p.m_6084_() && !p.m_5833_() && hd(p.m_20185_(), p.m_20189_()) < reach && Math.abs(p.m_20186_() - y) < dy;
        }

        /** Попал ли игрок в конус перед мобом (cos угла к взгляду больше c). */
        boolean inFront(ServerPlayer p, double c) {
            double px = p.m_20185_() - x, pz = p.m_20189_() - z, d = Math.sqrt(px * px + pz * pz);
            return d < 0.4 || (px * Math.sin(yaw) + pz * Math.cos(yaw)) / d > c;
        }

        /** Дуга-телеграф перед мобом радиуса r (7 точек). */
        void arc(double r, ParticleOptions o) {
            for (int i = -3; i <= 3; i++) { double a = yaw + i * 0.3; dust(o, x + Math.sin(a) * r, y + 0.15, z + Math.cos(a) * r); }
        }
    }

    // ================================================================ Пеплоход
    /** Медленный и тяжёлый: замах 0,9 с (дуга на полу, рука над головой), удар 8 по конусу вперёд; после смерти оставляет горящий пепел (6 с). */
    static final class Walker extends AMob {
        int wind = 0, cd = 20;

        @Override void act() {
            pose.strike = Math.max(0, pose.strike - 0.1);
            if (wind > 0) {
                pose.atk = 1 - wind / (double) AshLogic.WALKER_WIND;
                ServerPlayer q = nearest(6, 4);
                if (q != null) face(q.m_20185_(), q.m_20189_(), 0.05);
                if (wind % 3 == 0) arc(AshLogic.WALKER_REACH, EMBER);
                if (--wind == 0) strike();
                return;
            }
            pose.atk = Math.max(0, pose.atk - 0.12);
            if (cd > 0) cd--;
            ServerPlayer p = notice(22, 5);
            if (p == null) return;
            face(p.m_20185_(), p.m_20189_(), 0.18);
            double d = hd(p.m_20185_(), p.m_20189_());
            if (d < 2.4 && cd == 0 && Math.abs(p.m_20186_() - y) < 2.2) { wind = AshLogic.WALKER_WIND; sound("entity.zombie.attack_iron_door", 0.9F, 0.6F); return; }
            if (d > 1.5 && walk(p.m_20185_(), p.m_20189_(), AshLogic.WALKER_SPEED * speedMult(), 2)) pose.ph += 0.5;
        }

        void strike() {
            cd = AshLogic.WALKER_CD; pose.atk = 0; pose.strike = 1;
            for (ServerPlayer p : ps) if (hittable(p, AshLogic.WALKER_REACH, 2.5) && inFront(p, 0.3)) hitPlayer(p, AshLogic.WALKER_HIT, 0.6);
            sound("entity.generic.explode", 0.7F, 1.4F);
            Fx.send(level, Fx.blockParticle(Fx.state("blackstone")), x + Math.sin(yaw) * 2.0, y + 0.2, z + Math.cos(yaw) * 2.0, 12, 0.3, 0.1, 0.3, 0.1);
        }

        @Override void die() {
            Fx.send(level, ASH, x, y + 0.9, z, 25, 0.4, 0.6, 0.4, 0.08);
            Fx.send(level, Fx.particle("flame"), x, y + 0.6, z, 14, 0.3, 0.4, 0.3, 0.05);
            sound("entity.husk.death", 0.9F, 0.6F);
            if (!summoned) addPatch(level, x, y, z);
        }
    }

    // ================================================================ Искра
    /** Парит вокруг игрока на 4-5 блоках; прицел 0,8 с (линия частиц и разгорающееся ядро), затем жалящий луч до 14 блоков: 2 урона и поджог на 3 с. */
    static final class Spark extends AMob {
        double ang = RiftMobs.RNG.nextDouble() * Math.PI * 2, lx, ly, lz;
        int aim = -1, cd = 40 + RiftMobs.RNG.nextInt(40);
        { leash = 24; }

        @Override void act() {
            ServerPlayer p = notice(20, 12);
            double tx, tz, ty;
            if (p != null) { ang += 0.04; tx = p.m_20185_() + Math.cos(ang) * AshLogic.SPARK_KEEP; tz = p.m_20189_() + Math.sin(ang) * AshLogic.SPARK_KEEP; ty = p.m_20186_() + 2.2; }
            else { ang += 0.02; tx = hx + Math.cos(ang) * 2.5; tz = hz + Math.sin(ang) * 2.5; ty = hy; }
            while (ty > y - 4 && !(air(tx, ty, tz) && air(tx, ty + 1, tz))) ty -= 1;
            if (aim < 0) {
                fly(tx, ty, tz, 0.1 * speedMult());
                pose.charge = Math.max(0, pose.charge - 0.1);
                if (p == null) return;
                face(p.m_20185_(), p.m_20189_(), 0.2);
                if (cd > 0) cd--;
                if (cd == 0 && hd(p.m_20185_(), p.m_20189_()) < AshLogic.SPARK_RANGE && clear(x, y + 0.4, z, p.m_20185_(), p.m_20186_() + 1.2, p.m_20189_())) {
                    aim = AshLogic.SPARK_AIM; lx = p.m_20185_(); ly = p.m_20186_() + 1.0; lz = p.m_20189_();
                    sound("entity.blaze.ambient", 0.9F, 1.6F);
                }
                return;
            }
            aim--;
            pose.charge = 1 - aim / (double) AshLogic.SPARK_AIM;
            face(lx, lz, 0.3);
            double[] end = end();
            if (aim % 2 == 0) line(EMBER, x, y + 0.3, z, end[0], end[1], end[2], 1.5);
            if (aim == 0) { aim = -1; pose.charge = 0; cd = AshLogic.SPARK_CD_MIN + RiftMobs.RNG.nextInt(AshLogic.SPARK_CD_VAR); fire(end); }
        }

        double[] end() {
            double dx = lx - x, dy = ly - (y + 0.3), dz = lz - z, l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
            return rayEnd(x, y + 0.3, z, dx / l, dy / l, dz / l, AshLogic.SPARK_RANGE);
        }

        void fire(double[] end) {
            for (int i = 0; i <= 14; i++) {
                double u = i / 14.0;
                Fx.send(level, Fx.particle("flame"), x + (end[0] - x) * u, y + 0.3 + (end[1] - y - 0.3) * u, z + (end[2] - z) * u, 1, 0, 0, 0, 0);
            }
            sound("entity.blaze.shoot", 0.8F, 1.5F);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                if (RiftLogic.segDist(x, y + 0.3, z, end[0], end[1], end[2], p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_()) < AshLogic.SPARK_HIT_R) {
                    if (hitBeam(p, AshLogic.SPARK_BOLT, 0.1)) p.m_20254_(AshLogic.SPARK_FIRE_SECONDS);
                }
            }
        }

        @Override void die() {
            Fx.send(level, GOLD, x, y + 0.3, z, 14, 0.2, 0.2, 0.2, 0.08);
            Fx.send(level, Fx.particle("lava"), x, y + 0.3, z, 4, 0.2, 0.2, 0.2, 0.0);
            sound("entity.blaze.death", 0.6F, 1.8F);
        }
    }

    // ================================================================ Пепельная гончая
    /** Стая (до 3): каждая обходит игрока по своему слоту круга радиуса 3,6, ближе 4,8 бросается; перед укусом короткий присед (0,5 с). */
    static final class Hound extends AMob {
        int wind = -1, cd = 20, rank = 0, count = 1; double base = RiftMobs.RNG.nextDouble() * Math.PI * 2, wx, wz; int wanderT = 0;

        @Override void act() {
            if (t % 20 == 1) pack();
            if (wind >= 0) {
                pose.atk = 1 - wind / (double) AshLogic.HOUND_WIND;
                ServerPlayer q = nearest(4, 3);
                if (q != null) face(q.m_20185_(), q.m_20189_(), 0.3);
                if (--wind < 0) bite();
                return;
            }
            pose.atk = Math.max(0, pose.atk - 0.2);
            if (cd > 0) cd--;
            ServerPlayer p = notice(26, 5);
            if (p == null) { idle(); return; }
            double d = hd(p.m_20185_(), p.m_20189_());
            face(p.m_20185_(), p.m_20189_(), 0.3);
            if (d < 1.8 && cd == 0 && Math.abs(p.m_20186_() - y) < 2.0) { wind = AshLogic.HOUND_WIND; sound("entity.wolf.growl", 0.8F, 0.7F); return; }
            double tx = p.m_20185_(), tz = p.m_20189_();
            if (d > AshLogic.HOUND_RUSH && count > 1) {
                double[] s = AshLogic.houndSlot(rank, count, base + t * 0.01, AshLogic.HOUND_SURROUND_R);
                tx += s[0]; tz += s[1];
            }
            if (walk(tx, tz, AshLogic.HOUND_SPEED * speedMult(), 1)) pose.ph += 1.1;
        }

        void idle() {
            if (wanderT > 0) { wanderT--; face(wx, wz, 0.1); if (walk(wx, wz, 0.05, 1)) pose.ph += 0.5; }
            else if (RiftMobs.RNG.nextInt(80) == 0) { wx = hx + (RiftMobs.RNG.nextDouble() - 0.5) * 8; wz = hz + (RiftMobs.RNG.nextDouble() - 0.5) * 8; wanderT = 50; }
        }

        /** Стая: гончие того же вида рядом; слот по порядку id хостов. */
        void pack() {
            List<String> ids = new ArrayList<>();
            for (RiftMobs.Mob m : RiftMobs.MOBS.values())
                if (m.kind == kind && m.level == level && m.host != null && m.host.m_6084_() && Math.hypot(m.x - x, m.z - z) <= AshLogic.PACK_RADIUS) ids.add(m.id);
            if (!ids.contains(id)) ids.add(id);
            count = Math.min(ids.size(), 3); rank = Math.min(AshLogic.houndRank(id, ids), count - 1);
        }

        void bite() {
            cd = AshLogic.HOUND_CD; pose.atk = 0;
            for (ServerPlayer p : ps) if (hittable(p, AshLogic.HOUND_BITE_REACH, 2.3) && inFront(p, 0.0)) hitPlayer(p, AshLogic.HOUND_BITE, 0.3);
            sound("entity.wolf.hurt", 0.7F, 0.6F);
        }

        @Override void die() {
            Fx.send(level, ASH, x, y + 0.5, z, 18, 0.3, 0.2, 0.3, 0.06);
            Fx.send(level, EMBER, x, y + 0.5, z, 10, 0.3, 0.2, 0.3, 0.05);
            sound("entity.wolf.death", 0.7F, 0.6F);
        }
    }

    // ================================================================ Звонарь
    /** Держит шест с колоколом и не лезет в драку (пятится от игрока ближе 3,5 блока). Раз в ~7 с звонит: 1,3 с телеграф (кольцо, звон нарастает, actionbar), затем волна радиуса 7,5: 3 урона и сильный отброс - прыжок через волну спасает. */
    static final class Ringer extends AMob {
        int wind = -1, cd = 80 + RiftMobs.RNG.nextInt(60);

        @Override void act() {
            ServerPlayer p = notice(24, 6);
            if (wind >= 0) { windStep(); return; }
            pose.charge = Math.max(0, pose.charge - 0.08);
            if (p == null) return;
            if (cd > 0) cd--;
            face(p.m_20185_(), p.m_20189_(), 0.1);
            double d = hd(p.m_20185_(), p.m_20189_());
            if (d < 3.5) { double dx = x - p.m_20185_(), dz = z - p.m_20189_(), l = Math.max(0.3, Math.sqrt(dx * dx + dz * dz)); if (walk(x + dx / l * 3, z + dz / l * 3, 0.07 * speedMult(), 2)) pose.ph += 0.4; }
            if (cd == 0 && d <= AshLogic.RINGER_R + 5 && Math.abs(p.m_20186_() - y) < 6) {
                wind = AshLogic.RINGER_WIND;
                sound("block.bell.use", 1.8F, 0.5F);
                for (ServerPlayer q : ps) if (q.m_6084_() && hd(q.m_20185_(), q.m_20189_()) < AshLogic.RINGER_R + 10) bar(q, "explosionwand.mob.ash_ringer_wave");
            }
        }

        void windStep() {
            wind--;
            double f = 1 - wind / (double) AshLogic.RINGER_WIND;
            pose.charge = f;
            if (wind % 2 == 0) { ring(EMBER, x, y + 0.1, z, AshLogic.RINGER_R, 36); ring(GOLD, x, y + 0.1, z, AshLogic.RINGER_R * f, 20); }
            if (wind % 8 == 0 && wind > 0) sound("block.bell.use", 1.4F, 0.6F + (float) f * 0.5F);
            if (wind == 0) wave();
        }

        void wave() {
            wind = -1; cd = AshLogic.RINGER_CD + RiftMobs.RNG.nextInt(AshLogic.RINGER_CD_VAR);
            sound("block.bell.resonate", 2.5F, 0.5F);
            sound("entity.generic.explode", 1.2F, 0.8F);
            Fx.send(level, EMBER, x, y + 0.5, z, 60, AshLogic.RINGER_R * 0.5, 0.3, AshLogic.RINGER_R * 0.5, 0.1);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                if (!AshLogic.waveHits(hd(p.m_20185_(), p.m_20189_()), p.m_20186_() - y, AshLogic.RINGER_R)) continue;
                p.m_6469_(level.m_269111_().m_269333_(host), scaled(AshLogic.RINGER_WAVE));
                kick(p, x, z, AshLogic.WAVE_KICK, 0.45);
                Fx.send(level, GOLD, p.m_20185_(), p.m_20186_() + 1, p.m_20189_(), 14, 0.3, 0.4, 0.3, 0.05);
            }
        }

        @Override void die() {
            Fx.send(level, GOLD, x, y + 1.0, z, 25, 0.4, 0.6, 0.4, 0.1);
            sound("block.bell.use", 1.0F, 0.4F);
        }
    }
}
