package zone.moddev.mc.poweradvantage.blocks;

import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.fluids.FluidUtil;
import zone.moddev.mc.lib.api.BlockEntityBinding;
import zone.moddev.mc.lib.common.runtime.MaterialEntityBlock;
import zone.moddev.mc.poweradvantage.tiles.MachineBlockEntity;

/**
 * Material-backed machine block that delegates entity creation and ticking to MMDLib, supports bucket interaction
 * and opens a synchronized status menu.
 */
public class MachineBlock extends MaterialEntityBlock<MachineBlockEntity> {
    /**
     * Horizontal machine placement and rendering direction.
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * Creates a machine block delegating entity creation and ticking to its prepared MMDLib binding.
     *
     * @param properties fresh Minecraft block properties
     * @param binding prepared MMDLib block-entity creation/type binding
     */
    public MachineBlock(Properties properties, BlockEntityBinding<MachineBlockEntity> binding) {
        super(properties.noOcclusion(), binding.prepared(), binding.type());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    /**
     * Declares the block-state properties used for placement, connections and rendering.
     *
     * @param builder block-state definition builder
     */
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    /**
     * Creates the initial state from player placement context.
     *
     * @param context player placement and clicked-face context
     * @return initial placed block state
     */
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    /**
     * Transforms directional properties under a structure rotation.
     *
     * @param state current block state
     * @param rotation structure rotation to apply
     * @return rotated block state
     */
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    /**
     * Transforms directional properties under a structure mirror.
     *
     * @param state current block state
     * @param mirror structure mirror to apply
     * @return mirrored block state
     */
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    /**
     * Handles fluid-container interaction before falling back to the machine's ordinary block interaction.
     *
     * @param stack item or fluid contents offered to the operation
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param player interacting or watching player
     * @param hand hand used for container interaction
     * @param hit block interaction hit result
     * @return sided handled result for containers, otherwise default block interaction
     */
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity be) || be.isEnergy()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (FluidUtil.getFluidHandler(stack).isPresent()) {
            if (!level.isClientSide) FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection());
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    /**
     * Enables comparator queries for machine blocks.
     *
     * @param state current block state
     * @return true
     */
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    /**
     * Queries the machine's current tank-derived comparator output.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @return signal strength from zero to fifteen
     */
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) { return signal(level, pos); }
    /**
     * Enables redstone signal queries for the machine.
     *
     * @param state current block state
     * @return true
     */
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    /**
     * Queries the tank-derived weak redstone signal.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param side queried or incoming face; null denotes unsided access where supported
     * @return signal strength from zero to fifteen
     */
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return signal(level, pos); }
    /**
     * Queries the tank-derived strong redstone signal.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param side queried or incoming face; null denotes unsided access where supported
     * @return signal strength from zero to fifteen
     */
    @Override protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return signal(level, pos); }
    /**
     * Reads the machine entity's current redstone signal, if present.
     *
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @return tank-derived strength, or zero without a matching entity
     */
    private int signal(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MachineBlockEntity be ? be.redstoneSignal() : 0;
    }
}
