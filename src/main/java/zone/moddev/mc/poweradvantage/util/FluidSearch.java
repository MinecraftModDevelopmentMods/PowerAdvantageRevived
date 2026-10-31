package zone.moddev.mc.poweradvantage.util;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Read-only searches over fluids placed in the world: tracing a flow back to the source block feeding it, and
 * measuring a contiguous body of fluid by its source-block count.
 * <p>
 * Fluids are matched with {@link Fluid#isSame(Fluid)}, so the still and flowing variants of a fluid (for example
 * {@code Fluids.WATER} and {@code Fluids.FLOWING_WATER}) are interchangeable as search arguments. Nothing here
 * modifies the world.
 *
 * @since 3.1.0
 */
public class FluidSearch {
    /**
     * Prevents instantiation of the static fluid-search utility.
     */
    private FluidSearch() {
    }

    /**
     * Follows a flow of {@code searchedFor} upstream from {@code startPos} until it reaches the source block that
     * feeds it.
     * <p>
     * A falling block is fed from directly above, so the trace moves up. Otherwise the trace moves to the
     * horizontal neighbour holding the most fluid, provided it holds more than the current block; ties go to the
     * first neighbour found in {@link Direction.Plane#HORIZONTAL} order. Every step either rises one block or
     * moves to a strictly fuller block at the same height, so the trace always terminates.
     * <p>
     * Flow direction is inferred from fluid levels alone. Faces that block flow, such as the solid sides of a
     * waterlogged stair, are not considered, so in unusual layouts the trace can choose a neighbour that is not
     * the true feeder.
     * <p>
     * Positions in unloaded chunks are never read, so the trace never loads chunks: an unloaded neighbour is
     * ignored, and a trace whose next step is unloaded ends with {@code null}.
     *
     * @param level       world to search
     * @param searchedFor fluid to trace; its still or flowing variant may be given
     * @param startPos    position to start from; may itself be a source block
     * @return position of the feeding source block, which is {@code startPos} if it is already a source; or
     *         {@code null} if {@code startPos} does not hold {@code searchedFor}, the trace reaches a block with
     *         no feeder (for example while the fluid is mid-update after a source was removed), or the trace
     *         would have to enter an unloaded chunk
     */
    public static BlockPos findSourceBlock(Level level, Fluid searchedFor, BlockPos startPos) {
        BlockPos pos = startPos;
        while (true) {
            if (!level.isLoaded(pos)) return null;  // never force a chunk load
            FluidState fs = level.getFluidState(pos);
            if (!searchedFor.isSame(fs.getType())) return null;  // empty, wrong fluid, or mid-update
            if (fs.isSource()) return pos;

            if (fs.hasProperty(FlowingFluid.FALLING) && fs.getValue(FlowingFluid.FALLING)) {
                // flow is from above, trace that way
                pos = pos.above();
                continue;
            }

            // flow is from whichever side has more fluid than we do
            int best = fs.getAmount();
            BlockPos next = null;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos side = pos.relative(dir);
                if (!level.isLoaded(side)) continue;
                FluidState sfs = level.getFluidState(side);
                if (sfs.getAmount() > best && searchedFor.isSame(sfs.getType())) {
                    best = sfs.getAmount();
                    next = side;
                }
            }
            if (next == null) return null;  // no feeding neighbour
            pos = next;
        }
    }

    /**
     * Counts the source blocks of {@code fluid} contiguous with {@code start}, stopping once {@code limit} have
     * been found.
     * <p>
     * If {@code start} is not a source block, the search first traces upstream to one using
     * {@link #findSourceBlock}. It then runs a breadth-first search across all six faces, joining only source
     * blocks: flowing blocks neither count nor connect, so two pools joined only by a flowing stream count as
     * separate bodies. Waterlogged blocks report themselves as sources and are counted. Positions in unloaded
     * chunks are treated as boundaries, both here and in the initial trace, so the search never loads chunks.
     * <p>
     * Work is bounded by {@code limit} rather than by the size of the body: an ocean stops after {@code limit}
     * sources, and only bodies smaller than {@code limit} are explored completely. This makes the method suitable
     * for threshold checks such as deciding whether a body should be treated as infinite.
     *
     * @param level world to search
     * @param fluid fluid to count; its still or flowing variant may be given
     * @param start position within, or flowing from, the body to measure
     * @param limit maximum count to search for; should be positive
     * @return number of contiguous source blocks, capped at {@code limit}; a result equal to {@code limit} means
     *         "at least {@code limit}", and {@code 0} means no source of {@code fluid} could be reached from
     *         {@code start}
     */
    public static int countSourceBlocks(Level level, Fluid fluid, BlockPos start, int limit) {
        BlockPos origin = findSourceBlock(level, fluid, start);
        if (origin == null) return 0;

        LongSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        visited.add(origin.asLong());
        queue.enqueue(origin.asLong());

        while (!queue.isEmpty() && visited.size() < limit) {
            long cur = queue.dequeueLong();
            for (Direction dir : Direction.values()) {
                m.set(cur).move(dir);
                long next = m.asLong();
                if (visited.contains(next) || !level.isLoaded(m)) continue;
                FluidState fs = level.getFluidState(m);
                if (fs.isSource() && fluid.isSame(fs.getType())) {
                    visited.add(next);
                    queue.enqueue(next);
                }
            }
        }
        return Math.min(visited.size(), limit);
    }
}
