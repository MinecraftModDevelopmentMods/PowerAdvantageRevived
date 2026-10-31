package zone.moddev.mc.poweradvantage;

import java.util.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.registries.RegisterEvent;
import zone.moddev.mc.poweradvantage.init.ModContent;

@EventBusSubscriber(modid = PowerAdvantage.MODID, bus = EventBusSubscriber.Bus.MOD)
@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class AlternateRecipeGameTests {
    @SubscribeEvent public static void items(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> {
            helper.register(ModContent.id("test_recipe_steel_ingot"), new Item(new Item.Properties()));
            helper.register(ModContent.id("test_recipe_metal_rod"), new Item(new Item.Properties()));
        });
    }
    private static Item item(String name) { return BuiltInRegistries.ITEM.get(ModContent.id(name)); }
    private static Recipe<?> recipe(GameTestHelper h, String name) {
        var recipe = h.getLevel().getRecipeManager().byKey(ModContent.id(name));
        h.assertTrue(recipe.isPresent() && recipe.get().value() instanceof ShapedRecipe, "Missing shaped recipe " + name);
        return recipe.orElseThrow().value();
    }
    private static CraftingInput grid(Item... items) {
        return CraftingInput.of(3, 3, Arrays.stream(items).map(item -> item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item)).toList());
    }
    @GameTest(template = "empty")
    public static void copperPipeRetainsPa2ShapeAndSixItemYield(GameTestHelper h) {
        var recipe = (ShapedRecipe) recipe(h, "fluid_pipe_copper");
        var grid = grid(Items.COPPER_INGOT, Items.COPPER_INGOT, Items.COPPER_INGOT,
            Items.AIR, Items.AIR, Items.AIR, Items.COPPER_INGOT, Items.COPPER_INGOT, Items.COPPER_INGOT);
        h.assertTrue(recipe.matches(grid, h.getLevel()), "Copper ingots must craft pipes without a metal mod");
        var output = recipe.assemble(grid, h.getLevel().registryAccess());
        h.assertTrue(output.is(item("fluid_pipe")) && output.getCount() == 6, "PA2 copper pipe yield must remain six");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void steelTankAcceptsTaggedAddonSteel(GameTestHelper h) {
        var recipe = (ShapedRecipe) recipe(h, "fluid_metal_tank_steel");
        var steel = item("test_recipe_steel_ingot");
        var grid = grid(steel, steel, steel, steel, item("fluid_pipe"), steel, steel, steel, steel);
        h.assertTrue(recipe.matches(grid, h.getLevel()), "Steel tank must accept another provider's tagged steel");
        var output = recipe.assemble(grid, h.getLevel().registryAccess());
        h.assertTrue(output.is(item("fluid_metal_tank")) && output.getCount() == 1, "Steel tank recipe result");
        h.assertTrue(!recipe.matches(grid(steel, steel, steel, steel, Items.STICK, steel, steel, steel, steel), h.getLevel()), "Tank center must be a tagged pipe");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void sprocketsAcceptTaggedNonWoodenRods(GameTestHelper h) {
        var recipe = (ShapedRecipe) recipe(h, "sprocket_rod");
        var steel = item("test_recipe_steel_ingot");
        var grid = grid(Items.AIR, steel, Items.AIR, steel, item("test_recipe_metal_rod"), steel, Items.AIR, steel, Items.AIR);
        h.assertTrue(recipe.matches(grid, h.getLevel()), "A generic tagged metal rod must work without being a wooden stick");
        var output = recipe.assemble(grid, h.getLevel().registryAccess());
        h.assertTrue(output.is(item("sprocket")) && output.getCount() == 4, "PA2 sprocket yield must remain four");
        h.assertTrue(!recipe.matches(grid(Items.AIR, steel, Items.AIR, steel, Items.DIAMOND, steel, Items.AIR, steel, Items.AIR), h.getLevel()), "An untagged center ingredient must fail");
        h.succeed();
    }
}
