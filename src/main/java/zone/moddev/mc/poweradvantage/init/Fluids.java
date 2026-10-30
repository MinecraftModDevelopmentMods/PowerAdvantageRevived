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

public final class Fluids {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, PowerAdvantage.MODID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, PowerAdvantage.MODID);
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, PowerAdvantage.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, PowerAdvantage.MODID);
    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = TYPES.register("steam", () -> new FluidType(
        FluidType.Properties.create().density(-1).viscosity(1).temperature(373).canSwim(false).canDrown(false)) {});
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STEAM = FLUIDS.register("steam", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_STEAM = FLUIDS.register("flowing_steam", () -> new BaseFlowingFluid.Flowing(properties()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, LiquidBlock> STEAM_BLOCK = BLOCKS.register("steam",
        () -> new LiquidBlock(STEAM.get(), BlockBehaviour.Properties.of().noCollission().strength(100).noLootTable().liquid().replaceable()));
    public static final DeferredHolder<Item, BucketItem> STEAM_BUCKET = ITEMS.register("steam_bucket",
        () -> new BucketItem(STEAM.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(STEAM_TYPE, STEAM, FLOWING_STEAM).block(STEAM_BLOCK).bucket(STEAM_BUCKET).tickRate(5).slopeFindDistance(4);
    }
    public static void register(IEventBus bus) { TYPES.register(bus); FLUIDS.register(bus); BLOCKS.register(bus); ITEMS.register(bus); }
    private Fluids() {}
}
