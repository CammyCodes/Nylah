package com.smartypantsltd.nylah.move;

/**
 * The walkable world as the pathfinder sees it. {@code LevelGrid} answers from
 * the client's loaded blocks; tests answer from little hand-made maps.
 */
public interface Grid {

    /**
     * Where her feet would rest in column (x, z), searching from a little above
     * {@code yHint} down to a few blocks below it: the top of the ground (e.g.
     * 64.0 on a full block, 63.5 on a slab). NaN if there is nowhere to stand.
     */
    double feet(int x, int yHint, int z);

    /** Extra cost of standing at this spot; {@link Double#POSITIVE_INFINITY} = never (water, fire, lava...). */
    double cost(int x, double feet, int z);
}
