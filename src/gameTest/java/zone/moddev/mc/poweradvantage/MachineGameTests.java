package zone.moddev.mc.poweradvantage;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.lib.api.capabilities.Transfer;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.tiles.MachineBlockEntity;
import zone.moddev.mc.poweradvantage.util.WorldFluids;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class MachineGameTests {
    private static MachineBlockEntity place(GameTestHelper h, String name, BlockPos pos) {
        var id = ModContent.id(name);
        var block = BuiltInRegistries.BLOCK.get(id);
        h.assertTrue(block != Blocks.AIR, "Missing standalone block " + id);
        h.setBlock(pos, block);
        var be = h.getLevel().getBlockEntity(h.absolutePos(pos));
        h.assertTrue(be instanceof MachineBlockEntity, "Missing machine entity " + id);
        h.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).equals(id), "Wrong entity type " + id);
        return (MachineBlockEntity) be;
    }
    @GameTest(template = "empty")
    public static void registrationStorageAndPersistence(GameTestHelper h) {
        // The tanks are XML machines now; TankGameTests covers them.
        String[] names = {"fluid_drain", "fluid_discharge", "infinite_steam", "infinite_power"};
        int[] capacities = {2000, 1000, 131070, 65535};
        for (int i = 0; i < names.length; i++) {
            var be = place(h, names[i], new BlockPos(2+i,2,2));
            h.assertTrue(be.settings.capacity() == capacities[i], "Wrong data-driven capacity " + names[i]);
            for (Direction side : Direction.values()) {
                Object cap = be.isEnergy() ? h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), side)
                    : h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), side);
                h.assertTrue(cap != null, "Missing capability on " + side);
            }
        }
        var drain = (MachineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2,2,2)));
        drain.fluids.fill(new FluidStack(Fluids.WATER, 1500), FluidAction.EXECUTE);
        var saved = drain.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored = new MachineBlockEntity(drain.getType(), drain.getBlockPos(), drain.getBlockState(), drain.settings);
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(restored.fluids.tank(0).getFluidAmount() == 1500, "Machine NBT round trip");
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
    @GameTest(template = "empty")
    public static void generators(GameTestHelper h) {
        var energy = place(h, "infinite_power", new BlockPos(3,2,3));
        MachineBlockEntity.serverTick(h.getLevel(), energy.getBlockPos(), energy.getBlockState(), energy);
        var capability = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, energy.getBlockPos(), Direction.NORTH);
        var consumer = new EnergyStorage(10000);
        h.assertTrue(!capability.canReceive(), "Source must not accept energy");
        h.assertTrue(Transfer.energy(capability, consumer, 5000) == 4096 && consumer.getEnergyStored() == 4096, "Source output rate");
        var steam = place(h, "infinite_steam", new BlockPos(8,2,8));
        MachineBlockEntity.serverTick(h.getLevel(), steam.getBlockPos(), steam.getBlockState(), steam);
        var fluidCap = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, steam.getBlockPos(), Direction.NORTH);
        h.assertTrue(fluidCap.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE) == 0, "Steam source must refuse fluid input");
        h.assertTrue(fluidCap.drain(1000,FluidAction.EXECUTE).getAmount() == 1000, "Steam output");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 50)
    public static void drainTicksPushIntoNeighbour(GameTestHelper h) {
        var drain = place(h, "fluid_drain", new BlockPos(5,2,5));
        h.setBlock(new BlockPos(6,2,5), BuiltInRegistries.BLOCK.get(ModContent.id("fluid_storage_tank")));
        var sink = Machines.of(h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(6,2,5))));
        BlockPos source = drain.getBlockPos().above();
        for (Direction side : new Direction[]{Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})
            h.getLevel().setBlockAndUpdate(source.relative(side), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source, Blocks.WATER.defaultBlockState());
        h.runAfterDelay(25, () -> {
            h.assertTrue(sink.fluids.tank(0).getFluidAmount() == 1000, "Drain ticker must push collected water into adjacent tank");
            h.assertTrue(drain.fluids.tank(0).isEmpty() && h.getLevel().getFluidState(source).isEmpty(), "Drain must conserve the collected bucket");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 50)
    public static void dischargeTicksPullFromNeighbour(GameTestHelper h) {
        var discharge = place(h, "fluid_discharge", new BlockPos(5,3,5));
        h.setBlock(new BlockPos(6,3,5), BuiltInRegistries.BLOCK.get(ModContent.id("fluid_storage_tank")));
        var supply = Machines.of(h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(6,3,5))));
        supply.fluids.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE);
        BlockPos target = discharge.getBlockPos().below();
        for (Direction side : new Direction[]{Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})
            h.getLevel().setBlockAndUpdate(target.relative(side), Blocks.STONE.defaultBlockState());
        h.runAfterDelay(15, () -> {
            h.assertTrue(h.getLevel().getFluidState(target).isSource(), "Discharge ticker must place water below itself");
            h.assertTrue(supply.fluids.tank(0).isEmpty() && discharge.fluids.tank(0).isEmpty(), "Discharge must consume exactly one bucket");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void distillerConvertsCrudeToRefined(GameTestHelper h) {
        var recipe = zone.moddev.mc.poweradvantage.api.distillation.DistillingRecipe.find(h.getLevel(),
            new FluidStack(zone.moddev.mc.poweradvantage.init.Fluids.CRUDE_OIL.get(), 2));
        h.assertTrue(recipe.isPresent() && recipe.get().value().input().amount() == 2 && recipe.get().value().output().getAmount() == 1
            && recipe.get().value().output().getFluid() == zone.moddev.mc.poweradvantage.init.Fluids.REFINED_OIL.get(), "Default 2:1 data recipe");
        var still = place(h, "still", new BlockPos(2,2,2));
        h.assertTrue(still.fluids.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0, "Input filter");
        h.assertTrue(still.fluids.fill(new FluidStack(zone.moddev.mc.poweradvantage.init.Fluids.CRUDE_OIL.get(), 1000), FluidAction.EXECUTE) == 1000, "Crude accepted");
        MachineBlockEntity.serverTick(h.getLevel(), still.getBlockPos(), still.getBlockState(), still);
        h.assertTrue(still.fluids.tank(0).getFluidAmount() == 998 && still.fluids.tank(1).isEmpty(), "Input consumed at start, output pending");
        MachineBlockEntity.serverTick(h.getLevel(), still.getBlockPos(), still.getBlockState(), still);
        h.assertTrue(still.fluids.tank(1).getFluidAmount() == 1, "2:1 recipe finishes in 2 ticks");
        h.assertTrue(new zone.moddev.mc.poweradvantage.api.distillation.DistillingRecipe(net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.of(Fluids.WATER, 10), new FluidStack(Fluids.LAVA, 2), java.util.Optional.empty()).duration() == 5, "10:2 takes 5 ticks");
        h.assertTrue(still.fluids.tank(1).getFluid().getFluid() == zone.moddev.mc.poweradvantage.init.Fluids.REFINED_OIL.get(), "Refined output");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void distillerAcceptsCommonCrudeOilTags(GameTestHelper h) {
        var crude = new FluidStack(zone.moddev.mc.poweradvantage.init.Fluids.CRUDE_OIL.get(), 1);
        for (var tag : new String[]{"crude_oil", "oils/crude"})
            h.assertTrue(crude.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", tag))), "PA crude oil must be in c:" + tag);
        // GameTest data shadows c:oils/crude (same pack), repeating PA crude oil and adding lava as another mod's oil.
        var still = place(h, "still", new BlockPos(2,2,2));
        h.assertTrue(still.fluids.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 1000, "Tagged foreign crude oil accepted");
        MachineBlockEntity.serverTick(h.getLevel(), still.getBlockPos(), still.getBlockState(), still);
        MachineBlockEntity.serverTick(h.getLevel(), still.getBlockPos(), still.getBlockState(), still);
        h.assertTrue(still.fluids.tank(1).getFluid().getFluid() == zone.moddev.mc.poweradvantage.init.Fluids.REFINED_OIL.get(), "Tagged foreign crude oil distills to refined oil");
        h.succeed();
    }
}
