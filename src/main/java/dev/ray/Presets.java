package dev.ray;

/** One-click HUD layouts: each preset turns exactly the listed HUD modules on and every other HUD module off. */
public final class Presets {
	public record Preset(String name, String blurb, String[] modules) {}

	public static final Preset[] ALL = {
		new Preset("Minimal", "FPS and your coordinates, nothing else.",
			new String[]{"fps", "coords"}),
		new Preset("PvP", "FPS, CPS, ping, armor, keystrokes, combo, reach, cooldown, effects and target info.",
			new String[]{"fps", "cps", "ping", "armor", "keystrokes", "combo", "reach", "cooldown", "effects", "targetinfo"}),
		new Preset("Survival", "FPS, coordinates, direction, biome, clock, armor, light level and compass.",
			new String[]{"fps", "coords", "direction", "biome", "clock", "armor", "light", "compass", "deathpos"}),
		new Preset("Streamer", "FPS, CPS, keystrokes, clock and session timer.",
			new String[]{"fps", "cps", "keystrokes", "clock", "session"}),
		new Preset("Everything off", "Turns every HUD module off.", new String[]{})
	};

	private Presets() {}

	/** Applies a preset and returns how many HUD modules are on afterwards. */
	public static int apply(Preset p) {
		int on = 0;
		for (HudModule m : Modules.ALL) {
			boolean want = false;
			for (String id : p.modules()) if (id.equals(m.id)) want = true;
			m.setEnabled(want);
			if (want) on++;
		}
		RayConfig.save();
		Toasts.push("Preset " + p.name() + ": " + on + " module" + (on == 1 ? "" : "s") + " on");
		return on;
	}
}
