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
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.poweradvantage.init.ModContent;
import zone.moddev.mc.poweradvantage.machine.ElectricityExchange;
import zone.moddev.mc.poweradvantage.power.*;

/**
 * The Electricity-RF Transformer as an MMDLib XML machine with the {@link ElectricityExchange} processor.
 */
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
    private static DefinedMachineEntity place(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(ModContent.id("converter_rf_electricity")));
        var be = h.getLevel().getBlockEntity(h.absolutePos(pos));
        h.assertTrue(be instanceof DefinedMachineEntity, "The converter must be an MMDLib XML machine");
        return (DefinedMachineEntity) be;
    }
    private static DefinedMachineEntity place(GameTestHelper h) { return place(h, new BlockPos(4,3,5)); }
    private static ElectricityExchange exchange(DefinedMachineEntity be) {
        return be.features.findProcessor("exchange", ElectricityExchange.class).orElseThrow();
    }
    private static long fe(DefinedMachineEntity be) { return be.energy.stored(); }
    private static long electricity(DefinedMachineEntity be) { return exchange(be).stored(); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registrationCapabilitiesXmlAndExactExchange(GameTestHelper h) {
        var be = place(h);
        for (Direction side : Direction.values()) {
            h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), side) != null, "FE capability on " + side);
            h.assertTrue(h.getLevel().getCapability(PowerCapabilities.ELECTRICITY, be.getBlockPos(), side) == exchange(be).port(), "Electricity capability on " + side);
        }
        var menu = new zone.moddev.mc.lib.gui.runtime.MachineMenu(1,
            h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL).getInventory(), be);
        h.assertTrue(menu.getType() == zone.moddev.mc.lib.gui.runtime.ScreenRuntime.MENUS.get(), "MMDLib machine menu");
        var fePort = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), Direction.UP);
        h.assertTrue(fePort.receiveEnergy(1000, true) == 1000 && fe(be) == 0, "FE simulation must be read only");
        h.assertTrue(fePort.receiveEnergy(1000, false) == 1000, "FE admission");
        h.runAfterDelay(3, () -> {
            h.assertTrue(fe(be) == 0 && electricity(be) == 4000, "One FE must convert to four electricity units");
            var port = exchange(be).port();
            h.assertTrue(port.extractEnergy(1000, true) == 1000 && electricity(be) == 4000, "Electricity simulation must be read only");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void reverseExchangeRetainsFractionalBatchesAndSavesBothUnits(GameTestHelper h) {
        var be = place(h);
        exchange(be).port().receiveEnergy(4003, false);
        h.runAfterDelay(3, () -> {
            h.assertTrue(fe(be) == 0 && electricity(be) == 4003, "Three surplus electricity units must not round into FE");
            exchange(be).port().receiveEnergy(1, false);
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(fe(be) == 1 && electricity(be) == 4000, "Four surplus units convert to exactly one FE");
            var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
            var restored = place(h, new BlockPos(6,3,5));
            restored.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(fe(restored) == 1 && electricity(restored) == 4000, "Both buffers must persist in their own units");
            var old = exchange(be).port();
            h.setBlock(new BlockPos(4,3,5), Blocks.AIR);
            h.assertTrue(old.extractEnergy(4000, false) == 0 && old.receiveEnergy(4, false) == 0, "Removed converter must reject cached handler calls");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void neighboringFeSourceCanSupplyAnElectricityConsumer(GameTestHelper h) {
        var be = place(h);
        h.setBlock(new BlockPos(3,3,5), Blocks.COAL_BLOCK);
        h.runAfterDelay(20, () -> {
            h.assertTrue(electricity(be) == 4000, "Neighboring FE source must charge the electricity reserve");
            h.assertTrue(exchange(be).port().extractEnergy(1000, false) == 1000, "Electricity consumer must be able to draw power");
        });
        h.runAfterDelay(40, () -> {
            var source = sources.get(h.absolutePos(new BlockPos(3,3,5)));
            long total = source.getEnergyStored() + fe(be) + electricity(be)/4 + 250;
            h.assertTrue(electricity(be) == 4000 && total == 50000, "Source + converter + consumed output must conserve FE-equivalent energy");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void electricityInputFeedsANeighboringFeConsumer(GameTestHelper h) {
        var be = place(h);
        h.setBlock(new BlockPos(5,3,5), Blocks.REDSTONE_BLOCK);
        exchange(be).port().receiveEnergy(8000, false);
        h.runAfterDelay(20, () -> {
            var sink = sinks.get(h.absolutePos(new BlockPos(5,3,5)));
            h.assertTrue(sink != null && sink.getEnergyStored() > 0, "Electricity must be convertible into delivered FE");
            h.assertTrue(sink.getEnergyStored() + fe(be) + electricity(be)/4 == 2000, "Reverse conversion must conserve FE-equivalent energy");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void customRatioAndRateAreRespected(GameTestHelper h) {
        var fe = new EnergyBuffer(1000, 1000);
        fe.insert(6, false);
        long electricity = ElectricityExchange.exchange(fe, 0, new ConverterSettings(1000, 8000, 3, 7, 6));
        h.assertTrue(fe.stored() == 0 && electricity == 14, "A custom ratio must execute at most two complete batches");
        var port = exchange(place(h)).port();
        h.assertTrue(port.receiveEnergy(-1, false) == 0 && port.extractEnergy(-1, false) == 0, "Negative requests must do no work");
        h.succeed();
    }
}
