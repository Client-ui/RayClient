package dev.ray;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class RayClient implements ClientModInitializer {
	public static final String MOD_ID = "ray";
	public static final String VERSION = "1.13.1";

	private static KeyBinding menuKey;
	private static KeyBinding zoomKey;
	private static KeyBinding waypointKey;
	private static KeyBinding copyKey;
	private static KeyBinding swKey;
	private static KeyBinding swResetKey;
	private static KeyBinding lyricsKey, lyricsEarlierKey, lyricsLaterKey, lyricsToggleKey;
	private static long lastDurabilityAlert = 0;
	private static int alertTick = 0;
	private static boolean zooming = false;
	private static int savedFov = 70;
	private static boolean sprintForced = false;
	private static boolean sneakLock = false;
	private static boolean sneakForced = false;
	private static boolean nvForced = false;
	private static boolean wasDead = false;
	/** Deaths since the game started (shown by the Death Counter module). */
	public static int sessionDeaths = 0;
	private static int respawnTicks = 0;
	private static boolean gammaForced = false;
	private static double savedGamma = 0.5;
	private static boolean stabilizeForced = false;
	private static boolean savedBob = true;
	private static double savedFovEffects = 1.0;

	@Override
	public void onInitializeClient() {
		VersionGuard.requireSupported();
		RayConfig.load();
		CombatTracker.init();

		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
		menuKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.ray.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		zoomKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.ray.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
		waypointKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.ray.waypoint", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, category));

		copyKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.ray.copycoords", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		swKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.ray.stopwatch", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		swResetKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.ray.stopwatchreset", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		lyricsKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.ray.lyricsresync", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		lyricsEarlierKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.ray.lyricsearlier", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		lyricsLaterKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.ray.lyricslater", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		lyricsToggleKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.ray.lyricstoggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "hud"), (ctx, tc) -> Safe.render("HUD", Hud::render, ctx, tc));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "lyrics"), (ctx, tc) -> Safe.render("Lyric Sync", LyricsHud::render, ctx, tc));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "outline"), (ctx, tc) -> Safe.render("Block Outline", BlockOutline::render, ctx, tc));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "waypoints"), (ctx, tc) -> Safe.render("Waypoints", WaypointMarkers::render, ctx, tc));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "hitboxes"), (ctx, tc) -> Safe.render("Hitboxes", Hitboxes::render, ctx, tc));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "toasts"), (ctx, tc) -> Safe.render("Notifications", Toasts::render, ctx, tc));
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (ctx, tickCounter) -> {
			RayConfig cfg = RayConfig.get();
			MinecraftClient mc = MinecraftClient.getInstance();
			if (cfg.customCrosshair && mc.player != null && mc.options.getPerspective().isFirstPerson()) {
				Hud.drawCrosshair(ctx, mc);
			} else {
				original.render(ctx, tickCounter);
			}
		});
		try {
			HudElementRegistry.replaceElement(Identifier.of("minecraft", "scoreboard"), original -> (ctx, tickCounter) -> {
				RayConfig cfg = RayConfig.get();
				if (cfg.hideScoreboard) return;
				int sw = ctx.getScaledWindowWidth();
				int sh = ctx.getScaledWindowHeight();
				int[] off = Modules.scoreboard.offset(sw, sh);
				boolean custom = Modules.scoreboard.enabled() && (off[0] != 0 || off[1] != 0 || cfg.scoreboardScale != 100);
				if (!custom) {
					original.render(ctx, tickCounter);
					return;
				}
				float s = cfg.scoreboardScale / 100.0f;
				ctx.getMatrices().pushMatrix();
				ctx.getMatrices().translate(sw + off[0], sh / 2.0f + off[1]);
				ctx.getMatrices().scale(s, s);
				ctx.getMatrices().translate(-sw, -sh / 2.0f);
				original.render(ctx, tickCounter);
				ctx.getMatrices().popMatrix();
			});
		} catch (Throwable t) {
			System.err.println("[Ray Client] Could not hook the scoreboard: " + t);
		}
		ClientTickEvents.END_CLIENT_TICK.register(RayClient::onTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(RayClient::stopZoom);
	}

	public static void addWaypoint(MinecraftClient mc) {
		if (mc == null || mc.player == null || mc.world == null) return;
		RayConfig cfg = RayConfig.get();
		RayConfig.Waypoint w = new RayConfig.Waypoint();
		int n = cfg.waypoints.size() + 1;
		while (nameTaken(cfg, "Waypoint " + n)) n++;
		w.name = "Waypoint " + n;
		w.x = mc.player.getX();
		w.y = mc.player.getY();
		w.z = mc.player.getZ();
		w.dim = mc.world.getRegistryKey().getValue().toString();
		cfg.waypoints.add(w);
		RayConfig.save();
		mc.player.sendMessage(Text.literal("Saved " + w.name), true);
	}

	private static void onDeath(MinecraftClient mc) {
		RayConfig cfg = RayConfig.get();
		sessionDeaths++;
		RayConfig.Waypoint d = new RayConfig.Waypoint();
		d.name = "Death";
		d.x = mc.player.getX();
		d.y = mc.player.getY();
		d.z = mc.player.getZ();
		d.dim = mc.world.getRegistryKey().getValue().toString();
		cfg.lastDeath = d;
		if (cfg.deathWaypoint) {
			cfg.waypoints.removeIf(w -> "Death".equals(w.name));
			RayConfig.Waypoint copy = new RayConfig.Waypoint();
			copy.name = d.name;
			copy.x = d.x;
			copy.y = d.y;
			copy.z = d.z;
			copy.dim = d.dim;
			cfg.waypoints.add(copy);
			Toasts.push("Death point saved");
		}
		RayConfig.save();
	}

	private static boolean nameTaken(RayConfig cfg, String name) {
		for (RayConfig.Waypoint w : cfg.waypoints) if (name.equals(w.name)) return true;
		return false;
	}

	private static void onTick(MinecraftClient mc) {
		CpsTracker.poll(mc);
		Modules.tick(mc);
		boolean dead = mc.player != null && mc.world != null && mc.player.getHealth() <= 0.0f;
		if (dead && !wasDead) onDeath(mc);
		wasDead = dead;
		HitColor.tick();
		FontOverride.update();
		CombatTracker.tick(mc);
		ModuleKeys.tick(mc);
		Lyrics.tick();
		Updater.tick();
		while (lyricsKey.wasPressed()) Lyrics.resync();
		while (lyricsEarlierKey.wasPressed()) Lyrics.nudge(-250);
		while (lyricsLaterKey.wasPressed()) Lyrics.nudge(250);
		while (lyricsToggleKey.wasPressed()) {
			RayConfig lc = RayConfig.get();
			lc.lyricsOn = !lc.lyricsOn;
			RayConfig.save();
			Toasts.push("Lyric Sync " + (lc.lyricsOn ? "on" : "off"));
		}
		// first launch: tell the player how to open the menu
		if (!RayConfig.get().welcomed && mc.player != null && mc.world != null) {
			RayConfig.get().welcomed = true;
			RayConfig.save();
			Toasts.push("Welcome to Ray Client - press Right Shift for the menu");
		}
		while (swKey.wasPressed()) Modules.stopwatchToggle();
		while (swResetKey.wasPressed()) Modules.stopwatchReset();

		while (menuKey.wasPressed()) {
			if (mc.player != null && mc.currentScreen == null) mc.setScreen(new RayScreen(null));
		}
		while (waypointKey.wasPressed()) {
			if (mc.currentScreen == null) addWaypoint(mc);
		}

		while (copyKey.wasPressed()) {
			if (mc.player != null) {
				String coords = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
				mc.keyboard.setClipboard(coords);
				mc.player.sendMessage(Text.literal("Copied " + coords), true);
			}
		}

		// Auto respawn
		if (RayConfig.get().autoRespawn && mc.player != null && mc.currentScreen instanceof DeathScreen) {
			if (++respawnTicks >= RayConfig.get().respawnDelay) mc.player.requestRespawn();
		} else {
			respawnTicks = 0;
		}

		// Durability alert (checked once a second)
		if (RayConfig.get().durabilityAlert && mc.player != null && ++alertTick >= 20) {
			alertTick = 0;
			EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
			for (EquipmentSlot es : slots) {
				ItemStack st = mc.player.getEquippedStack(es);
				if (st.isEmpty() || !st.isDamageable() || st.getMaxDamage() <= 0) continue;
				int left = st.getMaxDamage() - st.getDamage();
				RayConfig dc = RayConfig.get();
				if (left * 100L <= (long) st.getMaxDamage() * dc.durabilityPercent && System.currentTimeMillis() - lastDurabilityAlert > dc.durabilitySeconds * 1000L) {
					lastDurabilityAlert = System.currentTimeMillis();
					mc.player.sendMessage(Text.literal("Low durability: " + st.getName().getString()), true);
					break;
				}
			}
		}

		// Zoom (optionally smooth)
		RayConfig zc = RayConfig.get();
		boolean wantZoom = zc.zoomEnabled && zoomKey.isPressed() && mc.currentScreen == null && mc.player != null;
		ZoomState.target = wantZoom ? 1f : 0f;
		if (!ZoomState.hooked) {
			// fallback if the render hook could not attach: the old tick-based zoom
			if (wantZoom && !zooming) {
				savedFov = mc.options.getFov().getValue();
				zooming = true;
			}
			if (zooming) {
				int target = wantZoom ? (int) Math.max(5, savedFov * 10.0 / Math.max(10, zc.zoomLevel)) : savedFov;
				int cur = mc.options.getFov().getValue();
				int next = zc.smoothZoom ? cur + Math.max(-8, Math.min(8, target - cur)) : target;
				mc.options.getFov().setValue(next);
				if (!wantZoom && next == savedFov) zooming = false;
			}
		}

		// Toggle sneak
		RayConfig c2 = RayConfig.get();
		while (mc.options.sneakKey.wasPressed()) {
			if (c2.toggleSneak && mc.currentScreen == null) sneakLock = !sneakLock;
		}
		if (c2.toggleSneak && mc.player != null && mc.currentScreen == null) {
			mc.options.sneakKey.setPressed(sneakLock);
			sneakForced = true;
		} else if (sneakForced) {
			mc.options.sneakKey.setPressed(false);
			sneakForced = false;
			sneakLock = false;
		}

		// Fullbright: brightness (gamma) pushed far past the vanilla maximum so nothing is dark. The normal setValue()
		// refuses anything above 100%, so Gamma writes the value directly. If that is impossible, or the mode is
		// "Gamma + Night Vision", a client-side night vision effect is added as well.
		if (c2.fullbright && mc.player != null) {
			var gamma = mc.options.getGamma();
			double target = c2.fullbrightLevel;
			if (!gammaForced) {
				savedGamma = gamma.getValue();
				if (savedGamma > 1.0) savedGamma = 0.5;
			}
			boolean gammaOk = gamma.getValue() == target || Gamma.set(gamma, target);
			if (gammaOk) gammaForced = true;
			boolean wantNv = !gammaOk || c2.fullbrightMode == 1;
			if (wantNv) {
				if (!mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
					mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false));
					nvForced = true;
				}
			} else if (nvForced) {
				mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
				nvForced = false;
			}
		} else {
			restoreFullbright(mc);
		}

		// Block outline alignment: no view bobbing / FOV effects while the outline is on (restored afterwards)
		boolean stabilize = c2.blockOutline && c2.outlineStabilize && mc.player != null;
		if (stabilize) {
			if (!stabilizeForced) {
				savedBob = mc.options.getBobView().getValue();
				savedFovEffects = mc.options.getFovEffectScale().getValue();
				stabilizeForced = true;
			}
			mc.options.getBobView().setValue(false);
			mc.options.getFovEffectScale().setValue(0.0);
		} else if (stabilizeForced) {
			restoreStabilize(mc);
		}

		// Toggle sprint
		RayConfig cfg = RayConfig.get();
		boolean sprint = cfg.toggleSprint && mc.player != null && mc.currentScreen == null;
		if (sprint && mc.options.forwardKey.isPressed()) {
			mc.options.sprintKey.setPressed(true);
			sprintForced = true;
		} else if (sprintForced) {
			mc.options.sprintKey.setPressed(false);
			sprintForced = false;
		}
	}

	public static boolean fullbrightActive() {
		return nvForced;
	}

	private static void restoreFullbright(MinecraftClient mc) {
		if (gammaForced) {
			Gamma.set(mc.options.getGamma(), savedGamma);
			gammaForced = false;
		}
		if (nvForced) {
			if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
			nvForced = false;
		}
	}

	private static void restoreStabilize(MinecraftClient mc) {
		if (!stabilizeForced) return;
		mc.options.getBobView().setValue(savedBob);
		mc.options.getFovEffectScale().setValue(savedFovEffects);
		stabilizeForced = false;
	}

	private static void stopZoom(MinecraftClient mc) {
		if (zooming) {
			mc.options.getFov().setValue(savedFov);
			zooming = false;
		}
		restoreStabilize(mc);
		restoreFullbright(mc);
	}
}
