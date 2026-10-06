package com.smartypantsltd.nylah;

import com.smartypantsltd.nylah.anim.Animator;
import com.smartypantsltd.nylah.entity.NylahCat;
import com.smartypantsltd.nylah.move.Mover;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What she decides to do. A small state machine with moods, tilted hard
 * toward affection: she stays near you, settles when you settle, fills the
 * quiet with her little repertoire, looks up at you, rubs your legs, and
 * answers a long look with a slow blink.
 *
 * <p>The brain only ever chooses: where to walk (via {@link Mover}), what to
 * play (via {@link Animator}), where to look. It never touches the world.</p>
 */
final class Brain {

    enum Mode { ARRIVE, FOLLOW, IDLE, RUB, WAIT_AIR, COME, PERFORM, SLEEP_BED, WAKE, ZOOM, LEAVE }

    private final Random rng = new Random();
    private final Map<String, Long> lastPlayed = new HashMap<>();

    private Mode mode = Mode.FOLLOW;
    private long t;
    private long modeSince;

    private float wantSpeed;
    private int side = rng.nextBoolean() ? 1 : -1;

    private long idleSince;
    private long nextIdleAction;
    private long loafAt;
    private long lieAt;
    private long blepUntil;

    private long rubReadyAt = 400;
    private long blinkReplyAt;
    private long bonkReadyAt;
    private long zoomReadyAt = 2400;

    private final List<Vec3> rubPath = new ArrayList<>();
    private int rubIndex;

    private String perform;
    private boolean greet;
    private final List<String> queue = new ArrayList<>();

    /** 0 sleepy .. 1 lively. */
    private float energy = 0.8f;
    private float affection = 0.7f;

    private boolean leftDone;

    Mode mode() {
        return mode;
    }

    float wantSpeed() {
        return wantSpeed;
    }

    boolean finishedLeaving() {
        return mode == Mode.LEAVE && leftDone;
    }

    // ------------------------------------------------------------------ requests

    /** She has just appeared out of view and is trotting in. {@code greet}: the full hello. */
    void arrive(boolean greet) {
        this.greet = greet;
        enter(Mode.ARRIVE);
    }

    /** She is already beside you (world join): start settled. */
    void settled(Animator a) {
        enter(Mode.IDLE);
        a.setPosture("sit");
    }

    void come() {
        enter(Mode.COME);
    }

    void perform(String action) {
        perform = action;
        enter(Mode.PERFORM);
    }

    void leave() {
        leftDone = false;
        enter(Mode.LEAVE);
    }

    /** A stroke landed. She stops, leans in, purrs, and loves you a little more. */
    void stroked(Gesture.Region region, Animator a, Mover m) {
        m.stop();
        affection = Math.min(1f, affection + 0.05f);
        String p = a.posture();
        if (p.equals("curl") || p.equals("lie_side") || p.equals("belly_up")) {
            a.setHappy(true);
            a.setPurring(true);
            happyUntil = t + 60;
            return;
        }
        if (a.busy() && (a.action().equals("chin_scratch") || a.action().equals("happy_purr") || a.action().equals("head_bonk"))) {
            happyUntil = t + 40;
            a.setPurring(true);
            return;
        }
        a.play(region == Gesture.Region.HEAD ? "chin_scratch" : (p.equals("loaf") ? "happy_purr" : "head_bonk"),
                region == Gesture.Region.HEAD ? 0 : 70);
        a.setPurring(true);
        happyUntil = t + 70;
        if (mode != Mode.IDLE) {
            enter(Mode.IDLE);
        }
    }

    private long happyUntil;

    // ------------------------------------------------------------------ thinking

    void tick(Senses s, NylahCat cat, Animator a, Mover m, NylahConfig cfg) {
        t++;
        Vec3 her = cat.position();
        double dist = horiz(her, s.player());
        mood(s, a);

        if (t > happyUntil && happyUntil > 0) {
            a.setHappy(false);
            a.setPurring(false);
            happyUntil = 0;
        }
        if (blepUntil > 0 && t > blepUntil) {
            a.setBlep(false);
            blepUntil = 0;
        }

        // Things that override whatever she is doing.
        if (mode != Mode.PERFORM && mode != Mode.COME && mode != Mode.LEAVE) {
            if (s.airborne() && mode != Mode.WAIT_AIR) {
                enter(Mode.WAIT_AIR);
            } else if (s.sleeping() && mode != Mode.SLEEP_BED) {
                enter(Mode.SLEEP_BED);
            }
        }

        switch (mode) {
            case ARRIVE -> arrive(s, cat, a, m, dist);
            case FOLLOW -> follow(s, cat, a, m, dist);
            case IDLE -> idle(s, cat, a, m, dist, cfg);
            case RUB -> rub(s, cat, a, m, dist);
            case WAIT_AIR -> waitAir(s, cat, a, m, dist);
            case COME -> comeHere(s, cat, a, m);
            case PERFORM -> doPerform(s, cat, a, m);
            case SLEEP_BED -> sleepBed(s, cat, a, m);
            case WAKE -> wake(s, a, m);
            case ZOOM -> zoom(s, cat, a, m);
            case LEAVE -> leaveTick(s, cat, a, m);
        }
        look(s, cat, a, dist);
    }

