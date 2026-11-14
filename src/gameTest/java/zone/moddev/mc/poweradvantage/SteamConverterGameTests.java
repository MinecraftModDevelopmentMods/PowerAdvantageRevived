package zone.moddev.mc.poweradvantage;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock;
import zone.moddev.mc.poweradvantage.init.*;
import zone.moddev.mc.poweradvantage.machine.SteamExchange;
import zone.moddev.mc.poweradvantage.power.*;

/**
 * The Steam-RF Transformer as an MMDLib XML machine with the {@link SteamExchange} processor.
 */
@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class SteamConverterGameTests {
    private static DefinedMachineEntity place(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(ModContent.id("converter_rf_steam")));
        var be = h.getLevel().getBlockEntity(h.absolutePos(pos));
        h.assertTrue(be instanceof DefinedMachineEntity, "The converter must be an MMDLib XML machine");
        return (DefinedMachineEntity) be;
    }
    private static DefinedMachineEntity place(GameTestHelper h) { return place(h, new BlockPos(4,3,5)); }
    private static FluidStack steam(int amount) { return new FluidStack(Fluids.STEAM.get(), amount); }
    private static long fe(DefinedMachineEntity be) { return be.energy.stored(); }
    private static int steamStored(DefinedMachineEntity be) { return be.fluids.getFluidInTank(0).getAmount(); }
    private static net.neoforged.neoforge.fluids.capability.IFluidHandler steamPort(GameTestHelper h, DefinedMachineEntity be) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), Direction.UP);
    }
    private static net.neoforged.neoforge.energy.IEnergyStorage fePort(GameTestHelper h, DefinedMachineEntity be) {
        return h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), Direction.UP);
    }

    @GameTest(template = "empty")
    public static void registrationXmlAndSteamOnlyCapabilities(GameTestHelper h) {
        var be = place(h);
        for (Direction side : Direction.values()) {
            h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), side) != null, "Steam capability on every face");
            h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), side) != null, "FE capability on every face");
        }
        var port = steamPort(h, be);
        h.assertTrue(port.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000), FluidAction.EXECUTE) == 0, "Reject water");
        h.assertTrue(port.fill(steam(1000), FluidAction.SIMULATE) == 1000 && steamStored(be) == 0, "Fluid simulation must not mutate storage");
        port.fill(steam(500), FluidAction.EXECUTE);
        h.assertTrue(steamStored(be) == 500, "Steam accepted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void eightFeProducesOneMillibucketWithoutRoundingGain(GameTestHelper h) {
        var be = place(h);
        fePort(h, be).receiveEnergy(7,false);
        h.runAfterDelay(3, () -> {
            h.assertTrue(fe(be) == 7 && steamStored(be) == 0, "Seven FE must remain FE until a complete batch exists");
            fePort(h, be).receiveEnergy(1,false);
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(fe(be) == 0 && steamStored(be) == 1, "Eight FE must produce exactly one mB");
            h.assertTrue(steamPort(h, be).drain(1,FluidAction.SIMULATE).getAmount() == 1 && steamStored(be) == 1, "Drain simulation is read only");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oneSurplusMillibucketProducesEightFeAndPersists(GameTestHelper h) {
        var be = place(h);
        steamPort(h, be).fill(steam(501),FluidAction.EXECUTE);
        h.runAfterDelay(3, () -> {
            h.assertTrue(fe(be) == 8 && steamStored(be) == 500, "One surplus mB must yield exactly eight FE");
            var restored = place(h, new BlockPos(6,3,5));
            restored.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(fe(restored) == 8 && steamStored(restored) == 500, "FE and steam must survive NBT round trip");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void transformersSavedBy31KeepTheirSteam(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        // What 3.0/3.1's SteamConverterBlockEntity saved.
        var steam = new FluidStorage();
        steam.addTank(1000, Role.STORAGE, stack -> true);
        steam.fill(steam(400), FluidAction.EXECUTE);
        var tag = new CompoundTag();
        tag.put("FE", new EnergyBuffer(1000, 1000).serializeNBT(registries));
        tag.put("Steam", steam.serializeNBT(registries));
        var be = place(h);
        be.loadWithComponents(tag, registries);
        h.assertTrue(be.fluids.getFluidInTank(0).is(Fluids.STEAM.get()) && steamStored(be) == 400,
            "3.1 steam must load, got " + steamStored(be));
        h.succeed();
    }

    @GameTest(template = "empty",timeoutTicks = 60)
    public static void steamInputExportsFeToANeighbor(GameTestHelper h) {
        var be = place(h);
        h.setBlock(new BlockPos(5,3,5),Blocks.REDSTONE_BLOCK);
        steamPort(h, be).fill(steam(1000),FluidAction.EXECUTE);
        h.runAfterDelay(30,()->{
            var sink = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,h.absolutePos(new BlockPos(5,3,5)),Direction.WEST);
            h.assertTrue(sink.getEnergyStored() > 0,"Steam must power an actual neighboring FE sink");
            h.assertTrue(sink.getEnergyStored()+fe(be)+8L*steamStored(be) == 8000,"Steam, converter FE and delivered FE must conserve energy");
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
            h.assertTrue(source.getEnergyStored()+fe(be)+8L*(steamStored(be)+delivered) == 50000,"FE source, steam in transit/destination and stored FE must conserve energy");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void customRatioAndConversionLimitUseWholeBatches(GameTestHelper h) {
        var fe = new EnergyBuffer(1000, 1000);
        fe.insert(6, false);
        var tank = new FluidTank(1000);
        SteamExchange.exchange(fe, tank, new SteamConverterSettings(1000,1000,3,2,6));
        h.assertTrue(fe.stored() == 0 && tank.getFluidAmount() == 4,"Custom six-FE work limit must produce two complete two-mB batches");
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
            h.assertTrue(steamStored(be) == 1000 && player.getMainHandItem().is(net.minecraft.world.item.Items.BUCKET),"Steam bucket must fill converter from " + side);
            be.getBlockState().useItemOn(player.getMainHandItem(),h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            h.assertTrue(steamStored(be) == 0 && player.getMainHandItem().is(Fluids.STEAM_BUCKET.get()),"Empty bucket must collect steam from " + side);
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()),Direction.UP,be.getBlockPos(),false);
        be.getBlockState().useItemOn(player.getMainHandItem(),h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(steamStored(be) == 0 && player.getMainHandItem().is(net.minecraft.world.item.Items.WATER_BUCKET),"Rejected water bucket must remain unchanged");
        h.succeed();
    }
}
