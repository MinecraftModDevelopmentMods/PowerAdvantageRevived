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

/**
 * Server-thread loaded-world component manager, instantiated once per dimension and transport kind. Handles
 * invalidation, bounded discovery/compression and route work; adapters supply geometry and endpoints while engines
 * own transported contents.
 *
 * @param <E> resource-specific endpoint metadata type
 */
public final class TransportNetworks<E> {
    /**
     * Fair-service identity of an ingress channel and its injecting source block, if known.
     *
     * @param node Physical ingress channel requesting service
     * @param source Injecting block position, or null when unknown
     */
    public record Origin(TransportNode node,BlockPos source) {
        /**
         * Copies a known injecting position so mutable block positions cannot change fairness identity.
         *
         * @param node Physical ingress channel requesting service
         * @param source Injecting block position, or null when unknown
         */
        public Origin { if(source!=null)source=source.immutable(); }
    }
    /**
     * Published component snapshot with capability watches, graph, sources and minimum node rating. Check validity
     * before executing cached routes; versions are process-local and are not persistent network IDs.
     *
     * @param <E> resource-specific endpoint metadata type
     */
    public static final class Component<E> {
        /**
         * Whether this snapshot is published and has not been invalidated.
         * Whether provisional discovery still owns its endpoint watches.
         */
        private boolean valid,building=true;
        /**
         * Process-local generation assigned when discovery starts.
         */
        private final long version;
        /**
         * Assigns a process-local generation to a provisional component.
         *
         * @param version Process-local generation assigned when discovery starts
         */
        private Component(long version){this.version=version;}
        /**
         * Process-local topology generation; recompute routes after restart.
         *
         * @return monotonic generation within this manager lifetime
         */
        public long version(){return version;}
        /**
         * Immutable view of physical channels in the published component.
         */
        private Set<TransportNode> nodes=Set.of();
        /**
         * All discovered endpoints, including source ports.
         */
        private final List<E> endpoints=new ArrayList<>();
        /**
         * Immutable adapter-classified extraction/source endpoints.
         */
        private List<E> sources=List.of();
        /**
         * Completed compressed directed graph, assigned before publication.
         */
        private RoutingGraph<TransportNode,E> graph;
        /**
         * Minimum positive node rating in adapter-defined units per tick.
         */
        private int transferRate=Integer.MAX_VALUE;
        /**
         * Optional shared component quantity/fairness policy.
         */
        private FairTransferBudget<Origin> budget;
        /**
         * Strong references keeping component capability caches alive.
         */
        private final List<Object> watchers=new ArrayList<>();
        /**
         * Last observed non-transport boundary block types for notification filtering.
         */
        private final Map<BlockPos,Block> boundary=new HashMap<>();
        /**
         * Reports whether this component is still published and safe to consult.
         *
         * @return true while the snapshot is valid
         */
        public boolean valid(){return valid;}
        /**
         * Returns physical channel identities belonging to the component.
         *
         * @return unmodifiable channel set
         */
        public Set<TransportNode> nodes(){return nodes;}
        /**
         * Returns all discovered endpoints, including extraction ports.
         *
         * @return unmodifiable endpoint view
         */
        public List<E> endpoints(){return Collections.unmodifiableList(endpoints);}
        /**
         * Returns endpoints classified as sources by the adapter.
         *
         * @return immutable source list
         */
        public List<E> sources(){return sources;}
        /**
         * Returns the completed compressed graph.
         *
         * @return published graph
         */
        public RoutingGraph<TransportNode,E> graph(){return graph;}
        /**
         * Returns the minimum positive rating encountered during component discovery.
         *
         * @return component rating in adapter-defined units per tick
         */
        public int transferRate(){return transferRate;}
        /**
         * Returns the optional fluid-style shared quantity/fairness policy. Other engines may apply per-segment
         * policies instead.
         *
         * @return component quantity budget
         */
        public FairTransferBudget<Origin> budget(){return budget;}
    }
    /**
     * Owning server dimension; world access occurs on its server thread.
     */
    private final ServerLevel level;
    /**
     * Block, channel, endpoint and static destination policy for this transport kind.
     */
    private final TransportAdapter<E> adapter;
    /**
     * Current provisional or published component by physical channel.
     */
    private final Map<TransportNode,Component<E>> owners=new HashMap<>();
    /**
     * Currently published valid components in service order.
     */
    private final Set<Component<E>> components=new LinkedHashSet<>();
    /**
     * Queued iterators of channels requiring validation or rediscovery.
     */
    private final ArrayDeque<Iterator<TransportNode>> seeds=new ArrayDeque<>();
    /**
     * Deferred loaded-chunk notifications, safe to enqueue before FULL promotion.
     */
    private final ConcurrentLinkedQueue<LevelChunk> loaded=new ConcurrentLinkedQueue<>();
    /**
     * Deferred unload notifications; pending entries suspend routing immediately.
     */
    private final ConcurrentLinkedQueue<ChunkPos> unloaded=new ConcurrentLinkedQueue<>();
    /**
     * Physical channel index by packed chunk position.
     */
    private final Map<Long,Set<TransportNode>> chunks=new HashMap<>();
    /**
     * Channel index by block position, retaining old IDs until withdrawal.
     */
    private final Map<BlockPos,Set<TransportNode>> positions=new HashMap<>();
    /**
     * Chunks logically withdrawn until a matching load is processed.
     */
    private final Set<Long> excludedChunks=new HashSet<>();
    /**
     * Deferred palette-selected chunk-section scans.
     */
    private final ArrayDeque<SectionScan> scans=new ArrayDeque<>();
    /**
     * Rotates first-serviced components for bounded shortest-path work.
     */
    private final FairScheduler graphScheduler=new FairScheduler();
    /**
     * Current provisional component discovery, or null.
     */
    private Build build;
    /**
     * Counter assigning process-local component generations.
     */
    private long nextVersion;
    /**
     * Creates an empty derived topology manager for one server dimension and transport kind.
     *
     * @param level Owning server dimension; world access occurs on its server thread
     * @param adapter Block, channel, endpoint and static destination policy for this transport kind
     */
    public TransportNetworks(ServerLevel level,TransportAdapter<E> adapter){this.level=level;this.adapter=adapter;}
    /**
     * Enqueues deferred inspection of this chunk without reading it before FULL promotion.
     *
     * @param chunk chunk instance or coordinate from the lifecycle event
     */
    public void chunkLoaded(LevelChunk chunk){loaded.add(chunk);}
    /**
     * Queues chunk withdrawal so routing suspends across the boundary before further transfers.
     *
     * @param chunk chunk coordinate being watched or withdrawn
     */
    public void chunkUnloaded(ChunkPos chunk){unloaded.add(chunk);}
    /**
     * Pending unloads fail closed before the next topology tick.
     *
     * @return true if no queued unload requires routing to fail closed
     */
    public boolean canRoute(){return unloaded.isEmpty();}
    /**
     * Checks the loaded-world boundary without requesting chunk loading.
     *
     * @param pos block position being queried or changed
     * @return true if the position is loaded and its chunk is not excluded
     */
    public boolean available(BlockPos pos){return !excludedChunks.contains(ChunkPos.asLong(pos))&&level.isLoaded(pos);}
    /**
     * Checks that a loaded block still exposes the specified transport channel.
     *
     * @param node physical position/channel identity
     * @return true if the adapter recognizes both block and channel
     */
    public boolean contains(TransportNode node) {
        if(!available(node.position()))return false;
        var state=level.getBlockState(node.position());
        return adapter.matches(state)&&adapter.nodes(node.position(),state).contains(node);
    }
    /**
     * Resolves the adapter channels at a loaded position.
     *
     * @param pos block position being queried or changed
     * @return channels at the position, or an empty list
     */
    private List<TransportNode> nodes(BlockPos pos) {
        if(!available(pos))return List.of();
        var state=level.getBlockState(pos);
        return adapter.matches(state)?adapter.nodes(pos,state):List.of();
    }
    /**
     * Finds the currently published valid component for a channel.
     *
     * @param node physical position/channel identity
     * @return valid component, or null while absent or rebuilding
     */
    public Component<E> component(TransportNode node) {
        var component=owners.get(node);return component!=null&&component.valid?component:null;
    }
    /**
     * Snapshots the set of currently published components.
     *
     * @return immutable list of valid components
     */
    public List<Component<E>> components(){return List.copyOf(components);}
    /**
     * Invalidates affected old channels immediately and seeds replacement discovery, including channels that
     * disappeared.
     *
     * @param pos block position being queried or changed
     */
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
    /**
     * Ignores unchanged content notifications while invalidating meaningful transport or boundary-block changes.
     *
     * @param pos block position being queried or changed
     * @param from neighbor position that caused the notification
     */
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
    /**
     * Withdraws a published component and queues its former channels for rediscovery.
     *
     * @param component provisional or published transport component
     */
    private void invalidate(Component<E> component) {
        if(!component.valid)return;
        component.valid=false;components.remove(component);seeds.add(component.nodes.iterator());
    }
    /**
     * Cancels provisional discovery, releases its watch validity and preserves visited seeds for retry.
     */
    private void abortBuild() {
        if(build==null)return;
        build.component.valid=false;build.component.building=false;seeds.add(build.seen.iterator());build=null;
    }
    /**
     * Processes deferred chunk events, budgeted component rebuilds and fair shortest-path work on the server
     * thread.
     */
    public void tick(){tick(512,512);}
    /**
     * Budgets count discovery/compression steps and Dijkstra steps, respectively.
     *
     * @param topologySteps maximum discovery/compression steps per dimension tick
     * @param routeSteps maximum shortest-path steps per dimension tick
     * @throws IllegalArgumentException if an explicitly supplied work budget is negative
     */
    public void tick(int topologySteps, int routeSteps) {
        if (topologySteps < 0 || routeSteps < 0) throw new IllegalArgumentException("Negative work budget");
        processChunks();
        rebuildComponents(topologySteps);
        advanceRoutes(routeSteps);
    }

