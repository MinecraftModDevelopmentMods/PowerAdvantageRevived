package zone.moddev.mc.poweradvantage.client;

import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.*;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.init.*;

/**
 * Client-only mod-bus registrations for fluid appearance and block render layers.
 */
@EventBusSubscriber(modid = PowerAdvantage.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    /**
     * Creates the stateless client registration subscriber.
     */
    public ClientEvents() {}

    /**
     * Registers steam and oil textures and tints with NeoForge client fluid extensions.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SubscribeEvent public static void fluids(RegisterClientExtensionsEvent event) {
        event.registerFluidType(fluidTextures("steam", 0x89cbdbfc), Fluids.STEAM_TYPE.get());
        event.registerFluidType(fluidTextures("crude_oil", 0xffffffff), Fluids.CRUDE_OIL_TYPE.get());
        event.registerFluidType(fluidTextures("refined_oil", 0xffffffff), Fluids.REFINED_OIL_TYPE.get());
    }
    /**
     * Creates client texture and tint extensions for a still/flowing fluid texture pair.
     *
     * @param name fluid texture path prefix
     * @param tint fluid ARGB tint
     * @return client rendering extensions for the fluid type
     */
    private static IClientFluidTypeExtensions fluidTextures(String name, int tint) {
        return new IClientFluidTypeExtensions() {
            /**
             * Supplies the fluid's still texture.
             *
             * @return still texture identifier
             */
            public ResourceLocation getStillTexture() { return ModContent.id("block/" + name + "_still"); }
            /**
             * Supplies the fluid's flowing texture.
             *
             * @return flowing texture identifier
             */
            public ResourceLocation getFlowingTexture() { return ModContent.id("block/" + name + "_flow"); }
            /**
             * Supplies the fluid's ARGB tint.
             *
             * @return packed ARGB tint
             */
            public int getTintColor() { return tint; }
        };
    }
    /**
     * Schedules client fluid and cutout block render-layer setup.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(Fluids.STEAM.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Fluids.FLOWING_STEAM.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Fluids.CRUDE_OIL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Fluids.FLOWING_CRUDE_OIL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Fluids.REFINED_OIL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Fluids.FLOWING_REFINED_OIL.get(), RenderType.translucent());
            for (String path : new String[]{"converter_rf_steam", "converter_rf_electricity", "steel_frame", "fluid_storage_tank", "fluid_metal_tank",
                    "item_filter_block", "item_filter_food", "item_filter_fuel", "item_filter_inventory",
                    "item_filter_ore", "item_filter_overflow", "item_filter_plant", "item_filter_smelt"})
                ItemBlockRenderTypes.setRenderLayer(BuiltInRegistries.BLOCK.get(ModContent.id(path)), RenderType.cutout());
        });
    }
}
