package zone.moddev.mc.poweradvantage.network.transport;

import net.minecraft.core.*;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

/** Sided resource access, independent of item/fluid handler types. */
public record CapabilityEndpoint<H>(TransportNode node, BlockPos position, Direction face,
        boolean source, BlockCapabilityCache<H,Direction> cache) {
    public CapabilityEndpoint { position=position.immutable(); }
}
