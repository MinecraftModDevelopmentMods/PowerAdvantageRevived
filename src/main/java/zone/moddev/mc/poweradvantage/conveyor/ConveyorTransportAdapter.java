package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import zone.moddev.mc.poweradvantage.blocks.ConveyorBlock;
import zone.moddev.mc.poweradvantage.network.transport.*;

/**
 * Conveyor-specific directed connections and item-handler endpoint discovery plugged into the shared topology
 * manager.
 */
public class ConveyorTransportAdapter implements TransportAdapter<CapabilityEndpoint<IItemHandler>> {
    /**
     * Creates the standard directed conveyor discovery policy.
     */
    public ConveyorTransportAdapter() {}

    /**
     * Recognizes all ConveyorBlock subclasses.
     *
     * @param state current block state
     * @return true for a conveyor block
     */
    public boolean matches(BlockState state){return state.getBlock() instanceof ConveyorBlock;}
    /**
     * Delegates physical channel selection to the conveyor block.
     *
     * @param pos block position being queried or changed
     * @param state current block state
     * @return stable channels exposed by that state
     */
    public List<TransportNode> nodes(BlockPos pos,BlockState state){return ((ConveyorBlock)state.getBlock()).nodes(pos,state);}
    /**
     * Reads whether the endpoint supplies items to the belt.
     *
     * @param endpoint sided resource endpoint
     * @return true for a source inventory port
     */
    public boolean isSource(CapabilityEndpoint<IItemHandler> endpoint){return endpoint.source();}
    /**
     * Includes the surrounding block cube for current and near-diagonal connection dependencies.
     *
     * @param pos block position being queried or changed
     * @return positions to invalidate and reseed
     */
    public List<BlockPos> affectedPositions(BlockPos pos) {
        var result=new ArrayList<BlockPos>();
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)result.add(pos.offset(x,y,z));
        return result;
    }
    /**
     * Discovers legal directed belt outputs, reciprocal weak neighbors and source/destination item capabilities.
     *
     * @param context placement, drawing or loaded-world inspection context
     * @param node physical position/channel identity
     * @return conveyor channel discovery result
     */
    public NodeData<CapabilityEndpoint<IItemHandler>> inspect(Context context,TransportNode node) {
        var level=context.level();var state=level.getBlockState(node.position());var belt=(ConveyorBlock)state.getBlock();
        var outgoing=belt.outputNodes(level,node,state).stream().filter(n->context.available(n.position())).toList();
        var adjacent=new LinkedHashSet<TransportNode>(outgoing);var endpoints=new ArrayList<CapabilityEndpoint<IItemHandler>>();
        var source=belt.sourcePosition(node,state);var target=belt.destinationPosition(node,state);
        for(var pos:belt.neighborPositions(node.position(),state)) {
            if(!context.available(pos))continue;
            var other=level.getBlockState(pos);
            if(other.getBlock() instanceof ConveyorBlock otherBelt) {
                for(var otherNode:otherBelt.nodes(pos,other))if(otherBelt.outputNodes(level,otherNode,other).contains(node))adjacent.add(otherNode);
            } else if(!other.isAir()&&(pos.equals(source)||pos.equals(target))) {
                var side=pos.equals(source)?state.getValue(ConveyorBlock.FACING):state.getValue(ConveyorBlock.FACING).getOpposite();
                var cache=context.watch(Capabilities.ItemHandler.BLOCK,pos,side);
                if(cache.getCapability()!=null)endpoints.add(new CapabilityEndpoint<>(node,pos,side,pos.equals(source),cache));
            }
        }
        return new NodeData<>(List.copyOf(adjacent),outgoing,endpoints,belt.itemsPerTick(),false);
    }
}
