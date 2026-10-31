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

/**
 * Fluid-transfer facade over the shared transport core, one instance per server dimension. Routes directly into
 * live handlers with shared quantity/work budgets and retains unexpected terminal-extraction remainders in
 * SavedData.
 */
public final class FluidNetworks {
    /**
     * Legacy default rating retained for source compatibility; use the block rating for actual transfers.
     *
     * @deprecated Use FluidPipeBlock.DEFAULT_TRANSFER_RATE or a pipe's getTransferRate().
     */
    @Deprecated public static final int MB_PER_TICK=FluidPipeBlock.DEFAULT_TRANSFER_RATE;
    /**
     * Dimension-to-fluid-facade registry, released on level unload.
     */
    private static final Map<ServerLevel,FluidNetworks> LEVELS=Collections.synchronizedMap(new IdentityHashMap<>());
    /**
     * Gets or creates the server dimension's fluid facade.
     *
     * @param level Server dimension owning the facade and recovery data
     * @return dimension fluid manager
     */
    public static FluidNetworks get(ServerLevel level){return LEVELS.computeIfAbsent(level,FluidNetworks::new);}
    /**
     * Releases the dimension manager; derived topology is rebuilt when a new manager is needed.
     *
     * @param level Server dimension owning the facade and recovery data
     */
    public static void unload(ServerLevel level){LEVELS.remove(level);}
    /**
     * Server dimension owning the facade and recovery data.
     */
    private final ServerLevel level;
    /**
     * Shared component lifecycle and directed route manager for fluid endpoints.
     */
    private final TransportNetworks<CapabilityEndpoint<IFluidHandler>> topology;
    /**
     * Separate simulation/execution work counters for this dimension.
     */
    private final WorkBudget work=new WorkBudget(256);
    /**
     * Rotating extraction-terminal service scheduler.
     */
    private final FairScheduler terminals=new FairScheduler();
    /**
     * Durably owned unexpected terminal extraction remainders.
     */
    private final FluidRecovery recovery;
    /**
     * Reentrancy guard preventing capability cycles from recursively routing.
     */
    private boolean routing;
    /**
     * Attaches shared fluid topology and the persistent exceptional-remainder store to the server dimension.
     *
     * @param level Server dimension owning the facade and recovery data
     */
    private FluidNetworks(ServerLevel level) {
        this.level=level;topology=new TransportNetworks<>(level,new FluidTransportAdapter());
        recovery=level.getDataStorage().computeIfAbsent(FluidRecovery.factory(),"poweradvantage_fluid_recovery");
    }
    /**
     * Enqueues deferred inspection of this chunk without reading it before FULL promotion.
     *
     * @param chunk chunk instance or coordinate from the lifecycle event
     */
    public void chunkLoaded(LevelChunk chunk){topology.chunkLoaded(chunk);}
    /**
     * Queues chunk withdrawal so routing suspends across the boundary before further transfers.
     *
     * @param chunk chunk coordinate being watched or withdrawn
     */
    public void chunkUnloaded(ChunkPos chunk){topology.chunkUnloaded(chunk);}
    /**
     * Invalidates affected transport components and queues replacement topology discovery.
     *
     * @param pos block position being queried or changed
     */
    public void changed(BlockPos pos){topology.changed(pos);}
    /**
     * Forwards meaningful server neighbor changes while ignoring unchanged content notifications.
     *
     * @param pos block position being queried or changed
     * @param from neighbor position that caused the notification
     */
    public void neighbourChanged(BlockPos pos,BlockPos from){topology.neighbourChanged(pos,from);}
    /**
     * Advances shared topology, retries owned remainders and services extraction terminals fairly.
     */
    public void tick() {
        work.begin(level.getGameTime());topology.tick();retryRecovery();
        var active=new ArrayList<CapabilityEndpoint<IFluidHandler>>();
        for(var component:topology.components())active.addAll(component.sources());
        terminals.run(active,work::canExecute,this::extract);
    }
    /**
     * Creates a fill-only virtual pipe handler; null face denotes unknown injecting side.
     *
     * @param pos block position being queried or changed
     * @param face sided capability face, or null for unsided access where supported
     * @return handler routing accepted fluid directly into endpoints
     */
    public IFluidHandler ingress(BlockPos pos,Direction face){return new Ingress(pos.immutable(),face);}
    /**
     * Diagnostic sizes of a ready fluid component; all values are zero while no valid component is published.
     *
     * @param pipes Number of physical pipe channels in the ready component
     * @param vertices Number of vertices retained by graph compression
     * @param endpoints Number of discovered source/destination endpoints
     */
    public record Stats(int pipes,int vertices,int endpoints) {}
    /**
     * Reports ready component sizes without exposing its mutable implementation state.
     *
     * @param pos block position being queried or changed
     * @return component counts, or zeros while unavailable
     */
    public Stats stats(BlockPos pos) {
        var component=topology.component(new TransportNode(pos));
        return component==null?new Stats(0,0,0):new Stats(component.nodes().size(),component.graph().vertexCount(),component.endpoints().size());
    }
    /**
     * Fill-only virtual handler at a pipe face. It contains no fluid; accepted quantities are delivered directly to
     * endpoints.
     */
    private final class Ingress implements IFluidHandler {
        /**
         * Block position being queried or changed.
         */
        private final BlockPos pos;
        /**
         * Sided capability face, or null for unsided access where supported.
         */
        private final Direction face;
        /**
         * Captures a pipe position and optional injecting side for a virtual fill-only handler.
         *
         * @param pos block position being queried or changed
         * @param face sided capability face, or null for unsided access where supported
         */
        Ingress(BlockPos pos,Direction face){this.pos=pos;this.face=face;}
        /**
         * Exposes a single virtual transport slot.
         *
         * @return one
         */
        public int getTanks(){return 1;}
        /**
         * Reports no stored fluid because the pipe interface is transport-only.
         *
         * @param tank virtual tank index
         * @return empty fluid stack
         */
        public FluidStack getFluidInTank(int tank){return FluidStack.EMPTY;}
        /**
         * Advertises the local pipe rating for the valid virtual slot.
         *
         * @param tank virtual tank index
         * @return local rating in mB/tick, or zero for an invalid slot/pipe
         */
        public int getTankCapacity(int tank) {
            return tank==0&&topology.contains(new TransportNode(pos))?((FluidPipeBlock)level.getBlockState(pos).getBlock()).getTransferRate():0;
        }
        /**
         * Checks only virtual-slot identity and a nonempty offered fluid. Acceptance is determined by fill
         * simulation.
         *
         * @param tank virtual tank index
         * @param fluid nonempty fluid contents owned by recovery
         * @return true for a nonempty fluid and slot zero
         */
        public boolean isFluidValid(int tank,FluidStack fluid){return tank==0&&!fluid.isEmpty();}
        /**
         * Routes this offer from the pipe face, excluding its injecting neighbor.
         *
         * @param stack item or fluid contents offered to the operation
         * @param action simulation or execution mode for this fluid operation
         * @return accepted millibuckets
         */
        public int fill(FluidStack stack,FluidAction action){return route(new TransportNode(pos),face==null?null:pos.relative(face),stack,action);}
        /**
         * Refuses extraction because this virtual handler owns no tank contents.
         *
         * @param stack item or fluid contents offered to the operation
         * @param action simulation or execution mode for this fluid operation
         * @return empty fluid stack
         */
        public FluidStack drain(FluidStack stack,FluidAction action){return FluidStack.EMPTY;}
        /**
         * Refuses extraction because this virtual handler owns no tank contents.
         *
         * @param amount requested or accepted resource quantity in the owning engine's units
         * @param action simulation or execution mode for this fluid operation
         * @return empty fluid stack
         */
        public FluidStack drain(int amount,FluidAction action){return FluidStack.EMPTY;}
    }
    /**
     * Actual or simulated destination results awaiting allowance/cursor accounting.
     *
     * @param amount Total amount accepted by destination handlers in millibuckets
     * @param lastDestination Last accepting candidate index, or -1 if nothing accepted
     * @param destinationCount Candidate count used to wrap the destination cursor
     */
    private record Delivery(int amount, int lastDestination, int destinationCount) {}

