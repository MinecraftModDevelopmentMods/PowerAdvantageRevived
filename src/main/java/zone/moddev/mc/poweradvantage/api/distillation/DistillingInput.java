package zone.moddev.mc.poweradvantage.api.distillation;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Recipe input carrying the fluid currently held by a Distillery's input tank.
 *
 * @param fluid offered fluid stack
 */
public record DistillingInput(FluidStack fluid) implements RecipeInput {
    /**
     * Distillery recipes have no item slots.
     *
     * @param index ignored
     * @return always the empty stack
     */
    @Override public ItemStack getItem(int index) { return ItemStack.EMPTY; }

    /**
     * Reports zero item slots.
     *
     * @return zero
     */
    @Override public int size() { return 0; }
}
