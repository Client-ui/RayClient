package dev.ray;

/**
 * Smooth zoom. The key sets a target (0 = normal, 1 = fully zoomed); every rendered frame the amount eases toward
 * it using real elapsed time, so it is smooth at any frame rate (the old zoom stepped the FOV setting 20 times a
 * second). The multiplier is applied to the world FOV by GameRendererFovMixin.
 */
public final class ZoomState {
	/** Set by the key handler (20 Hz); read every frame. */
	public static volatile float target = 0f;
	/** Set once the render hook has run; until then RayClient falls back to the old tick-based zoom. */
	public static volatile boolean hooked = false;

	private static float amount = 0f;
	private static long lastNanos = 0L;

	private ZoomState() {}

	/** Advances the easing to "now" and returns the current zoom amount (0..1). */
	public static float update() {
		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		if (!RayConfig.get().smoothZoom) {
			amount = target;
		} else {
			// exponential ease: ~95% of the way in about a quarter of a second
			amount += (target - amount) * (1.0f - (float) Math.exp(-12.0f * dt));
			if (Math.abs(target - amount) < 0.001f) amount = target;
		}
		return amount;
	}

	/** Factor to multiply the world FOV by (1 = no zoom). Geometric blend so it feels even the whole way. */
	public static float fovFactor(double baseFov, float amt) {
		if (amt <= 0f || baseFov <= 0) return 1f;
		double ratio = 10.0 / Math.max(10, RayConfig.get().zoomLevel); // 3.0x -> FOV / 3
		return (float) Math.pow(ratio, amt);
	}
}