    /**
     * Routes offered fluid under shared quantity and operation budgets, preserving reentrancy protection.
     *
     * @param ingress physical input channel for this route request
     * @param source injecting/source block position, or null when unknown
     * @param offered resource contents proposed by the source
     * @param action simulation or execution mode for this fluid operation
     * @return actually or hypothetically accepted amount in millibuckets
     */
    private int route(TransportNode ingress, BlockPos source, FluidStack offered, IFluidHandler.FluidAction action) {
        long tick = level.getGameTime();
        work.begin(tick);
        var component = topology.component(ingress);
        if (!mayRoute(component, ingress, offered, action)) return 0;
        var budget = component.budget();
        int limit = budget.allowance(tick, new TransportNetworks.Origin(ingress, source), offered.getAmount());
        if (limit <= 0) return 0;
        var candidates = component.graph().destinations(ingress);
        Delivery delivery;
        routing = true;
        try {
            delivery = transferToDestinations(component, candidates, source, offered, action, limit);
        } finally {
            routing = false;
        }
        if (action.execute()) commitDelivery(budget, delivery);
        return delivery.amount();
    }

    /**
     * Checks loaded topology, request state, reentrancy and operation availability before transfer.
     *
     * @param component provisional or published transport component
     * @param ingress physical input channel for this route request
     * @param offered resource contents proposed by the source
     * @param action simulation or execution mode for this fluid operation
     * @return true if this request may attempt routing
     */
    private boolean mayRoute(TransportNetworks.Component<CapabilityEndpoint<IFluidHandler>> component,
            TransportNode ingress, FluidStack offered, IFluidHandler.FluidAction action) {
        return topology.canRoute() && (action.execute() || work.canSimulate()) && !routing && !offered.isEmpty()
            && topology.contains(ingress) && component != null && work.canExecute();
    }

