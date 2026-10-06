package com.smartypantsltd.nylah;

import java.util.ArrayList;
import java.util.List;

/**
 * Where she can appear without being SEEN to appear: behind the camera.
 *
 * <p>She never pops into view. When she arrives (a new dimension, after a
 * teleport, when called back from a nap) she appears somewhere you are not
 * looking and pads in. Pure maths, tested without Minecraft.</p>
 */
public final class SpawnSpots {

    private SpawnSpots() {
    }

    /**
     * Candidate offsets (dx, dz) from you, best first: behind the camera at a
     * few distances, fanning out to the sides.
     */
    public static List<double[]> candidates(float cameraYawDeg, double[] distances) {
        List<double[]> out = new ArrayList<>();
        int[] fan = {0, 25, -25, 50, -50, 75, -75};
        for (double d : distances) {
            for (int f : fan) {
                double ang = Math.toRadians(cameraYawDeg + 180 + f);
                out.add(new double[] {-Math.sin(ang) * d, Math.cos(ang) * d});
            }
        }
        return out;
    }

    /**
     * Is an offset (dx, dz) from the camera outside its view? True when the
     * angle from where the camera faces is more than half the field of view
     * plus a margin (so she is not half-visible at the edge of the screen).
     */
    public static boolean outOfView(float cameraYawDeg, double dx, double dz, double fovDeg) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) {
            return false;
        }
        double yaw = Math.toRadians(cameraYawDeg);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double cos = (fx * dx + fz * dz) / len;
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
        double horizontalHalfFov = Math.min(80, fovDeg * 0.5 * 1.35);
        return angle > horizontalHalfFov + 15;
    }
}
