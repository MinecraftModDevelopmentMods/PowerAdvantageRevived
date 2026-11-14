package zone.moddev.mc.poweradvantage.machine;

import java.util.function.Consumer;
import net.minecraft.core.Direction;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/**
 * Base for XML machine processors that never run a cycle and do their work in {@link #idle}, which MMDLib calls every
 * server tick while the machine is not paused.
 */
public abstract class PassiveProcessor extends StoredProcessor {
    /**
     * Creates the processor; subclasses declare any state keys in their own constructors.
     */
    protected PassiveProcessor() {
    }

    /**
     * There is no work to start.
     *
     * @param state this processor's stored state
     * @param context machine hosting the processor
     * @return false
     */
    @Override public final boolean canStart(ProcessorState state, ProcessorMachine context) { return false; }

    /**
     * Never called, since no work starts; finishes immediately if it is.
     *
     * @param state this processor's stored state
     * @param context machine hosting the processor
     * @param deltaTicks ticks simulated by this call
     * @return idle
     */
    @Override public final ProcessState process(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        return ProcessState.IDLE;
    }

    /**
     * Runs {@code action} for each side whose neighbouring chunk is loaded, never loading one.
     *
     * @param context machine hosting the processor
     * @param action work for one side
     */
    protected static void forEachLoadedSide(ProcessorMachine context, Consumer<Direction> action) {
        for (Direction side : Direction.values())
            if (context.level().hasChunkAt(context.position().relative(side))) action.accept(side);
    }
}
