package com.explosionwand;

import com.explosionwand.AshMobs.AMob;
import com.explosionwand.RiftModels.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.*;

/**
 * Мобы Пепельного святилища, часть B (4.9): Магмовый червь, Пепельный жрец, Угольный страж, Хранитель Углей (мини-босс арены) и Блуждающий огонёк
 * в обычном мире. Каркас - RiftMobs/AshMobs, облик - AshModels, правила - AshLogic. В think() мобов и блоки, и реестр не трогаем:
 * постановка сундука и призыв искр ловушки - через CityMobsB.QUEUE (выполняется после обхода реестра).
 */
final class AshMobsB {
    private AshMobsB() {}

    /** Святилище (Rec.key) -> номер ночи, в которую огонёк уже отдал сундук. В памяти сервера: после перезапуска лимит обнуляется. */
    static final Map<String, Long> WISP_NIGHT = new HashMap<>();
    static void clear() { WISP_NIGHT.clear(); }

    private static boolean peaceful(ServerLevel l) { return l.m_46791_() == Difficulty.valueOf("PEACEFUL"); }

    // ================================================================ Магмовый червь
    /**
     * Засада на лавовом озере: прячется под лавой (неуязвим), при игроке в 16 блоках пузырится 1,1 с (круг на лаве), всплывает у игрока (не под ногами), плюётся:
     * прицел 0,9 с (круг на месте игрока, дуга из пасти), плевок 5 урона и поджог. Высунут 4,5 с, потом ныряет. Бить можно, пока высунут.
     */
    static final class Worm extends AMob {
        static final int HIDDEN = 0, BUBBLE = 1, UP = 2;
        int st = HIDDEN, tm = 40 + RiftMobs.RNG.nextInt(40), spit = -1, spitCd = 0;
        double[][] cells; double nx, ny, nz, sx, sy, sz;
        { leash = 40; }

        @Override boolean vulnerable() { return st == UP && pose.open > 0.6; }

        @Override void deflect() {
            Fx.send(level, Fx.particle("lava"), x, y + 0.5, z, 3, 0.3, 0.2, 0.3, 0);
            Fx.sound(level, x, y, z, "block.lava.extinguish", 0.8F, 1.4F);
        }

        /** Клетки лавы вокруг точки появления (сверху воздух): столбы в радиусе WORM_CELL_RANGE; нет лавы - только точка появления. */
        double[][] lavaCells() {
            List<double[]> out = new ArrayList<>();
            Object lava = Fx.block("lava");
            int r = (int) AshLogic.WORM_CELL_RANGE, hy0 = (int) Math.floor(hy + 1);
            for (int dx = -r; dx <= r; dx += 1)
                for (int dz = -r; dz <= r; dz += 1) {
                    if (dx * dx + dz * dz > r * r) continue;
                    int cx = (int) Math.floor(hx) + dx, cz = (int) Math.floor(hz) + dz;
                    for (int yy = hy0; yy > hy0 - 14; yy--) {
                        BlockState s = level.m_8055_(new BlockPos(cx, yy, cz));
                        if (s.m_60795_()) continue;
                        if (s.m_60734_() == lava && air(cx + 0.5, yy + 1, cz + 0.5)) out.add(new double[]{cx + 0.5, yy + 0.75, cz + 0.5});
                        break;
                    }
                }
            if (out.isEmpty()) out.add(new double[]{hx, hy, hz});
            return out.toArray(new double[0][]);
        }

        @Override void act() {
            if (cells == null) cells = lavaCells();
            pose.open += ((st == UP ? 1 : 0) - pose.open) * 0.25;
            ServerPlayer p = notice(AshLogic.WORM_RANGE, 8);
            if (st == HIDDEN) {
                pose.charge = 0;
                if (tm > 0) { tm--; return; }
                if (p == null) return;
                int i = AshLogic.wormCell(cells, p.m_20185_(), p.m_20189_());
                if (i < 0) { tm = 20; return; }
                nx = cells[i][0]; ny = cells[i][1]; nz = cells[i][2];
                st = BUBBLE; tm = AshLogic.WORM_BUBBLE;
                Fx.sound(level, nx, ny, nz, "block.lava.pop", 1.2F, 0.6F);
                return;
            }
            if (st == BUBBLE) {
                tm--;
                if (tm % 2 == 0) { ring(AshMobs.EMBER, nx, ny + 0.4, nz, 1.4, 14); Fx.send(level, Fx.particle("lava"), nx, ny + 0.4, nz, 2, 0.5, 0.1, 0.5, 0); }
                if (tm % 6 == 0) Fx.sound(level, nx, ny, nz, "block.lava.pop", 0.9F, 0.8F);
                if (tm > 0) return;
                setPos(nx, ny, nz);
                st = UP; tm = AshLogic.WORM_UP; spitCd = 18; spit = -1;
                Fx.send(level, Fx.particle("lava"), x, y + 0.8, z, 14, 0.5, 0.5, 0.5, 0);
                sound("entity.strider.hurt", 1.0F, 0.5F);
                return;
            }
            tm--;
            if (p != null) face(p.m_20185_(), p.m_20189_(), 0.2);
            if (spit >= 0) { spitStep(); return; }
            pose.charge = Math.max(0, pose.charge - 0.1);
            if (spitCd > 0) spitCd--;
            if (spitCd == 0 && p != null && hd(p.m_20185_(), p.m_20189_()) < AshLogic.WORM_RANGE) {
                spit = AshLogic.WORM_SPIT_WIND; sx = p.m_20185_(); sy = p.m_20186_(); sz = p.m_20189_();
                sound("entity.ghast.warn", 1.0F, 1.4F);
                return;
            }
            if (tm <= 0 || p == null) { st = HIDDEN; tm = AshLogic.WORM_HIDE + RiftMobs.RNG.nextInt(AshLogic.WORM_HIDE_VAR); Fx.send(level, Fx.particle("lava"), x, y + 0.8, z, 8, 0.4, 0.3, 0.4, 0); }
        }

