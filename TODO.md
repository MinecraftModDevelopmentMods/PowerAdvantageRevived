# Power Advantage 3 – Port TODO

Gap list from comparing the old 1.12 code (`PA2/`) against the current 1.21.1 port. Nothing below is implemented yet
unless noted.

## Blocks

- [x] **Item filters** (8): `item_filter_block`, `_food`, `_fuel`, `_inventory`, `_ore`, `_overflow`, `_plant`, `_smelt`.
  - MMDLib factories, persistent cargo/sample inventories, XML screens, recipes, loot and conveyor endpoints.
  - See [item filters](docs/item-filters.md) for PA2 routing semantics and configurable pacing.
- [x] **`fluid_switch`**: redstone closes the valve; no power opens it. Uses the shared pipe network,
  no block entity, the existing models, a crafting recipe and a self-drop loot table.
- [ ] **Unified infinite power source**: provide the resource
  requested by an attached cable or machine. Current `infinite_power` is FE-only as an initial implementation.
  - [x] Trace the bundled PA2 quantum references: only source/converter placeholders and design comments;
    no implemented quantum consumers in this tree. See [legacy power audit](docs/legacy-power-audit.md).
  - Dependent-mod request/units contracts still need to be established before implementing a unified source.
  - ElectricAdvantage may offer a selectable voltage/current mode; do not impose that model in PA3 yet.
  - Keep existing steam/power blocks until the unified implementation has a migration strategy.
- [x] **In-scope converters** (`converter_rf_electricity`, `converter_rf_steam`):
  - [x] `converter_rf_electricity`: MMDLib-backed FE/RF ↔ PA electricity bridge, exact 1 FE ↔ 4 electricity
    conversion, persistent buffers, public electricity capability and XML screen.
    See [electricity converter](docs/electricity-converter.md).
  - [x] `converter_rf_steam`: 1 mB steam ↔ 8 FE/RF, steam-only fluid capability, bucket support,
    pipe integration, shared FE service, persistence and MMDLib XML screen.
    See [steam converter](docs/steam-converter.md).
  RF source found under `PA2/src/main/java/cyano/poweradvantage/api/modsupport/rf/`, including
  electricity, quantum and steam subclasses. Shared balancing logic lives in
  `cyano/poweradvantage/api/modsupport/TileEntityConverter.java`; conversion ratios come from
  `PowerAdvantage.rfConversionTable`. Port RF support against modern energy APIs and PA3 resource
  contracts rather than copying the legacy CoFH interface.
  - Confirmed legacy ratios: steam power unit = 8 FE/RF; electricity unit = 0.25 FE/RF.
    PA2 fluid-energy API maps one steam unit directly to one mB; steam conversion is implemented.
  - FE and RF are equivalent at 1:1 here. Do not add a redundant FE-to-RF conversion layer.
    Converters remain intended bridges between distinct power models, including a future electrical mode.
  - Tech Reborn and quantum converter ports are explicitly out of scope; see scope decisions below.

## Items

- [x] Register `starch`, `bioplastic_ingot`, `sprocket`, `rotator_tool` (models and lang reused).
  - PA2 ore-dictionary names become item tags: `plastic`/`ingotPlastic`, `sprocket`/`gear`/`sprocketSteel`/`gearSteel`,
    and `rubber`/`ingotRubber` for plastic when the `plasticIsAlsoRubber` option is on.
  - [x] Port `RotationTool` using world-aware block rotation and item-use permissions.
  - [x] Potato and poisonous-potato crushing to starch uses `mmdlib:crushing`.
  - [x] Rubber equivalence is an optional per-world built-in data pack; see [optional packs](docs/optional-packs.md).

## Recipes and loot

- [x] **Crafting recipes**: keep one standard JSON set with useful ingredient/material alternatives.
  - Remove the recipe-mode TOML setting, condition codec and progression-only duplicates.
  - Use plain IDs such as `fluid_pipe_iron`, `fluid_pipe_copper`, `fluid_metal_tank_iron`,
    `sprocket_rod` and `sprocket_stickwood`; optional materials still use built-in tag conditions.
  - Pack makers control progression through data packs, CraftTweaker or KubeJS.
    See [recipes and customization](docs/recipes.md).
- [x] **Loot tables**: still, switch and all eight filters have self-drop tables. Filter cargo and real samples also drop on removal.

## Gameplay and compat

- [x] Oil effects: source and flowing refined oil apply nausea for 200 ticks; crude oil applies
  Slowness III for 200 ticks. PA2 used zero-based amplifier 2, despite the earlier TODO calling it II.
- [x] `FuelRegistry` audit: oil buckets and coal/carbon dust fuels now use the NeoForge furnace-fuel data map.
  Bucket remainders use normal item behavior; no PA-specific singleton is needed.
  See [fuels and crushing](docs/fuels-and-crushing.md).
- [ ] `BaseMetalsCompat`: specific compatibility, carbon-dust alias fallback and global hammer-config
  mutation remain deferred per maintainer direction. Potato crushing and fuel registrations already use
  public MMDLib/NeoForge data APIs. Legacy recipe-mode condition factories are intentionally dropped.
- [x] Verify textures that PA2 has: `fluid_switch_on/off`, `powercable`, `techreborn_converter`, `metal_panel`.
  All are present at canonical `textures/block` paths and byte-identical to PA2. The TR asset remains reference-only.

## Housekeeping

- [x] Add all `screen.poweradvantage.*` keys to every locale; preserve gauge placeholders.
- [ ] Remove the PA2 directory once the above is done.
- [x] Remove the unused `MachineMenu`/`MachineScreen`/`MACHINE_MENU` fallback and six-word status synchronization.
  Machines and filters use only MMDLib XML screens and its menu registration/opening path.
- [x] Keep distilling recipes data-driven (`poweradvantage:distilling`); do not restore the obsolete recipe-config option.
  Pack makers customize recipes through data packs, CraftTweaker or KubeJS.

## Scope decisions

- Tech Reborn energy conversion is excluded from this port; do not implement `converter_tr_electricity`.
- QuantumAdvantage remained planned rather than implemented in the available reference. Do not port
  `converter_rf_quantum` or add a standalone quantum creative source without an actual consuming system.
- Distilling uses modern JSON recipes and pack scripting; no separate legacy distilling-config mechanism.
- BaseMetals 3.0 and its matching Modern Metals are standalone. Real MMDLib integration is deferred until
  a future MMDLib-based branch (potentially 4.0); ordinary shared-tag recipe compatibility remains usable.
- Retain the PA2 reference tree and unregistered legacy assets while the remaining port work is pending.

## Done in this port

- MMDLib 2.0 XML screens for all machines, including the Distillery.
- Crude oil, refined oil and steam fluids, blocks and buckets.
- Distillery with a data-driven `poweradvantage:distilling` recipe type. The default recipe is 2 crude oil → 1 refined oil;
  duration is the input:output ratio in ticks, rounded up.

## First PA2 restoration pass

See [restored content and recipe policy](docs/pa2-content-port.md) for items, tags,
standard recipe choices, and remaining compatibility work. Starch smelting is restored.
