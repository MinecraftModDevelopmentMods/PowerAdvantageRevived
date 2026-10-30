package zone.moddev.mc.poweradvantage.network;

import java.util.*;
import net.minecraft.core.BlockPos;

/** Immutable compressed topology. Degree-two chains retain their physical positions. */
public final class PipeGraph {
    public record Edge(BlockPos target, List<BlockPos> path) { public int length() { return path.size()-1; } }
    private final Map<BlockPos,List<Edge>> edges;
    private final Map<BlockPos,List<FluidNetworks.Endpoint>> endpoints;
    private final Map<BlockPos,BlockPos> anchors;
    private final Map<BlockPos,List<FluidNetworks.Endpoint>> routes=new LinkedHashMap<>(16,0.75f,true) {
        protected boolean removeEldestEntry(Map.Entry<BlockPos,List<FluidNetworks.Endpoint>> entry) { return size()>32; }
    };
    private final Map<BlockPos,RouteBuild> pending=new LinkedHashMap<>();
    PipeGraph(Map<BlockPos,List<Edge>> edges,Map<BlockPos,List<FluidNetworks.Endpoint>> endpoints,Map<BlockPos,BlockPos> anchors) {
        this.edges=Collections.unmodifiableMap(edges);
        this.endpoints=Collections.unmodifiableMap(endpoints);
        this.anchors=Collections.unmodifiableMap(anchors);
        // Warm endpoint ingress routes within the same bounded work queue used for cold requests.
        for(BlockPos pos:endpoints.keySet()) { request(pos);if(pending.size()==32)break; }
    }
    public int vertexCount() { return edges.size(); }
    private void request(BlockPos start) {
        if(start!=null && !routes.containsKey(start) && !pending.containsKey(start) && pending.size()<32)
            pending.put(start,new RouteBuild(start));
    }
    /** A cold route waits for budgeted Dijkstra work; it never walks a large graph in a capability call. */
    public List<FluidNetworks.Endpoint> destinations(BlockPos ingress) {
        BlockPos start=edges.containsKey(ingress)?ingress:anchors.get(ingress);
        var cached=routes.get(start);
        if(cached!=null)return cached;
        request(start);return List.of();
    }
    int advanceRoutes(int budget) {
        int used=0;
        while(used<budget && !pending.isEmpty()) {
            var iterator=pending.entrySet().iterator();var entry=iterator.next();
            used++;
            if(entry.getValue().advance()) {
                routes.put(entry.getKey(),Collections.unmodifiableList(entry.getValue().result));iterator.remove();
            }
        }
        return used;
    }
    private record Step(BlockPos pos,int distance) {}
    private final class RouteBuild {
        final PriorityQueue<Step> queue=new PriorityQueue<>(Comparator.comparingInt(Step::distance));
        final Map<BlockPos,Integer> distances=new HashMap<>();
        final ArrayList<FluidNetworks.Endpoint> result=new ArrayList<>();
        RouteBuild(BlockPos start){queue.add(new Step(start,0));distances.put(start,0);}
        boolean advance() {
            if(queue.isEmpty())return true;
            Step step=queue.remove();
            if(step.distance()!=distances.get(step.pos()))return false;
            for(var endpoint:endpoints.getOrDefault(step.pos(),List.of()))if(!endpoint.source)result.add(endpoint);
            for(Edge edge:edges.getOrDefault(step.pos(),List.of())) {
                int distance=step.distance()+edge.length();
                if(distance<distances.getOrDefault(edge.target(),Integer.MAX_VALUE)) {
                    distances.put(edge.target(),distance);queue.add(new Step(edge.target(),distance));
                }
            }
            return false;
        }
    }

    /** Incremental compression: every advance handles at most one chain step or vertex. */
    static final class Builder {
        final Map<BlockPos,List<BlockPos>> physical;
        final Map<BlockPos,List<FluidNetworks.Endpoint>> endpoints;
        final Set<BlockPos> vertices=new LinkedHashSet<>();
        final Map<BlockPos,List<Edge>> edges=new HashMap<>();
        final Map<BlockPos,BlockPos> anchors=new HashMap<>();
        Iterator<BlockPos> classify;
        Iterator<BlockPos> starts;
        BlockPos start,previous,current;
        Iterator<BlockPos> neighbours=Collections.emptyIterator();
        ArrayList<BlockPos> path;
        Builder(Map<BlockPos,List<BlockPos>> physical,Map<BlockPos,List<FluidNetworks.Endpoint>> endpoints,Set<BlockPos> terminals) {
            this.physical=physical;this.endpoints=endpoints;
            vertices.addAll(terminals);
            classify=physical.keySet().iterator();
        }
        boolean advance() {
            if (classify!=null) {
                if (classify.hasNext()) {
                    var pos=classify.next();
                    if (physical.get(pos).size()!=2 || endpoints.containsKey(pos)) vertices.add(pos);
                    return false;
                }
                if (vertices.isEmpty()) vertices.add(physical.keySet().iterator().next());
                starts=vertices.iterator(); classify=null;
            }
            if (current!=null) {
                path.add(current);anchors.putIfAbsent(current,start);
                if (vertices.contains(current)) {
                    edges.get(start).add(new Edge(current,List.copyOf(path))); current=null;
                } else {
                    var adjacent=physical.get(current);
                    var next=adjacent.get(0).equals(previous)?adjacent.get(1):adjacent.get(0);
                    previous=current; current=next;
                }
                return false;
            }
            if (neighbours.hasNext()) {
                current=neighbours.next();previous=start;path=new ArrayList<>();path.add(start);return false;
            }
            if (starts.hasNext()) {
                start=starts.next();edges.put(start,new ArrayList<>());neighbours=physical.get(start).iterator();return false;
            }
            return true;
        }
        PipeGraph finish() { return new PipeGraph(edges,endpoints,anchors); }
    }
}
