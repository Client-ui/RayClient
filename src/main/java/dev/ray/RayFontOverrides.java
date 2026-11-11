package dev.ray;

import net.minecraft.util.Identifier;

import java.util.Map;

/** Implemented by a FontManager mixin so the client can swap the font that replaces Minecraft's default. */
public interface RayFontOverrides {
	/** Replaces the font id overrides. */
	void ray$setOverrides(Map<Identifier, Identifier> overrides);
}
