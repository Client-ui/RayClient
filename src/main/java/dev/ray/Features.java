package dev.ray;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * One unified list of everything you can switch on in Ray: every HUD module plus the standalone features
 * (toggle sprint, zoom, crosshair, block outline...). The menu shows them all as cards.
 */
public final class Features {
	public static final String[] CATEGORIES = {"Stats", "World", "Player", "PvP", "Extras", "Visual", "Gameplay"};

	/** Builds the rows of a feature's settings page. */
	public interface Rows {
		void build(SettingsScreen screen, Ui ui, Ui.Form f);
	}

	/** One tile in the module grid. */
	public static final class Card {
		public final String id, name, category, keywords;
		/** One-line description shown in the tooltip. */
		public String desc = "";
		/** Live value shown on the menu tile, or null. */
		public java.util.function.Supplier<String> preview = null;
		public final BooleanSupplier on;
		public final Runnable flip;
		public final Function<Screen, Screen> settings;
		private final String iconKey;
		private ItemStack icon;

		Card(String id, String name, String category, String keywords, String iconKey, BooleanSupplier on, Runnable flip,
			 Function<Screen, Screen> settings) {
			this.id = id;
			this.name = name;
			this.category = category;
			this.keywords = (name + " " + category + " " + keywords).toLowerCase();
			this.iconKey = iconKey;
			this.on = on;
			this.flip = flip;
			this.settings = settings;
		}

		public ItemStack icon() {
			if (icon == null) icon = new ItemStack(iconItem(iconKey, category));
			return icon;
		}

		public boolean favorite() {
			return RayConfig.get().favorites.contains(id);
		}

		public void toggleFavorite() {
			List<String> fav = RayConfig.get().favorites;
			if (!fav.remove(id)) fav.add(id);
			RayConfig.save();
		}
	}

	private static final java.util.Map<String, String> DESC = java.util.Map.ofEntries(
		java.util.Map.entry("air", "Shows your air supply while you are underwater."),
		java.util.Map.entry("armorvalue", "The total armor points you are wearing."),
		java.util.Map.entry("armor", "Your equipped armor with durability, on the HUD."),
		java.util.Map.entry("arrows", "How many arrows you are carrying."),
		java.util.Map.entry("biome", "The biome you are standing in."),
		java.util.Map.entry("blockbelow", "The name of the block under your feet."),
		java.util.Map.entry("blocks", "Counts the blocks you place or break."),
		java.util.Map.entry("bowcharge", "How far your bow is drawn."),
		java.util.Map.entry("chunk", "Your chunk coordinates, and optionally your position inside the chunk."),
		java.util.Map.entry("clicks", "Total left and right clicks, plus your best CPS."),
		java.util.Map.entry("clock", "Your computer's time of day."),
		java.util.Map.entry("combo", "Hits in a row without being hit."),
		java.util.Map.entry("compass", "A compass strip showing where you are facing."),
		java.util.Map.entry("cooldown", "How recharged your attack is."),
		java.util.Map.entry("coords", "Your X, Y and Z position."),
		java.util.Map.entry("cps", "Clicks per second."),
		java.util.Map.entry("date", "Today's date, in your choice of format."),
		java.util.Map.entry("day", "How many in-game days have passed."),
		java.util.Map.entry("deathpos", "Where you last died and how far away it is."),
		java.util.Map.entry("deaths", "How many times you have died this session."),
		java.util.Map.entry("direction", "The way you are facing, optionally with degrees."),
		java.util.Map.entry("distance", "How far you have traveled."),
		java.util.Map.entry("effects", "Your active potion effects and their timers."),
		java.util.Map.entry("fall", "How far you have fallen."),
		java.util.Map.entry("food", "Your hunger and saturation."),
		java.util.Map.entry("fpsminmax", "Lowest and highest FPS since you started."),
		java.util.Map.entry("fps", "Your frames per second."),
		java.util.Map.entry("health", "Your health, optionally with your maximum."),
		java.util.Map.entry("helditem", "The item in your hand, with stack count and durability."),
		java.util.Map.entry("inventory", "A copy of your inventory on the HUD."),
		java.util.Map.entry("itemcounter", "Counts one kind of item across your inventory."),
		java.util.Map.entry("keystrokes", "Shows your movement keys and mouse buttons as you press them."),
		java.util.Map.entry("light", "The light level where you stand."),
		java.util.Map.entry("memory", "How much memory the game is using."),
		java.util.Map.entry("minimap", "A small map of the terrain around you with your waypoints on it."),
		java.util.Map.entry("nether", "Your coordinates converted to the other dimension."),
		java.util.Map.entry("online", "How many players are on the server."),
		java.util.Map.entry("pearl", "The cooldown on your ender pearls."),
		java.util.Map.entry("ping", "Your connection delay to the server."),
		java.util.Map.entry("playtime", "Your total time played with Ray Client."),
		java.util.Map.entry("reach", "How far away your last hit landed."),
		java.util.Map.entry("rotation", "Your exact yaw and pitch."),
		java.util.Map.entry("scoreboard", "Moves and styles the server scoreboard."),
		java.util.Map.entry("serverip", "The address of the server you are on."),
		java.util.Map.entry("session", "How long you have been playing this session."),
		java.util.Map.entry("shield", "Whether your shield is up or on cooldown."),
		java.util.Map.entry("speed", "How fast you are moving."),
		java.util.Map.entry("sprintstatus", "Whether you are sprinting, sneaking or walking."),
		java.util.Map.entry("stopwatch", "A stopwatch you start and stop with a key."),
		java.util.Map.entry("targetinfo", "Health and details of the player you are hitting."),
		java.util.Map.entry("target", "The block you are looking at."),
		java.util.Map.entry("toolcondition", "The durability of the tool in your hand."),
		java.util.Map.entry("waypoints", "A list of your waypoints and how far away they are."),
		java.util.Map.entry("weather", "The current weather."),
		java.util.Map.entry("worldtime", "The in-game time of day."),
		java.util.Map.entry("xp", "Your experience level and progress."),
		java.util.Map.entry("f_sprint", "Keeps you sprinting while you hold forward."),
		java.util.Map.entry("f_sneak", "Press sneak once to start, again to stop."),
		java.util.Map.entry("f_zoom", "Hold a key to zoom in smoothly, with adjustable strength."),
		java.util.Map.entry("f_freelook", "Look around freely while your character keeps facing the same way."),
		java.util.Map.entry("f_deathpoint", "Saves a waypoint where you die so you can get your items back."),
		java.util.Map.entry("f_fullbright", "Makes everything fully lit, with no potion effect."),
		java.util.Map.entry("f_hurtcam", "Stops the screen tilting when you take damage."),
		java.util.Map.entry("f_viewmodel", "Moves and resizes the item in your hand."),
		java.util.Map.entry("f_swing", "Changes how long your swing animation lasts."),
		java.util.Map.entry("f_hitmarker", "A marker around the crosshair when you hit something."),
		java.util.Map.entry("f_hitsound", "A sound when you land a hit. Use the built-in one or your own file."),
		java.util.Map.entry("f_lowhealth", "Pulses a colored border when your health is low."),
		java.util.Map.entry("f_durability", "Warns you when armor or a tool is about to break."),
		java.util.Map.entry("f_respawn", "Respawns you automatically after you die."),
		java.util.Map.entry("f_wpmarkers", "Draws your waypoints in the world with name and distance."),
		java.util.Map.entry("f_lowfire", "Lowers the flames on your screen when you are burning, so you can still see."),
		java.util.Map.entry("f_sideshield", "Moves your raised shield off to the side so it does not block your view."),
		java.util.Map.entry("f_stretch", "Stretch the 3D world wider or taller, like a stretched resolution, without changing your window size. The hand and menus stay normal."),
		java.util.Map.entry("f_sky", "Swap the sky for one of 26 pre-made skies (sunset, aurora, nebula, synthwave, sakura, volcano...) or pick your own colours."),
		java.util.Map.entry("f_lyrics", "Shows your Spotify lyrics at the top and as floating words that pop up where you look."),
		java.util.Map.entry("f_hitcolor", "Changes the color of the flash when something is hurt."),
		java.util.Map.entry("f_hitbox", "Draws a custom-colored box around players you can see."),
		java.util.Map.entry("f_crosshair", "Replace the crosshair: draw your own, scale it, blend it."),
		java.util.Map.entry("f_outline", "Outlines the face of the block you are looking at, or the whole block."),
		java.util.Map.entry("f_mcfont", "Changes the font of Minecraft itself: chat, menus, tooltips."),
		java.util.Map.entry("f_clientfont", "Changes the font of the client's own menus and HUD."));

