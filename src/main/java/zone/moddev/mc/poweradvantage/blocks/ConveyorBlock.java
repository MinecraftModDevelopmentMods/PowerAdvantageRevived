package zone.moddev.mc.poweradvantage.blocks;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.capabilities.*;
import zone.moddev.mc.poweradvantage.conveyor.ConveyorNetworks;
import zone.moddev.mc.poweradvantage.network.transport.TransportNode;

/**
 * Extensible horizontal conveyor with dimension-owned cargo and no block entity. Configure positive segment travel
 * ticks and item-count limits when registering a block; specialize channel and connection hooks for future belt
 * forms.
 */
public class ConveyorBlock extends Block {
    /**
     * Horizontal output direction persisted in ordinary block state.
     */
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    /**
     * Default eight processed ticks required per belt segment.
     * Default per-segment ceiling of sixteen arriving/departing items per tick.
     */
    public static final int DEFAULT_TRAVEL_TICKS=8, DEFAULT_ITEMS_PER_TICK=16;
    /**
     * Immutable positive movement duration for this block type.
     * Immutable positive per-segment item-count ceiling.
     */
    private final int travelTicks,itemsPerTick;
    /**
     * Creates a conveyor block with no block entity, using defaults or the supplied positive travel/item ratings.
     *
     * @param properties fresh Minecraft block properties
     */
    public ConveyorBlock(Properties properties){this(properties,DEFAULT_TRAVEL_TICKS,DEFAULT_ITEMS_PER_TICK);}
    /**
     * Positive travel ticks per segment and an item-count limit per segment per server tick.
     *
     * @param properties fresh Minecraft block properties
     * @param travelTicks Immutable positive movement duration for this block type
     * @param itemsPerTick Immutable positive per-segment item-count ceiling
     * @throws IllegalArgumentException if either supplied travel duration or item-count rating is not positive
     */
    public ConveyorBlock(Properties properties,int travelTicks,int itemsPerTick) {
        super(properties.noOcclusion());
        if(travelTicks<=0||itemsPerTick<=0)throw new IllegalArgumentException("Conveyor rates must be positive");
        this.travelTicks=travelTicks;this.itemsPerTick=itemsPerTick;
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }
    /**
     * Returns the immutable segment travel duration.
     *
     * @return processed server ticks per segment
     */
    public final int travelTicks(){return travelTicks;}
    /**
     * Returns the immutable per-segment item-count ceiling.
     *
     * @return maximum arriving/departing items per tick
     */
    public final int itemsPerTick(){return itemsPerTick;}
    /**
     * Exposes stable channel identities for this block state.
     *
     * @param pos block position being queried or changed
     * @param state current block state
     * @return single main channel by default
     */
    public List<TransportNode> nodes(BlockPos pos,BlockState state){return List.of(new TransportNode(pos));}
    /**
     * Lists positions inspected for reciprocal belt links and inventory ports.
     *
     * @param pos block position being queried or changed
     * @param state current block state
     * @return six neighboring positions by default
     */
    public List<BlockPos> neighborPositions(BlockPos pos,BlockState state) {
        var result=new ArrayList<BlockPos>();for(var side:Direction.values())result.add(pos.relative(side));return result;
    }
    /**
     * Finds currently loaded, accepted downstream channels in the output direction.
     *
     * @param level owning world; server-only engines require its server thread
     * @param node physical position/channel identity
     * @param state current block state
     * @return legal directed output channels
     */
    public List<TransportNode> outputNodes(Level level,TransportNode node,BlockState state) {
        var target=node.position().relative(state.getValue(FACING));
        if(!level.isLoaded(target))return List.of();
        var next=level.getBlockState(target);
        if(next.getBlock() instanceof ConveyorBlock belt && belt.acceptsFrom(next,state.getValue(FACING).getOpposite()))
            return belt.inputNodes(target,next,state.getValue(FACING).getOpposite());
        return List.of();
    }
    /**
     * Specialized lanes may select only the channel connected to this incoming face.
     *
     * @param pos block position being queried or changed
     * @param state current block state
     * @param side queried or incoming face; null denotes unsided access where supported
     * @return eligible channels, or empty if the face is refused
     */
    public List<TransportNode> inputNodes(BlockPos pos,BlockState state,Direction side){return acceptsFrom(state,side)?nodes(pos,state):List.of();}
    /**
     * Selects the default automation/capture channel for an incoming face.
     *
     * @param pos block position being queried or changed
     * @param state current block state
     * @param side queried or incoming face; null denotes unsided access where supported
     * @return first eligible input channel, or null
     */
    public TransportNode insertionNode(BlockPos pos,BlockState state,Direction side) {
        var inputs=inputNodes(pos,state,side);return inputs.isEmpty()?null:inputs.getFirst();
    }
    /**
     * Accepts back, side, top or unsided input while refusing the output face and bottom.
     *
     * @param state current block state
     * @param side queried or incoming face; null denotes unsided access where supported
     * @return true if the incoming face is allowed
     */
    public boolean acceptsFrom(BlockState state,Direction side){return side==null||(side!=Direction.DOWN&&side!=state.getValue(FACING));}
    /**
     * Locates the inventory directly behind this segment.
     *
     * @param node physical position/channel identity
     * @param state current block state
     * @return source inventory position
     */
    public BlockPos sourcePosition(TransportNode node,BlockState state){return node.position().relative(state.getValue(FACING).getOpposite());}
    /**
     * Locates the inventory directly in front of this segment.
     *
     * @param node physical position/channel identity
     * @param state current block state
     * @return destination inventory position
     */
    public BlockPos destinationPosition(TransportNode node,BlockState state){return node.position().relative(state.getValue(FACING));}
    /**
     * Relative item position, with a continuous entry-edge -> center -> exit-edge path.
     *
     * @param state current block state
     * @param entry face from which the record entered its segment
     * @param progress processed ticks within the current physical segment
     * @return relative item-model position above the belt surface
     */
    public Vec3 itemPosition(BlockState state,Direction entry,double progress) {
        double p=Math.clamp(progress,0,1);var out=state.getValue(FACING);
        double before=Math.max(0,0.5-p),after=Math.max(0,p-0.5);
        return new Vec3(0.5+entry.getStepX()*before+out.getStepX()*after,1.08,0.5+entry.getStepZ()*before+out.getStepZ()*after);
    }
    /**
     * Registers insertion-only item capabilities for consuming mods' conveyor blocks.
     *
     * @param event NeoForge registration or lifecycle event
     * @param blocks registered block instances owned by the consuming mod
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event,ConveyorBlock... blocks) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK,(level,pos,state,be,side)->level instanceof ServerLevel server
            ?ingress(server,pos,state,side):null,blocks);
    }
    /**
     * Resolves a side-selected automation channel for the current block state.
     *
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param state current block state
     * @param side queried or incoming face; null denotes unsided access where supported
     * @return insertion-only item handler, or null when the face has no input
     */
    private static net.neoforged.neoforge.items.IItemHandler ingress(ServerLevel level,BlockPos pos,BlockState state,Direction side) {
        var node=((ConveyorBlock)state.getBlock()).insertionNode(pos,state,side);
        return node==null?null:ConveyorNetworks.get(level).ingress(node,side);
    }
    /**
     * Declares the block-state properties used for placement, connections and rendering.
     *
     * @param builder block-state definition builder
     */
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    /**
     * Creates the initial state from player placement context.
     *
     * @param context player placement and clicked-face context
     * @return initial placed block state
     */
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection());}
    /**
     * Transforms directional properties under a structure rotation.
     *
     * @param state current block state
     * @param rotation structure rotation to apply
     * @return rotated block state
     */
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    /**
     * Transforms directional properties under a structure mirror.
     *
     * @param state current block state
     * @param mirror structure mirror to apply
     * @return mirrored block state
     */
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    /**
     * Returns the collision/selection geometry for the current state.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param context entity collision or selection query context
     * @return state-dependent voxel shape
     */
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return box(0,0,0,16,15,16);}
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
        if(level instanceof ServerLevel server && !state.equals(old))ConveyorNetworks.get(server).changed(pos);
    }
    /**
     * Withdraws removed belt topology and releases unreserved carried contents exactly once before normal removal.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param next replacement block state
     * @param moving whether the block transition is part of a moving-block operation
     */
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(level instanceof ServerLevel server && state.getBlock()!=next.getBlock())ConveyorNetworks.get(server).removed(pos);
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
        if(level instanceof ServerLevel server)ConveyorNetworks.get(server).neighbourChanged(pos,from);
    }
    /**
     * Captures accepted top-landing ItemEntity contents and leaves any remainder in the world entity.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param entity entity intersecting the block
     */
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity) {
        if(level instanceof ServerLevel server && entity instanceof ItemEntity item && item.isAlive() && item.getY()>=pos.getY()+0.75) {
            var node=insertionNode(pos,state,Direction.UP);if(node==null)return;
            var remainder=ConveyorNetworks.get(server).offer(node,Direction.UP,item.getItem(),false);
            if(remainder.isEmpty())item.discard();else item.setItem(remainder);
        }
    }
    /**
     * Collects carried cargo only with an empty main hand; held items retain their normal interaction.
     *
     * @param state current block state
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param player interacting or watching player
     * @param hit block interaction hit result
     * @return pass for a held item, otherwise a sided handled result
     */
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!player.getMainHandItem().isEmpty())return InteractionResult.PASS;
        if(level instanceof ServerLevel server)ConveyorNetworks.get(server).pickup(pos,player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
