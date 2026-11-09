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
 * Minecraft Font and Client Font: two independent settings.
 *
 *  - Minecraft Font replaces Minecraft's "default" font id (the same id override Minecraft uses for its "Force Unicode
 *    Font" option), so chat, vanilla menus, tooltips, signs... all change at once.
 *  - Client Font is applied to the client's own text only (Ray menus and HUD modules) by {@link ClientFont}, which gives
 *    that text an explicit font. Explicit fonts are not touched by the Minecraft Font swap, so the two never interfere.
 *
 * Six fonts ship with the mod: a copy of Minecraft's normal font ("Minecraft default", used by the Client Font so client
 * text can stay as it is while Minecraft changes) and five others. A seventh slot, "My own font", comes from a .ttf/.otf
 * you drop into the ray-fonts config folder; Minecraft only loads fonts from resource packs, so {@link #preparePack}
 * turns that file into a small pack ("Ray Fonts") that you enable once in Options > Resource Packs.
 *
 * A font that is not loaded (hook missing, custom pack not enabled) is simply not applied, so text can never turn into
 * empty boxes.
 */
public final class FontOverride {
	/** Index 0 is a copy of Minecraft's own font; 6 is the one you add yourself. */
	public static final String[] NAMES = {"Minecraft default", "Pixelify Sans", "Outfit", "JetBrains Mono", "Tektur", "Work Sans", "My own font"};
	private static final String[] IDS = {"vanilla", "pixelify", "outfit", "jetbrains", "tektur", "worksans", "custom"};

	private static final Identifier DEFAULT = Identifier.of("minecraft", "default");

	/** The one font every Ray menu and HUD module is drawn in: Outfit (index 2 of {@link #NAMES}). */
	private static final int CLIENT_FONT = 2;

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
		return manager instanceof RayFontAccess;
	}

	/** True if the font in this slot was loaded from the resources. */
	public static boolean loaded(int index) {
		return index >= 0 && index < IDS.length && manager instanceof RayFontAccess a && a.ray$has(Identifier.of("ray", IDS[index]));
	}

	private static final Identifier[] FONT_IDS = new Identifier[IDS.length];

	static {
		for (int i = 0; i < IDS.length; i++) FONT_IDS[i] = Identifier.of("ray", IDS[i]);
	}

	// "is this font loaded" almost never changes, so it is looked up twice a second instead of on every drawn string
	private static int clientKey = -2;
	private static Identifier clientCached = null;
	private static long clientAt = 0L;

	/** The font the client's own text should use, or null to draw it normally. */
	public static Identifier clientFontId() {
		RayConfig c = RayConfig.get();
		int key = CLIENT_FONT; // the whole client uses one font; it is not a setting any more
		long now = System.currentTimeMillis();
		if (key == clientKey && now - clientAt < 500L) return clientCached;
		clientKey = key;
		clientAt = now;
		clientCached = key >= 0 && loaded(key) ? FONT_IDS[key] : null;
		return clientCached;
	}

	private static Identifier smoothCached = null;
	private static long smoothAt = -1L;

	/** The smooth font (Outfit) used where text must never look pixelated, or null if it is not loaded. */
	public static Identifier smoothId() {
		long now = System.currentTimeMillis();
		if (smoothAt < 0L || now - smoothAt > 500L) {
			smoothAt = now;
			smoothCached = loaded(2) ? FONT_IDS[2] : null;
		}
		return smoothCached;
	}

	/** Applies the Minecraft Font setting. Cheap; safe to call every tick. */
	public static void update() {
		if (!(manager instanceof RayFontAccess access)) return;
		RayConfig c = RayConfig.get();
		Identifier want = null;
		// index 0 is Minecraft's own font: choosing it means "no change"
		if (c.minecraftFontOn && c.minecraftFont > 0 && c.minecraftFont < IDS.length) {
			Identifier id = Identifier.of("ray", IDS[c.minecraftFont]);
			if (access.ray$has(id)) want = id;
		}

		if (want == null) {
			if (applied != null) {
				access.ray$setOverrides(Map.of());
				applied = null;
				appliedMap = null;
			}
			return;
		}
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
