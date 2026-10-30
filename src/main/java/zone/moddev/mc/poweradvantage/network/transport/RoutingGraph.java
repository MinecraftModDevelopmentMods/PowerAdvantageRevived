package zone.moddev.mc.poweradvantage.network.transport;

import java.util.*;

/** Compressed directed graph, with budgeted shortest paths and no dependency on a resource or world. */
public final class RoutingGraph<N,E> {
    public record Edge<N>(N target,List<N> path) { public int length(){return path.size()-1;} }
    /** Persistent path segments share memory across routes; materialize physical steps only when needed. */
    public record Path<N>(Path<N> previous,List<N> segment) {
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
    public record Route<N,E>(E endpoint,long distance,Path<N> path) {}
    private record Entry<N>(N vertex,List<N> prefix) {}
    private record Routes<N,E>(List<Route<N,E>> paths,List<E> endpoints) {}
    private final Map<N,List<Edge<N>>> edges;
    private final Map<N,List<E>> endpoints;
    private final Map<N,List<Entry<N>>> entries;
    private final Map<N,Routes<N,E>> cache=new LinkedHashMap<>(16,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<N,Routes<N,E>> entry){return size()>32;}
    };
    private final Map<N,RouteBuild> pending=new LinkedHashMap<>();

    private RoutingGraph(Map<N,List<Edge<N>>> edges,Map<N,List<E>> endpoints,Map<N,List<Entry<N>>> entries) {
        this.edges=Collections.unmodifiableMap(edges);
        this.endpoints=Collections.unmodifiableMap(endpoints);
        this.entries=Collections.unmodifiableMap(entries);
        for(N node:endpoints.keySet()){request(node);if(pending.size()==32)break;}
    }
    public int vertexCount(){return edges.size();}
    private void request(N start) {
        if(start!=null && (edges.containsKey(start)||entries.containsKey(start)) && !cache.containsKey(start)
                && !pending.containsKey(start) && pending.size()<32)pending.put(start,new RouteBuild(start));
    }
    /** Returns empty until the requested route has completed within advanceRoutes' budget. */
    public List<E> destinations(N start) {
        var routes=cache.get(start);
        if(routes!=null)return routes.endpoints();
        request(start);return List.of();
    }
    /** Includes exact directed physical paths, suitable for conveyor movement plans. */
    public List<Route<N,E>> routes(N start) {
        var routes=cache.get(start);
        if(routes!=null)return routes.paths();
        request(start);return List.of();
    }
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
    private record Step<N>(N node,long distance,Path<N> path) {}
    private final class RouteBuild {
        final PriorityQueue<Step<N>> queue=new PriorityQueue<>(Comparator.comparingLong(Step::distance));
        final Map<N,Long> distances=new HashMap<>();
        final ArrayList<Route<N,E>> result=new ArrayList<>();
        final ArrayList<E> destinations=new ArrayList<>();
        RouteBuild(N start) {
            if(edges.containsKey(start))offer(start,0,new Path<>(null,List.of(start)));
            else for(var entry:entries.getOrDefault(start,List.of()))
                offer(entry.vertex(),entry.prefix().size()-1,new Path<>(null,entry.prefix()));
        }
        void offer(N node,long distance,Path<N> path) {
            if(distance<distances.getOrDefault(node,Long.MAX_VALUE)) {
                distances.put(node,distance);queue.add(new Step<>(node,distance,path));
            }
        }
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
     * Incremental compression of an owned adjacency snapshot. Do not mutate supplied maps during
     * construction. Weak adjacency must be reciprocal, and outgoing links must be weak neighbors.
     * Endpoint and explicitly retained nodes are never compressed away. Directed discontinuities
     * also remain vertices, so compression cannot introduce reverse travel.
     */
    public static final class Builder<N,E> {
        private final Map<N,List<N>> neighbors,outgoing;
        private final Map<N,List<E>> endpoints;
        private final Set<N> vertices=new LinkedHashSet<>();
        private final Map<N,List<E>> ownedEndpoints=new HashMap<>();
        private final Map<N,Set<N>> incoming=new HashMap<>();
        private final Map<N,List<Edge<N>>> edges=new HashMap<>();
        private final Map<N,List<Entry<N>>> entries=new HashMap<>();
        private Iterator<N> validate,classify,starts,componentSeeds;
        private final Set<N> componentSeen=new HashSet<>();
        private final ArrayDeque<N> componentTodo=new ArrayDeque<>();
        private N componentAnchor;
        private boolean componentHasVertex;
        private Iterator<N> nextEdges=Collections.emptyIterator();
        private N start,previous,current;
        private ArrayList<N> path;
        private List<N> entryPath;
        private int entryIndex;
        private boolean complete;
        public Builder(Map<N,List<N>> neighbors,Map<N,List<N>> outgoing,Map<N,List<E>> endpoints,Set<N> retained) {
            this.neighbors=neighbors;this.outgoing=outgoing;this.endpoints=endpoints;
            vertices.addAll(retained);validate=neighbors.keySet().iterator();
        }
        
