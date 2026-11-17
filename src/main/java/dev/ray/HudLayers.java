package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The version-independent half of hooking into Minecraft's HUD. How the pieces get called differs per Minecraft
 * version (see the HudCompat class in src/compat/hud_*), but what they do is the same everywhere.
 */
public final class HudLayers {
	private HudLayers() {}

	/** True while the Ray crosshair replaces the vanilla one. */
	public static boolean customCrosshair() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return RayConfig.get().customCrosshair && mc.player != null && mc.options.getPerspective().isFirstPerson();
	}

	public static boolean scoreboardHidden() {
		return RayConfig.get().hideScoreboard;
	}

	/** Moves/scales the vanilla scoreboard when the Scoreboard module asks for it. Returns true if the matrix was pushed (call scoreboardEnd afterwards). */
	public static boolean scoreboardBegin(DrawContext ctx) {
		RayConfig cfg = RayConfig.get();
		int sw = ctx.getScaledWindowWidth();
		int sh = ctx.getScaledWindowHeight();
		int[] off = Modules.scoreboard.offset(sw, sh);
		boolean custom = Modules.scoreboard.enabled() && (off[0] != 0 || off[1] != 0 || cfg.scoreboardScale != 100);
		if (!custom) return false;
		float s = cfg.scoreboardScale / 100.0f;
		Gfx.push(ctx);
		Gfx.translate(ctx, sw + off[0], sh / 2.0f + off[1]);
		Gfx.scale(ctx, s, s);
		Gfx.translate(ctx, -sw, -sh / 2.0f);
		return true;
	}

	public static void scoreboardEnd(DrawContext ctx, boolean pushed) {
		if (pushed) Gfx.pop(ctx);
	}

	/** Draws the Ray crosshair when it is on (the vanilla one is switched off separately). */
	public static void crosshair(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options.hudHidden || !customCrosshair()) return;
		Safe.run("Crosshair", () -> Hud.drawCrosshair(ctx, mc));
	}

	/** The extra HUD parts, in drawing order. Used by the versions that only have one HUD callback. */
	public static void renderAll(DrawContext ctx, Tick tick) {
		crosshair(ctx);
		Safe.render("HUD", Hud::render, ctx, tick);
		Safe.render("Lyric Sync", LyricsHud::render, ctx, tick);
		Safe.render("Block Outline", BlockOutline::render, ctx, tick);
		Safe.render("Waypoints", WaypointMarkers::render, ctx, tick);
		Safe.render("Hitboxes", Hitboxes::render, ctx, tick);
		Safe.render("Notifications", Toasts::render, ctx, tick);
	}
}
