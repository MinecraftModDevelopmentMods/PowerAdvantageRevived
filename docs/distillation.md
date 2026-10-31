# Distillery and distilling recipes

The Distillery (`poweradvantage:still`, "Distillation Furnace") converts one fluid into another. Its recipes are
ordinary data-pack recipes of type `poweradvantage:distilling`, so data packs, CraftTweaker and KubeJS can add,
replace or remove them.

## Recipe format

Files go in `data/<namespace>/recipe/<name>.json`:

```json
{
  "type": "poweradvantage:distilling",
  "input":  { "fluid": "poweradvantage:crude_oil", "amount": 2 },
  "output": { "id": "poweradvantage:refined_oil", "amount": 1 },
  "ticks": 2
}
```

| Field    | Required | Meaning |
|----------|----------|---------|
| `input`  | yes      | A sized fluid ingredient: `fluid` (a fluid id) or `tag` (a fluid tag), plus `amount` in mB consumed per operation. |
| `output` | yes      | A fluid stack: `id` and `amount` in mB produced per operation. |
| `ticks`  | no       | Operation duration in ticks, at least 1. Overrides the automatic duration below. |

The built-in recipe is `poweradvantage:distilling_refined_oil` (2 crude oil to 1 refined oil). Override it by shipping a
recipe with the same id in a data pack, or remove it with a recipe-removal mod such as KubeJS or CraftTweaker.

## Behaviour

- The Distillery has two 4,000 mB tanks: input and output. Screen labels read just "Input" and "Output"; recipes
  come from data, so no fluid is named.
- The input tank only accepts a fluid that some recipe uses. Neighbouring fluid handlers (pipes, tanks) fill the input
  tank and drain the output tank every ten ticks.
- An operation starts when the input tank holds the recipe's full input amount and the output tank has room for its
  full output. The input is consumed at the start. The output appears when the timer ends.
- **Duration** is the input:output ratio, rounded up and never below one tick, unless `ticks` is set.
  2:1 takes 2 ticks (10 operations per second); 10:2 takes 5 ticks (4 per second).
- Progress and the pending output are saved with the block. If a recipe is removed mid-operation, that operation
  still completes.
- The block's `active` state is true while an operation is running, which switches between the `still_on` and
  `still_off` models.
- If several recipes accept the same fluid, the first match wins; avoid overlapping inputs.

## API for other mods

Package `zone.moddev.mc.poweradvantage.api.distillation`:

- `DistillingRecipe`: the recipe record, with `duration()`, plus `find(level, fluidStack)` (first recipe whose input
  and amount match) and `accepts(level, fluidStack)` (any recipe uses this fluid, regardless of amount).
- `DistillingInput`: the `RecipeInput` carrying the offered fluid. It has no item slots.
- `init.Recipes.DISTILLING_TYPE` and `DISTILLING_SERIALIZER`: the registered type and serializer.

Use `DistillingRecipe.find` rather than `RecipeManager.getRecipeFor`: the vanilla method treats an input with no item
slots as empty and never matches.

## Configuration policy

Distilling recipes remain JSON data-pack recipes, customizable through data packs,
CraftTweaker or KubeJS. The legacy distilling-recipe configuration option is intentionally
not restored; it predates the current recipe customization mechanisms.
