package zone.moddev.mc.poweradvantage.network.transport;

import java.util.*;

/**
 * Compressed directed graph with bounded shortest-path jobs and bounded ingress caches. It is independent of world
 * access and resource ownership. Cold queries return an empty list until route work finishes; paths preserve exact
 * physical steps.
 *
 * @param <N> physical node-identity type
 * @param <E> resource-specific endpoint metadata type
 */
public final class RoutingGraph<N,E> {
    /**
     * Directed compressed edge retaining every physical node between its endpoints.
     *
     * @param <N> physical node-identity type
     * @param target Directed destination vertex of the compressed edge
     * @param path Physical node sequence including both edge endpoints
     */
    public record Edge<N>(N target,List<N> path) {
    /**
     * Counts physical hops represented by this compressed edge.
     *
     * @return path node count minus one
     */
    public int length(){return path.size()-1;} }
    /**
     * Persistent path segment chain shared across routes. Physical node lists are materialized only when requested.
     *
     * @param <N> physical node-identity type
     * @param previous Preceding persistent path segment, or null for the initial prefix
     * @param segment Immutable physical nodes in this path segment
     */
    public record Path<N>(Path<N> previous,List<N> segment) {
        /**
         * Flattens shared path segments, omitting their duplicated joining vertices.
         *
         * @return immutable physical nodes from ingress to destination
         */
        public List<N> nodes() {
            var segments=new ArrayDeque<List<N>>();
            for(Path<N> path=this;path!=null;path=path.previous())segments.addFirst(path.segment());
            var result=new ArrayList<N>();
            for(var part:segments) {
                int start=!result.isEmpty() && !part.isEmpty() && Objects.equals(result.getLast(),part.getFirst())?1:0;
                for(int i=start;i<part.size();i++)result.add(part.get(i));
            }
            return List.copyOf(result);
        }
    }
    /**
     * Shortest topological route to one endpoint, including its exact physical path and distance.
     *
     * @param <N> physical node-identity type
     * @param <E> resource-specific endpoint metadata type
     * @param endpoint Resource-specific destination metadata
     * @param distance Physical-hop distance from the requested ingress
     * @param path Persistent exact path from ingress to destination
     */
    public record Route<N,E>(E endpoint,long distance,Path<N> path) {}
    /**
     * Reachable compressed vertex and physical prefix from an ingress inside a compressed chain.
     *
     * @param <N> physical node-identity type
     * @param vertex Compressed vertex reachable from an interior ingress
     * @param prefix Exact physical prefix from the interior ingress to that vertex
     */
    private record Entry<N>(N vertex,List<N> prefix) {}
    /**
     * Completed route cache entry exposing both full paths and endpoint-only views.
     *
     * @param <N> physical node-identity type
     * @param <E> resource-specific endpoint metadata type
     * @param paths Completed routes including physical paths
     * @param endpoints Endpoint-only view in the same order as paths
     */
    private record Routes<N,E>(List<Route<N,E>> paths,List<E> endpoints) {}
    /**
     * Compressed outgoing edges by retained vertex.
     */
    private final Map<N,List<Edge<N>>> edges;
    /**
     * Owned endpoint lists by physical attachment vertex.
     */
    private final Map<N,List<E>> endpoints;
    /**
     * Interior-ingress prefixes leading to reachable compressed vertices.
     */
    private final Map<N,List<Entry<N>>> entries;
    /**
     * Access-ordered completed ingress routes, retaining at most 32 entries.
     */
    private final Map<N,Routes<N,E>> cache=new LinkedHashMap<>(16,0.75f,true) {
        /**
         * Enforces the bounded least-recently-used cache or cursor table.
         *
         * @param entry eldest cached entry being considered for eviction
         * @return true when this eldest entry exceeds the configured bound
         */
        @Override protected boolean removeEldestEntry(Map.Entry<N,Routes<N,E>> entry){return size()>32;}
    };
    /**
     * Insertion-ordered cold route jobs, retaining at most 32 ingress queries.
     */
    private final Map<N,RouteBuild> pending=new LinkedHashMap<>();

