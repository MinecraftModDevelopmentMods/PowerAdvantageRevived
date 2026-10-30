package zone.moddev.mc.poweradvantage.conveyor;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ConveyorPackets {
    private ConveyorPackets(){}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(ConveyorSnapshot.TYPE,ConveyorSnapshot.CODEC,(packet,context)->
            context.enqueueWork(()->zone.moddev.mc.poweradvantage.client.ConveyorClient.accept(packet)));
    }
}
