package zone.moddev.mc.poweradvantage.network.transport;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.*;

/** Loaded-world component lifecycle. Instantiate once per dimension and transport kind. */
public final class TransportNetworks<E> {
    public record Origin(TransportNode node,BlockPos source) {
        public Origin { if(source!=null)source=source.immutable(); }
    }
    public static final class Component<E> {
        private boolean valid,building=true;
        private final long version;
        private Component(long version){this.version=version;}
        /** Process-local topology generation; recompute routes after restart. */
        public long version(){return version;}
        private Set<TransportNode> nodes=Set.of();
        private final List<E> endpoints=new ArrayList<>();
        private List<E> sources=List.of();
        private RoutingGraph<TransportNode,E> graph;
        private int transferRate=Integer.MAX_VALUE;
        private FairTransferBudget<Origin> budget;
        private final List<Object> watchers=new ArrayList<>();
        private final Map<BlockPos,Block> boundary=new HashMap<>();
        public boolean valid(){return valid;}
        public Set<TransportNode> nodes(){return nodes;}
        public List<E> endpoints(){return Collections.unmodifiableList(endpoints);}
        public List<E> sources(){return sources;}
        public RoutingGraph<TransportNode,E> graph(){return graph;}
        public int transferRate(){return transferRate;}
        public FairTransferBudget<Origin> budget(){return budget;}
    }
    private final ServerLevel level;
    private final TransportAdapter<E> adapter;
    private final Map<TransportNode,Component<E>> owners=new HashMap<>();
    private final Set<Component<E>> components=new LinkedHashSet<>();
    private final ArrayDeque<Iterator<TransportNode>> seeds=new ArrayDeque<>();
    private final ConcurrentLinkedQueue<LevelChunk> loaded=new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ChunkPos> unloaded=new ConcurrentLinkedQueue<>();
    private final Map<Long,Set<TransportNode>> chunks=new HashMap<>();
    private final Map<BlockPos,Set<TransportNode>> positions=new HashMap<>();
    private final Set<Long> excludedChunks=new HashSet<>();
    private final ArrayDeque<SectionScan> scans=new ArrayDeque<>();
    private final FairScheduler graphScheduler=new FairScheduler();
    private Build build;
    private long nextVersion;
    public TransportNetworks(ServerLevel level,TransportAdapter<E> adapter){this.level=level;this.adapter=adapter;}
    public void chunkLoaded(LevelChunk chunk){loaded.add(chunk);}
    public void chunkUnloaded(ChunkPos chunk){unloaded.add(chunk);}
    /** Pending unloads fail closed before the next topology tick. */
    public boolean canRoute(){return unloaded.isEmpty();}
    public boolean available(BlockPos pos){return !excludedChunks.contains(ChunkPos.asLong(pos))&&level.isLoaded(pos);}
    public boolean contains(TransportNode node) {
        if(!available(node.position()))return false;
        var state=level.getBlockState(node.position());
        return adapter.matches(state)&&adapter.nodes(node.position(),state).contains(node);
    }
    private List<TransportNode> nodes(BlockPos pos) {
        if(!available(pos))return List.of();
        var state=level.getBlockState(pos);
        return adapter.matches(state)?adapter.nodes(pos,state):List.of();
    }
    public Component<E> component(TransportNode node) {
        var component=owners.get(node);return component!=null&&component.valid?component:null;
    }
    public List<Component<E>> components(){return List.copyOf(components);}
    public void changed(BlockPos pos) {
        abortBuild();
        var nearby=new LinkedHashSet<TransportNode>();
        for(BlockPos p:adapter.affectedPositions(pos)) {
            // Old channel IDs can disappear when a block is removed or reconfigured.
            var indexed=positions.get(p);
            if(indexed!=null)for(TransportNode old:indexed) {
                var owner=owners.get(old);if(owner!=null)invalidate(owner);
            }
            nearby.addAll(nodes(p));
        }
        seeds.add(nearby.iterator());
    }
    public void neighbourChanged(BlockPos pos,BlockPos from) {
        var local=nodes(pos);
        if(!local.isEmpty()&&available(from)) {
            var state=level.getBlockState(from);boolean unchanged=true;
            for(TransportNode node:local) {
                var component=owners.get(node);
                if(component==null||!component.valid){unchanged=false;break;}
                if(adapter.matches(state)) {
                    if(nodes(from).stream().noneMatch(other->owners.get(other)==component)){unchanged=false;break;}
                } else if(state.getBlock()!=component.boundary.getOrDefault(from,Blocks.AIR)){unchanged=false;break;}
            }
            if(unchanged)return;
        }
        changed(pos);
    }
    private void invalidate(Component<E> component) {
        if(!component.valid)return;
        component.valid=false;components.remove(component);seeds.add(component.nodes.iterator());
    }
    private void abortBuild() {
        if(build==null)return;
        build.component.valid=false;build.component.building=false;seeds.add(build.seen.iterator());build=null;
    }
    public void tick(){tick(512,512);}
    /** Budgets count discovery/compression steps and Dijkstra steps, respectively. */
    public void tick(int topologySteps,int routeSteps) {
        if(topologySteps<0||routeSteps<0)throw new IllegalArgumentException("Negative work budget");
        processChunks();
        for(int step=0;step<topologySteps;step++) {
            if(build==null) {
                if(seeds.isEmpty())break;
                var iterator=seeds.peek();if(!iterator.hasNext()){seeds.remove();continue;}
                var node=iterator.next();var existing=owners.get(node);
                if(!contains(node)) {
                    owners.remove(node);unindexPosition(node);var index=chunks.get(ChunkPos.asLong(node.position()));
                    if(index!=null){index.remove(node);if(index.isEmpty())chunks.remove(ChunkPos.asLong(node.position()));}
                    continue;
                }
                if(existing!=null&&existing.valid)continue;
                build=new Build(node);
            }
            Build current=build;
            if(current.advance()&&build==current) {
                var component=build.component;
                component.graph=build.compressor.finish();component.nodes=Collections.unmodifiableSet(build.seen);
                component.sources=List.copyOf(build.sources);component.budget=new FairTransferBudget<>(component.transferRate);
                component.building=false;component.valid=true;components.add(component);build=null;
            }
        }
        int[] remaining={routeSteps};
        graphScheduler.run(new ArrayList<>(components),()->remaining[0]>0,
            component->remaining[0]-=component.graph.advanceRoutes(Math.min(32,remaining[0])));
    }
    private record SectionScan(LevelChunk chunk,int section) {}
    private void processChunks() {
        ChunkPos gone;
        while((gone=unloaded.poll())!=null) {
            excludedChunks.add(gone.toLong());var positions=chunks.remove(gone.toLong());
            if(positions!=null){abortBuild();for(var node:positions){unindexPosition(node);var owner=owners.remove(node);if(owner!=null)invalidate(owner);}}
        }
        for(int count=0;count<4;count++) {
            var chunk=loaded.poll();if(chunk==null)break;
            if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk){loaded.add(chunk);break;}
            excludedChunks.remove(chunk.getPos().toLong());var sections=chunk.getSections();
            for(int section=0;section<sections.length;section++)if(sections[section].maybeHas(adapter::matches))scans.add(new SectionScan(chunk,section));
            for(Direction side:Direction.Plane.HORIZONTAL) {
                long key=ChunkPos.asLong(chunk.getPos().x+side.getStepX(),chunk.getPos().z+side.getStepZ());
                var adjacent=chunks.get(key);
                if(adjacent!=null){seeds.add(new ArrayList<>(adjacent).iterator());for(var node:adjacent){var owner=owners.get(node);if(owner!=null)invalidate(owner);}}
            }
        }
        if(scans.isEmpty())return;
        var scan=scans.remove();var chunk=scan.chunk();
        if(excludedChunks.contains(chunk.getPos().toLong())||level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk)return;
        var section=chunk.getSections()[scan.section()];var positions=new ArrayList<TransportNode>();
        int baseY=level.getSectionYFromSectionIndex(scan.section())*16;
        for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++) {
            var state=section.getBlockState(x,y,z);
            if(adapter.matches(state)) {
                var pos=new BlockPos(chunk.getPos().getMinBlockX()+x,baseY+y,chunk.getPos().getMinBlockZ()+z);
                var discovered=adapter.nodes(pos,state);positions.addAll(discovered);
                for(var node:discovered)index(node);
            }
        }
        seeds.add(positions.iterator());
    }
    private void index(TransportNode node) {
        chunks.computeIfAbsent(ChunkPos.asLong(node.position()),key->new HashSet<>()).add(node);
        positions.computeIfAbsent(node.position(),key->new HashSet<>()).add(node);
    }
    private void unindexPosition(TransportNode node) {
        var channels=positions.get(node.position());
        if(channels!=null){channels.remove(node);if(channels.isEmpty())positions.remove(node.position());}
    }
    private TransportAdapter.Context context(Component<E> observed,TransportNode node) {
        return new TransportAdapter.Context() {
            public ServerLevel level(){return level;}
            public boolean available(BlockPos pos){return TransportNetworks.this.available(pos);}
            public <H> BlockCapabilityCache<H,Direction> watch(BlockCapability<H,Direction> capability,BlockPos pos,Direction face) {
                if(!available(pos))throw new IllegalArgumentException("Cannot watch an unloaded endpoint");
                observed.boundary.put(pos.immutable(),level.getBlockState(pos).getBlock());
                var cache=BlockCapabilityCache.create(capability,level,pos,face,()->observed.valid||observed.building,
                    ()->{if(observed.valid||observed.building)changed(node.position());});
                observed.watchers.add(cache);return cache;
            }
        };
    }
    private final class Build {
        final Component<E> component=new Component<>(++nextVersion);
        final Set<TransportNode> seen=new LinkedHashSet<>();
        final ArrayDeque<TransportNode> todo=new ArrayDeque<>();
        final Map<TransportNode,List<TransportNode>> physical=new LinkedHashMap<>(),outgoing=new HashMap<>();
        final Map<TransportNode,List<E>> attached=new HashMap<>();
        final List<E> sources=new ArrayList<>();
        final Set<TransportNode> retained=new HashSet<>();
        RoutingGraph.Builder<TransportNode,E> compressor;
        Build(TransportNode node){seen.add(node);todo.add(node);}
        boolean advance() {
            if(compressor!=null)return compressor.advance();
            if(todo.isEmpty()) {
                if(physical.isEmpty())throw new IllegalStateException("Empty transport component");
                Predicate<E> destinations=adapter.destinationPolicy(Collections.unmodifiableList(sources));
                attached.values().forEach(list->list.removeIf(destinations.negate()));
                compressor=new RoutingGraph.Builder<>(physical,outgoing,attached,retained);return false;
            }
            var node=todo.remove();
            if(!contains(node))return false;
            index(node);owners.put(node,component);
            var data=adapter.inspect(context(component,node),node);
            component.transferRate=Math.min(component.transferRate,data.transferRate());
            if(data.retainVertex())retained.add(node);
            for(var other:data.neighbors()) {
                if(!contains(other))throw new IllegalArgumentException("Transport adapter linked an unavailable channel: "+other);
                if(seen.add(other))todo.add(other);
            }
            component.endpoints.addAll(data.endpoints());
            for(E endpoint:data.endpoints())if(adapter.isSource(endpoint))sources.add(endpoint);
            if(!data.endpoints().isEmpty())attached.put(node,new ArrayList<>(data.endpoints()));
            physical.put(node,data.neighbors());outgoing.put(node,data.outgoing());adapter.refresh(level,node);return false;
        }
    }
}
