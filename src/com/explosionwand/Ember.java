package com.explosionwand;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Горящий осколок: летит по дуге, коптит и взрывается при падении. */
final class Ember {
    static final List<Ember> ACTIVE = new ArrayList<>();
    final ServerLevel level; final Entity owner;
    double x, y, z, vx, vy, vz; int life = 0; final float power;

    Ember(ServerLevel level, Entity owner, double x, double y, double z, double vx, double vy, double vz, float power) {
        this.level = level; this.owner = owner; this.x = x; this.y = y; this.z = z; this.vx = vx; this.vy = vy; this.vz = vz; this.power = power;
    }

    boolean tick() {
        life++;
        for (int s = 0; s < 3; s++) {
            x += vx / 3; y += vy / 3; z += vz / 3;
            Fx.send(level, Fx.particle("flame"), x, y, z, 1, 0.02, 0.02, 0.02, 0.01);
            if (s == 0) Fx.send(level, Fx.particle("large_smoke"), x, y, z, 1, 0.05, 0.05, 0.05, 0.01);
            if (!level.m_8055_(new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))).m_60795_() || life > 60) {
                level.m_255391_(owner, x, y, z, power, false, Level.ExplosionInteraction.valueOf("TNT"));
                return true;
            }
        }
        if (life % 3 == 0) Fx.send(level, Fx.particle("lava"), x, y, z, 1, 0, 0, 0, 0);
        vy -= 0.07; vx *= 0.99; vz *= 0.99;
        return false;
    }
}
