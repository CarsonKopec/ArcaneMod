# Arcane Spells

A Fabric mod for **Minecraft Java 1.21.11**. It adds a crystal staff that casts five spells, chosen from a radial spell wheel and paid for with a regenerating mana bar: Arcane Bolt, Chain Lightning, Blink, Arcane Ward and Frost Nova. [Nebulon](https://maven.riftrealms.de/releases/de/nexusrealms/nebulon/) supplies colored dynamic lighting, detailed rune circles and electrical arcs. No shader pack is required.

## Install

1. Install Fabric Loader **0.19.5 or later** for Minecraft **1.21.11** (https://fabricmc.net/use/installer/). Minecraft needs Java 21 or newer.
2. Put these three JARs in the instance's `mods` folder:
   - `arcane-spells-0.5.0.jar` (from `build/libs/`, not the `-sources` JAR)
   - [Fabric API 0.141.6+1.21.11](https://modrinth.com/mod/fabric-api/versions?g=1.21.11)
   - [Nebulon 0.1.2-beta+mc1.21.11.20260928.19](https://maven.riftrealms.de/releases/de/nexusrealms/nebulon/0.1.2-beta+mc1.21.11.20260928.19/nebulon-0.1.2-beta+mc1.21.11.20260928.19.jar)
3. For multiplayer, install all three on the server **and** every client, with the same Arcane Spells version on both. 0.5 is not network-compatible with 0.4.

## Play

- Find the **Arcane Staff** in the Combat creative tab, or run `/give @s arcane_spells:arcane_staff`.
- Craft it with an amethyst shard (top right), a gold ingot (center) and a stick (bottom left).
- **Choose a spell:** with the staff in hand, hold **R** (rebindable under Controls → Arcane Spells), point at a spell and release. Clicking also picks; Escape cancels. The staff remembers its spell, and its tooltip shows which.
- **Cast:** right-click. Arcane Bolt is held to charge and fires on release; the other spells fire on click.
- **Mana:** 100, refilling at 5 per second. The bar sits above the food bar while you hold the staff or are refilling. The active spell shows in a slot beside the hotbar, dimmed when you can't afford it and with a cooldown sweep. A refused cast (no mana, or still recharging) fizzles and says why above the hotbar. Creative players cast for free; cooldowns still apply.
- Every spell has its own sounds, with subtitles (Options → Accessibility → Show Subtitles).

| Spell | Mana | Cooldown | Effect |
| --- | --- | --- | --- |
| Arcane Bolt | 25 | 0.6 s | Charged explosive bolt: up to 24 damage on a direct hit, 20 splash over 8 blocks, breaks terrain. Flies 400 blocks. |
| Chain Lightning | 30 | 2.5 s | Strikes the creature you aim at within 24 blocks (8 damage), then leaps up to 4 more times to the nearest target within 7 blocks, each leap 20% weaker. Needs a clear path for every leap. |
| Blink | 20 | 3 s | Teleports up to 16 blocks along your aim, stopping short of walls. Fails, without cost, if there's no room at least 2 blocks away. Resets fall damage. |
| Arcane Ward | 40 | 15 s | An 8 s shell that absorbs 16 damage from attacks, projectiles and explosions (not falls, fire or hunger), and turns incoming projectiles back on their shooter. The hit that breaks it is still fully absorbed. |
| Frost Nova | 35 | 8 s | Everything within 6 blocks takes 4 damage (10 for blazes and other heat-loving mobs), Slowness III for 5 s and is frozen solid, as in powder snow (strays, snow golems and anyone wearing leather don't freeze). |

- Spells never hurt the caster, and Chain Lightning, Frost Nova and the bolt's direct hit pass over the caster's mount. PvP follows the server's rules.
- Arcane Bolt breaks terrain using vanilla explosion resistance and drops (strength 3–6; TNT is 4). It can set off TNT but never starts fires. Blocks only break where the caster could break them by hand: in Adventure mode, under spawn protection and beyond the world border, the blast still hurts entities but leaves terrain (and TNT) alone.
- Spell particles follow Video Settings → Particles: **All** shows everything, Decreased halves them and Minimal turns them off, leaving the lights and arcs. They appear within 64 blocks of the camera.

## Develop

The build tools need **JDK 25**; the mod itself targets **Java 21**.

| Task | Command (Windows; use `./gradlew` elsewhere) |
| --- | --- |
| Build the mod JAR | `gradlew.bat build` → `build/libs/` |
| Launch a dev client | `gradlew.bat runClient` |
| In-game integration tests | `gradlew.bat runClientGameTest` |
| Regenerate spell icons and sounds | `python tools/generate_assets.py` (needs `pip install numpy scipy pillow soundfile`) |

The spell icons (`textures/gui/sprites/spell/`) and sounds (`sounds/`) are drawn and synthesized by `tools/generate_assets.py`, so there are no third-party assets to license. Edit a function there and rerun it to change one; add `--preview DIR` to also get upscaled icons and waveform/spectrogram plots for checking sounds by eye. A resource pack can replace any icon or sound by path.

The client game tests run in a real client and save screenshots to `build/run/clientGameTest/screenshots/`:

- `LightingGameTest` fires a real charge and bolt. It checks that a Nebulon light renders, that a bolt cast in Adventure mode leaves a stone wall standing while a normal bolt destroys it, and that no light handles leak afterwards.
- `SpellsGameTest` checks that every spell sound has a file and every icon loaded, then picks Chain Lightning on the spell wheel with simulated key and mouse input, then casts it with a real right-click. It checks that all five spells affect the world as described above, and that mana, cooldowns and an empty mana pool are enforced.
- `SpellApiGameTest` uses `TestAddon`, a stand-in for another mod written only against the API, with one spell (Ping), its own effect type and a listener on each event. It checks that Ping joins the wheel after the built-in spells, that the events can refuse a cast, change its cost and see it happen, that Ping's effect reaches a client renderer even when another renderer throws, that the mana API stays in bounds and syncs, and that saved staffs and cooldowns survive a mod's spells disappearing.

**Offline/fast builds on this machine:** `.gradle-home/` is a pre-warmed Gradle home (Gradle 9.7.1 plus Loom's Minecraft 1.21.11 files). To reuse it instead of downloading everything into `~/.gradle`, set `GRADLE_USER_HOME` first:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle-home"; .\gradlew.bat build
```

### Project layout

```
src/main/java/dev/arcane/          common code (runs on server and client)
  ArcaneSpells.java                  entrypoint: wires everything up
  api/                               the API for other mods (see below)
  registry/                          item, particle, data component, attachment, spell and effect registration
  item/ArcaneStaffItem.java          casts the selected spell: charge/release or click
  spell/SpellTuning.java             ALL balance numbers: mana, costs, cooldowns, damage, ranges
  spell/SpellCasting.java            the one entry point: mana and cooldown checks, then the spell
  spell/CasterState.java             a player's mana and cooldowns (saved, synced to that player)
  spell/ArcaneBolt.java, SpellBolt   Arcane Bolt: charging, server-side simulation, collision and blast
  spell/ChainLightning, Blink,       the other spells
        ArcaneWard, FrostNova
  network/                           the effect packet, the spell-wheel pick, and the broadcast helper
src/client/java/dev/arcane/          client-only code
  api/client/                        client half of the API: effect renderers
  client/ArcaneClient.java           entrypoint: key binding, packet receiver and tick hooks
  client/gui/SpellWheelScreen.java   the radial spell wheel
  client/gui/SpellHud.java           mana bar and active-spell slot
  client/fx/SpellParticles.java      particle choreography for every built-in effect
  client/fx/SpellLighting.java       Nebulon lights, rune circles and arcs
  client/particle/ArcaneSpark.java   the tinted glow particle
src/gametest/                        Fabric client game tests, and TestAddon, a sample addon they use
tools/generate_assets.py             draws the spell icons and synthesizes the spell sounds
reference/                           local-only decompiled/library sources (gitignored)
```

### How it works

The server owns casting, mana, damage and collision. Mana and cooldowns are stored against game time, so nothing ticks per player and the client is only re-synced when a spell is cast. The staff's selected spell is an item component, set by the server when the wheel sends a pick.

For each visual event the server sends one small `SpellEffectPayload` to players within 448 blocks who have that chunk loaded. Each client then builds its own particles and Nebulon effects from that message, so individual particles never cross the network. Active bolts, shockwaves and lights are capped, and every temporary light, arc and rune circle expires on its own. If Nebulon ever throws, the client logs it once and continues with particles only, rather than letting the error disconnect the player. Your own ward is drawn as rings at your feet in first person, since full-size rings around the camera would fill the screen.

To rebalance, edit `SpellTuning`. To change the visuals, edit `SpellParticles` (particles) or `SpellLighting` (light and runes). To add a spell, write it the way another mod would (below) and register it in `ModSpells`; its visual events go in `ModSpellEffects`.

## API for other mods

Other mods can add spells, react to casting, use mana and send their own effects. Everything public and stable is in `dev.arcane.api` (and `dev.arcane.api.client`); the rest of the code may change between versions. `src/gametest/java/dev/arcane/test/TestAddon.java` is a complete, working example.

| Class | What it's for |
| --- | --- |
| `SpellRegistries` | `SPELLS` and `SPELL_EFFECTS`: register your spells and effect types here, in your mod initializer. |
| `spell.Spell` | Subclass it. `Properties` sets mana cost, cooldown, accent color and whether it's charged; override `cast` (and `onChargeStart`/`onChargeTick` for charged spells). |
| `spell.SpellContext` | What `cast` gets: the level, the caster and the charge power. |
| `event.SpellEvents` | `ALLOW_CAST` (refuse a cast, with a reason), `MODIFY_MANA_COST`, `AFTER_CAST`. |
| `ArcaneSpellsApi` | Read and add mana, read cooldowns, find the held staff and its spell, cast a spell from code, and `sendEffect`. |
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

The spell's name is the translation key `spell.mymod.fireball` (plus `spell.mymod.fireball.description` for the wheel), and its icon is the 16×16 sprite `assets/mymod/textures/gui/sprites/spell/fireball.png`. Every registered spell appears on the wheel, after the built-in ones. Other mods' staffs can extend `ArcaneStaffItem`; they then open the wheel and show the HUD too.

Both registries are synced, so a client without the addon can't join a server that has it. If an addon is removed, staffs holding its spells fall back to Arcane Bolt and its cooldowns are dropped.

To build against the mod, publish it with `gradlew.bat publishToMavenLocal` and add `modImplementation "dev.arcane:arcane-spells:0.5.0"` to the addon (with `mavenLocal()` in its repositories), plus a `"arcane_spells": ">=0.5.0"` dependency in its `fabric.mod.json`.

## Status

Nebulon is a beta dependency. Shader packs, Sodium, dedicated-server multiplayer and heavy spell spam have not been tested.

Built from the Fabric example mod template (CC0-1.0).
