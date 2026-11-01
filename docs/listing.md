# Arcane Spells: store page

The text for the Modrinth and CurseForge project pages. Paste the summary into the site's summary field and everything under "Description" into its description editor (both take Markdown). Keep it in step with the README when spells change.

## Summary

Two spell staffs, a radial spell wheel and a mana bar: five quick arcane spells and six world-shaking astral ones, with mastery, talents, affinities and corruption.

## Description

**Arcane Spells** gives you two staffs, a radial spell wheel and a regenerating mana bar. The Arcane Staff casts five quick spells that play off one another. The endgame Astral Staff holds six spells big enough to reshape the landscape: pillars of starlight that bore down to bedrock, meteor storms, a black hole that grows as it feeds, frozen time, gates across hundreds of blocks, and a planet dropped from the sky.

Every spell has its own coloured light, rune circles, particles, sounds and casting poses. The biggest ones are drawn with full-screen shaders: light bends round the black hole, colour drains out of stopped time, and the falling planet is raytraced. No shader pack is needed.

### Arcane Staff

- **Arcane Bolt:** a charged, explosive bolt. Fully charged, it bursts into shards that seek nearby creatures.
- **Chain Lightning:** leaps from creature to creature, and discharges round the last one if it runs out of targets.
- **Blink:** teleports you along your aim, and the outline you leave behind bursts a second later.
- **Arcane Ward:** a shell that absorbs damage, turns projectiles back on their shooter, and bursts outwards when broken.
- **Frost Nova:** freezes everything around you solid, and freezes still water and puts out fires.

The spells combine. A frozen creature takes half again as much from Arcane Bolt and Chain Lightning, and lightning hits creatures in water or rain harder and leaps further from them.

### Astral Staff

The Astral Staff's spells are channelled. Hold right-click while a ring marks where the spell will land, and release once the circle closes.

- **Celestial Judgment:** the sky darkens, a pillar of light bores a shaft to bedrock, and the ground detonates into a scorched crater.
- **Meteor Swarm:** 40 meteors rain on the mark, half of them seeking creatures, and then one colossal Dread Star.
- **Singularity:** a black hole drags in creatures, items and the ground itself, grows as it feeds, and then collapses.
- **Chrono Field:** time stops around you. Blows against frozen creatures land all at once, three times as hard, when it resumes.
- **Astral Gate:** opens a pair of gates up to 384 blocks apart, or home to your bed from any dimension.
- **Planetfall:** a rift opens in the sky and a burning planet falls through it.

### Progression

- **Mastery and talents:** casting earns mastery ranks and skill points. Spend the points in a talent tree for more mana, faster regeneration, shorter cooldowns and capstones.
- **Affinities:** each school of magic (Arcane, Storm, Frost and Astral) gets cheaper the more you cast it.
- **Corruption:** Astral casts build corruption, which makes Astral spells hit harder. Past halfway, each cast risks a backlash that hurts through armour.
- **Gear:** three trinkets (a ring, an amulet and a charm), three staff enchantments (Potency, Thrift and Celerity), potions of Mana, Arcane Flow and Clarity, and 12 advancements.

Press **K** to open the spell book. It lists every spell, your mastery and affinities, the talent tree, and settings.

### Before you install

- **Install it on the server and on every client.** Every player needs the same version.
- **It needs these mods:** Leyline, Fabric API, Nebulon, Player Animation Library, GeckoLib, Cardinal Components API, owo-lib and Trinkets Updated. Nebulon 0.1.2-beta+mc1.21.11.20260928.19 is published by NexusRealms.
- **Astral spells break a lot of terrain.** Blocks only break where the caster could break them by hand, and never in Adventure mode. To stop spells breaking terrain at all, run `/gamerule arcane_spells:terrain_damage false`.
- **Shader packs (Iris) and VulkanMod** haven't been tested. Without the shader passes, the biggest spells fall back to simpler visuals.

Other mods can add their own spells and staffs through the API, documented on [GitHub](https://github.com/CarsonKopec/ArcaneMod).
