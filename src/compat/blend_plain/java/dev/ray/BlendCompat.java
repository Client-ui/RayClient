package dev.ray;

import net.minecraft.client.gui.DrawContext;

/** Minecraft 1.21.6 and older: there is no colour-inverting GUI blend, so the crosshair is drawn as a plain fill. */
final class BlendCompat {
	private BlendCompat() {}

	static void invert(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1, y1, x2, y2, color);
	}
}