    private void mood(Senses s, Animator a) {
        String p = a.posture();
        boolean resting = p.equals("curl") || p.equals("lie_side");
        float drift = s.night() ? -0.0004f : 0.0001f;
        energy += resting ? 0.0012f : drift - (wantSpeed > 0.15f ? 0.0006f : 0f);
        energy = Math.max(0.05f, Math.min(1f, energy));
        affection = Math.max(0.4f, affection - 0.00002f);
    }

    private void enter(Mode next) {
        mode = next;
        modeSince = t;
        if (next == Mode.IDLE) {
            idleSince = t;
            nextIdleAction = t + 60 + rng.nextInt(80);
            loafAt = t + 500 + rng.nextInt(700);
            lieAt = t + 1300 + rng.nextInt(1500);
        }
    }

    private long inMode() {
        return t - modeSince;
    }

    // --- following ---------------------------------------------------------------------

    private void follow(Senses s, NylahCat cat, Animator a, Mover m, double dist) {
        if (a.busy() && !"leg_rub".equals(a.action())) {
            a.stopAction();
        }
        a.setPosture("stand");
        Vec3 spot = followSpot(s, s.mining() ? 3.6 : 1.7);
        m.goTo(spot, 0.5);
        double d = horiz(cat.position(), spot);
        wantSpeed = d > 10 ? 0.27f : d > 4.5 ? 0.15f : 0.075f;
        if (s.playerSpeed() > 0.2 && d > 2.5) {
            wantSpeed = Math.max(wantSpeed, 0.24f);
        }
        if (m.arrived(cat) && s.playerSpeed() < 0.03) {
            enter(Mode.IDLE);
        }
        if (inMode() % 400 == 0) {
            side = -side;  // now and then she swaps sides
        }
    }

    /** Beside and a little behind you, on her side, out of your way. */
    private Vec3 followSpot(Senses s, double back) {
        double ang = Math.toRadians(s.playerYaw() + 180 + side * 55);
        return s.player().add(-Math.sin(ang) * back, 0, Math.cos(ang) * back);
    }

    // --- idling near you ---------------------------------------------------------------

