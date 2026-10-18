package com.explosionwand;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Частицы, звуки, блоки, эффекты по строковым ID + общие утилиты. */
final class Fx {
    static final Random RNG = new Random();

    static ParticleOptions particle(String id) {
        Object o = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation("minecraft", id));
        return (o instanceof ParticleOptions p) ? p : null;
    }

    @SuppressWarnings("unchecked")
    static ParticleOptions blockParticle(BlockState state) {
        Object o = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation("minecraft", "block"));
        if (o == null || state == null) return null;
        return new BlockParticleOption((ParticleType<BlockParticleOption>) o, state);
    }

    static Block block(String id) { return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft", id)); }
    static BlockState state(String id) { Block b = block(id); return b == null ? null : b.m_49966_(); }
    static MobEffect effect(String id) { return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("minecraft", id)); }

    static void sound(ServerLevel level, double x, double y, double z, String id, float vol, float pitch) {
        SoundEvent s = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("minecraft", id));
        if (s != null) level.m_6263_(null, x, y, z, s, SoundSource.valueOf("PLAYERS"), vol, pitch);
    }

    /** Частицы всем игрокам, видимые издалека. */
    static void send(ServerLevel level, ParticleOptions o, double x, double y, double z,
                     int count, double dx, double dy, double dz, double speed) {
        if (o == null) return;
        for (ServerPlayer p : level.m_6907_()) level.m_8624_(p, o, true, x, y, z, count, dx, dy, dz, speed);
    }

    /** Частицы только одному игроку (метель вокруг него). */
    static void sendTo(ServerLevel level, ServerPlayer p, ParticleOptions o, double x, double y, double z,
                       int count, double dx, double dy, double dz, double speed) {
        if (o != null) level.m_8624_(p, o, true, x, y, z, count, dx, dy, dz, speed);
    }

    static double[] unit() {
        double x = RNG.nextGaussian(), y = RNG.nextGaussian(), z = RNG.nextGaussian();
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1.0E-6D) return new double[]{0, 1, 0};
        return new double[]{x / l, y / l, z / l};
    }

    // ---- шар смещений, отсортированный от центра к краю ----
    private static final Map<Integer, long[]> SPHERES = new HashMap<>();

    static long[] sphere(int R) {
        long[] cached = SPHERES.get(R);
        if (cached != null) return cached;
        int R2 = R * R, n = 0;
        for (int x = -R; x <= R; x++) for (int y = -R; y <= R; y++) for (int z = -R; z <= R; z++)
            if (x * x + y * y + z * z <= R2) n++;
        long[] a = new long[n];
        int i = 0;
        for (int x = -R; x <= R; x++) for (int y = -R; y <= R; y++) for (int z = -R; z <= R; z++) {
            int d2 = x * x + y * y + z * z;
            if (d2 <= R2) a[i++] = ((long) d2 << 24) | ((long) (x + R) << 16) | ((long) (y + R) << 8) | (z + R);
        }
        Arrays.sort(a);
        SPHERES.put(R, a);
        return a;
    }
    static int sx(long v, int R) { return (int) ((v >> 16) & 0xFF) - R; }
    static int sy(long v, int R) { return (int) ((v >> 8) & 0xFF) - R; }
    static int sz(long v, int R) { return (int) (v & 0xFF) - R; }
    static double sd(long v) { return Math.sqrt((double) (v >>> 24)); }
}
