package com.smartypantsltd.nylah.move;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The client's loaded blocks, as somewhere a cat can walk.
 *
 * <p>Read-only: only {@code getBlockState} / {@code getFluidState} /
 * {@code getCollisionShape} on blocks the client already has. Unloaded blocks
 * read as air, which simply has nowhere to stand.</p>
 */
public final class LevelGrid implements Grid {

    private final Level level;
    private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

    public LevelGrid(Level level) {
        this.level = level;
    }

    @Override
    public double feet(int x, int yHint, int z) {
        for (int y = yHint + 1; y >= yHint - 4; y--) {
            double f = standAt(x, y, z);
            if (!Double.isNaN(f)) {
                return f;
            }
        }
        return Double.NaN;
    }

    /** Feet height if she can stand in block y of this column, else NaN. */
    private double standAt(int x, int y, int z) {
        BlockState here = state(x, y, z);
        double low = top(here, x, y, z);          // a slab, carpet or snow layer in her own block
        if (!Double.isNaN(low) && low > 0.55) {
            return Double.NaN;                     // her block is (mostly) solid
        }
        double feet;
        if (!Double.isNaN(low)) {
            feet = y + low;
        } else {
            BlockState below = state(x, y - 1, z);
            double t = top(below, x, y - 1, z);
            if (Double.isNaN(t)) {
                return Double.NaN;                 // nothing under her
            }
            feet = y - 1 + t;
            if (t < 0.99) {
                return Double.NaN;                 // a slab below: she stands IN that block, found one step lower
            }
        }
        // Headroom: she is small, but needs the block her body is in to be clear.
        BlockState above = state(x, (int) Math.floor(feet) + 1, z);
        double topAbove = top(above, x, (int) Math.floor(feet) + 1, z);
        if (!Double.isNaN(topAbove) && feet - Math.floor(feet) + 0.7 > 1.0) {
            return Double.NaN;
        }
        return feet;
    }

    @Override
    public double cost(int x, double feet, int z) {
        int y = (int) Math.floor(feet + 0.01);
        BlockState at = state(x, y, z);
        BlockState under = state(x, y - 1, z);
        if (!at.getFluidState().isEmpty() || !under.getFluidState().isEmpty()) {
            return Double.POSITIVE_INFINITY;       // cats keep their paws dry
        }
        if (dangerous(at.getBlock()) || under.getBlock() == Blocks.MAGMA_BLOCK) {
            return Double.POSITIVE_INFINITY;
        }
        return 0.0;
    }

    private static boolean dangerous(Block b) {
        return b == Blocks.FIRE || b == Blocks.SOUL_FIRE || b == Blocks.LAVA || b == Blocks.CACTUS
                || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.POWDER_SNOW || b == Blocks.CAMPFIRE
                || b == Blocks.SOUL_CAMPFIRE || b == Blocks.WITHER_ROSE;
    }

    private BlockState state(int x, int y, int z) {
        return level.getBlockState(m.set(x, y, z));
    }

    /** Height of a block's collision top within its own block (0..1+), or NaN if it has none. */
    private double top(BlockState s, int x, int y, int z) {
        if (s.isAir()) {
            return Double.NaN;
        }
        VoxelShape shape = s.getCollisionShape(level, m.set(x, y, z));
        if (shape.isEmpty()) {
            return Double.NaN;
        }
        return Math.min(1.5, shape.max(Direction.Axis.Y));
    }
}
