package zone.moddev.mc.poweradvantage;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock;
import zone.moddev.mc.poweradvantage.init.*;
import zone.moddev.mc.poweradvantage.power.*;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class SteamConverterGameTests {
    private static SteamConverterBlockEntity place(GameTestHelper h) {
        var pos = new BlockPos(4,3,5);
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(ModContent.id("converter_rf_steam")));
        return (SteamConverterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }
    private static void tick(GameTestHelper h, SteamConverterBlockEntity be) {
        BufferedConverterBlockEntity.serverTick(h.getLevel(), be.getBlockPos(), be.getBlockState(), be);
    }
    private static FluidStack steam(int amount) { return new FluidStack(Fluids.STEAM.get(), amount); }
    @GameTest(template = "empty")
    public static void registrationXmlAndSteamOnlyCapabilities(GameTestHelper h) {
        var be = place(h);
        for (Direction side : Direction.values()) {
            h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), side) == be.steamPort(), "Steam capability on every face");
            h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), side) == be.fePort(), "FE capability on every face");
        }
        h.assertTrue(zone.moddev.mc.lib.gui.Screens.get(ModContent.id("converter_rf_steam")).isPresent(), "MMDLib XML screen binding");
        var menu = new zone.moddev.mc.lib.gui.runtime.MachineMenu(1,
            h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL).getInventory(), be);
        h.assertTrue(menu.slots.isEmpty(), "Status screen must have no item inventory");
        h.assertTrue(be.steamPort().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000), FluidAction.EXECUTE) == 0, "Reject water");
        h.assertTrue(be.steamPort().fill(steam(1000), FluidAction.SIMULATE) == 1000 && be.screenSteam().isEmpty(), "Fluid simulation must not mutate storage");
        be.steamPort().fill(steam(500), FluidAction.EXECUTE);
        var snapshot = be.steamPort().getFluidInTank(0);
        snapshot.setAmount(1);
        h.assertTrue(be.screenSteam().getAmount() == 500, "External snapshots must not mutate the tank");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void eightFeProducesOneMillibucketWithoutRoundingGain(GameTestHelper h) {
        var be = place(h);
        be.fePort().receiveEnergy(7,false);
        tick(h,be);
        h.assertTrue(be.screenFe() == 7 && be.screenSteam().isEmpty(), "Seven FE must remain FE until a complete batch exists");
        be.fePort().receiveEnergy(1,false);
        tick(h,be);
        h.assertTrue(be.screenFe() == 0 && be.screenSteam().getAmount() == 1, "Eight FE must produce exactly one mB");
        h.assertTrue(be.steamPort().drain(1,FluidAction.SIMULATE).getAmount() == 1 && be.screenSteam().getAmount() == 1, "Drain simulation is read only");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void oneSurplusMillibucketProducesEightFeAndPersists(GameTestHelper h) {
        var be = place(h);
        be.steamPort().fill(steam(501),FluidAction.EXECUTE);
        tick(h,be);
        h.assertTrue(be.screenFe() == 8 && be.screenSteam().getAmount() == 500, "One surplus mB must yield exactly eight FE");
        var restored = new SteamConverterBlockEntity(be.getType(),be.getBlockPos(),be.getBlockState(),be.settings);
        restored.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.screenFe() == 8 && restored.screenSteam().getAmount() == 500, "FE and steam must survive NBT round trip");
        var old = be.steamPort();
        h.setBlock(new BlockPos(4,3,5),Blocks.AIR);
        h.assertTrue(old.fill(steam(1),FluidAction.EXECUTE) == 0 && old.drain(1,FluidAction.EXECUTE).isEmpty(), "Removed converter must reject cached fluid handlers");
        h.succeed();
    }
    @GameTest(template = "empty",timeoutTicks = 60)
    public static void steamInputExportsFeToANeighbor(GameTestHelper h) {
        var be = place(h);
        h.setBlock(new BlockPos(5,3,5),Blocks.REDSTONE_BLOCK);
        be.steamPort().fill(steam(1000),FluidAction.EXECUTE);
        h.runAfterDelay(30,()->{
            var sink = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,h.absolutePos(new BlockPos(5,3,5)),Direction.WEST);
            h.assertTrue(sink.getEnergyStored() > 0,"Steam must power an actual neighboring FE sink");
            h.assertTrue(sink.getEnergyStored()+be.screenFe()+8L*be.screenSteam().getAmount() == 8000,"Steam, converter FE and delivered FE must conserve energy");
            h.succeed();
        });
    }
    @GameTest(template = "empty",timeoutTicks = 120)
    public static void feInputMakesSteamDeliveredByFluidPipes(GameTestHelper h) {
        var be = place(h);
        h.setBlock(new BlockPos(3,3,5),Blocks.COAL_BLOCK);
        h.setBlock(new BlockPos(5,3,5),BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe_terminal"))
            .defaultBlockState().setValue(FluidPipeBlock.SOURCE,Direction.WEST));
        h.setBlock(new BlockPos(6,3,5),BuiltInRegistries.BLOCK.get(ModContent.id("fluid_pipe")));
        h.setBlock(new BlockPos(7,3,5),BuiltInRegistries.BLOCK.get(ModContent.id("fluid_metal_tank")));
        h.runAfterDelay(80,()->{
            var source = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,h.absolutePos(new BlockPos(3,3,5)),Direction.EAST);
            var target = Machines.of(h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(7,3,5))));
            int delivered = target.fluids.tank(0).getFluidAmount();
            h.assertTrue(delivered > 0 && target.fluids.tank(0).getFluid().getFluid().isSame(Fluids.STEAM.get()),"Fluid network must extract generated steam and deliver it to a tank");
            h.assertTrue(source.getEnergyStored()+be.screenFe()+8L*(be.screenSteam().getAmount()+delivered) == 50000,"FE source, steam in transit/destination and stored FE must conserve energy");
            h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void customRatioAndConversionLimitUseWholeBatches(GameTestHelper h) {
        var registered = place(h);
        var be = new SteamConverterBlockEntity(registered.getType(),registered.getBlockPos(),registered.getBlockState(),
            new SteamConverterSettings(1000,1000,3,2,6));
        be.setLevel(h.getLevel());
        be.fePort().receiveEnergy(6,false);
        tick(h,be);
        h.assertTrue(be.screenFe() == 0 && be.screenSteam().getAmount() == 4,"Custom six-FE work limit must produce two complete two-mB batches");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void bucketsFillAndDrainSteamFromEveryFace(GameTestHelper h) {
        var be = place(h);
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for (Direction side : Direction.values()) {
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()),side,be.getBlockPos(),false);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Fluids.STEAM_BUCKET.get()));
            be.getBlockState().useItemOn(player.getMainHandItem(),h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(be.screenSteam().getAmount() == 1000 && player.getMainHandItem().is(net.minecraft.world.item.Items.BUCKET),"Steam bucket must fill converter from " + side);
            be.getBlockState().useItemOn(player.getMainHandItem(),h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(be.screenSteam().isEmpty() && player.getMainHandItem().is(Fluids.STEAM_BUCKET.get()),"Empty bucket must collect steam from " + side);
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()),Direction.UP,be.getBlockPos(),false);
        be.getBlockState().useItemOn(player.getMainHandItem(),h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(be.screenSteam().isEmpty() && player.getMainHandItem().is(net.minecraft.world.item.Items.WATER_BUCKET),"Rejected water bucket must remain unchanged");
        h.succeed();
    }

}
