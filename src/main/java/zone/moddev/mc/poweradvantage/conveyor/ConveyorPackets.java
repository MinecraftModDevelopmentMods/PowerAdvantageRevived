package zone.moddev.mc.poweradvantage.conveyor;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registration of the versioned, client-bound conveyor snapshot protocol.
 */
public final class ConveyorPackets {
    /**
     * Prevents instantiation of the static packet-registration holder.
     */
    private ConveyorPackets(){}
    /**
     * Registers the client-bound snapshot codec and enqueues handling on the client thread.
     *
     * @param event NeoForge registration or lifecycle event
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(ConveyorSnapshot.TYPE,ConveyorSnapshot.CODEC,(packet,context)->
            context.enqueueWork(()->zone.moddev.mc.poweradvantage.client.ConveyorClient.accept(packet)));
    }
}
