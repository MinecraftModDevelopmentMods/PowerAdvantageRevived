# Fluid network implementation plan

Implement the fluid-pipe foundation first. Conveyor transport, visible in-flight
items, filters, crossover channels, and dropping conveyors are later work; their
routing semantics must not be inferred from artwork alone.

1. Register ordinary fluid pipe and extraction-terminal blocks through MMDLib's
   standalone definitions. Neither owns a block entity. Six connection properties
   drive the existing models and matching collision/selection shapes.
2. Own topology in a dimension-level manager. Discover pipes when chunks load,
   defer chunk inspection until tick, rebuild affected connected components after
   placement/removal/neighbor changes, and never load chunks for routing.
3. Compress chains of degree-two pipes into weighted graph edges. Keep junctions,
   endpoints, terminals and an anchor for closed loops. Cache shortest destination
   lists at ingress vertices; invalidate the entire affected component on changes.
4. Offer fill-only virtual fluid capabilities on pipes. Route pushed fluid directly
   to accepting endpoint handlers, exclude the injecting block and all extraction
   sources, and prevent recursive routing. An explicitly oriented extraction
   terminal pulls from its selected neighbor. Ordinary tanks never automatically
   become sources. Recheck acceptance on every transfer; never blacklist a full tank.
5. Bound discovery/rebuild work and transfer attempts per dimension tick. Share a
   allowance equal to the slowest pipe rating across each connected network, rotating service among
   ingress points, terminals and destinations. Topology rebuilds suspend affected
   transfers. Preserve unexpected extraction remainders in dimension SavedData.
6. Test actual registration, no block entities, connection state, compression,
   conservation, simulation, endpoint replacement, splitting/merging, loops,
   terminal direction, fairness and loaded-chunk boundaries. Run the original
   machine regression suite alongside these tests and build the distributable JAR.

The fluid implementation and its validation are recorded below.

## Implemented behavior

`fluid_pipe` and `fluid_pipe_terminal` are MMDLib standalone blocks. Each has six
connection booleans, and their shapes match the existing eight-pixel-wide pipe
models. The source-facing property is ordinary persisted block state, so neither
block needs a block entity or per-block ticker.

An extraction terminal initially extracts from the block it was placed against.
Right-click a face of the terminal to select that neighboring source; the action
bar reports the selected direction. Attach ordinary pipes to transport the fluid
to neighboring fluid handlers. A machine that already exports fluid, such as the
steam source or drain, can push directly into ordinary pipes without a terminal.
Plain tanks connected to pipes do not become extraction sources automatically.
There are no new crafting recipes in this implementation; both blocks appear in
the Power Advantage creative tab.

Pipes expose a fill-only virtual fluid handler: one empty virtual tank with a
capacity equal to that block's transfer rating, representing transport throughput. They do not contain fluid,
and draining that interface returns empty. This prevents neighboring pullers from
silently draining arbitrary tanks behind a network. To feed a pull-only consumer,
use a terminal at the source; the network inserts into the consumer's fluid handler.
A network with no accepting destination refuses the offered fluid.

`FluidNetworks` delegates discovery and component lifecycle to the shared
`TransportNetworks` manager; `RoutingGraph` compresses physical connections.
`FluidTransportAdapter` supplies fluid-specific topology and endpoint policies.
See [the shared transport API](transport-core.md). Endpoint capability caches include sided access and null
results at occupied boundaries. NeoForge capability invalidation rebuilds affected
components when capabilities appear/disappear or providers are replaced. Contents
and acceptance are simulated live, rather than cached as permanent resource rules.
Current injection sources and every explicitly configured extraction source are
excluded from destination selection. Endpoint positions/faces are treated as
capability identities; multi-block providers must correctly expose their handlers
and invalidate capabilities when changing configuration.

A connected network shares **the minimum transfer rating of all its pipes per server
tick**, regardless of how many pipes or callers it has. Built-in pipes and terminals
default to 1,000 mB/tick. A slow side branch also constrains the entire component;
this is a component-wide budget, not independent per-route or per-edge throughput. Recently active ingress points receive rotating turns;
equal accepting destinations and extraction terminals also receive rotating
service. Demand expires after 40 ticks, so a recently idle input can briefly retain
a turn. This favors fair service over maximum instantaneous utilization. A new
source may wait until the next turn. There is no fluid travel time or pipe volume;
transfers are direct and completed synchronously at their endpoints.

