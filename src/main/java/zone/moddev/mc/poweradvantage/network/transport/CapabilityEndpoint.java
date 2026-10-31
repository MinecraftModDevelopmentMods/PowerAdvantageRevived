package zone.moddev.mc.poweradvantage.network.transport;

import net.minecraft.core.*;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

/**
 * Sided capability endpoint independent of the transported resource. The containing component retains and
 * invalidates its cache through the transport context.
 *
 * @param <H> capability handler type
 * @param node Transport channel to which the endpoint is attached
 * @param position Block position providing the capability
 * @param face Face used for the provider capability query
 * @param source Whether this endpoint is an extraction/source port
 * @param cache Invalidation-aware sided provider cache retained by its component
 */
public record CapabilityEndpoint<H>(TransportNode node, BlockPos position, Direction face,
        boolean source, BlockCapabilityCache<H,Direction> cache) {
    /**
     * Copies the endpoint position while retaining its channel, sided handler cache and source role.
     *
     * @param node Transport channel to which the endpoint is attached
     * @param position Block position providing the capability
     * @param face Face used for the provider capability query
     * @param source Whether this endpoint is an extraction/source port
     * @param cache Invalidation-aware sided provider cache retained by its component
     */
    public CapabilityEndpoint { position=position.immutable(); }
}
