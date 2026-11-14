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
  - Tanks resist explosions like other blocks of their hardness (1.5) instead of 6.
