package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class CompassModule extends HudModule {
	private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	public CompassModule(int ox, int oy) {
		super("compass", "Compass", "World", false, TL, ox, oy);
	}

	@Override
	public boolean prepare(MinecraftClient mc, TextRenderer tr, boolean editing) {
		w = opt("width");
		h = 14;
		return true;
	}

	@Override
	public void draw(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		int accent = accentColor();
		if (hasBg()) ctx.fill(x, y, x + w, y + h, bgColor());
		if (accentBar()) ctx.fill(x, y, x + w, y + 1, accent);
		double heading = (((mc.player.getYaw() + 180.0) % 360.0) + 360.0) % 360.0;
		for (int i = 0; i < DIRS.length; i++) {
			double diff = ((i * 45.0 - heading + 540.0) % 360.0) - 180.0;
			if (Math.abs(diff) > 70.0) continue;
			int px = x + w / 2 + (int) (diff * (w / 2.0) / 70.0);
			String d = DIRS[i];
			int col = i % 2 == 0 ? textColor() : Theme.withAlpha(textColor(), 0xB0);
			ClientFont.draw(ctx, tr, d, px - ClientFont.width(tr, d) / 2, y + 4, col, shadow());
		}
		ctx.fill(x + w / 2, y + h - 3, x + w / 2 + 1, y + h, accent);
	}
}