    /**
     * Visits rotated live destinations and records actual or simulated acceptance without committing quantity
     * accounting.
     *
     * @param component provisional or published transport component
     * @param candidates ready destination endpoints in cached route order
     * @param source injecting/source block position, or null when unknown
     * @param offered resource contents proposed by the source
     * @param action simulation or execution mode for this fluid operation
     * @param limit positive work-operation limit
     * @return transfer amount and last accepting destination
     */
    private Delivery transferToDestinations(TransportNetworks.Component<CapabilityEndpoint<IFluidHandler>> component,
            List<CapabilityEndpoint<IFluidHandler>> candidates, BlockPos source, FluidStack offered,
            IFluidHandler.FluidAction action, int limit) {
        var budget = component.budget();
        int sent = 0;
        int lastDestination = -1;
        for (int i = 0; i < candidates.size() && sent < limit && work.canExecute(); i++) {
            int index = budget.destinationIndex(i, candidates.size());
            var endpoint = candidates.get(index);
            if (!component.valid()) break;
            if (!destinationAvailable(endpoint, source)) continue;
            var handler = endpoint.cache().getCapability();
            if (handler == null) continue;
            if (!spendTransferWork(action)) break;
            int accepted = Math.clamp(handler.fill(offered.copyWithAmount(limit - sent), action), 0, limit - sent);
            sent += accepted;
            if (accepted > 0) lastDestination = index;
        }
        return new Delivery(sent, lastDestination, candidates.size());
    }

    /**
     * Excludes extraction ports, the injecting source block and unloaded endpoints.
     *
     * @param endpoint sided resource endpoint
     * @param source injecting/source block position, or null when unknown
     * @return true if this endpoint may be queried for delivery
     */
    private boolean destinationAvailable(CapabilityEndpoint<IFluidHandler> endpoint, BlockPos source) {
        return !endpoint.source() && !endpoint.position().equals(source) && topology.available(endpoint.position());
    }

