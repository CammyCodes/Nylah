package com.smartypantsltd.nylah;

import net.minecraft.world.phys.Vec3;

/** What Nylah can tell about you and the world this tick. Gathered by {@link Nylah}, read by {@link Brain}. */
record Senses(
        Vec3 player,          // your feet
        Vec3 playerEye,
        float playerYaw,      // degrees
        double playerSpeed,   // blocks per tick, horizontal
        int stillTicks,       // how long you have stood still
        boolean airborne,     // flying, gliding, riding, swimming
        boolean sneaking,
        boolean sleeping,
        boolean mining,       // pickaxe in hand and swinging lately
        boolean lookingAtHer,
        int lookingTicks,
        boolean night,
        boolean raining) {
}
