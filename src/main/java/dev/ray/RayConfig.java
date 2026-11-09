package dev.ray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RayConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ray.json");
	private static RayConfig instance = new RayConfig();

	public static class Waypoint {
		public String name = "Waypoint";
		public double x, y, z;
		public String dim = "minecraft:overworld";
	}

	/** Per-module look and options. */
	public static class Style {
		public int textColor = 0;
		public int accentColor = 0;
		/** -1 = use the global HUD background, otherwise 0-100 opacity. */
		public int bg = -1;
		public int scale = 100;
		/** -1 = global, 1 = on, 0 = off. */
		public int shadow = -1;
		public boolean accentBar = true;
		public boolean border = false;
		/** Text and background opacity, 20-100. */
		public int opacity = 100;
		/** 0 = always, 1 = hide while chat is open, 2 = only while holding Tab. */
		public int visibility = 0;
		/** GLFW key code that toggles this module, -1 = none. */
		public int key = -1;
		public Map<String, Integer> opts = new HashMap<>();
	}

	public static class Pos {
		public int x, y;
		public Pos() {}
		public Pos(int x, int y) { this.x = x; this.y = y; }
	}

	// Master switch + per-module state/positions
	public boolean enabled = true;
	public Map<String, Boolean> modules = new HashMap<>();
	public Map<String, Pos> positions = new HashMap<>();
	public Map<String, Style> styles = new HashMap<>();
	// Gameplay
	public boolean toggleSprint = false;
	public int zoomFov = 30;
	/** Zoom strength in tenths: 30 = 3.0x, range 15..100. */
	public int zoomLevel = 30;
	public boolean customCrosshair = false;
	public int crosshairSize = 5;
	public int crosshairGap = 2;
	public int crosshairThickness = 1;
	public boolean crosshairDot = false;
	public boolean toggleSneak = false;
	public boolean fullbright = false;
	/** Fullbright brightness (gamma). 100% in the vanilla slider is 1.0; Fullbright goes far past it. */
	public int fullbrightLevel = 15;
	/** 0 = gamma only, 1 = gamma + night vision effect. */
	public int fullbrightMode = 0;
	// Waypoints shown in the world
	public boolean waypointMarkers = true;
	public boolean waypointShowDist = true;
	/** 0 = unlimited. */
	public int waypointMaxDist = 0;
	/** Saves a "Death" waypoint where you die. */
	public boolean deathWaypoint = true;
	public Waypoint lastDeath = null;
	// Free look
	public boolean freelook = false;
	/** Keyboard key (GLFW code) or -1. */
	public int freelookKey = 342; // left alt
	/** Mouse button (GLFW code, 2 and up) or -1. */
	public int freelookBtn = -1;
	/** false = hold the key, true = press once to start and again to stop. */
	public boolean freelookToggle = false;
	/** Switch to third person while free look is on, and back to first person afterwards. */
	public boolean freelookThirdPerson = true;
	public boolean blockOutline = false;
	public boolean outlineRainbow = false;
	public boolean outlineGlow = true;
	/** 0 = only the face you are looking at (default), 1 = the whole block. */
	public int outlineMode = 0;
	/** While the block outline is on: turns off view bobbing + FOV effects so the outline lines up exactly. */
	public boolean outlineStabilize = true;
	public int outlineColor = 0;
	public int outlineThickness = 2;
	public int outlineOpacity = 100;
	public boolean smoothZoom = true;
	public boolean hitMarker = false;
	public boolean hitSound = false;
	// tunables that used to be hard-coded
	public int lowHealthHearts = 3;
	public int lowHealthColor = 0xFF0000;
	public int lowHealthThickness = 14;
	public int hitMarkerColor = 0xFFFFFF;
	public int hitMarkerSize = 8;
	public int hitMarkerTime = 220;
	/** Percent. */
	public int hitSoundVolume = 100;
	/** Percent (160 = 1.6, the old fixed value). */
	public int hitSoundPitch = 160;
	/** Play your own file from the ray-sounds folder instead of the Minecraft sound. */
	public boolean hitSoundCustom = false;
	public String hitSoundFile = "";
	/** Percent; 100 = the file at its original speed. */
	public int hitSoundFilePitch = 100;
	public int durabilityPercent = 10;
	public int durabilitySeconds = 15;
	/** Ticks to wait on the death screen before respawning. */
	public int respawnDelay = 0;
	// Hit color: recolours the red flash entities get when hurt
	public boolean hitColor = false;
	public int hitColorRgb = 0xFF0000;
	public int hitColorStrength = 70;
	// Custom hitbox
	public boolean hitbox = false;
	public int hitboxColor = 0xFFFFFF;
	public int hitboxHoverColor = 0xFF4040;
	public boolean hitboxHover = true;
	public int hitboxThickness = 1;
	public int hitboxOpacity = 100;
	/** vanilla's red eye-height line (off by default). */
	public boolean hitboxEye = false;
	/** vanilla's blue look-direction line (off by default). */
	public boolean hitboxLook = false;
	/** 0 = players, 1 = all living entities. */
	public int hitboxTarget = 0;
	public boolean noHurtCam = false;
	public boolean hideScoreboard = false;
	public int scoreboardScale = 100;
	public boolean lowHealthWarning = false;
	public boolean durabilityAlert = false;
	public boolean autoRespawn = false;
	public long totalSeconds = 0;
	// Viewmodel / animation
	// Low Fire: lowers the burning-screen fire overlay
	public boolean lowFire = false;
	/** 0-100: how far the fire drops (0 = vanilla height). */
	public int lowFireAmount = 50;
	// Side Shield: moves the shield off to the side when you hold it up
	public boolean sideShield = false;
	/** Only while the shield is actually raised (blocking). */
	public boolean ssOnlyBlocking = true;
	/** Sideways (positive = away from the middle), height, depth, size in percent, tilt in degrees. */
	public int ssX = 35, ssY = -20, ssZ = 0, ssScale = 85, ssTilt = 0;
	/** Smooth glide between the normal and the side position (0 = jump, 1..10 = slower). */
	public boolean ssSmooth = true;
	/** Menu size in percent (Ray Client menu). */
	public int menuScale = 100;
	/** Module tiles per row in the Ray menu (0 = automatic). */
	public int menuCols = 0;
	/** Re-style the vanilla buttons in menus outside a world. */
	public boolean styledMenuButtons = true;
	// Custom Sky: recolours the overworld sky, horizon fog and clouds with one of the pre-made skies
	public boolean skyOn = false;
	/** Index into Sky.PRESETS; Sky.PRESETS.length means "Custom colours". */
	public int skyPreset = 0;
	/** 0-100: how strongly the sky replaces the vanilla one. */
	public int skyStrength = 100;
	/** When on, the chosen sky still gets darker at night. */
	public boolean skyFollowDay = false;
	public int skyCustomTop = 0x6A4CFF, skyCustomHorizon = 0xFF7AC8, skyCustomCloud = 0xFFFFFF;
	// Lyric Sync: Spotify lyrics in the top middle of the screen and as floating words in the world
	public boolean lyricsOn = false;
	public boolean lyricsBar = true;
	public boolean lyricsWorld = true;
	public boolean lyricsShowTitle = true;
	/** 0 = Stack (words build up lines where you looked), 1 = Trail (every word lands at your crosshair). */
	public int lyricsMode = 0;
	/** Seconds until the floating words fade and a fresh set starts. */
	public int lyricsClear = 5;
	/** Size of the floating words in percent. */
	public int lyricsSize = 100;
	/** How far in front of you (in blocks) the words appear. */
	public int lyricsDistance = 6;
	/** Distance of the top bar from the top edge. */
	public int lyricsY = 8;
	/** Sync correction in milliseconds (positive = lyrics later). */
	public int lyricsOffset = 0;
	/** Colour of the floating words: 0 = accent colour, otherwise Theme palette. */
	public int lyricsColor = 0;
	// Lyric Sync, extra options
	/** 0 = glass pill, 1 = minimal (text only). */
	public int lyricsBarStyle = 0;
	/** 0 = top of the screen, 1 = bottom (above the hotbar). */
	public int lyricsBarPos = 0;
	public int lyricsBarScale = 100;
	public boolean lyricsProgress = true;
	public boolean lyricsNext = true;
	public boolean lyricsHidePaused = true;
	public boolean lyricsCache = true;
	/** 0 = glow, 1 = outline, 2 = clean. */
	public int lyricsWordStyle = 0;
	/** 0 = pop, 1 = rise, 2 = fade. */
	public int lyricsAnim = 0;
	/** 0 = one colour, 1 = rainbow, 2 = gradient across each line. */
	public int lyricsColorMode = 0;
	/** Sync correction remembered per song (key = artist and title), set with the nudge keys. */
	public Map<String, Integer> lyricsSongOffsets = new HashMap<>();
	/** The first-run welcome has been shown. */
	public boolean welcomed = false;
	/** 0 = manual (only when you press "Check for updates"), 1 = check in the background and tell me, 2 = also download (installed when the game closes). */
	public int updateMode = 0;
	/** Settings version for the updater; older configs are moved to manual checks. */
	public int updateSchema = 0;
	public long updateLastCheck = 0L;
	/** A version the player chose to skip. */
	public String updateSkipped = "";
	public boolean viewmodel = false;
	public int vmX = 0, vmY = 0, vmZ = 0, vmScale = 100;
	public boolean customSwing = false;
	public int swingDuration = 6;
	// Crosshair editor
	public boolean crosshairDrawn = false;
	public boolean enemyCrosshair = false;
	/** Draw the crosshair with vanilla's colour-inverting blend instead of flat colours. */
	public boolean crosshairVanillaBlend = false;
	/** Overall crosshair size multiplier you can type in (1.0 = normal, 1.33 = a third bigger). */
	public float crosshairZoom = 1.0f;
	public boolean crosshairOutline = false;
	public int crosshairColor = 0;
	public int enemyColor = 2;
	public int crosshairScale = 2;
	public int enemyTarget = 0;
	public String crosshairPixels = CrosshairArt.cross();
	public String enemyPixels = CrosshairArt.x();
	// Client screens
	public boolean customTitle = true;
	public boolean customLoading = true;
	// interface
	/** Smooth hover / toggle / window animations. */
	public boolean uiAnimations = true;
	/** How see-through the menu windows are (percent opaque). Lower shows more of the blurred game behind. */
	public int uiGlass = 90;
	public boolean uiShadows = true;
	public boolean toastsOn = true;
	/** Module list as rows instead of cards. */
	public boolean listView = false;
	/** 0 = by category, 1 = A-Z, 2 = enabled first, 3 = favorites first. */
	public int sortMode = 0;
	// fonts: index into FontOverride.NAMES (0 = Minecraft default, 1-5 built in, 6 = your own)
	/** Replaces Minecraft's own font everywhere (chat, menus, tooltips...). Never touches the client's text. */
	public boolean minecraftFontOn = false;
	public int minecraftFont = 2;
	/** Font for all of the client's own text (Ray menus and HUD modules). */
	public boolean clientFontOn = true;
	public int clientFont = 2;
	public String customFontFile = "";
	/** Ray background on the singleplayer / multiplayer / options screens (when not in a world). */
	public boolean customBackground = true;
	public boolean zoomEnabled = true;
	// Stretched Res: squeezes or stretches the 3D world (not the hand, not the menus) without changing the window resolution
	public boolean stretchOn = false;
	public int stretchX = 100;
	public int stretchY = 100;
	/** Module ids the player starred in the menu. */
	public List<String> favorites = new ArrayList<>();
	// Theme
	public int accent = 0;
	public boolean rainbow = false;
	public int hudOpacity = 40;
	public boolean textShadow = true;
	/** Draws every HUD text with a dark outline (reads on any background). */
	public boolean hudOutline = true;
	// Data
	public List<Waypoint> waypoints = new ArrayList<>();

	public Style style(String id) {
		if (styles == null) styles = new HashMap<>();
		Style st = styles.get(id);
		if (st == null) {
			st = new Style();
			styles.put(id, st);
		}
		if (st.opts == null) st.opts = new HashMap<>();
		return st;
	}

	public static RayConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try {
				RayConfig loaded = GSON.fromJson(Files.readString(FILE), RayConfig.class);
				if (loaded != null) {
					if (loaded.waypoints == null) loaded.waypoints = new ArrayList<>();
					if (loaded.favorites == null) loaded.favorites = new ArrayList<>();
					if (loaded.modules == null) loaded.modules = new HashMap<>();
					if (loaded.positions == null) loaded.positions = new HashMap<>();
					if (loaded.styles == null) loaded.styles = new HashMap<>();
					if (loaded.lyricsSongOffsets == null) loaded.lyricsSongOffsets = new HashMap<>();
					if (loaded.crosshairPixels == null) loaded.crosshairPixels = CrosshairArt.cross();
					if (loaded.enemyPixels == null) loaded.enemyPixels = CrosshairArt.x();
					loaded.sanitize();
					instance = loaded;
					copyTo(FILE.resolveSibling("ray.json.bak")); // last known-good copy
				}
			} catch (Exception e) {
				// never overwrite a file we could not read: keep it next to the config so nothing is lost
				Path broken = FILE.resolveSibling("ray.json.broken-" + System.currentTimeMillis());
				copyTo(broken);
				System.err.println("[Ray] Could not read the config (" + e + "). Using defaults. The old file was kept as " + broken.getFileName());
			}
		}
		save();
	}

	private static void copyTo(Path target) {
		try {
			Files.copy(FILE, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// best effort only
		}
	}

	/** Puts every setting back to its default (the old file is kept as a backup first). */
	public static void resetAll() {
		copyTo(FILE.resolveSibling("ray.json.before-reset"));
		instance = new RayConfig();
		save();
	}

	/** Saves a dated copy of the settings next to the config and returns its file name (or null on failure). */
	public static String backup() {
		save();
		Path target = FILE.resolveSibling("ray-backup-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date()) + ".json");
		try {
			Files.copy(FILE, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			return target.getFileName().toString();
		} catch (IOException e) {
			return null;
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/** Forces every setting into its valid range, so a hand-edited or damaged file can never crash the menu. */
	public void sanitize() {
		accent = clamp(accent, 0, Theme.COLORS.length - 1);
		hudOpacity = clamp(hudOpacity, 0, 100);
		uiGlass = clamp(uiGlass, 40, 100);
		skyPreset = clamp(skyPreset, 0, Sky.PRESETS.length);
		menuScale = clamp(menuScale, 50, 200);
		stretchX = clamp(stretchX, 50, 250);
		stretchY = clamp(stretchY, 50, 250);
		menuCols = clamp(menuCols, 0, 6);
		updateMode = clamp(updateMode, 0, 2);
		if (updateSchema < 2) { // earlier versions defaulted to automatic updates: updating is now opt-in
			updateMode = 0;
			updateSchema = 2;
		}
		if (updateSkipped == null) updateSkipped = "";
		lyricsBarStyle = clamp(lyricsBarStyle, 0, 1);
		lyricsBarPos = clamp(lyricsBarPos, 0, 1);
		lyricsBarScale = clamp(lyricsBarScale, 60, 180);
		lyricsWordStyle = clamp(lyricsWordStyle, 0, 2);
		lyricsAnim = clamp(lyricsAnim, 0, 2);
		lyricsColorMode = clamp(lyricsColorMode, 0, 2);
		if (lyricsSongOffsets == null) lyricsSongOffsets = new HashMap<>();
		skyStrength = clamp(skyStrength, 0, 100);
		lyricsMode = clamp(lyricsMode, 0, 1);
		lyricsClear = clamp(lyricsClear, 2, 15);
		lyricsSize = clamp(lyricsSize, 50, 250);
		lyricsDistance = clamp(lyricsDistance, 2, 16);
		lyricsY = clamp(lyricsY, 0, 120);
		lyricsOffset = clamp(lyricsOffset, -5000, 5000);
		lyricsColor = clamp(lyricsColor, 0, Theme.PALETTE_NAMES.length - 1);
		sortMode = clamp(sortMode, 0, 3);
		crosshairColor = clamp(crosshairColor, 0, CrosshairArt.colorCount() - 1);
		enemyColor = clamp(enemyColor, 0, CrosshairArt.colorCount() - 1);
		crosshairScale = clamp(crosshairScale, 1, 4);
		crosshairSize = clamp(crosshairSize, 2, 20);
		crosshairGap = clamp(crosshairGap, 0, 10);
		crosshairThickness = clamp(crosshairThickness, 1, 3);
		enemyTarget = clamp(enemyTarget, 0, 2);
		if (Float.isNaN(crosshairZoom) || Float.isInfinite(crosshairZoom)) crosshairZoom = 1.0f;
		crosshairZoom = Math.max(0.25f, Math.min(8.0f, crosshairZoom));
		outlineColor = clamp(outlineColor, 0, Theme.PALETTE_NAMES.length - 1);
		outlineThickness = clamp(outlineThickness, 1, 4);
		outlineOpacity = clamp(outlineOpacity, 20, 100);
		outlineMode = clamp(outlineMode, 0, 1);
		lowFireAmount = clamp(lowFireAmount, 0, 100);
		ssX = clamp(ssX, -50, 80);
		ssY = clamp(ssY, -60, 40);
		ssZ = clamp(ssZ, -50, 50);
		ssScale = clamp(ssScale, 30, 150);
		ssTilt = clamp(ssTilt, -60, 60);
		zoomLevel = clamp(zoomLevel, 15, 100);
		fullbrightLevel = clamp(fullbrightLevel, 2, 20);
		fullbrightMode = clamp(fullbrightMode, 0, 1);
		lowHealthHearts = clamp(lowHealthHearts, 1, 10);
		lowHealthThickness = clamp(lowHealthThickness, 6, 30);
		hitMarkerSize = clamp(hitMarkerSize, 4, 16);
		hitMarkerTime = clamp(hitMarkerTime, 100, 600);
		hitSoundVolume = clamp(hitSoundVolume, 10, 100);
		hitSoundPitch = clamp(hitSoundPitch, 50, 200);
		hitSoundFilePitch = clamp(hitSoundFilePitch, 50, 200);
		durabilityPercent = clamp(durabilityPercent, 5, 50);
		durabilitySeconds = clamp(durabilitySeconds, 5, 60);
		respawnDelay = clamp(respawnDelay, 0, 60);
		hitColorStrength = clamp(hitColorStrength, 10, 100);
		hitboxThickness = clamp(hitboxThickness, 1, 4);
		hitboxOpacity = clamp(hitboxOpacity, 20, 100);
		hitboxTarget = clamp(hitboxTarget, 0, 1);
		waypointMaxDist = clamp(waypointMaxDist, 0, 2000);
		minecraftFont = clamp(minecraftFont, 0, 6);
		clientFont = clamp(clientFont, 0, 6);
		if (customFontFile == null) customFontFile = "";
		if (hitSoundFile == null) hitSoundFile = "";
		for (Style st : styles.values()) {
			if (st == null) continue;
			st.textColor = clamp(st.textColor, 0, Theme.PALETTE_NAMES.length - 1);
			st.accentColor = 0; // the per-module accent colour setting was removed: modules always follow the client accent
			st.scale = clamp(st.scale, 50, 200);
			st.bg = clamp(st.bg, -1, 100);
			st.shadow = clamp(st.shadow, -1, 1);
			st.opacity = clamp(st.opacity, 20, 100);
			st.visibility = clamp(st.visibility, 0, 2);
			if (st.opts == null) st.opts = new HashMap<>();
		}
	}

	public static boolean saveTo(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(instance));
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/** Replaces the active settings with a saved profile (keeps waypoints and playtime). */
	public static boolean loadFrom(Path file) {
		try {
			RayConfig loaded = GSON.fromJson(Files.readString(file), RayConfig.class);
			if (loaded == null) return false;
			RayConfig old = instance;
			loaded.waypoints = old.waypoints;
			loaded.totalSeconds = old.totalSeconds;
			loaded.favorites = old.favorites;
			loaded.lastDeath = old.lastDeath;
			if (loaded.modules == null) loaded.modules = new HashMap<>();
			if (loaded.positions == null) loaded.positions = new HashMap<>();
			if (loaded.styles == null) loaded.styles = new HashMap<>();
			if (loaded.crosshairPixels == null) loaded.crosshairPixels = CrosshairArt.cross();
			if (loaded.enemyPixels == null) loaded.enemyPixels = CrosshairArt.x();
			instance = loaded;
			save();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static void save() {
		try {
			Path tmp = FILE.resolveSibling("ray.json.tmp");
			Files.writeString(tmp, GSON.toJson(instance));
			try {
				Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			System.err.println("[Ray] Could not save the config: " + e);
		}
	}
}
