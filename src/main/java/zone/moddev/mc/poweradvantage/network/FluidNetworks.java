package zone.moddev.mc.poweradvantage.network;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock;

/** Server-thread routing, one instance per dimension; ordinary pipe blocks hold no fluid. */
public final class FluidNetworks {
    /** @deprecated Use FluidPipeBlock.DEFAULT_TRANSFER_RATE or a pipe's getTransferRate(). */
    @Deprecated
    public static final int MB_PER_TICK=FluidPipeBlock.DEFAULT_TRANSFER_RATE;
    private static final int BUILD_STEPS=512, ROUTE_ATTEMPTS=256;
    private static final Map<ServerLevel,FluidNetworks> LEVELS=Collections.synchronizedMap(new IdentityHashMap<>());
    public static FluidNetworks get(ServerLevel level) { return LEVELS.computeIfAbsent(level,FluidNetworks::new); }
    public static void unload(ServerLevel level) { LEVELS.remove(level); }
    private final ServerLevel level;
    private final Map<BlockPos,Network> owners=new HashMap<>();
    private final Set<Network> networks=new LinkedHashSet<>();
    private final ArrayDeque<Iterator<BlockPos>> seeds=new ArrayDeque<>();
    private final ConcurrentLinkedQueue<LevelChunk> loaded=new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ChunkPos> unloaded=new ConcurrentLinkedQueue<>();
    private final Map<Long,Set<BlockPos>> chunks=new HashMap<>();
    private final Set<Long> excludedChunks=new HashSet<>();
    private final ArrayDeque<SectionScan> scans=new ArrayDeque<>();
    private Build build;
    private boolean routing;
    private long tick=Long.MIN_VALUE;
    private int attempts;
    private int simulations;
    private int terminalCursor;
    private int graphCursor;
    private final FluidRecovery recovery;

