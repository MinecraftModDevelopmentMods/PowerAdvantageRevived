package zone.moddev.mc.poweradvantage.api.distillation;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/**
 * A read-only view of one {@code poweradvantage:distilling} recipe, for mods written against Power Advantage 3.0 and
 * 3.1.
 *
 * <p>Since 3.2 the Distillery is an MMDLib XML machine, and {@code poweradvantage:distilling} is the MMDLib machine
 * recipe type it declares. Recipes use MMDLib's machine-recipe JSON:
 * <pre>{"type":"poweradvantage:distilling",
 * "inputs":{"input":[{"fluids":"#poweradvantage:crude_oils","amount":100}]},
 * "outputs":{"output":[{"fluid":{"id":"poweradvantage:refined_oil","amount":50}}]},
 * "time":100}</pre>
 * {@link #find} and {@link #accepts} read those recipes and present each one that has exactly one fluid input and
 * one fluid output as a {@code DistillingRecipe}, so callers keep working and see recipe changes from data packs.
 * Views are never registered or serialized.
 *
 * @param input fluid ingredient and amount consumed per operation
 * @param output fluid produced per operation
 * @param ticks the recipe's {@code time}
 * @deprecated Read the {@code poweradvantage:distilling} machine recipe type through MMDLib instead
 *             ({@code MachineContent.recipeType(DistillingRecipe.TYPE)}). Kept for Power Advantage 3.x addons.
 */
@Deprecated
public record DistillingRecipe(SizedFluidIngredient input, FluidStack output, Optional<Integer> ticks) implements Recipe<DistillingInput> {
    /**
     * ID of the MMDLib machine recipe type declared by the Distillery's XML definition.
     */
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(PowerAdvantage.MODID, "distilling");

    /**
     * Finds the first distilling recipe whose input fluid and full amount the offered stack satisfies.
     *
     * @param level world whose recipe manager is searched
     * @param fluid offered fluid and amount
     * @return the matching recipe view, if any
     */
    public static Optional<RecipeHolder<DistillingRecipe>> find(Level level, FluidStack fluid) {
        if (fluid.isEmpty()) return Optional.empty();
        var offered = new DistillingInput(fluid);
        return all(level).filter(holder -> holder.value().matches(offered, level)).findFirst();
    }

    /**
     * Checks whether any distilling recipe accepts this fluid, whatever its amount.
     *
     * @param level world whose recipe manager is searched
     * @param fluid offered fluid
     * @return true when some recipe uses the fluid
     */
    public static boolean accepts(Level level, FluidStack fluid) {
        return !fluid.isEmpty() && all(level).anyMatch(holder -> holder.value().input().ingredient().test(fluid));
    }

    /**
     * Views every loaded distilling recipe that this record can represent.
     *
     * @param level world whose recipe manager is searched
     * @return recipe views in recipe-manager order
     */
    private static Stream<RecipeHolder<DistillingRecipe>> all(Level level) {
        return type().recipes(level.getRecipeManager()).stream()
            .flatMap(holder -> view(holder.value().recipe()).map(view -> new RecipeHolder<>(holder.id(), view)).stream());
    }

    /**
     * Presents a machine recipe with exactly one consumed fluid input and one fluid output.
     *
     * @param recipe MMDLib machine recipe
     * @return its view, or empty when it has other inputs or outputs
     */
    static Optional<DistillingRecipe> view(MachineRecipe recipe) {
        List<MachineIngredient> inputs = recipe.inputs().values().stream().flatMap(List::stream).toList();
        List<MachineProduct> outputs = recipe.outputs().values().stream().flatMap(List::stream).toList();
        if (inputs.size() != 1 || outputs.size() != 1
            || !(inputs.getFirst() instanceof MachineIngredient.FluidPart in) || !in.consumed()
            || !(outputs.getFirst() instanceof MachineProduct.FluidProduct out)) return Optional.empty();
        FluidIngredient ingredient;
        var tag = in.fluids().unwrapKey();
        if (tag.isPresent()) ingredient = FluidIngredient.tag(tag.get());
        else if (in.fluids().size() > 0) ingredient = FluidIngredient.of(in.fluids().stream().map(Holder::value).toArray(Fluid[]::new));
        else return Optional.empty();
        var product = out.fluid();
        return Optional.of(new DistillingRecipe(new SizedFluidIngredient(ingredient, in.millibuckets()),
            new FluidStack(product.fluid(), product.amount(), product.components()), Optional.of(recipe.time())));
    }

    /**
     * The registered MMDLib machine recipe type.
     *
     * @return {@code poweradvantage:distilling}
     */
    private static MachineRecipeType type() { return MachineContent.recipeType(TYPE); }

    /**
     * Ticks one operation takes.
     *
     * @return the recipe's {@code time}; views always carry it
     */
    public int duration() { return ticks.orElseGet(() -> Math.max(1, Math.ceilDiv(input.amount(), Math.max(1, output.getAmount())))); }

    /**
     * Matches when the offered stack satisfies the input fluid and amount.
     *
     * @param container offered fluid
     * @param level world context
     * @return true on match
     */
    @Override public boolean matches(DistillingInput container, Level level) {
        return input.test(container.fluid()) && container.fluid().getAmount() >= input.amount();
    }

    /**
     * Fluid recipes produce no item.
     *
     * @param container input
     * @param registries registry lookup
     * @return empty stack
     */
    @Override public ItemStack assemble(DistillingInput container, HolderLookup.Provider registries) { return ItemStack.EMPTY; }

    /**
     * Fluid recipes have no grid.
     *
     * @param width ignored
     * @param height ignored
     * @return true
     */
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }

    /**
     * Fluid recipes produce no item.
     *
     * @param registries registry lookup
     * @return empty stack
     */
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }

    /**
     * Keeps these recipes out of the recipe book.
     *
     * @return true
     */
    @Override public boolean isSpecial() { return true; }

    /**
     * The machine recipe type's serializer. Views are never serialized.
     *
     * @return the {@code poweradvantage:distilling} serializer
     */
    @Override public RecipeSerializer<?> getSerializer() { return type().serializer(); }

    /**
     * The machine recipe type these views are read from.
     *
     * @return {@code poweradvantage:distilling}
     */
    @Override public RecipeType<?> getType() { return type(); }
}
