package zone.moddev.mc.poweradvantage.filter;

import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import zone.moddev.mc.lib.api.BlockEntityBinding;
import zone.moddev.mc.lib.common.runtime.MaterialEntityBlock;

/** Conveyor-height sorting machine using MMDLib's prepared entity binding and declarative menu event. */
public final class FilterBlock extends MaterialEntityBlock<FilterBlockEntity> {
    /** Direction in which nonmatching cargo travels. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Creates a filter from its prepared registration context.
     * @param properties fresh block properties
     * @param binding MMDLib entity factory and registered type
     */
    public FilterBlock(Properties properties, BlockEntityBinding<FilterBlockEntity> binding) {
        super(properties.noOcclusion(), binding.prepared(), binding.type());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** Declares horizontal travel direction.
     * @param builder state builder
     */
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    /** Places the filter in the same travel direction as an ordinary belt.
     * @param context placement context
     * @return initial state
     */
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /** Rotates travel direction with structures and the wrench.
     * @param state current state
     * @param rotation requested rotation
     * @return rotated state
     */
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }

    /** Mirrors travel direction.
     * @param state current state
     * @param mirror requested reflection
     * @return mirrored state
     */
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }

    /** Matches the existing conveyor-base model's height.
     * @param state current state
     * @param level block view
     * @param pos block position
     * @param context collision query
     * @return conveyor-height box
     */
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(0,0,0,16,15,16); }

    /** Enables a comparator signal reporting the current cargo classification.
     * @param state current state
     * @return true
     */
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

    /** Reads the entity's cargo signal.
     * @param state current state
     * @param level owning world
     * @param pos filter position
     * @return zero for empty, seven for nonmatching, fifteen for matching cargo
     */
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FilterBlockEntity be ? be.signal() : 0;
    }

    /** Drops all owned items exactly once on replacement, preserving them during rotation.
     * @param state old state
     * @param level owning world
     * @param pos filter position
     * @param next replacement state
     * @param moving whether this is part of a piston move
     */
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!level.isClientSide && state.getBlock() != next.getBlock() && level.getBlockEntity(pos) instanceof FilterBlockEntity be) be.dropContents();
        super.onRemove(state, level, pos, next, moving);
    }
}
