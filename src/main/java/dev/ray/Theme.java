package dev.ray;

import java.awt.Color;

public final class Theme {
	/** Accent themes: each has a main colour and a second colour (used for the gradient end). Ember is the logo's colour. */
	public static final int[] COLORS = {0xFFFFA45C, 0xFF5CC8FF, 0xFFB58CFF, 0xFF5CF2B0, 0xFFFF8FB5, 0xFFFFD65C, 0xFFE9EEFA, 0xFFFF6B6B};
	public static final int[] COLORS2 = {0xFFFF4D3D, 0xFF3D6BFF, 0xFF7B4DFF, 0xFF18B98A, 0xFFE8467C, 0xFFFF9F2E, 0xFF9DB4D6, 0xFFC81E3C};
	public static final String[] NAMES = {"Ember", "Ocean", "Violet", "Mint", "Rose", "Gold", "Ice", "Crimson"};

	public static final String[] PALETTE_NAMES = {"Default", "White", "Red", "Green", "Blue", "Yellow", "Cyan", "Pink", "Orange", "Purple"};
	private static final int[] PALETTE = {0, 0xFFFFFFFF, 0xFFEF4444, 0xFF22C55E, 0xFF3B82F6, 0xFFFACC15, 0xFF22D3EE, 0xFFF472B6, 0xFFF97316, 0xFF8B5CF6};

	// ---- UI palette: near-black glass, white text, one accent used only for state ----
	public static final int BG_DEEP = 0xFF040406;
	public static final int BG = 0xFF08080B;
	public static final int PANEL = 0xFF0E0E13;
	public static final int PANEL_TOP = 0xFF16161C;
	public static final int CARD = 0xFF121217;
	public static final int CARD_TOP = 0xFF18181F;
	public static final int CARD_HOVER = 0xFF1F1F27;
	/** Hairlines are translucent white so they pick up whatever is behind the glass. */
	public static final int STROKE = 0x24FFFFFF;
	public static final int STROKE_HI = 0x40FFFFFF;
	public static final int TEXT = 0xFFF5F5F8;
	public static final int DIM = 0xFFA8A8B4;
	public static final int MUTE = 0xFF6E6E7C;
	public static final int GOOD = 0xFF4ADE9A;
	public static final int BAD = 0xFFFF5D6C;
	public static final int BOLT = 0xFFFFB45C;

	/** Status colours of the module tiles. On uses the accent now; these remain for the small status dots. */
	public static final int TILE_ON = 0xFF4ADE9A;
	public static final int TILE_OFF = 0xFF6E6E7C;
	public static final int TILE_BG = 0xFF14141A;
	public static final int TILE_BG_HOVER = 0xFF1C1C24;

	private Theme() {}

	// ---- easing ----

	public static float clamp01(float t) {
		return Math.max(0f, Math.min(1f, t));
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	public static float easeOutCubic(float t) {
		t = clamp01(t);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	public static float easeOutQuint(float t) {
		t = clamp01(t);
		float u = 1f - t;
		return 1f - u * u * u * u * u;
	}

	/** Overshoots slightly and settles: good for pop-in effects. */
	public static float easeOutBack(float t) {
		t = clamp01(t);
		float c1 = 1.70158f, c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	public static float easeInOut(float t) {
		t = clamp01(t);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	public static float smoothstep(float t) {
		t = clamp01(t);
		return t * t * (3f - 2f * t);
	}

	/** Linear blend of two ARGB colours (t = 0 gives a, t = 1 gives b). */
	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
		int ar = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int ag = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int ab = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (aa << 24) | (ar << 16) | (ag << 8) | ab;
	}

	/** Multiplies the alpha channel of a colour by f (0..1). */
	public static int fade(int argb, float f) {
		int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Palette colour for an index; index 0 means "use the default colour". */
	public static int pick(int idx, int def) {
		if (idx <= 0 || idx >= PALETTE.length) return def;
		return PALETTE[idx];
	}

	public static int accent() {
		RayConfig cfg = RayConfig.get();
		if (cfg.rainbow) {
			float h = (System.currentTimeMillis() % 4000L) / 4000f;
			return 0xFF000000 | (Color.HSBtoRGB(h, 0.55f, 1f) & 0xFFFFFF);
		}
		int i = Math.max(0, Math.min(COLORS.length - 1, cfg.accent));
		return COLORS[i];
	}

	/** The second accent colour (the other end of the gradient). */
	public static int accent2() {
		RayConfig cfg = RayConfig.get();
		if (cfg.rainbow) {
			float h = ((System.currentTimeMillis() + 700L) % 4000L) / 4000f;
			return 0xFF000000 | (Color.HSBtoRGB(h, 0.75f, 0.9f) & 0xFFFFFF);
		}
		int i = Math.max(0, Math.min(COLORS2.length - 1, cfg.accent));
		return COLORS2[i];
	}

	/** Point t (0..1) along the accent gradient. */
	public static int accentAt(float t) {
		return mix(accent(), accent2(), t);
	}

	public static int withAlpha(int argb, int alpha) {
		return (alpha << 24) | (argb & 0xFFFFFF);
	}

	/** HUD background colour (black with the configured opacity). */
	public static int bg() {
		int a = RayConfig.get().hudOpacity * 255 / 100;
		return a << 24;
	}
}
