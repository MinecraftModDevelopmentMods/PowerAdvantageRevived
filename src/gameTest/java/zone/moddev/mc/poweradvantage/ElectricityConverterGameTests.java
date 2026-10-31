package zone.moddev.mc.poweradvantage;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.energy.*;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.power.*;

@EventBusSubscriber(modid = PowerAdvantage.MODID, bus = EventBusSubscriber.Bus.MOD)
@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class ElectricityConverterGameTests {
    private static final Map<BlockPos, EnergyStorage> sources = new HashMap<>();
    private static final Map<BlockPos, EnergyStorage> sinks = new HashMap<>();
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level,pos,state,be,side) ->
            sources.computeIfAbsent(pos.immutable(), p -> new EnergyStorage(50000, 0, 5000, 50000)), Blocks.COAL_BLOCK);
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level,pos,state,be,side) ->
            sinks.computeIfAbsent(pos.immutable(), p -> new EnergyStorage(50000, 5000, 0)), Blocks.REDSTONE_BLOCK);
    }
    private static ElectricityConverterBlockEntity place(GameTestHelper h) {
        var pos = new BlockPos(4,3,5);
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(ModContent.id("converter_rf_electricity")));
        return (ElectricityConverterBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }
    private static void tick(GameTestHelper h, ElectricityConverterBlockEntity be) {
        ElectricityConverterBlockEntity.serverTick(h.getLevel(), be.getBlockPos(), be.getBlockState(), be);
    }
    @GameTest(template = "empty")
    public static void registrationCapabilitiesXmlAndExactExchange(GameTestHelper h) {
        var be = place(h);
        for (Direction side : Direction.values()) {
            h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), side) == be.fePort(), "FE capability on " + side);
            h.assertTrue(h.getLevel().getCapability(PowerCapabilities.ELECTRICITY, be.getBlockPos(), side) == be.electricityPort(), "Electricity capability on " + side);
        }
        h.assertTrue(zone.moddev.mc.lib.gui.Screens.get(ModContent.id("converter_rf_electricity")).isPresent(), "Bound XML screen");
        var menu = new zone.moddev.mc.lib.gui.runtime.MachineMenu(1,
            h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL).getInventory(), be);
        h.assertTrue(menu.slots.isEmpty(), "Converter status screen has no item slots");
        h.assertTrue(be.fePort().receiveEnergy(1000, true) == 1000 && be.screenFe() == 0, "FE simulation must be read only");
        h.assertTrue(be.fePort().receiveEnergy(1000, false) == 1000, "FE admission");
        tick(h, be);
        h.assertTrue(be.screenFe() == 0 && be.screenElectricity() == 4000, "One FE must convert to four electricity units");
        h.assertTrue(be.electricityPort().extractEnergy(1000, true) == 1000 && be.screenElectricity() == 4000, "Electricity simulation must be read only");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void reverseExchangeRetainsFractionalBatchesAndSavesBothUnits(GameTestHelper h) {
        var be = place(h);
        be.electricityPort().receiveEnergy(4003, false);
        tick(h, be);
        h.assertTrue(be.screenFe() == 0 && be.screenElectricity() == 4003, "Three surplus electricity units must not round into FE");
        be.electricityPort().receiveEnergy(1, false);
        tick(h, be);
        h.assertTrue(be.screenFe() == 1 && be.screenElectricity() == 4000, "Four surplus units convert to exactly one FE");
        var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored = new ElectricityConverterBlockEntity(be.getType(), be.getBlockPos(), be.getBlockState(), be.settings);
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(restored.screenFe() == 1 && restored.screenElectricity() == 4000, "Both buffers must persist in their own units");
        var old = be.electricityPort();
        h.setBlock(new BlockPos(4,3,5), Blocks.AIR);
        h.assertTrue(old.extractEnergy(4000, false) == 0 && old.receiveEnergy(4, false) == 0, "Removed converter must reject cached handler calls");
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void neighboringFeSourceCanSupplyAnElectricityConsumer(GameTestHelper h) {
        var be = place(h);
        h.setBlock(new BlockPos(3,3,5), Blocks.COAL_BLOCK);
        h.runAfterDelay(20, () -> {
            h.assertTrue(be.screenElectricity() == 4000, "Neighboring FE source must charge the electricity reserve");
            h.assertTrue(be.electricityPort().extractEnergy(1000, false) == 1000, "Electricity consumer must be able to draw power");
        });
        h.runAfterDelay(40, () -> {
            var source = sources.get(h.absolutePos(new BlockPos(3,3,5)));
            long total = source.getEnergyStored() + be.screenFe() + be.screenElectricity()/4 + 250;
            h.assertTrue(be.screenElectricity() == 4000 && total == 50000, "Source + converter + consumed output must conserve FE-equivalent energy");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void electricityInputFeedsANeighboringFeConsumer(GameTestHelper h) {
        var be = place(h);
        h.setBlock(new BlockPos(5,3,5), Blocks.REDSTONE_BLOCK);
        be.electricityPort().receiveEnergy(8000, false);
        h.runAfterDelay(20, () -> {
            var sink = sinks.get(h.absolutePos(new BlockPos(5,3,5)));
            h.assertTrue(sink != null && sink.getEnergyStored() > 0, "Electricity must be convertible into delivered FE");
            h.assertTrue(sink.getEnergyStored() + be.screenFe() + be.screenElectricity()/4 == 2000, "Reverse conversion must conserve FE-equivalent energy");
            h.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void customRatioAndRateAreRespected(GameTestHelper h) {
        var registered = place(h);
        var be = new ElectricityConverterBlockEntity(registered.getType(), registered.getBlockPos(), registered.getBlockState(),
            new ConverterSettings(1000, 8000, 3, 7, 6));
        be.setLevel(h.getLevel());
        be.fePort().receiveEnergy(6, false);
        tick(h, be);
        h.assertTrue(be.screenFe() == 0 && be.screenElectricity() == 14, "Data-defined ratio must execute at most two complete batches");
        h.assertTrue(be.fePort().receiveEnergy(-1, false) == 0 && be.electricityPort().extractEnergy(-1, false) == 0, "Negative requests must do no work");
        h.succeed();
    }
}
