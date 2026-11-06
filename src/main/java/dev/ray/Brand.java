package dev.ray;

import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Ray Client branding: a pixel-art lightning bolt, a bitmap "RAY CLIENT" wordmark and the animated dark backdrop
 * used by the title screen and loading screen.
 *
 * Everything here is drawn with plain rectangles (no fonts or textures) on purpose: the loading screen is shown
 * while Minecraft is still loading its fonts and textures, so it must not depend on either.
 */
public final class Brand {
	private static final Map<Character, String[]> FONT = new HashMap<>();
	private static final String[] BOLT = {
		".........###..",
		"........###...",
		"........###...",
		".......###....",
		"......####....",
		"......####....",
		".....####.....",
		".....####.....",
		"....#########.",
		"...##########.",
		"...#########..",
		"..#########...",
		"......#####...",
		"......####....",
		"......###.....",
		"......###.....",
		".....###......",
		".....##.......",
		".....##.......",
		".....#........",
		"....#........."
	};

	static {
		put('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
		put('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
		put('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
		put('C', ".####", "#....", "#....", "#....", "#....", "#....", ".####");
		put('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
		put('I', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####");
		put('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
		put('N', "#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#");
		put('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
		put('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
		put('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
		put('G', ".####", "#....", "#....", "#.###", "#...#", "#...#", ".###.");
	}

	private Brand() {}

	private static void put(char c, String... rows) {
		FONT.put(c, rows);
	}

	// ---------- bitmap text ----------

	public static int wordWidth(String text, int scale) {
		int w = 0;
		for (int i = 0; i < text.length(); i++) w += (text.charAt(i) == ' ' ? 4 : 6) * scale;
		return Math.max(0, w - scale);
	}

	public static int wordHeight(int scale) {
		return 7 * scale;
	}

	public static void word(DrawContext ctx, String text, int x, int y, int scale, int color) {
		int cx = x;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == ' ') {
				cx += 4 * scale;
				continue;
			}
			String[] g = FONT.get(Character.toUpperCase(ch));
			if (g != null) {
				for (int r = 0; r < g.length; r++) {
					String row = g[r];
					for (int c = 0; c < row.length(); c++) {
						if (row.charAt(c) == '#') {
							ctx.fill(cx + c * scale, y + r * scale, cx + (c + 1) * scale, y + (r + 1) * scale, color);
						}
					}
				}
			}
			cx += 6 * scale;
		}
	}

	/** Wordmark with a soft coloured glow behind it. */
	public static void wordGlow(DrawContext ctx, String text, int x, int y, int scale, int color, int glow, float alpha) {
		int d = Math.max(1, scale / 2 + 1);
		int g = Theme.fade(glow, 0.16f * alpha);
		word(ctx, text, x - d, y, scale, g);
		word(ctx, text, x + d, y, scale, g);
		word(ctx, text, x, y - d, scale, g);
		word(ctx, text, x, y + d, scale, g);
		word(ctx, text, x, y, scale, Theme.fade(color, alpha));
	}

	/** Draws "RAY" in white and "CLIENT" in the accent colour, centred on cx. Returns the total width. */
	public static int wordmark(DrawContext ctx, int cx, int y, int scale, float alpha) {
		int total = wordWidth("RAY CLIENT", scale);
		int x = cx - total / 2;
		int accent = Theme.accent();
		wordGlow(ctx, "RAY", x, y, scale, 0xFFFFFFFF, accent, alpha);
		int cxStart = x + wordWidth("RAY ", scale) + scale;
		wordGlow(ctx, "CLIENT", cxStart, y, scale, accent, accent, alpha);
		return total;
	}

	// ---------- bolt ----------

	public static int boltWidth(int scale) {
		return BOLT[0].length() * scale;
	}

	public static int boltHeight(int scale) {
		return BOLT.length * scale;
	}

	/** Pixel-art lightning bolt with a pulsing glow. cx = horizontal centre, top = top edge. */
	public static void bolt(DrawContext ctx, int cx, int top, int scale, float alpha, long timeMs) {
		int x0 = cx - boltWidth(scale) / 2;
		int accent = Theme.accent();
		float pulse = 0.5f + 0.5f * (float) Math.sin(timeMs / 450.0);
		int d = scale + (int) (pulse * scale);
		int glow = Theme.fade(accent, (0.10f + 0.07f * pulse) * alpha);
		for (int[] o : new int[][]{{-d, 0}, {d, 0}, {0, -d}, {0, d}, {-d, -d}, {d, d}, {-d, d}, {d, -d}}) {
			drawBolt(ctx, x0 + o[0], top + o[1], scale, glow, glow);
		}
		drawBolt(ctx, x0, top, scale, Theme.fade(0xFFFFF4B8, alpha), Theme.fade(0xFFFFB400, alpha));
	}

	private static void drawBolt(DrawContext ctx, int x0, int top, int scale, int c1, int c2) {
		for (int r = 0; r < BOLT.length; r++) {
			int col = Theme.mix(c1, c2, r / (float) (BOLT.length - 1));
			String row = BOLT[r];
			int start = -1;
			for (int c = 0; c <= row.length(); c++) {
				boolean lit = c < row.length() && row.charAt(c) == '#';
				if (lit && start < 0) start = c;
				if (!lit && start >= 0) {
					ctx.fill(x0 + start * scale, top + r * scale, x0 + c * scale, top + (r + 1) * scale, col);
					start = -1;
				}
			}
		}
	}

	// ---------- backdrop ----------

	private static float frac(float v) {
		return v - (float) Math.floor(v);
	}

	/** Dark animated backdrop: gradient, faint grid, drifting sparks, diagonal light beams and a vignette. */
	public static void backdrop(DrawContext ctx, int w, int h, long timeMs, float alpha) {
		int accent = Theme.accent();
		ctx.fillGradient(0, 0, w, h, Theme.fade(0xFF05060B, alpha), Theme.fade(0xFF101226, alpha));

		// faint grid
		int step = 24;
		int gridCol = Theme.fade(0xFFFFFFFF, 0.035f * alpha);
		for (int x = step / 2; x < w; x += step) ctx.fill(x, 0, x + 1, h, gridCol);
		for (int y = step / 2; y < h; y += step) ctx.fill(0, y, w, y + 1, gridCol);

		// diagonal light beams
		float t = timeMs / 1000f;
		for (int i = 0; i < 2; i++) {
			float sweep = frac(t * 0.045f + i * 0.5f);
			float bx = -h + sweep * (w + h * 2);
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(bx, 0f);
			ctx.getMatrices().rotate(0.5f);
			ctx.fill(0, -h, 26, h * 2, Theme.fade(accent, 0.045f * alpha));
			ctx.fill(8, -h, 12, h * 2, Theme.fade(accent, 0.05f * alpha));
			ctx.getMatrices().popMatrix();
		}

		// sparks
		for (int i = 0; i < 56; i++) {
			float sx = frac(i * 0.6180339f + 0.11f);
			float speed = 0.012f + (i % 7) * 0.004f;
			float sy = frac(i * 0.3819660f + 0.37f - t * speed);
			int px = (int) (sx * w + Math.sin(t * 0.6f + i) * 6f);
			int py = (int) (sy * h);
			float tw = 0.5f + 0.5f * (float) Math.sin(t * 2.2f + i * 1.7f);
			int size = (i % 5 == 0) ? 2 : 1;
			int col = Theme.mix(accent, Theme.BOLT, (i % 3) / 3f);
			ctx.fill(px, py, px + size, py + size, Theme.fade(col, (0.18f + 0.5f * tw) * alpha));
		}

		// vignette
		ctx.fillGradient(0, 0, w, h / 3, Theme.fade(0xCC000000, alpha), 0x00000000);
		ctx.fillGradient(0, h - h / 3, w, h, 0x00000000, Theme.fade(0xDD000000, alpha));
	}

	// ---------- loading screen ----------

	/** Drawn on top of Minecraft's own loading overlay (see SplashOverlayMixin). alpha fades the whole thing out. */
	public static void drawLoading(DrawContext ctx, float alpha, long timeMs) {
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		ctx.fill(0, 0, w, h, Theme.fade(Theme.BG_DEEP, alpha));
		backdrop(ctx, w, h, timeMs, alpha);

		int s = Math.max(2, Math.min(4, h / 90));
		int bs = Math.max(2, s - 1);
		int boltH = boltHeight(bs);
		int wordH = wordHeight(s);
		int gap1 = 8 + s * 2, gap2 = 12 + s * 2;
		int total = boltH + gap1 + wordH + gap2 + 3 + 10 + 7;
		int y = (h - total) / 2;
		int cx = w / 2;

		bolt(ctx, cx, y, bs, alpha, timeMs);
		y += boltH + gap1;
		wordmark(ctx, cx, y, s, alpha);
		y += wordH + gap2;

		// indeterminate progress bar
		int barW = Math.min(150, w / 2);
		int bx = cx - barW / 2;
		int accent = Theme.accent();
		ctx.fill(bx, y, bx + barW, y + 3, Theme.fade(0xFFFFFFFF, 0.08f * alpha));
		float sweep = (timeMs % 1400L) / 1400f;
		int segW = barW / 3;
		int sx = bx - segW + (int) (sweep * (barW + segW));
		int x1 = Math.max(bx, sx), x2 = Math.min(bx + barW, sx + segW);
		if (x2 > x1) ctx.fill(x1, y, x2, y + 3, Theme.fade(accent, alpha));
		y += 3 + 10;

		// "LOADING" + animated dots
		String label = "LOADING";
		int ls = 1;
		int lw = wordWidth(label, ls);
		int lx = cx - (lw + 14) / 2;
		word(ctx, label, lx, y, ls, Theme.fade(Theme.DIM, alpha));
		int dots = (int) ((timeMs / 350L) % 4L);
		for (int i = 0; i < 3; i++) {
			int dx = lx + lw + 4 + i * 4;
			int col = i < dots ? Theme.fade(accent, alpha) : Theme.fade(Theme.MUTE, alpha);
			ctx.fill(dx, y + 6, dx + 2, y + 8, col);
		}
	}
}