	private static List<Card> cache;

	private Features() {}

	public static String normalize(String category) {
		return "Visuals".equals(category) ? "Visual" : category;
	}

	public static List<Card> cards() {
		if (cache == null) cache = build();
		return cache;
	}

	// ---------- icons ----------

	private static final Map<String, Item> ICONS = new HashMap<>();

	static {
		ICONS.put("fps", Items.REPEATER);
		ICONS.put("cps", Items.LEVER);
		ICONS.put("ping", Items.LEAD);
		ICONS.put("coords", Items.COMPASS);
		ICONS.put("direction", Items.RECOVERY_COMPASS);
		ICONS.put("biome", Items.GRASS_BLOCK);
		ICONS.put("keystrokes", Items.LEVER);
		ICONS.put("armor", Items.IRON_CHESTPLATE);
		ICONS.put("effects", Items.POTION);
		ICONS.put("waypoints", Items.MAP);
		ICONS.put("speed", Items.SUGAR);
		ICONS.put("memory", Items.COMPARATOR);
		ICONS.put("clock", Items.CLOCK);
		ICONS.put("date", Items.BOOK);
		ICONS.put("session", Items.CLOCK);
		ICONS.put("stopwatch", Items.CLOCK);
		ICONS.put("serverip", Items.NAME_TAG);
		ICONS.put("day", Items.CLOCK);
		ICONS.put("worldtime", Items.CLOCK);
		ICONS.put("light", Items.TORCH);
		ICONS.put("compass", Items.COMPASS);
		ICONS.put("weather", Items.WATER_BUCKET);
		ICONS.put("health", Items.GOLDEN_APPLE);
		ICONS.put("food", Items.COOKED_BEEF);
		ICONS.put("arrows", Items.ARROW);
		ICONS.put("bowcharge", Items.BOW);
		ICONS.put("shield", Items.SHIELD);
		ICONS.put("pearl", Items.ENDER_PEARL);
		ICONS.put("targetinfo", Items.DIAMOND_SWORD);
		ICONS.put("inventory", Items.CHEST);
		ICONS.put("fpsgraph", Items.PAPER);
		ICONS.put("pinggraph", Items.PAPER);
		ICONS.put("xp", Items.EXPERIENCE_BOTTLE);
		ICONS.put("air", Items.GLASS_BOTTLE);
		ICONS.put("blocks", Items.CRAFTING_TABLE);
		ICONS.put("distance", Items.LEATHER_BOOTS);
		ICONS.put("online", Items.NAME_TAG);
		ICONS.put("toolcondition", Items.DIAMOND_PICKAXE);
		ICONS.put("scoreboard", Items.OAK_SIGN);
		// standalone features
		ICONS.put("f_sprint", Items.IRON_BOOTS);
		ICONS.put("f_sneak", Items.LEATHER_BOOTS);
		ICONS.put("f_zoom", Items.SPYGLASS);
		ICONS.put("f_fullbright", Items.LANTERN);
		ICONS.put("f_hurtcam", Items.TOTEM_OF_UNDYING);
		ICONS.put("f_viewmodel", Items.DIAMOND_SWORD);
		ICONS.put("f_swing", Items.IRON_SWORD);
		ICONS.put("f_hitmarker", Items.TARGET);
		ICONS.put("f_hitsound", Items.NOTE_BLOCK);
		ICONS.put("f_lowhealth", Items.GOLDEN_APPLE);
		ICONS.put("f_durability", Items.ANVIL);
		ICONS.put("f_respawn", Items.TOTEM_OF_UNDYING);
		ICONS.put("f_scoreboard", Items.OAK_SIGN);
		ICONS.put("f_crosshair", Items.TARGET);
		ICONS.put("f_outline", Items.GLASS);
		ICONS.put("minimap", Items.FILLED_MAP);
		ICONS.put("f_wpmarkers", Items.RECOVERY_COMPASS);
		ICONS.put("f_freelook", Items.ENDER_EYE);
		ICONS.put("f_sky", Items.CLOCK);
		ICONS.put("f_stretch", Items.SPYGLASS);
		ICONS.put("f_lyrics", Items.JUKEBOX);
		ICONS.put("f_hitcolor", Items.RED_DYE);
		ICONS.put("f_lowfire", Items.FIRE_CHARGE);
		ICONS.put("f_sideshield", Items.SHIELD);
		ICONS.put("f_clientfont", Items.BOOK);
		ICONS.put("f_mcfont", Items.NAME_TAG);
		ICONS.put("f_hitbox", Items.TARGET);
		ICONS.put("f_deathpoint", Items.SKELETON_SKULL);
		ICONS.put("deathpos", Items.SKELETON_SKULL);
		ICONS.put("f_hud", Items.BEACON);
		ICONS.put("f_title", Items.NETHER_STAR);
		ICONS.put("f_loading", Items.NETHER_STAR);
	}

