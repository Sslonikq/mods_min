package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.*;

/**
 * Игровая часть палочек Земли (4.11, часть B): Огонька, Морозца, Роста, Сбора, Возврата, Сигнальная, Копателя и выстрел Взрывной.
 * Параметры и геометрия - {@link EarthLogic}, временные блоки - {@link EarthData}, предмет - {@link EarthWandItem}.
 *
 * Правила: блоки меняем только в обычных измерениях (не в наших: Dims.isOurs) и мимо данжей (Dungeons.shield), бедрок не трогаем.
 * Огонёк в данже ставится без блока (только частицы и хук CityMobs.registerLight).
 *
 * ХУКИ ДЛЯ ЧАСТИ C: новая палочка = значение EarthLogic.Kind + ветка в {@link #use} + регистрация в ExplosionWandMod + lang/модель/рецепт.
 * Временный блок - {@link #addTemp}/{@link #tickTemps}; фоновые эффекты - списки PULLS/BEACONS/CASTS и их tick* в {@link #tick}.
 */
final class Earth {
    private Earth() {}
    static final Random RNG = Fx.RNG;

    // ================================================================ общее
    static boolean canEdit(ServerLevel sl) { return !Dims.isOurs(sl); }

    static void deny(ServerPlayer p, String key) { p.m_5661_(Component.m_237115_("explosionwand.earth." + key), true); }

