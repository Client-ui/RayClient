package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;

public final class Hud {
	private Hud() {}

	public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		ClientFont.scale = 1f; // the HUD is drawn in plain GUI units
		MinecraftClient mc = MinecraftClient.getInstance();
		RayConfig cfg = RayConfig.get();
		if (mc.player != null) Nametags.frame(mc.player.getUuid());
		if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
		// the HUD editor draws the modules itself
		if (mc.currentScreen instanceof HudEditorScreen) return;

		TextRenderer tr = mc.textRenderer;
		int sw = ctx.getScaledWindowWidth();
		int sh = ctx.getScaledWindowHeight();
		if (cfg.lowHealthWarning && mc.player.getHealth() <= cfg.lowHealthHearts * 2.0f && mc.player.isAlive()) {
			float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0);
			int a = (int) (40 + 70 * pulse);
			int c = (a << 24) | (cfg.lowHealthColor & 0xFFFFFF);
			int t = cfg.lowHealthThickness;
			ctx.fill(0, 0, sw, t, c);
			ctx.fill(0, sh - t, sw, sh, c);
			ctx.fill(0, t, t, sh - t, c);
			ctx.fill(sw - t, t, sw, sh - t, c);
		}
		if (cfg.hitMarker && CombatTracker.lastHitMs() > 0) {
			long age = System.currentTimeMillis() - CombatTracker.lastHitMs();
			if (age < cfg.hitMarkerTime) {
				int alpha = (int) (255 * (1.0 - age / (double) cfg.hitMarkerTime));
				int col = (alpha << 24) | (cfg.hitMarkerColor & 0xFFFFFF);
				int mx = sw / 2, my = sh / 2;
				for (int i = 4; i <= cfg.hitMarkerSize; i++) {
					ctx.fill(mx + i, my + i, mx + i + 1, my + i + 1, col);
					ctx.fill(mx - i - 1, my + i, mx - i, my + i + 1, col);
					ctx.fill(mx + i, my - i - 1, mx + i + 1, my - i, col);
					ctx.fill(mx - i - 1, my - i - 1, mx - i, my - i, col);
				}
			}
		}
		// everything above works on its own; only the module HUD below needs the Client HUD switch
		if (!cfg.enabled) return;
		CpsTracker.poll(mc);
		for (HudModule m : Modules.ALL) {
			if (!m.enabled() || !m.visibleNow(mc)) continue;
			String key = m.safeKey();
			if (Safe.muted(key)) continue;
			try {
				if (!m.prepare(mc, tr, false)) continue;
				m.place(sw, sh);
				m.render(ctx, mc, tr);
			} catch (RuntimeException e) {
				Safe.failed(key, e);
			}
		}
	}

	private static boolean targetsEnemy(MinecraftClient mc, RayConfig cfg) {
		if (mc.crosshairTarget instanceof EntityHitResult ehr) {
			Entity e = ehr.getEntity();
			return switch (cfg.enemyTarget) {
				case 0 -> e instanceof PlayerEntity;
				case 1 -> e instanceof LivingEntity;
				default -> true;
			};
		}
		return false;
	}

	/** Custom crosshair: flat colours or (Vanilla Blending) vanilla's colour-inverting blend, optional outline, enemy variant. */
	public static void drawCrosshair(DrawContext ctx, MinecraftClient mc) {
		float z = RayConfig.get().crosshairZoom;
		if (Math.abs(z - 1.0f) < 0.001f) {
			drawCrosshairInner(ctx, mc);
			return;
		}
		float cx = ctx.getScaledWindowWidth() / 2.0f, cy = ctx.getScaledWindowHeight() / 2.0f;
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(cx, cy);
		ctx.getMatrices().scale(z, z);
		ctx.getMatrices().translate(-cx, -cy);
		drawCrosshairInner(ctx, mc);
		ctx.getMatrices().popMatrix();
	}

	private static void drawCrosshairInner(DrawContext ctx, MinecraftClient mc) {
		RayConfig cfg = RayConfig.get();
		int cx = ctx.getScaledWindowWidth() / 2;
		int cy = ctx.getScaledWindowHeight() / 2;
		boolean blend = cfg.crosshairVanillaBlend;

		if (cfg.enemyCrosshair && targetsEnemy(mc, cfg)) {
			CrosshairArt.draw(ctx, cfg.enemyPixels, cx, cy, cfg.crosshairScale, CrosshairArt.color(cfg.enemyColor, cfg.enemyCustom), cfg.crosshairOutline, blend);
			return;
		}
		int c = CrosshairArt.color(cfg.crosshairColor, cfg.crosshairCustom);
		if (cfg.crosshairDrawn) {
			CrosshairArt.draw(ctx, cfg.crosshairPixels, cx, cy, cfg.crosshairScale, c, cfg.crosshairOutline, blend);
			return;
		}
		int s = cfg.crosshairSize, g = cfg.crosshairGap, t = cfg.crosshairThickness;
		int x0 = cx - t / 2, y0 = cy - t / 2;
		if (cfg.crosshairOutline && !blend) {
			int o = 0xFF000000;
			ctx.fill(x0 - g - s - 1, y0 - 1, x0 - g + 1, y0 + t + 1, o);
			ctx.fill(x0 + t + g - 1, y0 - 1, x0 + t + g + s + 1, y0 + t + 1, o);
			ctx.fill(x0 - 1, y0 - g - s - 1, x0 + t + 1, y0 - g + 1, o);
			ctx.fill(x0 - 1, y0 + t + g - 1, x0 + t + 1, y0 + t + g + s + 1, o);
		}
		Blend.fill(ctx, x0 - g - s, y0, x0 - g, y0 + t, c, blend);
		Blend.fill(ctx, x0 + t + g, y0, x0 + t + g + s, y0 + t, c, blend);
		Blend.fill(ctx, x0, y0 - g - s, x0 + t, y0 - g, c, blend);
		Blend.fill(ctx, x0, y0 + t + g, x0 + t, y0 + t + g + s, c, blend);
		if (cfg.crosshairDot) Blend.fill(ctx, x0, y0, x0 + t, y0 + t, c, blend);
	}
}
