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

/** Pipe geometry/capability policy plugged into the resource-independent transport lifecycle. */
final class FluidTransportAdapter implements TransportAdapter<CapabilityEndpoint<IFluidHandler>> {
    public boolean matches(BlockState state){return state.getBlock() instanceof FluidPipeBlock;}
    public boolean isSource(CapabilityEndpoint<IFluidHandler> endpoint){return endpoint.source();}
    public Predicate<CapabilityEndpoint<IFluidHandler>> destinationPolicy(List<CapabilityEndpoint<IFluidHandler>> sources) {
        var sourcePositions=new HashSet<BlockPos>();for(var source:sources)sourcePositions.add(source.position());
        return endpoint->!endpoint.source()&&!sourcePositions.contains(endpoint.position());
    }
    public NodeData<CapabilityEndpoint<IFluidHandler>> inspect(Context context,TransportNode node) {
        var level=context.level();var pos=node.position();var state=level.getBlockState(pos);
        var block=(FluidPipeBlock)state.getBlock();
        var neighbors=new ArrayList<TransportNode>();var endpoints=new ArrayList<CapabilityEndpoint<IFluidHandler>>();
        for(Direction side:Direction.values()) {
            var other=pos.relative(side);if(!context.available(other))continue;
            var otherState=level.getBlockState(other);
            if(matches(otherState))neighbors.add(new TransportNode(other));
            else if(!otherState.isAir()) {
                var cache=context.watch(Capabilities.FluidHandler.BLOCK,other,side.getOpposite());
                if(cache.getCapability()!=null)endpoints.add(new CapabilityEndpoint<>(node,other,side.getOpposite(),
                    block.isTerminal()&&side==state.getValue(FluidPipeBlock.SOURCE),cache));
            }
        }
        return new NodeData<>(neighbors,neighbors,endpoints,block.getTransferRate(),block.isTerminal());
    }
    public void refresh(ServerLevel level,TransportNode node){FluidPipeBlock.refresh(level,node.position());}
}
