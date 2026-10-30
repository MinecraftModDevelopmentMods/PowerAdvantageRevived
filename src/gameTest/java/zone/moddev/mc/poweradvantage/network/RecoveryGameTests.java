package zone.moddev.mc.poweradvantage.network;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
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
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.tiles.MachineBlockEntity;

/** Test-only capability fixtures: absent in the release JAR. */
@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid=PowerAdvantage.MODID)
public final class RecoveryGameTests {
    private static final Set<BlockPos> enabled=new HashSet<>();
    private static final Map<BlockPos,FluidTank> late=new HashMap<>();
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.FluidHandler.BLOCK,(level,pos,state,be,side)->new FluidTank(1000) {
            @Override public int fill(FluidStack stack,FluidAction action) { return action.simulate()?Math.min(1000,stack.getAmount()):0; }
        },Blocks.DIAMOND_BLOCK);
        event.registerBlock(Capabilities.FluidHandler.BLOCK,(level,pos,state,be,side)->enabled.contains(pos)
            ?late.computeIfAbsent(pos.immutable(),p->new FluidTank(1000)):null,Blocks.GOLD_BLOCK);
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void unexpectedRemainderIsPersistentAndRecoverable(GameTestHelper h) {
        var level=h.getLevel();
        var tank=BuiltInRegistries.BLOCK.get(ModContent.id("fluid_metal_tank"));
        var pipe=BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe"));
        var terminal=BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe_terminal"));
        var sourcePos=new BlockPos(2,3,5);var ingress=new BlockPos(3,3,5);var target=new BlockPos(6,3,5);
        h.setBlock(sourcePos,tank);
        var source=(MachineBlockEntity)level.getBlockEntity(h.absolutePos(sourcePos));
        source.fluids.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE);
        h.setBlock(ingress,terminal.defaultBlockState().setValue(FluidPipeBlock.SOURCE,Direction.WEST));
        h.setBlock(new BlockPos(4,3,5),pipe);h.setBlock(new BlockPos(5,3,5),pipe);h.setBlock(target,Blocks.DIAMOND_BLOCK);
        var data=level.getDataStorage().computeIfAbsent(FluidRecovery.factory(),"poweradvantage_fluid_recovery");
        h.runAfterDelay(25,()->{
            int held=data.entries.stream().filter(e->e.pipe().equals(h.absolutePos(ingress))).mapToInt(e->e.fluid().getAmount()).sum();
            h.assertTrue(held>0 && held+source.fluids.tank(0).getFluidAmount()==1000,"Unexpected receiver rejection must not destroy extracted fluid");
            var roundtrip=FluidRecovery.load(data.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
            int saved=roundtrip.entries.stream().filter(e->e.pipe().equals(h.absolutePos(ingress))).mapToInt(e->e.fluid().getAmount()).sum();
            h.assertTrue(saved==held,"Recovery NBT must preserve fluid across restart");
            h.setBlock(target,tank);
        });
        h.runAfterDelay(55,()->{
            var sink=(MachineBlockEntity)level.getBlockEntity(h.absolutePos(target));
            h.assertTrue(sink.fluids.tank(0).getFluidAmount()==1000 && source.fluids.tank(0).isEmpty(),"Escrow must reach a restored destination exactly once");
            h.assertTrue(data.entries.stream().noneMatch(e->e.pipe().equals(h.absolutePos(ingress))),"Successful recovery must clear saved remainder");h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void capabilityAppearingWithoutBlockReplacementRebuilds(GameTestHelper h) {
        var level=h.getLevel();
        h.setBlock(new BlockPos(2,3,5),BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe")));
        var target=h.absolutePos(new BlockPos(3,3,5));h.setBlock(new BlockPos(3,3,5),Blocks.GOLD_BLOCK);
        h.runAfterDelay(20,()->{
            var ingress=level.getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(new BlockPos(2,3,5)),Direction.WEST);
            h.assertTrue(ingress.fill(new FluidStack(Fluids.WATER,1000),FluidAction.SIMULATE)==0,"Absent capability is not a destination");
            enabled.add(target);level.invalidateCapabilities(target);
        });
        h.runAfterDelay(45,()->{
            var ingress=level.getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(new BlockPos(2,3,5)),Direction.WEST);
            h.assertTrue(ingress.fill(new FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==1000,"Capability invalidation must discover newly exposed endpoints");
            h.assertTrue(late.get(target).getFluidAmount()==1000,"Late endpoint received fluid");
            enabled.remove(target);late.remove(target);level.invalidateCapabilities(target);h.succeed();
        });
    }
}
