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

/** Extensible ordinary belt block; carried items belong to the dimension, not a block entity. */
public class ConveyorBlock extends Block {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final int DEFAULT_TRAVEL_TICKS=8, DEFAULT_ITEMS_PER_TICK=16;
    private final int travelTicks,itemsPerTick;
    public ConveyorBlock(Properties properties){this(properties,DEFAULT_TRAVEL_TICKS,DEFAULT_ITEMS_PER_TICK);}
    /** Positive travel ticks per segment and an item-count limit per segment per server tick. */
    public ConveyorBlock(Properties properties,int travelTicks,int itemsPerTick) {
        super(properties.noOcclusion());
        if(travelTicks<=0||itemsPerTick<=0)throw new IllegalArgumentException("Conveyor rates must be positive");
        this.travelTicks=travelTicks;this.itemsPerTick=itemsPerTick;
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }
    public final int travelTicks(){return travelTicks;}
    public final int itemsPerTick(){return itemsPerTick;}
    public List<TransportNode> nodes(BlockPos pos,BlockState state){return List.of(new TransportNode(pos));}
    public List<BlockPos> neighborPositions(BlockPos pos,BlockState state) {
        var result=new ArrayList<BlockPos>();for(var side:Direction.values())result.add(pos.relative(side));return result;
    }
    public List<TransportNode> outputNodes(Level level,TransportNode node,BlockState state) {
        var target=node.position().relative(state.getValue(FACING));
        if(!level.isLoaded(target))return List.of();
        var next=level.getBlockState(target);
        if(next.getBlock() instanceof ConveyorBlock belt && belt.acceptsFrom(next,state.getValue(FACING).getOpposite()))
            return belt.inputNodes(target,next,state.getValue(FACING).getOpposite());
        return List.of();
    }
    /** Specialized lanes may select only the channel connected to this incoming face. */
    public List<TransportNode> inputNodes(BlockPos pos,BlockState state,Direction side){return acceptsFrom(state,side)?nodes(pos,state):List.of();}
    public TransportNode insertionNode(BlockPos pos,BlockState state,Direction side) {
        var inputs=inputNodes(pos,state,side);return inputs.isEmpty()?null:inputs.getFirst();
    }
    public boolean acceptsFrom(BlockState state,Direction side){return side==null||(side!=Direction.DOWN&&side!=state.getValue(FACING));}
    public BlockPos sourcePosition(TransportNode node,BlockState state){return node.position().relative(state.getValue(FACING).getOpposite());}
    public BlockPos destinationPosition(TransportNode node,BlockState state){return node.position().relative(state.getValue(FACING));}
    /** Relative item position, with a continuous entry-edge -> center -> exit-edge path. */
    public Vec3 itemPosition(BlockState state,Direction entry,double progress) {
        double p=Math.clamp(progress,0,1);var out=state.getValue(FACING);
        double before=Math.max(0,0.5-p),after=Math.max(0,p-0.5);
        return new Vec3(0.5+entry.getStepX()*before+out.getStepX()*after,1.08,0.5+entry.getStepZ()*before+out.getStepZ()*after);
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event,ConveyorBlock... blocks) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK,(level,pos,state,be,side)->level instanceof ServerLevel server
            ?ingress(server,pos,state,side):null,blocks);
    }
    private static net.neoforged.neoforge.items.IItemHandler ingress(ServerLevel level,BlockPos pos,BlockState state,Direction side) {
        var node=((ConveyorBlock)state.getBlock()).insertionNode(pos,state,side);
        return node==null?null:ConveyorNetworks.get(level).ingress(node,side);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return box(0,0,0,16,15,16);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving) {
        super.onPlace(state,level,pos,old,moving);
        if(level instanceof ServerLevel server && !state.equals(old))ConveyorNetworks.get(server).changed(pos);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if(level instanceof ServerLevel server && state.getBlock()!=next.getBlock())ConveyorNetworks.get(server).removed(pos);
        super.onRemove(state,level,pos,next,moving);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving) {
        if(level instanceof ServerLevel server)ConveyorNetworks.get(server).neighbourChanged(pos,from);
    }
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity) {
        if(level instanceof ServerLevel server && entity instanceof ItemEntity item && item.isAlive() && item.getY()>=pos.getY()+0.75) {
            var node=insertionNode(pos,state,Direction.UP);if(node==null)return;
            var remainder=ConveyorNetworks.get(server).offer(node,Direction.UP,item.getItem(),false);
            if(remainder.isEmpty())item.discard();else item.setItem(remainder);
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(!player.getMainHandItem().isEmpty())return InteractionResult.PASS;
        if(level instanceof ServerLevel server)ConveyorNetworks.get(server).pickup(pos,player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
