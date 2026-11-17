package dev.ray;

import net.minecraft.entity.effect.StatusEffectInstance;

/** Minecraft 1.20.4 and older: getEffectType returns the StatusEffect itself. */
final class EffectCompat {
	private EffectCompat() {}

	static String name(StatusEffectInstance e) {
		return e.getEffectType().getName().getString();
	}
}
