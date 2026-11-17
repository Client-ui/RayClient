package dev.ray;

import net.minecraft.client.gui.DrawContext;

/** Draws crisp lines of any angle on the HUD (a rotated rectangle), with the thickness in real screen pixels. */
public final class ScreenLines {
	private ScreenLines() {}

	public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, int thickPx, float guiScale, int color) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.hypot(dx, dy);
		if (len < 0.01f || len > 20000f) return;
		Gfx.push(ctx);
		Gfx.translate(ctx, x1, y1);
		Gfx.rotate(ctx, (float) Math.atan2(dy, dx));
		Gfx.scale(ctx, 1.0f / guiScale, 1.0f / guiScale);
		int length = Math.round(len * guiScale);
		int half = thickPx / 2;
		ctx.fill(-half, -half, length + (thickPx - half), thickPx - half, color);
		Gfx.pop(ctx);
	}
}
