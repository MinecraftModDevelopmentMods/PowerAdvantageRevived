# Power Advantage on NeoForge 1.21.1

Requires Java 21, NeoForge 21.1.265, and the MMDLib 2.0 alpha API with
`StandaloneBlock.blockEntityData`, `BlockContext.blockEntity`, and
`RuntimeContentRegistrar.blockEntityTypes`.

The build uses `libs/mmdlib-2.0.0-alpha.jar` directly so an older Maven-local
artifact cannot silently replace it. This copy came from the adjacent
`MMDLib-2.x/build/libs/mmdlib-2.0.0-alpha-all.jar`; the `-all` distribution
includes MMDLib's nested dependencies. MMDLib remains a separate required mod
when installing the built Power Advantage JAR. `libs/` is ignored by Git.

## Content and implementation

Seven standalone blocks are defined under `src/main/resources/mmdlib/standalone/block`.
The machine module supplies the shared block/entity factory; standalone settings
select behavior and capacity. MMDLib loads the definitions, and Power Advantage
queues its namespace through `RuntimeContentRegistrar`. No dummy machine
material or BaseMetals dependency is required.

- Infinite power: 65,535 FE buffer, output up to 4,096 FE per face per tick.
- Infinite steam: 131,070 mB buffer, output up to 65,535 mB per face per tick.
- Drain: 2,000 mB buffer; collects sources above and exports to neighbors every ten ticks.
- Discharge: 1,000 mB buffer; imports from neighbors and places fluid below every ten ticks.
- Storage tanks: 4,000 and 16,000 mB; weak/strong redstone and comparator output
  scale from 0 to 15 with their fill level.
- Steel frame: retains the existing model and texture.

Machines use MMDLib `FluidStorage`, `EnergyBuffer`, capability registration, and
transfer helpers. Storage is persisted through registry-aware NBT. World-fluid
searches stay within loaded chunks, a 32-block radius, and 4,096 visited positions.
Pickup and placement account for whole buckets; a partially full tank never
removes a source it cannot hold. Collection supports `BucketPickup`, including
waterlogged blocks. Discharge places into air or liquid space and delegates
placement and dimension vaporization behavior to NeoForge's `FluidUtil`.

Right-click a machine to open its synchronized status screen. Use fluid containers
on the block to fill or empty its tank. The screen follows `mmdlib-test-mod`:
a NeoForge menu/screen using MMDLib panel and meter sprites. It does not depend
on the XML screen runtime. Steam accepts extraction only; its bucket is registered
with a NeoForge dynamic fluid-container model.

Existing models/textures are retained. Registered blockstates and item model
references are updated, translations use JSON, and mining tags and self-drop loot
tables are supplied. The old unused example config and empty registration hooks
have been removed. The fluid network is now implemented; see [fluid networks](fluid-networks.md).
The legacy abstract conduit API remains an extension point. No new recipes or previously
unimplemented machines are introduced. This port does not migrate 1.12 worlds or
preserve the MMDLib 1 feature-inheritance Java API for downstream add-ons.

## Validation

```sh
./gradlew build --offline
./gradlew -PgameTests runGameTestServer --offline
```

GameTests are opt-in, live under `src/gameTest`, and are excluded from the released
JAR. Five tests cover standalone registration and capacities, capabilities on all
faces, fluid persistence and redstone, fluid conservation, generator output, and
actual ticking of both pumps with neighboring tanks. The test run uses
`build/gametest-run`, separate from the normal development run directory.

The dedicated GameTest server and build passed. Client appearance, menu opening
and synchronization over a real connection, bucket interaction, and compatibility
with other mods' automation still require an interactive client check.


## Texture atlas compatibility

Block textures use `assets/poweradvantage/textures/block/`, shared material
textures use `textures/block/materials/`, and item textures use `textures/item/`.
These locations participate in the modern default block atlas. Legacy `blocks/`,
`items/`, and top-level `materials/` paths can resolve as files while still missing
from the atlas; the pipe resource validator now checks both location and existence.
Steam's client fluid texture IDs use the same canonical block paths. A headless
NeoForge client completed atlas creation after this migration, with no Power
Advantage missing-model or missing-texture warnings. In-world appearance remains
an interactive verification step.
