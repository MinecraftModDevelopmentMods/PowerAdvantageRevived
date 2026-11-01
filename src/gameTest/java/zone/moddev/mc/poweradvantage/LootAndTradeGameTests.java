package zone.moddev.mc.poweradvantage;

import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import zone.moddev.mc.poweradvantage.init.*;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class LootAndTradeGameTests {
    @GameTest(template="empty")
    public static void dungeonChestsCanContainPowerAdvantageItems(GameTestHelper h) {
        var level=h.getLevel();
        var table=level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.withDefaultNamespace("chests/simple_dungeon")));
        var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(1,2,1)))).create(LootContextParamSets.CHEST);
        int found=0;
        for(int i=0;i<200;i++)for(var stack:table.getRandomItems(params))
            if(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(PowerAdvantage.MODID))found++;
        h.assertTrue(found>0,"200 dungeon chests must yield at least one Power Advantage item");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void noviceToolsmithsAndFarmersSellPowerAdvantageItems(GameTestHelper h) {
        h.assertTrue(VillagerTrades.TRADES.get(VillagerProfession.TOOLSMITH).get(1)!=null
            &&java.util.List.of(VillagerTrades.TRADES.get(VillagerProfession.TOOLSMITH).get(1)).contains(Trades.SPROCKETS),"Novice toolsmiths must sell sprockets");
        h.assertTrue(java.util.List.of(VillagerTrades.TRADES.get(VillagerProfession.FARMER).get(1)).contains(Trades.BIOPLASTIC),"Novice farmers must sell bioplastic");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void someNewWanderingTradersCarryOneOffer(GameTestHelper h) {
        int with=0,total=60;
        for(int i=0;i<total;i++) {
            var trader=h.spawn(EntityType.WANDERING_TRADER,1,2,1);
            long ours=trader.getOffers().stream().filter(o->o.getResult().is(ModItems.SPROCKET.get())||o.getResult().is(ModItems.BIOPLASTIC_INGOT.get())).count();
            h.assertTrue(ours<=1,"A wandering trader must carry at most one Power Advantage offer");
            if(ours==1)with++;
            trader.discard();
        }
        h.assertTrue(with>0&&with<total,"Wandering-trader offers must be random, got "+with+"/"+total);
        h.succeed();
    }
}
