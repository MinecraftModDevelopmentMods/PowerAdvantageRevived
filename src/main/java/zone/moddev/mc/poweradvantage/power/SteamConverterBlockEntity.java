package zone.moddev.mc.poweradvantage.power;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.poweradvantage.init.Fluids;

/** Exact bidirectional steam/FE bridge. Steam amounts are millibuckets, as in PA2's fluid-energy API. */
public final class SteamConverterBlockEntity extends BufferedConverterBlockEntity {
    /** Validated data-defined settings. */
    public final SteamConverterSettings settings;
    /** MMDLib-owned steam-only tank. */
    private final FluidStorage steam = new FluidStorage().onChange(this::setChanged);
    /** Stable guarded fluid capability. */
    private final IFluidHandler steamPort;

    /** Creates a registered steam converter.
     * @param type registered entity type
     * @param pos position
     * @param state block state
     * @param settings factory settings
     */
    public SteamConverterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, SteamConverterSettings settings) {
        super(type, pos, state, settings.feCapacity(), settings.fePerTick());
        this.settings = settings;
        steam.addTank(settings.steamCapacity(), Role.STORAGE, stack -> stack.getFluid().isSame(Fluids.STEAM.get()));
        steamPort = createSteamPort();
    }
    /** Returns the guarded steam-only handler on every face.
     * @return fluid capability
     */
    public IFluidHandler steamPort() { return steamPort; }
    /** Returns a snapshot for MMDLib's fluid gauge.
     * @return steam stack
     */
    public FluidStack screenSteam() { return steam.tank(0).getFluid().copy(); }
    /** Returns the fluid capacity for XML binding.
     * @return steam capacity in mB
     */
    public int screenSteamCapacity() { return settings.steamCapacity(); }

    /** Creates a non-reentrant handler whose snapshots cannot mutate owned fluid.
     * @return guarded fluid view
     */
    private IFluidHandler createSteamPort() {
        return new IFluidHandler() {
            public int getTanks() { return 1; }
            public FluidStack getFluidInTank(int tank) { return tank == 0 ? screenSteam() : FluidStack.EMPTY; }
            public int getTankCapacity(int tank) { return tank == 0 ? settings.steamCapacity() : 0; }
            public boolean isFluidValid(int tank, FluidStack fluid) { return tank == 0 && !fluid.isEmpty() && steam.isFluidValid(tank, fluid); }
            public int fill(FluidStack resource, FluidAction action) { return available() ? steam.fill(resource, action) : 0; }
            public FluidStack drain(FluidStack resource, FluidAction action) { return available() ? steam.drain(resource, action) : FluidStack.EMPTY; }
            public FluidStack drain(int maximum, FluidAction action) { return available() && maximum > 0 ? steam.drain(maximum, action) : FluidStack.EMPTY; }
        };
    }

    /** Balances steam around half capacity using complete batches and a separate FE-equivalent allowance. */
    @Override protected void convert() {
        long delta = (long) steam.tank(0).getFluidAmount() - settings.steamCapacity() / 2;
        long maximum = settings.fePerTick() / settings.feQuantum();
        if (delta > 0) {
            long batches = Math.min(maximum, Math.min(delta / settings.steamQuantum(), fe.space() / settings.feQuantum()));
            steam.tank(0).drain((int) (batches * settings.steamQuantum()), IFluidHandler.FluidAction.EXECUTE);
            fe.insert(batches * settings.feQuantum(), false);
        } else if (delta < 0) {
            long batches = Math.min(maximum, Math.min(-delta / settings.steamQuantum(), fe.stored() / settings.feQuantum()));
            fe.extract(batches * settings.feQuantum(), false);
            steam.tank(0).fill(new FluidStack(Fluids.STEAM.get(), (int) (batches * settings.steamQuantum())), IFluidHandler.FluidAction.EXECUTE);
        }
    }
    /** Saves the steam tank alongside inherited FE state.
     * @param tag destination tag
     * @param registries registry lookup
     */
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Steam", steam.serializeNBT(registries));
    }
    /** Restores both stores without converting their units.
     * @param tag saved tag
     * @param registries registry lookup
     */
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        steam.deserializeNBT(registries, tag.getCompound("Steam"));
    }
}
