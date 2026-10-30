package zone.moddev.mc.poweradvantage.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.blocks.ConveyorBlock;
import zone.moddev.mc.poweradvantage.conveyor.ConveyorSnapshot;
import zone.moddev.mc.poweradvantage.conveyor.ConveyorSnapshots;

/** Renders client snapshots directly; no server or client ItemEntities are allocated for belt cargo. */
@EventBusSubscriber(modid=PowerAdvantage.MODID,value=Dist.CLIENT)
public final class ConveyorClient {
    private record State(long version,long received,List<ConveyorSnapshot.View> items) {}
    private static final Map<Long,State> chunks=new HashMap<>();
    private static final ConveyorSnapshots snapshots=new ConveyorSnapshots();
    private static ClientLevel current;
    public static void accept(ConveyorSnapshot packet) {
        var level=Minecraft.getInstance().level;
        if(level!=current){clear();current=level;}
        if(level==null||!level.dimension().location().equals(packet.dimension()))return;
        var chunk=new ChunkPos(packet.chunk());if(!level.getChunkSource().hasChunk(chunk.x,chunk.z))return;
        snapshots.accept(packet).ifPresent(snapshot->chunks.put(snapshot.chunk(),new State(snapshot.version(),level.getGameTime(),snapshot.items())));
    }
    private static void clear(){chunks.clear();snapshots.clear();current=null;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if(event.getLevel()==current){long chunk=event.getChunk().getPos().toLong();chunks.remove(chunk);snapshots.forget(chunk);}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level!=current){clear();current=level;}
        if(level==null)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        int rendered=0;
        for(var chunk:chunks.entrySet()) {
            var chunkPos=new ChunkPos(chunk.getKey());if(!level.getChunkSource().hasChunk(chunkPos.x,chunkPos.z))continue;
            for(var item:chunk.getValue().items()) {
                BlockPos pos=item.pos();if(pos.distToCenterSqr(camera)>64*64)continue;
                if(!(level.getBlockState(pos).getBlock() instanceof ConveyorBlock belt))continue;
                double elapsed=Math.clamp(level.getGameTime()-chunk.getValue().received()+event.getPartialTick().getGameTimeDeltaPartialTick(false),0,5);
                double progress=Math.min(1,(item.progress()+elapsed)/item.duration());
                var point=belt.itemPosition(level.getBlockState(pos),item.entry(),progress).add(pos.getX(),pos.getY(),pos.getZ());
                pose.pushPose();pose.translate(point.x-camera.x,point.y-camera.y,point.z-camera.z);pose.scale(0.65f,0.65f,0.65f);
                mc.getItemRenderer().renderStatic(item.stack(),ItemDisplayContext.GROUND,LevelRenderer.getLightColor(level,pos.above()),OverlayTexture.NO_OVERLAY,
                    pose,buffers,level,item.id().hashCode());
                pose.popPose();
                if(++rendered>=2048)return;
            }
        }
    }
}
