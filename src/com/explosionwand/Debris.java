package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Отрывает блоки и швыряет их (падающими блоками) от центра — они разлетаются и приземляются в другом месте. */
final class Debris {
    static boolean movable(ServerLevel l, BlockPos p, BlockState s) {
        if (s.m_60795_() || s.m_155947_() || !s.m_60819_().m_76178_()) return false;
        float h = s.m_60800_(l, p);
        return h >= 0 && h < 50;
    }

    /** count попыток в шаре радиуса r (только оболочка от rMin), сила разлёта power. */
    static int fling(ServerLevel l, double cx, double cy, double cz, double rMin, double r, int count, double power, double up) {
        int done = 0;
        Dungeons.Shield sh = Dungeons.shield(l);
        for (int i = 0; i < count; i++) {
            double[] u = Fx.unit();
            double d = rMin + (r - rMin) * Math.cbrt(Fx.RNG.nextDouble());
            int x = (int) Math.floor(cx + u[0] * d), y = (int) Math.floor(cy + u[1] * d * 0.7), z = (int) Math.floor(cz + u[2] * d);
            BlockPos p = new BlockPos(x, y, z);
            if (sh.at(p)) continue;
            BlockState s = l.m_8055_(p);
            if (!movable(l, p, s)) continue;
            FallingBlockEntity fb = FallingBlockEntity.m_201971_(l, p, s);
            if (fb == null) continue;
            double dx = x + 0.5 - cx, dz = z + 0.5 - cz, dl = Math.max(0.4, Math.sqrt(dx * dx + dz * dz));
            double k = power * (0.7 + Fx.RNG.nextDouble() * 0.6);
            fb.m_20256_(new Vec3(dx / dl * k, up + Fx.RNG.nextDouble() * up * 0.8, dz / dl * k));
            fb.f_19864_ = true;
            done++;
        }
        return done;
    }
}
