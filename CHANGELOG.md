# Changelog

Each version's section is also its release notes on Modrinth and CurseForge: `gradlew publishMods` uploads the section headed with the version being released.

## 0.8.0

The first public release. What changed since 0.7.0:

**Now needs Leyline.** The effects, terrain and screen-shader code that Arcane Spells' spells are drawn with is now Leyline, a separate library mod: install Leyline 0.1.0 alongside Arcane Spells, on the server and every client. 0.8 is not network-compatible with 0.7.

- **Singularity grows as it feeds.** Every block, item and orb it takes in, and every creature it crushes to death, makes it bigger, up to three times its first size, and its collapse grows with it: at its largest, up to 120 damage within 72 blocks.
- **Meteor Swarm hits far harder on the ground.** Each meteor's blast reaches 10 blocks (was 6) and carves a crater 5 blocks out and 3½ deep whatever the ground is made of. The Dread Star's reaches 32 (was 16) and carves one 16 out and 9 deep, with seared floor and walls.
- **Planetfall's impact is 1.4 times bigger.** The pressure front reaches 100 blocks (was 72) and the crater is 62 blocks out and 39 deep (was 44 and 28). The least casting distance is now 100 blocks (was 72).
- **Chrono Field's clock hands** are drawn by the space-time shader pass, so they keep their colour inside the field and stay visible at any distance. The second hand now spins back as time rewinds.

**For addon authors:** spell effect messages moved to Leyline. `ArcaneSpellsApi.sendEffect` and `SpellRegistries.SPELL_EFFECTS` are gone: register effect types in `LeylineRegistries.EFFECT_TYPES`, send them with `Effects.send` and draw them with `EffectRenderers`. Depend on `"arcane_spells": ">=0.8.0"`.
