# Ray Client

A client-side Fabric quality-of-life and visual customization mod for Minecraft 1.21.11.

## What is new: the redesign

- **Start menu: "Orbit and Dock"**: the logo sits inside two faint, slowly turning orbits with small satellites (like the logo's own ring), with the stars shifting slightly as you move the mouse. The actions live in a glass dock at the bottom: icons swell toward the cursor and name themselves when hovered. No cards or tips; update news is one quiet line. (Magnification respects the Animations setting.)
- **Restraint**: no glows, no gradient fills, no tinted cards, no decorative highlight lines, no tracked all-caps labels. Surfaces are flat translucent glass, text is sentence case, and the accent colour appears only where something is on or selected.
- **Logo everywhere**: the orbiting-R logo is drawn as crisp vector shapes (no texture) on the title screen, loading screen, menu header, pause menu, HUD editor and as the mod icon.
- **Accent themes**: Ember (the logo's orange) by default, plus Ocean, Violet, Mint, Rose, Gold, Ice, Crimson and Rainbow.
- **Better Font** (replaces the old "Minecraft Font" module): two independent switches, one for Minecraft's own text and one for the client, plus a font picker. Turning a switch off brings back Minecraft's own font for that use. Ray Client's own text is drawn with **pixel-exact Inter**: pre-hinted bitmaps drawn one texture pixel per screen pixel (Minecraft samples its font texture pixel by pixel, so a normal smooth font always looks ragged; matching pixels exactly is what makes it crisp). Menus snap to whole pixels per unit for the same reason. An option switches back to the smooth TrueType font.
- **Module list**: each module is a card with its icon, name, category, a one-line description, an on/off switch and a Settings link.
- **HUD editor**: a logo and one settings button in the middle, a slim action bar at the top, and settings/turn-off buttons on whichever module you hover.
- **Lighter to draw**: far fewer draw calls per frame than the first glass version (the module list went from about 12k to about 3k), cheaper text outlines, and no per-letter spacing except in a few headings.

## What was new in 1.13

- **A calmer, more consistent look**: flat graphite surfaces with 1px hairlines, one accent colour used only for state (on, selected, progress, focus), smaller consistent corner radii, text tabs with a sliding underline, plain toggles and sliders, restrained glows. Applies to the module list, every settings page, the start menu, notifications and the buttons in Minecraft's own menus.
- **Lyrics behave better**: words are timed to how long a line is really sung (not the whole gap to the next line), a sentence never splits into a new group mid-line, the bar keeps one width per song, long lines scroll with the singing instead of shrinking, and floating words fade instead of towering over you when you walk up to them.

## What was new in 1.12

- **Module list**: compact tiles (icon, name, live value, enable and options side by side), more tiles per row, and a bigger window, so you see 8 to 15 modules at once instead of 3. New **Tiles per row** setting (Auto, 2 to 6).
- **Menu Size** is now a free slider from 50% to 200% (applies when you let go), plus quick-pick chips.
- **Start menu polish**: GitHub button, a status card that lines up with the button column and shows on/off dots, an update row, and a tip pinned to the bottom.

## What was new in 1.11

- **Auto updater**: Ray Client checks the GitHub Releases of Client-ui/RayClient (about every 6 hours), downloads new versions in the background, verifies them (GitHub only, size, SHA-256, and that the jar really is Ray Client), and installs them when you close the game. The old jar is kept in `mods/ray-backup`. Updates are manual by default: press "Check for updates" on the start menu (or Settings > Updates > Check Now). Settings > Updates can switch to Notify (checks every 6 hours and tells you) or Auto (also downloads in the background). The title screen shows a banner when an update is available.

### Publishing a release (for maintainers)

1. Set `mod_version` in `gradle.properties` (for example `1.12.0`) and commit.
2. Tag and push: `git tag v1.12.0` then `git push origin v1.12.0`.
3. The "Release Ray Client" workflow builds the jar and publishes a GitHub Release with the jar and its SHA-256. Players' clients pick it up automatically.

The tag must match `mod_version`, otherwise the workflow stops.

## What was new in 1.10

- **Lyric Sync, rebuilt**: live preview in its settings page; bar styles (glass pill / minimal), top or bottom, any size; progress line, next-line preview, song name and time; play / pause tile; floating words with three looks (glow / outline / clean), three animations (pop / rise / fade) and three colour modes (single / rainbow / gradient).
- **Lyric Sync, smarter**: lyrics are saved offline after the first time, the service is retried automatically if it was unreachable, advertisements are recognised, and the bar hides itself when you pause for a while.
- **Per-song sync fix**: new keys "Lyrics Earlier / Later (this song)" nudge the timing and the correction is remembered for that song. There is also a key to toggle Lyric Sync.
- **Crash protection**: if a HUD module or overlay throws an error, only that part is paused (with a notice) instead of the game crashing. Errors are written to the log once.
- **Menu Size** now applies to every settings page, not just the module list.
- **First launch** shows a welcome notice with the menu key.

## What was new in 1.9.1

- **New start menu**: welcome header with your name, a big glowing Singleplayer button, Multiplayer / Mods / Profiles buttons, a "Your Client" status card with quick switches (HUD, Sky, Lyrics) and tips, round Options and Quit buttons, and a background that leans toward your cursor.
- **Lighter HUD**: font lookups no longer allocate on every drawn string, module styles are looked up a few times a second instead of dozens of times per frame, Hitboxes check line of sight about 15 times a second instead of every frame (and no longer create garbage), and line projection allocates nothing.

## What was new in 1.9

- **New look**: the menu now uses big tiles with live previews (like the CheatBreaker style), a new main menu with three big cards, a new drifting-light background, and re-styled buttons in the singleplayer / multiplayer / options menus.
- **Menu Size** (Settings page): 70% to 150%.
- **Custom Sky**: the top of the sky is now recoloured too.
- **Lyric Sync**: smooth font, exact Spotify position on Windows, no more PowerShell restarts.
- **Performance**: HUD text is read ten times a second instead of every frame, the minimap scan is spread over frames, text widths are cached, and the menu draws far fewer shapes.

## What was new in 1.8

- **Side Shield fixed**: the shield now moves the moment you raise it (no more delay and snap), glides smoothly in and out, and picks the right hand for left-handed players.
- **UI rework**: smooth anti-aliased rounded corners, soft shadows and glows, gradient panels, animated pill toggles and sliders, cards that slide in one after another and lift on hover, redesigned notifications. Default menu font is now the smooth Outfit.
- **Custom Sky** (Visual): 10 pre-made skies plus your own colours for the sky, horizon fog and clouds.
- **Lyric Sync** (Extras): Spotify lyrics in the top middle with a karaoke sweep, plus words that pop into the world where your crosshair points, stay in place, stack, and fade after a few seconds. Lyrics come from LRCLIB (no account needed).

## What was new in 1.7

- **New menu**: every module and feature lives in one searchable grid (filter chips, favourites, an Enable button and a settings button per card). Press Right Shift in game.
- **Block outline rewritten**: draws the real shape of the block you look at, hides back edges like vanilla, and lines up with the camera. "Stabilize View" turns off view bobbing and FOV effects while it is on (restored afterwards).
- **Crosshair editor**: left-click draws, right-click erases, plus a **Vanilla Blending** option (same colour-inverting blend as the normal crosshair).
- **Ray Client title screen** and **dark loading screen** with the lightning bolt (both can be switched off in the Client category).

## Features

- HUD widgets for FPS, CPS, ping, coordinates, direction, biome, potion effects, armor, keystrokes, and more.
- HUD editor, module settings, profiles, configurable accent themes (orange by default), and custom crosshair.
- Client-side visual options such as zoom, viewmodel, and HUD styling.
- Informational PvP displays only; no automated combat, reach changes, packet manipulation, or anti-cheat bypasses.

## Multiplayer note

Ray aims to stay vanilla-friendly, but individual servers may restrict particular client mods or features. Check the rules of each server; no third-party client can guarantee acceptance everywhere.




## Client-side options

Ray includes optional toggle sprint and gamma-based fullbright, a configurable accent theme (orange by default), font choices for the Ray menu, and a branded title and loading screen. These are client-side conveniences, but individual multiplayer servers may restrict specific mods or features; check each server’s rules. No anti-cheat bypass or automated combat feature is included.
