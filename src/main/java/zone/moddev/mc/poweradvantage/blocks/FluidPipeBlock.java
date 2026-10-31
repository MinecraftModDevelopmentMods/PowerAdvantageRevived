package zone.moddev.mc.poweradvantage.blocks;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import zone.moddev.mc.poweradvantage.network.FluidNetworks;

/**
 * Ordinary fluid-pipe or extraction-terminal block without a block entity. The dimension manager derives
 * connections and routes fluid; the lowest block rating sets the shared connected-component throughput.
 */
public class FluidPipeBlock extends Block implements SimpleWaterloggedBlock {
    /**
     * Default throughput in millibuckets per server tick.
     */
    public static final int DEFAULT_TRANSFER_RATE = 1000;
    /**
     * Directional boolean properties selecting the existing pipe model variants.
     */
    public static final Map<Direction, BooleanProperty> CONNECTIONS = new EnumMap<>(Direction.class);
    /**
     * Persisted extraction-terminal source direction.
     */
    public static final DirectionProperty SOURCE = BlockStateProperties.FACING;
    /**
     * Whether the pipe holds a water source, so it can be placed in and under water.
     */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /**
     * Cached collision/selection shapes for all six-face connection masks.
     */
    private static final VoxelShape[] SHAPES = new VoxelShape[64];
    static {
        for (Direction side : Direction.values()) CONNECTIONS.put(side, BooleanProperty.create(side.getName()));
        VoxelShape core = box(4,4,4,12,12,12);
        VoxelShape[] arms = {box(4,0,4,12,4,12), box(4,12,4,12,16,12), box(4,4,0,12,12,4),
            box(4,4,12,12,12,16), box(0,4,4,4,12,12), box(12,4,4,16,12,12)};
        for (int mask=0; mask<64; mask++) {
            VoxelShape shape=core;
            for (Direction side : Direction.values()) if ((mask & (1 << side.ordinal())) != 0) shape=Shapes.or(shape,arms[side.ordinal()]);
            SHAPES[mask]=shape;
        }
    }
    /**
     * Whether this block form automatically extracts from a configured neighbor.
     */
    private final boolean terminal;
    /**
     * Immutable positive local rating in millibuckets per tick.
     */
    private final int transferRate;
    /**
     * Creates an ordinary pipe or extraction terminal with the default or supplied positive mB/tick rating.
     *
     * @param properties fresh Minecraft block properties
     * @param terminal Whether this block form automatically extracts from a configured neighbor
     */
    public FluidPipeBlock(Properties properties, boolean terminal) {
        this(properties, terminal, DEFAULT_TRANSFER_RATE);
    }
    /**
     * Creates an ordinary pipe or extraction terminal with the default or supplied positive mB/tick rating.
     *
     * @param transferRate positive millibuckets per server tick; shared component throughput is the minimum rating
     *     of all its connected pipes
     * @param properties fresh Minecraft block properties
     * @param terminal Whether this block form automatically extracts from a configured neighbor
     * @throws IllegalArgumentException if the supplied transfer rating is not positive
     */
    public FluidPipeBlock(Properties properties, boolean terminal, int transferRate) {
        super(properties.noOcclusion());
        if (transferRate <= 0) throw new IllegalArgumentException("Pipe transfer rate must be positive");
        this.transferRate=transferRate;
        this.terminal=terminal;
        BlockState state=stateDefinition.any().setValue(SOURCE,Direction.NORTH).setValue(WATERLOGGED,false);
        for (var property : CONNECTIONS.values()) state=state.setValue(property,false);
        registerDefaultState(state);
    }
    /**
     * Immutable throughput rating, in mB per server tick.
     *
     * @return millibuckets per server tick
     */
    public final int getTransferRate() { return transferRate; }
    /**
     * Register on the consuming mod's RegisterCapabilitiesEvent for its own pipe blocks.
     *
     * @param event NeoForge registration or lifecycle event
     * @param blocks registered block instances owned by the consuming mod
     */
    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event,
            FluidPipeBlock... blocks) {
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
            (level,pos,state,be,side) -> level instanceof ServerLevel server
                ? FluidNetworks.get(server).ingress(pos,side) : null, blocks);
    }
    /**
     * Reports whether this form automatically extracts from its configured source.
     *
     * @return true for an extraction terminal
     */
    public boolean isTerminal() { return terminal; }
    /** Tests whether this state participates in routing. Stateful valves override this hook.
     * @param state current state of this pipe
     * @return true when fluid may pass through this node
     */
    public boolean isTransportEnabled(BlockState state) { return true; }

    /**
     * Declares the block-state properties used for placement, connections and rendering.
     *
     * @param builder block-state definition builder
     */
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        builder.add(SOURCE, WATERLOGGED);
    }
    /**
     * Reports the water held by a waterlogged pipe.
     *
     * @param state current block state
     * @return still water when waterlogged, otherwise empty
     */
    @Override protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? net.minecraft.world.level.material.Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
    /**
     * Keeps water flowing around a waterlogged pipe and refreshes its visual connections.
     *
     * @param state current block state
     * @param direction side of the changed neighbour
     * @param neighbour neighbouring block state
     * @param level owning world
     * @param pos block position being queried or changed
     * @param neighbourPos position of the changed neighbour
     * @return the updated state
     */
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER, net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }
    /**
     * Creates the initial state from player placement context.
     *
     * @param context player placement and clicked-face context
     * @return initial placed block state
     */
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == net.minecraft.world.level.material.Fluids.WATER;
        return connections(defaultBlockState().setValue(SOURCE,context.getClickedFace().getOpposite()).setValue(WATERLOGGED,water), context.getLevel(),context.getClickedPos());
    }
    /**
     * Recomputes six connections to loaded pipes and sided fluid handlers.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @return state carrying the refreshed connection flags
     */
    public static BlockState connections(BlockState state, Level level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos other=pos.relative(side);
            boolean connected=level.isLoaded(other) && (level.getBlockState(other).getBlock() instanceof FluidPipeBlock pipe
                && pipe.isTransportEnabled(level.getBlockState(other))
                || level.getCapability(Capabilities.FluidHandler.BLOCK,other,side.getOpposite()) != null);
            state=state.setValue(CONNECTIONS.get(side),connected);
        }
        return state;
    }
    /**
     * Publishes changed visual connection flags using client updates without neighbor-notification recursion.
     *
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     */
    public static void refresh(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        var state=level.getBlockState(pos);
        if (state.getBlock() instanceof FluidPipeBlock) {
            var next=connections(state,level,pos);
            if (next != state) level.setBlock(pos,next,Block.UPDATE_CLIENTS);
        }
    }
    /**
     * Notifies the server transport manager when a new transport block is placed.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param old previous block state
     * @param moving whether the block transition is part of a moving-block operation
     */
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving) {
        super.onPlace(state,level,pos,old,moving);
        if (level instanceof ServerLevel server && state.getBlock()!=old.getBlock()) FluidNetworks.get(server).changed(pos);
    }
    /**
     * Withdraws removed transport topology before delegating normal block removal.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param next replacement block state
     * @param moving whether the block transition is part of a moving-block operation
     */
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (level instanceof ServerLevel server && state.getBlock()!=next.getBlock()) FluidNetworks.get(server).changed(pos);
        super.onRemove(state,level,pos,next,moving);
    }
    /**
     * Forwards potentially meaningful server-side neighbor changes to the transport manager.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param block neighbor block type originating the notification
     * @param from neighbor position that caused the notification
     * @param moving whether the block transition is part of a moving-block operation
     */
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        if (level instanceof ServerLevel server) FluidNetworks.get(server).neighbourChanged(pos,from);
    }
    /**
     * Selects the clicked terminal face as its extraction source and reports the direction; ordinary pipes pass.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param player interacting or watching player
     * @param hit block interaction hit result
     * @return pass for a plain pipe, otherwise a sided handled result
     */
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if (!terminal) return InteractionResult.PASS;
        if (!level.isClientSide) {
            // The clicked face selects the adjacent source; persists as ordinary block state.
            level.setBlock(pos,state.setValue(SOURCE,hit.getDirection()),Block.UPDATE_ALL);
            FluidNetworks.get((ServerLevel)level).changed(pos);
            player.displayClientMessage(Component.translatable("message.poweradvantage.pipe_source",Component.translatable("direction.poweradvantage."+hit.getDirection().getName())),true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    /**
     * Returns the collision/selection geometry for the current state.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param context entity collision or selection query context
     * @return state-dependent voxel shape
     */
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        int mask=0;
        for (Direction side : Direction.values()) if (state.getValue(CONNECTIONS.get(side))) mask |= 1 << side.ordinal();
        return SHAPES[mask];
    }
    /**
     * Transforms directional properties under a structure rotation.
     *
     * @param state current block state
     * @param rotation structure rotation to apply
     * @return rotated block state
     */
    @Override protected BlockState rotate(BlockState state,Rotation rotation) {
        BlockState next=state.setValue(SOURCE,rotation.rotate(state.getValue(SOURCE)));
        for (Direction side : Direction.values()) next=next.setValue(CONNECTIONS.get(rotation.rotate(side)),state.getValue(CONNECTIONS.get(side)));
        return next;
    }
    /**
     * Transforms directional properties under a structure mirror.
     *
     * @param state current block state
     * @param mirror structure mirror to apply
     * @return mirrored block state
     */
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {
        BlockState next=state.setValue(SOURCE,mirror.mirror(state.getValue(SOURCE)));
        for (Direction side : Direction.values()) next=next.setValue(CONNECTIONS.get(mirror.mirror(side)),state.getValue(CONNECTIONS.get(side)));
        return next;
    }
}