    /**
     * Consumes topology steps while starting, advancing and publishing replacement components.
     *
     * @param budget maximum work steps available to this pass
     */
    private void rebuildComponents(int budget) {
        for (int step = 0; step < budget; step++) {
            if (build == null) {
                if (seeds.isEmpty()) break;
                startNextBuild();
                if (build == null) continue;
            }
            Build current = build;
            if (current.advance() && build == current) publishBuild(current);
        }
    }

    /**
     * Consumes one queued seed, discarding stale channels or starting discovery for an unpublished component.
     */
    private void startNextBuild() {
        var iterator = seeds.peek();
        if (!iterator.hasNext()) {
            seeds.remove();
            return;
        }
        var node = iterator.next();
        var existing = owners.get(node);
        if (!contains(node)) {
            discardNode(node);
            return;
        }
        if (existing == null || !existing.valid) build = new Build(node);
    }

    /**
     * Removes an obsolete channel from ownership, position and chunk indexes.
     *
     * @param node physical position/channel identity
     */
    private void discardNode(TransportNode node) {
        owners.remove(node);
        unindexPosition(node);
        long chunk = ChunkPos.asLong(node.position());
        var index = chunks.get(chunk);
        if (index == null) return;
        index.remove(node);
        if (index.isEmpty()) chunks.remove(chunk);
    }

