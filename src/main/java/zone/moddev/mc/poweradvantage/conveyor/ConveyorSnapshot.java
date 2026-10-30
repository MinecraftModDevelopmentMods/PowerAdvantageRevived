package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import zone.moddev.mc.poweradvantage.init.ModContent;

/** Chunk-scoped, batched snapshots. Item counts are separate from the one-item rendering prototype. */
public record ConveyorSnapshot(ResourceLocation dimension,long chunk,long version,int part,int parts,List<View> items) implements CustomPacketPayload {
    public record View(UUID id,BlockPos pos,ItemStack stack,int count,Direction entry,int progress,int duration) {
        public View {stack=stack.copyWithCount(1);pos=pos.immutable();}
    }
    public static final Type<ConveyorSnapshot> TYPE=new Type<>(ModContent.id("conveyor_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConveyorSnapshot> CODEC=StreamCodec.of(ConveyorSnapshot::write,ConveyorSnapshot::read);
    @Override public Type<ConveyorSnapshot> type(){return TYPE;}
    private static void write(RegistryFriendlyByteBuf buffer,ConveyorSnapshot packet) {
        buffer.writeResourceLocation(packet.dimension);buffer.writeLong(packet.chunk);buffer.writeLong(packet.version);
        buffer.writeVarInt(packet.part);buffer.writeVarInt(packet.parts);buffer.writeVarInt(packet.items.size());
        for(var item:packet.items) {
            buffer.writeUUID(item.id);buffer.writeBlockPos(item.pos);ItemStack.STREAM_CODEC.encode(buffer,item.stack);
            buffer.writeVarInt(item.count);buffer.writeEnum(item.entry);buffer.writeVarInt(item.progress);buffer.writeVarInt(item.duration);
        }
    }
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
    private static final class ChunkPosKey {static long of(BlockPos pos){return net.minecraft.world.level.ChunkPos.asLong(pos);}}
}
