package zone.moddev.mc.poweradvantage.api.distillation;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import zone.moddev.mc.poweradvantage.init.Recipes;

/**
 * Distillery recipe ({@code poweradvantage:distilling}). JSON form:
 * <pre>{"type":"poweradvantage:distilling","input":{"fluid":"poweradvantage:crude_oil","amount":2},
 * "output":{"id":"poweradvantage:refined_oil","amount":1}}</pre>
 * The input may use a fluid tag ({@code "tag"}) instead of {@code "fluid"}. An operation takes the input:output ratio
 * in ticks, rounded up and at least one, unless {@code "ticks"} overrides it.
 *
 * @param input fluid ingredient and amount consumed per operation
 * @param output fluid produced per operation
 * @param ticks optional explicit duration override, at least one
 */
public record DistillingRecipe(SizedFluidIngredient input, FluidStack output, Optional<Integer> ticks) implements Recipe<DistillingInput> {
    /**
     * JSON codec.
     */
    public static final MapCodec<DistillingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        SizedFluidIngredient.FLAT_CODEC.fieldOf("input").forGetter(DistillingRecipe::input),
        FluidStack.CODEC.fieldOf("output").forGetter(DistillingRecipe::output),
        com.mojang.serialization.Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("ticks").forGetter(DistillingRecipe::ticks)
    ).apply(i, DistillingRecipe::new));
    /**
     * Network codec.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, DistillingRecipe> STREAM_CODEC = StreamCodec.composite(
        SizedFluidIngredient.STREAM_CODEC, DistillingRecipe::input,
        FluidStack.STREAM_CODEC, DistillingRecipe::output,
        net.minecraft.network.codec.ByteBufCodecs.optional(net.minecraft.network.codec.ByteBufCodecs.VAR_INT), DistillingRecipe::ticks,
        DistillingRecipe::new);
    /**
     * Serializer registered as {@code poweradvantage:distilling}.
     */
    public static final RecipeSerializer<DistillingRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override public MapCodec<DistillingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, DistillingRecipe> streamCodec() { return STREAM_CODEC; }
    };

    /**
     * Finds the first recipe whose input accepts the fluid and whose full amount is present.
     *
     * @param level level whose recipe manager is searched
     * @param fluid fluid held by the input tank
     * @return matching recipe, if any
     */
    public static Optional<RecipeHolder<DistillingRecipe>> find(Level level, FluidStack fluid) {
        if (fluid.isEmpty()) return Optional.empty();
        var input = new DistillingInput(fluid);
        // RecipeManager.getRecipeFor treats a zero-slot input as empty, so match manually.
        return level.getRecipeManager().getAllRecipesFor(Recipes.DISTILLING_TYPE.get()).stream()
            .filter(h -> h.value().matches(input, level)).findFirst();
    }

    /**
     * Checks whether any recipe accepts this fluid regardless of amount.
     *
     * @param level level whose recipe manager is searched
     * @param fluid fluid to test
     * @return true when a recipe consumes it
     */
    public static boolean accepts(Level level, FluidStack fluid) {
        if (fluid.isEmpty()) return false;
        return level.getRecipeManager().getAllRecipesFor(Recipes.DISTILLING_TYPE.get()).stream()
            .anyMatch(h -> h.value().input.test(fluid));
    }

    /**
     * Ticks one operation takes.
     *
     * @return explicit override, else input:output ratio rounded up and at least one
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
     * Returns the registered serializer.
     *
     * @return distilling serializer
     */
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }

    /**
     * Returns the registered recipe type.
     *
     * @return distilling type
     */
    @Override public RecipeType<?> getType() { return Recipes.DISTILLING_TYPE.get(); }
}