    /**
     * Takes ownership of completed compressed topology and warms bounded endpoint ingress jobs.
     *
     * @param edges Compressed outgoing edges by retained vertex
     * @param endpoints Owned endpoint lists by physical attachment vertex
     * @param entries Interior-ingress prefixes leading to reachable compressed vertices
     */
    private RoutingGraph(Map<N,List<Edge<N>>> edges,Map<N,List<E>> endpoints,Map<N,List<Entry<N>>> entries) {
        this.edges=Collections.unmodifiableMap(edges);
        this.endpoints=Collections.unmodifiableMap(endpoints);
        this.entries=Collections.unmodifiableMap(entries);
        for(N node:endpoints.keySet()){request(node);if(pending.size()==32)break;}
    }
    /**
     * Counts vertices retained after compression.
     *
     * @return number of compressed vertices
     */
    public int vertexCount(){return edges.size();}
    /**
     * Queues a cold ingress query if it is known and the bounded job/cache queues have room.
     *
     * @param start physical ingress or starting search position
     */
    private void request(N start) {
        if(start!=null && (edges.containsKey(start)||entries.containsKey(start)) && !cache.containsKey(start)
                && !pending.containsKey(start) && pending.size()<32)pending.put(start,new RouteBuild(start));
    }
    /**
     * Returns empty until the requested route has completed within advanceRoutes' budget.
     *
     * @param start physical ingress or starting search position
     * @return ready endpoint list, or an empty list
     */
    public List<E> destinations(N start) {
        var routes=cache.get(start);
        if(routes!=null)return routes.endpoints();
        request(start);return List.of();
    }
    /**
     * Includes exact directed physical paths, suitable for conveyor movement plans.
     *
     * @param start physical ingress or starting search position
     * @return ready route list, or an empty list
     */
    public List<Route<N,E>> routes(N start) {
        var routes=cache.get(start);
        if(routes!=null)return routes.paths();
        request(start);return List.of();
    }
    /**
     * Advances pending Dijkstra jobs within the supplied work budget.
     *
     * @param budget maximum work steps available to this pass
     * @return number of queue steps actually consumed
     * @throws IllegalArgumentException if the work budget is negative
     */
    public int advanceRoutes(int budget) {
        if(budget<0)throw new IllegalArgumentException("Negative route budget");
        int used=0;
        while(used<budget && !pending.isEmpty()) {
            var iterator=pending.entrySet().iterator();var entry=iterator.next();used++;
            if(entry.getValue().advance()) {
                var build=entry.getValue();
                cache.put(entry.getKey(),new Routes<>(Collections.unmodifiableList(build.result),Collections.unmodifiableList(build.destinations)));
                iterator.remove();
            }
        }
        return used;
    }
    /**
     * Priority-queue entry for a tentative shortest path to a compressed vertex.
     *
     * @param <N> physical node-identity type
     * @param node Tentative compressed vertex reached by the search
     * @param distance Tentative physical-hop distance from the ingress
     * @param path Persistent path producing the tentative distance
     */
    private record Step<N>(N node,long distance,Path<N> path) {}
    /**
     * Incremental Dijkstra search for one ingress. Each advance processes at most one queued vertex entry.
     */
    private final class RouteBuild {
        /**
         * Distance-ordered tentative compressed-vertex paths.
         */
        final PriorityQueue<Step<N>> queue=new PriorityQueue<>(Comparator.comparingLong(Step::distance));
        /**
         * Best known distance for each reached compressed vertex.
         */
        final Map<N,Long> distances=new HashMap<>();
        /**
         * Full routes collected as endpoints are reached in shortest-path order.
         */
        final ArrayList<Route<N,E>> result=new ArrayList<>();
        /**
         * Endpoint-only view collected in the same order as the full routes.
         */
        final ArrayList<E> destinations=new ArrayList<>();
        /**
         * Seeds a shortest-path job at a vertex or every legal compressed prefix from an interior ingress.
         *
         * @param start physical ingress or starting search position
         */
        RouteBuild(N start) {
            if(edges.containsKey(start))offer(start,0,new Path<>(null,List.of(start)));
            else for(var entry:entries.getOrDefault(start,List.of()))
                offer(entry.vertex(),entry.prefix().size()-1,new Path<>(null,entry.prefix()));
        }
        /**
         * Enqueues a tentative path only when it improves the known distance to its vertex.
         *
         * @param node physical position/channel identity
         * @param distance physical-hop distance from the requested ingress
         * @param path exact or persistent physical path
         */
        void offer(N node,long distance,Path<N> path) {
            if(distance<distances.getOrDefault(node,Long.MAX_VALUE)) {
                distances.put(node,distance);queue.add(new Step<>(node,distance,path));
            }
        }
        /**
         * Processes one queued shortest-path entry, collecting endpoints and relaxing outgoing edges.
         *
         * @return true only when the job has no queued entries left
         */
        boolean advance() {
            if(queue.isEmpty())return true;
            var step=queue.remove();
            if(step.distance()!=distances.get(step.node()))return false;
            for(E endpoint:endpoints.getOrDefault(step.node(),List.of())) {
                result.add(new Route<>(endpoint,step.distance(),step.path()));destinations.add(endpoint);
            }
            for(var edge:edges.getOrDefault(step.node(),List.of()))
                offer(edge.target(),step.distance()+edge.length(),new Path<>(step.path(),edge.path()));
            return false;
        }
    }

