package zone.moddev.mc.poweradvantage.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

@EventBusSubscriber(modid=PowerAdvantage.MODID)
public final class NetworkEvents {
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if(event.getLevel() instanceof ServerLevel level)FluidNetworks.get(level).tick();
    }
    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)FluidNetworks.get(level).chunkLoaded(chunk);
    }
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)FluidNetworks.get(level).chunkUnloaded(event.getChunk().getPos());
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)FluidNetworks.unload(level);
    }
}