    static BlockPos pos(double x, double y, double z) { return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)); }

    static String path(BlockState st) {
        ResourceLocation k = ForgeRegistries.BLOCKS.getKey(st.m_60734_());
        return k == null ? "" : k.m_135815_();
    }

    /** Применить палочку. Возвращает перезарядку в тиках (0 - ничего не вышло, перезарядки нет); множитель ArcaneSet.cd применяет предмет. */
    static int use(EarthLogic.Kind k, ServerLevel sl, ServerPlayer p) {
        switch (k) {
            case LIGHT: return light(sl, p);
            case FROST: return frost(sl, p);
            case GROWTH: return growth(sl, p);
            case GATHER: return gather(sl, p);
            case RETURN: return startReturn(sl, p);
            case SIGNAL: return signal(sl, p);
            case DIGGER: return dig(sl, p);
            case SPIKES: return spikes(sl, p);
            case SKIN: return skin(sl, p);
            case WIND: return wind(sl, p);
            case VINE: return vine(sl, p);
            case PULL: return pull(sl, p);
            case DECOY: return decoy(sl, p);
            case SPARKS: return sparks(sl, p);
            case HEAT: return heat(sl, p);
            default: return 0;
        }
    }

    // ================================================================ временные блоки (огонёк, лёд)
    static void addTemp(ServerLevel sl, EarthData d, BlockPos pos, int kind, long until, CityMobs.Light handle) {
        EarthData.Temp t = new EarthData.Temp(pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), kind, until);
        t.handle = handle;
        d.temps.add(t);
        d.m_77762_();
    }

    /** Убрать временный блок, если он ещё на месте (игрок мог его сломать или заменить). */
    static void expire(ServerLevel sl, EarthData.Temp t) {
        BlockPos pos = new BlockPos(t.x, t.y, t.z);
        Block cur = sl.m_8055_(pos).m_60734_();
        if (t.kind == EarthData.LIGHT && cur == Fx.block("light")) {
            sl.m_7731_(pos, Fx.state("air"), 3);
            Fx.send(sl, Fx.particle("smoke"), t.x + 0.5, t.y + 0.5, t.z + 0.5, 6, 0.1, 0.1, 0.1, 0.01);
        } else if (t.kind == EarthData.ICE && cur == Fx.block("ice")) {
            sl.m_7731_(pos, Fx.state("water"), 3);
            Fx.send(sl, Fx.particle("snowflake"), t.x + 0.5, t.y + 1.0, t.z + 0.5, 3, 0.2, 0.1, 0.2, 0.02);
        }
    }

    static void tickTemps(ServerLevel sl, long now) {
        EarthData d = EarthData.get(sl);
        if (d.temps.isEmpty()) return;
        boolean dirty = false;
        Iterator<EarthData.Temp> it = d.temps.iterator();
        while (it.hasNext()) {
            EarthData.Temp t = it.next();
            boolean dead = now >= t.until || (t.handle != null && t.handle.snuffed);
            if (!dead) {
                if (t.kind == EarthData.LIGHT) wispFx(sl, t.x + 0.5, t.y + 0.5, t.z + 0.5);
                continue;
            }
            if (t.handle != null && t.handle.snuffed) Fx.sound(sl, t.x + 0.5, t.y + 0.5, t.z + 0.5, "block.fire.extinguish", 0.6F, 1.4F);
            expire(sl, t);
            it.remove(); dirty = true;
        }
        if (dirty) d.m_77762_();
    }

    // ================================================================ Палочка Огонька
    static final class Wisp {
        final ServerLevel level; final double x, y, z; final long until; final CityMobs.Light handle;
        Wisp(ServerLevel level, double x, double y, double z, long until, CityMobs.Light handle) { this.level = level; this.x = x; this.y = y; this.z = z; this.until = until; this.handle = handle; }
    }
    /** Огоньки в данжах: без блока, только частицы и хук для мобов. */
    static final List<Wisp> WISPS = new ArrayList<>();

    static void wispFx(ServerLevel sl, double x, double y, double z) {
        Fx.send(sl, Fx.particle("end_rod"), x, y, z, 2, 0.08, 0.08, 0.08, 0.005);
        Fx.send(sl, Fx.particle("glow"), x, y, z, 1, 0.12, 0.12, 0.12, 0.0);
    }

    static int light(ServerLevel sl, ServerPlayer p) {
        Aim aim = Aim.of(sl, p, EarthLogic.LIGHT_RANGE);
        Vec3 t = aim.target;
        BlockPos pos = null;
        for (int i = 0; i < 5; i++) {      // от стены на шаг назад, пока клетка не станет свободной
            BlockPos c = pos(t.f_82479_, t.f_82480_, t.f_82481_);
            if (sl.m_8055_(c).m_60795_()) { pos = c; break; }
            t = t.m_82546_(aim.look.m_82490_(0.6D));
        }
        if (pos == null) { deny(p, "no_room"); return 0; }
        double cx = pos.m_123341_() + 0.5, cy = pos.m_123342_() + 0.5, cz = pos.m_123343_() + 0.5;
        long now = sl.m_46467_(), until = now + EarthLogic.LIGHT_TICKS;
        CityMobs.Light handle = CityMobs.registerLight(sl, cx, cy, cz, EarthLogic.LIGHT_RADIUS, EarthLogic.LIGHT_TICKS);
        boolean block = canEdit(sl) && !Dungeons.protectedAt(sl, pos);
        if (block) {
            EarthData d = EarthData.get(sl);
            int n = 0;
            for (EarthData.Temp x : d.temps) if (x.kind == EarthData.LIGHT) n++;
            for (Iterator<EarthData.Temp> it = d.temps.iterator(); it.hasNext() && n >= EarthLogic.LIGHT_MAX;) {   // старейший гаснет первым
                EarthData.Temp x = it.next();
                if (x.kind != EarthData.LIGHT) continue;
                if (x.handle != null) x.handle.snuff();
                expire(sl, x); it.remove(); n--;
            }
            sl.m_7731_(pos, Fx.state("light"), 3);
            addTemp(sl, d, pos, EarthData.LIGHT, until, handle);
        } else {
            while (WISPS.size() >= EarthLogic.LIGHT_MAX) { Wisp old = WISPS.remove(0); if (old.handle != null) old.handle.snuff(); }
            WISPS.add(new Wisp(sl, cx, cy, cz, until, handle));
        }
        Fx.send(sl, Fx.particle("end_rod"), cx, cy, cz, 14, 0.2, 0.2, 0.2, 0.05);
        aim.beam(sl, Fx.particle("end_rod"), 1.2D);
        Fx.sound(sl, cx, cy, cz, "block.amethyst_block.chime", 1.0F, 1.6F);
        return EarthLogic.Kind.LIGHT.cd;
    }

    static void tickWisps(ServerLevel sl, long now) {
        Iterator<Wisp> it = WISPS.iterator();
        while (it.hasNext()) {
            Wisp w = it.next();
            if (w.level != sl) continue;
            if (now >= w.until || (w.handle != null && w.handle.snuffed)) {
                if (w.handle != null && w.handle.snuffed) Fx.sound(sl, w.x, w.y, w.z, "block.fire.extinguish", 0.6F, 1.4F);
                it.remove();
            } else wispFx(sl, w.x, w.y, w.z);
        }
    }

    // ================================================================ Палочка Морозца
    static int frost(ServerLevel sl, ServerPlayer p) {
        Vec3 look = p.m_20252_(1.0F), eye = p.m_146892_();
        double R = EarthLogic.FROST_RANGE;
        MobEffect slow = Fx.effect("slowness");
        AABB box = new AABB(eye.f_82479_ - R, eye.f_82480_ - R, eye.f_82481_ - R, eye.f_82479_ + R, eye.f_82480_ + R, eye.f_82481_ + R);
        int hit = 0;
        for (Entity e : sl.m_6249_(p, box, (Entity en) -> en.m_6084_() && !en.m_5833_())) {
            if (!foe(e, p)) continue;
            LivingEntity le = (LivingEntity) e;
            Vec3 c = e.m_20182_();
            if (!EarthLogic.inCone(c.f_82479_ - eye.f_82479_, c.f_82480_ + e.m_20206_() * 0.5 - eye.f_82480_, c.f_82481_ - eye.f_82481_,
                    look.f_82479_, look.f_82480_, look.f_82481_, R, EarthLogic.FROST_COS)) continue;
            if (slow != null) le.m_7292_(new MobEffectInstance(slow, EarthLogic.FROST_SLOW_TICKS, EarthLogic.FROST_SLOW_AMP));
            e.m_146917_(Math.min(e.m_146891_(), e.m_146888_() + 60));
            Fx.send(sl, Fx.particle("snowflake"), c.f_82479_, c.f_82480_ + e.m_20206_() * 0.5, c.f_82481_, 10, 0.3, 0.4, 0.3, 0.03);
            hit++;
        }
        for (int i = 0; i < 40; i++) {      // облако холода перед игроком
            double d = 1.0 + RNG.nextDouble() * (R - 1.5), s = d * 0.3;
            Fx.send(sl, Fx.particle("snowflake"), eye.f_82479_ + look.f_82479_ * d, eye.f_82480_ + look.f_82480_ * d - 0.3, eye.f_82481_ + look.f_82481_ * d,
                    1, s, s * 0.6, s, 0.02);
        }
        int ice = canEdit(sl) ? icePath(sl, p, look) : 0;
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), ice > 0 ? "block.glass.place" : "entity.player.hurt_freeze", 1.0F, 1.3F);
        return EarthLogic.Kind.FROST.cd;
    }

    /** Вода-источник под ногами и впереди (полоса 3 шириной) становится льдом на 20 секунд. */
    static int icePath(ServerLevel sl, ServerPlayer p, Vec3 look) {
        double hx = look.f_82479_, hz = look.f_82481_, hl = Math.sqrt(hx * hx + hz * hz);
        if (hl > 0.3D) { hx /= hl; hz /= hl; } else { hx = 0; hz = 0; }
        double px = p.m_20185_(), py = p.m_20186_(), pz = p.m_20189_();
        int fy = (int) Math.floor(py);
        Dungeons.Shield sh = Dungeons.shield(sl);
        Block water = Fx.block("water");
        BlockState ice = Fx.state("ice");
        EarthData d = null;
        long until = sl.m_46467_() + EarthLogic.ICE_TICKS;
        int n = 0;
        for (int k = 0; k <= (hl > 0.3D ? EarthLogic.ICE_LENGTH : 0); k++)
            for (int s = -EarthLogic.ICE_HALF_WIDTH; s <= EarthLogic.ICE_HALF_WIDTH; s++) {
                int x = (int) Math.floor(px + hx * k - hz * s), z = (int) Math.floor(pz + hz * k + hx * s);
                for (int y = fy + 1; y >= fy - 2; y--) {          // поверхность воды у ног
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState st = sl.m_8055_(pos);
                    if (st.m_60734_() != water || !st.m_60819_().m_76170_()) continue;
                    if (!sl.m_8055_(new BlockPos(x, y + 1, z)).m_60795_() || sh.at(pos)) break;
                    if (d == null) d = EarthData.get(sl);
                    sl.m_7731_(pos, ice, 3);
                    addTemp(sl, d, pos, EarthData.ICE, until + RNG.nextInt(100), null);
                    Fx.send(sl, Fx.particle("snowflake"), x + 0.5, y + 1.0, z + 0.5, 3, 0.3, 0.1, 0.3, 0.02);
                    n++;
                    break;
                }
            }
        return n;
    }

    // ================================================================ Палочка Роста
    static int growth(ServerLevel sl, ServerPlayer p) {
        if (Dungeons.near(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), 12)) { deny(p, "blocked"); return 0; }   // деревья не должны прорасти в данж
        if (!canEdit(sl)) { deny(p, "blocked"); return 0; }
        int bx = (int) Math.floor(p.m_20185_()), by = (int) Math.floor(p.m_20186_()), bz = (int) Math.floor(p.m_20189_());
        Dungeons.Shield sh = Dungeons.shield(sl);
        int R = EarthLogic.GROW_R, n = 0;
        for (int dx = -R; dx <= R && n < EarthLogic.GROW_MAX; dx++)
            for (int dz = -R; dz <= R && n < EarthLogic.GROW_MAX; dz++)
                for (int dy = -EarthLogic.GROW_DOWN; dy <= EarthLogic.GROW_UP; dy++) {
                    BlockPos pos = new BlockPos(bx + dx, by + dy, bz + dz);
                    if (!EarthLogic.growable(path(sl.m_8055_(pos))) || sh.at(pos)) continue;
                    for (int i = 0; i < EarthLogic.GROW_PASSES; i++) {
                        BlockState st = sl.m_8055_(pos);
                        if (!EarthLogic.growable(path(st))) break;      // например, саженец уже стал деревом
                        st.m_222972_(sl, pos, sl.f_46441_);
                    }
                    Fx.send(sl, Fx.particle("happy_villager"), bx + dx + 0.5, by + dy + 0.6, bz + dz + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
                    n++;
                }
        if (n == 0) { deny(p, "nothing_grows"); return 0; }
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "item.bone_meal.use", 1.0F, 1.0F);
        return EarthLogic.Kind.GROWTH.cd;
    }

    // ================================================================ Палочка Сбора
    static final class Pull {
        final ServerPlayer p; final ServerLevel level; final long until;
        Pull(ServerPlayer p, ServerLevel level, long until) { this.p = p; this.level = level; this.until = until; }
    }
    static final List<Pull> PULLS = new ArrayList<>();

    static int gather(ServerLevel sl, ServerPlayer p) {
        PULLS.removeIf(x -> x.p == p);
        PULLS.add(new Pull(p, sl, sl.m_46467_() + EarthLogic.GATHER_TICKS));
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.beacon.power_select", 0.8F, 1.8F);
        return EarthLogic.Kind.GATHER.cd;
    }

    static void tickPulls(ServerLevel sl, long now) {
        Iterator<Pull> it = PULLS.iterator();
        while (it.hasNext()) {
            Pull w = it.next();
            if (w.level != sl) continue;
            ServerPlayer p = w.p;
            if (now >= w.until || !p.m_6084_() || p.m_9236_() != sl) { it.remove(); continue; }
            double R = EarthLogic.GATHER_R;
            Vec3 me = p.m_20182_();
            double tx = me.f_82479_, ty = me.f_82480_ + 0.6, tz = me.f_82481_;
            AABB box = new AABB(tx - R, ty - R, tz - R, tx + R, ty + R, tz + R);
            int n = 0;
            for (Entity e : sl.m_6249_(p, box, (Entity en) -> en instanceof ItemEntity || en instanceof ExperienceOrb)) {
                if (n++ >= 128) break;
                Vec3 to = new Vec3(tx - e.m_20185_(), ty - e.m_20186_(), tz - e.m_20189_());
                double d = to.m_82553_();
                if (d > R || d < 0.6D) continue;
                e.m_20256_(to.m_82541_().m_82490_(Math.min(0.9D, 0.3D + d * 0.05D)));
            }
            double ring = R * (1.0D - (w.until - now) / (double) EarthLogic.GATHER_TICKS * 0.8D);   // кольцо сжимается к игроку
            for (int j = 0; j < 20; j++) {
                double a = j * Math.PI * 2 / 20 + now * 0.1;
                Fx.send(sl, Fx.particle("enchant"), tx + Math.cos(a) * ring, ty, tz + Math.sin(a) * ring, 1, 0, 0.3, 0, 0.0);
            }
        }
    }

    // ================================================================ Сигнальная палочка
    static final class Beacon {
        final ServerLevel level; final double x, y, z; final long until;
        Beacon(ServerLevel level, double x, double y, double z, long until) { this.level = level; this.x = x; this.y = y; this.z = z; this.until = until; }
    }
    static final List<Beacon> BEACONS = new ArrayList<>();
    static DustParticleOptions RED, GOLD;

    static int signal(ServerLevel sl, ServerPlayer p) {
        Aim aim = Aim.of(sl, p, 128.0D);
        Vec3 t = aim.hit ? aim.target : p.m_20182_();
        while (BEACONS.size() >= EarthLogic.SIGNAL_MAX) BEACONS.remove(0);
        BEACONS.add(new Beacon(sl, t.f_82479_, t.f_82480_, t.f_82481_, sl.m_46467_() + EarthLogic.SIGNAL_TICKS));
        Fx.sound(sl, t.f_82479_, t.f_82480_, t.f_82481_, "entity.firework_rocket.launch", 4.0F, 0.8F);
        Fx.send(sl, Fx.particle("flash"), t.f_82479_, t.f_82480_ + 1, t.f_82481_, 1, 0, 0, 0, 0);
        // фейерверк в небе над меткой (сам взрывается на первом тике, блоков не ломает)
        Cmd.run(sl, t.f_82479_, t.f_82480_, t.f_82481_, "summon minecraft:firework_rocket " + Cmd.f(t.f_82479_) + " " + Cmd.f(t.f_82480_ + 60) + " " + Cmd.f(t.f_82481_)
                + " {LifeTime:0,FireworksItem:{id:\"minecraft:firework_rocket\",Count:1b,tag:{Fireworks:{Flight:1b,Explosions:[{Type:4,Colors:[I;16711680,16755200],FadeColors:[I;16777215],Flicker:1b,Trail:1b}]}}}}");
        return EarthLogic.Kind.SIGNAL.cd;
    }

    static void tickBeacons(ServerLevel sl, long now) {
        if (RED == null) { RED = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.1F), 2.5F); GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.3F), 2.0F); }
        Iterator<Beacon> it = BEACONS.iterator();
        while (it.hasNext()) {
            Beacon b = it.next();
            if (b.level != sl) continue;
            if (now >= b.until) { it.remove(); continue; }
            for (double h = 0; h < EarthLogic.SIGNAL_HEIGHT; h += 3.0D)
                Fx.send(sl, ((int) h / 3) % 2 == 0 ? RED : GOLD, b.x, b.y + h, b.z, 2, 0.12, 0.5, 0.12, 0);
            Fx.send(sl, Fx.particle("end_rod"), b.x, b.y + EarthLogic.SIGNAL_HEIGHT, b.z, 8, 0.6, 0.6, 0.6, 0.03);
        }
    }

    // ================================================================ Палочка Возврата
    static final class Cast {
        final ServerLevel level; final ServerPlayer p; final double x, y, z; float hp; int t;
        Cast(ServerLevel level, ServerPlayer p) { this.level = level; this.p = p; x = p.m_20185_(); y = p.m_20186_(); z = p.m_20189_(); hp = p.m_21223_(); }
    }
    static final Map<UUID, Cast> CASTS = new HashMap<>();

    static int startReturn(ServerLevel sl, ServerPlayer p) {
        if (Dims.isOurs(sl)) { deny(p, "return_blocked"); return 0; }
        if (CASTS.containsKey(p.m_20148_())) return 0;
        if (p.m_8961_() == null) { deny(p, "return_nobed"); return 0; }
        CASTS.put(p.m_20148_(), new Cast(sl, p));
        deny(p, "return_start");
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.portal.trigger", 0.6F, 1.6F);
        return 20;      // защита от повторного нажатия; настоящая перезарядка ставится по итогу каста
    }

    static void setCooldown(ServerPlayer p, int ticks) {
        p.m_36335_().m_41524_(ExplosionWandMod.RETURN_WAND.get(), ticks);
    }

    static void tickCasts(ServerLevel sl) {
        Iterator<Cast> it = CASTS.values().iterator();
        while (it.hasNext()) {
            Cast c = it.next();
            if (c.level != sl) continue;
            ServerPlayer p = c.p;
            if (!p.m_6084_() || p.m_9236_() != sl) { it.remove(); continue; }
            if (EarthLogic.moved(p.m_20185_() - c.x, p.m_20186_() - c.y, p.m_20189_() - c.z)) { interrupt(sl, p, "return_moved"); it.remove(); continue; }
            if (EarthLogic.hurt(c.hp, p.m_21223_())) { interrupt(sl, p, "return_hurt"); it.remove(); continue; }
            c.hp = Math.max(c.hp, p.m_21223_());      // лечение поднимает порог
            c.t++;
            if (c.t % 2 == 0) {
                double k = c.t / (double) EarthLogic.CAST_TICKS, a = c.t * 0.45, r = 1.3 - 0.6 * k;
                for (int j = 0; j < 3; j++) {
                    double aj = a + j * Math.PI * 2 / 3;
                    Fx.send(sl, Fx.particle("portal"), c.x + Math.cos(aj) * r, c.y + 0.2 + k * 1.8, c.z + Math.sin(aj) * r, 2, 0.05, 0.05, 0.05, 0.1);
                }
            }
            if (c.t >= EarthLogic.CAST_TICKS) { it.remove(); finishReturn(sl, p); }
        }
    }

    static void interrupt(ServerLevel sl, ServerPlayer p, String key) {
        deny(p, key);
        setCooldown(p, EarthLogic.cooldown(EarthLogic.CAST_FAIL_CD, 1.0));
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.fire.extinguish", 0.8F, 1.2F);
    }

    static boolean isBed(String path) { return path.endsWith("_bed") || path.equals("respawn_anchor"); }

    static void finishReturn(ServerLevel sl, ServerPlayer p) {
        BlockPos bed = p.m_8961_();
        ServerLevel dest = bed == null || p.m_8963_() == null ? null : Dims.get(sl, p.m_8963_());
        if (dest == null || Dims.isOurs(dest) || !isBed(path(dest.m_8055_(bed)))) { interrupt(sl, p, "return_nobed"); return; }
        int[] spot = Dims.nearSpot(dest, bed);
        int x = spot[0], y = spot[1], z = spot[2];
        if (Dungeons.near(dest, x + 0.5, y, z + 0.5, 1)) { interrupt(sl, p, "return_blocked"); return; }
        Fx.send(sl, Fx.particle("portal"), p.m_20185_(), p.m_20186_() + 1, p.m_20189_(), 60, 0.4, 0.8, 0.4, 0.5);
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.enderman.teleport", 1.0F, 1.0F);
        if (dest == sl) p.m_8999_(sl, x + 0.5, y, z + 0.5, p.m_146908_(), p.m_146909_());
        else Dims.teleport(p, dest, x + 0.5, y, z + 0.5);
        Fx.send(dest, Fx.particle("portal"), x + 0.5, y + 1, z + 0.5, 60, 0.4, 0.8, 0.4, 0.5);
        Fx.sound(dest, x + 0.5, y, z + 0.5, "entity.enderman.teleport", 1.0F, 1.2F);
        setCooldown(p, EarthLogic.cooldown(EarthLogic.Kind.RETURN.cd, ArcaneSet.cd(p)));
    }

    // ================================================================ Палочка Копателя
    static ItemStack TOOL;

    static ItemStack tool() {
        if (TOOL == null) {
            Object it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "diamond_pickaxe"));
            TOOL = new ItemStack((net.minecraft.world.item.Item) it);
        }
        return TOOL;
    }

    /** Блок можно сломать эффектом палочки: не воздух, не жидкость, не бедрок (твёрдость меньше нуля), не контейнер, не данж. */
    static boolean breakable(ServerLevel sl, Dungeons.Shield sh, BlockPos pos, BlockState st, float maxHardness) {
        if (st.m_60795_()) return false;
        Block b = st.m_60734_();
        if (b == Fx.block("water") || b == Fx.block("lava")) return false;
        float h = st.m_60800_(sl, pos);
        if (h < 0.0F || h > maxHardness) return false;
        if (sh.at(pos) || sl.m_7702_(pos) != null) return false;
        return true;
    }

    static int dig(ServerLevel sl, ServerPlayer p) {
        if (!canEdit(sl)) { deny(p, "blocked"); return 0; }
        Aim aim = Aim.of(sl, p, EarthLogic.DIG_RANGE);
        if (aim.block == null || aim.eye.m_82554_(aim.block.m_82450_()) >= EarthLogic.DIG_RANGE - 0.01D) { deny(p, "no_target"); return 0; }
        BlockPos s = aim.block.m_82425_();
        int axis = EarthLogic.axis(aim.look.f_82479_, aim.look.f_82480_, aim.look.f_82481_);
        List<int[]> cells = EarthLogic.tunnel(s.m_123341_(), s.m_123342_(), s.m_123343_(), axis, EarthLogic.DIG_DEPTH);
        Dungeons.Shield sh = Dungeons.shield(sl);
        for (int i = 0; i < EarthLogic.DIG_DEPTH; i++) {
            final int from = i * 9;
            final List<int[]> slice = cells.subList(from, from + 9);
            Later.run(sl, 1 + i * 2, () -> {
                if (!p.m_6084_() || p.m_9236_() != sl) return;      // игрок умер или ушёл в другое измерение
                for (int[] c : slice) {
                    BlockPos pos = new BlockPos(c[0], c[1], c[2]);
                    if (!sl.m_46805_(pos)) continue;
                    BlockState st = sl.m_8055_(pos);
                    if (!breakable(sl, sh, pos, st, Float.MAX_VALUE)) continue;
                    if (!p.m_7500_()) Block.m_49881_(st, sl, pos, null, p, tool());
                    sl.m_46961_(pos, false);
                }
                int[] mid = slice.get(4);
                Fx.sound(sl, mid[0] + 0.5, mid[1] + 0.5, mid[2] + 0.5, "block.stone.break", 0.8F, 0.7F);
            });
        }
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.generic.explode", 0.5F, 1.5F);
        return EarthLogic.Kind.DIGGER.cd;
    }

    // ================================================================ Взрывная палочка: тычок
    /** Луч, урон как стрела с Силой I, слабый взрыв без разрушения блоков и 2-4 сломанных блока рядом с попаданием (мимо данжей). */
    static int blast(ServerLevel sl, ServerPlayer p) {
        Aim aim = Aim.of(sl, p, EarthLogic.SHOT_RANGE);
        aim.beam(sl, Fx.particle("end_rod"), 0.75D);
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "item.firecharge.use", 1.0F, 0.8F);
        if (!aim.hit) return 10;
        Vec3 t = aim.target;
        if (aim.entity != null) aim.entity.m_6469_(sl.m_269111_().m_269075_(p), EarthLogic.SHOT_DAMAGE);
        Vec3 at = t.m_82546_(aim.look.m_82490_(0.3D));
        sl.m_255391_(p, at.f_82479_, at.f_82480_, at.f_82481_, EarthLogic.BLAST_POWER, false, Level.ExplosionInteraction.valueOf("NONE"));
        if (canEdit(sl)) {
            Dungeons.Shield sh = Dungeons.shield(sl);
            BlockPos c = pos(at.f_82479_, at.f_82480_, at.f_82481_);
            List<BlockPos> cand = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos q = new BlockPos(c.m_123341_() + dx, c.m_123342_() + dy, c.m_123343_() + dz);
                if (breakable(sl, sh, q, sl.m_8055_(q), EarthLogic.BREAK_MAX_HARDNESS)) cand.add(q);
            }
            final double ax = at.f_82479_, ay = at.f_82480_, az = at.f_82481_;
            cand.sort(Comparator.comparingDouble(q -> (q.m_123341_() + 0.5 - ax) * (q.m_123341_() + 0.5 - ax) + (q.m_123342_() + 0.5 - ay) * (q.m_123342_() + 0.5 - ay) + (q.m_123343_() + 0.5 - az) * (q.m_123343_() + 0.5 - az)));
            int n = Math.min(cand.size(), EarthLogic.blocksToBreak(RNG));
            for (int i = 0; i < n; i++) sl.m_46961_(cand.get(i), true);
        }
        return EarthLogic.SHOT_CD;
    }


    // ================================================================ часть C: палочки 9-16
    // Блоков эти палочки не меняют (шипы, лоза и т.д. - только урон, эффекты и частицы), поэтому им не нужны Shield/canEdit
    // и они работают в данжах и наших измерениях: "Земля помогает проходить данжи". Мобы данжа (теги ew*) не отбрасываются и не
    // притягиваются (Minions.controllable), но получают урон шипов и искр как от обычного удара.

    /** Цель эффекта: живое существо, не игрок, не свой/чужой питомец, не житель. */
    static boolean foe(Entity e, ServerPlayer p) {
        if (!(e instanceof LivingEntity) || e instanceof Player || !e.m_6084_() || e.m_5833_()) return false;
        if (e instanceof AbstractVillager || e instanceof ArmorStand) return false;
        if (e instanceof TamableAnimal t && (t.m_21830_(p) || t.m_21824_())) return false;
        return !Cmd.hasTagPrefix(e, "ewdecoy");
    }

    /** Подвижная цель (можно толкать и тянуть): к тому же не часть босса и не моб данжа. */
    static boolean movable(Entity e, ServerPlayer p) { return foe(e, p) && Minions.controllable(e); }

    /**
     * Цель для толчка, притяжения, ветра и лозы: как movable, но и обычные мобы данжей (хосты ewr_h). Им меняется только скорость и эффекты
     * (ИИ, цель и NoAI не трогаем), боссы и их хитбоксы - нет. Двойник по-прежнему берёт строгий movable: мобы данжа ходят своим ИИ и иллюзию игнорируют.
     */
    static boolean shovable(Entity e, ServerPlayer p) {
        return foe(e, p) && (Minions.controllable(e) || Minions.dungeonMob(e));
    }

    /** Толчок: мобам данжа (NoAI-хосты) - импульс RiftMobs.knock, остальным - скорость. */
    static void shove(Entity e, double vx, double vy, double vz) {
        if (RiftMobs.knock(e, vx, vy, vz)) return;
        e.m_20256_(new Vec3(vx, vy, vz)); e.f_19864_ = true;
    }

    static DustParticleOptions GREEN;
    static DustParticleOptions green() {
        if (GREEN == null) GREEN = new DustParticleOptions(new Vector3f(0.25F, 0.75F, 0.2F), 1.4F);
        return GREEN;
    }

    // ---------------------------------------------------------------- 9. Кристальных шипов
    static int spikes(ServerLevel sl, ServerPlayer p) {
        Vec3 look = p.m_20252_(1.0F);
        double[] h = EarthLogic.horizontal(look.f_82479_, look.f_82481_);
        final double px = p.m_20185_(), py = p.m_20186_(), pz = p.m_20189_();
        final Set<Entity> hit = new HashSet<>();
        for (int i = 1; i <= EarthLogic.SPIKE_LEN; i++) {
            final int k = i;
            Later.run(sl, k, () -> {
                if (!p.m_6084_() || p.m_9236_() != sl) return;      // игрок умер или ушёл в другое измерение
                int[] c = EarthLogic.spikeCell(px, pz, h[0], h[1], k);
                int y = -1000;
                for (int yy = (int) Math.floor(py) + 2; yy >= (int) Math.floor(py) - 3; yy--) {      // поверхность: воздух над твёрдым
                    if (sl.m_8055_(new BlockPos(c[0], yy, c[1])).m_60795_() && !sl.m_8055_(new BlockPos(c[0], yy - 1, c[1])).m_60795_()) { y = yy; break; }
                }
                if (y == -1000) return;
                double cx = c[0] + 0.5, cz = c[1] + 0.5;
                Fx.send(sl, Fx.blockParticle(Fx.state("amethyst_block")), cx, y + 0.3, cz, 14, 0.25, 0.4, 0.25, 0.08);
                Fx.send(sl, Fx.particle("end_rod"), cx, y + 0.6, cz, 4, 0.15, 0.5, 0.15, 0.04);
                Fx.send(sl, Fx.particle("crit"), cx, y + 1.0, cz, 6, 0.3, 0.4, 0.3, 0.2);
                Fx.sound(sl, cx, y, cz, "block.amethyst_cluster.break", 0.9F, 0.8F + k * 0.04F);
                double r = EarthLogic.SPIKE_REACH;
                AABB box = new AABB(cx - r, y - 0.5, cz - r, cx + r, y + 2.2, cz + r);
                for (Entity e : sl.m_6249_(p, box, (Entity en) -> foe(en, p))) {
                    if (!hit.add(e)) continue;                          // каждого один раз за линию
                    e.m_6469_(sl.m_269111_().m_269075_(p), EarthLogic.SPIKE_DAMAGE);
                    if (shovable(e, p)) shove(e, 0.0D, EarthLogic.SPIKE_LIFT, 0.0D);
                }
            });
        }
        Fx.sound(sl, px, py, pz, "block.amethyst_block.resonate", 1.0F, 0.7F);
        return EarthLogic.Kind.SPIKES.cd;
    }

    // ---------------------------------------------------------------- 10. Каменной кожи
    static final Map<UUID, Long> SKIN = new HashMap<>();

    static int skin(ServerLevel sl, ServerPlayer p) {
        SKIN.put(p.m_20148_(), sl.m_46467_() + EarthLogic.SKIN_TICKS);
        MobEffect slow = Fx.effect("slowness");
        if (slow != null) p.m_7292_(new MobEffectInstance(slow, EarthLogic.SKIN_TICKS, EarthLogic.SKIN_SLOW_AMP));
        Fx.send(sl, Fx.blockParticle(Fx.state("stone")), p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_(), 30, 0.4, 0.7, 0.4, 0.1);
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.stone.place", 1.0F, 0.6F);
        return EarthLogic.Kind.SKIN.cd;
    }

    /** Хук LivingHurtEvent: пока кожа активна, урон игроку вдвое меньше. */
    static void onHurt(LivingHurtEvent ev) {
        if (SKIN.isEmpty() || !(ev.getEntity() instanceof ServerPlayer p)) return;
        Long until = SKIN.get(p.m_20148_());
        if (until == null) return;
        if (p.m_9236_().m_46467_() >= until) { SKIN.remove(p.m_20148_()); return; }
        ev.setAmount(EarthLogic.skinned(ev.getAmount()));
        Fx.send((ServerLevel) p.m_9236_(), Fx.blockParticle(Fx.state("stone")), p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_(), 8, 0.3, 0.5, 0.3, 0.05);
    }

    static void tickSkin(ServerLevel sl, long now) {
        Iterator<Map.Entry<UUID, Long>> it = SKIN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (now >= e.getValue()) { it.remove(); continue; }          // срок вышел: убираем независимо от того, на сервере ли игрок
            ServerPlayer p = null;
            for (ServerPlayer q : sl.m_6907_()) if (q.m_20148_().equals(e.getKey())) { p = q; break; }
            if (p == null) continue;                                    // игрок в другом измерении: проверит его уровень
            if (!p.m_6084_()) { it.remove(); continue; }
            Fx.send(sl, Fx.blockParticle(Fx.state("stone")), p.m_20185_(), p.m_20186_() + 1.0, p.m_20189_(), 2, 0.35, 0.8, 0.35, 0.0);
        }
    }

    // ---------------------------------------------------------------- 11. Ветра
    static int wind(ServerLevel sl, ServerPlayer p) {
        Vec3 look = p.m_20252_(1.0F), eye = p.m_146892_();
        CityMobs.gust(sl, p.m_20185_(), p.m_20186_(), p.m_20189_());      // моли Города разгоняются
        boolean inDungeon = Dungeons.at(sl, p.m_20185_(), p.m_20186_(), p.m_20189_()) != null;
        if (p.m_6144_() && inDungeon) deny(p, "wind_dungeon");            // в данже подброса нет: обычный порыв
        else if (p.m_6144_()) {                                           // Shift: подброс себя вверх
            double[] h = EarthLogic.horizontal(look.f_82479_, look.f_82481_);
            shove(p, h[0] * 0.15, EarthLogic.WIND_SELF_LIFT, h[1] * 0.15);
            p.f_19789_ = 0.0F;
            MobEffect fall = Fx.effect("slow_falling");
            if (fall != null) p.m_7292_(new MobEffectInstance(fall, EarthLogic.WIND_FALL_TICKS, 0));
            Fx.send(sl, Fx.particle("cloud"), p.m_20185_(), p.m_20186_() + 0.2, p.m_20189_(), 30, 0.4, 0.1, 0.4, 0.15);
            Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.phantom.flap", 1.0F, 1.5F);
            return EarthLogic.Kind.WIND.cd + 60;
        }
        double R = EarthLogic.WIND_RANGE;
        double[] v = EarthLogic.windVel(look.f_82479_, look.f_82481_);
        AABB box = new AABB(eye.f_82479_ - R, eye.f_82480_ - R, eye.f_82481_ - R, eye.f_82479_ + R, eye.f_82480_ + R, eye.f_82481_ + R);
        for (Entity e : sl.m_6249_(p, box, (Entity en) -> en.m_6084_() && !en.m_5833_())) {
            Vec3 c = e.m_20182_();
            if (!EarthLogic.inCone(c.f_82479_ - eye.f_82479_, c.f_82480_ + e.m_20206_() * 0.5 - eye.f_82480_, c.f_82481_ - eye.f_82481_,
                    look.f_82479_, look.f_82480_, look.f_82481_, R, EarthLogic.WIND_COS)) continue;
            if (e instanceof Projectile pr) {                                // снаряд сбит
                Entity own = pr.m_19749_();
                if (pr.m_150171_(p) || own instanceof Player || pr instanceof ThrownTrident || pr instanceof ThrownEnderpearl || pr instanceof FishingHook) continue;   // снаряды игроков не трогаем
                Fx.send(sl, Fx.particle("cloud"), c.f_82479_, c.f_82480_, c.f_82481_, 8, 0.2, 0.2, 0.2, 0.05);
                e.m_146870_();
            } else if (shovable(e, p)) {
                shove(e, v[0], v[1], v[2]);
                Fx.send(sl, Fx.particle("cloud"), c.f_82479_, c.f_82480_ + e.m_20206_() * 0.5, c.f_82481_, 6, 0.3, 0.3, 0.3, 0.05);
            }
        }
        for (int i = 0; i < 50; i++) {
            double d = 1.0 + RNG.nextDouble() * (R - 1.0), s = d * 0.35;
            Fx.send(sl, Fx.particle(i % 3 == 0 ? "poof" : "cloud"), eye.f_82479_ + look.f_82479_ * d, eye.f_82480_ + look.f_82480_ * d - 0.3,
                    eye.f_82481_ + look.f_82481_ * d, 1, s, s * 0.5, s, 0.1);
        }
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.phantom.flap", 1.2F, 0.7F);
        return EarthLogic.Kind.WIND.cd;
    }

    // ---------------------------------------------------------------- 12. Лозы
    static int vine(ServerLevel sl, ServerPlayer p) {
        Aim aim = Aim.of(sl, p, EarthLogic.VINE_RANGE);
        final Vec3 c = aim.target;
        final double R = EarthLogic.VINE_R;
        AABB box = new AABB(c.f_82479_ - R, c.f_82480_ - EarthLogic.VINE_H - 1, c.f_82481_ - R, c.f_82479_ + R, c.f_82480_ + EarthLogic.VINE_H + 2, c.f_82481_ + R);
        List<Entity> bound = new ArrayList<>();
        MobEffect slow = Fx.effect("slowness");
        for (Entity e : sl.m_6249_(p, box, (Entity en) -> shovable(en, p))) {      // боссов лоза не держит, мобов данжей держит
            if (!EarthLogic.inCircle(e.m_20185_() - c.f_82479_, e.m_20186_() - c.f_82480_, e.m_20189_() - c.f_82481_, R, EarthLogic.VINE_H + 1)) continue;
            if (slow != null) ((LivingEntity) e).m_7292_(new MobEffectInstance(slow, EarthLogic.VINE_TICKS, EarthLogic.VINE_SLOW_AMP));
            bound.add(e);
            RiftMobs.root(e, EarthLogic.VINE_TICKS);      // мобы данжа: ходить и летать нельзя, пока держит лоза
        }
        for (int t = 0; t <= EarthLogic.VINE_TICKS; t += 4) {
            final int k = t;
            Later.run(sl, k, () -> {
                if (!p.m_6084_() || p.m_9236_() != sl) return;
                for (int j = 0; j < 24; j++) {                          // кольцо из лоз по краю круга
                    double a = j * Math.PI * 2 / 24 + k * 0.05;
                    Fx.send(sl, green(), c.f_82479_ + Math.cos(a) * R, c.f_82480_ + 0.2, c.f_82481_ + Math.sin(a) * R, 1, 0.0, 0.3, 0.0, 0.0);
                }
                for (Entity e : bound) {
                    if (!e.m_6084_()) continue;
                    Vec3 v = e.m_20184_();
                    e.m_20256_(new Vec3(0.0D, Math.min(v.f_82480_, 0.0D), 0.0D));      // держит на месте, вниз падать можно
                    Fx.send(sl, Fx.blockParticle(Fx.state("vine")), e.m_20185_(), e.m_20186_() + e.m_20206_() * 0.5, e.m_20189_(), 4, 0.3, 0.4, 0.3, 0.0);
                }
            });
        }
        Fx.sound(sl, c.f_82479_, c.f_82480_, c.f_82481_, "block.vine.step", 1.2F, 0.6F);
        Fx.sound(sl, c.f_82479_, c.f_82480_, c.f_82481_, "block.grass.break", 1.0F, 0.7F);
        return EarthLogic.Kind.VINE.cd;
    }

    // ---------------------------------------------------------------- 13. Притяжения
    static int pull(ServerLevel sl, ServerPlayer p) {
        Aim aim = Aim.of(sl, p, EarthLogic.PULL_RANGE);
        Vec3 eye = aim.eye, look = aim.look;
        double reach = aim.entity == null && aim.hit ? eye.m_82554_(aim.target) + 0.5D : EarthLogic.PULL_RANGE;   // стена закрывает цели за ней
        Entity best = null;
        double bt = Double.MAX_VALUE;
        double R = EarthLogic.PULL_RANGE;
        AABB box = new AABB(eye.f_82479_ - R, eye.f_82480_ - R, eye.f_82481_ - R, eye.f_82479_ + R, eye.f_82480_ + R, eye.f_82481_ + R);
        for (Entity e : sl.m_6249_(p, box, (Entity en) -> en.m_6084_() && (en instanceof ItemEntity || en instanceof ExperienceOrb || shovable(en, p)))) {
            Vec3 c = e.m_20182_();
            double[] r = EarthLogic.toRay(c.f_82479_ - eye.f_82479_, c.f_82480_ + e.m_20206_() * 0.5 - eye.f_82480_, c.f_82481_ - eye.f_82481_,
                    look.f_82479_, look.f_82480_, look.f_82481_);
            if (r[0] < 1.0D || r[0] > reach || r[1] > EarthLogic.PULL_WIDTH + e.m_20205_() * 0.5) continue;
            if (r[0] < bt) { bt = r[0]; best = e; }
        }
        if (best == null) { deny(p, "no_pull_target"); return 0; }
        final Entity target = best;
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "entity.fishing_bobber.throw", 1.0F, 0.6F);
        for (int i = 0; i < 3; i++) {
            Later.run(sl, i * 3, () -> {
                if (!p.m_6084_() || p.m_9236_() != sl || !target.m_6084_() || target.m_9236_() != sl) return;
                Vec3 a = p.m_146892_(), c = target.m_20182_();
                double tx = a.f_82479_ - c.f_82479_, ty = a.f_82480_ - 0.4 - (c.f_82480_ + target.m_20206_() * 0.5), tz = a.f_82481_ - c.f_82481_;
                double d = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (d < 1.5D) return;                                   // уже рядом, не сбиваем с ног
                double k = EarthLogic.pullSpeed(d) / d;
                shove(target, tx * k, ty * k * 0.6 + 0.15, tz * k);
                int n = (int) d;
                for (int j = 1; j < n; j++)
                    Fx.send(sl, Fx.particle("enchant"), c.f_82479_ + tx * j / n, c.f_82480_ + target.m_20206_() * 0.5 + ty * j / n, c.f_82481_ + tz * j / n, 1, 0, 0, 0, 0.0);
            });
        }
        return EarthLogic.Kind.PULL.cd;
    }

    // ---------------------------------------------------------------- 14. Двойника
    static final class Decoy {
        final ServerPlayer p; final ServerLevel level; final double x, y, z; final String tag; final long until;
        Entity host; int age;
        Decoy(ServerPlayer p, ServerLevel level, double x, double y, double z, String tag, long until) { this.p = p; this.level = level; this.x = x; this.y = y; this.z = z; this.tag = tag; this.until = until; }
    }
    static final List<Decoy> DECOYS = new ArrayList<>();
    static int decoySeq = 0;

    static int decoy(ServerLevel sl, ServerPlayer p) {
        for (Decoy d : new ArrayList<>(DECOYS)) if (d.p == p) endDecoy(d);      // у игрока одна иллюзия
        Vec3 look = p.m_20252_(1.0F);
        double[] h = EarthLogic.horizontal(look.f_82479_, look.f_82481_);
        double x = p.m_20185_() + h[0] * EarthLogic.DECOY_AHEAD, y = p.m_20186_(), z = p.m_20189_() + h[1] * EarthLogic.DECOY_AHEAD;
        BlockPos at = pos(x, y, z);
        if (!sl.m_8055_(at).m_60795_() || !sl.m_8055_(new BlockPos(at.m_123341_(), at.m_123342_() + 1, at.m_123343_())).m_60795_()) { x = p.m_20185_(); z = p.m_20189_(); }   // впереди стена: прямо на игроке
        String tag = "ewdecoy" + sl.m_46467_() + "x" + (decoySeq++) + "e";   // время мира: после перезапуска тег не повторяется
        String tags = "Tags:[\"ewdecoy\",\"" + tag + "\"]";
        // хост: невидимый безмолвный житель без ИИ, мобы его атакуют; много здоровья, чтобы не умер раньше срока
        Cmd.run(sl, x, y, z, "summon minecraft:villager " + Cmd.f(x) + " " + Cmd.f(y) + " " + Cmd.f(z) + " {" + tags
                + ",NoAI:1b,Silent:1b,NoGravity:1b,PersistenceRequired:1b,DeathLootTable:\"minecraft:empty\","
                + "Attributes:[{Name:\"minecraft:generic.max_health\",Base:1024d}],Health:1024f,"
                + "ActiveEffects:[{Id:14,Amplifier:0b,Duration:" + (EarthLogic.DECOY_TICKS + 40) + ",ShowParticles:0b}]}");
        // облик: стеклянный человечек из block_display (стоит на месте)
        double[][] parts = {{0, 1.68, 0, 0.5, 0.5, 0.5}, {0, 1.1, 0, 0.5, 0.7, 0.3}, {-0.13, 0.4, 0, 0.23, 0.75, 0.28}, {0.13, 0.4, 0, 0.23, 0.75, 0.28},
                {-0.38, 1.1, 0, 0.2, 0.7, 0.25}, {0.38, 1.1, 0, 0.2, 0.7, 0.25}};
        for (int i = 0; i < parts.length; i++) {
            double[] q = parts[i];
            Cmd.run(sl, x, y, z, "summon minecraft:block_display " + Cmd.f(x) + " " + Cmd.f(y) + " " + Cmd.f(z) + " {" + tags
                    + ",brightness:{sky:15,block:15},view_range:2f," + Disp.block(i == 0 ? "sea_lantern" : "light_blue_stained_glass") + ","
                    + Disp.tf(q[0], q[1], q[2], q[3], q[4], q[5], Disp.ID) + "}");
        }
        DECOYS.add(new Decoy(p, sl, x, y, z, tag, sl.m_46467_() + EarthLogic.DECOY_TICKS));
        Fx.send(sl, Fx.particle("poof"), x, y + 1.0, z, 20, 0.3, 0.7, 0.3, 0.05);
        Fx.sound(sl, x, y, z, "entity.illusioner.mirror_move", 1.0F, 1.2F);
        return EarthLogic.Kind.DECOY.cd;
    }

    static void endDecoy(Decoy d) {
        DECOYS.remove(d);
        for (Entity e : Cmd.tagged(d.level, d.x, d.y, d.z, 8, d.tag)) e.m_146870_();
        if (d.host != null) d.host.m_146870_();
        Fx.send(d.level, Fx.particle("poof"), d.x, d.y + 1.0, d.z, 20, 0.3, 0.7, 0.3, 0.05);
        Fx.sound(d.level, d.x, d.y, d.z, "entity.illusioner.mirror_move", 0.8F, 1.7F);
    }

    static void tickDecoys(ServerLevel sl, long now) {
        for (Decoy d : new ArrayList<>(DECOYS)) {
            if (d.level != sl) continue;
            if (now >= d.until || !d.p.m_6084_() || d.p.m_9236_() != sl) { endDecoy(d); continue; }
            if (d.host == null) {
                for (Entity e : Cmd.tagged(sl, d.x, d.y, d.z, 4, d.tag)) if (e instanceof LivingEntity) d.host = e;
                if (d.host == null) { if (++d.age > 4) endDecoy(d); continue; }       // хост не появился (команда не сработала)
            }
            if (!d.host.m_6084_()) { endDecoy(d); continue; }
            if (d.age++ % 2 == 0) {                                      // раз в 10 тиков: мобы переключаются на иллюзию
                Fx.send(sl, Fx.particle("enchant"), d.x, d.y + 1.0, d.z, 6, 0.3, 0.8, 0.3, 0.3);
                double R = EarthLogic.DECOY_R;
                AABB box = new AABB(d.x - R, d.y - 6, d.z - R, d.x + R, d.y + 6, d.z + R);
                for (Entity e : sl.m_6249_(d.host, box, (Entity en) -> en instanceof Mob && en.m_6084_())) {
                    Mob m = (Mob) e;
                    if (!(e instanceof Enemy) && m.m_5448_() != d.p) continue;     // мирные не трогаем, пока не охотятся на игрока
                    if (!movable(e, d.p)) continue;
                    m.m_6710_((LivingEntity) d.host);
                }
            }
        }
    }

    /** Уборка осиротевших иллюзий (сохранились после выхода из мира в разгар действия): тег ewdecoy без записи в DECOYS. */
    static void sweepDecoys(ServerLevel sl) {
        for (ServerPlayer q : sl.m_6907_()) {
            for (Entity e : Cmd.tagged(sl, q.m_20185_(), q.m_20186_(), q.m_20189_(), 96, "ewdecoy")) {
                boolean live = false;
                for (Decoy d : DECOYS) if (e.m_19880_().contains(d.tag)) { live = true; break; }
                if (!live) e.m_146870_();
            }
        }
    }

    static void onStopping(ServerStoppingEvent ev) { for (Decoy d : new ArrayList<>(DECOYS)) endDecoy(d); }

    // ---------------------------------------------------------------- 15. Искр
    static final class Spark {
        final ServerLevel level; final ServerPlayer p; double x, y, z; double[] v; LivingEntity target; int steps;
        Spark(ServerLevel level, ServerPlayer p, double x, double y, double z, double[] v, LivingEntity target) { this.level = level; this.p = p; this.x = x; this.y = y; this.z = z; this.v = v; this.target = target; }
    }
    static final List<Spark> SPARKS = new ArrayList<>();

    static int sparks(ServerLevel sl, ServerPlayer p) {
        Vec3 eye = p.m_146892_(), look = p.m_20252_(1.0F);
        double R = EarthLogic.SPARK_SEEK;
        List<LivingEntity> foes = new ArrayList<>();
        AABB box = new AABB(eye.f_82479_ - R, eye.f_82480_ - R, eye.f_82481_ - R, eye.f_82479_ + R, eye.f_82480_ + R, eye.f_82481_ + R);
        for (Entity e : sl.m_6249_(p, box, (Entity en) -> foe(en, p))) {
            Vec3 c = e.m_20182_();
            if (EarthLogic.inCone(c.f_82479_ - eye.f_82479_, c.f_82480_ + e.m_20206_() * 0.5 - eye.f_82480_, c.f_82481_ - eye.f_82481_,
                    look.f_82479_, look.f_82480_, look.f_82481_, R, 0.3D)) foes.add((LivingEntity) e);
        }
        foes.sort(Comparator.comparingDouble(e -> e.m_20280_(p)));
        while (!SPARKS.isEmpty() && SPARKS.size() + EarthLogic.SPARK_COUNT > EarthLogic.SPARK_MAX) SPARKS.remove(0);
        double[] deg = {-25, 0, 25};
        for (int i = 0; i < EarthLogic.SPARK_COUNT; i++) {
            double[] f = EarthLogic.fan(look.f_82479_, look.f_82480_ + 0.15, look.f_82481_, deg[i]);
            double[] v = EarthLogic.steer(f, new double[]{0, 0, 0}, 0.0D, EarthLogic.SPARK_SPEED);
            LivingEntity t = foes.isEmpty() ? null : foes.get(i % foes.size());
            Vec3 o = eye.m_82549_(look.m_82490_(0.8D));
            SPARKS.add(new Spark(sl, p, o.f_82479_, o.f_82480_ - 0.2, o.f_82481_, v, t));
        }
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "item.firecharge.use", 1.0F, 1.4F);
        return EarthLogic.Kind.SPARKS.cd;
    }

    /** Один шаг искры (вызывается раз в 2 тика): наведение, два полушага, столкновение с блоком или целью. */
    static void spark(Spark s) {
        ServerLevel sl = s.level;
        if (s.target == null || !s.target.m_6084_()) {                   // цель пропала: ближайшая рядом
            s.target = null;
            double best = Double.MAX_VALUE;
            for (Entity e : sl.m_6249_(s.p, new AABB(s.x - 10, s.y - 10, s.z - 10, s.x + 10, s.y + 10, s.z + 10), (Entity en) -> foe(en, s.p))) {
                double d = e.m_20275_(s.x, s.y, s.z);
                if (d < best) { best = d; s.target = (LivingEntity) e; }
            }
        }
        for (int sub = 0; sub < 2; sub++) {                              // два полушага: не пролетает сквозь мобов
            if (s.target != null) {
                double[] to = {s.target.m_20185_() - s.x, s.target.m_20186_() + s.target.m_20206_() * 0.5 - s.y, s.target.m_20189_() - s.z};
                s.v = EarthLogic.steer(s.v, to, EarthLogic.SPARK_TURN, EarthLogic.SPARK_SPEED);
            }
            s.x += s.v[0]; s.y += s.v[1]; s.z += s.v[2];
            if (!sl.m_8055_(pos(s.x, s.y, s.z)).m_60795_()) { s.steps = EarthLogic.SPARK_STEPS; break; }   // упёрлась в блок
            Fx.send(sl, Fx.particle("flame"), s.x, s.y, s.z, 2, 0.04, 0.04, 0.04, 0.01);
            double hr = EarthLogic.SPARK_HIT;
            Entity victim = null;
            for (Entity e : sl.m_6249_(s.p, new AABB(s.x - hr, s.y - hr, s.z - hr, s.x + hr, s.y + hr, s.z + hr), (Entity en) -> foe(en, s.p))) { victim = e; break; }
            if (victim != null) {
                victim.m_6469_(sl.m_269111_().m_269075_(s.p), EarthLogic.SPARK_DAMAGE);
                RiftMobs.ignite(victim, EarthLogic.SPARK_FIRE_SECONDS);
                Fx.send(sl, Fx.particle("lava"), s.x, s.y, s.z, 8, 0.2, 0.2, 0.2, 0.0);
                Fx.send(sl, Fx.particle("flame"), s.x, s.y, s.z, 12, 0.2, 0.2, 0.2, 0.08);
                Fx.sound(sl, s.x, s.y, s.z, "entity.generic.burn", 0.8F, 1.3F);
                s.steps = EarthLogic.SPARK_STEPS;
                break;
            }
        }
    }

    static void tickSparks(ServerLevel sl) {
        Iterator<Spark> it = SPARKS.iterator();
        while (it.hasNext()) {
            Spark s = it.next();
            if (s.level != sl) continue;
            if (!s.p.m_6084_() || s.p.m_9236_() != sl) { it.remove(); continue; }
            spark(s);
            if (++s.steps >= EarthLogic.SPARK_STEPS) {
                Fx.send(sl, Fx.particle("smoke"), s.x, s.y, s.z, 4, 0.1, 0.1, 0.1, 0.01);
                it.remove();
            }
        }
    }

    // ---------------------------------------------------------------- 16. Тепла
    static int heat(ServerLevel sl, ServerPlayer p) {
        double R = EarthLogic.HEAT_R;
        MobEffect regen = Fx.effect("regeneration");
        List<LivingEntity> who = new ArrayList<>();
        who.add(p);
        for (Entity e : sl.m_6249_(p, new AABB(p.m_20185_() - R, p.m_20186_() - R, p.m_20189_() - R, p.m_20185_() + R, p.m_20186_() + R, p.m_20189_() + R), (Entity en) -> en.m_6084_())) {
            if (e.m_20280_(p) > R * R) continue;
            if (e instanceof Player || (e instanceof TamableAnimal t && t.m_21830_(p))) who.add((LivingEntity) e);
        }
        for (LivingEntity le : who) {
            le.m_5634_(EarthLogic.HEAT_HEAL);
            if (regen != null) le.m_7292_(new MobEffectInstance(regen, EarthLogic.HEAT_REGEN_TICKS, EarthLogic.HEAT_REGEN_AMP));
            Fx.send(sl, Fx.particle("heart"), le.m_20185_(), le.m_20186_() + le.m_20206_() + 0.2, le.m_20189_(), 5, 0.3, 0.2, 0.3, 0.0);
            Fx.send(sl, Fx.particle("flame"), le.m_20185_(), le.m_20186_() + 0.2, le.m_20189_(), 8, 0.3, 0.1, 0.3, 0.03);
        }
        for (int j = 0; j < 36; j++) {                                   // тёплое кольцо
            double a = j * Math.PI * 2 / 36;
            Fx.send(sl, Fx.particle(j % 2 == 0 ? "flame" : "end_rod"), p.m_20185_() + Math.cos(a) * R * 0.8, p.m_20186_() + 0.3, p.m_20189_() + Math.sin(a) * R * 0.8, 1, 0.0, 0.2, 0.0, 0.01);
        }
        Fx.sound(sl, p.m_20185_(), p.m_20186_(), p.m_20189_(), "block.respawn_anchor.charge", 0.9F, 1.8F);
        return EarthLogic.Kind.HEAT.cd;
    }

    // ================================================================ тик и уборка
    /** Сколько раз упал какой-нибудь tick* (в лог идут сбои 1, 10, 100, 1000...: без спама и без немой потери). */
    static int tickFails;

    private static void guard(Runnable r) {
        try { r.run(); }
        catch (RuntimeException | LinkageError ex) {
            tickFails++;
            if (EarthLogic.warnDue(tickFails)) { System.err.println("[explosionwand] Earth.tick: сбой №" + tickFails + ": " + ex); ex.printStackTrace(); }
        }
    }

    static void tick(ServerLevel sl) {
        long now = sl.m_46467_();
        if (now % 10 == 0) { guard(() -> tickTemps(sl, now)); guard(() -> tickWisps(sl, now)); }
        if (now % 2 == 0 && !PULLS.isEmpty()) guard(() -> tickPulls(sl, now));
        if (now % 4 == 0 && !BEACONS.isEmpty()) guard(() -> tickBeacons(sl, now));
        if (!CASTS.isEmpty()) guard(() -> tickCasts(sl));
        if (now % 2 == 0 && !SPARKS.isEmpty()) guard(() -> tickSparks(sl));
        if (now % 5 == 0 && !DECOYS.isEmpty()) guard(() -> tickDecoys(sl, now));
        if (now % 10 == 0 && !SKIN.isEmpty()) guard(() -> tickSkin(sl, now));
        if (now % 200 == 0) guard(() -> sweepDecoys(sl));
    }

    /** Конец сервера: статическое состояние не должно перейти в следующий мир. Временные блоки лежат в EarthData и уберутся по сроку. */
    static void clear() { WISPS.clear(); PULLS.clear(); BEACONS.clear(); CASTS.clear(); SPARKS.clear(); SKIN.clear(); DECOYS.clear(); TOOL = null; }
}