    /**
     * Budgeted graph-compression state machine. Weak adjacency must be reciprocal and outgoing links must be weak
     * neighbors. Endpoints, directed discontinuities and retained nodes remain vertices; every isolated cycle
     * receives an anchor. Supplied adjacency maps must remain unchanged during construction.
     *
     * @param <N> physical node-identity type
     * @param <E> resource-specific endpoint metadata type
     */
    public static final class Builder<N,E> {
        /**
         * Owned weak-adjacency snapshot, required to be reciprocal.
         * Legal directed physical links from each channel.
         */
        private final Map<N,List<N>> neighbors,outgoing;
        /**
         * Endpoint snapshot supplied for construction.
         */
        private final Map<N,List<E>> endpoints;
        /**
         * Junctions, endpoints, retained policy boundaries and cycle anchors.
         */
        private final Set<N> vertices=new LinkedHashSet<>();
        /**
         * Defensive copies of endpoint metadata retained by the finished graph.
         */
        private final Map<N,List<E>> ownedEndpoints=new HashMap<>();
        /**
         * Reverse incidence of directed physical links for through-node classification.
         */
        private final Map<N,Set<N>> incoming=new HashMap<>();
        /**
         * Compressed edges accumulated for the finished graph.
         */
        private final Map<N,List<Edge<N>>> edges=new HashMap<>();
        /**
         * Reachable compressed-vertex prefixes for interior physical ingresses.
         */
        private final Map<N,List<Entry<N>>> entries=new HashMap<>();
        /**
         * Iterator for budgeted adjacency validation; null after that phase.
         * Iterator for budgeted through-node classification; null after that phase.
         * Iterator of retained vertices whose outgoing chains are being compressed.
         * Iterator of weak-component seeds; null after component anchoring.
         */
        private Iterator<N> validate,classify,starts,componentSeeds;
        /**
         * Physical nodes already assigned to a weak-component search.
         */
        private final Set<N> componentSeen=new HashSet<>();
        /**
         * Queued nodes in the current weak-component traversal.
         */
        private final ArrayDeque<N> componentTodo=new ArrayDeque<>();
        /**
         * Candidate anchor for the current weak component, or null between components.
         */
        private N componentAnchor;
        /**
         * Whether the current weak component already contains a retained vertex.
         */
        private boolean componentHasVertex;
        /**
         * Outgoing physical links still to walk from the current retained vertex.
         */
        private Iterator<N> nextEdges=Collections.emptyIterator();
        /**
         * Retained vertex at the start of the compressed edge under construction.
         * Previous physical node used to continue a bidirectional chain without reversing.
         * Next physical node to append to the current compressed edge, or null.
         */
        private N start,previous,current;
        /**
         * Mutable physical-node sequence for the current edge; frozen on completion.
         */
        private ArrayList<N> path;
        /**
         * Completed edge being indexed for interior ingress prefixes, or null.
         */
        private List<N> entryPath;
        /**
         * Next interior physical-node index in entryPath.
         */
        private int entryIndex;
        /**
         * Whether all compression phases have finished.
         */
        private boolean complete;
        /**
         * Starts budgeted compression over an adjacency snapshot that the caller must not mutate during
         * construction.
         *
         * @param neighbors Owned weak-adjacency snapshot, required to be reciprocal
         * @param outgoing Legal directed physical links from each channel
         * @param endpoints Endpoint snapshot supplied for construction
         * @param retained nodes explicitly kept as graph vertices
         */
        public Builder(Map<N,List<N>> neighbors,Map<N,List<N>> outgoing,Map<N,List<E>> endpoints,Set<N> retained) {
            this.neighbors=neighbors;this.outgoing=outgoing;this.endpoints=endpoints;
            vertices.addAll(retained);validate=neighbors.keySet().iterator();
        }

