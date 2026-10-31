# FE/RF and electricity converter

`poweradvantage:converter_rf_electricity` is the bidirectional Electricity–RF
Transformer. It uses NeoForge's standard FE capability for both FE and RF, with no
CoFH dependency or separate RF buffer. Its other boundary is PA electricity.

The confirmed PA2 default is **one electricity unit = 0.25 FE/RF**, so a complete
exchange consumes four electricity units for one FE, or one FE for four electricity
units. All exchanges use whole integer batches. Remainders stay in their original
buffer rather than being rounded up, discarded on each tick, or converted with
floating-point arithmetic.

## Behavior

The default FE buffer holds 1,000 FE, and the electricity buffer holds 8,000 PA
units. As in PA2, the converter balances the electricity buffer around half capacity:

- Below 4,000 electricity units, it converts available FE into electricity.
- Above 4,000, it converts surplus electricity into FE when the FE buffer has room.
- At the midpoint, no internal conversion is necessary.

There is a reserve rather than immediate emptying of one buffer into the other.
External users can draw directly from either store. Sub-batch differences around
the midpoint wait until a complete exchange is possible.

The converter services neighboring FE handlers automatically. Below half its FE
capacity it pulls toward that midpoint; above half it exports the surplus. Neighbor
visits rotate, share one per-tick FE allowance, use correctly sided capabilities,
and skip unloaded positions. The electricity side is an endpoint for an electrical
network or adapter to insert/extract through; this block does not implement the
unported ElectricAdvantage cable network.

Every face exposes both capability types, each with its own units. Facing is a
model orientation, not a hidden input/output restriction. Neither buffer can be
reentered through its capability while the converter is performing a transfer.
Cached handlers refuse calls after the block is removed. Breaking the block drops
the converter item; stored energy dissipates, as with a normal energy-storage block.
Both buffers persist across ordinary saves and chunk reloads.

## Public electrical boundary

`power.PowerCapabilities.ELECTRICITY` is a sided
`BlockCapability<IEnergyStorage, Direction>`. The familiar storage protocol is
reused, but **every amount on this capability is in PA electricity units, not FE**.
For example, a cable can obtain it with:

```java
var electricity = level.getCapability(PowerCapabilities.ELECTRICITY, pos, side);
```

Dependent mods register this capability on their own block entities using
`RegisterCapabilitiesEvent.registerBlockEntity`, returning a handler measured in
the same units. FE consumers continue using `Capabilities.EnergyStorage.BLOCK`.
There is no additional FE-to-RF conversion step.

This boundary does not define voltage, amperage, watts, electrical safety or a
transport graph. ElectricAdvantage can adapt a selectable electrical simulation
mode to this energy-storage boundary when its rules are defined. The converter
therefore bridges a distinct electrical model rather than silently treating its
quantities as FE.

## Settings and screen

MMDLib registers the block and entity through the `poweradvantage:electricity_converter`
implementation factories. `power.ConverterSettings` is constructor- and codec-backed:

```json
{
  "fe_capacity": 1000,
  "electricity_capacity": 8000,
  "fe_quantum": 1,
  "electricity_quantum": 4,
  "fe_per_tick": 1000
}
```

These are factory settings for MMDLib standalone definitions or code constructing
a converter; this pass does not introduce a global TOML configuration. Capacities
and rates must accommodate a complete positive exchange batch. `fe_per_tick` bounds
the automatic FE service phase and, separately, the FE-equivalent amount converted
in one tick. The external FE handler also uses that value as its per-call limit.
The electricity handler accepts/extracts up to available capacity/contents per call.

MMDLib's XML screen shows the two stores separately and handles opening, menus,
value synchronization and rendering. It has no item slots. Labels and gauge
placeholders are supplied in every existing locale.

The old recipe used a PSU, steel frame and redstone block. The port uses the
`poweradvantage:power_supply_units` tag for the PSU. That tag is empty until an
addon or data pack supplies it, so the crafting recipe is conditional. The block
is available in the creative tab and via commands independently of a PSU provider.

## Verification and remaining converters

Server tests exercise capability registration on all six faces, XML menu binding,
FE and electricity simulation, both conversion directions, retained partial batches,
persistence, stale handlers, custom ratios/rates and actual neighboring FE sources
and sinks. Their conservation assertions include energy delivered to consumers.

The [steam converter](steam-converter.md) is now implemented at eight FE/RF per
millibucket, following PA2's direct fluid-energy amount mapping. Both converters
share `BufferedConverterBlockEntity` for FE storage, guarded ports and neighbor
service. Quantum remains without implemented consumers in the bundled reference.
