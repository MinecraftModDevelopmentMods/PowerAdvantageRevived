package zone.moddev.mc.poweradvantage.network.transport;

import java.util.Objects;
import net.minecraft.core.BlockPos;

/**
 * Immutable identity of a physical transport channel. Independent crossover lanes may share a position but must
 * have different stable, nonblank channel IDs.
 *
 * @param position Copied immutable physical block position
 * @param channel Stable nonblank identity of a lane at that position
 */
public record TransportNode(BlockPos position, String channel) {
    /**
     * Copies the physical position and validates its stable, nonblank channel identity.
     *
     * @param position Copied immutable physical block position
     * @param channel Stable nonblank identity of a lane at that position
     * @throws IllegalArgumentException if an explicitly supplied channel ID is blank
     */
    public TransportNode {
        position=Objects.requireNonNull(position).immutable();
        Objects.requireNonNull(channel);
        if(channel.isBlank())throw new IllegalArgumentException("Empty transport channel");
    }
    /**
     * Copies the physical position and validates its stable, nonblank channel identity.
     *
     * @param position Copied immutable physical block position
     */
    public TransportNode(BlockPos position) { this(position,"main"); }
}
