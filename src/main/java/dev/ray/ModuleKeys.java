package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/** Per-module toggle keys (set in each module's settings). */
public final class ModuleKeys {
	private static final Set<String> down = new HashSet<>();

	private ModuleKeys() {}

	public static void tick(MinecraftClient mc) {
		if (mc.player == null) return;
		long win = mc.getWindow().getHandle();
		for (HudModule m : Modules.ALL) {
			RayConfig.Style st = RayConfig.get().styles.get(m.id);
			if (st == null || st.key < 0) {
				down.remove(m.id);
				continue;
			}
			boolean pressed = GLFW.glfwGetKey(win, st.key) == GLFW.GLFW_PRESS;
			if (pressed && !down.contains(m.id)) {
				down.add(m.id);
				if (mc.currentScreen == null) {
					m.setEnabled(!m.enabled());
					RayConfig.save();
					String msg = m.name + ": " + (m.enabled() ? "ON" : "OFF");
					if (RayConfig.get().toastsOn) Toasts.push(msg);
					else mc.player.sendMessage(Text.literal(msg), true);
				}
			} else if (!pressed) {
				down.remove(m.id);
			}
		}
	}
}
