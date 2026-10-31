package zone.moddev.mc.poweradvantage.api;
import net.minecraft.core.Direction;
/**
 * Legacy conduit extension contract; the current fluid and conveyor engines use dimension-owned transport networks.
 */
public interface ITypedConduit {
    /**
     * Tests whether a resource category may connect on a face.
     *
     * @param type legacy conduit resource category
     * @param facing connection face
     * @return true if this connection is supported
     */
    boolean canAcceptConnection(EnumPipeType type, Direction facing);
    /**
     * Identifies the resource category handled by this conduit.
     *
     * @return conduit resource category
     */
    EnumPipeType conduitType();
    /**
     * Attempts to admit a resource quantity, optionally simulating the operation.
     *
     * @param amount requested or accepted resource quantity in the owning engine's units
     * @param simulate true to predict acceptance without transferring resource ownership
     * @return quantity admitted
     */
    int fill(int amount, boolean simulate);
    /**
     * Attempts to release a resource quantity, optionally simulating the operation.
     *
     * @param amount requested or accepted resource quantity in the owning engine's units
     * @param simulate true to predict acceptance without transferring resource ownership
     * @return quantity released
     */
    int drain(int amount, boolean simulate);
    /**
     * Requests a connection on a face according to the implementation's policy.
     *
     * @param side queried or incoming face; null denotes unsided access where supported
     * @return true if the forced connection is accepted
     */
    boolean forceConnection(Direction side);
}
