package dev.ray;

import net.minecraft.client.texture.NativeImage;

/** Minecraft 1.21.1 and older: setColor takes ABGR, so the colour's red and blue are swapped first. */
public final class PixelCompat {
	private PixelCompat() {}

	public static void set(NativeImage image, int x, int y, int argb) {
		int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
		image.setColor(x, y, abgr);
	}
}
