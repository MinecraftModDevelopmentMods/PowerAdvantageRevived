package zone.moddev.mc.poweradvantage.tiles;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.poweradvantage.init.Fluids;
import zone.moddev.mc.poweradvantage.util.WorldFluids;
import static zone.moddev.mc.poweradvantage.tiles.MachineSettings.Kind.*;

/**
 * Shared machine implementation selected by data-defined settings. Owns MMDLib fluid/energy storage, server work,
 * registry-aware persistence, redstone output and status-menu synchronization.
 */
public final class MachineBlockEntity extends BlockEntity implements ScreenHost {
    /**
     * Immutable behavior/capacity settings selected by the standalone definition.
     */
    public final MachineSettings settings;
    /**
     * MMDLib fluid storage owned by this entity and exposed to permitted automation.
     */
    public final FluidStorage fluids = new FluidStorage().onChange(this::contentsChanged);
    /**
     * MMDLib energy buffer with generator output rates.
     */
    public final EnergyBuffer energy;
    /**
     * Empty machine inventory exposed to MMDLib screens; these machines have no item slots.
     */
    private final IItemHandlerModifiable screenItems = new ItemStackHandler(0);
    /**
     * Counter pacing drain/discharge work.
     */
    private int workTicks;
    /**
     * Last neighbor-notified tank redstone strength.
     */
    private int lastSignal;

    /**
     * Creates registered machine state and its MMDLib fluid/energy stores from immutable behavior settings.
     *
     * @param type registered entity type passed unchanged to the machine constructor
     * @param pos block position being queried or changed
     * @param state current block state
     * @param settings Immutable behavior/capacity settings selected by the standalone definition
     */
    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineSettings settings) {
        super(type, pos, state);
        this.settings = settings;
        fluids.addTank(settings.capacity(), settings.kind() == STEAM ? Role.OUTPUT : Role.STORAGE,
            stack -> settings.kind() != STEAM || stack.getFluid().isSame(Fluids.STEAM.get()));
        energy = new EnergyBuffer(settings.capacity(), 0, 4096).onChange(this::contentsChanged);
    }
    /**
     * Checks whether settings select the infinite-energy behavior.
     *
     * @return true for the energy generator
     */
    public boolean isEnergy() { return settings.kind() == ENERGY; }
    /**
     * Exposes the machine's (empty) item inventory to MMDLib's generic screen menu.
     *
     * @return empty modifiable item handler
     */
    @Override public IItemHandlerModifiable screenItems() { return screenItems; }
    /**
     * Reads the current stored FE for the MMDLib energy screen.
     *
     * @return stored energy
     */
    public int screenStoredEnergy() { return energy.getEnergyStored(); }
    /**
     * Reads the current fluid for the MMDLib fluid screen.
     *
     * @return a copy of the stored fluid stack
     */
    public FluidStack screenFluid() { return fluids.tank(0).getFluid().copy(); }
    /**
     * Reads the configured capacity for the MMDLib status gauges.
     *
     * @return capacity in FE or millibuckets
     */
    public int screenCapacity() { return settings.capacity(); }
    /**
     * Marks persistent storage dirty and notifies neighbors/comparators only when the tank signal changes.
     */
    private void contentsChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            int signal = redstoneSignal();
            if (signal != lastSignal) {
                lastSignal = signal;
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }
    /**
     * Converts a storage tank's fill fraction into its redstone signal. Other machine kinds emit zero.
     *
     * @return signal from zero to fifteen
     */
    public int redstoneSignal() {
        return settings.kind() == TANK ? (int) ((long) fluids.tank(0).getFluidAmount() * 15 / settings.capacity()) : 0;
    }
    /**
     * Runs only server-side behavior selected by the settings: replenish/export generators or periodically
     * collect/discharge world fluid.
     *
     * @param level owning world; server-only engines require its server thread
     * @param pos block position being queried or changed
     * @param state current block state
     * @param be machine entity being ticked
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        var kind = be.settings.kind();
        if (kind == ENERGY) {
            be.energy.setStored(be.settings.capacity());
            for (Direction side : Direction.values()) if (level.hasChunkAt(pos.relative(side)))
                Transfer.pushEnergy(level, pos, side, be.energy, 4096);
        } else if (kind == STEAM) {
            be.fluids.tank(0).setFluid(new FluidStack(Fluids.STEAM.get(), be.settings.capacity()));
            for (Direction side : Direction.values()) if (level.hasChunkAt(pos.relative(side)))
                Transfer.pushFluids(level, pos, side, be.fluids, 65535);
        } else if ((kind == DRAIN || kind == DISCHARGE) && ++be.workTicks >= 10) {
            be.workTicks = 0;
            for (Direction side : Direction.values()) if (level.hasChunkAt(pos.relative(side))) {
                if (kind == DRAIN) Transfer.pushFluids(level, pos, side, be.fluids, be.settings.capacity());
                else Transfer.pullFluids(level, pos, side, be.fluids, be.settings.capacity());
            }
            if (kind == DRAIN) WorldFluids.collect(level, pos.above(), be.fluids.tank(0));
            else WorldFluids.discharge(level, pos.below(), be.fluids.tank(0));
        }
    }
    /**
     * Saves registry-aware MMDLib fluid and energy storage alongside block-entity data.
     *
     * @param tag compound tag read or populated by this operation
     * @param registries registry lookup provider used by item/fluid NBT codecs
     */
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fluids", fluids.serializeNBT(registries));
        tag.put("Energy", energy.serializeNBT(registries));
    }
    /**
     * Restores registry-aware fluid and energy storage from saved block-entity data.
     *
     * @param tag compound tag read or populated by this operation
     * @param registries registry lookup provider used by item/fluid NBT codecs
     */
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fluids.deserializeNBT(registries, tag.getCompound("Fluids"));
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
    }
}
