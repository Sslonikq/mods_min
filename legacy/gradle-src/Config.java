package com.explosionwand;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.DoubleValue POWER = BUILDER
            .comment("Explosion power. TNT = 4, charged creeper = 6. Values above ~30 can lag.")
            .defineInRange("explosionPower", 16.0, 1.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue RANGE = BUILDER
            .comment("Max shooting distance in blocks.")
            .defineInRange("range", 128.0, 8.0, 512.0);

    public static final ForgeConfigSpec.IntValue COOLDOWN = BUILDER
            .comment("Cooldown between shots in ticks (20 ticks = 1 second).")
            .defineInRange("cooldownTicks", 30, 0, 72000);

    public static final ForgeConfigSpec.BooleanValue FIRE = BUILDER
            .comment("Whether the explosion sets things on fire.")
            .define("causesFire", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
