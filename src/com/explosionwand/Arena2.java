package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/** Арена Короля Затмения: огромный чёрно-золотой диск в багровой мгле. */
final class Arena2 {
    static boolean cleared = false;
    static int empty = 0, intro = 0; static long tk = 0;
    static final int FLOOR = Boss2.FLOOR, R = Boss2.ARENA_R;
    static DustParticleOptions GOLD, ORANGE;

    static void tick(ServerLevel level) {
        tk++;
        if (Boss2.ACTIVE != null && Boss2.ACTIVE.level != level) Boss2.ACTIVE = null;
        List<ServerPlayer> ps = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) if (p.m_6084_()) ps.add(p);
        if (ps.isEmpty()) {
            if (++empty == 100) {
                if (Boss2.ACTIVE != null) { Boss2.ACTIVE.reset(); Boss2.ACTIVE = null; }
                cleared = false; intro = 0;
            }
            return;
        }
        empty = 0;
        ensureBuilt(level);
        if (Boss2.ACTIVE == null && !cleared) {
            intro++;
            if (intro == 1) { Cmd.run(level, 0.5, FLOOR, 0.5, "kill @e[tag=ewb2]"); build(level); }
            if (intro % 12 == 0 && intro < 80) Fx.sound(level, 0.5, FLOOR + 9, 0.5, "entity.warden.heartbeat", 25.0F, 0.4F + intro / 160F);
            if (intro > 20 && intro < 80) {
                Fx.send(level, Fx.particle("flame"), 0.5, FLOOR + 9, 0.5, 30, 0.3, 0.3, 0.3, 0.4);
                Fx.send(level, Fx.particle("end_rod"), 0.5, FLOOR + 9, 0.5, 10, 6, 6, 6, 0);
            }
            if (intro >= 80) { Boss2.spawn(level); intro = 0; }
        }
        if (cleared) exitPortal(level, ps);
    }

    static void exitPortal(ServerLevel level, List<ServerPlayer> ps) {
        if (GOLD == null) { GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.4F), 1.6F); ORANGE = new DustParticleOptions(new Vector3f(1F, 0.45F, 0.05F), 1.6F); }
        double cx = 0.5, cy = FLOOR + 1.5, cz = 0.5;
        for (int i = 0; i < 24; i++) { double a = tk * 0.1 + i * Math.PI * 2 / 24; Fx.send(level, i % 2 == 0 ? GOLD : ORANGE, cx + Math.cos(a) * 1.2, cy + Math.sin(a) * 1.5, cz, 1, 0, 0, 0, 0); }
        for (ServerPlayer p : ps) {
            Vec3 v = p.m_20182_(); double dx = v.f_82479_ - cx, dy = v.f_82480_ + 0.9 - cy, dz = v.f_82481_ - cz;
            if (dx * dx + dy * dy + dz * dz < 1.6 * 1.6) Dims.sendHome(p, level);
        }
    }

    static void ensureBuilt(ServerLevel level) {
        if (!level.m_8055_(new BlockPos(0, FLOOR - 1, 0)).m_60795_()) return;
        build(level);
    }

    /** Строит (и восстанавливает после коллапса) арену. */
    static void build(ServerLevel level) {
        for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= 2; cz++) level.m_6325_(cx, cz);
        BlockState black = Fx.state("black_concrete"), gold = Fx.state("gold_block"), obs = Fx.state("obsidian"), pb = Fx.state("polished_blackstone"),
                bs = Fx.state("blackstone"), gild = Fx.state("gilded_blackstone"), bricks = Fx.state("polished_blackstone_bricks"), air = Fx.state("air"),
                lantern = Fx.state("shroomlight");
        Random r = new Random(7);
        for (int x = -R - 2; x <= R + 2; x++) for (int z = -R - 2; z <= R + 2; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > R + 0.5) continue;
            BlockState s;
            if (d < 4) s = black;
            else if ((d >= 4 && d < 5) || (d >= 13 && d < 14) || (d >= 22 && d < 23)) s = gold;
            else if (d >= R - 0.5) s = obs;
            else if (r.nextInt(30) == 0) s = gild;
            else s = ((int) (Math.atan2(z, x) / (Math.PI / 8) + 16) % 2 == 0) ? pb : bs;
            level.m_7731_(new BlockPos(x, FLOOR - 1, z), s, 18);
            level.m_7731_(new BlockPos(x, FLOOR - 2, z), obs, 18);
            for (int y = FLOOR; y <= FLOOR + 4; y++) { BlockPos bp = new BlockPos(x, y, z); if (!Loot.isChest(level, bp)) level.m_7731_(bp, (d >= R - 0.5 && y <= FLOOR + 1) ? bricks : air, 18); }
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12;
            int x = (int) Math.round(Math.cos(a) * (R + 4)), z = (int) Math.round(Math.sin(a) * (R + 4));
            for (int y = FLOOR - 10; y < FLOOR + 14; y++) level.m_7731_(new BlockPos(x, y, z), obs, 18);
            level.m_7731_(new BlockPos(x, FLOOR + 14, z), gold, 18);
            level.m_7731_(new BlockPos(x, FLOOR + 15, z), lantern, 18);
        }
    }
}
