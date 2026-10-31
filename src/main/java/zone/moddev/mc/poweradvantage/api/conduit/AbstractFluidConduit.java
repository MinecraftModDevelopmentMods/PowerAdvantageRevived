package zone.moddev.mc.poweradvantage.api.conduit;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import zone.moddev.mc.poweradvantage.api.*;
/**
 * Legacy abstract fluid-conduit base retained for consuming mods. It does not register a block, expose capabilities
 * or schedule transfers.
 */
public abstract class AbstractFluidConduit extends Block implements ITypedConduit {
    /**
     * Constructor-provided legacy conduit transfer rating.
     */
    private final int transferRate;
    /**
     * Creates a legacy fluid-conduit extension block with its supplied transfer rating.
     *
     * @param properties fresh Minecraft block properties
     * @param transferRate configured transfer rating in engine-defined units per tick
     */
    protected AbstractFluidConduit(Properties properties, int transferRate) { super(properties); this.transferRate = transferRate; }
    /**
     * Returns the constructor-provided legacy conduit rating.
     *
     * @return configured transfer-rate value
     */
    public final int getTransferRate() { return transferRate; }
    /**
     * Accepts only the fluid conduit category.
     *
     * @param type legacy conduit resource category
     * @param facing connection face
     * @return true for fluid connections
     */
    public boolean canAcceptConnection(EnumPipeType type, Direction facing) { return type == EnumPipeType.TYPE_FLUID; }
    /**
     * Identifies this legacy conduit as fluid transport.
     *
     * @return fluid conduit category
     */
    public EnumPipeType conduitType() { return EnumPipeType.TYPE_FLUID; }
}
