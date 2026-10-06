package com.smartypantsltd.nylah.anim;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Random;

/**
 * The ONE owner of her pose. Everything that moves her goes through here.
 *
 * <p>A frame is built in layers, in this order:</p>
 * <ol>
 *   <li>posture: a looping clip (stand, sit, loaf, ...), cross-faded when it changes;</li>
 *   <li>action: a one-off (or timed looping) clip blended over the posture;</li>
 *   <li>gait: legs, bob and tail from speed and stride phase;</li>
 *   <li>life: breathing, blinks, ear twitches, a lazy tail, a purr tremble;</li>
 *   <li>expressions: the blep and happy ^ ^ eyes, which can sit on any pose;</li>
 *   <li>look-at: head and neck turned toward what she is looking at.</li>
 * </ol>
 *
 * <p>State advances once per client tick ({@link #tick()}); {@link #sample}
 * evaluates at tick + partial, so she moves smoothly at any frame rate. Pure
 * Java: no Minecraft types, so it is unit-tested directly.</p>
 */
public final class Animator {

    private final Rig rig;
    private final Map<String, Clip> clips;
    private final Gait gait;
    private final LookAt look = new LookAt();
    private final Random rng = new Random();

    private final Pose scratch;
    private final Pose scratch2;

    private long ticks;

    private Clip posture;
    private Clip prevPosture;
    private float postureSince = -1000f;
    private static final float POSTURE_BLEND = 12f;

    private Clip action;
    private float actionStart;
    /** Absolute end time for an action; for a one-shot it is start + length. */
    private float actionEnd;
    private float prevActionT = -1f;

    private float speed;
    private float phase;
    private float prevPhase;
    private float moveWeight;
    private float prevMoveWeight;
    private boolean airborne;

    private boolean lookActive;
    private float lookWeight;
    private float prevLookWeight;

    private boolean blep;
    private boolean happy;
    private boolean purring;
    private float purrLevel;

    private long nextBlink;
    private long blinkAt = -100;
    private long nextEarTwitch;
    private long earTwitchAt = -100;
    private boolean earTwitchLeft;
    private long nextTailFlick;
    private long tailFlickAt = -100;

    private final Deque<String> events = new ArrayDeque<>();

    private final int iChest;
    private final int iBody;
    private final int iNeck;
    private final int iHead;
    private final int iLidL;
    private final int iLidR;
    private final int iHappy;
    private final int iTongue;
    private final int iEarL;
    private final int iEarR;
    private final int iTail2;
    private final int iTail3;
    private final int iTail4;
    private final int iThighL;
    private final int iThighR;
    private final int iArmL;
    private final int iArmR;

    public Animator(Rig rig, Map<String, Clip> clips) {
        this.rig = rig;
        this.clips = clips;
        this.gait = new Gait(rig);
        this.scratch = new Pose(rig);
        this.scratch2 = new Pose(rig);
        iChest = rig.indexOf("chest");
        iBody = rig.indexOf("body");
        iNeck = rig.indexOf("neck");
        iHead = rig.indexOf("head");
        iLidL = rig.indexOf("lid_l");
        iLidR = rig.indexOf("lid_r");
        iHappy = rig.indexOf("happy");
        iTongue = rig.indexOf("tongue");
        iEarL = rig.indexOf("ear_l");
        iEarR = rig.indexOf("ear_r");
        iTail2 = rig.indexOf("tail2");
        iTail3 = rig.indexOf("tail3");
        iTail4 = rig.indexOf("tail4");
        iThighL = rig.indexOf("thigh_l");
        iThighR = rig.indexOf("thigh_r");
        iArmL = rig.indexOf("arm_l");
        iArmR = rig.indexOf("arm_r");
        posture = clips.get("stand");
        nextBlink = 40;
        nextEarTwitch = 120;
        nextTailFlick = 90;
    }

    public Rig rig() {
        return rig;
    }

    public Map<String, Clip> clips() {
        return clips;
    }

    // ------------------------------------------------------------------ commands

    /** Settle into a posture (cross-fades). Unknown names are ignored. */
    public void setPosture(String name) {
        Clip c = clips.get(name);
        if (c == null || c == posture) {
            return;
        }
        prevPosture = posture;
        posture = c;
        postureSince = ticks;
    }

    public String posture() {
        return posture == null ? "stand" : posture.name;
    }

