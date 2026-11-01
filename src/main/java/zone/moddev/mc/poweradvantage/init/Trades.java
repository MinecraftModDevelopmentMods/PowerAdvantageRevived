package zone.moddev.mc.poweradvantage.init;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/**
 * Villager and wandering-trader offers restored from Power Advantage 2: novice toolsmiths sell sprockets and novice
 * farmers sell bioplastic, each for one emerald.
 */
@EventBusSubscriber(modid=PowerAdvantage.MODID)
public final class Trades {
    /**
     * Chance that a newly spawned wandering trader carries one Power Advantage offer.
     */
    public static final float WANDERER_CHANCE=2f/3f;
    /**
     * One emerald for one to four sprockets.
     */
    public static final VillagerTrades.ItemListing SPROCKETS=forEmerald(ModItems.SPROCKET::get,1,4);
    /**
     * One emerald for four to eight bioplastic ingots.
     */
    public static final VillagerTrades.ItemListing BIOPLASTIC=forEmerald(ModItems.BIOPLASTIC_INGOT::get,4,8);
    /**
     * Creates the stateless trade subscriber.
     */
    public Trades() {}
    /**
     * Sells a random count of an item for a single emerald, using vanilla novice-trade limits.
     *
     * @param item sold item, resolved when the offer is generated
     * @param min smallest stack sold
     * @param max largest stack sold
     * @return reusable trade listing
     */
    private static VillagerTrades.ItemListing forEmerald(Supplier<Item> item,int min,int max) {
        return (trader,random)->new MerchantOffer(new ItemCost(Items.EMERALD),new ItemStack(item.get(),min+random.nextInt(max-min+1)),12,1,0.05f);
    }
    /**
     * Adds the novice toolsmith and farmer offers.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SubscribeEvent public static void villagers(VillagerTradesEvent event) {
        if(event.getType()==VillagerProfession.TOOLSMITH)event.getTrades().get(1).add(SPROCKETS);
        else if(event.getType()==VillagerProfession.FARMER)event.getTrades().get(1).add(BIOPLASTIC);
    }
    /**
     * Gives a freshly spawned wandering trader one of the offers. The vanilla random pool would rarely pick it, so it
     * is appended directly; traders loaded from disk or already carrying an offer (after changing dimension) are skipped.
     *
     * @param event NeoForge registration or lifecycle event
     */
    @SubscribeEvent public static void wanderer(EntityJoinLevelEvent event) {
        if(event.loadedFromDisk()||!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof WanderingTrader trader))return;
        var random=level.getRandom();if(random.nextFloat()>=WANDERER_CHANCE)return;
        var offers=trader.getOffers();
        if(offers.stream().anyMatch(o->o.getResult().is(ModItems.SPROCKET.get())||o.getResult().is(ModItems.BIOPLASTIC_INGOT.get())))return;
        var offer=(random.nextBoolean()?SPROCKETS:BIOPLASTIC).getOffer(trader,random);
        if(offer!=null)offers.add(offer);
    }
}
