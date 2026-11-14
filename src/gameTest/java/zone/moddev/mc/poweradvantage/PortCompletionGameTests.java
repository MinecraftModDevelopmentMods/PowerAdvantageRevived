package zone.moddev.mc.poweradvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.registries.RegisterEvent;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;
import zone.moddev.mc.poweradvantage.init.*;

@EventBusSubscriber(modid = PowerAdvantage.MODID, bus = EventBusSubscriber.Bus.MOD)
@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class PortCompletionGameTests {
    @SubscribeEvent public static void items(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> {
            helper.register(ModContent.id("test_coal_dust"), new Item(new Item.Properties()));
            helper.register(ModContent.id("test_carbon_dust"), new Item(new Item.Properties()));
        });
    }

    @GameTest(template = "empty")
    public static void oilBucketsAndTaggedDustsUseStandardFuelLookup(GameTestHelper h) {
        h.assertTrue(new ItemStack(Fluids.CRUDE_OIL_BUCKET.get()).getBurnTime(RecipeType.SMELTING) == 5000, "Crude oil bucket must burn for 5,000 ticks");
        h.assertTrue(new ItemStack(Fluids.REFINED_OIL_BUCKET.get()).getBurnTime(RecipeType.SMELTING) == 25000, "Refined oil bucket must burn for 25,000 ticks");
        h.assertTrue(new ItemStack(Fluids.STEAM_BUCKET.get()).getBurnTime(RecipeType.SMELTING) == 0, "Steam must not become fuel");
        for (String name : new String[]{"test_coal_dust", "test_carbon_dust"}) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(ModContent.id(name)));
            h.assertTrue(stack.getBurnTime(RecipeType.SMELTING) == 1600, "Common-tag fuel registration missing for " + name);
        }
        h.assertTrue(new ItemStack(Items.COAL).getBurnTime(RecipeType.SMELTING) == 1600, "PA data map must preserve vanilla fuels");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void burningRefinedOilReturnsAnEmptyBucket(GameTestHelper h) {
        var pos = new BlockPos(3, 3, 3);
        h.setBlock(pos, Blocks.FURNACE);
        var furnace = (FurnaceBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        furnace.setItem(0, new ItemStack(Items.POTATO));
        furnace.setItem(1, new ItemStack(Fluids.REFINED_OIL_BUCKET.get()));
        h.runAfterDelay(5, () -> {
            h.assertTrue(furnace.getItem(1).is(Items.BUCKET), "A real furnace must retain the empty oil bucket");
            int remaining = furnace.saveWithoutMetadata(h.getLevel().registryAccess()).getShort("BurnTime");
            h.assertTrue(remaining > 24990 && remaining <= 25000, "Furnace must actually ignite using the oil data-map fuel");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void potatoCrushingAndStarchSmeltingRecipesFormAProductionPath(GameTestHelper h) {
        var level = h.getLevel();
        for (var item : new Item[]{Items.POTATO, Items.POISONOUS_POTATO}) {
            var input = new SingleRecipeInput(new ItemStack(item));
            var recipe = level.getRecipeManager().getRecipeFor(CrushingRecipe.TYPE.get(), input, level);
            h.assertTrue(recipe.isPresent(), "MMDLib must find a crushing recipe for " + item);
            var starch = recipe.orElseThrow().value().assemble(input, level.registryAccess());
            h.assertTrue(starch.is(ModItems.STARCH.get()) && starch.getCount() == 1, "Crushing must produce exactly one starch");
            var smeltingInput = new SingleRecipeInput(starch);
            var smelting = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, smeltingInput, level);
            h.assertTrue(smelting.isPresent() && smelting.orElseThrow().value().assemble(smeltingInput, level.registryAccess()).is(ModItems.BIOPLASTIC_INGOT.get()),
                "Crushed starch must smelt into bioplastic");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void machinesUseOnlyMmdlibMenus(GameTestHelper h) {
        h.assertTrue(!BuiltInRegistries.MENU.containsKey(ModContent.id("machine")), "Obsolete PA menu type must be removed");
        for (String name : new String[]{"fluid_storage_tank", "fluid_metal_tank", "fluid_drain", "fluid_discharge", "infinite_steam", "infinite_power", "still"}) {
            var pos = new BlockPos(3, 3, 3);
            h.setBlock(pos, BuiltInRegistries.BLOCK.get(ModContent.id(name)));
            var be = h.getLevel().getBlockEntity(h.absolutePos(pos));
            var menu = new zone.moddev.mc.lib.gui.runtime.MachineMenu(1,
                h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL).getInventory(), be);
            h.assertTrue(menu.getType() == zone.moddev.mc.lib.gui.runtime.ScreenRuntime.MENUS.get(), "Machine must bind to MMDLib menu: " + name);
        }
        h.succeed();
    }
}