    /**
     * Play an action. {@code durationTicks} &lt;= 0 plays a one-shot clip once
     * (a looping clip then plays one cycle). The action's own posture becomes
     * the posture, so she settles there and blends back to it at the end.
     */
    public boolean play(String name, float durationTicks) {
        Clip c = clips.get(name);
        if (c == null) {
            return false;
        }
        if (c.kind == Clip.Kind.POSTURE) {
            setPosture(name);
            return true;
        }
        setPosture(c.posture);
        action = c;
        actionStart = ticks;
        actionEnd = ticks + (durationTicks > 0 ? durationTicks : c.length);
        prevActionT = -1f;
        return true;
    }

    /** Stop the current action early, blending out over its blend-out time. */
    public void stopAction() {
        if (action != null) {
            actionEnd = Math.min(actionEnd, ticks + action.blendOut);
        }
    }

    public boolean busy() {
        return action != null;
    }

    public String action() {
        return action == null ? null : action.name;
    }

    /** Ticks until the current action has fully finished (0 when idle). */
    public float remaining() {
        return action == null ? 0f : Math.max(0f, actionEnd - ticks);
    }

    public void setMotion(float blocksPerTick, boolean airborne) {
        this.speed = Math.max(0f, blocksPerTick);
        this.airborne = airborne;
    }

    public float speed() {
        return speed;
    }

    /** Look toward a direction relative to her body (yaw + = her right, pitch - = up). */
    public void lookAt(float relYaw, float relPitch) {
        look.aim(relYaw, relPitch);
        lookActive = true;
    }

    public void lookAway() {
        look.relax();
        lookActive = false;
    }

    public void setBlep(boolean on) {
        blep = on;
    }

    public boolean blep() {
        return blep;
    }

    public void setHappy(boolean on) {
        happy = on;
    }

    public void setPurring(boolean on) {
        purring = on;
    }

    public boolean purring() {
        return purring;
    }

    /** Sound/feel cues the clips asked for since the last poll ("purr", "meow", ...). */
    public String pollEvent() {
        return events.pollFirst();
    }

    // ------------------------------------------------------------------ time

    public void tick() {
        ticks++;
        look.tick();
        prevLookWeight = lookWeight;
        lookWeight += ((lookActive ? 1f : 0f) - lookWeight) * 0.15f;

        prevPhase = phase;
        phase += speed * Gait.cyclesPerBlock(speed);
        prevMoveWeight = moveWeight;
        float wantMove = speed > 0.008f ? 1f : 0f;
        moveWeight += (wantMove - moveWeight) * 0.25f;

        purrLevel += ((purring ? 1f : 0f) - purrLevel) * 0.1f;

        if (action != null) {
            float t = ticks - actionStart;
            fireEvents(action, prevActionT, t);
            prevActionT = t;
            if (ticks >= actionEnd) {
                action = null;
            }
        }

        if (ticks >= nextBlink) {
            blinkAt = ticks;
            nextBlink = ticks + 60 + rng.nextInt(110);
        }
        if (ticks >= nextEarTwitch) {
            earTwitchAt = ticks;
            earTwitchLeft = rng.nextBoolean();
            nextEarTwitch = ticks + 90 + rng.nextInt(260);
        }
        if (ticks >= nextTailFlick) {
            tailFlickAt = ticks;
            nextTailFlick = ticks + 70 + rng.nextInt(200);
        }
    }

    private void fireEvents(Clip c, float from, float to) {
        if (c.events.isEmpty()) {
            return;
        }
        float duration = actionEnd - actionStart;
        for (Clip.Event e : c.events) {
            if (c.loop) {
                // Fire once per cycle: crossing e.time + k*length within (from, to],
                // but not a fresh cycle's cue on the very tick the action ends.
                float len = c.length;
                long kFrom = (long) Math.floor((from - e.time()) / len);
                long kTo = (long) Math.floor((to - e.time()) / len);
                if (kTo > kFrom && e.time() + kTo * len < duration) {
                    events.addLast(e.name());
                }
            } else if (e.time() > from && e.time() <= to) {
                events.addLast(e.name());
            }
        }
    }

    // ------------------------------------------------------------------ sampling

