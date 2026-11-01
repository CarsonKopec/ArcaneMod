# Releasing to Modrinth and CurseForge

`gradlew publishMods` uploads the built JAR, with this version's section of `CHANGELOG.md` as its release notes, to both sites. It runs on [mod-publish-plugin](https://modmuss50.github.io/mod-publish-plugin/), configured at the end of `build.gradle`. Without both tokens it's a dry run: nothing is uploaded, and what would be is written to `build/publishMods/`.

## Blocker: Nebulon

Arcane Spells and Leyline both require [Nebulon](https://maven.riftrealms.de/releases/de/nexusrealms/nebulon/), which is on neither Modrinth nor CurseForge, only on NexusRealms' Maven repository, and is All Rights Reserved. That conflicts with both sites' rules:

- **Modrinth** requires every dependency to be listed in each version's Dependencies section, and only Modrinth projects can be listed there.
- **CurseForge** doesn't allow external download links for files.

The licence also rules out bundling Nebulon in our JAR (jar-in-jar) or uploading it ourselves without NexusRealms' written permission. Until one of these is settled, neither mod can be listed with all its requirements:

1. NexusRealms publishes Nebulon on Modrinth and CurseForge. This is the cleanest option: add `nebulon` to the required mods in both `build.gradle` files.
2. NexusRealms gives written permission to bundle it. Include it with Loom's `include`, and credit them on the page.
3. Make Nebulon optional, so both mods run without it and only use it when it's installed. This is a code change in both mods: every Nebulon call must sit behind a check that Nebulon is loaded, and the screen passes need a hook of their own instead of Nebulon's post-processing.

## The first time

1. **Release Leyline first.** Arcane Spells lists it as a required mod by its slug, `leyline`, which must exist on both sites before Arcane Spells can be uploaded. Its own steps are in [Leyline's docs/releasing.md](https://github.com/CarsonKopec/Leyline/blob/main/docs/releasing.md).
2. **Create the project** on each site, from the text in [listing.md](listing.md):

   | Field | Modrinth | CurseForge |
   | --- | --- | --- |
   | Name | Arcane Spells | Arcane Spells. The slug `arcane-spells` is taken there by an unrelated "Arcane Spell's", so accept the slug it offers, or pick a name such as "Arcane Spells (Fabric)" if the name is refused |
   | Slug | `arcane-spells` (free as of 2026-11-01) | whatever it offers |
   | Summary and description | from `listing.md` | from `listing.md` |
   | Categories | Magic, Equipment, Adventure | Magic; Armor, Tools, and Weapons; Adventure and RPG |
   | Loader | Fabric | Fabric |
   | Environment | required on the client and the server | client and server |
   | Licence | CC0-1.0 | CC0 (or Custom, pasting `LICENSE`) |
   | Icon | `src/main/resources/assets/arcane_spells/icon.png` | the same |
   | Source and issues | https://github.com/CarsonKopec/ArcaneMod, `/issues` | the same |

3. **Add a gallery.** `gradlew runClientGameTest` saves screenshots to `build/run/clientGameTest/screenshots/`. These show off the most: `0004_spells-wheel`, `0005_spells-chain`, `0014_judgment-beam`, `0016_judgment-blast`, `0021_astral-meteors`, `0024_astral-black-hole`, `0027_astral-chrono`, `0029_astral-gate`, `0034_planetfall-looming`, `0037_planetfall-impact`, `0041_planetfall-crater` and `0047_libraries-book-talents`. Check each one before you use it: they are test captures, and some are taken mid-transition.
4. **Put the project IDs in `gradle.properties`:** `modrinth_project_id` (on the project page: the ⋮ menu, then Copy ID) and `curseforge_project_id` (the Project ID in the About Project panel). Commit them; they aren't secret.
5. **Make tokens.**
   - Modrinth: Settings, then Personal access tokens. Give it the Create versions, Read versions and Write versions scopes.
   - CurseForge: account settings, then API tokens.

   Keep them out of the repository.

## Every release

1. Bump `version` in `gradle.properties`, and add a `## <version>` section at the top of `CHANGELOG.md`. Update the versions in the README's Install and API sections.
2. Run the checks: `gradlew.bat build` and `gradlew.bat runClientGameTest`.
3. Dry-run the upload: `gradlew.bat publishMods` with no tokens set, then look in `build/publishMods/`.
4. Upload. Do one of these:
   - **From this machine:**
     ```powershell
     $env:MODRINTH_TOKEN = "..."; $env:CURSEFORGE_TOKEN = "..."; .\gradlew.bat build publishMods
     ```
   - **From GitHub:** add `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` as repository secrets (Settings, then Secrets and variables, then Actions). Then run the **release** workflow from the Actions tab. It builds Leyline from its repository first, as the build workflow does.
5. Tag the release: `git tag v<version>`, then `git push origin v<version>`.

Modrinth reviews a new project before it goes public; later versions appear straight away. CurseForge reviews every file.

## Later

Once Leyline is on Modrinth, Arcane Spells can build against the published JAR rather than one built locally. Depend on `maven.modrinth:leyline:<version>` (the Modrinth Maven repository is already in `build.gradle`), then drop the `mavenLocal()` entry and the Leyline steps in both workflows.
