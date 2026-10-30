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

/** Physical store-and-forward belts. No server entities are used for stacks on the belt. */
public final class ConveyorNetworks {
    private static final Map<ServerLevel,ConveyorNetworks> LEVELS=Collections.synchronizedMap(new IdentityHashMap<>());
    public static ConveyorNetworks get(ServerLevel level){return LEVELS.computeIfAbsent(level,ConveyorNetworks::new);}
    public static void unload(ServerLevel level){LEVELS.remove(level);}
    private static ConveyorLimits configured=ConveyorLimits.DEFAULT;
    /** Configure during mod setup, before any dimension creates a conveyor manager. */
    public static void configureLimits(ConveyorLimits limits) {
        synchronized(LEVELS) {
            if(!LEVELS.isEmpty())throw new IllegalStateException("Configure conveyor work budgets before world creation");
            configured=Objects.requireNonNull(limits);
        }
    }
    private final ServerLevel level;
    private final ConveyorLimits limits;
    private final TransportNetworks<CapabilityEndpoint<IItemHandler>> topology;
    final ConveyorStore store;
    private final ArrayDeque<UUID> scheduled=new ArrayDeque<>();
    private final Set<UUID> queued=new HashSet<>();
    private final ConcurrentLinkedQueue<LevelChunk> waking=new ConcurrentLinkedQueue<>();
    private final Set<TransportNode> reserved=new HashSet<>(),vacated=new HashSet<>();
    private final Map<TransportNode,Integer> used=new HashMap<>();
    private final WorkBudget operations;
    private final FairScheduler sources=new FairScheduler();
    private final Map<SourceKey,Integer> slots=new LinkedHashMap<>(16,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<SourceKey,Integer> entry){return size()>2048;}
    };
    private long tick=Long.MIN_VALUE,lastTick=Long.MIN_VALUE;private boolean ticking;
    private final ConveyorSync sync;
    private record SourceKey(TransportNode node,BlockPos position,Direction face) {}
    public record Item(UUID id,TransportNode node,ItemStack stack,Direction entry,int progress) {}
    private ConveyorNetworks(ServerLevel level) {
        this.level=level;limits=configured;operations=new WorkBudget(limits.operationsPerTick());
        topology=new TransportNetworks<>(level,new ConveyorTransportAdapter());
        store=level.getDataStorage().computeIfAbsent(ConveyorStore.factory(),"poweradvantage_conveyors");
        for(var item:store.records.values())if(level.isLoaded(item.node.position()))schedule(item.id);sync=new ConveyorSync(level,this,limits.snapshotPacketsPerTick());
    }
    public List<Item> items(){return store.records.values().stream().map(i->new Item(i.id,i.node,i.stack.copy(),i.entry,i.progress)).toList();}
    public void changed(BlockPos pos){topology.changed(pos);}
    public void neighbourChanged(BlockPos pos,BlockPos from){topology.neighbourChanged(pos,from);}
    public void chunkLoaded(LevelChunk chunk){topology.chunkLoaded(chunk);waking.add(chunk);}
    public void chunkUnloaded(ChunkPos pos){topology.chunkUnloaded(pos);}
    public void watch(ServerPlayer player,ChunkPos pos){sync.watch(player,pos);}
    public void unwatch(ServerPlayer player,ChunkPos pos){sync.unwatch(player,pos);}
    private void begin() {
        long now=level.getGameTime();operations.begin(now);
        if(now!=tick){tick=now;used.clear();vacated.clear();}
    }
    private ConveyorBlock belt(TransportNode node) {
        return topology.contains(node)?(ConveyorBlock)level.getBlockState(node.position()).getBlock():null;
    }
    private int remaining(TransportNode node,ConveyorBlock belt){return Math.max(0,belt.itemsPerTick()-used.getOrDefault(node,0));}
    private void charge(TransportNode node,int amount){used.merge(node,amount,Integer::sum);}
    private void schedule(UUID id){if(queued.add(id))scheduled.addLast(id);}
    private void add(ConveyorStore.Travel item){store.add(item);schedule(item.id);}
    private void wakeLoadedChunks() {
        for(int count=0;count<4;count++) {
            var chunk=waking.poll();if(chunk==null)return;
            if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk){waking.add(chunk);return;}
            for(var item:store.inChunk(chunk.getPos().toLong()))schedule(item.id);
        }
    }
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
    public IItemHandler ingress(TransportNode node,Direction face) {
        return new IItemHandler() {
            public int getSlots(){return 1;}
            public ItemStack getStackInSlot(int slot){var item=store.at(node);return slot==0&&item!=null?item.stack.copy():ItemStack.EMPTY;}
            public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return slot==0?offer(node,face,stack,simulate):stack.copy();}
            public ItemStack extractItem(int slot,int amount,boolean simulate){return ItemStack.EMPTY;}
            public int getSlotLimit(int slot){var block=belt(node);return slot==0&&block!=null?block.itemsPerTick():0;}
            public boolean isItemValid(int slot,ItemStack stack){var block=belt(node);return slot==0&&block!=null&&block.acceptsFrom(level.getBlockState(node.position()),face);}
        };
    }
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
    public void removed(BlockPos pos){changed(pos);for(var item:store.at(pos))drop(item);}
    private void drop(ConveyorStore.Travel item) {
        if(reserved.contains(item.node)||!topology.available(item.node.position()))return;
        var stack=item.stack.copyWithCount(Math.min(item.stack.getCount(),item.stack.getMaxStackSize()));
        var pos=item.node.position();var entity=new ItemEntity(level,pos.getX()+0.5,pos.getY()+1.1,pos.getZ()+0.5,stack);
        entity.setDefaultPickUpDelay();
        if(level.addFreshEntity(entity)){item.stack.shrink(stack.getCount());store.changed(item);if(item.stack.isEmpty())store.remove(item);}
    }
}
