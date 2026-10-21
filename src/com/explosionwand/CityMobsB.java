package com.explosionwand;

import com.explosionwand.RiftModels.Kind;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.*;

/**
 * Мобы Города Спящих, часть B (фаза 4.6): Сновидец (и его копии), Книжный страж, Статуя-страж, Колыбельщик, Туманный странник.
 * Каркас общий с {@link CityMobs} и мобами Разлома ({@link RiftMobs}); облик — {@link CityModels}, чистые правила — {@link CityLogic}.
 * Копии и страницы не создаются прямо из think(): RiftMobs.tick идёт по реестру, и добавление в него посреди обхода сломало бы цикл —
 * отложенные действия лежат в {@link #QUEUE} и выполняются в {@link #flush()} сразу после обхода.
 * В think() нельзя ни добавлять мобов, ни убивать хост командой (смерть шлёт событие и меняет реестр): всё это только через QUEUE.
 */
final class CityMobsB {
    private CityMobsB() {}

    static ParticleOptions CREAM;
    static final List<Runnable> QUEUE = new ArrayList<>();

    static void init() {
        CityMobs.init();
        if (CREAM == null) CREAM = new DustParticleOptions(new Vector3f(1.0F, 0.93F, 0.7F), 0.8F);
    }

    static void clear() { QUEUE.clear(); }

    /** Выполнить отложенные действия (вызывает RiftMobs.tick после обхода реестра). */
    static void flush() {
        if (QUEUE.isEmpty()) return;
        List<Runnable> run = new ArrayList<>(QUEUE);
        QUEUE.clear();
        for (Runnable r : run) {
            try { r.run(); }
            catch (RuntimeException e) { if (!RiftMobs.warned) { RiftMobs.warned = true; e.printStackTrace(); } }
        }
    }

    // ================================================================ взгляд игроков
    /** Игрок видит точку (x, ty, z) моба: она в поле зрения (CityLogic.seenBy) и между глазами и ней нет стены. */
    static boolean seenBy(RiftMobs.Mob m, ServerPlayer p, double ty) {
        Vec3 e = p.m_146892_(), l = p.m_20252_(1.0F);
        if (!CityLogic.seenBy(e.f_82479_, e.f_82480_, e.f_82481_, l.f_82479_, l.f_82480_, l.f_82481_, m.x, ty, m.z)) return false;
        return m.clear(e.f_82479_, e.f_82480_, e.f_82481_, m.x, ty, m.z);
    }

    static boolean seenByAny(RiftMobs.Mob m, double ty) {
        for (ServerPlayer p : m.ps) if (p.m_6084_() && !p.m_5833_() && seenBy(m, p, ty)) return true;
        return false;
    }

    static void bar(ServerPlayer p, String key) { p.m_5661_(Component.m_237115_(key), true); }

    // ================================================================ Сновидец
    /** Кастер: держит дистанцию, усыпляет кругом (телеграф 1,4 с; замедление и слабость), создаёт до 3 копий из стекла и меняется местами с одной из них. Удар прерывает заклинание. */
    static final class Dreamer extends RiftMobs.Mob {
        int cd = 60 + RiftMobs.RNG.nextInt(60), cloneCd = 100 + RiftMobs.RNG.nextInt(60), wind = 0, strafe = 1;

        @Override void think() {
            init();
            pose.t = t;
            if (cd > 0) cd--;
            if (cloneCd > 0) cloneCd--;
            if (wind > 0) { casting(); return; }
            pose.charge = Math.max(0, pose.charge - 0.1);
            ServerPlayer p = nearest(26, 6);
            if (p == null) return;
            face(p.m_20185_(), p.m_20189_(), 0.2);
            double dx = p.m_20185_() - x, dz = p.m_20189_() - z, d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            if (d < CityLogic.KEEP_MIN) walk(x - dx / d * 3, z - dz / d * 3, 0.12 * speedMult(), 2);
            else if (d > CityLogic.KEEP_MAX) walk(p.m_20185_(), p.m_20189_(), 0.12 * speedMult(), 2);
            else {
                if (t % 80 == 0) strafe = -strafe;
                walk(x - dz / d * strafe * 3, z + dx / d * strafe * 3, 0.06 * speedMult(), 2);
            }
            if (cd == 0 && d <= CityLogic.SLEEP_RADIUS - 0.5 && Math.abs(p.m_20186_() - y) < 4) { beginSleep(); return; }
            if (cloneCd == 0 && d < 22) summonClones();
        }

        void beginSleep() {
            wind = CityLogic.SLEEP_WIND;
            sound("entity.illusioner.prepare_blindness", 1.4F, 0.7F);
            for (ServerPlayer p : ps)
                if (p.m_6084_() && !p.m_5833_() && hd(p.m_20185_(), p.m_20189_()) < CityLogic.SLEEP_RADIUS + 6) CityMobsB.bar(p, "explosionwand.mob.dreamer_cast");
        }

        void casting() {
            wind--;
            double f = 1 - wind / (double) CityLogic.SLEEP_WIND;
            pose.charge = f;
            if (wind % 2 == 0) {
                ring(CityMobs.TEAL, x, y + 0.1, z, CityLogic.SLEEP_RADIUS, 32);
                ring(CityMobs.PALE, x, y + 0.1, z, CityLogic.SLEEP_RADIUS * f, 20);
            }
            if (wind == 0) cast();
        }

