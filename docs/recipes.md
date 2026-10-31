# Recipes and pack customization

Power Advantage has one standard JSON recipe set. There are no built-in progression
profiles, recipe-mode conditions or TOML recipe-mode setting. Existing
`poweradvantage-common.toml` files from earlier builds are no longer read by PA.

Material alternatives remain separate recipes with descriptive IDs:

| Recipe ID under `poweradvantage` | Ingredients | Result |
| --- | --- | --- |
| `fluid_pipe_iron` | Six iron ingots in the top and bottom rows | Six pipes |
| `fluid_pipe_copper` | Six copper ingots in the top and bottom rows | Six pipes |
| `fluid_metal_tank_iron` | Eight iron ingots around a pipe | One metal tank |
| `fluid_metal_tank_steel` | Eight steel ingots around a pipe | One metal tank |
| `sprocket_stickwood` | Four steel ingots around a wooden stick | Four sprockets |
| `sprocket_rod` | Four steel ingots around a generic rod | Four sprockets |

Ingredient tags let other mods provide compatible materials. Optional material
recipes use NeoForge's built-in tag-availability conditions. The wooden-stick and
generic-rod sprocket alternatives can overlap when sticks belong to `c:rods`.
These are ingredient alternatives, with no separate progression behavior.
The duplicate tech recipes, reduced-yield apocalyptic recipes and mode-specific
bronze fallback have been removed.

## Data packs

Recipe files live at `data/poweradvantage/recipe/<id>.json`. A pack can replace a
recipe using the same namespace and path. For example, replace the copper-pipe
recipe with a different yield:

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["xxx", "   ", "xxx"],
  "key": {"x": {"tag": "c:ingots/copper"}},
  "result": {"id": "poweradvantage:fluid_pipe", "count": 3}
}
```

Place it at `data/poweradvantage/recipe/fluid_pipe_copper.json` in the overriding
pack. Reload server data after changing recipes. CraftTweaker or KubeJS can also
remove, replace and add recipes; the mod imposes no additional progression selector.
Use the IDs in the packaged JSON, rather than the removed `_normal`, `_tech` or
`_apocalyptic` IDs, when targeting recipes.

The same data-driven approach applies to [distilling](distillation.md) and
[MMDLib crushing and furnace fuels](fuels-and-crushing.md). No BaseMetals-specific
integration is required to customize the standard recipes.