Per-dimension tick budgets are 512 topology/compression steps, 512 shortest-path
steps (up to 32 per network per pass), 256 simulated destination attempts, and 256
executed/source attempts. At most one matching chunk section (4,096 positions) is
scanned per tick, after cheap palette checks. Route caches and outstanding route
jobs each hold at most 32 ingress routes per network. A cold route refuses transfer
until budgeted Dijkstra computation finishes. Straight-chain interior entries now retain exact reachable paths through the
compressed graph, including the correct direction on one-way transport.
Fair destination rotation takes precedence over strict nearest-first delivery.

The work budgets limit world queries and graph work; they are not a measured
millisecond guarantee. Component invalidation, collection bookkeeping, path/list
allocation, endpoint scheduling and capability implementations still have costs
that scale with their data. This has not been benchmarked on a large modpack.

Chunk unloads suspend affected components; no routing operation requests a chunk
load. Topology is derived again from loaded blocks after restart. Chunk event
inspection waits until the exact chunk instance is available from `getChunkNow`,
avoiding access before FULL promotion. Dimension unload releases the manager.

Routine transfers do not use network storage. If a source has actually been
extracted and the receiver unexpectedly refuses execution, `FluidRecovery` keeps
the remainder in dimension SavedData and retries delivery or return to the source.
The record persists even if the pipe/source is removed or unloaded. Reconnecting
that position or restoring a source that accepts its return permits recovery.
This safeguard covers terminal-owned extraction; external exporters remain
responsible for honoring the actual amount returned by `fill`.

## Verification

```sh
python3 scripts/validate_pipe_models.py
./gradlew -PgameTests runGameTestServer build --offline
```

The resource validator checks both sets of 64 variants against rotated geometry,
including every model/texture reference and duplicate JSON keys. GameTests cover
compression, no block entities, all six connections, simulation and shared budgets,
split/merge and stale capabilities, passive tanks, explicit extraction, destination
and competing-source fairness, live acceptance, endpoint replacement, loops,
capability appearance, persisted exceptional remainders, and chunk-boundary
suspension/reconnection, alongside the five original machine tests.

The chunk-boundary test feeds the actual chunk-event queues while keeping the
GameTest arena loaded; it is not a full physical chunk-unload/restart stress test.
Client appearance, manual placement/direction selection and real third-party pipes
still need interactive validation.


## Defining a pipe in a downstream mod

The Java constructor accepts a positive `int` in **millibuckets per server tick**.
The two-argument constructor retains the 1,000 mB/tick default. Ratings are immutable
for a registered block; read a config value during block creation if desired.

```java
public final class LargeFluidPipe extends FluidPipeBlock {
    public LargeFluidPipe(BlockBehaviour.Properties properties) {
        super(properties, false, 2_048_000);
    }
}

// Or instantiate directly; true selects an extraction terminal with the same rating:
var largeTerminal = new FluidPipeBlock(properties, true, 2_048_000);

// In your mod-bus RegisterCapabilitiesEvent listener, after block registration:
FluidPipeBlock.registerCapabilities(event, LARGE_PIPE.get(), LARGE_TERMINAL.get());
```

Imports are `zone.moddev.mc.poweradvantage.blocks.FluidPipeBlock` and
`net.minecraft.world.level.block.state.BlockBehaviour`. Register blocks/items and
supply assets using the consuming mod's normal NeoForge or MMDLib registration.
The network discovers every `FluidPipeBlock` subclass, including ones registered
under another mod namespace. No Power Advantage registry list needs updating.

`getTransferRate()` returns the block's rating. A network made entirely of
2,048,000 mB/tick pipes and terminals can transfer that amount per tick, subject to
source/destination capability limits and scheduling. Connecting a built-in pipe or
terminal reduces the shared component limit to 1,000 mB/tick; replace every
bottleneck, including the terminal, to restore the higher rating. Rates from 1 to
`Integer.MAX_VALUE` are accepted, matching NeoForge's fluid handler amount range.
Zero and negative ratings fail construction. Fluid volume changes do not increase
the number of graph steps or capability attempts allowed per tick.
