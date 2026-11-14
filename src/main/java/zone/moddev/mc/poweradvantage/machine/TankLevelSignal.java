package zone.moddev.mc.poweradvantage.machine;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import zone.moddev.mc.lib.api.redstone.IRedstoneOutput;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.api.redstone.RedstoneOutput;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/**
 * Processor for XML tanks that never processes; it only offers the tank's fill level as a redstone output.
 *
 * <p>Power Advantage 3.0 and 3.1 tanks emitted their fill level on every face. The first time a tank runs this
 * processor, whether newly placed or loaded from one of those saves, it sets every face to emit the fill level.
 * Players can change any face afterwards; the defaults are never applied again.
 */
public final class TankLevelSignal extends PassiveProcessor implements IRedstoneOutput {
    /**
     * Stable redstone output ID, saved in each face's source selection.
     */
    public static final String OUTPUT_ID = "poweradvantage:fill_level";
    /**
     * Whether the all-faces emit defaults have been applied to this machine.
     */
    private final ProcessorState.Key<Boolean> facesConfigured = variables.define("faces_configured", Codec.BOOL, false);
    /**
     * The fill-level output; its signal supplier only runs on the server.
     */
    private final List<RedstoneOutput> outputs = List.of(new RedstoneOutput(OUTPUT_ID,
        Component.translatable("screen.poweradvantage.redstone.fill_level"),
        Component.translatable("screen.poweradvantage.redstone.fill_level.description"),
        0x3f76e4, Optional.empty(), this::signal));

    /**
     * Creates the processor; MMDLib constructs one per tank, plus prototypes for validation.
     */
    public TankLevelSignal() {
    }

    /**
     * Applies the all-faces emit defaults once, on the server.
     *
     * @param state this processor's stored state
     * @param context machine hosting the processor
     * @param deltaTicks ticks simulated by this call
     */
    @Override protected void idle(ProcessorState state, ProcessorMachine context, int deltaTicks) {
        if (state.get(facesConfigured) || !(context instanceof DefinedMachineEntity machine)) return;
        RedstoneControl redstone = machine.screenRedstone();
        if (redstone == null) return;
        int source = -1;
        for (int i = 0; i < redstone.outputs().size(); i++)
            if (redstone.outputs().get(i).id().equals(OUTPUT_ID)) source = i;
        if (source < 0) return;
        for (int face = 0; face < 6; face++) {
            if (!redstone.faceAvailable(face)) continue;
            redstone.mode(face, RedstoneControl.Mode.EMIT);
            redstone.source(face, source);
        }
        state.set(facesConfigured, true);
    }

    /**
     * Offers the fill-level output.
     *
     * @return the single fill-level output
     */
    @Override public List<RedstoneOutput> redstoneOutputs() { return outputs; }

    /**
     * Computes the fill-level signal exactly as 3.0 and 3.1 tanks did: amount times 15 divided by capacity, rounded down.
     *
     * @return signal from zero to fifteen
     */
    private int signal() {
        if (!(machine instanceof DefinedMachineEntity tank) || tank.fluids == null) return 0;
        long amount = 0, capacity = 0;
        for (int i = 0; i < tank.fluids.getTanks(); i++) {
            amount += tank.fluids.getFluidInTank(i).getAmount();
            capacity += tank.fluids.getTankCapacity(i);
        }
        return capacity == 0 ? 0 : (int) (amount * 15 / capacity);
    }
}
