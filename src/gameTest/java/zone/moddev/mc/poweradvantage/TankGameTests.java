package zone.moddev.mc.poweradvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.poweradvantage.init.ModContent;

/**
 * The fluid tanks as MMDLib XML machines: registration, storage, the fill-level redstone signal, and loading tanks
 * saved by Power Advantage 3.0 and 3.1.
 */
@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class TankGameTests {
    private static DefinedMachineEntity place(GameTestHelper h, String name, BlockPos pos) {
        var id = ModContent.id(name);
        var block = BuiltInRegistries.BLOCK.get(id);
        h.assertTrue(block != Blocks.AIR, "Missing tank block " + id);
        h.setBlock(pos, block);
        var be = h.getLevel().getBlockEntity(h.absolutePos(pos));
        h.assertTrue(be instanceof DefinedMachineEntity, "Tank must be an MMDLib XML machine: " + id);
        h.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).equals(id),
            "Tank block-entity type must keep the block's ID, so saved tanks find it: " + id);
        return (DefinedMachineEntity) be;
    }

    private static int signal(GameTestHelper h, DefinedMachineEntity tank, Direction side) {
        var pos = tank.getBlockPos();
        return h.getLevel().getBlockState(pos).getSignal(h.getLevel(), pos, side);
    }

    /**
     * The block-entity NBT a 3.0 or 3.1 tank saved: its MachineBlockEntity wrote one MMDLib FluidStorage under
     * {@code Fluids}, its unused energy buffer under {@code Energy}, and the Distillery's progress counter.
     */
    private static CompoundTag savedBy31(GameTestHelper h, int capacity, FluidStack contents) {
        var registries = h.getLevel().registryAccess();
        var fluids = new FluidStorage();
        fluids.addTank(capacity, Role.STORAGE, stack -> true);
        fluids.fill(contents, FluidAction.EXECUTE);
        var tag = new CompoundTag();
        tag.put("Fluids", fluids.serializeNBT(registries));
        tag.put("Energy", new EnergyBuffer(capacity, 0, 4096).serializeNBT(registries));
        tag.putInt("DistillProgress", 0);
        return tag;
    }

    @GameTest(template = "empty")
    public static void tanksAreXmlMachinesWithFluidCapabilities(GameTestHelper h) {
        String[] names = {"fluid_storage_tank", "fluid_metal_tank"};
        int[] capacities = {4000, 16000};
        for (int i = 0; i < names.length; i++) {
            var tank = place(h, names[i], new BlockPos(2 + 2 * i, 2, 2));
            h.assertTrue(tank.fluids.getTanks() == 1 && tank.fluids.getTankCapacity(0) == capacities[i],
                "Wrong tank capacity for " + names[i]);
            h.assertTrue(tank.items == null || tank.items.getSlots() == 0, "Tanks hold no items: " + names[i]);
            for (Direction side : Direction.values())
                h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, tank.getBlockPos(), side) != null,
                    "Missing fluid capability on " + side + " of " + names[i]);
        }
        var tank = place(h, "fluid_storage_tank", new BlockPos(8, 2, 2));
        tank.fluids.fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
        var target = new FluidTank(1000);
        h.assertTrue(Transfer.fluids(tank.fluids, target, 1000) == 1000, "Fluid transfer amount");
        h.assertTrue(tank.fluids.getFluidInTank(0).getAmount() == 1000 && target.getFluidAmount() == 1000, "Fluid conservation");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fillLevelDrivesRedstoneOnEveryFace(GameTestHelper h) {
        var tank = place(h, "fluid_storage_tank", new BlockPos(3, 2, 3));
        tank.fluids.fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
        h.runAfterDelay(5, () -> {
            for (Direction side : Direction.values())
                h.assertTrue(signal(h, tank, side) == 7, "Half-full 4000 mB tank must emit 7 toward " + side
                    + ", got " + signal(h, tank, side));
            tank.fluids.drain(2000, FluidAction.EXECUTE);
        });
        h.runAfterDelay(10, () -> {
            for (Direction side : Direction.values())
                h.assertTrue(signal(h, tank, side) == 0, "Empty tank must emit 0 toward " + side);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void faceChoicesSurviveSavesAndDefaultsApplyOnce(GameTestHelper h) {
        var tank = place(h, "fluid_storage_tank", new BlockPos(3, 2, 3));
        h.runAfterDelay(5, () -> {
            var redstone = tank.screenRedstone();
            for (int face = 0; face < 6; face++)
                h.assertTrue(redstone.mode(face) == RedstoneControl.Mode.EMIT, "New tank must emit on face " + face);
            redstone.mode(0, RedstoneControl.Mode.IGNORE);
            var saved = tank.saveWithoutMetadata(h.getLevel().registryAccess());
            var copy = place(h, "fluid_storage_tank", new BlockPos(6, 2, 3));
            copy.loadWithComponents(saved, h.getLevel().registryAccess());
        });
        h.runAfterDelay(15, () -> {
            var copy = (DefinedMachineEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(6, 2, 3)));
            var redstone = copy.screenRedstone();
            h.assertTrue(redstone.mode(0) == RedstoneControl.Mode.IGNORE, "A face the player changed must stay changed after a save");
            for (int face = 1; face < 6; face++)
                h.assertTrue(redstone.mode(face) == RedstoneControl.Mode.EMIT, "Other faces keep emitting: " + face);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tanksSavedBy31KeepTheirContents(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var small = place(h, "fluid_storage_tank", new BlockPos(2, 2, 3));
        small.loadWithComponents(savedBy31(h, 4000, new FluidStack(Fluids.WATER, 2500)), registries);
        var large = place(h, "fluid_metal_tank", new BlockPos(5, 2, 3));
        large.loadWithComponents(savedBy31(h, 16000, new FluidStack(Fluids.LAVA, 12000)), registries);
        h.assertTrue(small.fluids.getFluidInTank(0).is(Fluids.WATER) && small.fluids.getFluidInTank(0).getAmount() == 2500,
            "3.1 storage tank contents must load, got " + small.fluids.getFluidInTank(0).getAmount());
        h.assertTrue(large.fluids.getFluidInTank(0).is(Fluids.LAVA) && large.fluids.getFluidInTank(0).getAmount() == 12000,
            "3.1 metal tank contents must load, got " + large.fluids.getFluidInTank(0).getAmount());
        h.runAfterDelay(5, () -> {
            h.assertTrue(signal(h, small, Direction.NORTH) == 9, "A tank loaded from 3.1 must emit its fill level, got "
                + signal(h, small, Direction.NORTH));
            h.assertTrue(signal(h, large, Direction.UP) == 11, "A metal tank loaded from 3.1 must emit its fill level, got "
                + signal(h, large, Direction.UP));
            h.succeed();
        });
    }
}
