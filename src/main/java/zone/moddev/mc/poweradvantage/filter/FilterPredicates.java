package zone.moddev.mc.poweradvantage.filter;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SugarCaneBlock;

/** Modern item components, tags and recipes replacing PA2's item classes and ore dictionary. */
public final class FilterPredicates {
    /** Prevents construction of this predicate utility. */
    private FilterPredicates() {}

    /** Tests a stateless sorting category; inventory and overflow use block-local state.
     * @param kind configured filter kind
     * @param stack offered item
     * @param level world supplying current recipes
     * @return whether the stack matches this category
     */
    public static boolean matches(FilterSettings.Kind kind, ItemStack stack, Level level) {
        if (stack.isEmpty()) return false;
        return switch (kind) {
            case BLOCK -> stack.getItem() instanceof BlockItem;
            case FOOD -> stack.has(DataComponents.FOOD);
            case FUEL -> stack.getBurnTime(RecipeType.SMELTING) > 0;
            case ORE -> stack.getTags().anyMatch(tag -> {
                var id = tag.location();
                return id.getNamespace().equals("c") && java.util.stream.Stream.of("ores", "ingots", "dusts", "nuggets")
                    .anyMatch(group -> id.getPath().equals(group) || id.getPath().startsWith(group + "/"));
            });
            case PLANT -> stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.FLOWERS)
                || stack.getItem() instanceof BlockItem item && (item.getBlock() instanceof BonemealableBlock || item.getBlock() instanceof BushBlock
                    || item.getBlock() instanceof CactusBlock || item.getBlock() instanceof SugarCaneBlock);
            case SMELT -> level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level).isPresent();
            default -> false;
        };
    }
}