        void cast() {
            cd = CityLogic.SLEEP_CD + RiftMobs.RNG.nextInt(40);
            pose.charge = 1;
            sound("block.amethyst_block.resonate", 1.4F, 0.5F);
            Fx.send(level, CityMobs.TEAL, x, y + 1.2, z, 30, 1.5, 0.6, 1.5, 0.05);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                double d = hd(p.m_20185_(), p.m_20189_());
                if (Math.abs(p.m_20186_() - y) < 4 && CityLogic.sleepAffects(d, clear(x, y + 1.4, z, p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_()))) {
                    CityMobs.effect(level, p, "slowness", CityLogic.SLEEP_SLOW_TICKS, CityLogic.SLEEP_SLOW_AMP);
                    CityMobs.effect(level, p, "weakness", CityLogic.SLEEP_WEAK_TICKS, CityLogic.SLEEP_WEAK_AMP);
                    Fx.send(level, CityMobs.PALE, p.m_20185_(), p.m_20186_() + 1.4, p.m_20189_(), 14, 0.3, 0.3, 0.3, 0.03);
                }
            }
        }

        double hpFrac() { return host == null ? 1.0 : host.m_21223_() / Math.max(1.0, host.m_21233_()); }

        int clones() {
            int n = 0;
            for (RiftMobs.Mob m : RiftMobs.MOBS.values())
                if (m instanceof Clone c && id.equals(c.owner) && c.host != null && !c.host.m_213877_()) n++;
            return n;
        }

        int worldClones() {
            int n = 0;
            for (RiftMobs.Mob m : RiftMobs.MOBS.values()) if (m instanceof Clone && m.level == level) n++;
            return n;
        }

        void summonClones() {
            cloneCd = CityLogic.CLONE_CD;
            int n = CityLogic.cloneSpawn(clones(), hpFrac());
            if (n <= 0 || worldClones() + n > CityLogic.CLONE_WORLD_CAP) return;
            sound("entity.illusioner.cast_spell", 1.2F, 1.2F);
            QUEUE.add(() -> makeClones(n));
        }

        double[] spot(double sx, double sz) {
            int cx = (int) Math.floor(sx), cz = (int) Math.floor(sz), fy = (int) Math.floor(y + 0.01);
            for (int dy : new int[]{0, 1, -1}) {
                int cy = fy + dy;
                if (cellFree(cx, cy, cz, 2) && !air(cx, cy - 1, cz)) return new double[]{cx + 0.5, cy, cz + 0.5};
            }
            return null;
        }

