package zone.moddev.mc.poweradvantage.power;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.*;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/** Bidirectional FE/RF-to-electricity bridge with exact integer exchanges and PA2-style half-buffer balancing. */
public final class ElectricityConverterBlockEntity extends BufferedConverterBlockEntity {
    /** Validated work/storage settings selected by its standalone definition. */
    public final ConverterSettings settings;
    /** PA electricity storage, kept in its own units. */
    private final EnergyBuffer electricity;
    /** Guarded electricity capability view. */
    private final IEnergyStorage electricityPort;
    /** Creates the bridge and its electricity store.
     * @param type registered type
     * @param pos position
     * @param state block state
     * @param settings validated factory settings
     */
    public ElectricityConverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, ConverterSettings settings) {
        super(type, pos, state, settings.feCapacity(), settings.fePerTick());
        this.settings = settings;
        electricity = new EnergyBuffer(settings.electricityCapacity(), Integer.MAX_VALUE).onChange(this::setChanged);
        electricityPort = port(electricity);
    }
    /** Returns the electricity boundary.
     * @return guarded electricity storage
     */
    public IEnergyStorage electricityPort() { return electricityPort; }
    /** Reads stored electricity for XML binding.
     * @return electricity amount
     */
    public long screenElectricity() { return electricity.stored(); }
    /** Reads electricity capacity for XML binding.
     * @return electricity capacity
     */
    public long screenElectricityCapacity() { return electricity.capacity(); }
    /** Applies complete exchange batches only; sub-batch remainders stay in their original store. */
    @Override protected void convert() {
        long delta = electricity.stored() - electricity.capacity() / 2;
        long maximum = settings.fePerTick() / settings.feQuantum();
        if (delta > 0) {
            long batches = Math.min(maximum, Math.min(delta / settings.electricityQuantum(), fe.space() / settings.feQuantum()));
            electricity.extract(batches * settings.electricityQuantum(), false);
            fe.insert(batches * settings.feQuantum(), false);
        } else if (delta < 0) {
            long batches = Math.min(maximum, Math.min(-delta / settings.electricityQuantum(), fe.stored() / settings.feQuantum()));
            fe.extract(batches * settings.feQuantum(), false);
            electricity.insert(batches * settings.electricityQuantum(), false);
        }
    }

    /** Saves both stores with their own units.
     * @param tag destination tag
     * @param registries registry lookup
     */
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Electricity", electricity.serializeNBT(registries));
    }
    /** Restores stores, clamping through their configured capacities.
     * @param tag saved tag
     * @param registries registry lookup
     */
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Electricity")) electricity.deserializeNBT(registries, tag.get("Electricity"));
    }
}
