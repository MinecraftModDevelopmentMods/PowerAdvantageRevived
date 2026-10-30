package zone.moddev.mc.poweradvantage;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.network.FluidNetworks;
import zone.moddev.mc.poweradvantage.tiles.MachineBlockEntity;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class PipeGameTests {
    private static BlockPos pos(int x,int z) {return new BlockPos(x,3,z);}
    private static void pipe(GameTestHelper h,int x,int z) {
        h.setBlock(pos(x,z),BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe")));
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(pos(x,z)))==null,"Pipe must not have a block entity");
    }
    private static MachineBlockEntity tank(GameTestHelper h,int x,int z) {
        h.setBlock(pos(x,z),BuiltInRegistries.BLOCK.get(ModContent.id("fluid_metal_tank")));
        return (MachineBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos(x,z)));
    }
    private static IFluidHandler input(GameTestHelper h,int x,int z,Direction face) {
        var handler=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(pos(x,z)),face);
        h.assertTrue(handler!=null,"Pipe capability missing");return handler;
    }
    private static FluidStack water(int amount){return new FluidStack(Fluids.WATER,amount);}
    @GameTest(template="empty",timeoutTicks=100)
    public static void compressionSimulationAndBudget(GameTestHelper h) {
        var source=tank(h,1,5);var sink=tank(h,10,5);
        for(int x=2;x<10;x++)pipe(h,x,5);
        h.runAfterDelay(20,()->{
            var manager=FluidNetworks.get(h.getLevel());var stats=manager.stats(h.absolutePos(pos(2,5)));
            h.assertTrue(stats.pipes()==8 && stats.vertices()==2,"Straight pipe chain must compress to two endpoint vertices: "+stats);
            var input=input(h,2,5,Direction.WEST);
            h.assertTrue(input.fill(water(16000),FluidAction.SIMULATE)==1000,"Simulated throughput limit");
            h.assertTrue(sink.fluids.tank(0).isEmpty(),"Simulation must not insert fluid");
            h.assertTrue(input.fill(water(16000),FluidAction.EXECUTE)==1000,"Actual throughput limit");
            h.assertTrue(input.fill(water(1000),FluidAction.EXECUTE)==0,"Repeated calls must share the same per-tick budget");
            h.assertTrue(sink.fluids.tank(0).getFluidAmount()==1000 && source.fluids.tank(0).isEmpty(),"Do not send back into injecting endpoint");
            h.assertTrue(input.drain(1000,FluidAction.EXECUTE).isEmpty(),"Virtual pipe has no stored fluid to drain");
            var state=h.getBlockState(pos(2,5));
            h.assertTrue(state.getValue(FluidPipeBlock.CONNECTIONS.get(Direction.WEST)) && state.getValue(FluidPipeBlock.CONNECTIONS.get(Direction.EAST)),"Visual endpoint and pipe connections");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void splitMergeAndStaleCapability(GameTestHelper h) {
        tank(h,1,4);var sink=tank(h,7,4);
        for(int x=2;x<7;x++)pipe(h,x,4);
        h.runAfterDelay(20,()->{
            var old=input(h,2,4,Direction.WEST);
            h.assertTrue(old.fill(water(1000),FluidAction.EXECUTE)==1000,"Initial connected route");
            h.setBlock(pos(4,4),Blocks.AIR);
            h.assertTrue(old.fill(water(1000),FluidAction.EXECUTE)==0,"Break must suspend stale routes immediately");
        });
        h.runAfterDelay(40,()->{
            h.assertTrue(input(h,2,4,Direction.WEST).fill(water(1000),FluidAction.EXECUTE)==0,"Separated components must not transfer");
            pipe(h,4,4);
        });
        h.runAfterDelay(60,()->{
            h.assertTrue(input(h,2,4,Direction.WEST).fill(water(1000),FluidAction.EXECUTE)==1000,"Reconnected route");
            h.assertTrue(sink.fluids.tank(0).getFluidAmount()==2000,"Split/rejoin conservation");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void explicitExtractionAndFairDestinations(GameTestHelper h) {
        var source=tank(h,1,5);source.fluids.fill(water(10000),FluidAction.EXECUTE);
        var a=tank(h,7,3);var b=tank(h,7,7);
        for(int x=2;x<=7;x++)pipe(h,x,5);
        pipe(h,7,4);pipe(h,7,6);
        h.runAfterDelay(20,()->{
            h.assertTrue(source.fluids.tank(0).getFluidAmount()==10000 && a.fluids.tank(0).isEmpty() && b.fluids.tank(0).isEmpty(),"Passive tanks must not move fluid without extraction");
            var terminal=BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe_terminal"));
            h.setBlock(pos(2,5),terminal.defaultBlockState().setValue(FluidPipeBlock.SOURCE,Direction.WEST));
            h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(pos(2,5)))==null,"Terminal must not have a block entity");
        });
        h.runAfterDelay(55,()->{
            int first=a.fluids.tank(0).getFluidAmount(),second=b.fluids.tank(0).getFluidAmount();
            h.assertTrue(first>0 && second>0 && Math.abs(first-second)<=1000,"Equal destinations must both receive service: "+first+", "+second);
            h.assertTrue(source.fluids.tank(0).getFluidAmount()+first+second==10000,"Terminal transfer must conserve fluid");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void liveAcceptanceAndEndpointReplacement(GameTestHelper h) {
        tank(h,1,5);var sink=tank(h,5,5);sink.fluids.fill(new FluidStack(Fluids.LAVA,16000),FluidAction.EXECUTE);
        for(int x=2;x<5;x++)pipe(h,x,5);
        h.runAfterDelay(20,()->{
            var handler=input(h,2,5,Direction.WEST);
            h.assertTrue(handler.fill(water(1000),FluidAction.SIMULATE)==0,"Reject full incompatible destination");
            sink.fluids.tank(0).drain(16000,FluidAction.EXECUTE);
            h.assertTrue(handler.fill(water(1000),FluidAction.EXECUTE)==1000,"Recheck acceptance after contents change");
            h.setBlock(pos(5,5),Blocks.STONE);
        });
        h.runAfterDelay(40,()->{
            h.assertTrue(input(h,2,5,Direction.WEST).fill(water(1000),FluidAction.EXECUTE)==0,"Removed endpoint must not remain routable");
            tank(h,5,5);
        });
        h.runAfterDelay(60,()->{
            h.assertTrue(input(h,2,5,Direction.WEST).fill(water(1000),FluidAction.EXECUTE)==1000,"New endpoint must reconnect");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void closedLoopAndSixWayJoin(GameTestHelper h) {
        for(int x=3;x<=5;x++){pipe(h,x,3);pipe(h,x,5);}
        pipe(h,3,4);pipe(h,5,4);
        pipe(h,10,10);
        for(Direction side:Direction.values())h.setBlock(pos(10,10).relative(side),BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe")));
        h.runAfterDelay(20,()->{
            var manager=FluidNetworks.get(h.getLevel());
            h.assertTrue(manager.stats(h.absolutePos(pos(3,3))).pipes()==8,"Loop discovery must terminate");
            h.assertTrue(manager.stats(h.absolutePos(pos(3,3))).vertices()==1,"Endpoint-free loop needs one graph anchor");
            var state=h.getBlockState(pos(10,10));
            for(Direction side:Direction.values())h.assertTrue(state.getValue(FluidPipeBlock.CONNECTIONS.get(side)),"Missing six-way connection "+side);
            h.assertTrue(input(h,3,3,Direction.UP).fill(water(1000),FluidAction.EXECUTE)==0,"Loop without endpoints must not absorb fluid");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=140)
    public static void chunkBoundarySuspendsAndRecovers(GameTestHelper h) {
        var level=h.getLevel();
        BlockPos origin=h.absolutePos(pos(5,5));
        int boundary=(origin.getX()>>4)*16+16;
        BlockPos first=new BlockPos(boundary-1,origin.getY(),origin.getZ()),second=first.east();
        BlockPos source=first.west(),target=second.east();
        var pipe=BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe"));
        var tank=BuiltInRegistries.BLOCK.get(ModContent.id("fluid_metal_tank"));
        level.setBlockAndUpdate(source,tank.defaultBlockState());level.setBlockAndUpdate(target,tank.defaultBlockState());
        level.setBlockAndUpdate(first,pipe.defaultBlockState());level.setBlockAndUpdate(second,pipe.defaultBlockState());
        var manager=FluidNetworks.get(level);
        h.runAfterDelay(20,()->{
            h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK,first,Direction.WEST).fill(water(1000),FluidAction.EXECUTE)==1000,"Initial cross-chunk route");
            // Exercise exactly the chunk-event path without asking the test server to unload its own arena.
            manager.chunkUnloaded(new net.minecraft.world.level.ChunkPos(second));
        });
        h.runAfterDelay(40,()->{
            h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK,first,Direction.WEST).fill(water(1000),FluidAction.EXECUTE)==0,"Unloaded boundary must suspend cross-chunk routing");
            manager.chunkLoaded(level.getChunkAt(second));
        });
        h.runAfterDelay(70,()->{
            h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK,first,Direction.WEST).fill(water(1000),FluidAction.EXECUTE)==1000,"Reloaded boundary must reconnect");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void competingPushersShareService(GameTestHelper h) {
        tank(h,1,5).fluids.fill(new FluidStack(Fluids.LAVA,16000),FluidAction.EXECUTE);
        tank(h,5,5).fluids.fill(new FluidStack(Fluids.LAVA,16000),FluidAction.EXECUTE);
        var sink=tank(h,3,6);
        for(int x=2;x<=4;x++)pipe(h,x,5);
        var counts=new int[2];
        for(int delay=20;delay<30;delay++)h.runAfterDelay(delay,()->{
            counts[0]+=input(h,2,5,Direction.WEST).fill(water(1000),FluidAction.EXECUTE);
            counts[1]+=input(h,4,5,Direction.EAST).fill(water(1000),FluidAction.EXECUTE);
        });
        h.runAfterDelay(35,()->{
            h.assertTrue(counts[0]>0 && counts[1]>0 && Math.abs(counts[0]-counts[1])<=1000,"Earlier tick order must not starve later pusher: "+counts[0]+", "+counts[1]);
            h.assertTrue(sink.fluids.tank(0).getFluidAmount()==counts[0]+counts[1],"Competing pushers must share capacity without duplication");h.succeed();
        });
    }

}