    private void idle(Senses s, NylahCat cat, Animator a, Mover m, double dist, NylahConfig cfg) {
        wantSpeed = 0.06f;
        String p = a.posture();
        boolean lying = p.equals("curl") || p.equals("lie_side") || p.equals("belly_up");
        double leash = s.playerSpeed() > 0.05 ? 2.8 : (s.mining() ? 6.0 : 4.2);
        if (dist > leash) {
            if (lying) {
                enter(Mode.WAKE);
            } else {
                a.stopAction();
                enter(Mode.FOLLOW);
            }
            return;
        }
        if (s.mining() && dist < 2.4 && !lying) {
            enter(Mode.FOLLOW);  // give you room to swing
            return;
        }
        if (!lying && !a.busy()) {
            faceToward(cat, m, s.player(), 70f);
        }

        long idle = t - idleSince;
        // Posture drifts down as she settles: stand, sit, loaf, then a nap or a sunbathe.
        if (!a.busy()) {
            if (p.equals("stand") && idle > 30) {
                a.setPosture("sit");
                maybeBlep(0.15);
            } else if (p.equals("sit") && t > loafAt && rng.nextFloat() < 0.6f) {
                a.setPosture("loaf");
                maybeBlep(0.3);
                loafAt = Long.MAX_VALUE;
            } else if ((p.equals("loaf") || p.equals("sit")) && t > lieAt) {
                if (!s.night() && !s.raining() && rng.nextFloat() < 0.5f) {
                    a.play("sunbathe", 1200 + rng.nextInt(1200));
                } else {
                    a.play(rng.nextFloat() < 0.35f ? "sleep_blep" : "sleep", 1600 + rng.nextInt(2400));
                }
                lieAt = Long.MAX_VALUE;
            }
        }

        // Answer a long look with a slow blink.
        if (s.lookingTicks() >= 30 && dist < 8 && t > blinkReplyAt && !a.busy() && !lying) {
            a.play("slow_blink", 0);
            blinkReplyAt = t + 300 + rng.nextInt(300);
            return;
        }
        // Crouch near her and she comes for a head bonk.
        if (s.sneaking() && dist < 3.5 && t > bonkReadyAt && !lying) {
            bonkReadyAt = t + 240;
            queue.clear();
            queue.add("head_bonk");
            perform = null;
            enter(Mode.PERFORM);
            performBonk = true;
            performHere = false;   // walk over to you first
            return;
        }
        // Stand still a while and she weaves round your legs.
        if (s.stillTicks() > 70 && dist < 4.5 && t > rubReadyAt && !lying && !s.mining() && affection > 0.5f
                && rng.nextFloat() < 0.02f) {
            startRub(s, cat, a);
            return;
        }
        // A rare burst of zoomies when she is full of beans.
        if (cfg.activity != NylahConfig.Activity.CALM && energy > 0.75f && !s.night() && t > zoomReadyAt
                && !lying && rng.nextFloat() < (cfg.activity == NylahConfig.Activity.PLAYFUL ? 0.004f : 0.0015f)) {
            zoomReadyAt = t + 6000;
            enter(Mode.ZOOM);
            return;
        }
        // Otherwise, something from her repertoire now and then.
        if (!a.busy() && t >= nextIdleAction) {
            String act = pickIdle(p, s, dist);
            if (act != null) {
                a.play(act, 0);
                lastPlayed.put(act, t);
                if (act.equals("lick_lips")) {
                    maybeBlep(0.25);
                }
            }
            float pace = cfg.activity == NylahConfig.Activity.CALM ? 1.6f : cfg.activity == NylahConfig.Activity.PLAYFUL ? 0.6f : 1f;
            nextIdleAction = t + (long) ((110 + rng.nextInt(170)) * pace);
        }
    }

    private boolean performBonk;

