package zone.moddev.mc.poweradvantage.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.items.RotationTool;

/** Standalone crafting ingredients and tools retained from Power Advantage 2. */
public final class ModItems {
    /** Owning mod's item register. */
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, PowerAdvantage.MODID);
    /** Plant-derived plastic ingredient. */
    public static final DeferredHolder<Item, Item> STARCH = ITEMS.register("starch", () -> new Item(new Item.Properties()));
    /** Smelted plastic crafting ingredient. */
    public static final DeferredHolder<Item, Item> BIOPLASTIC_INGOT = ITEMS.register("bioplastic_ingot", () -> new Item(new Item.Properties()));
    /** Gear used in conveyors and tools. */
    public static final DeferredHolder<Item, Item> SPROCKET = ITEMS.register("sprocket", () -> new Item(new Item.Properties()));
    /** Reusable block rotation tool. */
    public static final DeferredHolder<Item, RotationTool> ROTATOR_TOOL = ITEMS.register("rotator_tool", RotationTool::new);
    /** Prevents construction of this registration holder. */
    private ModItems() {}
    /** Attaches registration to the owning mod bus.
     * @param bus mod event bus
     */
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
