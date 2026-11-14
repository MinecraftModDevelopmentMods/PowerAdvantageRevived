package zone.moddev.mc.poweradvantage.machine;

import net.neoforged.neoforge.fluids.FluidStack;
import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.poweradvantage.init.Fluids;

/**
 * Infinite Steam processor: keeps the machine's first tank full of steam and pushes up to {@value #RATE} mB per tick
 * into every neighbour, as 3.0 and 3.1 did.
 */
public final class CreativeSteamSource extends PassiveProcessor {
    /**
     * Millibuckets offered to each side per tick.
     */
    public static final int RATE = 65535;

    /**
     * Creates the processor; MMDLib constructs one per machine, plus prototypes for validation.
     */
    public CreativeSteamSource() {
    }

    /**
     * Refills the tank with steam and pushes it to every loaded side.
     *
     * @param state this processor's stored state
     * @param context machine hosting the processor
     * @param deltaTicks ticks simulated by this call
     */
    @Override protected void idle(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        if (!(context instanceof DefinedMachineEntity machine) || machine.fluids == null) return;
        var tank = machine.fluids.tank(0);
        tank.setFluid(new FluidStack(Fluids.STEAM.get(), tank.getCapacity()));
        forEachLoadedSide(context, side -> Transfer.pushFluids(context.level(), context.position(), side, machine.fluids, RATE));
    }
}