        void spitStep() {
            spit--;
            pose.charge = 1 - spit / (double) AshLogic.WORM_SPIT_WIND;
            if (spit % 2 == 0) {
                ring(AshMobs.EMBER, sx, sy + 0.1, sz, AshLogic.WORM_SPIT_R, 16);
                double f = 1 - spit / (double) AshLogic.WORM_SPIT_WIND;
                dust(AshMobs.GOLD, x + (sx - x) * f, y + 1.6 + Math.sin(f * Math.PI) * 2.0 + (sy - y - 1.6) * f, z + (sz - z) * f);
            }
            if (spit > 0) return;
            spit = -1; spitCd = AshLogic.WORM_SPIT_CD;
            Fx.send(level, Fx.particle("flame"), sx, sy + 0.4, sz, 25, 0.8, 0.3, 0.8, 0.05);
            Fx.send(level, Fx.particle("explosion"), sx, sy + 0.4, sz, 1, 0, 0, 0, 0);
            Fx.sound(level, sx, sy, sz, "entity.generic.explode", 1.0F, 1.3F);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                double dx = p.m_20185_() - sx, dz = p.m_20189_() - sz;
                if (dx * dx + dz * dz < AshLogic.WORM_SPIT_R * AshLogic.WORM_SPIT_R && Math.abs(p.m_20186_() - sy) < 2.5) {
                    if (ArmorSets.tryReflectBeam(p, host, scaled(AshLogic.WORM_SPIT))) continue;   // «Грань»: плевок возвращается червю
                    p.m_6469_(level.m_269111_().m_269333_(host), scaled(AshLogic.WORM_SPIT));
                    p.m_20254_(2);
                }
            }
        }