    /**
     * Publishes a fully compressed component, sources and quantity budget before releasing discovery state.
     *
     * @param completed fully discovered and compressed component builder
     */
    private void publishBuild(Build completed) {
        var component = completed.component;
        component.graph = completed.compressor.finish();
        component.nodes = Collections.unmodifiableSet(completed.seen);
        component.sources = List.copyOf(completed.sources);
        component.budget = new FairTransferBudget<>(component.transferRate);
        component.building = false;
        component.valid = true;
        components.add(component);
        build = null;
    }

    /**
     * Distributes route work fairly across published components, with at most 32 steps per component pass.
     *
     * @param budget maximum work steps available to this pass
     */
    private void advanceRoutes(int budget) {
        int[] remaining = {budget};
        graphScheduler.run(new ArrayList<>(components), () -> remaining[0] > 0,
            component -> remaining[0] -= component.graph.advanceRoutes(Math.min(32, remaining[0])));
    }

    /**
     * Deferred scan of one chunk section after the exact chunk instance becomes available for inspection.
     *
     * @param chunk Exact loaded chunk instance whose section is deferred
     * @param section Section index within that chunk
     */
    private record SectionScan(LevelChunk chunk, int section) {}

    /**
     * Processes queued unloads, queued loads and at most one deferred section scan, in that order.
     */
    private void processChunks() {
        processUnloadedChunks();
        processLoadedChunks();
        scanNextSection();
    }

    /**
     * Drains unload notifications before considering any load or scan work.
     */
    private void processUnloadedChunks() {
        ChunkPos gone;
        while ((gone = unloaded.poll()) != null) unloadChunk(gone);
    }

    /**
     * Excludes the chunk and withdraws ownership/components indexed within it.
     *
     * @param gone chunk coordinate being watched or withdrawn
     */
    private void unloadChunk(ChunkPos gone) {
        excludedChunks.add(gone.toLong());
        var indexed = chunks.remove(gone.toLong());
        if (indexed == null) return;
        abortBuild();
        for (var node : indexed) {
            unindexPosition(node);
            var owner = owners.remove(node);
            if (owner != null) invalidate(owner);
        }
    }

