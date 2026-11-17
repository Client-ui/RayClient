package dev.ray;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Minecraft 1.21.1 and older, where the method is called drawItemInSlot. */
final class StackCompat {
	private StackCompat() {}

	static void overlay(DrawContext ctx, TextRenderer tr, ItemStack stack, int x, int y) {
		ctx.drawItemInSlot(tr, stack, x, y);
	}
}