        /** Копии появляются вокруг; настоящий Сновидец меняется местами с одной из них (или остаётся). Копии не наносят урона и исчезают от удара или через 12 с. */
        void makeClones(int n) {
            if (host == null || host.m_213877_() || !host.m_6084_()) return;
            List<RiftMobs.Mob> made = new ArrayList<>();
            double a0 = RiftMobs.RNG.nextDouble() * Math.PI * 2;
            for (int i = 0; i < n; i++) {
                double a = a0 + i * Math.PI * 2 / n, r = 3.5 + RiftMobs.RNG.nextDouble();
                double[] s = spot(x + Math.cos(a) * r, z + Math.sin(a) * r);
                if (s == null) continue;
                RiftMobs.Mob c = RiftMobs.spawnKind(level, Kind.DREAM_CLONE, s[0], s[1], s[2], null);
                if (c instanceof Clone cl) { cl.owner = id; made.add(c); }
                else if (c != null) RiftMobs.retire(c);
            }
            if (made.isEmpty()) return;
            int k = RiftMobs.RNG.nextInt(made.size() + 1);
            if (k < made.size()) {
                RiftMobs.Mob c = made.get(k);
                if (c.host != null) {
                    double ox = x, oy = y, oz = z;
                    setPos(c.x, c.y, c.z);
                    c.setPos(ox, oy, oz);
                }
            }
            sound("entity.illusioner.mirror_move", 1.3F, 1.0F);
            for (RiftMobs.Mob c : made) Fx.send(level, CityMobs.PALE, c.x, c.y + 1.0, c.z, 12, 0.3, 0.6, 0.3, 0.03);
            Fx.send(level, CityMobs.PALE, x, y + 1.0, z, 12, 0.3, 0.6, 0.3, 0.03);
        }

        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            if (wind > 0) {   // удар по настоящему срывает заклинание
                wind = 0; pose.charge = 0; cd = 70;
                sound("entity.illusioner.hurt", 1.0F, 1.4F);
                Fx.send(level, CityMobs.PALE, x, y + 1.6, z, 12, 0.3, 0.3, 0.3, 0.05);
            }
            super.hurt(ev, atk);
        }

        @Override void die() {
            for (RiftMobs.Mob m : new ArrayList<>(RiftMobs.MOBS.values()))
                if (m instanceof Clone c && id.equals(c.owner)) RiftMobs.retire(c);
            Fx.send(level, CityMobs.TEAL, x, y + 1.0, z, 30, 0.4, 0.7, 0.4, 0.12);
            Fx.send(level, CityMobs.PALE, x, y + 1.0, z, 15, 0.4, 0.6, 0.4, 0.05);
            sound("entity.illusioner.death", 1.0F, 1.0F);
        }
    }

    /** Копия Сновидца: стеклянная, повторяет жест настоящего, держится от игрока на расстоянии, ничего не делает. Исчезает от любого удара, по времени и со смертью хозяина. */
    static final class Clone extends RiftMobs.Mob {
        String owner;
        int life = CityLogic.CLONE_LIFE;

        @Override void think() {
            init();
            pose.t = t;
            RiftMobs.Mob o = owner == null ? null : RiftMobs.BY_ID.get(owner);
            if (o == null || o.host == null || o.host.m_213877_() || !o.host.m_6084_() || --life <= 0) { RiftMobs.retire(this); return; }
            pose.charge = o.pose.charge;
            if (life < 40 && t % 4 == 0) Fx.send(level, CityMobs.PALE, x, y + 1.0, z, 3, 0.3, 0.6, 0.3, 0.01);
            ServerPlayer p = nearest(26, 6);
            if (p == null) return;
            face(p.m_20185_(), p.m_20189_(), 0.2);
            double dx = p.m_20185_() - x, dz = p.m_20189_() - z, d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            if (d < CityLogic.KEEP_MIN) walk(x - dx / d * 3, z - dz / d * 3, 0.08, 2);
            else if (d > CityLogic.KEEP_MAX) walk(p.m_20185_(), p.m_20189_(), 0.08, 2);
        }

        /** Любой удар лопает копию без смерти: ни опыта, ни добычи (иначе копии - ферма опыта). */
        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            ev.setCanceled(true);
            die();
            RiftMobs.retire(this);
        }

        @Override void die() {
            Fx.send(level, CityMobs.PALE, x, y + 1.0, z, 20, 0.3, 0.6, 0.3, 0.08);
            sound("entity.illusioner.mirror_move", 0.8F, 1.7F);
        }
    }

    // ================================================================ Книжный страж
    /** Три летающие книги: держится в 7 блоках от игрока, раз в ~5 с целится (линии частиц 1,3 с), потом веером из трёх страниц; страницы летят 0,55 блока/тик, не пролетают сквозь блоки. */
    static final class BookGuard extends RiftMobs.Mob {
        final List<double[]> pages = new ArrayList<>();   // {x, y, z, vx, vy, vz, жизнь}
        int cd = 50 + RiftMobs.RNG.nextInt(70), aim = -1;
        double ang = RiftMobs.RNG.nextDouble() * Math.PI * 2;
        double[] dir = {0, 0, 1};

        @Override void think() {
            init();
            pose.t = t;
            flyPages();
            pose.atk = Math.max(0, pose.atk - 0.08);
            ServerPlayer p = nearest(24, 8);
            double tx, tz, ty = hy + 1.5;
            if (p != null) { ang += 0.015; tx = p.m_20185_() + Math.cos(ang) * 7; tz = p.m_20189_() + Math.sin(ang) * 7; ty = Math.max(ty, p.m_20186_() + 1.6); }
            else { ang += 0.01; tx = hx + Math.cos(ang) * 3; tz = hz + Math.sin(ang) * 3; }
            while (ty > hy + 0.5 && !(air(tx, ty, tz) && air(tx, ty + 1, tz))) ty -= 0.5;
            fly(tx, ty, tz, 0.07 * speedMult());
            if (aim < 0) {
                pose.charge = Math.max(0, pose.charge - 0.08);
                if (p != null) face(p.m_20185_(), p.m_20189_(), 0.15);
                if (cd > 0) cd--;
                if (cd == 0 && p != null && hd(p.m_20185_(), p.m_20189_()) < CityLogic.PAGE_RANGE && clear(x, y + 0.5, z, p.m_20185_(), p.m_20186_() + 1.2, p.m_20189_())) {
                    double dx = p.m_20185_() - x, dy = p.m_20186_() + 1.0 - (y + 0.5), dz = p.m_20189_() - z, l = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
                    dir = new double[]{dx / l, dy / l, dz / l};
                    aim = CityLogic.PAGE_AIM;
                    sound("item.book.page_turn", 1.4F, 0.6F);
                }
                return;
            }
            aim--;
            pose.charge = 1 - aim / (double) CityLogic.PAGE_AIM;
            face(x + dir[0], z + dir[2], 0.3);
            if (aim % 2 == 0)
                for (double off : CityLogic.fan(CityLogic.PAGE_COUNT, CityLogic.PAGE_SPREAD)) {
                    double[] d = rot(off);
                    double[] end = rayEnd(x, y + 0.5, z, d[0], d[1], d[2], CityLogic.PAGE_RANGE);
                    line(CityMobsB.CREAM, x, y + 0.5, z, end[0], end[1], end[2], 1.6);
                }
            if (aim == 12) sound("block.enchantment_table.use", 1.0F, 1.6F);
            if (aim == 0) { aim = -1; pose.charge = 0; cd = CityLogic.PAGE_CD + RiftMobs.RNG.nextInt(40); fire(); }
        }

        /** Направление залпа, повёрнутое по горизонтали на off радиан. */
        double[] rot(double off) {
            double c = Math.cos(off), s = Math.sin(off);
            return new double[]{dir[0] * c - dir[2] * s, dir[1], dir[0] * s + dir[2] * c};
        }

        void fire() {
            pose.atk = 1;
            sound("item.book.page_turn", 1.6F, 1.5F);
            sound("block.enchantment_table.use", 1.2F, 1.2F);
            for (double off : CityLogic.fan(CityLogic.PAGE_COUNT, CityLogic.PAGE_SPREAD)) {
                double[] d = rot(off);
                pages.add(new double[]{x, y + 0.5, z, d[0] * CityLogic.PAGE_SPEED, d[1] * CityLogic.PAGE_SPEED, d[2] * CityLogic.PAGE_SPEED, CityLogic.PAGE_LIFE});
            }
        }

        void flyPages() {
            for (Iterator<double[]> it = pages.iterator(); it.hasNext();) {
                double[] g = it.next();
                double nx = g[0] + g[3], ny = g[1] + g[4], nz = g[2] + g[5];
                if (--g[6] <= 0 || !air(nx, ny, nz)) { Fx.send(level, CityMobsB.CREAM, g[0], g[1], g[2], 4, 0.1, 0.1, 0.1, 0.02); it.remove(); continue; }
                g[0] = nx; g[1] = ny; g[2] = nz;
                dust(CityMobsB.CREAM, nx, ny, nz);
                if (t % 2 == 0) Fx.send(level, Fx.particle("end_rod"), nx, ny, nz, 1, 0, 0, 0, 0);
                for (ServerPlayer p : ps) {
                    if (!p.m_6084_() || p.m_5833_()) continue;
                    double dx = p.m_20185_() - nx, dy = p.m_20186_() + 1.0 - ny, dz = p.m_20189_() - nz;
                    if (dx * dx + dy * dy + dz * dz <= CityLogic.PAGE_R * CityLogic.PAGE_R) {
                        hitPlayer(p, CityLogic.PAGE_HIT, 0.1);
                        Fx.send(level, CityMobsB.CREAM, nx, ny, nz, 10, 0.2, 0.2, 0.2, 0.05);
                        sound("item.book.page_turn", 1.0F, 0.8F);
                        it.remove();
                        break;
                    }
                }
            }
        }

        @Override void die() {
            pages.clear();
            Fx.send(level, CityMobsB.CREAM, x, y + 0.5, z, 30, 0.5, 0.3, 0.5, 0.1);
            Fx.send(level, CityMobs.TEAL, x, y + 0.5, z, 8, 0.4, 0.3, 0.4, 0.05);
            sound("item.book.page_turn", 1.2F, 0.7F);
        }
    }

    // ================================================================ Статуя-страж
    /** Стоит, пока на неё смотрит хоть один игрок (взгляд + прямая видимость); отвернулся — быстро идёт к нему. Касание телеграфируется 0,8 с и тоже замирает при взгляде. */
    static final class Statue extends RiftMobs.Mob {
        @Override boolean knockable() { return false; }
        int cd = 20, wind = 0;
        boolean frozen = true;

        @Override void think() {
            init();
            pose.t = t;
            if (cd > 0) cd--;
            pose.strike = Math.max(0, pose.strike - 0.1);
            ServerPlayer p = nearest(CityLogic.STATUE_RANGE, 3);
            if (p == null) {
                wind = 0;
                pose.atk = Math.max(0, pose.atk - 0.1);
                pose.charge = Math.max(0, pose.charge - 0.1);
                return;
            }
            boolean seen = seenByAny(this, y + 1.1);
            if (!CityLogic.statueMoves(seen)) {
                if (!frozen) {
                    frozen = true;
                    sound("block.stone.hit", 1.0F, 0.6F);
                    Fx.send(level, Fx.blockParticle(Fx.state("deepslate")), x, y + 1.0, z, 8, 0.3, 0.6, 0.3, 0.03);
                }
                pose.atk = Math.max(0, pose.atk - 0.3);
                return;
            }
            frozen = false;
            pose.atk += (1 - pose.atk) * 0.4;
            face(p.m_20185_(), p.m_20189_(), 0.5);
            double d = hd(p.m_20185_(), p.m_20189_());
            if (wind > 0) {
                wind--;
                pose.charge = 1 - wind / (double) CityLogic.STATUE_WIND;
                if (wind % 2 == 0) ring(CityMobs.TEAL, x, y + 0.1, z, CityLogic.STATUE_REACH, 18);
                if (wind == 0) touch();
                return;
            }
            pose.charge = Math.max(0, pose.charge - 0.1);
            if (d <= CityLogic.STATUE_NEAR && cd == 0 && Math.abs(p.m_20186_() - y) < 2.2) {
                wind = CityLogic.STATUE_WIND;
                sound("block.stone.break", 1.0F, 0.5F);
                return;
            }
            if (d > 1.6 && walk(p.m_20185_(), p.m_20189_(), CityLogic.STATUE_SPEED * speedMult(), 2)) {
                pose.ph += 0.9;
                if (t % 3 == 0) sound("block.deepslate.step", 0.9F, 0.7F);
                Fx.send(level, Fx.blockParticle(Fx.state("deepslate")), x, y + 0.1, z, 2, 0.3, 0.05, 0.3, 0.02);
            }
        }

        void touch() {
            cd = CityLogic.STATUE_CD; pose.strike = 1; pose.charge = 0;
            sound("entity.iron_golem.attack", 1.0F, 0.6F);
            for (ServerPlayer p : ps)
                if (p.m_6084_() && !p.m_5833_() && hd(p.m_20185_(), p.m_20189_()) <= CityLogic.STATUE_REACH && Math.abs(p.m_20186_() - y) < 2.2) {
                    hitPlayer(p, CityLogic.STATUE_TOUCH, 0.4);
                    Fx.send(level, Fx.blockParticle(Fx.state("deepslate")), p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_(), 12, 0.3, 0.4, 0.3, 0.05);
                }
        }

        @Override void die() {
            Fx.send(level, Fx.blockParticle(Fx.state("deepslate")), x, y + 1.0, z, 40, 0.4, 0.7, 0.4, 0.12);
            Fx.send(level, CityMobs.TEAL, x, y + 1.2, z, 15, 0.3, 0.5, 0.3, 0.06);
            sound("block.deepslate.break", 1.2F, 0.7F);
        }
    }

    // ================================================================ Колыбельщик
    /**
     * Элита, один на город (площадь). Пока цел фонарь, звон усыпляет всех в 13 блоках (телеграф 1,6 с, замедление III и слабость II), а тело получает лишь четверть урона:
     * бить нужно по фонарю (луч прицела у его центра), у фонаря свои 36 ХП. Разбит фонарь - звона нет, тело уязвимо, Колыбельщик быстрее и бьёт чаще.
     */
    static final class Cradler extends RiftMobs.Mob {
        double lampHp = CityLogic.LANTERN_HP;
        int chimeWind = -1, chimeCd = 100, fist = -1, fistTot = CityLogic.FIST_WIND, cd = 60;
        double sx, sz;
        { half = 0.6; }

        boolean lamp() { return lampHp > 0; }

        @Override void think() {
            init();
            pose.t = t;
            pose.stun += ((lamp() ? 0 : 1) - pose.stun) * 0.1;
            pose.strike = Math.max(0, pose.strike - 0.1);
            if (chimeWind >= 0) { chimeStep(); return; }
            pose.charge = Math.max(0, pose.charge - 0.05);
            if (fist >= 0) { fistStep(); return; }
            pose.atk = Math.max(0, pose.atk - 0.1);
            ServerPlayer p = nearest(30, 6);
            if (p == null) return;
            if (chimeCd > 0) chimeCd--;
            if (cd > 0) cd--;
            face(p.m_20185_(), p.m_20189_(), 0.07);
            double d = hd(p.m_20185_(), p.m_20189_());
            double want = Math.atan2(p.m_20185_() - x, p.m_20189_() - z), off = Math.abs(RiftLogic.turn(yaw, want, 10) - yaw);
            if (lamp() && chimeCd <= 0 && d <= CityLogic.CHIME_RADIUS - 2) { beginChime(); return; }
            if (d < 4.0 && cd == 0 && off < 0.7) {
                fistTot = lamp() ? CityLogic.FIST_WIND : CityLogic.FIST_WIND2;
                fist = fistTot;
                double fr = lamp() ? CityLogic.FIST_R : CityLogic.FIST_R2;
                sx = x + Math.sin(yaw) * (fr - 0.2); sz = z + Math.cos(yaw) * (fr - 0.2);
                sound("block.deepslate.break", 1.8F, 0.5F);
                return;
            }
            if (d > 2.4 && walk(p.m_20185_(), p.m_20189_(), CityLogic.cradlerSpeed(lamp()) * speedMult(), 3)) pose.ph += 0.12;
        }

        void beginChime() {
            chimeWind = CityLogic.CHIME_WIND;
            chimeCd = CityLogic.CHIME_CD + RiftMobs.RNG.nextInt(60);
            sound("block.bell.use", 2.0F, 0.5F);
            for (ServerPlayer p : ps)
                if (p.m_6084_() && !p.m_5833_() && hd(p.m_20185_(), p.m_20189_()) < CityLogic.CHIME_RADIUS + 8) CityMobsB.bar(p, "explosionwand.mob.cradler_chime");
        }

        void chimeStep() {
            chimeWind--;
            double f = 1 - chimeWind / (double) CityLogic.CHIME_WIND;
            pose.charge = f;
            ServerPlayer p = nearest(30, 6);
            if (p != null) face(p.m_20185_(), p.m_20189_(), 0.04);
            if (chimeWind % 2 == 0) {
                ring(CityMobs.TEAL, x, y + 0.1, z, CityLogic.CHIME_RADIUS, 40);
                ring(CityMobs.PALE, x, y + 0.1, z, CityLogic.CHIME_RADIUS * f, 24);
            }
            if (chimeWind % 10 == 0 && chimeWind > 0) sound("block.bell.use", 1.4F, 0.6F + (float) f * 0.4F);
            if (chimeWind == 0) chime();
        }

        void chime() {
            chimeWind = -1;
            sound("block.bell.resonate", 2.5F, 0.5F);
            sound("block.amethyst_block.resonate", 2.0F, 0.5F);
            Fx.send(level, CityMobs.TEAL, x, y + 1.5, z, 60, 4, 1.2, 4, 0.12);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                double d = hd(p.m_20185_(), p.m_20189_());
                if (Math.abs(p.m_20186_() - y) < 8 && CityLogic.chimeAffects(d, clear(x, y + 2.0, z, p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_()))) {
                    CityMobs.effect(level, p, "slowness", CityLogic.CHIME_SLOW_TICKS, CityLogic.CHIME_SLOW_AMP);
                    CityMobs.effect(level, p, "weakness", CityLogic.CHIME_WEAK_TICKS, CityLogic.CHIME_WEAK_AMP);
                    Fx.send(level, CityMobs.PALE, p.m_20185_(), p.m_20186_() + 1.4, p.m_20189_(), 16, 0.3, 0.4, 0.3, 0.04);
                }
            }
        }

        void fistStep() {
            fist--;
            pose.atk = 1 - fist / (double) fistTot;
            double fr = lamp() ? CityLogic.FIST_R : CityLogic.FIST_R2;
            if (fist % 2 == 0) { ring(CityMobs.TEAL, sx, y + 0.1, sz, fr, 22); ring(CityMobs.PALE, sx, y + 0.1, sz, fr / 2, 10); }
            if (fist == 0) { fist = -1; slam(fr); }
        }

        void slam(double fr) {
            pose.atk = 0; pose.strike = 1;
            cd = lamp() ? CityLogic.FIST_CD : CityLogic.FIST_CD2;
            Fx.send(level, Fx.blockParticle(Fx.state("deepslate")), sx, y + 0.3, sz, 40, fr / 2, 0.2, fr / 2, 0.2);
            Fx.send(level, Fx.particle("explosion"), sx, y + 0.5, sz, 1, 0, 0, 0, 0);
            Fx.sound(level, sx, y, sz, "entity.generic.explode", 1.6F, 0.7F);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                double dx = p.m_20185_() - sx, dz = p.m_20189_() - sz;
                if (dx * dx + dz * dz < fr * fr && Math.abs(p.m_20186_() - y) < 2.5) hitPlayer(p, CityLogic.CRADLER_FIST, 0.9);
            }
        }

        /** Центр фонаря в мире. */
        double[] lampWorld() {
            double[] lo = CityModels.lanternLocal(pose), w = CityLogic.toWorld(yaw, lo[0], lo[2]);
            return new double[]{x + w[0], y + lo[1], z + w[1]};
        }

        /** Удар пришёл по фонарю: луч прицела бьющего проходит у его центра (или снаряд оказался рядом с ним). */
        boolean lanternHit(LivingHurtEvent ev, Entity atk) {
            double[] c = lampWorld();
            Entity direct = ev.getSource() == null ? null : ev.getSource().m_7640_();
            if (direct != null && direct != atk) {
                double dx = direct.m_20185_() - c[0], dy = direct.m_20186_() - c[1], dz = direct.m_20189_() - c[2];
                return dx * dx + dy * dy + dz * dz <= CityLogic.LANTERN_PROJ_R * CityLogic.LANTERN_PROJ_R;
            }
            if (atk == null) return false;
            Vec3 e = atk.m_146892_(), l = atk.m_20252_(1.0F);
            return CityLogic.rayNear(e.f_82479_, e.f_82480_, e.f_82481_, l.f_82479_, l.f_82480_, l.f_82481_, c[0], c[1], c[2], CityLogic.LANTERN_R);
        }

        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            if (!lamp()) { super.hurt(ev, atk); return; }
            double[] c = lampWorld();
            if (lanternHit(ev, atk)) {
                lampHp -= ev.getAmount();
                ev.setAmount(0.0F);
                Fx.send(level, CityMobs.TEAL, c[0], c[1], c[2], 8, 0.2, 0.2, 0.2, 0.05);
                Fx.sound(level, c[0], c[1], c[2], "block.amethyst_cluster.hit", 1.5F, 1.2F);
                if (lampHp <= 0) breakLantern(c);
                return;
            }
            ev.setAmount((float) (ev.getAmount() * CityLogic.cradlerBodyMult(true)));
            deflect();
        }

        void breakLantern(double[] c) {
            lampHp = 0; chimeWind = -1;
            cd = Math.min(cd, 20);
            Fx.send(level, Fx.blockParticle(Fx.state("sea_lantern")), c[0], c[1], c[2], 40, 0.3, 0.3, 0.3, 0.15);
            Fx.send(level, CityMobs.TEAL, c[0], c[1], c[2], 30, 0.5, 0.5, 0.5, 0.2);
            Fx.sound(level, c[0], c[1], c[2], "block.glass.break", 2.0F, 0.6F);
            sound("entity.warden.sonic_boom", 1.5F, 1.3F);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || hd(p.m_20185_(), p.m_20189_()) > 40) continue;
                Cmd.run(level, x, y, z, "title " + p.m_20148_() + " times 5 40 15");
                Cmd.run(level, x, y, z, "title " + p.m_20148_() + " subtitle {\"translate\":\"explosionwand.mob.cradler_lantern.sub\",\"color\":\"gray\",\"italic\":true}");
                Cmd.run(level, x, y, z, "title " + p.m_20148_() + " title {\"translate\":\"explosionwand.mob.cradler_lantern\",\"color\":\"aqua\",\"bold\":true}");
            }
        }

        @Override void die() {
            Fx.send(level, CityMobs.TEAL, x, y + 1.5, z, 60, 0.7, 1.2, 0.7, 0.2);
            Fx.send(level, Fx.blockParticle(Fx.state("deepslate")), x, y + 1.5, z, 60, 0.7, 1.2, 0.7, 0.2);
            sound("block.bell.resonate", 2.0F, 0.4F);
            sound("entity.generic.explode", 1.4F, 0.7F);
        }
    }

    // ================================================================ Туманный странник
    /** Обычный мир, у Города ночью или в дождь: крадётся за игроком (при взгляде почти замирает), замахивается 0,7 с, крадёт безопасный предмет из основной руки и бежит. Убей, пока не растворился: вещь выпадет. Скрылся - вещь остаётся на земле. */
    static final class FogWalker extends RiftMobs.Mob {
        static final int STALK = 0, GRAB = 1, FLEE = 2;
        int state = STALK, wind = 0, stuck = 0;
        long fleeEnd;
        boolean carrying, escaped, restored;
        UUID victim;
        double fdx = 1, fdz = 0;
        { leash = 400; }

        @Override void think() {
            init();
            pose.t = t;
            if (t % 5 == 0) Fx.send(level, Fx.particle("cloud"), x, y + 0.2, z, 2, 0.5, 0.1, 0.5, 0.004);
            if (!restored) {   // новый Java-объект после загрузки чанка или перезапуска: вещь в руке хоста - значит, уже украл, бежим и растворяемся
                restored = true;
                if (holds()) { carrying = true; state = FLEE; fleeEnd = RiftMobs.now; }
            }
            if (level.m_46791_() == Difficulty.valueOf("PEACEFUL")) { escape(); return; }   // хост всё равно сотрёт ванильный despawn: вещь возвращаем заранее
            if (state == GRAB) { grab(); return; }
            pose.atk = Math.max(0, pose.atk - 0.1);
            if (state == FLEE) flee(); else stalk();
        }

        /** В руке хоста лежит украденная вещь. */
        boolean holds() { return host != null && host.m_21205_() != null && !host.m_21205_().m_41619_(); }

        /** Подходящая цель: живой игрок в выживании. */
        ServerPlayer target() {
            ServerPlayer best = null; double bd = 40 * 40;
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_() || p.m_7500_() || Math.abs(p.m_20186_() - y) > 8) continue;
                double dx = p.m_20185_() - x, dz = p.m_20189_() - z, d = dx * dx + dz * dz;
                if (d <= bd) { bd = d; best = p; }
            }
            return best;
        }

        ServerPlayer player(UUID id) {
            if (id == null) return null;
            for (ServerPlayer p : ps) if (p.m_20148_().equals(id)) return p;
            return null;
        }

        void stalk() {
            if (!level.m_46462_() && !level.m_46471_() && RiftMobs.RNG.nextInt(100) == 0) { escape(); return; }
            ServerPlayer p = target();
            if (p == null) { pose.charge = Math.max(0, pose.charge - 0.1); return; }
            boolean seen = CityMobsB.seenBy(this, p, y + 1.2);
            pose.charge += ((seen ? 1 : 0) - pose.charge) * 0.2;
            double d = hd(p.m_20185_(), p.m_20189_());
            face(p.m_20185_(), p.m_20189_(), 0.2);
            double[] b = RiftLogic.behind(p.m_20185_(), p.m_20189_(), p.m_146908_(), 2.2);
            double sp = (seen ? CityLogic.FOG_SPEED_SEEN : CityLogic.FOG_SPEED) * speedMult();
            if (d > CityLogic.FOG_GRAB_RANGE - 0.3 && walk(d > 6 ? b[0] : p.m_20185_(), d > 6 ? b[1] : p.m_20189_(), sp, 2)) pose.ph += seen ? 0.2 : 0.45;
            if (d <= CityLogic.FOG_GRAB_RANGE && Math.abs(p.m_20186_() - y) < 2.2) {
                state = GRAB; wind = CityLogic.FOG_GRAB_WIND; victim = p.m_20148_();
                sound("entity.enderman.stare", 0.8F, 1.6F);
            }
        }

        void grab() {
            ServerPlayer p = player(victim);
            if (p == null || !p.m_6084_() || p.m_5833_() || p.m_7500_()) { state = STALK; wind = 0; return; }
            wind--;
            pose.atk = 1 - wind / (double) CityLogic.FOG_GRAB_WIND;
            pose.charge = 0;
            face(p.m_20185_(), p.m_20189_(), 0.4);
            if (wind % 2 == 0) ring(CityMobs.PALE, x, y + 0.8, z, CityLogic.FOG_GRAB_R, 16);
            if (wind > 0) return;
            if (hd(p.m_20185_(), p.m_20189_()) > CityLogic.FOG_GRAB_R || Math.abs(p.m_20186_() - y) > 2.5) { state = STALK; return; }
            steal(p);
        }

        /** Берёт предмет командой (копия в руку хоста, потом очистка руки игрока), только если копия удалась: вещь не может пропасть. */
        void steal(ServerPlayer p) {
            ItemStack s = p.m_21205_();
            String id = s == null || s.m_41619_() ? null : String.valueOf(ForgeRegistries.ITEMS.getKey(s.m_41720_()));
            if (!holds() && CityLogic.canSteal(id, s != null && s.m_41782_())) {
                Cmd.run(level, x, y, z, "item replace entity " + host.m_20148_() + " weapon.mainhand from entity " + p.m_20148_() + " weapon.mainhand");
                ItemStack held = host.m_21205_();
                if (held != null && !held.m_41619_()) {
                    Cmd.run(level, x, y, z, "item replace entity " + p.m_20148_() + " weapon.mainhand with minecraft:air");
                    Cmd.run(level, x, y, z, "data merge entity " + host.m_20148_() + " {HandDropChances:[2.0f,0.0f]}");
                    carrying = true;
                    CityMobsB.bar(p, "explosionwand.mob.fog_steal");
                    sound("entity.enderman.teleport", 1.0F, 1.5F);
                    Fx.send(level, CityMobs.PALE, p.m_20185_(), p.m_20186_() + 1.2, p.m_20189_(), 20, 0.3, 0.3, 0.3, 0.05);
                }
            }
            if (!carrying) sound("entity.enderman.scream", 0.5F, 1.8F);
            state = FLEE; stuck = 0;
            fleeEnd = RiftMobs.now + (carrying ? CityLogic.FOG_FLEE : 80);
            ServerPlayer from = nearest(40, 12);
            double dx = from == null ? RiftMobs.RNG.nextDouble() - 0.5 : x - from.m_20185_(), dz = from == null ? RiftMobs.RNG.nextDouble() - 0.5 : z - from.m_20189_(), l = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            fdx = dx / l; fdz = dz / l;
        }

        void flee() {
            if (RiftMobs.now >= fleeEnd) { escape(); return; }
            pose.charge = 0;
            if (t % 10 == 0) {
                ServerPlayer from = nearest(30, 12);
                if (from != null) {
                    double dx = x - from.m_20185_(), dz = z - from.m_20189_(), l = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                    fdx = dx / l; fdz = dz / l;
                }
            }
            face(x + fdx, z + fdz, 0.4);
            if (walk(x + fdx * 6, z + fdz * 6, CityLogic.FOG_RUN * speedMult(), 2)) { pose.ph += 0.9; stuck = 0; }
            else if (++stuck > 5) {
                double a = RiftMobs.RNG.nextDouble() * Math.PI * 2;
                fdx = Math.cos(a); fdz = Math.sin(a); stuck = 0;
            }
        }

        /** Время вышло: странник растворяется в тумане. С вещью — убивается командой, и вещь выпадает на землю (HandDropChances 2.0: выпадает всегда), игроку подсказка и столб света. */
        void escape() {
            Fx.send(level, CityMobs.PALE, x, y + 1.0, z, 30, 0.4, 0.8, 0.4, 0.05);
            if (!carrying && !holds()) { RiftMobs.retire(this); return; }
            for (double yy = y; yy < y + 8; yy += 0.5) Fx.send(level, Fx.particle("end_rod"), x, yy, z, 1, 0.05, 0, 0.05, 0);
            if (!escaped) {
                escaped = true;
                ServerPlayer v = player(victim);
                if (v != null) CityMobsB.bar(v, "explosionwand.mob.fog_escaped");
            }
            String uuid = String.valueOf(host.m_20148_());
            ServerLevel lv = level; double kx = x, ky = y, kz = z;
            CityMobsB.QUEUE.add(() -> Cmd.run(lv, kx, ky, kz, "kill " + uuid));   // не посреди обхода реестра: kill шлёт событие смерти
            fleeEnd = RiftMobs.now + 20;   // команда не сработала - повторим через секунду
        }

        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            if (state == STALK && !carrying) { state = FLEE; stuck = 0; fleeEnd = RiftMobs.now + 80; }
            if (state == GRAB) { state = STALK; wind = 0; }
            super.hurt(ev, atk);
        }

        /** Выбросить вещь из руки хоста самим кодом (выпадение экипировки зависит от правила doMobLoot), руку очистить: ванильная добыча уже не удвоит. */
        void dropHeld() {
            if (!holds()) return;
            ItemStack h = host.m_21205_().m_41777_();
            Cmd.run(level, x, y, z, "item replace entity " + host.m_20148_() + " weapon.mainhand with minecraft:air");
            if (holds()) return;   // очистка не удалась - оставим ванильному выпадению
            ItemEntity ie = new ItemEntity(level, x, y + 0.5, z, h);
            level.m_7967_(ie);
        }

        @Override void die() {
            dropHeld();
            Fx.send(level, CityMobs.PALE, x, y + 1.0, z, 30, 0.4, 0.8, 0.4, 0.06);
            sound("entity.enderman.death", 0.6F, 1.6F);
            if (carrying && !escaped)
                for (ServerPlayer p : ps)
                    if (p.m_6084_() && hd(p.m_20185_(), p.m_20189_()) < 12) CityMobsB.bar(p, "explosionwand.mob.fog_returned");
        }
    }

    /** Ванильный despawn (Мирный режим, дальность) стирает хоста странника раньше нашего тика: вещь из его руки выбрасываем здесь. Выгрузка чанка (не DISCARDED) вещь не трогает: она сохраняется вместе с мобом. */
    static void onLeave(EntityLeaveLevelEvent ev) {
        Entity e = ev.getEntity();
        if (!(e instanceof LivingEntity le) || !(e.m_9236_() instanceof ServerLevel sl)) return;
        if (e.m_146911_() == null || !"DISCARDED".equals(e.m_146911_().name()) || !e.m_19880_().contains("ewrk_fog_walker")) return;
        ItemStack h = le.m_21205_();
        if (h == null || h.m_41619_()) return;
        ItemStack drop = h.m_41777_();
        double x = e.m_20185_(), y = e.m_20186_() + 0.5, z = e.m_20189_();
        Later.run(sl, 1, () -> sl.m_7967_(new ItemEntity(sl, x, y, z, drop)));   // не внутри обратного вызова удаления
    }

    // ================================================================ появление в обычном мире
    /** Раз в FOG_PERIOD тиков у каждого Города, рядом с которым есть игрок: ночью или в дождь иногда рождается странник (не больше FOG_CAP у города), вне данжа и вне воды. */
    static void tickWorld(ServerLevel sl, DungeonData data, List<ServerPlayer> ps, long now) {
        if (now % CityLogic.FOG_PERIOD != 0 || data.recs.isEmpty() || ps.isEmpty()) return;
        boolean night = sl.m_46462_(), rain = sl.m_46471_();
        if (!night && !rain) return;
        if (sl.m_46791_() == Difficulty.valueOf("PEACEFUL")) return;   // ванильный despawn тут же удалил бы хост
        for (DungeonData.Rec r : data.recs.values()) {
            if (r.def() != Dungeons.SLEEP || r.partsDone == 0) continue;
            List<ServerPlayer> near = new ArrayList<>();
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_() || p.m_7500_()) continue;
                if (Math.hypot(p.m_20185_() - r.x, p.m_20189_() - r.z) <= CityLogic.FOG_CITY_RANGE) near.add(p);
            }
            if (near.isEmpty()) continue;
            int have = 0;
            AABB box = new AABB(r.x - 120, r.y - 60, r.z - 120, r.x + 120, r.y + 80, r.z + 120);
            for (Entity e : sl.m_6249_(null, box, en -> en.m_19880_().contains("ewrk_" + Kind.FOG_WALKER.id)))
                if (e instanceof LivingEntity le && le.m_6084_()) have++;
            if (!CityLogic.fogSpawns(night, rain, have, RiftMobs.RNG.nextInt(100))) continue;
            ServerPlayer p = near.get(RiftMobs.RNG.nextInt(near.size()));
            double ang = RiftMobs.RNG.nextDouble() * Math.PI * 2, rad = CityLogic.FOG_SPAWN_MIN + RiftMobs.RNG.nextDouble() * (CityLogic.FOG_SPAWN_MAX - CityLogic.FOG_SPAWN_MIN);
            double bx = p.m_20185_() + Math.cos(ang) * rad, bz = p.m_20189_() + Math.sin(ang) * rad;
            if (!RiftBeetles.chunkLoaded(sl, bx, bz) || Math.hypot(bx - r.x, bz - r.z) > CityLogic.FOG_CITY_RANGE + 20) continue;
            int ix = (int) Math.floor(bx), iz = (int) Math.floor(bz), by = Dungeons.groundY(sl, ix, iz);
            if (Math.abs(by - p.m_20186_()) > 12 || Dungeons.wet(sl, ix, by - 1, iz) || Dungeons.near(sl, bx, by, bz, 6)) continue;
            RiftMobs.spawnKind(sl, Kind.FOG_WALKER, bx, by, bz, null);
        }
    }
}