        public boolean advance() {
            if(complete)return true;
            if(validate!=null) {
                if(validate.hasNext()) {
                    N node=validate.next();
                    if(endpoints.containsKey(node))ownedEndpoints.put(node,List.copyOf(endpoints.get(node)));
                    for(N other:neighbors.get(node))
                        if(!neighbors.containsKey(other)||!neighbors.get(other).contains(node))throw new IllegalArgumentException("Nonreciprocal weak adjacency");
                    for(N target:outgoing.getOrDefault(node,List.of())) {
                        if(!neighbors.get(node).contains(target))throw new IllegalArgumentException("Directed edge outside weak adjacency");
                        incoming.computeIfAbsent(target,key->new HashSet<>()).add(node);
                    }
                    return false;
                }
                if(!neighbors.keySet().containsAll(vertices)||!neighbors.keySet().containsAll(endpoints.keySet()))
                    throw new IllegalArgumentException("Unknown retained or endpoint node");
                validate=null;classify=neighbors.keySet().iterator();
            }
            if(classify!=null) {
                if(classify.hasNext()) {
                    N node=classify.next();var adjacent=neighbors.get(node);var out=outgoing.getOrDefault(node,List.of());
                    var in=incoming.getOrDefault(node,Set.of());
                    boolean bidirectional=adjacent.size()==2 && out.size()==2 && in.size()==2;
                    boolean oneWay=adjacent.size()==2 && out.size()==1 && in.size()==1 && !in.contains(out.getFirst());
                    if(!(bidirectional||oneWay)||endpoints.containsKey(node))vertices.add(node);
                    return false;
                }
                componentSeeds=neighbors.keySet().iterator();classify=null;
            }
            if(componentSeeds!=null) {
                if(!componentTodo.isEmpty()) {
                    N node=componentTodo.remove();componentHasVertex|=vertices.contains(node);
                    for(N other:neighbors.get(node))if(componentSeen.add(other))componentTodo.add(other);
                    return false;
                }
                if(componentAnchor!=null) {
                    if(!componentHasVertex)vertices.add(componentAnchor);
                    componentAnchor=null;
                }
                if(componentSeeds.hasNext()) {
                    N node=componentSeeds.next();
                    if(componentSeen.add(node)){componentAnchor=node;componentHasVertex=false;componentTodo.add(node);}
                    return false;
                }
                starts=vertices.iterator();componentSeeds=null;
            }
            if(entryPath!=null) {
                if(entryIndex<entryPath.size()-1) {
                    N node=entryPath.get(entryIndex);
                    entries.computeIfAbsent(node,key->new ArrayList<>()).add(new Entry<>(entryPath.getLast(),entryPath.subList(entryIndex,entryPath.size())));
                    entryIndex++;return false;
                }
                entryPath=null;
            }
            if(current!=null) {
                path.add(current);
                if(vertices.contains(current)) {
                    var frozen=Collections.unmodifiableList(path);
                    edges.get(start).add(new Edge<>(current,frozen));entryPath=frozen;entryIndex=1;current=null;
                } else {
                    var out=outgoing.get(current);
                    N next=out.getFirst();
                    if(out.size()==2 && Objects.equals(next,previous))next=out.get(1);
                    previous=current;current=next;
                }
                return false;
            }
            if(nextEdges.hasNext()) {
                current=nextEdges.next();previous=start;path=new ArrayList<>();path.add(start);return false;
            }
            if(starts.hasNext()) {
                start=starts.next();edges.put(start,new ArrayList<>());nextEdges=outgoing.getOrDefault(start,List.of()).iterator();return false;
            }
            complete=true;return true;
        }
        public RoutingGraph<N,E> finish() {
            if(!complete)throw new IllegalStateException("Compression is not complete");
            return new RoutingGraph<>(edges,ownedEndpoints,entries);
        }
    }
}
