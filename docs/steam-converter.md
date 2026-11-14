# Steam ↔ FE/RF converter

`poweradvantage:converter_rf_steam` converts **one millibucket of steam to eight
FE/RF**, and eight FE/RF back to one millibucket. FE and RF use the same NeoForge
energy capability; there is no additional RF storage or dependency.

## PA2 unit mapping

PA2's `FluidPoweredEntity.addEnergy` creates a `FluidStack` whose amount is the
requested energy amount. `subtractEnergy` similarly drains and returns that fluid
amount. `FluidRequest.BUCKET_VOLUME` is 1,000. Consequently one fluid-energy unit
is one millibucket at that API boundary. Combined with the confirmed
`rfConversionTable` default `steam=8`, this gives eight FE/RF per mB.

## Behavior

Defaults preserve the steam converter's PA2 buffer sizes: 1,000 FE and 1,000 mB.
It balances the steam tank around half capacity:

- Below 500 mB, available FE produces steam.
- Above 500 mB, surplus steam produces FE if there is room.
- Complete integer batches are exchanged, retaining any insufficient FE or steam
  in its original store. No fractional rounding produces or destroys energy.

The shared FE neighbor service pulls toward half its FE capacity or exports the
surplus above half. Neighbor visits rotate, respect sided handlers, share one
allowance per tick, and never load missing chunks. Pipes, tanks and other fluid
handlers insert/extract steam through the ordinary fluid capability on any face.
An extraction terminal can withdraw generated steam into an existing pipe network.
An incoming pipe can supply steam for FE generation.

Only PA steam is accepted; water, oil and other fluids are rejected. Steam and
empty buckets interact directly with the block. Whole-bucket extraction requires
1,000 mB to be present at the instant of interaction. The default 500 mB reserve
produced from FE is therefore normally collected through pipes or smaller fluid
containers; addons can choose a larger steam capacity if desired.

There is no item inventory. Both stores persist across saves; converters from 3.0
and 3.1 worlds keep their steam but start with an empty FE buffer. Capability
simulation is read-only. The converter drops itself on breaking; stored energy/steam
is not preserved in the dropped item.

## MMDLib XML machine and settings

Since 3.2 the converter is an MMDLib XML machine,
[`mmdlib/machine/converter_rf_steam.xml`](../src/main/resources/mmdlib/machine/converter_rf_steam.xml).
MMDLib supplies the FE buffer, the steam tank (storage ID `Steam`, as 3.1 saved it),
the capabilities, bucket handling and the standard machine screen with FE and steam
gauges. Its stored processor, `machine.SteamExchange`, does the exchange, sharing
neighbor service with the electricity converter through `machine.FeExchangeProcessor`.

The sizes, ratio and rate are `power.SteamConverterSettings.DEFAULT`: 1,000 FE,
1,000 mB, 8 FE to 1 mB, and 1,000 FE-equivalent per tick, hence at most 125 mB
exchanged per tick. They are no longer read from block-entity data. The automatic
FE-service phase has its own allowance of the same size; external fluid handlers are
bounded by tank contents/capacity and their network's limits.

The block has a restored PA2 recipe using a governor, steel frame and redstone
block. Addons supply governor items through `poweradvantage:governors`. Until that
tag has entries, crafting is disabled; creative and command access work without
SteamAdvantage. No replacement governor is invented in PA3.

## Verification

Server GameTests cover both exact exchange directions, sub-batch FE retention,
custom ratios and work limits, both capabilities on all faces, fluid simulation,
water rejection, persistence including 3.1-format steam, real FE source/sink
neighbors, pipe delivery, and full bucket interactions from every face. Client startup checks cover model, texture and XML resource loading;
manual GUI appearance is still a separate check.
