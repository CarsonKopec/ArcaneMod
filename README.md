# Arcane Spells

A Fabric mod for **Minecraft Java 1.21.11**. It adds two staffs whose spells are chosen from a radial spell wheel and paid for with a regenerating mana bar. The Arcane Staff casts five everyday spells: Arcane Bolt, Chain Lightning, Blink, Arcane Ward and Frost Nova. The endgame Astral Staff holds six devastating advanced spells: Celestial Judgment, Meteor Swarm, Singularity, Chrono Field, Astral Gate and Planetfall. [Nebulon](https://maven.riftrealms.de/releases/de/nexusrealms/nebulon/) supplies colored dynamic lighting, detailed rune circles and electrical arcs. No shader pack is required.

## Install

1. Install Fabric Loader **0.19.5 or later** for Minecraft **1.21.11** (https://fabricmc.net/use/installer/). Minecraft needs Java 21 or newer.
2. Put these eight JARs in the instance's `mods` folder. Choose the **Fabric / Minecraft 1.21.11** files:
   - `arcane-spells-0.7.0.jar` (from `build/libs/`, not the `-sources` JAR)
   - [Fabric API 0.141.6+1.21.11](https://modrinth.com/mod/fabric-api/versions?g=1.21.11)
   - [Nebulon 0.1.2-beta+mc1.21.11.20260928.19](https://maven.riftrealms.de/releases/de/nexusrealms/nebulon/0.1.2-beta+mc1.21.11.20260928.19/nebulon-0.1.2-beta+mc1.21.11.20260928.19.jar)
   - [Player Animation Library 1.1.10+mc.1.21.11](https://modrinth.com/mod/player-animation-library/version/wJLdv4ZQ)
   - [GeckoLib 5.4.5](https://modrinth.com/mod/geckolib/version/G1BvHQDL)
   - [Cardinal Components API 7.3.2](https://modrinth.com/mod/cardinal-components-api/version/HZMLTuxY) (the complete release JAR)
   - [owo-lib 0.13.0+1.21.11](https://modrinth.com/mod/owo-lib/version/33Ei8Thm)
   - [Trinkets Updated 3.11.0-beta.2+1.21.11](https://modrinth.com/mod/trinkets-updated/version/3.11.0-beta.2+1.21.11)
3. For multiplayer, install all eight on the server **and** every client, with the same Arcane Spells version on both. 0.7 is not network-compatible with 0.6.

These libraries power player casting poses, animated 3D staffs, saved player progression, the spell book and settings screens, and the trinket slots.

## Play

- Find the **Arcane Staff** in the Combat creative tab, or run `/give @s arcane_spells:arcane_staff`.
- Craft it with an amethyst shard (top right), a gold ingot (center) and a stick (bottom left).
- The **Astral Staff** (`/give @s arcane_spells:astral_staff`) is its endgame upgrade: an Arcane Staff (center), a nether star (top right), two echo shards (top middle and middle right) and a netherite ingot (bottom left). It survives fire and lava. Each staff casts only its own spells.
- **Choose a spell:** with the staff in hand, hold **R** (rebindable under Controls → Arcane Spells), point at a spell and release. Clicking also picks; Escape cancels. The staff remembers its spell, and its tooltip shows which.
- **Spell book:** press **K** to browse every spell, select one for your held staff, inspect mastery and affinities, or spend skill points in the talent tree. Both book and wheel use owo-lib. The book's Settings button opens saved client options for player and staff animations, and for Planetfall's glow.
- The grimoire uses gilded controls with distinct hover, keyboard-focus, selected and locked states. Spell sigils, a live mana gauge and mastery gems show your progress; the selection button stays visible while spell details scroll. Layout and text adapt to the window size.
- **Talent tree:** the Talents page is a tree that grows from a sigil showing your unspent points. Each rank is a node; hover one for its effect, price or requirement, and click it, or reach it with the arrow keys and press Enter, to learn it. Nodes you can afford pulse gold, learned nodes light up in their branch's colour, and light runs along the branches as far as you have grown.
- **Practice and talents:** every five successful casts that spend mana earn one mastery rank and one skill point, up to ten ranks per spell. Free and Creative casts award no progress. The tree has three branches of three ranks, costing 1, 2 and 3 points: Reservoir grants +20 maximum mana per rank, Recovery grants +0.5 mana per second, and Focus reduces new cooldowns by 5% per rank. A complete branch opens its capstone, for 4 points: Wellspring (after Reservoir) takes 5% off every spell's mana cost, Echo (after Recovery) returns a tenth of the mana each spell spent, and Tempo (after Focus) makes Astral spells' new cooldowns 10% shorter. All three capstones open Archmage, for 6 points, which doubles affinity XP. The whole tree costs 36 points. Raising maximum mana does not refill it. Knowledge and talents survive death.
- **Affinities:** paid casts grant affinity XP in the spell's school (Arcane, Storm, Frost or Astral), one per point of mana spent. Every 500 XP takes 1% off that school's mana costs, up to 10% at 5,000; the grimoire's Progression page shows each school's discount and when the next comes.
- **Corruption**, the Astral Staff's price and its power: every paid Astral cast builds a quarter of the mana it spent as corruption (at least 1; Celestial Judgment builds 23), up to 100. Each point makes Astral spells hit 0.5% harder, up to +50% at 100. From 50 on, a paid Astral cast may lash back at its caster, a 1% chance for each point over 50 (so 50% at 100). The spell still goes off, but the backlash deals 2 damage plus 0.1 per point of corruption (7 to 12) through armour, darkens the caster's sight for 4 s and burns off 30 corruption. It can kill. Corruption fades by one point every two seconds while not using an item. While there is any, a slim violet bar above the mana bar shows it, with a notch at 50; past the notch the bar throbs pink and a violet haze closes in at the edges of the screen (following Screen Effect Scale). The grimoire's Progression page shows your corruption, the power it adds and your backlash risk, and each Astral spell's page shows the corruption it builds. Free and Creative casts build none and never backlash.
- **Casting visuals:** both staffs have animated 3D crystals and crowns, with idle, channel and successful-release animations. In third person, the player uses distinct Arcane and Astral casting poses, mirrored for the casting hand. Planetfall has its own: a gathering channel that settles into a held ready pose once fully charged, and its own release. Short or refused casts do not play a success gesture.
- **Cast:** right-click. Arcane Bolt and Celestial Judgment are held to charge and fire on release; the other spells fire on click.
- **Trinkets:** three magical trinkets, worn in Trinkets' slots. Right-click one to put it on, or place it in its slot in the inventory: one ring slot sits beside the player, and the other ring, necklace and belt slots open from the off-hand, chestplate and leggings slots. Worn, the amulet hangs at the wearer's chest and the charm at the right hip; rings are too small to show.
  - **Arcane Ring** (ring, on either hand, so two can be worn): +20 max mana each. Craft an amethyst shard over three gold ingots set in a ring.
  - **Astral Amulet** (necklace): +1 mana per second. Craft four gold nuggets over an eye of ender.
  - **Chrono Charm** (belt): new spell cooldowns are 15% shorter. Craft string over an amethyst shard, a clock and another amethyst shard.
- **Mana:** 100, refilling at 5 per second. Both are player attributes, `arcane_spells:max_mana` and `arcane_spells:mana_regen`, so gear, effects, other mods and `/attribute` can change them. Raising the maximum doesn't refill the pool. The bar sits above the food bar while you hold the staff or are refilling. The active spell shows in a slot beside the hotbar, dimmed when you can't afford it and with a cooldown sweep. A refused cast (no mana, or still recharging) fizzles and says why above the hotbar. Creative players cast for free; cooldowns still apply.
- **Spell power:** every spell's damage is multiplied by the caster's spell power, 1 by default. It is a player attribute too, `arcane_spells:spell_power`, and Potency and corruption add to it. A spell keeps the power it was cast with. Each spell's page in the grimoire shows it.
- **Staff enchantments:** both staffs take three enchantments, each up to level III, from the enchanting table, books on an anvil, loot and librarians, as vanilla's do. **Potency** makes the staff's spells hit 10% harder per level, **Thrift** makes them cost 8% less mana per level and **Celerity** makes their new cooldowns 8% shorter per level. They apply to the spells the enchanted staff casts.
- **Potions:** brewed from an Awkward Potion, like vanilla's, and each also comes as a splash or lingering potion and a tipped arrow.
  - **Potion of Mana** (amethyst shard): restores 40 mana at once; glowstone makes it 80. A splash restores less the farther from its centre.
  - **Potion of Arcane Flow** (lapis lazuli): +2.5 mana per second for 3 minutes; redstone makes it 8 minutes, glowstone +5 for 90 s.
  - **Potion of Clarity** (glow ink sac): for 90 s (4 minutes with redstone), corruption drains ten times as fast, even while you cast, and no Astral spell backlashes.
- **`/arcane`** (operators): `/arcane mana get|set|add|fill`, `/arcane corruption get|set|add`, `/arcane cooldowns reset` and `/arcane talents reset` (which gives back the points spent), each for one or more players.
- **Advancements:** an Arcane Spells tab of 12, from taking up a staff and casting your first spell, through casting every Arcane Staff spell, mastering a spell, learning Archmage, enchanting a staff and brewing a Potion of Mana, to the Astral Staff, Planetfall, a backlash and purging corruption with Clarity.
- Every spell has its own sounds, with subtitles (Options → Accessibility → Show Subtitles). They ring out as if in a stone hall, impacts carry a sub-bass weight, and the commonest come in three takes, so repeats don't sound alike.

| Spell | Mana | Cooldown | Effect |
| --- | --- | --- | --- |
| Arcane Bolt | 25 | 0.6 s | Charged explosive bolt: up to 24 damage on a direct hit, 20 splash over 8 blocks, breaks terrain. Flies 400 blocks. **Shardburst:** a fully charged bolt also bursts into up to 4 shards, each seeking a different creature within 8 blocks of the blast, nearest first, for 4 damage. |
| Chain Lightning | 25 | 2.5 s | Strikes the creature you aim at within 24 blocks (10 damage), then leaps up to 4 more times to the nearest target within 7 blocks, each leap 20% weaker. Needs a clear path for every leap. **Overload:** if it runs out of targets, each leap it had left discharges round the last creature it struck, 3 damage within 3 blocks apiece. |
| Blink | 20 | 3 s | Teleports up to 16 blocks along your aim, stopping short of walls. Fails, without cost, if there's no room at least 2 blocks away. Resets fall damage. **Afterimage:** a second later, the outline you left behind bursts: 6 damage and a shove within 3 blocks. |
| Arcane Ward | 40 | 15 s | An 8 s shell that absorbs 20 damage from attacks, projectiles and explosions (not falls, fire or hunger), and turns incoming projectiles back on their shooter. The hit that breaks it is still fully absorbed. **Rebound:** when a blow breaks it, it bursts outwards: 6 damage and a hard shove within 4 blocks. |
| Frost Nova | 35 | 8 s | Everything within 6 blocks takes 6 damage (15 for blazes and other heat-loving mobs), Slowness III for 5 s and is frozen solid, as in powder snow (strays, snow golems and anyone wearing leather don't freeze). **Rime:** still water in reach freezes to frosted ice, which melts back as Frost Walker's does, and fires and burning creatures go out. |

**The Arcane Staff's spells play off one another.** **Shatter:** Arcane Bolt (direct hit or splash) and Chain Lightning hit a creature frozen solid, by Frost Nova or powder snow, half again as hard, and break the ice. **Conduction:** Chain Lightning hits a creature in water or rain half again as hard, and leaps half again as far from it (10.5 blocks). A frozen, wet creature takes both. Each leap is still 20% weaker than the last before any reaction, so a reaction's bonus doesn't carry down the chain.

**The Astral Staff's spells** are all channelled: hold right-click while a ring, in the spell's colour, marks where it will land (red where it can't), and an arc sweeps round the ring as the channel fills. Once the circle closes, release to cast. Releasing early, aiming at nothing, or marking somewhere too close to yourself casts nothing and costs nothing.

| Spell | Mana | Cooldown | Channel | Effect |
| --- | --- | --- | --- | --- |
| Celestial Judgment | 90 | 45 s | 2 s | Calls a pillar of starlight down on ground up to 128 blocks away (at least 30): see below. |
| Meteor Swarm | 75 | 35 s | 1.5 s | A burning sigil opens 70 blocks over ground up to 128 blocks away (at least 24) and rains 40 meteors across 24 blocks of it over 4 s: each up to 30 damage in a 6-block blast, setting things alight, with a charged creeper's crater (strength 6; TNT is 4). **Seeking:** every other meteor aims at a creature in the area, if there is one. Then the **Dread Star**: one colossal meteor straight onto the mark, up to 80 damage within 16 blocks, with a strength-12 explosion, its shockwave driving a wall of dust out across the ground. |
| Singularity | 70 | 35 s | 1.5 s | A black hole hangs 6 blocks over ground up to 96 blocks away (at least 24) for 10 s. It drags in creatures (players under PvP rules), items, experience, projectiles and falling blocks within 32 blocks, crushes anything at its heart (10 damage every half second), and devours everything within 12 blocks of it, tearing blocks loose to fly into it. Items and experience that reach its heart are held there, unharmed. For its last 4 s it becomes a **true black hole**: black, the world behind it bent round it as light bends round a real one, a white-hot disc whirling about it. Then it collapses: up to 60 damage within 24 blocks, everything flung outwards (what it held, too), and a strength-8 explosion. Nothing caught takes fall damage while it's pulled. |
| Chrono Field | 60 | 40 s | 1 s | Time stops in a 32-block dome around you for 15 s. Creatures you could hurt, projectiles (yours too) and lit TNT freeze where they are, as does anything that comes in. You move freely, and inside it fight with Speed II, Strength III and Haste II. Inside, the world is drained of its colour, and a clock on the ground ticks off the seconds. Damage dealt to frozen creatures is held back, then lands all at once, three times as hard, when time resumes, as colour floods back out from the centre. **Rewind:** projectiles that weren't yours fly back the way they came, as yours. |
| Astral Gate | 50 | 15 s | 1 s | Opens gates at your feet and on solid ground with room to stand, up to 384 blocks away (at least 8), and you step through. **Look straight up** as you release to gate home instead, to your bed, anchor or world spawn, even from another dimension. Both gates stay open 20 s and work both ways, for anyone and anything that steps in: step out of a gate and back in to return. **Astral Wake:** whatever comes through comes out with Speed II for 5 s. |
| Planetfall | 100 | 120 s | 3 s | Drops a burning planet on ground up to 160 blocks away (at least 72): see below. |

**Celestial Judgment** in full:

1. **The sky darkens** (2.5 s). Storm clouds wheel in around a vast seal 80 blocks up, a seal forms on the ground, light streams upwards, lightning strikes around the mark (for show: no fire, no damage) and the view darkens and starts to rumble.
2. **The beam** (1.5 s). A pillar of light slams down and bores a 12-wide shaft from the surface (through trees and roofs) **all the way down to bedrock**, and cracks of gold race out across the ground from its foot. Anything in it takes 30 damage every half second and burns for 10 s.
3. **The detonation.** Everything within 32 blocks takes up to 100 explosion damage and is hurled outwards. The ground is blasted into a crater roughly 48 wide and 16 deep, carved outwards over 1.5 s, its floor and walls scorched to blackstone, basalt and glowing magma. A fireball blooms, a shockwave races out, smoke rises, embers rain down, and the crater smoulders for 15 s, its rim longest.

**Smite:** undead take half again as much from the beam and the detonation.

**Planetfall** in full:

1. **The channel** (3 s). As you channel, the sky starts to give way for you alone: it dims towards space and the stars come out, the view narrows and its edges darken.
2. **The rift opens** (3 s). The sky turns to space, even at noon: nebulae, a galaxy's band, twinkling stars and a ringed giant hanging over the horizon. 150 blocks over the mark (a little beyond it, away from you) the sky tears open onto another universe: a ragged hole with a white-hot lip and a swirling throat, bending the world around its rim as a black hole would, with cracks of light running out across the sky as if it were glass. Far beyond it, the planet can be seen coming. A seal burns on the ground, and the view is drawn up towards the rift. Within 48 blocks of the mark gravity fails: creatures (players under PvP rules) float up, items and experience drift upwards, and up to 160 blocks of the ground tear loose and rise. If you are caught, your view drifts and sways.
3. **The planet falls** (4 s). A ringed planet 40 blocks across, with obsidian seas, ash continents and amethyst highlands, comes through the rift spinning and falls faster and faster. As it heats, molten faults widen between its plates, a bow shock wraps it and a long wake of plasma streams behind it, and it lights the ground below orange. The view is drawn to it, widens and rolls. Anything it touches takes 1000 damage, and it ploughs through trees, hills and buildings in its way. In the second half of its fall, burning shards break off and streak down, each striking somewhere 24 to 48 blocks from the mark: up to 24 damage within 6 blocks, setting things alight, and a charged creeper's crater.
4. **The impact.** A white flash, and for an instant the world inverts to stark silhouettes. Gravity returns and the floating ground is flung away. A dome of shocked air races outwards, bending the light as it goes: the pressure front reaches 72 blocks in 1.4 s and hits each creature once as it passes, for up to 400 damage (less the farther out) and a hurl outwards. The boom arrives with the front, not with the flash, so far away there is a silence first; the view is kicked as it hits. The planet shatters into tumbling blocks, slabs of the real ground are thrown out as the rupture spreads, a dust wall rolls outwards and a mushroom cloud rises. Over about 5 s a crater roughly 88 wide and 28 deep is torn out from the centre: terraced walls lined with blackstone, basalt and glowing magma, nine branching fissures running past the rim, banks of rubble heaped around it, and the planet's glowing core left at the bottom, studded with gold ore, diamond ore and a little ancient debris for you to dig out (**Riches**). The sky clears over 5 s; ash drifts down over the crater for half a minute, and embers rise from the core.

The sky, the rift, the planet, its light on the ground and the shock dome are drawn by a full-screen shader pass. With a shader pack on, they are drawn the older way instead, with rings of light and a planet built of blocks; the same happens if the pass ever fails, and the game log says so. On Intel integrated graphics the pass leaves out its glow (bloom and lens streaks), which hung Intel's driver; everything else is drawn as usual. With a newer driver, set Planetfall glow to On in the spell book's Settings (Off turns it off anywhere). Lower Particles settings give the shattered planet (and the planet itself, without the shader pass) fewer, larger blocks.

Singularity's black hole and Chrono Field's colourless world are drawn by a second, lighter screen pass, with the same fallbacks: without it, the singularity stays a knot of ink inside glowing rings to the end, and a field washes the view teal instead. It has no glow, so it runs in full on Intel graphics.

Blocks Celestial Judgment, Singularity and Planetfall break are vaporised rather than dropped (containers still spill what they held), and obsidian, anything as blast resistant, and anything unbreakable survive. Meteors, the Dread Star and the singularity's collapse break terrain like the Arcane Bolt, with vanilla explosions and drops. Every spell follows the bolt's rules for terrain, below, as they stood when it was cast; Planetfall's rubble, too, only lands where the caster could build. A server runs at most 8 Judgments, 8 swarms, 4 singularities, 4 chrono fields, 8 gates and 2 Planetfalls at once; more fizzle.

- Spells never hurt the caster, and Chain Lightning, Frost Nova and the bolt's direct hit pass over the caster's mount. PvP follows the server's rules.
- Arcane Bolt breaks terrain using vanilla explosion resistance and drops (strength 3–6; TNT is 4). It can set off TNT but never starts fires. Blocks only break where the caster could break them by hand: in Adventure mode, under spawn protection and beyond the world border, the blast still hurts entities but leaves terrain (and TNT) alone.
- **To stop spells breaking terrain at all,** for example on a shared server, run `/gamerule arcane_spells:terrain_damage false` (or switch off "Spells break terrain" under Miscellaneous in the world's game rules). Every spell still hurts, burns and throws creatures, but no spell breaks a block or sets off TNT, in any game mode. It's on by default.
- Spell particles follow Video Settings → Particles: **All** shows everything, Decreased halves them and Minimal turns them off, leaving the lights and arcs. They appear within 64 blocks of the camera (192 for the Astral Staff's spells).
- Spells' screen shake (Celestial Judgment's and Planetfall's above all) follows Options → Accessibility → Screen Effect Scale (none at 0%), and their flashes follow Hide Lightning Flashes. Both, and the darkening, weaken with distance from the strike. Planetfall also works the camera: its roll, the pull of your view, the bending of the image and the darkened edges follow Screen Effect Scale, its widening and narrowing of the view follows FOV Effects, and its impact frame is skipped with Hide Lightning Flashes.

## Develop

The build tools need **JDK 25**; the mod itself targets **Java 21**.

### Library foundation

Dependency versions are pinned in `gradle.properties`, with their Maven repositories and source-set setup in `build.gradle`. Keep the Fabric 1.21.11 releases: newer Minecraft 26.x artifacts are a different compatibility line.

| Library | Pinned version | Intended role and official documentation |
| --- | --- | --- |
| Player Animation Library (PAL) | `1.1.10+mc.1.21.11` | Player casting poses and animation layers. [Setup and documentation](https://docs.zigythebird.com/pal/gettingstarted/how_to_add_lib_to_mod/). |
| GeckoLib | `5.4.5` | Animated staff, spell entity and equipment models. [GeckoLib 5 documentation](https://github.com/bernie-g/geckolib/wiki). |
| Cardinal Components API (CCA) | `7.3.2` | Saved mana, cooldowns, mastery, affinities, corruption and talents, synchronized to their owner. Development uses only `cardinal-components-base` and `cardinal-components-entity`; players install the complete release JAR linked above. [Development setup](https://ladysnake.org/wiki/cardinal-components-api/dev-install). |
| owo-lib | `0.13.0+1.21.11` | Radial spell wheel, spell book, talent tree and generated client configuration screen. `annotationProcessor` and `clientAnnotationProcessor` generate configuration wrappers. [Official setup](https://github.com/wisp-forest/owo-lib). |
| Trinkets Updated | `3.11.0-beta.2+1.21.11` | Ring, necklace and belt slots for the trinkets. Patbox's fork of Trinkets, the only accessory-slot library with a 1.21.11 build; same `dev.emi.trinkets.api` API. From Modrinth's Maven. [Source](https://github.com/Patbox/trinkets). |

`PlayerProgressionComponent` is the CCA authority for player state. Existing Fabric `arcane_spells:caster` attachment saves are imported lazily, including mana and cooldowns, then retired; existing CCA data takes precedence. Normal death resets mana and cooldowns as before, while retaining progression; lossless respawns preserve everything. Talent purchases are server validated, and their attribute bonuses are transient modifiers reapplied after loading. `CasterState` and `ArcaneSpellsApi` retain the shared access path for the HUD and add-ons.

PAL's upper-body animations live in `assets/arcane_spells/player_animations/`. GeckoLib's staff geometry and animations live in `assets/arcane_spells/geckolib/`; regenerate geometry with `python tools/generate_staff_models.py`. Staff animation release packets are sent only after successful casts, and carry which set of poses to play, so changing the selected spell can't change a release already earned. Armor, grimoires, mobs and summons can use the same GeckoLib foundation but do not yet have new content or models.

**Equipment slots come from Trinkets Updated.** [Curios](https://modrinth.com/mod/curios/version/14.0.0%2B1.21.11) targets NeoForge and cannot load in this Fabric project, and the official [Accessories](https://github.com/wisp-forest/accessories) and [Trinkets](https://github.com/emilyploszaj/trinkets) have no 1.21.11 release. Trinkets only enables the slots mods ask for: `data/trinkets/entities/arcane_spells.json` turns on the ring, necklace and belt slots for players, and item tags under `data/trinkets/tags/item/` say which trinket fits which slot. `ArcaneTrinketItem` gives a trinket its attribute bonus while worn; the Chrono Charm's cooldown cut is a `SpellEvents.MODIFY_COOLDOWN` listener in `ModItems`. Their numbers are in `SpellTuning`, and their icons are drawn by `tools/generate_assets.py`.

### Build and test

| Task | Command (Windows; use `./gradlew` elsewhere) |
| --- | --- |
| Build the mod JAR | `gradlew.bat build` → `build/libs/` |
| Launch a dev client | `gradlew.bat runClient` |
| In-game integration tests | `gradlew.bat runClientGameTest` |
| Regenerate spell icons, textures and sounds | `python tools/generate_assets.py` (needs `pip install numpy scipy pillow soundfile`) |

The spell icons (`textures/gui/sprites/spell/`), the Astral Staff and beam textures and the sounds (`sounds/`) are drawn and synthesized by `tools/generate_assets.py`, so there are no third-party assets to license. (The Arcane Staff's texture predates it.) Edit a function there and rerun it to change one; add `--preview DIR` to also get upscaled icons and waveform/spectrogram plots for checking sounds by eye. A resource pack can replace any icon or sound by path.

The client game tests run in a real client and save screenshots to `build/run/clientGameTest/screenshots/`:

- `LibraryUiGameTest` opens the book with K, selects a spell, navigates progression, learns talents by clicking nodes of the tree (and is refused a locked capstone), checks every node stays on the page without overlapping, resizes the window, toggles both generated settings, and preserves the R wheel's hold/release behavior. It captures both staff models and checks PAL's channel/release layers fade out.
- `TrinketsGameTest` checks that players have the ring, necklace and belt slots, that each trinket fits only its own, that two rings, the amulet and the charm add their bonuses (on the server and the client, whose HUD and wheel read them) while a third ring has nowhere to go, and that taking them off removes every bonus. It photographs the inventory's necklace slot and the ring's tooltip.
- `ProgressionGameTest` connects to a dedicated server and tests legacy mana/cooldown import, CCA save/load, owner-only synchronization, paid-cast mastery and affinity, server-validated talent purchases, attribute bonuses, the capstones' prerequisites and effects, and death versus lossless respawn copying.
- `LightingGameTest` fires a real charge and bolt. It checks that a Nebulon light renders, that a bolt cast in Adventure mode or with `terrain_damage` off leaves a stone wall standing while a normal bolt destroys it, and that no light handles leak afterwards.
- `SpellsGameTest` checks that every spell sound, and every take of each, has a file and every icon loaded, then picks Chain Lightning on the spell wheel with simulated key and mouse input, then casts it with a real right-click. It checks that all five spells affect the world as described above, and that mana, cooldowns and an empty mana pool are enforced.
- `JudgmentGameTest` checks that the Astral Staff holds Celestial Judgment (and the Arcane Staff never can), that a short channel, a mark too close and no mark at all are refused without cost, and that a strike in Adventure mode or with `terrain_damage` off leaves terrain alone. It then channels a real two-second right-click on a stone plateau, and checks the crater, that the shaft reaches bedrock without breaking it, that obsidian survives, that the walls are scorched, and who the strike killed, hurt and spared. Smite is checked under one beam: a zombie must take half again what a pig beside it does. Screenshots cover the channel and every phase, from a spectator's vantage point, and the cracks from closer.
- `AstralSpellsGameTest` checks that the Astral Staff holds all six spells and shows them on the wheel, then casts the other four on a stone plateau: Meteor Swarm cratering it and hitting pigs, and seeking out a pig at the area's edge far more often than chance would; Singularity dragging pigs and a dropped diamond in, eating the ground, drawing its black hole with the space-time pass (which stops once it collapses), crushing what it caught and throwing the diamond back out; Chrono Field freezing a wandering pig and an incoming arrow, draining the colour, holding back a blow until time resumes (then dealing it three times over), letting the pig move on and flinging the arrow back the way it came, now the caster's; and Astral Gate refusing a spot too close, carrying the caster and a pig standing in the gate 54 blocks away, both with Astral Wake's Speed II, bringing the caster back when they step out of the far gate and in again, and gating home to their respawn point when they look straight up. Chrono Field also checks the caster is empowered. Both Astral tests build an 81-block stone plateau (`Plateau`) wide enough for the crater, and use `DamageLog`, which logs and cancels every blow aimed at a tagged mob, to count a spell's hits and compare their amounts exactly.
- `PlanetfallGameTest` checks that a short channel is refused without cost, and that a strike in Adventure mode or with `terrain_damage` off leaves terrain alone. It then channels a real three-second right-click at a broad, deep stone plateau and checks the crater: deep enough, with obsidian, bedrock and the ground beyond its reach left standing, a chest's diamond spilled rather than lost, a scorched and molten lining, raised rubble, fissures past the rim and the glowing core, with gold and diamond ore in it (and no ore at all when terrain is spared). Two pigs far out check that the pressure front hits each once, the outer one later and more lightly; a pig at the mark must die; the screen pass must draw the falling planet (no block-display planet is built) and stop once it's over; and no client-only block displays may be left. Screenshots cover the channel and every phase, from a spectator's vantage points, including the planet looming overhead from the ground and the instant of impact.
- `SpellApiGameTest` uses `TestAddon`, a stand-in for another mod written only against the API, with one spell (Ping), its own effect type and a listener on each event. It checks that Ping joins the wheel after the built-in spells, that the events can refuse a cast, change its cost and cooldown (shorter, longer or none) and see it happen, that setting a running cooldown keeps the HUD's sweep in proportion, that Ping's effect reaches a client renderer even when another renderer throws, that the mana API stays in bounds and syncs, that max mana and regeneration follow the attributes (by command and by modifier) without refilling or back-dating regeneration, and that saved staffs and cooldowns survive a mod's spells disappearing.
- `ArcanaGameTest` covers the mechanics around the spells. It checks that Potency, Thrift and Celerity fit only staffs, come from the enchanting table and change damage, costs and cooldowns; that Shatter and Conduction hit frozen and wet pigs harder, and Shatter breaks the ice; that Shardburst, Overload, Afterimage, Rebound and Rime work, cast from a plain staff at the spells' own power; that corruption adds power to Astral spells only, and that a real Astral cast builds it; that a backlash at the maximum hurts through diamond armour, darkens the view and burns off corruption, and happens about half the time there but never under Clarity, on free casts or below the threshold; that corruption fades, and fast under Clarity; that the potions brew, Mana restores 40 and 80, and Arcane Flow II adds 5 mana a second; that `/arcane` sets mana and corruption and resets cooldowns and talents; and that all 12 advancements load and are earned. It photographs the corruption bar and haze, the grimoire's corruption card and an Astral spell's page, the new effects in the inventory, and the advancement tab.
- `DedicatedServerGameTest` starts a real dedicated server and connects to it. In singleplayer, packets pass between client and server in memory without being encoded; here every one is. It picks a spell on the wheel and casts with real clicks, then checks that the server got the pick, that the client's mana, cooldowns and staff match the server's, and that the client decodes where a bolt struck. It also checks that `terrain_damage`, set from the server console, keeps a wall standing, and that an Astral Gate home carries the caster from the Nether to their spawn point, with the client following. A dedicated server only runs with the [Minecraft EULA](https://aka.ms/MinecraftEULA) accepted, so `build.gradle` accepts it for test runs (`eula = true` in `fabricApi.configureTests`).

**Offline/fast builds on this machine:** `.gradle-home/` is a pre-warmed Gradle home (Gradle 9.7.1 plus Loom's Minecraft 1.21.11 files). To reuse it instead of downloading everything into `~/.gradle`, set `GRADLE_USER_HOME` first:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle-home"; .\gradlew.bat build
```

After changing dependencies while a dev client is still open on Windows, use `gradlew.bat --project-cache-dir build/validation-gradle-cache build` to avoid replacing the remapped JARs that the running client has locked. Use the same cache option for any test run that follows. Restart the dev client to load the new libraries.

### Project layout

```
src/main/java/dev/arcane/          common code (runs on server and client)
  ArcaneSpells.java                  entrypoint: wires everything up
  api/                               the API for other mods (see below)
  mixin/PlayerMixin.java             adds the mana and spell power attributes to players
  command/ArcaneCommand.java         /arcane: mana, corruption, cooldowns and talents, for operators
  registry/                          item, particle, data component, attachment, game rule, damage type, spell and effect
                                       registration; status effects, potions and brewing, enchantments, advancement triggers
  progression/                       mastery, affinities, talents and corruption, saved and synced with CCA
  progression/Corruption.java        corruption's spell power and backlash
  item/ArcaneStaffItem.java          both staffs: casts the selected spell from the staff's tag, charge/release or click
  item/ArcaneTrinketItem.java        the trinkets: worn in Trinkets' slots, with an attribute bonus while worn
  spell/SpellTuning.java             ALL balance numbers: mana, costs, cooldowns, damage, ranges
  spell/SpellCasting.java            the one entry point: mana and cooldown checks, spell power, then the spell
  spell/SpellReactions.java          Shatter and Conduction
  spell/CasterState.java             a player's mana and cooldowns (saved, synced to that player)
  spell/ArcaneBolt.java, SpellBolt   Arcane Bolt: charging, server-side simulation, collision and blast
  spell/ChainLightning, Blink,       the other spells
        ArcaneWard, FrostNova
  spell/MarkedSpell.java             every Astral spell's channel and aiming: the ring, full channel, where it lands
  spell/SpellTerrain.java            the terrain rules every spell shares: who may break what, explosions, vaporising
  spell/CelestialJudgment,           Celestial Judgment: the strike's phases, terrain and damage, and its crater's plan
        JudgmentStrike, Crater
  spell/MeteorSwarm, Singularity,    the other Astral spells, each owning its swarms, holes, fields or gates
        ChronoField, AstralGate
  spell/Planetfall, PlanetfallStrike Planetfall: the strike's phases, lifting and damage
  spell/PlanetfallCrater             its crater, dug a column at a time within a fixed budget per tick
  spell/PlanetfallTerrain            the crater's seeded shape and the impact's fronts, shared with the client
  network/                           the effect packet, the spell-wheel pick, and the broadcast helper
src/client/java/dev/arcane/          client-only code
  api/client/                        client half of the API: effect renderers
  client/ArcaneClient.java           entrypoint: key binding, packet receiver and tick hooks
  client/gui/SpellWheelScreen.java   the radial spell wheel
  client/gui/SpellHud.java           mana bar, corruption bar and active-spell slot
  client/fx/SpellParticles.java      particle choreography for every built-in effect
  client/fx/SpellLighting.java       Nebulon lights, rune circles and arcs
  client/fx/AstralFx.java            the Astral spells' visuals, registered and ticked together
  client/fx/Fx.java                  their shared tools: shapes that update in place, lights, particles, trackers
  client/fx/ChannelFx.java           every Astral spell's channel ring
  client/fx/JudgmentFx, MeteorFx,    each Astral spell's visuals
        SingularityFx, ChronoFx, GateFx, PlanetfallFx
  client/fx/PlanetfallShader.java    Planetfall's full-screen shader pass: space, the rift, the planet, the shock dome
  client/fx/SpaceTimeShader.java     the screen pass for black holes and stopped time: bent light, a lensed disc, colour drained,
                                     the clock's hands
  client/fx/VoxelPlanet,             the planet's shattered blocks and the ground it throws up, as client-only block
        PlanetfallDebris               displays (and the whole planet, without the shader pass)
  client/fx/SpaceSky.java            turns the sky to space while a Planetfall's rift is open
  client/fx/ScreenEffects.java       screen shake, flash and darkening, within the accessibility settings
  client/fx/CorruptionFx.java        the corruption haze at the screen's edges, and a backlash's lurch
  client/particle/ArcaneSpark.java   the tinted glow particle
  client/particle/PlanetfallCloud    Planetfall's billows of dust, smoke and fire
  mixin/client/CameraMixin.java      turns the camera (never the aim) for screen shake and to draw the eye
  mixin/client/GameRendererMixin     widens, narrows and rolls the view
  mixin/client/SkyRendererMixin,     apply SpaceSky to the sky, stars and fog
        FogRendererMixin
src/main/resources/assets/arcane_spells/shaders/
                                     the screen passes (core/planetfall.fsh, core/spacetime.fsh), their frame copy, and
                                       their shared ray and projection helpers (include/screen_space.glsl)
src/main/resources/data/arcane_spells/tags/arcane_spells/spell/
                                     each staff's spells
src/main/resources/data/arcane_spells/enchantment/, advancement/
                                     the staff enchantments' levels and rarity, and the advancements
src/gametest/                        Fabric client game tests, and TestAddon, a sample addon they use
tools/generate_assets.py             draws the spell icons and textures, and synthesizes the spell sounds
reference/                           local-only decompiled/library sources (gitignored)
```

### How it works

The server owns casting, mana, damage and collision. Mana regeneration and cooldowns are calculated from game time; CCA synchronizes state changes to the owning player. The progression component also checks talent attributes after loading and periodically reduces corruption. The staff's selected spell is an item component, set by the server when the wheel or book sends a pick.

For each visual event the server sends one small `SpellEffectPayload` to players within 448 blocks who have that chunk loaded. Each client then builds its own particles and Nebulon effects from that message, so individual particles never cross the network. Active bolts, shockwaves and lights are capped, and every temporary light, arc and rune circle expires on its own. If Nebulon ever throws, the client logs it once and continues with particles only, rather than letting the error disconnect the player. Your own ward is drawn as rings at your feet in first person, since full-size rings around the camera would fill the screen.

To rebalance, edit `SpellTuning`. To change the visuals, edit `SpellParticles` (particles) or `SpellLighting` (light and runes), or the Astral spells' own classes in `client/fx` (on the shared tools in `Fx`). To add a spell, write it the way another mod would (below), register it in `ModSpells` and list it in a staff's tag; its visual events go in `ModSpellEffects`.

The Astral spells are too big for one message each: the server sends one per phase (Judgment's mark, beam and detonation; each meteor's launch and impact; a gate's opening and each crossing; Planetfall's rift, fall and impact) and each client animates the rest. Planetfall's message carries a seed, so every client draws the same planet and the same crater shape the server digs. The planet, the rift and the space they hang in are drawn by a full-screen shader pass (`PlanetfallShader`), run from Nebulon's post-processing hook after the world and before the hand: it reads the world's depth, so terrain hides what is behind it, and it raytraces the planet and the shock dome rather than building them of geometry. It only runs while a Planetfall is in view. The shattered planet, its shards and the slabs of ground thrown at the impact are block displays that exist only on each client, within a fixed budget, and are removed when the animation ends. Each client also plays the impact's boom itself, when the shock front reaches it. A second pass (`SpaceTimeShader`) draws over a copy of the frame for black holes and stopped time: for each pixel it traces the ray past each black hole as light really bends round one (to a black shadow about 2.6 times the hole's radius, ringed with light) and reads the copy again where the bent ray lands, and it drains the colour from whatever lies inside a Chrono Field.

Terrain work is spread over many ticks, a few hundred blocks at most in any one, except Planetfall's crater: it's far bigger, so it gets a fixed budget per tick (at most 2,048 blocks changed), simply takes longer in hilly ground, and skips unloaded chunks rather than loading them.

## API for other mods

Other mods can add spells, react to casting, use mana and send their own effects. Everything public and stable is in `dev.arcane.api` (and `dev.arcane.api.client`); the rest of the code may change between versions. `src/gametest/java/dev/arcane/test/TestAddon.java` is a complete, working example.

| Class | What it's for |
| --- | --- |
| `SpellRegistries` | `SPELLS` and `SPELL_EFFECTS`: register your spells and effect types here, in your mod initializer. |
| `SpellTags` | Which spells each staff holds (`ARCANE_STAFF`, `ASTRAL_STAFF`). Add your spell to a staff with a tag file. |
| `spell.Spell` | Subclass it. `Properties` sets mana cost, cooldown, accent color and whether it's charged (and for how long); override `cast` (and `onChargeStart`/`onChargeTick` for charged spells). |
| `spell.SpellContext` | What `cast` gets: the level, the caster, the charge power and the spell power. Multiply your spell's damage by `spellPower()` so Potency, corruption and the attribute apply to it. |
| `event.SpellEvents` | `ALLOW_CAST` (refuse a cast, with a reason), `MODIFY_MANA_COST`, `MODIFY_COOLDOWN`, `MODIFY_SPELL_POWER`, `AFTER_CAST`. |
| `SpellAttributes` | `MAX_MANA`, `MANA_REGEN` (per second) and `SPELL_POWER` (a damage multiplier): add attribute modifiers to change them. |
| `ArcaneSpellsApi` | Read and add mana, read max mana, regeneration and a spell's power, read and set cooldowns, find the held staff and its spell, cast a spell from code, and `sendEffect`. |
| `effect.SpellEffect`, `effect.SpellEffectType` | A visual event, sent once to nearby players and drawn by each client. |
| `client.SpellEffectRenderers` | Draw your effect types, or add to the built-in ones in `ModSpellEffects`. |

```java
public final class Fireball extends Spell {
    public Fireball() {
        super(new Properties().manaCost(30).cooldown(40).color(0xFFFF7A3D));
    }

    @Override public boolean cast(SpellContext context) {
        // Server only. Return false if it failed: the caster then pays nothing.
        ...
        return true;
    }
}

// In your ModInitializer:
Registry.register(SpellRegistries.SPELLS, Identifier.fromNamespaceAndPath("mymod", "fireball"), new Fireball());
```

Then put it on a staff with `data/arcane_spells/tags/arcane_spells/spell/arcane_staff.json` in your mod (tags from every mod are merged):

```json
{"values": ["mymod:fireball"]}
```

The spell's name is the translation key `spell.mymod.fireball` (plus `spell.mymod.fireball.description` for the wheel), and its icon is the 16×16 sprite `assets/mymod/textures/gui/sprites/spell/fireball.png`. A staff's wheel shows its spells after the built-in ones. Other mods can make staffs of their own with `ArcaneStaffItem` (or a subclass), giving each its own spell tag and glow color; they open the wheel and show the HUD too.

Both registries are synced, so a client without the addon can't join a server that has it. If an addon is removed, staffs holding its spells fall back to Arcane Bolt and its cooldowns are dropped.

To build against the mod, publish it with `gradlew.bat publishToMavenLocal` and add `modImplementation "dev.arcane:arcane-spells:0.7.0"` to the addon (with `mavenLocal()` in its repositories), plus a `"arcane_spells": ">=0.7.0"` dependency in its `fabric.mod.json`.

## Status

Nebulon is a beta dependency. A dedicated server with one player is covered by `DedicatedServerGameTest`; several players at once, shader packs, Sodium and heavy spell spam have not been tested. Two Nebulon 0.1.2 problems are worked around rather than fixed: its billboard renderer can stall the game when a frame draws billboards with several textures (so Celestial Judgment's beam is the only billboard, with one texture), and its volumetric fog softens the whole frame (so the storm is particles). Its additive rune circles also wash out to flat discs in daylight, so Judgment draws its seals' lines with crisp shapes and keeps the rune circles dim. Its broad point lights leave coloured dither on surfaces, so Planetfall lights the ground from its own shader instead. And copying a texture (`copyTextureToTexture`) loses the colour on the tested setup, which breaks Nebulon's compositor and its scene snapshots, so Planetfall's shader pass copies the frame itself with a draw.

Built from the Fabric example mod template (CC0-1.0).
