package com.smartypantsltd.nylah.move;

import com.smartypantsltd.nylah.entity.NylahCat;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks her: follows a path toward a goal at a chosen speed, turns smoothly,
 * hops up steps and drops down them, and notices when she is stuck.
 *
 * <p>She is placed by hand each tick (she has no physics), the way the pet fox
 * next door is: position set after the level has ticked, so the renderer
 * interpolates from last tick's spot to this one.</p>
 */
public final class Mover {

    private static final float TURN_PER_TICK = 18f;
    private static final int HOP_TICKS = 6;

    private final Grid grid;
    private final PathFinder finder;

    private List<PathFinder.Point> path = new ArrayList<>();
    private int next;
    private Vec3 goal;
    private int repathIn;

    private double hopFrom = Double.NaN;
    private double hopTo;
    private int hopT;

    private double lastProgressDist = Double.MAX_VALUE;
    private int noProgressTicks;
    private float speedNow;
    private boolean airborne;

    public Mover(Grid grid) {
        this.grid = grid;
        this.finder = new PathFinder(grid, 2500);
    }

    public void stop() {
        path.clear();
        goal = null;
        speedNow = 0f;
        noProgressTicks = 0;
    }

    public boolean moving() {
        return goal != null;
    }

    /** Speed actually covered last tick, in blocks per tick (the gait follows this). */
    public float speed() {
        return speedNow;
    }

    public boolean airborne() {
        return airborne;
    }

    /** No progress toward the goal for two seconds while trying to move. */
    public boolean stuck() {
        return noProgressTicks > 40;
    }

    /** Walk toward {@code target} at {@code speed}; arrives within {@code within} blocks. */
    public void goTo(Vec3 target, double within) {
        if (goal == null || goal.distanceToSqr(target) > 1.0) {
            repathIn = 0;
            lastProgressDist = Double.MAX_VALUE;
            noProgressTicks = 0;
        }
        goal = target;
        arriveWithin = within;
    }

    private double arriveWithin = 0.4;

    /** True when she is at the goal (or has no goal). */
    public boolean arrived(NylahCat cat) {
        return goal == null || horiz(cat.position(), goal) <= arriveWithin;
    }

    public void tick(NylahCat cat, double speed) {
        Vec3 pos = cat.position();
        airborne = false;
        double feetHere = grid.feet((int) Math.floor(pos.x), (int) Math.floor(pos.y + 0.01), (int) Math.floor(pos.z));

        if (goal != null && horiz(pos, goal) <= arriveWithin) {
            goal = null;
            path.clear();
        }
        if (goal == null) {
            speedNow = 0f;
            settleY(cat, pos, feetHere);
            noProgressTicks = 0;
            return;
        }

        if (--repathIn <= 0 || next >= path.size()) {
            path = finder.find((int) Math.floor(pos.x), Double.isNaN(feetHere) ? pos.y : feetHere, (int) Math.floor(pos.z),
                    (int) Math.floor(goal.x), (int) Math.floor(goal.y + 0.01), (int) Math.floor(goal.z));
            next = path.size() > 1 ? 1 : path.size();
            repathIn = 20;
        }

        Vec3 aim;
        if (next < path.size()) {
            PathFinder.Point p = path.get(next);
            aim = new Vec3(p.x(), p.y(), p.z());
            if (horiz(pos, aim) < 0.35 && next < path.size() - 1) {
                next++;
                p = path.get(next);
                aim = new Vec3(p.x(), p.y(), p.z());
            }
            if (next == path.size() - 1) {
                aim = new Vec3(goal.x, aim.y, goal.z);
            }
        } else {
            aim = goal;
        }

        double dx = aim.x - pos.x;
        double dz = aim.z - pos.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        double step = Math.min(speed, d);
        // Turn toward where she is going; slow down for sharp turns, like a cat does.
        float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yaw = cat.getYRot();
        float diff = wrap(want - yaw);
        float turn = Math.max(-TURN_PER_TICK, Math.min(TURN_PER_TICK, diff));
        yaw += turn;
        if (Math.abs(diff) > 70f) {
            step *= 0.35;
        }
        double nx = pos.x;
        double nz = pos.z;
        if (d > 1e-4) {
            nx += dx / d * step;
            nz += dz / d * step;
        }
        double feet = grid.feet((int) Math.floor(nx), (int) Math.floor(pos.y + 0.01), (int) Math.floor(nz));
        double ny = pos.y;
        if (Double.isNaN(feet)) {
            // The way ahead is not standable after all: stay put and repath soon.
            nx = pos.x;
            nz = pos.z;
            repathIn = Math.min(repathIn, 4);
        } else if (feet - pos.y > 0.3 && Double.isNaN(hopFrom)) {
            hopFrom = pos.y;
            hopTo = feet;
            hopT = 0;
        } else if (Double.isNaN(hopFrom)) {
            ny = feet < pos.y ? Math.max(feet, pos.y - 0.35) : feet;   // drops are quick but not instant
            airborne = feet < pos.y - 0.3;
        }
        if (!Double.isNaN(hopFrom)) {
            hopT++;
            double t = Math.min(1.0, hopT / (double) HOP_TICKS);
            ny = hopFrom + (hopTo - hopFrom) * t + Math.sin(t * Math.PI) * 0.45;
            airborne = true;
            if (t >= 1.0) {
                hopFrom = Double.NaN;
                ny = hopTo;
            }
        }
        place(cat, nx, ny, nz, yaw);
        speedNow = (float) Math.sqrt((nx - pos.x) * (nx - pos.x) + (nz - pos.z) * (nz - pos.z));

        double toGoal = horiz(new Vec3(nx, ny, nz), goal);
        if (toGoal < lastProgressDist - 0.02) {
            lastProgressDist = toGoal;
            noProgressTicks = 0;
        } else {
            noProgressTicks++;
        }
    }

    /** Turn on the spot to face a direction (degrees), smoothly. */
    public void face(NylahCat cat, float yawDeg) {
        float yaw = cat.getYRot();
        float diff = wrap(yawDeg - yaw);
        float turn = Math.max(-TURN_PER_TICK * 0.6f, Math.min(TURN_PER_TICK * 0.6f, diff));
        if (Math.abs(diff) < 1f) {
            return;
        }
        Vec3 p = cat.position();
        place(cat, p.x, p.y, p.z, yaw + turn);
    }

    private void settleY(NylahCat cat, Vec3 pos, double feet) {
        if (Double.isNaN(feet) || Math.abs(feet - pos.y) < 1e-3) {
            return;
        }
        double ny = feet < pos.y ? Math.max(feet, pos.y - 0.35) : feet;
        airborne = feet < pos.y - 0.3;
        place(cat, pos.x, ny, pos.z, cat.getYRot());
    }

    static void place(NylahCat cat, double x, double y, double z, float yaw) {
        cat.setPos(x, y, z);
        cat.setYRot(yaw);
        cat.setYBodyRot(yaw);
        cat.setYHeadRot(yaw);
    }

    /** Put her somewhere instantly (spawning, catching up) with no streak from her old spot. */
    public static void teleport(NylahCat cat, Vec3 at, float yaw) {
        cat.snapTo(at.x, at.y, at.z, yaw, 0f);
        cat.setYBodyRot(yaw);
        cat.setYHeadRot(yaw);
        cat.setOldPosAndRot();
    }

    public Grid grid() {
        return grid;
    }

    public PathFinder finder() {
        return finder;
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
