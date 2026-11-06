package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;

/** Name, distance and health of the mob or player you are looking at. */
public class TargetInfoModule extends HudModule {
	private String line1 = "", line2 = "";
	private float ratio = 1.0f;
	private boolean bar;

	public TargetInfoModule() {
		super("targetinfo", "Target Info", "PvP", false, MR, 4, -100);
	}

	@Override
	public boolean prepare(MinecraftClient mc, TextRenderer tr, boolean editing) {
		bar = opt("bar") == 1;
		boolean dist = opt("dist") == 1;
		String name;
		float hp, max;
		double d;
		if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
			name = le.getName().getString();
			hp = le.getHealth();
			max = Math.max(1.0f, le.getMaxHealth());
			d = mc.player.distanceTo(le);
		} else {
			if (!editing) return false;
			name = "Target Info";
			hp = 20.0f;
			max = 20.0f;
			d = 3.2;
		}
		line1 = name + (dist ? String.format("  %.1fm", d) : "");
		line2 = String.format("HP %.1f / %.1f", hp, max);
		ratio = Math.max(0.0f, Math.min(1.0f, hp / max));
		w = Math.max(80, Math.max(ClientFont.width(tr, line1), ClientFont.width(tr, line2)) + 8);
		h = 23 + (bar ? 5 : 0);
		return true;
	}

	@Override
	public void draw(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		if (hasBg()) ctx.fill(x, y, x + w, y + h, bgColor());
		if (accentBar()) ctx.fill(x, y, x + 2, y + h, accentColor());
		ClientFont.draw(ctx, tr, line1, x + 5, y + 2, textColor(), shadow());
		ClientFont.draw(ctx, tr, line2, x + 5, y + 13, textColor(), shadow());
		if (bar) {
			int bx = x + 5, bw = w - 10, by = y + h - 5;
			ctx.fill(bx, by, bx + bw, by + 3, 0x66000000);
			int g = (int) (255 * ratio);
			int col = 0xFF000000 | ((255 - g) << 16) | (g << 8);
			ctx.fill(bx, by, bx + (int) (bw * ratio), by + 3, col);
		}
	}
}