        @Override void die() {
            Fx.send(level, Fx.particle("lava"), x, y + 0.8, z, 20, 0.5, 0.5, 0.5, 0);
            Fx.send(level, AshMobs.EMBER, x, y + 1.0, z, 20, 0.4, 0.6, 0.4, 0.1);
            sound("entity.strider.death", 1.0F, 0.6F);
        }
    }

    // ================================================================ Пепельный жрец
    /** Держится в 6-11 блоках. Лечит раненого союзника (до 25%, не больше 40 ХП; луч 0,7 с) и ставит огненные круги под игроками (телеграф 1,3 с, 5 урона и поджог). Убивать первым. */
    static final class Priest extends AMob {
        int healCd = 60 + RiftMobs.RNG.nextInt(40), heal = -1, circleCd = 70 + RiftMobs.RNG.nextInt(40), circle = -1, strafe = 1;
        UUID healId;
        final List<double[]> circles = new ArrayList<>();

        @Override void act() {
            ServerPlayer p = notice(26, 6);
            if (heal >= 0) { healStep(); return; }
            if (circle >= 0) { circleStep(); return; }
            pose.charge = Math.max(0, pose.charge - 0.1);
            pose.atk = Math.max(0, pose.atk - 0.1);
            if (healCd > 0) healCd--;
            if (circleCd > 0) circleCd--;
            if (p != null) {
                face(p.m_20185_(), p.m_20189_(), 0.15);
                double dx = p.m_20185_() - x, dz = p.m_20189_() - z, d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                if (d < AshLogic.PRIEST_KEEP_MIN) { if (walk(x - dx / d * 3, z - dz / d * 3, 0.1 * speedMult(), 2)) pose.ph += 0.4; }
                else if (d > AshLogic.PRIEST_KEEP_MAX) { if (walk(p.m_20185_(), p.m_20189_(), 0.07 * speedMult(), 2)) pose.ph += 0.3; }
                else { if (t % 90 == 0) strafe = -strafe; if (walk(x - dz / d * strafe * 3, z + dx / d * strafe * 3, 0.04, 2)) pose.ph += 0.2; }
            }
            if (healCd == 0) {
                LivingEntity w = weakest();
                if (w != null) {
                    heal = AshLogic.PRIEST_HEAL_WIND; healId = w.m_20148_();
                    sound("block.amethyst_block.chime", 1.2F, 1.2F);
                    return;
                }
                healCd = 30;
            }
            if (circleCd == 0 && p != null && hd(p.m_20185_(), p.m_20189_()) < 22) startCircles();
        }

        /** Самый израненный союзник (не жрец) в радиусе лечения. */
        LivingEntity weakest() {
            LivingEntity best = null; double br = 2;
            for (RiftMobs.Mob m : RiftMobs.MOBS.values()) {
                if (m == this || m.level != level || m.host == null || !m.host.m_6084_() || !m.kind.ash() || m.kind == Kind.ASH_PRIEST || m.kind == Kind.ASH_WISP) continue;
                if (Math.hypot(m.x - x, m.z - z) > AshLogic.HEAL_RANGE || Math.abs(m.y - y) > 8) continue;
                double hp = m.host.m_21223_(), max = m.host.m_21233_();
                if (!AshLogic.needsHeal(hp, max) || hp / max >= br) continue;
                br = hp / max; best = m.host;
            }
            return best;
        }

        void healStep() {
            heal--;
            pose.charge = 1 - heal / (double) AshLogic.PRIEST_HEAL_WIND;
            RiftMobs.Mob tm = healId == null ? null : RiftMobs.MOBS.get(healId);
            if (tm == null || tm.host == null || !tm.host.m_6084_()) { heal = -1; healCd = 40; return; }
            if (heal % 2 == 0) line(AshMobs.GREEN, x, y + 1.6, z, tm.x, tm.y + 1.0, tm.z, 1.0);
            if (heal > 0) return;
            heal = -1; healCd = AshLogic.PRIEST_HEAL_CD;
            LivingEntity h = tm.host;
            h.m_21153_((float) Math.min(h.m_21233_(), h.m_21223_() + AshLogic.healAmount(h.m_21233_())));
            Fx.send(level, Fx.particle("heart"), tm.x, tm.y + 1.5, tm.z, 6, 0.4, 0.5, 0.4, 0);
            Fx.send(level, AshMobs.GREEN, tm.x, tm.y + 1.0, tm.z, 14, 0.4, 0.5, 0.4, 0.05);
            sound("block.amethyst_block.resonate", 1.0F, 1.4F);
        }

        void startCircles() {
            circles.clear();
            for (ServerPlayer q : ps) {
                if (circles.size() >= AshLogic.MAX_CIRCLES || !q.m_6084_() || q.m_5833_() || hd(q.m_20185_(), q.m_20189_()) > 20 || Math.abs(q.m_20186_() - y) > 5) continue;
                circles.add(new double[]{q.m_20185_(), q.m_20186_(), q.m_20189_()});
                AshMobs.bar(q, "explosionwand.mob.ash_priest_circle");
            }
            if (circles.isEmpty()) { circleCd = 40; return; }
            circle = AshLogic.PRIEST_CIRCLE_WIND;
            sound("entity.evoker.prepare_attack", 1.2F, 0.8F);
        }

        void circleStep() {
            circle--;
            double f = 1 - circle / (double) AshLogic.PRIEST_CIRCLE_WIND;
            pose.atk = f;
            if (circle % 2 == 0) for (double[] c : circles) { ring(AshMobs.EMBER, c[0], c[1] + 0.1, c[2], AshLogic.CIRCLE_R, 22); ring(AshMobs.GOLD, c[0], c[1] + 0.1, c[2], AshLogic.CIRCLE_R * f, 12); }
            if (circle > 0) return;
            circle = -1; circleCd = AshLogic.PRIEST_CIRCLE_CD + RiftMobs.RNG.nextInt(AshLogic.PRIEST_CIRCLE_VAR);
            for (double[] c : circles) {
                Fx.send(level, Fx.particle("flame"), c[0], c[1] + 0.3, c[2], 30, AshLogic.CIRCLE_R * 0.5, 0.2, AshLogic.CIRCLE_R * 0.5, 0.06);
                Fx.sound(level, c[0], c[1], c[2], "entity.blaze.shoot", 1.0F, 0.7F);
            }
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                for (double[] c : circles) {
                    double dx = p.m_20185_() - c[0], dz = p.m_20189_() - c[2];
                    if (dx * dx + dz * dz < AshLogic.CIRCLE_R * AshLogic.CIRCLE_R && Math.abs(p.m_20186_() - c[1]) < 2.5) {
                        p.m_6469_(level.m_269111_().m_269333_(host), scaled(AshLogic.PRIEST_CIRCLE));
                        p.m_20254_(3);
                        break;
                    }
                }
            }
            circles.clear();
        }

        @Override void die() {
            Fx.send(level, AshMobs.GREEN, x, y + 1.2, z, 18, 0.4, 0.6, 0.4, 0.08);
            Fx.send(level, AshMobs.EMBER, x, y + 1.2, z, 18, 0.4, 0.6, 0.4, 0.08);
            sound("entity.witch.death", 0.9F, 0.6F);
        }
    }

    // ================================================================ Угольный страж
    /** Элита: щит спереди гасит удар до 25%, со спины 130%. Булава: замах 1,1 с (щит опущен, дуга на полу), удар 10 по конусу; после удара 1,3 с открыт со всех сторон (150%). */
    static final class Guard extends AMob {
        int wind = 0, recover = 0, cd = 30;
        { half = 0.6; }

        boolean up() { return wind == 0; }
        boolean open() { return recover > 0; }

        @Override void act() {
            pose.lift += ((wind > 0 ? 1 : 0) - pose.lift) * 0.3;
            pose.stun += ((recover > 0 ? 1 : 0) - pose.stun) * 0.25;
            pose.strike = Math.max(0, pose.strike - 0.1);
            if (recover > 0) { recover--; return; }
            if (wind > 0) {
                pose.atk = 1 - wind / (double) AshLogic.GUARD_WIND;
                ServerPlayer q = nearest(8, 4);
                if (q != null) face(q.m_20185_(), q.m_20189_(), 0.05);
                if (wind % 2 == 0) arc(AshLogic.GUARD_REACH, AshMobs.EMBER);
                if (--wind == 0) swing();
                return;
            }
            pose.atk = Math.max(0, pose.atk - 0.15);
            if (cd > 0) cd--;
            ServerPlayer p = notice(24, 5);
            if (p == null) return;
            face(p.m_20185_(), p.m_20189_(), 0.12);
            double d = hd(p.m_20185_(), p.m_20189_());
            if (d < AshLogic.GUARD_REACH - 0.3 && cd == 0 && Math.abs(p.m_20186_() - y) < 2.5) { wind = AshLogic.GUARD_WIND; sound("entity.iron_golem.attack", 1.0F, 0.6F); return; }
            if (d > 2.4 && walk(p.m_20185_(), p.m_20189_(), AshLogic.GUARD_SPEED * speedMult(), 3)) pose.ph += 0.3;
        }

        void swing() {
            pose.atk = 0; pose.strike = 1; recover = AshLogic.GUARD_RECOVER; cd = AshLogic.GUARD_CD;
            for (ServerPlayer p : ps) if (hittable(p, AshLogic.GUARD_REACH, 2.8) && inFront(p, 0.1)) hitPlayer(p, AshLogic.GUARD_SWING, 0.8);
            Fx.send(level, Fx.blockParticle(Fx.state("blackstone")), x + Math.sin(yaw) * 2.4, y + 0.2, z + Math.cos(yaw) * 2.4, 20, 0.5, 0.1, 0.5, 0.15);
            sound("entity.generic.explode", 1.0F, 0.8F);
            sound("block.anvil.land", 0.8F, 0.6F);
        }

        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            double cos = 1;
            if (atk != null) {
                double ax = atk.m_20185_() - x, az = atk.m_20189_() - z, d = Math.sqrt(ax * ax + az * az);
                if (d > 1e-3) cos = (ax * Math.sin(yaw) + az * Math.cos(yaw)) / d;
            }
            double m = AshLogic.guardMult(cos, up(), open());
            ev.setAmount((float) (ev.getAmount() * m));
            if (m < 1) {
                Fx.send(level, AshMobs.GOLD, x + Math.sin(yaw) * 0.8, y + 1.5, z + Math.cos(yaw) * 0.8, 8, 0.3, 0.4, 0.3, 0.03);
                sound("item.shield.block", 1.2F, 0.7F);
            } else {
                super.hurt(ev, atk);
                if (m > 1) Fx.send(level, AshMobs.EMBER, x - Math.sin(yaw) * 0.4, y + 1.5, z - Math.cos(yaw) * 0.4, 10, 0.2, 0.4, 0.2, 0.05);
            }
        }

        @Override void die() {
            Fx.send(level, AshMobs.EMBER, x, y + 1.2, z, 35, 0.6, 0.9, 0.6, 0.15);
            Fx.send(level, Fx.blockParticle(Fx.state("coal_block")), x, y + 1.2, z, 30, 0.6, 0.9, 0.6, 0.1);
            sound("entity.iron_golem.death", 1.0F, 0.6F);
        }
    }

    // ================================================================ Хранитель Углей
    /**
     * Мини-босс арены (зал 20): 360 ХП. Щит спереди (20%), спина 130%. Молот: замах 1,3 с, 11 по конусу. Удар о землю: круг радиуса 6,5 растёт 1,7 с, 9 урона и отброс
     * (прыжок спасает), потом Хранитель вонзён в пол 3,5 с и открыт со всех сторон (180%). Круги углей под игроками: 1,4 с, 6 урона и поджог. Ниже 50% ХП (один удар порог не
     * перескакивает) - ярость: заголовок, быстрее, больше кругов, оглушение короче. Смерть зачищает зал: сундук с Угольным ключом.
     */
    static final class Keeper extends AMob {
        static final int IDLE = 0, HAMMER = 1, RECOVER = 2, SLAM = 3, STUN = 4, CIRCLE = 5;
        int st = IDLE, tm = 0, cd = 50, circleCd = 120;
        boolean rage;
        final List<double[]> circles = new ArrayList<>();
        { half = 0.7; leash = 24; }

        boolean up() { return st == IDLE || st == RECOVER || st == CIRCLE; }
        double k() { return rage ? 0.8 : 1.0; }

        @Override void act() {
            pose.lift += (((st == HAMMER || st == SLAM) ? 1 : 0) - pose.lift) * 0.3;
            pose.stun += ((st == STUN ? 1 : 0) - pose.stun) * 0.25;
            pose.crack += ((rage ? 1 : 0) - pose.crack) * 0.1;
            pose.strike = Math.max(0, pose.strike - 0.08);
            if (!rage && AshLogic.enraged(host.m_21223_(), host.m_21233_())) enrage();
            switch (st) {
                case HAMMER -> { hammerStep(); return; }
                case RECOVER -> { if (--tm <= 0) st = IDLE; return; }
                case SLAM -> { slamStep(); return; }
                case STUN -> { stunStep(); return; }
                case CIRCLE -> { circleStep(); return; }
                default -> { }
            }
            pose.atk = Math.max(0, pose.atk - 0.12);
            pose.charge = Math.max(0, pose.charge - 0.1);
            ServerPlayer p = notice(30, 6);
            if (p == null) return;
            if (cd > 0) cd--;
            if (circleCd > 0) circleCd--;
            face(p.m_20185_(), p.m_20189_(), 0.08);
            double d = hd(p.m_20185_(), p.m_20189_());
            if (circleCd == 0 && d > 3.5 && startCircles()) return;
            if (cd == 0) {
                if (d < AshLogic.KEEPER_REACH - 0.4 && Math.abs(p.m_20186_() - y) < 3) {
                    if (RiftMobs.RNG.nextInt(100) < 35) startSlam(); else startHammer();
                    return;
                }
                if (d < 9 && RiftMobs.RNG.nextInt(100) < 4) { startSlam(); return; }
            }
            if (d > 2.8 && walk(p.m_20185_(), p.m_20189_(), AshLogic.KEEPER_SPEED * (rage ? 1.3 : 1.0) * speedMult(), 3)) pose.ph += 0.25;
        }

        void enrage() {
            rage = true;
            sound("entity.ravager.roar", 2.0F, 0.6F);
            Fx.send(level, AshMobs.EMBER, x, y + 2.0, z, 60, 1.0, 1.5, 1.0, 0.2);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || hd(p.m_20185_(), p.m_20189_()) > 40) continue;
                Cmd.run(level, x, y, z, "title " + p.m_20148_() + " times 5 40 15");
                Cmd.run(level, x, y, z, "title " + p.m_20148_() + " subtitle {\"translate\":\"explosionwand.mob.ash_keeper_rage.sub\",\"color\":\"gray\",\"italic\":true}");
                Cmd.run(level, x, y, z, "title " + p.m_20148_() + " title {\"translate\":\"explosionwand.mob.ash_keeper_rage\",\"color\":\"gold\",\"bold\":true}");
            }
        }

        void startHammer() { st = HAMMER; tm = (int) Math.round(AshLogic.KEEPER_HAMMER_WIND * k()); sound("entity.iron_golem.attack", 1.4F, 0.5F); }

        void hammerStep() {
            int tot = (int) Math.round(AshLogic.KEEPER_HAMMER_WIND * k());
            pose.atk = 1 - tm / (double) tot;
            ServerPlayer q = nearest(8, 4);
            if (q != null) face(q.m_20185_(), q.m_20189_(), 0.04);
            if (tm % 2 == 0) arc(AshLogic.KEEPER_REACH, AshMobs.EMBER);
            if (--tm > 0) return;
            pose.atk = 0; pose.strike = 1; st = RECOVER; tm = (int) Math.round(AshLogic.KEEPER_HAMMER_REC * k()); cd = (int) Math.round(AshLogic.KEEPER_CD * k());
            for (ServerPlayer p : ps) if (hittable(p, AshLogic.KEEPER_REACH, 3.0) && inFront(p, 0.1)) hitPlayer(p, AshLogic.KEEPER_HAMMER, 1.0);
            Fx.send(level, Fx.blockParticle(Fx.state("blackstone")), x + Math.sin(yaw) * 3.0, y + 0.2, z + Math.cos(yaw) * 3.0, 25, 0.6, 0.1, 0.6, 0.15);
            sound("entity.generic.explode", 1.4F, 0.7F);
        }

        void startSlam() {
            st = SLAM; tm = AshLogic.KEEPER_SLAM_WIND;
            sound("entity.ravager.roar", 1.6F, 0.7F);
            for (ServerPlayer p : ps) if (p.m_6084_() && hd(p.m_20185_(), p.m_20189_()) < 40) AshMobs.bar(p, "explosionwand.mob.ash_keeper_slam");
        }

        void slamStep() {
            double f = 1 - tm / (double) AshLogic.KEEPER_SLAM_WIND;
            pose.charge = f;
            if (tm % 2 == 0) { ring(AshMobs.EMBER, x, y + 0.1, z, AshLogic.KEEPER_SLAM_R, 40); ring(AshMobs.GOLD, x, y + 0.1, z, AshLogic.KEEPER_SLAM_R * f, 24); }
            if (tm % 10 == 0 && tm > 0) sound("block.anvil.land", 1.0F, 0.5F + (float) f * 0.5F);
            if (--tm > 0) return;
            Fx.send(level, Fx.particle("explosion"), x, y + 0.5, z, 3, 1.5, 0.1, 1.5, 0);
            Fx.send(level, Fx.blockParticle(Fx.state("blackstone")), x, y + 0.3, z, 60, AshLogic.KEEPER_SLAM_R * 0.4, 0.2, AshLogic.KEEPER_SLAM_R * 0.4, 0.2);
            Fx.send(level, AshMobs.EMBER, x, y + 0.4, z, 70, AshLogic.KEEPER_SLAM_R * 0.5, 0.2, AshLogic.KEEPER_SLAM_R * 0.5, 0.1);
            sound("entity.generic.explode", 2.0F, 0.5F);
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_() || !AshLogic.waveHits(hd(p.m_20185_(), p.m_20189_()), p.m_20186_() - y, AshLogic.KEEPER_SLAM_R)) continue;
                p.m_6469_(level.m_269111_().m_269333_(host), scaled(AshLogic.KEEPER_SLAM));
                AshMobs.kick(p, x, z, 1.0, 0.5);
            }
            st = STUN; tm = rage ? AshLogic.KEEPER_STUN_RAGE : AshLogic.KEEPER_STUN; pose.charge = 0; pose.strike = 1;
            for (ServerPlayer p : ps) if (p.m_6084_() && hd(p.m_20185_(), p.m_20189_()) < 40) AshMobs.bar(p, "explosionwand.mob.ash_keeper_open");
        }

        void stunStep() {
            if (tm % 5 == 0) Fx.send(level, Fx.particle("smoke"), x, y + 2.4, z, 4, 0.5, 0.3, 0.5, 0.02);
            if (--tm > 0) return;
            st = IDLE; cd = (int) Math.round(AshLogic.KEEPER_CD * k());
            sound("entity.iron_golem.repair", 1.2F, 0.6F);
        }

        boolean startCircles() {
            circles.clear();
            int max = rage ? 4 : AshLogic.MAX_CIRCLES;
            for (ServerPlayer q : ps) {
                if (circles.size() >= max || !q.m_6084_() || q.m_5833_() || hd(q.m_20185_(), q.m_20189_()) > 24 || Math.abs(q.m_20186_() - y) > 5) continue;
                circles.add(new double[]{q.m_20185_(), q.m_20186_(), q.m_20189_()});
            }
            if (circles.isEmpty()) { circleCd = 40; return false; }
            if (rage) circles.add(new double[]{x + Math.sin(yaw) * 5, y, z + Math.cos(yaw) * 5});
            st = CIRCLE; tm = (int) Math.round(AshLogic.KEEPER_CIRCLE_WIND * k());
            sound("entity.evoker.prepare_attack", 1.5F, 0.6F);
            for (ServerPlayer p : ps) if (p.m_6084_() && hd(p.m_20185_(), p.m_20189_()) < 40) AshMobs.bar(p, "explosionwand.mob.ash_keeper_circle");
            return true;
        }

        void circleStep() {
            int tot = (int) Math.round(AshLogic.KEEPER_CIRCLE_WIND * k());
            double f = 1 - tm / (double) tot;
            pose.charge = f * 0.6;
            if (tm % 2 == 0) for (double[] c : circles) { ring(AshMobs.EMBER, c[0], c[1] + 0.1, c[2], AshLogic.KEEPER_CIRCLE_R, 26); ring(AshMobs.GOLD, c[0], c[1] + 0.1, c[2], AshLogic.KEEPER_CIRCLE_R * f, 14); }
            if (--tm > 0) return;
            st = IDLE; circleCd = (int) Math.round(AshLogic.KEEPER_CIRCLE_CD * k()); pose.charge = 0;
            for (double[] c : circles) {
                Fx.send(level, Fx.particle("flame"), c[0], c[1] + 0.3, c[2], 40, AshLogic.KEEPER_CIRCLE_R * 0.5, 0.2, AshLogic.KEEPER_CIRCLE_R * 0.5, 0.07);
                Fx.sound(level, c[0], c[1], c[2], "entity.blaze.shoot", 1.2F, 0.6F);
            }
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_()) continue;
                for (double[] c : circles) {
                    double dx = p.m_20185_() - c[0], dz = p.m_20189_() - c[2];
                    if (dx * dx + dz * dz < AshLogic.KEEPER_CIRCLE_R * AshLogic.KEEPER_CIRCLE_R && Math.abs(p.m_20186_() - c[1]) < 2.5) {
                        p.m_6469_(level.m_269111_().m_269333_(host), scaled(AshLogic.KEEPER_CIRCLE));
                        p.m_20254_(3);
                        break;
                    }
                }
            }
            circles.clear();
        }

        @Override void hurt(LivingHurtEvent ev, Entity atk) {
            double cos = 1;
            if (atk != null) {
                double ax = atk.m_20185_() - x, az = atk.m_20189_() - z, d = Math.sqrt(ax * ax + az * az);
                if (d > 1e-3) cos = (ax * Math.sin(yaw) + az * Math.cos(yaw)) / d;
            }
            double m = AshLogic.keeperMult(cos, up(), st == STUN);
            float amt = (float) (ev.getAmount() * m);
            if (!rage) amt = AshLogic.clampPhase(host.m_21223_(), amt, host.m_21233_() * (float) AshLogic.KEEPER_ENRAGE);
            ev.setAmount(amt);
            if (m < 1) {
                Fx.send(level, AshMobs.GOLD, x + Math.sin(yaw) * 1.0, y + 2.0, z + Math.cos(yaw) * 1.0, 10, 0.4, 0.5, 0.4, 0.03);
                sound("item.shield.block", 1.5F, 0.6F);
            } else {
                super.hurt(ev, atk);
                if (m > 1) Fx.send(level, AshMobs.EMBER, x, y + 2.0, z, 12, 0.4, 0.6, 0.4, 0.06);
            }
        }

        @Override void die() {
            Fx.send(level, AshMobs.EMBER, x, y + 2.0, z, 90, 1.0, 1.6, 1.0, 0.25);
            Fx.send(level, Fx.blockParticle(Fx.state("coal_block")), x, y + 2.0, z, 60, 1.0, 1.6, 1.0, 0.15);
            sound("entity.iron_golem.death", 2.0F, 0.4F);
            sound("block.beacon.deactivate", 1.5F, 0.6F);
            Mats.drop(level, Mats.Dungeon.ASH, 1, 2, x, y, z);
            for (ServerPlayer p : ps) if (p.m_6084_() && hd(p.m_20185_(), p.m_20189_()) < 40) AshMobs.bar(p, "explosionwand.mob.ash_keeper_dead");
        }
    }

    // ================================================================ Блуждающий огонёк
    /**
     * Обычный мир, ночью у башни Святилища (не больше 2): светится и манит - пока игрок рядом (до 12 блоков), плывёт к своей цели за 26-44 блока, иначе ждёт.
     * Исход выбран при появлении: у цели сундук с добычей (45%) или ловушка (55%): огонёк вспыхивает (круг 5 блоков, 1,4 с), поджигает всех в круге
     * и призывает 3 Искры (40 с). Хост - летучая мышь (мирный режим её не стирает); в мирном режиме ловушка только пугает. Днём и через 5 минут угасает.
     */
    static final class Wisp extends AMob {
        static final int LEAD = 0, SPRING = 1;
        boolean trap = RiftMobs.RNG.nextInt(100) < AshLogic.TRAP_PERCENT;
        int st = LEAD, tm = 0, tries = 0, stuck = 0;
        long born = 0;
        double dx, dz, dgy, hover = 0;
        boolean hasDest;
        String home;   // ключ Святилища, у которого появился огонёк (хранится в теге хоста ewwh_<ключ>, читается в RiftMobs.adopt; null - только у огонька без Святилища: общий ключ "wild")
        { leash = 120; }

        @Override void act() {
            if (born == 0) born = RiftMobs.now;
            pose.aim = trap ? 1 : 0;
            if ((!level.m_46462_() && RiftMobs.RNG.nextInt(40) == 0) || RiftMobs.now - born > AshLogic.WISP_LIFE) { fade(); return; }
            if (!hasDest) { pickDest(); return; }
            if (t % 5 == 1) hover = Dungeons.groundY(level, (int) Math.floor(x), (int) Math.floor(z)) + 1.6;
            double bob = Math.sin(t * 0.1) * 0.15;
            if (st == SPRING) { springStep(); return; }
            pose.charge = Math.max(0, pose.charge - 0.1);
            if (t % 4 == 0) Fx.send(level, Fx.particle("end_rod"), x, y + 0.3, z, 1, 0.1, 0.1, 0.1, 0.0);
            ServerPlayer p = nearest(30, 25);
            if (p == null || !AshLogic.wispLeads(hd(p.m_20185_(), p.m_20189_()))) {
                fly(x, hover + bob, z, 0.05);
                if (p != null) face(p.m_20185_(), p.m_20189_(), 0.1);
                return;
            }
            double d = Math.hypot(dx - x, dz - z);
            face(dx, dz, 0.2);
            if (d > AshLogic.ARRIVE) {
                if (fly(dx, hover + bob, dz, AshLogic.WISP_SPEED)) stuck = 0;
                else if (++stuck > 60) fade();
                return;
            }
            if (trap) {
                st = SPRING; tm = AshLogic.TRAP_WIND;
                sound("entity.blaze.ambient", 1.4F, 0.5F);
                for (ServerPlayer q : ps) if (q.m_6084_() && hd(q.m_20185_(), q.m_20189_()) < 20) AshMobs.bar(q, "explosionwand.mob.ash_wisp_trap");
            } else reward();
        }

        void pickDest() {
            if (++tries > 20) { RiftMobs.retire(this); return; }
            double a = RiftMobs.RNG.nextDouble() * Math.PI * 2, r = AshLogic.DEST_MIN + RiftMobs.RNG.nextDouble() * (AshLogic.DEST_MAX - AshLogic.DEST_MIN);
            double tx = x + Math.cos(a) * r, tz = z + Math.sin(a) * r;
            if (!RiftBeetles.chunkLoaded(level, tx, tz)) return;
            int ix = (int) Math.floor(tx), iz = (int) Math.floor(tz), gy = Dungeons.groundY(level, ix, iz);
            if (Math.abs(gy - y) > 14 || Dungeons.wet(level, ix, gy - 1, iz) || Dungeons.near(level, tx, gy, tz, 8)) return;
            dx = tx; dz = tz; dgy = gy; hasDest = true;
        }

        void springStep() {
            tm--;
            double f = 1 - tm / (double) AshLogic.TRAP_WIND;
            pose.charge = f;
            if (tm % 2 == 0) { ring(AshMobs.EMBER, x, dgy + 0.2, z, AshLogic.TRAP_R, 30); ring(AshMobs.GOLD, x, dgy + 0.2, z, AshLogic.TRAP_R * f, 16); }
            if (tm % 8 == 0) sound("block.fire.ambient", 1.2F, 0.6F + (float) f * 0.6F);
            if (tm > 0) return;
            double tx = x, tz = z; ServerLevel lv = level;
            Fx.send(level, Fx.particle("flame"), tx, dgy + 0.5, tz, 60, AshLogic.TRAP_R * 0.5, 0.3, AshLogic.TRAP_R * 0.5, 0.08);
            sound("entity.generic.explode", 1.4F, 0.9F);
            if (!peaceful(level)) {
                for (ServerPlayer p : ps) {
                    if (!p.m_6084_() || p.m_5833_() || hd(p.m_20185_(), p.m_20189_()) > AshLogic.TRAP_R) continue;
                    p.m_6469_(level.m_269111_().m_269333_(host), scaled(AshLogic.TRAP_DMG));
                    p.m_20254_(3);
                }
                CityMobsB.QUEUE.add(() -> {
                    for (int i = 0; i < AshLogic.TRAP_SPARKS; i++) {
                        double a = i * Math.PI * 2 / AshLogic.TRAP_SPARKS + 0.5;
                        RiftMobs.Mob m = RiftMobs.spawnKind(lv, Kind.ASH_SPARK, tx + Math.cos(a) * 3, dgy + 2.0, tz + Math.sin(a) * 3, null, "\"" + AshLogic.TRAP_TAG + "\"");
                        if (m instanceof AMob am) { am.expire = RiftMobs.now + AshLogic.TRAP_SPARK_LIFE; am.summoned = true; }
                    }
                });
            }
            RiftMobs.retire(this);
        }

        void reward() {
            ServerLevel lv = level; int ix = (int) Math.floor(dx), iz = (int) Math.floor(dz), gy = (int) dgy;
            // не больше одного сундука за ночь на Святилище (защита от фермы): сверх лимита - только 3 Пепельного угля на землю
            long night = AshLogic.nightId(level.m_46468_());
            String key = home == null ? "wild" : home;
            boolean chest = AshLogic.chestAllowed(WISP_NIGHT.get(key), night);
            if (chest) WISP_NIGHT.put(key, night);
            List<String> loot = new ArrayList<>(Mats.chestLoot(Mats.Dungeon.ASH, 1, RiftMobs.RNG));
            loot.add((2 + RiftMobs.RNG.nextInt(4)) + " minecraft:coal");
            loot.add((1 + RiftMobs.RNG.nextInt(3)) + " minecraft:gold_nugget");
            CityMobsB.QUEUE.add(() -> {   // сундук только в воздух на твёрдом грунте, без светокамня под ним; иначе добыча падает на землю
                if (!chest || !Loot.wild(lv, ix, gy, iz, "explosionwand.mob.ash_wisp_chest_name", loot)) Mats.drop(lv, Mats.Dungeon.ASH, 0, 3, ix + 0.5, gy, iz + 0.5);
            });
            Fx.send(level, AshMobs.GOLD, x, y + 0.3, z, 30, 0.4, 0.4, 0.4, 0.08);
            sound("block.amethyst_block.chime", 1.4F, 1.2F);
            for (ServerPlayer q : ps) if (q.m_6084_() && hd(q.m_20185_(), q.m_20189_()) < 20) AshMobs.bar(q, "explosionwand.mob.ash_wisp_chest");
            RiftMobs.retire(this);
        }

        void fade() {
            Fx.send(level, Fx.particle("end_rod"), x, y + 0.3, z, 12, 0.2, 0.2, 0.2, 0.03);
            RiftMobs.retire(this);
        }

        @Override void die() {
            Fx.send(level, AshMobs.GOLD, x, y + 0.3, z, 14, 0.2, 0.2, 0.2, 0.06);
            sound("block.amethyst_block.break", 0.8F, 1.6F);
        }
    }

    /** Раз в WISP_PERIOD тиков у каждого Святилища с игроком рядом: ночью иногда появляется огонёк (не больше WISP_CAP), вне данжа и воды. */
    static void tickWorld(ServerLevel sl, DungeonData data, List<ServerPlayer> ps, long now) {
        if (now % AshLogic.WISP_PERIOD != 0 || data.recs.isEmpty() || ps.isEmpty()) return;
        boolean night = sl.m_46462_();
        for (DungeonData.Rec r : data.recs.values()) {
            if (r.def() != Dungeons.ASH || r.partsDone == 0) continue;
            double cx = r.x, cz = r.z - 34;
            int have = 0;
            AABB box = new AABB(cx - 130, r.y - 60, cz - 130, cx + 130, r.y + 90, cz + 130);
            for (Entity e : sl.m_6249_(null, box, en -> en.m_19880_().contains("ewrk_" + Kind.ASH_WISP.id))) {
                if (!(e instanceof LivingEntity le) || !le.m_6084_()) continue;
                if (RiftMobs.MOBS.containsKey(e.m_20148_())) have++;
                else RiftMobs.remove(e);   // не усыновлён (scan видит хосты только рядом с игроками): убрать вместе с частями
            }
            if (!AshLogic.wispSpawns(night, have, RiftMobs.RNG.nextInt(100))) continue;
            List<ServerPlayer> near = new ArrayList<>();
            for (ServerPlayer p : ps) {
                if (!p.m_6084_() || p.m_5833_() || p.m_7500_()) continue;
                if (Math.hypot(p.m_20185_() - cx, p.m_20189_() - cz) <= AshLogic.WISP_RANGE) near.add(p);
            }
            if (near.isEmpty()) continue;
            ServerPlayer p = near.get(RiftMobs.RNG.nextInt(near.size()));
            double ang = RiftMobs.RNG.nextDouble() * Math.PI * 2, rad = AshLogic.WISP_MIN + RiftMobs.RNG.nextDouble() * (AshLogic.WISP_MAX - AshLogic.WISP_MIN);
            double bx = cx + Math.cos(ang) * rad, bz = cz + Math.sin(ang) * rad;
            if (Math.hypot(bx - p.m_20185_(), bz - p.m_20189_()) > 64 || !RiftBeetles.chunkLoaded(sl, bx, bz)) continue;
            int ix = (int) Math.floor(bx), iz = (int) Math.floor(bz), by = Dungeons.groundY(sl, ix, iz);
            if (Math.abs(by - p.m_20186_()) > 20 || Dungeons.wet(sl, ix, by - 1, iz) || Dungeons.near(sl, bx, by, bz, 8)) continue;
            String ht = "\"ewwh_" + r.key.replace("\\", "").replace("\"", "") + "\"";
            if (RiftMobs.spawnKind(sl, Kind.ASH_WISP, bx, by + 1.6, bz, null, ht) instanceof Wisp w) w.home = r.key;
        }
    }
}
