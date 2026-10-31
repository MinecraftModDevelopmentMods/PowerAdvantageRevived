# Restoring Power Advantage 2 content

The restored standalone items are `starch`, `bioplastic_ingot`, `sprocket`, and
`rotator_tool`, using their existing models and textures. The rotation wrench
turns blocks counterclockwise through NeoForge's world-aware rotation hook,
respects item-use permissions, and has no durability cost.

## Recipes and material tags

PA3 uses one standard JSON recipe set. Material alternatives remain available for
iron/copper pipes, iron/steel tanks, and wooden-stick/generic-rod sprockets.
Progression modes, their TOML selector, and duplicate mode recipes are intentionally
removed. Pack makers can customize progression through data packs, CraftTweaker
or KubeJS. See [recipes and customization](recipes.md) for the current recipe IDs.

Steel ingredients require a mod or pack supplying the relevant tags; PA3 does not
substitute iron for steel. Bioplastic contributes to `c:plastics` and
`c:ingots/plastic`; sprockets contribute to `c:gears`, `c:gears/steel` and
`poweradvantage:sprockets`; starch contributes to `c:starches`. Part ingredients
use `poweradvantage:fluid_pipes` and `poweradvantage:steel_frames`, which downstream
packs can extend. Rubber equivalence is an [optional built-in pack](optional-packs.md).

Potatoes and poisonous potatoes crush to starch through MMDLib; starch smelts into
bioplastic. Oil buckets and coal/carbon dusts use NeoForge's furnace-fuel data map.
See [fuels and crushing](fuels-and-crushing.md). No reflective BaseMetals integration
is needed for these registrations. BaseMetals-specific work remains deferred.

## Fluid switch and oil contact

`poweradvantage:fluid_switch` is a six-sided fluid valve. Redstone power closes it;
removing power opens it. It participates as a standard pipe node without a block
entity while open, and is excluded from routing while closed. Its default rating
is 1,000 mB/tick; dependent mods can supply another positive constructor rating.
Placement reads existing power, and transitions invalidate old routes before
neighbor callbacks. Cached handlers cannot bypass a closed valve. It stores no
fluid. The PA2 recipe uses a lever, two pipes and a steel frame.

Both source and flowing refined oil refresh nausea for 200 ticks. Crude oil
refreshes Slowness III for 200 ticks, preserving PA2's zero-based amplifier of 2.

## Filters and screens

All eight item filters have persistent cargo inventories, MMDLib XML screens,
crafting recipes and loot tables. The inventory filter also has nine real sample
slots. See [item filters](item-filters.md) for routing, automation and work limits.
Ordinary conveyors continue to use dimension-owned physical cargo without a block
entity for each belt segment.

Machines use MMDLib's XML menu registration, opening and synchronization. The old
PA-specific screen renderer and menu have been removed. All fourteen locales
include the screen keys, preserving gauge placeholders. The five legacy
textures flagged in `TODO.md` match their PA2 sources byte for byte.

## Remaining work

The intended creative source is unified, with FE/RF equivalent at 1:1 and explicit
bridges to distinct power models. The electrical boundary is available through the
[electricity converter](electricity-converter.md); the unified source still needs
dependent-mod integration and a migration strategy. Quantum and Tech Reborn
converter ports are explicitly out of scope; see [the legacy power audit](legacy-power-audit.md). The PA2 reference tree remains available while this
work is pending.
