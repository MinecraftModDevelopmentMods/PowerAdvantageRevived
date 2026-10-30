# Shared transport core

The fluid implementation now uses `network.transport`. The same core can discover
and route item-conveyor networks without depending on `FluidPipeBlock`,
`FluidStack`, `IFluidHandler`, or fluid recovery storage.

## Responsibilities

| Type | Responsibility |
| --- | --- |
| `TransportNode` | Immutable block position plus channel ID; lanes at a crossover have separate identities |
| `TransportAdapter<E>` | Supplies transport blocks/channels, weak neighbors, outgoing links, endpoints and ratings |
| `CapabilityEndpoint<H>` | A sided endpoint and invalidation-aware cache, parameterized by handler type |
| `TransportNetworks<E>` | Loaded-chunk discovery, component invalidation, budgeted rebuilds, source lists and topology generations |
| `RoutingGraph<N,E>` | Resource-independent graph compression, bounded Dijkstra jobs, cached destinations and exact physical paths |
| `FairTransferBudget<K>` | Shared per-tick quantity allowance, rotating input turns and destination selection |
| `WorkBudget` | Separate simulation/execution operation limits |
| `FairScheduler` | Rotating service order under a caller-supplied work budget |

`FluidTransportAdapter` describes pipe adjacency and watches fluid capabilities.
`FluidNetworks` owns fluid simulation/execution and reentrancy protection. Its public
facade, block constructor/rating API, shared minimum-rating behavior and
`poweradvantage_fluid_recovery` SavedData format are retained. No world migration
is required. The old pipe-only graph has been removed.

## Implementing another transport kind

Create one manager per server dimension and transport kind:

```java
TransportNetworks<CapabilityEndpoint<IItemHandler>> networks =
    new TransportNetworks<>(level, conveyorAdapter);
```

Here `conveyorAdapter` implements `TransportAdapter<CapabilityEndpoint<IItemHandler>>`.
It supplies these policies:

- `matches(state)` recognizes its blocks. `nodes(pos, state)` returns all channels
  at that position; the default returns a single `main` channel.
- `inspect(context, node)` returns `NodeData`: weak neighbors, legal outgoing links,
  endpoints, positive transfer rating and whether to retain this node as a vertex.
- `context.available(pos)` guards every world access and capability lookup.
  `context.watch(Capabilities.ItemHandler.BLOCK, pos, face)` retains a capability
  cache and invalidates the component when the capability changes. Watch occupied
  boundary blocks even if the first lookup is null, to discover capabilities that
  appear later. Do not query a cache from its invalidation callback.
- `isSource(endpoint)` and `destinationPolicy(sources)` select static endpoint roles.
  Resource contents/space/filter acceptance must still be checked when transferring.
- `affectedPositions(pos)` defaults to the position and six neighbors. Extend it
  for any diagonal/non-adjacent links, such as a belt reaching forward and down.
- `refresh(level, node)` may update visual connection state, but must not mutate
  topology while discovery is inspecting it.

Weak adjacency must be reciprocal: it groups physically connected channels into
components regardless of travel direction. Outgoing adjacency is directed and
must be a subset of the weak neighbors. For `A -> B`, both nodes list each other
as weak neighbors, while only A lists B as outgoing. A crossover lists only the
neighbors of its particular channel, never the other lane at the same position.

Mark filters and routing-policy boundaries with `retainVertex=true`. Compression
retains endpoints, junctions, directed discontinuities and explicit boundaries;
bidirectional and consistently one-way chains are compressed. Every closed cycle
gets an anchor, including multiple disconnected cycles supplied to a standalone
graph builder.

Forward block placement/removal/topology configuration changes to `changed(pos)`.
Forward neighbor notifications to `neighbourChanged(pos, from)`; unchanged content
notifications are ignored, while capability invalidation handles provider changes.
Forward chunk load/unload events to `chunkLoaded`/`chunkUnloaded`, then call `tick()`
on the server thread. Load-event inspection is deferred until the exact chunk is
available from `getChunkNow`. Drop the manager on dimension unload. The existing
`NetworkEvents` demonstrates this event forwarding through the fluid facade; a
conveyor facade should have its own instance registry and forwarding.

## Obtaining routes

```java
var node = new TransportNode(pos, "horizontal");
var component = networks.component(node);
if (component != null && networks.canRoute()) {
    var routes = component.graph().routes(node);
    // Empty can mean the cold query is still being computed, or no endpoint is reachable.
    for (var route : routes) {
        var endpoint = route.endpoint();
        long distance = route.distance();
        var physicalSteps = route.path().nodes();
        // Check resource-specific eligibility before selecting and scheduling this route.
    }
}
```

`destinations(node)` returns endpoints alone for direct transfers. `routes(node)`
returns endpoints, distances and persistent path segments. Exact paths start at
the requested node even when it lies inside a compressed chain. Materialize the
physical node list only when a traveler actually needs it. A directed path never
walks backward to a convenient chain anchor.

A component exposes `valid()` and a process-local monotonic `version()`. Keep
in-flight contents separately; invalidate/recompute their movement plans when the
component changes. Versions are not persistent network IDs and must not be relied
on across restart. Published route snapshots are topological hints: the caller
must check component validity, loaded positions, handler availability and current
acceptance before executing work.

Cached routes currently describe the shortest topological path per endpoint.
They do not solve resource-dependent alternative paths through filters. Conveyor
filter routing will need a policy-aware search/cache key or alternate-path search;
checking a rejected shortest path alone is insufficient if another legal route
exists. Directed/crossover topology is implemented now; filter policy is not.

## Budgets and ownership

Default manager budgets remain 512 topology/compression steps and 512 shortest-path
steps per tick, at most 32 route steps per component pass. Route caches and pending
jobs each hold at most 32 ingress queries. These settings can be supplied to
`tick(topologySteps, routeSteps)`. Chunk scans and endpoint scheduling retain the
limits described in [fluid networks](fluid-networks.md).

`component.transferRate()` is the minimum rating across its nodes. Its optional
`budget()` uses that quantity for the fluid-style shared component allowance.
A conveyor engine can reuse the fair scheduling/work counters while implementing
per-segment occupancy and movement instead; it need not treat all belts as one
volume buffer or consume the component-wide quantity allowance.

The core never extracts resources, calls `fill`, inserts items, saves transported
stacks, renders them or creates entities. The standard conveyor engine now owns
its records, backpressure, pickup, belt removal, persistence and client snapshots
in the separate `conveyor` package. [Conveyor documentation](conveyor-networks.md)
records those policies and the downstream extension API.

## Validation

```sh
./gradlew -PgameTests runGameTestServer build --offline
```

The original sixteen tests exercise fluid behavior through the new core. Six
additional tests cover exact one-way interior paths, isolated crossover lanes and
a dropping path, multiple directed loops, work/quantity scheduling, real item
capabilities through a non-fluid world adapter, and same-position channel
reconfiguration with stale-route invalidation. These are routing/lifecycle tests,
not proof of an implemented conveyor simulation or client renderer.
