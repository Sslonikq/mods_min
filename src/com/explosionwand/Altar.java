package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Алтарь Разлома: каменный алтарь с аметистовыми колоннами и портал к Сердцу Разлома. */
public class Altar {
    static final List<Altar> ACTIVE = new ArrayList<>();
    static DustParticleOptions PURPLE, BLACK, PINK, DREAM1, DREAM2, SKY1, SKY2;
    final ServerLevel level; final double cx, cy, cz; final int kind; int t = 0;

    Altar(ServerLevel level, int bx, int by, int bz) { this(level, bx, by, bz, 0); }

    Altar(ServerLevel level, int bx, int by, int bz, boolean eclipse) { this(level, bx, by, bz, eclipse ? 1 : 0); }

    Altar(ServerLevel level, int bx, int by, int bz, int kind) {
        this.level = level; this.kind = kind; boolean eclipse = kind == 1;
        Boss2.init();
        if (DREAM1 == null) { DREAM1 = new DustParticleOptions(new org.joml.Vector3f(0.2F,0.95F,0.95F),1.6F); DREAM2 = new DustParticleOptions(new org.joml.Vector3f(0.1F,0.6F,1.0F),1.3F); }
        if (PURPLE == null) {
            PURPLE = new DustParticleOptions(new Vector3f(0.6F, 0.2F, 1.0F), 1.8F);
            BLACK = new DustParticleOptions(new Vector3f(0.05F, 0, 0.1F), 2.4F);
            PINK = new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.95F), 1.3F);
        }
        if (SKY1 == null) { SKY1 = new DustParticleOptions(new Vector3f(0.45F, 0.8F, 1.0F), 1.6F); SKY2 = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.3F); }
        boolean dream = kind == 2, sky = kind == 3;
        BlockState base = Fx.state(sky ? "calcite" : dream ? "polished_deepslate" : "polished_blackstone_bricks"), crying = Fx.state(eclipse ? "gold_block" : (dream || sky) ? "sea_lantern" : "crying_obsidian"), am = Fx.state(eclipse ? "polished_blackstone" : sky ? "quartz_pillar" : dream ? "warped_planks" : "amethyst_block"),
                rod = Fx.state(eclipse ? "shroomlight" : (dream || sky) ? "sea_lantern" : "end_rod"), air = Fx.state("air"), cl = Fx.state(eclipse ? "gilded_blackstone" : sky ? "light_blue_stained_glass" : dream ? "sculk" : "amethyst_cluster");
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            level.m_7731_(new BlockPos(bx + x, by - 1, bz + z), (x == 0 && z == 0) || (Math.abs(x) + Math.abs(z) == 1) ? crying : base, 3);
            for (int y = 0; y <= 4; y++) level.m_7731_(new BlockPos(bx + x, by + y, bz + z), air, 3);
        }
        for (int[] c : new int[][]{{2, 2}, {2, -2}, {-2, 2}, {-2, -2}}) {
            for (int y = 0; y < 3; y++) level.m_7731_(new BlockPos(bx + c[0], by + y, bz + c[1]), am, 3);
            level.m_7731_(new BlockPos(bx + c[0], by + 3, bz + c[1]), rod, 3);
        }
        if (cl != null) level.m_7731_(new BlockPos(bx, by, bz), cl, 3);
        this.cx = bx + 0.5; this.cy = by + 2.2; this.cz = bz + 0.5;
        Fx.sound(level, cx, cy, cz, "block.end_portal.spawn", 4.0F, 0.6F);
        Fx.sound(level, cx, cy, cz, "entity.warden.heartbeat", 4.0F, 0.5F);
        Fx.send(level, Fx.particle("flash"), cx, cy, cz, 1, 0, 0, 0, 0);
    }

    boolean tick() {
        t++;
        double r = 1.3 * Math.min(1, t / 30.0);
        for (int i = 0; i < 26; i++) {                       // вихрь
            double a = t * 0.12 + i * Math.PI * 2 / 26;
            var c1 = kind == 3 ? (i % 3 == 0 ? SKY2 : SKY1) : kind == 2 ? (i % 3 == 0 ? DREAM1 : DREAM2) : kind == 1 ? (i % 3 == 0 ? Boss2.GOLD : Boss2.ORANGE) : (i % 3 == 0 ? PINK : PURPLE);
            Fx.send(level, c1, cx + Math.cos(a) * r, cy + Math.sin(a) * r * 1.4, cz, 1, 0, 0, 0, 0);
            Fx.send(level, c1, cx, cy + Math.sin(a) * r * 1.4, cz + Math.cos(a) * r, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 12; i++) {
            double a = Fx.RNG.nextDouble() * Math.PI * 2, rr = r * Math.sqrt(Fx.RNG.nextDouble());
            Fx.send(level, BLACK, cx + Math.cos(a) * rr * 0.7, cy + Math.sin(a) * rr, cz + Math.cos(a + 1) * rr * 0.7, 1, 0, 0, 0, 0);
        }
        Fx.send(level, Fx.particle("reverse_portal"), cx, cy, cz, 6, 0.4, 0.6, 0.4, 0.05);
        // лучи от колонн к порталу
        if (t % 3 == 0) for (int[] c : new int[][]{{2, 2}, {2, -2}, {-2, 2}, {-2, -2}}) {
            double sx = cx + c[0], sy = cy + 1.3, sz = cz + c[1];
            for (double s = 0; s < 1; s += 0.1) Fx.send(level, PINK, sx + (cx - sx) * s, sy + (cy - sy) * s, sz + (cz - sz) * s, 1, 0, 0, 0, 0);
        }
        if (t % 40 == 1) Fx.sound(level, cx, cy, cz, "entity.warden.heartbeat", 3.0F, 0.6F);
        if (t > 20) for (Entity e : level.m_6249_(null, new AABB(cx - 2, cy - 3, cz - 2, cx + 2, cy + 2, cz + 2), e -> e instanceof ServerPlayer && e.m_6084_())) {
            Vec3 p = e.m_20182_();
            double dx = cx - p.f_82479_, dy = cy - (p.f_82480_ + 0.9), dz = cz - p.f_82481_;
            if (dx * dx + dy * dy + dz * dz < 1.6 * 1.6) {
                Fx.sound(level, p.f_82479_, p.f_82480_, p.f_82481_, "block.portal.travel", 0.5F, 0.6F);
                Dims.send(e, level, kind == 3 ? Dims.SKY : kind == 2 ? Dims.DREAM : kind == 1 ? Dims.ECLIPSE : Dims.ARENA);
            }
        }
        return t >= 1200;
    }
}
