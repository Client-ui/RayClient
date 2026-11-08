package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

/**
 * Free look: while active, the mouse turns the camera but not your character, so you can look around while you keep
 * walking / facing the same way. Bind it to any keyboard key or mouse button (3+) in the module's settings.
 *
 * The camera mixin calls frame() every rendered frame and applies yaw/pitch; the entity mixin redirects mouse
 * movement into yaw/pitch while active. Until the camera hook has run once ("hooked"), free look stays off, so a
 * Minecraft version where the hook does not attach can never leave you with a frozen mouse.
 */
public final class FreeLook {
	/** Set by the camera mixin the first time it runs. */
	public static volatile boolean hooked = false;
	public static boolean active = false;
	public static float yaw, pitch;

	/** Set while the settings page waits for a key / button press. */
	public static boolean listening = false;
	private static long listenStart = 0L;
	private static boolean wasDown = false;
	/** True while we switched you from first to third person and owe you the switch back. */
	private static boolean autoSwitched = false;

	private FreeLook() {}

	/** Called every frame from the camera mixin. */
	public static void frame() {
		MinecraftClient mc = MinecraftClient.getInstance();
		RayConfig c = RayConfig.get();
		if (mc.player == null || !c.freelook || mc.currentScreen != null) {
			if (active) restorePerspective(mc);
			active = false;
			wasDown = false;
			return;
		}
		boolean down = bindingDown(mc, c);
		if (c.freelookToggle) {
			if (down && !wasDown) setActive(!active, mc);
		} else {
			setActive(down, mc);
		}
		wasDown = down;
	}

	private static void setActive(boolean on, MinecraftClient mc) {
		if (on && !active) {
			yaw = mc.player.getYaw();
			pitch = mc.player.getPitch();
			// from first person, free look is only really visible from outside your head: step back into third person
			if (RayConfig.get().freelookThirdPerson && mc.options.getPerspective().isFirstPerson()) {
				mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
				autoSwitched = true;
			}
		} else if (!on && active) {
			restorePerspective(mc);
		}
		active = on;
	}

	/** Goes back to first person if (and only if) we were the ones who left it. */
	private static void restorePerspective(MinecraftClient mc) {
		if (!autoSwitched) return;
		autoSwitched = false;
		if (mc.options.getPerspective() == Perspective.THIRD_PERSON_BACK) mc.options.setPerspective(Perspective.FIRST_PERSON);
	}

	private static boolean bindingDown(MinecraftClient mc, RayConfig c) {
		long win = mc.getWindow().getHandle();
		if (c.freelookKey >= 0 && GLFW.glfwGetKey(win, c.freelookKey) == GLFW.GLFW_PRESS) return true;
		return c.freelookBtn >= 0 && GLFW.glfwGetMouseButton(win, c.freelookBtn) == GLFW.GLFW_PRESS;
	}

	/** Mouse movement while active (same 0.15 degrees per unit as vanilla). */
	public static void rotate(double dx, double dy) {
		yaw += (float) (dx * 0.15);
		pitch = Math.max(-90f, Math.min(90f, pitch + (float) (dy * 0.15)));
	}

	// ---------- binding UI ----------

	public static String bindingName(RayConfig c) {
		if (listening) return "Press a key...";
		if (c.freelookKey >= 0) return Keys.name(c.freelookKey);
		if (c.freelookBtn >= 0) return c.freelookBtn == 2 ? "Middle Mouse" : "Mouse " + (c.freelookBtn + 1);
		return "None";
	}

	public static void startListening() {
		listening = true;
		listenStart = System.currentTimeMillis();
	}

	/** Call every frame while listening. Takes the first key or extra mouse button pressed; ESC cancels. */
	public static void pollBinding(MinecraftClient mc) {
		if (!listening || System.currentTimeMillis() - listenStart < 250L) return;
		RayConfig c = RayConfig.get();
		long win = mc.getWindow().getHandle();
		if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
			listening = false;
			return;
		}
		for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
			if (GLFW.glfwGetKey(win, k) == GLFW.GLFW_PRESS) {
				c.freelookKey = (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) ? -1 : k;
				c.freelookBtn = -1;
				listening = false;
				RayConfig.save();
				return;
			}
		}
		for (int b = 2; b <= GLFW.GLFW_MOUSE_BUTTON_LAST; b++) { // left (0) and right (1) stay free for the menu
			if (GLFW.glfwGetMouseButton(win, b) == GLFW.GLFW_PRESS) {
				c.freelookBtn = b;
				c.freelookKey = -1;
				listening = false;
				RayConfig.save();
				return;
			}
		}
	}
}
