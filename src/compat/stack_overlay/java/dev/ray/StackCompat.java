package dev.ray;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Minecraft 1.21.2 and newer. */
final class StackCompat {
	private StackCompat() {}

	static void overlay(DrawContext ctx, TextRenderer tr, ItemStack stack, int x, int y) {
		ctx.drawStackOverlay(tr, stack, x, y);
	}
}
