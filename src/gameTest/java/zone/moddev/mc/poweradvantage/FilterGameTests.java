package zone.moddev.mc.poweradvantage;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.*;
import zone.moddev.mc.poweradvantage.blocks.ConveyorBlock;
import zone.moddev.mc.poweradvantage.filter.*;
import zone.moddev.mc.poweradvantage.init.ModContent;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class FilterGameTests {
    private static FilterBlockEntity filter(GameTestHelper h, String kind, BlockPos pos) {
        h.setBlock(pos, BuiltInRegistries.BLOCK.get(ModContent.id("item_filter_" + kind)).defaultBlockState().setValue(FilterBlock.FACING, Direction.EAST));
        var be = h.getLevel().getBlockEntity(h.absolutePos(pos));
        h.assertTrue(be instanceof FilterBlockEntity, "Missing filter entity " + kind);
        return (FilterBlockEntity) be;
    }
    private static IItemHandler barrel(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, Blocks.BARREL);
        return h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos), Direction.UP);
    }
    private static int count(IItemHandler handler, Item item) {
        int result = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) if (handler.getStackInSlot(slot).is(item)) result += handler.getStackInSlot(slot).getCount();
        return result;
    }
    @GameTest(template = "empty")
    public static void registrationPredicatesSamplesAndPersistence(GameTestHelper h) {
        String[] kinds = {"block", "food", "fuel", "inventory", "ore", "overflow", "plant", "smelt"};
        for (int i = 0; i < kinds.length; i++) {
            var be = filter(h, kinds[i], new BlockPos(i + 2, 3, 3));
            h.assertTrue(zone.moddev.mc.lib.gui.Screens.get(ModContent.id("item_filter_" + kinds[i])).isPresent(), "Missing bound XML screen");
            var menu = new zone.moddev.mc.lib.gui.runtime.MachineMenu(1,
                h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL).getInventory(), be);
            // One cargo slot, nine samples for the inventory filter, and MMDLib's player slots:
            // 36 inventory, 4 armour and the offhand.
            int expected = (kinds[i].equals("inventory") ? 10 : 1) + 36 + 4 + 1;
            h.assertTrue(menu.slots.size() == expected, "MMDLib menu must contain cargo, samples and player inventory: expected "
                + expected + " slots, got " + menu.slots.size() + " for " + kinds[i]);
            h.assertTrue(be.screenItems().getSlots() == (kinds[i].equals("inventory") ? 10 : 1), "Screen slot count");
            h.assertTrue(h.getLevel().getRecipeManager().byKey(ModContent.id("item_filter_" + kinds[i])).isPresent(), "Crafting recipe missing");
        }
        var level = h.getLevel();
        h.assertTrue(FilterPredicates.matches(FilterSettings.Kind.BLOCK, new ItemStack(Items.STONE), level), "Block predicate");
        h.assertTrue(!FilterPredicates.matches(FilterSettings.Kind.BLOCK, new ItemStack(Items.STICK), level), "Non-block predicate");
        h.assertTrue(FilterPredicates.matches(FilterSettings.Kind.FOOD, new ItemStack(Items.BREAD), level), "Food component");
        h.assertTrue(FilterPredicates.matches(FilterSettings.Kind.FUEL, new ItemStack(Items.COAL), level), "Fuel burn time");
        h.assertTrue(FilterPredicates.matches(FilterSettings.Kind.ORE, new ItemStack(Items.IRON_INGOT), level), "Common ore tags");
        h.assertTrue(FilterPredicates.matches(FilterSettings.Kind.PLANT, new ItemStack(Items.WHEAT_SEEDS), level), "Plantable seed item");
        h.assertTrue(FilterPredicates.matches(FilterSettings.Kind.SMELT, new ItemStack(Items.RAW_IRON), level), "Live smelting recipes");
        var be = (FilterBlockEntity) level.getBlockEntity(h.absolutePos(new BlockPos(5, 3, 3)));
        be.screenItems().setStackInSlot(1, new ItemStack(Items.DIAMOND));
        be.screenItems().setStackInSlot(0, new ItemStack(Items.DIAMOND, 12));
        h.assertTrue(be.matches(new ItemStack(Items.DIAMOND)) && !be.matches(new ItemStack(Items.EMERALD)), "Sample matching");
        h.assertTrue(be.signal() == 15, "Matching comparator signal");
        var restored = new FilterBlockEntity(be.getType(), be.getBlockPos(), be.getBlockState(), be.settings);
        restored.loadWithComponents(be.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
        h.assertTrue(restored.screenItems().getStackInSlot(0).getCount() == 12 && restored.screenItems().getStackInSlot(1).is(Items.DIAMOND), "Save round trip preserves cargo and real samples");
        for (Direction side : Direction.values()) h.assertTrue(be.automation(side).getSlots() == 1, "Automation must never expose samples");
        h.assertTrue(be.automation(Direction.UP).extractItem(0, 1, false).isEmpty(), "Top cannot extract cargo");
        h.assertTrue(be.automation(Direction.EAST).extractItem(0, 1, false).isEmpty(), "Matching cargo cannot leave forward");
        h.assertTrue(be.automation(Direction.DOWN).extractItem(0, 1, true).getCount() == 1 && be.screenItems().getStackInSlot(0).getCount() == 12, "Extraction simulation must not consume cargo");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void beltsDeliverIntoFilterAndReceiveForwardOutput(GameTestHelper h) {
        var source = barrel(h, new BlockPos(1, 3, 5));
        var below = barrel(h, new BlockPos(4, 2, 5));
        var ahead = barrel(h, new BlockPos(8, 3, 5));
        var filter = filter(h, "block", new BlockPos(4, 3, 5));
        for (int x : new int[]{2,3,5,6,7}) h.setBlock(new BlockPos(x, 3, 5), BuiltInRegistries.BLOCK.get(ModContent.id("item_conveyor"))
            .defaultBlockState().setValue(ConveyorBlock.FACING, Direction.EAST));
        source.insertItem(0, new ItemStack(Items.STONE, 7), false);
        source.insertItem(1, new ItemStack(Items.IRON_INGOT, 8), false);
        h.runAfterDelay(160, () -> {
            h.assertTrue(count(below, Items.STONE) == 7 && count(ahead, Items.IRON_INGOT) == 8, "Physical belts must feed the filter and receive its sorted output");
            h.assertTrue(count(source, Items.STONE) + count(source, Items.IRON_INGOT) == 0 && filter.screenItems().getStackInSlot(0).isEmpty(), "All source cargo must be delivered");
            h.assertTrue(count(below, Items.IRON_INGOT) == 0 && count(ahead, Items.STONE) == 0, "Outputs must obey classification");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void blockedMatchingOutputHoldsAndResumes(GameTestHelper h) {
        var pos = new BlockPos(4, 3, 5);
        var below = barrel(h, pos.below());
        var ahead = barrel(h, pos.east());
        for (int slot = 0; slot < below.getSlots(); slot++) below.insertItem(slot, new ItemStack(Items.STONE, 64), false);
        var filter = filter(h, "block", pos);
        filter.screenItems().setStackInSlot(0, new ItemStack(Items.STONE, 8));
        h.runAfterDelay(20, () -> {
            h.assertTrue(filter.screenItems().getStackInSlot(0).getCount() == 8 && count(ahead, Items.STONE) == 0, "A blocked matching item must not fall through to forward output");
            below.extractItem(0, 64, false);
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(filter.screenItems().getStackInSlot(0).isEmpty() && below.getStackInSlot(0).getCount() == 8, "Opening destination capacity must resume transfer");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void overflowPreservesPa2LowerOutputPreference(GameTestHelper h) {
        var pos = new BlockPos(4, 3, 5);
        var below = barrel(h, pos.below());
        var ahead = barrel(h, pos.east());
        var filter = filter(h, "overflow", pos);
        filter.screenItems().setStackInSlot(0, new ItemStack(Items.STONE, 4));
        h.runAfterDelay(20, () -> {
            h.assertTrue(count(below, Items.STONE) == 4 && count(ahead, Items.STONE) == 0, "PA2 overflow prefers lower output when both accept");
            for (int slot = 0; slot < below.getSlots(); slot++) below.insertItem(slot, new ItemStack(Items.STONE, 64), false);
            filter.screenItems().setStackInSlot(0, new ItemStack(Items.STONE, 4));
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(count(ahead, Items.STONE) == 4 && filter.screenItems().getStackInSlot(0).isEmpty(), "Overflow must use forward output when lower inventory is full");
            h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 70)
    public static void partialInsertionAndBreakPreserveCargoAndSamples(GameTestHelper h) {
        var pos = new BlockPos(4, 3, 5);
        var below = barrel(h, pos.below());
        for (int slot = 0; slot < below.getSlots(); slot++) below.insertItem(slot, new ItemStack(Items.STONE, 64), false);
        below.extractItem(0, 2, false);
        var filter = filter(h, "inventory", pos);
        filter.screenItems().setStackInSlot(1, new ItemStack(Items.STONE));
        filter.screenItems().setStackInSlot(0, new ItemStack(Items.STONE, 8));
        var cached = filter.automation(Direction.DOWN);
        h.runAfterDelay(20, () -> {
            h.assertTrue(filter.screenItems().getStackInSlot(0).getCount() == 6 && below.getStackInSlot(0).getCount() == 64,
                "Partial insertion must retain the exact remainder");
            h.setBlock(pos, Blocks.AIR);
            int dropped = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(2)).stream()
                .filter(e -> e.getItem().is(Items.STONE)).mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(dropped == 7, "Breaking must drop six remaining cargo items and one real sample exactly once");
            h.assertTrue(cached.extractItem(0, 1, false).isEmpty(), "Removed filters must reject stale handler extraction");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void codeConfiguredBatchAndIntervalAreRespected(GameTestHelper h) {
        var pos = new BlockPos(4, 3, 5);
        var below = barrel(h, pos.below());
        var registered = filter(h, "block", pos);
        var be = new FilterBlockEntity(registered.getType(), registered.getBlockPos(), registered.getBlockState(),
            new FilterSettings(FilterSettings.Kind.BLOCK, 3, 2));
        be.setLevel(h.getLevel());
        be.screenItems().setStackInSlot(0, new ItemStack(Items.STONE, 8));
        FilterBlockEntity.serverTick(h.getLevel(), be.getBlockPos(), be.getBlockState(), be);
        h.assertTrue(count(below, Items.STONE) == 2, "First attempt must respect the two-item batch");
        var restored = new FilterBlockEntity(be.getType(), be.getBlockPos(), be.getBlockState(), be.settings);
        restored.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        restored.setLevel(h.getLevel());
        for (int tick = 0; tick < 2; tick++) FilterBlockEntity.serverTick(h.getLevel(), restored.getBlockPos(), restored.getBlockState(), restored);
        h.assertTrue(count(below, Items.STONE) == 2, "Reload must preserve the remaining cooldown");
        FilterBlockEntity.serverTick(h.getLevel(), restored.getBlockPos(), restored.getBlockState(), restored);
        h.assertTrue(count(below, Items.STONE) == 4 && restored.screenItems().getStackInSlot(0).getCount() == 4,
            "Next transfer must wait three calls and conserve cargo");
        h.succeed();
    }

}
