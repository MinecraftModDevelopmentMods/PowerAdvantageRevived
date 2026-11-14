# Changelog

## 3.2.0 (unreleased)

- Update MMDLib dependency (now requires 2.0.2 or later, and NeoForge 21.1.265 or later)
- Machines are moving to MMDLib 2.0.2's XML machine definitions (`mmdlib/machine/*.xml`). Worlds from Power
  Advantage 3.0 and later keep their machines and contents; worlds from the 2.x series are not supported.
  - The Portable Fluid Tank and Metal Fluid Tank are XML machines. Their screens are now MMDLib's standard machine
    screen, with a tank gauge you can click with buckets and other fluid containers, plus side configuration and
    redstone drawers.
  - Tank redstone output is set per face in the redstone drawer, as the "Fill level" source. Every face emits it by
    default, the same signal 3.1 tanks gave on every face, so existing contraptions keep working.
  - Comparators no longer read tanks; use the redstone output instead.
  - The Distillation Furnace is an XML machine. `poweradvantage:distilling` is now an MMDLib machine recipe type, so
    distilling recipes use MMDLib's machine-recipe JSON (see `docs/distillation.md`); 3.0/3.1 recipe files must be
    rewritten. The default recipe is 100 crude oil to 50 refined oil in 100 ticks: the same ratio, and nearly the
    same rate (MMDLib adds one tick per cycle to pick the recipe). Its input is the new `poweradvantage:crude_oils`
    fluid tag (`c:crude_oil` and `c:oils/crude`).
  - The furnace front lights up while distilling, from MMDLib's conditional model parts.
  - New Fluid Intake Pump and Fluid Output Pump: MMDLib automation addons that pull fluid into, or push it out of,
    any MMDLib machine's fluid group. The Distillery no longer moves fluid to and from its neighbours by itself;
    install a pump in its input and output groups for 3.1's behaviour. Existing stills keep their tank contents.
  - `api.distillation` is deprecated but still works for addons built against 3.0/3.1: `DistillingRecipe.find` and
    `accepts` now read the machine recipes. `init.Recipes` is removed.
  - The Infinite Power Source, Infinite Steam Source, Drain and Discharge Grate are XML machines, with their 3.1
    behaviour in Power Advantage processors. Each can be paused from its screen. The Infinite Power Source still
    pushes 4096 FE per tick into every neighbour, but other blocks can no longer pull power out of it.
  - The Electricity-RF and Steam-RF Transformers are XML machines with the same exchange ratios, buffers and
    neighbour service as 3.1. Steam in existing transformers is kept; buffered FE and electricity are not. Other
    blocks can still draw electricity and steam from them, but FE now leaves only through the transformer's own
    pushing. Their sizes and rates are no longer set through block-entity data.
  - XML machines resist explosions like other blocks of their hardness (1.5) instead of 6.
  - The eight item filters are unchanged. Their per-item routing needs their own item capability, which XML
    machines don't support yet; see `docs/item-filters.md`.
