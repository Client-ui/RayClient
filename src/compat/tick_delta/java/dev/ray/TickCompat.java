package dev.ray;

import net.minecraft.client.render.RenderTickCounter;

/** Minecraft 1.21 to 1.21.4. */
final class TickCompat {
	private TickCompat() {}

	static Tick of(RenderTickCounter tc) { return new Tick(tc.getTickDelta(false)); }
}
