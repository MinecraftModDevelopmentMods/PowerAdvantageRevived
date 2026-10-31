package zone.moddev.mc.poweradvantage.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.api.distillation.DistillingRecipe;

/**
 * Recipe type and serializer registrations.
 */
public final class Recipes {
    /**
     * Recipe type register.
     */
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, PowerAdvantage.MODID);
    /**
     * Recipe serializer register.
     */
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, PowerAdvantage.MODID);
    /**
     * The {@code poweradvantage:distilling} recipe type.
     */
    public static final DeferredHolder<RecipeType<?>, RecipeType<DistillingRecipe>> DISTILLING_TYPE = TYPES.register("distilling",
        () -> RecipeType.simple(ModContent.id("distilling")));
    /**
     * The {@code poweradvantage:distilling} recipe serializer.
     */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<DistillingRecipe>> DISTILLING_SERIALIZER =
        SERIALIZERS.register("distilling", () -> DistillingRecipe.SERIALIZER);

    /**
     * Utility class; not instantiable.
     */
    private Recipes() {}

    /**
     * Registers both deferred registers on the mod bus.
     *
     * @param bus mod event bus
     */
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }
}
