package dev.ray;

import net.minecraft.client.texture.NativeImage;

/** Minecraft 1.21.2 and newer: NativeImage has an ARGB setter. */
public final class PixelCompat {
	private PixelCompat() {}

	public static void set(NativeImage image, int x, int y, int argb) { image.setColorArgb(x, y, argb); }
}
