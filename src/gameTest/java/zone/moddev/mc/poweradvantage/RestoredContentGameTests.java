package zone.moddev.mc.poweradvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import zone.moddev.mc.poweradvantage.init.ModItems;
import zone.moddev.mc.poweradvantage.init.ModContent;

/** Runtime regression checks for restored PA2 content. */
@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class RestoredContentGameTests {
    @GameTest(template = "empty")
    public static void wrenchRotatesAndPassesOnFixedBlocks(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROTATOR_TOOL.get()));
        var absolute = h.absolutePos(pos);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        h.assertTrue(ModItems.ROTATOR_TOOL.get().useOn(context).consumesAction(), "Wrench should rotate furnace");
        h.assertTrue(h.getBlockState(pos).getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.WEST,
            "Wrench must rotate counterclockwise");
        h.setBlock(pos, Blocks.STONE);
        h.assertTrue(ModItems.ROTATOR_TOOL.get().useOn(context) == InteractionResult.PASS, "Fixed blocks must pass");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void restoredRecipesLoad(GameTestHelper h) {
        for (String name : new String[]{"fluid_drain", "fluid_discharge", "fluid_storage_tank",
                "fluid_metal_tank_iron", "fluid_pipe_iron", "item_conveyor", "steel_frame",
                "still", "rotator_tool", "sprocket_stickwood", "bioplastic_ingot"}) {
            h.assertTrue(h.getLevel().getRecipeManager().byKey(ModContent.id(name)).isPresent(), "Missing recipe " + name);
        }
        h.succeed();
    }
}
