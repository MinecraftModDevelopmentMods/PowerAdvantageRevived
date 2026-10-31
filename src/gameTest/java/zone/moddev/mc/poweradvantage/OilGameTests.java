package zone.moddev.mc.poweradvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import zone.moddev.mc.poweradvantage.init.Fluids;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class OilGameTests {
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sourceAndFlowingOilApplyContactEffects(GameTestHelper h) {
        var crudePos = new BlockPos(3, 3, 3);
        var refinedPos = new BlockPos(8, 3, 3);
        h.setBlock(crudePos.below(), Blocks.STONE);
        h.setBlock(refinedPos.below(), Blocks.STONE);
        h.setBlock(crudePos, Fluids.CRUDE_OIL_BLOCK.get());
        h.setBlock(refinedPos, Fluids.REFINED_OIL_BLOCK.get().defaultBlockState().setValue(LiquidBlock.LEVEL, 2));
        var crudePig = h.spawnWithNoFreeWill(EntityType.PIG, crudePos);
        var refinedPig = h.spawnWithNoFreeWill(EntityType.PIG, refinedPos);
        h.succeedWhen(() -> {
            var slow = crudePig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            var nausea = refinedPig.getEffect(MobEffects.CONFUSION);
            h.assertTrue(slow != null && slow.getAmplifier() == 2 && slow.getDuration() > 190 && slow.getDuration() <= 200,
                "Crude oil must apply PA2's ten-second Slowness III");
            h.assertTrue(nausea != null && nausea.getAmplifier() == 0 && nausea.getDuration() > 190 && nausea.getDuration() <= 200,
                "Flowing refined oil must apply ten-second nausea");
        });
    }
}
