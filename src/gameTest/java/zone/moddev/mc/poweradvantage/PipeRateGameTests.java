package zone.moddev.mc.poweradvantage;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.registries.RegisterEvent;
import zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock;
import zone.moddev.mc.poweradvantage.init.ModContent;

/** Downstream-style subclass, block registration and capability registration. */
@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid=PowerAdvantage.MODID)
public final class PipeRateGameTests {
    private static final int RATE=2_048_000;
    private static final Map<BlockPos,FluidTank> tanks=new HashMap<>();
    private static final class HighPipe extends FluidPipeBlock {
        HighPipe(boolean terminal) { super(Properties.of(),terminal,RATE); }
    }
    @SubscribeEvent public static void blocks(RegisterEvent event) {
        event.register(Registries.BLOCK,helper->{
            helper.register(ModContent.id("test_high_pipe"),new HighPipe(false));
            helper.register(ModContent.id("test_high_terminal"),new HighPipe(true));
        });
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        FluidPipeBlock.registerCapabilities(event,
            (FluidPipeBlock)BuiltInRegistries.BLOCK.get(ModContent.id("test_high_pipe")),
            (FluidPipeBlock)BuiltInRegistries.BLOCK.get(ModContent.id("test_high_terminal")));
        event.registerBlock(Capabilities.FluidHandler.BLOCK,(level,pos,state,be,side)->
            tanks.computeIfAbsent(pos.immutable(),key->new FluidTank(10_000_000)),Blocks.EMERALD_BLOCK);
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void highRateAndMixedTierBottleneck(GameTestHelper h) {
        var level=h.getLevel();
        var first=new BlockPos(3,3,5);var second=first.east();var target=second.east();
        var high=BuiltInRegistries.BLOCK.get(ModContent.id("test_high_pipe"));
        h.setBlock(first,high);h.setBlock(second,high);h.setBlock(target,Blocks.EMERALD_BLOCK);
        h.setBlock(first.west(),Blocks.EMERALD_BLOCK);
        level.getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(first.west()),Direction.EAST)
            .fill(new FluidStack(Fluids.LAVA,10_000_000),FluidAction.EXECUTE);
        h.runAfterDelay(20,()->{
            var handler=level.getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(first),Direction.WEST);
            h.assertTrue(handler.getTankCapacity(0)==RATE,"Advertised pipe rating");
            h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,4_096_000),FluidAction.SIMULATE)==RATE,"High-rate simulation");
            h.assertTrue(tanks.get(h.absolutePos(target)).isEmpty(),"Simulation must not move fluid");
            h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,4_096_000),FluidAction.EXECUTE)==RATE,"High-rate execution");
            h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,1),FluidAction.EXECUTE)==0,"Repeated calls share high-tier budget");
            h.setBlock(second,BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe")));
        });
        h.runAfterDelay(40,()->{
            var handler=level.getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(first),Direction.WEST);
            h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,RATE),FluidAction.EXECUTE)==1000,"Slow pipe must constrain mixed component");
            h.assertTrue(tanks.get(h.absolutePos(target)).getFluidAmount()==RATE+1000,"Mixed-rate conservation");
            h.setBlock(second,high);
        });
        h.runAfterDelay(60,()->{
            var handler=level.getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(first),Direction.WEST);
            h.assertTrue(handler.fill(new FluidStack(Fluids.WATER,RATE),FluidAction.EXECUTE)==RATE,"Replacing bottleneck restores high throughput");
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void terminalExtractionUsesConfiguredRate(GameTestHelper h) {
        var source=new BlockPos(2,3,5);var terminal=source.east();var target=terminal.east();
        h.setBlock(source,Blocks.EMERALD_BLOCK);h.setBlock(target,Blocks.EMERALD_BLOCK);
        var handler=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(source),Direction.EAST);
        handler.fill(new FluidStack(Fluids.WATER,RATE),FluidAction.EXECUTE);
        h.setBlock(terminal,BuiltInRegistries.BLOCK.get(ModContent.id("test_high_terminal")).defaultBlockState().setValue(FluidPipeBlock.SOURCE,Direction.WEST));
        h.runAfterDelay(25,()->{
            h.assertTrue(tanks.get(h.absolutePos(source)).isEmpty(),"High-rate terminal must extract full configured amount");
            h.assertTrue(tanks.get(h.absolutePos(target)).getFluidAmount()==RATE,"High-rate terminal must deliver full configured amount");
            h.succeed();
        });
    }
}
