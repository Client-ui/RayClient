package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Stand-in box for the server sidebar scoreboard. It only shows in the HUD editor; dragging it
 * moves the real scoreboard by the same amount.
 */
public class ScoreboardModule extends HudModule {
	public ScoreboardModule() {
		super("scoreboard", "Scoreboard", "Visuals", true, MR, 1, -45);
		w = 100;
		h = 90;
	}

	@Override
	public boolean prepare(MinecraftClient mc, TextRenderer tr, boolean editing) {
		w = 100;
		h = 90;
		return editing;
	}

	@Override
	public void draw(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		RayConfig cfg = RayConfig.get();
		int accent = Theme.accent();
		ctx.fill(x, y, x + w, y + h, 0x66000000);
		ctx.fill(x, y, x + w, y + 12, Theme.withAlpha(accent, 0x99));
		ClientFont.draw(ctx, tr, "Scoreboard", x + 4, y + 2, 0xFFFFFFFF, false);
		for (int i = 0; i < 6; i++) {
			ctx.fill(x + 4, y + 18 + i * 11, x + w - 4 - (i % 3) * 14, y + 26 + i * 11, 0x33FFFFFF);
		}
		String s = "Scale " + cfg.scoreboardScale + "%";
		ClientFont.draw(ctx, tr, s, x + w - 4 - ClientFont.width(tr, s), y + h - 10, 0xFFC4C4CE, false);
	}

	@Override
	public float scale() {
		return 1.0f;
	}

	/** How far the player has dragged the scoreboard away from its vanilla spot. */
	public int[] offset(int sw, int sh) {
		RayConfig.Pos p = RayConfig.get().positions.get(id);
		if (p == null) return new int[]{0, 0};
		int[] d = defaultPos(sw, sh);
		return new int[]{p.x - d[0], p.y - d[1]};
	}
}
