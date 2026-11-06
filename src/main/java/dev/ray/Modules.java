package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Registry of every HUD module and its options. */
public final class Modules {
	public static final List<HudModule> ALL = new ArrayList<>();
	public static ScoreboardModule scoreboard;

	/** A setting a module exposes in its settings screen. */
	public static final class Opt {
		public static final int TOGGLE = 0, SLIDER = 1, CHOICE = 2;
		public final String key, label;
		public final int type, min, max, def;
		public final String[] choices;

		private Opt(String key, String label, int type, int min, int max, int def, String[] choices) {
			this.key = key;
			this.label = label;
			this.type = type;
			this.min = min;
			this.max = max;
			this.def = def;
			this.choices = choices;
		}

		public static Opt toggle(String key, String label, int def) { return new Opt(key, label, TOGGLE, 0, 1, def, null); }
		public static Opt slider(String key, String label, int min, int max, int def) { return new Opt(key, label, SLIDER, min, max, def, null); }
		public static Opt choice(String key, String label, int def, String... choices) { return new Opt(key, label, CHOICE, 0, choices.length - 1, def, choices); }
	}

	private static final Map<String, List<Opt>> OPTS = new HashMap<>();
	private static final DateTimeFormatter[] CLOCKS = {
		DateTimeFormatter.ofPattern("HH:mm"), DateTimeFormatter.ofPattern("HH:mm:ss"),
		DateTimeFormatter.ofPattern("hh:mm a"), DateTimeFormatter.ofPattern("hh:mm:ss a")
	};
	private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
	private static final long SESSION_START = System.currentTimeMillis();
	private static double prevX, prevZ, speed, traveled;
	private static int slot = 0;
	private static int secTick = 0;
	private static final int[] FPS_RING = new int[200];
	private static int fpsIdx = 0, fpsFilled = 0;
	private static boolean swRunning = false;
	private static long swStart = 0, swAccum = 0;

