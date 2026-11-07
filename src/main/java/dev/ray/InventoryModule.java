package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Shows your 27 main inventory slots on the HUD. */
public class InventoryModule extends HudModule {
	public InventoryModule() {
		super("inventory", "Inventory HUD", "Extras", false, TR, 4, 4);
	}

	@Override
	public boolean prepare(MinecraftClient mc, TextRenderer tr, boolean editing) {
		w = 9 * 18 + 4;
		h = 3 * 18 + 4;
		return true;
	}

	@Override
	public void draw(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		if (hasBg()) ctx.fill(x, y, x + w, y + h, bgColor());
		if (accentBar()) ctx.fill(x, y, x + w, y + 1, accentColor());
		boolean slots = opt("slots") == 1;
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 9; c++) {
				int ix = x + 2 + c * 18, iy = y + 2 + r * 18;
				if (slots) ctx.fill(ix, iy, ix + 16, iy + 16, 0x33FFFFFF);
				ItemStack s = mc.player.getInventory().getStack(9 + r * 9 + c);
				if (s.isEmpty()) continue;
				ctx.drawItem(s, ix, iy);
				ctx.drawStackOverlay(tr, s, ix, iy);
			}
		}
	}
}