        /**
         * Advances one budgeted step, returning true only when compression is complete.
         *
         * @return true only when compression is complete
         * @throws IllegalArgumentException if supplied adjacency or retained-node metadata is inconsistent
         */
        public boolean advance() {
            if (complete) return true;
            if (validate != null && advanceValidation()) return false;
            if (classify != null && advanceClassification()) return false;
            if (componentSeeds != null && advanceComponentSearch()) return false;
            if (entryPath != null && indexNextEntry()) return false;
            if (current != null) {
                extendPath();
                return false;
            }
            if (nextEdges.hasNext()) {
                beginNextEdge();
                return false;
            }
            if (starts.hasNext()) {
                beginNextVertex();
                return false;
            }
            complete = true;
            return true;
        }

        /**
         * Stage helpers return true after consuming a step; false lets advance() enter the next stage.
         *
         * @return true if one node consumed a step; false when the phase ended
         */
        private boolean advanceValidation() {
            if (validate.hasNext()) {
                N node = validate.next();
                if (endpoints.containsKey(node)) ownedEndpoints.put(node, List.copyOf(endpoints.get(node)));
                validateWeakNeighbors(node);
                indexIncomingLinks(node);
                return true;
            }
            if (!neighbors.keySet().containsAll(vertices) || !neighbors.keySet().containsAll(endpoints.keySet()))
                throw new IllegalArgumentException("Unknown retained or endpoint node");
            validate = null;
            classify = neighbors.keySet().iterator();
            return false;
        }

        /**
         * Rejects missing or nonreciprocal weak links at the supplied node.
         *
         * @param node physical position/channel identity
         * @throws IllegalArgumentException if a weak neighbor is missing or nonreciprocal
         */
        private void validateWeakNeighbors(N node) {
            for (N other : neighbors.get(node)) {
                if (!neighbors.containsKey(other) || !neighbors.get(other).contains(node))
                    throw new IllegalArgumentException("Nonreciprocal weak adjacency");
            }
        }

        /**
         * Validates outgoing links and indexes their reverse incidence for classification.
         *
         * @param node physical position/channel identity
         * @throws IllegalArgumentException if an outgoing link is not a weak neighbor
         */
        private void indexIncomingLinks(N node) {
            for (N target : outgoing.getOrDefault(node, List.of())) {
                if (!neighbors.get(node).contains(target))
                    throw new IllegalArgumentException("Directed edge outside weak adjacency");
                incoming.computeIfAbsent(target, key -> new HashSet<>()).add(node);
            }
        }

        /**
         * Classifies one node as a retained vertex or through-chain node.
         *
         * @return true if one node consumed a step; false when classification ended
         */
        private boolean advanceClassification() {
            if (classify.hasNext()) {
                N node = classify.next();
                if (!isThroughNode(node) || endpoints.containsKey(node)) vertices.add(node);
                return true;
            }
            componentSeeds = neighbors.keySet().iterator();
            classify = null;
            return false;
        }

