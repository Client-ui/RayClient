package dev.ray;

/**
 * Hit Color: changes the colour (and strength) of the flash entities get when they are hurt. Vanilla's flash is a
 * 70% red layer baked into a small overlay texture; this rewrites that layer when the setting changes.
 * It applies to every entity that gets hurt, not only players you hit.
 */
public final class HitColor {
	private static final int VANILLA = 0xB2FF0000;

	/** Set by the OverlayTexture mixin when the texture is created. */
	public static Object overlay;
	private static int applied = VANILLA;

	private HitColor() {}

	public static void tick() {
		RayConfig c = RayConfig.get();
		int want = c.hitColor ? ((Math.max(10, Math.min(100, c.hitColorStrength)) * 255 / 100) << 24) | (c.hitColorRgb & 0xFFFFFF) : VANILLA;
		if (want == applied) return;
		if (overlay instanceof OverlayRecolor r) {
			r.ray$recolor(want);
			applied = want;
		}
	}
}
