package zone.moddev.mc.poweradvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import zone.moddev.mc.poweradvantage.blocks.FluidSwitchBlock;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.tiles.MachineBlockEntity;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class FluidSwitchGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void powerSplitsAndRejoinsRoutes(GameTestHelper h) {
        var valve = new BlockPos(4, 3, 5);
        var inputPos = new BlockPos(2, 3, 5);
        for (int x = 1; x <= 7; x++) {
            String name = x == 1 || x == 7 ? "fluid_metal_tank" : x == 4 ? "fluid_switch" : "fluid_pipe";
            h.setBlock(new BlockPos(x, 3, 5), BuiltInRegistries.BLOCK.get(ModContent.id(name)));
        }
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(valve)) == null, "Valve must not have a block entity");
        var level = h.getLevel();
        var input = level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(inputPos), Direction.WEST);
        var valveInput = level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(valve), Direction.WEST);
        h.assertTrue(input != null && valveInput != null, "Missing transport capabilities");
        h.runAfterDelay(20, () -> {
            h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000, "Open valve routes fluid");
            h.setBlock(valve.above(), Blocks.REDSTONE_BLOCK);
            h.assertTrue(!h.getBlockState(valve).getValue(FluidSwitchBlock.ACTIVE), "Power must close valve");
            h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0, "Old routes must suspend immediately");
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0, "Closed valve isolates both halves");
            h.assertTrue(valveInput.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0, "Cached valve handler cannot bypass closure");
            h.setBlock(valve.above(), Blocks.AIR);
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(h.getBlockState(valve).getValue(FluidSwitchBlock.ACTIVE), "Removing power opens valve");
            h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000, "Reopened valve rejoins route");
            var sink = (MachineBlockEntity) level.getBlockEntity(h.absolutePos(new BlockPos(7, 3, 5)));
            h.assertTrue(sink.fluids.tank(0).getFluidAmount() == 2000, "Valve cycle must conserve fluid");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void placementReadsExistingPower(GameTestHelper h) {
        var pos = new BlockPos(4, 3, 4);
        h.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(ModContent.id("fluid_switch")));
        h.assertTrue(!h.getBlockState(pos).getValue(FluidSwitchBlock.ACTIVE), "Valve placed beside power must start closed");
        h.succeed();
    }
}
