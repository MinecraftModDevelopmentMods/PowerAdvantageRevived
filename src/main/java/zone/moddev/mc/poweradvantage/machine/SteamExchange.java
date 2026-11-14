package zone.moddev.mc.poweradvantage.machine;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.poweradvantage.init.Fluids;
import zone.moddev.mc.poweradvantage.power.SteamConverterSettings;

/**
 * FE/RF to steam converter processor. The machine's MMDLib FE buffer and steam tank hold both sides; each tick steam
 * is balanced toward half full in whole batches of 8 FE to 1 mB, at most 1000 FE per tick, exactly as 3.0 and 3.1
 * did.
 */
public final class SteamExchange extends FeExchangeProcessor {
    /**
     * Buffer sizes, exchange ratio and rate.
     */
    public static final SteamConverterSettings SETTINGS = SteamConverterSettings.DEFAULT;

    /**
     * Creates the processor; MMDLib constructs one per converter, plus prototypes for validation.
     */
    public SteamExchange() {
    }

    /**
     * FE moved to or from neighbours per tick.
     *
     * @return FE per tick
     */
    @Override protected int fePerTick() { return SETTINGS.fePerTick(); }

    /**
     * Balances the steam tank toward half full against the FE buffer.
     *
     * @param machine the converter, with its steam tank
     * @param fe its FE buffer
     */
    @Override protected void convert(DefinedMachineEntity machine, EnergyBuffer fe) {
        if (machine.fluids != null) exchange(fe, machine.fluids.tank(0), SETTINGS);
    }

    /**
     * One tick of conversion: whole batches toward a half-full steam tank, limited by the FE rate, FE space and FE
     * stored. Changes both stores in place.
     *
     * @param fe FE buffer
     * @param steam steam tank
     * @param settings capacities, ratio and rate
     */
    public static void exchange(EnergyBuffer fe, FluidTank steam, SteamConverterSettings settings) {
        long delta = (long) steam.getFluidAmount() - steam.getCapacity() / 2;
        long maximum = settings.fePerTick() / settings.feQuantum();
        if (delta > 0) {
            long batches = Math.min(maximum, Math.min(delta / settings.steamQuantum(), fe.space() / settings.feQuantum()));
            steam.drain((int) (batches * settings.steamQuantum()), IFluidHandler.FluidAction.EXECUTE);
            fe.insert(batches * settings.feQuantum(), false);
        } else if (delta < 0) {
            long batches = Math.min(maximum, Math.min(-delta / settings.steamQuantum(), fe.stored() / settings.feQuantum()));
            fe.extract(batches * settings.feQuantum(), false);
            if (batches > 0)
                steam.fill(new FluidStack(Fluids.STEAM.get(), (int) (batches * settings.steamQuantum())), IFluidHandler.FluidAction.EXECUTE);
        }
    }
}
