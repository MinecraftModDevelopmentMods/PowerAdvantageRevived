package zone.moddev.mc.poweradvantage.tiles;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.poweradvantage.init.Fluids;
import zone.moddev.mc.poweradvantage.menu.MachineMenu;
import zone.moddev.mc.poweradvantage.util.WorldFluids;
import static zone.moddev.mc.poweradvantage.tiles.MachineSettings.Kind.*;

public final class MachineBlockEntity extends BlockEntity implements MenuProvider {
    public final MachineSettings settings;
    public final FluidStorage fluids = new FluidStorage().onChange(this::contentsChanged);
    public final EnergyBuffer energy;
    private int workTicks;
    private int lastSignal;

    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineSettings settings) {
        super(type, pos, state);
        this.settings = settings;
        fluids.addTank(settings.capacity(), settings.kind() == STEAM ? Role.OUTPUT : Role.STORAGE,
            stack -> settings.kind() != STEAM || stack.getFluid().isSame(Fluids.STEAM.get()));
        energy = new EnergyBuffer(settings.capacity(), 0, 4096).onChange(this::contentsChanged);
    }
    public boolean isEnergy() { return settings.kind() == ENERGY; }
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
    public int redstoneSignal() {
        return settings.kind() == TANK ? (int) ((long) fluids.tank(0).getFluidAmount() * 15 / settings.capacity()) : 0;
    }
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
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fluids", fluids.serializeNBT(registries));
        tag.put("Energy", energy.serializeNBT(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fluids.deserializeNBT(registries, tag.getCompound("Fluids"));
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
    }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MachineMenu(id, inventory, worldPosition, new ContainerData() {
            public int get(int index) {
                int amount = isEnergy() ? energy.getEnergyStored() : fluids.tank(0).getFluidAmount();
                return switch (index) {
                    case 0 -> amount & 65535;
                    case 1 -> amount >>> 16;
                    case 2 -> settings.capacity() & 65535;
                    case 3 -> settings.capacity() >>> 16;
                    case 4 -> isEnergy() ? 1 : 0;
                    case 5 -> net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(fluids.tank(0).getFluid().getFluid());
                    default -> 0;
                };
            }
            public void set(int index, int value) {}
            public int getCount() { return 6; }
        });
    }
}
