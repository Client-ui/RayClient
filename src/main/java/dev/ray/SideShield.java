package dev.ray;

/** Side Shield helpers: presets for the settings page and a flag that tells whether the render hook is running. */
public final class SideShield {
	/** Set once the shield hook has run: tells the settings page whether it works on this Minecraft version. */
	public static volatile boolean hooked = false;
	/** The "Side Shield is working" notice is shown once per game start. */
	public static volatile boolean announced = false;

	/** name, sideways, height, depth, size %, tilt */
	public static final Object[][] PRESETS = {
		{"LOW OUTER", 35, -20, 0, 85, 0},
		{"LOW CENTRE", 0, -38, 0, 80, 0},
		{"FAR CORNER", 60, -42, -5, 70, 8},
		{"TUCKED", 48, -48, -12, 60, 18},
		{"BARELY THERE", 70, -55, -20, 45, 22},
	};

	private SideShield() {}

	public static void apply(RayConfig c, int i) {
		Object[] p = PRESETS[i];
		c.ssX = (Integer) p[1];
		c.ssY = (Integer) p[2];
		c.ssZ = (Integer) p[3];
		c.ssScale = (Integer) p[4];
		c.ssTilt = (Integer) p[5];
		c.sideShield = true;
	}

	public static boolean matches(RayConfig c, int i) {
		Object[] p = PRESETS[i];
		return c.ssX == (Integer) p[1] && c.ssY == (Integer) p[2] && c.ssZ == (Integer) p[3] && c.ssScale == (Integer) p[4] && c.ssTilt == (Integer) p[5];
	}
}
