package dev.ray;

import net.minecraft.client.gui.DrawContext;

import java.util.function.BiPredicate;

/** 11x11 pixel crosshair stored as a string of '0' / '1'. */
public final class CrosshairArt {
	public static final int N = 11;
	public static final String[] COLOR_NAMES = {"Accent", "White", "Red", "Green", "Blue", "Yellow", "Cyan", "Pink"};
	private static final int[] COLORS = {0, 0xFFFFFFFF, 0xFFEF4444, 0xFF22C55E, 0xFF3B82F6, 0xFFFACC15, 0xFF22D3EE, 0xFFF472B6};

	private CrosshairArt() {}

	public static int colorCount() {
		return COLORS.length;
	}

	public static int color(int idx) {
		if (idx <= 0 || idx >= COLORS.length) return Theme.accent();
		return COLORS[idx];
	}

	private static String build(BiPredicate<Integer, Integer> f) {
		StringBuilder sb = new StringBuilder();
		for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) sb.append(f.test(r, c) ? '1' : '0');
		return sb.toString();
	}

	public static String blank() { return build((r, c) -> false); }
	public static String cross() { return build((r, c) -> (r == 5 && Math.abs(c - 5) >= 2) || (c == 5 && Math.abs(r - 5) >= 2)); }
	public static String dot() { return build((r, c) -> Math.abs(r - 5) <= 1 && Math.abs(c - 5) <= 1); }
	public static String ring() { return build((r, c) -> { double d = Math.hypot(r - 5, c - 5); return d >= 3.5 && d <= 4.9; }); }
	public static String x() { return build((r, c) -> Math.abs(r - 5) == Math.abs(c - 5) && Math.abs(r - 5) >= 2); }

	private static String fixed(String art) {
		return (art == null || art.length() != N * N) ? blank() : art;
	}

	public static boolean get(String art, int r, int c) {
		art = fixed(art);
		return art.charAt(r * N + c) == '1';
	}

	public static String set(String art, int r, int c, boolean v) {
		char[] a = fixed(art).toCharArray();
		a[r * N + c] = v ? '1' : '0';
		return new String(a);
	}

	public static void draw(DrawContext ctx, String art, int cx, int cy, int scale, int color, boolean outline, boolean blend) {
		art = fixed(art);
		int x0 = cx - (N * scale) / 2;
		int y0 = cy - (N * scale) / 2;
		// an outline would be invisible under the inverting blend, so it is skipped there
		if (outline && !blend) {
			for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
				if (art.charAt(r * N + c) != '1') continue;
				ctx.fill(x0 + c * scale - 1, y0 + r * scale - 1, x0 + (c + 1) * scale + 1, y0 + (r + 1) * scale + 1, 0xFF000000);
			}
		}
		for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
			if (art.charAt(r * N + c) != '1') continue;
			Blend.fill(ctx, x0 + c * scale, y0 + r * scale, x0 + (c + 1) * scale, y0 + (r + 1) * scale, color, blend);
		}
	}
}
