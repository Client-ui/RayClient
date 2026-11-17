package dev.ray;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.RotationAxis;

/** 2D matrix operations for Minecraft 1.21.5 and older, where DrawContext uses a MatrixStack. */
public final class Gfx {
	private Gfx() {}

	public static void push(DrawContext ctx) { ctx.getMatrices().push(); }

	public static void pop(DrawContext ctx) { ctx.getMatrices().pop(); }

	public static void translate(DrawContext ctx, float x, float y) { ctx.getMatrices().translate(x, y, 0.0f); }

	public static void scale(DrawContext ctx, float x, float y) { ctx.getMatrices().scale(x, y, 1.0f); }

	public static void rotate(DrawContext ctx, float radians) {
		ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation(radians));
	}
}
