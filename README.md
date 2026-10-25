# OriginalSword - Fabric 26.3 port

First-person sword animations ported from the Bedrock "OriginalSword" pack (Kyy x Tacz): draw, 4-hit combo
(click again ~0.6 s into a swing to chain), idle inspect, light trails, sounds. Works with wooden / stone / iron /
golden / diamond / netherite swords. Third person is untouched. Pure Java, client-side only.

Targets: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Java 25.

## Build without installing anything (GitHub Actions)
1. Create a GitHub repo and upload this folder's contents (including the hidden `.github` folder).
2. Open the **Actions** tab; the `build` workflow runs (or press "Run workflow").
3. Download the `originalsword-jar` artifact, use `originalsword-1.1.0.jar` (not `-sources`), put it in `mods/` with Fabric API.

Local build: JDK 25 + Gradle 9.6, `gradle wrapper --gradle-version 9.6.0`, then `./gradlew build`.

## Not compiled or run
Written without being able to compile or launch 26.3. The likely trouble spots:
- Compile errors from 26.3 API renames. Candidates: `SubmitNodeCollector#submitCustomGeometry`, `RenderTypes.*`,
  `player.getSkin().body().texturePath()` in `SwordRenderer`, `options.keyAttack`. Paste the CI log to get them fixed.
- Orientation: Bedrock vs Java axes may make the sword/arms look rotated or mirrored. Edit
  `config/originalsword.json`, set `"preset"` to `a`..`h` (applies live), tune `scale`/`offset`/`camera`.

## How the 26.3 hook works
26.3 replaced `ItemInHandRenderer` with `FirstPersonHandsAndItemsRenderer`, and its `submitHandsWithItems`
signature could not be verified. `OriginalSwordMixinPlugin` therefore patches that method with ASM so it works
with whatever arguments it has; it logs `[OriginalSword] Hooked ...` on success, or a warning (and keeps vanilla
hands) if the method is missing. Check `latest.log` for that line.

## Changes vs the 26.1.2 build
- Click detection uses the attack key (the swing fields were reworked in 26.3).
- Pause detection is reflective; render errors fall back to vanilla hands instead of crashing.
- Versions: Loader 0.19.5, Fabric API 0.162.0+26.3, Gradle 9.6.0 in CI.

## 1.1.0 changes
- Arms/hands stay and animate with the sword (`"showArms": false` hides them).
- Off-hand item (shield, torch, totem...) is now drawn next to the sword. It uses reflection against the 26.3 item
  renderer; if the API does not match it disables itself with a log line (`off-hand rendering unavailable`) and the
  sword keeps working. Turn it off with `"offhand": false`.
- Axis handling rewritten: presets `a`-`h` are now 8 consistent conventions (animation signs + one global flip for the
  whole scene) instead of mixing mirrored animation with unmirrored model. Preset `a` is the best guess; if the sword
  is rotated, mirrored or behind you, set `"preset"` to `b`...`h` in `config/originalsword.json` (applies live) and tune
  `offset` / `scale`.

## Not ported
Copper swords; the off-hand does not play equip/raise animations.

## Assets / license
Models, animations, textures and sounds belong to the original pack authors; get permission before sharing. Code is MIT.

## GitHub quick start
1. github.com -> New repository (any name, Public or Private) -> Create.
2. "uploading an existing file" -> drag in everything from this folder, including the hidden `.github` folder -> Commit.
   (If drag-and-drop skips `.github`, use Add file -> Create new file and name it `.github/workflows/build.yml`, pasting the contents.)
3. Actions tab -> "Build mod jar" -> wait for the green check (about 3-5 min).
4. Click the run -> Artifacts -> `originalsword-jar` -> unzip -> `originalsword-1.1.0.jar` goes in `mods/`.
If it is red, open the failed step and send the error text to Claude.
