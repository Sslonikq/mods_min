package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/** Арена Небесного Кита: облачная платформа над бездной в вечных сумерках. */
final class Arena4 {
    static boolean cleared = false;
    static int empty = 0, intro = 0; static long tk = 0;
    static final int FLOOR = Boss4.FLOOR, R = Boss4.ARENA_R;
    static DustParticleOptions C;

    static void tick(ServerLevel level) {
        tk++;
        if (Boss4.ACTIVE != null && Boss4.ACTIVE.level != level) Boss4.ACTIVE = null;
        List<ServerPlayer> ps = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) if (p.m_6084_()) ps.add(p);
        if (ps.isEmpty()) { if (++empty == 100) { if (Boss4.ACTIVE != null) { Boss4.ACTIVE.reset(); Boss4.ACTIVE = null; } cleared = false; intro = 0; } return; }
        empty = 0;
        ensureBuilt(level);
        if (C == null) C = new DustParticleOptions(new Vector3f(0.45F, 0.8F, 1.0F), 1.6F);
        if (Boss4.ACTIVE == null && !cleared) {
            intro++;
            if (intro == 1) { Cmd.run(level, 0.5, FLOOR, 0.5, "kill @e[tag=ewb4]"); build(level); }
            if (intro % 20 == 0 && intro < 100) Fx.sound(level, 0.5, FLOOR + 20, 0.5, "entity.elder_guardian.ambient", 25.0F, 0.3F + intro / 300F);
            if (intro > 30 && intro < 100) Fx.send(level, Fx.particle("cloud"), 0.5, FLOOR + 18, -20, 15, 4, 2, 4, 0.05);
            if (intro >= 100) { Boss4.spawn(level); intro = 0; }
        }
        if (cleared) exitPortal(level, ps);
        // проплывающие облака
        if (tk % 10 == 0) for (int i = 0; i < 3; i++) {
            double a = Fx.RNG.nextDouble() * Math.PI * 2, r = R + 6 + Fx.RNG.nextDouble() * 20;
            Fx.send(level, Fx.particle("cloud"), Math.cos(a) * r, FLOOR - 6 + Fx.RNG.nextDouble() * 20, Math.sin(a) * r, 6, 2, 0.5, 2, 0.01);
        }
    }

    static void exitPortal(ServerLevel level, List<ServerPlayer> ps) {
        double cx = 0.5, cy = FLOOR + 1.5, cz = 0.5;
        for (int i = 0; i < 24; i++) { double a = tk * 0.1 + i * Math.PI * 2 / 24; Fx.send(level, C, cx + Math.cos(a) * 1.2, cy + Math.sin(a) * 1.5, cz, 1, 0, 0, 0, 0); }
        for (ServerPlayer p : ps) { Vec3 v = p.m_20182_(); double dx = v.f_82479_ - cx, dy = v.f_82480_ + 0.9 - cy, dz = v.f_82481_ - cz; if (dx * dx + dy * dy + dz * dz < 1.6 * 1.6) Dims.sendHome(p, level); }
    }

    static void ensureBuilt(ServerLevel level) { if (level.m_8055_(new BlockPos(0, FLOOR - 1, 0)).m_60795_()) build(level); }

    static void build(ServerLevel level) {
        for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= 2; cz++) level.m_6325_(cx, cz);
        BlockState white = Fx.state("white_concrete"), snow = Fx.state("snow_block"), ice = Fx.state("light_blue_concrete"), calcite = Fx.state("calcite"),
                lamp = Fx.state("sea_lantern"), wool = Fx.state("white_wool"), quartz = Fx.state("quartz_pillar"), air = Fx.state("air");
        Random rng = new Random(4242);
        for (int x = -R - 2; x <= R + 2; x++) for (int z = -R - 2; z <= R + 2; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > R + 0.5) continue;
            BlockState s;
            if (d < 2) s = lamp;
            else if ((d >= 10 && d < 10.6) || (d >= 20 && d < 20.6)) s = ice;
            else if (d >= R - 0.5) s = calcite;
            else s = (Math.sin(x * 0.45) + Math.cos(z * 0.38) > 0.6) ? snow : white;
            level.m_7731_(new BlockPos(x, FLOOR - 1, z), s, 18);
            level.m_7731_(new BlockPos(x, FLOOR - 2, z), white, 18);
            for (int y = FLOOR; y <= FLOOR + 3; y++) { BlockPos bp = new BlockPos(x, y, z); if (!Loot.isChest(level, bp)) level.m_7731_(bp, air, 18); }
            // облачная «подушка» под платформой
            if (d < R - 3 && rng.nextInt(9) == 0) for (int y = 3; y <= 3 + rng.nextInt(3); y++) level.m_7731_(new BlockPos(x, FLOOR - y, z), wool, 18);
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 2 / 8 + Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * (R + 3)), z = (int) Math.round(Math.sin(a) * (R + 3));
            for (int y = FLOOR - 4; y < FLOOR + 6; y++) level.m_7731_(new BlockPos(x, y, z), quartz, 18);
            level.m_7731_(new BlockPos(x, FLOOR + 6, z), lamp, 18);
        }
    }
}