    /**
     * Handles at most four loaded chunks, postponing any whose exact instance is not yet available.
     */
    private void processLoadedChunks() {
        for (int count = 0; count < 4; count++) {
            var chunk = loaded.poll();
            if (chunk == null) break;
            if (!isCurrentChunk(chunk)) {
                loaded.add(chunk);
                break;
            }
            excludedChunks.remove(chunk.getPos().toLong());
            queueChunkSections(chunk);
            reconnectAdjacentChunks(chunk.getPos());
        }
    }

    /**
     * Checks FULL-access availability by exact chunk-instance identity without requesting a load.
     *
     * @param chunk chunk instance or coordinate from the lifecycle event
     * @return true if getChunkNow returns this chunk instance
     */
    private boolean isCurrentChunk(LevelChunk chunk) {
        return level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) == chunk;
    }

    /**
     * Queues only sections whose palettes may contain adapter-recognized transport blocks.
     *
     * @param chunk chunk instance or coordinate from the lifecycle event
     */
    private void queueChunkSections(LevelChunk chunk) {
        var sections = chunk.getSections();
        for (int section = 0; section < sections.length; section++) {
            if (sections[section].maybeHas(adapter::matches)) scans.add(new SectionScan(chunk, section));
        }
    }

    /**
     * Seeds and invalidates known neighboring chunk channels so a reloaded boundary can reconnect.
     *
     * @param chunk chunk coordinate being watched or withdrawn
     */
    private void reconnectAdjacentChunks(ChunkPos chunk) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            long key = ChunkPos.asLong(chunk.x + side.getStepX(), chunk.z + side.getStepZ());
            var adjacent = chunks.get(key);
            if (adjacent == null) continue;
            seeds.add(new ArrayList<>(adjacent).iterator());
            for (var node : adjacent) {
                var owner = owners.get(node);
                if (owner != null) invalidate(owner);
            }
        }
    }

    /**
     * Consumes one queued section scan if its original chunk instance is still available.
     */
    private void scanNextSection() {
        if (scans.isEmpty()) return;
        var scan = scans.remove();
        var chunk = scan.chunk();
        if (excludedChunks.contains(chunk.getPos().toLong()) || !isCurrentChunk(chunk)) return;
        seeds.add(discoverSectionNodes(scan).iterator());
    }

    /**
     * Scans and indexes adapter channels in one 4096-block section.
     *
     * @param scan deferred chunk-section scan
     * @return discovered channels to seed for component rebuilding
     */
    private List<TransportNode> discoverSectionNodes(SectionScan scan) {
        var chunk = scan.chunk();
        var section = chunk.getSections()[scan.section()];
        var discovered = new ArrayList<TransportNode>();
        int baseY = level.getSectionYFromSectionIndex(scan.section()) * 16;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    var state = section.getBlockState(x, y, z);
                    if (!adapter.matches(state)) continue;
                    var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                    var channels = adapter.nodes(pos, state);
                    discovered.addAll(channels);
                    for (var node : channels) index(node);
                }
            }
        }
        return discovered;
    }
    /**
     * Adds a channel to its chunk and block-position indexes.
     *
     * @param node physical position/channel identity
     */
    private void index(TransportNode node) {
        chunks.computeIfAbsent(ChunkPos.asLong(node.position()),key->new HashSet<>()).add(node);
        positions.computeIfAbsent(node.position(),key->new HashSet<>()).add(node);
    }
    /**
     * Removes a channel from the block-position index and prunes empty buckets.
     *
     * @param node physical position/channel identity
     */
    private void unindexPosition(TransportNode node) {
        var channels=positions.get(node.position());
        if(channels!=null){channels.remove(node);if(channels.isEmpty())positions.remove(node.position());}
    }
    /**
     * Creates watch services retaining the component without retaining its discovery builder.
     *
     * @param observed component whose validity owns the capability watches
     * @param node physical position/channel identity
     * @return inspection context for this component and physical channel
     */
    private TransportAdapter.Context context(Component<E> observed,TransportNode node) {
        return new TransportAdapter.Context() {
            /**
             * Returns the server dimension being inspected.
             *
             * @return owning server level
             */
            public ServerLevel level(){return level;}
            /**
             * Checks the manager's loaded/excluded chunk boundary.
             *
             * @param pos block position being queried or changed
             * @return true if the position is available
             */
            public boolean available(BlockPos pos){return TransportNetworks.this.available(pos);}
            /**
             * Retains an endpoint cache and rebuilds the observed component on capability invalidation.
             *
             * @param <H> capability handler type
             * @param capability sided block-capability type to cache
             * @param pos block position being queried or changed
             * @param face sided capability face, or null for unsided access where supported
             * @return component-scoped capability cache
             * @throws IllegalArgumentException if the requested endpoint is unavailable
             */
            public <H> BlockCapabilityCache<H,Direction> watch(BlockCapability<H,Direction> capability,BlockPos pos,Direction face) {
                if(!available(pos))throw new IllegalArgumentException("Cannot watch an unloaded endpoint");
                observed.boundary.put(pos.immutable(),level.getBlockState(pos).getBlock());
                var cache=BlockCapabilityCache.create(capability,level,pos,face,()->observed.valid||observed.building,
                    ()->{if(observed.valid||observed.building)changed(node.position());});
                observed.watchers.add(cache);return cache;
            }
        };
    }
    /**
     * Incremental discovery state for a replacement component. Provisional owners are not routable until
     * compression finishes and the component is published.
     */
    private final class Build {
        /**
         * Provisional component populated by this discovery job.
         */
        final Component<E> component=new Component<>(++nextVersion);
        /**
         * Discovered channel identities, later exposed as the published node set.
         */
        final Set<TransportNode> seen=new LinkedHashSet<>();
        /**
         * Physical channels awaiting adapter inspection.
         */
        final ArrayDeque<TransportNode> todo=new ArrayDeque<>();
        /**
         * Discovered reciprocal weak adjacency.
         * Discovered directed legal travel links.
         */
        final Map<TransportNode,List<TransportNode>> physical=new LinkedHashMap<>(),outgoing=new HashMap<>();
        /**
         * Endpoint lists by physical attachment channel.
         */
        final Map<TransportNode,List<E>> attached=new HashMap<>();
        /**
         * Discovered endpoints classified as sources.
         */
        final List<E> sources=new ArrayList<>();
        /**
         * Explicitly retained policy/routing vertices.
         */
        final Set<TransportNode> retained=new HashSet<>();
        /**
         * Incremental graph compressor, created once discovery finishes.
         */
        RoutingGraph.Builder<TransportNode,E> compressor;
        /**
         * Seeds provisional discovery from one unowned or invalidated channel.
         *
         * @param node physical position/channel identity
         */
        Build(TransportNode node){seen.add(node);todo.add(node);}
        /**
         * Discovers one physical node or advances compression; publication belongs to the outer manager.
         *
         * @return true only when compression is complete
         */
        boolean advance() {
            if (compressor != null) return compressor.advance();
            if (todo.isEmpty()) {
                startCompression();
                return false;
            }
            var node = todo.remove();
            if (contains(node)) discoverNode(node);
            return false;
        }

        /**
         * Filters static destination eligibility and creates the incremental compressor for the discovered
         * component.
         *
         * @throws IllegalStateException if discovery produced no physical channels
         */
        private void startCompression() {
            if (physical.isEmpty()) throw new IllegalStateException("Empty transport component");
            Predicate<E> destinations = adapter.destinationPolicy(Collections.unmodifiableList(sources));
            attached.values().forEach(list -> list.removeIf(destinations.negate()));
            compressor = new RoutingGraph.Builder<>(physical, outgoing, attached, retained);
        }

        /**
         * Indexes one channel, inspects adapter data and records its neighbors, endpoints and outgoing links.
         *
         * @param node physical position/channel identity
         */
        private void discoverNode(TransportNode node) {
            index(node);
            owners.put(node, component);
            var data = adapter.inspect(context(component, node), node);
            component.transferRate = Math.min(component.transferRate, data.transferRate());
            if (data.retainVertex()) retained.add(node);
            discoverNeighbors(data.neighbors());
            rememberEndpoints(node, data.endpoints());
            physical.put(node, data.neighbors());
            outgoing.put(node, data.outgoing());
            adapter.refresh(level, node);
        }

        /**
         * Validates live neighboring channels and queues previously unseen identities.
         *
         * @param neighbors reciprocal weak adjacency used for component discovery
         * @throws IllegalArgumentException if the adapter linked an unavailable channel
         */
        private void discoverNeighbors(List<TransportNode> neighbors) {
            for (var other : neighbors) {
                if (!contains(other)) throw new IllegalArgumentException("Transport adapter linked an unavailable channel: " + other);
                if (seen.add(other)) todo.add(other);
            }
        }

        /**
         * Collects source classifications and associates nonempty endpoint lists with their physical channels.
         *
         * @param node physical position/channel identity
         * @param endpoints endpoint metadata indexed by attachment node, or endpoints attached to this channel
         */
        private void rememberEndpoints(TransportNode node, List<E> endpoints) {
            component.endpoints.addAll(endpoints);
            for (E endpoint : endpoints) {
                if (adapter.isSource(endpoint)) sources.add(endpoint);
            }
            if (!endpoints.isEmpty()) attached.put(node, new ArrayList<>(endpoints));
        }
    }
}
