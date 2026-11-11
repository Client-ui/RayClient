package dev.ray;

import net.minecraft.client.gui.DrawContext;

/**
 * Ray Client branding: the orbiting-R logo (see {@link Logo}), the animated dark backdrop used by the title screen,
 * menus and loading screen, and the loading screen itself.
 *
 * Everything here is drawn with plain rectangles (no fonts or textures) on purpose: the loading screen is shown
 * while Minecraft is still loading its fonts and textures, so it must not depend on either.
 */
public final class Brand {
	private Brand() {}

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	// ---------- logo ----------

	/** The logo h pixels tall, centred on cx. alpha is 0..1. (timeMs is unused now: the logo stays still, no pulsing glow.) */
	public static void logo(DrawContext ctx, int cx, int top, int h, float alpha, long timeMs) {
		Logo.draw(ctx, cx - Logo.width(h) / 2, top, h, alpha);
	}

	// ---------- backdrop ----------

	/**
	 * A soft round light: stacked translucent discs, drawn as horizontal strips (cheap, no per-pixel work).
	 * The strip height grows with the radius so a big light costs about 350 fills, not thousands.
	 */
	public static void orb(DrawContext ctx, int cx, int cy, int radius, int color, float alpha) {
		final int layers = 5;
		for (int k = 1; k <= layers; k++) {
			int r = radius * k / layers;
			if (r < 2) continue;
			int col = Theme.fade(color, alpha / layers);
			int step = Math.max(2, r / 9);
			for (int dy = -r; dy < r; dy += step) {
				double mid = Math.min(r, Math.abs(dy + step / 2.0));
				int hw = (int) Math.sqrt((double) r * r - mid * mid);
				if (hw <= 0) continue;
				ctx.fill(cx - hw, cy + dy, cx + hw, Math.min(cy + r, cy + dy + step), col);
			}
		}
	}

	/** A calm near-black backdrop with a few fixed stars and a vignette. */
	public static void backdrop(DrawContext ctx, int w, int h, long timeMs, float alpha) {
		backdrop(ctx, w, h, timeMs, alpha, 0f, 0f);
	}

	/** Same, with the stars shifting a little by the cursor (px, py in -1..1); nearer stars move more. */
	public static void backdrop(DrawContext ctx, int w, int h, long timeMs, float alpha, float parX, float parY) {
		ctx.fillGradient(0, 0, w, h, Theme.fade(0xFF050507, alpha), Theme.fade(0xFF0C0C11, alpha));
		float t = timeMs / 1000f;
		for (int i = 0; i < 56; i++) {
			int layer = i % 3;
			float sx = frac(i * 0.6180339f + 0.13f), sy = frac(i * 0.7548777f + 0.29f);
			int px = Math.round(sx * w - parX * (2 + 4 * layer)), py = Math.round(sy * h - parY * (1 + 3 * layer));
			float tw = 0.85f + 0.15f * (float) Math.sin(t * 0.5f + i * 2.3f);
			float a = (0.05f + 0.07f * layer) * tw * alpha;
			ctx.fill(px, py, px + 1, py + 1, Theme.fade(0xFFFFFFFF, a));
		}
		ctx.fillGradient(0, 0, w, h / 5, Theme.fade(0x99000000, alpha), 0x00000000);
		ctx.fillGradient(0, h - h / 4, w, h, 0x00000000, Theme.fade(0xB0000000, alpha));
	}

	// ---------- loading screen ----------

	/** Drawn on top of Minecraft's own loading overlay (see SplashOverlayMixin). alpha fades the whole thing out. */
	public static void drawLoading(DrawContext ctx, float alpha, long timeMs) {
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		ctx.fill(0, 0, w, h, Theme.fade(Theme.BG_DEEP, alpha));
		backdrop(ctx, w, h, timeMs, alpha);

		int lh = Math.max(36, Math.min(96, h / 5));
		int barW = Math.min(140, w / 2), barH = 3;
		int total = lh + 22 + barH;
		int y = (h - total) / 2;
		int cx = w / 2;
		logo(ctx, cx, y, lh, alpha, timeMs);
		y += lh + 22;

		// slim indeterminate bar with a gradient head
		int bx = cx - barW / 2;
		ctx.fill(bx, y, bx + barW, y + barH, Theme.fade(0xFFFFFFFF, 0.08f * alpha));
		float sweep = (timeMs % 1500L) / 1500f;
		int segW = barW / 3;
		int sx = bx - segW + (int) (sweep * (barW + segW));
		int x1 = Math.max(bx, sx), x2 = Math.min(bx + barW, sx + segW);
		if (x2 > x1) {
			int n = 6;
			for (int i = 0; i < n; i++) {
				int a = x1 + (x2 - x1) * i / n, b = x1 + (x2 - x1) * (i + 1) / n;
				float edge = (float) Math.sin(Math.PI * (i + 0.5f) / n);
				ctx.fill(a, y, b, y + barH, Theme.fade(Theme.accent(), alpha * (0.35f + 0.65f * edge)));
			}
		}
	}
}