    public void sample(float partial, Pose out) {
        float now = ticks + partial;

        // 1. posture, cross-faded from the previous one
        float pw = Math.min(1f, (now - postureSince) / POSTURE_BLEND);
        if (prevPosture != null && pw < 1f) {
            prevPosture.sample(now, out);
            posture.sample(now, scratch);
            out.blendToward(scratch, ease(pw));
        } else {
            posture.sample(now, out);
        }
        float lookAllowance = posture.lookAt;

        // 2. action
        if (action != null) {
            float t = now - actionStart;
            action.sample(t, scratch2);
            float w = actionWeight(now);
            out.blendToward(scratch2, w);
            lookAllowance = lookAllowance + (action.lookAt - lookAllowance) * w;
        }

        // 3. gait
        float mw = prevMoveWeight + (moveWeight - prevMoveWeight) * partial;
        float ph = prevPhase + (phase - prevPhase) * partial;
        gait.apply(out, ph, speed, mw);
        if (airborne) {
            out.addRot(iArmL, -0.6f, 0, 0);
            out.addRot(iArmR, -0.6f, 0, 0);
            out.addRot(iThighL, 0.7f, 0, 0);
            out.addRot(iThighR, 0.7f, 0, 0);
        }

        // 4. life
        float breath = (float) Math.sin(now * (posture.name.equals("curl") ? 0.07 : 0.12));
        out.mulScale(iChest, 1f + 0.018f * breath, 1f + 0.018f * breath, 1f);
        float tailIdle = 1f - mw;
        out.addRot(iTail2, 0, (float) Math.sin(now * 0.06) * 0.09f * tailIdle, 0);
        out.addRot(iTail3, 0, (float) Math.sin(now * 0.06 - 0.7) * 0.1f * tailIdle, 0);
        float flick = pulse(now - tailFlickAt, 10f);
        out.addRot(iTail4, 0, flick * 0.45f, 0);
        float twitch = pulse(now - earTwitchAt, 6f);
        if (earTwitchLeft) {
            out.addRot(iEarL, twitch * 0.35f, 0, twitch * 0.2f);
        } else {
            out.addRot(iEarR, twitch * 0.35f, 0, -twitch * 0.2f);
        }
        float purr = purrLevel * (float) Math.sin(now * 2.6) * 0.045f;
        out.addPos(iBody, 0, purr, 0);

        // A blink, unless something else already controls her eyelids.
        float bl = pulse(now - blinkAt, 5f);
        if (bl > 0f && !out.visible[iLidL] && !out.visible[iHappy]) {
            out.setVisible(iLidL, true);
            out.setVisible(iLidR, true);
            out.scale[iLidL * 3 + 1] = bl;
            out.scale[iLidR * 3 + 1] = bl;
        }

        // 5. expressions
        if (blep && !out.visible[iTongue]) {
            out.setVisible(iTongue, true);
            out.addPos(iTongue, 0f, -0.35f, -0.55f);
            out.mulScale(iTongue, 1f, 0.5f, 1f);
        }
        if (happy) {
            out.setVisible(iHappy, true);
        }

        // 6. look-at
        float lw = (prevLookWeight + (lookWeight - prevLookWeight) * partial) * lookAllowance;
        if (lw > 0.001f) {
            float[] s = LookAt.split(look.yaw(partial), look.pitch(partial));
            out.addRot(iHead, s[2] * lw, s[0] * lw, 0);
            out.addRot(iNeck, s[1] * lw, 0, 0);
            if (s[1] + s[2] < -0.5f) {
                // Looking right up at you: ears tip out to the sides (12(13), 12(14)).
                float k = Math.min(1f, (-(s[1] + s[2]) - 0.5f) * 1.5f) * lw;
                out.addRot(iEarL, 0, 0, 0.18f * k);
                out.addRot(iEarR, 0, 0, -0.18f * k);
            }
        }
    }

    private float actionWeight(float now) {
        float in = action.blendIn <= 0 ? 1f : Math.min(1f, (now - actionStart) / action.blendIn);
        float out = action.blendOut <= 0 ? 1f : Math.min(1f, (actionEnd - now) / action.blendOut);
        return ease(Math.max(0f, Math.min(in, out)));
    }

    /** A quick up-and-down bump of the given length, 0 outside it. */
    static float pulse(float t, float len) {
        if (t < 0f || t > len) {
            return 0f;
        }
        return (float) Math.sin(t / len * Math.PI);
    }

    static float ease(float t) {
        return t * t * (3f - 2f * t);
    }
}
