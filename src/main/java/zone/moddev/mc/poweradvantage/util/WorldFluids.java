package zone.moddev.mc.poweradvantage.util;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Bounded, loaded-chunk-only world fluid work. Only commit whole buckets that fit. */
public final class WorldFluids {
    private static final int RANGE = 32;
    private static final int SEARCH_LIMIT = 4096;
    private static final Direction[] COLLECT = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final Direction[] FILL = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private WorldFluids() {}

    public static void collect(Level level, BlockPos start, FluidTank tank) {
        if (tank.getSpace() < FluidType.BUCKET_VOLUME) return;
        var queue = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && tank.getSpace() >= FluidType.BUCKET_VOLUME) {
            BlockPos pos = queue.removeFirst();
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) continue;
            var state = level.getBlockState(pos);
            var fluid = state.getFluidState();
            if (fluid.isEmpty()) {
                if (state.isAir()) enqueue(queue, seen, start, pos, COLLECT);
                continue;
            }
            if (!tank.isEmpty() && !fluid.getType().isSame(tank.getFluid().getFluid())) continue;
            enqueue(queue, seen, start, pos, COLLECT);
            if (!fluid.isSource() || !(state.getBlock() instanceof BucketPickup pickup)) continue;
            var offered = new FluidStack(fluid.getType(), FluidType.BUCKET_VOLUME);
            if (tank.fill(offered, FluidAction.SIMULATE) != FluidType.BUCKET_VOLUME) continue;
            // BucketPickup also handles waterlogged blocks without destroying their host block.
            ItemStack bucket = pickup.pickupBlock(null, level, pos, state);
            if (!bucket.isEmpty()) tank.fill(offered, FluidAction.EXECUTE);
        }
    }

    public static void discharge(Level level, BlockPos start, FluidTank tank) {
        if (tank.getFluidAmount() < FluidType.BUCKET_VOLUME) return;
        var fluid = tank.getFluid().getFluid();
        if (!(fluid instanceof FlowingFluid)) return;
        var queue = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && tank.getFluidAmount() >= FluidType.BUCKET_VOLUME) {
            BlockPos pos = queue.removeFirst();
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) continue;
            var state = level.getBlockState(pos);
            var existing = state.getFluidState();
            if (!existing.isEmpty() && !existing.getType().isSame(fluid)) continue;
            if (existing.isSource()) {
                enqueue(queue, seen, start, pos, FILL);
                continue;
            }
            // Stay in replaceable fluid space; never remove containers or waterlogged hosts.
            if (!state.isAir() && !(state.getBlock() instanceof LiquidBlock)) continue;
            var offered = tank.drain(FluidType.BUCKET_VOLUME, FluidAction.SIMULATE);
            if (FluidUtil.tryPlaceFluid(null, level, InteractionHand.MAIN_HAND, pos, tank, offered)) {
                enqueue(queue, seen, start, pos, FILL);
            }
        }
    }
    private static void enqueue(ArrayDeque<BlockPos> queue, Set<BlockPos> seen, BlockPos start, BlockPos pos, Direction[] directions) {
        for (Direction side : directions) {
            BlockPos next = pos.relative(side);
            if (seen.size() < SEARCH_LIMIT && start.distSqr(next) <= RANGE * RANGE && seen.add(next)) queue.addLast(next);
        }
    }
}
