package com.smartypantsltd.nylah.move;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Her pathfinding on little hand-made maps. */
class PathFinderTest {

    /** A flat world at height 64, with walls (no standing) and raised/sunk columns. */
    static final class Map2 implements Grid {
        final Map<Long, Double> heights = new HashMap<>();
        final Set<Long> water = new HashSet<>();
        final Set<Long> walls = new HashSet<>();

        static long k(int x, int z) {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }

        @Override
        public double feet(int x, int yHint, int z) {
            if (walls.contains(k(x, z))) {
                return Double.NaN;
            }
            double h = heights.getOrDefault(k(x, z), 64.0);
            return h > yHint + 1.5 || h < yHint - 4 ? Double.NaN : h;
        }

        @Override
        public double cost(int x, double feet, int z) {
            return water.contains(k(x, z)) ? Double.POSITIVE_INFINITY : 0;
        }
    }

    @Test
    void walksStraightAcrossOpenGround() {
        Map2 m = new Map2();
        List<PathFinder.Point> p = new PathFinder(m, 4000).find(0, 64, 0, 10, 64, 0);
        assertEquals(10.5, p.get(p.size() - 1).x(), 1e-6);
        assertEquals(2, p.size(), "open ground should string-pull to one straight line");
    }

    @Test
    void goesRoundAWall() {
        Map2 m = new Map2();
        for (int z = -3; z <= 3; z++) {
            m.walls.add(Map2.k(5, z));
        }
        List<PathFinder.Point> p = new PathFinder(m, 4000).find(0, 64, 0, 10, 64, 0);
        PathFinder.Point end = p.get(p.size() - 1);
        assertEquals(10.5, end.x(), 1e-6);
        for (PathFinder.Point pt : p) {
            assertFalse(m.walls.contains(Map2.k((int) Math.floor(pt.x()), (int) Math.floor(pt.z()))), "walked into the wall");
        }
    }

    @Test
    void hopsUpOneBlockButNotTwo() {
        Map2 m = new Map2();
        for (int z = -20; z <= 20; z++) {
            m.heights.put(Map2.k(5, z), 65.0);
            for (int x = 6; x <= 12; x++) {
                m.heights.put(Map2.k(x, z), 65.0);
            }
        }
        List<PathFinder.Point> up = new PathFinder(m, 4000).find(0, 64, 0, 10, 65, 0);
        assertEquals(65.0, up.get(up.size() - 1).y(), 1e-6);

        Map2 cliff = new Map2();
        for (int z = -30; z <= 30; z++) {
            for (int x = 5; x <= 12; x++) {
                cliff.heights.put(Map2.k(x, z), 66.0);
            }
        }
        // The goal is on top of a two-block cliff she cannot climb: she may walk round to
        // the nearest spot she can reach, but must never stand on the cliff.
        List<PathFinder.Point> blocked = new PathFinder(cliff, 4000).find(0, 64, 0, 10, 66, 0);
        for (PathFinder.Point pt : blocked) {
            assertEquals(64.0, pt.y(), 1e-6, "she climbed a two-block cliff");
        }
    }

    @Test
    void keepsHerPawsDry() {
        Map2 m = new Map2();
        for (int x = 3; x <= 6; x++) {
            for (int z = -2; z <= 2; z++) {
                m.water.add(Map2.k(x, z));
            }
        }
        List<PathFinder.Point> p = new PathFinder(m, 4000).find(0, 64, 0, 10, 64, 0);
        for (int i = 1; i < p.size(); i++) {
            PathFinder.Point a = p.get(i - 1);
            PathFinder.Point b = p.get(i);
            for (double t = 0; t <= 1; t += 0.05) {
                int x = (int) Math.floor(a.x() + (b.x() - a.x()) * t);
                int z = (int) Math.floor(a.z() + (b.z() - a.z()) * t);
                assertFalse(m.water.contains(Map2.k(x, z)), "walked through water at " + x + "," + z);
            }
        }
        assertEquals(10.5, p.get(p.size() - 1).x(), 1e-6);
    }

    @Test
    void neverCutsACorner() {
        Map2 m = new Map2();
        m.walls.add(Map2.k(1, 0));
        m.walls.add(Map2.k(0, 1));
        List<PathFinder.Point> p = new PathFinder(m, 4000).find(0, 64, 0, 1, 64, 1);
        // The only diagonal step is blocked on both sides: she must not squeeze through.
        PathFinder.Point end = p.get(p.size() - 1);
        assertFalse(Math.floor(end.x()) == 1 && Math.floor(end.z()) == 1 && p.size() == 2);
    }

    @Test
    void unreachableGoalGetsHerAsCloseAsPossible() {
        Map2 m = new Map2();
        for (int z = -50; z <= 50; z++) {
            m.walls.add(Map2.k(5, z));
        }
        List<PathFinder.Point> p = new PathFinder(m, 3000).find(0, 64, 0, 10, 64, 0);
        PathFinder.Point end = p.get(p.size() - 1);
        assertEquals(4.5, end.x(), 1e-6);
    }
}