        /**
         * Checks whether a degree-two node consistently supports bidirectional or one-way through travel.
         *
         * @param node physical position/channel identity
         * @return true if the node may be compressed without changing directionality
         */
        private boolean isThroughNode(N node) {
            var adjacent = neighbors.get(node);
            var out = outgoing.getOrDefault(node, List.of());
            var in = incoming.getOrDefault(node, Set.of());
            boolean bidirectional = adjacent.size() == 2 && out.size() == 2 && in.size() == 2;
            boolean oneWay = adjacent.size() == 2 && out.size() == 1 && in.size() == 1 && !in.contains(out.getFirst());
            return bidirectional || oneWay;
        }

        /**
         * Visits one weak-component node or seed, retaining an anchor for otherwise vertex-free cycles.
         *
         * @return true if a search step was consumed; false when component search ended
         */
        private boolean advanceComponentSearch() {
            if (!componentTodo.isEmpty()) {
                visitComponentNode();
                return true;
            }
            finishComponentAnchor();
            if (componentSeeds.hasNext()) {
                N node = componentSeeds.next();
                if (componentSeen.add(node)) {
                    componentAnchor = node;
                    componentHasVertex = false;
                    componentTodo.add(node);
                }
                return true;
            }
            starts = vertices.iterator();
            componentSeeds = null;
            return false;
        }

        /**
         * Visits the next weak-component node and discovers its unseen neighbors.
         */
        private void visitComponentNode() {
            N node = componentTodo.remove();
            componentHasVertex |= vertices.contains(node);
            for (N other : neighbors.get(node)) {
                if (componentSeen.add(other)) componentTodo.add(other);
            }
        }

        /**
         * Retains the current component anchor if no other vertex was encountered.
         */
        private void finishComponentAnchor() {
            if (componentAnchor == null) return;
            if (!componentHasVertex) vertices.add(componentAnchor);
            componentAnchor = null;
        }

        /**
         * Indexes one interior physical ingress against a reachable edge endpoint.
         *
         * @return true if an entry consumed a step; false when this edge has no more interior entries
         */
        private boolean indexNextEntry() {
            if (entryIndex < entryPath.size() - 1) {
                N node = entryPath.get(entryIndex);
                var entry = new Entry<>(entryPath.getLast(), entryPath.subList(entryIndex, entryPath.size()));
                entries.computeIfAbsent(node, key -> new ArrayList<>()).add(entry);
                entryIndex++;
                return true;
            }
            entryPath = null;
            return false;
        }

        /**
         * Extends the current compressed edge by one physical node and finishes it on reaching a vertex.
         */
        private void extendPath() {
            path.add(current);
            if (vertices.contains(current)) {
                finishEdge();
                return;
            }
            var out = outgoing.get(current);
            N next = out.getFirst();
            if (out.size() == 2 && Objects.equals(next, previous)) next = out.get(1);
            previous = current;
            current = next;
        }

        /**
         * Publishes the finished directed edge and starts indexing its interior ingress prefixes.
         */
        private void finishEdge() {
            var frozen = Collections.unmodifiableList(path);
            edges.get(start).add(new Edge<>(current, frozen));
            entryPath = frozen;
            entryIndex = 1;
            current = null;
        }

        /**
         * Starts walking the next outgoing edge from the current retained vertex.
         */
        private void beginNextEdge() {
            current = nextEdges.next();
            previous = start;
            path = new ArrayList<>();
            path.add(start);
        }

        /**
         * Starts compressed-edge construction for the next retained vertex.
         */
        private void beginNextVertex() {
            start = starts.next();
            edges.put(start, new ArrayList<>());
            nextEdges = outgoing.getOrDefault(start, List.of()).iterator();
        }

        /**
         * Returns the completed graph after the incremental builder has reported completion.
         *
         * @return finished graph with owned endpoint metadata
         * @throws IllegalStateException if compression has not completed
         */
        public RoutingGraph<N,E> finish() {
            if(!complete)throw new IllegalStateException("Compression is not complete");
            return new RoutingGraph<>(edges,ownedEndpoints,entries);
        }
    }
}
