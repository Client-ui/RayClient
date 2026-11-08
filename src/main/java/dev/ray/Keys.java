package dev.ray;

import org.lwjgl.glfw.GLFW;

public final class Keys {
	private Keys() {}

	public static String name(int k) {
		if (k < 0) return "None";
		String n = GLFW.glfwGetKeyName(k, 0);
		if (n != null && !n.isEmpty()) return n.toUpperCase();
		if (k >= GLFW.GLFW_KEY_F1 && k <= GLFW.GLFW_KEY_F12) return "F" + (k - GLFW.GLFW_KEY_F1 + 1);
		return switch (k) {
			case GLFW.GLFW_KEY_SPACE -> "Space";
			case GLFW.GLFW_KEY_ENTER -> "Enter";
			case GLFW.GLFW_KEY_TAB -> "Tab";
			case GLFW.GLFW_KEY_INSERT -> "Insert";
			case GLFW.GLFW_KEY_HOME -> "Home";
			case GLFW.GLFW_KEY_END -> "End";
			case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
			case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
			case GLFW.GLFW_KEY_UP -> "Up";
			case GLFW.GLFW_KEY_DOWN -> "Down";
			case GLFW.GLFW_KEY_LEFT -> "Left";
			case GLFW.GLFW_KEY_RIGHT -> "Right";
			case GLFW.GLFW_KEY_LEFT_SHIFT -> "L-Shift";
			case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-Shift";
			case GLFW.GLFW_KEY_LEFT_CONTROL -> "L-Ctrl";
			case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-Ctrl";
			case GLFW.GLFW_KEY_LEFT_ALT -> "L-Alt";
			case GLFW.GLFW_KEY_RIGHT_ALT -> "R-Alt";
			case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps Lock";
			default -> "Key " + k;
		};
	}
}
