package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;

/** Арена Сердца Разлома: парящая площадка в пустоте под небом Энда. */
final class Arena {
    static boolean cleared = false;
    static int empty = 0, intro = 0; static long tk = 0;
    static final int FLOOR = Boss.FLOOR;
    static DustParticleOptions GOLD, PURPLE;

    static void tick(ServerLevel level) {
        tk++;
        if (Boss.ACTIVE != null && Boss.ACTIVE.level != level) Boss.ACTIVE = null;   // мир перезагружали
        List<ServerPlayer> ps = new ArrayList<>();
        for (ServerPlayer p : level.m_6907_()) if (p.m_6084_()) ps.add(p);
        if (ps.isEmpty()) {
            if (++empty == 100) {
                if (Boss.ACTIVE != null && Boss.ACTIVE.level == level) { Boss.ACTIVE.reset(); Boss.ACTIVE = null; }
                cleared = false; intro = 0;
            }
            return;
        }
        empty = 0;
        ensureBuilt(level);
        if (Boss.ACTIVE == null && !cleared) {
            intro++;
            if (intro == 1) {
                Cmd.run(level, 0.5, FLOOR, 0.5, "kill @e[tag=ewb]");
                Fx.sound(level, 0.5, FLOOR + 6, 0.5, "entity.warden.heartbeat", 20.0F, 0.5F);
            }
            if (intro % 15 == 0 && intro < 60) Fx.sound(level, 0.5, FLOOR + 6, 0.5, "entity.warden.heartbeat", 20.0F, 0.5F + intro / 120F);
            if (intro > 20 && intro < 60) Fx.send(level, Fx.particle("reverse_portal"), 0.5, FLOOR + 6, 0.5, 40, 0.2, 0.2, 0.2, 1.5);
            if (intro >= 60) { Boss.spawn(level); intro = 0; }
        }
        if (cleared) exitPortal(level, ps);
    }

    static void exitPortal(ServerLevel level, List<ServerPlayer> ps) {
        if (GOLD == null) { GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.4F), 1.6F); PURPLE = new DustParticleOptions(new Vector3f(0.6F, 0.2F, 1F), 1.6F); }
        double cx = 0.5, cy = FLOOR + 1.5, cz = 0.5;
        long t = tk;
        for (int i = 0; i < 24; i++) {
            double a = t * 0.1 + i * Math.PI * 2 / 24;
            Fx.send(level, i % 2 == 0 ? GOLD : PURPLE, cx + Math.cos(a) * 1.2, cy + Math.sin(a) * 1.5, cz, 1, 0, 0, 0, 0);
        }
        Fx.send(level, Fx.particle("end_rod"), cx, cy, cz, 2, 0.3, 0.6, 0.3, 0.02);
        for (ServerPlayer p : ps) {
            Vec3 v = p.m_20182_();
            double dx = v.f_82479_ - cx, dy = v.f_82480_ + 0.9 - cy, dz = v.f_82481_ - cz;
            if (dx * dx + dy * dy + dz * dz < 1.6 * 1.6) Dims.sendHome(p, level);
        }
    }

    static void ensureBuilt(ServerLevel level) {
        if (!level.m_8055_(new BlockPos(0, FLOOR - 1, 0)).m_60795_()) return;
        for (int cx = -3; cx <= 2; cx++) for (int cz = -3; cz <= 2; cz++) level.m_6325_(cx, cz);
        BlockState crying = Fx.state("crying_obsidian"), am = Fx.state("amethyst_block"), obs = Fx.state("obsidian"),
                tiles = Fx.state("deepslate_tiles"), pol = Fx.state("polished_deepslate"), bricks = Fx.state("polished_blackstone_bricks"),
                rod = Fx.state("end_rod");
        Random r = new Random(42);
        for (int x = -27; x <= 27; x++) for (int z = -27; z <= 27; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 26.5) continue;
            BlockState s;
            if (d < 2.5) s = crying;
            else if (d < 3.5 || (d >= 9 && d < 10)) s = am;
            else if (d >= 25.5) s = obs;
            else if (r.nextInt(40) == 0) s = crying;
            else s = ((int) (d / 4)) % 2 == 0 ? tiles : pol;
            level.m_7731_(new BlockPos(x, FLOOR - 1, z), s, 18);
            level.m_7731_(new BlockPos(x, FLOOR - 2, z), obs, 18);
            if (d >= 25.5) { level.m_7731_(new BlockPos(x, FLOOR, z), bricks, 18); level.m_7731_(new BlockPos(x, FLOOR + 1, z), bricks, 18); }
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 2 / 8 + Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * 30), z = (int) Math.round(Math.sin(a) * 30);
            for (int y = FLOOR - 6; y < FLOOR + 10; y++) level.m_7731_(new BlockPos(x, y, z), obs, 18);
            level.m_7731_(new BlockPos(x, FLOOR + 10, z), am, 18);
            level.m_7731_(new BlockPos(x, FLOOR + 11, z), rod, 18);
        }
    }
}
