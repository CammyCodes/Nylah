package com.smartypantsltd.nylah.anim;

/**
 * How she moves her legs, procedurally, from speed and stride phase.
 *
 * <p>Three gaits, blended by speed so there is never a pop between them:</p>
 * <ul>
 *   <li>walk: the cat's lateral-sequence walk, each foot a quarter-cycle
 *       after the last (left hind, left fore, right hind, right fore);</li>
 *   <li>trot: diagonal pairs together;</li>
 *   <li>run: a bounding gallop, front pair then hind pair, the spine flexing.</li>
 * </ul>
 * <p>Speeds are in blocks per tick. The stride phase advances with distance
 * covered, never with time, so her feet do not slide.</p>
 */
public final class Gait {

    public static final float TROT_FROM = 0.085f;
    public static final float RUN_FROM = 0.19f;

    private final int body;
    private final int chest;
    private final int head;
    private final int armL;
    private final int armR;
    private final int foreL;
    private final int foreR;
    private final int thighL;
    private final int thighR;
    private final int shinL;
    private final int shinR;
    private final int tail1;
    private final int tail2;
    private final int tail3;

    public Gait(Rig rig) {
        body = rig.indexOf("body");
        chest = rig.indexOf("chest");
        head = rig.indexOf("head");
        armL = rig.indexOf("arm_l");
        armR = rig.indexOf("arm_r");
        foreL = rig.indexOf("forearm_l");
        foreR = rig.indexOf("forearm_r");
        thighL = rig.indexOf("thigh_l");
        thighR = rig.indexOf("thigh_r");
        shinL = rig.indexOf("shin_l");
        shinR = rig.indexOf("shin_r");
        tail1 = rig.indexOf("tail1");
        tail2 = rig.indexOf("tail2");
        tail3 = rig.indexOf("tail3");
    }

    /** Stride cycles per block for a speed (longer strides when faster). */
    public static float cyclesPerBlock(float speed) {
        if (speed < TROT_FROM) {
            return 1.9f;
        }
        if (speed < RUN_FROM) {
            return 1.45f;
        }
        return 1.05f;
    }

    /** Weights of walk / trot / run for a speed; they always sum to 1. */
    public static float[] weights(float speed) {
        float trot = smooth(TROT_FROM - 0.02f, TROT_FROM + 0.02f, speed);
        float run = smooth(RUN_FROM - 0.03f, RUN_FROM + 0.03f, speed);
        float w = (1 - trot);
        float t = trot * (1 - run);
        return new float[] {w, t, run};
    }

    /**
     * Add the gait to a pose. {@code weight} fades the whole thing in and out
     * as she starts and stops; {@code phase} is in cycles.
     */
    public void apply(Pose p, float phase, float speed, float weight) {
        if (weight <= 0.001f) {
            return;
        }
        float[] g = weights(speed);
        // Phase offsets per leg: LH, LF, RH, RF.
        float[][] offsets = {
            {0f, 0.25f, 0.5f, 0.75f},   // walk
            {0.5f, 0f, 0f, 0.5f},       // trot: LF+RH, RF+LH
            {0.5f, 0f, 0.6f, 0.1f},     // run: fronts together-ish, then hinds
        };
        float[] amp = {0.42f, 0.6f, 0.85f};
        float lh = 0;
        float lf = 0;
        float rh = 0;
        float rf = 0;
        float lhLift = 0;
        float lfLift = 0;
        float rhLift = 0;
        float rfLift = 0;
        for (int k = 0; k < 3; k++) {
            if (g[k] <= 0f) {
                continue;
            }
            float a = amp[k] * g[k];
            lh += swing(phase, offsets[k][0]) * a;
            lf += swing(phase, offsets[k][1]) * a;
            rh += swing(phase, offsets[k][2]) * a;
            rf += swing(phase, offsets[k][3]) * a;
            lhLift += lift(phase, offsets[k][0]) * a;
            lfLift += lift(phase, offsets[k][1]) * a;
            rhLift += lift(phase, offsets[k][2]) * a;
            rfLift += lift(phase, offsets[k][3]) * a;
        }
        float w = weight;
        p.addRot(armL, -lf * w, 0, 0);
        p.addRot(armR, -rf * w, 0, 0);
        p.addRot(foreL, lfLift * 1.1f * w, 0, 0);
        p.addRot(foreR, rfLift * 1.1f * w, 0, 0);
        p.addRot(thighL, -lh * w, 0, 0);
        p.addRot(thighR, -rh * w, 0, 0);
        p.addRot(shinL, -lhLift * 0.6f * w, 0, 0);
        p.addRot(shinR, -rhLift * 0.6f * w, 0, 0);

        double cyc = phase * Math.PI * 2;
        float bob = (float) Math.abs(Math.sin(cyc * 2)) * (0.25f + 0.35f * g[2]);
        p.addPos(body, 0, -bob * w, 0);
        // The gallop flexes her spine; the walk sways her a touch side to side.
        p.addRot(body, (float) Math.sin(cyc) * 0.12f * g[2] * w, 0, (float) Math.sin(cyc) * 0.05f * g[0] * w);
        p.addRot(chest, (float) -Math.sin(cyc) * 0.1f * g[2] * w, 0, 0);
        // Keep her head steady while the body moves under it.
        p.addRot(head, (float) -Math.sin(cyc) * 0.06f * g[2] * w, 0, 0);
        // Tail up and happy while trotting along, with a little sway.
        p.addRot(tail1, 0.1f * w, (float) Math.sin(cyc) * 0.18f * w, 0);
        p.addRot(tail2, 0, (float) Math.sin(cyc - 0.6) * 0.14f * w, 0);
        p.addRot(tail3, 0, (float) Math.sin(cyc - 1.2) * 0.12f * w, 0);
    }

    /** Leg swing for a phase and offset: +1 fully forward, -1 fully back. */
    static float swing(float phase, float offset) {
        return (float) Math.sin((phase + offset) * Math.PI * 2);
    }

    /** How lifted the paw is: only while swinging forward. */
    static float lift(float phase, float offset) {
        return (float) Math.max(0, Math.cos((phase + offset) * Math.PI * 2));
    }

    static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
