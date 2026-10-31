# Item filters

The eight PA2 filters are available as `poweradvantage:item_filter_block`, `_food`,
`_fuel`, `_inventory`, `_ore`, `_overflow`, `_plant`, and `_smelt`.
Their existing models and textures are reused, with modern blockstate model paths.
Each has a restored shaped recipe, a self-drop loot table, and an MMDLib XML screen.

## Placement and routing

Place a filter like a conveyor: nonmatching items travel in the direction the
player faces. Feed cargo from behind or above. Matching cargo goes down; other
cargo goes forward. Either output can feed a sided item inventory or another belt.
The receiving belt owns the items as physical cargo and moves them normally.
A blocked matching output holds its cargo; it does not redirect it forward.
If matching cargo has no inventory below and the lower block has no collision or
block entity, the filter drops an ordinary item entity into that space.
Unloaded outputs pause transfers without loading their chunks.

Filters are functional machines, not ordinary belt segments. Each has a block
entity containing one cargo slot. Standard conveyor blocks continue to have no
block entities: their cargo remains in the dimension-owned conveyor store.
Filters pull from an adjacent upstream inventory when empty; upstream belts push
into the filter when their physical cargo arrives at the endpoint.

| Filter | Matching items |
| --- | --- |
| Block | Block items |
| Food | Stacks with the current food component |
| Fuel | Positive furnace burn time, including mod-provided fuel |
| Inventory | Item type matching one of nine sample items |
| Ore | Common `c:ores`, `c:ingots`, `c:dusts`, `c:nuggets` tags, including their subgroups |
| Overflow | Uses the lower inventory if it accepts anything; otherwise uses forward output if it accepts anything |
| Plant | Saplings, flowers, growable/bush blocks, cactus, sugar cane and their block items, including crop seeds |
| Smelt | Inputs accepted by a current smelting recipe |

Overflow preserves the actual PA2 lower-output preference, despite its name.
When neither inventory accepts an item, it selects the lower output: it drops
into empty space or waits at a blocked lower output. Acceptance is simulated on
live sided handlers each attempt; a partially accepted stack retains its remainder.

## Screens and samples

MMDLib loads `mmdlib/screen/item_filter_<kind>.xml`, binds it to the matching entity
type, opens the menu through the `mmdlib:open_menu` block event, and handles slot
synchronization, player inventory and interaction. There is no custom filter menu
or screen renderer in PA3.

The inventory filter has nine real sample slots in addition to cargo. Each sample
slot holds one item, matches by item type, and stays in the machine until removed
by a player. These are not ghost slots. Shift-clicking from player inventory sends
items only into cargo; placing samples is deliberate. Automation sees only the
cargo slot on every face and can never take or fill samples. Breaking the filter
drops cargo and samples; rotating it preserves both.

A comparator reads 0 when cargo is empty, 15 for downward-selected cargo, and 7 for
forward-selected cargo. For overflow this classification depends on current output
acceptance.

## Pacing and extension

The shared implementation is in `filter/`: `FilterBlock`, `FilterBlockEntity`,
`FilterPredicates` and `FilterSettings`. `init/FilterContent` registers their public
MMDLib factories under `poweradvantage:item_filter`. New standalone plans can reuse
the module or factories with their own entity settings:

```json
{
  "module": "poweradvantage:item_filter",
  "block_entity_data": {
    "kind": "FUEL",
    "interval": 4,
    "batch": 32
  }
}
```

`interval` is 1–1200 server ticks between attempts; `batch` is 1–64 items per
attempt. Defaults are 8 ticks and 16 items. Empty machines pull on an attempt;
loaded machines push on an attempt. Cargo and remaining cooldown persist across
saves. External automation can extract cargo directly through its selected output;
the pacing settings govern the machine's own work, rather than that automation.

A consuming mod must provide an XML screen with the exact registered entity ID and
register its entity's item capability (using `FilterContent.registerCapabilities`).
The standard kinds are intentionally fixed; custom predicates can be added as a
separate implementation behind the same endpoint contract.

Simulation never moves cargo. Live capability views read the current facing and
reject reentry during a transfer; a cached handler cannot extract from a removed
machine. Rejected transfer remainders return to cargo. If a destination callback
removes the filter, outstanding remainders drop at the original location rather
than being restored into an orphaned block entity.

## Verification

Server GameTests cover all eight registrations and recipes, XML menu construction,
predicates, sample persistence and isolation, physical belt delivery, blocked and
partial outputs, PA2 overflow behavior, item drops on removal, and configurable
batch/interval behavior across a save/load cycle. A client startup smoke test checks
resource loading; interactive GUI layout and manual gameplay still need visual testing.
