package zone.moddev.mc.poweradvantage.client;

import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.*;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.init.*;

@EventBusSubscriber(modid = PowerAdvantage.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(ModContent.MACHINE_MENU.get(), MachineScreen::new); }
    @SubscribeEvent public static void fluids(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            public ResourceLocation getStillTexture() { return ModContent.id("block/steam_still"); }
            public ResourceLocation getFlowingTexture() { return ModContent.id("block/steam_flow"); }
            public int getTintColor() { return 0x89cbdbfc; }
        }, Fluids.STEAM_TYPE.get());
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(Fluids.STEAM.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(Fluids.FLOWING_STEAM.get(), RenderType.translucent());
            for (String path : new String[]{"steel_frame", "fluid_storage_tank", "fluid_metal_tank"})
                ItemBlockRenderTypes.setRenderLayer(BuiltInRegistries.BLOCK.get(ModContent.id(path)), RenderType.cutout());
        });
    }
}
