package zone.moddev.mc.poweradvantage.filter;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.*;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/** Persistent sorting endpoint between belts or inventories. Samples never enter the transported item stream. */
public final class FilterBlockEntity extends BlockEntity implements ScreenHost {
    /** Immutable predicate and per-attempt work bounds. */
    public final FilterSettings settings;
    /** Slot zero holds cargo; inventory filters additionally own nine real sample slots. */
    private final ItemStorage items;
    /** Saved work pacing, so chunk reloads do not accelerate the machine. */
    private int cooldown;
    /** Prevents capability reentry while this machine owns an in-progress transfer. */
    private boolean transferring;

    /** Creates a filter from its prepared MMDLib type and settings.
     * @param type registered block-entity type
     * @param pos block position
     * @param state initial block state
     * @param settings validated sorting and pacing settings
     */
    public FilterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, FilterSettings settings) {
        super(type, pos, state);
        this.settings = settings;
        items = new ItemStorage(settings.kind() == FilterSettings.Kind.INVENTORY ? 10 : 1).onChange(this::contentsChanged);
        if (items.getSlots() > 1) items.role(Role.MANUAL, 1,2,3,4,5,6,7,8,9).limit(1, 1,2,3,4,5,6,7,8,9);
    }

    /** Exposes the real cargo and sample slots to MMDLib's synchronized XML menu.
     * @return menu inventory
     */
    @Override public IItemHandlerModifiable screenItems() { return items; }

    /** Keeps shift-clicked player items out of the explicitly configured sample slots.
     * @param stack player inventory stack
     * @return only the transit slot
     */
    @Override public int[] screenDestinations(ItemStack stack) { return new int[]{0}; }

    /** Persists slot changes and refreshes comparator readers. */
    private void contentsChanged() {
        setChanged();
        if (level != null && !level.isClientSide) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    /** Reads the configured horizontal travel direction.
     * @return forward direction
     */
    private Direction facing() { return getBlockState().getValue(FilterBlock.FACING); }

    /** Tests whether this stack selects the lower output. A blocked matching output holds the cargo.
     * @param stack candidate cargo
     * @return true for downward routing
     */
    public boolean matches(ItemStack stack) {
        if (level == null || stack.isEmpty()) return false;
        if (settings.kind() == FilterSettings.Kind.INVENTORY) {
            for (int slot = 1; slot < items.getSlots(); slot++) if (ItemStack.isSameItem(items.getStackInSlot(slot), stack)) return true;
            return false;
        }
        if (settings.kind() == FilterSettings.Kind.OVERFLOW) return accepts(Direction.DOWN, stack) || !accepts(facing(), stack);
        return FilterPredicates.matches(settings.kind(), stack, level);
    }

    /** Reports PA2's empty, nonmatching and matching comparator strengths.
     * @return 0, 7 or 15
     */
    public int signal() { return items.getStackInSlot(0).isEmpty() ? 0 : matches(items.getStackInSlot(0)) ? 15 : 7; }

    /** Finds a loaded neighbor's correctly sided item capability without loading a chunk.
     * @param direction neighbor direction
     * @return item handler, or null
     */
    private IItemHandler neighbor(Direction direction) {
        var pos = worldPosition.relative(direction);
        return level != null && level.isLoaded(pos) ? level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction.getOpposite()) : null;
    }

    /** Checks live acceptance without altering the offered stack.
     * @param direction candidate output
     * @param stack candidate cargo
     * @return true if at least one item can be inserted
     */
    private boolean accepts(Direction direction, ItemStack stack) {
        var handler = neighbor(direction);
        return handler != null && ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true).getCount() < stack.getCount();
    }

    /** Gives automation a stable single-slot view that never exposes sample items.
     * @param side queried face; null permits insertion but not extraction
     * @return sided cargo handler
     */
    public IItemHandler automation(Direction side) {
        return new IItemHandler() {
            public int getSlots() { return 1; }
            public ItemStack getStackInSlot(int slot) { return slot == 0 ? items.getStackInSlot(0).copy() : ItemStack.EMPTY; }
            public int getSlotLimit(int slot) { return slot == 0 ? 64 : 0; }
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot == 0 && (side == null || side == Direction.UP || side == facing().getOpposite());
            }
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return !transferring && !isRemoved() && level != null && !level.isClientSide && isItemValid(slot, stack) ? items.insertItem(0, stack, simulate) : stack.copy();
            }
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (slot != 0 || transferring || isRemoved() || level == null || level.isClientSide) return ItemStack.EMPTY;
                // Guard overflow acceptance probes against cycles of neighboring filters.
                transferring = true;
                try {
                    var direction = matches(items.getStackInSlot(0)) ? Direction.DOWN : facing();
                    return side == direction ? items.extractItem(0, amount, simulate) : ItemStack.EMPTY;
                } finally { transferring = false; }
            }
        };
    }

    /** Runs one bounded transfer attempt at the configured interval.
     * @param level owning world
     * @param pos filter position
     * @param state current block state
     * @param be filter instance
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, FilterBlockEntity be) {
        if (level.isClientSide || be.transferring) return;
        if (be.cooldown > 0) { be.cooldown--; be.setChanged(); return; }
        be.transferring = true;
        try {
            if (be.items.getStackInSlot(0).isEmpty()) be.pull();
            else be.push();
            be.cooldown = be.settings.interval() - 1;
            be.setChanged();
        } finally { be.transferring = false; }
    }

    /** Pulls one batch from an upstream inventory; belts deliver their cargo through the input capability. */
    private void pull() {
        var source = neighbor(facing().getOpposite());
        if (source == null) return;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            var offered = source.extractItem(slot, settings.batch(), true);
            if (offered.isEmpty()) continue;
            int accepted = offered.getCount() - items.insertItem(0, offered.copy(), true).getCount();
            if (accepted == 0) continue;
            var actual = source.extractItem(slot, accepted, false);
            restore(actual);
            return;
        }
    }

    /** Moves at most one batch, retaining any rejected remainder in cargo slot zero. */
    private void push() {
        var direction = matches(items.getStackInSlot(0)) ? Direction.DOWN : facing();
        var targetPos = worldPosition.relative(direction);
        if (!level.isLoaded(targetPos)) return;
        var target = neighbor(direction);
        if (target != null) {
            var offered = items.extractItem(0, settings.batch(), false);
            var remainder = ItemHandlerHelper.insertItemStacked(target, offered, false);
            restore(remainder);
        } else if (direction == Direction.DOWN && level.getBlockState(targetPos).getCollisionShape(level, targetPos).isEmpty()
                && level.getBlockEntity(targetPos) == null) {
            var offered = items.extractItem(0, settings.batch(), false);
            var entity = new ItemEntity(level, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, offered);
            entity.setDeltaMovement(0, 0, 0);
            if (!level.addFreshEntity(entity)) restore(offered);
        }
    }

    /** Returns owned transfer remainders to storage, or drops them if a destination callback removed this filter.
     * @param stack items still owned by this transfer
     */
    private void restore(ItemStack stack) {
        if (stack.isEmpty()) return;
        var remainder = isRemoved() ? stack : items.insertItem(0, stack, false);
        if (!remainder.isEmpty()) net.minecraft.world.Containers.dropItemStack(level,
            worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), remainder);
    }

    /** Drops cargo and real samples when this block is replaced. */
    public void dropContents() { if (level != null) items.dropAll(level, worldPosition); }

    /** Writes inventory and pacing state.
     * @param tag destination tag
     * @param registries registry lookup
     */
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Cooldown", cooldown);
    }

    /** Restores cargo and sample slots without changing their configured count.
     * @param tag saved tag
     * @param registries registry lookup
     */
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("Items"));
        cooldown = Math.clamp(tag.getInt("Cooldown"), 0, settings.interval() - 1);
    }
}
