package zone.moddev.mc.poweradvantage.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import zone.moddev.mc.poweradvantage.network.FluidNetworks;

/** Full-cube fluid valve, conducting without redstone power and isolating all faces when powered. */
public class FluidSwitchBlock extends FluidPipeBlock {
    /** Persisted valve state used by both routing and the existing on/off models. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** Creates a switch with the standard pipe rating.
     * @param properties block properties supplied by MMDLib
     */
    public FluidSwitchBlock(Properties properties) { this(properties, DEFAULT_TRANSFER_RATE); }

    /** Creates a switch with a downstream mod's positive transfer rating.
     * @param properties block properties supplied by the caller
     * @param transferRate maximum throughput in mB per tick
     */
    public FluidSwitchBlock(Properties properties, int transferRate) {
        super(properties, false, transferRate);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, true));
    }

    /** Adds the valve flag alongside the inherited pipe properties.
     * @param builder block state builder
     */
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    /** Tests whether the valve currently conducts.
     * @param state current block state
     * @return true for an open valve
     */
    @Override public boolean isTransportEnabled(BlockState state) { return state.getValue(ACTIVE); }

    /** Initializes the valve from the power at the placement position.
     * @param context placement context
     * @return initial pipe state with the appropriate valve flag
     */
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(ACTIVE,
            !context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /** Updates power for placements made without a player as well as normal placement.
     * @param state placed state
     * @param level owning world
     * @param pos valve position
     * @param old previous state
     * @param moving whether placement is part of a piston move
     */
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (old.getBlock() != this) updatePower(state, level, pos);
    }

    /** Rechecks power before forwarding neighbor topology changes.
     * @param state current state
     * @param level owning world
     * @param pos valve position
     * @param block notifying block
     * @param from notifying position
     * @param moving whether the notification comes from a piston move
     */
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        updatePower(state, level, pos);
        super.neighborChanged(state, level, pos, block, from, moving);
    }

    /** Invalidates old routes immediately whenever redstone changes the valve state.
     * @param state state before checking power
     * @param level owning world
     * @param pos valve position
     */
    private void updatePower(BlockState state, Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        boolean active = !level.hasNeighborSignal(pos);
        if (state.getValue(ACTIVE) == active) return;
        // Suspend old routes before block updates can call neighboring fluid handlers.
        FluidNetworks.get(server).changed(pos);
        level.setBlock(pos, state.setValue(ACTIVE, active), Block.UPDATE_ALL);
    }

    /** Uses the full-cube geometry of the legacy switch models.
     * @param state current state
     * @param level block view
     * @param pos valve position
     * @param context shape query
     * @return full cube
     */
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}
