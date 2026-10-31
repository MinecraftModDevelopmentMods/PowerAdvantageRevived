package zone.moddev.mc.poweradvantage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.poweradvantage.init.*;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class OptionalPackGameTests {
    @GameTest(template = "empty")
    public static void rubberPackIsAvailableButNotForced(GameTestHelper h) {
        var repository = h.getLevel().getServer().getPackRepository();
        var pack = repository.getPack(OptionalPacks.RUBBER_ID);
        h.assertTrue(pack != null, "Built-in rubber pack must be discoverable");
        h.assertTrue(!pack.isRequired() && !pack.getPackSource().shouldAddAutomatically(), "Rubber equivalence must remain opt-in");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "rubber_reload", timeoutTicks = 400)
    public static void enablingAndDisablingRubberPackUpdatesTags(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var original = List.copyOf(server.getPackRepository().getSelectedIds());
        // The GameTest server enables every discovered pack, including optional ones.
        var baseline = new ArrayList<>(original);
        baseline.remove(OptionalPacks.RUBBER_ID);
        var enabled = new ArrayList<>(baseline);
        enabled.add(OptionalPacks.RUBBER_ID);
        var rubber = TagKey.create(Registries.ITEM, net.minecraft.resources.ResourceLocation.parse("c:rubbers"));
        var ingots = TagKey.create(Registries.ITEM, net.minecraft.resources.ResourceLocation.parse("c:ingots/rubber"));
        var stack = new net.minecraft.world.item.ItemStack(ModItems.BIOPLASTIC_INGOT.get());
        var reload = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>>();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        h.startSequence()
            .thenExecute(() -> reload.set(server.reloadResources(baseline)))
            .thenWaitUntil(() -> h.assertTrue(reload.get().isDone(), "Waiting for baseline pack selection"))
            .thenExecute(() -> {
                reload.get().join();
                h.assertTrue(!stack.is(rubber) && !stack.is(ingots), "Bioplastic must not be rubber with the optional pack disabled");
                reload.set(server.reloadResources(enabled));
            })
            .thenWaitUntil(() -> h.assertTrue(reload.get().isDone(), "Waiting for enabled pack reload"))
            .thenExecute(() -> {
                try {
                    reload.get().join();
                    h.assertTrue(stack.is(rubber) && stack.is(ingots), "Enabling pack must add both common rubber tags");
                } catch (Throwable error) { failure.set(error); }
                finally { reload.set(server.reloadResources(baseline)); }
            })
            .thenWaitUntil(() -> h.assertTrue(reload.get().isDone(), "Waiting for original pack selection to reload"))
            .thenExecute(() -> {
                reload.get().join();
                h.assertTrue(failure.get() == null, "Enabled-pack verification failed: " + failure.get());
                h.assertTrue(!stack.is(rubber) && !stack.is(ingots), "Disabling pack must remove its rubber equivalence");
                reload.set(server.reloadResources(original));
            })
            .thenWaitUntil(() -> h.assertTrue(reload.get().isDone(), "Restoring initial server pack selection"))
            .thenExecute(() -> reload.get().join()).thenSucceed();
    }
}
