package zone.moddev.mc.poweradvantage.network.transport;

import java.util.Objects;
import net.minecraft.core.BlockPos;

/** A physical transport channel. Independent crossover lanes share a position, not an identity. */
public record TransportNode(BlockPos position, String channel) {
    public TransportNode {
        position=Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(channel);
        if(channel.isBlank())throw new IllegalArgumentException("Empty transport channel");
    }
    public TransportNode(BlockPos position) { this(position,"main"); }
}
