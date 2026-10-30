package zone.moddev.mc.poweradvantage.network.transport;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.*;

/** Resource/block-specific discovery. The core never extracts, inserts or owns transported contents. */
public interface TransportAdapter<E> {
    boolean matches(BlockState state);
    /** All independent channels at this position; must be stable for the given block state. */
    default List<TransportNode> nodes(BlockPos pos,BlockState state) { return List.of(new TransportNode(pos)); }
    NodeData<E> inspect(Context context,TransportNode node);
    default boolean isSource(E endpoint) { return false; }
    /** Compute static destination policy once per component; recheck resource acceptance at transfer time. */
    default Predicate<E> destinationPolicy(List<E> sources) { return endpoint->!isSource(endpoint); }
    /** Include diagonal/vertical dependencies here for non-adjacent transport links. */
    default List<BlockPos> affectedPositions(BlockPos pos) {
        var positions=new ArrayList<BlockPos>();positions.add(pos.immutable());
        for(Direction side:Direction.values())positions.add(pos.relative(side));
        return positions;
    }
    /** Optional client blockstate refresh, after inspection. Must not change the topology. */
    default void refresh(ServerLevel level,TransportNode node) {}

    /** Weak neighbors discover components; outgoing links define legal travel directions.
     * Every outgoing link must also be a weak neighbor, and weak links must be reciprocal.
     * Retain filter/routing-policy boundaries so compression cannot erase their semantics.
     */
    record NodeData<E>(List<TransportNode> neighbors,List<TransportNode> outgoing,List<E> endpoints,
            int transferRate,boolean retainVertex) {
        public NodeData {
            neighbors=List.copyOf(neighbors);outgoing=List.copyOf(outgoing);endpoints=List.copyOf(endpoints);
            if(transferRate<=0)throw new IllegalArgumentException("Transfer rating must be positive");
            if(!neighbors.containsAll(outgoing))throw new IllegalArgumentException("Outgoing links must be weak neighbors");
        }
    }
    interface Context {
        ServerLevel level();
        boolean available(BlockPos pos);
        /** Retains the cache, including null results, and invalidates this component when it changes. */
        <H> BlockCapabilityCache<H,Direction> watch(BlockCapability<H,Direction> capability,BlockPos pos,Direction face);
    }
}
