package zone.moddev.mc.poweradvantage.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.conveyor.ConveyorNetworks;

/**
 * Server game-bus lifecycle forwarding for fluid and conveyor managers. Chunk events enqueue inspection rather than
 * reading partially promoted chunks.
 */
@EventBusSubscriber(modid=PowerAdvantage.MODID)
public final class NetworkEvents {
    /**
     * Creates the stateless server lifecycle subscriber.
     */
    public NetworkEvents() {}

    /**
     * Advances fluid and conveyor work after the server level tick.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if(event.getLevel() instanceof ServerLevel level){FluidNetworks.get(level).tick();ConveyorNetworks.get(level).tick();}
    }
    /**
     * Queues the loaded chunk for both transport managers without inspecting it in the event callback.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if(event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk){FluidNetworks.get(level).chunkLoaded(chunk);ConveyorNetworks.get(level).chunkLoaded(chunk);}
    }
    /**
     * Queues chunk withdrawal or releases dimension managers, according to the event type.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level){FluidNetworks.get(level).chunkUnloaded(event.getChunk().getPos());ConveyorNetworks.get(level).chunkUnloaded(event.getChunk().getPos());}
    }
    /**
     * Queues chunk withdrawal or releases dimension managers, according to the event type.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level){FluidNetworks.unload(level);ConveyorNetworks.unload(level);}
    }
    /**
     * Schedules conveyor state only after the native chunk has been sent to the player.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SubscribeEvent public static void watch(ChunkWatchEvent.Sent event){ConveyorNetworks.get(event.getLevel()).watch(event.getPlayer(),event.getPos());}
    /**
     * Withdraws the player's conveyor chunk subscription.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SubscribeEvent public static void unwatch(ChunkWatchEvent.UnWatch event){ConveyorNetworks.get(event.getLevel()).unwatch(event.getPlayer(),event.getPos());}

}
