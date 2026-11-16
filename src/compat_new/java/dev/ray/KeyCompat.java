package dev.ray;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

/** Key bindings for Minecraft 1.21.9 and newer, where categories are objects */
final class KeyCompat {
	private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(RayClient.MOD_ID, "main"));

	private KeyCompat() {}

	static KeyBinding make(String id, int key) {
		return new KeyBinding(id, InputUtil.Type.KEYSYM, key, CATEGORY);
	}
}
