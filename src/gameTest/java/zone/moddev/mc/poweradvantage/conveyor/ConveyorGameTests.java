package zone.moddev.mc.poweradvantage.conveyor;

import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.RegisterEvent;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.blocks.ConveyorBlock;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.network.transport.TransportNode;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid=PowerAdvantage.MODID)
public final class ConveyorGameTests {
    private static final Map<BlockPos,net.neoforged.neoforge.items.ItemStackHandler> callbacks=new HashMap<>();
    @SubscribeEvent public static void blocks(RegisterEvent event) {
        event.register(Registries.BLOCK,helper->{
            helper.register(ModContent.id("test_fast_conveyor"),new ConveyorBlock(Block.Properties.of(),1,2));
            helper.register(ModContent.id("test_slow_conveyor"),new ConveyorBlock(Block.Properties.of(),4,4));
        });
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK,(level,pos,state,be,side)->callbacks.computeIfAbsent(pos.immutable(),key->new net.neoforged.neoforge.items.ItemStackHandler(1) {
            @Override public int getSlotLimit(int slot){return 1;}
            @Override protected void onContentsChanged(int slot){level.setBlockAndUpdate(pos.west(),Blocks.AIR.defaultBlockState());}
        }),Blocks.LAPIS_BLOCK);
        ConveyorBlock.registerCapabilities(event,(ConveyorBlock)BuiltInRegistries.BLOCK.get(ModContent.id("test_fast_conveyor")),
            (ConveyorBlock)BuiltInRegistries.BLOCK.get(ModContent.id("test_slow_conveyor")));
    }
    private static BlockPos p(int x){return new BlockPos(x,3,5);}
    private static void belt(GameTestHelper h,int x,String name) {
        h.setBlock(p(x),BuiltInRegistries.BLOCK.get(ModContent.id(name)).defaultBlockState().setValue(ConveyorBlock.FACING,Direction.EAST));
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(p(x)))==null,"Conveyor must not have a block entity");
    }
    private static IItemHandler barrel(GameTestHelper h,int x) {
        h.setBlock(p(x),Blocks.BARREL);
        return h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(p(x)),Direction.UP);
    }
    private static TransportNode node(GameTestHelper h,int x){return new TransportNode(h.absolutePos(p(x)));}
    private static int count(IItemHandler handler,net.minecraft.world.item.Item item) {
        int count=0;for(int i=0;i<handler.getSlots();i++)if(handler.getStackInSlot(i).is(item))count+=handler.getStackInSlot(i).getCount();return count;
    }
    private static int carried(GameTestHelper h,int from,int to) {
        var manager=ConveyorNetworks.get(h.getLevel());int total=0;
        for(int x=from;x<=to;x++){var item=manager.store.at(node(h,x));if(item!=null)total+=item.stack.getCount();}return total;
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void automaticExtractionTravelAndDelivery(GameTestHelper h) {
        var source=barrel(h,1);var target=barrel(h,7);source.insertItem(0,new ItemStack(Items.IRON_INGOT,8),false);
        for(int x=2;x<7;x++)belt(h,x,"item_conveyor");
        h.runAfterDelay(15,()->{
            h.assertTrue(count(source,Items.IRON_INGOT)==0&&carried(h,2,6)==8&&count(target,Items.IRON_INGOT)==0,"Extracted items must physically exist on belts before delivery");
        });
        h.runAfterDelay(100,()->{
            h.assertTrue(count(target,Items.IRON_INGOT)==8&&carried(h,2,6)==0,"Items must reach the destination exactly once");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void configurableRatesSimulationAndOneStepPerTick(GameTestHelper h) {
        belt(h,3,"test_fast_conveyor");belt(h,4,"test_fast_conveyor");belt(h,5,"test_fast_conveyor");
        var manager=ConveyorNetworks.get(h.getLevel());
        h.runAfterDelay(10,()->{
            var capability=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,node(h,3).position(),Direction.WEST);
            h.assertTrue(capability.insertItem(0,new ItemStack(Items.GOLD_INGOT,8),true).getCount()==6,"Configured intake simulation limit");
            h.assertTrue(carried(h,3,5)==0,"Simulation must not create ownership");
            h.assertTrue(capability.insertItem(0,new ItemStack(Items.GOLD_INGOT,8),false).getCount()==6,"Configured actual intake limit");
            h.assertTrue(capability.insertItem(0,new ItemStack(Items.GOLD_INGOT),false).getCount()==1,"Repeated insertion shares per-tick allowance");
            manager.tick();manager.tick();
            h.assertTrue(manager.store.at(node(h,5))==null,"Repeated manager calls cannot move an item several segments in one tick");
        });
        h.runAfterDelay(25,()->{
            h.assertTrue(manager.store.at(node(h,5))!=null&&carried(h,3,5)==2,"Open-ended belt must retain physical cargo at its end");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void slowerBeltsKeepPhysicalIntermediatePositions(GameTestHelper h) {
        for(int x=3;x<=5;x++)belt(h,x,"test_slow_conveyor");var manager=ConveyorNetworks.get(h.getLevel());
        h.runAfterDelay(10,()->manager.offer(node(h,3),Direction.WEST,new ItemStack(Items.COPPER_INGOT,4),false));
        h.runAfterDelay(11,()->h.assertTrue(manager.store.at(node(h,3))!=null,"Slow belt must retain its item during segment travel"));
        h.runAfterDelay(15,()->h.assertTrue(manager.store.at(node(h,5))==null,"Travel cannot skip the intermediate segment"));
        h.runAfterDelay(35,()->{h.assertTrue(manager.store.at(node(h,5))!=null&&carried(h,3,5)==4,"Slow item eventually reaches the final belt");h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void fullDestinationBackpressuresAndResumes(GameTestHelper h) {
        var source=barrel(h,1);var target=barrel(h,4);source.insertItem(0,new ItemStack(Items.IRON_INGOT,8),false);
        for(int slot=0;slot<target.getSlots();slot++)target.insertItem(slot,new ItemStack(Items.STONE,64),false);
        belt(h,2,"test_fast_conveyor");belt(h,3,"test_fast_conveyor");
        h.runAfterDelay(30,()->{
            h.assertTrue(count(source,Items.IRON_INGOT)+carried(h,2,3)==8,"Blocked conveyor must conserve extracted and unextracted items");
            h.assertTrue(count(source,Items.IRON_INGOT)>0&&carried(h,2,3)>0,"Full destination must produce backpressure");
            target.extractItem(0,64,false);
        });
        h.runAfterDelay(70,()->{
            h.assertTrue(count(source,Items.IRON_INGOT)==0&&carried(h,2,3)==0&&count(target,Items.IRON_INGOT)==8,"Opening destination capacity resumes transport");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void droppedItemCaptureAndPickupAreSingleOwnership(GameTestHelper h) {
        belt(h,4,"item_conveyor");var manager=ConveyorNetworks.get(h.getLevel());var pos=node(h,4).position();
        var entity=new ItemEntity(h.getLevel(),pos.getX()+0.5,pos.getY()+1.1,pos.getZ()+0.5,new ItemStack(Items.PAPER,3));
        entity.setDeltaMovement(0,0,0);h.getLevel().addFreshEntity(entity);
        h.runAfterDelay(25,()->{
            h.assertTrue(!entity.isAlive()&&carried(h,4,4)==3,"Captured dropped item must cease to be an entity");
            var first=h.makeMockPlayer(GameType.SURVIVAL);var second=h.makeMockPlayer(GameType.SURVIVAL);
            first.setPos(pos.getX()+0.5,pos.getY()+1,pos.getZ()+0.5);second.setPos(first.position());
            h.assertTrue(manager.pickup(pos,first)==3&&manager.pickup(pos,second)==0,"Two players must not collect the same record");
            h.assertTrue(first.getInventory().countItem(Items.PAPER)==3&&carried(h,4,4)==0,"Pickup transfers ownership into inventory");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void breakingBeltDropsCargoExactlyOnce(GameTestHelper h) {
        belt(h,4,"item_conveyor");h.setBlock(p(4).below(),Blocks.STONE);var manager=ConveyorNetworks.get(h.getLevel());
        h.runAfterDelay(10,()->manager.offer(node(h,4),Direction.UP,new ItemStack(Items.DIAMOND,5),false));
        h.runAfterDelay(15,()->h.setBlock(p(4),Blocks.AIR));
        h.runAfterDelay(25,()->{
            int dropped=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(node(h,4).position()).inflate(3)).stream()
                .filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(dropped==5&&carried(h,4,4)==0,"Breaking a belt must release exactly its owned cargo");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void savedOwnershipAndSnapshotPreserveComponents(GameTestHelper h) {
        belt(h,4,"item_conveyor");var manager=ConveyorNetworks.get(h.getLevel());var stack=new ItemStack(Items.IRON_INGOT,3);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Conveyor ownership test"));
        h.runAfterDelay(10,()->manager.offer(node(h,4),Direction.WEST,stack,false));
        h.runAfterDelay(25,()->{
            var original=manager.store.at(node(h,4));
            var restored=ConveyorStore.load(manager.store.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            var copy=restored.records.get(original.id);
            h.assertTrue(copy!=null&&copy.node.equals(original.node)&&copy.progress==original.progress&&copy.stack.getCount()==3
                &&ItemStack.isSameItemSameComponents(copy.stack,original.stack),"NBT round trip must preserve record identity, progress and components");
            var packet=new ConveyorSnapshot(h.getLevel().dimension().location(),net.minecraft.world.level.ChunkPos.asLong(original.node.position()),17,0,1,
                List.of(new ConveyorSnapshot.View(original.id,original.node.position(),original.stack,3,original.entry,original.progress,8)));
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                ConveyorSnapshot.CODEC.encode(buffer,packet);var decoded=ConveyorSnapshot.CODEC.decode(buffer);var item=decoded.items().getFirst();
                h.assertTrue(decoded.version()==17&&item.id().equals(original.id)&&item.count()==3
                    &&ItemStack.isSameItemSameComponents(item.stack(),original.stack),"Snapshot codec must preserve visible item identity/components");
            } finally {buffer.release();}
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=170,batch="conveyor_chunk_boundary")
    public static void unloadedBoundarySuspendsCargoUntilReload(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(p(5)).offset(4096,0,4096);int boundary=(origin.getX()>>4)*16+16;
        var first=new BlockPos(boundary-1,origin.getY(),origin.getZ());var second=first.east();var target=second.east();
        var firstChunk=new net.minecraft.world.level.ChunkPos(first);var secondChunk=new net.minecraft.world.level.ChunkPos(second);
        level.setChunkForced(firstChunk.x,firstChunk.z,true);level.setChunkForced(secondChunk.x,secondChunk.z,true);
        var block=BuiltInRegistries.BLOCK.get(ModContent.id("test_fast_conveyor"));
        level.setBlockAndUpdate(first,block.defaultBlockState().setValue(ConveyorBlock.FACING,Direction.EAST));
        level.setBlockAndUpdate(second,block.defaultBlockState().setValue(ConveyorBlock.FACING,Direction.EAST));level.setBlockAndUpdate(target,Blocks.BARREL.defaultBlockState());
        var manager=ConveyorNetworks.get(level);var node=new TransportNode(first);var chunk=new net.minecraft.world.level.ChunkPos(second);
        h.runAfterDelay(20,()->{
            h.assertTrue(manager.offer(node,Direction.WEST,new ItemStack(Items.EMERALD,2),false).isEmpty(),"Detached test chunks must be loaded before injecting cargo");
            manager.chunkUnloaded(chunk);
        });
        h.runAfterDelay(40,()->{
            h.assertTrue(manager.store.at(node)!=null&&manager.store.at(node).stack.getCount()==2,"Unloaded boundary must not consume or move cargo; first loaded="+level.isLoaded(first)+", records="+manager.items());
            manager.chunkLoaded(level.getChunkAt(second));
        });
        h.runAfterDelay(70,()->{
            var sink=level.getCapability(Capabilities.ItemHandler.BLOCK,target,Direction.WEST);
            h.assertTrue(count(sink,Items.EMERALD)==2&&manager.store.at(node)==null,"Cargo resumes exactly once after chunk-event reload");
            h.assertTrue(manager.offer(node,Direction.WEST,new ItemStack(Items.EMERALD,2),false).isEmpty(),"Inject second record before suspending its owner chunk");
            manager.chunkUnloaded(firstChunk);
        });
        h.runAfterDelay(95,()->{
            h.assertTrue(manager.store.at(node)!=null&&manager.store.at(node).progress==0,"An unloaded owner must retain and suspend its record");
            manager.chunkLoaded(level.getChunkAt(first));
        });
        h.runAfterDelay(125,()->{
            var sink=level.getCapability(Capabilities.ItemHandler.BLOCK,target,Direction.WEST);
            h.assertTrue(count(sink,Items.EMERALD)==4&&manager.store.at(node)==null,"Reloading the owner chunk must reactivate its saved record exactly once");
            level.setChunkForced(firstChunk.x,firstChunk.z,false);level.setChunkForced(secondChunk.x,secondChunk.z,false);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void mergingBeltsShareTheirDownstreamSegment(GameTestHelper h) {
        var sourceA=barrel(h,2);sourceA.insertItem(0,new ItemStack(Items.IRON_INGOT,32),false);
        var target=barrel(h,5);belt(h,3,"test_fast_conveyor");belt(h,4,"test_fast_conveyor");
        var northSource=new BlockPos(4,3,3);h.setBlock(northSource,Blocks.BARREL);
        var sourceB=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(northSource),Direction.SOUTH);
        sourceB.insertItem(0,new ItemStack(Items.GOLD_INGOT,32),false);
        h.setBlock(new BlockPos(4,3,4),BuiltInRegistries.BLOCK.get(ModContent.id("test_fast_conveyor")).defaultBlockState().setValue(ConveyorBlock.FACING,Direction.SOUTH));
        h.runAfterDelay(45,()->{
            int iron=count(target,Items.IRON_INGOT),gold=count(target,Items.GOLD_INGOT);
            h.assertTrue(iron>0&&gold>0&&Math.abs(iron-gold)<=4,"Merging inputs must both receive service: "+iron+", "+gold);
            h.succeed();
        });
    }
    @GameTest(template="empty")
    public static void sideFedBeltRendersAsCorner(GameTestHelper h) {
        belt(h,4,"item_conveyor");
        h.setBlock(new BlockPos(4,3,4),BuiltInRegistries.BLOCK.get(ModContent.id("item_conveyor")).defaultBlockState().setValue(ConveyorBlock.FACING,Direction.SOUTH));
        h.assertBlockProperty(p(4),ConveyorBlock.SHAPE,ConveyorBlock.Shape.LEFT);
        belt(h,3,"item_conveyor");
        h.assertBlockProperty(p(4),ConveyorBlock.SHAPE,ConveyorBlock.Shape.STRAIGHT);
        h.setBlock(p(3),Blocks.BARREL);
        h.assertBlockProperty(p(4),ConveyorBlock.SHAPE,ConveyorBlock.Shape.STRAIGHT);
        h.setBlock(p(3),Blocks.AIR);
        h.assertBlockProperty(p(4),ConveyorBlock.SHAPE,ConveyorBlock.Shape.LEFT);
        h.setBlock(new BlockPos(4,3,6),BuiltInRegistries.BLOCK.get(ModContent.id("item_conveyor")).defaultBlockState().setValue(ConveyorBlock.FACING,Direction.NORTH));
        h.assertBlockProperty(p(4),ConveyorBlock.SHAPE,ConveyorBlock.Shape.STRAIGHT);
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void partialDeliveryKeepsRemainderOnBelt(GameTestHelper h) {
        belt(h,4,"item_conveyor");var target=barrel(h,5);
        target.insertItem(0,new ItemStack(Items.IRON_INGOT,63),false);
        for(int i=1;i<target.getSlots();i++)target.insertItem(i,new ItemStack(Items.STONE,64),false);
        var manager=ConveyorNetworks.get(h.getLevel());
        h.runAfterDelay(10,()->manager.offer(node(h,4),Direction.WEST,new ItemStack(Items.IRON_INGOT,4),false));
        h.runAfterDelay(30,()->{
            h.assertTrue(count(target,Items.IRON_INGOT)==64&&carried(h,4,4)==3,"Partial insertion must leave the exact remainder in transit");
            target.extractItem(0,1,false);
        });
        h.runAfterDelay(45,()->{
            h.assertTrue(count(target,Items.IRON_INGOT)==64&&carried(h,4,4)==2,"A second partial insertion must not replay the original stack");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void removalDuringInsertionDoesNotDuplicateCargo(GameTestHelper h) {
        belt(h,4,"item_conveyor");h.setBlock(p(4).below(),Blocks.STONE);h.setBlock(p(5),Blocks.LAPIS_BLOCK);
        var manager=ConveyorNetworks.get(h.getLevel());
        h.runAfterDelay(10,()->manager.offer(node(h,4),Direction.WEST,new ItemStack(Items.DIAMOND,4),false));
        h.runAfterDelay(35,()->{
            var receiver=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,h.absolutePos(p(5)),Direction.WEST);
            int dropped=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(node(h,4).position()).inflate(3)).stream()
                .filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(count(receiver,Items.DIAMOND)==1&&dropped==3&&carried(h,4,4)==0,"A receiver removing its belt during insertion must not duplicate the accepted item");h.succeed();
        });
    }
    @GameTest(template="empty")
    public static void snapshotAssemblyRejectsStaleAndIncompleteBatches(GameTestHelper h) {
        var pos=h.absolutePos(p(4));long chunk=net.minecraft.world.level.ChunkPos.asLong(pos);var dimension=h.getLevel().dimension().location();
        var first=new ConveyorSnapshot.View(UUID.randomUUID(),pos,new ItemStack(Items.IRON_INGOT),2,Direction.WEST,1,8);
        var second=new ConveyorSnapshot.View(UUID.randomUUID(),pos.east(),new ItemStack(Items.GOLD_INGOT),3,Direction.WEST,2,8);
        var assembly=new ConveyorSnapshots();
        h.assertTrue(assembly.accept(new ConveyorSnapshot(dimension,chunk,42,1,2,List.of(second))).isEmpty(),"Incomplete snapshot must not become visible");
        var complete=assembly.accept(new ConveyorSnapshot(dimension,chunk,42,0,2,List.of(first)));
        h.assertTrue(complete.isPresent()&&complete.get().items().equals(List.of(first,second)),"Out-of-order snapshot must assemble in batch order");
        h.assertTrue(assembly.accept(new ConveyorSnapshot(dimension,chunk,41,0,1,List.of())).isEmpty(),"Stale snapshot must not erase newer cargo");
        h.assertTrue(assembly.accept(new ConveyorSnapshot(dimension,chunk,43,0,2,List.of(first))).isEmpty(),"New partial snapshot must wait");
        h.assertTrue(assembly.accept(new ConveyorSnapshot(dimension,chunk,44,0,1,List.of())).isPresent(),"Complete newer empty snapshot must remove cargo");
        h.assertTrue(assembly.accept(new ConveyorSnapshot(dimension,chunk,43,1,2,List.of(second))).isEmpty(),"Late old batch must not resurrect removed cargo");
        assembly.forget(chunk);
        h.assertTrue(assembly.accept(new ConveyorSnapshot(dimension,chunk,1,0,1,List.of(first))).isPresent(),"Chunk reload must reset its snapshot epoch");h.succeed();
    }

}