	private static Item iconItem(String key, String category) {
		Item it = ICONS.get(key);
		if (it != null) return it;
		return switch (category) {
			case "Stats" -> Items.REPEATER;
			case "World" -> Items.COMPASS;
			case "Player" -> Items.LEATHER_CHESTPLATE;
			case "PvP" -> Items.DIAMOND_SWORD;
			case "Visual" -> Items.ENDER_EYE;
			case "Gameplay" -> Items.IRON_BOOTS;
			default -> Items.NETHER_STAR;
		};
	}

	// ---------- registry ----------

	private static Card feature(String id, String name, String category, String keywords, BooleanSupplier on, Runnable flip, Rows rows) {
		return new Card(id, name, category, keywords, id, on, flip, parent -> new FeatureSettingsScreen(parent, name, rows));
	}

	private static List<Card> build() {
		List<Card> out = new ArrayList<>();

		for (HudModule m : Modules.ALL) {
			Card mc = new Card(m.id, m.name, normalize(m.category), "hud module", m.id,
				m::enabled,
				() -> m.setEnabled(!m.enabled()),
				parent -> new ModuleSettingsScreen(parent, m));
			mc.preview = () -> m.preview(net.minecraft.client.MinecraftClient.getInstance());
			out.add(mc);
		}

		out.add(feature("f_sprint", "Toggle Sprint", "Gameplay", "sprint run hold",
			() -> RayConfig.get().toggleSprint, () -> { RayConfig c = RayConfig.get(); c.toggleSprint = !c.toggleSprint; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Toggle Sprint");
				ui.toggle(f.full(), "Enabled", c.toggleSprint, () -> c.toggleSprint = !c.toggleSprint);
				f.note("Hold forward and you keep sprinting, without holding the sprint key.");
			}));

		out.add(feature("f_sneak", "Toggle Sneak", "Gameplay", "sneak crouch hold",
			() -> RayConfig.get().toggleSneak, () -> { RayConfig c = RayConfig.get(); c.toggleSneak = !c.toggleSneak; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Toggle Sneak");
				ui.toggle(f.full(), "Enabled", c.toggleSneak, () -> c.toggleSneak = !c.toggleSneak);
				f.note("Press sneak once to start sneaking, again to stop.");
			}));

		out.add(feature("f_zoom", "Zoom", "Gameplay", "fov spyglass optifine",
			() -> RayConfig.get().zoomEnabled, () -> { RayConfig c = RayConfig.get(); c.zoomEnabled = !c.zoomEnabled; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Zoom");
				ui.toggle(f.half(), "Enabled", c.zoomEnabled, () -> c.zoomEnabled = !c.zoomEnabled);
				ui.toggle(f.half(), "Smooth Zoom", c.smoothZoom, () -> c.smoothZoom = !c.smoothZoom);
				ui.sliderShown(f.half(), "Zoom Strength", 15, 100, c.zoomLevel, zoomText(c.zoomLevel), v -> c.zoomLevel = v);
				ui.slider(f.half(), "FOV Effects", 0, 100, (int) Math.round(ui.mc.options.getFovEffectScale().getValue() * 100.0), "%",
					v -> ui.mc.options.getFovEffectScale().setValue(v / 100.0));
				f.note("Zoom Strength is how far you zoom in: 2.0x is mild, 10.0x is like a spyglass. Hold the Zoom key (C by default, change it in Controls).");
			}));

		out.add(feature("f_freelook", "Free Look", "Gameplay", "camera look around perspective rotate alt",
			() -> RayConfig.get().freelook, () -> { RayConfig c = RayConfig.get(); c.freelook = !c.freelook; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Free Look");
				ui.toggle(f.half(), "Enabled", c.freelook, () -> c.freelook = !c.freelook);
				ui.choice(f.half(), "Mode", c.freelookToggle ? "Toggle" : "Hold", () -> c.freelookToggle = !c.freelookToggle);
				ui.toggle(f.full(), "Switch To Third Person", c.freelookThirdPerson, () -> c.freelookThirdPerson = !c.freelookThirdPerson);
				FreeLook.pollBinding(ui.mc);
				ui.choice(f.full(), "Key / Button", FreeLook.bindingName(c), FreeLook::startListening);
				f.note("Click Key / Button, then press any keyboard key or a mouse button (middle, side buttons...). Backspace clears it. While active, the mouse turns the camera but not your character. From first person it steps back into third person and returns to first person when you let go.");
			}));

		out.add(feature("f_deathpoint", "Death Waypoint", "Gameplay", "death waypoint save items grave map",
			() -> RayConfig.get().deathWaypoint, () -> { RayConfig c = RayConfig.get(); c.deathWaypoint = !c.deathWaypoint; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Death Waypoint");
				ui.toggle(f.full(), "Enabled", c.deathWaypoint, () -> c.deathWaypoint = !c.deathWaypoint);
				f.note("Saves a waypoint named Death where you die, so you can find your items. It shows in the world and on the Minimap. Turn on the Death Coordinates module to see the position on your HUD.");
			}));

		out.add(feature("f_fullbright", "Fullbright", "Visual", "night vision gamma bright dark",
			() -> RayConfig.get().fullbright, () -> { RayConfig c = RayConfig.get(); c.fullbright = !c.fullbright; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Fullbright");
				ui.toggle(f.half(), "Enabled", c.fullbright, () -> c.fullbright = !c.fullbright);
				ui.slider(f.half(), "Brightness", 2, 20, c.fullbrightLevel, v -> c.fullbrightLevel = v);
				ui.choice(f.half(), "Mode", c.fullbrightMode == 1 ? "Gamma + Night Vision" : "Gamma", () -> c.fullbrightMode = 1 - c.fullbrightMode);
				f.note("Gamma pushes the game brightness far past the normal maximum so caves, nights and shadows are fully lit, with no potion effect. If it still looks too dark, set Mode to Gamma + Night Vision. Your normal brightness is restored when you turn it off.");
			}));

		out.add(feature("f_hurtcam", "No Hurt Cam", "Visual", "damage tilt shake screen",
			() -> RayConfig.get().noHurtCam, () -> { RayConfig c = RayConfig.get(); c.noHurtCam = !c.noHurtCam; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("No Hurt Cam");
				ui.toggle(f.full(), "Enabled", c.noHurtCam, () -> c.noHurtCam = !c.noHurtCam);
				f.note("Stops the screen tilting when you take damage.");
			}));

		out.add(feature("f_viewmodel", "Viewmodel", "Visual", "hand item position first person offset scale",
			() -> RayConfig.get().viewmodel, () -> { RayConfig c = RayConfig.get(); c.viewmodel = !c.viewmodel; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Viewmodel");
				ui.toggle(f.full(), "Enabled", c.viewmodel, () -> c.viewmodel = !c.viewmodel);
				ui.slider(f.half(), "Scale", 20, 150, c.vmScale, "%", v -> c.vmScale = v);
				ui.slider(f.half(), "X", -50, 50, c.vmX, v -> c.vmX = v);
				ui.slider(f.half(), "Y", -50, 50, c.vmY, v -> c.vmY = v);
				ui.slider(f.half(), "Z", -50, 50, c.vmZ, v -> c.vmZ = v);
				f.note("Moves and resizes the item in your hand. Visual only.");
			}));

		out.add(feature("f_swing", "Custom Swing", "Visual", "animation hand swing speed",
			() -> RayConfig.get().customSwing, () -> { RayConfig c = RayConfig.get(); c.customSwing = !c.customSwing; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Custom Swing");
				ui.toggle(f.half(), "Enabled", c.customSwing, () -> c.customSwing = !c.customSwing);
				ui.slider(f.half(), "Swing Duration", 1, 30, c.swingDuration, " ticks", v -> c.swingDuration = v);
				f.note("Changes how long your swing animation lasts. Visual only; attack timing is unchanged.");
			}));

		out.add(feature("f_hitmarker", "Hit Marker", "PvP", "hit indicator crosshair damage",
			() -> RayConfig.get().hitMarker, () -> { RayConfig c = RayConfig.get(); c.hitMarker = !c.hitMarker; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Hit Marker");
				ui.toggle(f.full(), "Enabled", c.hitMarker, () -> c.hitMarker = !c.hitMarker);
				ui.slider(f.half(), "Size", 4, 16, c.hitMarkerSize, v -> c.hitMarkerSize = v);
				ui.slider(f.half(), "Duration", 100, 600, c.hitMarkerTime, " ms", v -> c.hitMarkerTime = v);
				f.section("Color");
				ui.colorPicker(f, "hm_color", c.hitMarkerColor, v -> c.hitMarkerColor = v);
			}));

		out.add(feature("f_hitsound", "Hit Sound", "PvP", "sound audio hit volume pitch custom wav ogg file",
			() -> RayConfig.get().hitSound, () -> { RayConfig c = RayConfig.get(); c.hitSound = !c.hitSound; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				java.util.List<String> files = CustomSounds.list();
				f.section("Hit Sound");
				ui.toggle(f.half(), "Enabled", c.hitSound, () -> c.hitSound = !c.hitSound);
				ui.choice(f.half(), "Sound", c.hitSoundCustom ? "My own file" : "Minecraft", () -> c.hitSoundCustom = !c.hitSoundCustom);
				ui.slider(f.half(), "Volume", 10, 100, c.hitSoundVolume, "%", v -> c.hitSoundVolume = v);
				if (c.hitSoundCustom) {
					ui.slider(f.half(), "Speed / Pitch", 50, 200, c.hitSoundFilePitch, "%", v -> c.hitSoundFilePitch = v);
					int idx = files.indexOf(c.hitSoundFile);
					String shown = files.isEmpty() ? "No files yet" : (idx < 0 ? "Pick a file" : c.hitSoundFile);
					ui.choice(f.full(), "File", shown, () -> {
						if (files.isEmpty()) return;
						c.hitSoundFile = files.get((Math.max(idx, -1) + 1) % files.size());
					});
				} else {
					ui.slider(f.half(), "Pitch", 50, 200, c.hitSoundPitch, "%", v -> c.hitSoundPitch = v);
				}
				ui.button(f.half(), "Test Sound", () -> CombatTracker.playHitSound(ui.mc));
				if (c.hitSoundCustom) ui.button(f.half(), "Open Sounds Folder", CustomSounds::openFolder);
				if (c.hitSoundCustom) {
					f.note("Put your own .wav or .ogg files in: " + CustomSounds.folder() + "  then click File to cycle through them.");
					int bad = CustomSounds.unsupportedCount();
					if (bad > 0) f.note(bad + " MP3/MP4/M4A/AAC/FLAC file(s) found. Minecraft cannot play those: convert them to .wav (any free converter) and drop the .wav in the folder.");
				} else {
					f.note("Plays a short sound when you land a hit. Switch Sound to My own file to use your own .wav or .ogg.");
				}
			}));

		out.add(feature("f_lowhealth", "Low Health Warning", "Gameplay", "red pulse screen hearts",
			() -> RayConfig.get().lowHealthWarning, () -> { RayConfig c = RayConfig.get(); c.lowHealthWarning = !c.lowHealthWarning; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Low Health Warning");
				ui.toggle(f.full(), "Enabled", c.lowHealthWarning, () -> c.lowHealthWarning = !c.lowHealthWarning);
				ui.slider(f.half(), "Warn At (hearts)", 1, 10, c.lowHealthHearts, v -> c.lowHealthHearts = v);
				ui.slider(f.half(), "Border Thickness", 6, 30, c.lowHealthThickness, v -> c.lowHealthThickness = v);
				f.section("Color");
				ui.colorPicker(f, "lh_color", c.lowHealthColor, v -> c.lowHealthColor = v);
			}));

		out.add(feature("f_durability", "Durability Alert", "Gameplay", "tool armor break low warning",
			() -> RayConfig.get().durabilityAlert, () -> { RayConfig c = RayConfig.get(); c.durabilityAlert = !c.durabilityAlert; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Durability Alert");
				ui.toggle(f.full(), "Enabled", c.durabilityAlert, () -> c.durabilityAlert = !c.durabilityAlert);
				ui.slider(f.half(), "Warn Below", 5, 50, c.durabilityPercent, "%", v -> c.durabilityPercent = v);
				ui.slider(f.half(), "Repeat Every", 5, 60, c.durabilitySeconds, " s", v -> c.durabilitySeconds = v);
				f.note("Shows a message when your armor or held tool drops below this durability.");
			}));

		out.add(feature("f_respawn", "Auto Respawn", "Gameplay", "death screen respawn delay",
			() -> RayConfig.get().autoRespawn, () -> { RayConfig c = RayConfig.get(); c.autoRespawn = !c.autoRespawn; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Auto Respawn");
				ui.toggle(f.half(), "Enabled", c.autoRespawn, () -> c.autoRespawn = !c.autoRespawn);
				ui.slider(f.half(), "Delay", 0, 60, c.respawnDelay, " ticks", v -> c.respawnDelay = v);
				f.note("Skips the death screen and respawns for you. Delay is in ticks: 20 ticks = 1 second, so you can still see what killed you.");
			}));

		out.add(feature("f_wpmarkers", "Waypoint Markers", "World", "waypoints show world map markers beacon distance",
			() -> RayConfig.get().waypointMarkers, () -> { RayConfig c = RayConfig.get(); c.waypointMarkers = !c.waypointMarkers; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Waypoint Markers");
				ui.toggle(f.half(), "Show in World", c.waypointMarkers, () -> c.waypointMarkers = !c.waypointMarkers);
				ui.toggle(f.half(), "Show Distance", c.waypointShowDist, () -> c.waypointShowDist = !c.waypointShowDist);
				ui.sliderShown(f.full(), "Max Distance", 0, 2000, c.waypointMaxDist, c.waypointMaxDist == 0 ? "Unlimited" : c.waypointMaxDist + "m", v -> c.waypointMaxDist = v);
				f.note("Draws a marker with the name and distance where each waypoint is. Turn on the Minimap module to also see your waypoints on a map. Press B to save a waypoint where you stand.");
			}));

		out.add(feature("f_hitcolor", "Hit Color", "PvP", "hurt flash damage tint red color wheel",
			() -> RayConfig.get().hitColor, () -> { RayConfig c = RayConfig.get(); c.hitColor = !c.hitColor; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Hit Color");
				ui.toggle(f.half(), "Enabled", c.hitColor, () -> c.hitColor = !c.hitColor);
				ui.slider(f.half(), "Strength", 10, 100, c.hitColorStrength, "%", v -> c.hitColorStrength = v);
				f.section("Color");
				ui.colorPicker(f, "hc_color", c.hitColorRgb, v -> c.hitColorRgb = v);
				f.note("Changes the colour of the flash an entity gets when it is hurt (vanilla is a 70% red). It applies to everything that gets hurt, so it is not limited to players you hit.");
			}));

		out.add(feature("f_hitbox", "Custom Hitbox", "PvP", "hitbox box outline players color hover eye look",
			() -> RayConfig.get().hitbox, () -> { RayConfig c = RayConfig.get(); c.hitbox = !c.hitbox; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Custom Hitbox");
				ui.toggle(f.half(), "Enabled", c.hitbox, () -> c.hitbox = !c.hitbox);
				ui.choice(f.half(), "Show On", c.hitboxTarget == 0 ? "Players" : "All Living", () -> c.hitboxTarget = 1 - c.hitboxTarget);
				ui.slider(f.half(), "Thickness", 1, 4, c.hitboxThickness, v -> c.hitboxThickness = v);
				ui.slider(f.half(), "Opacity", 20, 100, c.hitboxOpacity, "%", v -> c.hitboxOpacity = v);
				f.section("Box color");
				ui.colorPicker(f, "hb_color", c.hitboxColor, v -> c.hitboxColor = v);
				f.section("When your crosshair is on it");
				ui.toggle(f.full(), "Use a different color", c.hitboxHover, () -> c.hitboxHover = !c.hitboxHover);
				ui.colorPicker(f, "hb_hover", c.hitboxHoverColor, v -> c.hitboxHoverColor = v);
				f.section("Vanilla extras");
				ui.toggle(f.half(), "Eye Line (red)", c.hitboxEye, () -> c.hitboxEye = !c.hitboxEye);
				ui.toggle(f.half(), "Look Line (blue)", c.hitboxLook, () -> c.hitboxLook = !c.hitboxLook);
				f.note("Unlike vanilla's F3+B boxes, the red eye-height line and the blue look-direction line are optional and off by default. Only players you can actually see are boxed: never through walls, and never invisible ones.");
			}));

		out.add(feature("f_mcfont", "Minecraft Font", "Visual", "font text typeface chat game vanilla custom ttf otf",
			() -> RayConfig.get().minecraftFontOn, () -> { RayConfig c = RayConfig.get(); c.minecraftFontOn = !c.minecraftFontOn; },
			(s, ui, f) -> fontRows(ui, f, false)));

		out.add(feature("f_lowfire", "Low Fire", "Visual", "fire burning overlay flames screen view lava",
			() -> RayConfig.get().lowFire, () -> { RayConfig c = RayConfig.get(); c.lowFire = !c.lowFire; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Low Fire");
				ui.toggle(f.half(), "Enabled", c.lowFire, () -> c.lowFire = !c.lowFire);
				ui.slider(f.half(), "Fire Height", 0, 100, c.lowFireAmount, "%", v -> c.lowFireAmount = v);
				f.note("Lowers the fire that covers your screen when you are burning. Higher = lower flames, so more of the view stays clear. 100% drops it by half a block.");
			}));

		out.add(feature("f_sideshield", "Side Shield", "PvP", "shield block side position low blocking offhand view",
			() -> RayConfig.get().sideShield, () -> { RayConfig c = RayConfig.get(); c.sideShield = !c.sideShield; },
			(s, ui, f) -> {
				RayConfig c = RayConfig.get();
				f.section("Side Shield");
				ui.toggle(f.half(), "Enabled", c.sideShield, () -> c.sideShield = !c.sideShield);
				ui.toggle(f.half(), "Only When Blocking", c.ssOnlyBlocking, () -> c.ssOnlyBlocking = !c.ssOnlyBlocking);

				f.section("Presets");
				int ptop = f.reserve(40);
				int px2 = f.x(), py2 = ptop;
				for (int i = 0; i < SideShield.PRESETS.length; i++) {
					final int idx = i;
					String pn = (String) SideShield.PRESETS[i][0];
					int cw = ui.w(pn) + 14;
					if (px2 + cw > f.x() + f.width()) {
						px2 = f.x();
						py2 += 20;
					}
					px2 += ui.chip(px2, py2, pn, SideShield.matches(c, i), () -> SideShield.apply(c, idx)) + 4;
				}

				f.section("Fine Tuning");
				ui.slider(f.half(), "Sideways", -50, 80, c.ssX, v -> c.ssX = v);
				ui.slider(f.half(), "Height", -60, 40, c.ssY, v -> c.ssY = v);
				ui.slider(f.half(), "Depth", -50, 50, c.ssZ, v -> c.ssZ = v);
				ui.slider(f.half(), "Size", 30, 150, c.ssScale, "%", v -> c.ssScale = v);
				ui.slider(f.half(), "Tilt", -60, 60, c.ssTilt, " deg", v -> c.ssTilt = v);
				ui.toggle(f.half(), "Smooth Glide", c.ssSmooth, () -> c.ssSmooth = !c.ssSmooth);
				f.note(SideShield.hooked ? "Status: active. Raise a shield in a world to see the result."
					: "Status: waiting. This turns to active the first time a shield is drawn in your hand; if it never does while you hold one, tell me and I will check this Minecraft version.");
				f.note("Moves your shield away from the middle of the screen so it stops blocking your view. Sideways pushes it outward, Height lowers it (negative = down), Size shrinks it. It only changes how the shield looks; blocking works exactly the same.");
			}));

		out.add(feature("f_stretch", "Stretched Res", "Visual", "stretch stretched resolution aspect ratio 4:3 widescreen fov squeeze",
			() -> RayConfig.get().stretchOn, () -> { RayConfig c = RayConfig.get(); c.stretchOn = !c.stretchOn; },
			Features::stretchRows));

		out.add(feature("f_sky", "Custom Sky", "Visual", "sky skybox sunset night aurora clouds fog horizon color",
			() -> RayConfig.get().skyOn, () -> { RayConfig c = RayConfig.get(); c.skyOn = !c.skyOn; },
			Features::skyRows));

		out.add(feature("f_lyrics", "Lyric Sync", "Extras", "spotify lyrics music song karaoke words subtitle",
			() -> RayConfig.get().lyricsOn, () -> { RayConfig c = RayConfig.get(); c.lyricsOn = !c.lyricsOn; },
			Features::lyricsRows));

		out.add(feature("f_crosshair", "Custom Crosshair", "Visual", "aim reticle dot draw editor blending",
			() -> RayConfig.get().customCrosshair, () -> { RayConfig c = RayConfig.get(); c.customCrosshair = !c.customCrosshair; },
			Features::crosshairRows));

		out.add(feature("f_outline", "Block Outline", "Visual", "highlight selection target block box",
			() -> RayConfig.get().blockOutline, () -> { RayConfig c = RayConfig.get(); c.blockOutline = !c.blockOutline; },
			Features::outlineRows));

		for (Card c : out) c.desc = DESC.getOrDefault(c.id, "A " + c.category.toLowerCase() + " module.");
		out.sort(Comparator.<Card>comparingInt(c -> categoryIndex(c.category)).thenComparing(c -> c.name.toLowerCase()));
		return out;
	}

