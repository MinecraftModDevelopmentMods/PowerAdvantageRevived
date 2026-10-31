package zone.moddev.mc.poweradvantage.network.transport;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.*;

/**
 * World/block/resource-specific discovery policy. Weak links group components; outgoing links constrain legal
 * travel. The core never extracts or owns resources, and dynamic handler acceptance must be checked by the transfer
 * engine.
 *
 * @param <E> resource-specific endpoint metadata type
 */
public interface TransportAdapter<E> {
    /**
     * Recognizes block states owned by this transport kind.
     *
     * @param state current block state
     * @return true if this adapter owns the block state
     */
    boolean matches(BlockState state);
    /**
     * All independent channels at this position; must be stable for the given block state.
     *
     * @param pos block position being queried or changed
     * @param state current block state
     * @return physical channels at this block
     */
    default List<TransportNode> nodes(BlockPos pos,BlockState state) { return List.of(new TransportNode(pos)); }
    /**
     * Inspects one channel using loaded-world access and invalidation-aware endpoint caches.
     *
     * @param context placement, drawing or loaded-world inspection context
     * @param node physical position/channel identity
     * @return weak adjacency, outgoing links, endpoints, rating and retention policy
     */
    NodeData<E> inspect(Context context,TransportNode node);
    /**
     * Classifies an endpoint as an extraction/source port.
     *
     * @param endpoint sided resource endpoint
     * @return true for a source endpoint
     */
    default boolean isSource(E endpoint) { return false; }
    /**
     * Compute static destination policy once per component; recheck resource acceptance at transfer time.
     *
     * @param sources adapter-classified extraction endpoints
     * @return predicate used to retain destination endpoints
     */
    default Predicate<E> destinationPolicy(List<E> sources) { return endpoint->!isSource(endpoint); }
    /**
     * Include diagonal/vertical dependencies here for non-adjacent transport links.
     *
     * @param pos block position being queried or changed
     * @return positions to invalidate and reseed
     */
    default List<BlockPos> affectedPositions(BlockPos pos) {
        var positions=new ArrayList<BlockPos>();positions.add(pos.immutable());
        for(Direction side:Direction.values())positions.add(pos.relative(side));
        return positions;
    }
    /**
     * Optional client blockstate refresh, after inspection. Must not change the topology.
     *
     * @param level owning world; server-only engines require its server thread
     * @param node physical position/channel identity
     */
    default void refresh(ServerLevel level,TransportNode node) {}

    /**
     * Immutable discovery result for one physical channel. Outgoing links must be a subset of reciprocal weak
     * neighbors; retained vertices preserve policy boundaries.
     *
     * @param <E> resource-specific endpoint metadata type
     * @param neighbors Reciprocal weak neighbors used to group components
     * @param outgoing Directed legal travel links, a subset of weak neighbors
     * @param endpoints Resource endpoints attached to this channel
     * @param transferRate Positive per-tick node rating in adapter-defined units
     * @param retainVertex Whether compression must retain this policy boundary
     */
    record NodeData<E>(List<TransportNode> neighbors,List<TransportNode> outgoing,List<E> endpoints,
            int transferRate,boolean retainVertex) {
        /**
         * Copies neighbor, outgoing and endpoint lists and validates the positive rating and outgoing subset.
         *
         * @param neighbors Reciprocal weak neighbors used to group components
         * @param outgoing Directed legal travel links, a subset of weak neighbors
         * @param endpoints Resource endpoints attached to this channel
         * @param transferRate Positive per-tick node rating in adapter-defined units
         * @param retainVertex Whether compression must retain this policy boundary
         * @throws IllegalArgumentException if the rating is nonpositive or an outgoing link is not a weak neighbor
         */
        public NodeData {
            neighbors=List.copyOf(neighbors);outgoing=List.copyOf(outgoing);endpoints=List.copyOf(endpoints);
            if(transferRate<=0)throw new IllegalArgumentException("Transfer rating must be positive");
            if(!neighbors.containsAll(outgoing))throw new IllegalArgumentException("Outgoing links must be weak neighbors");
        }
    }
    /**
     * Loaded-world inspection and capability-watch services scoped to the component being built. Do not query an
     * endpoint cache inside its invalidation callback.
     */
    interface Context {
        /**
         * Returns the owning server dimension.
         *
         * @return server level for inspection
         */
        ServerLevel level();
        /**
         * Tests loaded and non-excluded access before inspecting a position.
         *
         * @param pos block position being queried or changed
         * @return true if inspection may access the position without loading a chunk
         */
        boolean available(BlockPos pos);
        /**
         * Retains the cache, including null results, and invalidates this component when it changes.
         *
         * @param <H> capability handler type
         * @param capability sided block-capability type to cache
         * @param pos block position being queried or changed
         * @param face sided capability face, or null for unsided access where supported
         * @return cache bound to this component lifetime
         * @throws IllegalArgumentException if the requested endpoint is unavailable
         */
        <H> BlockCapabilityCache<H,Direction> watch(BlockCapability<H,Direction> capability,BlockPos pos,Direction face);
    }
}
