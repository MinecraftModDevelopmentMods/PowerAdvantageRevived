# Distillery and distilling recipes

The Distillery (`poweradvantage:still`, "Distillation Furnace") converts one fluid into another. Since 3.2 it is an
MMDLib XML machine, defined in [`mmdlib/machine/still.xml`](../src/main/resources/mmdlib/machine/still.xml), and
`poweradvantage:distilling` is the MMDLib machine recipe type that definition declares. Recipes are ordinary data-pack
recipes, so data packs, CraftTweaker and KubeJS can add, replace or remove them.

## Recipe format

Files go in `data/<namespace>/recipe/<name>.json`, in MMDLib's machine-recipe format:

```json
{
  "type": "poweradvantage:distilling",
  "inputs": {
    "input": [{ "fluids": "#poweradvantage:crude_oils", "amount": 100 }]
  },
  "outputs": {
    "output": [{ "fluid": { "id": "poweradvantage:refined_oil", "amount": 50 } }]
  },
  "time": 100
}
```

| Field | Meaning |
|-------|---------|
| `inputs.input` | One fluid ingredient for the input tank: `fluids` is a fluid ID, a `#tag`, or a list of IDs, and `amount` is the mB consumed per operation. |
| `outputs.output` | One fluid product for the output tank: `id` and `amount` in mB. |
| `time` | Ticks per operation. |

`input` and `output` are the Distillery's tank groups; recipes must use those names. MMDLib's
[XML machines guide](https://github.com/MinecraftModDevelopmentMods/MMDLib/blob/master-2.0-1.21.1-neo/docs/xml-machines.md)
documents the full format, including several recipes in one file (`mmdlib:machine_recipes`).

The built-in recipe is `poweradvantage:distilling_refined_oil`: 100 mB crude oil to 50 mB refined oil in 100 ticks,
the same 2:1 ratio as 3.1 and nearly the same rate (MMDLib spends one
tick per cycle choosing the recipe). Its input is the `poweradvantage:crude_oils` fluid tag, which includes the common
`c:crude_oil` and `c:oils/crude` tags, so other mods' crude oils that use either tag distill without configuration.
Override the recipe by shipping one with the same ID in a data pack, or remove it with KubeJS or CraftTweaker.

3.0 and 3.1 recipes (`"input"`, `"output"`, optional `"ticks"`) no longer load; rewrite them in this format.

## Behaviour

- Two 4,000 mB tanks: input and output. The input only accepts fluids some recipe uses. Buckets and other fluid
  containers work on the block and on the screen's tank gauges.
- An operation starts when the input holds the recipe's full amount and the output has room for its product. MMDLib's
  processor reserves both, so neither can be taken while the operation runs.
- The front shows a lit furnace while an operation runs.
- The screen is MMDLib's standard machine screen: tank gauges, a progress arrow (which opens the recipe in JEI when
  JEI is installed), pause, side configuration, redstone control and statistics.
- Pipes can fill the input and drain the output through the faces enabled in side configuration.

## Fluid pumps

3.1's Distillery pulled from and pushed into neighbouring blocks by itself. Since 3.2 that is done by two addon items,
installed in a fluid group's addon slots in the side configuration drawer:

- **Fluid Intake Pump**: pulls fluid into the group from neighbouring blocks.
- **Fluid Output Pump**: pushes fluid from the group into neighbouring blocks.

Each moves 1,000 mB every 10 ticks, needs no power, and uses only the faces that group is enabled on. Put an intake
pump in the Distillery's input group and an output pump in its output group to get 3.1's behaviour. Stills from 3.1
worlds keep their contents but start without pumps.

The pumps are generic MMDLib automation addons, so they work in any MMDLib machine with fluid tanks, including other
mods'. They are crafted from a fluid pipe, a sprocket and iron ingots.

## API for other mods

Read and match recipes through MMDLib: `MachineContent.recipeType(ResourceLocation.parse("poweradvantage:distilling"))`.

For addons written against 3.0 and 3.1, package `zone.moddev.mc.poweradvantage.api.distillation` remains, deprecated:
`DistillingRecipe.find(level, fluidStack)` and `DistillingRecipe.accepts(level, fluidStack)` read the machine recipes
and present each recipe with one fluid input and one fluid output as a `DistillingRecipe` view, whose `duration()` is
the recipe's `time`. The views are not registered recipes; `init.Recipes` and the old JSON codec are gone.

## Configuration policy

Distilling recipes remain JSON data-pack recipes, customizable through data packs,
CraftTweaker or KubeJS. The legacy distilling-recipe configuration option is intentionally
not restored; it predates the current recipe customization mechanisms.
