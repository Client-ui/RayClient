package dev.ray;

import net.minecraft.entity.effect.StatusEffectInstance;

/** Minecraft 1.20.5 and newer: getEffectType returns a RegistryEntry. */
final class EffectCompat {
	private EffectCompat() {}

	static String name(StatusEffectInstance e) {
		return e.getEffectType().value().getName().getString();
	}
}
