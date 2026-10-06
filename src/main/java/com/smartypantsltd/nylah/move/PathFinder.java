package com.smartypantsltd.nylah.move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A* over standable columns, for a cat: she can hop up one block and drop
 * three, never cuts a corner round a wall, and keeps clear of water, lava and
 * fire (the grid prices those at infinity).
 *
 * <p>Pure Java (no Minecraft): tested on hand-made grids. Results are
 * string-pulled so she walks a natural line instead of grid zig-zags.</p>
 */
public final class PathFinder {

    public static final double MAX_STEP_UP = 1.05;
    public static final double MAX_DROP = 3.05;

    private static final int[][] DIRS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
    };

    private final Grid grid;
    private final int maxNodes;

    public PathFinder(Grid grid, int maxNodes) {
        this.grid = grid;
        this.maxNodes = maxNodes;
    }

    /** A waypoint: column centre and feet height. */
    public record Point(double x, double y, double z) {
    }

    private static final class Node {
        final int x;
        final int z;
        final double feet;
        double g;
        double f;
        Node parent;
        boolean closed;

        Node(int x, int z, double feet) {
            this.x = x;
            this.z = z;
            this.feet = feet;
        }
    }

    /**
     * A path from (sx, sy, sz) to (tx, ty, tz), block coordinates of the
     * columns. If the goal cannot be reached, the path to the reachable spot
     * closest to it (or an empty list if she cannot move at all).
     */
    public List<Point> find(int sx, double sFeet, int sz, int tx, int tyHint, int tz) {
        Map<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Node start = new Node(sx, sz, sFeet);
        start.f = h(sx, sz, tx, tz);
        nodes.put(key(sx, sz, sFeet), start);
        open.add(start);
        Node best = start;
        double bestH = start.f;
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            Node n = open.poll();
            if (n.closed) {
                continue;
            }
            n.closed = true;
            expanded++;
            double hn = h(n.x, n.z, tx, tz);
            if (hn < bestH) {
                bestH = hn;
                best = n;
            }
            if (n.x == tx && n.z == tz) {
                best = n;
                break;
            }
            for (int[] d : DIRS) {
                int nx = n.x + d[0];
                int nz = n.z + d[1];
                double f = step(n.x, n.feet, n.z, nx, nz);
                if (Double.isNaN(f)) {
                    continue;
                }
                if (d[0] != 0 && d[1] != 0) {
                    // No corner cutting: both side-steps must be open too.
                    if (Double.isNaN(step(n.x, n.feet, n.z, n.x + d[0], n.z))
                            || Double.isNaN(step(n.x, n.feet, n.z, n.x, n.z + d[1]))) {
                        continue;
                    }
                }
                double extra = grid.cost(nx, f, nz);
                if (Double.isInfinite(extra)) {
                    continue;
                }
                double dy = f - n.feet;
                double g = n.g + (d[0] != 0 && d[1] != 0 ? 1.414 : 1.0)
                        + Math.max(0, dy) * 0.6 + Math.max(0, -dy) * 0.3 + extra;
                long k = key(nx, nz, f);
                Node m = nodes.get(k);
                if (m == null) {
                    m = new Node(nx, nz, f);
                    nodes.put(k, m);
                } else if (m.closed || g >= m.g) {
                    continue;
                }
                m.g = g;
                m.f = g + h(nx, nz, tx, tz);
                m.parent = n;
                open.add(m);
            }
        }
        List<Point> path = new ArrayList<>();
        for (Node n = best; n != null; n = n.parent) {
            path.add(new Point(n.x + 0.5, n.feet, n.z + 0.5));
        }
        Collections.reverse(path);
        return smooth(path);
    }

    /** Feet height after stepping from one column to the next, or NaN if she can't. */
    double step(int x, double feet, int z, int nx, int nz) {
        double f = grid.feet(nx, (int) Math.floor(feet + 0.01), nz);
        if (Double.isNaN(f)) {
            return Double.NaN;
        }
        double dy = f - feet;
        if (dy > MAX_STEP_UP || dy < -MAX_DROP) {
            return Double.NaN;
        }
        return f;
    }

    /** Drop waypoints she can reach in a straight walk from an earlier one. */
    List<Point> smooth(List<Point> path) {
        if (path.size() <= 2) {
            return path;
        }
        List<Point> out = new ArrayList<>();
        int i = 0;
        out.add(path.get(0));
        while (i < path.size() - 1) {
            int j = path.size() - 1;
            while (j > i + 1 && !straight(path.get(i), path.get(j))) {
                j--;
            }
            out.add(path.get(j));
            i = j;
        }
        return out;
    }

    /** Can she walk straight from a to b: every column on the way standable, no big steps? */
    boolean straight(Point a, Point b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double len = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(len / 0.3));
        double feet = a.y();
        int px = (int) Math.floor(a.x());
        int pz = (int) Math.floor(a.z());
        for (int s = 1; s <= steps; s++) {
            double t = s / (double) steps;
            int cx = (int) Math.floor(a.x() + dx * t);
            int cz = (int) Math.floor(a.z() + dz * t);
            if (cx == px && cz == pz) {
                continue;
            }
            if (cx != px && cz != pz
                    && (Double.isNaN(step(px, feet, pz, cx, pz)) || Double.isNaN(step(px, feet, pz, px, cz)))) {
                return false;
            }
            double f = step(px, feet, pz, cx, cz);
            if (Double.isNaN(f) || Math.abs(f - feet) > 0.6 || Double.isInfinite(grid.cost(cx, f, cz))) {
                return false;
            }
            feet = f;
            px = cx;
            pz = cz;
        }
        return true;
    }

    private static double h(int x, int z, int tx, int tz) {
        int dx = Math.abs(x - tx);
        int dz = Math.abs(z - tz);
        return Math.max(dx, dz) + 0.414 * Math.min(dx, dz);
    }

    private static long key(int x, int z, double feet) {
        long fy = (long) Math.floor(feet * 2) & 0xFFFFL;
        return (((long) x & 0xFFFFFFL) << 40) | (((long) z & 0xFFFFFFL) << 16) | fy;
    }
}
