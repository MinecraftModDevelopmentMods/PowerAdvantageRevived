package zone.moddev.mc.poweradvantage.machine;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import zone.moddev.mc.lib.api.automation.AddonCapabilities;
import zone.moddev.mc.lib.api.automation.AddonSpec;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.init.ModItems;

/**
 * Fluid pump addons for MMDLib machines. Installed in a fluid group's addon slots (the side configuration drawer),
 * an intake pump pulls fluid into the group from neighbouring blocks and an output pump pushes it out, through the
 * faces that group is enabled on. They work in any MMDLib machine, including other mods'.
 *
 * <p>They move {@value #BATCH} mB every {@value #INTERVAL} ticks and need no power, matching the neighbour transfer
 * that 3.1's Distillery did on its own.
 */
public final class FluidPumps {
    /**
     * Millibuckets moved per transfer.
     */
    public static final int BATCH = 1000;
    /**
     * Ticks between transfers.
     */
    public static final int INTERVAL = 10;
    /**
     * Pulls fluid from neighbours into the group.
     */
    public static final AddonSpec INTAKE = new AddonSpec(ModContent.id("fluid_intake_pump"), ResourceType.FLUIDS,
        IoMode.INPUT, BATCH, INTERVAL, 0, false);
    /**
     * Pushes fluid from the group into neighbours.
     */
    public static final AddonSpec OUTPUT = new AddonSpec(ModContent.id("fluid_output_pump"), ResourceType.FLUIDS,
        IoMode.OUTPUT, BATCH, INTERVAL, 0, false);

    /**
     * Holds constants and registration only; not instantiable.
     */
    private FluidPumps() {}

    /**
     * Registers both pump items as MMDLib inventory addons.
     *
     * @param event NeoForge capability registration event
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        register(event, ModItems.FLUID_INTAKE_PUMP.get(), INTAKE);
        register(event, ModItems.FLUID_OUTPUT_PUMP.get(), OUTPUT);
    }

    /**
     * Makes one item an MMDLib inventory addon with a fixed specification.
     *
     * @param event NeoForge capability registration event
     * @param item pump item
     * @param spec what the pump moves, which way, how much and how often
     */
    private static void register(RegisterCapabilitiesEvent event, Item item, AddonSpec spec) {
        event.registerItem(AddonCapabilities.ADDON, (stack, context) -> addon -> spec, item);
    }
}
