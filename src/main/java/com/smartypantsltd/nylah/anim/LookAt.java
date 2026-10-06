package com.smartypantsltd.nylah.anim;

/**
 * Where her head points: smoothed toward a target, split across neck and head.
 *
 * <p>The smoothing is a critically damped spring stepped once per tick: her
 * head turns briskly to you and settles without swinging past. Render frames
 * interpolate between the last two ticks, so it is smooth at any frame rate.</p>
 *
 * <p>The split matters. Minecraft applies a part's yaw about its PARENT's axis,
 * and when she sits her chest is pitched up steeply, so yaw on the neck would
 * swing her head through a tilted plane. Yaw therefore goes on the HEAD, whose
 * parent (the neck) stays close to upright in every posture; the neck only
 * takes a share of the pitch. That is also what makes "looking up at you" read
 * right: the neck stretches back, the head tips back further.</p>
 */
public final class LookAt {

    public static final float MAX_YAW = 1.25f;
    public static final float MAX_PITCH_UP = -1.25f;
    public static final float MAX_PITCH_DOWN = 0.7f;

    private static final float OMEGA = 0.32f;

    private float yaw;
    private float pitch;
    private float prevYaw;
    private float prevPitch;
    private float vYaw;
    private float vPitch;
    private float targetYaw;
    private float targetPitch;

    /** Aim at a target. Angles are relative to her body: yaw + = her right, pitch - = up. */
    public void aim(float relYaw, float relPitch) {
        targetYaw = clamp(relYaw, -MAX_YAW, MAX_YAW);
        targetPitch = clamp(relPitch, MAX_PITCH_UP, MAX_PITCH_DOWN);
    }

    public void relax() {
        targetYaw = 0f;
        targetPitch = 0f;
    }

    public void tick() {
        prevYaw = yaw;
        prevPitch = pitch;
        float aY = OMEGA * OMEGA * (targetYaw - yaw) - 2f * OMEGA * vYaw;
        vYaw += aY;
        yaw += vYaw;
        float aP = OMEGA * OMEGA * (targetPitch - pitch) - 2f * OMEGA * vPitch;
        vPitch += aP;
        pitch += vPitch;
    }

    public float yaw(float partial) {
        return prevYaw + (yaw - prevYaw) * partial;
    }

    public float pitch(float partial) {
        return prevPitch + (pitch - prevPitch) * partial;
    }

    /** Head yaw, neck pitch, head pitch for a look direction. */
    public static float[] split(float yaw, float pitch) {
        float headYaw = clamp(yaw, -MAX_YAW, MAX_YAW);
        float neckPitch = clamp(pitch * 0.45f, -0.6f, 0.35f);
        float headPitch = clamp(pitch - neckPitch, -0.75f, 0.45f);
        return new float[] {headYaw, neckPitch, headPitch};
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
