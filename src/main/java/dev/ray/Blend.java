package dev.ray;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

/**
 * "Vanilla blending" for the custom crosshair.
 *
 * Vanilla draws its crosshair with a colour-inverting blend, so it stays visible on any background (light sky,
 * dark cave). This uses the exact same blend (RenderPipelines.GUI_INVERT) for our own pixels. With a coloured
 * pixel the result is "your colour on dark backgrounds, its opposite on bright ones".
 *
 * This is the ONLY place that touches the render pipeline. If a future Minecraft version renames
 * GUI_INVERT, replace the first branch with a plain ctx.fill(...) and the rest of the client keeps working.
 */
public final class Blend {
	private Blend() {}

	public static void fill(DrawContext ctx, int x1, int y1, int x2, int y2, int color, boolean vanilla) {
		if (vanilla) {
			ctx.fill(RenderPipelines.GUI_INVERT, x1, y1, x2, y2, color);
		} else {
			ctx.fill(x1, y1, x2, y2, color);
		}
	}
}
