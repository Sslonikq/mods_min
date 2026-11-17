package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.*;

/**
 * Перчатка Бесконечности: семь камней из миров мода, способность камня - по ПКМ, смена камня - Shift+ПКМ.
 * Текущий камень лежит в NBT стака (int ewStone). Порядок и перезарядки - GauntletLogic.
 * Камни: rift (прыжок), sleep (сон), ash (огненный луч), eclipse (день/ночь), sky (взлёт и удар), deep (превращение блоков), snap (Щелчок).
 */
public class Gauntlet {
    static final Random RNG = Fx.RNG;
    static DustParticleOptions[] DUST;

    /** Защита от урона падением после прыжка: гасим урон в LivingFallEvent (onFall) и сбрасываем fallDistance, пока не приземлится или не истекут 10 с. */
    static final class Safe {
        final ServerLevel level; final ServerPlayer p; int t = 0;
        Safe(ServerLevel level, ServerPlayer p) { this.level = level; this.p = p; }
        boolean tick() {
            t++;
            if (!p.m_6084_() || p.m_9236_() != level || t > 200) return true;
            p.f_19789_ = 0.0F;
            return t > 4 && p.m_20096_();
        }
    }
    static final List<Safe> SAFE = new ArrayList<>();

    static void init() {
        if (DUST != null) return;
        DUST = new DustParticleOptions[GauntletLogic.count()];
        for (int i = 0; i < DUST.length; i++) {
            int c = GauntletLogic.color(i);
            DUST[i] = new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255.0F, ((c >> 8) & 255) / 255.0F, (c & 255) / 255.0F), 1.5F);
        }
    }

    static int stone(ItemStack stack) {
        if (stack.m_41783_() == null) return 0;
        return GauntletLogic.clamp(stack.m_41783_().m_128451_(GauntletLogic.NBT_KEY));
    }

    /** Игровое время для перезарядок: у Верхнего мира через сервер (не зависит от измерения); на клиенте - время уровня. */
    static long now(Level l) {
        if (l instanceof ServerLevel sl && sl.m_7654_() != null) return sl.m_7654_().m_129783_().m_46467_();
        return l.m_46467_();
    }

    /** Остаток перезарядки камня в стаке, тики (0 - готов). */
    static int left(ItemStack stack, int stone, long now) {
        if (stack.m_41783_() == null || !stack.m_41783_().m_128441_(GauntletLogic.cdKey(stone))) return 0;
        return GauntletLogic.left(stack.m_41783_().m_128454_(GauntletLogic.cdKey(stone)), now, stone);
    }

    static Component stoneName(int stone) { return Component.m_237115_("explosionwand.gauntlet.stone." + GauntletLogic.key(stone)); }

    /** Прыжок Разлома: пока игрок в SAFE (до приземления или 10 с), урон падением гасится. */
    static void onFall(LivingFallEvent ev) {
        if (SAFE.isEmpty() || !(ev.getEntity() instanceof ServerPlayer p)) return;
        for (Safe s : SAFE) if (s.p == p) { ev.setDamageMultiplier(0.0F); ev.setDistance(0.0F); return; }
    }

    static boolean alive(ServerLevel l, ServerPlayer p) { return p.m_6084_() && p.m_9236_() == l; }

    static void tick(ServerLevel l) { Ticker.run(SAFE, l, s -> s.level, Safe::tick); }

    // ---------------------------------------------------------------- смена камня
    static void cycle(ServerLevel l, ServerPlayer p, ItemStack stack) {
        init();
        int n = GauntletLogic.next(stone(stack));
        stack.m_41784_().m_128405_(GauntletLogic.NBT_KEY, n);
        p.m_5661_(Component.m_237115_("explosionwand.gauntlet.stone." + GauntletLogic.key(n)), true);
        Vec3 v = p.m_20182_();
        Fx.send(l, DUST[n], v.f_82479_, v.f_82480_ + 1.2, v.f_82481_, 24, 0.45, 0.45, 0.45, 0);
        Fx.send(l, Fx.particle("end_rod"), v.f_82479_, v.f_82480_ + 1.2, v.f_82481_, 6, 0.4, 0.4, 0.4, 0.02);
        Fx.sound(l, v.f_82479_, v.f_82480_, v.f_82481_, "block.amethyst_block.chime", 1.0F, 0.7F + 0.1F * n);
    }

    /** Способность камня. true - сработала (запускать перезарядку). */
    static boolean use(ServerLevel l, ServerPlayer p, int stone) {
        init();
        Vec3 v = p.m_20182_();
        boolean ok = switch (stone) {
            case 0 -> rift(l, p);
            case 1 -> sleep(l, p);
            case 2 -> ash(l, p);
            case 3 -> eclipse(l, p);
            case 4 -> sky(l, p);
            case 5 -> deep(l, p);
            default -> snap(l, p);
        };
        if (ok && stone != 0) Fx.send(l, DUST[stone], v.f_82479_, v.f_82480_ + 1.2, v.f_82481_, 16, 0.4, 0.4, 0.4, 0);
        return ok;
    }

    /** Кого камни могут трогать: живые не-игроки; существа мода только если это мобы данжа или приспешники боссов. */
    static boolean affects(Entity e, Entity owner) {
        return e instanceof LivingEntity && e != owner && !(e instanceof Player) && e.m_6084_() && !e.m_5833_()
                && !Minions.bossPart(e) && (!Cmd.hasTagPrefix(e, "ew") || Minions.dungeonMob(e) || Minions.minion(e));
    }

    static List<Entity> around(ServerLevel l, ServerPlayer p, Vec3 c, double r) {
        return l.m_6249_(p, new AABB(c.f_82479_ - r, c.f_82480_ - r, c.f_82481_ - r, c.f_82479_ + r, c.f_82480_ + r, c.f_82481_ + r), e -> affects(e, p) && e.m_20182_().m_82557_(c) <= r * r);
    }

    // ---------------------------------------------------------------- 0. Разлом: прыжок
    static boolean rift(ServerLevel l, ServerPlayer p) {
        Aim aim = Aim.of(l, p, 64.0D);
        double dist = aim.hit ? aim.eye.m_82554_(aim.target) : 64.0D;
        Vec3 spot = null;
        // отступ 0.7 назад по лучу от точки попадания; место свободно, если хитбокс игрока 0.6x1.8 ни с чем не пересекается
        for (double d = (aim.hit ? dist - 0.7D : dist); d >= 1.5 && spot == null; d -= 0.4) {
            Vec3 q = aim.eye.m_82549_(aim.look.m_82490_(d));
            double fy = Math.floor(q.f_82480_ - 1.62D);
            if (fy < -60.0D || q.f_82480_ > 316.0D) continue;
            AABB body = new AABB(q.f_82479_ - 0.3D, fy, q.f_82481_ - 0.3D, q.f_82479_ + 0.3D, fy + 1.8D, q.f_82481_ + 0.3D);
            if (l.m_45756_(p, body)) spot = new Vec3(q.f_82479_, fy, q.f_82481_);
        }
        if (spot == null) return false;
        Vec3 from = p.m_20182_();
        Fx.send(l, Fx.particle("reverse_portal"), from.f_82479_, from.f_82480_ + 1, from.f_82481_, 60, 0.4, 0.8, 0.4, 0.2);
        Fx.send(l, DUST[0], from.f_82479_, from.f_82480_ + 1, from.f_82481_, 20, 0.4, 0.8, 0.4, 0);
        Fx.sound(l, from.f_82479_, from.f_82480_, from.f_82481_, "entity.enderman.teleport", 1.0F, 1.3F);
        p.m_8999_(l, spot.f_82479_, spot.f_82480_, spot.f_82481_, p.m_146908_(), p.m_146909_());
        p.m_20256_(new Vec3(0, 0, 0)); p.f_19864_ = true; p.f_19789_ = 0.0F;
        SAFE.add(new Safe(l, p));
        Fx.send(l, Fx.particle("reverse_portal"), spot.f_82479_, spot.f_82480_ + 1, spot.f_82481_, 60, 0.4, 0.8, 0.4, 0.2);
        Fx.send(l, DUST[0], spot.f_82479_, spot.f_82480_ + 1, spot.f_82481_, 20, 0.4, 0.8, 0.4, 0);
        Fx.sound(l, spot.f_82479_, spot.f_82480_, spot.f_82481_, "entity.enderman.teleport", 1.0F, 0.8F);
        return true;
    }

    // ---------------------------------------------------------------- 1. Сон: все мобы вокруг засыпают
    static boolean sleep(ServerLevel l, ServerPlayer p) {
        Vec3 c = p.m_20182_();
        DreamStaff.init();
        for (int i = 0; i < 40; i++) { double a = i * Math.PI / 20; Fx.send(l, DUST[1], c.f_82479_ + Math.cos(a) * 16, c.f_82480_ + 0.3, c.f_82481_ + Math.sin(a) * 16, 1, 0, 0.05, 0, 0); }
        Fx.sound(l, c.f_82479_, c.f_82480_, c.f_82481_, "entity.elder_guardian.curse", 0.8F, 1.6F);
        for (Entity e : around(l, p, c, 16.0D)) DreamStaff.sleep(l, (LivingEntity) e, 160);
        return true;
    }

    // ---------------------------------------------------------------- 2. Пепел: огненный луч
    static boolean ash(ServerLevel l, ServerPlayer p) {
        Aim aim = Aim.of(l, p, 40.0D);
        double len = aim.block != null ? Math.min(40.0D, aim.eye.m_82554_(aim.block.m_82450_())) : 40.0D;
        Vec3 eye = aim.eye, look = aim.look;
        double nextBoom = 5.0D;            // «взрывы» луча - только частицы и звук, урон даёт сам луч по фильтру affects
        for (double d = 1.2D; d < len; d += 0.5D) {
            Vec3 v = eye.m_82549_(look.m_82490_(d));
            Fx.send(l, Fx.particle("flame"), v.f_82479_, v.f_82480_ - 0.2, v.f_82481_, 2, 0.08, 0.08, 0.08, 0.01);
            Fx.send(l, DUST[2], v.f_82479_, v.f_82480_ - 0.2, v.f_82481_, 1, 0.1, 0.1, 0.1, 0);
            if (d >= nextBoom) {
                nextBoom += 4.0D;
                Fx.send(l, Fx.particle("explosion"), v.f_82479_, v.f_82480_, v.f_82481_, 1, 0, 0, 0, 0);
                Fx.sound(l, v.f_82479_, v.f_82480_, v.f_82481_, "entity.generic.explode", 0.5F, 1.3F);
            }
        }
        Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.blaze.shoot", 1.5F, 0.6F);
        Fx.sound(l, p.m_20185_(), p.m_20186_(), p.m_20189_(), "item.firecharge.use", 1.2F, 0.7F);
        Vec3 end = eye.m_82549_(look.m_82490_(len));
        AABB box = new AABB(Math.min(eye.f_82479_, end.f_82479_) - 2, Math.min(eye.f_82480_, end.f_82480_) - 2, Math.min(eye.f_82481_, end.f_82481_) - 2,
                Math.max(eye.f_82479_, end.f_82479_) + 2, Math.max(eye.f_82480_, end.f_82480_) + 2, Math.max(eye.f_82481_, end.f_82481_) + 2);
        for (Entity e : l.m_6249_(p, box, x -> affects(x, p))) {
            Vec3 r = e.m_20182_().m_82520_(0, e.m_20206_() / 2, 0).m_82546_(eye);
            double al = r.m_82526_(look);
            if (al < 0 || al > len + 1.0D) continue;
            double rad = 1.5D + e.m_20205_() / 2;
            if (r.m_82546_(look.m_82490_(al)).m_82556_() > rad * rad) continue;
            e.m_6469_(l.m_269111_().m_269075_(p), 20.0F);
            RiftMobs.ignite(e, 6);
        }
        return true;
    }

    // ---------------------------------------------------------------- 3. Затмение: день <-> ночь
    static boolean eclipse(ServerLevel l, ServerPlayer p) {
        Vec3 c = p.m_20182_();
        if (l.m_46472_() == Level.f_46428_) {
            long t = Math.floorMod(l.m_46468_(), 24000L);
            Cmd.run(l, c.f_82479_, c.f_82480_, c.f_82481_, t < 12500L ? "time set midnight" : "time set day");
        }
        Fx.sound(l, c.f_82479_, c.f_82480_, c.f_82481_, "block.beacon.activate", 1.5F, 0.6F);
        Fx.sound(l, c.f_82479_, c.f_82480_, c.f_82481_, "entity.illusioner.cast_spell", 1.2F, 0.6F);
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0F, 0.8F, 0.2F), 1.6F);
        for (int s = 0; s < 12; s++) {
            final double r = 3.0 + s * 3.0;
            Later.run(l, s + 1, () -> {
                if (!alive(l, p)) return;
                Vec3 q = p.m_20182_();
                int n = (int) (16 + r * 3);
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n, y = q.f_82480_ + 14 + r * 0.25;
                    double x = q.f_82479_ + Math.cos(a) * r, z = q.f_82481_ + Math.sin(a) * r;
                    Fx.send(l, i % 3 == 0 ? Fx.particle("end_rod") : (i % 3 == 1 ? Fx.particle("squid_ink") : gold), x, y, z, 1, 0.2, 0.3, 0.2, 0.01);
                }
            });
        }
        return true;
    }

    // ---------------------------------------------------------------- 4. Небо: взлёт и удар
    static boolean sky(ServerLevel l, ServerPlayer p) {
        Vec3 c = p.m_20182_();
        Fx.sound(l, c.f_82479_, c.f_82480_, c.f_82481_, "entity.firework_rocket.launch", 1.5F, 0.6F);
        MobEffect lev = Fx.effect("levitation");
        List<Entity> list = around(l, p, c, 16.0D);
        for (Entity e : list) {
            LivingEntity le = (LivingEntity) e;
            if (!RiftMobs.knock(e, 0, 1.4, 0) && lev != null) le.m_7292_(new MobEffectInstance(lev, 40, 5));
            Vec3 v = e.m_20182_();
            Fx.send(l, DUST[4], v.f_82479_, v.f_82480_ + 0.5, v.f_82481_, 10, 0.3, 0.4, 0.3, 0);
            Fx.send(l, Fx.particle("cloud"), v.f_82479_, v.f_82480_ + 0.2, v.f_82481_, 6, 0.3, 0.1, 0.3, 0.05);
        }
        Later.run(l, 40, () -> {
            if (!alive(l, p)) return;
            for (Entity e : list) {
                if (!e.m_6084_() || e.m_9236_() != l) continue;
                if (lev != null) ((LivingEntity) e).m_21195_(lev);
                if (!RiftMobs.knock(e, 0, -2.5, 0)) { e.m_20256_(new Vec3(0, -2.5, 0)); e.f_19864_ = true; }
            }
            Later.run(l, 8, () -> {
                if (!alive(l, p)) return;
                for (Entity e : list) {
                    if (!e.m_6084_() || e.m_9236_() != l) continue;
                    Vec3 v = e.m_20182_();
                    Fx.send(l, Fx.particle("explosion"), v.f_82479_, v.f_82480_ + 0.2, v.f_82481_, 1, 0, 0, 0, 0);
                    Fx.send(l, Fx.particle("cloud"), v.f_82479_, v.f_82480_ + 0.2, v.f_82481_, 10, 0.4, 0.1, 0.4, 0.1);
                    Fx.sound(l, v.f_82479_, v.f_82480_, v.f_82481_, "entity.generic.explode", 0.6F, 1.4F);
                    e.m_6469_(l.m_269111_().m_269075_(p), 10.0F);
                }
            });
        });
        return true;
    }

    // ---------------------------------------------------------------- 5. Глубины: превращение блоков
    static BlockState gem(int i) {
        return switch (i) {
            case 0 -> Fx.state("gold_block");
            case 1 -> Fx.state("amethyst_block");
            case 2 -> Fx.state("glass");
            case 3 -> Fx.state("copper_block");
            default -> Fx.state("quartz_block");
        };
    }

    static boolean deep(ServerLevel l, ServerPlayer p) {
        Aim aim = Aim.of(l, p, 48.0D);
        if (!aim.hit) return false;
        Vec3 t = aim.target;
        int cx = (int) Math.floor(t.f_82479_), cy = (int) Math.floor(t.f_82480_), cz = (int) Math.floor(t.f_82481_);
        if (aim.block != null && aim.entity == null) {       // точка попадания лежит на грани блока - берём сам блок
            BlockPos b = aim.block.m_82425_();
            cx = b.m_123341_(); cy = b.m_123342_(); cz = b.m_123343_();
        }
        final int R = 4;
        Dungeons.Shield sh = Dungeons.shield(l);
        long[] sph = Fx.sphere(R);
        int n = 0;
        for (long v : sph) {
            BlockPos pos = new BlockPos(cx + Fx.sx(v, R), cy + Fx.sy(v, R), cz + Fx.sz(v, R));
            if (sh.at(pos)) continue;
            BlockState st = l.m_8055_(pos);
            if (st.m_60795_() || !st.m_60819_().m_76178_() || l.m_7702_(pos) != null) continue;
            float hard = st.m_60800_(l, pos);
            if (hard < 0.0F || hard >= 50.0F || !st.m_60838_(l, pos)) continue;
            BlockState to = gem(RNG.nextInt(5));
            if (to == null || to == st) continue;
            l.m_7731_(pos, to, 3);
            n++;
            if (n % 6 == 0) Fx.send(l, DUST[5], pos.m_123341_() + 0.5, pos.m_123342_() + 1.0, pos.m_123343_() + 0.5, 1, 0.3, 0.2, 0.3, 0);
        }
        Fx.send(l, Fx.particle("end_rod"), cx + 0.5, cy + 0.5, cz + 0.5, 40, 2.5, 2.5, 2.5, 0.05);
        Fx.sound(l, cx, cy, cz, "block.amethyst_block.resonate", 2.0F, 0.6F);
        Fx.sound(l, cx, cy, cz, "block.beacon.power_select", 1.2F, 0.7F);
        return true;
    }

    // ---------------------------------------------------------------- 6. Щелчок: половина врагов рассыпается в пепел
    /** Щелчок не трогает ванильных боссов и мобов данжей (у них своя смерть и добыча). Приспешники боссов мода - обычные враги. */
    static boolean snapAllowed(Entity e) {
        if (Minions.dungeonMob(e)) return false;
        net.minecraft.resources.ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.m_6095_());
        return id == null || !GauntletLogic.snapSpared(id.m_135815_());
    }

    static boolean snap(ServerLevel l, ServerPlayer p) {
        Vec3 c = p.m_20182_();
        List<Entity> foes = new ArrayList<>();
        for (Entity e : around(l, p, c, 64.0D)) if (e instanceof Enemy && snapAllowed(e)) foes.add(e);
        List<Entity> fate = GauntletLogic.pickHalf(foes, RNG);
        for (int i = 0; i < 40; i++) {
            double[] u = Fx.unit();
            Fx.send(l, DUST[i % DUST.length], c.f_82479_, c.f_82480_ + 1.3, c.f_82481_, 0, u[0], u[1], u[2], 0.6);
        }
        Fx.send(l, Fx.particle("flash"), c.f_82479_, c.f_82480_ + 1.3, c.f_82481_, 1, 0, 0, 0, 0);
        Fx.sound(l, c.f_82479_, c.f_82480_, c.f_82481_, "entity.player.attack.crit", 1.5F, 0.6F);
        Fx.sound(l, c.f_82479_, c.f_82480_, c.f_82481_, "block.note_block.hat", 2.0F, 0.5F);
        Fx.sound(l, c.f_82479_, c.f_82480_, c.f_82481_, "entity.wither.spawn", 0.3F, 1.5F);
        if (fate.isEmpty()) return true;
        final int STEPS = 10;
        for (int s = 0; s <= STEPS; s++) {                   // пепел поднимается снизу вверх, потом мобы исчезают
            final int step = s;
            Later.run(l, 4 * s + 2, () -> {
                for (Entity e : fate) {
                    if (!e.m_6084_() || e.m_9236_() != l) continue;
                    Vec3 v = e.m_20182_();
                    double h = e.m_20206_() * Math.min(1.0, step / (double) STEPS);
                    Fx.send(l, Fx.particle("ash"), v.f_82479_, v.f_82480_ + h, v.f_82481_, 12, e.m_20205_() / 2, 0.1, e.m_20205_() / 2, 0.02);
                    Fx.send(l, DUST[6], v.f_82479_, v.f_82480_ + h, v.f_82481_, 4, e.m_20205_() / 2, 0.1, e.m_20205_() / 2, 0);
                    if (step == STEPS) { Fx.send(l, Fx.particle("poof"), v.f_82479_, v.f_82480_ + e.m_20206_() / 2, v.f_82481_, 14, 0.3, 0.4, 0.3, 0.05); e.m_6074_(); }
                }
            });
        }
        return true;
    }
}
