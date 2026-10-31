# PA2 power contracts: source audit

This audit covers only the bundled `PA2` tree. It does not establish what separate
ElectricAdvantage, SteamAdvantage or third-party repositories implemented.

## Quantum

The PA2 implementation references to `quantum` are confined to the creative block,
RF converter registration/subclass, converter recipe, and default conversion map.
There are no implemented quantum-consuming machines or quantum cable classes in
this reference tree. The long comment in `PowerAdvantage.java` describes a planned
QuantumAdvantage with wireless power, relays, machinery, tools and storage; those
comments are design proposals rather than implementations.

Source entry points:

- `PA2/src/main/java/com/mcmoddev/poweradvantage/init/Blocks.java` registers
  `infinite_quantum` as an `InfiniteEnergyBlock` with a `quantum` conduit type.
- `PA2/src/main/java/cyano/poweradvantage/api/modsupport/rf/TileEntityRFQuantumConverter.java`
  supplies that type to the shared RF converter with 1,000-unit local/RF buffers.
- `PA2/src/main/java/com/mcmoddev/poweradvantage/init/ModSupport.java` registers
  the converter and recipe.
- `PA2/src/main/java/com/mcmoddev/poweradvantage/PowerAdvantage.java` contains the
  QuantumAdvantage planning comment and default RF conversion configuration.

The default configuration string was `steam=8;electricity=0.25;quantum=8`, explicitly
in units of **RF per PA energy unit**. These are historical configurable defaults,
not a decision about PA3's physical units or future conversion efficiencies.
FE and RF remain equivalent at 1:1; PA's named legacy electricity units are a
separate question. In particular, this audit does not equate a PA steam energy
unit with a millibucket of PA3 steam.

## Current direction

The intended creative source is one `infinite_power` block that supplies the type
requested by its connection. Preserve the existing FE and steam forms until a
working unified source and migration path exist. A separate quantum creative block
is not needed just to reproduce an otherwise unused type name.

Converters are useful bridges between distinct power models. Do not create an
additional FE/RF subsystem or redundant conversion buffer. ElectricAdvantage's
possible selectable voltage/current mode should define its own electrical behavior
and explicit conversion boundaries; this audit does not choose those rules for it.

The next implementation needs to establish which requests are supported by dependent
mods and how those requests identify a resource and its units. Quantum is explicitly outside the port scope: the available reference contains no
implemented QuantumAdvantage system to connect. Neither a quantum converter nor a
standalone quantum creative source is planned. Tech Reborn conversion is also excluded.

The FE/RF–electricity bridge is now implemented using the confirmed historical electricity ratio;
see [electricity converter](electricity-converter.md) for the public boundary and current limitations.
