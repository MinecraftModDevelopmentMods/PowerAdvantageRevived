package zone.moddev.mc.poweradvantage.machine;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/**
 * Shared work of the FE converters, unchanged from 3.0 and 3.1. Each tick the machine's FE buffer is balanced toward
 * half full against its neighbours, pulling FE when below half and pushing it when above, up to the converter's FE
 * rate and starting from a rotating side. Then the subclass exchanges FE with its other unit.
 */
public abstract class FeExchangeProcessor extends PassiveProcessor {
    /**
     * Side the next neighbour service starts from; not saved, as in 3.1.
     */
    private int cursor;

    /**
     * Creates the processor.
     */
    protected FeExchangeProcessor() {
    }

    /**
     * Most FE moved to or from neighbours per tick.
     *
     * @return FE per tick
     */
    protected abstract int fePerTick();

    /**
     * Exchanges FE with the other unit.
     *
     * @param machine the converter, with its FE buffer
     * @param fe its FE buffer
     */
    protected abstract void convert(DefinedMachineEntity machine, EnergyBuffer fe);

    /**
     * Services neighbours, then converts.
     *
     * @param state this processor's stored state
     * @param context machine hosting the processor
     * @param deltaTicks ticks simulated by this call
     */
    @Override protected final void idle(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        if (!(context instanceof DefinedMachineEntity machine) || machine.energy == null || context.removed()) return;
        serviceNeighbours(context, machine.energy);
        if (!context.removed()) convert(machine, machine.energy);
    }

    /**
     * Whether ports should refuse work: the converter is gone or not yet placed.
     *
     * @return true when the hosting machine is missing or removed
     */
    protected final boolean unavailable() { return machine == null || machine.removed(); }

    /**
     * Balances the FE buffer toward half full against neighbouring FE handlers.
     *
     * @param context machine hosting the processor
     * @param fe its FE buffer
     */
    private void serviceNeighbours(ProcessorMachine context, EnergyBuffer fe) {
        long delta = fe.stored() - fe.capacity() / 2;
        int remaining = (int) Math.min(Math.abs(delta), fePerTick());
        var level = context.level();
        for (int offset = 0; offset < 6 && remaining > 0 && !context.removed(); offset++) {
            var side = Direction.from3DDataValue((cursor + offset) % 6);
            var pos = context.position().relative(side);
            if (!level.isLoaded(pos)) continue;
            var target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side.getOpposite());
            if (target == null) continue;
            remaining -= delta < 0 ? pull(fe, target, remaining, context) : push(fe, target, remaining, context);
        }
        cursor = (cursor + 1) % 6;
    }

    /**
     * Pulls FE from a neighbour, returning any that arrives after the converter is removed.
     *
     * @param fe the converter's FE buffer
     * @param source neighbouring FE handler
     * @param limit most FE to move
     * @param context machine hosting the processor
     * @return FE moved
     */
    private static int pull(EnergyBuffer fe, IEnergyStorage source, int limit, ProcessorMachine context) {
        if (!source.canExtract()) return 0;
        int amount = Math.min(source.extractEnergy(limit, true), (int) Math.min(Integer.MAX_VALUE, fe.space()));
        if (amount <= 0) return 0;
        int actual = source.extractEnergy(amount, false);
        if (!context.removed()) fe.insert(actual, false);
        else source.receiveEnergy(actual, false);
        return actual;
    }

    /**
     * Pushes FE into a neighbour, keeping whatever it refuses.
     *
     * @param fe the converter's FE buffer
     * @param target neighbouring FE handler
     * @param limit most FE to move
     * @param context machine hosting the processor
     * @return FE delivered
     */
    private static int push(EnergyBuffer fe, IEnergyStorage target, int limit, ProcessorMachine context) {
        if (!target.canReceive()) return 0;
        int amount = Math.min(limit, target.receiveEnergy(limit, true));
        if (amount <= 0) return 0;
        int owned = (int) fe.extract(amount, false);
        int accepted = target.receiveEnergy(owned, false);
        if (!context.removed()) fe.insert(owned - accepted, false);
        return accepted;
    }
}
