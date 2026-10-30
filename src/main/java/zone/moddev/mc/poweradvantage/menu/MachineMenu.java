package zone.moddev.mc.poweradvantage.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.tiles.MachineBlockEntity;

/** A synchronized status menu. Buckets interact directly with the block. */
public final class MachineMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerData data;
    public MachineMenu(int id, Inventory inventory, BlockPos pos) { this(id, inventory, pos, new SimpleContainerData(6)); }
    public MachineMenu(int id, Inventory inventory, BlockPos pos, ContainerData data) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
    }
    public int amount() { return (data.get(0) & 65535) | ((data.get(1) & 65535) << 16); }
    public int capacity() { return (data.get(2) & 65535) | ((data.get(3) & 65535) << 16); }
    public boolean isEnergy() { return data.get(4) == 1; }
    public int fluidId() { return data.get(5) & 65535; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof MachineBlockEntity && player.distanceToSqr(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5) <= 64;
    }
}
