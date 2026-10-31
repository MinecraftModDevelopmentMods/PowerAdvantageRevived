package zone.moddev.mc.poweradvantage.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.registries.*;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/**
 * Deferred registration of steam, crude oil and refined oil fluid types, source/flowing pairs, liquid blocks and
 * buckets. These fluids are registered directly with NeoForge rather than through material plans.
 */
public final class Fluids {
    /**
     * Deferred fluid-type register.
     */
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, PowerAdvantage.MODID);
    /**
     * Deferred source and flowing fluid register.
     */
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, PowerAdvantage.MODID);
    /**
     * Deferred liquid-block register.
     */
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, PowerAdvantage.MODID);
    /**
     * Deferred bucket register.
     */
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, PowerAdvantage.MODID);
    /**
     * Steam behavior/type holder; NeoForge supplies its default fluid_type translation key.
     */
    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = TYPES.register("steam", () -> new FluidType(
        FluidType.Properties.create().density(-1).viscosity(1).temperature(373).canSwim(false).canDrown(false)) {});
    /**
     * Crude oil fluid type and physical properties.
     */
    public static final DeferredHolder<FluidType, FluidType> CRUDE_OIL_TYPE = TYPES.register("crude_oil", () -> new FluidType(
        FluidType.Properties.create().density(900).viscosity(3000).temperature(290).canSwim(false).canDrown(false)) {});
    /**
     * Refined oil fluid type and physical properties.
     */
    public static final DeferredHolder<FluidType, FluidType> REFINED_OIL_TYPE = TYPES.register("refined_oil", () -> new FluidType(
        FluidType.Properties.create().density(850).viscosity(1800).temperature(290).canSwim(false).canDrown(false)) {});
    /**
     * Still/source steam fluid holder.
     */
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STEAM = FLUIDS.register("steam", () -> new BaseFlowingFluid.Source(properties()));
    /**
     * Flowing steam fluid holder.
     */
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_STEAM = FLUIDS.register("flowing_steam", () -> new BaseFlowingFluid.Flowing(properties()));
    /**
     * Still/source crude oil fluid holder.
     */
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CRUDE_OIL = FLUIDS.register("crude_oil",
        () -> new BaseFlowingFluid.Source(propertiesCrudeOil()));
    /**
     * Flowing crude oil fluid holder.
     */
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_CRUDE_OIL = FLUIDS.register("flowing_crude_oil",
        () -> new BaseFlowingFluid.Flowing(propertiesCrudeOil()));
    /**
     * Still/source refined oil fluid holder.
     */
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> REFINED_OIL = FLUIDS.register("refined_oil",
        () -> new BaseFlowingFluid.Source(propertiesRefinedOil()));
    /**
     * Flowing refined oil fluid holder.
     */
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_REFINED_OIL = FLUIDS.register("flowing_refined_oil",
        () -> new BaseFlowingFluid.Flowing(propertiesRefinedOil()));
    /**
     * Placeable steam liquid block holder.
     */
    public static final DeferredHolder<net.minecraft.world.level.block.Block, LiquidBlock> STEAM_BLOCK = BLOCKS.register("steam",
        () -> new LiquidBlock(STEAM.get(), BlockBehaviour.Properties.of().noCollission().strength(100).noLootTable().liquid().replaceable()));
    /**
     * Placeable crude oil liquid block holder.
     */
    public static final DeferredHolder<net.minecraft.world.level.block.Block, LiquidBlock> CRUDE_OIL_BLOCK = BLOCKS.register("crude_oil",
        () -> new zone.moddev.mc.poweradvantage.blocks.OilBlock(CRUDE_OIL.get(), BlockBehaviour.Properties.of().noCollission().strength(100).noLootTable().liquid().replaceable(), net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 2));
    /**
     * Placeable refined oil liquid block holder.
     */
    public static final DeferredHolder<net.minecraft.world.level.block.Block, LiquidBlock> REFINED_OIL_BLOCK = BLOCKS.register("refined_oil",
        () -> new zone.moddev.mc.poweradvantage.blocks.OilBlock(REFINED_OIL.get(), BlockBehaviour.Properties.of().noCollission().strength(100).noLootTable().liquid().replaceable(), net.minecraft.world.effect.MobEffects.CONFUSION, 0));
    /**
     * Steam bucket item holder.
     */
    public static final DeferredHolder<Item, BucketItem> STEAM_BUCKET = ITEMS.register("steam_bucket",
        () -> new BucketItem(STEAM.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    /**
     * Crude oil bucket item holder.
     */
    public static final DeferredHolder<Item, BucketItem> CRUDE_OIL_BUCKET = ITEMS.register("bucket_crude_oil",
        () -> new BucketItem(CRUDE_OIL.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    /**
     * Refined oil bucket item holder.
     */
    public static final DeferredHolder<Item, BucketItem> REFINED_OIL_BUCKET = ITEMS.register("bucket_refined_oil",
        () -> new BucketItem(REFINED_OIL.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    /**
     * Builds linked deferred suppliers for steam's type, flow, liquid block and bucket.
     *
     * @return steam flow properties
     */
    private static BaseFlowingFluid.Properties properties() {
        return properties(STEAM_TYPE, STEAM, FLOWING_STEAM, STEAM_BLOCK, STEAM_BUCKET);
    }
    /**
     * Resolves the deferred crude oil registration suppliers after their declarations are initialized.
     *
     * @return linked crude oil flow properties
     */
    private static BaseFlowingFluid.Properties propertiesCrudeOil() {
        return properties(CRUDE_OIL_TYPE, CRUDE_OIL, FLOWING_CRUDE_OIL, CRUDE_OIL_BLOCK, CRUDE_OIL_BUCKET);
    }
    /**
     * Resolves the deferred refined oil registration suppliers after their declarations are initialized.
     *
     * @return linked refined oil flow properties
     */
    private static BaseFlowingFluid.Properties propertiesRefinedOil() {
        return properties(REFINED_OIL_TYPE, REFINED_OIL, FLOWING_REFINED_OIL, REFINED_OIL_BLOCK, REFINED_OIL_BUCKET);
    }
    /**
     * Builds linked deferred suppliers for a fluid type, source/flowing pair, liquid block and bucket.
     *
     * @param type fluid type supplier
     * @param source source fluid supplier
     * @param flowing flowing fluid supplier
     * @param block liquid block supplier
     * @param bucket bucket item supplier
     * @return linked flowing-fluid properties
     */
    private static BaseFlowingFluid.Properties properties(DeferredHolder<FluidType, FluidType> type,
            DeferredHolder<Fluid, ? extends Fluid> source, DeferredHolder<Fluid, ? extends Fluid> flowing,
            DeferredHolder<net.minecraft.world.level.block.Block, ? extends LiquidBlock> block,
            DeferredHolder<Item, ? extends Item> bucket) {
        return new BaseFlowingFluid.Properties(type, source, flowing).block(block).bucket(bucket).tickRate(5).slopeFindDistance(4);
    }
    /**
     * Attaches all fluid deferred registers to the owning mod bus.
     *
     * @param bus owning mod event bus
     */
    public static void register(IEventBus bus) { TYPES.register(bus); FLUIDS.register(bus); BLOCKS.register(bus); ITEMS.register(bus); }
    /**
     * Prevents instantiation of the static steam-registration holder.
     */
    private Fluids() {}
}
