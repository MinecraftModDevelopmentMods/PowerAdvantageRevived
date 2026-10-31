# Fuels and crushing compatibility

Power Advantage no longer needs PA2's `FuelRegistry` singleton or reflective
BaseMetals crusher registration. NeoForge provides furnace-fuel data maps, and
MMDLib provides the crushing recipe type. Both are data-pack extensible and need
no optional BaseMetals dependency.

## Furnace fuels

`data/neoforge/data_maps/item/furnace_fuels.json` adds these PA2 defaults without
replacing the vanilla fuel map:

| Item or common item tag | Burn time in server ticks |
| --- | ---: |
| `poweradvantage:bucket_crude_oil` | 5,000 |
| `poweradvantage:bucket_refined_oil` | 25,000 |
| `c:dusts/coal` | 1,600 |
| `c:dusts/carbon` | 1,600 |

Dust tags are resolved from the current data packs. An absent or empty dust tag
adds no fuel entries; Power Advantage does not register another mod's dust items.
The oil buckets already declare the empty bucket as their crafting remainder, so
a standard furnace returns it when consuming the fuel. Steam buckets remain
nonfuel. The fuel filter uses the same standard burn-time lookup, so it recognizes
these additions automatically.

A data pack can change the defaults through the same NeoForge data-map location:

```json
{
  "values": {
    "poweradvantage:bucket_refined_oil": { "burn_time": 30000 }
  }
}
```

This restores PA2's registered static fuel values. Other mods needing stack-specific
fuel behavior can use NeoForge's item burn-time APIs or fuel event; there is no
separate PA registry to keep synchronized. PA2's special post-burn universal-bucket
handler is replaced by the ordinary remainder of each registered oil bucket.
The PA3 distillery remains the existing fluid-recipe machine: this pass does not
add a fuel slot or change its operation.

## Starch production

`poweradvantage:starch_from_crushing` is an ordinary `mmdlib:crushing` recipe that
accepts either a potato or a poisonous potato and produces one starch. The existing
smelting recipe turns one starch into one bioplastic ingot in 200 ticks for 0.1 XP.
This restores the PA2 potato-to-starch recipe through MMDLib's public recipe type
instead of searching for legacy BaseMetals registry classes with reflection.

Data packs can replace or remove the recipe by ID. Crackhammers use MMDLib's normal
crushing interaction; see the bundled MMDLib runtime documentation for tool usage.
Server tests verify recipe matching and assembled results for both potato types;
manual crackhammer interaction is not part of this batch's runtime verification.

## Remaining legacy compatibility decisions

Recipes use one standard set with [pack customization](recipes.md); the earlier recipe modes have been removed.
The carbon-dust alias fallback and global BaseMetals hammer-configuration changes
remain deferred in `TODO.md`. The completed integrations
above do not require those legacy hooks or a BaseMetals installation.
