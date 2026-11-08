package dev.ray;

/**
 * Stretched Res: the 3D world is drawn wider (or taller) than normal, like playing on a stretched resolution, but the
 * window resolution is not changed, and the first-person hand (viewmodel) and every menu keep their normal shape.
 *
 * It works by scaling the world's projection matrix. The game asks for the world field of view and the hand's field of
 * view through the same method with a flag, so the flag tells the two apart.
 */
public final class Stretch {
	/** True when the next projection matrix is for the world (not the hand). */
	public static volatile boolean worldCall = false;
	/** Set once the projection hook has run: tells the settings page whether it works on this Minecraft build. */
	public static volatile boolean hooked = false;

	private Stretch() {}

	public static boolean active() {
		RayConfig c = RayConfig.get();
		return c.stretchOn && (c.stretchX != 100 || c.stretchY != 100);
	}

	/** 133 -> "1.33x". */
	public static String text(int percent) {
		return (percent / 100) + "." + String.format("%02d", percent % 100) + "x";
	}
}
