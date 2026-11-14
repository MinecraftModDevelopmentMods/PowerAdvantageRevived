package zone.moddev.mc.poweradvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.poweradvantage.api.distillation.DistillingRecipe;
import zone.moddev.mc.poweradvantage.init.Fluids;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.init.ModItems;

/**
 * The Distillery as an MMDLib XML machine: its recipe, the 3.0/3.1 compatibility API that addons call, loading
 * stills saved by 3.1, and the fluid pump addons that replace 3.1's built-in neighbour transfer.
 */
@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("deprecation")
public final class DistilleryGameTests {
    private static Fluid crude() { return Fluids.CRUDE_OIL.get(); }
    private static Fluid refined() { return Fluids.REFINED_OIL.get(); }

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

    private static void install(GameTestHelper h, DefinedMachineEntity machine, String group, ItemStack addon) {
        var automation = machine.screenAutomation();
        for (int i = 0; i < automation.groups().size(); i++)
            if (automation.groups().get(i).id().equals(group)) {
                automation.slots().setStackInSlot(AutomationInventory.addonSlot(i, 0), addon);
                return;
            }
        h.fail("No automation group " + group);
    }

    @GameTest(template = "empty")
    public static void compatibilityApiReadsTheMachineRecipe(GameTestHelper h) {
        var level = h.getLevel();
        var found = DistillingRecipe.find(level, new FluidStack(crude(), 100));
        h.assertTrue(found.isPresent(), "3.1 API must find the default recipe");
        var recipe = found.get().value();
        h.assertTrue(recipe.input().amount() == 100 && recipe.output().is(refined()) && recipe.output().getAmount() == 50
            && recipe.duration() == 100, "Default recipe: 100 crude to 50 refined over 100 ticks");
        h.assertTrue(found.get().id().equals(ModContent.id("distilling_refined_oil")), "Recipe ID is kept");
        h.assertTrue(DistillingRecipe.find(level, new FluidStack(crude(), 99)).isEmpty(), "A partial input must not match");
        h.assertTrue(DistillingRecipe.accepts(level, new FluidStack(crude(), 1)), "Crude oil is accepted whatever the amount");
        h.assertTrue(!DistillingRecipe.accepts(level, new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000)),
            "Water is not distillable");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stillIsAnXmlMachineThatFiltersItsInput(GameTestHelper h) {
        var still = place(h, "still", new BlockPos(3, 2, 3));
        h.assertTrue(still.fluids.getTanks() == 2 && still.fluids.getTankCapacity(0) == 4000 && still.fluids.getTankCapacity(1) == 4000,
            "Two 4000 mB tanks");
        var input = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, still.getBlockPos(), Direction.NORTH);
        h.assertTrue(input != null, "Fluid capability");
        h.assertTrue(input.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), FluidAction.EXECUTE) == 0,
            "Input only takes fluids a recipe uses");
        h.assertTrue(input.fill(new FluidStack(crude(), 1000), FluidAction.EXECUTE) == 1000, "Crude oil accepted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void distillsCrudeIntoRefinedOilAndLightsUp(GameTestHelper h) {
        var still = place(h, "still", new BlockPos(3, 2, 3));
        still.fluids.fill(new FluidStack(crude(), 1000), FluidAction.EXECUTE);
        h.runAfterDelay(50, () -> h.assertTrue(still.running(), "The still runs, which lights its front"));
        h.runAfterDelay(250, () -> {
            var output = still.fluids.getFluidInTank(1);
            int input = still.fluids.getFluidInTank(0).getAmount();
            h.assertTrue(output.is(refined()) && output.getAmount() >= 100 && output.getAmount() % 50 == 0,
                "Two cycles in 250 ticks make at least 100 mB refined oil, got " + output.getAmount());
            h.assertTrue(input <= 1000 - 2 * output.getAmount(), "Each 50 mB of refined oil costs 100 mB of crude");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void otherModsCrudeOilDistillsThroughCommonTags(GameTestHelper h) {
        var crude = new FluidStack(crude(), 1);
        for (var tag : new String[]{"crude_oil", "oils/crude"})
            h.assertTrue(crude.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", tag))), "PA crude oil must be in c:" + tag);
        // GameTest data adds lava to c:oils/crude, standing in for another mod's oil.
        var still = place(h, "still", new BlockPos(3, 2, 3));
        h.assertTrue(still.fluids.fill(new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000), FluidAction.EXECUTE) == 1000,
            "Tagged foreign crude oil accepted");
        h.runAfterDelay(150, () -> {
            h.assertTrue(still.fluids.getFluidInTank(1).is(refined()), "Tagged foreign crude oil distills to refined oil");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void stillsSavedBy31KeepTheirTanks(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        // What 3.0/3.1's MachineBlockEntity saved for a Distillery partway through an operation.
        var fluids = new FluidStorage();
        fluids.addTank(4000, Role.INPUT, stack -> true);
        fluids.addTank(4000, Role.OUTPUT);
        fluids.tank(0).fill(new FluidStack(crude(), 500), FluidAction.EXECUTE);
        fluids.tank(1).fill(new FluidStack(refined(), 300), FluidAction.EXECUTE);
        var tag = new CompoundTag();
        tag.put("Fluids", fluids.serializeNBT(registries));
        tag.put("Energy", new EnergyBuffer(4000, 0, 4096).serializeNBT(registries));
        tag.putInt("DistillProgress", 1);
        tag.put("DistillOutput", new FluidStack(refined(), 1).save(registries));
        var still = place(h, "still", new BlockPos(3, 2, 3));
        still.loadWithComponents(tag, registries);
        h.assertTrue(still.fluids.getFluidInTank(0).is(crude()) && still.fluids.getFluidInTank(0).getAmount() == 500,
            "3.1 input tank must load, got " + still.fluids.getFluidInTank(0).getAmount());
        h.assertTrue(still.fluids.getFluidInTank(1).is(refined()) && still.fluids.getFluidInTank(1).getAmount() == 300,
            "3.1 output tank must load, got " + still.fluids.getFluidInTank(1).getAmount());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void pumpAddonsPullCrudeAndPushRefinedOil(GameTestHelper h) {
        var still = place(h, "still", new BlockPos(3, 2, 3));
        var supply = place(h, "fluid_storage_tank", new BlockPos(4, 2, 3));
        var receiver = place(h, "fluid_storage_tank", new BlockPos(2, 2, 3));
        supply.fluids.fill(new FluidStack(crude(), 2000), FluidAction.EXECUTE);
        h.runAfterDelay(30, () -> h.assertTrue(still.fluids.getFluidInTank(0).isEmpty(),
            "Without pumps the still must not pull from its neighbours"));
        h.runAfterDelay(31, () -> {
            install(h, still, "input", new ItemStack(ModItems.FLUID_INTAKE_PUMP.get()));
            install(h, still, "output", new ItemStack(ModItems.FLUID_OUTPUT_PUMP.get()));
        });
        h.runAfterDelay(330, () -> {
            h.assertTrue(supply.fluids.getFluidInTank(0).getAmount() < 2000, "The intake pump must pull crude oil from the supply tank");
            int delivered = receiver.fluids.getFluidInTank(0).is(refined()) ? receiver.fluids.getFluidInTank(0).getAmount() : 0;
            delivered += supply.fluids.getFluidInTank(0).is(refined()) ? supply.fluids.getFluidInTank(0).getAmount() : 0;
            h.assertTrue(delivered >= 50, "The output pump must push refined oil into a neighbouring tank, got " + delivered);
            h.succeed();
        });
    }
}
