package zone.moddev.mc.poweradvantage.machine;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.energy.IEnergyStorage;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.poweradvantage.power.ConverterSettings;
import zone.moddev.mc.poweradvantage.power.PowerCapabilities;

/**
 * FE/RF to Power Advantage electricity converter processor. The machine's MMDLib FE buffer holds the FE side; this
 * processor holds the electricity buffer as a saved, client-synced variable and exposes it through
 * {@link #port()}, registered as the {@link PowerCapabilities#ELECTRICITY} capability.
 *
 * <p>Each tick electricity is balanced toward half full in whole batches of 1 FE to 4 electricity units, at most
 * 1000 FE per tick, exactly as 3.0 and 3.1 did.
 */
public final class ElectricityExchange extends FeExchangeProcessor {
    /**
     * Buffer sizes, exchange ratio and rate.
     */
    public static final ConverterSettings SETTINGS = ConverterSettings.DEFAULT;
    /**
     * Stored electricity units.
     */
    private final ProcessorState.Key<Long> electricity = variables
        .value("electricity", Codec.LONG, 0L, UnaryOperator.identity()).synced().define();
    /**
     * Screen text for the stored electricity.
     */
    private final ProcessorState.Key<List<Component>> details = variables
        .components("details", List.of(reading(0))).synced().define();
    /**
     * The electricity capability, refusing work once the converter is removed.
     */
    private final IEnergyStorage port = new IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            if (amount <= 0 || unavailable()) return 0;
            int accepted = (int) Math.min(amount, SETTINGS.electricityCapacity() - stored());
            if (!simulate && accepted > 0) store(stored() + accepted);
            return accepted;
        }
        @Override public int extractEnergy(int amount, boolean simulate) {
            if (amount <= 0 || unavailable()) return 0;
            int extracted = (int) Math.min(amount, stored());
            if (!simulate && extracted > 0) store(stored() - extracted);
            return extracted;
        }
        @Override public int getEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, stored()); }
        @Override public int getMaxEnergyStored() { return SETTINGS.electricityCapacity(); }
        @Override public boolean canExtract() { return !unavailable(); }
        @Override public boolean canReceive() { return !unavailable(); }
    };

    /**
     * Creates the processor; MMDLib constructs one per converter, plus prototypes for validation.
     */
    public ElectricityExchange() {
    }

    /**
     * The converter's electricity capability.
     *
     * @return this converter's electricity port
     */
    public IEnergyStorage port() { return port; }

    /**
     * Stored electricity units.
     *
     * @return electricity
     */
    public long stored() { return variables.get(electricity); }

    /**
     * FE moved to or from neighbours per tick.
     *
     * @return FE per tick
     */
    @Override protected int fePerTick() { return SETTINGS.fePerTick(); }

    /**
     * Balances electricity toward half full against the FE buffer.
     *
     * @param machine the converter
     * @param fe its FE buffer
     */
    @Override protected void convert(DefinedMachineEntity machine, EnergyBuffer fe) {
        long updated = exchange(fe, stored(), SETTINGS);
        if (updated != stored()) store(updated);
    }

    /**
     * One tick of conversion: whole batches toward half-full electricity, limited by the FE rate, FE space and FE
     * stored. Changes {@code fe} in place.
     *
     * @param fe FE buffer
     * @param electricity stored electricity units
     * @param settings capacities, ratio and rate
     * @return electricity units afterwards
     */
    public static long exchange(EnergyBuffer fe, long electricity, ConverterSettings settings) {
        long delta = electricity - settings.electricityCapacity() / 2;
        long maximum = settings.fePerTick() / settings.feQuantum();
        if (delta > 0) {
            long batches = Math.min(maximum, Math.min(delta / settings.electricityQuantum(), fe.space() / settings.feQuantum()));
            fe.insert(batches * settings.feQuantum(), false);
            return electricity - batches * settings.electricityQuantum();
        }
        if (delta < 0) {
            long batches = Math.min(maximum, Math.min(-delta / settings.electricityQuantum(), fe.stored() / settings.feQuantum()));
            fe.extract(batches * settings.feQuantum(), false);
            return electricity + batches * settings.electricityQuantum();
        }
        return electricity;
    }

    /**
     * Saves a new electricity amount and its screen text.
     *
     * @param amount electricity units
     */
    private void store(long amount) {
        variables.update(() -> {
            variables.set(electricity, amount);
            variables.set(details, List.of(reading(amount)));
        });
    }

    /**
     * Screen line for an electricity amount.
     *
     * @param amount electricity units
     * @return translated reading
     */
    private static Component reading(long amount) {
        return Component.translatable("screen.poweradvantage.electricity_stored", amount, SETTINGS.electricityCapacity());
    }
}
