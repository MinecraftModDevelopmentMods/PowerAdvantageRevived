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

Machines, the frame, two fluid-pipe forms, fluid switch, standard conveyor and
eight item filters are defined
under `src/main/resources/mmdlib/standalone/block`.
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
- Distillery: two 4,000 mB tanks converting fluids through data-driven recipes; see [distillation](distillation.md).
- Steam–FE/RF converter: one mB ↔ eight FE, steam-only tank and pipe/bucket interaction; see [steam converter](steam-converter.md).
- FE/RF–electricity converter: exact bidirectional exchange through distinct power capabilities; see [converter](electricity-converter.md).
- Steel frame: retains the existing model and texture.
- Fluid switch and item filters: see [restored content](pa2-content-port.md) and [item filters](item-filters.md).

Machines use MMDLib XML screens and its registered menu/opening/synchronization path.
The unused PA-specific menu and renderer have been removed.

Machines use MMDLib `FluidStorage`, `EnergyBuffer`, capability registration, and
transfer helpers. Storage is persisted through registry-aware NBT. World-fluid
searches stay within loaded chunks, a 32-block radius, and 4,096 visited positions.
Pickup and placement account for whole buckets; a partially full tank never
removes a source it cannot hold. Collection supports `BucketPickup`, including
waterlogged blocks. Discharge places into air or liquid space and delegates
placement and dimension vaporization behavior to NeoForge's `FluidUtil`.

Right-click a machine to open its MMDLib 2.0 XML screen, which uses MMDLib's
generic menu, synchronized getter values, and fluid or energy gauge. Use fluid
containers on the block to fill or empty its tank. Machine screen definitions
live in `mmdlib/screen/` and are selected by block-entity type; the shared
machine definition binds right-clicks to `mmdlib:open_menu`. Steam accepts
extraction only; its bucket is registered with a NeoForge dynamic fluid-container
model.

Existing models/textures are retained. Registered blockstates and item model
references are updated, translations use JSON, and mining tags and self-drop loot
tables are supplied. The old unused example config and empty registration hooks
have been removed. The fluid network is now implemented; see [fluid networks](fluid-networks.md).
The legacy abstract conduit API remains an extension point. The only recipe added is the default distilling recipe.
See [TODO.md](../TODO.md) for remaining unported content. This port does not migrate 1.12 worlds or
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


The standard physical conveyor is now implemented; see [conveyor operation and
extension settings](conveyor-networks.md). Specialized dropping/crossover forms
remain downstream work.
