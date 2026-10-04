# Arcane Spells

A Fabric mod for **Minecraft Java 1.21.11**. It adds a crystal staff with chargeable violet magic: animated rune circles while charging, a cyan/violet double-helix bolt, and layered impact shockwaves. [Nebulon](https://maven.riftrealms.de/releases/de/nexusrealms/nebulon/) supplies colored dynamic lighting, detailed rune circles and electrical arcs. No shader pack is required.

## Install

1. Install Fabric Loader **0.19.5 or later** for Minecraft **1.21.11** (https://fabricmc.net/use/installer/). Minecraft needs Java 21 or newer.
2. Put these three JARs in the instance's `mods` folder:
   - `arcane-spells-0.4.1.jar` (from `build/libs/`, not the `-sources` JAR)
   - [Fabric API 0.141.6+1.21.11](https://modrinth.com/mod/fabric-api/versions?g=1.21.11)
   - [Nebulon 0.1.2-beta+mc1.21.11.20260928.19](https://maven.riftrealms.de/releases/de/nexusrealms/nebulon/0.1.2-beta+mc1.21.11.20260928.19/nebulon-0.1.2-beta+mc1.21.11.20260928.19.jar)
3. For multiplayer, install all three on the server **and** every client. 0.4.x versions are network-compatible with each other.

## Play

- Find the **Arcane Staff** in the Combat creative tab, or run `/give @s arcane_spells:arcane_staff`.
- Craft it with an amethyst shard (top right), a gold ingot (center) and a stick (bottom left).
- Hold right-click to charge and release to fire. A full charge takes **1.2 s**, and a very quick tap cancels. Cooldown: **0.6 s**.
- A direct hit deals up to **24 damage**. Splash damage reaches **20** and falls off over a radius of up to **8 blocks**. Walls shield targets from splash damage. The caster is immune to their own spell, but not to falling, collapsing terrain or chained TNT.
- Bolts fly up to **400 blocks** over 8 s and stop at unloaded chunks. Impacts break terrain using vanilla explosion resistance and drops (strength 3–6; TNT is 4). They can set off TNT but never start fires. PvP follows the server's rules.
- For the fullest effect, set Video Settings → Particles → **All**.

## Develop

The build tools need **JDK 25**; the mod itself targets **Java 21**.

| Task | Command (Windows; use `./gradlew` elsewhere) |
| --- | --- |
| Build the mod JAR | `gradlew.bat build` → `build/libs/` |
| Launch a dev client | `gradlew.bat runClient` |
| In-game integration test | `gradlew.bat runClientGameTest` |

The client game test opens a world and fires a real charge and bolt. It checks that a Nebulon light renders, that the bolt destroys a stone wall, and that no light handles leak afterwards.

**Offline/fast builds on this machine:** `.gradle-home/` is a pre-warmed Gradle home (Gradle 9.7.1 plus Loom's Minecraft 1.21.11 files). To reuse it instead of downloading everything into `~/.gradle`, set `GRADLE_USER_HOME` first:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle-home"; .\gradlew.bat build
```

### Project layout

```
src/main/java/dev/arcane/          common code (runs on server and client)
  ArcaneSpells.java                  entrypoint: wires everything up
  registry/                          item and particle registration
  item/ArcaneStaffItem.java          charge / release handling
  spell/SpellTuning.java             ALL balance numbers: damage, radius, speed, cooldown
  spell/SpellBolts.java, SpellBolt   server-side bolt simulation, collision and blast
  network/                           the single effect packet + broadcast helper
src/client/java/dev/arcane/client/  client-only code
  ArcaneClient.java                  entrypoint: packet receiver and tick hooks
  fx/SpellParticles.java             particle choreography (rings, helix, shockwaves)
  fx/SpellLighting.java              Nebulon lights, rune circles and arcs
  particle/ArcaneSpark.java          the tinted glow particle
src/gametest/                        Fabric client game test
reference/                           local-only decompiled/library sources (gitignored)
```

### How it works

The server owns charging, damage and swept block/entity collision. For each visual event it sends one small `SpellEffectPayload` (`CHARGE`, `BOLT` or `IMPACT`) to players within 448 blocks. Each client then builds its own particles and Nebulon effects from that message, so individual particles never cross the network. Active bolts, shockwaves and lights are all capped, and every temporary light expires on its own.

To rebalance, edit `SpellTuning`. To change the visuals, edit `SpellParticles` (particles) or `SpellLighting` (light and runes).

## Status

Nebulon is a beta dependency. Shader packs, Sodium, dedicated-server multiplayer and heavy spell spam have not been tested.

Built from the Fabric example mod template (CC0-1.0).
