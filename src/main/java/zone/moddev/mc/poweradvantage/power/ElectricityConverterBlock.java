package zone.moddev.mc.poweradvantage.power;

import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import zone.moddev.mc.lib.api.BlockEntityBinding;
import zone.moddev.mc.lib.common.runtime.MaterialEntityBlock;

/** FE/electricity conversion machine using MMDLib's prepared entity binding and declarative menu event. */
public final class ElectricityConverterBlock extends MaterialEntityBlock<ElectricityConverterBlockEntity> {
    /** Horizontal rendering orientation. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Creates a converter from its prepared registration context.
     * @param properties fresh block properties
     * @param binding MMDLib entity factory and registered type
     */
    public ElectricityConverterBlock(Properties properties, BlockEntityBinding<ElectricityConverterBlockEntity> binding) {
        super(properties.noOcclusion(), binding.prepared(), binding.type());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** Declares horizontal travel direction.
     * @param builder state builder
     */
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    /** Orients the converter using the placement direction.
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

    /** Matches the existing converter model's bounds.
     * @param state current state
     * @param level block view
     * @param pos block position
     * @param context collision query
     * @return full-cube box
     */
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(0,0,0,16,16,16); }

}
