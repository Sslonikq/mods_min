package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/** Арена Недремлющего: чёрный диск с бирюзовыми рунами-кольцами в бездне без неба. */
final class Arena3 {
    static boolean cleared = false;
    static int empty = 0, intro = 0; static long tk = 0;
    static final int FLOOR = Boss3.FLOOR, R = Boss3.ARENA_R;
    static DustParticleOptions C;

    static void tick(ServerLevel level) {
        tk++;
        if (Boss3.ACTIVE != null && Boss3.ACTIVE.level != level) Boss3.ACTIVE = null;
        List<ServerPlayer> ps = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) if (p.m_6084_()) ps.add(p);
        if (ps.isEmpty()) { if (++empty == 100) { if (Boss3.ACTIVE != null) { Boss3.ACTIVE.reset(); Boss3.ACTIVE = null; } cleared = false; intro = 0; } return; }
        empty = 0;
        ensureBuilt(level);
        if (Boss3.ACTIVE == null && !cleared) {
            intro++;
            if (intro == 1) { Cmd.run(level, 0.5, FLOOR, 0.5, "kill @e[tag=ewb3]"); build(level); }
            if (intro % 12 == 0 && intro < 90) Fx.sound(level, 0.5, FLOOR + 12, 0.5, "entity.warden.heartbeat", 25.0F, 0.3F + intro / 180F);
            if (intro > 25 && intro < 90) { if (C == null) C = new DustParticleOptions(new Vector3f(0.2F, 0.95F, 0.95F), 1.5F); Fx.send(level, C, 0.5, FLOOR + 12, 0.5, 20, 1, 3, 1, 0.3); }
            if (intro >= 90) { Boss3.spawn(level); intro = 0; }
        }
        if (cleared) exitPortal(level, ps);
    }

    static void exitPortal(ServerLevel level, List<ServerPlayer> ps) {
        if (C == null) C = new DustParticleOptions(new Vector3f(0.2F, 0.95F, 0.95F), 1.5F);
        double cx = 0.5, cy = FLOOR + 1.5, cz = 0.5;
        for (int i = 0; i < 24; i++) { double a = tk * 0.1 + i * Math.PI * 2 / 24; Fx.send(level, C, cx + Math.cos(a) * 1.2, cy + Math.sin(a) * 1.5, cz, 1, 0, 0, 0, 0); }
        for (ServerPlayer p : ps) { Vec3 v = p.m_20182_(); double dx = v.f_82479_ - cx, dy = v.f_82480_ + 0.9 - cy, dz = v.f_82481_ - cz; if (dx * dx + dy * dy + dz * dz < 1.6 * 1.6) Dims.sendHome(p, level); }
    }

    static void ensureBuilt(ServerLevel level) { if (level.m_8055_(new BlockPos(0, FLOOR - 1, 0)).m_60795_()) build(level); }

    static void build(ServerLevel level) {
        for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= 2; cz++) level.m_6325_(cx, cz);
        BlockState black = Fx.state("black_concrete"), deep = Fx.state("polished_deepslate"), tiles = Fx.state("deepslate_tiles"),
                warped = Fx.state("warped_planks"), lamp = Fx.state("sea_lantern"), obs = Fx.state("obsidian"), air = Fx.state("air");
        for (int x = -R - 2; x <= R + 2; x++) for (int z = -R - 2; z <= R + 2; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > R + 0.5) continue;
            BlockState s;
            if (d < 2) s = lamp;
            else if ((d >= 9 && d < 9.6) || (d >= 18 && d < 18.6) || (d >= 27 && d < 27.6)) s = warped;   // бирюзовые кольца
            else if (d >= R - 0.5) s = obs;
            else s = ((int) (d) % 2 == 0) ? black : deep;
            level.m_7731_(new BlockPos(x, FLOOR - 1, z), s, 18);
            level.m_7731_(new BlockPos(x, FLOOR - 2, z), obs, 18);
            for (int y = FLOOR; y <= FLOOR + 3; y++) { BlockPos bp = new BlockPos(x, y, z); if (!Loot.isChest(level, bp)) level.m_7731_(bp, air, 18); }
        }
        // руны-столбы по краю
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 2 / 8;
            int x = (int) Math.round(Math.cos(a) * (R + 4)), z = (int) Math.round(Math.sin(a) * (R + 4));
            for (int y = FLOOR - 8; y < FLOOR + 10; y++) level.m_7731_(new BlockPos(x, y, z), obs, 18);
            level.m_7731_(new BlockPos(x, FLOOR + 10, z), lamp, 18);
        }
    }
}