    /**
     * Consumes the operation counter appropriate to simulation or execution.
     *
     * @param action simulation or execution mode for this fluid operation
     * @return true if work remains available
     */
    private boolean spendTransferWork(IFluidHandler.FluidAction action) {
        return action.execute() ? work.tryExecution() : work.trySimulation();
    }

    /**
     * Charges executed quantity and advances fairness past the last accepting endpoint.
     *
     * @param budget maximum work steps available to this pass
     * @param delivery accepted quantity and destination cursor result awaiting accounting
     */
    private void commitDelivery(FairTransferBudget<TransportNetworks.Origin> budget, Delivery delivery) {
        budget.consume(delivery.amount());
        if (delivery.lastDestination() >= 0)
            budget.servedDestination(delivery.lastDestination(), delivery.destinationCount());
    }

    /**
     * Simulates source extraction and destination acceptance before executing a terminal-owned transfer.
     *
     * @param endpoint sided resource endpoint
     */
    private void extract(CapabilityEndpoint<IFluidHandler> endpoint) {
        if (!topology.available(endpoint.position()) || !topology.contains(endpoint.node())) return;
        var component = topology.component(endpoint.node());
        if (component == null || component.budget().remaining(level.getGameTime()) <= 0) return;
        var source = endpoint.cache().getCapability();
        if (source == null || !work.tryExecution()) return;
        var offered = source.drain(component.transferRate(), IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) return;
        int accepted = route(endpoint.node(), endpoint.position(), offered, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0 || !component.valid() || !work.canExecute()) return;
        executeExtraction(endpoint, source, offered, accepted);
    }

    /**
     * Extracts the agreed amount, routes the actual result and saves any unexpected undelivered remainder.
     *
     * @param endpoint sided resource endpoint
     * @param source resource handler supplying extracted contents
     * @param offered resource contents proposed by the source
     * @param accepted quantity agreed during admission simulation
     */
    private void executeExtraction(CapabilityEndpoint<IFluidHandler> endpoint, IFluidHandler source,
            FluidStack offered, int accepted) {
        var extracted = source.drain(offered.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (extracted.isEmpty()) return;
        int sent = route(endpoint.node(), endpoint.position(), extracted, IFluidHandler.FluidAction.EXECUTE);
        if (sent < extracted.getAmount()) {
            var remainder = extracted.copyWithAmount(extracted.getAmount() - sent);
            recovery.add(endpoint.node().position(), endpoint.position(), endpoint.face(), remainder);
        }
    }

    /**
     * Retries retained remainders through their original ingress or back into their loaded source.
     */
    private void retryRecovery() {
        for (int i = 0; i < Math.min(8, recovery.entries.size()) && work.canExecute(); i++) {
            var entry = recovery.entries.get(i);
            int sent = route(new TransportNode(entry.pipe()), entry.source(), entry.fluid(), IFluidHandler.FluidAction.EXECUTE);
            if (sent == 0 && topology.available(entry.source())) sent = returnToSource(entry);
            if (sent > 0 && updateRecovery(i, entry, sent)) i--;
        }
    }

    /**
     * Attempts a budgeted refund into the original sided source handler.
     *
     * @param entry saved transfer remainder being retried or updated
     * @return amount accepted by the source, or zero
     */
    private int returnToSource(FluidRecovery.Entry entry) {
        var source = level.getCapability(Capabilities.FluidHandler.BLOCK, entry.source(), entry.face());
        if (source == null || !work.tryExecution()) return 0;
        return source.fill(entry.fluid(), IFluidHandler.FluidAction.EXECUTE);
    }

    /**
     * Shrinks a retained remainder after accepted delivery/refund and removes an empty entry.
     *
     * @param index entry or slot index
     * @param entry saved transfer remainder being retried or updated
     * @param sent quantity actually accepted by delivery or refund
     * @return true if the indexed entry was removed
     */
    private boolean updateRecovery(int index, FluidRecovery.Entry entry, int sent) {
        entry.fluid().shrink(Math.min(sent, entry.fluid().getAmount()));
        recovery.setDirty();
        if (!entry.fluid().isEmpty()) return false;
        recovery.entries.remove(index);
        return true;
    }
}
