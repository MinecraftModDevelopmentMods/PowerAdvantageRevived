# Power Advantage developer documentation

This checkout targets Minecraft 1.21.1, NeoForge and the matching MMDLib 2.0 alpha
API. Start with the subsystem you intend to extend:

- [Fluid pipe networks](fluid-networks.md): architecture, transfer/lifecycle walkthrough,
  limitations, per-pipe rates and downstream registration examples.
- [Shared transport core](transport-core.md): adapters, directed channel identities,
  compression, physical paths, budgets and incremental state-machine maintenance.
- [Standard conveyors](conveyor-networks.md): physical cargo ownership, movement,
  per-belt/per-dimension settings, synchronization and future extension boundaries.
- [Distillery and distilling recipes](distillation.md): the data-driven `poweradvantage:distilling` recipe
  format, timing rules and the lookup API.
- [Steam converter](steam-converter.md): bidirectional fluid/FE conversion and pipe integration.
- [Electricity converter](electricity-converter.md): FE/RF bridge, electrical capability, exact ratios and MMDLib screen.
- [Recipes and pack customization](recipes.md): standard recipe IDs, material alternatives and data-pack overrides.
- [Optional compatibility packs](optional-packs.md): per-world bioplastic/rubber equivalence.
- [Legacy power audit](legacy-power-audit.md): what the PA2 quantum references actually implement.
- [Fuels and crushing](fuels-and-crushing.md): data-driven fuel values and potato-to-bioplastic production.
- [Item filters](item-filters.md): sorting predicates, sample inventory, XML menus and bounded transfer work.
- [PA2 content restoration](pa2-content-port.md): restored items, crafting recipes, tags and remaining compatibility work.
- [Port and installation notes](port-1.21.1.md): build/dependency setup, registered
  machines, resource conventions and runtime validation scope.

## Generated Java API and implementation reference

All main-source declarations are documented, including private helpers, record
components, work-budget semantics and ownership boundaries. Generate the HTML with:

```sh
./gradlew javadoc --offline
```

Open `build/docs/javadoc/index.html`. The Gradle task includes private members for
maintenance work, enables all doclint checks, and treats Javadoc warnings as errors.
Existing Java compilation deprecation warnings are separate from documentation
warnings. A Javadoc jar is not part of `build`; run the `javadoc` task when needed.

Useful starting classes are `FluidPipeBlock`, `FluidNetworks`, `TransportNetworks`,
`RoutingGraph`, `TransportAdapter`, `ConveyorBlock` and `ConveyorNetworks`. Public
factory/capability hooks are the downstream API; private helper descriptions explain
implementation and lifecycle rather than promising source compatibility.

## Validation

```sh
./gradlew build --offline
./gradlew -PgameTests runGameTestServer --offline
python3 scripts/validate_pipe_models.py
```

The test flag opts into server GameTests; their classes/resources are excluded from
the released mod. Current tests cover machine, fluid, topology, conveyor and filter
cases. See each subsystem's validation section for the distinction between tested
server behavior, simulated unload boundaries and remaining interactive-client or
large-network performance checks. A successful build is not an in-world rendering
or third-party interoperability test.
