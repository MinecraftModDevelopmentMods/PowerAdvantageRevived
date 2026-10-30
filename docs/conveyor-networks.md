# Conveyor stage: physical items in transit

The selected behavior is physical transport: items can queue, stop and be picked
up midway. A completed inventory transfer must not be animated as if it were an
item still owned by the belt.

This is the next implementation stage after fluid pipes. No conveyor blocks or
transport simulation are registered by the current fluid implementation.

## Ownership and representation

Each in-transit stack belongs to one dimension-level transport record with a
stable ID, ItemStack (including components), current block/channel, incoming port,
progress along the segment, and optional destination/route version. It is not an
Entity or a BlockEntity. Extraction creates a record only after storage capacity
on the first belt segment is available. Insertion removes only the amount the
actual inventory accepted. Splitting/merging stacks updates ownership atomically
on the server, including a defined order for player pickup races.

Persist transport records in SavedData and suspend records whose owning chunk is
unloaded. Do not move a record across an unloaded boundary. Pipe topology is fully
derivable; conveyor stack ownership is not and must be saved. Belt removal must
move remaining records to a defined recovery location or spawn actual dropped
ItemEntities exactly once. ItemEntities are appropriate for items that leave the
belt, not for representing every traveling stack.

## Topology and scheduling

Factor the physical/compressed topology machinery into a port/channel graph.
Normal belts have a directed input-to-output connection. A crossover has separate
channels at the same BlockPos; crossing lanes do not connect. A dropping segment
has a directed edge to its receiving segment below. Filters add resource
predicates to edges, rather than permanently classifying an inventory as accepting
one resource.

Retain all physical segment positions along compressed edges. Routing uses the
compressed graph; movement and capacity use those segments. Each segment/channel
has a bounded occupancy queue and headway. Move downstream records first, or use a
two-phase propose/commit step, so tick iteration order cannot overlap records or
move one stack several segments in a single tick. Junctions arbitrate fairly.
An invalidated destination causes a route refresh or backpressure; it never causes
an already-extracted stack to disappear. A destination reservation is advisory:
acceptance must still be checked at arrival.

## Client presentation

Send initial snapshots to tracking players and bounded updates for changed chunks.
Render stacks at their segment/progress positions using the client item renderer;
interpolate between authoritative updates. Do not send the entire dimension graph
each tick. Include record IDs/versioning so removal, pickup, chunk tracking changes
and reconnects cannot leave duplicate visual stacks. Player pickup requests must
identify a record and be validated by the server for reach and ownership.

## Required tests before completion

Exercise blocked queues, junction fairness, crossover isolation, dropping links,
component-aware filters, partial destination acceptance, inventory extraction
remainders, belt removal, pickup races, split/rejoin, unloaded boundaries,
save/restart with occupied belts, and client tracking snapshots. Rendering and
manual pickup need a real client check in addition to dedicated-server tests.
