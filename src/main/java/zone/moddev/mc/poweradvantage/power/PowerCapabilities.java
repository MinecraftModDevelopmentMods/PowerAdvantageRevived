package zone.moddev.mc.poweradvantage.power;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.energy.IEnergyStorage;
import zone.moddev.mc.poweradvantage.init.ModContent;

/** Public connection boundaries for PA power models distinct from FE/RF. */
public final class PowerCapabilities {
    /**
     * Electricity storage measured in PA electricity units. The storage protocol is the familiar integer
     * energy interface, but quantities are not FE. All amounts in a call use electricity units.
     * Electrical simulation modes can implement adapters here without exposing volts or amps as FE.
     */
    public static final BlockCapability<IEnergyStorage, Direction> ELECTRICITY =
        BlockCapability.createSided(ModContent.id("electricity"), IEnergyStorage.class);
    /** Prevents construction of this capability holder. */
    private PowerCapabilities() {}
}
