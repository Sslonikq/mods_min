package com.explosionwand;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

final class Aim {
    final Vec3 eye, look, target; final boolean hit; Entity entity; BlockHitResult block;
    Aim(Vec3 eye, Vec3 look, Vec3 target, boolean hit) { this.eye = eye; this.look = look; this.target = target; this.hit = hit; }

    static Aim of(Level level, Player player, double range) {
        Vec3 eye = player.m_146892_();
        Vec3 look = player.m_20252_(1.0F);
        Vec3 end = eye.m_82549_(look.m_82490_(range));
        BlockHitResult bh = level.m_45547_(new ClipContext(eye, end,
                ClipContext.Block.valueOf("COLLIDER"), ClipContext.Fluid.valueOf("NONE"), player));
        Vec3 target = bh.m_82450_();
        boolean hit = eye.m_82554_(target) < range - 0.01D;
        AABB box = player.m_20191_().m_82369_(look.m_82490_(range)).m_82400_(1.0D);
        EntityHitResult eh = ProjectileUtil.m_37304_(level, player, eye, target, box,
                (Entity e) -> !e.m_5833_() && e.m_6087_());
        Entity ent = null;
        if (eh != null) { target = eh.m_82450_(); hit = true; ent = eh.m_82443_(); }
        Aim a = new Aim(eye, look, target, hit); a.entity = ent; a.block = bh;
        return a;
    }

    void beam(net.minecraft.server.level.ServerLevel sl, net.minecraft.core.particles.ParticleOptions p, double step) {
        double dist = eye.m_82554_(target);
        for (double d = 1.5D; d < dist; d += step) {
            Vec3 v = eye.m_82549_(look.m_82490_(d));
            Fx.send(sl, p, v.f_82479_, v.f_82480_, v.f_82481_, 1, 0, 0, 0, 0);
        }
    }
}
