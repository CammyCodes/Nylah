package com.smartypantsltd.nylah;

import com.smartypantsltd.nylah.anim.Animator;
import com.smartypantsltd.nylah.anim.Clip;
import com.smartypantsltd.nylah.entity.NylahCat;
import com.smartypantsltd.nylah.move.Mover;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /nylah debug}: she performs EVERY animation in turn, in front of you.
 *
 * <p>Every clip in the library (postures held a moment, actions played through,
 * looping ones for two cycles), then the procedural ones that are not clips:
 * walk, trot and run in a little circle, the hop, the look-up, the blep and the
 * happy eyes. The current one is shown under her name while it plays. Nylah
 * tells you in chat when it starts and when it is done.</p>
 */
final class DebugReel {

    enum Kind { CLIP, WALK, TROT, RUN, HOP, LOOK_UP, BLEP, HAPPY, BLINK }

    record Step(String label, String clip, Kind kind, int ticks) {
    }

    private final List<Step> steps = new ArrayList<>();
    private int index = -1;
    private long stepTicks;
    private Vec3 anchor;
    private float faceYaw;
    private boolean active;

    boolean active() {
        return active;
    }

    int size() {
        return steps.size();
    }

    /** Total running time in seconds, for the start message. */
    int seconds() {
        int t = 0;
        for (Step s : steps) {
            t += s.ticks();
        }
        return t / 20;
    }

    /** Build the reel and stand her in front of you. */
    void start(Animator a, Senses s) {
        steps.clear();
        for (Clip c : a.clips().values()) {
            int ticks;
            if (c.kind == Clip.Kind.POSTURE) {
                ticks = 50;
            } else if (c.loop) {
                ticks = (int) Math.max(80, c.length * 2);
            } else {
                ticks = (int) c.length + 8;
            }
            steps.add(new Step(c.name, c.name, Kind.CLIP, ticks));
        }
        steps.add(new Step("walk", null, Kind.WALK, 80));
        steps.add(new Step("trot", null, Kind.TROT, 70));
        steps.add(new Step("run", null, Kind.RUN, 60));
        steps.add(new Step("hop", null, Kind.HOP, 30));
        steps.add(new Step("look up at you", null, Kind.LOOK_UP, 70));
        steps.add(new Step("blink", null, Kind.BLINK, 40));
        steps.add(new Step("blep (any pose)", null, Kind.BLEP, 60));
        steps.add(new Step("happy eyes", null, Kind.HAPPY, 50));
        double ang = Math.toRadians(s.playerYaw());
        anchor = s.player().add(-Math.sin(ang) * 2.6, 0, Math.cos(ang) * 2.6);
        Vec3 d = s.player().subtract(anchor);
        faceYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        index = -1;
        stepTicks = 0;
        active = true;
    }

    void stop(Animator a) {
        active = false;
        index = -1;
        a.stopAction();
        a.setBlep(false);
        a.setHappy(false);
        a.setPurring(false);
    }

    /** Current label for her name tag, e.g. "debug 12/45: groom_paw". */
    String label() {
        if (!active || index < 0 || index >= steps.size()) {
            return null;
        }
        return "debug " + (index + 1) + "/" + steps.size() + ": " + steps.get(index).label();
    }

    /** Advance; returns true on the tick the reel finishes. */
    boolean tick(NylahCat cat, Animator a, Mover m, Senses s) {
        if (!active) {
            return false;
        }
        if (index < 0) {
            // Walk to the stage first.
            a.setPosture("stand");
            m.goTo(anchor, 0.3);
            m.tick(cat, 0.14);
            a.setMotion(m.speed(), m.airborne());
            if (m.arrived(cat) || stepTicks++ > 200) {
                m.stop();
                next(a);
            }
            return false;
        }
        Step st = steps.get(index);
        stepTicks++;
        switch (st.kind()) {
            case CLIP -> {
                m.face(cat, faceYaw);
                a.setMotion(0f, false);
            }
            case WALK, TROT, RUN -> {
                float speed = st.kind() == Kind.WALK ? 0.06f : st.kind() == Kind.TROT ? 0.13f : 0.24f;
                double ang = stepTicks * speed / 1.6;
                Vec3 p = anchor.add(Math.cos(ang) * 1.6 - 1.6, 0, Math.sin(ang) * 1.6);
                m.goTo(p, 0.05);
                m.tick(cat, speed);
                a.setMotion(m.speed(), m.airborne());
            }
            case HOP -> {
                double k = Math.min(1.0, stepTicks / 10.0);
                a.setMotion(0f, stepTicks > 4 && stepTicks < 14);
                Vec3 p = cat.position();
                cat.setPos(p.x, anchor.y + Math.sin(k * Math.PI) * 0.6 * (stepTicks < 14 ? 1 : 0), p.z);
            }
            case LOOK_UP -> {
                a.setMotion(0f, false);
                a.setPosture("sit");
            }
            case BLINK, BLEP, HAPPY -> a.setMotion(0f, false);
        }
        if (st.kind() == Kind.LOOK_UP || st.kind() == Kind.BLEP || st.kind() == Kind.HAPPY || st.kind() == Kind.BLINK) {
            Vec3 eye = cat.position().add(0, 0.45, 0);
            Vec3 d = s.playerEye().subtract(eye);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float rel = Brain.wrap(yaw - cat.getYRot());
            a.lookAt((float) Math.toRadians(rel), (float) -Math.atan2(d.y, Math.max(0.2, h)));
        } else {
            a.lookAway();
        }
        if (stepTicks >= st.ticks()) {
            return next(a);
        }
        return false;
    }

    private boolean next(Animator a) {
        a.stopAction();
        a.setBlep(false);
        a.setHappy(false);
        a.setPurring(false);
        index++;
        stepTicks = 0;
        if (index >= steps.size()) {
            active = false;
            a.setPosture("sit");
            return true;
        }
        Step st = steps.get(index);
        switch (st.kind()) {
            case CLIP -> a.play(st.clip(), st.ticks() - 4);
            case WALK, TROT, RUN, HOP -> a.setPosture("stand");
            case LOOK_UP, BLINK -> a.setPosture("sit");
            case BLEP -> {
                a.setPosture("loaf");
                a.setBlep(true);
            }
            case HAPPY -> {
                a.setPosture("sit");
                a.setHappy(true);
                a.setPurring(true);
            }
        }
        if (st.kind() == Kind.BLINK) {
            a.play("slow_blink", 0);
        }
        return false;
    }
}
