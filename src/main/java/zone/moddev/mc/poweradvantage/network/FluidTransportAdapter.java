package zone.moddev.mc.poweradvantage.network;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock;
import zone.moddev.mc.poweradvantage.network.transport.*;

/**
 * Fluid-specific pipe recognition, bidirectional connections and sided capability discovery. Explicit source
 * positions are excluded from destination routing, including aliases reached from another face.
 */
final class FluidTransportAdapter implements TransportAdapter<CapabilityEndpoint<IFluidHandler>> {
    /**
     * Creates the fluid-pipe discovery and static destination policy.
     */
    FluidTransportAdapter() {}

    /**
     * Recognizes enabled FluidPipeBlock states, including open valves.
     *
     * @param state current block state
     * @return true for a fluid-pipe or terminal block
     */
    public boolean matches(BlockState state){return state.getBlock() instanceof FluidPipeBlock pipe && pipe.isTransportEnabled(state);}
    /**
     * Reads the endpoint's extraction-port flag.
     *
     * @param endpoint sided resource endpoint
     * @return true for an extraction endpoint
     */
    public boolean isSource(CapabilityEndpoint<IFluidHandler> endpoint){return endpoint.source();}
    /**
     * Excludes all explicit source block positions from delivery, including their other faces.
     *
     * @param sources adapter-classified extraction endpoints
     * @return static fluid destination predicate
     */
    public Predicate<CapabilityEndpoint<IFluidHandler>> destinationPolicy(List<CapabilityEndpoint<IFluidHandler>> sources) {
        var sourcePositions=new HashSet<BlockPos>();for(var source:sources)sourcePositions.add(source.position());
        return endpoint->!endpoint.source()&&!sourcePositions.contains(endpoint.position());
    }
    /**
     * Discovers bidirectional pipe links and watches occupied sided fluid boundaries.
     *
     * @param context placement, drawing or loaded-world inspection context
     * @param node physical position/channel identity
     * @return fluid channel discovery result
     */
    public NodeData<CapabilityEndpoint<IFluidHandler>> inspect(Context context,TransportNode node) {
        var level=context.level();var pos=node.position();var state=level.getBlockState(pos);
        var block=(FluidPipeBlock)state.getBlock();
        var neighbors=new ArrayList<TransportNode>();var endpoints=new ArrayList<CapabilityEndpoint<IFluidHandler>>();
        for(Direction side:Direction.values()) {
            var other=pos.relative(side);if(!context.available(other))continue;
            var otherState=level.getBlockState(other);
            if(matches(otherState))neighbors.add(new TransportNode(other));
            else if(!(otherState.getBlock() instanceof FluidPipeBlock) && !otherState.isAir()) {
                var cache=context.watch(Capabilities.FluidHandler.BLOCK,other,side.getOpposite());
                if(cache.getCapability()!=null)endpoints.add(new CapabilityEndpoint<>(node,other,side.getOpposite(),
                    block.isTerminal()&&side==state.getValue(FluidPipeBlock.SOURCE),cache));
            }
        }
        return new NodeData<>(neighbors,neighbors,endpoints,block.getTransferRate(),block.isTerminal());
    }
    /**
     * Refreshes six visual connection flags after discovery.
     *
     * @param level owning world; server-only engines require its server thread
     * @param node physical position/channel identity
     */
    public void refresh(ServerLevel level,TransportNode node){FluidPipeBlock.refresh(level,node.position());}
}
