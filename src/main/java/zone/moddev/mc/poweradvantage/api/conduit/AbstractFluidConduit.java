package zone.moddev.mc.poweradvantage.api.conduit;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import zone.moddev.mc.poweradvantage.api.*;
/** Extension point only: the old project did not implement or register a conduit network. */
public abstract class AbstractFluidConduit extends Block implements ITypedConduit {
    private final int transferRate;
    protected AbstractFluidConduit(Properties properties, int transferRate) { super(properties); this.transferRate = transferRate; }
    public final int getTransferRate() { return transferRate; }
    public boolean canAcceptConnection(EnumPipeType type, Direction facing) { return type == EnumPipeType.TYPE_FLUID; }
    public EnumPipeType conduitType() { return EnumPipeType.TYPE_FLUID; }
}
