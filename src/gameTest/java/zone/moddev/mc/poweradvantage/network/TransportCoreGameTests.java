package zone.moddev.mc.poweradvantage.network;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandler;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.network.transport.*;

@GameTestHolder(PowerAdvantage.MODID)
@PrefixGameTestTemplate(false)
public final class TransportCoreGameTests {
    private static <N,E> RoutingGraph<N,E> build(Map<N,List<N>> weak,Map<N,List<N>> out,Map<N,List<E>> endpoints) {
        var builder=new RoutingGraph.Builder<>(weak,out,endpoints,Set.of());
        for(int i=0;i<10000;i++)if(builder.advance())return builder.finish();
        throw new AssertionError("Compression did not finish");
    }
    private static <N,E> List<RoutingGraph.Route<N,E>> routes(RoutingGraph<N,E> graph,N start) {
        graph.routes(start);
        for(int i=0;i<1000;i++)graph.advanceRoutes(1);
        return graph.routes(start);
    }
    @GameTest(template="empty")
    public static void directedCompressionKeepsExactInteriorPath(GameTestHelper h) {
        var graph=build(Map.of(0,List.of(1),1,List.of(0,2),2,List.of(1,3),3,List.of(2)),
            Map.of(0,List.of(1),1,List.of(2),2,List.of(3),3,List.of()),Map.of(0,List.of("upstream"),3,List.of("downstream")));
        h.assertTrue(graph.vertexCount()==2,"One-way chain must compress");
        var forward=routes(graph,1);
        h.assertTrue(forward.size()==1 && forward.getFirst().endpoint().equals("downstream"),"Interior route must never travel backward");
        h.assertTrue(forward.getFirst().distance()==2 && forward.getFirst().path().nodes().equals(List.of(1,2,3)),"Exact directed segment path from interior ingress");
        h.assertTrue(routes(graph,3).stream().noneMatch(route->route.endpoint().equals("upstream")),"No reverse path through a one-way chain");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void crossoverChannelsAndDroppingPathStaySeparate(GameTestHelper h) {
        var center=new BlockPos(4,4,4);
        TransportNode west=new TransportNode(center.west()),horizontal=new TransportNode(center,"horizontal"),east=new TransportNode(center.east());
        TransportNode north=new TransportNode(center.north()),vertical=new TransportNode(center,"dropping"),below=new TransportNode(center.below());
        var graph=build(Map.of(west,List.of(horizontal),horizontal,List.of(west,east),east,List.of(horizontal),
            north,List.of(vertical),vertical,List.of(north,below),below,List.of(vertical)),
            Map.of(west,List.of(horizontal),horizontal,List.of(east),east,List.of(),north,List.of(vertical),vertical,List.of(below),below,List.of()),
            Map.of(east,List.of("east inventory"),below,List.of("lower inventory")));
        var horizontalRoutes=routes(graph,west);var droppingRoutes=routes(graph,north);
        h.assertTrue(horizontalRoutes.size()==1 && horizontalRoutes.getFirst().endpoint().equals("east inventory"),"Crossing lanes must not merge");
        h.assertTrue(droppingRoutes.size()==1 && droppingRoutes.getFirst().path().nodes().equals(List.of(north,vertical,below)),"Dropping connection must retain the lower physical segment");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void disconnectedDirectedLoopsTerminateUnderBudget(GameTestHelper h) {
        var weak=Map.of(0,List.of(1,2),1,List.of(0,2),2,List.of(0,1),10,List.of(11,12),11,List.of(10,12),12,List.of(10,11));
        var graph=TransportCoreGameTests.<Integer,String>build(weak,Map.of(0,List.of(1),1,List.of(2),2,List.of(0),10,List.of(11),11,List.of(12),12,List.of(10)),Map.of());
        h.assertTrue(graph.vertexCount()==2,"Every isolated directed cycle needs its own anchor");
        graph.routes(1);h.assertTrue(graph.advanceRoutes(0)==0,"Zero route budget must do no work");
        for(int i=0;i<30;i++)h.assertTrue(graph.advanceRoutes(1)<=1,"Single-step route budget exceeded");
        h.assertTrue(graph.routes(1).isEmpty(),"Endpoint-free loop must not invent a destination");h.succeed();
    }
    @GameTest(template="empty")
    public static void schedulerSeparatesUnitsFromWorkAndSimulation(GameTestHelper h) {
        var budget=new FairTransferBudget<String>(2_048_000);
        h.assertTrue(budget.allowance(0,"first",Integer.MAX_VALUE)==2_048_000,"Custom unit allowance");
        h.assertTrue(budget.allowance(0,"first",Integer.MAX_VALUE)==2_048_000,"Simulation must not consume allowance");
        budget.allowance(0,"second",Integer.MAX_VALUE);budget.consume(2_048_000);
        h.assertTrue(budget.allowance(0,"first",1)==0,"Repeated operations must share a tick allowance");
        h.assertTrue(budget.allowance(1,"second",Integer.MAX_VALUE)==2_048_000,"Second ingress must get its turn");
        var work=new WorkBudget(2);work.begin(0);
        h.assertTrue(work.trySimulation()&&work.trySimulation()&&!work.trySimulation(),"Simulation work limit");
        h.assertTrue(work.tryExecution()&&work.tryExecution()&&!work.tryExecution(),"Execution work limit independent of volume");
        work.begin(1);h.assertTrue(work.tryExecution(),"New tick resets work allowance");h.succeed();
    }
    @GameTest(template="empty")
    public static void sharedWorldLifecycleRoutesItemCapabilities(GameTestHelper h) {
        var level=h.getLevel();var left=h.absolutePos(new BlockPos(3,3,5));var right=left.east(2);
        level.setBlockAndUpdate(left.west(),Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(right.east(),Blocks.BARREL.defaultBlockState());
        for(int i=0;i<3;i++)level.setBlockAndUpdate(left.east(i),Blocks.STONE.defaultBlockState());
        var adapter=new TransportAdapter<CapabilityEndpoint<IItemHandler>>() {
            public boolean matches(BlockState state){return state.is(Blocks.STONE);}
            public boolean isSource(CapabilityEndpoint<IItemHandler> endpoint){return endpoint.source();}
            public NodeData<CapabilityEndpoint<IItemHandler>> inspect(Context context,TransportNode node) {
                var adjacent=new ArrayList<TransportNode>();var endpoints=new ArrayList<CapabilityEndpoint<IItemHandler>>();
                for(var side:Direction.values()) {
                    var pos=node.position().relative(side);if(!context.available(pos))continue;
                    var state=level.getBlockState(pos);
                    if(matches(state))adjacent.add(new TransportNode(pos));
                    else if(state.is(Blocks.BARREL)) {
                        var cache=context.watch(Capabilities.ItemHandler.BLOCK,pos,side.getOpposite());
                        if(cache.getCapability()!=null)endpoints.add(new CapabilityEndpoint<>(node,pos,side.getOpposite(),pos.equals(left.west()),cache));
                    }
                }
                return new NodeData<>(adjacent,adjacent,endpoints,64,false);
            }
        };
        var manager=new TransportNetworks<>(level,adapter);manager.changed(left);
        for(int i=0;i<10;i++)manager.tick();
        var component=manager.component(new TransportNode(left));
        h.assertTrue(component!=null&&component.graph().vertexCount()==2,"Shared manager must discover/compress non-fluid blocks");
        var targets=component.graph().destinations(new TransportNode(left));
        h.assertTrue(targets.size()==1,"Only the item destination should be routable");
        var source=component.sources().getFirst().cache().getCapability();
        h.assertTrue(source.insertItem(0,new ItemStack(Items.IRON_INGOT,8),false).isEmpty(),"Populate source inventory");
        var moved=source.extractItem(0,8,false);
        h.assertTrue(targets.getFirst().cache().getCapability().insertItem(0,moved,false).isEmpty(),"Use routed item capability without fluid types");
        manager.changed(left.east());h.assertTrue(!component.valid(),"Invalidate published routes immediately");
        level.setBlockAndUpdate(left.east(),Blocks.AIR.defaultBlockState());
        for(int i=0;i<10;i++)manager.tick();
        h.assertTrue(manager.component(new TransportNode(left)).graph().destinations(new TransportNode(left)).isEmpty(),"Split item component must lose the destination");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void worldChannelsRemainSeparateAcrossReconfiguration(GameTestHelper h) {
        var center=h.absolutePos(new BlockPos(5,3,5));
        var west=new TransportNode(center.west());var east=new TransportNode(center.east());
        var north=new TransportNode(center.north());var south=new TransportNode(center.south());
        var horizontal=new TransportNode(center,"horizontal");var vertical=new TransportNode(center,"vertical");
        var weak=new LinkedHashMap<TransportNode,List<TransportNode>>();
        weak.put(west,List.of(horizontal));weak.put(horizontal,List.of(west,east));weak.put(east,List.of(horizontal));
        weak.put(north,List.of(vertical));weak.put(vertical,List.of(north,south));weak.put(south,List.of(vertical));
        var outgoing=new HashMap<TransportNode,List<TransportNode>>();
        outgoing.put(west,List.of(horizontal));outgoing.put(horizontal,List.of(east));outgoing.put(east,List.of());
        outgoing.put(north,List.of(vertical));outgoing.put(vertical,List.of(south));outgoing.put(south,List.of());
        for(var node:weak.keySet())h.getLevel().setBlockAndUpdate(node.position(),Blocks.STONE.defaultBlockState());
        var adapter=new TransportAdapter<String>() {
            public boolean matches(BlockState state){return state.is(Blocks.STONE);}
            public List<TransportNode> nodes(BlockPos pos,BlockState state){return weak.keySet().stream().filter(node->node.position().equals(pos)).toList();}
            public NodeData<String> inspect(Context context,TransportNode node) {
                List<String> endpoints=node.equals(east)?List.of("east"):node.equals(south)?List.of("south"):List.of();
                return new NodeData<>(weak.get(node),outgoing.get(node),endpoints,64,false);
            }
        };
        var manager=new TransportNetworks<>(h.getLevel(),adapter);manager.changed(center);
        for(int i=0;i<20;i++)manager.tick();
        var first=manager.component(west);var second=manager.component(north);
        h.assertTrue(first!=null&&second!=null&&first!=second,"Same-position channels must form independent world components");
        first.graph().routes(west);second.graph().routes(north);
        for(int i=0;i<20;i++)manager.tick();
        h.assertTrue(first.graph().destinations(west).equals(List.of("east"))&&second.graph().destinations(north).equals(List.of("south")),"World adapter must preserve directed crossover isolation");
        long version=second.version();
        weak.remove(horizontal);outgoing.remove(horizontal);weak.put(west,List.of());weak.put(east,List.of());outgoing.put(west,List.of());
        manager.changed(center);
        h.assertTrue(!first.valid()&&!second.valid(),"Reconfiguration must invalidate old channel routes");
        for(int i=0;i<20;i++)manager.tick();
        h.assertTrue(manager.component(horizontal)==null,"Retired channel must disappear");
        var restored=manager.component(north);restored.graph().routes(north);
        for(int i=0;i<20;i++)manager.tick();
        h.assertTrue(restored.version()>version&&restored.graph().destinations(north).equals(List.of("south")),"Remaining channel must rebuild without leaking into the retired lane");
        h.succeed();
    }

}
