# Standard conveyors

`poweradvantage:item_conveyor` is implemented as an MMDLib standalone block using
the existing conveyor model and textures. Belts have no block entity or block
entity ticker. Carried stacks are dimension-owned records and are rendered from
client snapshots, not represented by ItemEntities while on a belt.

## Using the standard belt

Place a belt facing its output (the player's horizontal facing at placement).
Connect its front to another belt or an inventory. Belts accept belt connections
from their back and sides; head-on outputs do not connect. This permits turns and
merging inputs. The standard form has one output; splitters, filters, crossovers,
vertical/dropping forms and wrench rotation are outside this implementation.

A belt automatically extracts from an inventory directly behind it. Automation
can insert through its back, sides or top using the item-handler capability.
Dropped ItemEntities landing on the top are captured as owned transport records;
any unaccepted remainder stays in the world entity. Empty-hand right-click picks
up the carried stack, respecting available player inventory space and reach.
The automation interface does not allow extraction from arbitrary belt positions.

An open end or full inventory stops the carried stack. Normal belts do not eject
items into the world at an open end; a future specialized dropping form can add
that behavior. Breaking a loaded belt releases its cargo as ordinary dropped
items exactly once. There are currently no crafting recipes; the standard belt
is available in the Power Advantage creative tab and through `/give`.

## Code-configurable speed and throughput

The default belt takes **8 processed server ticks per segment** and allows at most
**16 arriving or departing items per segment per server tick**. Each segment/channel
holds one normal-sized item stack. New arrivals wait for their own movement turn;
a record never crosses several segments in one tick. A segment vacated in a tick
cannot be immediately reused, giving deterministic headway and backpressure.
These occupancy rules, stack-size limits and server work budgets can reduce the
actual throughput below the item-count rating.

A downstream mod can instantiate or subclass the base block:

```java
public class FastConveyor extends ConveyorBlock {
    public FastConveyor(BlockBehaviour.Properties properties) {
        super(properties, 2, 64); // two ticks per segment, at most 64 items per tick
    }
}

// In your mod's RegisterCapabilitiesEvent listener:
ConveyorBlock.registerCapabilities(event, FAST_CONVEYOR.get());
```

Register its block/item and assets using the consuming mod's normal NeoForge or
MMDLib registration. The manager recognizes `ConveyorBlock` subclasses under any
namespace. Constructor values must be positive; rates are immutable for a block.
A high item-count rating is a limit, not an oversized-stack storage mechanism.
`travelTicks()` and `itemsPerTick()` expose the ratings.

The base also exposes `nodes`, `neighborPositions`, `inputNodes`, `insertionNode`,
`outputNodes`, `acceptsFrom`, `sourcePosition`, `destinationPosition` and
`itemPosition` hooks. These preserve room for later specialized forms; their
routing policies and models are not implemented here. Input-channel selection
must remain explicit for a crossover rather than joining its lanes.

Global work budgets are independently code-configurable during mod setup, before
any world creates a conveyor manager:

```java
ConveyorNetworks.configureLimits(new ConveyorLimits(
    512, // topology/compression steps per dimension tick
    512, // shortest-path steps
    512, // in-flight records processed
    128, // extraction endpoints visited
    512, // simulated and executed inventory operations, separately
    32   // snapshot packets
));
```

All values must be positive. `ConveyorLimits.DEFAULT` supplies those values.
Each snapshot packet contains at most 128 records. A manager retains its chosen
limits for its lifetime; changing them after world creation is rejected. Limits
bound work units rather than guaranteeing a measured number of milliseconds.

## Movement and ownership

`ConveyorTransportAdapter` uses the [shared transport core](transport-core.md) for
loaded component discovery, directed connectivity, cached endpoints and topology
invalidation. `ConveyorNetworks` follows physical output connections one segment
at a time; it never teleports a stack to the inventory returned by a graph query.
For standard one-output belts the route is unambiguous. Component generations
and current capabilities are checked as the topology changes.

`ConveyorStore` owns stable UUIDs, stacks including components, block/channel,
entry direction, travel progress and any waiting record. It stores the rendering
prototype and item count separately in dimension SavedData
`poweradvantage_conveyors`. Routine cargo stays as legal-sized stacks; unexpected
source extraction amounts can also be preserved without the prototype codec
truncating their count. Loaded ownership is indexed by segment, position and chunk.

A rotating active-record queue bounds movement work and gives merging inputs
service as space opens. Unloaded owners leave that queue until their chunk reloads;
large stores of suspended records do not consume every tick's movement allowance.
Unloaded boundaries are never traversed and production routing does not force
chunks to load. Topology is rebuilt after reload while cargo ownership persists.

Sources are visited in rotating order, with bounded slot scanning. Extraction
simulates available intake and reserves the segment before executing, preventing
reentrant insertion from stealing that space. Actual extracted stacks become
owned records even if the belt disappears during a provider callback. Destination
insertion honors the actual remainder, so partial acceptance leaves cargo on the
belt. Pickup/removal cannot release a record reserved by an active insertion;
if an inventory removes its belt during delivery, only the remaining cargo drops.

Each record receives at most one movement update per game tick, including if
`tick()` is called more than once. With exhausted work budgets, progress slows
rather than catching up with an unbounded movement burst. One stack cannot be
claimed by two players, by a player and a belt, or by removal and delivery during
the same synchronous callback.

## Client presentation

`ConveyorSync` sends initial state after `ChunkWatchEvent.Sent`, then changed-chunk
snapshots on a five-tick cadence. Batched jobs are paced by the packet budget and
only sent to players still watching that chunk in that dimension. Incomplete or
older snapshots cannot replace newer state; an empty complete snapshot removes
previously visible items. Chunk unload and logout clear the client cache.

`ConveyorClient` renders item models directly at their interpolated entry/center/
exit position, using the standard belt's top surface. It limits rendering to
2,048 nearby items per frame and never creates cargo ItemEntities. Interpolation
stays within the current segment; clients do not own or execute transfers.
Under large sync jobs or heavy load, visual updates may lag authoritative movement.

## Validation and remaining checks

```sh
./gradlew -PgameTests runGameTestServer build --offline
```

The suite includes the existing 22 machine/fluid/shared-core tests and 12 conveyor
tests: extraction and physical delivery, configurable rates and simulation,
intermediate movement positions, full-inventory backpressure, dropped-item capture,
pickup races, belt removal, component-aware SavedData/packet round trips, chunk
boundary and owner reload, merge fairness, partial delivery, reentrant removal
and out-of-order/stale snapshot assembly.

The boundary test feeds unload/reload events while holding its detached fixture
chunks loaded, avoiding accidental arena eviction; it is not a physical chunk
unload/restart stress test. SavedData is round-trip tested, rather than testing a
complete server crash/restart. Headless client startup checks registration and
resource loading. Actual on-belt rendering, multiplayer tracking/reconnection,
manual interaction, real unloading and third-party inventories still need an
interactive test instance. Large-network performance has not been benchmarked.
