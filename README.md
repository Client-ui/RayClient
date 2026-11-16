# Ray Client

A client-side Fabric quality-of-life and visual customization mod for Minecraft 1.21.11.

## What is new: the redesign

- **Start menu**: laid out like the Ray Launcher's home screen: the logo top left, a big "Ray Client" wordmark with Singleplayer and Multiplayer, slow orange silk ribbons across the right (shifting slightly with the mouse), and a row of cards for Mods, Profiles, Options and Quit.
- **Pause (Esc) menu**: one card with the logo and "PAUSED", an orange "Back to Game", a quiet red "Save and Quit", and clean list rows.
- **More modules**: Free Slots, Item Total, Saturation, Sea Level, Daylight Timer and Connection quality (46+ text modules now).
- **Matches the Ray Launcher**: near-black surfaces (`#0C0B0E`, cards `#141217`), the launcher's orange (`#FF8A2B`), Inter, tracked-capital section labels, text tabs with an underline, a bordered pill for the current page, dark cards with an orange Enable button, and the launcher's toggle (dark knob on an orange track, with On/Off beside it). The accent is used only where something is on or primary.
- **Logo everywhere**: the orbiting-R logo is drawn as crisp vector shapes (no texture) on the title screen, loading screen, menu header, pause menu, HUD editor and as the mod icon.
- **Accent themes**: Ember (the logo's orange) by default, plus Ocean, Violet, Mint, Rose, Gold, Ice, Crimson and Rainbow.
- **Better Font** (replaces the old "Minecraft Font" module): two independent switches, one for Minecraft's own text and one for the client, plus a font picker. Turning a switch off brings back Minecraft's own font for that use. Ray Client's own text is drawn with **pixel-exact Inter**: pre-hinted bitmaps drawn one texture pixel per screen pixel (Minecraft samples its font texture pixel by pixel, so a normal smooth font always looks ragged; matching pixels exactly is what makes it crisp). Menus snap to whole pixels per unit for the same reason. An option switches back to the smooth TrueType font.
- **Module list**: each module is a card with its icon, name, category, a one-line description, an on/off switch and a Settings link.
- **HUD editor**: a logo and one settings button in the middle, a slim action bar at the top, and settings/turn-off buttons on whichever module you hover.
- **Lighter to draw**: far fewer draw calls per frame than the first glass version (the module list went from about 12k to about 3k), cheaper text outlines, and no per-letter spacing except in a few headings.