	/** 30 -> "3.0x" (done by hand so the decimal separator never changes with the system language). */
	public static String zoomText(int level) {
		return (level / 10) + "." + (level % 10) + "x";
	}

	public static int categoryIndex(String category) {
		for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].equals(category)) return i;
		return CATEGORIES.length;
	}

	// ---------- bigger settings pages ----------

	private static void stretchRows(SettingsScreen s, Ui ui, Ui.Form f) {
		RayConfig c = RayConfig.get();
		f.section("Stretched Res");
		ui.toggle(f.full(), "Enabled", c.stretchOn, () -> c.stretchOn = !c.stretchOn);
		ui.sliderShown(f.half(), "Horizontal", 50, 250, c.stretchX, Stretch.text(c.stretchX), v -> c.stretchX = v);
		ui.sliderShown(f.half(), "Vertical", 50, 250, c.stretchY, Stretch.text(c.stretchY), v -> c.stretchY = v);

		f.section("Quick Presets");
		int top = f.reserve(18);
		int x = f.x();
		String[] names = {"OFF", "4:3", "5:4", "16:10", "NARROW"};
		int[][] vals = {{100, 100}, {133, 100}, {125, 100}, {110, 100}, {80, 100}};
		for (int i = 0; i < names.length; i++) {
			final int idx = i;
			boolean sel = c.stretchX == vals[i][0] && c.stretchY == vals[i][1];
			x += ui.chip(x, top, names[i], sel, () -> {
				c.stretchX = vals[idx][0];
				c.stretchY = vals[idx][1];
				c.stretchOn = idx != 0;
			}) + 4;
		}
		f.note("Stretches how the 3D world is drawn, like a stretched resolution in a shooter, but your real window resolution does not change. 1.33x horizontal is the classic 4:3 look. The first-person hand and all menus keep their normal shape.");
		f.note(Stretch.hooked ? "Status: active on this Minecraft version."
			: "Status: not active yet. This note updates once you are in a world; if it stays like this, this Minecraft build draws the world differently and the effect is unavailable.");
	}

	private static int skyEdit = 0;
	private static final String[] SKY_PARTS = {"Sky Colour", "Horizon Colour", "Cloud Colour"};

	private static void skyRows(SettingsScreen s, Ui ui, Ui.Form f) {
		RayConfig c = RayConfig.get();
		f.section("Custom Sky");
		ui.toggle(f.half(), "Enabled", c.skyOn, () -> c.skyOn = !c.skyOn);
		ui.toggle(f.half(), "Follow Day Cycle", c.skyFollowDay, () -> c.skyFollowDay = !c.skyFollowDay);
		ui.slider(f.full(), "Strength", 0, 100, c.skyStrength, "%", v -> c.skyStrength = v);

		f.section("Pre-made Skies");
		final int cols = 4, tileH = 46, gap = 5;
		int tw = (f.width() - gap * (cols - 1)) / cols;
		int count = Sky.PRESETS.length + 1; // the last tile is "Custom"
		int rowsN = (count + cols - 1) / cols;
		int top = f.reserve(rowsN * (tileH + gap));
		int accent = Theme.accent();
		for (int i = 0; i < count; i++) {
			final int idx = i;
			int tx = f.x() + (i % cols) * (tw + gap);
			int ty = top + (i / cols) * (tileH + gap);
			boolean sel = c.skyPreset == i;
			float sv = ui.anim("skyT" + i, sel), hv = ui.anim("skyH" + i, ui.over(tx, ty, tw, tileH));
			int topC = 0xFF000000 | Sky.top(i), horC = 0xFF000000 | Sky.horizon(i);
			ui.glow(tx, ty, tw, tileH, 7, accent, Math.max(sv, hv * 0.5f));
			ui.rrectGrad(tx, ty, tw, tileH, 7, topC, horC);
			ui.rrect(tx + tw / 5, ty + tileH / 3, tw * 3 / 5, 5, 2, Theme.fade(0xFF000000 | Sky.cloud(i), 0.85f));
			ui.rrect(tx + tw / 3, ty + tileH / 3 + 7, tw / 3, 4, 2, Theme.fade(0xFF000000 | Sky.cloud(i), 0.55f));
			ui.ring(tx, ty, tw, tileH, 7, Theme.mix(0x33FFFFFF, accent, Math.max(sv, hv * 0.6f)));
			ui.textShadow(Sky.presetName(i), tx + 6, ty + tileH - 12, 0xFFFFFFFF);
			if (sel) ui.circle(tx + tw - 8, ty + 8, 3, accent);
			ui.click("skyP" + i, tx, ty, tw, tileH, () -> {
				c.skyPreset = idx;
				c.skyOn = true;
			});
		}
		if (c.skyPreset == Sky.CUSTOM) {
			f.section("Your Colours");
			ui.choice(f.full(), "Editing", SKY_PARTS[skyEdit], () -> skyEdit = (skyEdit + 1) % SKY_PARTS.length);
			int cur = skyEdit == 0 ? c.skyCustomTop : skyEdit == 1 ? c.skyCustomHorizon : c.skyCustomCloud;
			ui.colorPicker(f, "sky" + skyEdit, cur, v -> {
				if (skyEdit == 0) c.skyCustomTop = v;
				else if (skyEdit == 1) c.skyCustomHorizon = v;
				else c.skyCustomCloud = v;
			});
		}
		f.note("Recolours the overworld sky, the horizon fog and the clouds. Strength blends it with the normal sky. Follow Day Cycle makes your sky darken at night. It only changes how your own screen looks.");
	}

	private static final String[] LYRIC_MODES = {"Sentence", "Per-Word Crosshair"};
	private static final String[] BAR_STYLES = {"Glass Pill", "Minimal"};
	private static final String[] BAR_POS = {"Top", "Bottom"};
	private static final String[] WORD_STYLES = {"Glow", "Outline", "Clean"};
	private static final String[] WORD_ANIMS = {"Pop", "Rise", "Fade"};
	private static final String[] COLOR_MODES = {"Single Colour", "Rainbow", "Gradient"};

	private static void lyricsRows(SettingsScreen s, Ui ui, Ui.Form f) {
		RayConfig c = RayConfig.get();

		// live preview of the bar with your current settings
		f.section("Preview");
		int pvH = 84;
		int pvTop = f.reserve(pvH);
		ui.rrectGrad(f.x(), pvTop, f.width(), pvH, 8, 0xFF1B2540, 0xFF0B1224);
		ui.ring(f.x(), pvTop, f.width(), pvH, 8, Theme.STROKE);
		LyricsHud.drawPreview(ui.ctx, net.minecraft.client.MinecraftClient.getInstance().textRenderer, ui, f.x() + f.width() / 2, pvTop + 8, f.width());

		f.section("Lyric Sync");
		ui.toggle(f.half(), "Enabled", c.lyricsOn, () -> c.lyricsOn = !c.lyricsOn);
		ui.toggle(f.half(), "Top Bar", c.lyricsBar, () -> c.lyricsBar = !c.lyricsBar);
		ui.toggle(f.half(), "Floating Words", c.lyricsWorld, () -> c.lyricsWorld = !c.lyricsWorld);
		ui.toggle(f.half(), "Hide When Paused", c.lyricsHidePaused, () -> c.lyricsHidePaused = !c.lyricsHidePaused);
		ui.toggle(f.half(), "Save Lyrics Offline", c.lyricsCache, () -> c.lyricsCache = !c.lyricsCache);

		f.section("Bar");
		ui.choice(f.half(), "Style", BAR_STYLES[c.lyricsBarStyle], () -> c.lyricsBarStyle = (c.lyricsBarStyle + 1) % BAR_STYLES.length);
		ui.choice(f.half(), "Position", BAR_POS[c.lyricsBarPos], () -> c.lyricsBarPos = (c.lyricsBarPos + 1) % BAR_POS.length);
		ui.slider(f.half(), "Size", 60, 180, c.lyricsBarScale, "%", v -> c.lyricsBarScale = v);
		ui.slider(f.half(), "Edge Distance", 0, 120, c.lyricsY, " px", v -> c.lyricsY = v);
		ui.toggle(f.half(), "Progress", c.lyricsProgress, () -> c.lyricsProgress = !c.lyricsProgress);
		ui.toggle(f.half(), "Next Line", c.lyricsNext, () -> c.lyricsNext = !c.lyricsNext);
		ui.toggle(f.half(), "Song Name", c.lyricsShowTitle, () -> c.lyricsShowTitle = !c.lyricsShowTitle);

		f.section("Floating Words");
		ui.choice(f.full(), "Layout", LYRIC_MODES[c.lyricsMode], () -> c.lyricsMode = (c.lyricsMode + 1) % LYRIC_MODES.length);
		ui.choice(f.half(), "Style", WORD_STYLES[c.lyricsWordStyle], () -> c.lyricsWordStyle = (c.lyricsWordStyle + 1) % WORD_STYLES.length);
		ui.choice(f.half(), "Animation", WORD_ANIMS[c.lyricsAnim], () -> c.lyricsAnim = (c.lyricsAnim + 1) % WORD_ANIMS.length);
		ui.choice(f.half(), "Colours", COLOR_MODES[c.lyricsColorMode], () -> c.lyricsColorMode = (c.lyricsColorMode + 1) % COLOR_MODES.length);
		ui.colorChoice(f.half(), "Word Colour", Theme.PALETTE_NAMES[c.lyricsColor], Theme.pick(c.lyricsColor, Theme.accent()),
			() -> c.lyricsColor = (c.lyricsColor + 1) % Theme.PALETTE_NAMES.length);
		ui.slider(f.half(), "Clear After", 2, 15, c.lyricsClear, " s", v -> c.lyricsClear = v);
		ui.slider(f.half(), "Word Size", 50, 250, c.lyricsSize, "%", v -> c.lyricsSize = v);
		ui.slider(f.full(), "Distance", 2, 16, c.lyricsDistance, " blocks", v -> c.lyricsDistance = v);
		f.note("Sentence builds each lyric line around the spot you were looking at and stacks the next lines under it. Per-Word Crosshair drops every word where your crosshair is at that moment; words that land on the same spot line up like a sentence. Words stay where they appeared and fade after the Clear After time.");

		f.section("Sync");
		ui.slider(f.full(), "Global Offset", -5000, 5000, c.lyricsOffset, " ms", v -> c.lyricsOffset = v);
		int so = Lyrics.songOffset();
		f.note("This song: " + (so >= 0 ? "+" : "") + so + " ms. Use the Lyrics Earlier / Later keys (Controls) while a song plays and the fix is remembered for that song.");
		ui.button(f.half(), "Earlier 250 ms", () -> Lyrics.nudge(-250));
		ui.button(f.half(), "Later 250 ms", () -> Lyrics.nudge(250));
		ui.button(f.half(), "Reset This Song", () -> {
			Lyrics.resetSongOffset();
			Toasts.push("Song sync reset");
		});
		ui.button(f.half(), "Song just started", Lyrics::resync);

		f.section("Status");
		boolean found = Lyrics.spotifyFound && !Lyrics.title.isEmpty();
		if (!c.lyricsOn) f.note("Turn Enabled on, open the Spotify desktop app and play a song.");
		else if (!found) f.note("Spotify not detected. Open the Spotify desktop app and press play. " + Lyrics.platformNote);
		else {
			String state = switch (Lyrics.status) {
				case LOADING -> "finding lyrics...";
				case READY -> (Lyrics.synced ? "synced, " : "not synced (spread evenly), ") + Lyrics.lines.size() + " lines";
				case MISSING -> "none found for this song";
				case ERROR -> "service unreachable, retrying";
				case AD -> "advertisement";
				default -> "waiting";
			};
			f.note("Now playing: " + Lyrics.artist + " - " + Lyrics.title + " (lyrics: " + state + ")");
		}
		f.note(Lyrics.platformNote.isEmpty() ? "Lyrics come from LRCLIB." : Lyrics.platformNote + ". Lyrics come from LRCLIB.");
	}

	private static void fontRows(Ui ui, Ui.Form f, boolean client) {
		RayConfig c = RayConfig.get();
		boolean on = client ? c.clientFontOn : c.minecraftFontOn;
		int selected = client ? c.clientFont : c.minecraftFont;
		f.section(client ? "Client Font" : "Minecraft Font");
		ui.toggle(f.full(), "Enabled", on, () -> {
			if (client) c.clientFontOn = !c.clientFontOn;
			else c.minecraftFontOn = !c.minecraftFontOn;
		});
		f.note(client
			? "Client Font is the font of the client's own text: all the Ray menus and your HUD modules (FPS, coordinates...). Minecraft's text is not affected. Leave it on Minecraft default to keep the client looking as it does now."
			: "Minecraft Font is the font of Minecraft itself: chat, vanilla menus, tooltips, signs, everything except the client's own text, which has its own Client Font setting.");
		if (!FontOverride.supported()) {
			f.note("The font hook could not attach on this Minecraft version, so fonts will not change. Tell the developer.");
		}
		f.section("Pick a font");
		for (int i = client ? 0 : 1; i < FontOverride.NAMES.length; i++) { // Minecraft's own font is not an option for itself
			final int idx = i;
			String label = FontOverride.NAMES[i] + (FontOverride.supported() && !FontOverride.loaded(i) ? " (not loaded)" : "");
			ui.toggle(f.half(), label, selected == i, () -> {
				if (client) c.clientFont = idx;
				else c.minecraftFont = idx;
			});
		}

		f.section("Your own font");
		java.util.List<String> files = FontOverride.customFiles();
		int at = files.indexOf(c.customFontFile);
		ui.choice(f.full(), "Font file", files.isEmpty() ? "No files yet" : (at < 0 ? "Pick a file" : c.customFontFile), () -> {
			if (!files.isEmpty()) c.customFontFile = files.get((Math.max(at, -1) + 1) % files.size());
		});
		ui.button(f.half(), "Prepare Font Pack", () -> FontOverride.preparePack(c.customFontFile));
		ui.button(f.half(), "Open Fonts Folder", FontOverride::openFolder);
		f.note("1) Put a .ttf or .otf file in: " + FontOverride.fontsFolder() + "   2) Pick it above and click Prepare Font Pack.   3) In Options > Resource Packs, enable \"Ray Fonts\".   4) Choose My own font here.");
		if (!FontOverride.message.isEmpty()) f.note(FontOverride.message);
	}

	private static final String[] TARGETS = {"Players", "Living", "Any"};

	private static void crosshairRows(SettingsScreen s, Ui ui, Ui.Form f) {
		RayConfig c = RayConfig.get();
		f.section("Crosshair");
		ui.toggle(f.half(), "Custom Crosshair", c.customCrosshair, () -> c.customCrosshair = !c.customCrosshair);
		ui.toggle(f.half(), "Use Drawn Crosshair", c.crosshairDrawn, () -> c.crosshairDrawn = !c.crosshairDrawn);
		ui.toggle(f.half(), "Vanilla Blending", c.crosshairVanillaBlend, () -> c.crosshairVanillaBlend = !c.crosshairVanillaBlend);
		ui.toggle(f.half(), "Outline", c.crosshairOutline, () -> c.crosshairOutline = !c.crosshairOutline);
		ui.colorChoice(f.half(), "Color", CrosshairArt.COLOR_NAMES[c.crosshairColor], CrosshairArt.color(c.crosshairColor),
			() -> c.crosshairColor = (c.crosshairColor + 1) % CrosshairArt.colorCount());
		ui.slider(f.half(), "Pixel Scale", 1, 4, c.crosshairScale, "x", v -> c.crosshairScale = v);
		s.numberRow(f.half(), "Overall Size (type)", "ch_size", c.crosshairZoom, 0.25f, 8.0f, v -> c.crosshairZoom = v);
		f.note("Overall Size multiplies the whole crosshair: 1.0 is normal, 1.33 is a third bigger, 0.5 is half. Click the box and type any number from 0.25 to 8.");
		f.section("Classic crosshair");
		ui.slider(f.half(), "Line Length", 2, 20, c.crosshairSize, v -> c.crosshairSize = v);
		ui.slider(f.half(), "Gap", 0, 10, c.crosshairGap, v -> c.crosshairGap = v);
		ui.slider(f.half(), "Thickness", 1, 3, c.crosshairThickness, v -> c.crosshairThickness = v);
		ui.toggle(f.half(), "Center Dot", c.crosshairDot, () -> c.crosshairDot = !c.crosshairDot);
		f.section("Enemy crosshair");
		ui.toggle(f.half(), "Enemy Crosshair", c.enemyCrosshair, () -> c.enemyCrosshair = !c.enemyCrosshair);
		ui.choice(f.half(), "Enemy Target", TARGETS[c.enemyTarget], () -> c.enemyTarget = (c.enemyTarget + 1) % TARGETS.length);
		f.section("Draw your own");
		ui.button(f.full(), "Open Crosshair Editor", () -> s.open(new CrosshairEditorScreen(s)));
		f.note("Vanilla Blending draws with the same colour-inverting blend as the normal crosshair, so it stays visible on any background (your colour on dark areas, its opposite on bright ones). The outline is skipped while it is on.");
	}

	private static void outlineRows(SettingsScreen s, Ui ui, Ui.Form f) {
		RayConfig c = RayConfig.get();
		f.section("Block Outline");
		ui.toggle(f.half(), "Block Outline", c.blockOutline, () -> c.blockOutline = !c.blockOutline);
		ui.choice(f.half(), "Outline", c.outlineMode == 0 ? "Looked-at face" : "Whole block", () -> c.outlineMode = 1 - c.outlineMode);
		ui.toggle(f.half(), "Stabilize View", c.outlineStabilize, () -> c.outlineStabilize = !c.outlineStabilize);
		ui.colorChoice(f.half(), "Color", c.outlineColor == 0 ? "Accent" : Theme.PALETTE_NAMES[c.outlineColor],
			Theme.pick(c.outlineColor, Theme.accent()), () -> c.outlineColor = (c.outlineColor + 1) % Theme.PALETTE_NAMES.length);
		ui.toggle(f.half(), "Rainbow", c.outlineRainbow, () -> c.outlineRainbow = !c.outlineRainbow);
		ui.toggle(f.half(), "Glow", c.outlineGlow, () -> c.outlineGlow = !c.outlineGlow);
		ui.slider(f.half(), "Thickness", 1, 4, c.outlineThickness, v -> c.outlineThickness = v);
		ui.slider(f.half(), "Opacity", 20, 100, c.outlineOpacity, "%", v -> c.outlineOpacity = v);
		f.note("Looked-at face outlines only the side of the block your crosshair is on (look at the top and you get the top). Whole block outlines every visible edge of the block's real shape, stairs, slabs and fences included.");
		f.note("Stabilize View turns off view bobbing and FOV effects while the outline is on, so the lines sit perfectly on the block. Your own settings are restored when you turn the outline off.");
	}
}