	static {
		// ---- module options ----
		opts("fps", Opt.toggle("label", "Show Label", 1));
		opts("cps", Opt.choice("style", "Style", 0, "Left | Right", "Total"));
		opts("ping", Opt.toggle("suffix", "Show ms", 1));
		opts("coords", Opt.slider("dec", "Decimals", 0, 3, 1));
		opts("speed", Opt.choice("unit", "Unit", 0, "b/s", "km/h"));
		opts("memory", Opt.choice("unit", "Unit", 0, "MB", "GB"));
		opts("clock", Opt.toggle("h24", "24-Hour", 1), Opt.toggle("sec", "Show Seconds", 0));
		opts("distance", Opt.choice("unit", "Unit", 0, "Auto", "Blocks"));
		opts("effects", Opt.slider("max", "Max Lines", 1, 10, 6), Opt.toggle("timer", "Show Timer", 1));
		opts("waypoints", Opt.slider("max", "Max Entries", 1, 8, 4));
		opts("keystrokes", Opt.toggle("mouse", "Mouse Buttons", 1), Opt.toggle("space", "Spacebar", 1));
		opts("armor", Opt.toggle("dur", "Durability Numbers", 1), Opt.toggle("hand", "Held Item", 1));
		opts("compass", Opt.slider("width", "Width", 80, 200, 130));
		opts("date", Opt.choice("format", "Format", 0, "YYYY-MM-DD", "DD/MM/YYYY", "MM/DD/YYYY"));
		opts("session", Opt.toggle("seconds", "Show Seconds", 1));
		opts("serverip", Opt.toggle("hidesp", "Hide In Singleplayer", 0));
		opts("worldtime", Opt.choice("fmt", "Format", 0, "24-hour", "12-hour"));
		opts("chunk", Opt.toggle("offset", "Show Position In Chunk", 0));
		opts("health", Opt.toggle("max", "Show Max Health", 0), Opt.toggle("whole", "Round To Whole Number", 0));
		opts("food", Opt.toggle("sat", "Show Saturation", 1));
		opts("rotation", Opt.slider("dec", "Decimals", 0, 3, 1));
		opts("direction", Opt.toggle("deg", "Show Degrees", 0));
		opts("air", Opt.toggle("always", "Always Show", 0));
		opts("fall", Opt.slider("min", "Show After (blocks)", 0, 10, 0));
		opts("cooldown", Opt.toggle("hidefull", "Hide When Full", 0));
		opts("reach", Opt.slider("dec", "Decimals", 1, 3, 2));
		opts("clicks", Opt.toggle("best", "Show Best CPS", 1));
		opts("helditem", Opt.toggle("count", "Show Stack Count", 1), Opt.toggle("dur", "Show Durability Left", 0));
		opts("toolcondition", Opt.choice("fmt", "Format", 0, "Left / Max (%)", "Percent only", "Left only"));
		opts("playtime", Opt.toggle("sec", "Show Seconds", 0));
		opts("deathpos", Opt.toggle("dist", "Show Distance", 1));
		opts("minimap", Opt.slider("size", "Size", 64, 160, 100), Opt.slider("range", "Range (blocks)", 16, 128, 48),
			Opt.toggle("waypoints", "Show Waypoints", 1), Opt.toggle("north", "Show North (N)", 1));
		opts("inventory", Opt.toggle("slots", "Slot Background", 0));
		opts("fpsgraph", Opt.slider("height", "Height", 24, 64, 36));
		opts("pinggraph", Opt.slider("height", "Height", 24, 64, 36));
		opts("targetinfo", Opt.toggle("dist", "Show Distance", 1), Opt.toggle("bar", "Health Bar", 1));
		opts("target", Opt.toggle("hardness", "Show Hardness", 0));
		opts("xp", Opt.toggle("progress", "Show Progress", 1));

		// ---- on by default ----
		text("fps", "FPS", "Stats", true, mc -> List.of(opt("fps", "label") == 1 ? "FPS: " + mc.getCurrentFps() : String.valueOf(mc.getCurrentFps())));
		text("cps", "CPS", "Stats", true, mc -> opt("cps", "style") == 1
			? List.of("CPS: " + (CpsTracker.left() + CpsTracker.right()))
			: List.of("CPS: " + CpsTracker.left() + " | " + CpsTracker.right()));
		text("ping", "Ping", "Stats", true, Modules::ping);
		text("coords", "Coordinates", "World", true, mc -> {
			int d = opt("coords", "dec");
			String f = "XYZ: %." + d + "f / %." + d + "f / %." + d + "f";
			return List.of(String.format(f, mc.player.getX(), mc.player.getY(), mc.player.getZ()));
		});
		text("direction", "Direction", "World", true, mc -> {
			String f = mc.player.getHorizontalFacing().asString();
			String deg = opt("direction", "deg") == 1
				? String.format(" (%d\u00B0)", Math.round(((mc.player.getYaw() % 360f) + 360f) % 360f)) : "";
			return List.of("Facing: " + Character.toUpperCase(f.charAt(0)) + f.substring(1) + deg);
		});
		text("biome", "Biome", "World", true, Modules::biome);
		ALL.add(new KeystrokesModule());
		ALL.add(new ArmorModule());
		textAt("effects", "Potion Effects", "Player", true, HudModule.MR, 4, 52, Modules::effects);
		textAt("waypoints", "Waypoint List", "Player", true, HudModule.ML, 4, 52, Modules::waypoints);

		// ---- Stats ----
		text("speed", "Speed", "Stats", false, mc -> opt("speed", "unit") == 1
			? List.of(String.format("Speed: %.1f km/h", speed * 3.6))
			: List.of(String.format("Speed: %.1f b/s", speed)));
		text("memory", "Memory", "Stats", false, mc -> {
			Runtime rt = Runtime.getRuntime();
			long used = rt.totalMemory() - rt.freeMemory();
			long max = rt.maxMemory();
			if (opt("memory", "unit") == 1) return List.of(String.format("RAM: %.1f/%.1f GB", used / 1073741824.0, max / 1073741824.0));
			return List.of("RAM: " + (used / 1048576L) + "/" + (max / 1048576L) + " MB");
		});
		text("clock", "Clock", "Stats", false, mc -> {
			boolean h24 = opt("clock", "h24") == 1, sec = opt("clock", "sec") == 1;
			return List.of("Time: " + LocalTime.now().format(CLOCKS[(h24 ? 0 : 2) + (sec ? 1 : 0)]));
		});
		text("date", "Date", "Stats", false, mc -> {
			LocalDate d = LocalDate.now();
			int fmt = opt("date", "format");
			String s = fmt == 1 ? String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear())
				: fmt == 2 ? String.format("%02d/%02d/%d", d.getMonthValue(), d.getDayOfMonth(), d.getYear()) : d.toString();
			return List.of("Date: " + s);
		});
		text("session", "Session Timer", "Stats", false, mc -> {
			long s = (System.currentTimeMillis() - SESSION_START) / 1000L;
			if (opt("session", "seconds") == 0) return List.of(String.format("Session: %d:%02d", s / 3600, (s / 60) % 60));
			return List.of(String.format("Session: %d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60));
		});
		text("serverip", "Server IP", "Stats", false, mc -> mc.getCurrentServerEntry() == null && opt("serverip", "hidesp") == 1 ? List.<String>of() :
			List.of("IP: " + (mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer")));
		text("stopwatch", "Stopwatch", "Stats", false, Modules::stopwatch);
		text("fpsminmax", "FPS Min/Max", "Stats", false, Modules::fpsMinMax);
		text("clicks", "Click Counter", "Stats", false, mc -> {
			String clicks = "Clicks: " + CpsTracker.totalLeft() + " L / " + CpsTracker.totalRight() + " R";
			return opt("clicks", "best") == 1 ? List.of(clicks, "Best CPS: " + CpsTracker.best()) : List.of(clicks);
		});

		// ---- World ----
		text("day", "Day Counter", "World", false, mc -> List.of("Day: " + (mc.world.getTimeOfDay() / 24000L)));
		text("worldtime", "World Time", "World", false, mc -> {
			long t = mc.world.getTimeOfDay() % 24000L;
			int hour = (int) ((t / 1000L + 6) % 24);
			int min = (int) ((t % 1000L) * 60L / 1000L);
			if (opt("worldtime", "fmt") == 1) {
				return List.of(String.format("World Time: %d:%02d %s", hour % 12 == 0 ? 12 : hour % 12, min, hour < 12 ? "AM" : "PM"));
			}
			return List.of(String.format("World Time: %02d:%02d", hour, min));
		});
		text("light", "Light Level", "World", false, mc -> List.of("Light: " + mc.world.getLightLevel(mc.player.getBlockPos())));
		text("chunk", "Chunk Coords", "World", false,
			mc -> List.of("Chunk: " + (mc.player.getBlockX() >> 4) + ", " + (mc.player.getBlockZ() >> 4)
				+ (opt("chunk", "offset") == 1 ? "  (" + (mc.player.getBlockX() & 15) + ", " + (mc.player.getBlockZ() & 15) + ")" : "")));
		text("nether", "Nether Coords", "World", false, Modules::netherCoords);
		text("target", "Target Block", "World", false, Modules::targetBlock);
		ALL.add(new CompassModule(4, 4 + 13 * slot++));
		ALL.add(new MinimapModule());

		// ---- Player ----
		text("health", "Health", "Player", false, mc -> {
			float hp = mc.player.getHealth();
			String v = opt("health", "whole") == 1 ? String.valueOf(Math.round(hp)) : String.format("%.1f", hp);
			if (opt("health", "max") == 1) v += " / " + Math.round(mc.player.getMaxHealth());
			return List.of("Health: " + v);
		});
		text("food", "Hunger", "Player", false, mc -> opt("food", "sat") == 0
			? List.of("Food: " + mc.player.getHungerManager().getFoodLevel())
			: List.of(String.format("Food: %d (sat %.1f)",
				mc.player.getHungerManager().getFoodLevel(), mc.player.getHungerManager().getSaturationLevel())));
		text("rotation", "Rotation", "Player", false,
			mc -> {
				String f = "%." + opt("rotation", "dec") + "f";
				return List.of(String.format("Yaw: " + f + "  Pitch: " + f, mc.player.getYaw(), mc.player.getPitch()));
			});
		text("helditem", "Held Item", "Player", false, mc -> {
			ItemStack s = mc.player.getMainHandStack();
			if (s.isEmpty()) return List.of();
			String n = s.getName().getString();
			if (opt("helditem", "count") == 1 && s.getCount() > 1) n += " x" + s.getCount();
			if (opt("helditem", "dur") == 1 && s.isDamageable() && s.getMaxDamage() > 0) n += "  (" + (s.getMaxDamage() - s.getDamage()) + " left)";
			return List.of(n);
		});
		text("arrows", "Arrow Counter", "Player", false, mc -> {
			int n = count(mc, Items.ARROW);
			return n > 0 ? List.of("Arrows: " + n) : List.of();
		});
		text("itemcounter", "Item Counter", "Player", false, Modules::itemCounter);
		text("fall", "Fall Distance", "Player", false,
			mc -> mc.player.fallDistance > Math.max(0.5, opt("fall", "min")) ? List.of(String.format("Fall: %.1f", mc.player.fallDistance)) : List.of());
		text("deathpos", "Death Coordinates", "Player", false, mc -> {
			RayConfig.Waypoint d = RayConfig.get().lastDeath;
			if (d == null) return List.of("Death: none yet");
			String where = (int) d.x + " " + (int) d.y + " " + (int) d.z;
			if (!d.dim.equals(mc.world.getRegistryKey().getValue().toString())) return List.of("Death: " + where + " (other dimension)");
			if (opt("deathpos", "dist") == 0) return List.of("Death: " + where);
			return List.of("Death: " + where + "  " + (int) distance(mc, d) + "m");
		});
		text("blockbelow", "Block Below", "World", false, mc -> {
			var below = mc.world.getBlockState(mc.player.getBlockPos().down());
			return List.of("Below: " + below.getBlock().getName().getString());
		});
		text("deaths", "Death Counter", "Player", false, mc -> List.of("Deaths: " + RayClient.sessionDeaths));
		text("sprintstatus", "Sprint Status", "Player", false, mc -> {
			if (RayConfig.get().toggleSprint) return List.of("[Sprint: Toggled]");
			if (mc.player.isSprinting()) return List.of("[Sprinting]");
			return List.of();
		});

		// ---- PvP (display only) ----
		text("combo", "Combo Counter", "PvP", false,
			mc -> CombatTracker.combo > 0 ? List.of("Combo: " + CombatTracker.combo) : List.of());
		text("reach", "Reach Display", "PvP", false,
			mc -> CombatTracker.reach >= 0 ? List.of(String.format("Reach: %." + opt("reach", "dec") + "f", CombatTracker.reach)) : List.of());
		text("cooldown", "Attack Cooldown", "PvP", false,
			mc -> {
				int pct = Math.round(mc.player.getAttackCooldownProgress(0.0f) * 100);
				if (pct >= 100 && opt("cooldown", "hidefull") == 1) return List.<String>of();
				return List.of(String.format("Cooldown: %d%%", pct));
			});
		ALL.add(new TargetInfoModule());
		text("bowcharge", "Bow Charge", "PvP", false, mc -> {
			if (!mc.player.isUsingItem() || !mc.player.getActiveItem().isOf(Items.BOW)) return List.of();
			return List.of("Bow: " + Math.round(BowItem.getPullProgress(mc.player.getItemUseTime()) * 100) + "%");
		});
		text("shield", "Shield Status", "PvP", false, mc -> {
			ItemStack s = mc.player.getOffHandStack().isOf(Items.SHIELD) ? mc.player.getOffHandStack()
				: (mc.player.getMainHandStack().isOf(Items.SHIELD) ? mc.player.getMainHandStack() : ItemStack.EMPTY);
			if (s.isEmpty()) return List.of();
			return List.of("Shield: " + (s.getMaxDamage() - s.getDamage()) + "/" + s.getMaxDamage() + (mc.player.isBlocking() ? " [Blocking]" : ""));
		});
		text("pearl", "Pearl Countdown", "PvP", false, mc -> {
			long ms = CombatTracker.pearlRemainingMs();
			return ms > 0 ? List.of(String.format("Pearl: %.1fs", ms / 1000.0)) : List.of();
		});

		scoreboard = new ScoreboardModule();
		ALL.add(scoreboard);

		// ---- Extras ----
		ALL.add(new InventoryModule());
		ALL.add(new GraphModule("fpsgraph", "FPS Graph", "Extras", false, HudModule.TR, 4, 66, "FPS", 60, MinecraftClient::getCurrentFps));
		ALL.add(new GraphModule("pinggraph", "Ping Graph", "Extras", false, HudModule.TR, 4, 108, "Ping", 100, Modules::pingValue));
		text("armorvalue", "Armor Value", "Extras", false, mc -> List.of("Armor: " + mc.player.getArmor()));
		text("xp", "XP Level", "Extras", false, mc -> List.of("XP: Level " + mc.player.experienceLevel
			+ (opt("xp", "progress") == 1 ? String.format(" (%d%%)", Math.round(mc.player.experienceProgress * 100)) : "")));
		text("air", "Air Supply", "Extras", false, mc -> mc.player.getAir() < mc.player.getMaxAir() || opt("air", "always") == 1
			? List.of(String.format("Air: %d%%", Math.max(0, mc.player.getAir()) * 100 / Math.max(1, mc.player.getMaxAir())))
			: List.of());
		text("weather", "Weather", "Extras", false,
			mc -> List.of("Weather: " + (mc.world.isThundering() ? "Thunder" : mc.world.isRaining() ? "Rain" : "Clear")));
		text("online", "Players Online", "Extras", false, mc -> mc.getNetworkHandler() != null
			? List.of("Online: " + mc.getNetworkHandler().getPlayerList().size())
			: List.of());
		text("blocks", "Block Counter", "Extras", false, Modules::blockCounter);
		text("playtime", "Total Playtime", "Extras", false, mc -> {
			long s = RayConfig.get().totalSeconds;
			if (opt("playtime", "sec") == 1) return List.of(String.format("Played: %dh %02dm %02ds", s / 3600, (s / 60) % 60, s % 60));
			return List.of(String.format("Played: %dh %02dm", s / 3600, (s / 60) % 60));
		});
		text("distance", "Distance Traveled", "Extras", false, mc -> {
			if (opt("distance", "unit") == 1 || traveled < 1000.0) return List.of(String.format("Traveled: %d blocks", (int) traveled));
			return List.of(String.format("Traveled: %.2f km", traveled / 1000.0));
		});
		text("toolcondition", "Tool Durability", "Extras", false, mc -> {
			ItemStack s = mc.player.getMainHandStack();
			if (s.isEmpty() || !s.isDamageable() || s.getMaxDamage() <= 0) return List.of();
			int left = s.getMaxDamage() - s.getDamage();
			int fmt = opt("toolcondition", "fmt");
			if (fmt == 1) return List.of("Durability: " + (left * 100 / s.getMaxDamage()) + "%");
			if (fmt == 2) return List.of("Durability: " + left);
			return List.of(String.format("Durability: %d/%d (%d%%)", left, s.getMaxDamage(), left * 100 / s.getMaxDamage()));
		});
	}

	private Modules() {}

	// ---------- registry helpers ----------

	private static void opts(String id, Opt... o) {
		OPTS.put(id, List.of(o));
	}

	public static List<Opt> optionsFor(String id) {
		return OPTS.getOrDefault(id, List.of());
	}

	/** Current value of a module option (falls back to its default). */
	public static int opt(String id, String key) {
		Integer v = RayConfig.get().style(id).opts.get(key);
		if (v != null) return v;
		for (Opt o : optionsFor(id)) if (o.key.equals(key)) return o.def;
		return 0;
	}

	private static void text(String id, String name, String cat, boolean on, Function<MinecraftClient, List<String>> f) {
		int col = slot / 14, row = slot % 14;
		slot++;
		ALL.add(new TextModule(id, name, cat, on, HudModule.TL, 4 + col * 118, 4 + 13 * row, f));
	}

	private static void textAt(String id, String name, String cat, boolean on, int anchor, int ox, int oy,
							   Function<MinecraftClient, List<String>> f) {
		ALL.add(new TextModule(id, name, cat, on, anchor, ox, oy, f));
	}

	public static void tick(MinecraftClient mc) {
		if (mc.player == null) return;
		double dx = mc.player.getX() - prevX;
		double dz = mc.player.getZ() - prevZ;
		prevX = mc.player.getX();
		prevZ = mc.player.getZ();
		double d = Math.hypot(dx, dz);
		speed = speed * 0.7 + d * 20.0 * 0.3;
		if (d < 10.0) traveled += d;
		FPS_RING[fpsIdx] = mc.getCurrentFps();
		fpsIdx = (fpsIdx + 1) % FPS_RING.length;
		if (fpsFilled < FPS_RING.length) fpsFilled++;
		if (++secTick >= 20) {
			secTick = 0;
			RayConfig c = RayConfig.get();
			c.totalSeconds++;
			if (c.totalSeconds % 60 == 0) RayConfig.save();
		}
	}

	// ---------- module sources ----------

	public static void stopwatchToggle() {
		long now = System.currentTimeMillis();
		if (swRunning) {
			swAccum += now - swStart;
			swRunning = false;
		} else {
			swStart = now;
			swRunning = true;
		}
	}

	public static void stopwatchReset() {
		swRunning = false;
		swAccum = 0;
	}

	private static List<String> stopwatch(MinecraftClient mc) {
		long ms = swAccum + (swRunning ? System.currentTimeMillis() - swStart : 0L);
		long sec = ms / 1000L;
		return List.of(String.format("Stopwatch: %d:%02d.%d%s", sec / 60, sec % 60, (ms % 1000L) / 100L, swRunning ? "" : " (paused)"));
	}

	private static List<String> fpsMinMax(MinecraftClient mc) {
		int min = Integer.MAX_VALUE, max = 0;
		for (int i = 0; i < fpsFilled; i++) {
			int v = FPS_RING[i];
			if (v <= 0) continue;
			min = Math.min(min, v);
			max = Math.max(max, v);
		}
		if (max == 0) return List.of();
		return List.of("FPS min " + min + " / max " + max);
	}

	private static List<String> biome(MinecraftClient mc) {
		String b = mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k -> k.getValue().getPath()).orElse("unknown");
		StringBuilder sb = new StringBuilder();
		for (String part : b.replace('_', ' ').split(" ")) {
			if (part.isEmpty()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return List.of("Biome: " + sb);
	}

	public static int pingValue(MinecraftClient mc) {
		if (mc.getNetworkHandler() == null || mc.player == null) return 0;
		PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
		return e != null ? e.getLatency() : 0;
	}

	private static List<String> ping(MinecraftClient mc) {
		return List.of("Ping: " + pingValue(mc) + (opt("ping", "suffix") == 1 ? " ms" : ""));
	}

	private static List<String> netherCoords(MinecraftClient mc) {
		String dim = mc.world.getRegistryKey().getValue().getPath();
		if (dim.equals("overworld")) {
			return List.of(String.format("Nether: %d / %d", (int) Math.floor(mc.player.getX() / 8.0), (int) Math.floor(mc.player.getZ() / 8.0)));
		}
		if (dim.equals("the_nether")) {
			return List.of(String.format("Overworld: %d / %d", (int) Math.floor(mc.player.getX() * 8.0), (int) Math.floor(mc.player.getZ() * 8.0)));
		}
		return List.of();
	}

	private static List<String> targetBlock(MinecraftClient mc) {
		if (mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
			var state = mc.world.getBlockState(bhr.getBlockPos());
			String n = state.getBlock().getName().getString();
			if (opt("target", "hardness") == 1) n += String.format(" (hardness %.1f)", state.getHardness(mc.world, bhr.getBlockPos()));
			return List.of("Looking at: " + n);
		}
		return List.of();
	}

	private static List<String> effects(MinecraftClient mc) {
		int max = opt("effects", "max");
		boolean timer = opt("effects", "timer") == 1;
		List<String> lines = new ArrayList<>();
		for (StatusEffectInstance e : mc.player.getStatusEffects()) {
			if (lines.size() >= max) break;
			if (e.getEffectType().equals(StatusEffects.NIGHT_VISION) && RayClient.fullbrightActive()) continue;
			String name = e.getEffectType().value().getName().getString();
			int secs = e.getDuration() / 20;
			String t = e.isInfinite() ? "inf" : String.format("%d:%02d", secs / 60, secs % 60);
			lines.add(name + " " + (e.getAmplifier() + 1) + (timer ? " - " + t : ""));
		}
		return lines;
	}

	private static List<String> waypoints(MinecraftClient mc) {
		RayConfig cfg = RayConfig.get();
		String dim = mc.world.getRegistryKey().getValue().toString();
		List<RayConfig.Waypoint> list = new ArrayList<>();
		for (RayConfig.Waypoint w : cfg.waypoints) if (dim.equals(w.dim)) list.add(w);
		list.sort(Comparator.comparingDouble(w -> distance(mc, w)));
		int max = opt("waypoints", "max");
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < Math.min(max, list.size()); i++) {
			RayConfig.Waypoint w = list.get(i);
			lines.add(w.name + "  " + (int) distance(mc, w) + "m " + compass(mc, w));
		}
		return lines;
	}

	private static double distance(MinecraftClient mc, RayConfig.Waypoint w) {
		double dx = w.x - mc.player.getX();
		double dy = w.y - mc.player.getY();
		double dz = w.z - mc.player.getZ();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static String compass(MinecraftClient mc, RayConfig.Waypoint w) {
		double dx = w.x - mc.player.getX();
		double dz = w.z - mc.player.getZ();
		double bearing = Math.toDegrees(Math.atan2(-dx, dz));
		double c = (bearing + 180.0 + 360.0) % 360.0;
		return DIRS[(int) Math.round(c / 45.0) % 8];
	}

	private static List<String> blockCounter(MinecraftClient mc) {
		ItemStack held = mc.player.getMainHandStack();
		if (held.isEmpty() || !(held.getItem() instanceof BlockItem)) return List.of();
		return List.of("Blocks: " + count(mc, held.getItem()));
	}

	private static List<String> itemCounter(MinecraftClient mc) {
		List<String> l = new ArrayList<>();
		int g = count(mc, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE);
		int p = count(mc, Items.ENDER_PEARL);
		int t = count(mc, Items.TOTEM_OF_UNDYING);
		int pots = count(mc, Items.SPLASH_POTION);
		if (g > 0) l.add("Gapples: " + g);
		if (p > 0) l.add("Pearls: " + p);
		if (t > 0) l.add("Totems: " + t);
		if (pots > 0) l.add("Splash Pots: " + pots);
		return l;
	}

	private static int count(MinecraftClient mc, Item... items) {
		int n = 0;
		for (int i = 0; i < mc.player.getInventory().size(); i++) {
			ItemStack s = mc.player.getInventory().getStack(i);
			for (Item it : items) if (s.isOf(it)) n += s.getCount();
		}
		return n;
	}
}
