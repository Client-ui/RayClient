package dev.ray;

import net.minecraft.client.gui.DrawContext;

/** 2D matrix operations for Minecraft 1.21.6 and newer, where DrawContext uses a JOML Matrix3x2fStack. */
public final class Gfx {
	private Gfx() {}

	public static void push(DrawContext ctx) { ctx.getMatrices().pushMatrix(); }

	public static void pop(DrawContext ctx) { ctx.getMatrices().popMatrix(); }

	public static void translate(DrawContext ctx, float x, float y) { ctx.getMatrices().translate(x, y); }

	public static void scale(DrawContext ctx, float x, float y) { ctx.getMatrices().scale(x, y); }

	public static void rotate(DrawContext ctx, float radians) { ctx.getMatrices().rotate(radians); }
}
