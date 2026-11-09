package dev.ray;

import net.minecraft.client.MinecraftClient;

/**
 * Makes every Ray screen fit the window, whatever the window size and Minecraft's GUI Scale are.
 *
 * A screen is laid out for a fixed "design" size (for example 760 x 440). {@link #fit} returns the factor to scale the
 * drawing by so that design size fits the window with a small margin, times the player's Menu Size setting (100% = fits).
 * The result is snapped to a whole number of real screen pixels per layout unit (when large enough), so text and 1px
 * lines stay sharp instead of blurring at fractional scales.
 */
public final class UiScale {
	private UiScale() {}

	/** The Minecraft GUI scale factor (real pixels per GUI unit). */
	public static double guiScale() {
		try {
			double s = MinecraftClient.getInstance().getWindow().getScaleFactor();
			return s < 1.0 ? 1.0 : s;
		} catch (Throwable t) {
			return 1.0;
		}
	}

	public static float fit(int guiW, int guiH, int designW, int designH, int percent) {
		return fit(guiW, guiH, designW, designH, percent, guiScale());
	}

	/** Pure version (no Minecraft calls) so it can be tested alone. */
	public static float fit(int guiW, int guiH, int designW, int designH, int percent, double gs) {
		// the largest size that fits the window, then 78% of that: menus take about two thirds of the window, not all of it
		float auto = Math.min(guiW * 0.94f / Math.max(1, designW), guiH * 0.92f / Math.max(1, designH)) * 0.78f;
		float want = auto * Math.max(0.5f, percent / 100.0f);
		want = Math.max(0.2f, Math.min(5.0f, want));
		float real = (float) (want * gs); // real screen pixels per layout unit
		// only whole and half pixels: text and lines keep their shape at every Menu Size instead of smearing
		if (real >= 1.0f) real = Math.max(1.0f, Math.round(real * 2f) / 2f);
		else real = Math.max(0.5f, Math.round(real * 4f) / 4f);
		return (float) (real / gs);
	}

	/** Size of the virtual screen (in layout units) for a window dimension in GUI units. */
	public static int view(int gui, float sc) {
		return (int) Math.ceil(gui / sc);
	}

	/** A mouse coordinate in GUI units to layout units. The integer is treated as the middle of its pixel. */
	public static int mouse(int gui, float sc) {
		return (int) Math.floor((gui + 0.5f) / sc);
	}
}
