package zone.moddev.mc.poweradvantage.api;
import net.minecraft.core.Direction;
public interface ITypedConduit {
    boolean canAcceptConnection(EnumPipeType type, Direction facing);
    EnumPipeType conduitType();
    int fill(int amount, boolean simulate);
    int drain(int amount, boolean simulate);
    boolean forceConnection(Direction side);
}
