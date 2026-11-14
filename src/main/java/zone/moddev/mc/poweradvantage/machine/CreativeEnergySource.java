package zone.moddev.mc.poweradvantage.machine;

import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/**
 * Infinite Power processor: keeps the machine's FE buffer full and pushes up to {@value #RATE} FE per tick into every
 * neighbour, as 3.0 and 3.1 did.
 */
public final class CreativeEnergySource extends PassiveProcessor {
    /**
     * FE offered to each side per tick.
     */
    public static final int RATE = 4096;

    /**
     * Creates the processor; MMDLib constructs one per machine, plus prototypes for validation.
     */
    public CreativeEnergySource() {
    }

    /**
     * Refills the buffer and pushes energy to every loaded side.
     *
     * @param state this processor's stored state
     * @param context machine hosting the processor
     * @param deltaTicks ticks simulated by this call
     */
    @Override protected void idle(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        if (!(context instanceof DefinedMachineEntity machine) || machine.energy == null) return;
        machine.energy.setStored(machine.energy.capacity());
        forEachLoadedSide(context, side -> Transfer.pushEnergy(context.level(), context.position(), side, machine.energy, RATE));
    }
}
