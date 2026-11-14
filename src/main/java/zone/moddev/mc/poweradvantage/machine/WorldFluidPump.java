package zone.moddev.mc.poweradvantage.machine;

import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.poweradvantage.util.WorldFluids;

/**
 * Shared work of the Fluid Drain and Fluid Discharge: every {@value #INTERVAL} ticks, exchange fluid with neighbours
 * and with the world, as 3.0 and 3.1 did. Subclasses choose the direction.
 */
public abstract class WorldFluidPump extends PassiveProcessor {
    /**
     * Ticks between rounds of work.
     */
    public static final int INTERVAL = 10;
    /**
     * Ticks since the last round; not saved, as in 3.1.
     */
    private int ticks;

    /**
     * Creates the processor.
     */
    protected WorldFluidPump() {
    }

    /**
     * Does one round of work every {@value #INTERVAL} ticks.
     *
     * @param state this processor's stored state
     * @param context machine hosting the processor
     * @param deltaTicks ticks simulated by this call
     */
    @Override protected final void idle(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        if (!(context instanceof DefinedMachineEntity machine) || machine.fluids == null) return;
        ticks += deltaTicks;
        if (ticks < INTERVAL) return;
        ticks = 0;
        work(context, machine);
    }

    /**
     * One round of work.
     *
     * @param context machine hosting the processor
     * @param machine the same machine, with its storage
     */
    protected abstract void work(ProcessorMachine context, DefinedMachineEntity machine);

    /**
     * Fluid Drain: pushes its tank into every neighbour, then collects one bucket of fluid from the world above.
     */
    public static final class Drain extends WorldFluidPump {
        /**
         * Creates the processor; MMDLib constructs one per machine, plus prototypes for validation.
         */
        public Drain() {
        }

        /**
         * Pushes to neighbours and collects from above.
         *
         * @param context machine hosting the processor
         * @param machine the same machine, with its storage
         */
        @Override protected void work(ProcessorMachine context, DefinedMachineEntity machine) {
            var tank = machine.fluids.tank(0);
            forEachLoadedSide(context, side ->
                Transfer.pushFluids(context.level(), context.position(), side, machine.fluids, tank.getCapacity()));
            WorldFluids.collect(context.level(), context.position().above(), tank);
        }
    }

    /**
     * Fluid Discharge: pulls fluid from every neighbour, then places one bucket of it in the world below.
     */
    public static final class Discharge extends WorldFluidPump {
        /**
         * Creates the processor; MMDLib constructs one per machine, plus prototypes for validation.
         */
        public Discharge() {
        }

        /**
         * Pulls from neighbours and places below.
         *
         * @param context machine hosting the processor
         * @param machine the same machine, with its storage
         */
        @Override protected void work(ProcessorMachine context, DefinedMachineEntity machine) {
            var tank = machine.fluids.tank(0);
            forEachLoadedSide(context, side ->
                Transfer.pullFluids(context.level(), context.position(), side, machine.fluids, tank.getCapacity()));
            WorldFluids.discharge(context.level(), context.position().below(), tank);
        }
    }
}