    private FluidNetworks(ServerLevel level) {
        this.level=level;
        recovery=level.getDataStorage().computeIfAbsent(FluidRecovery.factory(),"poweradvantage_fluid_recovery");
    }
    /** Chunk events only enqueue work, avoiding access to chunks before FULL promotion. */
    public void chunkLoaded(LevelChunk chunk) { loaded.add(chunk); }
    public void chunkUnloaded(ChunkPos chunk) { unloaded.add(chunk); }
    private boolean available(BlockPos pos) {
        return !excludedChunks.contains(ChunkPos.asLong(pos)) && level.isLoaded(pos);
    }
    private boolean pipe(BlockPos pos) {
        return available(pos) && level.getBlockState(pos).getBlock() instanceof FluidPipeBlock;
    }
    /** Invalidates a whole affected component immediately; discovery/compression is budgeted. */
    public void changed(BlockPos pos) {
        abortBuild();
        var nearby=new ArrayList<BlockPos>();nearby.add(pos.immutable());
        for (Direction side:Direction.values()) nearby.add(pos.relative(side));
        for (BlockPos p:nearby) {
            Network old=owners.get(p);
            if (old!=null) invalidate(old);
        }
        seeds.add(nearby.iterator());
    }
    /** Comparator/content notifications do not change topology. Capability invalidation has its own listener. */
    public void neighbourChanged(BlockPos pos,BlockPos from) {
        Network network=owners.get(pos);
        if(network!=null && network.valid && available(from)) {
            var block=level.getBlockState(from).getBlock();
            if(block instanceof FluidPipeBlock && owners.get(from)==network)return;
            if(!(block instanceof FluidPipeBlock) && block==network.boundary.getOrDefault(from,net.minecraft.world.level.block.Blocks.AIR))return;
        }
        changed(pos);
    }
    private void invalidate(Network network) {
        if (!network.valid) return;
        network.valid=false;networks.remove(network);
        seeds.add(network.pipes.iterator());
    }
    private void abortBuild() {
        if (build==null) return;
        build.network.valid=false;build.network.building=false;
        seeds.add(build.seen.iterator());
        build=null;
    }
    private void newTick() {
        if (tick!=level.getGameTime()) {
            tick=level.getGameTime();attempts=0;simulations=0;
        }
    }
    public void tick() {
        newTick();
        processChunks();
        for (int i=0;i<BUILD_STEPS;i++) {
            if (build==null) {
                if (seeds.isEmpty()) break;
                var iterator=seeds.peek();
                if (!iterator.hasNext()) { seeds.remove();continue; }
                BlockPos pos=iterator.next();
                Network existing=owners.get(pos);
                if (!pipe(pos)) {
                    owners.remove(pos);
                    var index=chunks.get(ChunkPos.asLong(pos));
                    if(index!=null) { index.remove(pos);if(index.isEmpty())chunks.remove(ChunkPos.asLong(pos)); }
                    continue;
                }
                if (existing!=null && existing.valid) continue;
                build=new Build(pos);
            }
            Build current=build;
            if (current.advance() && build==current) {
                Network network=build.network;
                network.graph=build.compressor.finish();
                network.pipes=Collections.unmodifiableSet(build.seen);
                network.terminals=List.copyOf(build.terminals);
                networks.add(network);
                // Publication is incremental too: writes happened during discovery; validity
                // remains false until the entire replacement graph is ready.
                network.building=false;network.valid=true;
                build=null;
            }
        }
        var graphs=new ArrayList<>(networks);
        int graphWork=512;
        if(!graphs.isEmpty()) {
            graphCursor=Math.floorMod(graphCursor,graphs.size());
            for(int i=0;i<graphs.size() && graphWork>0;i++)
                graphWork-=graphs.get((graphCursor+i)%graphs.size()).graph.advanceRoutes(Math.min(32,graphWork));
            graphCursor=(graphCursor+1)%graphs.size();
        }
        retryRecovery();
        // Snapshot only terminals, not all pipe blocks; rotate the first source every tick.
        var active=new ArrayList<Endpoint>();
        for (Network network:networks) active.addAll(network.terminals);
        if (!active.isEmpty()) {
            terminalCursor=Math.floorMod(terminalCursor,active.size());
            for (int i=0;i<active.size() && attempts<ROUTE_ATTEMPTS;i++) extract(active.get((terminalCursor+i)%active.size()));
            terminalCursor=(terminalCursor+1)%active.size();
        }
    }
    private record SectionScan(LevelChunk chunk,int section) {}
    private void processChunks() {
        ChunkPos gone;
        while ((gone=unloaded.poll())!=null) {
            excludedChunks.add(gone.toLong());
            var positions=chunks.remove(gone.toLong());
            if (positions!=null) {
                abortBuild();
                for (BlockPos pos:positions) {
                    var owner=owners.remove(pos);
                    if (owner!=null) invalidate(owner);
                }
            }
        }
        // Inspect palettes cheaply; scan at most one matching 4096-block section per tick.
        for (int count=0;count<4;count++) {
            LevelChunk chunk=loaded.poll();if (chunk==null) break;
            if (level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk) {
                loaded.add(chunk);break;
            }
            excludedChunks.remove(chunk.getPos().toLong());
            var sections=chunk.getSections();
            for (int section=0;section<sections.length;section++)
                if (sections[section].maybeHas(state->state.getBlock() instanceof FluidPipeBlock)) scans.add(new SectionScan(chunk,section));
            // Reconnect known pipes just outside a reloaded chunk without scanning the world.
            for (Direction side:Direction.Plane.HORIZONTAL) {
                long key=ChunkPos.asLong(chunk.getPos().x+side.getStepX(),chunk.getPos().z+side.getStepZ());
                var adjacent=chunks.get(key);
                if (adjacent!=null) seeds.add(new ArrayList<>(adjacent).iterator());
                if (adjacent!=null) for (BlockPos p:adjacent) { var n=owners.get(p);if(n!=null)invalidate(n); }
            }
        }
        if (scans.isEmpty()) return;
        var scan=scans.remove();var chunk=scan.chunk();
        if (excludedChunks.contains(chunk.getPos().toLong()) || level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk) return;
        var section=chunk.getSections()[scan.section()];
        var positions=new ArrayList<BlockPos>();
        int baseY=level.getSectionYFromSectionIndex(scan.section())*16;
        for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)
            if(section.getBlockState(x,y,z).getBlock() instanceof FluidPipeBlock) {
                var pos=new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z);
                positions.add(pos);chunks.computeIfAbsent(chunk.getPos().toLong(),key->new HashSet<>()).add(pos);
            }
        seeds.add(positions.iterator());
    }
    public IFluidHandler ingress(BlockPos pos,Direction face) { return new Ingress(pos.immutable(),face); }
    public record Stats(int pipes,int vertices,int endpoints) {}
    public Stats stats(BlockPos pos) {
        Network n=owners.get(pos);
        return n==null || !n.valid ? new Stats(0,0,0):new Stats(n.pipes.size(),n.graph.vertexCount(),n.endpoints.size());
    }
    private final class Ingress implements IFluidHandler {
        private final BlockPos pos;private final Direction face;
        Ingress(BlockPos pos,Direction face){this.pos=pos;this.face=face;}
        public int getTanks(){return 1;}
        public FluidStack getFluidInTank(int tank){return FluidStack.EMPTY;}
        public int getTankCapacity(int tank){return tank==0 && pipe(pos)?((FluidPipeBlock)level.getBlockState(pos).getBlock()).getTransferRate():0;}
        public boolean isFluidValid(int tank,FluidStack fluid){return tank==0 && !fluid.isEmpty();}
        public int fill(FluidStack stack,FluidAction action){return route(pos,face==null?null:pos.relative(face),stack,action);}
        public FluidStack drain(FluidStack stack,FluidAction action){return FluidStack.EMPTY;}
        public FluidStack drain(int amount,FluidAction action){return FluidStack.EMPTY;}
    }
    private int route(BlockPos ingress,BlockPos source,FluidStack offered,IFluidHandler.FluidAction action) {
        newTick();
        Network network=owners.get(ingress);
        if (!unloaded.isEmpty() || (!action.execute() && simulations>=ROUTE_ATTEMPTS) || routing || offered.isEmpty() || !pipe(ingress) || network==null || !network.valid || attempts>=ROUTE_ATTEMPTS) return 0;
        IngressKey contender=new IngressKey(ingress,source);
        network.contenders.put(contender,tick);
        if (network.allowanceTick!=tick) {
            network.allowanceTick=tick;network.remaining=network.transferRate;
            network.contenders.entrySet().removeIf(entry->tick-entry.getValue()>40);
            var active=new ArrayList<>(network.contenders.keySet());
            network.sourceCursor=Math.floorMod(network.sourceCursor,active.size());
            network.granted=active.get(network.sourceCursor);
            network.sourceCursor++;
        }
        if(!contender.equals(network.granted))return 0;
        int limit=Math.min(offered.getAmount(),network.remaining);
        if(limit<=0)return 0;
        var candidates=network.graph.destinations(ingress);
        int sent=0;
        int nextDestination=network.destinationCursor;
        routing=true;
        try {
            for(int i=0;i<candidates.size() && sent<limit && attempts<ROUTE_ATTEMPTS;i++) {
                Endpoint endpoint=candidates.get(Math.floorMod(network.destinationCursor+i,candidates.size()));
                if(!network.valid)break;
                if(endpoint.source || endpoint.pos.equals(source) || !available(endpoint.pos))continue;
                var handler=endpoint.cache.getCapability();
                if(handler==null)continue;
                if(action.execute())attempts++;else if(++simulations>ROUTE_ATTEMPTS)break;
                int accepted=handler.fill(offered.copyWithAmount(limit-sent),action);
                accepted=Math.clamp(accepted,0,limit-sent);
                sent+=accepted;
                if(accepted>0)nextDestination=Math.floorMod(network.destinationCursor+i+1,candidates.size());
            }
        } finally { routing=false; }
        if(action.execute()) {
            network.remaining-=sent;
            if(sent>0)network.destinationCursor=nextDestination;
        }
        return sent;
    }
    private void extract(Endpoint endpoint) {
        if (!available(endpoint.pos) || !pipe(endpoint.pipe))return;
        Network network=owners.get(endpoint.pipe);
        if(network==null || !network.valid)return;
        if(network.allowanceTick==tick && network.remaining<=0)return;
        IFluidHandler source=endpoint.cache.getCapability();if(source==null)return;
        attempts++;
        FluidStack offered=source.drain(network.transferRate,IFluidHandler.FluidAction.SIMULATE);
        if(offered.isEmpty())return;
        int accepted=route(endpoint.pipe,endpoint.pos,offered,IFluidHandler.FluidAction.SIMULATE);
        if(accepted<=0)return;
        // Do not extract when discovery/capability invalidation changed the route during simulation.
        if(!network.valid || attempts>=ROUTE_ATTEMPTS)return;
        FluidStack extracted=source.drain(offered.copyWithAmount(accepted),IFluidHandler.FluidAction.EXECUTE);
        if(extracted.isEmpty())return;
        int sent=route(endpoint.pipe,endpoint.pos,extracted,IFluidHandler.FluidAction.EXECUTE);
        if(sent<extracted.getAmount()) recovery.add(endpoint.pipe,endpoint.pos,endpoint.face,extracted.copyWithAmount(extracted.getAmount()-sent));
    }
    private void retryRecovery() {
        for (int i=0;i<Math.min(8,recovery.entries.size()) && attempts<ROUTE_ATTEMPTS;i++) {
            var entry=recovery.entries.get(i);
            int sent=route(entry.pipe(),entry.source(),entry.fluid(),IFluidHandler.FluidAction.EXECUTE);
            if(sent==0 && available(entry.source())) {
                var source=level.getCapability(Capabilities.FluidHandler.BLOCK,entry.source(),entry.face());
                if(source!=null) { attempts++;sent=source.fill(entry.fluid(),IFluidHandler.FluidAction.EXECUTE); }
            }
            if(sent>0) {
                entry.fluid().shrink(Math.min(sent,entry.fluid().getAmount()));recovery.setDirty();
                if(entry.fluid().isEmpty()) {recovery.entries.remove(i);i--;}
            }
        }
    }
    private record IngressKey(BlockPos pipe,BlockPos source) {}
    static final class Network {
        boolean valid;
        boolean building=true;
        Set<BlockPos> pipes=Set.of();
        List<Endpoint> endpoints=new ArrayList<>(),terminals=List.of();
        PipeGraph graph;
        long allowanceTick=Long.MIN_VALUE;
        int transferRate=Integer.MAX_VALUE;
        int remaining,destinationCursor,sourceCursor;
        IngressKey granted;
        final Map<IngressKey,Long> contenders=new LinkedHashMap<>();
        final List<BlockCapabilityCache<IFluidHandler,Direction>> watchers=new ArrayList<>();
        final Map<BlockPos,net.minecraft.world.level.block.Block> boundary=new HashMap<>();
    }
    static final class Endpoint {
        final BlockPos pipe,pos;final Direction face;final boolean source;
        final BlockCapabilityCache<IFluidHandler,Direction> cache;
        Endpoint(BlockPos pipe,BlockPos pos,Direction face,boolean source,BlockCapabilityCache<IFluidHandler,Direction> cache) {
            this.pipe=pipe;this.pos=pos;this.face=face;this.source=source;this.cache=cache;
        }
    }
    private final class Build {
        final Network network=new Network();
        final Set<BlockPos> seen=new LinkedHashSet<>();
        final ArrayDeque<BlockPos> todo=new ArrayDeque<>();
        final Map<BlockPos,List<BlockPos>> physical=new LinkedHashMap<>();
        final Map<BlockPos,List<Endpoint>> attached=new HashMap<>();
        final List<Endpoint> terminals=new ArrayList<>();
        final Set<BlockPos> terminalPositions=new HashSet<>();
        PipeGraph.Builder compressor;
        Build(BlockPos start){seen.add(start);todo.add(start);}
        boolean advance() {
            if(compressor!=null)return compressor.advance();
            if(todo.isEmpty()) {
                if(physical.isEmpty())throw new IllegalStateException("Empty pipe discovery");
                // A block reached via multiple faces is never both a source and a sink.
                var sources=new HashSet<BlockPos>();
                for(Endpoint e:terminals)sources.add(e.pos);
                attached.values().forEach(list->list.removeIf(e->!e.source && sources.contains(e.pos)));
                compressor=new PipeGraph.Builder(physical,attached,terminalPositions);return false;
            }
            var pos=todo.remove();
            if(!pipe(pos))return false;
            chunks.computeIfAbsent(ChunkPos.asLong(pos),key->new HashSet<>()).add(pos);
            owners.put(pos,network);
            var state=level.getBlockState(pos);var block=(FluidPipeBlock)state.getBlock();
            network.transferRate=Math.min(network.transferRate,block.getTransferRate());
            if(block.isTerminal())terminalPositions.add(pos);
            var neighbours=new ArrayList<BlockPos>();
            for(Direction side:Direction.values()) {
                var other=pos.relative(side);
                if(!available(other))continue;
                if(pipe(other)) {
                    neighbours.add(other);
                    if(seen.add(other))todo.add(other);
                } else if(!level.getBlockState(other).isAir()) {
                    network.boundary.put(other,level.getBlockState(other).getBlock());
                    var watchedNetwork=network;
                    var cache=BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK,level,other,side.getOpposite(),
                        ()->watchedNetwork.valid || watchedNetwork.building,()-> { if(watchedNetwork.valid || watchedNetwork.building)changed(pos); });
                    network.watchers.add(cache);
                    if(cache.getCapability()!=null) {
                        boolean source=block.isTerminal() && side==state.getValue(FluidPipeBlock.SOURCE);
                        var endpoint=new Endpoint(pos,other,side.getOpposite(),source,cache);
                        network.endpoints.add(endpoint);
                        attached.computeIfAbsent(pos,key->new ArrayList<>()).add(endpoint);
                        if(source)terminals.add(endpoint);
                    }
                }
            }
            physical.put(pos,List.copyOf(neighbours));
            FluidPipeBlock.refresh(level,pos);
            return false;
        }
    }
}
