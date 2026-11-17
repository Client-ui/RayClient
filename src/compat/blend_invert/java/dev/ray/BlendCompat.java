package dev.ray;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

/** Minecraft 1.21.7 and newer: the game's own colour-inverting GUI blend exists. */
final class BlendCompat {
	private BlendCompat() {}

	static void invert(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(RenderPipelines.GUI_INVERT, x1, y1, x2, y2, color);
	}
}
