# Optional per-world compatibility packs

Power Advantage offers built-in data packs through NeoForge's pack repository.
They are not forced active and are not automatically selected for normal worlds.
Select them during world creation or use the vanilla data-pack commands. Selection
is saved with the world; it is not a client preference or a global TOML setting.

## Bioplastic as rubber

Enable **Power Advantage: Bioplastic as Rubber**, or run:

```mcfunction
datapack enable "mod/poweradvantage:optional_packs/bioplastic_as_rubber"
```

The pack adds `poweradvantage:bioplastic_ingot` to `c:rubbers` and
`c:ingots/rubber`, preserving all existing entries. This restores the purpose of
PA2's `plastic_equals_rubber` option without changing another mod's items or tags
in code. It does not add rubber items or recipes. Bioplastic's plastic tags remain
available regardless of this pack.

Disable it with:

```mcfunction
datapack disable "mod/poweradvantage:optional_packs/bioplastic_as_rubber"
```

The commands reload server data. Disabling removes this pack's contributions;
other packs may still independently classify bioplastic as rubber. Power
Advantage keeps the pack opt-in to preserve PA3's existing tag behavior, although
PA2's old global option defaulted to enabled.

The pack's files live under `src/main/resources/optional_packs/bioplastic_as_rubber`.
`init.OptionalPacks` registers its stable ID and discovery metadata. Data packs
that want rubber equivalence can also make the same tag additions directly.

## Verification

GameTests verify repository discovery and optional selection metadata. A separate
reload batch explicitly disables the pack, enables it, checks both real item tags,
disables it again, and restores the server's original pack selection. The GameTest
server initially selects all available packs, so the test establishes its own
baseline rather than assuming an optional pack starts disabled there.
