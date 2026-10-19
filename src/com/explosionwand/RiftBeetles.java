package com.explosionwand;

import com.explosionwand.RiftModels.Kind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Кристальные жуки у Разлома в обычном мире: мирные светящиеся жуки, днём стайками по 2-3 в пределах ~60 блоков от входа
 * (не внутри данжа, не больше {@link RiftLogic#BEETLE_CAP} на Разлом). Ночью и когда игроки ушли — исчезают.
 * Облик и хост — как у мобов Разлома (RiftMobs); тега ewd у жуков нет, зачистка залов их не касается.
 */
final class RiftBeetles {
    private RiftBeetles() {}

    static final int ENTRANCE_RANGE = 90, FLOCK_MIN = 14, FLOCK_MAX = 55, COUNT_RANGE = 80, GONE_RANGE = 110;

    static void tick(ServerLevel sl, DungeonData data, List<ServerPlayer> ps, long now) {
        if (now % 60 != 0 || data.recs.isEmpty() || sl.m_46462_()) return;   // m_46462_ = isNight: жуки только днём
        for (DungeonData.Rec r : data.recs.values()) {
            if (r.def() != Dungeons.RIFT || r.partsDone == 0) continue;
            boolean near = false;
            for (ServerPlayer p : ps) {
                double dx = p.m_20185_() - r.x, dz = p.m_20189_() - r.z;
                if (dx * dx + dz * dz <= (double) ENTRANCE_RANGE * ENTRANCE_RANGE) { near = true; break; }
            }
            if (!near || RiftMobs.RNG.nextInt(3) != 0) continue;
            int have = 0;   // считаем сущности, а не реестр: жуки, пережившие выгрузку, тоже занимают место
            AABB box = new AABB(r.x - COUNT_RANGE, r.y - 60, r.z - COUNT_RANGE, r.x + COUNT_RANGE, r.y + 80, r.z + COUNT_RANGE);
            for (Entity e : sl.m_6249_(null, box, en -> en.m_19880_().contains("ewrk_" + Kind.BEETLE.id))) {
                if (!(e instanceof LivingEntity le) || !le.m_6084_()) continue;
                if (RiftMobs.MOBS.containsKey(e.m_20148_())) have++;
                else RiftMobs.remove(e);   // не из этой сессии: убрать вместе с частями
            }
            int n = RiftLogic.beetleFlock(have, RiftMobs.RNG.nextInt(100));
            if (n == 0) continue;
            double ang = RiftMobs.RNG.nextDouble() * Math.PI * 2, rad = FLOCK_MIN + RiftMobs.RNG.nextDouble() * (FLOCK_MAX - FLOCK_MIN);
            double cx = r.x + Math.cos(ang) * rad, cz = r.z + Math.sin(ang) * rad;
            for (int i = 0; i < n; i++) {
                double bx = cx + (RiftMobs.RNG.nextDouble() - 0.5) * 5, bz = cz + (RiftMobs.RNG.nextDouble() - 0.5) * 5;
                if (!chunkLoaded(sl, bx, bz)) break;
                int by = Dungeons.groundY(sl, (int) Math.floor(bx), (int) Math.floor(bz));
                if (Dungeons.wet(sl, (int) Math.floor(bx), by - 1, (int) Math.floor(bz)) || Dungeons.near(sl, bx, by, bz, 6)) continue;
                RiftMobs.Mob m = RiftMobs.spawnKind(sl, Kind.BEETLE, bx, by, bz, null);
                if (m != null) m.home(cx, by, cz);
            }
        }
    }

    static boolean chunkLoaded(ServerLevel sl, double x, double z) {
        long cx = (long) Math.floor(x) >> 4, cz = (long) Math.floor(z) >> 4;
        return sl.m_143319_((cx & 0xFFFFFFFFL) | ((cz & 0xFFFFFFFFL) << 32));
    }

    /** Жук: бродит у центра стайки (home), от игрока ближе 3,5 блоков удирает. */
    static final class Beetle extends RiftMobs.Mob {
        double wx, wz; int wanderT = 0;
        { leash = 15; }

        @Override void think() {
            pose.t = t;
            pose.atk = Math.max(0, pose.atk - 0.05);
            if (sl().m_46462_()) {   // ночью жуки зарываются
                if (RiftMobs.RNG.nextInt(60) == 0) retire();
                return;
            }
            ServerPlayer p = nearest(3.5, 3);
            if (p != null) {
                double dx = x - p.m_20185_(), dz = z - p.m_20189_(), l = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
                yaw = Math.atan2(dx, dz);
                if (walk(x + dx / l * 3, z + dz / l * 3, 0.12, 1)) { pose.ph += 1.4; pose.atk = 1; }
                wanderT = 0;
                return;
            }
            if (wanderT > 0) {
                wanderT--;
                face(wx, wz, 0.2);
                if (walk(wx, wz, 0.04, 1)) pose.ph += 0.6;
            } else if (RiftMobs.RNG.nextInt(50) == 0) {
                wx = hx + (RiftMobs.RNG.nextDouble() - 0.5) * 7; wz = hz + (RiftMobs.RNG.nextDouble() - 0.5) * 7; wanderT = 30 + RiftMobs.RNG.nextInt(40);
            }
        }

        ServerLevel sl() { return level; }

        void retire() {
            Fx.send(level, Fx.particle("poof"), x, y + 0.2, z, 6, 0.2, 0.1, 0.2, 0.01);
            RiftMobs.retire(this);
        }

        @Override void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent ev, net.minecraft.world.entity.Entity atk) {
            pose.atk = 1;
        }
    }
}
