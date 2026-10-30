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

/** Ordinary blocks: all simulation and endpoint state belongs to the dimension manager. */
public class FluidPipeBlock extends Block {
    /** Default throughput in millibuckets per server tick. */
    public static final int DEFAULT_TRANSFER_RATE = 1000;
    public static final Map<Direction, BooleanProperty> CONNECTIONS = new EnumMap<>(Direction.class);
    public static final DirectionProperty SOURCE = BlockStateProperties.FACING;
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
    private final boolean terminal;
    private final int transferRate;
    public FluidPipeBlock(Properties properties, boolean terminal) {
        this(properties, terminal, DEFAULT_TRANSFER_RATE);
    }
    /**
     * @param transferRate positive millibuckets per server tick; shared component throughput
     *                     is the minimum rating of all its connected pipes
     */
    public FluidPipeBlock(Properties properties, boolean terminal, int transferRate) {
        super(properties.noOcclusion());
        if (transferRate <= 0) throw new IllegalArgumentException("Pipe transfer rate must be positive");
        this.transferRate=transferRate;
        this.terminal=terminal;
        BlockState state=stateDefinition.any().setValue(SOURCE,Direction.NORTH);
        for (var property : CONNECTIONS.values()) state=state.setValue(property,false);
        registerDefaultState(state);
    }
    /** Immutable throughput rating, in mB per server tick. */
    public final int getTransferRate() { return transferRate; }
    /** Register on the consuming mod's RegisterCapabilitiesEvent for its own pipe blocks. */
    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event,
            FluidPipeBlock... blocks) {
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
            (level,pos,state,be,side) -> level instanceof ServerLevel server
                ? FluidNetworks.get(server).ingress(pos,side) : null, blocks);
    }
    public boolean isTerminal() { return terminal; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        builder.add(SOURCE);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(defaultBlockState().setValue(SOURCE,context.getClickedFace().getOpposite()), context.getLevel(),context.getClickedPos());
    }
    public static BlockState connections(BlockState state, Level level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos other=pos.relative(side);
            boolean connected=level.isLoaded(other) && (level.getBlockState(other).getBlock() instanceof FluidPipeBlock
                || level.getCapability(Capabilities.FluidHandler.BLOCK,other,side.getOpposite()) != null);
            state=state.setValue(CONNECTIONS.get(side),connected);
        }
        return state;
    }
    public static void refresh(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        var state=level.getBlockState(pos);
        if (state.getBlock() instanceof FluidPipeBlock) {
            var next=connections(state,level,pos);
            if (next != state) level.setBlock(pos,next,Block.UPDATE_CLIENTS);
        }
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving) {
        super.onPlace(state,level,pos,old,moving);
        if (level instanceof ServerLevel server && state.getBlock()!=old.getBlock()) FluidNetworks.get(server).changed(pos);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (level instanceof ServerLevel server && state.getBlock()!=next.getBlock()) FluidNetworks.get(server).changed(pos);
        super.onRemove(state,level,pos,next,moving);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        if (level instanceof ServerLevel server) FluidNetworks.get(server).neighbourChanged(pos,from);
    }
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
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        int mask=0;
        for (Direction side : Direction.values()) if (state.getValue(CONNECTIONS.get(side))) mask |= 1 << side.ordinal();
        return SHAPES[mask];
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation) {
        BlockState next=state.setValue(SOURCE,rotation.rotate(state.getValue(SOURCE)));
        for (Direction side : Direction.values()) next=next.setValue(CONNECTIONS.get(rotation.rotate(side)),state.getValue(CONNECTIONS.get(side)));
        return next;
    }
    @Override protected BlockState mirror(BlockState state,Mirror mirror) {
        BlockState next=state.setValue(SOURCE,mirror.mirror(state.getValue(SOURCE)));
        for (Direction side : Direction.values()) next=next.setValue(CONNECTIONS.get(mirror.mirror(side)),state.getValue(CONNECTIONS.get(side)));
        return next;
    }
}
