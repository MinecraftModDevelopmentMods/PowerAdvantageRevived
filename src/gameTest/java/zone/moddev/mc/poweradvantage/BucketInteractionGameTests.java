package zone.moddev.mc.poweradvantage;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.tiles.MachineBlockEntity;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class BucketInteractionGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bucketsFillAndEmptyTanksFromEveryFace(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        int x = 1;
        for (Direction face : Direction.values()) {
            BlockPos rel = new BlockPos(x, 2, 3);
            x += 2;
            h.setBlock(rel, BuiltInRegistries.BLOCK.get(ModContent.id("fluid_storage_tank")));
            BlockPos abs = h.absolutePos(rel);
            var tank = (MachineBlockEntity) h.getLevel().getBlockEntity(abs);
            var hit = new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            h.getLevel().getBlockState(abs).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(tank.fluids.tank(0).getFluidAmount() == 1000 && player.getMainHandItem().is(Items.BUCKET), "Water bucket must empty into the tank via " + face);
            h.getLevel().getBlockState(abs).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(tank.fluids.tank(0).isEmpty() && player.getMainHandItem().is(Items.WATER_BUCKET), "Empty bucket must fill from the tank via " + face);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void drainThroughPipesIsBoundedByTankCapacity(GameTestHelper h) {
        var drainRel = new BlockPos(2, 3, 5);
        h.setBlock(drainRel, BuiltInRegistries.BLOCK.get(ModContent.id("fluid_drain")));
        for (int x = 3; x <= 5; x++) h.setBlock(new BlockPos(x, 3, 5), BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe")));
        var tankRel = new BlockPos(6, 3, 5);
        h.setBlock(tankRel, BuiltInRegistries.BLOCK.get(ModContent.id("fluid_storage_tank")));
        var drain = (MachineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(drainRel));
        var tank = (MachineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(tankRel));
        BlockPos source = h.absolutePos(drainRel).above();
        for (Direction side : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
            h.getLevel().setBlockAndUpdate(source.relative(side), Blocks.STONE.defaultBlockState());
        for (int t = 5; t < 600; t += 5) h.runAfterDelay(t, () -> {
            if (h.getLevel().getFluidState(source).isEmpty()) h.getLevel().setBlockAndUpdate(source, Blocks.WATER.defaultBlockState());
        });
        h.runAfterDelay(600, () -> {
            h.assertTrue(tank.fluids.tank(0).getFluidAmount() == 4000, "Tank must fill to exactly its capacity");
            h.assertTrue(drain.fluids.tank(0).getFluidAmount() == 2000, "Drain must stop at its own capacity once the tank is full");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realRightClickUsesBucketsInsteadOfOpeningMenu(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 2, 3);
        h.setBlock(rel, BuiltInRegistries.BLOCK.get(ModContent.id("fluid_storage_tank")));
        BlockPos abs = h.absolutePos(rel);
        var tank = (MachineBlockEntity) h.getLevel().getBlockEntity(abs);
        tank.fluids.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        var hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        var event = net.neoforged.neoforge.common.CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, abs, hit);
        h.assertTrue(!event.isCanceled(), "MMDLib's menu binding must not consume a bucket click");
        h.getLevel().getBlockState(abs).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET) && tank.fluids.tank(0).getFluidAmount() == 1000, "Empty bucket must take 1000 mB from the tank");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pipesCanBeWaterlogged(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 3, 3);
        h.setBlock(rel, Blocks.WATER);
        h.setBlock(rel, BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe")).defaultBlockState()
            .setValue(zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock.WATERLOGGED, true));
        h.assertTrue(h.getLevel().getFluidState(h.absolutePos(rel)).isSource(), "Waterlogged pipe must hold a water source");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void flowingWaterDoesNotBreakPipes(GameTestHelper h) {
        BlockPos pipe = new BlockPos(3, 3, 3);
        h.setBlock(pipe, BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe")));
        h.setBlock(pipe.above(), Blocks.WATER);
        h.setBlock(pipe.east(), Blocks.WATER);
        h.runAfterDelay(40, () -> {
            h.assertTrue(h.getBlockState(pipe).is(BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe"))), "Water flowing onto a pipe must not break it");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void emptyBucketRemovesWaterFromWaterloggedPipe(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 3, 3);
        var block = BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe"));
        h.setBlock(rel, block.defaultBlockState().setValue(zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock.WATERLOGGED, true));
        BlockPos abs = h.absolutePos(rel);
        var picked = ((net.minecraft.world.level.block.BucketPickup) block).pickupBlock(null, h.getLevel(), abs, h.getLevel().getBlockState(abs));
        h.assertTrue(picked.is(Items.WATER_BUCKET) && h.getLevel().getBlockState(abs).is(block) && h.getLevel().getFluidState(abs).isEmpty(), "Pickup must drain the pipe and keep it");
        h.succeed();
    }
}
