package zone.moddev.mc.poweradvantage;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.util.WorldFluids;

/**
 * The creative sources, Fluid Drain and Discharge Grate as MMDLib XML machines with Power Advantage processors.
 */
@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class MachineGameTests {
    private static DefinedMachineEntity place(GameTestHelper h, String name, BlockPos pos) {
        var id = ModContent.id(name);
        var block = BuiltInRegistries.BLOCK.get(id);
        h.assertTrue(block != Blocks.AIR, "Missing block " + id);
        h.setBlock(pos, block);
        var be = h.getLevel().getBlockEntity(h.absolutePos(pos));
        h.assertTrue(be instanceof DefinedMachineEntity, "Must be an MMDLib XML machine: " + id);
        h.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).equals(id),
            "Block-entity type must keep the block's ID, so saved machines find it: " + id);
        return (DefinedMachineEntity) be;
    }

    @GameTest(template = "empty")
    public static void registrationAndStorage(GameTestHelper h) {
        String[] fluidMachines = {"fluid_drain", "fluid_discharge", "infinite_steam"};
        int[] capacities = {2000, 1000, 131070};
        for (int i = 0; i < fluidMachines.length; i++) {
            var machine = place(h, fluidMachines[i], new BlockPos(2 + 2 * i, 2, 2));
            h.assertTrue(machine.fluids.getTanks() == 1 && machine.fluids.getTankCapacity(0) == capacities[i],
                "Wrong capacity for " + fluidMachines[i]);
            for (Direction side : Direction.values())
                h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, machine.getBlockPos(), side) != null,
                    "Missing fluid capability on " + side + " of " + fluidMachines[i]);
        }
        var power = place(h, "infinite_power", new BlockPos(8, 2, 2));
        h.assertTrue(power.energy != null && power.energy.capacity() == 65535, "Infinite power keeps a 65535 FE buffer");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drainsSavedBy31KeepTheirContents(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        // What 3.0/3.1's MachineBlockEntity saved for a Fluid Drain.
        var fluids = new FluidStorage();
        fluids.addTank(2000, Role.STORAGE, stack -> true);
        fluids.fill(new FluidStack(Fluids.WATER, 1500), FluidAction.EXECUTE);
        var tag = new CompoundTag();
        tag.put("Fluids", fluids.serializeNBT(registries));
        tag.put("Energy", new EnergyBuffer(2000, 0, 4096).serializeNBT(registries));
        tag.putInt("DistillProgress", 0);
        var drain = place(h, "fluid_drain", new BlockPos(3, 2, 3));
        drain.loadWithComponents(tag, registries);
        h.assertTrue(drain.fluids.getFluidInTank(0).is(Fluids.WATER) && drain.fluids.getFluidInTank(0).getAmount() == 1500,
            "3.1 drain contents must load, got " + drain.fluids.getFluidInTank(0).getAmount());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void worldFluidConservation(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(5,3,5));
        var level = h.getLevel();
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        var tooSmall = new FluidTank(500);
        WorldFluids.collect(level, pos, tooSmall);
        h.assertTrue(level.getFluidState(pos).isSource() && tooSmall.isEmpty(), "Partial bucket must not remove a source");
        var tank = new FluidTank(1500);
        WorldFluids.collect(level, pos, tank);
        h.assertTrue(level.getFluidState(pos).isEmpty() && tank.getFluidAmount() == 1000, "World pickup must conserve a bucket");
        for (Direction side : Direction.values()) level.setBlockAndUpdate(pos.relative(side), Blocks.STONE.defaultBlockState());
        WorldFluids.discharge(level, pos, tank);
        h.assertTrue(level.getFluidState(pos).isSource() && tank.isEmpty(), "World placement must consume one bucket");
        WorldFluids.discharge(level, pos, tank);
        h.assertTrue(level.getFluidState(pos).isSource() && tank.isEmpty(), "Empty tank must not place more fluid");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void infinitePowerPushesIntoNeighbours(GameTestHelper h) {
        var source = place(h, "infinite_power", new BlockPos(3, 2, 3));
        h.setBlock(new BlockPos(4, 2, 3), BuiltInRegistries.BLOCK.get(ModContent.id("converter_rf_electricity")));
        var converter = (DefinedMachineEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4, 2, 3)));
        h.runAfterDelay(5, () -> {
            h.assertTrue(converter.energy.stored() > 0, "Infinite power must push FE into a neighbouring consumer");
            h.assertTrue(source.energy.stored() == source.energy.capacity(), "Infinite power refills itself");
            var capability = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, source.getBlockPos(), Direction.NORTH);
            h.assertTrue(capability == null || !capability.canReceive(), "Infinite power must not accept FE");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void infiniteSteamSuppliesOnlySteam(GameTestHelper h) {
        var steam = place(h, "infinite_steam", new BlockPos(8, 2, 8));
        h.runAfterDelay(2, () -> {
            var fluidCap = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, steam.getBlockPos(), Direction.NORTH);
            h.assertTrue(fluidCap.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0, "Steam source must refuse fluid input");
            var drained = fluidCap.drain(1000, FluidAction.EXECUTE);
            h.assertTrue(drained.getAmount() == 1000 && drained.is(zone.moddev.mc.poweradvantage.init.Fluids.STEAM.get()), "Steam output");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void drainTicksPushIntoNeighbour(GameTestHelper h) {
        var drain = place(h, "fluid_drain", new BlockPos(5,2,5));
        var sink = place(h, "fluid_storage_tank", new BlockPos(6,2,5));
        BlockPos source = drain.getBlockPos().above();
        for (Direction side : new Direction[]{Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})
            h.getLevel().setBlockAndUpdate(source.relative(side), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source, Blocks.WATER.defaultBlockState());
        h.runAfterDelay(25, () -> {
            h.assertTrue(sink.fluids.getFluidInTank(0).getAmount() == 1000, "Drain must push collected water into adjacent tank");
            h.assertTrue(drain.fluids.getFluidInTank(0).isEmpty() && h.getLevel().getFluidState(source).isEmpty(), "Drain must conserve the collected bucket");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void dischargeTicksPullFromNeighbour(GameTestHelper h) {
        var discharge = place(h, "fluid_discharge", new BlockPos(5,3,5));
        var supply = place(h, "fluid_storage_tank", new BlockPos(6,3,5));
        supply.fluids.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE);
        BlockPos target = discharge.getBlockPos().below();
        for (Direction side : new Direction[]{Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})
            h.getLevel().setBlockAndUpdate(target.relative(side), Blocks.STONE.defaultBlockState());
        h.runAfterDelay(25, () -> {
            h.assertTrue(h.getLevel().getFluidState(target).isSource(), "Discharge must place water below itself");
            h.assertTrue(supply.fluids.getFluidInTank(0).isEmpty() && discharge.fluids.getFluidInTank(0).isEmpty(), "Discharge must consume exactly one bucket");
            h.succeed();
        });
    }
}
