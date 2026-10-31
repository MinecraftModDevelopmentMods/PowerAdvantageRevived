package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.poweradvantage.init.ModContent;

/**
 * One client-bound fragment of a chunk snapshot. Rendering prototypes carry one item; the separate count preserves
 * authoritative quantities without relying on stack-codec count limits.
 *
 * @param dimension Owning dimension registry ID
 * @param chunk Packed owning chunk coordinate
 * @param version Server generation shared by all fragments of this snapshot
 * @param part Zero-based index of this fragment
 * @param parts Total fragment count for this snapshot
 * @param items Rendering records included in this fragment
 */
public record ConveyorSnapshot(ResourceLocation dimension,long chunk,long version,int part,int parts,List<View> items) implements CustomPacketPayload {
    /**
     * Rendering description of one carried record within a chunk snapshot.
     *
     * @param id Stable cargo UUID used for rendering identity
     * @param pos Immutable owning block position
     * @param stack Copied one-item rendering prototype including components
     * @param count Positive authoritative item quantity
     * @param entry Incoming face of this segment
     * @param progress Processed movement ticks in this segment
     * @param duration Positive travel duration in processed ticks
     */
    public record View(UUID id,BlockPos pos,ItemStack stack,int count,Direction entry,int progress,int duration) {
        /**
         * Copies a one-item rendering prototype and immutable position while retaining quantity and motion fields.
         *
         * @param id Stable cargo UUID used for rendering identity
         * @param pos Immutable owning block position
         * @param stack Copied one-item rendering prototype including components
         * @param count Positive authoritative item quantity
         * @param entry Incoming face of this segment
         * @param progress Processed movement ticks in this segment
         * @param duration Positive travel duration in processed ticks
         */
        public View {stack=stack.copyWithCount(1);pos=pos.immutable();}
    }
    /**
     * Registered client-bound conveyor payload identifier.
     *
     */
    public static final Type<ConveyorSnapshot> TYPE=new Type<>(ModContent.id("conveyor_snapshot"));
    /**
     * Registry-aware codec for this definition or snapshot payload.
     *
     */
    public static final StreamCodec<RegistryFriendlyByteBuf,ConveyorSnapshot> CODEC=StreamCodec.of(ConveyorSnapshot::write,ConveyorSnapshot::read);
    /**
     * Identifies the client-bound conveyor snapshot payload.
     *
     * @return registered payload type
     */
    @Override public Type<ConveyorSnapshot> type(){return TYPE;}
    /**
     * Encodes one chunk fragment with registry-aware item prototypes and separate counts.
     *
     * @param buffer registry-aware packet buffer
     * @param packet chunk snapshot fragment
     */
    private static void write(RegistryFriendlyByteBuf buffer,ConveyorSnapshot packet) {
        buffer.writeResourceLocation(packet.dimension);buffer.writeLong(packet.chunk);buffer.writeLong(packet.version);
        buffer.writeVarInt(packet.part);buffer.writeVarInt(packet.parts);buffer.writeVarInt(packet.items.size());
        for(var item:packet.items) {
            buffer.writeUUID(item.id);buffer.writeBlockPos(item.pos);ItemStack.STREAM_CODEC.encode(buffer,item.stack);
            buffer.writeVarInt(item.count);buffer.writeEnum(item.entry);buffer.writeVarInt(item.progress);buffer.writeVarInt(item.duration);
        }
    }
    /**
     * Decodes and validates fragment bounds, item quantities and chunk ownership.
     *
     * @param buffer registry-aware packet buffer
     * @return validated snapshot fragment
     * @throws IllegalArgumentException if fragment bounds, quantities, progress or chunk ownership are invalid
     */
    private static ConveyorSnapshot read(RegistryFriendlyByteBuf buffer) {
        var dimension=buffer.readResourceLocation();long chunk=buffer.readLong(),version=buffer.readLong();
        int part=buffer.readVarInt(),parts=buffer.readVarInt(),count=buffer.readVarInt();
        if(parts<=0||parts>65536||part<0||part>=parts||count<0||count>128)throw new IllegalArgumentException("Invalid conveyor snapshot bounds");
        var items=new ArrayList<View>();
        for(int i=0;i<count;i++) {
            var id=buffer.readUUID();var pos=buffer.readBlockPos();var stack=ItemStack.STREAM_CODEC.decode(buffer);
            int amount=buffer.readVarInt();var entry=buffer.readEnum(Direction.class);int progress=buffer.readVarInt(),duration=buffer.readVarInt();
            if(amount<=0||progress<0||duration<=0||ChunkPosKey.of(pos)!=chunk)throw new IllegalArgumentException("Invalid conveyor item snapshot");
            items.add(new View(id,pos,stack,amount,entry,progress,duration));
        }
        return new ConveyorSnapshot(dimension,chunk,version,part,parts,List.copyOf(items));
    }
    /**
     * Internal conversion of item positions to packed chunk coordinates for packet validation.
     */
    private static final class ChunkPosKey {
        /**
         * Prevents instantiation of the internal packed-coordinate helper.
         */
        private ChunkPosKey() {}

    /**
     * Converts a physical position to its packed chunk key.
     *
     * @param pos block position being queried or changed
     * @return packed chunk coordinate
     */
    static long of(BlockPos pos){return net.minecraft.world.level.ChunkPos.asLong(pos);}}
}
