package dev.ray;

import net.minecraft.client.render.RenderTickCounter;

/** Minecraft 1.21.5 and newer. */
final class TickCompat {
	private TickCompat() {}

	static Tick of(RenderTickCounter tc) { return new Tick(tc.getTickProgress(false)); }
}
