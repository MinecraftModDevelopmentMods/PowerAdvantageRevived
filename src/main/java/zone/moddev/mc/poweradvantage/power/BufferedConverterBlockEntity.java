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

/** Shared guarded FE storage, neighbor balancing and XML inventory for resource converters. */
public abstract class BufferedConverterBlockEntity extends BlockEntity implements ScreenHost {
    /** Shared FE/RF store. */
    protected final EnergyBuffer fe;
    /** FE allowance for one neighbor-service phase. */
    protected final int fePerTick;
    /** Rejects external calls while a converter transaction is in progress. */
    private boolean working;
    /** Rotating FE neighbor start. */
    private int cursor;
    /** Stable guarded FE view. */
    private final IEnergyStorage fePort;
    /** Empty item inventory used by the XML menu. */
    private final IItemHandlerModifiable screenItems = new ItemStackHandler(0);

    /** Creates common converter state.
     * @param type registered entity type
     * @param pos position
     * @param state block state
     * @param capacity FE capacity
     * @param rate FE allowance per service/conversion phase
     */
    protected BufferedConverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity, int rate) {
        super(type, pos, state);
        fePerTick = rate;
        fe = new EnergyBuffer(capacity, rate).onChange(this::setChanged);
        fePort = port(fe);
    }
    /** Returns the common FE/RF capability.
     * @return guarded FE view
     */
    public final IEnergyStorage fePort() { return fePort; }
    /** Supplies the menu's empty inventory.
     * @return empty item handler
     */
    @Override public final IItemHandlerModifiable screenItems() { return screenItems; }
    /** Reads stored FE for XML binding.
     * @return FE amount
     */
    public final long screenFe() { return fe.stored(); }
    /** Reads FE capacity for XML binding.
     * @return FE capacity
     */
    public final long screenFeCapacity() { return fe.capacity(); }
    /** Checks whether an external operation may enter this converter.
     * @return true for an idle, live server-side converter
     */
    protected final boolean available() { return !working && !isRemoved() && level != null && !level.isClientSide; }
    /** Creates a non-reentrant view; cached views stop working after block removal.
     * @param buffer owning store
     * @return external storage interface
     */
    protected final IEnergyStorage port(EnergyBuffer buffer) {
        return new IEnergyStorage() {
            public int receiveEnergy(int amount, boolean simulate) {
                return available() ? buffer.receiveEnergy(amount, simulate) : 0;
            }
            public int extractEnergy(int amount, boolean simulate) {
                return available() ? buffer.extractEnergy(amount, simulate) : 0;
            }
            public int getEnergyStored() { return buffer.getEnergyStored(); }
            public int getMaxEnergyStored() { return buffer.getMaxEnergyStored(); }
            public boolean canExtract() { return available(); }
            public boolean canReceive() { return available(); }
        };
    }


    /** Services FE neighbors and then performs one bounded resource conversion.
     * @param level owning world
     * @param pos converter position
     * @param state block state
     * @param be converter instance
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, BufferedConverterBlockEntity be) {
        if (level.isClientSide || be.working || be.isRemoved()) return;
        be.working = true;
        try {
            be.serviceFeNeighbors();
            if (!be.isRemoved()) be.convert();
        } finally { be.working = false; }
    }
    /** Exchanges complete resource batches without exceeding storage or work limits. */
    protected abstract void convert();
    /** Pulls FE below half capacity or pushes the surplus above half, within one shared work limit. */
    private void serviceFeNeighbors() {
        long delta = fe.stored() - fe.capacity() / 2;
        int remaining = (int) Math.min(Math.abs(delta), fePerTick);
        for (int offset = 0; offset < 6 && remaining > 0 && !isRemoved(); offset++) {
            var side = Direction.from3DDataValue((cursor + offset) % 6);
            var pos = worldPosition.relative(side);
            if (!level.isLoaded(pos)) continue;
            var target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side.getOpposite());
            if (target == null || target == fePort) continue;
            remaining -= delta < 0 ? pullFe(target, remaining) : pushFe(target, remaining);
        }
        cursor = (cursor + 1) % 6;
    }

    /** Pulls only the FE that fits; external callbacks cannot reenter this bridge.
     * @param source neighboring storage
     * @param limit remaining transfer allowance
     * @return extracted FE
     */
    private int pullFe(IEnergyStorage source, int limit) {
        if (!source.canExtract()) return 0;
        int offered = source.extractEnergy(limit, true);
        int amount = Math.min(offered, (int) fe.space());
        if (amount <= 0) return 0;
        int actual = source.extractEnergy(amount, false);
        if (!isRemoved()) fe.insert(actual, false);
        else source.receiveEnergy(actual, false);
        return actual;
    }

    /** Withdraws ownership before insertion and returns any rejected remainder to its store.
     * @param target neighboring storage
     * @param limit remaining transfer allowance
     * @return accepted FE
     */
    private int pushFe(IEnergyStorage target, int limit) {
        if (!target.canReceive()) return 0;
        int amount = Math.min(limit, target.receiveEnergy(limit, true));
        if (amount <= 0) return 0;
        int owned = (int) fe.extract(amount, false);
        int accepted = target.receiveEnergy(owned, false);
        if (!isRemoved()) fe.insert(owned - accepted, false);
        return accepted;
    }


    /** Saves common FE state.
     * @param tag destination tag
     * @param registries registry lookup
     */
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("FE", fe.serializeNBT(registries));
    }
    /** Restores common FE state through the configured capacity clamp.
     * @param tag saved tag
     * @param registries registry lookup
     */
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("FE")) fe.deserializeNBT(registries, tag.get("FE"));
    }
}