    private String pickIdle(String posture, Senses s, double dist) {
        boolean near = dist < 3.5;
        List<String> pool = new ArrayList<>();
        List<Integer> weight = new ArrayList<>();
        switch (posture) {
            case "sit" -> {
                add(pool, weight, "look_up", near ? 8 : 1);
                add(pool, weight, "slow_blink", near ? 4 : 1);
                add(pool, weight, "lick_lips", 3);
                add(pool, weight, "groom_paw", 3);
                add(pool, weight, "head_tilt", near ? 4 : 2);
                add(pool, weight, "over_shoulder", 2);
                add(pool, weight, "yawn", energy < 0.5f ? 4 : 1);
                add(pool, weight, "paw_tap", near && s.stillTicks() > 100 ? 2 : 0);
                add(pool, weight, "meow", near ? 1 : 0);
                add(pool, weight, "sneeze", 1);
                add(pool, weight, "blep", 3);
            }
            case "loaf" -> {
                add(pool, weight, "groom_flank", 3);
                add(pool, weight, "happy_purr", near ? 4 : 1);
                add(pool, weight, "slow_blink", near ? 3 : 1);
                add(pool, weight, "yawn", energy < 0.5f ? 3 : 1);
                add(pool, weight, "stretch_long", near && affection > 0.65f ? 1 : 0);
                add(pool, weight, "roll_wiggle", near && affection > 0.7f ? 2 : 0);
            }
            case "stand" -> {
                add(pool, weight, "stretch_front", 3);
                add(pool, weight, "stretch_back", 2);
                add(pool, weight, "cheek_rub", 2);
                add(pool, weight, "trill", near ? 2 : 0);
                add(pool, weight, "pounce", energy > 0.6f ? 1 : 0);
            }
            case "belly_up" -> {
                add(pool, weight, "roll_wiggle", 3);
                add(pool, weight, "belly_reach", 3);
                add(pool, weight, "upside_meow", near ? 2 : 0);
            }
            case "lie_side" -> {
                add(pool, weight, "stretch_long", 2);
                add(pool, weight, "roll_wiggle", near ? 1 : 0);
            }
            default -> {
                return null;
            }
        }
        int total = 0;
        for (int i = 0; i < pool.size(); i++) {
            Long last = lastPlayed.get(pool.get(i));
            if (last != null && t - last < 900) {
                weight.set(i, 0);  // not the same thing again so soon
            }
            total += weight.get(i);
        }
        if (total <= 0) {
            return null;
        }
        int r = rng.nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            r -= weight.get(i);
            if (r < 0) {
                return pool.get(i);
            }
        }
        return null;
    }

    private static void add(List<String> p, List<Integer> w, String n, int weight) {
        if (weight > 0) {
            p.add(n);
            w.add(weight);
        }
    }

    private void maybeBlep(double chance) {
        if (rng.nextDouble() < chance) {
            blepUntil = t + 300 + rng.nextInt(700);
            blepOn = true;
        }
    }

    private boolean blepOn;

    // --- leg rub ---------------------------------------------------------------------

    private void startRub(Senses s, NylahCat cat, Animator a) {
        rubPath.clear();
        double yaw = Math.toRadians(s.playerYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -fz;
        double rz = fx;
        // A figure of eight round your feet, twice: her flank brushes your shins on each pass.
        int n = 28;
        for (int i = 0; i <= n * 2; i++) {
            double u = (i / (double) n) * Math.PI * 2;
            double a1 = 0.62 * Math.sin(u);
            double a2 = 0.42 * Math.sin(u) * Math.cos(u);
            rubPath.add(s.player().add(rx * a1 + fx * a2, 0, rz * a1 + fz * a2));
        }
        rubIndex = 0;
        rubReadyAt = t + 2400 + rng.nextInt(2400);
        enter(Mode.RUB);
        a.play("leg_rub", 400);
        a.setPurring(true);
    }

    private void rub(Senses s, NylahCat cat, Animator a, Mover m, double dist) {
        wantSpeed = 0.06f;
        if (s.playerSpeed() > 0.06 || dist > 3.0) {
            a.stopAction();
            a.setPurring(false);
            enter(Mode.FOLLOW);
            return;
        }
        if (rubIndex >= rubPath.size()) {
            a.stopAction();
            a.setPurring(false);
            m.stop();
            queue.clear();
            queue.add("look_up");
            queue.add("slow_blink");
            perform = null;
            enter(Mode.PERFORM);
            performHere = true;
            return;
        }
        Vec3 wp = rubPath.get(rubIndex);
        m.goTo(wp, 0.18);
        if (m.arrived(cat)) {
            rubIndex++;
        }
    }

    // --- waiting while you fly / swim / ride ---------------------------------------------

    private void waitAir(Senses s, NylahCat cat, Animator a, Mover m, double dist) {
        m.stop();
        if (!a.busy()) {
            a.setPosture("sit");
        }
        if (!s.airborne() && inMode() > 10) {
            enter(Mode.FOLLOW);
        }
    }

    // --- "come here!" and performing for you ------------------------------------------

    private void comeHere(Senses s, NylahCat cat, Animator a, Mover m) {
        Vec3 spot = frontSpot(s, 1.3);
        a.setPosture("stand");
        m.goTo(spot, 0.4);
        wantSpeed = horiz(cat.position(), spot) > 3 ? 0.26f : 0.1f;
        if (m.arrived(cat) || inMode() > 300) {
            m.stop();
            queue.clear();
            queue.add("trill");
            queue.add("look_up");
            perform = null;
            enter(Mode.PERFORM);
            performHere = true;
        }
    }

    private boolean performHere;

    private void doPerform(Senses s, NylahCat cat, Animator a, Mover m) {
        if (perform != null) {
            queue.clear();
            queue.add(perform);
            perform = null;
            performHere = false;
        }
        if (!performHere) {
            Vec3 spot = performBonk ? frontSpot(s, 0.7) : frontSpot(s, 1.8);
            a.setPosture("stand");
            m.goTo(spot, performBonk ? 0.25 : 0.4);
            wantSpeed = horiz(cat.position(), spot) > 4 ? 0.22f : 0.09f;
            if (!m.arrived(cat) && inMode() < 400) {
                return;
            }
            m.stop();
            performHere = true;
        }
        faceToward(cat, m, s.player(), 15f);
        if (a.busy()) {
            return;
        }
        if (queue.isEmpty()) {
            performBonk = false;
            enter(Mode.IDLE);
            return;
        }
        String next = queue.remove(0);
        a.play(next, 0);
        lastPlayed.put(next, t);
        if (next.equals("head_bonk")) {
            a.setPurring(true);
            happyUntil = t + 60;
        }
    }

    private Vec3 frontSpot(Senses s, double d) {
        double ang = Math.toRadians(s.playerYaw());
        return s.player().add(-Math.sin(ang) * d, 0, Math.cos(ang) * d);
    }

    // --- arriving ------------------------------------------------------------------------

    private void arrive(Senses s, NylahCat cat, Animator a, Mover m, double dist) {
        a.setPosture("stand");
        Vec3 spot = frontSpot(s, 1.4);
        m.goTo(spot, 0.5);
        wantSpeed = dist > 6 ? 0.16f : 0.09f;
        if (m.arrived(cat) || dist < 1.6 || inMode() > 600) {
            m.stop();
            queue.clear();
            queue.add("trill");
            if (greet) {
                greet = false;
                queue.add("head_bonk");
                queue.add("look_up");
                queue.add("slow_blink");
            } else {
                queue.add("look_up");
            }
            perform = null;
            enter(Mode.PERFORM);
            performHere = true;
        }
    }

    // --- bedtime ------------------------------------------------------------------------

    private void sleepBed(Senses s, NylahCat cat, Animator a, Mover m) {
        if (!s.sleeping()) {
            enter(Mode.WAKE);
            return;
        }
        Vec3 spot = followSpot(s, 1.1);
        if (!a.busy() || !a.posture().equals("curl")) {
            m.goTo(spot, 0.4);
            wantSpeed = 0.08f;
            if (m.arrived(cat) && !a.busy()) {
                a.play("sleep", 6000);
            }
        }
    }

    private int wakeStep;

    private void wake(Senses s, Animator a, Mover m) {
        m.stop();
        if (inMode() == 0) {
            wakeStep = 0;
            a.stopAction();
        }
        if (a.busy()) {
            return;
        }
        switch (wakeStep++) {
            case 0 -> a.play("yawn", 0);
            case 1 -> a.play("stretch_front", 0);
            case 2 -> a.play("stretch_back", 0);
            default -> enter(Mode.FOLLOW);
        }
    }

    // --- zoomies ------------------------------------------------------------------------

    private void zoom(Senses s, NylahCat cat, Animator a, Mover m) {
        a.setPosture("stand");
        double ang = (t - modeSince) * 0.09;
        Vec3 spot = s.player().add(Math.cos(ang) * 3.2, 0, Math.sin(ang) * 3.2);
        m.goTo(spot, 0.6);
        wantSpeed = 0.29f;
        if (inMode() > 90) {
            m.stop();
            queue.clear();
            queue.add("lick_lips");
            perform = null;
            enter(Mode.PERFORM);
            performHere = true;
        }
    }

    // --- off for a nap ------------------------------------------------------------------

    private void leaveTick(Senses s, NylahCat cat, Animator a, Mover m) {
        a.setPosture("stand");
        double ang = Math.toRadians(s.playerYaw() + 180);
        Vec3 away = s.player().add(-Math.sin(ang) * 9, 0, Math.cos(ang) * 9);
        m.goTo(away, 1.0);
        wantSpeed = 0.1f;
        if (m.arrived(cat) || inMode() > 120 || m.stuck()) {
            leftDone = true;
        }
    }

    // --- where she looks ------------------------------------------------------------------

    private void look(Senses s, NylahCat cat, Animator a, double dist) {
        boolean asleep = a.posture().equals("curl");
        if (asleep || dist > 9 || (mode == Mode.FOLLOW && dist > 3.5) || mode == Mode.ZOOM || mode == Mode.LEAVE) {
            a.lookAway();
            return;
        }
        Vec3 eye = cat.position().add(0, 0.45, 0);
        Vec3 d = s.playerEye().subtract(eye);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float rel = wrap(yaw - cat.getYRot());
        float pitch = (float) -Math.atan2(d.y, Math.max(0.2, h));
        a.lookAt((float) Math.toRadians(rel), pitch);
    }

    private void faceToward(NylahCat cat, Mover m, Vec3 target, float tolerance) {
        Vec3 d = target.subtract(cat.position());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        if (Math.abs(wrap(yaw - cat.getYRot())) > tolerance) {
            m.face(cat, yaw);
        }
    }

    boolean blepWanted() {
        return blepOn && blepUntil > t;
    }

    static double horiz(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static float wrap(float deg) {
        deg %= 360f;
        if (deg >= 180f) {
            deg -= 360f;
        }
        if (deg < -180f) {
            deg += 360f;
        }
        return deg;
    }
}
