package zone.moddev.mc.poweradvantage.network;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock;
import zone.moddev.mc.poweradvantage.network.transport.*;

/** Fluid-only transfer facade over the shared transport topology and scheduling core. */
public final class FluidNetworks {
    /** @deprecated Use FluidPipeBlock.DEFAULT_TRANSFER_RATE or a pipe's getTransferRate(). */
    @Deprecated public static final int MB_PER_TICK=FluidPipeBlock.DEFAULT_TRANSFER_RATE;
    private static final Map<ServerLevel,FluidNetworks> LEVELS=Collections.synchronizedMap(new IdentityHashMap<>());
    public static FluidNetworks get(ServerLevel level){return LEVELS.computeIfAbsent(level,FluidNetworks::new);}
    public static void unload(ServerLevel level){LEVELS.remove(level);}
    private final ServerLevel level;
    private final TransportNetworks<CapabilityEndpoint<IFluidHandler>> topology;
    private final WorkBudget work=new WorkBudget(256);
    private final FairScheduler terminals=new FairScheduler();
    private final FluidRecovery recovery;
    private boolean routing;
    private FluidNetworks(ServerLevel level) {
        this.level=level;topology=new TransportNetworks<>(level,new FluidTransportAdapter());
        recovery=level.getDataStorage().computeIfAbsent(FluidRecovery.factory(),"poweradvantage_fluid_recovery");
    }
    public void chunkLoaded(LevelChunk chunk){topology.chunkLoaded(chunk);}
    public void chunkUnloaded(ChunkPos chunk){topology.chunkUnloaded(chunk);}
    public void changed(BlockPos pos){topology.changed(pos);}
    public void neighbourChanged(BlockPos pos,BlockPos from){topology.neighbourChanged(pos,from);}
    public void tick() {
        work.begin(level.getGameTime());topology.tick();retryRecovery();
        var active=new ArrayList<CapabilityEndpoint<IFluidHandler>>();
        for(var component:topology.components())active.addAll(component.sources());
        terminals.run(active,work::canExecute,this::extract);
    }
    public IFluidHandler ingress(BlockPos pos,Direction face){return new Ingress(pos.immutable(),face);}
    public record Stats(int pipes,int vertices,int endpoints) {}
    public Stats stats(BlockPos pos) {
        var component=topology.component(new TransportNode(pos));
        return component==null?new Stats(0,0,0):new Stats(component.nodes().size(),component.graph().vertexCount(),component.endpoints().size());
    }
    private final class Ingress implements IFluidHandler {
        private final BlockPos pos;private final Direction face;
        Ingress(BlockPos pos,Direction face){this.pos=pos;this.face=face;}
        public int getTanks(){return 1;}
        public FluidStack getFluidInTank(int tank){return FluidStack.EMPTY;}
        public int getTankCapacity(int tank) {
            return tank==0&&topology.contains(new TransportNode(pos))?((FluidPipeBlock)level.getBlockState(pos).getBlock()).getTransferRate():0;
        }
        public boolean isFluidValid(int tank,FluidStack fluid){return tank==0&&!fluid.isEmpty();}
        public int fill(FluidStack stack,FluidAction action){return route(new TransportNode(pos),face==null?null:pos.relative(face),stack,action);}
        public FluidStack drain(FluidStack stack,FluidAction action){return FluidStack.EMPTY;}
        public FluidStack drain(int amount,FluidAction action){return FluidStack.EMPTY;}
    }
    private int route(TransportNode ingress,BlockPos source,FluidStack offered,IFluidHandler.FluidAction action) {
        long tick=level.getGameTime();work.begin(tick);
        var component=topology.component(ingress);
        if(!topology.canRoute()||(!action.execute()&&!work.canSimulate())||routing||offered.isEmpty()
                ||!topology.contains(ingress)||component==null||!work.canExecute())return 0;
        var budget=component.budget();
        int limit=budget.allowance(tick,new TransportNetworks.Origin(ingress,source),offered.getAmount());
        if(limit<=0)return 0;
        var candidates=component.graph().destinations(ingress);
        int sent=0,lastDestination=-1;routing=true;
        try {
            for(int i=0;i<candidates.size()&&sent<limit&&work.canExecute();i++) {
                int index=budget.destinationIndex(i,candidates.size());var endpoint=candidates.get(index);
                if(!component.valid())break;
                if(endpoint.source()||endpoint.position().equals(source)||!topology.available(endpoint.position()))continue;
                var handler=endpoint.cache().getCapability();if(handler==null)continue;
                if(action.execute()){if(!work.tryExecution())break;}else if(!work.trySimulation())break;
                int accepted=Math.clamp(handler.fill(offered.copyWithAmount(limit-sent),action),0,limit-sent);
                sent+=accepted;if(accepted>0)lastDestination=index;
            }
        } finally {routing=false;}
        if(action.execute()) {
            budget.consume(sent);
            if(lastDestination>=0)budget.servedDestination(lastDestination,candidates.size());
        }
        return sent;
    }
    private void extract(CapabilityEndpoint<IFluidHandler> endpoint) {
        if(!topology.available(endpoint.position())||!topology.contains(endpoint.node()))return;
        var component=topology.component(endpoint.node());
        if(component==null||component.budget().remaining(level.getGameTime())<=0)return;
        var source=endpoint.cache().getCapability();if(source==null||!work.tryExecution())return;
        var offered=source.drain(component.transferRate(),IFluidHandler.FluidAction.SIMULATE);
        if(offered.isEmpty())return;
        int accepted=route(endpoint.node(),endpoint.position(),offered,IFluidHandler.FluidAction.SIMULATE);
        if(accepted<=0||!component.valid()||!work.canExecute())return;
        var extracted=source.drain(offered.copyWithAmount(accepted),IFluidHandler.FluidAction.EXECUTE);
        if(extracted.isEmpty())return;
        int sent=route(endpoint.node(),endpoint.position(),extracted,IFluidHandler.FluidAction.EXECUTE);
        if(sent<extracted.getAmount())recovery.add(endpoint.node().position(),endpoint.position(),endpoint.face(),extracted.copyWithAmount(extracted.getAmount()-sent));
    }
    private void retryRecovery() {
        for(int i=0;i<Math.min(8,recovery.entries.size())&&work.canExecute();i++) {
            var entry=recovery.entries.get(i);
            int sent=route(new TransportNode(entry.pipe()),entry.source(),entry.fluid(),IFluidHandler.FluidAction.EXECUTE);
            if(sent==0&&topology.available(entry.source())) {
                var source=level.getCapability(Capabilities.FluidHandler.BLOCK,entry.source(),entry.face());
                if(source!=null&&work.tryExecution())sent=source.fill(entry.fluid(),IFluidHandler.FluidAction.EXECUTE);
            }
            if(sent>0) {
                entry.fluid().shrink(Math.min(sent,entry.fluid().getAmount()));recovery.setDirty();
                if(entry.fluid().isEmpty()){recovery.entries.remove(i);i--;}
            }
        }
    }
}
