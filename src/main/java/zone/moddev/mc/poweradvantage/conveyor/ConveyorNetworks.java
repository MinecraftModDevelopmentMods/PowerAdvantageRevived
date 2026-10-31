package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.items.IItemHandler;
import zone.moddev.mc.poweradvantage.blocks.ConveyorBlock;
import zone.moddev.mc.poweradvantage.network.transport.*;

/**
 * Server-thread physical conveyor engine, one instance per dimension. Owns movement, admission, extraction and
 * pickup while the shared transport core owns topology. Cargo persists independently of chunk loading; one
 * processed update cannot traverse multiple segments.
 */
public final class ConveyorNetworks {
    /**
     * Dimension-to-conveyor-engine registry, released on level unload.
     */
    private static final Map<ServerLevel,ConveyorNetworks> LEVELS=Collections.synchronizedMap(new IdentityHashMap<>());
    /**
     * Gets or creates the server dimension's physical conveyor engine.
     *
     * @param level Owning server dimension
     * @return dimension conveyor manager
     */
    public static ConveyorNetworks get(ServerLevel level){return LEVELS.computeIfAbsent(level,ConveyorNetworks::new);}
    /**
     * Releases derived runtime state; saved cargo remains in dimension SavedData.
     *
     * @param level Owning server dimension
     */
    public static void unload(ServerLevel level){LEVELS.remove(level);}
    /**
     * Global work budgets selected before managers are constructed.
     */
    private static ConveyorLimits configured=ConveyorLimits.DEFAULT;
    /**
     * Configure during mod setup, before any dimension creates a conveyor manager.
     *
     * @param limits Work budgets captured for this manager lifetime
     * @throws IllegalStateException if any dimension already has a conveyor manager
     */
    public static void configureLimits(ConveyorLimits limits) {
        synchronized(LEVELS) {
            if(!LEVELS.isEmpty())throw new IllegalStateException("Configure conveyor work budgets before world creation");
            configured=Objects.requireNonNull(limits);
        }
    }
    /**
     * Owning server dimension.
     */
    private final ServerLevel level;
    /**
     * Work budgets captured for this manager lifetime.
     */
    private final ConveyorLimits limits;
    /**
     * Shared directed component manager for conveyor channels and inventory ports.
     */
    private final TransportNetworks<CapabilityEndpoint<IItemHandler>> topology;
    /**
     * Saved authoritative cargo ownership and spatial indexes.
     */
    final ConveyorStore store;
    /**
     * Rotating queue of active cargo UUIDs.
     */
    private final ArrayDeque<UUID> scheduled=new ArrayDeque<>();
    /**
     * UUIDs already present in the active queue.
     */
    private final Set<UUID> queued=new HashSet<>();
    /**
     * Deferred chunk notifications whose suspended records need reactivation.
     */
    private final ConcurrentLinkedQueue<LevelChunk> waking=new ConcurrentLinkedQueue<>();
    /**
     * Segments protected against reentrant insertion, pickup or removal.
     * Segments freed this tick that must wait until a later tick for reuse.
     */
    private final Set<TransportNode> reserved=new HashSet<>(),vacated=new HashSet<>();
    /**
     * Per-segment arriving/departing item counts charged this tick.
     */
    private final Map<TransportNode,Integer> used=new HashMap<>();
    /**
     * Per-tick simulated and executed inventory operation counters.
     */
    private final WorkBudget operations;
    /**
     * Rotating inventory-extraction endpoint scheduler.
     */
    private final FairScheduler sources=new FairScheduler();
    /**
     * Bounded LRU table retaining next extraction slot per stable source identity.
     */
    private final Map<SourceKey,Integer> slots=new LinkedHashMap<>(16,0.75f,true) {
        /**
         * Enforces the bounded least-recently-used cache or cursor table.
         *
         * @param entry eldest cached entry being considered for eviction
         * @return true when this eldest entry exceeds the configured bound
         */
        @Override protected boolean removeEldestEntry(Map.Entry<SourceKey,Integer> entry){return size()>2048;}
    };
    /**
     * Tick owning quantity and vacancy accounting.
     * Last world tick processed, preventing repeated movement calls within one tick.
     */
    private long tick=Long.MIN_VALUE,lastTick=Long.MIN_VALUE;
    /**
     * Guard against reentrant execution of the whole engine.
     */
    private boolean ticking;
    /**
     * Chunk-watcher snapshot scheduler.
     */
    private final ConveyorSync sync;
    /**
     * Stable identity for an extraction port across topology rebuilds, used to rotate inventory slots.
     *
     * @param node Conveyor channel admitting extracted items
     * @param position Source inventory block position
     * @param face Source inventory capability face
     */
    private record SourceKey(TransportNode node,BlockPos position,Direction face) {}
    /**
     * Detached diagnostic view of a carried stack. The stack is copied from authoritative storage; mutating it does
     * not update the conveyor.
     *
     * @param id Stable cargo record UUID
     * @param node Channel currently owning the cargo
     * @param stack Detached copy of carried contents and components
     * @param entry Incoming face at the current segment
     * @param progress Processed travel ticks within the current segment
     */
    public record Item(UUID id,TransportNode node,ItemStack stack,Direction entry,int progress) {}
    /**
     * Attaches the shared topology manager, durable cargo store, active-record queue and client snapshot scheduler
     * to this dimension.
     *
     * @param level Owning server dimension
     */
    private ConveyorNetworks(ServerLevel level) {
        this.level=level;limits=configured;operations=new WorkBudget(limits.operationsPerTick());
        topology=new TransportNetworks<>(level,new ConveyorTransportAdapter());
        store=level.getDataStorage().computeIfAbsent(ConveyorStore.factory(),"poweradvantage_conveyors");
        for(var item:store.records.values())if(level.isLoaded(item.node.position()))schedule(item.id);sync=new ConveyorSync(level,this,limits.snapshotPacketsPerTick());
    }
    /**
     * Copies cargo into detached diagnostic views; this does not expose authoritative mutable stacks.
     *
     * @return current diagnostic cargo snapshots
     */
    public List<Item> items(){return store.records.values().stream().map(i->new Item(i.id,i.node,i.stack.copy(),i.entry,i.progress)).toList();}
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
     * Enqueues deferred inspection of this chunk without reading it before FULL promotion.
     *
     * @param chunk chunk instance or coordinate from the lifecycle event
     */
    public void chunkLoaded(LevelChunk chunk){topology.chunkLoaded(chunk);waking.add(chunk);}
    /**
     * Queues chunk withdrawal so routing suspends across the boundary before further transfers.
     *
     * @param pos chunk coordinate being watched or withdrawn
     */
    public void chunkUnloaded(ChunkPos pos){topology.chunkUnloaded(pos);}
    /**
     * Schedules initial cargo state for a player after native chunk transmission.
     *
     * @param player interacting or watching player
     * @param pos chunk coordinate being watched or withdrawn
     */
    public void watch(ServerPlayer player,ChunkPos pos){sync.watch(player,pos);}
    /**
     * Removes a player/chunk subscription and pending cargo snapshot job.
     *
     * @param player interacting or watching player
     * @param pos chunk coordinate being watched or withdrawn
     */
    public void unwatch(ServerPlayer player,ChunkPos pos){sync.unwatch(player,pos);}
    /**
     * Resets per-tick operation, quantity and vacancy accounting when game time changes.
     */
    private void begin() {
        long now=level.getGameTime();operations.begin(now);
        if(now!=tick){tick=now;used.clear();vacated.clear();}
    }
    /**
     * Finds the loaded block exposing this channel without loading chunks.
     *
     * @param node physical position/channel identity
     * @return recognized conveyor block, or null
     */
    private ConveyorBlock belt(TransportNode node) {
        return topology.contains(node)?(ConveyorBlock)level.getBlockState(node.position()).getBlock():null;
    }
    /**
     * Calculates unused per-channel item-count allowance for the current tick.
     *
     * @param node physical position/channel identity
     * @param belt conveyor block supplying the immutable local item-count rating
     * @return nonnegative remaining item count
     */
    private int remaining(TransportNode node,ConveyorBlock belt){return Math.max(0,belt.itemsPerTick()-used.getOrDefault(node,0));}
    /**
     * Charges arriving or departing items against the current channel allowance.
     *
     * @param node physical position/channel identity
     * @param amount requested or accepted resource quantity in the owning engine's units
     */
    private void charge(TransportNode node,int amount){used.merge(node,amount,Integer::sum);}
    /**
     * Queues a cargo UUID at most once for active processing.
     *
     * @param id stable cargo UUID
     */
    private void schedule(UUID id){if(queued.add(id))scheduled.addLast(id);}
    /**
     * Takes ownership of a cargo record and schedules it for movement.
     *
     * @param item authoritative cargo record
     */
    private void add(ConveyorStore.Travel item){store.add(item);schedule(item.id);}
    /**
     * Reactivates records from at most four queued, fully available chunk instances.
     */
    private void wakeLoadedChunks() {
        for(int count=0;count<4;count++) {
            var chunk=waking.poll();if(chunk==null)return;
            if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk){waking.add(chunk);return;}
            for(var item:store.inChunk(chunk.getPos().toLong()))schedule(item.id);
        }
    }
    /**
     * Simulates or admits a stack at a loaded eligible segment. Honors slot capacity, per-tick allowance,
     * reservations and vacancy headway.
     *
     * @param node physical position/channel identity
     * @param entry face from which the record entered its segment
     * @param stack item or fluid contents offered to the operation
     * @param simulate true to predict acceptance without transferring resource ownership
     * @return unaccepted copied remainder; empty if fully admitted
     */
    public ItemStack offer(TransportNode node,Direction entry,ItemStack stack,boolean simulate) {
        begin();var block=belt(node);
        if(stack.isEmpty())return ItemStack.EMPTY;
        if(block==null||!topology.canRoute()||reserved.contains(node)||vacated.contains(node)
                ||!block.acceptsFrom(level.getBlockState(node.position()),entry))return stack.copy();
        if(simulate?!operations.trySimulation():!operations.tryExecution())return stack.copy();
        var carried=store.at(node);int space=Math.min(stack.getMaxStackSize(),remaining(node,block));
        if(carried!=null) {
            if(carried.progress!=0||!ItemStack.isSameItemSameComponents(carried.stack,stack))return stack.copy();
            space=Math.min(space,Math.max(0,stack.getMaxStackSize()-carried.stack.getCount()));
        }
        int accepted=Math.min(stack.getCount(),space);if(accepted<=0)return stack.copy();
        if(!simulate) {
            if(carried==null)add(new ConveyorStore.Travel(UUID.randomUUID(),node,stack.copyWithCount(accepted),entry==null?Direction.UP:entry,0));
            else {carried.stack.grow(accepted);store.changed(carried);}
            charge(node,accepted);
        }
        return stack.copyWithCount(stack.getCount()-accepted);
    }
    /**
     * Creates the segment's insertion-only item-handler view.
     *
     * @param node physical position/channel identity
     * @param face sided capability face, or null for unsided access where supported
     * @return automation capability with extraction disabled
     */
    public IItemHandler ingress(TransportNode node,Direction face) {
        return new IItemHandler() {
            /**
             * Exposes one virtual conveyor insertion slot.
             *
             * @return one
             */
            public int getSlots(){return 1;}
            /**
             * Returns a copy of visible carried contents for a valid slot.
             *
             * @param slot virtual or inventory slot index
             * @return copied carried stack, or empty
             */
            public ItemStack getStackInSlot(int slot){var item=store.at(node);return slot==0&&item!=null?item.stack.copy():ItemStack.EMPTY;}
            /**
             * Admits or simulates an incoming stack through the transport interface.
             *
             * @param slot virtual or inventory slot index
             * @param stack item or fluid contents offered to the operation
             * @param simulate true to predict acceptance without transferring resource ownership
             * @return unaccepted item remainder
             */
            public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return slot==0?offer(node,face,stack,simulate):stack.copy();}
            /**
             * Refuses automation extraction from carried conveyor contents.
             *
             * @param slot virtual or inventory slot index
             * @param amount requested or accepted resource quantity in the owning engine's units
             * @param simulate true to predict acceptance without transferring resource ownership
             * @return empty item stack
             */
            public ItemStack extractItem(int slot,int amount,boolean simulate){return ItemStack.EMPTY;}
            /**
             * Advertises the local conveyor item-count ceiling for the valid virtual slot.
             *
             * @param slot virtual or inventory slot index
             * @return configured limit, or zero
             */
            public int getSlotLimit(int slot){var block=belt(node);return slot==0&&block!=null?block.itemsPerTick():0;}
            /**
             * Checks slot identity and the conveyor's incoming-face policy.
             *
             * @param slot virtual or inventory slot index
             * @param stack item or fluid contents offered to the operation
             * @return true if the slot and face permit this item offer
             */
            public boolean isItemValid(int slot,ItemStack stack){var block=belt(node);return slot==0&&block!=null&&block.acceptsFrom(level.getBlockState(node.position()),face);}
        };
    }
    /**
     * Processes each eligible record at most once per world tick, then extraction endpoints and client snapshots.
     */
    public void tick() {
        if(ticking||lastTick==level.getGameTime())return;lastTick=level.getGameTime();ticking=true;
        try {
            begin();topology.tick(limits.topologySteps(),limits.routeSteps());wakeLoadedChunks();
            int count=Math.min(limits.recordsPerTick(),scheduled.size());
            for(int i=0;i<count;i++) {
                UUID id=scheduled.removeFirst();queued.remove(id);var item=store.records.get(id);if(item==null)continue;
                process(item);if(store.records.containsKey(id)&&topology.available(item.node.position()))schedule(id);
            }
            var active=new ArrayList<CapabilityEndpoint<IItemHandler>>();
            for(var component:topology.components())active.addAll(component.sources());
            int[] endpointCount={0};
            sources.run(active,()->endpointCount[0]<limits.endpointsPerTick()&&operations.canExecute(),source->{endpointCount[0]++;extract(source);});
            sync.tick();
        } finally {ticking=false;}
    }
    /**
     * Advances or delivers one owned record, pausing at unavailable topology, occupied outputs or full inventories.
     *
     * @param item authoritative cargo record
     */
    private void process(ConveyorStore.Travel item) {
        if(!topology.available(item.node.position()))return;
        var block=belt(item.node);
        if(block==null){drop(item);return;}
        if(!topology.canRoute()||topology.component(item.node)==null)return;
        if(item.waiting) {
            if(store.at(item.node)==null&&!vacated.contains(item.node))store.promote(item);
            return;
        }
        if(item.progress<block.travelTicks()){item.progress++;store.changed(item);}
        if(item.progress<block.travelTicks()||!operations.canExecute())return;
        var state=level.getBlockState(item.node.position());
        var outputs=block.outputNodes(level,item.node,state).stream().filter(n->topology.available(n.position())).toList();
        if(!outputs.isEmpty()) {
            // Base belts have one output. Specialized junctions may order their own legal outputs.
            for(var target:outputs) {
                var next=belt(target);
                if(next==null||store.at(target)!=null||reserved.contains(target)||vacated.contains(target))continue;
                int amount=Math.min(item.stack.getCount(),Math.min(remaining(item.node,block),remaining(target,next)));
                if(amount<=0)continue;
                var from=item.node;var entry=state.getValue(ConveyorBlock.FACING).getOpposite();
                if(amount==item.stack.getCount()){store.move(item,target,entry);vacated.add(from);}
                else {item.stack.shrink(amount);store.changed(item);add(new ConveyorStore.Travel(UUID.randomUUID(),target,item.stack.copyWithCount(amount),entry,0));}
                charge(from,amount);charge(target,amount);return;
            }
            return;
        }
        var component=topology.component(item.node);if(component==null)return;
        int amount=Math.min(item.stack.getCount(),remaining(item.node,block));if(amount<=0)return;
        for(var endpoint:component.graph().destinations(item.node)) {
            if(endpoint.source()||!endpoint.node().equals(item.node)||!endpoint.position().equals(block.destinationPosition(item.node,state))||!topology.available(endpoint.position()))continue;
            var handler=endpoint.cache().getCapability();if(handler==null)continue;
            var offered=item.stack.copyWithCount(Math.min(amount,item.stack.getMaxStackSize()));
            reserved.add(item.node);
            try {
                for(int slot=0;slot<handler.getSlots()&&!offered.isEmpty()&&operations.tryExecution();slot++)
                    offered=handler.insertItem(slot,offered,false);
            } finally {reserved.remove(item.node);}
            int accepted=Math.clamp(Math.min(amount,item.stack.getMaxStackSize())-offered.getCount(),0,amount);
            if(accepted>0){item.stack.shrink(accepted);charge(item.node,accepted);store.changed(item);}
            if(item.stack.isEmpty()){var old=item.node;store.remove(item);vacated.add(old);}
            return;
        }
    }
    /**
     * Simulates and reserves intake before executing source extraction; unexpected actual contents remain owned.
     *
     * @param endpoint sided resource endpoint
     */
    private void extract(CapabilityEndpoint<IItemHandler> endpoint) {
        var node=endpoint.node();var block=belt(node);
        if(block==null||store.at(node)!=null||reserved.contains(node)||vacated.contains(node)||!topology.available(endpoint.position()))return;
        var component=topology.component(node);if(component==null)return;
        var handler=endpoint.cache().getCapability();if(handler==null||handler.getSlots()<=0)return;
        var key=new SourceKey(node,endpoint.position(),endpoint.face());int start=Math.floorMod(slots.getOrDefault(key,0),handler.getSlots());
        for(int i=0;i<Math.min(8,handler.getSlots())&&operations.canExecute();i++) {
            int slot=(start+i)%handler.getSlots();slots.put(key,slot+1);
            if(!operations.trySimulation())return;
            var offered=handler.extractItem(slot,remaining(node,block),true);if(offered.isEmpty())continue;
            int accepted=offered.getCount()-offer(node,level.getBlockState(node.position()).getValue(ConveyorBlock.FACING).getOpposite(),offered,true).getCount();
            if(accepted<=0||!component.valid()||!operations.tryExecution())return;
            reserved.add(node);
            try {
                var actual=handler.extractItem(slot,accepted,false);
                if(!actual.isEmpty()) {
                    // Reservation prevents reentrant insertion. If the belt disappeared, the
                    // persisted record is released safely on the next loaded tick.
                    add(new ConveyorStore.Travel(UUID.randomUUID(),node,actual,level.getBlockState(node.position()).getBlock() instanceof ConveyorBlock
                        ?level.getBlockState(node.position()).getValue(ConveyorBlock.FACING).getOpposite():Direction.UP,0));
                    charge(node,Math.min(actual.getCount(),remaining(node,block)));
                }
            } finally {reserved.remove(node);}
            return;
        }
    }
    /**
     * Collects at most one record into a nearby same-dimension player's available inventory space.
     *
     * @param pos block position being queried or changed
     * @param player interacting or watching player
     * @return number of items actually collected
     */
    public int pickup(BlockPos pos,Player player) {
        begin();
        if(player.level()!=level||player.distanceToSqr(pos.getX()+0.5,pos.getY()+1,pos.getZ()+0.5)>36)return 0;
        int collected=0;
        for(var item:store.at(pos)) {
            if(reserved.contains(item.node))continue;
            var offered=item.stack.copyWithCount(Math.min(item.stack.getCount(),item.stack.getMaxStackSize()));int before=offered.getCount();
            player.getInventory().add(offered);int taken=before-offered.getCount();
            if(taken>0){item.stack.shrink(taken);collected+=taken;store.changed(item);}
            if(item.stack.isEmpty()){store.remove(item);vacated.add(item.node);}
            break;
        }
        return collected;
    }
    /**
     * Invalidates the changed block and releases its unreserved cargo as dropped items.
     *
     * @param pos block position being queried or changed
     */
    public void removed(BlockPos pos){changed(pos);for(var item:store.at(pos))drop(item);}
    /**
     * Releases one legal-sized portion only after a loaded world accepts its ItemEntity; any remaining ownership
     * stays saved.
     *
     * @param item authoritative cargo record
     */
    private void drop(ConveyorStore.Travel item) {
        if(reserved.contains(item.node)||!topology.available(item.node.position()))return;
        var stack=item.stack.copyWithCount(Math.min(item.stack.getCount(),item.stack.getMaxStackSize()));
        var pos=item.node.position();var entity=new ItemEntity(level,pos.getX()+0.5,pos.getY()+1.1,pos.getZ()+0.5,stack);
        entity.setDefaultPickUpDelay();
        if(level.addFreshEntity(entity)){item.stack.shrink(stack.getCount());store.changed(item);if(item.stack.isEmpty())store.remove(item);}
    }
}
