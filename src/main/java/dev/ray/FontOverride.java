package dev.ray;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Better Font: the fonts of the client, with two independent switches.
 *
 *  - "For Minecraft" replaces Minecraft's "default" font (chat, vanilla menus, tooltips, signs...). The mod also ships a
 *    resource file that puts Inter in front of the default font, so this works even where the font hook cannot attach;
 *    the hook only adds switching it off and choosing another face.
 *  - "For Ray Client" is applied to the client's own text (Ray menus and HUD modules) by {@link ClientFont}, which gives
 *    that text an explicit font, so the two switches never interfere.
 *
 * Minecraft draws text from a texture with nearest-pixel sampling, so a font only looks sharp when its texture detail
 * matches the real on-screen size. Every bundled font therefore ships at several detail levels (oversample 1 to 8) and the
 * one closest to the real size (GUI scale times the menu scale) is chosen for each piece of text. That is what keeps
 * small menu text from breaking up.
 *
 * A seventh slot, "My own font", comes from a .ttf/.otf you drop into the ray-fonts config folder; Minecraft only loads
 * fonts from resource packs, so {@link #preparePack} turns that file into a small pack ("Ray Fonts").
 */
public final class FontOverride {
	/** Index 0 is a copy of Minecraft's own font; 6 is the one you add yourself. */
	public static final String[] NAMES = {"Minecraft default", "Inter", "Outfit", "JetBrains Mono", "Tektur", "Work Sans", "My own font"};
	private static final String[] IDS = {"vanilla", "inter", "outfit", "jetbrains", "tektur", "worksans", "custom"};

	private static final Identifier DEFAULT = Identifier.of("minecraft", "default");

	/** Set by the FontManager mixin when Minecraft creates its font manager. */
	public static Object manager;

	private static Identifier applied = null;
	private static Map<Identifier, Identifier> appliedMap = null;

	/** Last result of {@link #preparePack}, shown on the settings pages. */
	public static String message = "";

	private static List<String> filesCache = new ArrayList<>();
	private static long filesTime = 0L;

	private FontOverride() {}

	/** True if this Minecraft version let the font hook attach. */
	public static boolean supported() {
		return manager instanceof RayFontOverrides;
	}

	/** True if the font in this slot was loaded from the resources. */
	public static boolean loaded(int index) {
		if (index < 0 || index >= IDS.length) return false;
		// the fonts that ship inside the mod are always there; only "My own font" (a separate resource pack) needs checking
		if (index < IDS.length - 1) return true;
		return manager instanceof RayFontAccess a && a.ray$has(Identifier.of("ray", IDS[index]));
	}

	/** Detail levels (oversample times ten) in which every bundled font ships. */
	private static final int[] TAGS = {10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 80};
	private static final String[] BUNDLED = {"inter", "interbold", "outfit", "jetbrains", "tektur", "worksans"};
	private static final Map<String, Identifier[]> VARIANTS = new java.util.HashMap<>();
	private static final Identifier VANILLA_ID = Identifier.of("ray", "vanilla");
	private static final Identifier CUSTOM_ID = Identifier.of("ray", "custom");

	static {
		for (String n : BUNDLED) {
			Identifier[] a = new Identifier[TAGS.length];
			for (int i = 0; i < TAGS.length; i++) a[i] = Identifier.of("ray", n + "_o" + TAGS[i]);
			VARIANTS.put(n, a);
		}
	}

	/** Real screen pixels per unit of whatever is being drawn right now (GUI scale times the menu scale). */
	public static float real() {
		return (float) (UiScale.guiScale() * ClientFont.scale);
	}

	/** The detail level of a bundled font that is closest to the given real size. */
	private static Identifier variant(String name, float real) {
		Identifier[] a = VARIANTS.get(name);
		if (a == null) return null;
		int best = 0;
		float bd = Float.MAX_VALUE;
		for (int i = 0; i < TAGS.length; i++) {
			float d = Math.abs(TAGS[i] / 10f - real);
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		return a[best];
	}

	/** The font id for a slot of {@link #NAMES} at a real size. Never null. */
	private static Identifier faceId(int face, boolean head, float real) {
		if (face <= 0) return VANILLA_ID;
		if (face >= IDS.length - 1) return loaded(face) ? CUSTOM_ID : variant("inter", real);
		return variant(head && face == 1 ? "interbold" : IDS[face], real);
	}

	/** The font the client's own text should use, or null to draw it the normal way (Better Font off for the client). */
	public static Identifier clientFontId() {
		RayConfig c = RayConfig.get();
		return c.clientFontOn ? faceId(c.minecraftFont, false, real()) : null;
	}

	/** The bold face used for headings (Inter SemiBold when Inter is chosen), or null like {@link #clientFontId}. */
	public static Identifier headId() {
		RayConfig c = RayConfig.get();
		return c.clientFontOn ? faceId(c.minecraftFont, true, real()) : null;
	}

	/** Inter at the right detail for the current size, whatever the settings (Lyric Sync wants smooth text always). */
	public static Identifier smoothId() {
		return variant("inter", real());
	}

	/** Applies the "for Minecraft" switch and face. Cheap; safe to call every tick. */
	public static void update() {
		if (!(manager instanceof RayFontOverrides access)) return;
		RayConfig c = RayConfig.get();
		// off = Minecraft's real default font; on = the chosen face at the detail that matches the GUI scale
		Identifier want = c.minecraftFontOn ? faceId(c.minecraftFont, false, (float) UiScale.guiScale()) : VANILLA_ID;
		if (!want.equals(applied)) {
			applied = want;
			appliedMap = Map.of(DEFAULT, want);
		}
		access.ray$setOverrides(appliedMap); // re-applied every call so a resource reload cannot undo it
	}

	// ---------- your own font ----------

	private static Path fontsDir;

	public static Path fontsFolder() {
		if (fontsDir == null) {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("ray-fonts");
			try {
				Files.createDirectories(p);
			} catch (IOException ignored) {
				// listing just comes back empty
			}
			fontsDir = p;
		}
		return fontsDir;
	}

	public static Path packFolder() {
		return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve("Ray Fonts");
	}

	/** .ttf / .otf files in the ray-fonts folder (refreshed at most once a second). */
	public static List<String> customFiles() {
		long now = System.currentTimeMillis();
		if (now - filesTime < 1000L) return filesCache;
		filesTime = now;
		List<String> out = new ArrayList<>();
		File[] files = fontsFolder().toFile().listFiles();
		if (files != null) {
			for (File f : files) {
				String n = f.getName().toLowerCase(Locale.ROOT);
				if (f.isFile() && (n.endsWith(".ttf") || n.endsWith(".otf"))) out.add(f.getName());
			}
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		filesCache = out;
		return out;
	}

	public static void openFolder() {
		Path p = fontsFolder();
		Thread t = new Thread(() -> {
			try {
				if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(p.toFile());
			} catch (Throwable ignored) {
				// the path is shown on the settings page as a fallback
			}
		}, "ray-open-folder");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Builds the "Ray Fonts" resource pack from the chosen file: pack.mcmeta, the font definition and a copy of the font.
	 * Returns a short status message (also stored in {@link #message}).
	 */
	public static String preparePack(String fileName) {
		try {
			Path src = fontsFolder().resolve(fileName).normalize();
			String lower = fileName.toLowerCase(Locale.ROOT);
			if (!src.startsWith(fontsFolder()) || !Files.isRegularFile(src) || !(lower.endsWith(".ttf") || lower.endsWith(".otf"))) {
				return message = "Pick a .ttf or .otf file first.";
			}
			Path pack = packFolder();
			Path fontDir = pack.resolve("assets").resolve("ray").resolve("font");
			Files.createDirectories(fontDir);
			String ext = lower.endsWith(".otf") ? "otf" : "ttf";
			Files.deleteIfExists(fontDir.resolve("custom.ttf"));
			Files.deleteIfExists(fontDir.resolve("custom.otf"));
			Files.copy(src, fontDir.resolve("custom." + ext), StandardCopyOption.REPLACE_EXISTING);
			Files.writeString(fontDir.resolve("custom.json"),
				"{\n  \"providers\": [\n"
					+ "    {\"type\": \"ttf\", \"file\": \"ray:custom." + ext + "\", \"shift\": [0.0, 0.0], \"size\": 10.0, \"oversample\": 4.0},\n"
					+ "    {\"type\": \"reference\", \"id\": \"minecraft:default\"}\n  ]\n}\n");
			Files.writeString(pack.resolve("pack.mcmeta"),
				"{\n  \"pack\": {\n    \"pack_format\": 75,\n    \"supported_formats\": {\"min_inclusive\": 1, \"max_inclusive\": 999},\n"
					+ "    \"description\": \"Ray Client custom font\"\n  }\n}\n");
			Toasts.push("Font pack saved");
			return message = "Font pack saved. In Options > Resource Packs, enable \"Ray Fonts\" (press F3+T if you already had).";
		} catch (Exception e) {
			return message = "Could not write the font pack: " + e.getMessage();
		}
	}
}
