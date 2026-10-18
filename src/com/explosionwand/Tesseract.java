package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.*;

/**
 * Тессеракт. Манящий портал. Кто войдёт — окажется в комнате из книжных полок над этим местом
 * и сможет только смотреть, как внизу его двойник делает странные вещи... и уходит умирать.
 * Можно ломать полки — внизу у двойника падает книга. Он замрёт. И не послушает.
 */
public class Tesseract {
    static final List<Tesseract> PORTALS = new ArrayList<>();
    static final List<Session> SESSIONS = new ArrayList<>();
    static final Random RNG = Fx.RNG;
    static final int PORTAL_LIFE = 600;
    static ParticleOptions GOLD, BLUE, WHITE, RED, BLACK, THREAD;

    static void init() {
        if (GOLD != null) return;
        GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.8F, 0.35F), 1.6F);
        BLUE = new DustParticleOptions(new Vector3f(0.35F, 0.65F, 1.0F), 1.8F);
        WHITE = new DustParticleOptions(new Vector3f(1, 1, 1), 1.2F);
        RED = new DustParticleOptions(new Vector3f(0.7F, 0, 0), 1.6F);
        BLACK = new DustParticleOptions(new Vector3f(0, 0, 0), 2.4F);
        THREAD = new DustParticleOptions(new Vector3f(1.0F, 0.9F, 0.6F), 0.7F);
    }

    // ================= Портал =================
    final ServerLevel level; final double cx, cy, cz, px, pz;
    int t = 0;

    Tesseract(ServerLevel level, Vec3 c, double lx, double lz) {
        init();
        this.level = level; this.cx = c.f_82479_; this.cy = c.f_82480_; this.cz = c.f_82481_;
        double l = Math.sqrt(lx * lx + lz * lz); if (l < 1e-4) { lx = 1; lz = 0; l = 1; }
        this.px = -lz / l; this.pz = lx / l;
        Fx.sound(level, cx, cy, cz, "block.beacon.activate", 3.0F, 0.8F);
        Fx.sound(level, cx, cy, cz, "block.enchantment_table.use", 3.0F, 0.6F);
    }

    void p(ParticleOptions o, double u, double v) { Fx.send(level, o, cx + px * u, cy + v, cz + pz * u, 1, 0, 0, 0, 0); }

    boolean tick() {
        t++;
        double r = 2.4 * Math.min(1.0, t / 30.0);
        double rot = t * 0.05;
        // квадратная рамка, вложенные «кубы» — как проекция тессеракта
        for (int k = 0; k < 3; k++) {
            double s = r * (1.0 - k * 0.28) * (1 + 0.05 * Math.sin(t * 0.1 + k));
            ParticleOptions c = k == 0 ? GOLD : (k == 1 ? BLUE : WHITE);
            for (double q = -s; q <= s; q += 0.35) {
                double a = rot * (k % 2 == 0 ? 1 : -1);
                double[][] pts = {{q, s}, {q, -s}, {s, q}, {-s, q}};
                for (double[] pt : pts) {
                    double u = pt[0] * Math.cos(a) - pt[1] * Math.sin(a), v = pt[0] * Math.sin(a) + pt[1] * Math.cos(a);
                    p(c, u, v);
                }
            }
        }
        Fx.send(level, Fx.particle("enchant"), cx, cy, cz, 12, 1.5, 1.5, 1.5, 0.6);
        if (t % 40 == 1) Fx.sound(level, cx, cy, cz, "block.beacon.ambient", 3.0F, 1.4F);
        if (t % 60 == 30) Fx.sound(level, cx, cy, cz, "block.amethyst_block.chime", 2.0F, 1.0F);
        if (t > 20) for (Entity e : level.m_6249_(null, new AABB(cx - 2, cy - 3, cz - 2, cx + 2, cy + 2, cz + 2), e -> e instanceof ServerPlayer && e.m_6084_())) {
            ServerPlayer pl = (ServerPlayer) e;
            Vec3 pp = pl.m_20182_();
            double dx = cx - pp.f_82479_, dy = cy - (pp.f_82480_ + 0.9), dz = cz - pp.f_82481_;
            if (dx * dx + dy * dy + dz * dz > 1.7 * 1.7) continue;
            if (Session.of(pl) != null) continue;
            SESSIONS.add(new Session(level, pl, cx, cz, SESSIONS.size()));
        }
        return t >= PORTAL_LIFE;
    }

    // ================= Сеанс наблюдения =================
    static final class Session {
        final ServerLevel level; final ServerPlayer player;
        final int ox, oz, ground, roomY;
        final double homeX, homeZ;
        final List<BlockPos> built = new ArrayList<>();
        final Set<Long> builtKeys = new HashSet<>();
        final List<Entity> books = new ArrayList<>();
        Mob dbl;
        double dx, dy, dz;            // позиция двойника
        float yaw, pitch;
        double ax, az, bx, bz;        // точки маршрута
        int t = 0, pause = 0;
        double bookX, bookZ;
        boolean done = false;

        static Session of(ServerPlayer p) { for (Session s : SESSIONS) if (s.player == p) return s; return null; }
        static long key(int x, int y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF); }

        Session(ServerLevel level, ServerPlayer player, double px, double pz, int idx) {
            this.level = level; this.player = player;
            this.homeX = px; this.homeZ = pz;
            this.ox = (int) Math.floor(px); this.oz = (int) Math.floor(pz);
            this.ground = level.m_6924_(Heightmap.Types.valueOf("MOTION_BLOCKING_NO_LEAVES"), ox, oz);
            this.roomY = Math.min(level.m_151558_() - 12, ground + 22 + idx * 9);
            build();
            Vec3 pp = player.m_20182_();
            float startYaw = player.m_146908_();
            player.m_6021_(ox + 0.5, roomY, oz + 0.5);
            player.f_19789_ = 0;
            var nausea = Fx.effect("nausea"); var dark = Fx.effect("darkness");
            if (nausea != null) player.m_7292_(new MobEffectInstance(nausea, 120, 0));
            if (dark != null) player.m_7292_(new MobEffectInstance(dark, 40, 0));
            Fx.sound(level, ox, roomY, oz, "block.portal.travel", 0.6F, 1.6F);
            Fx.sound(level, ox, roomY, oz, "block.beacon.activate", 2.0F, 0.5F);
            // маршрут двойника
            double yr = Math.toRadians(startYaw);
            double fx = -Math.sin(yr), fz = Math.cos(yr);
            ax = ox + 0.5 + fx * 12; az = oz + 0.5 + fz * 12;
            double away = RNG.nextDouble() * Math.PI * 2;
            bx = ox + 0.5 + Math.cos(away) * 24; bz = oz + 0.5 + Math.sin(away) * 24;
            spawnDouble(startYaw);
        }

        BlockState BOOK = Fx.state("bookshelf"), BARRIER = Fx.state("barrier"), AIR = Fx.state("air");

        void put(int x, int y, int z, BlockState s) {
            if (s == null) return;
            BlockPos p = new BlockPos(x, y, z);
            if (!level.m_8055_(p).m_60795_()) return;
            level.m_7731_(p, s, 3);
            built.add(p); builtKeys.add(key(x, y, z));
        }

        /** Комната из полок с невидимым полом, решётка полок вокруг. */
        void build() {
            int X = ox, Y = roomY, Z = oz;
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) put(X + x, Y - 1, Z + z, BARRIER);   // невидимый пол
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 3; y++) {
                boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                boolean ceil = y == 3;
                if (!wall && !ceil) continue;
                boolean window = y == 1 && ((Math.abs(x) == 2 && z == 0) || (Math.abs(z) == 2 && x == 0));
                if (!window) put(X + x, Y + y, Z + z, BOOK);
            }
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = 0; y <= 4; y++)
                if (Math.abs(x) == 3 || Math.abs(z) == 3 || y == 4) put(X + x, Y + y, Z + z, BARRIER);   // за полками — невидимая стена
            // бесконечная решётка полок
            for (int x = -10; x <= 10; x++) for (int y = 0; y <= 12; y++) for (int z = -10; z <= 10; z++) {
                if (Math.abs(x) <= 3 && Math.abs(z) <= 3 && y <= 4) continue;
                int c = (x % 4 == 0 ? 1 : 0) + ((y + 1) % 4 == 0 ? 1 : 0) + (z % 4 == 0 ? 1 : 0);
                if (c >= 2) put(X + x, Y + y, Z + z, BOOK);
            }
        }

        void spawnDouble(float yaw0) {
            Object type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("minecraft", "zombie"));
            if (!(type instanceof EntityType<?> et)) return;
            Entity e = et.m_20615_(level);
            if (!(e instanceof Mob m)) return;
            dbl = m;
            dx = ox + 0.5; dz = oz + 0.5; dy = ground; yaw = yaw0;
            m.m_6034_(dx, dy, dz);
            m.m_146922_(yaw); m.m_5616_(yaw); m.f_20883_ = yaw;
            m.m_21557_(true); m.m_20225_(true); m.m_20331_(true); m.m_21530_();
            m.m_6593_(Component.m_237113_(player.m_36316_().getName()));
            m.m_20340_(true);
            Object head = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "player_head"));
            if (head instanceof Item hi) {
                ItemStack hs = new ItemStack(hi);
                hs.m_41784_().m_128359_("SkullOwner", player.m_36316_().getName());
                m.m_8061_(EquipmentSlot.valueOf("HEAD"), hs);
            }
            for (String slot : new String[]{"CHEST", "LEGS", "FEET", "MAINHAND"}) {
                EquipmentSlot s = EquipmentSlot.valueOf(slot);
                ItemStack it = player.m_6844_(s);
                if (it != null) m.m_8061_(s, it.m_41777_());
            }
            level.m_7967_(m);
            Fx.send(level, Fx.particle("enchant"), dx, dy + 1, dz, 60, 0.5, 1, 0.5, 1.0);
        }

        // --- поведение двойника ---
        void look(float targetYaw, float targetPitch, float speed) {
            yaw = approach(yaw, targetYaw, speed); pitch = approachLin(pitch, targetPitch, speed);
        }
        static float wrap(float a) { a %= 360; if (a >= 180) a -= 360; if (a < -180) a += 360; return a; }
        static float approach(float a, float b, float s) { float d = wrap(b - a); return a + Math.max(-s, Math.min(s, d)); }
        static float approachLin(float a, float b, float s) { return a + Math.max(-s, Math.min(s, b - a)); }
        float yawTo(double x, double z) { return (float) Math.toDegrees(Math.atan2(-(x - dx), z - dz)); }

        void lookAtPlayer(float speed) {
            Vec3 p = player.m_20182_();
            double ddx = p.f_82479_ - dx, ddz = p.f_82481_ - dz, ddy = (p.f_82480_ + 1.6) - (dy + 1.6);
            float ty = (float) Math.toDegrees(Math.atan2(-ddx, ddz));
            float tp = (float) -Math.toDegrees(Math.atan2(ddy, Math.sqrt(ddx * ddx + ddz * ddz)));
            look(ty, tp, speed);
        }

        boolean walkTo(double x, double z, double speed, boolean face) {
            double ex = x - dx, ez = z - dz, d = Math.sqrt(ex * ex + ez * ez);
            if (d < 0.3) return true;
            double k = Math.min(speed, d) / d;
            dx += ex * k; dz += ez * k;
            int h = level.m_6924_(Heightmap.Types.valueOf("MOTION_BLOCKING_NO_LEAVES"), (int) Math.floor(dx), (int) Math.floor(dz));
            dy += Math.max(-0.5, Math.min(0.5, h - dy));
            if (face) look(yawTo(x, z), 0, 12);
            return false;
        }

        void apply() {
            if (dbl == null) return;
            dbl.m_6034_(dx, dy, dz);
            dbl.m_146922_(yaw); dbl.m_5616_(yaw); dbl.f_20883_ = yaw; dbl.m_146926_(pitch);
        }

        void signal(BlockPos pos) {
            if (dbl == null || t > 800) return;
            pause = 50;
            bookX = dx + (RNG.nextDouble() - 0.5) * 3; bookZ = dz + (RNG.nextDouble() - 0.5) * 3;
            Object book = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "book"));
            if (book instanceof Item bi) {
                ItemEntity ie = new ItemEntity(level, bookX, dy + 3, bookZ, new ItemStack(bi));
                level.m_7967_(ie); books.add(ie);
            }
            Fx.sound(level, bookX, dy + 1, bookZ, "item.book.page_turn", 2.0F, 0.6F);
            Fx.send(level, Fx.particle("enchant"), bookX, dy + 1.5, bookZ, 30, 0.4, 0.6, 0.4, 0.5);
        }

        boolean tick() {
            t++;
            if (!player.m_6084_() || !level.m_6907_().contains(player)) { cleanup(false); return true; }
            ambience();
            if (dbl != null && !dbl.m_6084_()) dbl = null;
            if (pause > 0 && t < 800) {          // смотрит на упавшую книгу... и не слушает
                pause--;
                look(yawTo(bookX, bookZ), 35, 8);
                if (pause == 10) look(yawTo(bookX, bookZ) + 180, 0, 180);
                apply();
                return false;
            }
            if (t < 120) {                                   // стоит, дёргает головой
                if (t % 15 == 0) yaw += (float) ((RNG.nextDouble() - 0.5) * 120);
                pitch = (float) ((RNG.nextDouble() - 0.5) * 30);
            } else if (t < 280) {                            // уходит
                walkTo(ax, az, 0.12, true);
            } else if (t < 360) {                            // медленно поднимает голову — на тебя
                lookAtPlayer(3);
                if (t == 300) Fx.sound(level, dx, dy + 1, dz, "ambient.cave", 2.0F, 0.6F);
            } else if (t < 520) {                            // пятится, не сводя глаз
                walkTo(ox + 0.5, oz + 0.5, 0.07, false);
                lookAtPlayer(10);
            } else if (t < 600) {                            // кружится
                yaw += 9; pitch = 0;
                if (t % 20 == 0) Fx.sound(level, ox, roomY, oz, "entity.warden.heartbeat", 1.5F, 0.8F);
            } else if (t < 800) {                            // уходит прочь
                walkTo(bx, bz, 0.1, true);
                if (t % 40 == 0) Fx.sound(level, ox, roomY, oz, "entity.warden.heartbeat", 1.5F, 1.0F);
            } else if (t < 830) {                            // оборачивается. последний взгляд
                lookAtPlayer(12);
            } else if (t < 880) {                            // поднимается в воздух, дёргается
                dy += 0.07;
                yaw += (float) ((RNG.nextDouble() - 0.5) * 70);
                pitch = (float) ((RNG.nextDouble() - 0.5) * 80);
                if (dbl != null) dbl.m_20242_(true);
                Fx.send(level, RED, dx, dy + 1, dz, 6, 0.3, 0.6, 0.3, 0);
                Fx.send(level, Fx.particle("soul"), dx, dy + 1, dz, 2, 0.3, 0.5, 0.3, 0.02);
                if (t % 10 == 0) Fx.sound(level, dx, dy, dz, "entity.player.hurt", 1.5F, 0.5F + RNG.nextFloat() * 0.3F);
            } else if (t == 880) {                           // конец
                death();
            } else if (t >= 960) {
                cleanup(true);
                return true;
            }
            if (t < 880) apply();
            return false;
        }

        void ambience() {
            int X = ox, Y = roomY, Z = oz;
            // нити времени вдоль решётки
            for (int i = 0; i < 6; i++) {
                int ax2 = RNG.nextInt(3);
                double a = (RNG.nextInt(6) - 2.5) * 4, b = (RNG.nextInt(4)) * 4 - 1;
                double s = (RNG.nextDouble() - 0.5) * 20;
                double x = X + 0.5, y = Y + 0.5, z = Z + 0.5;
                if (ax2 == 0) { x += s; y += b; z += a; } else if (ax2 == 1) { x += a; y += b; z += s; } else { x += a; y += (RNG.nextDouble()) * 12; z += b; }
                Fx.sendTo(level, player, THREAD, x, y, z, 3, ax2 == 0 ? 1.5 : 0, ax2 == 2 ? 1.5 : 0, ax2 == 1 ? 1.5 : 0, 0);
            }
            Fx.sendTo(level, player, Fx.particle("enchant"), X + 0.5, Y + 1.5, Z + 0.5, 6, 2, 1, 2, 0.5);
            if (t % 70 == 0) Fx.sound(level, X, Y, Z, "block.beacon.ambient", 1.5F, 0.5F);
            if (t % 130 == 60) Fx.sound(level, X, Y, Z, "entity.allay.ambient_without_item", 0.8F, 0.4F);
        }

        void death() {
            Fx.send(level, BLACK, dx, dy + 1, dz, 120, 0.5, 1.0, 0.5, 0);
            Fx.send(level, RED, dx, dy + 1, dz, 60, 0.5, 1.0, 0.5, 0);
            Fx.send(level, Fx.particle("soul"), dx, dy + 1, dz, 40, 0.4, 0.8, 0.4, 0.1);
            Fx.send(level, Fx.particle("flash"), dx, dy + 1, dz, 1, 0, 0, 0, 0);
            Fx.sound(level, dx, dy, dz, "entity.player.death", 3.0F, 0.5F);
            Fx.sound(level, dx, dy, dz, "entity.wither.death", 2.0F, 1.6F);
            Fx.sound(level, ox, roomY, oz, "entity.warden.heartbeat", 3.0F, 0.4F);
            if (dbl != null) { dbl.m_146870_(); dbl = null; }
            // на месте смерти — череп и розы иссушения
            int gx = (int) Math.floor(dx), gz = (int) Math.floor(dz);
            int gy = level.m_6924_(Heightmap.Types.valueOf("MOTION_BLOCKING_NO_LEAVES"), gx, gz);
            BlockState skull = Fx.state("skeleton_skull"), rose = Fx.state("wither_rose");
            BlockPos sp = new BlockPos(gx, gy, gz);
            if (skull != null && level.m_8055_(sp).m_60795_()) level.m_7731_(sp, skull, 3);
            for (int i = 0; i < 6; i++) {
                BlockPos rp = new BlockPos(gx + RNG.nextInt(5) - 2, gy, gz + RNG.nextInt(5) - 2);
                if (rose != null && level.m_8055_(rp).m_60795_() && rose.m_60710_(level, rp)) level.m_7731_(rp, rose, 3);
            }
            homeX2 = gx + 0.5; homeZ2 = gz + 0.5;
        }
        double homeX2 = Double.NaN, homeZ2;

        void cleanup(boolean returnPlayer) {
            if (done) return; done = true;
            if (dbl != null) dbl.m_146870_();
            for (Entity b : books) if (b.m_6084_()) b.m_146870_();
            for (BlockPos p : built) {
                BlockState s = level.m_8055_(p);
                if (s == BOOK || s == BARRIER) level.m_7731_(p, AIR, 3);
            }
            if (returnPlayer && player.m_6084_()) {
                double x = Double.isNaN(homeX2) ? homeX : homeX2 + 1.5, z = Double.isNaN(homeX2) ? homeZ : homeZ2;
                int y = level.m_6924_(Heightmap.Types.valueOf("MOTION_BLOCKING_NO_LEAVES"), (int) Math.floor(x), (int) Math.floor(z));
                player.m_6021_(x, y, z); player.f_19789_ = 0;
                var dark = Fx.effect("darkness"); var nausea = Fx.effect("nausea"); var slow = Fx.effect("slowness");
                if (dark != null) player.m_7292_(new MobEffectInstance(dark, 100, 0));
                if (nausea != null) player.m_7292_(new MobEffectInstance(nausea, 200, 0));
                if (slow != null) player.m_7292_(new MobEffectInstance(slow, 200, 1));
                Fx.sound(level, x, y, z, "entity.elder_guardian.curse", 1.0F, 0.6F);
            }
        }
    }

    static void tickSessions(ServerLevel level) {
        Iterator<Session> it = SESSIONS.iterator();
        while (it.hasNext()) { Session s = it.next(); if (s.level == level && s.tick()) it.remove(); }
    }

    /** Сломал полку в тессеракте — внизу у двойника падает книга. */
    static void onBreak(BlockEvent.BreakEvent ev) {
        if (!(ev.getPlayer() instanceof ServerPlayer p)) return;
        Session s = Session.of(p);
        if (s == null) return;
        BlockPos pos = ev.getPos();
        if (!s.builtKeys.contains(Session.key(pos.m_123341_(), pos.m_123342_(), pos.m_123343_()))) return;
        ev.setCanceled(true);                       // полка не ломается — время не изменить
        s.signal(pos);
    }
}
