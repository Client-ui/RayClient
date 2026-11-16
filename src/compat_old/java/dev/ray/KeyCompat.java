package dev.ray;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/** Key bindings for Minecraft 1.21.8 and older, where the category is a translation key */
final class KeyCompat {
	private KeyCompat() {}

	static KeyBinding make(String id, int key) {
		return new KeyBinding(id, InputUtil.Type.KEYSYM, key, "key.category.ray.main");
	}
}
